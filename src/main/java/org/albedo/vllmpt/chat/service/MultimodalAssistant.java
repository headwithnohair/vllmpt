package org.albedo.vllmpt.chat.service;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.spring.AiService;
import org.albedo.vllmpt.chat.model.dto.MultimodalChatRequest;
import org.albedo.vllmpt.chat.service.impl.DefaultChatStream;


public interface MultimodalAssistant {

     String chatWithMultipleFiles(MultimodalChatRequest request);

     DefaultChatStream chatWithMultipleFilesStreaming(MultimodalChatRequest request);
}
