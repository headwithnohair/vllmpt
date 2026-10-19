package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.dto.InstanceDetailVO;
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
import org.albedo.vllmpt.module.approval.core.engine.ApprovalEngine;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalCcMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalInstanceMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalRecordMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTaskMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter INSTANCE_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalCcMapper ccMapper;
    private final ApprovalTemplateService templateService;
    private final ApprovalEngine engine;
    private final FormSchemaValidator formSchemaValidator;
    private final ObjectMapper objectMapper;

    /**
     * 提交审批：创建实例、写提交流水、启动流程，全部在同一事务内。
     */
    @Transactional
    public Long submit(SubmitInstanceRequest request) {
        Long userId = UserContextHolder.requireUserId();
        ApprovalTemplate template = resolveTemplate(request);

        Map<String, Object> form = toMap(request.getFormData());
        formSchemaValidator.validate(template.getFormSchema(), form);

        // 表单字段默认进入事实集；facts 显式覆盖后再补系统字段
        Map<String, Object> facts = new LinkedHashMap<>(form);
        if (request.getFacts() != null && !request.getFacts().isEmpty()) {
            facts.putAll(toMap(request.getFacts()));
        }
        facts.put("applicantId", userId);
        facts.put("bizType", template.getBizType());

        LocalDateTime now = LocalDateTime.now();
        ApprovalInstance instance = new ApprovalInstance();
        instance.setInstanceNo(generateInstanceNo());
        instance.setBizType(template.getBizType());
        instance.setBizId(0L);
        instance.setTemplateId(template.getId());
        instance.setTemplateVersion(template.getVersion());
        instance.setApplicantId(userId);
        instance.setTitle(StrUtil.blankToDefault(request.getTitle(), template.getName()));
        instance.setFormData(writeJson(form));
        instance.setFacts(writeJson(facts));
        instance.setStatus(ApprovalInstanceStatus.RUNNING.name());
        instance.setSubmitCount(1);
        instance.setVersion(0);
        instance.setSubmittedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instanceMapper.insert(instance);

        saveRecord(instance, null, null, userId, ApprovalOperatorType.USER, ApprovalAction.SUBMIT, null);
        engine.start(instance.getId());
        log.info("[审批] 提交实例: instanceId={}, no={}, template={}, applicant={}",
                instance.getId(), instance.getInstanceNo(), template.getTemplateKey(), userId);
        return instance.getId();
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

    private String generateInstanceNo() {
        return "AP" + INSTANCE_NO_FORMAT.format(LocalDateTime.now()) + RandomUtil.randomNumbers(6);
    }
}
