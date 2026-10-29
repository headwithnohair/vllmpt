-- ==========================================
-- 通用审批平台 P0/P1/P3/P4 建表与演示数据
--
-- 执行方式（二选一）：
--   1) 用 MySQL 客户端直接打开本文件执行
--   2) 命令行导入（PowerShell 不支持 < 重定向，先 docker cp 进容器）：
--        docker cp doc/sql/init_approval_platform.sql vllmpt-mysql:/tmp/init_approval_platform.sql
--        docker exec vllmpt-mysql sh -c "mysql -uroot -proot123 vllmpt_dev < /tmp/init_approval_platform.sql"
--        docker exec vllmpt-mysql rm -f /tmp/init_approval_platform.sql
--
-- 说明：
--   * 全部使用 CREATE TABLE IF NOT EXISTS + 幂等插入，可重复执行
--   * 有 deleted 列的表与实体上的 @TableLogic 一一对应；
--     approval_task / approval_record / approval_cc 没有 deleted 列，实体也没有该字段
--   * 演示数据使用固定 ID，仅用于开发环境；正式环境请通过接口创建模板
-- ==========================================


-- ------------------------------------------
-- 组织关系：部门 / 员工档案 / 用户角色
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_department` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门 ID',
  `dept_code`  VARCHAR(64)  NOT NULL                COMMENT '部门编码',
  `dept_name`  VARCHAR(128) NOT NULL                COMMENT '部门名称',
  `parent_id`  BIGINT                DEFAULT NULL   COMMENT '上级部门 ID',
  `leader_id`  BIGINT                DEFAULT NULL   COMMENT '部门负责人 sys_user.id',
  `deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`),
  KEY `idx_leader_id` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门表';

CREATE TABLE IF NOT EXISTS `sys_user_profile` (
  `user_id`           BIGINT      NOT NULL                COMMENT '与 sys_user.id 一致',
  `dept_id`           BIGINT               DEFAULT NULL   COMMENT '所属部门 ID',
  `manager_id`        BIGINT               DEFAULT NULL   COMMENT '直属主管 sys_user.id',
  `hire_date`         DATE                 DEFAULT NULL   COMMENT '入职日期',
  `employment_status` VARCHAR(32) NOT NULL DEFAULT 'REGULAR' COMMENT 'PROBATION / REGULAR / LEFT',
  `job_level`         VARCHAR(32)          DEFAULT NULL   COMMENT '职级',
  `deleted`           TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
  `updated_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_manager_id` (`manager_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工档案表';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT      NOT NULL                COMMENT '用户 ID',
  `role_code`  VARCHAR(64) NOT NULL                COMMENT 'EMPLOYEE / MANAGER / FINANCE / HR / ADMIN / CFO',
  `deleted`    TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_code`),
  KEY `idx_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色表';


-- ------------------------------------------
-- 审批模板与节点
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `approval_template` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板 ID',
  `template_key`     VARCHAR(64)  NOT NULL                COMMENT '模板业务键，如 leave / travel_expense',
  `biz_type`         VARCHAR(64)  NOT NULL                COMMENT '业务类型，如 LEAVE / TRAVEL_EXPENSE',
  `name`             VARCHAR(128) NOT NULL                COMMENT '模板名称',
  `version`          INT          NOT NULL DEFAULT 1      COMMENT '版本号，同一 template_key 递增',
  `status`           VARCHAR(32)  NOT NULL                COMMENT 'DRAFT / PUBLISHED / DISABLED',
  `form_schema`      LONGTEXT              DEFAULT NULL   COMMENT '表单字段定义 JSON',
  `description`      VARCHAR(512)          DEFAULT NULL   COMMENT '说明',
  `ai_fill_enabled`  TINYINT      NOT NULL DEFAULT 1      COMMENT '1=启用 AI 填表 0=关闭',
  `created_by`       BIGINT                DEFAULT NULL   COMMENT '创建人',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_key_version` (`template_key`, `version`),
  KEY `idx_biz_status` (`biz_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批模板表';

