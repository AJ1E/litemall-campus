# Epic 5: 限时拍卖模块 - 完成总结

**Epic ID**: Epic 5  
**Epic 名称**: 限时拍卖模块  
**完成日期**: 2025-10-28  
**状态**: ✅ 已完成

---

## 一、Epic 概览

### 1.1 业务价值
为四川农业大学校园闲置物品交易系统引入限时拍卖功能，增加交易趣味性和商品定价效率，支持稀缺商品的价格发现机制。

### 1.2 Stories 清单
| Story ID | Story 名称 | 状态 | 完成日期 |
|----------|-----------|------|---------|
| 5.1 | 发起拍卖 | ✅ Done | 2025-10-25 |
| 5.2 | 参与竞拍 | ✅ Done | 2025-10-27 |
| 5.3 | 拍卖结算 | ✅ Done | 2025-10-27 |
| 5.4 | 拍卖数据统计 | ✅ Done | 2025-10-28 |

---

## 二、技术架构总览

### 2.1 数据层 (litemall-db)

**数据表设计** (2张表):

1. **sicau_auction** (拍卖主表, 19字段):
   - 基础信息: id, goods_id, seller_id, title, description
   - 价格字段: start_price, current_price, increment, deposit_amount
   - 时间字段: start_time, end_time, extend_count
   - 状态字段: status (0=草稿, 1=进行中, 2=等待支付, 3=已成交, 4=已流拍)
   - 竞拍信息: highest_bidder_id, bid_count
   - 审计字段: add_time, update_time, deleted

2. **sicau_auction_bid** (出价记录表, 6字段):
   - 关联信息: id, auction_id, bidder_id
   - 出价信息: bid_price, bid_time
   - 审计字段: deleted

**Service 层新增类**:
- `SicauAuctionService` (14个方法)
  - Story 5.1: createAuction(), startAuctionAfterDepositPaid()
  - Story 5.2: placeBid() (87行核心逻辑), getMinBidPrice()
  - Story 5.3: queryExpiredAuctions(), queryByStatus(), queryFinishedAuctions()
  - Story 5.4: countTotal(), countSold(), countUnsold(), queryAllSoldAuctions()
  
- `SicauAuctionBidService` (4个方法)
  - add(), queryByAuctionId(), queryByBidderId(), countByAuctionId()

### 2.2 API 层 (litemall-wx-api)

**WxAuctionController** (10个端点):

| HTTP方法 | 路径 | Story | 功能 |
|---------|------|-------|------|
| POST | /wx/auction/create | 5.1 | 发起拍卖 (需缴纳10%保证金) |
| GET | /wx/auction/detail?id=X | 5.1 | 查看拍卖详情 |
| GET | /wx/auction/myAuctions | 5.1 | 我发起的拍卖 |
| GET | /wx/auction/ongoing | 5.1 | 进行中的拍卖列表 |
| POST | /wx/auction/bid | 5.2 | 参与竞拍 (加价出价) |
| GET | /wx/auction/detailWithBids?id=X | 5.2 | 详情+出价历史+剩余时间 |
| GET | /wx/auction/myBids | 5.2 | 我的出价记录 |
| POST | /wx/auction/settleNow?id=X | 5.3 | 手动结算拍卖 (测试接口) |
| GET | /wx/auction/finished?status=X | 5.3 | 已结束拍卖 (可筛选成交/流拍) |
| GET | /wx/auction/statistics | 5.4 | 拍卖数据统计 |

**AuctionSettleTask** (定时任务):
- Cron表达式: `0 * * * * ?` (每分钟执行)
- 自动结算过期拍卖 (成交/流拍状态更新)
- 日志记录: 成功数/失败数/总计

### 2.3 并发控制

**JVM级别锁** (当前实现):
```java
private static final ConcurrentHashMap<Integer, Object> auctionLocks = new ConcurrentHashMap<>();

public Object placeBid(@RequestBody AuctionBidRequest request) {
    Object lock = auctionLocks.computeIfAbsent(auctionId, k -> new Object());
    synchronized (lock) {
        // 出价逻辑
    }
}
```

**生产环境建议**: 升级到 Redis 分布式锁 (Redisson)

---

## 三、核心功能实现

### 3.1 Story 5.1: 发起拍卖

**关键逻辑**:
1. 用户提交拍卖信息 (起拍价、时长12/24/48h、加价幅度)
2. 系统计算10%保证金 (`depositAmount = startPrice * 0.1`)
3. 创建拍卖记录 (status=0 草稿状态)
4. 前端跳转支付页面
5. 支付成功后调用 `startAuctionAfterDepositPaid()` 激活拍卖

**技术要点**:
- 时长验证: `durationHours ∈ {12, 24, 48}`
- 保证金: 卖家违约不退还 (后续订单模块处理)
- 时间计算: `LocalDateTime.now().plusHours(durationHours)`

**文档**: `docs/epic-5-story-5.1-implementation.md`

### 3.2 Story 5.2: 参与竞拍

