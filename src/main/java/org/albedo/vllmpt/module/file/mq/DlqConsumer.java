package org.albedo.vllmpt.module.file.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.alert.AlertService;
import org.albedo.vllmpt.module.file.service.FileParseTaskService;
import org.albedo.vllmpt.module.file.service.TaskProgressWriter;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 死信兜底消费者【已实现】——监听向量化阶段的重试耗尽消息。
 * <p>
 * <b>DLQ 机制（考点）</b>：
 * <ul>
 *   <li>消费失败（onMessage 抛异常）→ broker 按递增延迟重试，次数超过
 *       {@code maxReconsumeTimes} 后，消息被投进 {@code %DLQ%原消费组名}；</li>
 *   <li>DLQ Topic 由 broker 自动创建，不手动声明；</li>
 *   <li><b>监听 DLQ 必须用「新的」消费组</b>（本类 {@code file-dlq-consumer-group}），
 *       不能复用原组——否则位点会串。</li>
 * </ul>
 * <p>
 * <b>本类的兜底职责</b>：把任务收敛到失败终态（台账 + Redis 进度）+ 发告警，
 * 避免"消息进了 DLQ 但用户永远看到 RUNNING"。
 * <p>
 * <b>两条纪律</b>：
 * <ol>
 *   <li>onMessage 里绝不能向外抛异常（DLQ 消息消费失败会在本组再次重试、甚至二次死信，
 *       形成套娃）；这里用 try-catch 全兜住；</li>
 *   <li>处理必须幂等——markFailed 只允许 PENDING→FAILED，重复消费天然安全。</li>
 * </ol>
 * 解析阶段的 DLQ（{@code %DLQ%file-parse-consumer-group}）：本类只覆盖了向量化阶段；
 * 如需要，复制本类改 {@code topic = MqConst.TOPIC_DLQ_PARSE} 并换一个消费组名即可
 * （或双 Topic 拆两个小监听器，保持"一类一 Topic"的模式）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConst.TOPIC_DLQ_EMBED,
        consumerGroup = MqConst.GROUP_DLQ,
        selectorExpression = "*"
)
public class DlqConsumer implements RocketMQListener<FileParseMessage> {

    private final TaskProgressWriter progressWriter;
    private final FileParseTaskService fileParseTaskService;
    private final AlertService alertService;

    @Override
    public void onMessage(FileParseMessage message) {
        String taskId = message.getTaskId();
        String reason = "向量化阶段重试耗尽，进入死信队列";
        try {
            // 1) 进度 → FAILED（前端 SSE / 轮询立刻能看到终态）
            progressWriter.failed(taskId, reason);
            // 2) 台账 → FAILED（只允许 PENDING→FAILED，天然幂等）
            fileParseTaskService.markFailed(taskId, reason);
            // 3) 告警（always ERROR 日志 + 可选 webhook）
            alertService.alert("FILE_PARSE_DLQ",
                    "taskId=" + taskId + ", file=" + message.getFileName() + ", " + reason);
            log.warn("[DLQ兜底] 已收敛失败终态: taskId={}, file={}", taskId, message.getFileName());
        } catch (Exception e) {
            // 兜底消费者绝不允许向外抛：DLQ 的消费失败会再次重试/二次死信，套娃没完没了。
            // 这里只记日志（下一层兜底 = 运维看日志/面板）。
            log.error("[DLQ兜底] 处理死信时自身异常（已吞掉，避免套娃）: taskId={}", taskId, e);
        }
    }
}
