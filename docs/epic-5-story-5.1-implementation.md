# Story 5.1 实现文档：发起拍卖

**Story ID**: 5.1  
**Story 标题**: 发起拍卖  
**状态**: ✅ 已完成  
**完成时间**: 2025-10-28 09:43 UTC  
**依赖**: Epic 3（交易流程）

---

## 功能概述

实现卖家发起拍卖功能，包括：

1. **创建拍卖**: 设置起拍价、加价幅度、拍卖时长
2. **保证金计算**: 自动计算 10% 起拍价作为保证金
3. **拍卖时长选择**: 支持 12/24/48 小时
4. **状态管理**: 创建后状态为"待支付保证金"
5. **查询功能**: 查看自己发起的拍卖、进行中的拍卖

---

## 数据库设计

### 1. sicau_auction (拍卖表)

```sql
CREATE TABLE `sicau_auction` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `goods_id` INT NOT NULL COMMENT '商品ID',
  `seller_id` INT NOT NULL COMMENT '卖家用户ID',
  `start_price` DECIMAL(10,2) NOT NULL COMMENT '起拍价（元）',
  `current_price` DECIMAL(10,2) NOT NULL COMMENT '当前最高价（元）',
  `increment` DECIMAL(10,2) DEFAULT 1.00 COMMENT '加价幅度（元）',
  `deposit` DECIMAL(10,2) NOT NULL COMMENT '保证金（元）',
  `deposit_status` TINYINT DEFAULT 0 COMMENT '保证金状态: 0-待支付, 1-已支付, 2-已退还',
  `status` TINYINT DEFAULT 0 COMMENT '拍卖状态: 0-待支付保证金, 1-进行中, 2-已结束, 3-已成交, 4-已流拍',
  `duration_hours` INT NOT NULL COMMENT '拍卖时长（小时）',
  `start_time` DATETIME COMMENT '开始时间（保证金支付后）',
  `end_time` DATETIME COMMENT '结束时间（动态更新）',
  `extend_count` INT DEFAULT 0 COMMENT '延长次数',
  `highest_bidder_id` INT COMMENT '当前最高出价者用户ID',
  `total_bids` INT DEFAULT 0 COMMENT '出价总次数',
  `order_id` INT COMMENT '成交后生成的订单ID',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BOOLEAN DEFAULT FALSE,
  INDEX `idx_goods_id` (`goods_id`),
  INDEX `idx_seller_id` (`seller_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**字段说明**:
- `deposit`: 保证金 = 起拍价 × 10%（四舍五入到分）
- `status`: 0=待支付保证金, 1=进行中, 2=已结束, 3=已成交, 4=已流拍
- `deposit_status`: 0=待支付, 1=已支付, 2=已退还
- `extend_count`: 延时次数（最后5分钟出价延长5分钟，最多3次）

### 2. sicau_auction_bid (出价记录表)

