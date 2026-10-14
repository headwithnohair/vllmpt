package org.albedo.vllmpt.module.file.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.alert.AlertService;
import org.albedo.vllmpt.module.file.service.FileParseTaskService;
import org.albedo.vllmpt.module.file.service.MinioService;
import org.albedo.vllmpt.module.file.service.TaskProgressWriter;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 解析阶段消费者【已实现，作为向量化消费者（EmbedConsumer）的对照样板】。
 * <p>
 * <b>注解三个关键属性（考点：消费组与重试）</b>：
 * <ul>
 *   <li>{@code consumerGroup}：负载均衡的"单位"——同组多实例分摊队列（集群消费），
 *       不同组各收一份（广播各一份），所以每个阶段用独立组；</li>
 *   <li>{@code consumeThreadNumber}：并发消费线程数——大文件解析是重活，开小一点（2），
 *       避免多文件并行把内存/CPU 打满；</li>
 *   <li>{@code maxReconsumeTimes}：重试上限——重试耗尽后消息自动进入
 *       {@code %DLQ%file-parse-consumer-group}（在 Dashboard 的 Topic 页可见）。</li>
 * </ul>
 * <p>
 * <b>返回语义（考点：消费可靠性）</b>——{@link RocketMQListener#onMessage} 是"返回值即确认"模型：
 * <ul>
 *   <li>正常 return = ACK（消息消费成功，提交位点）；</li>
 *   <li>抛异常 = {@code RECONSUME_LATER}（broker 延迟后重投，重试次数越多延迟级别越高）。</li>
 * </ul>
 * 因此本类对失败做"分流"：业务性失败（重试也没用的）直接标终态并 ACK；
 * 可恢复失败（IO/网络抖动）抛异常交给 broker 重试。
 * <p>
 * <b>幂等说明</b>：重试或重复投递时本类会重复执行——解析产物用同 taskId 覆盖写（幂等），
 * 但"再次发送 embed 消息"会造成下游重复，向量化侧需要保证幂等（见 EmbedConsumer 的 TODO 提示）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConst.TOPIC_FILE_PARSE,
        consumerGroup = MqConst.GROUP_PARSE,
        selectorExpression = "*",
        consumeThreadNumber = 2,
        maxReconsumeTimes = MqConst.MAX_RECONSUME_TIMES
)
public class ParseConsumer implements RocketMQListener<FileParseMessage> {

    /** 数据桶：源文件归档后所在桶，解析产物也写到这里 */
    private static final String DATA_BUCKET = "vllmpt-data";

    private final MinioService minioService;
    private final TaskProgressWriter progressWriter;
    private final FileParseTaskService fileParseTaskService;
    private final FileParseProducer producer;
    private final AlertService alertService;

    @Override
    public void onMessage(FileParseMessage message) {
        String taskId = message.getTaskId();
        log.info("[解析阶段] 收到消息: taskId={}, file={}", taskId, message.getFileName());

        // 进度约定：解析开始 10%，解析完成 30%（阶段内完成度由消费者自行约定）
        progressWriter.running(taskId, "PARSING", 10, "开始解析文件...");

        try {
            // 1) 提取纯文本（按 MIME 分流；PDF 分支留给你实现，见 extractText）
            String text = extractText(message);

            // 2) 解析产物（纯文本）写回 MinIO——解析与向量化解耦的"交接棒"。
            //    为什么不上消息：几百 MB 的文本塞不进消息体，而且重试时无需重复解析。
            String parsedObjectName = "parsed/" + taskId + ".txt";
            minioService.uploadText(DATA_BUCKET, parsedObjectName, text);
            message.setParsedObjectName(parsedObjectName);

            // 3) 写进度 30%，发送下一阶段消息（级联）
            progressWriter.running(taskId, "PARSING", 30, "解析完成");
            producer.sendEmbedStage(message);
            log.info("[解析阶段] 完成: taskId={}, textLength={}", taskId, text.length());

        } catch (UnsupportedOperationException e) {
            // ============ 失败分流 A：业务性失败（重试无意义） ============
            // 直接标终态 + 告警，然后正常返回（ACK 掉）——不进重试、不浪费 DLQ 位。
            String reason = "解析失败(不可重试): " + e.getMessage();
            progressWriter.failed(taskId, reason);
            fileParseTaskService.markFailed(taskId, reason);
            alertService.alert("FILE_PARSE_FAILED",
                    "taskId=" + taskId + ", file=" + message.getFileName() + ", " + reason);
            log.warn("[解析阶段] 业务性失败，已标终态: taskId={}, reason={}", taskId, e.getMessage());

        } catch (Exception e) {
            // ============ 失败分流 B：可恢复失败（IO/网络抖动等） ============
            // 重新抛出 → broker 按 maxReconsumeTimes 重试 → 耗尽进 %DLQ%（由 DlqConsumer 兜底）。
            log.error("[解析阶段] 处理异常，将触发重试: taskId={}", taskId, e);
            progressWriter.running(taskId, "PARSING", 10, "解析异常，等待重试...");
            throw new RuntimeException("解析阶段失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把源文件提取为纯文本
     *
     * <pre>
     *   文本类（text/plain、text/markdown）：已实现——直接流式读成字符串；
     *   PDF（application/pdf）：TODO 留给你——见下方注释的实施方案。
     * </pre>
     */
    private String extractText(FileParseMessage message) throws Exception {
        String mimeType = message.getMimeType() == null ? "" : message.getMimeType().toLowerCase();

        if (mimeType.startsWith("text/")) {
            // 文本类文件：流式读全量（生产环境大文本建议逐块处理，这里演示简化）
            try (InputStream in = minioService.getFileStream(message.getObjectName(), message.getBucket())) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        if ("application/pdf".equals(mimeType)) {
            // ==================== TODO（留给你）：PDF 文本提取 ====================
            // PDFBox 需要「本地文件路径」而不是流，实施步骤建议：
            //  1) MinioService 目前只暴露了 getFileStream / processFileFromMinIO（后者下载完就删，
            //     不给你路径）——可以给 MinioService 扩展一个「下载到本地临时文件并返回 File」的方法，
            //     或直接复用其内部 processWithLocalTempFile 的落盘思路；
            //  2) 用 ai 模块的 DocumentExtractor（实现类 PdfBoxDocumentExtractor）：
            //        String text = pdfBoxDocumentExtractor.extractText(本地文件路径);
            //  3) 解析完成在 finally 里删除临时文件（防止 data/app_temp 堆积）。
            // 实现完把这里的 throw 删掉即可。
            throw new UnsupportedOperationException("PDF 解析尚未实现（TODO）：" + message.getFileName());
        }

        throw new UnsupportedOperationException("暂不支持的文件类型: " + mimeType);
    }
}
