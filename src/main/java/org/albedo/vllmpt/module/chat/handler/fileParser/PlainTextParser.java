package org.albedo.vllmpt.module.chat.handler.fileParser;

import org.albedo.vllmpt.module.chat.model.entity.Attachment;
import org.albedo.vllmpt.module.chat.model.entity.FileParseResult;
import org.albedo.vllmpt.module.chat.service.FileParser;

public class PlainTextParser implements FileParser {
    @Override
    public boolean supports(String type) {
        //"text/plain"

        return false;
    }

    @Override
    public FileParseResult process(Attachment attachment) {
        return null;
    }
}
