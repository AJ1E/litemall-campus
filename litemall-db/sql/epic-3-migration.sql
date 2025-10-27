-- =============================================
-- Epic 3: 交易流程与支付 - 数据库迁移脚本
-- 生成日期: 2025-10-27
-- 开发者: bmm-dev
-- =============================================

USE litemall;

-- =============================================
-- 1. 扩展 litemall_order 表（订单主表）
-- =============================================

-- 检查并添加 delivery_type 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA='litemall' AND TABLE_NAME='litemall_order' AND COLUMN_NAME='delivery_type');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE `litemall_order` ADD COLUMN `delivery_type` TINYINT DEFAULT 1 COMMENT ''配送方式: 1-学生快递员, 2-自提'' AFTER `freight_price`',
    'SELECT ''delivery_type already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;

-- 检查并添加 pickup_code 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA='litemall' AND TABLE_NAME='litemall_order' AND COLUMN_NAME='pickup_code');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `litemall_order` ADD COLUMN `pickup_code` VARCHAR(4) COMMENT ''自提取件码（4位数字）'' AFTER `delivery_type`',
    'SELECT ''pickup_code already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;

-- 检查并添加 courier_id 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA='litemall' AND TABLE_NAME='litemall_order' AND COLUMN_NAME='courier_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `litemall_order` ADD COLUMN `courier_id` INT COMMENT ''快递员用户ID（配送方式=1时填写）'' AFTER `pickup_code`',
    'SELECT ''courier_id already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;

-- 检查并添加 seller_id 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA='litemall' AND TABLE_NAME='litemall_order' AND COLUMN_NAME='seller_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `litemall_order` ADD COLUMN `seller_id` INT NOT NULL DEFAULT 0 COMMENT ''卖家用户ID'' AFTER `user_id`',
    'SELECT ''seller_id already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;

-- 检查并添加 cancel_reason 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA='litemall' AND TABLE_NAME='litemall_order' AND COLUMN_NAME='cancel_reason');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `litemall_order` ADD COLUMN `cancel_reason` VARCHAR(200) COMMENT ''取消原因'' AFTER `order_status`',
    'SELECT ''cancel_reason already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;

-- 检查并添加 ship_time 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA='litemall' AND TABLE_NAME='litemall_order' AND COLUMN_NAME='ship_time');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `litemall_order` ADD COLUMN `ship_time` DATETIME COMMENT ''发货时间'' AFTER `add_time`',
    'SELECT ''ship_time already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;

-- 检查并添加 confirm_time 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA='litemall' AND TABLE_NAME='litemall_order' AND COLUMN_NAME='confirm_time');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `litemall_order` ADD COLUMN `confirm_time` DATETIME COMMENT ''确认收货时间'' AFTER `ship_time`',
    'SELECT ''confirm_time already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;

-- 检查并添加索引
CREATE INDEX IF NOT EXISTS `idx_seller_id` ON `litemall_order` (`seller_id`);
CREATE INDEX IF NOT EXISTS `idx_courier_id` ON `litemall_order` (`courier_id`);

-- =============================================
-- 2. 创建 sicau_comment 表（互评表）
-- =============================================

CREATE TABLE IF NOT EXISTS `sicau_comment` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `order_id` INT NOT NULL COMMENT '订单ID',
  `from_user_id` INT NOT NULL COMMENT '评价者用户ID',
  `to_user_id` INT NOT NULL COMMENT '被评价者用户ID',
  `role` TINYINT NOT NULL COMMENT '评价者角色: 1-买家评卖家, 2-卖家评买家',
  `rating` TINYINT NOT NULL COMMENT '评分: 1-5星',
  `tags` JSON COMMENT '标签（数组）["描述相符","态度友好"]',
  `content` VARCHAR(500) COMMENT '文字评价',
  `reply` VARCHAR(500) COMMENT '回复评价（被评价者）',
  `is_anonymous` BOOLEAN DEFAULT FALSE COMMENT '是否匿名评价',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_to_user_id` (`to_user_id`),
  UNIQUE KEY `uk_order_from_role` (`order_id`, `from_user_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='买卖双方互评表';

-- =============================================
-- 3. 创建 sicau_report 表（举报申诉表）
-- =============================================

CREATE TABLE IF NOT EXISTS `sicau_report` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `order_id` INT NOT NULL COMMENT '订单ID',
  `reporter_id` INT NOT NULL COMMENT '举报人用户ID',
  `reported_id` INT NOT NULL COMMENT '被举报人用户ID',
  `type` TINYINT NOT NULL COMMENT '举报类型: 1-描述不符, 2-质量问题, 3-虚假发货, 4-其他',
  `reason` TEXT NOT NULL COMMENT '举报原因详细描述',
  `images` JSON COMMENT '证据图片URL数组',
  `status` TINYINT DEFAULT 0 COMMENT '处理状态: 0-待处理, 1-处理中, 2-已解决, 3-已驳回',
  `handler_admin_id` INT COMMENT '处理管理员ID',
  `handle_result` TEXT COMMENT '处理结果说明',
  `handle_time` DATETIME COMMENT '处理时间',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_reporter_id` (`reporter_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单举报申诉表';

-- =============================================
-- 4. 创建 sicau_order_refund 表（退款记录表）
-- =============================================

CREATE TABLE IF NOT EXISTS `sicau_order_refund` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `order_id` INT NOT NULL COMMENT '订单ID',
  `refund_sn` VARCHAR(64) NOT NULL COMMENT '退款单号',
  `refund_amount` DECIMAL(10,2) NOT NULL COMMENT '退款金额',
  `refund_reason` VARCHAR(200) COMMENT '退款原因',
  `refund_type` TINYINT NOT NULL COMMENT '退款类型: 1-用户主动取消, 2-超时未支付, 3-举报退款',
  `refund_status` TINYINT DEFAULT 0 COMMENT '退款状态: 0-待退款, 1-退款中, 2-退款成功, 3-退款失败',
  `refund_time` DATETIME COMMENT '退款成功时间',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_refund_sn` (`refund_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单退款记录表';

-- =============================================
-- 5. 初始化评价标签数据
-- =============================================

-- 创建评价标签配置表
CREATE TABLE IF NOT EXISTS `sicau_comment_tags` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `role` TINYINT NOT NULL COMMENT '角色: 1-买家评卖家, 2-卖家评买家',
  `tag_name` VARCHAR(20) NOT NULL COMMENT '标签名称',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价标签配置表';

-- 插入买家评卖家标签
INSERT INTO `sicau_comment_tags` (`role`, `tag_name`, `sort_order`) VALUES
(1, '描述相符', 1),
(1, '态度友好', 2),
(1, '响应及时', 3),
(1, '包装完好', 4),
(1, '物超所值', 5),
(1, '新旧如描述', 6);

-- 插入卖家评买家标签
INSERT INTO `sicau_comment_tags` (`role`, `tag_name`, `sort_order`) VALUES
(2, '好买家', 1),
(2, '付款及时', 2),
(2, '沟通愉快', 3),
(2, '确认收货快', 4);

-- =============================================
-- 完成提示
-- =============================================
SELECT 'Epic 3 数据库迁移完成！' AS message;
SELECT '已创建/扩展表: litemall_order(+8字段), sicau_comment, sicau_report, sicau_order_refund, sicau_comment_tags' AS info;
SELECT '已初始化: 10条评价标签数据' AS data_info;
