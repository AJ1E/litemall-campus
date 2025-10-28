# 快递员通知推送功能实现文档

**功能名称**: 快递员通知推送系统  
**实现状态**: ✅ 已完成  
**完成时间**: 2025-10-28 09:18 UTC  
**依赖模块**: litemall-core (NotifyService)

---

## 功能概述

实现快递员业务场景中的通知推送功能，包括短信通知和微信模板消息（TODO）。

### 支持的通知场景

1. **接单通知** - 通知买家订单已被快递员接单（包含取件码）
2. **配送完成通知** - 通知买家订单已送达
3. **配送超时通知** - 通知快递员配送超时（含扣分和超时次数）
4. **提现成功通知** - 通知快递员提现到账
5. **提现失败通知** - 通知快递员提现失败及原因

---

## 技术架构

### 1. 通知类型扩展

**文件**: `litemall-core/src/main/java/org/linlinjava/litemall/core/notify/NotifyType.java`

新增4个通知类型枚举值：

```java
// 快递员通知类型
COURIER_ACCEPT("courierAccept"),        // 快递员接单通知（通知买家）
COURIER_DELIVER("courierDeliver"),      // 配送完成通知（通知买家）
COURIER_TIMEOUT("courierTimeout"),      // 配送超时通知（通知快递员）
COURIER_WITHDRAW("courierWithdraw");    // 提现到账通知（通知快递员）
```

