package org.albedo.vllmpt.module.file.entity.dto;

import lombok.Data;

@Data
public class FileUploadDTO {

    String objectName;
    Double fileSize;
    String MimeType;
    String businessId;
    String etag;

}
