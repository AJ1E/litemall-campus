-- =============================================
-- Story 2.1: 商品发布 - 数据库迁移脚本
-- Author: bmm-dev
-- Date: 2025-10-28
-- Description: 为 litemall_goods 表添加校园二手交易所需字段
-- =============================================

USE litemall;

-- 1. 为 litemall_goods 表添加新字段
ALTER TABLE `litemall_goods` 
ADD COLUMN `original_price` DECIMAL(10,2) COMMENT '原价（用于展示折扣）' AFTER `retail_price`,
ADD COLUMN `newness` TINYINT DEFAULT 2 COMMENT '新旧程度: 1-全新, 2-几乎全新, 3-轻微使用痕迹, 4-明显使用痕迹' AFTER `original_price`,
ADD COLUMN `purchase_time` DATE COMMENT '购买时间' AFTER `newness`,
ADD COLUMN `user_id` INT NOT NULL COMMENT '发布者用户ID' AFTER `id`,
ADD COLUMN `status` TINYINT DEFAULT 0 COMMENT '商品状态: 0-待审核, 1-上架中, 2-已售出, 3-已下架, 4-违规下架' AFTER `is_on_sale`,
ADD INDEX `idx_user_id` (`user_id`),
ADD INDEX `idx_status` (`status`);

-- 2. 更新已有商品数据（如果有）
-- 将已有商品设置为 user_id = 1（管理员），状态设置为上架中
UPDATE `litemall_goods` 
SET `user_id` = 1, `status` = 1 
WHERE `user_id` IS NULL OR `user_id` = 0;

-- 3. 验证字段已添加
SELECT 
  COLUMN_NAME, 
  COLUMN_TYPE, 
  COLUMN_COMMENT, 
  IS_NULLABLE, 
  COLUMN_DEFAULT
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'litemall' 
  AND TABLE_NAME = 'litemall_goods' 
  AND COLUMN_NAME IN ('user_id', 'status', 'original_price', 'newness', 'purchase_time')
ORDER BY ORDINAL_POSITION;

-- 4. 验证索引已创建
SHOW INDEX FROM `litemall_goods` WHERE Key_name IN ('idx_user_id', 'idx_status');
