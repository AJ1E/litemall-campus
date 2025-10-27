# Epic 2 技术上下文：商品发布与管理

**Epic ID**: 2  
**Epic 标题**: 商品发布与管理  
**优先级**: P0  
**预估工时**: 48 小时  
**依赖关系**: Epic 1（用户必须通过学号认证后才能发布商品）  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)

---

## 1. Epic 概述

本 Epic 实现四川农业大学校园闲置物品交易系统的核心功能——商品发布与管理。包括：
- 商品发布（图片上传、敏感词检测）
- 分类标签管理（一级/二级分类）
- 敏感词过滤（DFA 算法）
- 教材课程名搜索（校园场景优化）
- 商品列表检索（分类筛选、价格排序）
- 商品收藏功能

这是用户核心交易流程的第一步，为后续的订单、支付、配送提供商品数据基础。

---

## 2. 架构决策引用

基于 `architecture.md` 中的以下关键决策：

### ADR-003: 图片存储方案
- **存储服务**: 阿里云 OSS
- **上传方式**: 客户端直传（减轻服务器压力）
- **图片处理**: OSS 图片处理服务（自动压缩至 800x800px，质量 80%）
- **CDN 加速**: 阿里云 CDN（提升访问速度）
- **存储桶**: `litemall-campus-goods` (华南区域)

### ADR-004: 敏感词检测
- **算法**: DFA（Deterministic Finite Automaton）字典树
- **词库来源**: 初始 500+ 敏感词，来自公开词库 + 校园场景自定义
- **性能要求**: 单次检测响应时间 < 50ms
- **增量更新**: 管理后台实时新增，每 5 分钟同步到应用缓存

### 技术栈
- **后端框架**: Spring Boot 2.7.x
- **数据库**: MySQL 8.0
- **缓存**: Redis 6.0（缓存分类树、敏感词库）
- **文件存储**: 阿里云 OSS
- **全文搜索**: MySQL 全文索引（后期可升级为 Elasticsearch）

---

## 3. 数据库变更

### 3.1 复用 litemall 现有表

#### litemall_goods (商品主表)
已存在字段复用：
- `id`, `name`, `brief`, `pic_url`, `gallery`, `category_id`
- `retail_price`, `is_on_sale`, `is_new`, `is_hot`, `add_time`, `update_time`, `deleted`

**新增字段**:
```sql
ALTER TABLE `litemall_goods` 
ADD COLUMN `original_price` DECIMAL(10,2) COMMENT '原价（用于展示折扣）' AFTER `retail_price`,
ADD COLUMN `newness` TINYINT DEFAULT 2 COMMENT '新旧程度: 1-全新, 2-几乎全新, 3-轻微使用痕迹, 4-明显使用痕迹' AFTER `original_price`,
ADD COLUMN `purchase_time` DATE COMMENT '购买时间' AFTER `newness`,
ADD COLUMN `user_id` INT NOT NULL COMMENT '发布者用户ID' AFTER `id`,
ADD COLUMN `status` TINYINT DEFAULT 0 COMMENT '商品状态: 0-待审核, 1-上架中, 2-已售出, 3-已下架, 4-违规下架' AFTER `is_on_sale`,
ADD INDEX `idx_user_id` (`user_id`),
ADD INDEX `idx_status` (`status`);
```

#### litemall_category (分类表)
已存在字段复用：
- `id`, `name`, `pid`, `level`, `sort_order`, `deleted`

**数据初始化**（6 个一级分类 + 20 个二级分类）:
```sql
-- 一级分类
INSERT INTO `litemall_category` (name, pid, level, sort_order) VALUES
('教材教辅', 0, 'L1', 1),
('电子数码', 0, 'L1', 2),
('生活用品', 0, 'L1', 3),
('服饰鞋包', 0, 'L1', 4),
('运动器材', 0, 'L1', 5),
('其他', 0, 'L1', 6);

-- 二级分类（教材教辅）
INSERT INTO `litemall_category` (name, pid, level, sort_order) VALUES
('通识课教材', 1, 'L2', 1),
('专业课教材', 1, 'L2', 2),
('考研资料', 1, 'L2', 3),
('英语四六级', 1, 'L2', 4);

-- 二级分类（电子数码）
INSERT INTO `litemall_category` (name, pid, level, sort_order) VALUES
('手机', 2, 'L2', 1),
('电脑', 2, 'L2', 2),
('平板', 2, 'L2', 3),
('配件', 2, 'L2', 4);
```

