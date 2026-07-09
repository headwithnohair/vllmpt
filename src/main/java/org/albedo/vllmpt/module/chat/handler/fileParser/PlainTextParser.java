package org.albedo.vllmpt.module.chat.handler.fileParser;

import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.model.entity.FileParseResult;
import org.albedo.vllmpt.module.chat.service.FileParser;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class PlainTextParser implements FileParser {


    private final String SupportType ="text/plain";
    @Override
    public boolean supports(String type) {
        return type.equals(SupportType);
    }

    @Override
    public FileParseResult process(InputStream inputStream, FileUploadDTO  fileUploadDTO) {
        byte[] bytes = new byte[8192];
        long chunkCurrunt = 0L;
        int len ;
        try{
            // 1. 流式读取 拆分进行 分批向量化
            while ((len =  inputStream.read(bytes)) != -1){
                ++chunkCurrunt;
                String chunk = new String(bytes, 0, len, StandardCharsets.UTF_8);
                log.info("{},{}",chunk,chunkCurrunt);
                // 2. 进行元数据标记 文件名,页数, 作者 ,章节,被拆分的第n次,用于避免上下文缺失
            }

            //构建FileParseResult对象,并返回
            //



        }catch (Exception e){
            log.info("Error FileParseResult",e);


        }
        return null;
    }

    @Override
    public String getSupportTypes() {
        return SupportType;
    }
}
