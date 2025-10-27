package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.linlinjava.litemall.db.domain.SicauSensitiveWord;

import java.util.List;

/**
 * 敏感词 Mapper
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Mapper
public interface SicauSensitiveWordMapper {
    
    /**
     * 查询所有有效敏感词
     */
    @Select("SELECT word FROM sicau_sensitive_words WHERE deleted = 0")
    List<String> selectAllWords();
    
    /**
     * 查询所有敏感词（带分页）
     */
    List<SicauSensitiveWord> selectByCondition(
            @Param("word") String word,
            @Param("type") Byte type,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );
    
    /**
     * 统计敏感词数量
     */
    int countByCondition(
            @Param("word") String word,
            @Param("type") Byte type
    );
    
    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM sicau_sensitive_words WHERE id = #{id}")
    SicauSensitiveWord selectByPrimaryKey(Integer id);
    
    /**
     * 插入敏感词
     */
    int insertSelective(SicauSensitiveWord record);
    
    /**
     * 更新敏感词
     */
    int updateByPrimaryKeySelective(SicauSensitiveWord record);
    
    /**
     * 逻辑删除
     */
    int logicalDeleteByPrimaryKey(Integer id);
}
