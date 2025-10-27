# Epic 5 技术上下文：限时拍卖模块

**Epic ID**: 5  
**Epic 标题**: 限时拍卖模块  
**优先级**: P2  
**预估工时**: 32 小时  
**依赖关系**: Epic 3（交易流程与支付）  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)

---

## 1. Epic 概述

本 Epic 实现限时拍卖功能，允许卖家将闲置物品设置起拍价和拍卖时长，买家竞价购买。核心功能包括：

- **发起拍卖**: 卖家设置起拍价、加价幅度、拍卖时长（12/24/48 小时）
- **保证金机制**: 卖家缴纳 10% 起拍价作为保证金，流拍退还
- **参与竞拍**: 买家出价，实时更新最高价
- **延时规则**: 最后 5 分钟内有出价，自动延长 5 分钟
- **自动结算**: 拍卖结束自动生成订单，通知最高出价者支付
- **数据统计**: 拍卖成功率、溢价率、热门品类分析

**业务价值**: 
- 为稀缺物品（绝版教材、演唱会门票）提供价格发现机制
- 增强平台交易趣味性
- 提升高价值商品成交率

---

## 2. 架构决策引用

基于 `architecture.md` 中的以下关键决策：

### ADR-009: 拍卖状态机设计
- **拍卖状态流转**: 
  ```
  待支付保证金(0) → 进行中(1) → 已结束(2) → 已成交(3) / 已流拍(4)
  ```
- **保证金规则**: 
  - 发起拍卖时冻结 10% 起拍价
  - 成交后保证金转为订单金额的一部分
  - 流拍后全额退还
- **延时规则**: 
  - 最后 5 分钟内有新出价 → 延长 5 分钟
  - 最多延长 3 次（防止恶意拖延）

### ADR-010: 拍卖结算机制
- **自动结算**: Cron 任务每分钟扫描已结束拍卖
- **成交处理**: 自动创建订单，锁定最高出价者 30 分钟支付
- **流拍处理**: 退还保证金，商品自动下架

### 技术栈
- **后端框架**: Spring Boot 2.7.x
- **定时任务**: Spring `@Scheduled`（每分钟扫描）
- **并发控制**: Redis 分布式锁（防止重复结算）
- **实时推送**: WebSocket（出价实时通知）

---

## 3. 数据库变更

### 3.1 新增表：sicau_auction (拍卖表)

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拍卖表';
```

### 3.2 新增表：sicau_auction_bid (出价记录表)

```sql
CREATE TABLE `sicau_auction_bid` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `auction_id` INT NOT NULL COMMENT '拍卖ID',
  `bidder_id` INT NOT NULL COMMENT '出价者用户ID',
  `bid_price` DECIMAL(10,2) NOT NULL COMMENT '出价（元）',
  `bid_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '出价时间',
  `is_auto_bid` BOOLEAN DEFAULT FALSE COMMENT '是否自动出价（预留）',
  INDEX `idx_auction_id` (`auction_id`),
  INDEX `idx_bidder_id` (`bidder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拍卖出价记录表';
```

### 3.3 复用 litemall_goods 表

**新增字段**:
```sql
ALTER TABLE `litemall_goods` 
ADD COLUMN `is_auction` BOOLEAN DEFAULT FALSE COMMENT '是否拍卖商品' AFTER `is_on_sale`,
ADD COLUMN `auction_id` INT COMMENT '关联拍卖ID' AFTER `is_auction`,
ADD INDEX `idx_auction_id` (`auction_id`);
```

---

## 4. 核心代码实现指导

### 4.1 拍卖服务

创建 `litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauAuctionService.java`：

```java
package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauAuctionBidMapper;
import org.linlinjava.litemall.db.dao.SicauAuctionMapper;
import org.linlinjava.litemall.db.domain.SicauAuction;
import org.linlinjava.litemall.db.domain.SicauAuctionBid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 拍卖服务
 */
@Service
public class SicauAuctionService {
    
    @Resource
    private SicauAuctionMapper auctionMapper;
    
    @Resource
    private SicauAuctionBidMapper bidMapper;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private WxPayService wxPayService;
    
