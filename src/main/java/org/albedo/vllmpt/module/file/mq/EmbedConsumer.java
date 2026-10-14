package org.albedo.vllmpt.module.file.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.service.impl.KnowledgeBaseRagServiceImpl;
import org.albedo.vllmpt.module.file.service.FileParseTaskService;
import org.albedo.vllmpt.module.file.service.MinioService;
import org.albedo.vllmpt.module.file.service.TaskProgressWriter;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 向量化阶段消费者【骨架 · 留给你实现】。
 * <p>
 * 对照样板：{@link ParseConsumer}——注解怎么写、依赖怎么注入、失败怎么分流，全部照抄即可；
 * 本类只把「业务步骤」留给你的 TODO，按编号补全后删掉 {@link #onMessage} 里的 throw 即可运行。
 * <p>
 * 注意：实现之前不要删这个 throw——它让消息走「重试 → %DLQ%」链路而不是被静默 ACK 丢弃，
 * 顺便让你现在就能在 Dashboard 里观察重试次数与死信 Topic。
 * <p>
 * <b>幂等（考点，动手前先想清楚）</b>：重试/重复投递会让本方法重复执行。
 * 提示三选一：
 * <ul>
 *   <li>进入时先查台账 {@code fileParseTaskService.getByTaskId(taskId)}，已是 SUCCESS 直接 return；</li>
 *   <li>入库前按 taskId 清理旧数据（向量库删旧 chunk）再写新的；</li>
 *   <li>TextSegment 的 metadata 带 taskId + chunkIndex 作为去重键（取决于向量库能力）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConst.TOPIC_FILE_EMBED,
        consumerGroup = MqConst.GROUP_EMBED,
        selectorExpression = "*",
        consumeThreadNumber = 2,
        maxReconsumeTimes = MqConst.MAX_RECONSUME_TIMES
)
public class EmbedConsumer implements RocketMQListener<FileParseMessage> {

    private final MinioService minioService;
    private final TaskProgressWriter progressWriter;
    private final FileParseTaskService fileParseTaskService;
    private final KnowledgeBaseRagServiceImpl knowledgeBaseRagService;

    @Override
    public void onMessage(FileParseMessage message) {
        // ==================== TODO（留给你，按编号补全） ====================
        //
        // 参考进度时序：CHUNKING 60% → EMBEDDING 90% → success 100%
        //
        // TODO(1) 幂等前置检查：getByTaskId(taskId) 若已是终态(SUCCESS/FAILED) → 直接 return；
        //         进度可先写一条：progressWriter.running(taskId, "CHUNKING", 60, "分块中...")；
        //
        // TODO(2) 拉取解析产物文本（解析阶段已写好在 message.getParsedObjectName()）：
        //         try (InputStream in = minioService.getFileStream(message.getParsedObjectName(), "vllmpt-data")) {
        //             String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        //         }
        //
        // TODO(3) 分块（langchain4j 现成能力，MinioService 里已有 import 示范）：
        //         List<TextSegment> segments = DocumentSplitters.recursive(1000, 150)
        //                 .split(Document.from(text));
        //         可给每个 segment 的 metadata 打上 taskId / fileName / chunkIndex：
        //         Metadata meta = new Metadata().put("taskId", taskId).put("fileName", message.getFileName());
        //         segment = TextSegment.from(segment.text(), meta);
        //
        // TODO(4) 逐块向量化入库（就是"向量化"本身）：
        //         for (TextSegment segment : segments) { knowledgeBaseRagService.indexTextSegment(segment); }
        //         建议每处理 N 块（如 20 块）刷新一次进度：
        //         progressWriter.running(taskId, "EMBEDDING", 90, "向量化中 " + done + "/" + total);
        //
        // TODO(5) 全部完成 → 双写终态：
        //         progressWriter.success(taskId);
        //         fileParseTaskService.markSuccess(taskId, segments.size());   // chunk_count 回填
        //
        // TODO(6) 失败处理：不要 catch 后吞掉——让异常抛出（= RECONSUME_LATER），
        //         重试耗尽后进入 %DLQ%file-embed-consumer-group（DlqConsumer 会兜底标失败+告警）。
        //         模拟失败方法：测试期把 Chroma 容器停掉（docker stop）即可复现。
        //
        // 建议实现顺序：先把 TODO(2)(3)(4)(5) 连起来跑通全链路，再加 TODO(1) 幂等、TODO(6) 失败分流。
        // =================================================================
        throw new UnsupportedOperationException(
                "向量化消费者尚未实现（TODO）：请按 onMessage 注释中的步骤补全。taskId=" + message.getTaskId());
    }
}
