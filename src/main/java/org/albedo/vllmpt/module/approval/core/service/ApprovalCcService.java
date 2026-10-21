package org.albedo.vllmpt.module.approval.core.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.module.approval.core.domain.dto.CcItemVO;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalCc;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalCcMapper;
import org.albedo.vllmpt.module.approval.core.mapper.ApprovalInstanceMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 抄送服务：我的抄送、标记已读。
 * <p>
 * 抄送只做消息触达，不生成待办、不阻塞流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalCcService {

    private final ApprovalCcMapper ccMapper;
    private final ApprovalInstanceMapper instanceMapper;

    public List<CcItemVO> my(int limit) {
        Long userId = UserContextHolder.requireUserId();
        int size = Math.min(Math.max(limit, 1), 100);
        List<ApprovalCc> ccs = ccMapper.selectList(Wrappers.<ApprovalCc>lambdaQuery()
                .eq(ApprovalCc::getReceiverId, userId)
                .orderByDesc(ApprovalCc::getId)
                .last("LIMIT " + size));
        if (ccs.isEmpty()) {
            return List.of();
        }

        Set<Long> instanceIds = ccs.stream()
                .map(ApprovalCc::getInstanceId)
                .collect(Collectors.toSet());
        Map<Long, ApprovalInstance> instances = instanceMapper.selectBatchIds(instanceIds).stream()
                .collect(Collectors.toMap(ApprovalInstance::getId, Function.identity()));

        List<CcItemVO> result = new ArrayList<>();
        for (ApprovalCc cc : ccs) {
            ApprovalInstance instance = instances.get(cc.getInstanceId());
            if (instance == null) {
                continue;
            }
            CcItemVO vo = new CcItemVO();
            vo.setId(cc.getId());
            vo.setInstanceId(instance.getId());
            vo.setInstanceNo(instance.getInstanceNo());
            vo.setTitle(instance.getTitle());
            vo.setBizType(instance.getBizType());
            vo.setStatus(instance.getStatus());
            vo.setApplicantId(instance.getApplicantId());
            vo.setNodeKey(cc.getNodeKey());
            vo.setReadStatus(cc.getReadStatus());
            vo.setCreatedAt(cc.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    public void markRead(Long ccId) {
        Long userId = UserContextHolder.requireUserId();
        ApprovalCc cc = ccMapper.selectById(ccId);
        if (cc == null) {
            throw new BusinessException(404, "抄送记录不存在: " + ccId);
        }
        if (!userId.equals(cc.getReceiverId())) {
            throw new BusinessException(403, "只能操作发给自己的抄送");
        }
        if (cc.getReadStatus() != null && cc.getReadStatus() == 1) {
            return;
        }
        ccMapper.update(null, new LambdaUpdateWrapper<ApprovalCc>()
                .eq(ApprovalCc::getId, ccId)
                .eq(ApprovalCc::getReceiverId, userId)
                .set(ApprovalCc::getReadStatus, 1));
        log.info("[审批] 抄送标记已读: ccId={}, userId={}", ccId, userId);
    }
}
