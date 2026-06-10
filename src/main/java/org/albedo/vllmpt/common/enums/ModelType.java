package org.albedo.vllmpt.common.enums;

import lombok.Getter;

/**
 * 模型类型枚举
 */
@Getter
public enum ModelType {
    
    TEXT("纯文本模型"),
    IMAGE("纯图像模型"),
    MULTIMODAL("多模态模型");
    
    private final String description;
    
    ModelType(String description) {
        this.description = description;
    }
}
