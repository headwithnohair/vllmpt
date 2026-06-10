package org.albedo.vllmpt.common.enums;

import lombok.Getter;

/**
 * 路由策略枚举
 */
@Getter
public enum RoutingStrategyType {
    
    PRIORITY("优先级路由"),
    COST("成本优先路由"),
    PERFORMANCE("性能优先路由"),
    ROUND_ROBIN("轮询路由");
    
    private final String description;
    
    RoutingStrategyType(String description) {
        this.description = description;
    }
}
