package org.albedo.vllmpt.chat.model.dto;

import lombok.Data;
import org.albedo.vllmpt.chat.model.entity.Attachment;

import java.util.List;

/**
 * 多模态对话请求 DTO
 */
@Data
public class MultimodalChatRequest {
    
    /**
     * 对话文本
     */
    private String text;

    private String chatId;
    /**
     * 多张图片 URL 列表（用于多图对话）
     */
    private List<Attachment> attachments ; //fileUrls

}
