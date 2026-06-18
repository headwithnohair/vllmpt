package org.albedo.vllmpt.chat.service;

import org.albedo.vllmpt.chat.model.dto.MultimodalChatRequest;

public interface MultimodalAssistant {

     String chatWithMultipleFiles(MultimodalChatRequest request);
}
