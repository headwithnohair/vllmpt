package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.dto.TaskDetailVO;
import org.albedo.vllmpt.module.approval.core.domain.dto.TaskTodoVO;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalRecord;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTask;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalAction;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalInstanceStatus;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalMode;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalOperatorType;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalTaskStatus;
import org.albedo.vllmpt.module.approval.core.engine.ApprovalEngine;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalInstanceMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalRecordMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTaskMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审批任务服务：待办、详情、同意、拒绝。
 * <p>
 * 任务状态变更与流程推进在同一事务内完成，任何一步失败都会整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalTaskService {

    private final ApprovalTaskMapper taskMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalEngine engine;

    public List<TaskTodoVO> todo(int limit) {
        Long userId = UserContextHolder.requireUserId();
        int size = Math.min(Math.max(limit, 1), 100);
        List<ApprovalTask> tasks = taskMapper.selectList(Wrappers.<ApprovalTask>lambdaQuery()
                .eq(ApprovalTask::getAssigneeId, userId)
                .eq(ApprovalTask::getStatus, ApprovalTaskStatus.PENDING.name())
                .orderByAsc(ApprovalTask::getCreatedAt)
                .last("LIMIT " + size));
        if (tasks.isEmpty()) {
            return List.of();
        }

        Set<Long> instanceIds = tasks.stream()
                .map(ApprovalTask::getInstanceId)
                .collect(Collectors.toSet());
        Map<Long, ApprovalInstance> instances = instanceMapper.selectBatchIds(instanceIds).stream()
                .collect(Collectors.toMap(ApprovalInstance::getId, Function.identity()));

        List<TaskTodoVO> result = new ArrayList<>();
        for (ApprovalTask task : tasks) {
            ApprovalInstance instance = instances.get(task.getInstanceId());
            if (instance == null
                    || !ApprovalInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
                continue;
            }
            TaskTodoVO vo = new TaskTodoVO();
            vo.setTaskId(task.getId());
            vo.setInstanceId(instance.getId());
            vo.setTitle(instance.getTitle());
            vo.setBizType(instance.getBizType());
            vo.setNodeKey(task.getNodeKey());
            vo.setNodeName(task.getNodeName());
            vo.setApplicantId(instance.getApplicantId());
            vo.setCreatedAt(task.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    public TaskDetailVO detail(Long taskId) {
        Long userId = UserContextHolder.requireUserId();
        ApprovalTask task = requireTask(taskId);
        if (!userId.equals(task.getAssigneeId())) {
            throw new BusinessException(403, "只能查看分配给自己的审批任务");
        }
        ApprovalInstance instance = requireInstance(task.getInstanceId());

        TaskDetailVO vo = new TaskDetailVO();
        vo.setTask(task);
        vo.setInstance(instance);
        ApprovalTemplate template = templateMapper.selectById(instance.getTemplateId());
        vo.setTemplateName(template == null ? null : template.getName());
        vo.setFormSchema(template == null ? null : template.getFormSchema());
        vo.setRecords(recordMapper.selectList(Wrappers.<ApprovalRecord>lambdaQuery()
                .eq(ApprovalRecord::getInstanceId, instance.getId())
                .orderByAsc(ApprovalRecord::getId)));
        return vo;
    }

    @Transactional
    public void approve(Long taskId, String comment) {
        Long userId = UserContextHolder.requireUserId();
        ApprovalTask task = requirePendingTask(taskId, userId);
        // 先锁实例，再复核任务，保证同一实例的审批动作串行执行
        ApprovalInstance instance = requireRunningInstance(task.getInstanceId());
        task = lockPendingTask(taskId, userId);
        assertCurrentNode(instance, task);

        int rows = completeTask(task, ApprovalTaskStatus.APPROVED, ApprovalAction.APPROVE, comment);
        if (rows == 0) {
            throw new BusinessException(409, "任务已被处理，请刷新后重试");
        }
        saveRecord(instance, task, userId, ApprovalAction.APPROVE, comment);
        afterApprove(instance, task);
        log.info("[审批] 同意: instanceId={}, taskId={}, node={}, approver={}",
                instance.getId(), taskId, task.getNodeKey(), userId);
    }

    @Transactional
    public void reject(Long taskId, String comment) {
        Long userId = UserContextHolder.requireUserId();
        ApprovalTask task = requirePendingTask(taskId, userId);
        ApprovalInstance instance = requireRunningInstance(task.getInstanceId());
        task = lockPendingTask(taskId, userId);
        assertCurrentNode(instance, task);

        int rows = completeTask(task, ApprovalTaskStatus.REJECTED, ApprovalAction.REJECT, comment);
        if (rows == 0) {
            throw new BusinessException(409, "任务已被处理，请刷新后重试");
        }
        saveRecord(instance, task, userId, ApprovalAction.REJECT, comment);

        // 同节点其他待办全部失效
        cancelSiblings(instance.getId(), task.getNodeKey(), task.getId());
        engine.reject(instance.getId(), task.getNodeKey(), comment);
        log.info("[审批] 拒绝: instanceId={}, taskId={}, node={}, approver={}",
                instance.getId(), taskId, task.getNodeKey(), userId);
    }

    private void afterApprove(ApprovalInstance instance, ApprovalTask task) {
        ApprovalMode mode = parseMode(task.getApprovalMode());
        switch (mode) {
            case ANY -> {
                cancelSiblings(instance.getId(), task.getNodeKey(), task.getId());
                engine.advance(instance.getId(), task.getNodeKey());
            }
            case ALL -> {
                // 锁定读：REPEATABLE READ 下普通查询可能看不到前一个会签人刚提交的状态
                List<ApprovalTask> pendingTasks = taskMapper.selectList(Wrappers.<ApprovalTask>lambdaQuery()
                        .eq(ApprovalTask::getInstanceId, instance.getId())
                        .eq(ApprovalTask::getNodeKey, task.getNodeKey())
                        .eq(ApprovalTask::getStatus, ApprovalTaskStatus.PENDING.name())
                        .last("FOR UPDATE"));
                if (pendingTasks.isEmpty()) {
                    engine.advance(instance.getId(), task.getNodeKey());
                }
            }
            case SEQUENTIAL -> {
                ApprovalTask next = taskMapper.selectOne(Wrappers.<ApprovalTask>lambdaQuery()
                        .eq(ApprovalTask::getInstanceId, instance.getId())
                        .eq(ApprovalTask::getNodeKey, task.getNodeKey())
                        .eq(ApprovalTask::getStatus, ApprovalTaskStatus.WAITING.name())
                        .orderByAsc(ApprovalTask::getSequenceNo)
                        .last("LIMIT 1"));
                if (next == null) {
                    engine.advance(instance.getId(), task.getNodeKey());
                } else {
                    activate(next);
                }
            }
        }
    }

    private void activate(ApprovalTask task) {
        int rows = taskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getId, task.getId())
                .eq(ApprovalTask::getStatus, ApprovalTaskStatus.WAITING.name())
                .set(ApprovalTask::getStatus, ApprovalTaskStatus.PENDING.name())
                .set(ApprovalTask::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(409, "任务状态已变化，请刷新后重试");
        }
    }

    private void cancelSiblings(Long instanceId, String nodeKey, Long excludeTaskId) {
        taskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instanceId)
                .eq(ApprovalTask::getNodeKey, nodeKey)
                .in(ApprovalTask::getStatus,
                        ApprovalTaskStatus.PENDING.name(), ApprovalTaskStatus.WAITING.name())
                .ne(excludeTaskId != null, ApprovalTask::getId, excludeTaskId)
                .set(ApprovalTask::getStatus, ApprovalTaskStatus.CANCELED.name())
                .set(ApprovalTask::getUpdatedAt, LocalDateTime.now()));
    }

    private int completeTask(ApprovalTask task, ApprovalTaskStatus status,
                             ApprovalAction action, String comment) {
        LambdaUpdateWrapper<ApprovalTask> wrapper = new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getId, task.getId())
                .eq(ApprovalTask::getStatus, ApprovalTaskStatus.PENDING.name())
                .set(ApprovalTask::getStatus, status.name())
                .set(ApprovalTask::getAction, action.name())
                .set(ApprovalTask::getUpdatedAt, LocalDateTime.now());
        if (StrUtil.isNotBlank(comment)) {
            wrapper.set(ApprovalTask::getComment, comment);
        }
        return taskMapper.update(null, wrapper);
    }

    private ApprovalTask requireTask(Long taskId) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "审批任务不存在: " + taskId);
        }
        return task;
    }

    private ApprovalTask requirePendingTask(Long taskId, Long userId) {
        ApprovalTask task = requireTask(taskId);
        if (!userId.equals(task.getAssigneeId())) {
            throw new BusinessException(403, "只能处理分配给自己的审批任务");
        }
        if (!ApprovalTaskStatus.PENDING.name().equals(task.getStatus())) {
            throw new BusinessException(409, "任务已处理或已取消");
        }
        return task;
    }

    /**
     * 拿到实例锁后重新读取任务，避免并发时基于旧快照做判断。
     */
    private ApprovalTask lockPendingTask(Long taskId, Long userId) {
        ApprovalTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(404, "审批任务不存在: " + taskId);
        }
        if (!userId.equals(task.getAssigneeId())) {
            throw new BusinessException(403, "只能处理分配给自己的审批任务");
        }
        if (!ApprovalTaskStatus.PENDING.name().equals(task.getStatus())) {
            throw new BusinessException(409, "任务已处理或已取消");
        }
        return task;
    }

    private ApprovalInstance requireInstance(Long instanceId) {
        ApprovalInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(404, "审批实例不存在: " + instanceId);
        }
        return instance;
    }

    private ApprovalInstance requireRunningInstance(Long instanceId) {
        ApprovalInstance instance = instanceMapper.selectByIdForUpdate(instanceId);
        if (instance == null) {
            throw new BusinessException(404, "审批实例不存在: " + instanceId);
        }
        if (!ApprovalInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            throw new BusinessException(409, "审批实例不在运行中");
        }
        return instance;
    }

    private void assertCurrentNode(ApprovalInstance instance, ApprovalTask task) {
        if (!task.getNodeKey().equals(instance.getCurrentNodeKey())) {
            throw new BusinessException(409, "当前节点已流转，请刷新后重试");
        }
    }

    private void saveRecord(ApprovalInstance instance, ApprovalTask task, Long userId,
                            ApprovalAction action, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setNodeKey(task.getNodeKey());
        record.setTaskId(task.getId());
        record.setOperatorId(userId);
        record.setOperatorType(ApprovalOperatorType.USER.name());
        record.setAction(action.name());
        record.setComment(comment);
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private ApprovalMode parseMode(String mode) {
        if (StrUtil.isBlank(mode)) {
            return ApprovalMode.ANY;
        }
        try {
            return ApprovalMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(500, "任务审批模式不合法: " + mode);
        }
    }
}
