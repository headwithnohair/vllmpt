package org.albedo.vllmpt.chat.controller;


import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.ai.service.MemoryChatAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat/memory")
@Slf4j
public class MemoryChatController {

    @Autowired
    private MemoryChatAssistant memoryChatAssistant;

    /**
     * 带记忆的对话
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String chatId = request.get("chatId");  // 会话 ID
        String message = request.get("message");
        log.info(message);
        String response = memoryChatAssistant.chat(chatId, message);
        return Map.of("response", response, "chatId", chatId);
    }

    /**
     * 清除会话记忆
     */
    @DeleteMapping("/{chatId}")
    public Map<String, String> clearMemory(@PathVariable String chatId) {
        memoryChatAssistant.clearMemory(chatId);
        return Map.of("message", "会话记忆已清除", "chatId", chatId);
    }
}
