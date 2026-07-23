package org.albedo.vllmpt.module.chat.service;

import dev.langchain4j.data.message.Content;

import java.util.List;

public interface  Assistant {
    String chat(String userMessage);
    String chat(List<Content> contentList);
}
