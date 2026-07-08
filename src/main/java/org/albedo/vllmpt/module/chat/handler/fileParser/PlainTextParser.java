package org.albedo.vllmpt.module.chat.handler.fileParser;

import org.albedo.vllmpt.module.chat.model.entity.Attachment;
import org.albedo.vllmpt.module.chat.model.entity.FileParseResult;
import org.albedo.vllmpt.module.chat.service.FileParser;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


@Component
public class PlainTextParser implements FileParser {


    private final String SupportType ="text/plain";
    @Override
    public boolean supports(String type) {
        return type.equals(SupportType);
    }

    @Override
    public FileParseResult process(Attachment attachment) {
        // 读取文件 区分


        // 获取文本


        //构建FileParseResult对象,并返回




        return null;
    }

    @Override
    public String getSupportTypes() {
        return SupportType;
    }
}
