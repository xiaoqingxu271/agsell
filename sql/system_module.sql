-- ============================================================
-- 系统管理模块数据库变更（RBAC 三级角色 + 系统配置 + 操作日志）
-- 可重复执行（幂等）
-- ============================================================

-- 1. 内置 admin 账号升级为超级管理员（幂等：仅当不存在 SUPER_ADMIN 时执行）
SET @super_cnt = (SELECT COUNT(*) FROM sys_admin WHERE role = 'SUPER_ADMIN');
UPDATE sys_admin
SET role = 'SUPER_ADMIN'
WHERE username = 'admin'
  AND role <> 'SUPER_ADMIN'
  AND @super_cnt = 0;

-- 2. 系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`  VARCHAR(50)  NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(500) DEFAULT NULL COMMENT '配置值',
    `description` VARCHAR(100) DEFAULT NULL COMMENT '配置描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 2.1 初始配置项（幂等 seed）
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
    ('platform_name', '农产品销售系统', '平台名称（小程序/后台标题展示）'),
    ('service_phone', '400-000-0000', '客服电话（个人中心联系客服展示）'),
    ('default_freight', '0', '默认运费（元），订单结算运费兜底值'),
    ('free_shipping_threshold', '0', '满额包邮阈值（元），0 = 不启用包邮')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- 3. 操作日志表
CREATE TABLE IF NOT EXISTS `sys_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admin_id`    BIGINT       NOT NULL COMMENT '操作人ID',
    `admin_name`  VARCHAR(50)  NOT NULL COMMENT '操作人姓名',
    `module`      VARCHAR(50)  NOT NULL COMMENT '操作模块',
    `action`      VARCHAR(50)  NOT NULL COMMENT '操作动作',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '操作内容描述',
    `ip`          VARCHAR(50)  DEFAULT NULL COMMENT '操作IP',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_module` (`module`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
