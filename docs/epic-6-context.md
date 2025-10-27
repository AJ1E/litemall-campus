# Epic 6 技术上下文：公益捐赠通道

**Epic ID**: 6  
**Epic 标题**: 公益捐赠通道  
**优先级**: P2  
**预估工时**: 24 小时  
**依赖关系**: 无  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)

---

## 1. Epic 概述

本 Epic 实现校园公益捐赠功能，允许用户将闲置物品捐赠给需要的同学，建立校园公益通道，促进闲置资源再分配。核心功能包括：

- **提交捐赠申请**: 上传物品照片、选择分类（衣物/文具/书籍）、选择取件方式
- **管理员审核**: 审核物品完好度和公益适配性
- **捐赠完成奖励**: 完成捐赠获得 +20 积分，累计 5 次获得"爱心大使"徽章

**业务价值**: 
- 践行绿色环保理念
- 为贫困学生提供帮助
- 提升平台社会价值
- 增强用户粘性（公益积分激励）

---

## 2. 架构决策引用

基于 `architecture.md` 中的以下关键决策：

### ADR-011: 捐赠审核机制
- **审核周期**: 24 小时内完成审核
- **审核标准**: 物品完好度、公益适配性（杜绝垃圾物品）
- **拒绝原因**: 物品破损、不适合捐赠（如内衣）、照片不清晰

### ADR-012: 徽章系统设计
- **徽章存储**: 使用 JSON 字段存储用户徽章列表
- **徽章类型**: 
  - "爱心大使"（捐赠 5 次）
  - "公益达人"（捐赠 10 次）
  - "环保先锋"（捐赠 20 次）
- **展示位置**: 个人主页、评价卡片、排行榜

### 技术栈
- **后端框架**: Spring Boot 2.7.x
- **图片存储**: 阿里云 OSS（复用 Epic 2 配置）
- **积分奖励**: 复用 `CreditScoreService`

---

## 3. 数据库变更

### 3.1 新增表：sicau_donation (捐赠表)

```sql
CREATE TABLE `sicau_donation` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '捐赠者用户ID',
  `category` TINYINT NOT NULL COMMENT '分类: 1-衣物, 2-文具, 3-书籍, 4-其他',
  `quantity` INT NOT NULL COMMENT '数量',
  `images` JSON NOT NULL COMMENT '物品照片URL数组（1-3张）',
  `pickup_type` TINYINT NOT NULL COMMENT '取件方式: 1-自送至捐赠站点, 2-预约志愿者上门',
  `pickup_address` VARCHAR(200) COMMENT '取件地址（上门取件时填写）',
  `pickup_time` DATETIME COMMENT '预约上门时间',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待审核, 1-审核通过, 2-审核拒绝, 3-已完成',
  `reject_reason` VARCHAR(200) COMMENT '拒绝原因',
  `auditor_id` INT COMMENT '审核管理员ID',
  `audit_time` DATETIME COMMENT '审核时间',
  `volunteer_id` INT COMMENT '志愿者ID（上门取件时分配）',
  `finish_time` DATETIME COMMENT '完成时间',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公益捐赠表';
```

### 3.2 新增表：sicau_donation_point (捐赠站点表)

