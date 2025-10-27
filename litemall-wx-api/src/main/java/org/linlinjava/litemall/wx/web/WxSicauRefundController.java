package org.linlinjava.litemall.wx.web;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.SicauOrderRefund;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.SicauOrderRefundService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 川农互评退款控制器（Wx端）
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/wx/sicau/refund")
public class WxSicauRefundController {

    @Resource
    private SicauOrderRefundService refundService;
    
    @Resource
    private LitemallOrderService orderService;

    /**
     * 发起退款申请
     * 
     * @param userId 登录用户ID
     * @param refund 退款信息（需包含 orderId, refundAmount, refundReason, refundType 等）
     * @return 申请结果
     */
    @PostMapping("apply")
    public Object apply(@LoginUser Integer userId, @RequestBody SicauOrderRefund refund) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (refund == null || refund.getOrderId() == null || refund.getRefundAmount() == null) {
            return ResponseUtil.badArgument("订单ID和退款金额不能为空");
        }
        
        // 检查该订单是否已有退款记录
        SicauOrderRefund existing = refundService.findByOrderId(refund.getOrderId());
        if (existing != null) {
            return ResponseUtil.fail(600, "该订单已申请退款，请勿重复提交");
        }
        
        // 调用service创建退款记录
        int rows = refundService.createRefund(
            refund.getOrderId(),
            refund.getRefundAmount(),
            refund.getRefundReason(),
            refund.getRefundType() != null ? refund.getRefundType() : (byte) 1
        );
        
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        
        // 返回创建的退款记录
        SicauOrderRefund created = refundService.findByOrderId(refund.getOrderId());
        return ResponseUtil.ok(created);
    }

    /**
     * 查询退款详情（根据订单ID）
     * 
     * @param userId 登录用户ID
     * @param orderId 订单ID
     * @return 退款详情
     */
    @GetMapping("order/{orderId}")
    public Object byOrder(@LoginUser Integer userId, @PathVariable Integer orderId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }
        
        // 权限校验：检查订单是否属于当前用户
        LitemallOrder order = orderService.findById(orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue("订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            return ResponseUtil.unauthz();
        }
        
        SicauOrderRefund refund = refundService.findByOrderId(orderId);
        if (refund == null) {
            return ResponseUtil.ok(null); // 订单无退款记录
        }
        
        return ResponseUtil.ok(refund);
    }

    /**
     * 根据退款单号查询退款详情
     * 
     * @param userId 登录用户ID
     * @param refundSn 退款单号
     * @return 退款详情
     */
    @GetMapping("sn/{refundSn}")
    public Object byRefundSn(@LoginUser Integer userId, @PathVariable String refundSn) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (refundSn == null || refundSn.isEmpty()) {
            return ResponseUtil.badArgument();
        }
        
        SicauOrderRefund refund = refundService.findByRefundSn(refundSn);
        if (refund == null) {
            return ResponseUtil.badArgumentValue("退款单号不存在");
        }
        
        // 权限校验：检查订单是否属于当前用户
        LitemallOrder order = orderService.findById(refund.getOrderId());
        if (order == null) {
            return ResponseUtil.badArgumentValue("关联订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            return ResponseUtil.unauthz();
        }
        
        return ResponseUtil.ok(refund);
    }

    /**
     * 撤销退款申请
     * 
     * @param userId 登录用户ID
     * @param refundId 退款记录ID
     * @return 操作结果
     */
    @PostMapping("cancel/{refundId}")
    public Object cancel(@LoginUser Integer userId, @PathVariable Integer refundId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (refundId == null) {
            return ResponseUtil.badArgument();
        }
        
        SicauOrderRefund refund = refundService.findById(refundId);
        if (refund == null) {
            return ResponseUtil.badArgumentValue("退款记录不存在");
        }
        
        // 权限校验：检查订单是否属于当前用户
        LitemallOrder order = orderService.findById(refund.getOrderId());
        if (order == null) {
            return ResponseUtil.badArgumentValue("关联订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            return ResponseUtil.unauthz();
        }
        
        // 仅"申请中"状态可撤销（status = 0）
        if (refund.getRefundStatus() != 0) {
            return ResponseUtil.fail(600, "当前状态不允许撤销");
        }
        
        // 更新为已取消（status = 3）
        int rows = refundService.updateRefundStatus(refundId, (byte) 3);
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        
        return ResponseUtil.ok();
    }
}
