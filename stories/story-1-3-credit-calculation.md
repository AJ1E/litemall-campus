# 用户故事 1.3: 信用积分计算

**Story ID**: 1.3  
**Epic**: Epic 1 - 用户认证与信用体系  
**优先级**: P0  
**预估工时**: 12 小时  
**创建日期**: 2025-10-27  
**创建者**: bmm-sm (Sprint Master)

---

## 📖 用户故事

**作为** 系统  
**我想要** 根据用户行为自动计算信用积分  
**以便** 激励良好交易行为并惩罚违规操作

---

## ✅ 验收标准

### 功能性要求
- [ ] 完成交易自动 +10 分（买家确认收货后触发）
- [ ] 收到好评（5星）+5 分，差评（1-2星）-5 分
- [ ] 取消订单 -5 分（仅限付款后 5 分钟后取消）
- [ ] 商品被举报下架 -50 分（管理员审核确认后）
- [ ] 完成捐赠 +20 分（捐赠物品送达站点后）
- [ ] 准时配送（快递员）+2 分/单
- [ ] 配送超时 -10 分（超过时效 2 小时）

### 数据要求
- [ ] 积分变动必须记录到 `sicau_credit_log` 表（包含变动原因、关联订单ID、操作时间）
- [ ] `litemall_user.credit_score` 字段实时更新
- [ ] 积分不能低于 0 分，不能高于 1000 分

### 通知要求
- [ ] 积分增加时发送微信服务通知："恭喜您获得 +10 积分，当前总积分 150"
- [ ] 积分扣除时发送微信服务通知："您的积分被扣除 -5 分，原因：取消订单"

### 性能要求
- [ ] 单次积分计算响应时间 < 100ms
- [ ] 支持并发计算（使用乐观锁防止并发冲突）

---

## 📊 积分规则表

| 行为 | 积分变动 | 触发时机 | 关联实体 |
|-----|---------|---------|---------|
| 完成交易 | +10 | 买家确认收货后 | litemall_order |
| 好评（5 星）| +5 | 互评提交后 | litemall_comment |
| 中评（3-4 星）| 0 | 互评提交后 | litemall_comment |
| 差评（1-2 星）| -5 | 互评提交后 | litemall_comment |
| 取消订单 | -5 | 付款后 5 分钟后取消 | litemall_order |
| 违规商品下架 | -50 | 管理员审核下架 | litemall_goods |
| 完成捐赠 | +20 | 捐赠物品送达站点 | sicau_donation |
| 准时配送（快递员）| +2 | 每单配送完成 | sicau_delivery_order |
| 配送超时 | -10 | 超过时效 2 小时 | sicau_delivery_order |

---

## 🗄️ 数据库设计

### 1. 用户表扩展字段（已在 Epic 1 Context 中定义）

```sql
ALTER TABLE litemall_user ADD COLUMN credit_score INT DEFAULT 100 COMMENT '信用积分';
```

### 2. 新增积分日志表

```sql
CREATE TABLE `sicau_credit_log` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `score_change` INT NOT NULL COMMENT '积分变动（正数为增加，负数为扣除）',
  `reason` VARCHAR(100) NOT NULL COMMENT '变动原因（完成交易/好评/差评/取消订单等）',
  `related_type` VARCHAR(50) COMMENT '关联实体类型（order/comment/goods/donation）',
  `related_id` INT COMMENT '关联实体 ID',
  `before_score` INT NOT NULL COMMENT '变动前积分',
  `after_score` INT NOT NULL COMMENT '变动后积分',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用积分日志表';
```

---

## 🔌 API 接口设计

### 1. 积分变动接口（内部调用）

**接口**: `POST /wx/user/updateCredit`  
**权限**: 需要 JWT Token（系统内部调用）  

**请求体**:
```json
{
  "userId": 123,
  "scoreChange": 10,
  "reason": "完成交易",
  "relatedType": "order",
  "relatedId": 456
}
```

**响应**:
```json
{
  "errno": 0,
  "errmsg": "积分更新成功",
  "data": {
    "beforeScore": 100,
    "afterScore": 110,
    "currentLevel": "新手"
  }
}
```

**错误码**:
- `601`: 用户不存在
- `602`: 积分变动超出范围（< 0 或 > 1000）
- `603`: 并发更新冲突

---

### 2. 查询积分历史接口

**接口**: `GET /wx/user/creditHistory`  
**权限**: 需要 JWT Token  

**请求参数**:
```
page=1&limit=20
```

**响应**:
```json
{
  "errno": 0,
  "data": {
    "total": 50,
    "items": [
      {
        "id": 123,
        "scoreChange": 10,
        "reason": "完成交易",
        "beforeScore": 100,
        "afterScore": 110,
        "createTime": "2025-10-27 14:30:00"
      },
      {
        "id": 122,
        "scoreChange": -5,
        "reason": "取消订单",
        "beforeScore": 105,
        "afterScore": 100,
        "createTime": "2025-10-26 10:15:00"
      }
    ]
  }
}
```

