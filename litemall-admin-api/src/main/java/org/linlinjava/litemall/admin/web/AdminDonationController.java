package org.linlinjava.litemall.admin.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.SicauDonation;
import org.linlinjava.litemall.db.service.SicauDonationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 捐赠审核接口
 */
@RestController
@RequestMapping("/admin/donation")
@Validated
public class AdminDonationController {
    private final Log logger = LogFactory.getLog(AdminDonationController.class);
    
    @Autowired
    private SicauDonationService donationService;
    
    /**
     * 待审核捐赠列表
     * @param page 页码
     * @param size 每页数量
     * @return 待审核捐赠列表
     */
    @GetMapping("/pending")
    public Object getPendingList(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "10") Integer size,
                                  @Sort @RequestParam(defaultValue = "add_time") String sort,
                                  @Order @RequestParam(defaultValue = "asc") String order) {
        List<SicauDonation> donations = donationService.queryPendingAudits(page, size);
        
        // 统计待审核数量
        int total = donationService.countByStatus((byte) 0);
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", donations);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        
        return ResponseUtil.ok(data);
    }
    
    /**
     * 查询所有捐赠记录（支持状态筛选）
     * @param status 状态筛选（0-待审核, 1-审核通过, 2-审核拒绝, 3-已完成）
     * @param page 页码
     * @param size 每页数量
     * @return 捐赠列表
     */
    @GetMapping("/list")
    public Object getList(@RequestParam(required = false) Byte status,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size) {
        List<SicauDonation> donations;
        int total;
        
        if (status != null) {
            donations = donationService.queryByStatus(status, page, size);
            total = donationService.countByStatus(status);
        } else {
            // 查询所有状态（通过传入null，Service层会处理）
            donations = donationService.queryByStatus(null, page, size);
            total = donationService.countByStatus(null);
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", donations);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        
        return ResponseUtil.ok(data);
    }
    
    /**
     * 查询捐赠详情
     * @param id 捐赠ID
     * @return 捐赠详情
     */
    @GetMapping("/detail")
    public Object getDetail(@RequestParam @NotNull Integer id) {
        SicauDonation donation = donationService.findById(id);
        if (donation == null) {
            return ResponseUtil.badArgumentValue();
        }
        
        return ResponseUtil.ok(donation);
    }
    
    /**
     * 审核捐赠
     * @param request 审核请求
     * @return 审核结果
     */
    @PostMapping("/audit")
    public Object audit(@RequestBody AuditRequest request) {
        // 获取管理员ID（从 Subject 中获取）
        Integer adminId = getAdminId();
        if (adminId == null) {
            return ResponseUtil.unauthz();
        }
        
        try {
            donationService.audit(
                request.getDonationId(),
                adminId,
                request.getPass(),
                request.getRejectReason()
            );
            
            String message = request.getPass() ? "审核通过" : "审核拒绝";
            logger.info("管理员 " + adminId + " 审核捐赠 " + request.getDonationId() + ": " + message);
            
            return ResponseUtil.ok(message);
        } catch (Exception e) {
            logger.error("审核捐赠失败", e);
            return ResponseUtil.fail(500, e.getMessage());
        }
    }
    
    /**
     * 确认收货（完成捐赠）
     * @param request 确认收货请求
     * @return 确认结果
     */
    @PostMapping("/finish")
    public Object finish(@RequestBody FinishRequest request) {
        try {
            donationService.finish(request.getDonationId());
            
            logger.info("捐赠 " + request.getDonationId() + " 已确认收货");
            
            return ResponseUtil.ok("收货确认成功，用户已获得积分奖励");
        } catch (Exception e) {
            logger.error("确认收货失败", e);
            return ResponseUtil.fail(500, e.getMessage());
        }
    }
    
    /**
     * 捐赠统计数据
     * @return 统计数据
     */
    @GetMapping("/statistics")
    public Object getStatistics() {
        int totalCount = donationService.countByStatus(null);
        int pendingCount = donationService.countByStatus((byte) 0);
        int approvedCount = donationService.countByStatus((byte) 1);
        int rejectedCount = donationService.countByStatus((byte) 2);
        int finishedCount = donationService.countByStatus((byte) 3);
        
        Map<String, Object> data = new HashMap<>();
        data.put("total", totalCount);
        data.put("pending", pendingCount);
        data.put("approved", approvedCount);
        data.put("rejected", rejectedCount);
        data.put("finished", finishedCount);
        
        // 计算通过率
        if (totalCount > 0) {
            double approveRate = (double) (approvedCount + finishedCount) / totalCount;
            data.put("approveRate", Math.round(approveRate * 10000.0) / 10000.0);
        } else {
            data.put("approveRate", 0.0);
        }
        
        return ResponseUtil.ok(data);
    }
    
    /**
     * 获取当前管理员ID
     * TODO: 从 Shiro Subject 中获取
     */
    private Integer getAdminId() {
        // 暂时返回固定值，实际应从 Shiro Subject 中获取
        // Subject subject = SecurityUtils.getSubject();
        // return (Integer) subject.getPrincipal();
        return 1; // 默认管理员ID
    }
    
    /**
     * 审核请求体
     */
    public static class AuditRequest {
        @NotNull(message = "捐赠ID不能为空")
        private Integer donationId;
        
        @NotNull(message = "审核结果不能为空")
        private Boolean pass;
        
        private String rejectReason;
        
        public Integer getDonationId() {
            return donationId;
        }
        
        public void setDonationId(Integer donationId) {
            this.donationId = donationId;
        }
        
        public Boolean getPass() {
            return pass;
        }
        
        public void setPass(Boolean pass) {
            this.pass = pass;
        }
        
        public String getRejectReason() {
            return rejectReason;
        }
        
        public void setRejectReason(String rejectReason) {
            this.rejectReason = rejectReason;
        }
    }
    
    /**
     * 确认收货请求体
     */
    public static class FinishRequest {
        @NotNull(message = "捐赠ID不能为空")
        private Integer donationId;
        
        public Integer getDonationId() {
            return donationId;
        }
        
        public void setDonationId(Integer donationId) {
            this.donationId = donationId;
        }
    }
}