```sql
CREATE TABLE `sicau_auction_bid` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `auction_id` INT NOT NULL COMMENT '拍卖ID',
  `bidder_id` INT NOT NULL COMMENT '出价者用户ID',
  `bid_price` DECIMAL(10,2) NOT NULL COMMENT '出价（元）',
  `bid_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `is_auto_bid` BOOLEAN DEFAULT FALSE COMMENT '是否自动出价（预留）',
  INDEX `idx_auction_id` (`auction_id`),
  INDEX `idx_bidder_id` (`bidder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 服务层实现

### SicauAuctionService.java

**核心方法**:

#### 1. createAuction() - 创建拍卖

```java
@Transactional
public Integer createAuction(Integer sellerId, Integer goodsId, 
                              BigDecimal startPrice, BigDecimal increment, 
                              Integer durationHours) {
    // 1. 验证拍卖时长（只允许 12/24/48 小时）
    if (durationHours != 12 && durationHours != 24 && durationHours != 48) {
        throw new RuntimeException("拍卖时长只能是 12、24 或 48 小时");
    }
    
    // 2. 验证起拍价和加价幅度
    if (startPrice.compareTo(BigDecimal.ZERO) <= 0) {
        throw new RuntimeException("起拍价必须大于 0");
    }
    
    if (increment.compareTo(BigDecimal.ZERO) <= 0) {
        throw new RuntimeException("加价幅度必须大于 0");
    }
    
    // 3. 计算保证金（10% 起拍价，四舍五入到分）
    BigDecimal deposit = startPrice.multiply(new BigDecimal("0.1"))
                                  .setScale(2, BigDecimal.ROUND_HALF_UP);
    
    // 4. 创建拍卖记录
    SicauAuction auction = new SicauAuction();
    auction.setGoodsId(goodsId);
    auction.setSellerId(sellerId);
    auction.setStartPrice(startPrice);
    auction.setCurrentPrice(startPrice); // 初始最高价=起拍价
    auction.setIncrement(increment);
    auction.setDeposit(deposit);
    auction.setDepositStatus((byte) 0); // 待支付保证金
    auction.setStatus((byte) 0); // 待支付保证金
    auction.setDurationHours(durationHours);
    auction.setExtendCount(0);
    auction.setTotalBids(0);
    auction.setDeleted(false);
    
    auctionMapper.insertSelective(auction);
    
    return auction.getId();
}
```

#### 2. startAuctionAfterDepositPaid() - 支付保证金后启动拍卖

```java
@Transactional
public void startAuctionAfterDepositPaid(Integer auctionId) {
    SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
    
    if (auction == null || auction.getDepositStatus() != 0) {
        throw new RuntimeException("保证金状态异常");
    }
    
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime endTime = now.plusHours(auction.getDurationHours());
    
    // 更新拍卖状态
    auction.setDepositStatus((byte) 1); // 已支付保证金
    auction.setStatus((byte) 1); // 拍卖进行中
    auction.setStartTime(now);
    auction.setEndTime(endTime);
    auction.setUpdateTime(now);
    
    auctionMapper.updateByPrimaryKeySelective(auction);
}
```

#### 3. 查询方法

```java
// 根据ID查询
public SicauAuction findById(Integer id);

// 根据商品ID查询
public SicauAuction findByGoodsId(Integer goodsId);

// 查询卖家的拍卖列表
public List<SicauAuction> queryBySellerId(Integer sellerId);

// 查询进行中的拍卖列表
public List<SicauAuction> queryOngoingAuctions();
```

---

## API 端点实现

### WxAuctionController.java

#### 1. POST /wx/auction/create - 发起拍卖

**请求体**:
```json
{
  "goodsId": 123,
  "startPrice": 50.00,
  "increment": 5.00,
  "durationHours": 24
}
```

**响应示例**:
```json
{
  "errno": 0,
  "data": {
    "auctionId": 1,
    "deposit": 5.00,
    "message": "拍卖创建成功，请支付保证金 5.00 元"
  }
}
```

**错误码**:
- 601: 创建拍卖失败（参数错误、时长不合法等）

#### 2. GET /wx/auction/detail?id=1 - 查询拍卖详情

**响应示例**:
```json
{
  "errno": 0,
  "data": {
    "id": 1,
    "goodsId": 123,
    "sellerId": 1,
    "startPrice": 50.00,
    "currentPrice": 50.00,
    "increment": 5.00,
    "deposit": 5.00,
    "depositStatus": 0,
    "status": 0,
    "durationHours": 24,
    "startTime": null,
    "endTime": null,
    "extendCount": 0,
    "highestBidderId": null,
    "totalBids": 0,
    "addTime": "2025-10-28T09:30:00"
  }
}
```

#### 3. GET /wx/auction/myAuctions - 查询我发起的拍卖

返回当前用户创建的所有拍卖列表（数组）。

#### 4. GET /wx/auction/ongoing - 查询进行中的拍卖

返回所有状态为"进行中"的拍卖列表，按结束时间升序排列。

---

## 业务规则

### 1. 保证金计算

```
保证金 = 起拍价 × 10%（四舍五入到分）
```

**示例**:
- 起拍价 50.00 元 → 保证金 5.00 元
- 起拍价 23.45 元 → 保证金 2.35 元
- 起拍价 17.77 元 → 保证金 1.78 元

### 2. 拍卖时长限制

只允许以下三种时长：
- **12 小时**: 适合快速成交的商品
- **24 小时**: 标准拍卖时长
- **48 小时**: 高价值商品

### 3. 拍卖状态流转

```
0 (待支付保证金) → 1 (进行中) → 2 (已结束) → 3 (已成交) / 4 (已流拍)
                                ↓
                        保证金支付后自动启动
```

**状态说明**:
- **0 - 待支付保证金**: 拍卖创建后，等待卖家支付保证金
- **1 - 进行中**: 保证金支付后，拍卖正式开始
- **2 - 已结束**: 拍卖时间到期，等待结算
- **3 - 已成交**: 有人出价，拍卖成功
- **4 - 已流拍**: 无人出价，拍卖失败

### 4. 参数验证

- 起拍价必须 > 0
- 加价幅度必须 > 0
- 拍卖时长只能是 12/24/48 小时

---

## 测试场景

### 场景 1: 正常发起拍卖

**前置条件**: 用户已登录

**执行**:
```bash
curl -X POST http://localhost:8080/wx/auction/create \
  -H "X-Litemall-Token: xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "goodsId": 123,
    "startPrice": 100.00,
    "increment": 10.00,
    "durationHours": 24
  }'
