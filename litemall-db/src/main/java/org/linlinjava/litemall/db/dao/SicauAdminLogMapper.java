package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauAdminLog;

import java.util.List;

/**
 * 管理员操作日志 Mapper
 */
public interface SicauAdminLogMapper {
    
    /**
     * 插入日志记录
     */
    int insertSelective(SicauAdminLog record);
    
    /**
     * 根据管理员ID查询日志
     */
    List<SicauAdminLog> selectByAdminId(@Param("adminId") Integer adminId);
    
    /**
     * 根据操作类型查询日志
     */
    List<SicauAdminLog> selectByActionType(@Param("actionType") String actionType);
    
    /**
     * 查询所有日志（分页）
     */
    List<SicauAdminLog> selectAll();
}
