# Epic 4 技术上下文：学生快递员配送系统

**Epic ID**: 4  
**Epic 标题**: 学生快递员配送系统  
**优先级**: P1  
**预估工时**: 40 小时  
**依赖关系**: Epic 3（交易流程与支付）  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)

---

## 1. Epic 概述

本 Epic 实现校内学生快递员配送系统，允许信用等级达标的学生申请成为校内快递员，承接订单配送任务，赚取配送费。核心功能包括：

- **快递员申请**: 信用等级 ≥ ⭐⭐（良好）可申请
- **订单匹配**: 自动显示同校区待配送订单
- **距离计算**: 根据寝室楼栋计算配送距离
- **接单配送**: 生成 4 位取件码，完成配送确认
- **超时惩罚**: 接单后 2 小时未完成配送 -10 分，3 次违规取消资格
- **收入统计**: 查看配送收入，支持微信钱包提现

**业务价值**: 
- 降低平台配送成本（无需对接第三方快递）
- 为学生提供勤工俭学机会
- 提升交易闭环的配送效率

---

## 2. 架构决策引用

基于 `architecture.md` 中的以下关键决策：

### ADR-007: 距离计算算法
- **算法选择**: 欧几里得距离（校内配送最大距离 < 3km，平面坐标误差可忽略）
- **楼栋坐标**: 预置四川农业大学雅安校区所有寝室楼坐标
- **配送费计算**: 
  - 1km 内: 2 元
  - 1-2km: 4 元
  - 2-3km: 6 元

### ADR-008: 快递员资格管理
- **准入门槛**: 信用等级 ≥ ⭐⭐（70 分以上）
- **违规退出**: 
  - 超时配送 3 次 → 取消资格（永久）
  - 被投诉 5 次 → 取消资格（可重新申请）
- **奖励机制**: 完成 10 单 +5 分，完成 50 单 +10 分

### 技术栈
- **后端框架**: Spring Boot 2.7.x
- **地理计算**: 自定义工具类 `DistanceCalculator`
- **定时任务**: 监控超时配送（每 10 分钟扫描一次）
- **提现接口**: 微信企业付款 API

---

## 3. 数据库变更

### 3.1 新增表：sicau_courier (快递员表)

```sql
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
```

### 3.2 新增表：sicau_building (校区楼栋坐标表)

