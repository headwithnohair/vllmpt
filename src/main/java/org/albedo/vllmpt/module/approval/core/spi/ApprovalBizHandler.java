package org.albedo.vllmpt.module.approval.core.spi;

import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalInstanceStatus;

import java.util.Map;

/**
 * 审批业务插件。
 * <p>
 * 核心引擎只认识本接口，不认识请假、差旅费等具体业务。
 * 新增业务只需实现本接口并注册为 Spring Bean，无需修改引擎与通用接口。
 * <p>
 * 调用顺序：
 * <pre>
 * validate → createBizRecord → 创建审批实例 → bindInstance → engine.start
 * 审批结束（通过/拒绝）→ applyResult（事务内）→ onApproved / onRejected
 * 撤回 → applyResult(CANCELED) → onCanceled
 * </pre>
 */
public interface ApprovalBizHandler {

    /**
     * 业务类型，与 {@code approval_template.biz_type} 一致（大写）。
     */
    String bizType();

    /**
     * 业务校验：表单 + 当前用户 + 业务数据。
     * <p>
     * 通用层已完成表单 Schema 校验，这里只做业务规则（余额、时间冲突、证明材料等）。
     */
    ValidationResult validate(ApprovalForm form, ApprovalContext ctx);

    /**
     * 创建业务记录，返回业务表主键。
     * <p>
     * 必须在审批实例创建之前调用，与实例创建处于同一事务。
     */
    Long createBizRecord(ApprovalForm form, ApprovalContext ctx);

    /**
     * 回填审批实例 ID，保证业务表与实例双向关联。
     */
    default void bindInstance(Long bizId, Long instanceId) {
    }

    /**
     * 提取扁平事实，供条件节点与 AI 决策使用。
     */
    default Map<String, Object> extractFacts(ApprovalForm form, ApprovalContext ctx) {
        return Map.of();
    }

    /**
     * 业务详情，供通用详情接口/业务接口返回。
     */
    Object loadBizDetail(ApprovalInstance instance);

    /**
     * 审批结果回调，在审批事务内执行。
     * <p>
     * 余额扣减、房间占用等关键动作必须幂等，失败将回滚整个审批事务。
     */
    default void applyResult(ApprovalInstance instance, ApprovalInstanceStatus result) {
    }

    /**
     * 生命周期回调：事务内同步调用，实现方需自行保证异常不影响审批主流程。
     */
    default void onSubmitted(ApprovalInstance instance) {
    }

    default void onApproved(ApprovalInstance instance) {
    }

    default void onRejected(ApprovalInstance instance) {
    }

    default void onCanceled(ApprovalInstance instance) {
    }
}