---

## 🎯 业务场景示例

### 场景 1: 买家确认收货，卖家获得积分

```
1. 买家点击"确认收货"
   ↓
2. 订单状态更新为 "已完成"（order_status = 401）
   ↓
3. 系统调用 updateCredit()
   - userId = 卖家 ID
   - scoreChange = +10
   - reason = "完成交易"
   - relatedType = "order"
   - relatedId = 订单 ID
   ↓
4. 更新 litemall_user.credit_score（100 → 110）
   ↓
5. 插入 sicau_credit_log 记录
   ↓
6. 发送微信服务通知给卖家
   ↓
7. 检查是否升级（100 → 110 仍为"新手"，无需操作）
```

---

### 场景 2: 用户收到差评，扣除积分

```
1. 买家提交差评（1 星）
   ↓
2. 系统调用 updateCredit()
   - userId = 卖家 ID
   - scoreChange = -5
   - reason = "收到差评"
   - relatedType = "comment"
   - relatedId = 评论 ID
   ↓
3. 更新 litemall_user.credit_score（110 → 105）
   ↓
4. 插入 sicau_credit_log 记录
   ↓
5. 发送微信服务通知给卖家："您的积分被扣除 -5 分，原因：收到差评"
```

---

### 场景 3: 快递员准时配送，获得积分

```
1. 快递员点击"完成配送"
   ↓
2. 检查配送时效：承诺 2 小时内，实际 1.5 小时
   ↓
3. 判断：准时配送
   ↓
4. 系统调用 updateCredit()
   - userId = 快递员用户 ID
   - scoreChange = +2
   - reason = "准时配送"
   - relatedType = "delivery_order"
   - relatedId = 配送订单 ID
   ↓
5. 更新积分并记录日志
```

---

## 📦 依赖关系

### 前置依赖
- Story 1.1: 微信一键登录（需要 userId）
- Story 1.2: 学号实名认证（完成认证后才能参与交易）

### 影响范围
- Story 1.4: 信用等级展示（依赖本故事的积分计算结果）
- Epic 2: 商品发布与管理（发布违规商品会扣分）
- Epic 3: 交易流程与支付（完成交易会加分）
- Epic 6: 学生快递配送（配送表现影响积分）
- Epic 7: 捐赠与公益（完成捐赠会加分）

---

## 🧪 测试用例

### 测试用例 1: 完成交易加分
**前置条件**: 用户当前积分 100 分  
**操作步骤**:
1. 模拟买家确认收货
2. 调用积分更新接口

**预期结果**:
- 积分变为 110 分
- sicau_credit_log 表新增一条记录（scoreChange = +10）
- 用户收到微信通知

---

### 测试用例 2: 积分不能低于 0
**前置条件**: 用户当前积分 3 分  
**操作步骤**:
1. 模拟用户取消订单（应扣除 5 分）
2. 调用积分更新接口

**预期结果**:
- 积分变为 0 分（而非 -2 分）
- 日志记录 scoreChange = -3（实际扣除量）

---

### 测试用例 3: 积分不能超过 1000
**前置条件**: 用户当前积分 995 分  
**操作步骤**:
1. 模拟用户完成交易（应增加 10 分）
2. 调用积分更新接口

**预期结果**:
- 积分变为 1000 分（而非 1005 分）
- 日志记录 scoreChange = +5（实际增加量）

---

### 测试用例 4: 并发更新冲突
**前置条件**: 用户当前积分 100 分  
**操作步骤**:
1. 同时触发两次积分更新（+10 和 +5）
2. 使用乐观锁机制

**预期结果**:
- 两次更新都成功
- 最终积分为 115 分
- sicau_credit_log 表有 2 条记录

---

## 🔐 安全要求

- [ ] 积分更新接口必须验证请求来源（内部服务调用，不对外暴露）
- [ ] 防止恶意刷分：同一订单/评论只能触发一次积分变动
- [ ] 使用数据库事务保证积分更新和日志插入的原子性
- [ ] 乐观锁机制防止并发冲突（基于 version 字段）

---

## 📈 性能指标

- 单次积分计算响应时间: < 100ms
- 支持并发量: 100 TPS（事务/秒）
- 日志表查询性能: 通过 user_id + create_time 索引，响应时间 < 50ms

---

## 📝 Definition of Done (DoD)

- [ ] 所有验收标准通过
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试通过（模拟完整交易流程）
- [ ] 代码 Review 通过
- [ ] API 文档已更新
- [ ] 性能测试通过（100 TPS 无错误）
- [ ] 微信服务通知模板已配置并测试

---

**状态**: 待开发  
**负责人**: 待分配  
**开始日期**: 待定  
**完成日期**: 待定
