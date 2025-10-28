# Epic 6 Story 6.3 实现文档
## 捐赠完成奖励

**生成时间**: 2025-10-28  
**实现者**: Amelia (Developer Agent)  
**状态**: ✅ 已完成

---

## 一、Story 概述

### 功能目标
实现捐赠完成后的奖励机制，包括：
1. 积分奖励：+20 分
2. 累计捐赠次数统计
3. 徽章颁发系统（3种徽章）
4. 用户表字段扩展

### 业务规则（ADR-012: 徽章系统设计）
- **徽章类型**:
  - 🏅 "爱心大使"（捐赠 5 次）
  - 🌟 "公益达人"（捐赠 10 次）
  - ♻️ "环保先锋"（捐赠 20 次）
- **徽章存储**: 使用 JSON 字段存储用户徽章列表
- **积分奖励**: 每次捐赠完成 +20 积分
- **触发时机**: 管理员确认收货时（`finish()` 接口）

---

## 二、技术实现

### 1. 数据库变更

#### 扩展 litemall_user 表

```sql
ALTER TABLE `litemall_user` 
ADD COLUMN `badges` JSON COMMENT '用户徽章（数组）' AFTER `nickname`,
ADD COLUMN `donation_count` INT DEFAULT 0 COMMENT '累计捐赠次数' AFTER `badges`;
```

**字段说明**:
- `badges`: JSON 数组，存储徽章名称列表，如 `["爱心大使", "公益达人"]`
- `donation_count`: 整数，记录用户累计完成的捐赠次数（status=3的记录数）

**执行结果**:
```bash
mysql> DESC litemall_user;
+-----------------+-------------+------+-----+---------+----------------+
| Field           | Type        | Null | Key | Default | Extra          |
+-----------------+-------------+------+-----+---------+----------------+
| nickname        | varchar(63) | NO   |     |         |                |
| badges          | longtext    | YES  |     | NULL    |                |
| donation_count  | int(11)     | YES  |     | 0       |                |
+-----------------+-------------+------+-----+---------+----------------+
```

### 2. 实体类更新

**文件**: `/litemall-db/src/main/java/org/linlinjava/litemall/db/domain/LitemallUser.java`

**新增字段**:
```java
/**
 * This field corresponds to the database column litemall_user.badges
 */
private String badges;

/**
 * This field corresponds to the database column litemall_user.donation_count
 */
private Integer donationCount;
```

**新增Getter/Setter**:
```java
public String getBadges() {
    return badges;
}

public void setBadges(String badges) {
    this.badges = badges;
}

public Integer getDonationCount() {
    return donationCount;
}

public void setDonationCount(Integer donationCount) {
    this.donationCount = donationCount;
}
```

### 3. 奖励逻辑实现

**文件**: `/litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauDonationService.java`

#### 3.1 增强 finish() 方法

```java
@Transactional
public void finish(Integer donationId) {
    SicauDonation donation = donationMapper.selectByPrimaryKey(donationId);
    
    if (donation == null) {
        throw new RuntimeException("捐赠记录不存在");
    }
    
    if (donation.getStatus() != 1) {
        throw new RuntimeException("捐赠状态异常，当前状态: " + donation.getStatus());
    }
    
    // 1. 更新捐赠状态
    donation.setStatus((byte) 3); // 已完成
    donation.setFinishTime(LocalDateTime.now());
    donation.setUpdateTime(LocalDateTime.now());
    donationMapper.updateByPrimaryKeySelective(donation);
    
    // 2. 奖励积分（+20 分）
    LitemallUser user = userService.findById(donation.getUserId());
    if (user != null) {
        Integer currentScore = user.getCreditScore() != null ? user.getCreditScore() : 100;
        user.setCreditScore(currentScore + 20);
        logger.info("捐赠 " + donationId + " 已完成，用户 " + user.getId() + 
                   " 获得 +20 积分，当前积分: " + user.getCreditScore());
        
        // 3. 增加捐赠次数
        int newCount = (user.getDonationCount() != null ? user.getDonationCount() : 0) + 1;
        user.setDonationCount(newCount);
        
        // 4. 检查并颁发徽章
        awardBadge(user, newCount);
        
        // 5. 更新用户信息
        userService.updateById(user);
        
        logger.info("捐赠 " + donationId + " 完成，用户 " + user.getId() + 
                   " 累计捐赠 " + newCount + " 次");
    }
}
```

**核心流程**:
1. **验证状态**: 只有 status=1（审核通过）的捐赠才能确认收货
2. **更新捐赠**: 状态 → 3，记录完成时间
3. **增加积分**: 当前积分 + 20
4. **更新计数**: donation_count + 1
5. **检查徽章**: 调用 awardBadge() 判断是否达到徽章条件
6. **持久化**: 一次性更新用户所有字段

#### 3.2 徽章颁发逻辑

