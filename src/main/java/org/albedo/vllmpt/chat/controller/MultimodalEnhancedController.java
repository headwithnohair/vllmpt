package org.albedo.vllmpt.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.ai.service.MultimodalAssistant;
import org.albedo.vllmpt.file.service.FileUploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 多模态对话增强控制器（支持直接上传图片）
 */
@RestController
@RequestMapping("/api/chat/multimodal")
@RequiredArgsConstructor
@Slf4j
public class MultimodalEnhancedController {

    private final MultimodalAssistant multimodalAssistant;
    private final FileUploadService fileUploadService;

    /**
     * 上传图片并对话（一步完成）
     * 
     * @param file 图片文件
     * @param text 对话文本
     * @return AI 回复和使用的图片 URL
     */
    @PostMapping("/upload-and-chat")
    public Map<String, String> uploadAndChat(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("text") String text) {
        
        log.info("接收到上传并对话请求 - 文本: {}, 文件名: {}", text, file.getOriginalFilename());
        
        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支持图片文件");
        }

        // 验证文件大小（最大 10MB）
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("图片大小不能超过 10MB");
        }
        
        try {
            // 1. 上传图片到 MinIO
            String minioUrl = fileUploadService.uploadImage(file);
            log.info("图片上传成功: {}", minioUrl);
            
            // 2. 使用 MinIO URL 进行多模态对话
            String response = multimodalAssistant.chatWithImage(sessionId,text, minioUrl);
            
            // 3. 返回结果
            return Map.of(
                "response", response,
                "imageUrl", minioUrl
            );
        } catch (Exception e) {
            log.error("上传并对话失败", e);
            throw new RuntimeException("处理失败: " + e.getMessage(), e);
        }
    }
}
