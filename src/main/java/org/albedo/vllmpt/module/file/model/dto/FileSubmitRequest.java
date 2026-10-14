package org.albedo.vllmpt.module.file.model.dto;

import lombok.Data;

/**
 * 提交异步解析任务请求体（场景6），POST /api/file/task/submit 用。
 * <p>
 * 对应流程：前端用 presignedUrl PUT 直传 vllmpt-temp 桶成功后，带 objectName 调本接口。
 */
@Data
public class FileSubmitRequest {

    /** 临时桶（vllmpt-temp）中的对象名，必填 */
    private String objectName;

    /** 原始文件名（可选；缺省时取 objectName，用于展示） */
    private String fileName;

    /**
     * 文件 etag（可选）：PUT 直传的响应头里有，带上则服务端做一致性校验，
     * 防止"上传中断导致文件不完整"还继续处理。
     */
    private String etag;

    /** 用户 ID（可选，0/空=未登录/系统任务） */
    private Long userId;
}
