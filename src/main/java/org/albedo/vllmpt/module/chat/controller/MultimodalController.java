package org.albedo.vllmpt.module.chat.controller;

import org.albedo.vllmpt.module.chat.service.MultimodalAssistant;
import org.albedo.vllmpt.module.chat.model.dto.MultimodalChatRequest;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat/multimodal")
public class MultimodalController {

    @Autowired
    private MultimodalAssistant multimodalAssistant;

    @PostMapping("/multiple")
    public Map<String, String> chatWithMultiple(@RequestBody MultimodalChatRequest request) {
        if (request.getAttachments() == null) {
            throw new BusinessException("Attachments required");
        }
        String response = multimodalAssistant.chatWithMultipleFiles(request);
        return Map.of("response", response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatWithFiles(@RequestBody MultimodalChatRequest request) {

        return Flux.create(sink -> {
            multimodalAssistant.chatWithMultipleFilesStreaming(request)
                    .onNext(sink::next) // 直接推入 Flux
                    .onComplete(response -> {
                        sink.next("[DONE]");
                        sink.complete();
                    })
                    .onError(sink::error)
                    .start();
        });
    }


    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Map<String, String> easyReply(@RequestBody MultimodalChatRequest request) {

        if (request.getAttachments() == null) {
            throw new BusinessException("Attachments required");
        }
        String response = "132";
        return Map.of("response", response);
    }
}
