package org.linlinjava.litemall.wx.service;

import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 快递员通知服务
 * 
 * 负责发送快递员相关的通知消息，包括：
 * 1. 接单通知（通知买家）
 * 2. 配送完成通知（通知买家）
 * 3. 配送超时通知（通知快递员）
 * 4. 提现到账通知（通知快递员）
 */
@Service
public class CourierNotifyService {
    private static final Logger logger = LoggerFactory.getLogger(CourierNotifyService.class);

    @Autowired
    private NotifyService notifyService;

    @Autowired
    private LitemallUserService userService;

    /**
     * 通知买家：订单已被快递员接单
     * 
     * @param userId 买家用户ID
     * @param orderId 订单ID
     * @param pickupCode 取件码
     * @param courierName 快递员姓名
     */
    @Async
    public void notifyBuyerOrderAccepted(Integer userId, Integer orderId, String pickupCode, String courierName) {
        try {
            LitemallUser user = userService.findById(userId);
            if (user == null) {
                logger.error("通知失败：用户不存在 userId={}", userId);
                return;
            }

            String mobile = user.getMobile();
            if (mobile == null || mobile.isEmpty()) {
                logger.warn("用户 {} 没有手机号，跳过短信通知", userId);
            } else {
                // 短信通知参数：{1}订单号 {2}取件码 {3}快递员姓名
                String[] params = new String[]{
                    orderId.toString(),
                    pickupCode,
                    courierName != null ? courierName : "快递员"
                };
                
                notifyService.notifySmsTemplate(mobile, NotifyType.COURIER_ACCEPT, params);
                logger.info("已发送接单通知短信：userId={}, orderId={}, pickupCode={}", userId, orderId, pickupCode);
            }

            // TODO: 微信模板消息通知
            // 需要集成微信公众号模板消息API
            // notifyService.notifyWxTemplate(user.getWeixinOpenid(), NotifyType.COURIER_ACCEPT, data);

        } catch (Exception e) {
            logger.error("发送接单通知失败：userId={}, orderId={}, error={}", userId, orderId, e.getMessage(), e);
        }
    }

    /**
     * 通知买家：订单配送完成
     * 
     * @param userId 买家用户ID
     * @param orderId 订单ID
     * @param courierName 快递员姓名
     */
    @Async
    public void notifyBuyerOrderDelivered(Integer userId, Integer orderId, String courierName) {
        try {
            LitemallUser user = userService.findById(userId);
            if (user == null) {
                logger.error("通知失败：用户不存在 userId={}", userId);
                return;
            }

            String mobile = user.getMobile();
            if (mobile == null || mobile.isEmpty()) {
                logger.warn("用户 {} 没有手机号，跳过短信通知", userId);
            } else {
                // 短信通知参数：{1}订单号 {2}快递员姓名
                String[] params = new String[]{
                    orderId.toString(),
                    courierName != null ? courierName : "快递员"
                };
                
                notifyService.notifySmsTemplate(mobile, NotifyType.COURIER_DELIVER, params);
                logger.info("已发送配送完成通知短信：userId={}, orderId={}", userId, orderId);
            }

            // TODO: 微信模板消息通知
            
        } catch (Exception e) {
            logger.error("发送配送完成通知失败：userId={}, orderId={}, error={}", userId, orderId, e.getMessage(), e);
        }
    }

    /**
     * 通知快递员：配送超时处罚
     * 
     * @param courierId 快递员用户ID
     * @param orderId 订单ID
     * @param timeoutCount 累计超时次数
     * @param creditDeduct 扣除的信用分
     */
    @Async
    public void notifyCourierTimeout(Integer courierId, Integer orderId, Integer timeoutCount, Integer creditDeduct) {
        try {
            LitemallUser user = userService.findById(courierId);
            if (user == null) {
                logger.error("通知失败：快递员不存在 courierId={}", courierId);
                return;
            }

            String mobile = user.getMobile();
            if (mobile == null || mobile.isEmpty()) {
                logger.warn("快递员 {} 没有手机号，跳过短信通知", courierId);
            } else {
                // 短信通知参数：{1}订单号 {2}扣除积分 {3}累计超时次数
                String[] params = new String[]{
                    orderId.toString(),
                    creditDeduct.toString(),
                    timeoutCount.toString()
                };
                
                notifyService.notifySmsTemplate(mobile, NotifyType.COURIER_TIMEOUT, params);
                logger.info("已发送超时通知短信：courierId={}, orderId={}, timeoutCount={}", 
                    courierId, orderId, timeoutCount);
            }

            // TODO: 微信模板消息通知
            
        } catch (Exception e) {
            logger.error("发送超时通知失败：courierId={}, orderId={}, error={}", 
                courierId, orderId, e.getMessage(), e);
        }
    }

    /**
     * 通知快递员：提现到账
     * 
     * @param courierId 快递员用户ID
     * @param withdrawSn 提现单号
     * @param amount 到账金额
     */
    @Async
    public void notifyCourierWithdrawSuccess(Integer courierId, String withdrawSn, String amount) {
        try {
            LitemallUser user = userService.findById(courierId);
            if (user == null) {
                logger.error("通知失败：快递员不存在 courierId={}", courierId);
                return;
            }

            String mobile = user.getMobile();
            if (mobile == null || mobile.isEmpty()) {
                logger.warn("快递员 {} 没有手机号，跳过短信通知", courierId);
            } else {
                // 短信通知参数：{1}提现单号 {2}到账金额
                String[] params = new String[]{
                    withdrawSn,
                    amount
                };
                
                notifyService.notifySmsTemplate(mobile, NotifyType.COURIER_WITHDRAW, params);
                logger.info("已发送提现到账通知短信：courierId={}, withdrawSn={}, amount={}", 
                    courierId, withdrawSn, amount);
            }

            // TODO: 微信模板消息通知
            
        } catch (Exception e) {
            logger.error("发送提现通知失败：courierId={}, withdrawSn={}, error={}", 
                courierId, withdrawSn, e.getMessage(), e);
        }
    }

    /**
     * 通知快递员：提现失败
     * 
     * @param courierId 快递员用户ID
     * @param withdrawSn 提现单号
     * @param amount 提现金额
     * @param failReason 失败原因
     */
    @Async
    public void notifyCourierWithdrawFailed(Integer courierId, String withdrawSn, String amount, String failReason) {
        try {
            LitemallUser user = userService.findById(courierId);
            if (user == null) {
                logger.error("通知失败：快递员不存在 courierId={}", courierId);
                return;
            }

            String mobile = user.getMobile();
            if (mobile == null || mobile.isEmpty()) {
                logger.warn("快递员 {} 没有手机号，跳过短信通知", courierId);
                return;
            }

            // 发送简单短信通知（非模板）
            String message = String.format("您的提现申请（单号:%s，金额:%s元）处理失败，原因：%s。如有疑问请联系客服。", 
                withdrawSn, amount, failReason);
            
            notifyService.notifySms(mobile, message);
            logger.info("已发送提现失败通知短信：courierId={}, withdrawSn={}", courierId, withdrawSn);

        } catch (Exception e) {
            logger.error("发送提现失败通知异常：courierId={}, withdrawSn={}, error={}", 
                courierId, withdrawSn, e.getMessage(), e);
        }
    }
}
