-- ==========================================
-- 每日 Token 配额相关表（用户 / 配额 / 每日用量）
--
-- 执行方式（二选一）：
--   1) 用 MySQL 客户端直接打开本文件执行
--   2) 命令行导入（PowerShell 不支持 < 重定向，需先 docker cp 进容器，已实测可用）：
--        docker cp doc/sql/init_user_quota.sql vllmpt-mysql:/tmp/init_user_quota.sql
--        docker exec vllmpt-mysql sh -c "mysql -uroot -proot123 vllmpt_dev < /tmp/init_user_quota.sql"
--        docker exec vllmpt-mysql rm -f /tmp/init_user_quota.sql
--
-- 说明：
--   * 全部使用 CREATE TABLE IF NOT EXISTS + 幂等插入，可重复执行
--   * 三张表都带 `deleted` 列，因为 application.yml 里全局配置了
--     logic-delete-field: deleted，MyBatis-Plus 会给所有 SQL 自动拼 `deleted = 0`
--   * 时间字段依赖数据库默认值填充（项目里没有 MetaObjectHandler）
-- ==========================================


-- ------------------------------------------
-- 表 1：用户表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
  `username`      VARCHAR(64)  NOT NULL                COMMENT '登录名',
  `nickname`      VARCHAR(64)           DEFAULT NULL   COMMENT '昵称',
  `email`         VARCHAR(128)          DEFAULT NULL   COMMENT '邮箱',
  `phone`         VARCHAR(32)           DEFAULT NULL   COMMENT '手机号',
  `password_hash` VARCHAR(128)          DEFAULT NULL   COMMENT '密码哈希（不存明文）',
  `status`        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1=启用 0=禁用',
  `deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';


-- ------------------------------------------
-- 表 2：用户 Token 配额表
-- 每日额度从这里读取；used_today / used_month 是 MySQL 侧的对账冗余，
-- 热路径以 Redis 的计数为准，不要用这两列做限额判断。
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_user_token_quota` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT       NOT NULL                COMMENT '用户 ID（sys_user.id）',
  `daily_limit`    BIGINT       NOT NULL DEFAULT 100000 COMMENT '每日 token 配额上限',
  `monthly_limit`  BIGINT       NOT NULL DEFAULT 0      COMMENT '每月 token 配额上限，0=不限制',
  `used_today`     BIGINT       NOT NULL DEFAULT 0      COMMENT '今日已用 token（对账冗余，非限额依据）',
  `used_month`     BIGINT       NOT NULL DEFAULT 0      COMMENT '本月已用 token（对账冗余，非限额依据）',
  `quota_enabled`  TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用配额：1=启用 0=不限制',
  `effective_from` DATETIME              DEFAULT NULL   COMMENT '配额生效开始时间，NULL=立即生效',
  `effective_to`   DATETIME              DEFAULT NULL   COMMENT '配额生效结束时间，NULL=长期有效',
  `remark`         VARCHAR(255)          DEFAULT NULL   COMMENT '备注（如：活动赠额）',
  `deleted`        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户 Token 配额表';


-- ------------------------------------------
-- 表 3：用户每日 Token 用量表（按模型维度一行）
--
-- 唯一键 uk_user_date_model 是给「对账落库」用的：
--   INSERT INTO ... ON DUPLICATE KEY UPDATE
--       total_tokens = total_tokens + 本次增量      -- 累加，不能写成 = 新值
-- 重复执行对账任务不会重复计数。
--
-- 当日合计不要单独存一行，用 SUM() 聚合：
--   SELECT SUM(total_tokens) FROM ai_user_daily_token_usage
--    WHERE user_id = ? AND stat_date = ? AND deleted = 0;
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_user_daily_token_usage` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`           BIGINT        NOT NULL                COMMENT '用户 ID（sys_user.id）',
  `stat_date`         DATE          NOT NULL                COMMENT '统计日期（Asia/Shanghai）',
  `model_name`        VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '模型名（对应 ai.models[*].model-id）',
  `prompt_tokens`     BIGINT        NOT NULL DEFAULT 0      COMMENT '输入 token 累计',
  `completion_tokens` BIGINT        NOT NULL DEFAULT 0      COMMENT '输出 token 累计',
  `total_tokens`      BIGINT        NOT NULL DEFAULT 0      COMMENT '总 token 累计',
  `request_count`     INT           NOT NULL DEFAULT 0      COMMENT '请求次数',
  `estimated_cost`    DECIMAL(16,6) NOT NULL DEFAULT 0      COMMENT '预估费用（模型单价 × total_tokens）',
  `deleted`           TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
  `updated_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date_model` (`user_id`, `stat_date`, `model_name`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_user_date` (`user_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户每日 Token 用量表（按模型维度）';


-- ==========================================
-- 初始化数据（幂等，可重复执行）
-- ==========================================

-- 测试用户，id 固定为 1，方便 Redis 侧的键名（ai:quota:1:20260923）直接可用
INSERT INTO `sys_user` (`id`, `username`, `nickname`, `email`, `status`, `deleted`)
VALUES (1, 'test_user', '测试用户', 'test@example.com', 1, 0)
ON DUPLICATE KEY UPDATE `username` = 'test_user';

-- 测试用户的默认配额：每日 10 万 token，月额度不限制
INSERT INTO `ai_user_token_quota` (`user_id`, `daily_limit`, `monthly_limit`, `quota_enabled`, `remark`, `deleted`)
VALUES (1, 100000, 0, 1, '默认初始化配额', 0)
ON DUPLICATE KEY UPDATE `user_id` = 1;
