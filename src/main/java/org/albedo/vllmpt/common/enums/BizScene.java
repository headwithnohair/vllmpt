package org.albedo.vllmpt.common.enums;

import lombok.Getter;

/**
 * 业务场景枚举
 */
@Getter
public enum BizScene {
    
    GENERAL("通用问答"),
    CUSTOMER_SERVICE("智能客服"),
    COPYWRITING("文案生成"),
    IMAGE_AUDIT("图片审核"),
    PRODUCT_ANALYSIS("商品分析"),
    REVIEW_ANALYSIS("评论分析");
    
    private final String description;
    
    BizScene(String description) {
        this.description = description;
    }
}
