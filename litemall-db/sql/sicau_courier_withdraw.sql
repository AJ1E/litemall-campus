-- 快递员提现记录表
CREATE TABLE `sicau_courier_withdraw` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `courier_id` INT NOT NULL COMMENT '快递员用户ID',
  `withdraw_sn` VARCHAR(64) NOT NULL COMMENT '提现单号',
  `withdraw_amount` DECIMAL(10,2) NOT NULL COMMENT '提现金额（元）',
  `fee_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '手续费（元）',
  `actual_amount` DECIMAL(10,2) NOT NULL COMMENT '实际到账金额（元）',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-已到账, 2-失败',
  `wx_transfer_id` VARCHAR(100) COMMENT '微信付款单号',
  `fail_reason` VARCHAR(200) COMMENT '失败原因',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `success_time` DATETIME COMMENT '到账时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_courier_id` (`courier_id`),
  INDEX `idx_withdraw_sn` (`withdraw_sn`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递员提现记录表';
