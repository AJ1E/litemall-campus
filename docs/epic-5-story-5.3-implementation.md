# Story 5.3 实现文档：拍卖结算

**Story ID**: 5.3  
**Story 标题**: 拍卖结算  
**状态**: ✅ 已完成  
**完成时间**: 2025-10-28 10:12 UTC  
**依赖**: Story 5.1（发起拍卖）, Story 5.2（参与竞拍）

---

## 功能概述

实现拍卖自动结算功能，包括：

1. **定时扫描**: 每分钟扫描已到期但未结算的拍卖
2. **成交处理**: 有人出价 → 更新状态为"已成交"（status=3）
3. **流拍处理**: 无人出价 → 更新状态为"已流拍"（status=4）
4. **手动结算**: 提供管理接口手动触发结算（测试用）
5. **结算查询**: 查询已结束的拍卖列表

---

## 核心业务规则

### 1. 结算触发条件

```
当前时间 > 拍卖结束时间 AND 拍卖状态 = 1（进行中）
```

**示例**:
- 拍卖结束时间: 2025-10-28 10:00:00
- 当前时间: 2025-10-28 10:01:00
- 拍卖状态: 1（进行中）
- **触发**: ✅ 自动结算

### 2. 成交条件

```
highest_bidder_id IS NOT NULL AND total_bids > 0
```

**处理流程**:
1. 更新拍卖状态: `status = 3`（已成交）
2. 记录最终成交价: `current_price`
3. 记录成交者: `highest_bidder_id`
4. 更新时间: `update_time = now()`

**未来扩展**:
- 创建订单（需要订单服务）
- 支付尾款（成交价 - 保证金）
- 推送成交通知

### 3. 流拍条件

```
highest_bidder_id IS NULL OR total_bids = 0
```

**处理流程**:
1. 更新拍卖状态: `status = 4`（已流拍）
2. 更新时间: `update_time = now()`

**未来扩展**:
- 退还保证金（需要支付退款接口）
- 商品下架（需要商品服务）
- 推送流拍通知

### 4. 定时任务配置

**Cron 表达式**: `0 * * * * ?`

**含义**: 每分钟的第 0 秒执行

**执行时间示例**:
- 10:00:00
- 10:01:00
- 10:02:00
- ...

---

## 定时任务实现

### AuctionSettleTask.java

**位置**: `/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/AuctionSettleTask.java`

**核心方法**:

#### 1. settleExpiredAuctions() - 定时扫描

```java
@Scheduled(cron = "0 * * * * ?")
public void settleExpiredAuctions() {
    LocalDateTime now = LocalDateTime.now();
    logger.info("开始扫描已到期拍卖，当前时间: " + now);
    
    // 查询已到期但未结算的拍卖
    List<SicauAuction> expiredAuctions = auctionService.queryExpiredAuctions();
    
    if (expiredAuctions.isEmpty()) {
        logger.info("无待结算拍卖");
        return;
    }
    
    logger.info("发现 " + expiredAuctions.size() + " 个待结算拍卖");
    
    // 逐个结算
    int successCount = 0;
    int failCount = 0;
    
    for (SicauAuction auction : expiredAuctions) {
        try {
            settleAuction(auction);
            successCount++;
        } catch (Exception e) {
            failCount++;
            logger.error("拍卖 " + auction.getId() + " 结算失败", e);
        }
    }
    
    logger.info("拍卖结算完成: 成功 " + successCount + " 个, 失败 " + failCount + " 个");
}
```

#### 2. settleAuction() - 结算单个拍卖

```java
@Transactional
public void settleAuction(SicauAuction auction) {
    // 检查是否有人出价
    if (auction.getHighestBidderId() == null || auction.getTotalBids() == 0) {
        // 流拍
        handleUnsold(auction);
    } else {
        // 成交
        handleSold(auction);
    }
}
```

#### 3. handleSold() - 成交处理

```java
private void handleSold(SicauAuction auction) {
    logger.info("拍卖 " + auction.getId() + " 成交: 最高出价者 " + 
               auction.getHighestBidderId() + ", 成交价 " + auction.getCurrentPrice());
    
    // 更新拍卖状态
    auction.setStatus((byte) 3); // 已成交
    auction.setUpdateTime(LocalDateTime.now());
    auctionService.updateById(auction);
    
    logger.info("拍卖 " + auction.getId() + " 成交处理完成");
}
```

#### 4. handleUnsold() - 流拍处理

```java
private void handleUnsold(SicauAuction auction) {
    logger.info("拍卖 " + auction.getId() + " 流拍: 无人出价");
    
    // 更新拍卖状态
    auction.setStatus((byte) 4); // 已流拍
    auction.setUpdateTime(LocalDateTime.now());
    auctionService.updateById(auction);
    
    logger.info("拍卖 " + auction.getId() + " 流拍处理完成");
}
```

---

## 服务层实现

### SicauAuctionService - 新增方法

