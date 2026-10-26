package org.albedo.vllmpt.module.approval.ai;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.dto.ApprovalDraftRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.ApprovalDraftVO;
import org.albedo.vllmpt.module.approval.core.domain.dto.InstanceDetailVO;
import org.albedo.vllmpt.module.approval.core.domain.dto.SubmitInstanceRequest;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.albedo.vllmpt.module.approval.core.service.ApprovalDraftService;
import org.albedo.vllmpt.module.approval.core.service.ApprovalInstanceService;
import org.albedo.vllmpt.module.approval.core.service.ApprovalTemplateService;
import org.albedo.vllmpt.module.chat.tool.AiTool;
import org.albedo.vllmpt.module.chat.tool.AiToolProvider;
import org.albedo.vllmpt.module.chat.tool.ToolContext;
import org.albedo.vllmpt.module.chat.tool.ToolContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批 AI 填表工具。
 * <p>
 * 只暴露在 {@code approval} 工具组；userId / sessionId / bizType 由
 * {@link ToolContextHolder} 注入，不接受模型传参，避免越权与跨会话操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@AiTool(groups = {"approval"})
public class ApprovalToolProvider implements AiToolProvider {

    private final ApprovalDraftService draftService;
    private final ApprovalInstanceService instanceService;
    private final ApprovalTemplateService templateService;
    private final ObjectMapper objectMapper;

    @Tool("""
            保存或更新当前审批草稿。formJson 必须是 JSON 对象字符串，只包含用户已经明确提供的字段。
            不要猜测日期、金额、类型；服务端会计算缺失字段并在返回中给出 missingFields。
            """)
    public String saveApprovalDraft(
            @P("表单 JSON 对象字符串，例如 {\"leaveType\":\"ANNUAL\",\"startTime\":\"2026-10-27 09:00\"}")
            String formJson) {
        ToolContext ctx = ToolContextHolder.require();
        Long userId = UserContextHolder.requireUserId();
        String bizType = requireBizType(ctx);
        Map<String, Object> form = parseForm(formJson);

        ApprovalDraftRequest request = new ApprovalDraftRequest();
        request.setBizType(bizType);
        request.setSessionId(ctx.sessionId());
        request.setFormData(form);
        ApprovalDraftVO draft = draftService.save(userId, request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("bizType", draft.getBizType());
        result.put("formData", draft.getFormData());
        result.put("missingFields", draft.getMissingFields());
        result.put("message", draft.getMissingFields().isEmpty()
                ? "字段已齐全，请向用户展示摘要并等待明确确认后再调用 submitApprovalDraft"
                : "请向用户追问缺失字段：" + String.join("、", draft.getMissingFields()));
        return writeJson(result);
    }

    @Tool("""
            提交已确认的审批草稿。只有用户明确说“确认提交/可以/提交”后才能调用。
            提交前会校验草稿完整性；成功返回单号与当前节点。
            """)
    public String submitApprovalDraft() {
        ToolContext ctx = ToolContextHolder.require();
        Long userId = UserContextHolder.requireUserId();
        String bizType = requireBizType(ctx);
        String sessionId = StrUtil.blankToDefault(ctx.sessionId(), "default");

        ApprovalDraftVO draft = draftService.get(userId, bizType, sessionId);
        if (draft == null) {
            throw new BusinessException(400, "没有可提交的草稿，请先调用 saveApprovalDraft");
        }
        if (draft.getMissingFields() != null && !draft.getMissingFields().isEmpty()) {
            throw new BusinessException(400, "草稿字段不完整，缺少：" + String.join("、", draft.getMissingFields()));
        }
        ApprovalTemplate template = templateService.findPublishedByBizType(bizType);
        if (template == null) {
            throw new BusinessException(404, "未找到已发布的审批模板: " + bizType);
        }

        SubmitInstanceRequest request = new SubmitInstanceRequest();
        request.setBizType(bizType);
        request.setTitle(template.getName());
        request.setFormData(draft.getFormData());
        request.setIdempotencyKey(idempotencyKey(userId, bizType, sessionId, draft.getUpdatedAt()));

        Long instanceId = instanceService.submit(request);
        draftService.delete(userId, bizType, sessionId);

        InstanceDetailVO detail = instanceService.detail(instanceId);
        ApprovalInstance instance = detail.getInstance();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submitted", true);
        result.put("instanceId", instanceId);
        result.put("instanceNo", instance == null ? null : instance.getInstanceNo());
        result.put("status", instance == null ? null : instance.getStatus());
        result.put("currentNodeName", instance == null ? null : instance.getCurrentNodeName());
        result.put("message", "审批已提交，请告知用户单号与当前节点");
        log.info("[审批AI] 提交草稿: userId={}, bizType={}, instanceId={}", userId, bizType, instanceId);
        return writeJson(result);
    }

    @Tool("""
            查询我的审批记录。可按状态过滤：RUNNING/APPROVED/REJECTED/CANCELED；
            不传状态返回最近记录。
            """)
    public String queryMyApprovals(
            @P(value = "审批状态，可选", required = false) String status) {
        ToolContext ctx = ToolContextHolder.require();
        String bizType = requireBizType(ctx);
        List<ApprovalInstance> instances = instanceService.my(bizType, status, 10);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ApprovalInstance instance : instances) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("instanceId", instance.getId());
            item.put("instanceNo", instance.getInstanceNo());
            item.put("title", instance.getTitle());
            item.put("status", instance.getStatus());
            item.put("currentNodeName", instance.getCurrentNodeName());
            item.put("submittedAt", instance.getSubmittedAt());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("items", items);
        return writeJson(result);
    }

    @Tool("撤回我的审批申请。只有申请人本人、且实例处于 RUNNING 状态时才能撤回。")
    public String cancelApproval(
            @P("审批实例 ID") Long instanceId) {
        ToolContextHolder.require();
        Long userId = UserContextHolder.requireUserId();
        instanceService.cancel(instanceId, "AI 对话撤回");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canceled", true);
        result.put("instanceId", instanceId);
        result.put("message", "审批已撤回，假期余额等资源已释放");
        log.info("[审批AI] 撤回: userId={}, instanceId={}", userId, instanceId);
        return writeJson(result);
    }

    private String requireBizType(ToolContext ctx) {
        if (StrUtil.isBlank(ctx.bizType())) {
            throw new BusinessException(400, "当前对话未指定审批业务类型，请从审批入口发起");
        }
        return ctx.bizType().trim().toUpperCase();
    }

    private Map<String, Object> parseForm(String formJson) {
        if (StrUtil.isBlank(formJson)) {
            throw new BusinessException(400, "formJson 不能为空");
        }
        try {
            Map<String, Object> form = objectMapper.readValue(formJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return form == null ? Map.of() : form;
        } catch (Exception e) {
            throw new BusinessException(400, "formJson 必须是合法的 JSON 对象");
        }
    }

    private String idempotencyKey(Long userId, String bizType, String sessionId, LocalDateTime updatedAt) {
        long version = updatedAt == null
                ? System.currentTimeMillis()
                : updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "ai-draft:" + userId + ":" + bizType + ":" + sessionId + ":" + version;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "审批工具返回序列化失败");
        }
    }
}
