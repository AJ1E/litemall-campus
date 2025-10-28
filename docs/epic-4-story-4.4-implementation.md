# Story 4.4 实现文档：配送超时处理

**Story ID**: 4.4  
**Story 标题**: 配送超时处理  
**状态**: ✅ 已完成  
**预估工时**: 6 小时  
**实际工时**: 0.2 小时  
**完成时间**: 2025-10-28 08:17 UTC  
**依赖**: Story 4.3 - 接单与配送

---

## 功能概述

实现快递员超时配送监控机制，自动扫描超时订单并进行惩罚处理：

1. **定时扫描**: 每 10 分钟扫描一次状态为 301（待收货）的订单
2. **超时判定**: 接单后 2 小时未完成配送视为超时
3. **自动惩罚**:
   - 扣除快递员 10 积分
   - 超时次数 +1
   - 超时 3 次取消快递员资格
4. **订单释放**: 超时订单重新回到待配送列表（状态 301→201）

---

## 技术实现

### 1. 定时任务实现

创建 `CourierTimeoutTask` 定时任务，使用 Spring @Scheduled 注解：

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/CourierTimeoutTask.java`

**核心逻辑**:
```java
@Scheduled(cron = "0 */10 * * * ?")
public void checkTimeoutDelivery() {
    // 1. 查询状态=301（待收货）的所有订单
    List<LitemallOrder> orders = orderService.queryByStatus((short) 301);
    
    // 2. 计算超时阈值（当前时间 - 2小时）
    LocalDateTime timeoutThreshold = now.minusHours(2);
    
    // 3. 遍历订单，处理超时配送
    for (LitemallOrder order : orders) {
        if (order.getCourierId() != null && 
            order.getShipTime() != null && 
            order.getShipTime().isBefore(timeoutThreshold)) {
            handleTimeout(order);
        }
    }
}
```

### 2. 超时处理流程

```java
@Transactional
public void handleTimeout(LitemallOrder order) {
    // 1. 获取快递员信息
    SicauCourier courier = courierService.findByUserId(courierId);
    
    // 2. 扣除 10 积分
    LitemallUser user = userService.findById(courierId);
    user.setCreditScore(Math.max(0, currentScore - 10));
    userService.updateById(user);
    
    // 3. 增加超时次数
    courier.setTimeoutCount(currentTimeoutCount + 1);
    
    // 4. 判断是否取消资格（超时 3 次）
    if (courier.getTimeoutCount() >= 3) {
        courier.setStatus((byte) 3); // 已取消资格
    }
    courierService.updateById(courier);
    
    // 5. 释放订单（回到待配送列表）
    order.setCourierId(null);
    order.setOrderStatus((short) 201);
    order.setShipTime(null);
    order.setPickupCode(null);
    orderService.updateWithOptimisticLocker(order);
}
```

### 3. 状态流转

```
订单状态:
201 (待发货) → 301 (待收货，快递员已接单) → [超时2小时] → 201 (重新待发货)

快递员状态:
1 (已通过) → [超时1次] → 1 (已通过, timeout_count=1)
            → [超时2次] → 1 (已通过, timeout_count=2)
            → [超时3次] → 3 (已取消资格)
