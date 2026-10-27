package org.albedo.vllmpt.module.approval.core.spi;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 决策插件注册表：按 ai_policy_key 查找。
 */
@Slf4j
@Component
public class ApprovalAiDecisionRegistry {

    private final Map<String, ApprovalAiDecisionProvider> providers;

    public ApprovalAiDecisionRegistry(List<ApprovalAiDecisionProvider> all) {
        Map<String, ApprovalAiDecisionProvider> map = new LinkedHashMap<>();
        for (ApprovalAiDecisionProvider provider : all) {
            String key = normalize(provider.policyKey());
            if (StrUtil.isBlank(key)) {
                throw new IllegalStateException("AI 决策插件 policyKey 不能为空: "
                        + provider.getClass().getName());
            }
            ApprovalAiDecisionProvider previous = map.putIfAbsent(key, provider);
            if (previous != null) {
                throw new IllegalStateException("AI 决策插件重复注册: policyKey=" + key
                        + ", " + previous.getClass().getName() + " / " + provider.getClass().getName());
            }
            log.info("[审批] 注册 AI 决策插件: policyKey={}, provider={}",
                    key, provider.getClass().getSimpleName());
        }
        this.providers = Map.copyOf(map);
    }

    /**
     * @return 未注册返回 null
     */
    public ApprovalAiDecisionProvider find(String policyKey) {
        if (StrUtil.isBlank(policyKey)) {
            return null;
        }
        return providers.get(normalize(policyKey));
    }

    private String normalize(String key) {
        return key == null ? null : key.trim().toUpperCase(Locale.ROOT);
    }
}
