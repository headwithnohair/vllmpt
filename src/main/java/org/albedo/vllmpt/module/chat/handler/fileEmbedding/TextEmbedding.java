package org.albedo.vllmpt.module.chat.handler.fileEmbedding;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.service.impl.KnowledgeBaseRagServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TextEmbedding {

    @Autowired
    KnowledgeBaseRagServiceImpl knowledgeBaseRagService;


    public void   chunkEmbeding(TextSegment textSegment){
        // 向量化并存储
        log.info("{}",textSegment);
        knowledgeBaseRagService.indexTextSegment(textSegment);

    }

}
