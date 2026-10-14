package org.albedo.vllmpt.module.file.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 文件解析流水线生产者（场景6）：统一收口三个 Topic 的发送逻辑。
 * <p>
 * <b>考点：发送可靠性</b>——send 方法与 broker 的契约：
 * <ul>
 *   <li>{@code syncSend}：同步等待 broker 返回 {@link SendResult}，<b>必须校验</b>
 *       {@link SendStatus}——只有 {@code SEND_OK} 才算投递成功；
 *       {@code FLUSH_DISK_TIMEOUT / FLUSH_SLAVE_TIMEOUT / SLAVE_NOT_AVAILABLE}
 *       分别代表刷盘/主从复制异常（取决于 broker.conf 的 flushDiskType / brokerRole）。</li>
 *   <li>网络级失败（超时、连接断开）会抛异常；rocketmq-spring 会按配置的
 *       {@code retry-times-when-send-failed} 自动重试（application-dev.yml 已配 2 次）。</li>
 *   <li>业务上：发送失败必须让调用方感知——submit 接口据此把台账标记失败，不允许"悄悄丢消息"。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileParseProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送「解析阶段」消息（submit 接口调用，级联起点）。
     * <p>payload 为对象时，rocketmq-spring 用 Jackson 序列化成 JSON，消费端自动反序列化。
     */
    public void sendParseStage(FileParseMessage message) {
        sendJson(MqConst.TOPIC_FILE_PARSE, message);
    }

    /**
     * 发送「向量化阶段」消息（解析消费者完成后调用）。
     */
    public void sendEmbedStage(FileParseMessage message) {
        sendJson(MqConst.TOPIC_FILE_EMBED, message);
    }

    /**
     * 发送「超时检查」延迟消息（submit 时一并发送，与解析消息同生命周期）。
     * <p>
     * <b>考点：延迟消息</b>——通过消息头 {@code PROPERTY_DELAY_TIME_LEVEL} 指定延迟级别，
     * broker 会在延迟后把消息投递给 {@code FILE_TIMEOUT_TOPIC} 的消费者。
     * 注意开源版 RocketMQ 只支持 18 个固定级别（1s~2h），不能任意秒数；
     * 这里选 1 小时：到点后由消费者比对 Redis 的 updatedAt 判断是否"真的超时",
     * （详见 TimeoutCheckConsumer 的 TODO）。
     */
    public void sendTimeoutCheck(FileParseMessage message) {
        Message<FileParseMessage> delayed = MessageBuilder.withPayload(message)
                .setHeader(MessageConst.PROPERTY_DELAY_TIME_LEVEL, MqConst.DELAY_LEVEL_1H)
                .build();
        try {
            SendResult result = rocketMQTemplate.syncSend(MqConst.TOPIC_FILE_TIMEOUT, delayed);
            checkSendResult(MqConst.TOPIC_FILE_TIMEOUT, result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "延迟消息发送异常: " + e.getMessage());
        }
    }

    // ==================== 私有工具 ====================

    /** 发送普通 JSON 消息并校验发送状态。 */
    private void sendJson(String topic, FileParseMessage message) {
        try {
            SendResult result = rocketMQTemplate.syncSend(topic, message);
            checkSendResult(topic, result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 网络/序列化等异常统一转业务异常，让 submit 接口能感知并回滚台账
            throw new BusinessException(500, "消息发送异常: topic=" + topic + ", " + e.getMessage());
        }
    }

    /**
     * 校验 broker 返回的发送状态（发送可靠性的落点：不校验等于裸奔）。
     */
    private void checkSendResult(String topic, SendResult result) {
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            String status = result == null ? "null" : String.valueOf(result.getSendStatus());
            throw new BusinessException(500, "消息发送未确认: topic=" + topic + ", status=" + status);
        }
        log.info("消息发送成功: topic={}, msgId={}, queue={}", topic, result.getMsgId(), result.getMessageQueue());
    }
}