```sql
CREATE TABLE `sicau_building` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `campus` VARCHAR(50) NOT NULL COMMENT '校区: 雅安本部, 成都校区',
  `building_name` VARCHAR(100) NOT NULL COMMENT '楼栋名称: 7舍A栋, 信息楼',
  `latitude` DECIMAL(10,6) NOT NULL COMMENT '纬度',
  `longitude` DECIMAL(10,6) NOT NULL COMMENT '经度',
  `building_type` TINYINT DEFAULT 1 COMMENT '类型: 1-寝室, 2-教学楼, 3-食堂, 4-其他',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_campus` (`campus`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校区楼栋坐标表';
```

**预置数据**（雅安校区示例）：
```sql
INSERT INTO `sicau_building` (`campus`, `building_name`, `latitude`, `longitude`, `building_type`) VALUES
('雅安本部', '7舍A栋', 29.9854, 102.9967, 1),
('雅安本部', '7舍B栋', 29.9856, 102.9970, 1),
('雅安本部', '11舍', 29.9860, 102.9980, 1),
('雅安本部', '信息楼', 29.9870, 103.0000, 2),
('雅安本部', '西苑食堂', 29.9865, 102.9985, 3);
-- 实际应包含所有楼栋坐标
```

### 3.3 新增表：sicau_courier_income (快递员收入流水表)

```sql
CREATE TABLE `sicau_courier_income` (
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
```

### 3.4 新增表：sicau_courier_withdraw (提现记录表)

```sql
CREATE TABLE `sicau_courier_withdraw` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `courier_id` INT NOT NULL COMMENT '快递员用户ID',
  `withdraw_sn` VARCHAR(64) NOT NULL COMMENT '提现单号',
  `withdraw_amount` DECIMAL(10,2) NOT NULL COMMENT '提现金额（元）',
  `fee_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '手续费（元）',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-已到账, 2-失败',
  `wx_transfer_id` VARCHAR(100) COMMENT '微信付款单号',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `success_time` DATETIME COMMENT '到账时间',
  INDEX `idx_courier_id` (`courier_id`),
  INDEX `idx_withdraw_sn` (`withdraw_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递员提现记录表';
```

### 3.5 复用 litemall_order 表

**使用字段**:
- `courier_id`: 快递员用户ID（Epic 3 中已新增）
- `delivery_type`: 1-学生快递员配送
- `pickup_code`: 4 位取件码（快递员给买家）
- `address`: 收货地址（包含楼栋信息，用于计算距离）

---

## 4. 核心代码实现指导

### 4.1 距离计算工具类

创建 `litemall-core/src/main/java/org/linlinjava/litemall/core/util/DistanceCalculator.java`：

```java
package org.linlinjava.litemall.core.util;

/**
 * 距离计算工具类
 */
public class DistanceCalculator {
    
    /**
     * 计算两点之间的欧几里得距离（单位：km）
     * @param lat1 纬度1
     * @param lon1 经度1
     * @param lat2 纬度2
     * @param lon2 经度2
     * @return 距离（km）
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // 纬度1度 ≈ 111 km，经度1度 ≈ 111 * cos(lat) km
        double latDiff = (lat2 - lat1) * 111;
        double lonDiff = (lon2 - lon1) * 111 * Math.cos(Math.toRadians(lat1));
        
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }
    
    /**
     * 根据距离计算配送费
     * @param distance 距离（km）
     * @return 配送费（元）
     */
    public static double calculateFee(double distance) {
        if (distance <= 1.0) {
            return 2.0;
        } else if (distance <= 2.0) {
            return 4.0;
        } else {
            return 6.0;
        }
    }
}
```

### 4.2 快递员申请服务

创建 `litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauCourierService.java`：

```java
package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauCourierMapper;
import org.linlinjava.litemall.db.domain.SicauCourier;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 快递员服务
 */
@Service
public class SicauCourierService {
    
    @Resource
    private SicauCourierMapper courierMapper;
    
    @Autowired
    private SicauStudentAuthService studentAuthService;
    
    @Autowired
    private CreditScoreService creditScoreService;
    
    /**
     * 申请成为快递员
     * @param userId 用户ID
     * @param applyReason 申请理由
     * @return 是否申请成功
     */
    @Transactional
    public boolean apply(Integer userId, String applyReason) {
        // 1. 检查是否已申请
        SicauCourier existCourier = courierMapper.selectByUserId(userId);
        if (existCourier != null) {
            if (existCourier.getStatus() == 3) {
                throw new RuntimeException("您的快递员资格已被取消，无法重新申请");
            }
            throw new RuntimeException("您已申请过快递员");
        }
        
        // 2. 检查学号认证
        SicauStudentAuth studentAuth = studentAuthService.findByUserId(userId);
        if (studentAuth == null || studentAuth.getAuthStatus() != 1) {
            throw new RuntimeException("请先完成学号认证");
        }
        
        // 3. 检查信用等级（≥ 70 分）
        int creditScore = creditScoreService.getCreditScore(userId);
        if (creditScore < 70) {
            throw new RuntimeException("信用等级不足 ⭐⭐（良好），无法申请");
        }
        
        // 4. 创建快递员申请
        SicauCourier courier = new SicauCourier();
        courier.setUserId(userId);
        courier.setStatus((byte) 0); // 待审核
        courier.setApplyReason(applyReason);
        courier.setApplyTime(LocalDateTime.now());
        courierMapper.insertSelective(courier);
        
        return true;
    }
    
    /**
     * 查询待配送订单列表（同校区）
     */
    public List<Map<String, Object>> queryPendingOrders(Integer courierId) {
        // 1. 获取快递员所在校区
        SicauStudentAuth studentAuth = studentAuthService.findByUserId(courierId);
        String campus = studentAuth.getCampus();
        
        // 2. 查询订单（状态=201 待发货，配送方式=1 学生快递员，未分配快递员）
        List<LitemallOrder> orders = orderMapper.selectPendingDeliveryOrders(campus);
        
        // 3. 计算配送距离和配送费
        List<Map<String, Object>> result = new ArrayList<>();
        for (LitemallOrder order : orders) {
            Map<String, Object> item = new HashMap<>();
            item.put("orderId", order.getId());
            item.put("orderSn", order.getOrderSn());
            item.put("consignee", order.getConsignee());
            item.put("mobile", order.getMobile());
            item.put("address", order.getAddress());
            
            // 计算距离
            double distance = calculateOrderDistance(order, studentAuth);
            item.put("distance", distance);
            item.put("fee", DistanceCalculator.calculateFee(distance));
            
            result.add(item);
        }
        
        return result;
    }
    
    /**
     * 接单
     */
    @Transactional
    public boolean acceptOrder(Integer courierId, Integer orderId) {
        // 1. 检查快递员资格
        SicauCourier courier = courierMapper.selectByUserId(courierId);
        if (courier == null || courier.getStatus() != 1) {
            throw new RuntimeException("您不是认证快递员");
        }
        
        // 2. 检查订单是否已被接
        LitemallOrder order = orderService.findById(orderId);
        if (order.getCourierId() != null) {
            throw new RuntimeException("该订单已被其他快递员接取");
        }
        
        // 3. 分配快递员
        order.setCourierId(courierId);
        order.setShipTime(LocalDateTime.now());
        order.setOrderStatus((short) 301); // 待收货
        
        // 生成 4 位取件码
        String pickupCode = generatePickupCode();
        order.setPickupCode(pickupCode);
        orderService.update(order);
        
        // 4. 推送通知给买家
        notifyService.notifyBuyer(order.getUserId(), 
            "您的订单已由快递员接单，取件码：" + pickupCode);
        
        return true;
    }
    
    /**
     * 生成 4 位取件码
     */
    private String generatePickupCode() {
        return String.format("%04d", new Random().nextInt(10000));
    }
}
```

### 4.3 超时配送监控定时任务

创建 `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/CourierTimeoutTask.java`：

```java
package org.linlinjava.litemall.wx.task;

import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.SicauCourier;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.SicauCourierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 快递员超时配送监控
 */
@Component
public class CourierTimeoutTask {
    
    @Autowired
    private LitemallOrderService orderService;
    
    @Autowired
    private SicauCourierService courierService;
    
    @Autowired
    private CreditScoreService creditScoreService;
    
    /**
     * 每 10 分钟扫描一次超时配送
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void checkTimeoutDelivery() {
        LocalDateTime now = LocalDateTime.now();
        
        // 查询状态=301（待收货）且快递员已接单的订单
        List<LitemallOrder> orders = orderService.queryByStatus((short) 301);
        
        for (LitemallOrder order : orders) {
            if (order.getCourierId() == null) {
                continue;
            }
            
            // 接单后 2 小时未完成配送视为超时
            if (order.getShipTime().plusHours(2).isBefore(now)) {
                handleTimeout(order);
            }
        }
    }
    
    /**
     * 处理超时配送
     */
    private void handleTimeout(LitemallOrder order) {
        Integer courierId = order.getCourierId();
        SicauCourier courier = courierService.findByUserId(courierId);
        
        // 1. 扣除快递员 10 积分
        creditScoreService.updateCreditScore(courierId, -10);
        
        // 2. 增加超时次数
        courier.setTimeoutCount(courier.getTimeoutCount() + 1);
        
        // 3. 超时 3 次取消资格
        if (courier.getTimeoutCount() >= 3) {
            courier.setStatus((byte) 3); // 已取消资格
            courierService.update(courier);
            
            // 推送通知
            notifyService.notify(courierId, "因配送超时3次，您的快递员资格已被取消");
        } else {
            courierService.update(courier);
            
            // 推送警告
            notifyService.notify(courierId, 
                String.format("订单 %s 配送超时，已扣除 10 积分，再超时 %d 次将取消资格", 
                    order.getOrderSn(), 3 - courier.getTimeoutCount()));
        }
        
        // 4. 释放订单（回到待配送列表）
        order.setCourierId(null);
        order.setOrderStatus((short) 201); // 重新待发货
        orderService.update(order);
    }
}
```

### 4.4 收入统计与提现

创建 `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxCourierController.java`：

```java
package org.linlinjava.litemall.wx.web;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.service.SicauCourierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 快递员接口
 */
@RestController
@RequestMapping("/wx/courier")
public class WxCourierController {
    
    @Autowired
    private SicauCourierService courierService;
    
    /**
     * 查询收入统计
     */
    @GetMapping("/income")
    public Object getIncome(@LoginUser Integer userId) {
        Map<String, Object> data = courierService.getIncomeStats(userId);
        return ResponseUtil.ok(data);
    }
    
    /**
     * 申请提现
     */
    @PostMapping("/withdraw")
    public Object withdraw(@LoginUser Integer userId, @RequestBody Map<String, Object> body) {
        Double amount = (Double) body.get("amount");
        
        if (amount < 10) {
            return ResponseUtil.badArgument("提现金额不能低于 10 元");
        }
        
        courierService.withdraw(userId, amount);
        return ResponseUtil.ok("提现申请已提交，1-3 个工作日到账");
    }
}
```

---

## 5. API 契约定义

### 5.1 POST /wx/courier/apply - 申请成为快递员

**请求体**:
```json
{
  "applyReason": "我是大三学生，课余时间充足，想通过配送赚取生活费"
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "申请成功，请等待管理员审核"
}
```

### 5.2 GET /wx/courier/pendingOrders - 查看待配送订单

**请求参数**: 无（根据登录用户 ID 自动获取）

**响应体**:
```json
{
  "errno": 0,
  "data": [
    {
      "orderId": 100,
      "orderSn": "20251027123456",
      "consignee": "张三",
      "mobile": "138****8000",
      "address": "7舍A栋 501",
      "distance": 1.2,
      "fee": 4.0
    }
  ]
}
```

### 5.3 POST /wx/courier/acceptOrder - 接单

**请求体**:
```json
{
  "orderId": 100
}
```

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "pickupCode": "1234",
    "consignee": "张三",
    "mobile": "13800138000",
    "address": "7舍A栋 501"
  }
}
```

### 5.4 POST /wx/courier/completeOrder - 完成配送

**请求体**:
```json
{
  "orderId": 100,
  "pickupCode": "1234"
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "配送完成，+4 元收入"
}
```

### 5.5 GET /wx/courier/income - 收入统计

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "totalIncome": 120.00,
    "totalOrders": 30,
    "availableBalance": 100.00,
    "withdrawing": 20.00,
    "recentIncomes": [
      {
        "orderSn": "20251027123456",
        "income": 4.00,
        "distance": 1.2,
        "addTime": "2025-10-27 10:30:00"
      }
    ]
  }
}
```

### 5.6 POST /wx/courier/withdraw - 申请提现

**请求体**:
```json
{
  "amount": 100.00
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "提现申请已提交，1-3 个工作日到账"
}
```

---

## 6. 配置文件变更

### 6.1 application-wx.yml

```yaml
# 快递员配置
courier:
  # 资格要求
  qualification:
    min_credit_score: 70  # 最低信用分
  
  # 超时配置
  timeout:
    delivery_hours: 2  # 接单后必须在 2 小时内完成配送
    max_timeout_count: 3  # 超时 3 次取消资格
  
  # 提现配置
  withdraw:
    min_amount: 10.00  # 最低提现金额
    fee_rate: 0.006  # 手续费率（0.6%）