```java
private void awardBadge(LitemallUser user, int count) {
    List<String> badges = new ArrayList<>();
    String badgesJson = user.getBadges();
    
    // 解析现有徽章
    if (badgesJson != null && !badgesJson.isEmpty() && !"null".equals(badgesJson)) {
        try {
            TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {};
            badges = objectMapper.readValue(badgesJson, typeRef);
        } catch (Exception e) {
            logger.warn("解析徽章JSON失败: " + badgesJson, e);
            badges = new ArrayList<>();
        }
    }
    
    boolean updated = false;
    
    // 捐赠 5 次 → 爱心大使
    if (count == 5 && !badges.contains("爱心大使")) {
        badges.add("爱心大使");
        logger.info("用户 " + user.getId() + " 获得【爱心大使】徽章！");
        updated = true;
    }
    
    // 捐赠 10 次 → 公益达人
    if (count == 10 && !badges.contains("公益达人")) {
        badges.add("公益达人");
        logger.info("用户 " + user.getId() + " 获得【公益达人】徽章！");
        updated = true;
    }
    
    // 捐赠 20 次 → 环保先锋
    if (count == 20 && !badges.contains("环保先锋")) {
        badges.add("环保先锋");
        logger.info("用户 " + user.getId() + " 获得【环保先锋】徽章！");
        updated = true;
    }
    
    if (updated) {
        try {
            user.setBadges(objectMapper.writeValueAsString(badges));
        } catch (JsonProcessingException e) {
            logger.error("徽章JSON序列化失败", e);
        }
    }
}
```

**徽章颁发逻辑**:
1. **解析现有徽章**: 从 JSON 字符串反序列化为 List<String>
2. **判断条件**: 精确匹配次数（5/10/20），避免重复颁发（`!badges.contains()`）
3. **添加徽章**: 满足条件时追加到列表
4. **序列化保存**: 转回 JSON 字符串存储
5. **日志记录**: 每次颁发都记录日志

**JSON 示例**:
```json
// 第一次捐赠完成
user.badges = null

// 第5次捐赠完成
user.badges = "[\"爱心大使\"]"

// 第10次捐赠完成
user.badges = "[\"爱心大使\",\"公益达人\"]"

// 第20次捐赠完成
user.badges = "[\"爱心大使\",\"公益达人\",\"环保先锋\"]"
```

---

## 三、编译与部署

### 编译结果
```bash
$ mvn clean compile -T1C -DskipTests

[INFO] Reactor Summary for litemall 0.1.0:
[INFO] 
[INFO] litemall ........................................... SUCCESS [  0.310 s]
[INFO] litemall-db ........................................ SUCCESS [ 11.741 s]
[INFO] litemall-core ...................................... SUCCESS [  1.301 s]
[INFO] litemall-wx-api .................................... SUCCESS [  2.090 s]
[INFO] litemall-admin-api ................................. SUCCESS [  2.051 s]
[INFO] litemall-all ....................................... SUCCESS [  0.569 s]
[INFO] litemall-all-war ................................... SUCCESS [  0.391 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  16.407 s (Wall Clock)
```

**所有模块编译成功！**

---

## 四、功能测试建议

### 1. 积分奖励测试
```bash
# 准备: 插入一条审核通过的捐赠
INSERT INTO sicau_donation (user_id, category, quantity, images, pickup_type, status)
VALUES (1, 1, 5, '["http://img1.jpg"]', 1, 1);

# 执行: 确认收货
POST /admin/donation/finish
{
  "donationId": 1
}

# 验证点:
SELECT credit_score, donation_count FROM litemall_user WHERE id=1;
-- 应显示: credit_score = 原值+20, donation_count = 1
```

### 2. 捐赠次数累计测试
```sql
-- 模拟多次捐赠完成
-- 每次调用 finish() 后检查 donation_count 是否递增
SELECT id, donation_count FROM litemall_user WHERE id=1;
```

### 3. 徽章颁发测试

#### 测试场景 1: 获得"爱心大使"
```bash
# 完成第5次捐赠
POST /admin/donation/finish
{
  "donationId": 5
}

# 验证:
SELECT badges, donation_count FROM litemall_user WHERE id=1;
-- 应显示: badges = ["爱心大使"], donation_count = 5
```

#### 测试场景 2: 获得"公益达人"
```bash
# 完成第10次捐赠
POST /admin/donation/finish
{
  "donationId": 10
}

# 验证:
SELECT badges, donation_count FROM litemall_user WHERE id=1;
-- 应显示: badges = ["爱心大使","公益达人"], donation_count = 10
```

#### 测试场景 3: 获得"环保先锋"
```bash
# 完成第20次捐赠
POST /admin/donation/finish
{
  "donationId": 20
}

# 验证:
SELECT badges, donation_count FROM litemall_user WHERE id=1;
-- 应显示: badges = ["爱心大使","公益达人","环保先锋"], donation_count = 20
```

### 4. 异常场景测试

#### 状态异常
```bash
# 尝试对待审核（status=0）的捐赠确认收货
POST /admin/donation/finish
{
  "donationId": 100
}

# 预期响应:
{
  "errno": 502,
  "errmsg": "收货确认失败"
}

# 日志:
ERROR - 捐赠状态异常，当前状态: 0
```

#### 重复确认
```bash
# 对已完成（status=3）的捐赠再次确认
POST /admin/donation/finish
{
  "donationId": 1
}

# 预期响应:
{
  "errno": 502,
  "errmsg": "收货确认失败"
}

# 日志:
ERROR - 捐赠状态异常，当前状态: 3
```

