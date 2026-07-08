package org.albedo.vllmpt.module.chat.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.service.AttachmentProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AttachmentProcessorRegistry {
    private final Map<String, AttachmentProcessor> processorMap = new HashMap<>();
    private final List<String> supportSet = List.of("image", "txt");

    @Autowired
    public void registerProcessors(List<AttachmentProcessor> processors) {



        for (AttachmentProcessor p : processors) {
            //getFirst 注意  目前只支持 image !!
            // 后面再考虑其他
            if (p.supports(supportSet.getFirst())) {
                processorMap.put(supportSet.getFirst(), p);
            }
        }
    }

    public AttachmentProcessor getProcessor(String type) {
        AttachmentProcessor processor = processorMap.get(type);
        if (processor == null) {
            throw new Error("Unsupported type: " + type);
        }
        return processor;
    }
}
