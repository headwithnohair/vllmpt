package org.albedo.vllmpt.module.approval.core.spi;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务插件上下文。
 * <p>
 * 提交阶段 {@code instance} 为 null（实例尚未创建），事实由通用层预先合并；
 * 结果回调阶段 {@code instance} 为最终状态的实例快照。
 *
 * @param applicantId 申请人
 * @param bizType     业务类型
 * @param instance    审批实例，提交阶段为 null
 * @param facts       扁平事实（表单 + 显式 facts + applicantId + bizType）
 */
public record ApprovalContext(Long applicantId,
                              String bizType,
                              ApprovalInstance instance,
                              Map<String, Object> facts) {

    public ApprovalContext {
        facts = facts == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }
}
