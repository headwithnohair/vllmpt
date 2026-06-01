package org.albedo.vllmpt.chat.controller;


import org.albedo.vllmpt.ai.service.MultimodalAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/multimodal")
public class MultimodalController {

    @Autowired
    private MultimodalAssistant multimodalAssistant;

    /**
     * 单图对话
     */
    @PostMapping("/single-image")
    public Map<String, String> chatWithImage(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String imageUrl = request.get("imageUrl");

        String response = multimodalAssistant.chatWithImage(text, imageUrl);
        return Map.of("response", response);
    }

    /**
     * 多图对话
     */
    @PostMapping("/multiple-images")
    public Map<String, String> chatWithMultipleImages(
            @RequestBody Map<String, Object> request) {
        String text = (String) request.get("text");
        List<String> imageUrls = (List<String>) request.get("imageUrls");
        String response = multimodalAssistant.chatWithMultipleImages(text, imageUrls);
        return Map.of("response", response);
    }
}