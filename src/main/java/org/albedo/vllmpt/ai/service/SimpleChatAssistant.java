package org.albedo.vllmpt.ai.service;


import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import org.springframework.stereotype.Service;

@AiService
public interface SimpleChatAssistant {



    /**
     * 简单对话
     * @param userMessage 用户消息
     * @return AI 回复
     */
    @SystemMessage("你是一个专业的电商智能助手，回答要简洁友好")
     String chat(@UserMessage String userMessage);
}
