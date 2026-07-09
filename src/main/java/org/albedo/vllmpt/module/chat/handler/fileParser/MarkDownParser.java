package org.albedo.vllmpt.module.chat.handler.fileParser;

import org.albedo.vllmpt.module.chat.model.entity.Attachment;
import org.albedo.vllmpt.module.chat.model.entity.FileParseResult;
import org.albedo.vllmpt.module.chat.service.FileParser;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;

import java.io.InputStream;

public class MarkDownParser implements FileParser {

    private final String SupportType ="text/markdown";
    @Override
    public boolean supports(String type) {


        return type.equals(SupportType);
    }

    @Override
    public FileParseResult process(InputStream inputStream , FileUploadDTO fileUploadDTO) {

        return null;
    }

    @Override
    public String getSupportTypes() {
        return SupportType;
    }
}
