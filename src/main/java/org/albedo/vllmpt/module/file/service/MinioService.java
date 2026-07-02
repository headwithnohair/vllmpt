package org.albedo.vllmpt.module.file.service;


import cn.hutool.core.lang.UUID;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MinioService {

    @Autowired
    private MinioClient minioClient;


    public Map<String, String> getUploadUrl(String fileName){

        // todo
        //  1.携带 fileName 和 mimeType 请求后端,后端白名单校验
        //  2.生成安全的 objectName（强制指定目录）
        //  3.UUID高并发性能问题
        String objectName = UUID.randomUUID() + "_" + fileName;
        Map<String, String> result = new HashMap<>();

        result.put("objectName", objectName); // 前端等下要用这个！
        try {
           String presignedUrl=  minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket("vllmpt-temp")
                            .object(objectName)
                            .expiry(5, TimeUnit.MINUTES) // 5分钟后链接失效
                            .build());
            result.put("presignedUrl", presignedUrl);
        }catch (Exception e){
            log.info(e.getMessage());
                    throw  new BusinessException(500,"无法获取MinIo链接");
        }

        return  result;
    }



    public Boolean  FileExistCheck(FileUploadDTO fileUploadDTO, String targetBucket) {

        try {
            StatObjectResponse pp= minioClient.statObject(StatObjectArgs.builder()
                            .object(fileUploadDTO.getObjectName())
                            .bucket(targetBucket)
                            .build());
//        log.info(pp.);

        }catch (ErrorResponseException  e){
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.info("NoSuchKey");
                return false;
            }
            // 其他错误（如权限、桶不存在等）视为严重问题，转为运行时异常
            throw new RuntimeException("MinIO statObject failed", e);
        } catch (Exception e) {
            // 其他异常（网络、证书等）也转为运行时异常
            throw new RuntimeException("MinIO statObject failed", e);
        }



        return  false;
    }
}
