package org.albedo.vllmpt.chat.model.entity;

import lombok.Data;

@Data
public class Attachment {
    private String type;    // "image", "video", "audio", "pdf", "doc", "txt"
    private String url;     // 已上传到MinIO的临时URL或fileId
    private String name;    // 原文件名（可选）
}