#### 1. queryExpiredAuctions() - 查询已到期拍卖

```java
public List<SicauAuction> queryExpiredAuctions() {
    LocalDateTime now = LocalDateTime.now();
    SicauAuctionExample example = new SicauAuctionExample();
    example.or()
        .andStatusEqualTo((byte) 1) // 状态=进行中
        .andDeletedEqualTo(false);
    
    List<SicauAuction> ongoingAuctions = auctionMapper.selectByExample(example);
    
    // 过滤出已过期的拍卖
    List<SicauAuction> expiredAuctions = new java.util.ArrayList<>();
    for (SicauAuction auction : ongoingAuctions) {
        if (auction.getEndTime() != null && now.isAfter(auction.getEndTime())) {
            expiredAuctions.add(auction);
        }
    }
    
    return expiredAuctions;
}
```

#### 2. queryByStatus() - 根据状态查询

```java
public List<SicauAuction> queryByStatus(Byte status) {
    SicauAuctionExample example = new SicauAuctionExample();
    example.or().andStatusEqualTo(status).andDeletedEqualTo(false);
    example.setOrderByClause("update_time DESC");
    return auctionMapper.selectByExample(example);
}
```

#### 3. queryFinishedAuctions() - 查询已结束拍卖

```java
public List<SicauAuction> queryFinishedAuctions() {
    SicauAuctionExample example = new SicauAuctionExample();
    example.or().andStatusEqualTo((byte) 3).andDeletedEqualTo(false);
    example.or().andStatusEqualTo((byte) 4).andDeletedEqualTo(false);
    example.setOrderByClause("update_time DESC");
    return auctionMapper.selectByExample(example);
}
```

---

## API 端点实现

### 1. POST /wx/auction/settleNow - 手动结算（测试接口）

**请求参数**:
- `id`: 拍卖ID

**请求示例**:
```bash
curl -X POST "http://localhost:8080/wx/auction/settleNow?id=1" \
  -H "X-Litemall-Token: xxx"
```

**成功响应**:
```json
{
  "errno": 0,
  "data": {
    "auctionId": 1,
    "status": 3,
    "statusText": "已成交"
  }
}
```

**失败响应**:
```json
{
  "errno": 606,
  "errmsg": "拍卖未进行或已结算"
}
```

**错误码**:
- 606: 拍卖未进行或已结算
- 607: 结算服务未启动
- 608: 结算失败

**注意**: 此接口仅用于测试，生产环境应删除或限制权限

### 2. GET /wx/auction/finished - 查询已结束拍卖

**请求参数**:
- `status` (可选): 3=已成交, 4=已流拍，不传则返回所有已结束拍卖

**请求示例**:
```bash
# 查询所有已结束拍卖
curl "http://localhost:8080/wx/auction/finished"

# 查询已成交拍卖
curl "http://localhost:8080/wx/auction/finished?status=3"

# 查询已流拍拍卖
curl "http://localhost:8080/wx/auction/finished?status=4"
```

**响应示例**:
```json
{
  "errno": 0,
  "data": [
    {
      "id": 1,
      "goodsId": 123,
      "sellerId": 1,
      "startPrice": 50.00,
      "currentPrice": 60.00,
      "increment": 5.00,
      "status": 3,
      "highestBidderId": 5,
      "totalBids": 3,
      "endTime": "2025-10-28 10:00:00",
      "updateTime": "2025-10-28 10:01:00"
    },
    {
      "id": 2,
      "goodsId": 124,
      "sellerId": 2,
      "startPrice": 30.00,
      "currentPrice": 30.00,
      "increment": 3.00,
      "status": 4,
      "highestBidderId": null,
      "totalBids": 0,
      "endTime": "2025-10-27 18:00:00",
      "updateTime": "2025-10-27 18:01:00"
    }
  ]
}
```

---

## 测试场景

### 场景 1: 成交结算

**前置条件**:
- 拍卖 ID=1
- 状态=1（进行中）
- 结束时间: 2025-10-28 10:00:00
- 当前时间: 2025-10-28 10:01:00
- 最高出价者: userId=5
- 成交价: 60.00 元

**执行**: 定时任务自动触发或手动调用

**预期结果**:
- ✅ 拍卖状态更新为 3（已成交）
- ✅ update_time 更新为当前时间
- ✅ 日志记录: "拍卖 1 成交: 最高出价者 5, 成交价 60.00"

### 场景 2: 流拍结算

**前置条件**:
- 拍卖 ID=2
- 状态=1（进行中）
- 结束时间: 2025-10-28 10:00:00
- 当前时间: 2025-10-28 10:01:00
- 最高出价者: null
- 出价次数: 0

**执行**: 定时任务自动触发

**预期结果**:
- ✅ 拍卖状态更新为 4（已流拍）
- ✅ update_time 更新为当前时间
- ✅ 日志记录: "拍卖 2 流拍: 无人出价"

### 场景 3: 批量结算

