package org.albedo.vllmpt.module.chat.model.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StageAudit {
    private String stageName;
    private int    inputMessageCount;
    private int    outputMessageCount;
    private int    inputTokens;
    private int    outputTokens;
    private long   costMs;
    private String summary; // 人类可读的操作描述
}