-- Migration script for Story 1.1: Add credit_score to litemall_user table
-- Date: 2024
-- Description: Add credit score field for campus credit system

USE litemall;

-- Add credit_score column to litemall_user table
ALTER TABLE `litemall_user` 
ADD COLUMN `credit_score` INT NOT NULL DEFAULT 100 COMMENT '信用积分，默认100分' 
AFTER `status`;

-- Add index for credit_score to improve query performance
CREATE INDEX `idx_credit_score` ON `litemall_user`(`credit_score`);

-- Create sicau_student_auth table for student authentication
CREATE TABLE `sicau_student_auth` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `user_id` INT(11) NOT NULL COMMENT '用户ID，关联litemall_user.id',
  `student_no` VARCHAR(255) NOT NULL COMMENT '学号（AES-256-GCM加密）',
  `real_name` VARCHAR(255) NOT NULL COMMENT '真实姓名（AES-256-GCM加密）',
  `id_card` VARCHAR(255) DEFAULT NULL COMMENT '身份证号（AES-256-GCM加密）',
  `phone` VARCHAR(255) DEFAULT NULL COMMENT '手机号（AES-256-GCM加密）',
  `college` VARCHAR(100) DEFAULT NULL COMMENT '学院',
  `major` VARCHAR(100) DEFAULT NULL COMMENT '专业',
  `student_card_url` VARCHAR(255) DEFAULT NULL COMMENT '学生证照片URL',
  `status` TINYINT(3) NOT NULL DEFAULT 0 COMMENT '认证状态：0-未认证，1-审核中，2-已认证，3-认证失败',
  `fail_reason` VARCHAR(255) DEFAULT NULL COMMENT '认证失败原因',
  `submit_time` DATETIME DEFAULT NULL COMMENT '提交认证时间',
  `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `audit_admin_id` INT(11) DEFAULT NULL COMMENT '审核管理员ID',
  `auditor` VARCHAR(63) DEFAULT NULL COMMENT '审核人',
  `add_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  `deleted` TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_status` (`status`),
  KEY `idx_submit_time` (`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='川农学生认证表';
