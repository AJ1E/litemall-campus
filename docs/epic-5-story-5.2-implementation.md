# Story 5.2 实现文档：参与竞拍

**Story ID**: 5.2  
**Story 标题**: 参与竞拍  
**状态**: ✅ 已完成  
**完成时间**: 2025-10-28 09:54 UTC  
**依赖**: Story 5.1（发起拍卖）

---

## 功能概述

实现买家参与拍卖竞价功能，包括：

1. **出价**: 对进行中的拍卖出价，遵守最低出价规则
2. **延时规则**: 最后 5 分钟内出价自动延长 5 分钟（最多 3 次）
3. **并发控制**: 使用内存锁防止并发出价冲突
4. **出价历史**: 记录所有出价，支持查询
5. **实时信息**: 查看当前最高价、剩余时间、最低出价金额

---

## 核心业务规则

### 1. 最低出价计算

```
最低出价 = 当前最高价 + 加价幅度
```

**示例**:
- 当前价 50.00 元，加价幅度 5.00 元 → 最低出价 55.00 元
- 当前价 60.00 元，加价幅度 2.00 元 → 最低出价 62.00 元

### 2. 延时规则（防狙击）

**触发条件**: 距离结束时间 < 5 分钟时有人出价

**延时行为**:
- 第 1 次出价 → 结束时间延长 5 分钟
- 第 2 次出价 → 再延长 5 分钟
- 第 3 次出价 → 再延长 5 分钟
- 第 4 次及以后 → 不再延长

**最大延长时间**: 5 × 3 = 15 分钟

**示例时间线**:
```
原定结束时间: 10:00:00
09:57:00 用户A出价 → 结束时间延长至 10:05:00 (延长1次)
10:02:00 用户B出价 → 结束时间延长至 10:10:00 (延长2次)
10:07:00 用户C出价 → 结束时间延长至 10:15:00 (延长3次)
10:12:00 用户D出价 → 不再延长，仍为 10:15:00
```

### 3. 并发控制

使用 `ConcurrentHashMap<Integer, Object>` 维护每个拍卖的锁对象：

```java
Object lock = auctionLocks.computeIfAbsent(auctionId, k -> new Object());
synchronized (lock) {
    // 执行出价逻辑
}
```

**为什么需要锁**:
- 防止两个用户同时出价导致数据不一致
- 确保"最低出价"校验的准确性
- 避免延时规则重复触发

**生产环境建议**: 使用 Redis 分布式锁（支持多实例部署）

---

## 服务层实现

### SicauAuctionService.placeBid()

**核心逻辑**:

```java
@Transactional
public boolean placeBid(Integer auctionId, Integer bidderId, BigDecimal bidPrice) {
    LocalDateTime now = LocalDateTime.now();
    
    // 1. 查询拍卖
    SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
    
    // 2. 校验状态（必须是进行中）
    if (auction.getStatus() != 1) {
        throw new RuntimeException("拍卖未开始或已结束");
    }
    
    // 3. 校验时间（未过期）
    if (now.isAfter(auction.getEndTime())) {
        throw new RuntimeException("拍卖已结束");
    }
    
    // 4. 校验出价金额（>= 当前价 + 加价幅度）
    BigDecimal minBidPrice = auction.getCurrentPrice().add(auction.getIncrement());
    if (bidPrice.compareTo(minBidPrice) < 0) {
        throw new RuntimeException("出价必须不低于 " + minBidPrice + " 元");
    }
    
    // 5. 校验卖家不能自己出价
    if (bidderId.equals(auction.getSellerId())) {
        throw new RuntimeException("卖家不能对自己的拍卖出价");
    }
    
    // 6. 记录出价
    SicauAuctionBid bid = new SicauAuctionBid();
    bid.setAuctionId(auctionId);
    bid.setBidderId(bidderId);
    bid.setBidPrice(bidPrice);
    bidMapper.insertSelective(bid);
    
    // 7. 更新拍卖信息
    auction.setCurrentPrice(bidPrice);
    auction.setHighestBidderId(bidderId);
    auction.setTotalBids(auction.getTotalBids() + 1);
    
    // 8. 检查延时规则
    long minutesToEnd = ChronoUnit.MINUTES.between(now, auction.getEndTime());
    if (minutesToEnd >= 0 && minutesToEnd < 5 && auction.getExtendCount() < 3) {
        auction.setEndTime(auction.getEndTime().plusMinutes(5));
        auction.setExtendCount(auction.getExtendCount() + 1);
    }
    
    auctionMapper.updateByPrimaryKeySelective(auction);
    
    return true;
}
```

