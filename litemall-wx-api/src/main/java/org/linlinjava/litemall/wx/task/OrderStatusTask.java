package org.linlinjava.litemall.wx.task;

import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.db.service.SicauCommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态自动流转定时任务
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Component
public class OrderStatusTask {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderStatusTask.class);
    
    @Autowired
    private LitemallOrderService orderService;
    
    @Autowired
    private LitemallUserService userService;
    
    @Autowired
    private NotifyService notifyService;
    
    @Autowired
    private SicauCommentService commentService;
    
    /**
     * 每5分钟扫描一次待付款订单，超时自动取消
     * 规则：待付款超过30分钟自动取消
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void cancelUnpaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.minusMinutes(30);
        
        try {
            // 查询待付款订单（status=101）
            List<LitemallOrder> unpaidOrders = orderService.queryByStatus((short) 101);
            
            int cancelCount = 0;
            for (LitemallOrder order : unpaidOrders) {
                // 检查是否超时
                if (order.getAddTime().isBefore(expireTime)) {
                    order.setOrderStatus((short) 102); // 102=已取消
                    order.setCancelReason("超时未支付");
                    order.setUpdateTime(now);
                    
                    orderService.updateWithOptimisticLocker(order);
                    cancelCount++;
                    
                    logger.info("订单超时自动取消: orderSn={}, addTime={}", 
                        order.getOrderSn(), order.getAddTime());
                    
                    // 推送通知给买家（使用 REFUND 类型模板）
                    LitemallUser buyer = userService.findById(order.getUserId());
                    if (buyer != null && buyer.getMobile() != null) {
                        notifyService.notifySmsTemplate(
                            buyer.getMobile(), 
                            NotifyType.REFUND, 
                            new String[]{order.getOrderSn(), "超时未支付"}
                        );
                    }
                }
            }
            
            if (cancelCount > 0) {
                logger.info("本次取消超时订单数: {}", cancelCount);
            }
            
        } catch (Exception e) {
            logger.error("取消超时订单任务执行失败", e);
        }
    }
    
    /**
     * 每小时扫描一次待发货订单，超过24小时推送提醒
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void remindUnshippedOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.minusHours(24);
        
        try {
            // 查询待发货订单（status=201）
            List<LitemallOrder> unshippedOrders = orderService.queryByStatus((short) 201);
            
            int remindCount = 0;
            for (LitemallOrder order : unshippedOrders) {
                // 检查是否超过24小时未发货
                if (order.getPayTime() != null && order.getPayTime().isBefore(expireTime)) {
                    // 推送提醒给卖家
                    LitemallUser seller = userService.findById(order.getSellerId());
                    if (seller != null && seller.getMobile() != null) {
                        notifyService.notifySmsTemplate(
                            seller.getMobile(), 
                            NotifyType.SHIP, 
                            new String[]{order.getOrderSn()}
                        );
                        remindCount++;
                    }
                    
                    logger.info("推送发货提醒: orderSn={}, sellerId={}", 
                        order.getOrderSn(), order.getSellerId());
                }
            }
            
            if (remindCount > 0) {
                logger.info("本次发送发货提醒数: {}", remindCount);
            }
            
        } catch (Exception e) {
            logger.error("发货提醒任务执行失败", e);
        }
    }
    
    /**
     * 每天凌晨2点扫描待收货订单，超过7天自动确认收货
     * 规则：待收货超过7天自动确认收货
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void autoConfirmReceivedOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.minusDays(7);
        
        try {
            // 查询待收货订单（status=301）
            List<LitemallOrder> receivingOrders = orderService.queryByStatus((short) 301);
            
            int confirmCount = 0;
            for (LitemallOrder order : receivingOrders) {
                // 检查是否超过7天
                if (order.getShipTime() != null && order.getShipTime().isBefore(expireTime)) {
                    order.setOrderStatus((short) 401); // 401=待评价
                    order.setConfirmTime(now);
                    order.setUpdateTime(now);
                    
                    orderService.updateWithOptimisticLocker(order);
                    confirmCount++;
                    
                    logger.info("订单自动确认收货: orderSn={}, shipTime={}", 
                        order.getOrderSn(), order.getShipTime());
                    
                    // 推送通知给买家和卖家
                    LitemallUser buyer = userService.findById(order.getUserId());
                    if (buyer != null && buyer.getMobile() != null) {
                        notifyService.notifySmsTemplate(
                            buyer.getMobile(), 
                            NotifyType.SHIP, 
                            new String[]{order.getOrderSn(), "已自动确认收货"}
                        );
                    }
                    
                    LitemallUser seller = userService.findById(order.getSellerId());
                    if (seller != null && seller.getMobile() != null) {
                        notifyService.notifySmsTemplate(
                            seller.getMobile(), 
                            NotifyType.SHIP, 
                            new String[]{order.getOrderSn(), "买家已确认收货"}
                        );
                    }
                }
            }
            
            if (confirmCount > 0) {
                logger.info("本次自动确认收货订单数: {}", confirmCount);
            }
            
        } catch (Exception e) {
            logger.error("自动确认收货任务执行失败", e);
        }
    }
    
    /**
     * 每天凌晨3点扫描待评价订单，超过15天自动关闭
     * 规则：待评价超过15天自动关闭为已完成
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void autoCloseCommentOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.minusDays(15);
        
        try {
            // 查询待评价订单（status=401）
            List<LitemallOrder> commentOrders = orderService.queryByStatus((short) 401);
            
            int closeCount = 0;
            for (LitemallOrder order : commentOrders) {
                // 检查是否超过15天
                if (order.getConfirmTime() != null && order.getConfirmTime().isBefore(expireTime)) {
                    order.setOrderStatus((short) 402); // 402=已完成
                    order.setUpdateTime(now);
                    
                    orderService.updateWithOptimisticLocker(order);
                    closeCount++;
                    
                    logger.info("订单自动关闭: orderSn={}, confirmTime={}", 
                        order.getOrderSn(), order.getConfirmTime());
                }
            }
            
            if (closeCount > 0) {
                logger.info("本次自动关闭订单数: {}", closeCount);
            }
            
        } catch (Exception e) {
            logger.error("自动关闭订单任务执行失败", e);
        }
    }
    
    /**
     * 每小时检查一次评价状态，统计卖家信用分
     * （可选功能，用于计算卖家信誉）
     */
    @Scheduled(cron = "0 30 * * * ?")
    public void updateSellerCreditScore() {
        // TODO: 实现卖家信用分统计逻辑
        // 1. 查询最近完成的订单
        // 2. 统计卖家的平均评分
        // 3. 更新卖家信用等级
        logger.debug("卖家信用分统计任务执行（待实现）");
    }
}
