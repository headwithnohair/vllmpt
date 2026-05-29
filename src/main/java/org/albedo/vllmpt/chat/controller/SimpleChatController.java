package org.albedo.vllmpt.chat.controller;

import org.albedo.vllmpt.ai.service.SimpleChatAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chat/simple")
public class SimpleChatController {
    @Autowired
    private SimpleChatAssistant chatAssistant;

    /**
     * 简单对话接口
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String response = chatAssistant.chat(userMessage);
        return Map.of("response", response);
    }
}