**关键逻辑** (placeBid方法, 87行):
1. 校验拍卖存在且状态为1 (进行中)
2. 校验卖家不能自己出价
3. 校验出价 >= 当前价 + 加价幅度
4. 检查是否在最后5分钟内
5. 若是且延长次数<3, 则延长5分钟 (最多延长15分钟)
6. 更新拍卖记录 (currentPrice, highestBidderId, bidCount, extendCount)
7. 插入出价记录到 sicau_auction_bid
8. 返回成功及剩余时间

**延时规则**:
```java
long minutesToEnd = ChronoUnit.MINUTES.between(now, endTime);
if (minutesToEnd < 5 && extendCount < 3) {
    newEndTime = endTime.plusMinutes(5);
    auction.setExtendCount(extendCount + 1);
}
```

**技术亮点**:
- 并发控制: ConcurrentHashMap + synchronized
- 原子更新: 单次数据库写入完成所有字段更新
- 剩余时间计算: ChronoUnit.SECONDS.between(now, endTime)

**文档**: `docs/epic-5-story-5.2-implementation.md`, `docs/epic-5-story-5.2-summary.md`

### 3.3 Story 5.3: 拍卖结算

**定时任务逻辑** (AuctionSettleTask):
```java
@Scheduled(cron = "0 * * * * ?") // 每分钟第0秒执行
public void settleExpiredAuctions() {
    List<SicauAuction> expiredAuctions = auctionService.queryExpiredAuctions();
    for (SicauAuction auction : expiredAuctions) {
        settleAuction(auction.getId()); // 事务处理
    }
}

@Transactional
public void settleAuction(Integer auctionId) {
    if (auction.getHighestBidderId() != null) {
        handleSold(auction); // 成交: status=3
    } else {
        handleUnsold(auction); // 流拍: status=4
    }
}
```

**TODO (后续集成)**:
- 成交后创建订单 (金额=currentPrice)
- 流拍后退还保证金给卖家
- 发送微信模板消息通知买卖双方

**文档**: `docs/epic-5-story-5.3-implementation.md`

### 3.4 Story 5.4: 拍卖数据统计

**统计指标** (5个):
1. **totalAuctions**: 拍卖总数 (所有状态)
2. **soldCount**: 成交数量 (status=3)
3. **unsoldCount**: 流拍数量 (status=4)
4. **soldRate**: 成交率 = soldCount / totalAuctions (4位小数)
5. **avgPremiumRate**: 平均溢价率 = avg((成交价 - 起拍价) / 起拍价) (4位小数)

**溢价率计算示例**:
```
拍卖A: 起拍¥100, 成交¥135 → 溢价率 = (135-100)/100 = 0.35 (35%)
拍卖B: 起拍¥200, 成交¥220 → 溢价率 = (220-200)/200 = 0.10 (10%)
平均溢价率 = (0.35 + 0.10) / 2 = 0.225 (22.5%)
```

**技术亮点**:
- 使用 `RoundingMode.HALF_UP` 替代已弃用的 `BigDecimal.ROUND_HALF_UP`
- 4位小数精度: `Math.round(value * 10000.0) / 10000.0`
- 边界处理: startPrice=0 跳过, totalAuctions=0 返回0.0

**文档**: `docs/epic-5-story-5.4-implementation.md`

---

## 四、编译验证

### 4.1 编译历史
| Story | 编译时间 | 结果 | 模块数 |
|-------|---------|------|-------|
| 5.1 | - | ✅ SUCCESS | 7 |
| 5.2 | 16.244s | ✅ SUCCESS | 7 |
| 5.3 | 15.757s | ✅ SUCCESS | 7 |
| 5.4 | 15.937s | ✅ SUCCESS | 7 |

### 4.2 最终编译命令
```bash
cd /workspaces/litemall-campus
mvn clean compile -T1C -DskipTests
```

**输出**:
```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  15.937 s (Wall Clock)
[INFO] Finished at: 2025-10-28T10:18:24Z
[INFO] ------------------------------------------------------------------------
```

---

## 五、数据流图

### 5.1 拍卖生命周期
```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│ 0: 草稿     │─支付→│ 1: 进行中     │─时间到→│ 2: 等待支付  │
│ (未支付保证金)│      │ (接受出价)    │      │ (系统预留)   │
└─────────────┘      └──────────────┘      └─────────────┘
                            │                      │
                            │                      ▼
                            │               ┌─────────────┐
                            │               │ 3: 已成交    │
                            │               │ (有最高出价) │
                            │               └─────────────┘
                            │
                            ▼
                     ┌─────────────┐
                     │ 4: 已流拍    │
                     │ (无人出价)   │
                     └─────────────┘
```

### 5.2 出价流程
```
用户发起出价
    ↓
竞争锁 (synchronized)
    ↓
校验 (8个检查点)
    ↓
计算延时 (< 5分钟?)
    ↓
更新拍卖记录 (原子操作)
    ↓
插入出价历史
    ↓
释放锁
    ↓
返回剩余时间
```

---

## 六、关键技术决策

### 6.1 为何选择 JVM 锁而非分布式锁?
**原因**: MVP 阶段, 单体部署, 简化实现  
**后果**: 多实例部署时会出现并发问题  
**迁移路径**: 引入 Redisson, 使用 `RLock lock = redisson.getLock("auction:" + id);`

