package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.SicauCourseMaterialMapper;
import org.linlinjava.litemall.db.domain.SicauCourseMaterial;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程教材服务
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Service
public class SicauCourseMaterialService {
    
    @Resource
    private SicauCourseMaterialMapper courseMaterialMapper;
    
    /**
     * 根据课程名称搜索教材
     */
    public List<SicauCourseMaterial> searchByCourseName(String keyword, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        return courseMaterialMapper.searchByCourseName(keyword, limit);
    }
    
    /**
     * 根据教材名称搜索
     */
    public List<SicauCourseMaterial> searchByBookName(String keyword, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        return courseMaterialMapper.searchByBookName(keyword, limit);
    }
    
    /**
     * 综合搜索（课程名或教材名）
     */
    public List<SicauCourseMaterial> search(String keyword, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        return courseMaterialMapper.search(keyword, limit);
    }
    
    /**
     * 分页查询教材
     */
    public List<SicauCourseMaterial> querySelective(String courseName, String bookName, 
                                                     String college, Integer page, Integer limit) {
        PageHelper.startPage(page, limit);
        return courseMaterialMapper.selectByCondition(courseName, bookName, college, null, null);
    }
    
    /**
     * 统计教材数量
     */
    public int countSelective(String courseName, String bookName, String college) {
        return courseMaterialMapper.countByCondition(courseName, bookName, college);
    }
    
    /**
     * 根据ID查询
     */
    public SicauCourseMaterial findById(Integer id) {
        return courseMaterialMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 添加教材
     */
    public int add(SicauCourseMaterial material) {
        material.setAddTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());
        material.setDeleted(false);
        return courseMaterialMapper.insertSelective(material);
    }
    
    /**
     * 更新教材
     */
    public int updateById(SicauCourseMaterial material) {
        material.setUpdateTime(LocalDateTime.now());
        return courseMaterialMapper.updateByPrimaryKeySelective(material);
    }
    
    /**
     * 删除教材
     */
    public int deleteById(Integer id) {
        return courseMaterialMapper.logicalDeleteByPrimaryKey(id);
    }
}