### 2. 通知服务类

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/service/CourierNotifyService.java`

**职责**: 封装快递员相关的通知发送逻辑

**核心方法**:

#### 2.1 notifyBuyerOrderAccepted()
通知买家订单已被快递员接单

```java
@Async
public void notifyBuyerOrderAccepted(Integer userId, Integer orderId, 
                                    String pickupCode, String courierName) {
    // 参数：{1}订单号 {2}取件码 {3}快递员姓名
    String[] params = new String[]{
        orderId.toString(),
        pickupCode,
        courierName
    };
    
    notifyService.notifySmsTemplate(mobile, NotifyType.COURIER_ACCEPT, params);
}
```

**短信模板示例**:
```
【校园闲置】您的订单{1}已由快递员{3}接单，取件码：{2}，请准备好商品等待配送。
```

#### 2.2 notifyBuyerOrderDelivered()
通知买家配送完成

```java
@Async
public void notifyBuyerOrderDelivered(Integer userId, Integer orderId, String courierName) {
    // 参数：{1}订单号 {2}快递员姓名
    String[] params = new String[]{
        orderId.toString(),
        courierName
    };
    
    notifyService.notifySmsTemplate(mobile, NotifyType.COURIER_DELIVER, params);
}
```

**短信模板示例**:
```
【校园闲置】您的订单{1}已由快递员{2}送达，请确认收货。感谢使用学生快递员服务！
```

#### 2.3 notifyCourierTimeout()
通知快递员配送超时

```java
@Async
public void notifyCourierTimeout(Integer courierId, Integer orderId, 
                                Integer timeoutCount, Integer creditDeduct) {
    // 参数：{1}订单号 {2}扣除积分 {3}累计超时次数
    String[] params = new String[]{
        orderId.toString(),
        creditDeduct.toString(),
        timeoutCount.toString()
    };
    
    notifyService.notifySmsTemplate(mobile, NotifyType.COURIER_TIMEOUT, params);
}
```

**短信模板示例**:
```
【校园闲置】订单{1}配送超时，已扣除{2}信用分。累计超时{3}次，超时3次将取消快递员资格。
```

#### 2.4 notifyCourierWithdrawSuccess()
通知快递员提现成功

```java
@Async
public void notifyCourierWithdrawSuccess(Integer courierId, String withdrawSn, String amount) {
    // 参数：{1}提现单号 {2}到账金额
    String[] params = new String[]{
        withdrawSn,
        amount
    };
    
    notifyService.notifySmsTemplate(mobile, NotifyType.COURIER_WITHDRAW, params);
}
```

**短信模板示例**:
```
【校园闲置】您的提现申请（单号{1}）已到账，金额{2}元。请查收微信钱包。
```

#### 2.5 notifyCourierWithdrawFailed()
通知快递员提现失败

```java
@Async
public void notifyCourierWithdrawFailed(Integer courierId, String withdrawSn, 
                                       String amount, String failReason) {
    String message = String.format(
        "您的提现申请（单号:%s，金额:%s元）处理失败，原因：%s。如有疑问请联系客服。", 
        withdrawSn, amount, failReason
    );
    
    notifyService.notifySms(mobile, message);
}
```

---

## 集成调用

### 1. 接单通知

**位置**: `WxCourierController.acceptOrder()`

```java
@PostMapping("/acceptOrder")
public Object acceptOrder(@LoginUser Integer userId, @RequestBody Map<String, Integer> body) {
    // ... 业务逻辑
    
    Map<String, Object> result = courierService.acceptOrder(userId, orderId);
    
    // 发送通知
    try {
        LitemallOrder order = orderService.findById(orderId);
        LitemallUser courierUser = userService.findById(userId);
        String courierName = courierUser != null ? courierUser.getNickname() : null;
        String pickupCode = (String) result.get("pickupCode");
        
        notifyService.notifyBuyerOrderAccepted(order.getUserId(), orderId, 
                                              pickupCode, courierName);
    } catch (Exception e) {
        // 通知失败不影响主流程
        logger.warn("发送接单通知失败: " + e.getMessage());
    }
    
    return ResponseUtil.ok(result);
}
```

**通知时机**: 订单状态从 201（待发货）更新为 301（待收货）后

### 2. 配送完成通知

**位置**: `WxCourierController.completeOrder()`

```java
@PostMapping("/completeOrder")
public Object completeOrder(@LoginUser Integer userId, @RequestBody Map<String, Object> body) {
    // ... 业务逻辑
    
    Map<String, Object> result = courierService.completeOrder(userId, orderId, pickupCode);
    
    // 发送通知
    try {
        LitemallOrder order = orderService.findById(orderId);
        LitemallUser courierUser = userService.findById(userId);
        String courierName = courierUser != null ? courierUser.getNickname() : null;
        
        notifyService.notifyBuyerOrderDelivered(order.getUserId(), orderId, courierName);
    } catch (Exception e) {
        logger.warn("发送配送完成通知失败: " + e.getMessage());
    }
    
    return ResponseUtil.ok(result);
}
```

**通知时机**: 订单状态从 301（待收货）更新为 401（已收货）后

### 3. 配送超时通知

**位置**: `CourierTimeoutTask.handleTimeout()`

```java
@Transactional
public void handleTimeout(LitemallOrder order) {
    // ... 扣分、记录超时次数
    
    // 发送超时通知
    try {
        notifyService.notifyCourierTimeout(courierId, order.getId(), 
                                          courier.getTimeoutCount(), 10);
    } catch (Exception e) {
        // 通知失败不影响主流程
    }
    
    // ... 释放订单
}
```

**通知时机**: 
- 定时任务每10分钟扫描超时订单
- 超时 = 接单时间（ship_time） + 2小时 < 当前时间
- 无论超时几次，每次都发送通知

---

## 异步机制

所有通知方法都使用 `@Async` 注解，实现异步发送：

```java
@Async
public void notifyBuyerOrderAccepted(...) {
    // 异步执行，不阻塞主流程
}
```

**优点**:
1. 不阻塞业务主流程
2. 通知失败不影响业务操作（接单、配送、超时处理）
3. 提高接口响应速度

**异常处理**:
```java
try {
    notifyService.notifyXXX(...);
} catch (Exception e) {
    // 仅记录日志，不抛出异常
    logger.warn("通知发送失败: " + e.getMessage());
}
```

---

## 配置要求

### 1. 短信服务配置

需要在 `application.yml` 中配置短信服务商信息：

```yaml
litemall:
  notify:
    sms:
      enable: true
      # 短信服务商配置（如阿里云、腾讯云）
      template:
        - name: courierAccept
          templateId: "SMS_12345678"  # 接单通知模板ID
        - name: courierDeliver
          templateId: "SMS_12345679"  # 配送完成模板ID
        - name: courierTimeout
          templateId: "SMS_12345680"  # 超时通知模板ID
        - name: courierWithdraw
          templateId: "SMS_12345681"  # 提现到账模板ID
