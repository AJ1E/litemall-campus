package org.linlinjava.litemall.admin.web;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauOrderRefund;
import org.linlinjava.litemall.db.service.SicauOrderRefundService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 川农互评退款管理控制器（Admin端）
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/admin/sicau/refund")
public class AdminSicauRefundController {

    @Resource
    private SicauOrderRefundService refundService;

    /**
     * 退款列表（管理后台）
     * 
     * @param refundStatus 退款状态（可选：0-申请中，1-同意退款，2-已退款，3-已取消）
     * @param page 页码
     * @param limit 每页数量
     * @return 退款列表
     */
    @GetMapping("list")
    public Object list(@RequestParam(required = false) Byte refundStatus,
                      @RequestParam(defaultValue = "1") Integer page,
                      @RequestParam(defaultValue = "10") Integer limit) {
        List<SicauOrderRefund> refunds = refundService.queryByStatus(refundStatus, page, limit);
        return ResponseUtil.okList(refunds);
    }

    /**
     * 退款详情
     * 
     * @param id 退款记录ID
     * @return 退款详情
     */
    @GetMapping("detail/{id}")
    public Object detail(@PathVariable Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        SicauOrderRefund refund = refundService.findById(id);
        if (refund == null) {
            return ResponseUtil.badArgumentValue("退款记录不存在");
        }
        
        return ResponseUtil.ok(refund);
    }

    /**
     * 审核退款申请（同意或拒绝）
     * 
     * @param id 退款记录ID
     * @param status 新状态（1-同意退款，3-拒绝/已取消）
     * @param adminNote 管理员备注
     * @return 操作结果
     */
    @PostMapping("review/{id}")
    public Object review(@PathVariable Integer id,
                        @RequestParam Byte status,
                        @RequestParam(required = false) String adminNote) {
        if (id == null || status == null) {
            return ResponseUtil.badArgument("参数不完整");
        }
        
        SicauOrderRefund refund = refundService.findById(id);
        if (refund == null) {
            return ResponseUtil.badArgumentValue("退款记录不存在");
        }
        
        // 仅申请中状态可审核（status = 0）
        if (refund.getRefundStatus() != 0) {
            return ResponseUtil.fail(600, "该退款申请已处理，不可重复审核");
        }
        
        // 更新状态并保存管理员备注
        int rows;
        if (adminNote != null && !adminNote.trim().isEmpty()) {
            rows = refundService.updateRefundStatusWithNote(id, status, adminNote);
        } else {
            rows = refundService.updateRefundStatus(id, status);
        }
        
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        
        return ResponseUtil.ok();
    }

    /**
     * 确认退款完成（将状态从"同意退款"改为"已退款"）
     * 
     * @param id 退款记录ID
     * @param refundId 第三方退款ID（如微信退款单号）
     * @return 操作结果
     */
    @PostMapping("confirm/{id}")
    public Object confirm(@PathVariable Integer id,
                         @RequestParam(required = false) String refundId) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        SicauOrderRefund refund = refundService.findById(id);
        if (refund == null) {
            return ResponseUtil.badArgumentValue("退款记录不存在");
        }
        
        // 仅"同意退款"状态可确认（status = 1）
        if (refund.getRefundStatus() != 1) {
            return ResponseUtil.fail(600, "当前状态不允许确认退款");
        }
        
        // 更新为已退款（status = 2），并记录第三方退款ID
        int rows = refundService.confirmRefund(id, (byte) 2, refundId);
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        
        return ResponseUtil.ok();
    }

    /**
     * 删除退款记录（逻辑删除）
     * 
     * @param id 退款记录ID
     * @return 操作结果
     */
    @DeleteMapping("delete/{id}")
    public Object delete(@PathVariable Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        int rows = refundService.deleteById(id);
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        
        return ResponseUtil.ok();
    }
}
