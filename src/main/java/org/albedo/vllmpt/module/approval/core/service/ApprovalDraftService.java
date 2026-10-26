package org.albedo.vllmpt.module.approval.core.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.approval.core.domain.dto.ApprovalDraftRequest;
import org.albedo.vllmpt.module.approval.core.domain.dto.ApprovalDraftVO;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalTemplate;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 审批填表草稿服务（Redis）。
 * <p>
 * 草稿只服务「多轮补全」阶段，不进入正式审批表：
 * <ul>
 *   <li>key：{@code approval:draft:{userId}:{bizType}:{sessionId}}；</li>
 *   <li>TTL 30 分钟；</li>
 *   <li>保存时按表单 Schema 计算缺失字段，供 AI 追问/前端提示；</li>
 *   <li>Redis 不可用时降级：保存不落库、读取返回 null，不阻断填表主链路。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalDraftService {

    private static final long TTL_MINUTES = 30;
    private static final String DEFAULT_SESSION = "default";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final ApprovalTemplateService templateService;
    private final FormSchemaValidator formSchemaValidator;

    /**
     * 保存/合并草稿，返回合并后的草稿与缺失字段。
     */
    public ApprovalDraftVO save(Long userId, ApprovalDraftRequest request) {
        String bizType = normalize(request.getBizType());
        String sessionId = sessionId(request.getSessionId());
        ApprovalTemplate template = templateService.findPublishedByBizType(bizType);
        if (template == null) {
            throw new BusinessException(404, "未找到已发布的审批模板: " + bizType);
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        ApprovalDraftVO existing = get(userId, bizType, request.getSessionId());
        if (existing != null && existing.getFormData() != null) {
            merged.putAll(existing.getFormData());
        }
        merged.putAll(request.getFormData());

        List<String> missing = formSchemaValidator.missingFields(template.getFormSchema(), merged);
        ApprovalDraftVO draft = new ApprovalDraftVO(bizType, sessionId, merged, missing, LocalDateTime.now());
        String json = writeJson(draft);
        try {
            bucket(userId, bizType, sessionId).set(json, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.warn("[审批] 草稿保存失败，降级为不落 Redis: userId={}, bizType={}, error={}",
                    userId, bizType, e.getMessage());
        }
        return draft;
    }

    /**
     * @return 不存在或 Redis 不可用时返回 null
     */
    public ApprovalDraftVO get(Long userId, String bizType, String sessionId) {
        if (StrUtil.isBlank(bizType)) {
            return null;
        }
        String type = normalize(bizType);
        try {
            String json = bucket(userId, type, sessionId(sessionId)).get();
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return objectMapper.readValue(json, ApprovalDraftVO.class);
        } catch (Exception e) {
            log.warn("[审批] 草稿读取失败: userId={}, bizType={}, error={}", userId, type, e.getMessage());
            return null;
        }
    }

    public void delete(Long userId, String bizType, String sessionId) {
        if (StrUtil.isBlank(bizType)) {
            return;
        }
        try {
            bucket(userId, normalize(bizType), sessionId(sessionId)).delete();
        } catch (RuntimeException e) {
            log.warn("[审批] 草稿删除失败: userId={}, bizType={}, error={}", userId, bizType, e.getMessage());
        }
    }

    private RBucket<String> bucket(Long userId, String bizType, String sessionId) {
        return redissonClient.getBucket(RedisKey.approvalDraft(userId, bizType, sessionId));
    }

    private String normalize(String bizType) {
        return bizType.trim().toUpperCase(Locale.ROOT);
    }

    private String sessionId(String sessionId) {
        return StrUtil.isBlank(sessionId) ? DEFAULT_SESSION : sessionId.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "草稿序列化失败");
        }
    }
}
