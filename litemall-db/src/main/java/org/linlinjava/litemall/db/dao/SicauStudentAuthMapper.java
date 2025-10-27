package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;

import java.util.List;

/**
 * 川农学生认证 Mapper接口
 */
public interface SicauStudentAuthMapper {
    
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
    int insert(SicauStudentAuth record);

    /**
     * 插入记录（仅插入非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int insertSelective(SicauStudentAuth record);

    /**
     * 根据主键查询记录
     * @param id 主键ID
     * @return 实体对象
     */
    SicauStudentAuth selectByPrimaryKey(Integer id);

    /**
     * 根据用户ID查询认证信息
     * @param userId 用户ID
     * @return 实体对象
     */
    SicauStudentAuth selectByUserId(@Param("userId") Integer userId);

    /**
     * 根据学号查询认证信息（学号已加密）
     * @param studentNo 加密后的学号
     * @return 实体对象
     */
    SicauStudentAuth selectByStudentNo(@Param("studentNo") String studentNo);

    /**
     * 根据状态查询认证列表
     * @param status 认证状态
     * @return 认证列表
     */
    List<SicauStudentAuth> selectByStatus(@Param("status") Byte status);

    /**
     * 根据主键更新记录（仅更新非空字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKeySelective(SicauStudentAuth record);

    /**
     * 根据主键更新记录（更新所有字段）
     * @param record 实体对象
     * @return 影响行数
     */
    int updateByPrimaryKey(SicauStudentAuth record);
}