```sql
CREATE TABLE `sicau_donation_point` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `campus` VARCHAR(50) NOT NULL COMMENT '校区: 雅安本部, 成都校区',
  `name` VARCHAR(100) NOT NULL COMMENT '站点名称: 图书馆一楼捐赠站',
  `address` VARCHAR(200) NOT NULL COMMENT '详细地址',
  `contact_name` VARCHAR(50) COMMENT '联系人',
  `contact_phone` VARCHAR(20) COMMENT '联系电话',
  `open_time` VARCHAR(100) COMMENT '开放时间: 周一至周五 8:00-18:00',
  `is_active` BOOLEAN DEFAULT TRUE COMMENT '是否开放',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_campus` (`campus`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='捐赠站点表';
```

**预置数据**（雅安校区示例）：
```sql
INSERT INTO `sicau_donation_point` (`campus`, `name`, `address`, `contact_name`, `contact_phone`, `open_time`) VALUES
('雅安本部', '图书馆一楼捐赠站', '图书馆一楼大厅东侧', '学生会', '028-86290000', '周一至周五 8:00-18:00'),
('雅安本部', '西苑食堂爱心驿站', '西苑食堂二楼', '青年志愿者协会', '028-86290001', '周一至周日 11:00-13:00');
```

### 3.3 复用 litemall_user 表

**新增字段**:
```sql
ALTER TABLE `litemall_user` 
ADD COLUMN `badges` JSON COMMENT '用户徽章（数组）["爱心大使", "公益达人"]' AFTER `nickname`,
ADD COLUMN `donation_count` INT DEFAULT 0 COMMENT '累计捐赠次数' AFTER `badges`;
```

---

## 4. 核心代码实现指导

### 4.1 捐赠服务

创建 `litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauDonationService.java`：

```java
package org.linlinjava.litemall.db.service;

import com.alibaba.fastjson.JSON;
import org.linlinjava.litemall.db.dao.SicauDonationMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.SicauDonation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 公益捐赠服务
 */
@Service
public class SicauDonationService {
    
    @Resource
    private SicauDonationMapper donationMapper;
    
    @Autowired
    private LitemallUserService userService;
    
    @Autowired
    private CreditScoreService creditScoreService;
    
    @Autowired
    private NotifyService notifyService;
    
    /**
     * 提交捐赠申请
     * @param userId 用户ID
     * @param category 分类（1-衣物, 2-文具, 3-书籍, 4-其他）
     * @param quantity 数量
     * @param images 照片URL数组
     * @param pickupType 取件方式（1-自送, 2-上门）
     * @param pickupAddress 取件地址（上门时填写）
     * @param pickupTime 预约上门时间
     */
    @Transactional
    public Integer submit(Integer userId, Integer category, Integer quantity,
                          List<String> images, Integer pickupType,
                          String pickupAddress, LocalDateTime pickupTime) {
        // 1. 参数校验
        if (images == null || images.isEmpty() || images.size() > 3) {
            throw new RuntimeException("请上传 1-3 张物品照片");
        }
        
        if (pickupType == 2 && (pickupAddress == null || pickupTime == null)) {
            throw new RuntimeException("上门取件请填写地址和预约时间");
        }
        
        // 2. 创建捐赠记录
        SicauDonation donation = new SicauDonation();
        donation.setUserId(userId);
        donation.setCategory(category.byteValue());
        donation.setQuantity(quantity);
        donation.setImages(JSON.toJSONString(images));
        donation.setPickupType(pickupType.byteValue());
        donation.setPickupAddress(pickupAddress);
        donation.setPickupTime(pickupTime);
        donation.setStatus((byte) 0); // 待审核
        donation.setAddTime(LocalDateTime.now());
        
        donationMapper.insertSelective(donation);
        
        // 3. 推送通知
        notifyService.notify(userId, "捐赠申请已提交，管理员将在 24 小时内审核");
        
        return donation.getId();
    }
    
    /**
     * 管理员审核捐赠
     * @param donationId 捐赠ID
     * @param auditorId 审核管理员ID
     * @param pass 是否通过
     * @param rejectReason 拒绝原因（不通过时必填）
     */
    @Transactional
    public void audit(Integer donationId, Integer auditorId, Boolean pass, String rejectReason) {
        SicauDonation donation = donationMapper.selectByPrimaryKey(donationId);
        
        if (donation.getStatus() != 0) {
            throw new RuntimeException("该捐赠已审核过");
        }
        
        // 1. 更新审核结果
        donation.setAuditorId(auditorId);
        donation.setAuditTime(LocalDateTime.now());
        
        if (pass) {
            donation.setStatus((byte) 1); // 审核通过
            donationMapper.updateByPrimaryKeySelective(donation);
            
            // 2. 分配志愿者（若选择上门取件）
            if (donation.getPickupType() == 2) {
                // TODO: 实际应从志愿者池中分配
                // donation.setVolunteerId(assignVolunteer());
            }
            
            // 3. 推送通知
            notifyService.notify(donation.getUserId(), "捐赠审核已通过，感谢您的爱心！");
            
        } else {
            donation.setStatus((byte) 2); // 审核拒绝
            donation.setRejectReason(rejectReason);
            donationMapper.updateByPrimaryKeySelective(donation);
            
            // 推送拒绝通知
            notifyService.notify(donation.getUserId(), "捐赠审核未通过：" + rejectReason);
        }
    }
    
    /**
     * 确认收货（志愿者或管理员操作）
     * @param donationId 捐赠ID
     */
    @Transactional
    public void finish(Integer donationId) {
        SicauDonation donation = donationMapper.selectByPrimaryKey(donationId);
        
        if (donation.getStatus() != 1) {
            throw new RuntimeException("捐赠状态异常");
        }
        
        // 1. 更新捐赠状态
        donation.setStatus((byte) 3); // 已完成
        donation.setFinishTime(LocalDateTime.now());
        donationMapper.updateByPrimaryKeySelective(donation);
        
        // 2. 奖励积分（+20 分）
        creditScoreService.updateCreditScore(
            donation.getUserId(), 
            CreditScoreService.CreditRule.DONATION
        );
        
        // 3. 增加捐赠次数
        LitemallUser user = userService.findById(donation.getUserId());
        user.setDonationCount(user.getDonationCount() + 1);
        
        // 4. 检查并颁发徽章
        awardBadge(user);
        
        userService.updateById(user);
        
        // 5. 推送感谢通知
        notifyService.notify(donation.getUserId(), 
            "捐赠已完成，+20 积分！感谢您的爱心❤️");
    }
    
    /**
     * 颁发徽章
     */
    private void awardBadge(LitemallUser user) {
        List<String> badges = new ArrayList<>();
        if (user.getBadges() != null) {
            badges = JSON.parseArray(user.getBadges(), String.class);
        }
        
        int count = user.getDonationCount();
        
        // 捐赠 5 次 → 爱心大使
        if (count == 5 && !badges.contains("爱心大使")) {
            badges.add("爱心大使");
            notifyService.notify(user.getId(), "恭喜获得【爱心大使】徽章！");
        }
        
        // 捐赠 10 次 → 公益达人
        if (count == 10 && !badges.contains("公益达人")) {
            badges.add("公益达人");
            notifyService.notify(user.getId(), "恭喜获得【公益达人】徽章！");
        }
        
        // 捐赠 20 次 → 环保先锋
        if (count == 20 && !badges.contains("环保先锋")) {
            badges.add("环保先锋");
            notifyService.notify(user.getId(), "恭喜获得【环保先锋】徽章！");
        }
        
        user.setBadges(JSON.toJSONString(badges));
    }
    
    /**
     * 查询捐赠站点列表
     */
    public List<SicauDonationPoint> queryDonationPoints(String campus) {
        return donationPointMapper.selectByCampus(campus);
    }
    
    /**
     * 查询我的捐赠记录
     */
    public List<SicauDonation> queryMyDonations(Integer userId, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        return donationMapper.selectByUserId(userId);
    }
}
```

### 4.2 积分规则扩展

在 `litemall-core/src/main/java/org/linlinjava/litemall/core/service/CreditScoreService.java` 中新增：

```java
public enum CreditRule {
    // ... 现有规则
    
    DONATION(20, "完成公益捐赠");
    
    // ... 其他代码
}
```

---

## 5. API 契约定义

### 5.1 POST /wx/donation/submit - 提交捐赠申请

**请求体**:
```json
{
  "category": 1,
  "quantity": 5,
  "images": ["https://...", "https://..."],
  "pickupType": 2,
  "pickupAddress": "7舍A栋 501",
  "pickupTime": "2025-10-28 14:00:00"
}
```

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "donationId": 100
  },
  "errmsg": "捐赠申请已提交，管理员将在 24 小时内审核"
}
```

### 5.2 GET /wx/donation/points - 捐赠站点列表

**请求参数**:
- `campus`: 校区（雅安本部/成都校区）

**响应体**:
```json
{
  "errno": 0,
  "data": [
    {
      "id": 1,
      "name": "图书馆一楼捐赠站",
      "address": "图书馆一楼大厅东侧",
      "contactName": "学生会",
      "contactPhone": "028-86290000",
      "openTime": "周一至周五 8:00-18:00"
    }
  ]
}
```

### 5.3 GET /wx/donation/myList - 我的捐赠记录

**请求参数**:
- `page`, `size`

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "total": 3,
    "list": [
      {
        "id": 100,
        "category": 1,
        "categoryName": "衣物",
        "quantity": 5,
        "images": ["https://...", "https://..."],
        "status": 3,
        "statusName": "已完成",
        "addTime": "2025-10-20 10:00:00",
        "finishTime": "2025-10-22 14:00:00"
      }
    ]
  }
}
```

### 5.4 POST /admin/donation/audit - 审核捐赠

**请求体**:
```json
{
  "donationId": 100,
  "pass": true,
  "rejectReason": null
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "审核成功"
}
```

### 5.5 POST /admin/donation/finish - 确认收货

**请求体**:
```json
{
  "donationId": 100
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "收货确认成功，用户已获得 +20 积分"
}
```

---

## 6. 配置文件变更

### 6.1 application-wx.yml

```yaml
# 公益捐赠配置
donation:
  # 积分奖励
  credit_reward: 20
  
  # 徽章配置
  badges:
    - name: "爱心大使"
      count: 5
    - name: "公益达人"
      count: 10
    - name: "环保先锋"
      count: 20
  
  # 审核配置
  audit:
    timeout_hours: 24  # 审核超时提醒
