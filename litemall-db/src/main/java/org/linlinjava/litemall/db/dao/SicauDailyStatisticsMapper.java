package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauDailyStatistics;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日数据统计 Mapper
 */
public interface SicauDailyStatisticsMapper {
    
    /**
     * 插入统计记录
     */
    int insertSelective(SicauDailyStatistics record);
    
    /**
     * 根据日期查询统计
     */
    SicauDailyStatistics selectByDate(@Param("statDate") LocalDate statDate);
    
    /**
     * 查询最近N天的统计
     */
    List<SicauDailyStatistics> selectRecentDays(@Param("days") Integer days);
    
    /**
     * 查询所有统计（分页）
     */
    List<SicauDailyStatistics> selectAll();
}
