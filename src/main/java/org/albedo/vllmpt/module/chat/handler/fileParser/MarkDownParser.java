package org.albedo.vllmpt.module.chat.handler.fileParser;

import org.albedo.vllmpt.module.chat.model.entity.Attachment;
import org.albedo.vllmpt.module.chat.model.entity.FileParseResult;
import org.albedo.vllmpt.module.chat.service.FileParser;

public class MarkDownParser implements FileParser {

    private final String SupportType ="text/markdown";
    @Override
    public boolean supports(String type) {


        return type.equals(SupportType);
    }

    @Override
    public FileParseResult process(Attachment attachment) {
        return null;
    }

    @Override
    public String getSupportTypes() {
        return SupportType;
    }
}
