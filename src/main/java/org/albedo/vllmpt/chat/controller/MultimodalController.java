package org.albedo.vllmpt.chat.controller;


import org.albedo.vllmpt.ai.service.MultimodalAssistant;
import org.albedo.vllmpt.chat.model.dto.MultimodalChatRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat/multimodal")
public class MultimodalController {

    @Autowired
    private MultimodalAssistant multimodalAssistant;



    @PostMapping("/multiple")
    public Map<String, String> chatWithMultiple(@RequestBody MultimodalChatRequest request) {
        String response = multimodalAssistant.chatWithMultipleFiles(request.getChatId(),request.getText(), request.getAttachments());
        return Map.of("response", response);
    }


}