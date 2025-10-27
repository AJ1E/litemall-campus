-- Epic 2: 商品发布与管理 - 数据库迁移脚本
-- 执行日期: 2025-10-27
-- 开发者: bmm-dev

-- ============================================
-- 1. 扩展 litemall_goods 表
-- ============================================
-- 检查并添加 user_id 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'litemall' AND TABLE_NAME = 'litemall_goods' AND COLUMN_NAME = 'user_id');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE litemall_goods ADD COLUMN user_id INT NOT NULL DEFAULT 0 COMMENT ''发布者用户ID'' AFTER id',
    'SELECT ''user_id already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 original_price 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'litemall' AND TABLE_NAME = 'litemall_goods' AND COLUMN_NAME = 'original_price');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE litemall_goods ADD COLUMN original_price DECIMAL(10,2) COMMENT ''原价（用于展示折扣）'' AFTER retail_price',
    'SELECT ''original_price already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 newness 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'litemall' AND TABLE_NAME = 'litemall_goods' AND COLUMN_NAME = 'newness');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE litemall_goods ADD COLUMN newness TINYINT DEFAULT 2 COMMENT ''新旧程度: 1-全新, 2-几乎全新, 3-轻微使用痕迹, 4-明显使用痕迹'' AFTER retail_price',
    'SELECT ''newness already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 purchase_time 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'litemall' AND TABLE_NAME = 'litemall_goods' AND COLUMN_NAME = 'purchase_time');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE litemall_goods ADD COLUMN purchase_time DATE COMMENT ''购买时间'' AFTER newness',
    'SELECT ''purchase_time already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 status 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'litemall' AND TABLE_NAME = 'litemall_goods' AND COLUMN_NAME = 'status');
SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE litemall_goods ADD COLUMN status TINYINT DEFAULT 0 COMMENT ''商品状态: 0-待审核, 1-上架中, 2-已售出, 3-已下架, 4-违规下架'' AFTER is_on_sale',
    'SELECT ''status already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_user_id ON litemall_goods(user_id);
CREATE INDEX IF NOT EXISTS idx_status ON litemall_goods(status);

-- ============================================
-- 2. 创建敏感词库表
-- ============================================
CREATE TABLE IF NOT EXISTS `sicau_sensitive_words` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `word` VARCHAR(50) NOT NULL COMMENT '敏感词',
  `type` TINYINT DEFAULT 1 COMMENT '类型: 1-违规交易, 2-黄赌毒, 3-政治敏感, 4-其他',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库表';

-- 初始化敏感词（校园场景）
INSERT INTO `sicau_sensitive_words` (word, type) VALUES
('代考', 1),
('代写论文', 1),
('代写作业', 1),
('刷单', 1),
('买卖学分', 1),
('假证', 1),
('假学历', 1),
('黄色', 2),
('赌博', 2),
('毒品', 2),
('色情', 2),
('反动', 3),
('暴力', 3);

-- ============================================
-- 3. 创建课程教材映射表
-- ============================================
CREATE TABLE IF NOT EXISTS `sicau_course_material` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `course_name` VARCHAR(100) NOT NULL COMMENT '课程名称（如：高等数学A）',
  `book_name` VARCHAR(200) NOT NULL COMMENT '教材名称',
  `isbn` VARCHAR(13) COMMENT 'ISBN编号',
  `semester` VARCHAR(50) COMMENT '适用学期（如：大一上）',
  `college` VARCHAR(100) COMMENT '适用学院（空表示全校通识课）',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_course_name` (`course_name`),
  INDEX `idx_book_name` (`book_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程教材映射表';

