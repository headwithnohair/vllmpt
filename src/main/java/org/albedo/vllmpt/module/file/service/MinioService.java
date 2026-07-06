package org.albedo.vllmpt.module.file.service;


import cn.hutool.core.lang.UUID;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.compress.utils.FileNameUtils.getExtension;

@Service
@Slf4j
public class MinioService {
    private static final Semaphore processingLimiter = new Semaphore(4);
    private static final File TEMP_BASE_DIR = new File("/data/app_temp/");

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
            StatObjectResponse stat= minioClient.statObject(StatObjectArgs.builder()
                            .object(fileUploadDTO.getObjectName())
                            .bucket(targetBucket)
                            .build());

        log.info("========{}", stat.contentType());

            return  stat.etag().equals(fileUploadDTO.getEtag());
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




    }

    public void  FileChangeBucket(FileUploadDTO fileUploadDTO,String  source,String target){
            try{
                String fileType = resolveFileType(fileUploadDTO.getMimeType());
                String finalObjectName = "fileType"+"/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/" + fileUploadDTO.getObjectName();

                minioClient.copyObject(CopyObjectArgs.builder()
                                .bucket(target)
                                .object(finalObjectName)
                                .source(CopySource.builder()
                                        .bucket(source)
                                        .object(fileUploadDTO.getObjectName())
                                        .build())
                        .build());
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(source)
                                .object(fileUploadDTO.getObjectName())
                                .build()
                );
            }catch(Exception e){
                log.info("文件转移失败",e);
                throw  new BusinessException(500,"文件转移失败");

            }
            // 数据库 存储 文件信息,方便后续调用,最好带会话id+用户id;


        return;
    }

    public  boolean processFileFromMinIO(FileUploadDTO fileUploadDTO,String  source){


       try{
           StatObjectResponse stat = minioClient.statObject(
                   StatObjectArgs.builder()
                           .bucket(source)
                           .object(fileUploadDTO.getObjectName())
                           .build());
           //确认文件大小,后缀.,准备存放
           InputStream minioStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(source)
                            .object(fileUploadDTO.getObjectName())
                            .build());

           long fileSize = stat.size();
           String contentType = stat.contentType();
           if (TEMP_BASE_DIR.getFreeSpace() < 2L * 1024 * 1024 * 1024) { // 小于 2GB
               throw new RuntimeException("磁盘空间不足，拒绝处理");
           }

           processingLimiter.acquire();
           log.info("file size {}",fileSize);
           log.info("file contentType {}",contentType);
           // 5. 分级处理逻辑
//           if (isTextFile(contentType) && fileSize < 2 * 1024 * 1024) {
//               // 【TXT/小文本】：纯内存处理，不落盘
//               processInMemory(bucketName, objectName);
//           } else {
//               // 【图片/PDF】：下载到本地 SSD 临时目录
//               processWithLocalTempFile(bucketName, objectName, contentType);
//           }

           processingLimiter.release();

       }catch (Exception e){
           log.warn("minioStream Error",e);
       }

        return  true;
    }

//    private void processWithLocalTempFile(String bucket, String objectName, String contentType) {
//        // 创建按日期和类型分类的临时文件
//        String dateDir = LocalDate.now().toString();
//        String typeDir = getFileTypeDir(contentType); // 返回 "pdf" 或 "image"
//        File dir = new File(TEMP_BASE_DIR, dateDir + "/" + typeDir);
//        dir.mkdirs();
//
//        File tempFile = new File(dir, UUID.randomUUID() + "_" + objectName);
//
//        try {
//            // 7. 流式下载到本地磁盘（边读边写，不占满内存）
//            try (InputStream in = minioClient.getObject(
//                    GetObjectArgs.builder()
//                            .object(objectName)
//                            .bucket(bucket)
//                            .build());
//                 OutputStream out = new FileOutputStream(tempFile)) {
//                byte[] buffer = new byte[8192];
//                int len;
//                while ((len = in.read(buffer)) != -1) {
//                    out.write(buffer, 0, len);
//                }
//            }
//
//            // 8. 执行具体的解析和向量化（OCR / PDF解析）
//            vectorizeFile(tempFile, contentType);
//
//        } catch (Exception e) {
//            log.error("处理文件失败: {}", objectName, e);
//        } finally {
//            // 9. 核心：无论成功失败，必须删除临时文件
//            if (tempFile.exists() && !tempFile.delete()) {
//                log.warn("临时文件删除失败，等待兜底任务清理: {}", tempFile.getAbsolutePath());
//            }
//        }
//    }
    /**
     * 根据 MimeType 判断文件类型
     */
    private String resolveFileType(String mimeType) {
        if (mimeType == null) {
            return "unknown";
        }
        String lower = mimeType.toLowerCase();
        if (lower.startsWith("image/")) {
            return "img";
        }
        if (lower.startsWith("video/")) {
            return "video";
        }
        // 文档类：word、pdf、txt、excel、ppt 等
        if (lower.startsWith("text/")
                || lower.equals("application/pdf")
                || lower.equals("application/msword")
                || lower.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || lower.equals("application/vnd.ms-excel")
                || lower.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || lower.equals("application/vnd.ms-powerpoint")
                || lower.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return "word";
        }
        return "unknown";
    }


}
