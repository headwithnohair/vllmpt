package org.albedo.vllmpt.module.chat.model.dto;

import lombok.Data;
import org.albedo.vllmpt.module.chat.model.entity.ChatMessage;

import java.util.List;

@Data
public class AgentContext {

    List<ChatMessage> currentMessages;
    ChatMessage finalResult;
    int stepCount;
    String toolExecutor; // 用来执行大模型要求的工具

}
