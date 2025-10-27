# Epic 1 技术上下文：用户认证与信用体系

**Epic ID**: 1  
**Epic 标题**: 用户认证与信用体系  
**优先级**: P0  
**预估工时**: 40 小时  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)

---

## 1. Epic 概述

本 Epic 旨在建立四川农业大学校园闲置物品交易系统的用户认证和信用评价机制，包括：
- 微信小程序一键登录
- 学号实名认证（12位学号 + AES加密）
- 信用积分自动计算系统
- 信用等级可视化展示
- 个人主页功能

这是整个系统的基础模块，后续所有功能（商品发布、交易、快递员）都依赖于此。

---

## 2. 架构决策引用

基于 `architecture.md` 中的以下关键决策：

### ADR-002: 学号认证安全机制
- **加密算法**: AES-256-GCM
- **密钥管理**: 存储在 `application.yml` 中（生产环境应使用 Vault）
- **加密字段**: `student_no`, `real_name`

### 技术栈
- **后端框架**: Spring Boot 2.7.x (从 litemall 2.1.5 升级)
- **数据库**: MySQL 8.0
- **缓存**: Redis 6.0 (用于缓存用户信用等级)
- **认证**: JWT Token (有效期 30 天)

---

## 3. 数据库变更

### 3.1 修改现有表：litemall_user

```sql
-- 添加信用积分字段
ALTER TABLE `litemall_user` 
ADD COLUMN `credit_score` INT NOT NULL DEFAULT 100 COMMENT '信用积分，初始值100' AFTER `status`;

-- 添加索引以优化信用查询
CREATE INDEX `idx_credit_score` ON `litemall_user`(`credit_score`);
```

### 3.2 新增表：sicau_student_auth

```sql
CREATE TABLE `sicau_student_auth` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '关联 litemall_user.id',
  `student_no` VARCHAR(255) NOT NULL COMMENT '学号 (AES-256 加密存储)',
  `real_name` VARCHAR(255) NOT NULL COMMENT '真实姓名 (AES-256 加密存储)',
  `college` VARCHAR(100) COMMENT '学院',
  `major` VARCHAR(100) COMMENT '专业',
  `student_card_url` VARCHAR(255) COMMENT '学生证照片 URL (阿里云 OSS)',
  `status` TINYINT DEFAULT 0 COMMENT '0-待审核, 1-通过, 2-拒绝',
  `audit_admin_id` INT COMMENT '审核管理员 ID',
  `audit_time` DATETIME COMMENT '审核时间',
  `audit_reason` VARCHAR(255) COMMENT '拒绝原因（status=2时填写）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='四川农业大学学生实名认证表';
```

---

## 4. 核心代码实现指导

### 4.1 AES 加密工具类

创建 `litemall-core/src/main/java/org/linlinjava/litemall/core/util/AesUtil.java`：

```java
package org.linlinjava.litemall.core.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具类
 * 用于加密学号、姓名等敏感信息
 */
public class AesUtil {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    
    // 从配置文件读取（application.yml 中的 sicau.aes.key）
    private static String SECRET_KEY; // 32字节密钥
    
    /**
     * 加密
     * @param plaintext 明文
     * @return Base64 编码的密文
     */
    public static String encrypt(String plaintext) throws Exception {
        // 生成随机 IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // 将 IV 和密文拼接后返回
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    /**
     * 解密
     * @param ciphertext Base64 编码的密文
     * @return 明文
     */
    public static String decrypt(String ciphertext) throws Exception {
        byte[] combined = Base64.getDecoder().decode(ciphertext);
        
        // 提取 IV 和密文
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
        
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        
        byte[] plaintext = cipher.doFinal(encrypted);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
    
    // Setter 用于从配置注入密钥
    public static void setSecretKey(String key) {
        SECRET_KEY = key;
    }
}
```

### 4.2 信用积分计算服务

创建 `litemall-core/src/main/java/org/linlinjava/litemall/core/service/CreditScoreService.java`：

```java
package org.linlinjava.litemall.core.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditScoreService {
    
    /**
     * 信用积分规则枚举
     */
    public enum CreditRule {
        COMPLETE_ORDER(10, "完成交易"),
        GOOD_REVIEW(5, "收到好评"),
        BAD_REVIEW(-5, "收到差评"),
        CANCEL_ORDER(-5, "取消订单"),
        VIOLATE_GOODS(-50, "违规商品"),
        DONATE(20, "完成捐赠"),
        ON_TIME_DELIVERY(2, "准时配送"),
        LATE_DELIVERY(-10, "配送超时");
        
        private final int score;
        private final String description;
        
        CreditRule(int score, String description) {
            this.score = score;
            this.description = description;
        }
    }
    
    /**
     * 更新用户信用积分
     * @param userId 用户ID
     * @param rule 积分规则
     */
    @Transactional
    public void updateCreditScore(Integer userId, CreditRule rule) {
        // 1. 查询当前积分
        LitemallUser user = userService.findById(userId);
        int currentScore = user.getCreditScore();
        
        // 2. 计算新积分
        int newScore = Math.max(0, currentScore + rule.score); // 最低为0
        
        // 3. 更新数据库
        user.setCreditScore(newScore);
        userService.updateById(user);
        
        // 4. 清除 Redis 缓存
        redisTemplate.delete("credit:level:" + userId);
        
        // 5. 发送微信模板消息通知
        sendCreditChangeNotification(userId, rule, newScore);
    }
    
    /**
     * 计算信用等级
     * @param score 信用积分
     * @return 1-新手, 2-良好, 3-优秀, 4-信誉商家
     */
    public int getCreditLevel(int score) {
        if (score >= 501) return 4;
        if (score >= 301) return 3;
        if (score >= 101) return 2;
        return 1;
    }
}
```

