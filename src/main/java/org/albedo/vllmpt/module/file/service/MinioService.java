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
    private static final File TEMP_BASE_DIR = new File("data/app_temp/");
    private static final long MIN_DISK_SPACE_BYTES = 2L * 1024 * 1024 * 1024; // 2GB

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
            fileUploadDTO.setMimeType( stat.contentType());
            fileUploadDTO.setFileSize( stat.size());
            fileUploadDTO.setEtag(stat.etag());
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

    public String  FileChangeBucket(FileUploadDTO fileUploadDTO,String  source,String target){
            try{
                String fileType = resolveFileType(fileUploadDTO.getMimeType());
                String finalObjectName = fileType+"/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/" + fileUploadDTO.getObjectName();

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
                // 数据库 存储 文件信息,方便后续调用,最好带会话id+用户id;
                return finalObjectName;
            }catch(Exception e){
                log.info("文件转移失败",e);
                throw  new BusinessException(500,"文件转移失败");

            }
    }

    public  boolean processFileFromMinIO(String finalObjectName,String  source){

       try{
           long usableSpace = TEMP_BASE_DIR.getUsableSpace();
           if (usableSpace < MIN_DISK_SPACE_BYTES) {
               log.error("磁盘空间不足，拒绝处理。当前可用: {} bytes, 需要: {} bytes", usableSpace, MIN_DISK_SPACE_BYTES);
               return false; // 或者抛出业务异常
           }
           StatObjectResponse stat = minioClient.statObject(
                   StatObjectArgs.builder()
                           .bucket(source)
                           .object(finalObjectName)
                           .build());
           //确认文件大小,后缀.,准备存放


           long fileSize = stat.size();
           String contentType = stat.contentType();
               log.info("file size {}",fileSize);
               log.info("file contentType {}",contentType);

           processingLimiter.acquire();
           try{
               try (InputStream minioStream = minioClient.getObject(
                       GetObjectArgs.builder()
                               .bucket(source)
                               .object(finalObjectName)
                               .build())) {

                    // TODO: 在这里使用 minioStream 进行实际处理
                    // 例如：将流写入本地临时文件，或直接解析内容
                    processWithLocalTempFile(minioStream, stat);

                    log.info("文件处理成功: {}", finalObjectName);

           }
           }finally {
               processingLimiter.release();
           }
       }catch (Exception e){
           log.warn("minioStream Error",e);
           return  false;
       }

        return  true;
    }

    private void processWithLocalTempFile(InputStream minioStream,StatObjectResponse stat) {
        // 创建按日期和类型分类的临时文件
        String dateDir = LocalDate.now().toString();
//        String typeDir = getFileTypeDir(contentType); // 返回 "pdf" 或 "image"
        File dir = new File(TEMP_BASE_DIR, dateDir + "/" + stat.contentType());
            dir.mkdirs();

        File tempFile = new File(dir, UUID.randomUUID() + "_" + stat.object());

        try {
            // 7. 流式下载到本地磁盘（边读边写，不占满内存）
            try (OutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = minioStream.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

            // 8. 执行具体的解析和向量化（OCR / PDF解析）
//            vectorizeFile(tempFile, contentType);

        } catch (Exception e) {
            log.error("处理文件失败: {}", stat.object(), e);
        } finally {
            // 9. 核心：无论成功失败，必须删除临时文件
            if (tempFile.exists() && !tempFile.delete()) {
                log.warn("临时文件删除失败，等待兜底任务清理: {}", tempFile.getAbsolutePath());
            }
        }
    }
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