**关键字段更新**:
- `current_price`: 更新为最新出价
- `highest_bidder_id`: 更新为当前出价者
- `total_bids`: 累加出价次数
- `end_time`: 触发延时规则时延长 5 分钟
- `extend_count`: 记录延长次数（0→1→2→3）

---

## API 端点实现

### 1. POST /wx/auction/bid - 出价

**请求体**:
```json
{
  "auctionId": 1,
  "bidPrice": 60.00
}
```

**成功响应**:
```json
{
  "errno": 0,
  "errmsg": "成功",
  "data": {
    "currentPrice": 60.00,
    "remainSeconds": 3600,
    "extended": false,
    "extendCount": 0
  }
}
```

**失败响应**:
```json
{
  "errno": 604,
  "errmsg": "出价必须不低于 55.00 元（当前价 50.00 + 加价幅度 5.00）"
}
```

**错误码**:
- 604: 出价失败（金额不足、拍卖已结束、卖家自己出价等）

**响应字段说明**:
- `currentPrice`: 出价后的当前价
- `remainSeconds`: 剩余秒数
- `extended`: 是否触发了延时（true/false）
- `extendCount`: 当前延长次数（0-3）

### 2. GET /wx/auction/detailWithBids - 拍卖详情（含出价历史）

**请求参数**:
- `id`: 拍卖ID

**响应示例**:
```json
{
  "errno": 0,
  "data": {
    "auction": {
      "id": 1,
      "goodsId": 123,
      "startPrice": 50.00,
      "currentPrice": 60.00,
      "increment": 5.00,
      "status": 1,
      "durationHours": 24,
      "startTime": "2025-10-27 10:00:00",
      "endTime": "2025-10-28 10:05:00",
      "extendCount": 1,
      "highestBidderId": 5,
      "totalBids": 3
    },
    "bids": [
      {
        "id": 3,
        "auctionId": 1,
        "bidderId": 5,
        "bidPrice": 60.00,
        "bidTime": "2025-10-27 15:30:00",
        "isAutoBid": false
      },
      {
        "id": 2,
        "auctionId": 1,
        "bidderId": 4,
        "bidPrice": 55.00,
        "bidTime": "2025-10-27 14:20:00",
        "isAutoBid": false
      }
    ],
    "remainSeconds": 3600,
    "minBidPrice": 65.00
  }
}
```

**字段说明**:
- `auction`: 拍卖基本信息
- `bids`: 最近 10 条出价记录（按时间倒序）
- `remainSeconds`: 剩余时间（秒），拍卖结束返回 null
- `minBidPrice`: 下一次最低出价金额

### 3. GET /wx/auction/myBids - 我的出价记录

**响应示例**:
```json
{
  "errno": 0,
  "data": [
    {
      "id": 5,
      "auctionId": 2,
      "bidderId": 3,
      "bidPrice": 70.00,
      "bidTime": "2025-10-27 16:00:00",
      "isAutoBid": false
    },
    {
      "id": 3,
      "auctionId": 1,
      "bidderId": 3,
      "bidPrice": 60.00,
      "bidTime": "2025-10-27 15:30:00",
      "isAutoBid": false
    }
  ]
}
```

---

## 测试场景

### 场景 1: 正常出价

**前置条件**:
- 拍卖 ID=1，状态=1（进行中）
- 当前价 50.00 元，加价幅度 5.00 元
- 用户 ID=3 已登录

**执行**:
```bash
curl -X POST http://localhost:8080/wx/auction/bid \
  -H "X-Litemall-Token: xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "auctionId": 1,
    "bidPrice": 55.00
  }'
```

**预期结果**:
- ✅ 返回 errno=0，currentPrice=55.00
- ✅ 数据库 `sicau_auction_bid` 新增记录
- ✅ 数据库 `sicau_auction` 更新：current_price=55.00, highest_bidder_id=3, total_bids=1

### 场景 2: 出价金额不足

**前置条件**: 当前价 50.00，加价幅度 5.00

**执行**:
```json
{
  "auctionId": 1,
  "bidPrice": 52.00
}
```

**预期结果**:
- ❌ 返回 errno=604, errmsg="出价必须不低于 55.00 元（当前价 50.00 + 加价幅度 5.00）"
- ❌ 数据库无变化

### 场景 3: 触发延时规则

**前置条件**:
- 拍卖原定结束时间: 2025-10-28 10:00:00
- 当前时间: 2025-10-28 09:57:00（距离结束 3 分钟）
- 延长次数: 0

**执行**:
```json
{
  "auctionId": 1,
  "bidPrice": 55.00
}
```

**预期结果**:
- ✅ 返回 errno=0, extended=true, extendCount=1
- ✅ 数据库 `end_time` 更新为 2025-10-28 10:05:00（延长 5 分钟）
- ✅ `extend_count` 从 0 更新为 1

