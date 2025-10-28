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
import java.util.List;
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

    /**
     * 查询待配送订单列表（Story 4.2）
     * 
     * GET /wx/courier/pendingOrders
     * 
     * 返回数据示例：
     * {
     *   "errno": 0,
     *   "data": [
     *     {
     *       "orderId": 123,
     *       "orderSn": "20250101123456",
     *       "consignee": "张三",
     *       "mobile": "138****8000",
     *       "address": "雅安本部 7舍A栋 501",
     *       "buildingName": "7舍A栋",
     *       "distance": 0.85,
     *       "fee": 2.0,
     *       "actualPrice": 29.90,
     *       "addTime": "2025-01-01T12:34:56"
     *     }
     *   ]
     * }
     */
    @GetMapping("/pendingOrders")
    public Object getPendingOrders(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        try {
            List<Map<String, Object>> orders = courierService.queryPendingOrders(userId);
            logger.info("快递员 " + userId + " 查询待配送订单，共 " + orders.size() + " 个");
            return ResponseUtil.ok(orders);
        } catch (RuntimeException e) {
            logger.warn("快递员 " + userId + " 查询待配送订单失败: " + e.getMessage());
            return ResponseUtil.fail(502, e.getMessage());
        }
    }

    /**
     * 接单（Story 4.3）
     * 
     * POST /wx/courier/acceptOrder
     * {
     *   "orderId": 123
     * }
     * 
     * 返回数据：
     * {
     *   "errno": 0,
     *   "data": {
     *     "pickupCode": "3857",
     *     "orderSn": "20251028001",
     *     "consignee": "张三",
     *     "mobile": "138****8000",
     *     "address": "7舍A栋 501",
     *     "shipTime": "2025-10-28T15:30:00"
     *   }
     * }
     */
    @PostMapping("/acceptOrder")
    public Object acceptOrder(@LoginUser Integer userId, @RequestBody Map<String, Integer> body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        Integer orderId = body.get("orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument("订单ID不能为空");
        }

        try {
            Map<String, Object> result = courierService.acceptOrder(userId, orderId);
            logger.info("快递员 " + userId + " 接单成功: orderId=" + orderId + 
                       ", pickupCode=" + result.get("pickupCode"));
            return ResponseUtil.ok(result);
        } catch (RuntimeException e) {
            logger.warn("快递员 " + userId + " 接单失败: " + e.getMessage());
            return ResponseUtil.fail(503, e.getMessage());
        }
    }

    /**
     * 完成配送（Story 4.3）
     * 
     * POST /wx/courier/completeOrder
     * {
     *   "orderId": 123,
     *   "pickupCode": "3857"
     * }
     * 
     * 返回数据：
     * {
     *   "errno": 0,
     *   "data": {
     *     "income": 4.0,
     *     "distance": 1.5,
     *     "orderSn": "20251028001",
     *     "totalOrders": 5,
     *     "totalIncome": 20.0
     *   }
     * }
     */
    @PostMapping("/completeOrder")
    public Object completeOrder(@LoginUser Integer userId, @RequestBody Map<String, Object> body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        Integer orderId = (Integer) body.get("orderId");
        String pickupCode = (String) body.get("pickupCode");
        
        if (orderId == null) {
            return ResponseUtil.badArgument("订单ID不能为空");
        }
        if (pickupCode == null || pickupCode.trim().isEmpty()) {
            return ResponseUtil.badArgument("取件码不能为空");
        }

        try {
            Map<String, Object> result = courierService.completeOrder(userId, orderId, pickupCode);
            logger.info("快递员 " + userId + " 完成配送: orderId=" + orderId + 
                       ", income=" + result.get("income"));
            return ResponseUtil.ok(result);
        } catch (RuntimeException e) {
            logger.warn("快递员 " + userId + " 完成配送失败: " + e.getMessage());
            return ResponseUtil.fail(504, e.getMessage());
        }
    }
}