### 3.2 新增表：sicau_sensitive_words (敏感词库)

```sql
CREATE TABLE `sicau_sensitive_words` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `word` VARCHAR(50) NOT NULL COMMENT '敏感词',
  `type` TINYINT DEFAULT 1 COMMENT '类型: 1-违规交易, 2-黄赌毒, 3-政治敏感, 4-其他',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库表';

-- 初始化敏感词（示例）
INSERT INTO `sicau_sensitive_words` (word, type) VALUES
('代考', 1),
('代写论文', 1),
('刷单', 1),
('黄色', 2),
('赌博', 2),
('毒品', 2);
```

### 3.3 新增表：sicau_course_material (课程-教材映射)

```sql
CREATE TABLE `sicau_course_material` (
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

-- 初始化数据（示例）
INSERT INTO `sicau_course_material` (course_name, book_name, isbn, semester) VALUES
('高等数学A', '高等数学（第七版）上册', '9787040396614', '大一上'),
('高等数学A', '高等数学（第七版）下册', '9787040396621', '大一下'),
('大学英语', '新视野大学英语读写教程1', '9787513533898', '大一上'),
('大学英语', '新视野大学英语读写教程2', '9787513533904', '大一下'),
('线性代数', '线性代数（第六版）', '9787040396454', '大一下'),
('概率论与数理统计', '概率论与数理统计（第四版）', '9787040238969', '大二上');
```

### 3.4 新增表：sicau_goods_violation (违规商品记录)

```sql
CREATE TABLE `sicau_goods_violation` (
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
```

---

## 4. 核心代码实现指导

### 4.1 敏感词 DFA 过滤器

创建 `litemall-core/src/main/java/org/linlinjava/litemall/core/util/SensitiveWordFilter.java`：

```java
package org.linlinjava.litemall.core.util;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.*;

/**
 * DFA 算法敏感词过滤器
 */
@Component
public class SensitiveWordFilter {
    
    // DFA 字典树根节点
    private Map<String, Object> dfaMap = new HashMap<>();
    
    // 敏感词集合（从数据库加载）
    private Set<String> sensitiveWords = new HashSet<>();
    
    /**
     * 初始化 DFA 字典树
     */
    @PostConstruct
    public void init() {
        // 从数据库加载敏感词
        loadSensitiveWords();
        
        // 构建 DFA 树
        for (String word : sensitiveWords) {
            addWordToTree(word);
        }
    }
    
    /**
     * 添加敏感词到字典树
     */
    private void addWordToTree(String word) {
        Map<String, Object> nowMap = dfaMap;
        
        for (int i = 0; i < word.length(); i++) {
            String key = String.valueOf(word.charAt(i));
            
            // 获取下一层节点
            Map<String, Object> nextMap = (Map<String, Object>) nowMap.get(key);
            
            if (nextMap == null) {
                nextMap = new HashMap<>();
                nowMap.put(key, nextMap);
            }
            
            nowMap = nextMap;
            
            // 最后一个字符，标记结束
            if (i == word.length() - 1) {
                nowMap.put("isEnd", true);
            }
        }
    }
    
    /**
     * 检测文本是否包含敏感词
     * @param text 待检测文本
     * @return 是否包含敏感词
     */
    public boolean containsSensitive(String text) {
        return !getSensitiveWords(text).isEmpty();
    }
    
    /**
     * 获取文本中的所有敏感词
     * @param text 待检测文本
     * @return 敏感词列表
     */
    public List<String> getSensitiveWords(String text) {
        List<String> result = new ArrayList<>();
        
        for (int i = 0; i < text.length(); i++) {
            int length = checkSensitiveWord(text, i);
            if (length > 0) {
                result.add(text.substring(i, i + length));
                i += length - 1; // 跳过已匹配的字符
            }
        }
        
        return result;
    }
    
    /**
     * 检查指定位置开始的敏感词长度
     * @param text 文本
     * @param start 起始位置
     * @return 敏感词长度（0 表示不包含）
     */
    private int checkSensitiveWord(String text, int start) {
        Map<String, Object> nowMap = dfaMap;
        int matchLength = 0;
        
        for (int i = start; i < text.length(); i++) {
            String key = String.valueOf(text.charAt(i));
            nowMap = (Map<String, Object>) nowMap.get(key);
            
            if (nowMap == null) {
                break;
            }
            
            matchLength++;
            
            if (Boolean.TRUE.equals(nowMap.get("isEnd"))) {
                return matchLength;
            }
        }
        
        return 0;
    }
    
    /**
     * 替换敏感词为 ***
     */
    public String replaceSensitive(String text) {
        List<String> words = getSensitiveWords(text);
        for (String word : words) {
            text = text.replace(word, "***");
        }
        return text;
    }
    
    /**
     * 从数据库加载敏感词（由 Service 层调用）
     */
    public void loadSensitiveWords() {
        // 在 Service 层注入后调用
        // sensitiveWords = sensitiveWordService.queryAllWords();
    }
    
    /**
     * 动态添加敏感词
     */
    public void addSensitiveWord(String word) {
        sensitiveWords.add(word);
        addWordToTree(word);
    }
}
```

