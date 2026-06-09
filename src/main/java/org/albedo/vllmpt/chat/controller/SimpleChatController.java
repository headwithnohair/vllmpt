package org.albedo.vllmpt.chat.controller;

import org.albedo.vllmpt.ai.service.SimpleChatAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.redis.core.RedisTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/simple")
public class SimpleChatController {
    @Autowired
    private SimpleChatAssistant chatAssistant;


    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    /**
     * 简单对话接口
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String response = chatAssistant.chat(userMessage);
        return Map.of("response", response);
    }
    @PostMapping("/test")
    public String test(){
        // 假设你的 key 是 "chat_memory:user_123"
        String key = "user127";
        byte[] rawBytes = redisTemplate.getConnectionFactory()
                .getConnection()
                .get(key.getBytes(StandardCharsets.UTF_8));

        System.out.println("原始字节长度: " + rawBytes.length);
        System.out.println("十六进制前 20 字节: " + bytesToHex(rawBytes, 0, Math.min(20, rawBytes.length)));

        return "";
    }
    // 辅助方法：转十六进制
    private static String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString();
    }
}