    /**
     * 发起拍卖
     * @param goodsId 商品ID
     * @param startPrice 起拍价
     * @param increment 加价幅度
     * @param durationHours 拍卖时长（小时）
     * @return 拍卖ID
     */
    @Transactional
    public Integer createAuction(Integer sellerId, Integer goodsId, 
                                  BigDecimal startPrice, BigDecimal increment, 
                                  Integer durationHours) {
        // 1. 检查商品是否存在
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !goods.getUserId().equals(sellerId)) {
            throw new RuntimeException("商品不存在或无权限");
        }
        
        // 2. 计算保证金（10% 起拍价）
        BigDecimal deposit = startPrice.multiply(new BigDecimal("0.1"));
        
        // 3. 创建拍卖记录
        SicauAuction auction = new SicauAuction();
        auction.setGoodsId(goodsId);
        auction.setSellerId(sellerId);
        auction.setStartPrice(startPrice);
        auction.setCurrentPrice(startPrice);
        auction.setIncrement(increment);
        auction.setDeposit(deposit);
        auction.setDepositStatus((byte) 0); // 待支付保证金
        auction.setStatus((byte) 0);
        auction.setDurationHours(durationHours);
        auctionMapper.insertSelective(auction);
        
        // 4. 更新商品表
        goods.setIsAuction(true);
        goods.setAuctionId(auction.getId());
        goods.setIsOnSale(false); // 拍卖期间不可直接购买
        goodsService.update(goods);
        
        return auction.getId();
    }
    
    /**
     * 支付保证金（调用微信支付）
     */
    @Transactional
    public void payDeposit(Integer auctionId, String openid) throws Exception {
        SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
        
        // 调用微信支付
        WxPayMpOrderResult payResult = wxPayService.createPayOrder(
            "拍卖保证金-" + auctionId, 
            auction.getDeposit(), 
            openid
        );
        
        // 支付成功后在回调中更新状态
    }
    
    /**
     * 保证金支付回调
     */
    @Transactional
    public void handleDepositPayNotify(Integer auctionId) {
        SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
        
        // 更新保证金状态
        auction.setDepositStatus((byte) 1);
        auction.setStatus((byte) 1); // 拍卖进行中
        
        LocalDateTime now = LocalDateTime.now();
        auction.setStartTime(now);
        auction.setEndTime(now.plusHours(auction.getDurationHours()));
        
        auctionMapper.updateByPrimaryKeySelective(auction);
        
        // 推送通知
        notifyService.notifySeller(auction.getSellerId(), "拍卖已开始");
    }
    
    /**
     * 出价
     * @param auctionId 拍卖ID
     * @param bidderId 出价者ID
     * @param bidPrice 出价
     */
    @Transactional
    public synchronized void placeBid(Integer auctionId, Integer bidderId, BigDecimal bidPrice) {
        // 1. 使用 Redis 分布式锁（防止并发出价冲突）
        String lockKey = "auction:bid:lock:" + auctionId;
        Boolean lockAcquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS);
        
        if (!lockAcquired) {
            throw new RuntimeException("出价过于频繁，请稍后重试");
        }
        
        try {
            // 2. 检查拍卖状态
            SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
            
            if (auction.getStatus() != 1) {
                throw new RuntimeException("拍卖未进行或已结束");
            }
            
            if (LocalDateTime.now().isAfter(auction.getEndTime())) {
                throw new RuntimeException("拍卖已结束");
            }
            
            // 3. 检查出价金额
            BigDecimal minBidPrice = auction.getCurrentPrice().add(auction.getIncrement());
            if (bidPrice.compareTo(minBidPrice) < 0) {
                throw new RuntimeException("出价不得低于 " + minBidPrice + " 元");
            }
            
            // 4. 检查是否自己出价
            if (bidderId.equals(auction.getHighestBidderId())) {
                throw new RuntimeException("您已是当前最高出价者");
            }
            
            // 5. 更新拍卖信息
            auction.setCurrentPrice(bidPrice);
            auction.setHighestBidderId(bidderId);
            auction.setTotalBids(auction.getTotalBids() + 1);
            
            // 6. 检查是否需要延时（最后 5 分钟内出价）
            LocalDateTime now = LocalDateTime.now();
            if (auction.getEndTime().minusMinutes(5).isBefore(now) 
                && auction.getExtendCount() < 3) {
                auction.setEndTime(auction.getEndTime().plusMinutes(5));
                auction.setExtendCount(auction.getExtendCount() + 1);
            }
            
            auctionMapper.updateByPrimaryKeySelective(auction);
            
            // 7. 记录出价
            SicauAuctionBid bid = new SicauAuctionBid();
            bid.setAuctionId(auctionId);
            bid.setBidderId(bidderId);
            bid.setBidPrice(bidPrice);
            bid.setBidTime(now);
            bidMapper.insertSelective(bid);
            
            // 8. 推送实时通知（WebSocket）
            webSocketService.pushAuctionUpdate(auctionId, bidPrice, bidderId);
            
        } finally {
            // 9. 释放锁
            redisTemplate.delete(lockKey);
        }
    }
    
    /**
     * 查询拍卖详情
     */
    public Map<String, Object> getAuctionDetail(Integer auctionId) {
        SicauAuction auction = auctionMapper.selectByPrimaryKey(auctionId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("auction", auction);
        
        // 查询商品信息
        LitemallGoods goods = goodsService.findById(auction.getGoodsId());
        result.put("goods", goods);
        
        // 查询出价记录（最近 10 条）
        List<SicauAuctionBid> bids = bidMapper.selectByAuctionId(auctionId, 10);
        result.put("bids", bids);
        
        // 计算剩余时间
        if (auction.getStatus() == 1) {
            long remainSeconds = ChronoUnit.SECONDS.between(
                LocalDateTime.now(), 
                auction.getEndTime()
            );
            result.put("remainSeconds", Math.max(0, remainSeconds));
        }
        
        return result;
    }
}
```

### 4.2 拍卖结算定时任务

创建 `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/AuctionSettleTask.java`：

```java
package org.linlinjava.litemall.wx.task;

