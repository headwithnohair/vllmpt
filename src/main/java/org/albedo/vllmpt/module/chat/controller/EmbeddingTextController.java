package org.albedo.vllmpt.module.chat.controller;


import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.chat.service.FileParser;
import org.albedo.vllmpt.module.chat.service.impl.FileParserRegistry;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;
import org.albedo.vllmpt.module.file.service.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/indexAction")
@Slf4j
public class EmbeddingTextController {

     @Autowired
     MinioService minioService;

     @Autowired
     FileParserRegistry fileParserRegistry;


     @PostMapping("/file")
    public Result<String> indexFile(@RequestBody FileUploadDTO fileUploadDTO){
         int MIN_SAVE_SIZE =1024*1024*2;
         // log.info("{}",fileUploadDTO);
        // 确认前端确实传递了文件,
        minioService.FileExistCheck(fileUploadDTO,"vllmpt-temp");
        // 将文件移出temp桶,放入data桶
         String finalObjectName = minioService.FileChangeBucket(fileUploadDTO,"vllmpt-temp","vllmpt-data");
         log.info("test4");

         //Tika  做头数据校验
         //确认文件类型  是否支持向量化
         FileParser fileParser=fileParserRegistry.getParser(fileUploadDTO.getMimeType());

         try( InputStream inputStream= minioService.getFileStream(finalObjectName,"vllmpt-data")){
             fileParser.process(inputStream,fileUploadDTO);
         }catch (Exception e){
             log.warn("Error ",e);
         }
         //最好是 异步返回 traceId
         //分配文件解析器,然后进行向量化.

         log.info("test4");
         // 返还拆分结果,
         //理应不直接返回文件名
         //但简化一下,先不处理
        return Result.success(finalObjectName);
    }

    @PostMapping("/DownloadFile")
    public void DownloadFile(@RequestBody FileUploadDTO fileUploadDTO){

//         log.info("{}",fileUploadDTO);
        // 确认前端确实  传递了文件,
        // 保存数据
        minioService.processFileFromMinIO(fileUploadDTO.getObjectName(),"vllmpt-data");
        // 返还拆分结果,

        return;
    }

}
