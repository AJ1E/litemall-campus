-- ================================================
-- 公益捐赠模块数据库表
-- Epic 6: 公益捐赠通道
-- ================================================

-- 1. 捐赠记录表
CREATE TABLE IF NOT EXISTS `sicau_donation` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '捐赠者用户ID',
  `category` TINYINT NOT NULL COMMENT '分类: 1-衣物, 2-文具, 3-书籍, 4-其他',
  `quantity` INT NOT NULL COMMENT '数量',
  `images` VARCHAR(1000) NOT NULL COMMENT '物品照片URL数组（JSON格式，1-3张）',
  `pickup_type` TINYINT NOT NULL COMMENT '取件方式: 1-自送至捐赠站点, 2-预约志愿者上门',
  `pickup_address` VARCHAR(200) COMMENT '取件地址（上门取件时填写）',
  `pickup_time` DATETIME COMMENT '预约上门时间',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待审核, 1-审核通过, 2-审核拒绝, 3-已完成',
  `reject_reason` VARCHAR(200) COMMENT '拒绝原因',
  `auditor_id` INT COMMENT '审核管理员ID',
  `audit_time` DATETIME COMMENT '审核时间',
  `volunteer_id` INT COMMENT '志愿者ID（上门取件时分配）',
  `finish_time` DATETIME COMMENT '完成时间',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公益捐赠表';

-- 2. 捐赠站点表
CREATE TABLE IF NOT EXISTS `sicau_donation_point` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `campus` VARCHAR(50) NOT NULL COMMENT '校区: 雅安本部, 成都校区',
  `name` VARCHAR(100) NOT NULL COMMENT '站点名称',
  `address` VARCHAR(200) NOT NULL COMMENT '详细地址',
  `contact_name` VARCHAR(50) COMMENT '联系人',
  `contact_phone` VARCHAR(20) COMMENT '联系电话',
  `open_time` VARCHAR(100) COMMENT '开放时间',
  `is_active` BOOLEAN DEFAULT TRUE COMMENT '是否开放',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_campus` (`campus`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='捐赠站点表';

-- 3. 预置捐赠站点数据（雅安校区）
INSERT INTO `sicau_donation_point` (`campus`, `name`, `address`, `contact_name`, `contact_phone`, `open_time`, `is_active`) VALUES
('雅安本部', '图书馆一楼捐赠站', '图书馆一楼大厅东侧', '学生会', '028-86290000', '周一至周五 8:00-18:00', TRUE),
('雅安本部', '西苑食堂爱心驿站', '西苑食堂二楼', '青年志愿者协会', '028-86290001', '周一至周日 11:00-13:00', TRUE),
('成都校区', '教学楼B座捐赠点', '教学楼B座一楼', '成都校区学生会', '028-86290002', '周一至周五 9:00-17:00', TRUE);

-- 4. 扩展 litemall_user 表（新增徽章和捐赠次数字段）
ALTER TABLE `litemall_user` 
ADD COLUMN IF NOT EXISTS `badges` VARCHAR(500) COMMENT '用户徽章（JSON数组）["爱心大使", "公益达人"]' AFTER `nickname`,
ADD COLUMN IF NOT EXISTS `donation_count` INT DEFAULT 0 COMMENT '累计捐赠次数' AFTER `badges`;
