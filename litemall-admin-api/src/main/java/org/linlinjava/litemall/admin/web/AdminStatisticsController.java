package org.linlinjava.litemall.admin.web;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.linlinjava.litemall.admin.annotation.RequiresPermissionsDesc;
import org.linlinjava.litemall.admin.service.StatisticsService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据统计控制器
 */
@RestController
@RequestMapping("/admin/statistics")
public class AdminStatisticsController {
    
    @Autowired
    private StatisticsService statisticsService;
    
    /**
     * 获取运营大屏数据
     * 
     * @return 大屏数据
     */
    @RequiresPermissions("admin:statistics:dashboard")
    @RequiresPermissionsDesc(menu = {"数据统计", "数据大屏"}, button = "查询")
    @GetMapping("/dashboard")
    public Object dashboard() {
        try {
            Map<String, Object> data = statisticsService.getDashboard();
            return ResponseUtil.ok(data);
        } catch (Exception e) {
            return ResponseUtil.fail(500, "获取统计数据失败: " + e.getMessage());
        }
    }
}
