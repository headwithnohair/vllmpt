package org.albedo.vllmpt.common.alert;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 告警服务（场景6：DLQ / 超时任务触发）。
 * <p>
 * 行为：先打 ERROR 日志（永远执行），再按配置决定是否推送 webhook：
 * <ul>
 *   <li>{@code app.alert.webhook-url} 为空或 enabled=false：只打日志，适合本地开发；</li>
 *   <li>配置了 webhook：同步 POST（5s 超时），支持钉钉（DINGTALK）与飞书（FEISHU）文本消息格式。</li>
 * </ul>
 * 注意：告警发送失败只打 warn 日志、绝不向上抛异常——它一定被调在 DLQ 消费、
 * 失败补偿这类本身就是"错误路径"的代码里，不能雪上加霜。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertProperties props;

    /**
     * 发送告警。
     *
     * @param scene   场景标识（如 FILE_PARSE_DLQ、FILE_PARSE_TIMEOUT），便于群里筛选
     * @param content 告警正文（建议带 taskId / 文件名 / 失败原因）
     */
    public void alert(String scene, String content) {
        log.error("【告警】scene={} | {}", scene, content);
        sendWebhook(scene, content);
    }

    private void sendWebhook(String scene, String content) {
        if (!props.isEnabled() || StrUtil.isBlank(props.getWebhookUrl())) {
            return;
        }
        try {
            String text = "[vllmpt][" + scene + "] " + content;
            String body;
            if ("FEISHU".equalsIgnoreCase(props.getFormat())) {
                // 飞书自定义机器人：{"msg_type":"text","content":{"text":"..."}}
                body = JSONUtil.toJsonStr(Map.of(
                        "msg_type", "text",
                        "content", Map.of("text", text)));
            } else {
                // 钉钉自定义机器人：{"msgtype":"text","text":{"content":"..."}}
                body = JSONUtil.toJsonStr(Map.of(
                        "msgtype", "text",
                        "text", Map.of("content", text)));
            }
            try (HttpResponse resp = HttpRequest.post(props.getWebhookUrl())
                    .timeout(5000)
                    .body(body)
                    .execute()) {
                if (!resp.isOk()) {
                    log.warn("告警 webhook 响应异常: status={}, body={}", resp.getStatus(), resp.body());
                }
            }
        } catch (Exception e) {
            log.warn("告警 webhook 发送失败: scene={}", scene, e);
        }
    }
}