```

**预期结果**:
- ✅ 返回 `auctionId=1`, `deposit=10.00`
- ✅ 数据库创建拍卖记录
- ✅ `status=0`, `deposit_status=0`
- ✅ `current_price=100.00`（等于起拍价）

### 场景 2: 不合法的拍卖时长

**执行**:
```json
{
  "goodsId": 123,
  "startPrice": 50.00,
  "increment": 5.00,
  "durationHours": 36
}
```

**预期结果**:
- ❌ 返回 errno=601, errmsg="拍卖时长只能是 12、24 或 48 小时"
- ❌ 数据库无新记录

### 场景 3: 起拍价为 0

**执行**:
```json
{
  "goodsId": 123,
  "startPrice": 0,
  "increment": 5.00,
  "durationHours": 24
}
```

**预期结果**:
- ❌ 返回 errno=601, errmsg="起拍价必须大于 0"

### 场景 4: 查询拍卖详情

**前置条件**: 已创建拍卖 ID=1

**执行**:
```bash
curl -X GET "http://localhost:8080/wx/auction/detail?id=1" \
  -H "X-Litemall-Token: xxx"
```

**预期结果**:
- ✅ 返回完整拍卖信息
- ✅ 包含所有字段（id, goodsId, sellerId, prices, status, times...）

---

## 文件清单

### 新增文件

1. **数据库**:
   - `/litemall-db/sql/sicau_auction.sql` (拍卖表)
   - `/litemall-db/sql/sicau_auction_tables_only.sql` (仅拍卖表，无ALTER语句)

2. **MyBatis 生成**（自动生成）:
   - `/litemall-db/src/main/java/org/linlinjava/litemall/db/domain/SicauAuction.java`
   - `/litemall-db/src/main/java/org/linlinjava/litemall/db/domain/SicauAuctionExample.java`
   - `/litemall-db/src/main/java/org/linlinjava/litemall/db/dao/SicauAuctionMapper.java`
   - `/litemall-db/src/main/resources/org/linlinjava/litemall/db/dao/SicauAuctionMapper.xml`
   - `/litemall-db/src/main/java/org/linlinjava/litemall/db/domain/SicauAuctionBid.java`
   - `/litemall-db/src/main/java/org/linlinjava/litemall/db/domain/SicauAuctionBidExample.java`
   - `/litemall-db/src/main/java/org/linlinjava/litemall/db/dao/SicauAuctionBidMapper.java`
   - `/litemall-db/src/main/resources/org/linlinjava/litemall/db/dao/SicauAuctionBidMapper.xml`

3. **服务层**:
   - `/litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauAuctionService.java` (175 行)

4. **API 层**:
   - `/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxAuctionController.java` (186 行)

### 修改文件

1. `/litemall-db/mybatis-generator/generatorConfig.xml`
   - 新增 `<table tableName="sicau_auction">`
   - 新增 `<table tableName="sicau_auction_bid">`

---

## 编译验证

**编译状态**: ✅ BUILD SUCCESS (16.401s)

```bash
mvn clean compile -T1C -DskipTests
```

**编译结果**:
- litemall: SUCCESS (0.535s)
- litemall-db: SUCCESS (11.556s) ← +8 拍卖相关文件
- litemall-core: SUCCESS (1.464s)
- litemall-wx-api: SUCCESS (1.501s) ← +1 Controller
- litemall-admin-api: SUCCESS (1.665s)
- litemall-all: SUCCESS (0.729s)
- litemall-all-war: SUCCESS (0.751s)

---

## 技术债务

### 高优先级

1. **保证金支付集成**:
   - 当前只创建拍卖记录，未集成支付
   - 需要集成微信支付 API
   - 支付成功后调用 `startAuctionAfterDepositPaid()`
   - **影响**: 无法真正启动拍卖

2. **商品状态关联**:
   - 需要更新商品表字段（`is_auction`, `auction_id`）
   - 但 litemall_goods 表不存在
   - 建议复用现有商品管理逻辑
   - **影响**: 商品可能被同时拍卖和直接购买

### 中优先级

1. **商品验证**:
   - 当前未验证商品是否存在
   - 未验证商品所有权
   - 未验证商品是否已在拍卖
   - **影响**: 可能发起无效拍卖

2. **保证金退还机制**:
   - 流拍时需退还保证金
   - 当前仅预留字段，未实现逻辑
   - **影响**: 用户体验不完整

3. **拍卖列表分页**:
   - `queryOngoingAuctions()` 未分页
   - 大量拍卖时性能问题
   - **影响**: 接口响应慢

### 低优先级

1. **拍卖图片展示**: 需关联商品图片
2. **拍卖搜索**: 按关键词、价格区间搜索
3. **拍卖统计**: 成交率、平均溢价率

---

## 下一步

**当前状态**: Story 5.1 已完成，拍卖创建功能可用

**后续 Story**:
1. **Story 5.2: 参与竞拍** - 出价、延时规则、并发控制
2. **Story 5.3: 拍卖结算** - 定时任务、自动结算、订单生成
3. **Story 5.4: 拍卖数据统计** - 成交率、溢价率、热门品类

**建议优先完成**:
1. 集成保证金支付功能
2. 实现 Story 5.2（出价功能）
3. 实现 Story 5.3（结算逻辑）

---

**文档版本**: 1.0  
**最后更新**: 2025-10-28 09:43 UTC  
**实现者**: GitHub Copilot  
**审核状态**: 待审核