### 4.2 阿里云 OSS 图片上传工具

已存在于 `litemall-core/src/main/java/org/linlinjava/litemall/core/storage/AliyunStorage.java`，配置示例：

```yaml
# application.yml
litemall:
  storage:
    active: aliyun
    aliyun:
      endpoint: oss-cn-guangzhou.aliyuncs.com
      access-key-id: ${ALIYUN_ACCESS_KEY_ID}
      access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET}
      bucket-name: litemall-campus-goods
```

### 4.3 商品发布服务

创建 `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxGoodsController.java`：

```java
package org.linlinjava.litemall.wx.web;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.util.SensitiveWordFilter;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.SicauStudentAuthService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/wx/goods")
public class WxGoodsController {
    
    @Autowired
    private LitemallGoodsService goodsService;
    
    @Autowired
    private SicauStudentAuthService studentAuthService;
    
    @Autowired
    private SensitiveWordFilter sensitiveWordFilter;
    
    /**
     * 发布商品
     */
    @PostMapping("publish")
    public Object publish(@LoginUser Integer userId, @RequestBody LitemallGoods goods) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        
        // 1. 验证学号认证状态
        SicauStudentAuth auth = studentAuthService.findByUserId(userId);
        if (auth == null || auth.getStatus() != 2) {
            return ResponseUtil.fail(701, "请先完成学号认证");
        }
        
        // 2. 参数验证
        if (goods.getName() == null || goods.getName().length() > 30) {
            return ResponseUtil.fail(402, "标题长度应为1-30字");
        }
        if (goods.getRetailPrice() == null || goods.getRetailPrice().compareTo(new BigDecimal("1")) < 0) {
            return ResponseUtil.fail(402, "价格不能低于1元");
        }
        if (goods.getCategoryId() == null) {
            return ResponseUtil.badArgument("请选择商品分类");
        }
        if (goods.getNewness() == null || goods.getNewness() < 1 || goods.getNewness() > 4) {
            return ResponseUtil.badArgument("请选择新旧程度");
        }
        
        // 3. 敏感词检测
        String fullText = goods.getName() + " " + (goods.getBrief() != null ? goods.getBrief() : "");
        List<String> sensitiveWords = sensitiveWordFilter.getSensitiveWords(fullText);
        if (!sensitiveWords.isEmpty()) {
            return ResponseUtil.fail(702, "包含违规词汇：" + String.join(", ", sensitiveWords));
        }
        
        // 4. 图片数量验证
        if (goods.getGallery() == null || goods.getGallery().length == 0) {
            return ResponseUtil.fail(402, "请至少上传1张图片");
        }
        if (goods.getGallery().length > 9) {
            return ResponseUtil.fail(402, "最多上传9张图片");
        }
        
        // 5. 保存商品
        goods.setUserId(userId);
        goods.setStatus((byte) 0); // 待审核（后期可改为自动上架）
        goods.setIsOnSale(true);
        goodsService.add(goods);
        
        return ResponseUtil.ok("发布成功，等待审核");
    }
    
    /**
     * 我的发布列表
     */
    @GetMapping("myList")
    public Object myList(@LoginUser Integer userId,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "20") Integer size) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        
        List<LitemallGoods> goodsList = goodsService.queryByUserId(userId, page, size);
        return ResponseUtil.okList(goodsList);
    }
}
```

