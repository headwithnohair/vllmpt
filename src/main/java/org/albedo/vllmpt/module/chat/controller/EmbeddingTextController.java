package org.albedo.vllmpt.module.chat.controller;


import lombok.extern.slf4j.Slf4j;
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

     @PostMapping("/file")
    public void indexFile(@RequestBody FileUploadDTO fileUploadDTO){

//         log.info("{}",fileUploadDTO);
         log.info("test1");
        // 确认前端确实 传递了文件,
        minioService.FileExistCheck(fileUploadDTO,"vllmpt-temp");
         log.info("test2");
        // 将文件移出temp桶,放入datat桶
         minioService.FileChangeBucket(fileUploadDTO,"vllmpt-temp","vllmpt-data");
         log.info("test3");
         // 保存数据
         minioService.processFileFromMinIO(fileUploadDTO,"vllmpt-data");
         log.info("test4");
         // 返还拆分结果,

        return;
    }

}
