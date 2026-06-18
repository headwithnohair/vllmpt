package org.albedo.vllmpt.chat.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.albedo.vllmpt.chat.model.entity.Attachment;

import java.util.List;

/**
 * 多模态对话请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
 // 使用 Lombok 的 Builder 模式，让构建对象更优雅
public class MultimodalChatRequest {

    /** 会话 ID */
    private String sessionId;

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