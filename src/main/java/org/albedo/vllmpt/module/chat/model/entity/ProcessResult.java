package org.albedo.vllmpt.module.chat.model.entity;

import dev.langchain4j.data.message.Content;
import lombok.Data;

import java.util.List;

@Data
public class ProcessResult {
    private List<Content> contentsForModel;  // 直接传给模型的 Content 列表
    private String memoryText;               // 存入记忆的纯文本（含摘要/标记）
    private boolean needVectorization;       // 是否需要异步向量化（长文本）
    private String textForVector;            // 需要向量化的文本内容

    public ProcessResult(List<Content> contents, String memoryText, boolean b, String o) {
        this.contentsForModel = contents;
        this.memoryText = memoryText;
        this.needVectorization = b;
        this.textForVector = o;
    }
}
