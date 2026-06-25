package org.albedo.vllmpt.module.chat.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.redis.core.RedisTemplate;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/chat/simple")
public class SimpleChatController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/test")
    public String test() {
        // 注意：这里使用你实际查到的完整 key
        String key = "langchain4j:chat_memory:user127";

        // 使用 lRange 读取 List 的所有元素 (0 到 -1 表示从第一个到最后一个)
        List<byte[]> rawList = redisTemplate.execute((RedisCallback<List<byte[]>>) connection ->
                connection.lRange(key.getBytes(StandardCharsets.UTF_8), 0, -1)
        );

        if (rawList == null || rawList.isEmpty()) {
            return "❌ Key 不存在或 List 为空";
        }

        StringBuilder result = new StringBuilder();
        result.append("✅ 读取成功! List 共包含 ").append(rawList.size()).append(" 条消息:\n\n");

        for (int i = 0; i < rawList.size(); i++) {
            byte[] elementBytes = rawList.get(i);

            // 1. 打印十六进制 (前 30 个字节足以看清头部)
            int previewLen = Math.min(30, elementBytes.length);
            String hex = bytesToHex(elementBytes, 0, previewLen);

            // 2. 打印字符串预览 (遇到乱码会显示 )
            String strPreview = new String(elementBytes, 0, previewLen, StandardCharsets.UTF_8);

            result.append("👉 [Index ").append(i).append("] 总长度: ").append(elementBytes.length).append(" bytes\n");
            result.append("   十六进制: ").append(hex).append("\n");
            result.append("   字符串预览: ").append(strPreview).append("...\n");
            result.append("   --------------------------------------------------\n");
        }

        return result.toString();
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