### 4.3 JWT Token 增强

修改 `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/util/JwtHelper.java`：

```java
// 在生成 Token 时添加学号认证状态
public static String createToken(Integer userId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", userId);
    
    // 查询学号认证状态
    SicauStudentAuth auth = authService.getByUserId(userId);
    claims.put("authStatus", auth != null ? auth.getStatus() : 0);
    
    return Jwts.builder()
        .setClaims(claims)
        .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
        .signWith(SignatureAlgorithm.HS512, SECRET)
        .compact();
}
```

---

## 5. API 契约定义

### 5.1 POST /wx/auth/login - 微信一键登录

**请求体**:
```json
{
  "code": "081xYz0w3H7FZn2RFH3w3SJlFh2xYz0o"
}
```

**响应体（成功）**:
```json
{
  "errno": 0,
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "userInfo": {
      "id": 123,
      "username": "微信用户",
      "avatar": "https://...",
      "authStatus": 0,  // 0-未认证, 1-已通过, 2-被拒绝
      "creditScore": 100
    }
  }
}
```

**响应体（失败）**:
```json
{
  "errno": 501,
  "errmsg": "微信登录失败，请重试"
}
```

### 5.2 POST /wx/auth/bindStudentNo - 绑定学号

**请求体**:
```json
{
  "studentNo": "202112345678",
  "realName": "张三",
  "college": "信息工程学院",
  "major": "计算机科学与技术",
  "studentCardUrl": "https://oss.aliyuncs.com/..."
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "提交成功，请等待审核"
}
```

### 5.3 GET /wx/user/creditDetail - 信用详情

**请求参数**: `userId=123`

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "totalScore": 350,
    "level": 3,  // 优秀
    "levelName": "优秀",
    "thisMonthGain": 25,
    "thisMonthLoss": 5
  }
}
```

---

## 6. 配置文件变更

### 6.1 application.yml

在 `litemall-all/src/main/resources/application-wx.yml` 中添加：

```yaml
sicau:
  # AES 加密配置
  aes:
    key: "your-32-byte-secret-key-here!!" # 生产环境使用 Vault 管理
  
  # 微信小程序配置
  wx:
    appid: "wx1234567890abcdef"
    secret: "your-wx-app-secret"
```

---

## 7. 前端开发要点

### 7.1 小程序端关键文件

- `pages/auth/login/login.js` - 登录页面
- `pages/auth/bind-student/bind-student.js` - 学号绑定页面
- `pages/user/user.js` - 个人主页

### 7.2 全局状态管理

需要在 `app.js` 中维护：
```javascript
globalData: {
  userInfo: null,
  token: '',
  authStatus: 0,  // 学号认证状态
  creditScore: 100
}
```

---

## 8. 测试策略

### 8.1 单元测试
- `AesUtil` 加解密正确性测试
- `CreditScoreService` 积分计算逻辑测试
- JWT Token 生成和解析测试

### 8.2 集成测试
- 微信登录完整流程测试（需 Mock 微信 API）
- 学号认证提交和审核流程测试
- 信用积分更新触发器测试

### 8.3 性能测试
- 1000 并发用户登录测试
- Redis 缓存命中率测试（目标 > 80%）

---

## 9. 依赖关系

### 前置条件
- MySQL 数据库已创建并执行迁移脚本
- Redis 服务已启动
- 阿里云 OSS 已配置（用于学生证照片上传）

### 后续依赖
- **Epic 2 (商品发布)** 依赖学号认证状态（`authStatus == 1` 才能发布）
- **Epic 4 (学生快递员)** 依赖信用等级（`creditLevel >= 2` 才能申请）

---

## 10. 风险提示

1. **密钥泄露风险**: AES 密钥硬编码在配置文件中，生产环境必须使用 Vault 或 KMS
2. **微信 API 限流**: 微信登录接口有频率限制，需实现重试机制
3. **学号审核负担**: 预计每天 100+ 学号认证申请，需配置专人审核

---

**Epic 状态**: 已生成技术上下文，等待故事开发  
**下一步**: 为故事 1.1 创建开发任务文件
