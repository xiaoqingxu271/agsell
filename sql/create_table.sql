-- ============================================================
-- agsell 数据库初始化脚本
-- 创建数据库 + 用户模块相关表
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `agsell` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `agsell`;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录用户名（手机号）',
    `password`    VARCHAR(255) NOT NULL COMMENT '加密密码（BCrypt）',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `openid`      VARCHAR(100) DEFAULT NULL COMMENT '微信小程序openid',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0=禁用 1=正常',
    `login_ip`    VARCHAR(50)  DEFAULT NULL COMMENT '最后登录IP',
    `login_time`  DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_openid` (`openid`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 管理员表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_admin`;
CREATE TABLE `sys_admin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '加密密码（BCrypt）',
    `real_name`   VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' COMMENT '角色 ADMIN/OPERATOR',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0=禁用 1=正常',
    `login_ip`    VARCHAR(50)  DEFAULT NULL COMMENT '最后登录IP',
    `login_time`  DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表';

-- ------------------------------------------------------------
-- 用户收货地址表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_address`;
CREATE TABLE `user_address` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `receiver`    VARCHAR(50)  NOT NULL COMMENT '收件人姓名',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号',
    `province`    VARCHAR(50)  NOT NULL COMMENT '省',
    `city`        VARCHAR(50)  NOT NULL COMMENT '市',
    `district`    VARCHAR(50)  NOT NULL COMMENT '区/县',
    `detail`      VARCHAR(255) NOT NULL COMMENT '详细地址',
    `is_default`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认地址 0=否 1=是',
    `tag`         VARCHAR(20)  DEFAULT NULL COMMENT '标签 家/公司/学校',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_user_default` (`user_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收货地址表';

-- ------------------------------------------------------------
-- 初始化数据
-- ------------------------------------------------------------

-- 默认管理员（密码: admin123，BCrypt加密）
INSERT INTO `sys_admin` (`username`, `password`, `real_name`, `role`, `status`)
VALUES ('admin', '$2a$10$Avfvk6Or0vvfd1NQFAwvMu77vstrDblceh0U3AK7GXbPPiz/o9YHe', '系统管理员', 'ADMIN', 1);

-- 默认测试用户（密码: test123，BCrypt加密）
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `phone`, `status`)
VALUES ('13800138000', '$2a$10$fxuJy2oVABCIJkkR7VyFaucIfH6vxl96ey2tngESm85RaFLZ1roD2', '测试用户', '13800138000', 1);
