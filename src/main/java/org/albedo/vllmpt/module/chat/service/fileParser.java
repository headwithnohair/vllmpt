package org.albedo.vllmpt.module.chat.service;

import org.albedo.vllmpt.module.chat.model.entity.Attachment;
import org.albedo.vllmpt.module.chat.model.entity.ProcessResult;

public interface fileParser {

    boolean supports(String type);  // 判断是否能处理该类型
    ProcessResult process(Attachment attachment, String sessionId);
}