import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.SicauAuction;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.SicauAuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拍卖自动结算任务
 */
@Component
public class AuctionSettleTask {
    
    @Autowired
    private SicauAuctionService auctionService;
    
    @Autowired
    private LitemallOrderService orderService;
    
    @Autowired
    private WxPayService wxPayService;
    
    /**
     * 每分钟扫描一次已结束的拍卖
     */
    @Scheduled(cron = "0 * * * * ?")
    public void settleAuctions() {
        LocalDateTime now = LocalDateTime.now();
        
        // 查询状态=1（进行中）且已到结束时间的拍卖
        List<SicauAuction> auctions = auctionService.queryOngoingAuctions();
        
        for (SicauAuction auction : auctions) {
            if (now.isAfter(auction.getEndTime())) {
                settleAuction(auction);
            }
        }
    }
    
    /**
     * 结算单个拍卖
     */
    private void settleAuction(SicauAuction auction) {
        // 1. 检查是否有人出价
        if (auction.getHighestBidderId() == null) {
            // 流拍
            handleUnsold(auction);
        } else {
            // 成交
            handleSold(auction);
        }
    }
    
    /**
     * 处理流拍
     */
    private void handleUnsold(SicauAuction auction) {
        // 1. 更新拍卖状态
        auction.setStatus((byte) 4); // 已流拍
        auctionService.update(auction);
        
        // 2. 退还保证金
        wxPayService.refund(
            "拍卖保证金退款-" + auction.getId(), 
            auction.getDeposit()
        );
        auction.setDepositStatus((byte) 2); // 已退还
        auctionService.update(auction);
        
        // 3. 商品下架
        LitemallGoods goods = goodsService.findById(auction.getGoodsId());
        goods.setIsOnSale(false);
        goodsService.update(goods);
        
        // 4. 推送通知
        notifyService.notifySeller(auction.getSellerId(), "拍卖流拍，保证金已退还");
    }
    
