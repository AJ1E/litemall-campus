package org.linlinjava.litemall.admin.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.admin.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 每日数据统计定时任务
 */
@Component
public class DailyStatisticsTask {
    private final Log logger = LogFactory.getLog(DailyStatisticsTask.class);
    
    @Autowired
    private StatisticsService statisticsService;
    
    /**
     * 每天凌晨 1 点执行统计任务
     * cron表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void statisticsDaily() {
        try {
            logger.info("开始执行每日数据统计任务...");
            
            LocalDate yesterday = LocalDate.now().minusDays(1);
            
            // 实际项目中这里应该查询昨天的真实数据
            // 这里使用模拟数据作为示例
            int dau = 100;
            int newUsers = 20;
            int totalOrders = 50;
            int paidOrders = 45;
            BigDecimal gmv = new BigDecimal("2500.00");
            BigDecimal avgPrice = new BigDecimal("50.00");
            int newGoods = 30;
            
            // 保存统计数据
            int result = statisticsService.saveDailyStats(
                yesterday, dau, newUsers, totalOrders, paidOrders, 
                gmv, avgPrice, newGoods
            );
            
            if (result > 0) {
                logger.info("每日数据统计任务完成，统计日期: " + yesterday);
            } else {
                logger.warn("每日数据统计任务执行失败");
            }
        } catch (Exception e) {
            logger.error("每日数据统计任务执行异常", e);
        }
    }
    
    /**
     * 测试方法：每分钟执行一次（用于测试，生产环境应注释掉）
     * 取消注释下面的方法可用于测试定时任务是否正常运行
     */
    // @Scheduled(cron = "0 * * * * ?")
    // public void testTask() {
    //     logger.info("定时任务测试: " + LocalDateTime.now());
    // }
}