---

## 5. API 契约定义

### 5.1 POST /wx/goods/publish - 发布商品

**请求体**:
```json
{
  "name": "高等数学A（第七版）上册",
  "categoryId": 1,
  "brief": "仅用过一学期，无笔记无划线",
  "picUrl": "https://oss.aliyuncs.com/goods/123.jpg",
  "gallery": [
    "https://oss.aliyuncs.com/goods/123-1.jpg",
    "https://oss.aliyuncs.com/goods/123-2.jpg"
  ],
  "retailPrice": 50.00,
  "originalPrice": 89.00,
  "newness": 2,
  "purchaseTime": "2023-09"
}
```

**响应体（成功）**:
```json
{
  "errno": 0,
  "errmsg": "发布成功，等待审核"
}
```

**响应体（敏感词）**:
```json
{
  "errno": 702,
  "errmsg": "包含违规词汇：代考"
}
```

### 5.2 GET /wx/goods/list - 商品列表检索

**请求参数**:
- `categoryId`: 分类ID（可选）
- `minPrice`: 最低价格（可选）
- `maxPrice`: 最高价格（可选）
- `sortType`: 排序类型（default/priceAsc/priceDesc）
- `keyword`: 关键词搜索（可选）
- `page`: 页码（默认 1）
- `size`: 每页数量（默认 20）

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "list": [
      {
        "id": 1,
        "name": "高等数学A（第七版）上册",
        "picUrl": "https://...",
        "retailPrice": 50.00,
        "originalPrice": 89.00,
        "newness": 2,
        "userId": 123,
        "userNickname": "川农学子",
        "userCreditLevel": 3
      }
    ],
    "total": 156,
    "page": 1,
    "pages": 8
  }
}
```

### 5.3 GET /wx/goods/searchByCourse - 课程教材搜索

**请求参数**:
- `keyword`: 课程名关键词（如：高等数学）

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "courses": [
      {
        "courseName": "高等数学A",
        "semester": "大一上",
        "materials": [
          {
            "bookName": "高等数学（第七版）上册",
            "isbn": "9787040396614",
            "goods": [
              {
                "id": 1,
                "picUrl": "https://...",
                "retailPrice": 50.00
              }
            ]
          }
        ]
      }
    ]
  }
}
```

### 5.4 POST /wx/collect/add - 收藏商品

**请求体**:
```json
{
  "goodsId": 123
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "收藏成功"
}
```

---

## 6. 配置文件变更

### 6.1 application-wx.yml

```yaml
# 阿里云 OSS 配置
litemall:
  storage:
    active: aliyun
    aliyun:
      endpoint: oss-cn-guangzhou.aliyuncs.com
      access-key-id: ${ALIYUN_ACCESS_KEY_ID}
      access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET}
      bucket-name: litemall-campus-goods
      
# 敏感词缓存配置
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000 # 1小时
```

---

## 7. 前端开发要点

### 7.1 小程序端关键文件

- `pages/goods/publish/publish.js` - 商品发布页
- `pages/goods/list/list.js` - 商品列表页
- `pages/goods/detail/detail.js` - 商品详情页
- `pages/user/collect/collect.js` - 收藏夹

### 7.2 图片上传组件

```javascript
// utils/ossUpload.js
export function uploadToOSS(filePath) {
  return new Promise((resolve, reject) => {
    // 1. 获取 OSS 签名
    wx.request({
      url: 'https://api.xxx.com/wx/storage/policy',
      success: (res) => {
        const policy = res.data.data;
        
        // 2. 上传到 OSS
        wx.uploadFile({
          url: policy.host,
          filePath: filePath,
          name: 'file',
          formData: {
            'key': policy.key,
            'policy': policy.policy,
            'OSSAccessKeyId': policy.accessKeyId,
            'signature': policy.signature
          },
          success: () => {
            resolve(policy.url); // 返回图片 URL
          },
          fail: reject
        });
      }
    });
  });
}
```

