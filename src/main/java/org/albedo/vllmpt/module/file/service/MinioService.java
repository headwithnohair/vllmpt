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


import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.compress.utils.FileNameUtils.getExtension;

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

            return  pp.etag().equals(fileUploadDTO.getEtag());
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
            }catch(Exception e){
                log.info("文件转移失败",e);
                throw  new BusinessException(500,"文件转移失败");

            }
            // 数据库 存储 文件信息,方便后续调用,最好带会话id+用户id;


        return;
    }


    public Document loadPdfFromMinIO(FileUploadDTO fileUploadDTO, String bucketName){
        try {

            InputStream minioStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileUploadDTO.getObjectName())
                            .build()
            );


            ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
            // 解析流，返回 Document 对象
            Document document = parser.parse(minioStream);

            //  可以添加元数据，如文件名、来源等
            document.metadata().put("source_file_name", fileUploadDTO.getObjectName());
            document.metadata().put("bucket", bucketName);

            return document;
        } catch (Exception e) {
            // 处理异常
            throw new RuntimeException("Failed to load PDF from MinIO", e);
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

    /**
     * 按页解析 PDF，带页面间重叠，避免语义在分页处被截断。
     * 解析后的文本块直接存入向量数据库。
     *
     * @param minioStream PDF 文件输入流
     * @param objectName  文件名，用于标记来源
     * @return 切分后的文本块数量
     */
    public int parseWithPageOverlap(InputStream minioStream, String objectName) {
        List<TextSegment> allSegments = new java.util.ArrayList<>();
        try (PDDocument document = Loader.loadPDF(minioStream.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();
            log.info("开始解析 PDF [{}]，共 {} 页", objectName, totalPages);

            String previousPageSuffix = "";
            int OVERLAP_SIZE = 200;

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String currentPageText = stripper.getText(document);
                if (currentPageText.isBlank()) {
                    previousPageSuffix = "";
                    continue;
                }

                // 把上一页末尾拼到当前页开头，保证跨页语义不丢失
                String enrichedText = previousPageSuffix + currentPageText;

                Document tempDoc = Document.from(enrichedText);
                tempDoc.metadata().put("source_file", objectName);
                tempDoc.metadata().put("page_num", String.valueOf(pageNum));
                tempDoc.metadata().put("total_pages", String.valueOf(totalPages));

                DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
                List<TextSegment> segments = splitter.split(tempDoc);

                // 给每个分块打上 metadata
                for (TextSegment segment : segments) {
                    segment.metadata().put("source_file", objectName);
                    segment.metadata().put("page_num", String.valueOf(pageNum));
                }
                allSegments.addAll(segments);

                // 缓存当前页尾部，供下一页 overlap
                int len = currentPageText.length();
                previousPageSuffix = len > OVERLAP_SIZE
                        ? currentPageText.substring(len - OVERLAP_SIZE) : currentPageText;
            }

            log.info("PDF [{}] 解析完成，共生成 {} 个文本块", objectName, allSegments.size());

        } catch (IOException e) {
            log.error("PDF 解析失败 [{}]", objectName, e);
            throw new BusinessException(500, "PDF 解析失败: " + e.getMessage());
        }

        // 存入向量数据库
        if (!allSegments.isEmpty()) {
            saveSegments(allSegments);
        }
        return allSegments.size();
    }

    /**
     * 将文本块列表存入向量数据库（需注入 EmbeddingStore 和 EmbeddingModelFactory）
     */
    private void saveSegments(List<TextSegment> segments) {
        // 由调用方通过构造注入，避免 MinioService 与 AI 模块耦合过深
        // 此处暂不实现，留待上层调用 KnowledgeBaseRagService.indexText() 逐条入库
        log.debug("待入库文本块数量: {}", segments.size());
    }
}
