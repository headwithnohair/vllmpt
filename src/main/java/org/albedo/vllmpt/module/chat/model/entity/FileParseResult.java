package org.albedo.vllmpt.module.chat.model.entity;


import lombok.Data;

@Data
public class FileParseResult {
    String mineType;
    boolean supportIndex;
    String fileId;
    String taskId;


}
