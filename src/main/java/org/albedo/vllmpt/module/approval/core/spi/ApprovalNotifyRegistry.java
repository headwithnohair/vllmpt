package org.albedo.vllmpt.module.approval.core.spi;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 审批通知渠道注册表。
 */
@Slf4j
@Component
public class ApprovalNotifyRegistry {

    private final Map<String, ApprovalNotifyChannel> channels;

    public ApprovalNotifyRegistry(List<ApprovalNotifyChannel> all) {
        Map<String, ApprovalNotifyChannel> map = new LinkedHashMap<>();
        for (ApprovalNotifyChannel channel : all) {
            String key = normalize(channel.channel());
            if (StrUtil.isBlank(key)) {
                throw new IllegalStateException("通知渠道 channel 不能为空: "
                        + channel.getClass().getName());
            }
            ApprovalNotifyChannel previous = map.putIfAbsent(key, channel);
            if (previous != null) {
                throw new IllegalStateException("通知渠道重复注册: channel=" + key);
            }
            log.info("[审批] 注册通知渠道: channel={}, impl={}",
                    key, channel.getClass().getSimpleName());
        }
        this.channels = Map.copyOf(map);
    }

    public ApprovalNotifyChannel find(String channel) {
        return StrUtil.isBlank(channel) ? null : channels.get(normalize(channel));
    }

    private String normalize(String channel) {
        return channel == null ? null : channel.trim().toUpperCase(Locale.ROOT);
    }
}