### 场景 4: 延时次数达到上限

**前置条件**:
- 延长次数: 3
- 距离结束时间: 2 分钟

**执行**:
```json
{
  "auctionId": 1,
  "bidPrice": 60.00
}
```

**预期结果**:
- ✅ 出价成功，但 extended=false
- ✅ `end_time` 不变
- ✅ `extend_count` 仍为 3

### 场景 5: 卖家自己出价

**前置条件**:
- 拍卖 seller_id=5
- 当前用户 userId=5

**执行**:
```json
{
  "auctionId": 1,
  "bidPrice": 55.00
}
```

**预期结果**:
- ❌ 返回 errno=604, errmsg="卖家不能对自己的拍卖出价"

### 场景 6: 并发出价

**前置条件**:
- 当前价 50.00，加价幅度 5.00
- 用户A和用户B同时出价 55.00

**执行**:
```bash
# 用户A
curl -X POST /wx/auction/bid -d '{"auctionId":1, "bidPrice":55.00}' &

# 用户B（几乎同时）
curl -X POST /wx/auction/bid -d '{"auctionId":1, "bidPrice":55.00}' &
```

**预期结果**:
- ✅ 一个用户成功（errno=0）
- ❌ 另一个用户失败（errno=604，"出价必须不低于 60.00 元"）
- ✅ 数据库只有一条新出价记录
- ✅ current_price=55.00（不是 110.00）

---

## 文件清单

### 新增文件

1. **服务层**:
   - `/litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauAuctionBidService.java` (88 行)

### 修改文件

1. `/litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauAuctionService.java`
   - 新增 `placeBid()` 方法（87 行）
   - 新增 `getMinBidPrice()` 方法（7 行）
   - 新增 `SicauAuctionBidMapper` 依赖

2. `/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxAuctionController.java`
   - 新增 `placeBid()` 接口（POST /wx/auction/bid）
   - 新增 `getDetailWithBids()` 接口（GET /wx/auction/detailWithBids）
   - 新增 `getMyBids()` 接口（GET /wx/auction/myBids）
   - 新增并发锁 `auctionLocks`
   - 新增 `SicauAuctionBidService` 依赖

---

## 编译验证

**编译状态**: ✅ BUILD SUCCESS (16.244s)

```bash
mvn clean compile -T1C -DskipTests
```

**编译结果**:
- litemall: SUCCESS (0.288s)
- litemall-db: SUCCESS (11.811s) ← +1 服务类（SicauAuctionBidService）
- litemall-core: SUCCESS (1.185s)
- litemall-wx-api: SUCCESS (1.711s) ← +3 API 接口
- litemall-admin-api: SUCCESS (1.623s)
- litemall-all: SUCCESS (0.594s)
- litemall-all-war: SUCCESS (0.778s)

---

## 技术债务

### 高优先级

1. **分布式锁**:
   - 当前使用 JVM 内存锁（`ConcurrentHashMap`）
   - 仅适用于单实例部署
   - 生产环境需迁移到 Redis 分布式锁
   - **影响**: 多实例部署时并发控制失效

2. **WebSocket 实时推送**:
   - 当前无实时通知
   - 买家不知道被别人超越
   - 建议集成 WebSocket 推送实时更新
   - **影响**: 用户体验不佳

### 中优先级

1. **出价通知**:
   - 出价成功后通知卖家
   - 被超越时通知原最高出价者
   - 拍卖即将结束提醒（最后 10 分钟）
   - **影响**: 用户无感知，需主动刷新

2. **出价历史分页**:
   - 当前固定返回最近 10 条
   - 大量出价时需分页
   - **影响**: 无法查看全部历史

3. **自动出价**:
   - `sicau_auction_bid.is_auto_bid` 字段已预留
   - 可实现代理出价（设置最高价，系统自动跟价）
   - **影响**: 功能不完整

### 低优先级

1. **出价速率限制**: 防止恶意刷价
2. **出价撤回**: 用户出价后 5 秒内可撤回
3. **出价排行榜**: 显示当前拍卖的前 N 名出价者

---

## 下一步

**当前状态**: Story 5.2 已完成，出价功能可用

**后续 Story**:
1. **Story 5.3: 拍卖结算** - 定时任务、流拍处理、成交订单生成
2. **Story 5.4: 拍卖数据统计** - 成交率、溢价率、热门品类

**建议优先完成**:
1. 实现 Story 5.3（结算逻辑）
2. 集成 Redis 分布式锁
3. 实现 WebSocket 实时推送

---

**文档版本**: 1.0  
**最后更新**: 2025-10-28 09:54 UTC  
**实现者**: GitHub Copilot  
**审核状态**: 待审核
