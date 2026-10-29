package org.albedo.vllmpt.module.approval.core.engine.node;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.core.config.ApprovalAiProperties;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.engine.NodeHandler;
import org.albedo.vllmpt.module.approval.core.service.ApprovalAiDecisionService;
import org.albedo.vllmpt.module.approval.core.spi.AiDecision;
import org.albedo.vllmpt.module.approval.core.spi.AiDecisionContext;
import org.albedo.vllmpt.module.approval.core.spi.AiDecisionResult;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalAiDecisionProvider;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalAiDecisionRegistry;
import org.albedo.vllmpt.module.approval.core.spi.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 决策节点（P3）。
 * <p>
 * 路由规则：
 * <ul>
 *   <li>APPROVE → 跳 {@code aiOnApproveNodeKey}（为空则顺序下一个）；</li>
 *   <li>ESCALATE → 跳 {@code aiOnEscalateNodeKey}，未配置则直接失败（绝不自动通过）；</li>
 *   <li>REJECT → {@code aiOnRejectAction=REJECT} 时拒绝实例；{@code NEXT_NODE} 时继续下一个节点。</li>
 * </ul>
 * 任何插件缺失、异常、低置信度、高风险都会转人工；每次决策写审计快照与 AI 流水。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiDecisionNodeHandler implements NodeHandler {

    private final ApprovalAiDecisionRegistry providerRegistry;
    private final ApprovalAiDecisionService decisionService;
    private final ApprovalAiProperties properties;

    @Override
    public ApprovalNodeType supportType() {
        return ApprovalNodeType.AI_DECISION;
    }

    @Override
    public NodeResult handle(NodeHandleContext ctx, ApprovalTemplateNode node) {
        long startedAt = System.currentTimeMillis();
        double minConfidence = node.getAiMinConfidence() == null
                ? properties.getDefaultMinConfidence()
                : node.getAiMinConfidence().doubleValue();

        AiDecisionResult result = decide(ctx, node, minConfidence);
        if (result.decision() == AiDecision.APPROVE
                && (result.riskLevel() == RiskLevel.HIGH || result.confidence() < minConfidence)) {
            result = result.toEscalate("置信度或风险等级不满足自动通过条件");
        }

        if (result.decision() == AiDecision.ESCALATE
                && StrUtil.isBlank(node.getAiOnEscalateNodeKey())) {
            throw new BusinessException(500,
                    "AI 决策节点未配置转人工节点，已阻止自动通过: " + node.getNodeKey());
        }

        decisionService.save(ctx.instance(), node, result, System.currentTimeMillis() - startedAt);

        log.info("[审批AI] decision={}, instanceId={}, node={}, policy={}, confidence={}, reason={}",
                result.decision(), ctx.instance().getId(), node.getNodeKey(),
                node.getAiPolicyKey(), result.confidence(), result.reason());
        return route(node, result);
    }

    private AiDecisionResult decide(NodeHandleContext ctx, ApprovalTemplateNode node, double minConfidence) {
        if (!properties.isEnabled()) {
            return AiDecisionResult.escalate(0.0, RiskLevel.HIGH,
                    "AI 决策未启用，转人工", List.of(), Map.of(), null, "disabled");
        }
        if (!properties.allows(ctx.instance().getId())) {
            return AiDecisionResult.escalate(0.0, RiskLevel.HIGH,
                    "AI 决策未命中灰度，转人工", List.of(), Map.of(), null, "gray");
        }
        String policyKey = node.getAiPolicyKey();
        if (StrUtil.isBlank(policyKey)) {
            return AiDecisionResult.escalate(0.0, RiskLevel.HIGH,
                    "AI 决策节点未配置 aiPolicyKey", List.of(), Map.of(), null, "unknown");
        }
        ApprovalAiDecisionProvider provider = providerRegistry.find(policyKey);
        if (provider == null) {
            return AiDecisionResult.escalate(0.0, RiskLevel.HIGH,
                    "AI 决策策略未注册: " + policyKey, List.of(), Map.of(), null, "unknown");
        }
        try {
            AiDecisionResult result = provider.decide(new AiDecisionContext(
                    ctx.instance(), node, ctx.facts(), minConfidence));
            return result == null
                    ? AiDecisionResult.escalate(0.0, RiskLevel.HIGH,
                    "AI 决策策略返回空结果", List.of(), Map.of(), null, provider.policyVersion())
                    : result;
        } catch (Exception e) {
            log.error("[审批AI] 策略执行异常，转人工: instanceId={}, policy={}",
                    ctx.instance().getId(), policyKey, e);
            return AiDecisionResult.escalate(0.0, RiskLevel.HIGH,
                    "AI 决策异常，已转人工：" + e.getMessage(),
                    List.of(), Map.of(), null, provider.policyVersion());
        }
    }

    private NodeResult route(ApprovalTemplateNode node, AiDecisionResult result) {
        return switch (result.decision()) {
            case APPROVE -> passToOrNext(node.getAiOnApproveNodeKey());
            case ESCALATE -> NodeResult.passTo(node.getAiOnEscalateNodeKey());
            case REJECT -> "NEXT_NODE".equalsIgnoreCase(node.getAiOnRejectAction())
                    ? NodeResult.pass()
                    : NodeResult.reject(result.reason());
        };
    }

    private NodeResult passToOrNext(String nodeKey) {
        return StrUtil.isBlank(nodeKey) ? NodeResult.pass() : NodeResult.passTo(nodeKey);
    }
}
