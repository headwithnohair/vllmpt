package org.albedo.vllmpt.module.file.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.file.service.FileUploadService;
import org.albedo.vllmpt.module.file.service.MinioService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private  final MinioService minioService;

    @PostMapping("/upload/UploadUrl")
    public Result< Map<String, String>> getMinioUploadUrl(@RequestBody Map<String, Object> jsonMap){
        String fileName =jsonMap.get("fileName").toString();
        Map<String, String> op= minioService.getUploadUrl(fileName);
        return Result.success(op);
    }



    /**
     * 上传图片（multipart/form-data）
     *
     * @param file 图片文件
     * @return 公开访问的 URL
     */
    @PostMapping("/upload/image")
    public Map<String, String> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("接收到图片上传请求: {}", file.getOriginalFilename());

        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支持图片文件");
        }

        // 验证文件大小（最大 10MB）

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("图片大小不能超过 10MB");
        }

        String url = fileUploadService.uploadImage(file);
        return Map.of("url", url);
    }

    /**
     * 从 URL 上传图片到 MinIO
     *
     * @param request 包含 imageUrl 的请求体
     * @return MinIO 中的公开访问 URL
     */
    @PostMapping("/upload/from-url")
    public Map<String, String> uploadImageFromUrl(@RequestBody Map<String, String> request) {
        String imageUrl = request.get("imageUrl");

        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("imageUrl 不能为空");
        }

        log.info("从 URL 上传图片: {}", imageUrl);
        String url = fileUploadService.uploadImageFromUrl(imageUrl);
        return Map.of("url", url);
    }
}
