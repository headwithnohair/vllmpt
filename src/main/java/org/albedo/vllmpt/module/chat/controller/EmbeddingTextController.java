package org.albedo.vllmpt.module.chat.controller;


import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.chat.service.FileParser;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;
import org.albedo.vllmpt.module.file.service.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/indexAction")
@Slf4j
public class EmbeddingTextController {

     @Autowired
     MinioService minioService;

     @Autowired
     FileParser fileParser;


     @PostMapping("/file")
    public Result<String> indexFile(@RequestBody FileUploadDTO fileUploadDTO){
         // log.info("{}",fileUploadDTO);
        // 确认前端确实传递了文件,
        minioService.FileExistCheck(fileUploadDTO,"vllmpt-temp");
        // 将文件移出temp桶,放入data桶
         String finalObjectName = minioService.FileChangeBucket(fileUploadDTO,"vllmpt-temp","vllmpt-data");
         log.info("test4");

         //确认文件类型  是否支持向量化
         //后期引入  Tika  做头数据校验;
         //确认文件大小 大的采用流式 小的使用内存




         // 保存数据
         minioService.processFileFromMinIO(finalObjectName,"vllmpt-data");

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
        // 确认前端确实 传递了文件,
        log.info("test4");
        // 保存数据
        minioService.processFileFromMinIO(fileUploadDTO.getObjectName(),"vllmpt-data");
        log.info("test4");
        // 返还拆分结果,

        return;
    }

}