-- 初始化四川农业大学常见课程教材数据
INSERT INTO `sicau_course_material` (course_name, book_name, isbn, semester) VALUES
('高等数学A', '高等数学（第七版）上册', '9787040396614', '大一上'),
('高等数学A', '高等数学（第七版）下册', '9787040396621', '大一下'),
('大学英语', '新视野大学英语读写教程1', '9787513533898', '大一上'),
('大学英语', '新视野大学英语读写教程2', '9787513533904', '大一下'),
('大学英语', '新视野大学英语读写教程3', '9787513533911', '大二上'),
('大学英语', '新视野大学英语读写教程4', '9787513533928', '大二下'),
('线性代数', '线性代数（第六版）', '9787040396454', '大一下'),
('概率论与数理统计', '概率论与数理统计（第四版）', '9787040238969', '大二上'),
('大学物理', '大学物理学（第五版）上册', '9787040472059', '大一下'),
('大学物理', '大学物理学（第五版）下册', '9787040472066', '大二上'),
('C语言程序设计', 'C程序设计（第五版）', '9787302481447', '大一上'),
('数据结构', '数据结构（C语言版）', '9787302147510', '大二上'),
('毛泽东思想和中国特色社会主义理论', '毛泽东思想和中国特色社会主义理论体系概论', '9787040494815', '大二上'),
('马克思主义基本原理', '马克思主义基本原理（2021年版）', '9787040566369', '大二下'),
('思想道德与法治', '思想道德与法治（2021年版）', '9787040566420', '大一上'),
('中国近现代史纲要', '中国近现代史纲要（2021年版）', '9787040566376', '大一下');

-- ============================================
-- 4. 创建违规商品记录表
-- ============================================
CREATE TABLE IF NOT EXISTS `sicau_goods_violation` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `goods_id` INT NOT NULL COMMENT '商品ID',
  `user_id` INT NOT NULL COMMENT '发布者用户ID',
  `violation_type` TINYINT NOT NULL COMMENT '违规类型: 1-敏感词, 2-虚假信息, 3-违禁品, 4-其他',
  `violation_content` TEXT COMMENT '违规内容详情',
  `handler_admin_id` INT COMMENT '处理管理员ID',
  `handle_time` DATETIME COMMENT '处理时间',
  `penalty` VARCHAR(100) COMMENT '处罚措施（如：下架商品、扣50积分）',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_goods_id` (`goods_id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='违规商品处理记录表';

-- ============================================
-- 5. 初始化分类数据（6个一级 + 24个二级）
-- ============================================
-- 清空现有分类数据
TRUNCATE TABLE `litemall_category`;

-- 一级分类
INSERT INTO `litemall_category` (id, name, keywords, `desc`, pid, icon_url, pic_url, level, sort_order, add_time, update_time, deleted) VALUES
(1, '教材教辅', '教材,教辅,课本,考研', '教材教辅类商品', 0, '', '', 'L1', 1, NOW(), NOW(), 0),
(2, '电子数码', '手机,电脑,平板,耳机', '电子数码产品', 0, '', '', 'L1', 2, NOW(), NOW(), 0),
(3, '生活用品', '床上用品,洗护,文具', '日常生活用品', 0, '', '', 'L1', 3, NOW(), NOW(), 0),
(4, '服饰鞋包', '衣服,鞋子,包包', '服饰鞋包类', 0, '', '', 'L1', 4, NOW(), NOW(), 0),
(5, '运动器材', '篮球,羽毛球,健身', '运动健身器材', 0, '', '', 'L1', 5, NOW(), NOW(), 0),
(6, '其他', '其他闲置物品', '其他分类', 0, '', '', 'L1', 6, NOW(), NOW(), 0);

-- 二级分类 - 教材教辅
INSERT INTO `litemall_category` (name, keywords, `desc`, pid, icon_url, pic_url, level, sort_order, add_time, update_time, deleted) VALUES
('通识课教材', '高数,英语,思政', '大学通识课程教材', 1, '', '', 'L2', 1, NOW(), NOW(), 0),
('专业课教材', '专业课,教材', '各专业课程教材', 1, '', '', 'L2', 2, NOW(), NOW(), 0),
('考研资料', '考研,真题,辅导书', '考研复习资料', 1, '', '', 'L2', 3, NOW(), NOW(), 0),
('英语四六级', '四级,六级,真题', '英语四六级资料', 1, '', '', 'L2', 4, NOW(), NOW(), 0);

