package org.albedo.vllmpt.chat.service;

import org.albedo.vllmpt.chat.model.entity.Attachment;
import org.albedo.vllmpt.chat.model.entity.ProcessResult;

public interface AttachmentProcessor {
    boolean supports(String type);  // 判断是否能处理该类型
    ProcessResult process(Attachment attachment, String sessionId);
}

