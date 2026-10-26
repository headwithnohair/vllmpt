package org.albedo.vllmpt.module.approval.core.spi;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 业务插件注册表：按 bizType 查找 {@link ApprovalBizHandler}。
 * <p>
 * 未注册的业务类型返回 null，通用审批流程继续按无业务插件方式运行，
 * 便于历史模板与纯通用审批共存。
 */
@Slf4j
@Component
public class ApprovalBizRegistry {

    private final Map<String, ApprovalBizHandler> handlers;

    public ApprovalBizRegistry(List<ApprovalBizHandler> all) {
        Map<String, ApprovalBizHandler> map = new LinkedHashMap<>();
        for (ApprovalBizHandler handler : all) {
            String key = normalize(handler.bizType());
            if (StrUtil.isBlank(key)) {
                throw new IllegalStateException("审批业务插件 bizType 不能为空: " + handler.getClass().getName());
            }
            ApprovalBizHandler previous = map.putIfAbsent(key, handler);
            if (previous != null) {
                throw new IllegalStateException("审批业务插件重复注册: bizType=" + key
                        + ", " + previous.getClass().getName() + " / " + handler.getClass().getName());
            }
            log.info("[审批] 注册业务插件: bizType={}, handler={}", key, handler.getClass().getSimpleName());
        }
        this.handlers = Map.copyOf(map);
    }

    /**
     * @return 对应插件；未注册返回 null
     */
    public ApprovalBizHandler find(String bizType) {
        if (StrUtil.isBlank(bizType)) {
            return null;
        }
        return handlers.get(normalize(bizType));
    }

    public boolean contains(String bizType) {
        return find(bizType) != null;
    }

    private String normalize(String bizType) {
        return bizType == null ? null : bizType.trim().toUpperCase(Locale.ROOT);
    }
}
