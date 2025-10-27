package org.linlinjava.litemall.wx.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 微信支付服务
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Service
public class WxOrderPayService {
    
    private static final Logger logger = LoggerFactory.getLogger(WxOrderPayService.class);
    
    @Autowired
    private WxPayService wxPayService;
    
    @Autowired
    private WxMaService wxMaService;
    
    @Autowired
    private LitemallOrderService orderService;
    
    @Autowired
    private LitemallUserService userService;
    
    @Autowired
    private NotifyService notifyService;
    
    @Value("${litemall.wx.pay-notify-url:https://api.example.com/wx/order/payNotify}")
    private String payNotifyUrl;
    
    /**
     * 创建微信支付订单
     * 
     * @param order 订单对象
     * @param openid 用户openid
     * @return 支付参数（给小程序端调起支付）
     */
    public WxPayMpOrderResult createPayOrder(LitemallOrder order, String openid) throws WxPayException {
        // 1. 构建微信支付请求
        WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
        request.setOutTradeNo(order.getOrderSn()); // 商户订单号
        request.setOpenid(openid);
        request.setBody("校园闲置物品-" + order.getOrderSn());
        
        // 转为分（微信支付金额单位）
        int totalFee = order.getActualPrice().multiply(new BigDecimal("100")).intValue();
        request.setTotalFee(totalFee);
        
        request.setSpbillCreateIp("127.0.0.1");
        request.setNotifyUrl(payNotifyUrl); // 支付回调地址
        request.setTradeType("JSAPI");
        
        // 2. 调用微信统一下单接口
        WxPayMpOrderResult result = wxPayService.createOrder(request);
        
        logger.info("微信支付订单创建成功: orderSn={}, totalFee={}", order.getOrderSn(), totalFee);
        
        return result;
    }
    
    /**
     * 处理支付回调
     * 
     * @param xmlData 微信回调XML数据
     * @return 是否处理成功
     */
    @Transactional
    public boolean handlePayNotify(String xmlData) {
        try {
            // 1. 验证签名并解析回调数据
            WxPayOrderNotifyResult notifyResult = wxPayService.parseOrderNotifyResult(xmlData);
            
            // 2. 查询订单
            String orderSn = notifyResult.getOutTradeNo();
            LitemallOrder order = orderService.findBySn(orderSn);
            
            if (order == null) {
                logger.error("支付回调订单不存在: {}", orderSn);
                return false;
            }
            
            // 3. 检查订单状态（避免重复处理）
            if (order.getOrderStatus() != 101) { // 101=待付款
                logger.warn("订单状态不是待付款，跳过处理: orderSn={}, status={}", orderSn, order.getOrderStatus());
                return true; // 返回成功，避免微信重复通知
            }
            
            // 4. 校验订单金额
            int dbAmount = order.getActualPrice().multiply(new BigDecimal("100")).intValue();
            int wxAmount = notifyResult.getTotalFee();
            
            if (dbAmount != wxAmount) {
                logger.error("订单金额校验失败: orderSn={}, dbAmount={}, wxAmount={}", orderSn, dbAmount, wxAmount);
                return false;
            }
            
            // 5. 更新订单状态
            order.setOrderStatus((short) 201); // 201=待发货
            order.setPayTime(LocalDateTime.now());
            orderService.updateWithOptimisticLocker(order);
            
            // 6. 扣减商品库存（如果有库存管理）
            // TODO: 实现库存扣减逻辑
            
            // 7. 推送通知给卖家
            LitemallUser seller = userService.findById(order.getSellerId());
            if (seller != null && seller.getMobile() != null) {
                notifyService.notifySmsTemplate(
                    seller.getMobile(), 
                    NotifyType.PAY_SUCCEED, 
                    new String[]{order.getOrderSn()}
                );
            }
            
            logger.info("支付回调处理成功: orderSn={}", orderSn);
            return true;
            
        } catch (WxPayException e) {
            logger.error("支付回调签名验证失败", e);
            return false;
        } catch (Exception e) {
            logger.error("支付回调处理异常", e);
            return false;
        }
    }
    
    /**
     * 发起退款
     * 
     * @param order 订单对象
     * @param refundAmount 退款金额
     * @param refundReason 退款原因
     * @return 退款是否成功
     */
    @Transactional
    public boolean refund(LitemallOrder order, BigDecimal refundAmount, String refundReason) {
        try {
            // 构建退款请求
            com.github.binarywang.wxpay.bean.request.WxPayRefundRequest request = 
                new com.github.binarywang.wxpay.bean.request.WxPayRefundRequest();
            
            request.setOutTradeNo(order.getOrderSn());
            request.setOutRefundNo("RF" + order.getOrderSn()); // 退款单号
            
            int totalFee = order.getActualPrice().multiply(new BigDecimal("100")).intValue();
            int refundFee = refundAmount.multiply(new BigDecimal("100")).intValue();
            
            request.setTotalFee(totalFee);
            request.setRefundFee(refundFee);
            request.setRefundDesc(refundReason);
            request.setNotifyUrl(payNotifyUrl.replace("payNotify", "refundNotify"));
            
            // 调用微信退款接口
            com.github.binarywang.wxpay.bean.result.WxPayRefundResult result = 
                wxPayService.refund(request);
            
            logger.info("微信退款成功: orderSn={}, refundAmount={}", order.getOrderSn(), refundAmount);
            return true;
            
        } catch (WxPayException e) {
            logger.error("微信退款失败: orderSn=" + order.getOrderSn(), e);
            return false;
        }
    }
}