---

## 8. 测试策略

### 8.1 单元测试

- `SensitiveWordFilter` 敏感词检测准确性测试
- DFA 字典树性能测试（1000 词库，1000 字文本 < 50ms）
- 图片压缩质量测试（压缩后文件大小 < 原文件 50%）

### 8.2 集成测试

- 发布商品完整流程测试（上传图片 → 填写信息 → 敏感词检测 → 保存）
- 分类筛选测试（二级分类联动）
- 课程教材搜索测试（模糊匹配准确性）

### 8.3 性能测试

- 商品列表加载性能（1000 条数据分页加载 < 500ms）
- 图片上传并发测试（10 用户同时上传 9 张图片）
- 敏感词检测并发测试（100 QPS 稳定性）

---

## 9. 依赖关系

### 前置条件
- Epic 1 已完成（学号认证状态 = 2）
- 阿里云 OSS 已配置
- Redis 服务已启动

### 后续依赖
- **Epic 3 (交易流程)** 依赖商品数据
- **Epic 5 (限时拍卖)** 复用商品发布流程

---

## 10. 风险提示

1. **敏感词库维护**: 需定期更新校园场景特有敏感词（如："代课"、"刷学分"）
2. **图片审核成本**: OSS 图片识别服务按调用次数收费，建议仅审核首次上传
3. **存储成本**: OSS 存储费用约 0.12 元/GB/月，预估首年 500GB（约 60 元/月）
4. **课程教材数据**: 需与教务处协调获取课程-教材映射关系

---

## 11. Story 任务分解

### Story 2.1: 商品发布 (12h)
- Task 1: 修改 `litemall_goods` 表结构（新增 5 个字段）
- Task 2: 创建 `WxGoodsController.publish()` 方法
- Task 3: 集成阿里云 OSS 图片上传
- Task 4: 集成敏感词检测
- Task 5: 前端发布页面开发
- Task 6: 单元测试 + 集成测试

### Story 2.2: 分类标签管理 (6h)
- Task 1: 初始化 `litemall_category` 数据（6 个一级 + 20 个二级）
- Task 2: 管理后台分类 CRUD 接口
- Task 3: 前端分类选择组件
- Task 4: 删除分类前检查商品引用

### Story 2.3: 敏感词过滤 (8h)
- Task 1: 创建 `sicau_sensitive_words` 表
- Task 2: 实现 `SensitiveWordFilter` DFA 算法
- Task 3: 管理后台敏感词管理接口
- Task 4: 敏感词缓存（Redis）
- Task 5: 性能测试（响应时间 < 50ms）

### Story 2.4: 教材课程名搜索 (10h)
- Task 1: 创建 `sicau_course_material` 表
- Task 2: 初始化课程-教材映射数据（200+ 条）
- Task 3: 实现 `searchByCourse()` 接口
- Task 4: 前端搜索结果页优化
- Task 5: 管理后台课程-教材管理

### Story 2.5: 商品列表检索 (8h)
- Task 1: 优化 `LitemallGoodsService.querySelective()` 方法
- Task 2: 添加价格区间筛选
- Task 3: 添加排序功能（价格升序/降序）
- Task 4: 前端筛选栏组件
- Task 5: 数据库索引优化

### Story 2.6: 商品收藏 (4h)
- Task 1: 复用 `litemall_collect` 表
- Task 2: 实现 `WxCollectController` 接口
- Task 3: 前端收藏夹页面
- Task 4: 商品下架自动移除收藏

---

## 12. 验收清单

- [ ] 学号认证用户可成功发布商品（图片 1-9 张）
- [ ] 敏感词检测准确率 > 95%
- [ ] 商品分类树正确展示（6 个一级 + 20 个二级）
- [ ] 课程名搜索可匹配到对应教材
- [ ] 商品列表支持分类筛选、价格排序
- [ ] 收藏/取消收藏功能正常
- [ ] 图片上传后自动压缩至 800x800px
- [ ] 单次敏感词检测响应时间 < 50ms

---

**Epic 状态**: 准备就绪，等待开发  
**下一步**: 由 bmm-dev 开发者开始实施