    /**
     * 处理成交
     */
    private void handleSold(SicauAuction auction) {
        // 1. 更新拍卖状态
        auction.setStatus((byte) 3); // 已成交
        
        // 2. 创建订单
        LitemallOrder order = new LitemallOrder();
        order.setUserId(auction.getHighestBidderId());
        order.setSellerId(auction.getSellerId());
        order.setOrderSn(orderService.generateOrderSn());
        order.setOrderStatus((short) 101); // 待付款
        
        // 订单金额 = 成交价 - 保证金（保证金已支付，直接抵扣）
        BigDecimal remainAmount = auction.getCurrentPrice().subtract(auction.getDeposit());
        order.setActualPrice(remainAmount);
        order.setGoodsPrice(auction.getCurrentPrice());
        
        orderService.add(order);
        
        // 3. 关联订单
        auction.setOrderId(order.getId());
        auctionService.update(auction);
        
        // 4. 推送通知
        notifyService.notify(auction.getHighestBidderId(), 
            String.format("恭喜您拍得商品，请在 30 分钟内支付 %.2f 元", remainAmount));
        
        notifyService.notifySeller(auction.getSellerId(), 
            String.format("拍卖成交，成交价 %.2f 元", auction.getCurrentPrice()));
    }
}
```

---

## 5. API 契约定义

### 5.1 POST /wx/auction/create - 发起拍卖

**请求体**:
```json
{
  "goodsId": 1,
  "startPrice": 50.00,
  "increment": 5.00,
  "durationHours": 24
}
```

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "auctionId": 100,
    "deposit": 5.00,
    "payParams": {
      "timeStamp": "...",
      "paySign": "..."
    }
  }
}
```

### 5.2 POST /wx/auction/bid - 出价

**请求体**:
```json
{
  "auctionId": 100,
  "bidPrice": 60.00
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "出价成功",
  "data": {
    "currentPrice": 60.00,
    "remainSeconds": 3600
  }
}
```

### 5.3 GET /wx/auction/detail - 拍卖详情

**请求参数**:
- `id`: 拍卖ID

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "auction": {
      "id": 100,
      "startPrice": 50.00,
      "currentPrice": 60.00,
      "increment": 5.00,
      "status": 1,
      "endTime": "2025-10-28 10:00:00",
      "totalBids": 5,
      "extendCount": 1
    },
    "goods": {
      "name": "高等数学A（第七版）上册",
      "picUrl": "https://..."
    },
    "bids": [
      {
        "bidderNickname": "川农学子",
        "bidPrice": 60.00,
        "bidTime": "2025-10-27 09:30:00"
      }
    ],
    "remainSeconds": 3600
  }
}
```

### 5.4 GET /wx/auction/list - 拍卖列表

**请求参数**:
- `status`: 拍卖状态（1-进行中, 3-已成交, 4-已流拍）
- `page`, `size`

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "total": 10,
    "list": [
      {
        "auctionId": 100,
        "goodsName": "高等数学A（第七版）上册",
        "goodsPicUrl": "https://...",
        "startPrice": 50.00,
        "currentPrice": 60.00,
        "totalBids": 5,
        "endTime": "2025-10-28 10:00:00"
      }
    ]
  }
}
```

### 5.5 GET /admin/auction/statistics - 拍卖数据统计

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "totalAuctions": 100,
    "soldRate": 0.75,
    "avgPremiumRate": 0.20,
    "topCategories": [
      {
        "categoryName": "教材教辅",
        "count": 30
      }
    ]
  }
}
```

---

## 6. 配置文件变更

### 6.1 application-wx.yml

```yaml
# 拍卖配置
auction:
  # 保证金配置
  deposit:
    rate: 0.1  # 保证金比例（10%）
  
  # 延时配置
  extend:
    last_minutes: 5  # 最后 5 分钟内出价延时
    max_count: 3  # 最多延时 3 次
    extend_minutes: 5  # 每次延长 5 分钟
  
  # 支付配置
  pay:
    timeout_minutes: 30  # 成交后 30 分钟支付
