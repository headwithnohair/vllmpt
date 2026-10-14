package org.albedo.vllmpt.module.file.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.alert.AlertService;
import org.albedo.vllmpt.module.file.config.FileTaskProperties;
import org.albedo.vllmpt.module.file.service.FileParseTaskService;
import org.albedo.vllmpt.module.file.service.TaskProgressWriter;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 超时检查消费者【骨架 · 留给你实现】——消费「延迟消息」，判定卡死的任务。
 * <p>
 * <b>延迟消息方案（考点）</b>：submit 接口发送解析消息的同时，往 {@code FILE_TIMEOUT_TOPIC}
 * 发了一条「延迟 1 小时」的消息（消息头 {@code PROPERTY_DELAY_TIME_LEVEL}，见 FileParseProducer）。
 * 1 小时后本消费者收到消息，再比对 Redis 进度的 {@code updatedAt}：
 * <ul>
 *   <li>任务早已成功/失败 → 什么都不做（延迟消息只是"探针"，谁都会收到）；</li>
 *   <li>任务还在 RUNNING 但 {@code updatedAt} 距今超过阈值 → 判定卡死，标失败 + 告警。</li>
 * </ul>
 * 为什么这样做：RocketMQ 延迟级别只有固定 18 档（30m/1h/2h…），做不到"恰好 30 分钟后触发"，
 * 所以工程上普遍是「粗粒度定时 + 精确比对时间戳」的组合拳。
 * <p>
 * <b>调试提示</b>：验证超时链路时不必等 1 小时——把 {@link FileParseProducer#sendTimeoutCheck}
 * 里的延迟级别临时改成 {@code MqConst.DELAY_LEVEL_30M}（30 分钟）甚至 level 5（1 分钟），
 * 同时把 {@code app.file-task.stage-timeout-seconds} 调小（如 30 秒）即可快速演示。
 * <p>
 * 提醒：本类收到消息后 {@code log} 已经写好了——先直接启动应用，观察日志里 1 小时后
 * （或调小级别后）确实有本消息到达、消费组在 Dashboard 里可见，再动手写判定逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConst.TOPIC_FILE_TIMEOUT,
        consumerGroup = MqConst.GROUP_TIMEOUT,
        selectorExpression = "*"
)
public class TimeoutCheckConsumer implements RocketMQListener<FileParseMessage> {

    private final TaskProgressWriter progressWriter;
    private final FileParseTaskService fileParseTaskService;
    private final FileTaskProperties fileTaskProperties;
    private final AlertService alertService;

    @Override
    public void onMessage(FileParseMessage message) {
        String taskId = message.getTaskId();
        log.info("[超时检查] 收到延迟消息: taskId={}, file={}（TODO 未实现）", taskId, message.getFileName());

        // ==================== TODO（留给你，按编号补全） ====================
        //
        // TODO(1) 读进度快照：
        //         Map<String, String> snapshot = progressWriter.snapshot(taskId);
        //         若 snapshot 为空 → 任务记录已被 TTL 清理（或从未落库），直接 return；
        //
        // TODO(2) 终态豁免：status 是 "SUCCESS" / "FAILED" → 任务已结束，直接 return；
        //
        // TODO(3) 超时判定：
        //         long updatedAt = Long.parseLong(snapshot.get("updatedAt"));
        //         long timeoutMillis = fileTaskProperties.getStageTimeoutSeconds() * 1000L;
        //         if (System.currentTimeMillis() - updatedAt > timeoutMillis) {
        //             // 判定卡死 → 三连：进度 failed + 台账 markFailed + 告警（scene=FILE_PARSE_TIMEOUT）
        //             // 注意 message 内容带上"卡在阶段 stage、进度 percent"便于排查
        //         }
        //         // 未超时 → 什么都不做（任务还在正常推进）
        //
        // TODO(4)（可选进阶）：未超时且仍在 RUNNING 时，再补发一条延迟消息继续观察，
        //         实现"滚动探针"；但要注意终止条件，别把消息无限续下去。
        // =================================================================
    }
}
