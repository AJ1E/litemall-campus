# Epic 4 开发进度跟踪

**Epic**: Epic 4 - 学生快递员配送系统  
**当前状态**: in-progress (60% 完成)  
**开始时间**: 2025-10-28 06:34 UTC  
**开发者**: bmm-dev (Developer Agent Amelia)

---

## 📊 Stories 完成情况

| Story ID | 标题 | 状态 | 完成度 | 备注 |
|----------|------|------|--------|------|
| 4.1 | 申请成为快递员 | ✅ review | 100% | 已实现，待审查 |
| 4.2 | 查看待配送订单 | 🚧 in-dev | 0% | **进行中** |
| 4.3 | 接单与配送 | ⏳ ready-for-dev | 0% | 待开始 |
| 4.4 | 配送超时处理 | ⏳ ready-for-dev | 0% | 待开始 |
| 4.5 | 收入统计 | ⏳ ready-for-dev | 0% | 待开始 |

**总体完成度**: 20% (1/5 stories)

---

## ✅ Story 4.1: 申请成为快递员 (已完成)

### 实现清单
- [x] 数据库表: sicau_courier
- [x] Domain/Mapper: SicauCourier, SicauCourierMapper (MyBatis Generator 生成)
- [x] Service: SicauCourierService.apply()
- [x] Controller: WxCourierController.apply()
- [x] 信用等级验证 (≥70分)
- [x] 学号认证验证
- [x] 防重复申请逻辑
- [x] 单元测试: SicauCourierServiceTest

### API
- POST /wx/courier/apply - 申请成为快递员 ✅

### 遗留问题
- 无

---

## 🚧 Story 4.2: 查看待配送订单 (进行中)

### 需求分析
快递员可查看同校区待配送订单列表，系统自动计算配送距离和配送费。

### 实现计划
1. **数据库层**:
   - [ ] 创建 sicau_building 表（校区楼栋坐标表）
   - [ ] 预置雅安校区楼栋坐标数据

2. **工具层**:
   - [ ] 实现 DistanceCalculator.calculateDistance() - 欧几里得距离计算
   - [ ] 实现 DistanceCalculator.calculateFee() - 配送费计算

3. **Service 层**:
   - [ ] SicauCourierService.queryPendingOrders() - 查询待配送订单
   - [ ] 解析订单地址中的楼栋信息
   - [ ] 计算快递员与订单的距离

4. **Controller 层**:
   - [ ] WxCourierController.getPendingOrders() - API 接口

5. **数据库查询**:
   - [ ] LitemallOrderMapper.selectPendingDeliveryOrders() - 自定义查询

### API 设计
- GET /wx/courier/pendingOrders - 查看待配送订单列表

### 预计工时
10小时

---

## ⏳ Story 4.3: 接单与配送 (待开发)

### 实现计划
1. **Service 层**:
   - [ ] SicauCourierService.acceptOrder() - 快递员接单
   - [ ] SicauCourierService.completeOrder() - 完成配送
   - [ ] 生成4位取件码
   - [ ] 验证取件码

2. **收入流水表**:
   - [ ] 创建 sicau_courier_income 表
   - [ ] SicauCourierIncomeService 基础CRUD

3. **订单状态流转**:
   - [ ] 接单: 201 待发货 → 301 待收货
   - [ ] 完成: 301 待收货 → 401 已完成

4. **Controller 层**:
   - [ ] POST /wx/courier/acceptOrder - 接单
   - [ ] POST /wx/courier/completeOrder - 完成配送

### 预计工时
12小时

---

## ⏳ Story 4.4: 配送超时处理 (待开发)

### 实现计划
1. **定时任务**:
   - [ ] CourierTimeoutTask.checkTimeoutDelivery() - 每10分钟扫描
   - [ ] 检测接单后2小时未完成的订单

2. **惩罚机制**:
   - [ ] 超时1次: 扣10分，警告通知
   - [ ] 超时3次: 取消快递员资格

3. **订单释放**:
   - [ ] 超时订单重新进入待配送列表
   - [ ] 清空 courier_id 字段

4. **通知集成**:
   - [ ] 超时警告通知
   - [ ] 资格取消通知

### 预计工时
6小时

---

## ⏳ Story 4.5: 收入统计 (待开发)

### 实现计划
1. **数据库表**:
   - [ ] 创建 sicau_courier_withdraw 表（提现记录）

2. **Service 层**:
   - [ ] SicauCourierService.getIncomeStats() - 收入统计
   - [ ] SicauCourierService.withdraw() - 申请提现
   - [ ] 微信企业付款集成

3. **Controller 层**:
   - [ ] GET /wx/courier/income - 收入统计
   - [ ] POST /wx/courier/withdraw - 申请提现

4. **统计数据**:
   - [ ] 累计收入
   - [ ] 可提现余额
   - [ ] 提现中金额
   - [ ] 近期收入明细

### 预计工时
4小时

---

## 📋 下一步行动

**当前优先级**: Story 4.2 查看待配送订单

### 步骤1: 创建楼栋坐标表
1. 创建 sicau_building.sql
2. 预置雅安校区楼栋坐标

### 步骤2: 实现距离计算工具
1. 创建 DistanceCalculator.java
2. 实现欧几里得距离算法
3. 实现配送费计算规则

### 步骤3: 实现订单查询
1. 编写自定义 SQL 查询
2. 实现 Service 层逻辑
3. 创建 API 接口

---

**更新时间**: 2025-10-28 06:34 UTC
**下次更新**: Story 4.2 完成后
