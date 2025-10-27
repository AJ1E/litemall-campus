package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauComment;

import java.util.List;

/**
 * 川农互评系统 Mapper接口
 */
public interface SicauCommentMapper {
    
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
    int insert(SicauComment record);

    /**
     * 插入记录（仅插入非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int insertSelective(SicauComment record);

    /**
     * 根据主键查询记录
     * @param id 主键ID
     * @return 实体对象
     */
    SicauComment selectByPrimaryKey(Integer id);

    /**
     * 根据订单ID查询评价列表
     * @param orderId 订单ID
     * @return 评价列表
     */
    List<SicauComment> selectByOrderId(@Param("orderId") Integer orderId);

    /**
     * 根据订单ID和角色查询评价（判断是否已评价）
     * @param orderId 订单ID
     * @param role 角色 1-买家评价卖家 2-卖家评价买家
     * @return 评价对象
     */
    SicauComment selectByOrderIdAndRole(@Param("orderId") Integer orderId, @Param("role") Byte role);

    /**
     * 查询用户收到的评价列表（作为被评价方）
     * @param toUserId 被评价用户ID
     * @param isAnonymous 是否匿名（null-所有，true-仅匿名，false-仅实名）
     * @param offset 分页偏移量
     * @param limit 分页大小
     * @return 评价列表
     */
    List<SicauComment> selectReceivedComments(@Param("toUserId") Integer toUserId, 
                                               @Param("isAnonymous") Boolean isAnonymous,
                                               @Param("offset") Integer offset, 
                                               @Param("limit") Integer limit);

    /**
     * 查询用户发出的评价列表（作为评价方）
     * @param fromUserId 评价用户ID
     * @param offset 分页偏移量
     * @param limit 分页大小
     * @return 评价列表
     */
    List<SicauComment> selectSentComments(@Param("fromUserId") Integer fromUserId,
                                           @Param("offset") Integer offset, 
                                           @Param("limit") Integer limit);

    /**
     * 统计用户收到的评价数量
     * @param toUserId 被评价用户ID
     * @param isAnonymous 是否匿名（null-所有，true-仅匿名，false-仅实名）
     * @return 评价数量
     */
    long countReceivedComments(@Param("toUserId") Integer toUserId, @Param("isAnonymous") Boolean isAnonymous);

    /**
     * 统计用户发出的评价数量
     * @param fromUserId 评价用户ID
     * @return 评价数量
     */
    long countSentComments(@Param("fromUserId") Integer fromUserId);

    /**
     * 计算用户平均评分
     * @param toUserId 被评价用户ID
     * @return 平均评分（保留1位小数）
     */
    Double calculateAverageRating(@Param("toUserId") Integer toUserId);

    /**
     * 根据主键更新记录（仅更新非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKeySelective(SicauComment record);

    /**
     * 根据主键更新记录（更新所有字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKey(SicauComment record);
}
