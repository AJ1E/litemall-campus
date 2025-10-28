# Story 4.5 实现文档：收入统计

**Story ID**: 4.5  
**Story 标题**: 收入统计  
**状态**: ✅ 已完成  
**预估工时**: 4 小时  
**实际工时**: 0.3 小时  
**完成时间**: 2025-10-28 08:41 UTC  
**依赖**: Story 4.3 - 接单与配送

---

## 功能概述

实现快递员收入统计和提现功能，帮助快递员管理收入：

1. **收入统计**: 查看总收入、总订单数、可提现余额
2. **提现功能**: 申请提现（最低10元），创建提现记录
3. **提现记录**: 查看最近提现历史和状态
4. **收入明细**: 查看最近10笔收入记录

---

## 技术实现

### 1. 数据库表设计

#### sicau_courier_withdraw (提现记录表)

```sql
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
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `success_time` DATETIME COMMENT '到账时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_courier_id` (`courier_id`),
  INDEX `idx_withdraw_sn` (`withdraw_sn`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**字段说明**:
- `withdraw_sn`: 提现单号（格式: WD + yyyyMMddHHmmss + 用户ID后4位）
- `withdraw_amount`: 快递员申请提现的金额
- `fee_amount`: 手续费（当前为0，未来可配置）
- `actual_amount`: 实际到账金额（= withdraw_amount - fee_amount）
- `status`: 0=待处理（已申请），1=已到账，2=失败

### 2. 服务层实现

#### SicauCourierWithdrawService

**职责**: 提现记录管理

**核心方法**:

```java
// 创建提现记录
public SicauCourierWithdraw createWithdraw(Integer courierId, 
                                           BigDecimal withdrawAmount, 
                                           BigDecimal feeAmount) {
    SicauCourierWithdraw withdraw = new SicauCourierWithdraw();
    withdraw.setWithdrawSn(generateWithdrawSn(courierId));
    withdraw.setWithdrawAmount(withdrawAmount);
    withdraw.setFeeAmount(feeAmount);
    withdraw.setActualAmount(withdrawAmount.subtract(feeAmount));
    withdraw.setStatus((byte) 0); // 待处理
    
    withdrawMapper.insertSelective(withdraw);
    return withdraw;
}

// 生成提现单号
// 格式: WD20251028103045001
private String generateWithdrawSn(Integer courierId) {
    String timestamp = LocalDateTime.now().format("yyyyMMddHHmmss");
    String userIdSuffix = String.format("%04d", courierId % 10000);
    return "WD" + timestamp + userIdSuffix;
}

// 计算已提现总金额（包括处理中和已到账）
public BigDecimal getTotalWithdrawn(Integer courierId) {
    // status IN (0, 1) = 待处理 + 已到账
    List<SicauCourierWithdraw> withdraws = ...;
    return withdraws.stream()
        .map(SicauCourierWithdraw::getWithdrawAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

#### SicauCourierService 扩展

**新增方法**:

1. **getIncomeStats()** - 收入统计

```java
public Map<String, Object> getIncomeStats(Integer courierId) {
    // 1. 总收入和总订单（来自 sicau_courier 表）
    BigDecimal totalIncome = courier.getTotalIncome();
    Integer totalOrders = courier.getTotalOrders();
    
    // 2. 已提现金额（包括待处理和已到账）
    BigDecimal totalWithdrawn = withdrawService.getTotalWithdrawn(courierId);
    
    // 3. 可提现余额 = 总收入 - 已提现
    BigDecimal availableBalance = totalIncome.subtract(totalWithdrawn);
    
    // 4. 最近10条收入记录
    List<SicauCourierIncome> recentIncomes = incomeService.findByCourierId(courierId);
    
    // 5. 最近5条提现记录
    List<SicauCourierWithdraw> recentWithdraws = withdrawService.findByCourierId(courierId);
    
    return result;
}
```

2. **withdraw()** - 申请提现

```java
@Transactional
public String withdraw(Integer courierId, BigDecimal amount) {
    // 1. 验证快递员资格
    if (courier.getStatus() != 1) {
        throw new RuntimeException("您的快递员资格已被取消或待审核");
    }
    
    // 2. 验证提现金额
    if (amount < 10.00) {
        throw new RuntimeException("提现金额不能低于 10 元");
    }
    
    // 3. 验证余额充足
    BigDecimal availableBalance = totalIncome - totalWithdrawn;
    if (amount > availableBalance) {
        throw new RuntimeException("余额不足");
    }
    
    // 4. 创建提现记录（status=0 待处理）
    SicauCourierWithdraw withdraw = withdrawService.createWithdraw(
        courierId, amount, BigDecimal.ZERO
    );
    
    // TODO: 调用微信企业付款 API
    
    return withdraw.getWithdrawSn();
}
```

### 3. API 端点实现

#### GET /wx/courier/income - 收入统计

**响应示例**:
```json
{
  "errno": 0,
  "data": {
    "totalIncome": 120.50,
    "totalOrders": 25,
    "totalWithdrawn": 20.00,
    "availableBalance": 100.50,
    "recentIncomes": [
      {
        "orderId": 100,
        "income": 4.00,
        "distance": 1.2,
        "addTime": "2025-10-28T10:30:00"
      }
    ],
    "recentWithdraws": [
      {
        "withdrawSn": "WD20251028103045001",
        "amount": 20.00,
        "status": 1,
        "addTime": "2025-10-28T10:30:45"
      }
    ]
  }
}
```

#### POST /wx/courier/withdraw - 申请提现

**请求体**:
```json
{
  "amount": 100.00
}
```

**响应示例**:
```json
{
  "errno": 0,
  "data": {
    "withdrawSn": "WD20251028103045001",
    "message": "提现申请已提交，1-3个工作日到账"
  }
}
```

**错误码**:
- 505: 查询收入统计失败
- 506: 申请提现失败

---

## 业务规则

### 1. 提现金额限制
- **最低金额**: 10.00 元
- **最大金额**: 可提现余额
- **手续费**: 当前为 0（未来可配置）

### 2. 余额计算
```
可提现余额 = 总收入 - (待处理提现 + 已到账提现)
```

**说明**:
- 总收入来自 `sicau_courier.total_income`
- 待处理提现（status=0）锁定余额，防止重复提现
- 已到账提现（status=1）已从余额扣除
- 失败提现（status=2）释放余额

### 3. 提现状态流转
```
0 (待处理) → 1 (已到账)
           → 2 (失败)
```

- **待处理**: 快递员申请后立即创建，锁定余额
- **已到账**: 微信企业付款成功后更新
- **失败**: 付款失败时更新（如余额不足、账号异常）

### 4. 提现单号规则
```
WD + yyyyMMddHHmmss + 用户ID后4位
例: WD20251028103045001
```

**组成**:
- WD: Withdraw 缩写
- 14位时间戳: 精确到秒
- 4位用户ID后缀: 便于查询和对账

---

## 测试场景

### 场景 1: 查询收入统计

**前置条件**:
- 快递员完成了 3 笔配送
- 收入分别为: 2元, 4元, 6元（总收入12元）
- 已提现 1 次: 10元（status=1）

**执行**: GET /wx/courier/income

**预期结果**:
```json
{
  "totalIncome": 12.00,
  "totalOrders": 3,
  "totalWithdrawn": 10.00,
  "availableBalance": 2.00,
  "recentIncomes": [3条记录],
  "recentWithdraws": [1条记录]
}
```

### 场景 2: 正常提现

**前置条件**:
- 可提现余额: 100元
- 申请提现: 50元

**执行**: POST /wx/courier/withdraw {"amount": 50}

**预期结果**:
- ✅ 返回提现单号 "WD20251028..."
- ✅ 数据库创建提现记录（status=0）
- ✅ 再次查询余额: availableBalance=50元

### 场景 3: 余额不足

**前置条件**:
- 可提现余额: 5元
- 申请提现: 10元

**执行**: POST /wx/courier/withdraw {"amount": 10}

**预期结果**:
- ❌ 返回 errno=506, errmsg="余额不足，当前可提现金额: 5.00 元"
- ❌ 未创建提现记录

### 场景 4: 金额低于最低限额

**前置条件**:
- 可提现余额: 100元
- 申请提现: 5元

**执行**: POST /wx/courier/withdraw {"amount": 5}

**预期结果**:
- ❌ 返回 errno=506, errmsg="提现金额不能低于 10 元"

### 场景 5: 并发提现

**前置条件**:
- 可提现余额: 100元
- 同时发起 2 次提现请求，各 60元

**执行**: 
- 用户A: POST /wx/courier/withdraw {"amount": 60}
- 用户B: POST /wx/courier/withdraw {"amount": 60}

**预期结果** (取决于事务隔离级别):
- ✅ 第一个请求成功，余额锁定 60元
- ❌ 第二个请求失败，"余额不足，当前可提现金额: 40.00 元"

---

## 数据流转

### 收入累加流程
```
1. 快递员完成配送 (completeOrder)
   ↓
2. 创建收入记录 (sicau_courier_income)
   - income_amount: 4.00
   - settle_status: 0 (未结算)
   ↓
3. 更新快递员总收入 (sicau_courier)
   - total_income += 4.00
   - total_orders += 1
```

### 提现流程
```
1. 快递员申请提现
   ↓
2. 验证资格和余额
   ↓
3. 创建提现记录 (sicau_courier_withdraw)
   - status: 0 (待处理)
   - 锁定余额
   ↓
4. TODO: 调用微信企业付款 API
   ↓
5. 付款成功后更新状态
   - status: 1 (已到账)
   - success_time: now()
```

### 余额计算示例
```
总收入 (total_income):      120.00
待处理提现 (status=0):       20.00
已到账提现 (status=1):       50.00
失败提现 (status=2):         10.00 (不计入)
-------------------------------------------
可提现余额:                  50.00
= 120.00 - (20.00 + 50.00)
```

---

## 技术债务

### 高优先级
1. **微信企业付款集成**:
   - 当前仅创建记录（status=0）
   - 需要集成微信企业付款 API
   - 处理付款成功/失败回调
   - **影响**: 无法真正提现到微信钱包

2. **提现审核机制**:
   - 当前自动创建待处理记录
   - 建议增加人工审核环节（金额>500元）
   - **影响**: 风控不足，可能被滥用

### 中优先级
1. **手续费配置**:
   - 当前 fee_amount 固定为 0
   - 建议支持配置手续费率（如 0.6%）
   - 存储在系统配置表中

2. **提现限额**:
   - 每日提现次数限制
   - 单笔最大提现金额
   - 月度提现总额限制

3. **收入结算状态**:
   - sicau_courier_income.settle_status 字段未使用
   - 建议提现时更新收入记录为已结算
   - 便于财务对账

### 低优先级
1. **提现到账通知**:
   - 微信模板消息通知用户
   - 短信通知（金额>100元）

2. **收入统计优化**:
   - 按日/周/月分组统计
   - 收入趋势图数据

3. **导出功能**:
   - 导出收入明细（Excel）
   - 导出提现记录（PDF）

---

## 文件清单

### 新增文件
- `/workspaces/litemall-campus/litemall-db/sql/sicau_courier_withdraw.sql` (20 行)
- `/workspaces/litemall-campus/litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauCourierWithdrawService.java` (101 行)
- `/workspaces/litemall-campus/litemall-db/src/main/java/org/linlinjava/litemall/db/domain/SicauCourierWithdraw.java` (MyBatis 生成)
- `/workspaces/litemall-campus/litemall-db/src/main/java/org/linlinjava/litemall/db/domain/SicauCourierWithdrawExample.java` (MyBatis 生成)
- `/workspaces/litemall-campus/litemall-db/src/main/java/org/linlinjava/litemall/db/dao/SicauCourierWithdrawMapper.java` (MyBatis 生成)
- `/workspaces/litemall-campus/litemall-db/src/main/resources/org/linlinjava/litemall/db/dao/SicauCourierWithdrawMapper.xml` (MyBatis 生成)

### 修改文件
- `/workspaces/litemall-campus/litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauCourierService.java`
  - 添加 withdrawService 依赖注入
  - 添加 getIncomeStats() 方法 (68 行)
  - 添加 withdraw() 方法 (36 行)

- `/workspaces/litemall-campus/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxCourierController.java`
  - 添加 GET /income 端点 (18 行)
  - 添加 POST /withdraw 端点 (54 行)

- `/workspaces/litemall-campus/litemall-db/mybatis-generator/generatorConfig.xml`
  - 添加 `<table tableName="sicau_courier_withdraw">` 配置

---

## 编译验证

**编译状态**: ✅ BUILD SUCCESS (15.124s)

```bash
mvn clean compile -T1C -DskipTests
```

**编译结果**:
- litemall: SUCCESS (0.435s)
- litemall-db: SUCCESS (10.528s) ← +4 文件（提现表）
- litemall-core: SUCCESS (1.288s)
- litemall-wx-api: SUCCESS (1.590s) ← +2 API 端点
- litemall-admin-api: SUCCESS (1.685s)
- litemall-all: SUCCESS (0.581s)
- litemall-all-war: SUCCESS (0.572s)

**源文件统计**:
- litemall-db: 207 源文件（+4 提现表相关）
- litemall-wx-api: 54 源文件（无变化，修改现有文件）

---

## API 测试示例

### 测试 1: 查询收入统计

```bash
curl -X GET http://localhost:8080/wx/courier/income \
  -H "X-Litemall-Token: <user_token>"
```

**成功响应**:
```json
{
  "errno": 0,
  "data": {
    "totalIncome": 120.50,
    "totalOrders": 25,
    "totalWithdrawn": 20.00,
    "availableBalance": 100.50,
    "recentIncomes": [...],
    "recentWithdraws": [...]
  }
}
```

### 测试 2: 申请提现

```bash
curl -X POST http://localhost:8080/wx/courier/withdraw \
  -H "Content-Type: application/json" \
  -H "X-Litemall-Token: <user_token>" \
  -d '{"amount": 50.00}'
```

**成功响应**:
```json
{
  "errno": 0,
  "data": {
    "withdrawSn": "WD20251028104523001",
    "message": "提现申请已提交，1-3个工作日到账"
  }
}
```

---

## 下一步

**Epic 4 已完成** ✅

所有 Story 都已实现：
- ✅ Story 4.1: 申请成为快递员
- ✅ Story 4.2: 查看待配送订单
- ✅ Story 4.3: 接单与配送
- ✅ Story 4.4: 配送超时处理
- ✅ Story 4.5: 收入统计 ← **刚完成**

**建议后续工作**:
1. 集成微信企业付款 API（生产必需）
2. 完善通知推送（4处 TODO）
3. 编写单元测试
4. 添加提现审核功能
5. 优化收入统计报表

---

**文档版本**: 1.0  
**最后更新**: 2025-10-28 08:41 UTC  
**实现者**: Developer Agent  
**审核状态**: 待审核
