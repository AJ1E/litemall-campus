-- 快递员收入流水表
CREATE TABLE IF NOT EXISTS `sicau_courier_income` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `courier_id` INT NOT NULL COMMENT '快递员用户ID',
  `order_id` INT NOT NULL COMMENT '订单ID',
  `income_amount` DECIMAL(10,2) NOT NULL COMMENT '收入金额（元）',
  `distance` DECIMAL(5,2) COMMENT '配送距离（km）',
  `settle_status` TINYINT DEFAULT 0 COMMENT '结算状态: 0-未结算, 1-已结算',
  `settle_time` DATETIME COMMENT '结算时间',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_courier_id` (`courier_id`),
  INDEX `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递员收入流水表';
