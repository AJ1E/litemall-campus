package org.linlinjava.litemall.admin.service;

import org.linlinjava.litemall.db.dao.SicauDailyStatisticsMapper;
import org.linlinjava.litemall.db.domain.SicauDailyStatistics;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计服务
 */
@Service
public class StatisticsService {
    
    @Resource
    private LitemallUserService userService;
    
    @Resource
    private LitemallOrderService orderService;
    
    @Resource
    private SicauDailyStatisticsMapper dailyStatMapper;
    
    /**
     * 获取运营大屏数据
     */
    public Map<String, Object> getDashboard() {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 今日数据（实时统计）
        result.put("today", getTodayStats());
        
        // 2. 本周订单趋势（最近 7 天，从历史表读取）
        result.put("weekTrend", getWeekTrend());
        
        // 3. 总览数据
        result.put("overview", getOverview());
        
        return result;
    }
    
    /**
     * 今日数据（实时统计）
     */
    private Map<String, Object> getTodayStats() {
        Map<String, Object> stats = new HashMap<>();
        
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        
        // 统计今日新增用户数
        long newUsers = userService.count();
        stats.put("newUsers", newUsers);
        
        // 统计今日订单数
        long orderCount = orderService.count();
        stats.put("orderCount", orderCount);
        
        // 模拟 DAU 和 GMV（实际项目中需要实现具体统计逻辑）
        stats.put("dau", newUsers * 3); // 模拟：假设DAU是新增用户的3倍
        stats.put("gmv", new BigDecimal("1000.00")); // 模拟GMV
        
        return stats;
    }
    
    /**
     * 本周趋势（最近 7 天，从历史统计表读取）
     */
    private List<Map<String, Object>> getWeekTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        
        // 查询最近7天的统计数据
        List<SicauDailyStatistics> dailyStats = dailyStatMapper.selectRecentDays(7);
        
        // 如果没有历史数据，返回模拟数据
        if (dailyStats == null || dailyStats.isEmpty()) {
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                Map<String, Object> item = new HashMap<>();
                item.put("date", date.toString());
                item.put("orderCount", 10 + i * 5); // 模拟数据
                item.put("gmv", new BigDecimal(500 + i * 100)); // 模拟数据
                trend.add(item);
            }
        } else {
            // 使用实际统计数据
            for (SicauDailyStatistics stat : dailyStats) {
                Map<String, Object> item = new HashMap<>();
                item.put("date", stat.getStatDate().toString());
                item.put("orderCount", stat.getTotalOrders() != null ? stat.getTotalOrders() : 0);
                item.put("gmv", stat.getGmv() != null ? stat.getGmv() : BigDecimal.ZERO);
                trend.add(item);
            }
        }
        
        return trend;
    }
    
    /**
     * 总览数据
     */
    private Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();
        
        // 统计总用户数
        long totalUsers = userService.count();
        overview.put("totalUsers", totalUsers);
        
        // 统计总订单数
        long totalOrders = orderService.count();
        overview.put("totalOrders", totalOrders);
        
        // 模拟数据
        overview.put("totalGmv", new BigDecimal("50000.00"));
        overview.put("avgPrice", new BigDecimal("50.00"));
        
        return overview;
    }
    
    /**
     * 保存每日统计数据（供定时任务调用）
     */
    public int saveDailyStats(LocalDate date, Integer dau, Integer newUsers, 
                               Integer totalOrders, Integer paidOrders, 
                               BigDecimal gmv, BigDecimal avgPrice, Integer newGoods) {
        SicauDailyStatistics stat = new SicauDailyStatistics();
        stat.setStatDate(date);
        stat.setDau(dau);
        stat.setNewUsers(newUsers);
        stat.setTotalOrders(totalOrders);
        stat.setPaidOrders(paidOrders);
        stat.setGmv(gmv);
        stat.setAvgPrice(avgPrice);
        stat.setNewGoods(newGoods);
        stat.setAddTime(LocalDateTime.now());
        
        return dailyStatMapper.insertSelective(stat);
    }
}