-- 二级分类 - 电子数码
INSERT INTO `litemall_category` (name, keywords, `desc`, pid, icon_url, pic_url, level, sort_order, add_time, update_time, deleted) VALUES
('手机', '手机,智能手机', '智能手机', 2, '', '', 'L2', 1, NOW(), NOW(), 0),
('电脑', '笔记本,台式机', '笔记本电脑和台式机', 2, '', '', 'L2', 2, NOW(), NOW(), 0),
('平板', 'iPad,平板', '平板电脑', 2, '', '', 'L2', 3, NOW(), NOW(), 0),
('数码配件', '耳机,充电器,数据线', '数码产品配件', 2, '', '', 'L2', 4, NOW(), NOW(), 0);

-- 二级分类 - 生活用品
INSERT INTO `litemall_category` (name, keywords, `desc`, pid, icon_url, pic_url, level, sort_order, add_time, update_time, deleted) VALUES
('床上用品', '被子,枕头,床单', '床上用品', 3, '', '', 'L2', 1, NOW(), NOW(), 0),
('洗护用品', '洗发水,沐浴露', '洗护清洁用品', 3, '', '', 'L2', 2, NOW(), NOW(), 0),
('文具用品', '笔记本,笔,文具', '学习文具用品', 3, '', '', 'L2', 3, NOW(), NOW(), 0),
('小家电', '吹风机,台灯,风扇', '小型家用电器', 3, '', '', 'L2', 4, NOW(), NOW(), 0);

-- 二级分类 - 服饰鞋包
INSERT INTO `litemall_category` (name, keywords, `desc`, pid, icon_url, pic_url, level, sort_order, add_time, update_time, deleted) VALUES
('上衣', 'T恤,衬衫,卫衣', '上衣类服饰', 4, '', '', 'L2', 1, NOW(), NOW(), 0),
('裤子', '牛仔裤,休闲裤', '裤子类服饰', 4, '', '', 'L2', 2, NOW(), NOW(), 0),
('鞋子', '运动鞋,帆布鞋', '各类鞋子', 4, '', '', 'L2', 3, NOW(), NOW(), 0),
('包包', '书包,双肩包', '各类包包', 4, '', '', 'L2', 4, NOW(), NOW(), 0);

-- 二级分类 - 运动器材
INSERT INTO `litemall_category` (name, keywords, `desc`, pid, icon_url, pic_url, level, sort_order, add_time, update_time, deleted) VALUES
('球类', '篮球,足球,羽毛球', '各类球类器材', 5, '', '', 'L2', 1, NOW(), NOW(), 0),
('健身器材', '哑铃,瑜伽垫', '健身运动器材', 5, '', '', 'L2', 2, NOW(), NOW(), 0),
('运动服饰', '运动鞋,运动服', '运动类服饰', 5, '', '', 'L2', 3, NOW(), NOW(), 0),
('户外装备', '帐篷,登山包', '户外运动装备', 5, '', '', 'L2', 4, NOW(), NOW(), 0);

-- 二级分类 - 其他
INSERT INTO `litemall_category` (name, keywords, `desc`, pid, icon_url, pic_url, level, sort_order, add_time, update_time, deleted) VALUES
('演出门票', '演唱会,话剧', '各类演出门票', 6, '', '', 'L2', 1, NOW(), NOW(), 0),
('代金券', '优惠券,代金券', '各类优惠券', 6, '', '', 'L2', 2, NOW(), NOW(), 0),
('其他物品', '闲置物品', '其他闲置物品', 6, '', '', 'L2', 3, NOW(), NOW(), 0);

-- ============================================
-- 执行完成
-- ============================================
SELECT 'Epic 2 数据库迁移完成' AS message;