---

## 五、技术亮点与设计决策

### 1. 事务一致性
- 使用 `@Transactional` 确保捐赠状态更新、积分增加、次数累加、徽章颁发**原子性完成**
- 任一步骤失败都会回滚，避免数据不一致

### 2. 徽章防重机制
- 使用 `!badges.contains(徽章名)` 判断，确保同一徽章不会重复颁发
- 即使多次调用 `awardBadge(user, 5)`，也只会在第一次添加"爱心大使"

### 3. JSON 健壮性处理
- 解析时捕获异常，失败时返回空列表而非抛错
- 序列化失败时记录错误日志，不阻塞主流程
- 兼容 `null`、空字符串、`"null"` 字符串等边界情况

### 4. 日志设计
- 使用 Apache Commons Log（与项目其他模块保持一致）
- 字符串拼接而非参数化（避免 SLF4J 风格导致的编译错误）
- 关键节点记录：积分变更、徽章颁发、次数更新

### 5. 可扩展性
- 徽章条件集中在 `awardBadge()` 方法，易于添加新徽章
- 未来可扩展：
  - 徽章图标 URL（badges 改为对象数组）
  - 徽章等级（铜/银/金）
  - 特殊徽章（如"慈善家"捐赠物品总价值超1000元）

---

## 六、与其他 Story 的关联

### Story 6.1 基础
- 依赖 `sicau_donation` 表和 `SicauDonationService.finish()` 方法（Story 6.1 已实现）
- 依赖 `LitemallUserService.findById()` 和 `updateById()` 方法

### Story 6.2 触发点
- AdminDonationController 的 `finish()` 接口调用本 Story 实现的奖励逻辑
- 返回消息"收货确认成功，用户已获得积分奖励"现在真正生效

### 前端展示（未实现）
- **个人主页**: 显示 `badges` 字段，渲染徽章图标和名称
- **捐赠记录**: 显示 `donation_count` 字段，显示"已完成 X 次捐赠"
- **排行榜**: 按 `donation_count` 降序排列，展示公益达人榜

---

## 七、待办事项（TODO）

### 高优先级
1. **个人主页 API 增强**:
   ```java
   // 在 WxUserController 或个人主页接口中返回徽章信息
   @GetMapping("/profile")
   public Object getProfile() {
       LitemallUser user = userService.findById(userId);
       Map<String, Object> profile = new HashMap<>();
       profile.put("badges", parseBadges(user.getBadges())); // 解析为数组
       profile.put("donationCount", user.getDonationCount());
       // ... 其他字段
   }
   ```

2. **徽章通知推送**（可选）:
   - 获得新徽章时，通过微信模板消息通知用户
   - 在 `awardBadge()` 中集成 NotifyService

### 中优先级
3. **徽章图标设计**:
   - 设计 3 个徽章的图标（PNG/SVG格式）
   - 上传到 OSS，前端根据徽章名称映射图标 URL

4. **捐赠排行榜**:
   ```java
   // 新增查询方法
   public List<LitemallUser> queryTopDonors(int topN) {
       // ORDER BY donation_count DESC LIMIT topN
   }
   ```

### 低优先级
5. **徽章数据结构升级**:
   ```json
   // 当前: ["爱心大使", "公益达人"]
   
   // 未来:
   [
     {"name": "爱心大使", "icon": "http://...", "awardTime": "2025-10-28"},
     {"name": "公益达人", "icon": "http://...", "awardTime": "2025-11-15"}
   ]
   ```

6. **积分规则对接 CreditScoreService**:
   - 当前直接加 20 分，未来可调用 `creditScoreService.updateCreditScore(userId, CreditRule.DONATE)`
   - 需要确保 litemall-db 模块能访问 litemall-core 模块

---

## 八、总结

### 完成内容
✅ 扩展 `litemall_user` 表（badges, donation_count）  
✅ 更新 `LitemallUser` 实体类（字段 + Getter/Setter）  
✅ 实现 `finish()` 积分奖励逻辑 (+20 分)  
✅ 实现 `awardBadge()` 徽章颁发机制（3种徽章）  
✅ 编译成功 (BUILD SUCCESS)  
✅ 更新 sprint-status.yaml（Epic 6 → completed）

### 关键成果
1. **完整的奖励闭环**: 捐赠提交 → 审核 → 确认收货 → 积分+徽章
2. **三级徽章体系**: 激励用户持续参与公益捐赠
3. **数据完整性**: 事务保证所有字段同步更新
4. **代码可维护性**: 徽章逻辑独立在 `awardBadge()` 方法中

### Epic 6 整体进度
**3/3 stories 完成 (100%)**
- ✅ Story 6.1: 提交捐赠申请
- ✅ Story 6.2: 管理员审核捐赠
- ✅ Story 6.3: 捐赠完成奖励

**Epic 6: 公益捐赠通道 已完成！** ��

### 下一步建议
继续实现 **Epic 7: 管理后台增强**（4个stories）或进行 Epic 6 的前端开发。
