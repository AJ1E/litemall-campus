package org.linlinjava.litemall.wx.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauCourier;
import org.linlinjava.litemall.db.service.SicauCourierService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * 快递员相关接口
 */
@RestController
@RequestMapping("/wx/courier")
@Validated
public class WxCourierController {
    private final Log logger = LogFactory.getLog(WxCourierController.class);

    @Autowired
    private SicauCourierService courierService;

    /**
     * 申请成为快递员
     * 
     * POST /wx/courier/apply
     * {
     *   "applyReason": "我是大三学生，课余时间充足，想通过配送赚取生活费"
     * }
     */
    @PostMapping("/apply")
    public Object apply(@LoginUser Integer userId, @RequestBody Map<String, String> body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        String applyReason = body.get("applyReason");
        if (applyReason == null || applyReason.trim().isEmpty()) {
            return ResponseUtil.badArgument("申请理由不能为空");
        }

        if (applyReason.length() > 200) {
            return ResponseUtil.badArgument("申请理由不能超过200字");
        }

        try {
            SicauCourier courier = courierService.apply(userId, applyReason);
            logger.info("用户 " + userId + " 申请成为快递员");
            
            Map<String, Object> data = new HashMap<>();
            data.put("id", courier.getId());
            data.put("status", courier.getStatus());
            data.put("message", "申请成功，请等待管理员审核");
            
            return ResponseUtil.ok(data);
        } catch (RuntimeException e) {
            logger.warn("用户 " + userId + " 申请快递员失败: " + e.getMessage());
            return ResponseUtil.fail(501, e.getMessage());
        }
    }

    /**
     * 查询我的快递员信息
     * 
     * GET /wx/courier/info
     */
    @GetMapping("/info")
    public Object getInfo(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        SicauCourier courier = courierService.findByUserId(userId);
        
        if (courier == null) {
            Map<String, Object> data = new HashMap<>();
            data.put("hasCourier", false);
            return ResponseUtil.ok(data);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("hasCourier", true);
        data.put("status", courier.getStatus());
        data.put("applyReason", courier.getApplyReason());
        data.put("rejectReason", courier.getRejectReason());
        data.put("totalOrders", courier.getTotalOrders());
        data.put("totalIncome", courier.getTotalIncome());
        data.put("timeoutCount", courier.getTimeoutCount());
        data.put("complaintCount", courier.getComplaintCount());
        data.put("applyTime", courier.getApplyTime());
        data.put("approveTime", courier.getApproveTime());

        // 状态描述
        String statusDesc;
        switch (courier.getStatus()) {
            case 0:
                statusDesc = "待审核";
                break;
            case 1:
                statusDesc = "已通过";
                break;
            case 2:
                statusDesc = "已拒绝";
                break;
            case 3:
                statusDesc = "已取消资格";
                break;
            default:
                statusDesc = "未知状态";
        }
        data.put("statusDesc", statusDesc);

        return ResponseUtil.ok(data);
    }

    /**
     * 查询快递员状态（用于页面判断是否显示快递员功能）
     * 
     * GET /wx/courier/status
     */
    @GetMapping("/status")
    public Object getStatus(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        boolean isApprovedCourier = courierService.isApprovedCourier(userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("isApprovedCourier", isApprovedCourier);
        
        return ResponseUtil.ok(data);
    }
}