```

### 2. 短信模板申请

需要在短信服务商平台申请以下模板（示例）：

| 模板名称 | 模板ID | 模板内容 | 参数 |
|---------|--------|---------|------|
| courierAccept | SMS_XXX1 | 您的订单{1}已由快递员{3}接单，取件码：{2}，请准备好商品等待配送。 | {1}订单号 {2}取件码 {3}快递员姓名 |
| courierDeliver | SMS_XXX2 | 您的订单{1}已由快递员{2}送达，请确认收货。感谢使用学生快递员服务！ | {1}订单号 {2}快递员姓名 |
| courierTimeout | SMS_XXX3 | 订单{1}配送超时，已扣除{2}信用分。累计超时{3}次，超时3次将取消快递员资格。 | {1}订单号 {2}扣除积分 {3}累计超时次数 |
| courierWithdraw | SMS_XXX4 | 您的提现申请（单号{1}）已到账，金额{2}元。请查收微信钱包。 | {1}提现单号 {2}到账金额 |

---

## 文件清单

### 新增文件
1. `/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/service/CourierNotifyService.java` (217 行)
   - 快递员通知服务类
   - 5个通知方法

### 修改文件
1. `/litemall-core/src/main/java/org/linlinjava/litemall/core/notify/NotifyType.java`
   - 新增 4 个通知类型枚举值

2. `/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxCourierController.java`
   - 注入 CourierNotifyService
   - acceptOrder() 中调用接单通知
   - completeOrder() 中调用配送完成通知

3. `/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/CourierTimeoutTask.java`
   - 注入 CourierNotifyService
   - handleTimeout() 中调用超时通知

---

## 编译验证

**编译状态**: ✅ BUILD SUCCESS (15.547s)

```bash
mvn clean compile -T1C -DskipTests
```

**编译结果**:
- litemall: SUCCESS (0.279s)
- litemall-db: SUCCESS (10.903s)
- litemall-core: SUCCESS (1.585s)
- litemall-wx-api: SUCCESS (1.556s)
- litemall-admin-api: SUCCESS (1.437s)
- litemall-all: SUCCESS (0.800s)
- litemall-all-war: SUCCESS (0.797s)

---

## 测试场景

### 场景 1: 接单通知

**前置条件**:
- 买家下单并支付成功（订单状态 = 201）
- 快递员调用接单接口

**执行**:
```bash
curl -X POST http://localhost:8080/wx/courier/acceptOrder \
  -H "X-Litemall-Token: xxx" \
  -H "Content-Type: application/json" \
  -d '{"orderId": 123}'
```

**预期结果**:
- ✅ 订单状态更新为 301
- ✅ 生成4位取件码
- ✅ 买家收到短信：`【校园闲置】您的订单123已由快递员张三接单，取件码：3857...`

### 场景 2: 配送完成通知

**前置条件**:
- 订单已被接单（状态 = 301）
- 快递员输入正确的取件码

**执行**:
```bash
curl -X POST http://localhost:8080/wx/courier/completeOrder \
  -H "X-Litemall-Token: xxx" \
  -H "Content-Type: application/json" \
  -d '{"orderId": 123, "pickupCode": "3857"}'
```

**预期结果**:
- ✅ 订单状态更新为 401
- ✅ 创建收入记录
- ✅ 买家收到短信：`【校园闲置】您的订单123已由快递员张三送达，请确认收货...`

### 场景 3: 配送超时通知

**前置条件**:
- 订单已接单 2小时30分钟
- 定时任务扫描到超时订单

**执行**: 定时任务自动触发

**预期结果**:
- ✅ 快递员扣除10信用分
- ✅ timeout_count += 1
- ✅ 订单释放回待配送列表
- ✅ 快递员收到短信：`【校园闲置】订单123配送超时，已扣除10信用分。累计超时1次...`

### 场景 4: 超时3次取消资格

**前置条件**:
- 快递员累计超时2次
- 第3次超时被检测到

**执行**: 定时任务自动触发

**预期结果**:
- ✅ 快递员状态更新为 3（已取消资格）
- ✅ 快递员收到短信：`【校园闲置】订单123配送超时，已扣除10信用分。累计超时3次，快递员资格已被取消。`

---

## 技术债务

### 高优先级
1. **微信模板消息集成**
   - 当前仅支持短信通知
   - 需要集成微信公众号模板消息 API
   - 需要用户关注公众号并授权
   - **影响**: 用户体验不够完整，只能依赖短信

2. **短信模板配置**
   - 当前模板ID硬编码在代码注释中
   - 需要在 application.yml 中配置
   - 需要在短信服务商平台申请模板
   - **影响**: 无法真正发送短信

### 中优先级
1. **通知发送失败重试**
   - 当前通知失败仅记录日志
   - 建议引入消息队列（RabbitMQ/Kafka）
   - 实现失败重试机制
   - **影响**: 通知可能丢失

2. **通知历史记录**
   - 当前不记录通知发送历史
   - 建议创建通知日志表
   - 便于问题排查和数据分析
   - **影响**: 无法追溯通知发送情况

3. **通知开关配置**
   - 当前无法关闭特定通知
   - 建议在配置文件中支持开关
   - 用户可自定义通知偏好
   - **影响**: 用户可能被打扰

### 低优先级
1. **站内信通知**
   - 补充应用内消息推送
   - 消息中心展示
   - 已读/未读标记

2. **通知统计**
   - 发送成功率
   - 用户阅读率
   - 通知效果分析

---

## 下一步

**当前状态**: 通知框架已完成，短信发送逻辑已集成

**后续工作**:
1. 在短信服务商平台申请模板
2. 配置 application.yml 短信模板ID
3. 集成微信公众号模板消息 API
4. 添加通知历史记录表
5. 实现提现到账后的通知调用（TODO）

---

**文档版本**: 1.0  
**最后更新**: 2025-10-28 09:18 UTC  
**实现者**: GitHub Copilot Agent  
**审核状态**: 待审核