CREATE TABLE IF NOT EXISTS `approval_template_node` (
  `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点 ID',
  `template_id`            BIGINT       NOT NULL                COMMENT '模板 ID',
  `node_key`               VARCHAR(64)  NOT NULL                COMMENT '节点键，模板内唯一',
  `node_name`              VARCHAR(128) NOT NULL                COMMENT '节点名称',
  `node_type`              VARCHAR(32)  NOT NULL                COMMENT 'START / APPROVAL / AI_DECISION / CC / CONDITION / END',
  `sort_order`             INT          NOT NULL                COMMENT '顺序号，线性推进依据',
  `assignee_type`          VARCHAR(32)           DEFAULT NULL   COMMENT 'USER / ROLE / DIRECT_MANAGER / DEPT_HEAD / FORM_FIELD / INITIATOR',
  `assignee_config`        LONGTEXT              DEFAULT NULL   COMMENT '审批人配置 JSON',
  `approval_mode`          VARCHAR(32)           DEFAULT NULL   COMMENT 'ANY / ALL / SEQUENTIAL',
  `condition_json`         LONGTEXT              DEFAULT NULL   COMMENT '节点跳过条件 JSON',
  `ai_policy_key`          VARCHAR(64)           DEFAULT NULL   COMMENT 'AI 决策策略键',
  `ai_min_confidence`      DECIMAL(5,4)          DEFAULT NULL   COMMENT 'AI 自动通过最低置信度',
  `ai_on_approve_node_key` VARCHAR(64)           DEFAULT NULL   COMMENT 'AI 通过后跳转节点',
  `ai_on_escalate_node_key` VARCHAR(64)          DEFAULT NULL   COMMENT 'AI 转人工节点',
  `ai_on_reject_action`    VARCHAR(32)           DEFAULT NULL   COMMENT 'REJECT / NEXT_NODE',
  `cc_config`              LONGTEXT              DEFAULT NULL   COMMENT '抄送配置 JSON',
  `sla_hours`              INT                   DEFAULT NULL   COMMENT '节点处理时限（小时）',
  `enabled`                TINYINT      NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
  `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_node` (`template_id`, `node_key`),
  KEY `idx_template_sort` (`template_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批模板节点表';


-- ------------------------------------------
-- 审批实例 / 任务 / 流水 / 抄送
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `approval_instance` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '实例 ID',
  `instance_no`       VARCHAR(64)  NOT NULL                COMMENT '实例编号',
  `biz_type`          VARCHAR(64)  NOT NULL                COMMENT '业务类型',
  `biz_id`            BIGINT       NOT NULL DEFAULT 0      COMMENT '业务表主键，通用提交为 0',
  `template_id`       BIGINT       NOT NULL                COMMENT '创建时固定，模板升级不影响运行中实例',
  `template_version`  INT          NOT NULL                COMMENT '模板版本快照',
  `applicant_id`      BIGINT       NOT NULL                COMMENT '申请人',
  `title`             VARCHAR(255)          DEFAULT NULL   COMMENT '标题',
  `form_data`         LONGTEXT              DEFAULT NULL   COMMENT '表单快照 JSON',
  `facts`             LONGTEXT              DEFAULT NULL   COMMENT '扁平事实 JSON，供条件/AI 使用',
  `status`            VARCHAR(32)  NOT NULL                COMMENT 'DRAFT / RUNNING / APPROVED / REJECTED / CANCELED / TERMINATED',
  `current_node_key`  VARCHAR(64)           DEFAULT NULL   COMMENT '当前节点',
  `current_node_name` VARCHAR(128)          DEFAULT NULL   COMMENT '当前节点名称',
  `parent_instance_id` BIGINT               DEFAULT NULL   COMMENT '父实例（预留）',
  `submit_count`      INT          NOT NULL DEFAULT 1      COMMENT '提交次数',
  `submitted_at`      DATETIME              DEFAULT NULL   COMMENT '提交时间',
  `finished_at`       DATETIME              DEFAULT NULL   COMMENT '结束时间',
  `version`           INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_no` (`instance_no`),
  KEY `idx_applicant_status` (`applicant_id`, `status`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批实例表';

CREATE TABLE IF NOT EXISTS `approval_task` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '任务 ID',
  `task_key`      VARCHAR(160) NOT NULL               COMMENT '幂等键：instanceId:nodeKey:assigneeId:sequenceNo',
  `instance_id`   BIGINT      NOT NULL                COMMENT '实例 ID',
  `node_key`      VARCHAR(64) NOT NULL                COMMENT '节点键',
  `node_name`     VARCHAR(128)         DEFAULT NULL   COMMENT '节点名称',
  `assignee_id`   BIGINT      NOT NULL                COMMENT '审批人',
  `assignee_type` VARCHAR(32)          DEFAULT NULL   COMMENT '审批人解析类型',
  `task_type`     VARCHAR(32) NOT NULL DEFAULT 'APPROVAL' COMMENT 'APPROVAL / CC',
  `status`        VARCHAR(32) NOT NULL                COMMENT 'WAITING / PENDING / APPROVED / REJECTED / SKIPPED / TRANSFERRED / CANCELED',
  `approval_mode` VARCHAR(32)          DEFAULT NULL   COMMENT 'ANY / ALL / SEQUENTIAL',
  `action`        VARCHAR(32)          DEFAULT NULL   COMMENT '最终动作',
  `comment`       VARCHAR(500)         DEFAULT NULL   COMMENT '审批意见',
  `due_at`        DATETIME             DEFAULT NULL   COMMENT '处理时限',
  `sequence_no`   INT         NOT NULL DEFAULT 1      COMMENT '顺序审批序号',
  `version`       INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_key` (`task_key`),
  KEY `idx_assignee_status` (`assignee_id`, `status`),
  KEY `idx_instance_node` (`instance_id`, `node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批任务表';

CREATE TABLE IF NOT EXISTS `approval_record` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '流水 ID',
  `instance_id`   BIGINT      NOT NULL                COMMENT '实例 ID',
  `node_key`      VARCHAR(64)          DEFAULT NULL   COMMENT '节点键',
  `task_id`       BIGINT               DEFAULT NULL   COMMENT '任务 ID',
  `operator_id`   BIGINT               DEFAULT NULL   COMMENT '操作人',
  `operator_type` VARCHAR(32) NOT NULL                COMMENT 'USER / AI / SYSTEM',
  `action`        VARCHAR(32) NOT NULL                COMMENT 'SUBMIT / APPROVE / REJECT / WITHDRAW / SKIP ...',
  `comment`       VARCHAR(500)         DEFAULT NULL   COMMENT '意见/说明',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_instance` (`instance_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批流水表';

CREATE TABLE IF NOT EXISTS `approval_cc` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `instance_id` BIGINT      NOT NULL                COMMENT '实例 ID',
  `node_key`    VARCHAR(64) NOT NULL                COMMENT '抄送节点',
  `receiver_id` BIGINT      NOT NULL                COMMENT '接收人',
  `channel`     VARCHAR(64) NOT NULL DEFAULT 'INBOX' COMMENT '渠道，多个用逗号分隔',
  `read_status` TINYINT     NOT NULL DEFAULT 0      COMMENT '0=未读 1=已读',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_node_receiver` (`instance_id`, `node_key`, `receiver_id`),
  KEY `idx_receiver` (`receiver_id`, `read_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批抄送表';

CREATE TABLE IF NOT EXISTS `approval_ai_decision` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `instance_id`     BIGINT       NOT NULL                COMMENT '审批实例 ID',
  `node_key`        VARCHAR(64)  NOT NULL                COMMENT 'AI 决策节点',
  `decision_no`     INT          NOT NULL DEFAULT 1      COMMENT '同节点决策序号',
  `policy_key`      VARCHAR(64)  NOT NULL                COMMENT '决策策略',
  `decision`        VARCHAR(16)  NOT NULL                COMMENT 'APPROVE/REJECT/ESCALATE',
  `confidence`      DECIMAL(4,3) DEFAULT NULL            COMMENT '置信度 0~1',
  `risk_level`      VARCHAR(16)  DEFAULT NULL            COMMENT 'LOW/MEDIUM/HIGH',
  `facts_snapshot`  JSON         DEFAULT NULL            COMMENT '事实快照',
  `rule_result`     JSON         DEFAULT NULL            COMMENT '规则命中',
  `llm_output`      JSON         DEFAULT NULL            COMMENT 'LLM 原始输出',
  `model_name`      VARCHAR(128) DEFAULT NULL            COMMENT '模型名',
  `prompt_version`  VARCHAR(32)  DEFAULT NULL            COMMENT 'Prompt 版本',
  `policy_version`  VARCHAR(32)  DEFAULT NULL            COMMENT '策略版本',
  `latency_ms`      INT          DEFAULT NULL            COMMENT '决策耗时（毫秒）',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_node_decision` (`instance_id`, `node_key`, `decision_no`),
  KEY `idx_instance` (`instance_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 决策审计快照';

-- ------------------------------------------
-- 业务表：差旅费 / 会议室
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `travel_expense` (
  `id`                   BIGINT        NOT NULL AUTO_INCREMENT,
  `approval_instance_id` BIGINT        DEFAULT NULL,
  `applicant_id`         BIGINT        NOT NULL,
  `destination`          VARCHAR(128)  NOT NULL,
  `start_date`           DATE          NOT NULL,
  `end_date`             DATE          NOT NULL,
  `estimated_amount`     DECIMAL(12,2) NOT NULL,
  `currency`             VARCHAR(8)    NOT NULL DEFAULT 'CNY',
  `cost_center`          VARCHAR(64)   DEFAULT NULL,
  `reason`               VARCHAR(500)  NOT NULL,
  `invoice_urls`         JSON          DEFAULT NULL,
  `approval_status`      VARCHAR(16)   NOT NULL DEFAULT 'RUNNING',
  `deleted`              TINYINT       NOT NULL DEFAULT 0,
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_travel_instance` (`approval_instance_id`),
  KEY `idx_travel_applicant` (`applicant_id`, `start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='差旅费申请业务表';

CREATE TABLE IF NOT EXISTS `meeting_room` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `room_code`  VARCHAR(32)  NOT NULL,
  `name`       VARCHAR(128) NOT NULL,
  `capacity`   INT          NOT NULL DEFAULT 0,
  `location`   VARCHAR(128) DEFAULT NULL,
  `enabled`    TINYINT      NOT NULL DEFAULT 1,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_code` (`room_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议室主数据';

CREATE TABLE IF NOT EXISTS `meeting_room_booking` (
  `id`                   BIGINT      NOT NULL AUTO_INCREMENT,
  `approval_instance_id` BIGINT      DEFAULT NULL,
  `applicant_id`         BIGINT      NOT NULL,
  `room_id`              BIGINT      NOT NULL,
  `start_time`           DATETIME    NOT NULL,
  `end_time`             DATETIME    NOT NULL,
  `attendees`            INT         NOT NULL DEFAULT 1,
  `purpose`              VARCHAR(500) NOT NULL,
  `equipment`            JSON        DEFAULT NULL,
  `has_external_guest`   TINYINT     NOT NULL DEFAULT 0,
  `approval_status`      VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
  `deleted`              TINYINT     NOT NULL DEFAULT 0,
  `created_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meeting_instance` (`approval_instance_id`),
  KEY `idx_room_time` (`room_id`, `start_time`, `end_time`),
  KEY `idx_meeting_status` (`approval_status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议室预约业务表';

-- ------------------------------------------
-- 通知发件箱
-- ------------------------------------------
CREATE TABLE IF NOT EXISTS `approval_notification` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `notify_key`    VARCHAR(128) NOT NULL COMMENT '幂等键',
  `instance_id`   BIGINT       DEFAULT NULL,
  `task_id`       BIGINT       DEFAULT NULL,
  `receiver_id`   BIGINT       NOT NULL,
  `notify_type`   VARCHAR(24)  NOT NULL COMMENT 'TODO/CC/RESULT/REMIND',
  `channel`       VARCHAR(16)  NOT NULL DEFAULT 'INBOX',
  `title`         VARCHAR(200) DEFAULT NULL,
  `content`       VARCHAR(2000) DEFAULT NULL,
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  `retry_count`   INT          NOT NULL DEFAULT 0,
  `next_retry_at` DATETIME     DEFAULT NULL,
  `sent_at`       DATETIME     DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_key` (`notify_key`),
  KEY `idx_notify_receiver` (`receiver_id`, `type`, `created_at`),
  KEY `idx_notify_retry` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批通知发件箱';


-- ==========================================
-- 演示数据（仅开发环境，固定 ID）
-- ==========================================

INSERT INTO `sys_department` (`id`, `dept_code`, `dept_name`, `parent_id`, `leader_id`, `deleted`)
VALUES
  (1, 'HQ', '总经办', NULL, 5, 0),
  (2, 'RD', '研发部', 1, 2, 0),
  (3, 'FIN', '财务部', 1, 3, 0),
  (4, 'HR', '人力资源部', 1, 4, 0)
ON DUPLICATE KEY UPDATE `dept_name` = VALUES(`dept_name`), `leader_id` = VALUES(`leader_id`);

INSERT INTO `sys_user` (`id`, `username`, `nickname`, `email`, `status`, `deleted`)
VALUES
  (2, 'manager_zhang', '张主管', 'manager@example.com', 1, 0),
  (3, 'finance_li', '李财务', 'finance@example.com', 1, 0),
  (4, 'hr_wang', '王HR', 'hr@example.com', 1, 0),
  (5, 'cfo_zhao', '赵总', 'cfo@example.com', 1, 0)
ON DUPLICATE KEY UPDATE `nickname` = VALUES(`nickname`), `email` = VALUES(`email`);

INSERT INTO `sys_user_profile` (`user_id`, `dept_id`, `manager_id`, `hire_date`, `employment_status`, `job_level`, `deleted`)
VALUES
  (1, 2, 2, '2024-03-01', 'REGULAR', 'P5', 0),
  (2, 2, 5, '2020-06-01', 'REGULAR', 'M2', 0),
  (3, 3, 5, '2019-09-01', 'REGULAR', 'P6', 0),
  (4, 4, 5, '2021-01-11', 'REGULAR', 'M1', 0),
  (5, 1, NULL, '2018-01-01', 'REGULAR', 'M3', 0)
ON DUPLICATE KEY UPDATE `dept_id` = VALUES(`dept_id`), `manager_id` = VALUES(`manager_id`);

INSERT INTO `sys_user_role` (`user_id`, `role_code`, `deleted`)
VALUES
  (2, 'MANAGER', 0),
  (3, 'FINANCE', 0),
  (4, 'HR', 0),
  (5, 'CFO', 0),
  -- 用户 5 同时具备 ADMIN，用于模板管理演示
  (5, 'ADMIN', 0)
ON DUPLICATE KEY UPDATE `role_code` = VALUES(`role_code`);


-- ------------------------------------------
-- 演示模板 1：请假（P1 请假业务插件接管校验与余额，P3 AI 决策）
-- START → AI_DECISION → 直属主管(ANY) → CC(HR) → END
-- AI 决策策略未注册/异常/低置信度时转人工
-- ------------------------------------------
INSERT INTO `approval_template`
  (`id`, `template_key`, `biz_type`, `name`, `version`, `status`, `form_schema`, `description`, `ai_fill_enabled`, `created_by`, `deleted`)
VALUES
  (1, 'leave', 'LEAVE', '请假申请', 1, 'PUBLISHED',
   '{"fields":[{"name":"leaveType","label":"请假类型","type":"STRING","required":true},{"name":"startTime","label":"开始时间","type":"STRING","required":true},{"name":"endTime","label":"结束时间","type":"STRING","required":true},{"name":"reason","label":"请假事由","type":"STRING","required":true},{"name":"attachmentUrls","label":"证明材料","type":"ARRAY","required":false}]}',
   'P1 请假业务：工作日/半天计算、余额锁定、时间冲突校验', 1, 1, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`), `form_schema` = VALUES(`form_schema`);

INSERT INTO `approval_template_node`
  (`id`, `template_id`, `node_key`, `node_name`, `node_type`, `sort_order`, `assignee_type`, `assignee_config`,
   `approval_mode`, `condition_json`, `ai_policy_key`, `ai_min_confidence`, `ai_on_approve_node_key`,
   `ai_on_escalate_node_key`, `ai_on_reject_action`, `cc_config`, `sla_hours`, `enabled`)
VALUES
  (1, 1, 'start', '发起', 'START', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),
  (2, 1, 'ai_decision', 'AI 预审', 'AI_DECISION', 2, NULL, NULL, NULL, NULL, 'leavePolicy', 0.8000, 'cc_hr', 'manager_approval', 'REJECT', NULL, NULL, 1),
  (3, 1, 'manager_approval', '直属主管审批', 'APPROVAL', 3, 'DIRECT_MANAGER', NULL, 'ANY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 24, 1),
  (4, 1, 'cc_hr', '抄送 HR', 'CC', 4, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[{"type":"ROLE","roleCode":"HR"}]', NULL, 1),
  (5, 1, 'end', '结束', 'END', 5, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1)
ON DUPLICATE KEY UPDATE `node_name` = VALUES(`node_name`), `node_type` = VALUES(`node_type`), `sort_order` = VALUES(`sort_order`);


-- ------------------------------------------
-- 演示模板 2：差旅费（验证条件跳过 + 多级审批）
-- START → AI_DECISION → 主管(ANY) → 财务(ALL, amount>5000) → CFO(ANY, amount>20000) → CC(HR) → END
-- ------------------------------------------
INSERT INTO `approval_template`
  (`id`, `template_key`, `biz_type`, `name`, `version`, `status`, `form_schema`, `description`, `ai_fill_enabled`, `created_by`, `deleted`)
VALUES
  (2, 'travel_expense', 'TRAVEL_EXPENSE', '差旅费申请', 1, 'PUBLISHED',
   '{"fields":[{"name":"destination","label":"目的地","type":"STRING","required":true},{"name":"startDate","label":"开始日期","type":"DATE","required":true},{"name":"endDate","label":"结束日期","type":"DATE","required":true},{"name":"estimatedAmount","label":"预计金额","type":"NUMBER","required":true},{"name":"costCenter","label":"成本中心","type":"STRING","required":false},{"name":"reason","label":"出差事由","type":"STRING","required":true},{"name":"invoiceUrls","label":"发票/凭证","type":"ARRAY","required":false}]}',
   'P4 差旅费业务：金额分级走财务/CFO，申请阶段校验日期与金额', 1, 1, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`), `form_schema` = VALUES(`form_schema`);

INSERT INTO `approval_template_node`
  (`id`, `template_id`, `node_key`, `node_name`, `node_type`, `sort_order`, `assignee_type`, `assignee_config`,
   `approval_mode`, `condition_json`, `ai_policy_key`, `ai_min_confidence`, `ai_on_approve_node_key`,
   `ai_on_escalate_node_key`, `ai_on_reject_action`, `cc_config`, `sla_hours`, `enabled`)
VALUES
  (6, 2, 'start', '发起', 'START', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),
  (7, 2, 'ai_decision', 'AI 预审', 'AI_DECISION', 2, NULL, NULL, NULL, NULL, 'travelExpensePolicy', 0.8500, 'finance_approval', 'manager_approval', 'REJECT', NULL, NULL, 1),
  (8, 2, 'manager_approval', '直属主管审批', 'APPROVAL', 3, 'DIRECT_MANAGER', NULL, 'ANY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 24, 1),
  (9, 2, 'finance_approval', '财务审批', 'APPROVAL', 4, 'ROLE', '{"roleCode":"FINANCE"}', 'ALL', '{"field":"amount","op":"GT","value":5000}', NULL, NULL, NULL, NULL, NULL, NULL, 48, 1),
  (10, 2, 'cfo_approval', 'CFO 审批', 'APPROVAL', 5, 'ROLE', '{"roleCode":"CFO"}', 'ANY', '{"field":"amount","op":"GT","value":20000}', NULL, NULL, NULL, NULL, NULL, NULL, 72, 1),
  (11, 2, 'cc_hr', '抄送 HR', 'CC', 6, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[{"type":"ROLE","roleCode":"HR"}]', NULL, 1),
  (12, 2, 'end', '结束', 'END', 7, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1)
ON DUPLICATE KEY UPDATE `node_name` = VALUES(`node_name`), `node_type` = VALUES(`node_type`), `sort_order` = VALUES(`sort_order`);


-- ------------------------------------------
-- 演示模板 3：会议室预约（P4）
-- START → AI_DECISION → 行政管理员(HR, ANY) → CC(HR) → END
-- ------------------------------------------
INSERT INTO `meeting_room` (`id`, `room_code`, `name`, `capacity`, `location`, `enabled`)
VALUES
  (1, 'R301', '301 会议室', 10, '3F', 1),
  (2, 'R302', '302 会议室', 20, '3F', 1),
  (3, 'BOARD', '董事会议室', 30, '5F', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `capacity` = VALUES(`capacity`), `enabled` = VALUES(`enabled`);

INSERT INTO `approval_template`
  (`id`, `template_key`, `biz_type`, `name`, `version`, `status`, `form_schema`, `description`, `ai_fill_enabled`, `created_by`, `deleted`)
VALUES
  (3, 'meeting_room', 'MEETING_ROOM', '会议室预约', 1, 'PUBLISHED',
   '{"fields":[{"name":"roomId","label":"会议室ID","type":"NUMBER","required":true},{"name":"startTime","label":"开始时间","type":"STRING","required":true},{"name":"endTime","label":"结束时间","type":"STRING","required":true},{"name":"attendees","label":"参会人数","type":"NUMBER","required":true},{"name":"purpose","label":"会议用途","type":"STRING","required":true},{"name":"equipment","label":"设备需求","type":"ARRAY","required":false},{"name":"hasExternalGuest","label":"是否有外部访客","type":"BOOLEAN","required":false}]}',
   'P4 会议室业务：容量/开放时间/冲突校验', 1, 1, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`), `form_schema` = VALUES(`form_schema`);

INSERT INTO `approval_template_node`
  (`id`, `template_id`, `node_key`, `node_name`, `node_type`, `sort_order`, `assignee_type`, `assignee_config`,
   `approval_mode`, `condition_json`, `ai_policy_key`, `ai_min_confidence`, `ai_on_approve_node_key`,
   `ai_on_escalate_node_key`, `ai_on_reject_action`, `cc_config`, `sla_hours`, `enabled`)
VALUES
  (13, 3, 'start', '发起', 'START', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),
  (14, 3, 'ai_decision', 'AI 预审', 'AI_DECISION', 2, NULL, NULL, NULL, NULL, 'meetingRoomPolicy', 0.8500, 'cc_hr', 'manager_approval', 'REJECT', NULL, NULL, 1),
  (15, 3, 'manager_approval', '行政管理员审批', 'APPROVAL', 3, 'ROLE', '{"roleCode":"HR"}', 'ANY', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 24, 1),
  (16, 3, 'cc_hr', '抄送 HR', 'CC', 4, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[{"type":"ROLE","roleCode":"HR"}]', NULL, 1),
  (17, 3, 'end', '结束', 'END', 5, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1)
ON DUPLICATE KEY UPDATE `node_name` = VALUES(`node_name`), `node_type` = VALUES(`node_type`), `sort_order` = VALUES(`sort_order`);