```

---

## 7. 前端开发要点

### 7.1 小程序端关键文件

- `pages/donation/submit/submit.js` - 提交捐赠页
- `pages/donation/list/list.js` - 我的捐赠记录
- `pages/donation/points/points.js` - 捐赠站点列表
- `pages/user/profile/profile.js` - 个人主页（展示徽章）

### 7.2 徽章展示

```javascript
// pages/user/profile/profile.js
data: {
  badges: []
},

onLoad() {
  wx.request({
    url: 'https://api.xxx.com/wx/user/profile',
    success: (res) => {
      this.setData({
        badges: res.data.data.badges || []
      });
    }
  });
}
```

**WXML**:
```xml
<view class="badges">
  <view wx:for="{{badges}}" wx:key="index" class="badge">
    <image src="/images/badge-{{item}}.png" />
    <text>{{item}}</text>
  </view>
</view>
```

---

## 8. 测试策略

### 8.1 单元测试

- 积分奖励测试（完成捐赠 +20 分）
- 徽章颁发测试（5 次 → 爱心大使，10 次 → 公益达人）
- 捐赠状态流转测试

### 8.2 集成测试

- 完整捐赠流程测试（提交 → 审核 → 确认收货 → 积分到账）
- 审核拒绝流程测试
- 上门取件流程测试（分配志愿者）

### 8.3 用户验收测试

- 捐赠站点信息准确性
- 徽章正确显示在个人主页

---

## 9. 依赖关系

### 前置条件
- 阿里云 OSS 已配置（图片上传）
- 信用积分系统已实现（Epic 1）

### 后续依赖
- 无直接依赖

---

## 10. 风险提示

1. **审核人力**: 需要管理员或志愿者审核，人力成本较高
2. **捐赠物品质量**: 可能收到破损物品，需严格审核标准
3. **志愿者资源**: 上门取件需要志愿者支持，资源有限
4. **隐私保护**: 捐赠者地址信息需脱敏展示
5. **刷单风险**: 防止用户虚假捐赠刷积分（需人工审核照片）

---

## 11. Story 任务分解

### Story 6.1: 提交捐赠申请 (8h)
- Task 1: 创建 `sicau_donation` 表
- Task 2: 创建 `sicau_donation_point` 表并预置数据
- Task 3: 实现 `SicauDonationService.submit()` 方法
- Task 4: 前端捐赠申请页开发（图片上传、分类选择）
- Task 5: 查询捐赠站点接口

### Story 6.2: 管理员审核捐赠 (6h)
- Task 1: 实现 `audit()` 接口
- Task 2: 管理后台审核列表页开发
- Task 3: 查看物品照片（大图预览）
- Task 4: 通过/拒绝操作
- Task 5: 审核通知推送

### Story 6.3: 捐赠完成奖励 (10h)
- Task 1: 实现 `finish()` 接口
- Task 2: 积分奖励（+20 分）
- Task 3: 扩展 `litemall_user` 表（badges 字段）
- Task 4: 实现徽章颁发逻辑
- Task 5: 前端个人主页展示徽章
- Task 6: 徽章图片设计（3 个徽章）

---

## 12. 验收清单

- [ ] 可成功提交捐赠申请（上传 1-3 张照片）
- [ ] 管理后台可查看待审核捐赠
- [ ] 审核通过后用户收到通知
- [ ] 审核拒绝后显示拒绝原因
- [ ] 确认收货后用户获得 +20 积分
- [ ] 捐赠 5 次自动获得"爱心大使"徽章
- [ ] 徽章正确显示在个人主页
- [ ] 捐赠站点列表信息准确
- [ ] 所有 API 无编译错误

---

**Epic 状态**: 准备就绪，等待开发  
**下一步**: 由 bmm-dev 开发者开始实施
