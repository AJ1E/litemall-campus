package org.linlinjava.litemall.wx.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.SicauCourier;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.db.service.SicauCourierService;
import org.linlinjava.litemall.wx.service.CourierNotifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 快递员超时配送监控任务
 * 每10分钟扫描一次，处理超时配送订单
 */
@Component
public class CourierTimeoutTask {
    private final Log logger = LogFactory.getLog(CourierTimeoutTask.class);
    
    @Autowired
    private LitemallOrderService orderService;
    
    @Autowired
    private SicauCourierService courierService;
    
    @Autowired
    private LitemallUserService userService;
    
    @Autowired
    private CourierNotifyService notifyService;
    
    /**
     * 每 10 分钟扫描一次超时配送
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void checkTimeoutDelivery() {
        logger.info("开始扫描超时配送订单...");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutThreshold = now.minusHours(2);
        
        try {
            // 查询状态=301（待收货）的所有订单
            List<LitemallOrder> orders = orderService.queryByStatus((short) 301);
            
            int timeoutCount = 0;
            for (LitemallOrder order : orders) {
                // 只处理已分配快递员的订单
                if (order.getCourierId() == null) {
                    continue;
                }
                
                // 接单后2小时未完成配送视为超时
                if (order.getShipTime() != null && order.getShipTime().isBefore(timeoutThreshold)) {
                    handleTimeout(order);
                    timeoutCount++;
                }
            }
            
            logger.info(String.format("超时配送扫描完成，共处理 %d 个超时订单", timeoutCount));
        } catch (Exception e) {
            logger.error("超时配送扫描失败", e);
        }
    }
    
    /**
     * 处理单个超时配送订单
     */
    @Transactional
    public void handleTimeout(LitemallOrder order) {
        try {
            Integer courierId = order.getCourierId();
            SicauCourier courier = courierService.findByUserId(courierId);
            
            if (courier == null) {
                logger.warn(String.format("订单 %s 的快递员 %d 不存在", order.getOrderSn(), courierId));
                return;
            }
            
            // 如果快递员已被取消资格，跳过处理
            if (courier.getStatus() == 3) {
                logger.info(String.format("快递员 %d 已被取消资格，跳过订单 %s", courierId, order.getOrderSn()));
                return;
            }
            
            // 1. 扣除快递员 10 积分
            LitemallUser user = userService.findById(courierId);
            if (user != null) {
                int currentScore = user.getCreditScore() != null ? user.getCreditScore() : 100;
                user.setCreditScore(Math.max(0, currentScore - 10));
                userService.updateById(user);
                logger.info(String.format("快递员 %d 超时配送，扣除 10 积分，当前积分: %d", 
                    courierId, user.getCreditScore()));
            }
            
            // 2. 增加超时次数
            int currentTimeoutCount = courier.getTimeoutCount() != null ? courier.getTimeoutCount() : 0;
            courier.setTimeoutCount(currentTimeoutCount + 1);
            
            // 3. 超时 3 次取消资格
            if (courier.getTimeoutCount() >= 3) {
                courier.setStatus((byte) 3); // 已取消资格
                courierService.updateById(courier);
                
                logger.warn(String.format("快递员 %d 因配送超时 3 次，已取消资格", courierId));
                
                // 发送取消资格通知
                try {
                    notifyService.notifyCourierTimeout(courierId, order.getId(), 
                        courier.getTimeoutCount(), 10);
                } catch (Exception e) {
                    // 通知失败不影响主流程
                }
            } else {
                courierService.updateById(courier);
                
                logger.info(String.format("快递员 %d 超时次数: %d/3", courierId, courier.getTimeoutCount()));
                
                // 发送超时警告通知
                try {
                    notifyService.notifyCourierTimeout(courierId, order.getId(), 
                        courier.getTimeoutCount(), 10);
                } catch (Exception e) {
                    // 通知失败不影响主流程
                }
            }
            
            // 4. 释放订单（回到待配送列表）
            order.setCourierId(null);
            order.setOrderStatus((short) 201); // 重新待发货
            order.setShipTime(null);
            order.setPickupCode(null);
            orderService.updateWithOptimisticLocker(order);
            
            logger.info(String.format("订单 %s 已释放，重新回到待配送列表", order.getOrderSn()));
            
        } catch (Exception e) {
            logger.error(String.format("处理超时订单 %s 失败", order.getOrderSn()), e);
            throw e; // 回滚事务
        }
    }
}
