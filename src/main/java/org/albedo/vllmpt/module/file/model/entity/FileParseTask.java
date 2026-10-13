package org.albedo.vllmpt.module.file.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大文件解析入库任务台账 ai_file_parse_task（场景6）。
 * <p>
 * 建表脚本：doc/sql/init_file_parse_task.sql
 * <p>
 * 与 Redis 进度的职责划分：
 * <ul>
 *   <li>本实体（MySQL）：任务台账，最终状态 PENDING/SUCCESS/FAILED + 统计信息，供前端列表/详情；</li>
 *   <li>Redis（{@code ai:file:task:progress:{taskId}}）：实时阶段进度，供 SSE 推送 / 轮询。</li>
 * </ul>
 */
@Data
@TableName("ai_file_parse_task")
public class FileParseTask {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务 ID（UUID，生产者生成，随 MQ 消息流转） */
    private String taskId;

    /** 用户 ID（0=未登录/系统任务） */
    private Long userId;

    /** 原始文件名 */
    private String fileName;

    /** MinIO 桶名 */
    private String bucket;

    /** MinIO 对象名 */
    private String objectName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String mimeType;

    /** 台账状态：PENDING/SUCCESS/FAILED，取值见 {@link org.albedo.vllmpt.module.file.model.enums.FileTaskStatus} */
    private String status;

    /** 分块数量（成功时回填） */
    private Integer chunkCount;

    /** 失败原因（超长会截断存储） */
    private String errorMsg;

    /** 逻辑删除：0=正常 1=已删除 */
    @TableLogic
    private Integer deleted;

    /** 创建时间（数据库默认值填充） */
    private LocalDateTime createdAt;

    /** 更新时间（数据库 default now + on update 自动刷新） */
    private LocalDateTime updatedAt;
}
