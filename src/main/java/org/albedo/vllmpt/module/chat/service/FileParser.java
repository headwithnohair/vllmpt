package org.albedo.vllmpt.module.chat.service;

import org.albedo.vllmpt.module.chat.model.entity.Attachment;
import org.albedo.vllmpt.module.chat.model.entity.FileParseResult;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;

import java.io.InputStream;

//用于知识库 获取文件后进行文件判别 ->拆分 ->最后向量化
public interface FileParser {

    boolean supports(String type);  // 判断是否能处理该类型
    FileParseResult process(InputStream inputStream, FileUploadDTO fileUploadDTO);
    String getSupportTypes();
}
