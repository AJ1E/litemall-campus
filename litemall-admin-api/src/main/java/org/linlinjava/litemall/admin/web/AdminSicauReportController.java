package org.linlinjava.litemall.admin.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.linlinjava.litemall.admin.annotation.RequiresPermissionsDesc;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.SicauReport;
import org.linlinjava.litemall.db.domain.SicauReportWithBLOBs;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.SicauOrderRefundService;
import org.linlinjava.litemall.db.service.SicauReportService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 川农互评举报管理控制器（Admin端）
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/admin/sicau/report")
public class AdminSicauReportController {
    private final Log logger = LogFactory.getLog(AdminSicauReportController.class);

    @Resource
    private SicauReportService reportService;

    @Resource
    private LitemallOrderService orderService;

    @Resource
    private SicauOrderRefundService refundService;

    @Resource
    private org.linlinjava.litemall.admin.service.AdminLogService adminLogService;

    /**
     * 举报列表（管理后台）
     * 
     * @param status 举报状态（可选：0-待处理，1-处理中，2-已处理，3-已驳回）
     * @param type 举报类型（可选：1-用户举报，2-订单举报，3-评价举报）
     * @param page 页码
     * @param limit 每页数量
     * @return 举报列表
     */
    @RequiresPermissions("admin:report:list")
    @RequiresPermissionsDesc(menu = {"举报管理", "举报列表"}, button = "查询")
    @GetMapping("list")
    public Object list(@RequestParam(required = false) Byte status,
                      @RequestParam(required = false) Byte type,
                      @RequestParam(defaultValue = "1") Integer page,
                      @RequestParam(defaultValue = "10") Integer limit) {
        List<SicauReport> reports = reportService.queryAllReports(status, type, page, limit);
        long total = reportService.countAllReports(status, type);
        
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("items", reports);
        data.put("page", page);
        data.put("limit", limit);
        
        return ResponseUtil.ok(data);
    }

    /**
     * 举报详情
     * 
     * @param id 举报ID
     * @return 举报详情
     */
    @RequiresPermissions("admin:report:detail")
    @RequiresPermissionsDesc(menu = {"举报管理", "举报列表"}, button = "详情")
    @GetMapping("detail/{id}")
    public Object detail(@PathVariable Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        SicauReport report = reportService.findById(id);
        if (report == null) {
            return ResponseUtil.badArgumentValue("举报记录不存在");
        }
        
        // 如果是订单举报，附带订单信息
        if (report.getType() == 2 && report.getOrderId() != null) {
            LitemallOrder order = orderService.findById(report.getOrderId());
            Map<String, Object> data = new HashMap<>();
            data.put("report", report);
            data.put("order", order);
            return ResponseUtil.ok(data);
        }
        
        return ResponseUtil.ok(report);
    }

    /**
     * 处理举报（更新状态和处理结果）
     * 
     * @param body 请求体，包含 reportId, handleType, handleResult
     * @return 操作结果
     */
    @RequiresPermissions("admin:report:handle")
    @RequiresPermissionsDesc(menu = {"举报管理", "举报列表"}, button = "处理")
    @PostMapping("handle")
    public Object handle(@RequestBody String body) {
        Integer reportId = JacksonUtil.parseInteger(body, "reportId");
        Integer handleType = JacksonUtil.parseInteger(body, "handleType"); // 1-强制退款, 2-驳回举报, 3-协商
        String handleResult = JacksonUtil.parseString(body, "handleResult");
        
        if (reportId == null || handleType == null) {
            return ResponseUtil.badArgument("参数不完整");
        }
        
        try {
            SicauReport report = reportService.findById(reportId);
            if (report == null) {
                return ResponseUtil.badArgumentValue("举报记录不存在");
            }
            
            // 获取当前管理员信息
            Subject currentUser = SecurityUtils.getSubject();
            LitemallAdmin admin = (LitemallAdmin) currentUser.getPrincipal();
            
            // 1. 根据处理类型执行相应操作
            if (handleType == 1) {
                // 强制退款
                if (report.getOrderId() == null) {
                    return ResponseUtil.fail(400, "该举报不涉及订单，无法退款");
                }
                
                LitemallOrder order = orderService.findById(report.getOrderId());
                if (order == null) {
                    return ResponseUtil.fail(404, "订单不存在");
                }
                
                // 创建退款记录
                int rows = refundService.createRefund(
                    order.getId(), 
                    order.getActualPrice(), 
                    "管理员强制退款：" + handleResult, 
                    (byte) 3 // 3-举报退款
                );
                
                if (rows <= 0) {
                    return ResponseUtil.fail(500, "创建退款记录失败");
                }
                
                // 更新订单状态为已取消
                order.setOrderStatus((short) 103); // 103-已取消
                order.setUpdateTime(LocalDateTime.now());
                orderService.updateWithOptimisticLocker(order);
                
                logger.info("管理员 " + admin.getUsername() + " 强制退款订单: " + order.getOrderSn());
            } else if (handleType == 2) {
                // 驳回举报
                logger.info("管理员 " + admin.getUsername() + " 驳回举报: " + reportId);
            } else if (handleType == 3) {
                // 协商处理
                logger.info("管理员 " + admin.getUsername() + " 协商处理举报: " + reportId);
            }
            
            // 2. 更新举报状态
            int result = reportService.handleReport(reportId, admin.getId(), handleResult, (byte) 2); // 2-已处理
            if (result <= 0) {
                return ResponseUtil.fail(500, "更新举报状态失败");
            }
            
            // 3. 记录操作日志
            Map<String, Object> detail = new HashMap<>();
            detail.put("reportId", reportId);
            detail.put("handleType", handleType == 1 ? "强制退款" : handleType == 2 ? "驳回举报" : "协商处理");
            detail.put("handleResult", handleResult);
            detail.put("orderId", report.getOrderId());
            adminLogService.log(admin.getId(), admin.getUsername(), "handle_report", "report", reportId, detail);
            
            return ResponseUtil.ok();
        } catch (Exception e) {
            logger.error("处理举报失败", e);
            return ResponseUtil.fail(500, "处理举报失败: " + e.getMessage());
        }
    }

    /**
     * 删除举报记录（逻辑删除）
     * 
     * @param id 举报ID
     * @return 操作结果
     */
    @RequiresPermissions("admin:report:delete")
    @RequiresPermissionsDesc(menu = {"举报管理", "举报列表"}, button = "删除")
    @DeleteMapping("delete/{id}")
    public Object delete(@PathVariable Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        try {
            // 获取当前管理员信息
            Subject currentUser = SecurityUtils.getSubject();
            LitemallAdmin admin = (LitemallAdmin) currentUser.getPrincipal();
            
            int rows = reportService.deleteById(id);
            if (rows <= 0) {
                return ResponseUtil.fail();
            }
            
            // 记录操作日志
            Map<String, Object> detail = new HashMap<>();
            detail.put("reportId", id);
            adminLogService.log(admin.getId(), admin.getUsername(), "delete_report", "report", id, detail);
            
            logger.info("管理员 " + admin.getUsername() + " 删除举报记录: " + id);
            
            return ResponseUtil.ok();
        } catch (Exception e) {
            logger.error("删除举报记录失败", e);
            return ResponseUtil.fail(500, "删除举报记录失败");
        }
    }
}
