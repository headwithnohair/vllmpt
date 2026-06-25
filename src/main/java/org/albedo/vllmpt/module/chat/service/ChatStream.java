package org.albedo.vllmpt.module.chat.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;

import java.util.function.Consumer;

public interface ChatStream {
    ChatStream onNext(Consumer<String> nextHandler);
    ChatStream onComplete(Consumer<Response<AiMessage>> completeHandler);
    ChatStream onError(Consumer<Throwable> errorHandler);
    void start();
}
