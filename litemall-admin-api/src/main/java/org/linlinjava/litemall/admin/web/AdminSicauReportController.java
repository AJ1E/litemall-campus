package org.linlinjava.litemall.admin.web;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauReport;
import org.linlinjava.litemall.db.service.SicauReportService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 川农互评举报管理控制器（Admin端）
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/admin/sicau/report")
public class AdminSicauReportController {

    @Resource
    private SicauReportService reportService;

    /**
     * 举报列表（管理后台）
     * 
     * @param status 举报状态（可选：0-待处理，1-处理中，2-已处理，3-已驳回）
     * @param type 举报类型（可选：1-用户举报，2-订单举报，3-评价举报）
     * @param page 页码
     * @param limit 每页数量
     * @return 举报列表
     */
    @GetMapping("list")
    public Object list(@RequestParam(required = false) Byte status,
                      @RequestParam(required = false) Byte type,
                      @RequestParam(defaultValue = "1") Integer page,
                      @RequestParam(defaultValue = "10") Integer limit) {
        List<SicauReport> reports = reportService.queryAllReports(status, type, page, limit);
        return ResponseUtil.okList(reports);
    }

    /**
     * 举报详情
     * 
     * @param id 举报ID
     * @return 举报详情
     */
    @GetMapping("detail/{id}")
    public Object detail(@PathVariable Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        SicauReport report = reportService.findById(id);
        if (report == null) {
            return ResponseUtil.badArgumentValue("举报记录不存在");
        }
        
        return ResponseUtil.ok(report);
    }

    /**
     * 处理举报（更新状态和处理结果）
     * 
     * @param id 举报ID
     * @param status 新状态（1-处理中，2-已处理，3-已驳回）
     * @param result 处理结果描述
     * @return 操作结果
     */
    @PostMapping("handle/{id}")
    public Object handle(@PathVariable Integer id,
                        @RequestParam Byte status,
                        @RequestParam(required = false) String result) {
        if (id == null || status == null) {
            return ResponseUtil.badArgument("参数不完整");
        }
        
        SicauReport report = reportService.findById(id);
        if (report == null) {
            return ResponseUtil.badArgumentValue("举报记录不存在");
        }
        
        // 使用 handleReport 方法，传入管理员ID（需要从登录上下文获取）
        // 这里暂时传 null，后续可以通过 @LoginAdmin 注解获取
        int rows = reportService.handleReport(id, null, result, status);
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        
        return ResponseUtil.ok();
    }

    /**
     * 删除举报记录（逻辑删除）
     * 
     * @param id 举报ID
     * @return 操作结果
     */
    @DeleteMapping("delete/{id}")
    public Object delete(@PathVariable Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        int rows = reportService.deleteById(id);
        if (rows <= 0) {
            return ResponseUtil.fail();
        }
        
        return ResponseUtil.ok();
    }
}
