package org.albedo.vllmpt.file.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.file.config.MinioConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 上传图片到 MinIO
     *
     * @param file 图片文件
     * @return 公开访问的 URL
     */
    public String uploadImage(MultipartFile file) {
        try {
            // 1. 生成唯一的文件名
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String fileName = generateFileName(extension);

            // 2. 确保 bucket 存在
            ensureBucketExists();

            // 3. 上传文件
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(fileName)
                                .stream(inputStream, file.getSize(), -1L)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            // 4. 生成公开访问 URL（7天有效期）
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketName())
                            .object(fileName)
                            .expiry(7, TimeUnit.DAYS) // 7天
                            .build()
            );

            log.info("图片上传成功: {}", url);
            return url;

        } catch (Exception e) {
            log.error("图片上传失败", e);
            throw new RuntimeException("图片上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 URL 下载图片并上传到 MinIO
     *
     * @param imageUrl 图片 URL
     * @return MinIO 中的公开访问 URL
     */
    public String uploadImageFromUrl(String imageUrl) {
        try {
            // 1. 下载图片
            java.net.URL url = new java.net.URL(imageUrl);
            try (InputStream inputStream = url.openStream()) {
                byte[] imageBytes = inputStream.readAllBytes();

                // 2. 生成文件名
                String extension = getImageExtensionFromUrl(imageUrl);
                String fileName = generateFileName(extension);

                // 3. 确保 bucket 存在
                ensureBucketExists();

                // 4. 上传到 MinIO
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(fileName)
                                .stream(new java.io.ByteArrayInputStream(imageBytes), (long) imageBytes.length, -1L)
                                .contentType("image/" + extension.replace(".", ""))
                                .build()
                );

                // 5. 生成公开访问 URL（7天有效期）
                String presignedUrl = minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(minioConfig.getBucketName())
                                .object(fileName)
                                .expiry(7, TimeUnit.DAYS) // 7天
                                .build()
                );

                log.info("图片从 URL 上传成功: {}", presignedUrl);
                return presignedUrl;
            }

        } catch (Exception e) {
            log.error("从 URL 上传图片失败: {}", imageUrl, e);
            throw new RuntimeException("从 URL 上传图片失败: " + e.getMessage(), e);
        }
    }

    /**
     * 确保 Bucket 存在
     */
    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .build()
            );
            if (!found) {
                minioClient.makeBucket(
                        io.minio.MakeBucketArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .build()
                );
                log.info("创建 Bucket 成功: {}", minioConfig.getBucketName());
            }
        } catch (Exception e) {
            log.error("检查/创建 Bucket 失败", e);
            throw new RuntimeException("Bucket 操作失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成唯一文件名
     */
    private String generateFileName(String extension) {
        return UUID.randomUUID().toString().replace("-", "") + extension;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg"; // 默认 jpg
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * 从 URL 推断图片扩展名
     */
    private String getImageExtensionFromUrl(String imageUrl) {
        if (imageUrl.contains(".png")) {
            return ".png";
        } else if (imageUrl.contains(".gif")) {
            return ".gif";
        } else if (imageUrl.contains(".webp")) {
            return ".webp";
        } else {
            return ".jpg"; // 默认 jpg
        }
    }
}
