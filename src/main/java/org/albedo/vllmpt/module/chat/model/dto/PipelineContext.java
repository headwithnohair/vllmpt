package org.albedo.vllmpt.module.chat.model.dto;

import java.util.HashMap;
import java.util.Map;

public class PipelineContext<T> {
    private T data;                    // 主数据
    private Map<String, Object> attributes = new HashMap<>(); // 附加属性
    private boolean interrupted = false; // 是否中断
    private String interruptReason;

    public void interrupt(String reason) {
        this.interrupted = true;
        this.interruptReason = reason;
    }
    // getters/setters...
}