package org.albedo.vllmpt.module.approval.core.engine.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplateNode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalNodeType;
import org.albedo.vllmpt.module.approval.core.domain.model.AssigneeSpec;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeHandleContext;
import org.albedo.vllmpt.module.approval.core.domain.model.NodeResult;
import org.albedo.vllmpt.module.approval.core.engine.NodeHandler;
import org.albedo.vllmpt.module.approval.core.engine.assignee.AssigneeResolverRegistry;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalCcMapper;
import org.albedo.vllmpt.module.approval.core.service.ApprovalNotifyService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 抄送节点：写入抄送记录后立即继续，不阻塞流程。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CcNodeHandler implements NodeHandler {

    /** P0 只落站内信；钉钉/飞书/邮件在通知发件箱接入后扩展 */
    private static final String DEFAULT_CHANNEL = "INBOX";

    private final AssigneeResolverRegistry resolverRegistry;
    private final ApprovalCcMapper ccMapper;
    private final ApprovalNotifyService notifyService;

    @Override
    public ApprovalNodeType supportType() {
        return ApprovalNodeType.CC;
    }

    @Override
    public NodeResult handle(NodeHandleContext ctx, ApprovalTemplateNode node) {
        List<AssigneeSpec> specs = resolverRegistry.parseCcSpecs(node.getCcConfig());
        if (specs.isEmpty()) {
            log.warn("[审批] 抄送节点未配置接收人，直接跳过: instanceId={}, node={}",
                    ctx.instance().getId(), node.getNodeKey());
            return NodeResult.pass();
        }

        LocalDateTime now = LocalDateTime.now();
        for (AssigneeSpec spec : specs) {
            List<Long> receivers = resolverRegistry.resolve(spec, ctx.instance(), ctx.facts());
            for (Long receiverId : receivers) {
                int rows = ccMapper.insertIgnore(ctx.instance().getId(), node.getNodeKey(),
                        receiverId, DEFAULT_CHANNEL, now);
                if (rows > 0) {
                    // 通知失败不影响审批主流程，Service 内部已吞掉异常
                    notifyService.sendCc(ctx.instance(), node, receiverId);
                }
            }
        }
        return NodeResult.pass();
    }
}