```

---

## 7. 前端开发要点

### 7.1 小程序端关键文件

- `pages/auction/create/create.js` - 发起拍卖页
- `pages/auction/detail/detail.js` - 拍卖详情页
- `pages/auction/list/list.js` - 拍卖列表页

### 7.2 倒计时实现

```javascript
// pages/auction/detail/detail.js
data: {
  remainSeconds: 3600
},

onLoad() {
  this.startCountdown();
},

startCountdown() {
  this.countdown = setInterval(() => {
    let remain = this.data.remainSeconds - 1;
    
    if (remain <= 0) {
      clearInterval(this.countdown);
      wx.showToast({ title: '拍卖已结束', icon: 'none' });
      return;
    }
    
    this.setData({ remainSeconds: remain });
  }, 1000);
},

formatTime(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h}时${m}分${s}秒`;
}
```

---

## 8. 测试策略

### 8.1 单元测试

- 保证金计算测试（10% 起拍价）
- 最低出价计算测试（当前价 + 加价幅度）
- 延时规则测试（最后 5 分钟内出价 → 延长 5 分钟）

### 8.2 集成测试

- 完整拍卖流程测试（发起 → 出价 → 结算 → 支付）
- 流拍测试（无人出价 → 退还保证金）
- 并发出价测试（100 人同时出价 → 只有最高价生效）

### 8.3 性能测试

- 出价接口 QPS（目标 500/s）
- 结算定时任务性能（1000 个拍卖结算 < 10s）

---

## 9. 依赖关系

### 前置条件
- Epic 3 已完成（订单表和支付功能）
- Redis 已部署（分布式锁）

### 后续依赖
- 无直接依赖

---

## 10. 风险提示

1. **并发冲突**: 多人同时出价可能导致数据不一致，需使用分布式锁
2. **恶意出价**: 用户出价后不支付，需设置支付超时机制
3. **保证金退款**: 微信退款 1-3 个工作日到账，需向用户说明
4. **延时滥用**: 恶意用户在最后 1 秒出价拖延时间，限制延时次数（3 次）
5. **系统时间**: 结算定时任务依赖系统时间，需确保服务器时间准确

---

## 11. Story 任务分解

### Story 5.1: 发起拍卖 (10h)
- Task 1: 创建 `sicau_auction` 表
- Task 2: 实现 `SicauAuctionService.createAuction()` 方法
- Task 3: 保证金支付接口（复用微信支付）
- Task 4: 前端发起拍卖页开发
- Task 5: 测试保证金冻结和退还

### Story 5.2: 参与竞拍 (10h)
- Task 1: 创建 `sicau_auction_bid` 表
- Task 2: 实现 `placeBid()` 接口
- Task 3: Redis 分布式锁防止并发冲突
- Task 4: 实现延时规则（最后 5 分钟延长 5 分钟）
- Task 5: 前端拍卖详情页开发（实时倒计时）
- Task 6: WebSocket 实时推送（可选）

### Story 5.3: 拍卖结算 (8h)
- Task 1: 创建 `AuctionSettleTask` 定时任务
- Task 2: 实现流拍处理（退还保证金）
- Task 3: 实现成交处理（创建订单）
- Task 4: 推送成交通知
- Task 5: 测试自动结算流程

### Story 5.4: 拍卖数据统计 (4h)
- Task 1: 实现 `getStatistics()` 接口
- Task 2: 计算成功率（成交数 / 总数）
- Task 3: 计算溢价率（(成交价 - 起拍价) / 起拍价）
- Task 4: 统计热门品类
- Task 5: 管理后台数据看板开发

---

## 12. 验收清单

- [ ] 可成功发起拍卖并支付保证金
- [ ] 出价金额必须 ≥ 当前价 + 加价幅度
- [ ] 最后 5 分钟内出价自动延长 5 分钟
- [ ] 延长次数不超过 3 次
- [ ] 拍卖结束后自动结算（成交或流拍）
- [ ] 成交后自动创建订单，通知最高出价者
- [ ] 流拍后自动退还保证金
- [ ] 拍卖统计数据准确（成功率、溢价率）
- [ ] 所有 API 无编译错误

---

**Epic 状态**: 准备就绪，等待开发  
**下一步**: 由 bmm-dev 开发者开始实施
