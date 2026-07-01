package org.albedo.vllmpt.module.file.service;


import cn.hutool.core.lang.UUID;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MinioService {

    @Autowired
    private MinioClient minioClient;


    public String getUploadUrl(String fileName){
        // UUID高并发性能问题
        String objectName = UUID.randomUUID() + "_" + fileName;

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket("vllmpt-temp")
                            .object(objectName)
                            .expiry(5, TimeUnit.MINUTES) // 5分钟后链接失效
                            .build());

        }catch (Exception e){
            log.info(e.getMessage());
                    throw  new BusinessException(500,"无法获取MinIo链接");
        }
    }

}
