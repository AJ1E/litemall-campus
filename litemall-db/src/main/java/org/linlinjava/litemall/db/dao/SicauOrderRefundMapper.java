package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauOrderRefund;

import java.util.List;

/**
 * 川农订单退款 Mapper接口
 */
public interface SicauOrderRefundMapper {
    
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
    int insert(SicauOrderRefund record);

    /**
     * 插入记录（仅插入非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int insertSelective(SicauOrderRefund record);

    /**
     * 根据主键查询记录
     * @param id 主键ID
     * @return 实体对象
     */
    SicauOrderRefund selectByPrimaryKey(Integer id);

    /**
     * 根据订单ID查询退款记录
     * @param orderId 订单ID
     * @return 退款对象
     */
    SicauOrderRefund selectByOrderId(@Param("orderId") Integer orderId);

    /**
     * 根据退款流水号查询退款记录
     * @param refundSn 退款流水号
     * @return 退款对象
     */
    SicauOrderRefund selectByRefundSn(@Param("refundSn") String refundSn);

    /**
     * 查询退款列表（按状态）
     * @param refundStatus 退款状态（null-所有，0-待退款，1-退款中，2-退款成功，3-退款失败）
     * @param offset 分页偏移量
     * @param limit 分页大小
     * @return 退款列表
     */
    List<SicauOrderRefund> selectByStatus(@Param("refundStatus") Byte refundStatus,
                                           @Param("offset") Integer offset, 
                                           @Param("limit") Integer limit);

    /**
     * 统计退款记录数量（按状态）
     * @param refundStatus 退款状态（null-所有）
     * @return 退款数量
     */
    long countByStatus(@Param("refundStatus") Byte refundStatus);

    /**
     * 根据主键更新记录（仅更新非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKeySelective(SicauOrderRefund record);

    /**
     * 根据主键更新记录（更新所有字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKey(SicauOrderRefund record);
}