```

---

## 数据库变更

**无新增表**，复用现有字段：

### sicau_courier 表
- `timeout_count`: 超时次数（每次超时 +1）
- `status`: 快递员状态（3 = 已取消资格）

### litemall_user 表
- `credit_score`: 信用积分（每次超时 -10）

### litemall_order 表
- `courier_id`: 快递员ID（超时后设为 NULL）
- `order_status`: 订单状态（301→201）
- `ship_time`: 发货时间（用于判断超时）
- `pickup_code`: 取件码（超时后清空）

---

## 配置说明

### Cron 表达式

```
0 */10 * * * ?
│  │  │ │ │ │
│  │  │ │ │ └─ 周（任意）
│  │  │ │ └─── 月（任意）
│  │  │ └───── 日（任意）
│  │  └─────── 时（任意）
│  └────────── 分（每10分钟）
└──────────── 秒（0秒）
```

**执行频率**: 每小时执行 6 次（00:00, 00:10, 00:20, 00:30, 00:40, 00:50）

### Spring Scheduling

项目中已启用 `@EnableScheduling`：
- `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/Application.java`
- `litemall-all/src/main/java/org/linlinjava/litemall/Application.java`

---

## 业务规则

### 1. 超时判定
- **触发条件**: `ship_time + 2小时 < 当前时间`
- **扫描范围**: 仅扫描 `order_status = 301` 且 `courier_id IS NOT NULL` 的订单
- **边界情况**: 如果快递员已被取消资格（status=3），跳过处理

### 2. 积分扣除
- **扣除分值**: 每次超时扣除 10 分
- **最低分值**: 0 分（不会出现负数）
- **默认分值**: 如果 `credit_score` 为 NULL，默认为 100 分

### 3. 资格取消
- **触发条件**: `timeout_count >= 3`
- **状态变更**: `status` 从 1（已通过）变为 3（已取消资格）
- **后续影响**: 快递员无法再接单（前端接口会拦截）

### 4. 订单释放
- **状态回退**: 301 → 201（重新待发货）
- **字段清空**: 
  - `courier_id` 设为 NULL
  - `ship_time` 设为 NULL
  - `pickup_code` 设为 NULL
- **可重新接单**: 其他快递员可以再次接取该订单

---

## 测试场景

### 场景 1: 正常超时处理

**前置条件**:
- 快递员 A 接单，当前时间 08:00
- 订单状态 301，ship_time = 08:00
- 快递员 timeout_count = 0, credit_score = 100

**执行**:
- 等待到 10:10（超时 2 小时 10 分钟）
- 定时任务扫描到该订单

**预期结果**:
- ✅ 快递员 timeout_count = 1
- ✅ 快递员 credit_score = 90
- ✅ 订单 order_status = 201, courier_id = NULL
- ✅ 日志记录: "快递员 X 超时次数: 1/3"

### 场景 2: 第三次超时取消资格

**前置条件**:
- 快递员 B 已超时 2 次（timeout_count = 2）
- 第三次接单后再次超时

**预期结果**:
- ✅ 快递员 timeout_count = 3
- ✅ 快递员 status = 3（已取消资格）
- ✅ 快递员 credit_score = 70（原80 - 10）
- ✅ 日志警告: "快递员 X 因配送超时 3 次，已取消资格"

### 场景 3: 已取消资格快递员的订单

**前置条件**:
- 快递员 C 已被取消资格（status = 3）
- 历史订单仍在超时中

**预期结果**:
- ✅ 跳过处理该订单
- ✅ 日志: "快递员 X 已被取消资格，跳过订单 Y"

### 场景 4: 边界时间测试

**测试用例**:
- 接单时间: 08:00:00
- 超时阈值: 10:00:00
- 扫描时间: 10:00:01 → **应触发超时**
- 扫描时间: 09:59:59 → **不应触发超时**

---

## 日志示例

### 正常扫描
```
2025-10-28 10:00:00 INFO  CourierTimeoutTask - 开始扫描超时配送订单...
2025-10-28 10:00:01 INFO  CourierTimeoutTask - 超时配送扫描完成，共处理 0 个超时订单
```

### 发现超时
```
2025-10-28 10:10:00 INFO  CourierTimeoutTask - 开始扫描超时配送订单...
2025-10-28 10:10:01 INFO  CourierTimeoutTask - 快递员 123 超时配送，扣除 10 积分，当前积分: 90
2025-10-28 10:10:01 INFO  CourierTimeoutTask - 快递员 123 超时次数: 1/3
2025-10-28 10:10:01 INFO  CourierTimeoutTask - 订单 20251028001 已释放，重新回到待配送列表
2025-10-28 10:10:02 INFO  CourierTimeoutTask - 超时配送扫描完成，共处理 1 个超时订单
```

### 取消资格
```
2025-10-28 12:00:00 WARN  CourierTimeoutTask - 快递员 123 因配送超时 3 次，已取消资格
```

---

## 技术债务

### 待实现功能
1. **通知推送**: 
   - 超时警告通知（已超时 X/3 次）
   - 资格取消通知
   - 当前已标注 TODO，需集成 NotifyService

2. **买家通知**:
   - 订单超时释放后通知买家
   - "您的订单配送超时，已重新分配快递员"

3. **超时统计**:
   - 记录每次超时的详细日志（sicau_timeout_log 表）
   - 支持查询超时历史记录

### 性能优化
1. **索引优化**:
   - 为 litemall_order.ship_time 添加索引（如果数据量大）
   - 当前扫描范围: WHERE order_status=301 AND deleted=false

2. **扫描频率**:
   - 当前每 10 分钟扫描一次
   - 生产环境可考虑每 5 分钟（更及时）或每 15 分钟（降低负载）

---

## 文件清单

### 新增文件
- `/workspaces/litemall-campus/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/CourierTimeoutTask.java` (147 行)

### 依赖服务
- `LitemallOrderService` - 查询和更新订单
- `SicauCourierService` - 查询和更新快递员
- `LitemallUserService` - 更新用户积分

---

## 编译验证

**编译状态**: ✅ BUILD SUCCESS (14.331s)

```bash
mvn clean compile -pl litemall-wx-api -am -DskipTests
```

**编译结果**:
- litemall: SUCCESS (0.358s)
- litemall-db: SUCCESS (10.966s)
- litemall-core: SUCCESS (1.390s)
- litemall-wx-api: SUCCESS (1.072s)

---

## 下一步

**Story 4.5**: 收入统计 (预计 4 小时)
- 创建 `sicau_courier_withdraw` 提现记录表
- 实现 `getIncomeStats()` 收入统计 API
- 实现 `withdraw()` 提现功能
- 集成微信企业付款 API（可选）

---

**文档版本**: 1.0  
**最后更新**: 2025-10-28 08:17 UTC  
**实现者**: Developer Agent  
**审核状态**: 待审核
