package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.dto.InstanceDetailVO;
import org.albedo.vllmpt.module.approval.core.domain.dto.ResubmitInstanceRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.SubmitInstanceRequest;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalCc;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalRecord;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTask;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAction;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalInstanceStatus;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalOperatorType;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalTaskStatus;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalTemplateStatus;
import org.albedo.vllmpt.module.approval.core.engine.ApprovalEngine;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalCcMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalInstanceMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalRecordMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTaskMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批实例服务：提交、详情、我的申请、撤回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalInstanceService {

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalCcMapper ccMapper;
    private final ApprovalTemplateService templateService;
    private final ApprovalEngine engine;
    private final FormSchemaValidator formSchemaValidator;
    private final ObjectMapper objectMapper;
    private final ApprovalNumberGenerator numberGenerator;
    private final ApprovalIdempotencyGuard idempotencyGuard;

    /**
     * 提交审批：创建实例、写提交流水、启动流程，全部在同一事务内。
     * <p>
     * 带 idempotencyKey 时，5 分钟内重复提交返回首次创建的实例，避免重复建单。
     */
    @Transactional
    public Long submit(SubmitInstanceRequest request) {
        Long userId = UserContextHolder.requireUserId();
        String idempotencyKey = request.getIdempotencyKey();
        Long existing = idempotencyGuard.acquire(userId, idempotencyKey);
        if (existing != null) {
            log.info("[审批] 命中提交幂等: userId={}, instanceId={}", userId, existing);
            return existing;
        }

        try {
            ApprovalTemplate template = resolveTemplate(request);
            Map<String, Object> form = toMap(request.getFormData());
            formSchemaValidator.validate(template.getFormSchema(), form);
            Map<String, Object> facts = buildFacts(form, request.getFacts(), template.getBizType(), userId);

            Long instanceId = createAndStart(template, form, facts,
                    StrUtil.blankToDefault(request.getTitle(), template.getName()),
                    null, 1, ApprovalAction.SUBMIT, null, userId);
            idempotencyGuard.complete(userId, idempotencyKey, instanceId);
            registerRollbackRelease(userId, idempotencyKey);
            return instanceId;
        } catch (RuntimeException e) {
            idempotencyGuard.release(userId, idempotencyKey);
            throw e;
        }
    }

    /**
     * 驳回后重新提交：沿用原实例的模板与版本创建新实例，parent_instance_id 指向旧实例。
     */
    @Transactional
    public Long resubmit(Long instanceId, ResubmitInstanceRequest request) {
        Long userId = UserContextHolder.requireUserId();
        ApprovalInstance parent = requireInstance(instanceId);
        if (!userId.equals(parent.getApplicantId())) {
            throw new BusinessException(403, "只有申请人可以重新提交");
        }
        if (!ApprovalInstanceStatus.REJECTED.name().equals(parent.getStatus())) {
            throw new BusinessException(400, "只有已驳回的审批可以重新提交");
        }

        String idempotencyKey = request.getIdempotencyKey();
        Long existing = idempotencyGuard.acquire(userId, idempotencyKey);
        if (existing != null) {
            log.info("[审批] 命中重新提交幂等: userId={}, instanceId={}", userId, existing);
            return existing;
        }

        try {
            ApprovalTemplate template = templateMapper.selectById(parent.getTemplateId());
            if (template == null) {
                throw new BusinessException(404, "原审批模板不存在，无法重新提交");
            }
            if (!ApprovalTemplateStatus.PUBLISHED.name().equals(template.getStatus())) {
                throw new BusinessException(400, "模板已下线，无法重新提交");
            }

            Map<String, Object> form = request.getFormData() == null
                    ? parseForm(parent.getFormData())
                    : toMap(request.getFormData());
            formSchemaValidator.validate(template.getFormSchema(), form);
            Map<String, Object> facts = buildFacts(form, request.getFacts(), template.getBizType(), userId);

            int submitCount = parent.getSubmitCount() == null ? 2 : parent.getSubmitCount() + 1;
            Long newInstanceId = createAndStart(template, form, facts,
                    StrUtil.blankToDefault(request.getTitle(), parent.getTitle()),
                    parent.getId(), submitCount, ApprovalAction.RESUBMIT,
                    "重新提交，原实例 " + parent.getInstanceNo(), userId);
            idempotencyGuard.complete(userId, idempotencyKey, newInstanceId);
            registerRollbackRelease(userId, idempotencyKey);
            log.info("[审批] 重新提交: parent={}, new={}, applicant={}",
                    parent.getInstanceNo(), newInstanceId, userId);
            return newInstanceId;
        } catch (RuntimeException e) {
            idempotencyGuard.release(userId, idempotencyKey);
            throw e;
        }
    }

    private Long createAndStart(ApprovalTemplate template, Map<String, Object> form,
                                Map<String, Object> facts, String title, Long parentInstanceId,
                                int submitCount, ApprovalAction action, String comment, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        ApprovalInstance instance = new ApprovalInstance();
        instance.setInstanceNo(numberGenerator.next(template.getBizType()));
        instance.setBizType(template.getBizType());
        instance.setBizId(0L);
        instance.setTemplateId(template.getId());
        instance.setTemplateVersion(template.getVersion());
        instance.setApplicantId(userId);
        instance.setTitle(title);
        instance.setFormData(writeJson(form));
        instance.setFacts(writeJson(facts));
        instance.setStatus(ApprovalInstanceStatus.RUNNING.name());
        instance.setParentInstanceId(parentInstanceId);
        instance.setSubmitCount(submitCount);
        instance.setVersion(0);
        instance.setSubmittedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instanceMapper.insert(instance);

        saveRecord(instance, null, null, userId, ApprovalOperatorType.USER, action, comment);
        engine.start(instance.getId());
        log.info("[审批] 提交实例: instanceId={}, no={}, template={}, applicant={}, parent={}",
                instance.getId(), instance.getInstanceNo(), template.getTemplateKey(), userId, parentInstanceId);
        return instance.getId();
    }

    private Map<String, Object> buildFacts(Map<String, Object> form, Map<String, Object> extra,
                                           String bizType, Long userId) {
        Map<String, Object> facts = new LinkedHashMap<>(form);
        if (extra != null && !extra.isEmpty()) {
            facts.putAll(toMap(extra));
        }
        facts.put("applicantId", userId);
        facts.put("bizType", bizType);
        return facts;
    }

    public InstanceDetailVO detail(Long instanceId) {
        Long userId = UserContextHolder.requireUserId();
        ApprovalInstance instance = requireInstance(instanceId);
        assertVisible(instance, userId);

        InstanceDetailVO vo = new InstanceDetailVO();
        vo.setInstance(instance);
        ApprovalTemplate template = templateMapper.selectById(instance.getTemplateId());
        vo.setTemplateName(template == null ? null : template.getName());
        vo.setTasks(taskMapper.selectList(Wrappers.<ApprovalTask>lambdaQuery()
                .eq(ApprovalTask::getInstanceId, instanceId)
                .orderByAsc(ApprovalTask::getId)));
        vo.setRecords(recordMapper.selectList(Wrappers.<ApprovalRecord>lambdaQuery()
                .eq(ApprovalRecord::getInstanceId, instanceId)
                .orderByAsc(ApprovalRecord::getId)));
        vo.setCcs(ccMapper.selectList(Wrappers.<ApprovalCc>lambdaQuery()
                .eq(ApprovalCc::getInstanceId, instanceId)
                .orderByAsc(ApprovalCc::getId)));
        return vo;
    }

    public List<ApprovalInstance> my(String status, int limit) {
        Long userId = UserContextHolder.requireUserId();
        int size = Math.min(Math.max(limit, 1), 100);
        String statusValue = StrUtil.isBlank(status) ? null : status.trim().toUpperCase();
        return instanceMapper.selectList(Wrappers.<ApprovalInstance>lambdaQuery()
                .eq(ApprovalInstance::getApplicantId, userId)
                .eq(statusValue != null, ApprovalInstance::getStatus, statusValue)
                .orderByDesc(ApprovalInstance::getId)
                .last("LIMIT " + size));
    }

    /**
     * 撤回：只允许申请人撤回运行中的实例。
     */
    @Transactional
    public void cancel(Long instanceId, String reason) {
        Long userId = UserContextHolder.requireUserId();
        ApprovalInstance instance = requireInstance(instanceId);
        if (!userId.equals(instance.getApplicantId())) {
            throw new BusinessException(403, "只有申请人可以撤回");
        }
        if (!ApprovalInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            throw new BusinessException(400, "只有运行中的审批可以撤回");
        }

        int version = instance.getVersion() == null ? 0 : instance.getVersion();
        LocalDateTime now = LocalDateTime.now();
        int rows = instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, instanceId)
                .eq(ApprovalInstance::getVersion, version)
                .eq(ApprovalInstance::getStatus, ApprovalInstanceStatus.RUNNING.name())
                .set(ApprovalInstance::getStatus, ApprovalInstanceStatus.CANCELED.name())
                .set(ApprovalInstance::getFinishedAt, now)
                .set(ApprovalInstance::getVersion, version + 1)
                .set(ApprovalInstance::getUpdatedAt, now));
        if (rows == 0) {
            throw new BusinessException(409, "审批状态已变化，请刷新后重试");
        }

        taskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instanceId)
                .in(ApprovalTask::getStatus,
                        ApprovalTaskStatus.PENDING.name(), ApprovalTaskStatus.WAITING.name())
                .set(ApprovalTask::getStatus, ApprovalTaskStatus.CANCELED.name())
                .set(ApprovalTask::getUpdatedAt, now));

        saveRecord(instance, instance.getCurrentNodeKey(), null, userId,
                ApprovalOperatorType.USER, ApprovalAction.WITHDRAW, reason);
        log.info("[审批] 撤回实例: instanceId={}, applicant={}", instanceId, userId);
    }

    private ApprovalTemplate resolveTemplate(SubmitInstanceRequest request) {
        ApprovalTemplate template;
        if (StrUtil.isNotBlank(request.getTemplateKey())) {
            template = templateService.findPublishedByKey(request.getTemplateKey());
        } else if (StrUtil.isNotBlank(request.getBizType())) {
            template = templateService.findPublishedByBizType(request.getBizType());
        } else {
            throw new BusinessException(400, "templateKey 与 bizType 不能同时为空");
        }
        if (template == null) {
            throw new BusinessException(404, "未找到已发布的审批模板");
        }
        return template;
    }

    private ApprovalInstance requireInstance(Long instanceId) {
        ApprovalInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(404, "审批实例不存在: " + instanceId);
        }
        return instance;
    }

    private void assertVisible(ApprovalInstance instance, Long userId) {
        if (userId.equals(instance.getApplicantId())) {
            return;
        }
        Long taskCount = taskMapper.selectCount(Wrappers.<ApprovalTask>lambdaQuery()
                .eq(ApprovalTask::getInstanceId, instance.getId())
                .eq(ApprovalTask::getAssigneeId, userId));
        if (taskCount != null && taskCount > 0) {
            return;
        }
        Long ccCount = ccMapper.selectCount(Wrappers.<ApprovalCc>lambdaQuery()
                .eq(ApprovalCc::getInstanceId, instance.getId())
                .eq(ApprovalCc::getReceiverId, userId));
        if (ccCount != null && ccCount > 0) {
            return;
        }
        throw new BusinessException(403, "无权查看该审批");
    }

    private void saveRecord(ApprovalInstance instance, String nodeKey, Long taskId, Long operatorId,
                            ApprovalOperatorType operatorType, ApprovalAction action, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setNodeKey(nodeKey);
        record.setTaskId(taskId);
        record.setOperatorId(operatorId);
        record.setOperatorType(operatorType.name());
        record.setAction(action.name());
        record.setComment(comment);
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private Map<String, Object> toMap(Map<String, Object> map) {
        if (map == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(map);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "审批数据序列化失败");
        }
    }

    private Map<String, Object> parseForm(String formJson) {
        if (StrUtil.isBlank(formJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(formJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "审批表单快照解析失败");
        }
    }

    /**
     * 事务回滚时释放幂等占位，避免失败请求阻塞用户 5 分钟。
     */
    private void registerRollbackRelease(Long userId, String idempotencyKey) {
        if (StrUtil.isBlank(idempotencyKey) || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    idempotencyGuard.release(userId, idempotencyKey);
                }
            }
        });
    }
}
