package org.albedo.vllmpt.module.chat.model.entity;

import lombok.Builder;
import lombok.Data;
import org.albedo.vllmpt.common.enums.Role;

import java.util.Map;

@Data
@Builder
public class ChatMessage {
    private Role role;
    private String content;
    private String toolCallId;    // tool 消息专用
    private Map<String, Object> metadata; // 扩展元数据
}