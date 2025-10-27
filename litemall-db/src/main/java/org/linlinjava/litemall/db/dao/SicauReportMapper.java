package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauReport;

import java.util.List;

/**
 * 川农举报与申诉 Mapper接口
 */
public interface SicauReportMapper {
    
    /**
     * 根据主键删除记录
     * @param id 主键ID
     * @return 影响行数
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * 插入记录（包含所有字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int insert(SicauReport record);

    /**
     * 插入记录（仅插入非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int insertSelective(SicauReport record);

    /**
     * 根据主键查询记录
     * @param id 主键ID
     * @return 实体对象
     */
    SicauReport selectByPrimaryKey(Integer id);

    /**
     * 根据订单ID查询举报记录
     * @param orderId 订单ID
     * @return 举报列表
     */
    List<SicauReport> selectByOrderId(@Param("orderId") Integer orderId);

    /**
     * 查询用户发起的举报列表
     * @param reporterId 举报人ID
     * @param status 状态（null-所有，0-待处理，1-处理中，2-已解决，3-已驳回）
     * @param offset 分页偏移量
     * @param limit 分页大小
     * @return 举报列表
     */
    List<SicauReport> selectByReporter(@Param("reporterId") Integer reporterId,
                                        @Param("status") Byte status,
                                        @Param("offset") Integer offset, 
                                        @Param("limit") Integer limit);

    /**
     * 查询针对某用户的举报列表
     * @param reportedId 被举报人ID
     * @param status 状态（null-所有）
     * @param offset 分页偏移量
     * @param limit 分页大小
     * @return 举报列表
     */
    List<SicauReport> selectByReported(@Param("reportedId") Integer reportedId,
                                        @Param("status") Byte status,
                                        @Param("offset") Integer offset, 
                                        @Param("limit") Integer limit);

    /**
     * 查询所有举报列表（管理员用）
     * @param status 状态（null-所有，0-待处理，1-处理中，2-已解决，3-已驳回）
     * @param type 举报类型（null-所有，1-描述不符，2-质量问题，3-虚假发货，4-其他）
     * @param offset 分页偏移量
     * @param limit 分页大小
     * @return 举报列表
     */
    List<SicauReport> selectAllReports(@Param("status") Byte status,
                                        @Param("type") Byte type,
                                        @Param("offset") Integer offset, 
                                        @Param("limit") Integer limit);

    /**
     * 统计用户发起的举报数量
     * @param reporterId 举报人ID
     * @param status 状态（null-所有）
     * @return 举报数量
     */
    long countByReporter(@Param("reporterId") Integer reporterId, @Param("status") Byte status);

    /**
     * 统计针对某用户的举报数量
     * @param reportedId 被举报人ID
     * @param status 状态（null-所有）
     * @return 举报数量
     */
    long countByReported(@Param("reportedId") Integer reportedId, @Param("status") Byte status);

    /**
     * 统计所有举报数量（管理员用）
     * @param status 状态（null-所有）
     * @param type 举报类型（null-所有）
     * @return 举报数量
     */
    long countAllReports(@Param("status") Byte status, @Param("type") Byte type);

    /**
     * 根据主键更新记录（仅更新非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKeySelective(SicauReport record);

    /**
     * 根据主键更新记录（更新所有字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKey(SicauReport record);
}
