package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.linlinjava.litemall.db.domain.SicauCourseMaterial;

import java.util.List;

/**
 * 课程教材 Mapper
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Mapper
public interface SicauCourseMaterialMapper {
    
    /**
     * 根据课程名称模糊搜索教材
     */
    @Select("SELECT * FROM sicau_course_material " +
            "WHERE deleted = 0 AND course_name LIKE CONCAT('%', #{keyword}, '%') " +
            "ORDER BY course_name LIMIT #{limit}")
    List<SicauCourseMaterial> searchByCourseName(
            @Param("keyword") String keyword, 
            @Param("limit") Integer limit);
    
    /**
     * 根据教材名称模糊搜索
     */
    @Select("SELECT * FROM sicau_course_material " +
            "WHERE deleted = 0 AND book_name LIKE CONCAT('%', #{keyword}, '%') " +
            "ORDER BY book_name LIMIT #{limit}")
    List<SicauCourseMaterial> searchByBookName(
            @Param("keyword") String keyword, 
            @Param("limit") Integer limit);
    
    /**
     * 综合搜索（课程名或教材名）
     */
    @Select("SELECT * FROM sicau_course_material " +
            "WHERE deleted = 0 AND (" +
            "   course_name LIKE CONCAT('%', #{keyword}, '%') OR " +
            "   book_name LIKE CONCAT('%', #{keyword}, '%')" +
            ") ORDER BY course_name LIMIT #{limit}")
    List<SicauCourseMaterial> search(
            @Param("keyword") String keyword, 
            @Param("limit") Integer limit);
    
    /**
     * 分页查询
     */
    List<SicauCourseMaterial> selectByCondition(
            @Param("courseName") String courseName,
            @Param("bookName") String bookName,
            @Param("college") String college,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit);
    
    /**
     * 统计数量
     */
    int countByCondition(
            @Param("courseName") String courseName,
            @Param("bookName") String bookName,
            @Param("college") String college);
    
    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM sicau_course_material WHERE id = #{id}")
    SicauCourseMaterial selectByPrimaryKey(Integer id);
    
    /**
     * 插入
     */
    int insertSelective(SicauCourseMaterial record);
    
    /**
     * 更新
     */
    int updateByPrimaryKeySelective(SicauCourseMaterial record);
    
    /**
     * 逻辑删除
     */
    int logicalDeleteByPrimaryKey(Integer id);
}
