package org.albedo.vllmpt.chat.service;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.spring.AiService;
import org.albedo.vllmpt.chat.model.dto.MultimodalChatRequest;




public interface MultimodalAssistant {

     String chatWithMultipleFiles(MultimodalChatRequest request);

     TokenStream   chatStream(MultimodalChatRequest request);
}
