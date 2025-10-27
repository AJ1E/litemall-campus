package org.linlinjava.litemall.wx.web;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauReport;
import org.linlinjava.litemall.db.service.SicauReportService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 川农互评举报控制器（Wx端）
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/wx/sicau/report")
public class WxSicauReportController {

    @Resource
    private SicauReportService reportService;

    /**
     * 提交举报
     * 
     * @param userId 登录用户ID
     * @param report 举报信息
     * @return 提交结果
     */
    @PostMapping("submit")
    public Object submit(@LoginUser Integer userId, @RequestBody SicauReport report) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (report == null || report.getReportedId() == null || report.getType() == null) {
            return ResponseUtil.badArgument("举报对象和类型不能为空");
        }
        
        // 设置举报人
        report.setReporterId(userId);
        
        int rows = reportService.addReport(report);
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        
        return ResponseUtil.ok(report);
    }

    /**
     * 查询我的举报记录
     * 
     * @param userId 登录用户ID
     * @param status 举报状态（可选：0-待处理，1-处理中，2-已处理，3-已驳回）
     * @param page 页码
     * @param limit 每页数量
     * @return 举报列表
     */
    @GetMapping("my")
    public Object myReports(@LoginUser Integer userId,
                           @RequestParam(required = false) Byte status,
                           @RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "10") Integer limit) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        
        List<SicauReport> reports = reportService.queryByReporter(userId, status, page, limit);
        return ResponseUtil.okList(reports);
    }

    /**
     * 查询针对我的举报（被举报记录）
     * 
     * @param userId 登录用户ID
     * @param status 举报状态（可选）
     * @param page 页码
     * @param limit 每页数量
     * @return 举报列表
     */
    @GetMapping("against-me")
    public Object againstMe(@LoginUser Integer userId,
                           @RequestParam(required = false) Byte status,
                           @RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "10") Integer limit) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        
        List<SicauReport> reports = reportService.queryByReported(userId, status, page, limit);
        return ResponseUtil.okList(reports);
    }

    /**
     * 查询举报详情
     * 
     * @param userId 登录用户ID
     * @param id 举报ID
     * @return 举报详情
     */
    @GetMapping("detail/{id}")
    public Object detail(@LoginUser Integer userId, @PathVariable Integer id) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        SicauReport report = reportService.findById(id);
        if (report == null) {
            return ResponseUtil.badArgumentValue("举报记录不存在");
        }
        
        // 仅允许举报人或被举报人查看
        if (!userId.equals(report.getReporterId()) && !userId.equals(report.getReportedId())) {
            return ResponseUtil.unauthz();
        }
        
        return ResponseUtil.ok(report);
    }
}
