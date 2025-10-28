package org.linlinjava.litemall.wx.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauAuction;
import org.linlinjava.litemall.db.domain.SicauAuctionBid;
import org.linlinjava.litemall.db.service.SicauAuctionService;
import org.linlinjava.litemall.db.service.SicauAuctionBidService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拍卖相关接口
 * 
 * Story 5.1: 发起拍卖
 * Story 5.2: 参与竞拍
 */
@RestController
@RequestMapping("/wx/auction")
@Validated
public class WxAuctionController {
    private final Log logger = LogFactory.getLog(WxAuctionController.class);
    
    // 内存锁，用于防止并发出价冲突（生产环境应使用 Redis 分布式锁）
    private static final ConcurrentHashMap<Integer, Object> auctionLocks = new ConcurrentHashMap<>();

    @Autowired
    private SicauAuctionService auctionService;
    
    @Autowired
    private SicauAuctionBidService bidService;
    
    @Autowired(required = false)
    private org.linlinjava.litemall.wx.task.AuctionSettleTask settleTask;

    /**
     * 发起拍卖（Story 5.1）
     * 
     * POST /wx/auction/create
     * {
     *   "goodsId": 123,
     *   "startPrice": 50.00,
     *   "increment": 5.00,
     *   "durationHours": 24
     * }
     * 
     * 返回：
     * {
     *   "errno": 0,
     *   "data": {
     *     "auctionId": 1,
     *     "deposit": 5.00,
     *     "message": "拍卖创建成功，请支付保证金"
     *   }
     * }
     */
    @PostMapping("/create")
    public Object createAuction(@LoginUser Integer userId, @RequestBody Map<String, Object> body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        // 1. 解析参数
        Integer goodsId = (Integer) body.get("goodsId");
        Object startPriceObj = body.get("startPrice");
        Object incrementObj = body.get("increment");
        Integer durationHours = (Integer) body.get("durationHours");

        if (goodsId == null) {
            return ResponseUtil.badArgument("商品ID不能为空");
        }

        BigDecimal startPrice;
        try {
            if (startPriceObj instanceof Integer) {
                startPrice = new BigDecimal((Integer) startPriceObj);
            } else if (startPriceObj instanceof Double) {
                startPrice = BigDecimal.valueOf((Double) startPriceObj);
            } else if (startPriceObj instanceof String) {
                startPrice = new BigDecimal((String) startPriceObj);
            } else {
                return ResponseUtil.badArgument("起拍价格式错误");
            }
        } catch (Exception e) {
            return ResponseUtil.badArgument("起拍价格式错误");
        }

        BigDecimal increment;
        try {
            if (incrementObj instanceof Integer) {
                increment = new BigDecimal((Integer) incrementObj);
            } else if (incrementObj instanceof Double) {
                increment = BigDecimal.valueOf((Double) incrementObj);
            } else if (incrementObj instanceof String) {
                increment = new BigDecimal((String) incrementObj);
            } else {
                return ResponseUtil.badArgument("加价幅度格式错误");
            }
        } catch (Exception e) {
            return ResponseUtil.badArgument("加价幅度格式错误");
        }

        if (durationHours == null) {
            return ResponseUtil.badArgument("拍卖时长不能为空");
        }

        // 2. 创建拍卖
        try {
            Integer auctionId = auctionService.createAuction(userId, goodsId, startPrice, increment, durationHours);
            SicauAuction auction = auctionService.findById(auctionId);

            logger.info("用户 " + userId + " 发起拍卖: auctionId=" + auctionId + 
                       ", startPrice=" + startPrice + ", deposit=" + auction.getDeposit());

            Map<String, Object> data = new HashMap<>();
            data.put("auctionId", auctionId);
            data.put("deposit", auction.getDeposit());
            data.put("message", "拍卖创建成功，请支付保证金 " + auction.getDeposit() + " 元");

            return ResponseUtil.ok(data);
        } catch (RuntimeException e) {
            logger.warn("用户 " + userId + " 发起拍卖失败: " + e.getMessage());
            return ResponseUtil.fail(601, e.getMessage());
        }
    }

    /**
     * 查询拍卖详情
     * 
     * GET /wx/auction/detail?id=1
     */
    @GetMapping("/detail")
    public Object getDetail(@LoginUser Integer userId, @RequestParam Integer id) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        if (id == null) {
            return ResponseUtil.badArgument();
        }

        SicauAuction auction = auctionService.findById(id);
        if (auction == null) {
            return ResponseUtil.badArgumentValue();
        }

