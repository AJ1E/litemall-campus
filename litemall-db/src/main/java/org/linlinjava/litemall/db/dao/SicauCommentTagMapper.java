package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauCommentTag;

import java.util.List;

/**
 * 川农评价标签 Mapper接口
 */
public interface SicauCommentTagMapper {
    
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
    int insert(SicauCommentTag record);

    /**
     * 插入记录（仅插入非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int insertSelective(SicauCommentTag record);

    /**
     * 根据主键查询记录
     * @param id 主键ID
     * @return 实体对象
     */
    SicauCommentTag selectByPrimaryKey(Integer id);

    /**
     * 根据角色查询标签列表
     * @param role 角色 1-买家评卖家 2-卖家评买家
     * @return 标签列表
     */
    List<SicauCommentTag> selectByRole(@Param("role") Byte role);

    /**
     * 查询所有标签列表
     * @return 标签列表
     */
    List<SicauCommentTag> selectAll();

    /**
     * 根据主键更新记录（仅更新非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKeySelective(SicauCommentTag record);

    /**
     * 根据主键更新记录（更新所有字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKey(SicauCommentTag record);
}
