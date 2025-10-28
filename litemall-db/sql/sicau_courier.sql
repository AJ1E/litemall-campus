-- =====================================================
-- Story 4.1: 快递员注册申请
-- 创建 sicau_courier 表
-- =====================================================

DROP TABLE IF EXISTS `sicau_courier`;

CREATE TABLE `sicau_courier` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '用户ID',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待审核, 1-已通过, 2-已拒绝, 3-已取消资格',
  `apply_reason` VARCHAR(200) COMMENT '申请理由',
  `reject_reason` VARCHAR(200) COMMENT '拒绝理由（审核不通过时填写）',
  `total_orders` INT DEFAULT 0 COMMENT '累计配送订单数',
  `total_income` DECIMAL(10,2) DEFAULT 0.00 COMMENT '累计收入（元）',
  `timeout_count` INT DEFAULT 0 COMMENT '超时次数',
  `complaint_count` INT DEFAULT 0 COMMENT '被投诉次数',
  `apply_time` DATETIME COMMENT '申请时间',
  `approve_time` DATETIME COMMENT '审核通过时间',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  UNIQUE KEY `uk_user_id` (`user_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生快递员表';

-- =====================================================
-- 测试数据（可选）
-- =====================================================

-- 插入一条待审核的快递员申请
INSERT INTO `sicau_courier` 
  (`user_id`, `status`, `apply_reason`, `apply_time`) 
VALUES 
  (1, 0, '我是大三学生，课余时间充足，想通过配送赚取生活费', NOW());

-- 插入一条已通过的快递员
INSERT INTO `sicau_courier` 
  (`user_id`, `status`, `apply_reason`, `apply_time`, `approve_time`, `total_orders`, `total_income`) 
VALUES 
  (2, 1, '有配送经验，想为同学服务', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), 15, 60.00);

-- 插入一条已取消资格的快递员（超时3次）
INSERT INTO `sicau_courier` 
  (`user_id`, `status`, `apply_reason`, `apply_time`, `approve_time`, `timeout_count`) 
VALUES 
  (3, 3, '想赚零花钱', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 29 DAY), 3);
