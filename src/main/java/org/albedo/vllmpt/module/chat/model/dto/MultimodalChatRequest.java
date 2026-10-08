package org.albedo.vllmpt.module.chat.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.albedo.vllmpt.module.chat.model.entity.Attachment;

import java.util.List;

/**
 * 多模态对话请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalChatRequest {

    /** 会话 ID */
    private String sessionId;

    /**
     * 用户 ID（sys_user.id）。
     * <p>
     * 与 {@code sessionId} 的区别：userId 是「谁」，用于配额与用量落库；sessionId 是「哪次会话」，用于记忆与互斥锁。
     * 显式传入时，Token 配额的 Redis 键会变成 {@code ai:quota:1:{yyyyMMdd}}，
     * 与 {@code ai_user_daily_token_usage.user_id=1} 对得上；不传则退回 sessionId。
     */
    private String userId;

    /** 用户输入的文本 */
    private String text;

    /** 附件列表（图片、文件等） */
    private List<Attachment> attachments;

    // --- 以下是模型控制参数（可选） ---

    /** 指定模型名称，为空则使用默认 */
    private String modelName;

    /** 温度，为空则使用默认 */
    private Double temperature;

    /** 最大 Token，为空则使用默认 */
    private Integer maxTokens;

    private Boolean isStream;
}
