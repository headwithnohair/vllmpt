package org.albedo.vllmpt.common.enums;

import lombok.Getter;

/**
 * 消息角色枚举
 */
@Getter
public enum MessageRole {
    
    USER("user", "用户"),
    ASSISTANT("assistant", "助手"),
    SYSTEM("system", "系统");
    
    private final String code;
    private final String description;
    
    MessageRole(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