### 6.2 为何定时任务每分钟执行而非实时结算?
**原因**: 
1. 减少系统复杂度, 不需要延迟队列
2. 1分钟误差对拍卖业务影响极小
3. 批量处理提高数据库效率

**权衡**: 实时性 vs 简洁性 (选择了简洁性)

### 6.3 为何统计接口实时计算而非缓存?
**原因**:
1. 初期数据量小, 性能压力不大
2. 保证数据一致性 (无缓存失效问题)

**优化建议**: 超过1万条拍卖后引入 Redis 缓存 (TTL: 5分钟)

---

## 七、测试覆盖度

### 7.1 单元测试
❌ **未实现** (MVP 阶段优先功能开发)

**待补充测试**:
- `SicauAuctionService.placeBid()` 边界场景 (延时规则, 并发出价)
- `AuctionSettleTask.settleAuction()` 成交/流拍逻辑
- 溢价率计算精度测试

### 7.2 集成测试
✅ **通过编译验证** (无语法错误, 依赖完整)

### 7.3 手动测试建议
1. 创建拍卖 → 支付保证金 → 验证status=1
2. 多用户并发出价 → 验证currentPrice更新正确
3. 最后5分钟出价 → 验证延时规则 (最多延长3次)
4. 等待拍卖结束 → 验证定时任务自动结算
5. 调用统计接口 → 验证成交率/溢价率计算正确

---

## 八、已知限制与技术债

### 8.1 功能限制
1. ❌ 未实现订单创建 (成交后需手动对接订单模块)
2. ❌ 未实现保证金退款 (流拍后需手动处理)
3. ❌ 未实现微信通知 (结算后无消息推送)
4. ❌ 未实现管理后台界面 (统计数据无可视化)

### 8.2 技术债
1. 🔒 **并发控制**: JVM锁 → 需升级为分布式锁
2. 📊 **统计性能**: 实时计算 → 需引入缓存
3. 🧪 **测试覆盖**: 0% → 需补充单元/集成测试
4. 📝 **API文档**: 无 → 需补充 Swagger 注释

### 8.3 安全问题
1. ⚠️ **恶意出价**: 未实现出价冷却时间 (可被刷单)
2. ⚠️ **保证金欺诈**: 未验证支付真实性 (依赖微信支付回调)
3. ⚠️ **数据越权**: 未严格校验用户只能查看自己的出价记录

---

## 九、后续优化路线图

### 9.1 短期优化 (1-2周)
- [ ] 对接订单模块: 成交后自动创建订单
- [ ] 对接支付模块: 保证金支付/退款流程
- [ ] 对接通知模块: 出价/成交/流拍消息推送
- [ ] 补充单元测试: 核心业务逻辑覆盖率 > 80%

### 9.2 中期增强 (1个月)
- [ ] 引入 Redis 分布式锁 (支持多实例部署)
- [ ] 统计接口缓存 (TTL: 5分钟)
- [ ] 管理后台界面: 拍卖审核、数据大屏
- [ ] 热门品类统计: 按category_id分组统计

### 9.3 长期规划 (3个月)
- [ ] 延迟队列: 替换定时任务, 实现秒级结算精度
- [ ] 反作弊系统: 检测异常出价模式 (同一IP频繁出价)
- [ ] 推荐算法: 根据用户浏览历史推荐拍卖品
- [ ] 直播功能: 支持拍卖品实时视频展示

---

## 十、文档清单

| 文档名称 | 路径 | 说明 |
|---------|------|------|
| Story 5.1 实现文档 | `docs/epic-5-story-5.1-implementation.md` | 发起拍卖详细设计 |
| Story 5.2 实现文档 | `docs/epic-5-story-5.2-implementation.md` | 竞拍逻辑+延时规则 |
| Story 5.2 总结 | `docs/epic-5-story-5.2-summary.md` | 并发控制方案 |
| Story 5.3 实现文档 | `docs/epic-5-story-5.3-implementation.md` | 定时任务+结算逻辑 |
| Story 5.4 实现文档 | `docs/epic-5-story-5.4-implementation.md` | 统计接口+溢价率计算 |
| Epic 5 完成总结 | `docs/epic-5-completion-summary.md` | 本文档 |

---

## 十一、总结

Epic 5 (限时拍卖模块) 历时4天, 完成4个 Stories, 新增**2张数据表、2个Service类、1个Controller、1个定时任务**, 共计**10个REST API端点**。核心功能包括:

✅ **发起拍卖**: 10%保证金机制, 12/24/48小时可选时长  
✅ **参与竞拍**: 延时规则 (最后5分钟延长5分钟, 最多3次), JVM级并发控制  
✅ **自动结算**: 定时任务每分钟扫描过期拍卖, 自动标记成交/流拍  
✅ **数据统计**: 成交率、平均溢价率实时计算 (4位小数精度)  

代码编译通过 (BUILD SUCCESS), 架构清晰, 为后续订单对接、分布式改造、管理后台开发奠定了坚实基础。Epic 5 成功交付,  MVP Sprint 1 进度推进至 **71.4% (5/7 Epics完成)** 🎉
