package org.linlinjava.litemall.wx.web;

import org.linlinjava.litemall.wx.service.WxOrderPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 微信支付回调处理
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/wx/pay")
public class WxPayNotifyController {
    
    private static final Logger logger = LoggerFactory.getLogger(WxPayNotifyController.class);
    
    @Autowired
    private WxOrderPayService payService;
    
    /**
     * 微信支付成功回调
     * 
     * @param xmlData 微信POST的XML数据
     * @return 返回给微信的响应
     */
    @PostMapping("/payNotify")
    public String payNotify(@RequestBody String xmlData) {
        logger.info("收到微信支付回调");
        
        try {
            boolean success = payService.handlePayNotify(xmlData);
            
            if (success) {
                // 返回成功响应给微信（XML格式）
                return buildSuccessXml();
            } else {
                // 返回失败响应，微信会重复通知
                return buildFailXml("订单处理失败");
            }
            
        } catch (Exception e) {
            logger.error("支付回调处理异常", e);
            return buildFailXml("系统异常");
        }
    }
    
    /**
     * 微信退款成功回调
     * 
     * @param xmlData 微信POST的XML数据
     * @return 返回给微信的响应
     */
    @PostMapping("/refundNotify")
    public String refundNotify(@RequestBody String xmlData) {
        logger.info("收到微信退款回调");
        
        try {
            // TODO: 实现退款回调处理逻辑
            // 1. 解析XML数据
            // 2. 验证签名
            // 3. 更新退款记录状态
            
            return buildSuccessXml();
            
        } catch (Exception e) {
            logger.error("退款回调处理异常", e);
            return buildFailXml("系统异常");
        }
    }
    
    /**
     * 构建成功响应XML
     */
    private String buildSuccessXml() {
        return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
    }
    
    /**
     * 构建失败响应XML
     */
    private String buildFailXml(String msg) {
        return "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[" + msg + "]]></return_msg></xml>";
    }
}
