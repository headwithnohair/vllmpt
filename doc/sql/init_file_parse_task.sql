-- ==========================================
-- 大文件解析入库任务台账表（场景6）
--
-- 职责划分（与 Redis 进度共存，不要混用）：
--   * 本表（MySQL）= 任务台账：记录“谁传了什么文件、最终成功还是失败”，支撑前端列表/详情/排查；
--   * Redis（ai:file:task:progress:{taskId}）= 实时进度：stage/percent，供 SSE 推送与轮询。
--
-- 执行方式（二选一）：
--   1) 用 MySQL 客户端直接打开本文件执行
--   2) 命令行导入（PowerShell 不支持 < 重定向，需先 docker cp 进容器，已实测可用）：
--        docker cp doc/sql/init_file_parse_task.sql vllmpt-mysql:/tmp/init_file_parse_task.sql
--        docker exec vllmpt-mysql sh -c "mysql -uroot -proot123 vllmpt_dev < /tmp/init_file_parse_task.sql"
--        docker exec vllmpt-mysql rm -f /tmp/init_file_parse_task.sql
--
-- 说明：
--   * CREATE TABLE IF NOT EXISTS，可重复执行；
--   * 带 `deleted` 列：application.yml 全局配置了 logic-delete-field: deleted，
--     MyBatis-Plus 会给所有 SQL 自动拼 `deleted = 0`；
--   * status 只有三个终态值，中间阶段（PARSING/CHUNKING/EMBEDDING）只存在于 Redis 进度里，
--     避免高频阶段流转打满 MySQL；
--   * updated_at 依赖 ON UPDATE CURRENT_TIMESTAMP 自动刷新（项目里没有 MetaObjectHandler）。
-- ==========================================

CREATE TABLE IF NOT EXISTS `ai_file_parse_task` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`     VARCHAR(64)   NOT NULL                COMMENT '任务 ID（UUID，生产者生成，随 MQ 消息流转）',
  `user_id`     BIGINT        NOT NULL DEFAULT 0      COMMENT '用户 ID（0=未登录/系统任务）',
  `file_name`   VARCHAR(255)           DEFAULT NULL   COMMENT '原始文件名',
  `bucket`      VARCHAR(64)   NOT NULL                COMMENT 'MinIO 桶名',
  `object_name` VARCHAR(512)  NOT NULL                COMMENT 'MinIO 对象名',
  `file_size`   BIGINT        NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
  `mime_type`   VARCHAR(128)           DEFAULT NULL   COMMENT 'MIME 类型',
  `status`      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '台账状态：PENDING/SUCCESS/FAILED（实时进度看 Redis）',
  `chunk_count` INT           NOT NULL DEFAULT 0      COMMENT '分块数量（成功时回填）',
  `error_msg`   VARCHAR(1024)          DEFAULT NULL   COMMENT '失败原因（超长会截断存储）',
  `deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大文件解析入库任务台账（场景6）';