```

---

## 7. 前端开发要点

### 7.1 小程序端关键文件

- `pages/courier/apply/apply.js` - 快递员申请页
- `pages/courier/orders/orders.js` - 待配送订单列表
- `pages/courier/income/income.js` - 收入统计页
- `pages/courier/withdraw/withdraw.js` - 提现页面

### 7.2 地图展示（可选）

```javascript
// pages/courier/orders/orders.js
// 使用腾讯地图 SDK 展示订单位置
import QQMapWX from '../../../lib/qqmap-wx-jssdk.min.js';

const qqmapsdk = new QQMapWX({
  key: 'YOUR_TENCENT_MAP_KEY'
});

// 显示订单位置
qqmapsdk.reverseGeocoder({
  location: {
    latitude: order.latitude,
    longitude: order.longitude
  },
  success: (res) => {
    console.log('地址:', res.result.address);
  }
});
```

---

## 8. 测试策略

### 8.1 单元测试

- 距离计算准确性测试（误差 < 5%）
- 配送费计算测试（1km/2km/3km 边界值）
- 快递员资格验证测试（信用分 69 vs 70）

### 8.2 集成测试

- 完整配送流程测试（申请 → 接单 → 完成 → 收入结算）
- 超时惩罚测试（接单后 2 小时不操作 → 自动扣分）
- 提现流程测试（申请提现 → 微信付款）

### 8.3 性能测试

- 待配送订单查询性能（1000+ 订单 < 300ms）
- 距离计算并发性能（100 单/秒）

---

## 9. 依赖关系

### 前置条件
- Epic 3 已完成（订单表已包含 `courier_id` 字段）
- 微信企业付款已配置（用于提现）
- 楼栋坐标数据已预置

### 后续依赖
- 无直接依赖

---

## 10. 风险提示

1. **地理坐标精度**: 楼栋坐标需实地测量，误差可能影响距离计算
2. **恶意接单**: 快递员接单后不配送，需监控超时
3. **提现风险**: 微信企业付款单日限额 100 万，需控制提现频率
4. **刷单风险**: 防止卖家和快递员串通刷单赚取配送费
5. **隐私保护**: 买家手机号中间 4 位脱敏展示

---

## 11. Story 任务分解

### Story 4.1: 申请成为快递员 (8h)
- Task 1: 创建 `sicau_courier` 表
- Task 2: 实现 `SicauCourierService.apply()` 方法
- Task 3: 检查信用等级（≥ 70 分）
- Task 4: 前端申请页开发
- Task 5: 管理后台审核接口（Epic 7 提前实现）

### Story 4.2: 查看待配送订单 (10h)
- Task 1: 创建 `sicau_building` 表并预置数据
- Task 2: 实现 `DistanceCalculator` 工具类
- Task 3: 实现 `queryPendingOrders()` 接口
- Task 4: 前端订单列表页开发
- Task 5: 集成腾讯地图 SDK（可选）

### Story 4.3: 接单与配送 (12h)
- Task 1: 实现 `acceptOrder()` 接口
- Task 2: 生成 4 位取件码
- Task 3: 实现 `completeOrder()` 接口
- Task 4: 验证取件码
- Task 5: 创建 `sicau_courier_income` 记录
- Task 6: 前端配送确认页开发

### Story 4.4: 配送超时处理 (6h)
- Task 1: 创建 `CourierTimeoutTask` 定时任务
- Task 2: 实现超时检测逻辑（2 小时）
- Task 3: 扣除信用积分（-10 分）
- Task 4: 3 次超时取消资格
- Task 5: 推送超时警告通知

### Story 4.5: 收入统计 (4h)
- Task 1: 创建 `sicau_courier_withdraw` 表
- Task 2: 实现 `getIncomeStats()` 接口
- Task 3: 实现 `withdraw()` 接口
- Task 4: 集成微信企业付款 API
- Task 5: 前端收入统计页开发

---

## 12. 验收清单

- [ ] 信用等级 ≥ ⭐⭐ 可成功申请快递员
- [ ] 待配送订单列表正确显示同校区订单
- [ ] 距离和配送费计算准确（误差 < 5%）
- [ ] 接单后生成 4 位取件码
- [ ] 完成配送后快递员收入正确增加
- [ ] 超时 2 小时自动扣除 10 积分
- [ ] 超时 3 次自动取消快递员资格
- [ ] 提现功能正常（金额 ≥ 10 元）
- [ ] 所有 API 无编译错误

---

**Epic 状态**: 准备就绪，等待开发  
**下一步**: 由 bmm-dev 开发者开始实施