**前置条件**:
- 拍卖 ID=1, 2, 3 都已到期
- ID=1: 有人出价
- ID=2, 3: 无人出价

**执行**: 定时任务执行

**预期结果**:
- ✅ 日志: "发现 3 个待结算拍卖"
- ✅ ID=1 状态更新为 3（已成交）
- ✅ ID=2, 3 状态更新为 4（已流拍）
- ✅ 日志: "拍卖结算完成: 成功 3 个, 失败 0 个"

### 场景 4: 手动结算

**前置条件**:
- 拍卖 ID=1
- 状态=1（进行中）
- 已到期

**执行**:
```bash
curl -X POST "http://localhost:8080/wx/auction/settleNow?id=1" \
  -H "X-Litemall-Token: xxx"
```

**预期结果**:
- ✅ 返回 errno=0, status=3, statusText="已成交"
- ✅ 数据库状态更新

### 场景 5: 重复结算防护

**前置条件**:
- 拍卖 ID=1
- 状态=3（已成交）

**执行**: 再次调用手动结算

**预期结果**:
- ❌ 返回 errno=606, errmsg="拍卖未进行或已结算"
- ❌ 数据库无变化

---

## 定时任务日志示例

```log
2025-10-28 10:00:00 INFO  AuctionSettleTask - 开始扫描已到期拍卖，当前时间: 2025-10-28T10:00:00
2025-10-28 10:00:00 INFO  AuctionSettleTask - 发现 2 个待结算拍卖
2025-10-28 10:00:00 INFO  AuctionSettleTask - 拍卖 1 成交: 最高出价者 5, 成交价 60.00
2025-10-28 10:00:00 INFO  AuctionSettleTask - 拍卖 1 成交处理完成
2025-10-28 10:00:00 INFO  AuctionSettleTask - 拍卖 2 流拍: 无人出价
2025-10-28 10:00:00 INFO  AuctionSettleTask - 拍卖 2 流拍处理完成
2025-10-28 10:00:00 INFO  AuctionSettleTask - 拍卖结算完成: 成功 2 个, 失败 0 个
```

---

## 文件清单

### 新增文件

1. **定时任务**:
   - `/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/AuctionSettleTask.java` (148 行)

### 修改文件

1. `/litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauAuctionService.java`
   - 新增 `queryExpiredAuctions()` 方法（19 行）
   - 新增 `queryByStatus()` 方法（6 行）
   - 新增 `queryFinishedAuctions()` 方法（7 行）

2. `/litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxAuctionController.java`
   - 新增 `settleNow()` 接口（POST /wx/auction/settleNow）
   - 新增 `getFinishedAuctions()` 接口（GET /wx/auction/finished）

---

## 编译验证

**编译状态**: ✅ BUILD SUCCESS (15.757s)

```bash
mvn clean compile -T1C -DskipTests
```

**编译结果**:
- litemall: SUCCESS (0.292s)
- litemall-db: SUCCESS (11.355s) ← +3 查询方法
- litemall-core: SUCCESS (1.244s)
- litemall-wx-api: SUCCESS (1.706s) ← +1 定时任务 +2 API 接口
- litemall-admin-api: SUCCESS (1.590s)
- litemall-all: SUCCESS (0.693s)
- litemall-all-war: SUCCESS (0.673s)

---

## 技术债务

### 高优先级

1. **订单创建集成**:
   - 当前仅更新状态，未创建订单
   - 需要集成订单服务
   - 成交价需要拆分为保证金+尾款
   - **影响**: 买家无法支付完成交易

2. **保证金退还**:
   - 流拍时需退还保证金
   - 需要集成微信支付退款接口
   - **影响**: 卖家无法拿回保证金

### 中优先级

1. **结算通知**:
   - 成交时通知买卖双方
   - 流拍时通知卖家
   - 建议使用短信+站内信
   - **影响**: 用户无感知，需主动查询

2. **商品下架**:
   - 流拍时自动下架商品
   - 成交时标记商品已售
   - 需要商品服务支持
   - **影响**: 商品可能被重复购买

3. **定时任务监控**:
   - 任务执行失败时报警
   - 记录任务执行历史
   - 统计结算成功率
   - **影响**: 无法及时发现结算异常

### 低优先级

1. **结算重试机制**: 失败的结算自动重试
2. **结算日志持久化**: 保存到数据库，便于审计
3. **结算数据统计**: 成交率、流拍率趋势

---

## 下一步

**当前状态**: Story 5.3 已完成，拍卖结算功能可用

**后续 Story**:
1. **Story 5.4: 拍卖数据统计** - 成交率、溢价率、热门品类

**建议优先完成**:
1. 实现 Story 5.4（完成 Epic 5）
2. 集成订单创建功能
3. 集成保证金退款功能
4. 实现结算通知功能

---

**文档版本**: 1.0  
**最后更新**: 2025-10-28 10:12 UTC  
**实现者**: GitHub Copilot  
**审核状态**: 待审核