        return ResponseUtil.ok(auction);
    }

    /**
     * 查询我发起的拍卖列表
     * 
     * GET /wx/auction/myAuctions
     */
    @GetMapping("/myAuctions")
    public Object getMyAuctions(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        try {
            return ResponseUtil.ok(auctionService.queryBySellerId(userId));
        } catch (Exception e) {
            logger.error("查询拍卖列表失败", e);
            return ResponseUtil.fail(602, "查询失败");
        }
    }

    /**
     * 查询进行中的拍卖列表
     * 
     * GET /wx/auction/ongoing
     */
    @GetMapping("/ongoing")
    public Object getOngoingAuctions() {
        try {
            return ResponseUtil.ok(auctionService.queryOngoingAuctions());
        } catch (Exception e) {
            logger.error("查询进行中拍卖失败", e);
            return ResponseUtil.fail(603, "查询失败");
        }
    }
    
    /**
     * 出价（Story 5.2）
     * 
     * POST /wx/auction/bid
     * {
     *   "auctionId": 1,
     *   "bidPrice": 60.00
     * }
     * 
     * 返回：
     * {
     *   "errno": 0,
     *   "errmsg": "出价成功",
     *   "data": {
     *     "currentPrice": 60.00,
     *     "remainSeconds": 3600,
     *     "extended": false
     *   }
     * }
     */
    @PostMapping("/bid")
    public Object placeBid(@LoginUser Integer userId, @RequestBody Map<String, Object> body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        // 1. 解析参数
        Integer auctionId = (Integer) body.get("auctionId");
        Object bidPriceObj = body.get("bidPrice");

        if (auctionId == null) {
            return ResponseUtil.badArgument("拍卖ID不能为空");
        }

        BigDecimal bidPrice;
        try {
            if (bidPriceObj instanceof Integer) {
                bidPrice = new BigDecimal((Integer) bidPriceObj);
            } else if (bidPriceObj instanceof Double) {
                bidPrice = BigDecimal.valueOf((Double) bidPriceObj);
            } else if (bidPriceObj instanceof String) {
                bidPrice = new BigDecimal((String) bidPriceObj);
            } else {
                return ResponseUtil.badArgument("出价格式错误");
            }
        } catch (Exception e) {
            return ResponseUtil.badArgument("出价格式错误");
        }

        // 2. 获取锁对象（防止并发出价冲突）
        Object lock = auctionLocks.computeIfAbsent(auctionId, k -> new Object());

        // 3. 同步出价
        synchronized (lock) {
            try {
                // 记录出价前的状态
                SicauAuction beforeAuction = auctionService.findById(auctionId);
                int extendCountBefore = beforeAuction.getExtendCount();

                // 执行出价
                auctionService.placeBid(auctionId, userId, bidPrice);

                // 查询出价后的状态
                SicauAuction afterAuction = auctionService.findById(auctionId);

                // 计算剩余时间
                long remainSeconds = ChronoUnit.SECONDS.between(
                    LocalDateTime.now(),
                    afterAuction.getEndTime()
                );

                logger.info("用户 " + userId + " 出价成功: auctionId=" + auctionId + 
                           ", bidPrice=" + bidPrice + ", remainSeconds=" + remainSeconds);

                Map<String, Object> data = new HashMap<>();
                data.put("currentPrice", afterAuction.getCurrentPrice());
                data.put("remainSeconds", Math.max(0, remainSeconds));
                data.put("extended", afterAuction.getExtendCount() > extendCountBefore);
                data.put("extendCount", afterAuction.getExtendCount());

                return ResponseUtil.ok(data);
            } catch (RuntimeException e) {
                logger.warn("用户 " + userId + " 出价失败: " + e.getMessage());
                return ResponseUtil.fail(604, e.getMessage());
            }
        }
    }
    
    /**
     * 查询拍卖详情（增强版，包含出价历史）
     * 
     * GET /wx/auction/detailWithBids?id=1
     */
    @GetMapping("/detailWithBids")
    public Object getDetailWithBids(@LoginUser Integer userId, @RequestParam Integer id) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        if (id == null) {
            return ResponseUtil.badArgument();
        }

        SicauAuction auction = auctionService.findById(id);
        if (auction == null) {
            return ResponseUtil.badArgumentValue();
        }

        // 查询出价记录（最近 10 条）
        List<SicauAuctionBid> bids = bidService.queryByAuctionId(id, 10);

        // 计算剩余时间
        Long remainSeconds = null;
        if (auction.getStatus() == 1) {
            remainSeconds = ChronoUnit.SECONDS.between(
                LocalDateTime.now(),
                auction.getEndTime()
            );
            remainSeconds = Math.max(0, remainSeconds);
        }
        
        // 计算最低出价金额
        BigDecimal minBidPrice = auctionService.getMinBidPrice(id);

        Map<String, Object> data = new HashMap<>();
        data.put("auction", auction);
        data.put("bids", bids);
        data.put("remainSeconds", remainSeconds);
        data.put("minBidPrice", minBidPrice);

        return ResponseUtil.ok(data);
    }
    
    /**
     * 查询我的出价记录
     * 
     * GET /wx/auction/myBids
     */
    @GetMapping("/myBids")
    public Object getMyBids(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        try {
            List<SicauAuctionBid> bids = bidService.queryByBidderId(userId);
            return ResponseUtil.ok(bids);
        } catch (Exception e) {
            logger.error("查询出价记录失败", e);
            return ResponseUtil.fail(605, "查询失败");
        }
    }
    
    /**
     * 手动触发拍卖结算（Story 5.3 - 测试接口）
     * 
     * POST /wx/auction/settleNow?id=1
     * 
     * 注意: 生产环境应删除此接口，仅用于测试
     */
    @PostMapping("/settleNow")
    public Object settleNow(@LoginUser Integer userId, @RequestParam Integer id) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        if (id == null) {
            return ResponseUtil.badArgument("拍卖ID不能为空");
        }

        try {
            SicauAuction auction = auctionService.findById(id);
            if (auction == null) {
                return ResponseUtil.badArgumentValue();
            }

            // 检查是否已结算
            if (auction.getStatus() != 1) {
                return ResponseUtil.fail(606, "拍卖未进行或已结算");
            }

            // 调用结算任务
            if (settleTask != null) {
                settleTask.settleAuction(auction);
                
                // 重新查询状态
                auction = auctionService.findById(id);
                
                Map<String, Object> data = new HashMap<>();
                data.put("auctionId", id);
                data.put("status", auction.getStatus());
                data.put("statusText", auction.getStatus() == 3 ? "已成交" : "已流拍");
                
                return ResponseUtil.ok(data);
            } else {
                return ResponseUtil.fail(607, "结算服务未启动");
            }
        } catch (Exception e) {
            logger.error("手动结算失败", e);
            return ResponseUtil.fail(608, "结算失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询已结束的拍卖列表（Story 5.3）
     * 
     * GET /wx/auction/finished?status=3
     * status: 3=已成交, 4=已流拍
     */
    @GetMapping("/finished")
    public Object getFinishedAuctions(@RequestParam(required = false) Byte status) {
        try {
            List<SicauAuction> auctions;
            
            if (status != null && (status == 3 || status == 4)) {
                // 查询指定状态的拍卖
                auctions = auctionService.queryByStatus(status);
            } else {
                // 查询所有已结束的拍卖（status=3 或 4）
                auctions = auctionService.queryFinishedAuctions();
            }
            
            return ResponseUtil.ok(auctions);
        } catch (Exception e) {
            logger.error("查询已结束拍卖失败", e);
            return ResponseUtil.fail(609, "查询失败");
        }
    }
    
    /**
     * 拍卖数据统计（Story 5.4）
     * 
     * GET /wx/auction/statistics
     * 
     * 返回：
     * {
     *   "totalAuctions": 100,       // 拍卖总数
     *   "soldCount": 75,            // 成交数
     *   "unsoldCount": 25,          // 流拍数
     *   "soldRate": 0.75,           // 成交率（成交数/总数）
     *   "avgPremiumRate": 0.20      // 平均溢价率（(成交价-起拍价)/起拍价）
     * }
     */
    @GetMapping("/statistics")
    public Object getStatistics() {
        try {
            // 1. 统计拍卖总数
            int totalAuctions = auctionService.countTotal();
            
            // 2. 统计成交数和流拍数
            int soldCount = auctionService.countSold();
            int unsoldCount = auctionService.countUnsold();
            
            // 3. 计算成交率
            double soldRate = 0.0;
            if (totalAuctions > 0) {
                soldRate = (double) soldCount / totalAuctions;
            }
            
            // 4. 计算平均溢价率
            double avgPremiumRate = 0.0;
            if (soldCount > 0) {
                List<SicauAuction> soldAuctions = auctionService.queryAllSoldAuctions();
                double totalPremiumRate = 0.0;
                int validCount = 0;
                
                for (SicauAuction auction : soldAuctions) {
                    if (auction.getStartPrice() != null && 
                        auction.getCurrentPrice() != null &&
                        auction.getStartPrice().compareTo(BigDecimal.ZERO) > 0) {
                        
                        // 溢价率 = (成交价 - 起拍价) / 起拍价
                        BigDecimal premium = auction.getCurrentPrice().subtract(auction.getStartPrice());
                        double premiumRate = premium.divide(auction.getStartPrice(), 4, RoundingMode.HALF_UP)
                                                   .doubleValue();
                        totalPremiumRate += premiumRate;
                        validCount++;
                    }
                }
                
                if (validCount > 0) {
                    avgPremiumRate = totalPremiumRate / validCount;
                }
            }
            
            // 5. 组装返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("totalAuctions", totalAuctions);
            data.put("soldCount", soldCount);
            data.put("unsoldCount", unsoldCount);
            data.put("soldRate", Math.round(soldRate * 10000.0) / 10000.0); // 保留4位小数
            data.put("avgPremiumRate", Math.round(avgPremiumRate * 10000.0) / 10000.0); // 保留4位小数
            
            logger.info("拍卖统计: 总数=" + totalAuctions + ", 成交=" + soldCount + 
                       ", 流拍=" + unsoldCount + ", 成交率=" + soldRate + 
                       ", 平均溢价率=" + avgPremiumRate);
            
            return ResponseUtil.ok(data);
        } catch (Exception e) {
            logger.error("查询拍卖统计失败", e);
            return ResponseUtil.fail(610, "统计失败");
        }
    }
}
