package org.albedo.vllmpt.chat.controller;


import org.albedo.vllmpt.ai.service.MultimodalAssistant;
import org.albedo.vllmpt.chat.model.dto.MultimodalChatRequest;
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
    public Map<String, String> chatWithImage(@RequestBody MultimodalChatRequest request) {
        String response = multimodalAssistant.chatWithImage(request.getChatId(),request.getText(), request.getImageUrl());
        return Map.of("response", response);
    }

    /**
     * 多图对话
     */
    @PostMapping("/multiple-images")
    public Map<String, String> chatWithMultipleImages(@RequestBody MultimodalChatRequest request) {
        String response = multimodalAssistant.chatWithMultipleImages(request.getChatId(),request.getText(), request.getImageUrls());
        return Map.of("response", response);
    }
}