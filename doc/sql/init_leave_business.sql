-- ==========================================
-- 通用审批平台 P1：请假业务表与演示余额
--
-- 执行方式（PowerShell 不支持 < 重定向，先 docker cp 进容器）：
--   docker cp doc/sql/init_leave_business.sql vllmpt-mysql:/tmp/init_leave_business.sql
--   docker exec vllmpt-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -proot123 vllmpt_dev < /tmp/init_leave_business.sql"
--   docker exec vllmpt-mysql rm -f /tmp/init_leave_business.sql
--
-- 说明：
--   * CREATE TABLE IF NOT EXISTS + 幂等插入，可重复执行；
--   * leave_request 只存业务字段，审批状态由 approval_instance 管理；
--   * leave_balance 只有年假（ANNUAL）与调休（COMP_TIME）使用；
--   * sys_holiday 为空时按周一至周五计算工作日。
-- ==========================================

-- ------------------------------------------
-- 请假业务表
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `leave_request` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
  `approval_instance_id` BIGINT       DEFAULT NULL COMMENT '关联 approval_instance.id',
  `applicant_id`         BIGINT       NOT NULL,
  `leave_type`           VARCHAR(16)  NOT NULL COMMENT 'ANNUAL/SICK/PERSONAL/COMP_TIME',
  `start_time`           DATETIME     NOT NULL,
  `end_time`             DATETIME     NOT NULL,
  `leave_days`           DECIMAL(4,1) NOT NULL COMMENT '服务端计算，支持 0.5',
  `reason`               VARCHAR(500) NOT NULL,
  `attachment_urls`      JSON         DEFAULT NULL,
  `approval_status`      VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT '冗余状态，便于业务查询',
  `deleted`              TINYINT      NOT NULL DEFAULT 0,
  `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_leave_instance` (`approval_instance_id`),
  KEY `idx_applicant_time` (`applicant_id`, `start_time`),
  KEY `idx_status_time` (`approval_status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假业务表';

-- ------------------------------------------
-- 假期余额表（年假 / 调休）
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `leave_balance` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT        NOT NULL,
  `leave_type`  VARCHAR(16)   NOT NULL COMMENT 'ANNUAL/COMP_TIME',
  `year`        INT           NOT NULL,
  `total_days`  DECIMAL(6,1)  NOT NULL DEFAULT 0,
  `used_days`   DECIMAL(6,1)  NOT NULL DEFAULT 0,
  `locked_days` DECIMAL(6,1)  NOT NULL DEFAULT 0 COMMENT '已提交未结算的占用量',
  `version`     INT           NOT NULL DEFAULT 0,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type_year` (`user_id`, `leave_type`, `year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期余额';

-- ------------------------------------------
-- 节假日 / 调休工作日
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_holiday` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `holiday_date` DATE        NOT NULL,
  `day_type`     VARCHAR(16) NOT NULL COMMENT 'HOLIDAY=放假 / WORKDAY=调休上班',
  `name`         VARCHAR(64) DEFAULT NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_holiday_date` (`holiday_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节假日与调休工作日';

-- 示例（按需取消注释并改成当年真实安排）：
-- INSERT INTO `sys_holiday` (`holiday_date`, `day_type`, `name`) VALUES
--   ('2026-10-01', 'HOLIDAY', '国庆节'),
--   ('2026-10-10', 'WORKDAY', '国庆调休上班')
-- ON DUPLICATE KEY UPDATE `day_type` = VALUES(`day_type`), `name` = VALUES(`name`);

-- ------------------------------------------
-- 演示余额（用户 1=演示员工，用户 2=主管）
-- ------------------------------------------
INSERT INTO `leave_balance`
  (`user_id`, `leave_type`, `year`, `total_days`, `used_days`, `locked_days`, `version`)
VALUES
  (1, 'ANNUAL', YEAR(CURDATE()), 10.0, 0.0, 0.0, 0),
  (1, 'COMP_TIME', YEAR(CURDATE()), 5.0, 0.0, 0.0, 0),
  (2, 'ANNUAL', YEAR(CURDATE()), 10.0, 0.0, 0.0, 0)
ON DUPLICATE KEY UPDATE
  `total_days` = VALUES(`total_days`);
