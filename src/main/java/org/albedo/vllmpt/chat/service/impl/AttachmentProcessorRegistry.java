package org.albedo.vllmpt.chat.service.impl;

import org.albedo.vllmpt.chat.service.AttachmentProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttachmentProcessorRegistry {
    private final Map<String, AttachmentProcessor> processorMap = new HashMap<>();

    @Autowired
    public void registerProcessors(List<AttachmentProcessor> processors) {
        for (AttachmentProcessor p : processors) {
            // 这里简单模拟 type -> processor，也可以让每个 processor 自己声明支持的 type
            // 更优雅：使用注解或策略模式自行注册
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