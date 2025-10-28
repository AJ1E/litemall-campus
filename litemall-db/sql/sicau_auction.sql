-- 拍卖表
CREATE TABLE `sicau_auction` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `goods_id` INT NOT NULL COMMENT '商品ID',
  `seller_id` INT NOT NULL COMMENT '卖家用户ID',
  `start_price` DECIMAL(10,2) NOT NULL COMMENT '起拍价（元）',
  `current_price` DECIMAL(10,2) NOT NULL COMMENT '当前最高价（元）',
  `increment` DECIMAL(10,2) DEFAULT 1.00 COMMENT '加价幅度（元）',
  `deposit` DECIMAL(10,2) NOT NULL COMMENT '保证金（元）',
  `deposit_status` TINYINT DEFAULT 0 COMMENT '保证金状态: 0-待支付, 1-已支付, 2-已退还',
  `status` TINYINT DEFAULT 0 COMMENT '拍卖状态: 0-待支付保证金, 1-进行中, 2-已结束, 3-已成交, 4-已流拍',
  `duration_hours` INT NOT NULL COMMENT '拍卖时长（小时）',
  `start_time` DATETIME COMMENT '开始时间（保证金支付后）',
  `end_time` DATETIME COMMENT '结束时间（动态更新）',
  `extend_count` INT DEFAULT 0 COMMENT '延长次数',
  `highest_bidder_id` INT COMMENT '当前最高出价者用户ID',
  `total_bids` INT DEFAULT 0 COMMENT '出价总次数',
  `order_id` INT COMMENT '成交后生成的订单ID',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_goods_id` (`goods_id`),
  INDEX `idx_seller_id` (`seller_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拍卖表';

-- 出价记录表
CREATE TABLE `sicau_auction_bid` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `auction_id` INT NOT NULL COMMENT '拍卖ID',
  `bidder_id` INT NOT NULL COMMENT '出价者用户ID',
  `bid_price` DECIMAL(10,2) NOT NULL COMMENT '出价（元）',
  `bid_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '出价时间',
  `is_auto_bid` BOOLEAN DEFAULT FALSE COMMENT '是否自动出价（预留）',
  INDEX `idx_auction_id` (`auction_id`),
  INDEX `idx_bidder_id` (`bidder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拍卖出价记录表';

-- 修改 litemall_goods 表，新增拍卖相关字段
ALTER TABLE `litemall_goods` 
ADD COLUMN `is_auction` BOOLEAN DEFAULT FALSE COMMENT '是否拍卖商品' AFTER `is_on_sale`,
ADD COLUMN `auction_id` INT COMMENT '关联拍卖ID' AFTER `is_auction`,
ADD INDEX `idx_auction_id` (`auction_id`);
