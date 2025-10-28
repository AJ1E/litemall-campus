package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.SicauCourseMaterialMapper;
import org.linlinjava.litemall.db.domain.SicauCourseMaterial;
import org.linlinjava.litemall.db.domain.SicauCourseMaterialExample;
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
        SicauCourseMaterialExample example = new SicauCourseMaterialExample();
        example.or().andCourseNameLike("%" + keyword + "%").andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(1, limit);
        return courseMaterialMapper.selectByExample(example);
    }
    
    /**
     * 根据教材名称搜索
     */
    public List<SicauCourseMaterial> searchByBookName(String keyword, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        SicauCourseMaterialExample example = new SicauCourseMaterialExample();
        example.or().andBookNameLike("%" + keyword + "%").andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(1, limit);
        return courseMaterialMapper.selectByExample(example);
    }
    
    /**
     * 综合搜索（课程名或教材名）
     */
    public List<SicauCourseMaterial> search(String keyword, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        SicauCourseMaterialExample example = new SicauCourseMaterialExample();
        SicauCourseMaterialExample.Criteria criteria1 = example.or();
        criteria1.andCourseNameLike("%" + keyword + "%").andDeletedEqualTo(false);
        SicauCourseMaterialExample.Criteria criteria2 = example.or();
        criteria2.andBookNameLike("%" + keyword + "%").andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(1, limit);
        return courseMaterialMapper.selectByExample(example);
    }
    
    /**
     * 分页查询教材
     */
    public List<SicauCourseMaterial> querySelective(String courseName, String bookName, 
                                                     String college, Integer page, Integer limit) {
        SicauCourseMaterialExample example = new SicauCourseMaterialExample();
        SicauCourseMaterialExample.Criteria criteria = example.or();
        criteria.andDeletedEqualTo(false);
        
        if (courseName != null && !courseName.isEmpty()) {
            criteria.andCourseNameLike("%" + courseName + "%");
        }
        if (bookName != null && !bookName.isEmpty()) {
            criteria.andBookNameLike("%" + bookName + "%");
        }
        if (college != null && !college.isEmpty()) {
            criteria.andCollegeEqualTo(college);
        }
        
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(page, limit);
        return courseMaterialMapper.selectByExample(example);
    }
    
    /**
     * 统计教材数量
     */
    public int countSelective(String courseName, String bookName, String college) {
        SicauCourseMaterialExample example = new SicauCourseMaterialExample();
        SicauCourseMaterialExample.Criteria criteria = example.or();
        criteria.andDeletedEqualTo(false);
        
        if (courseName != null && !courseName.isEmpty()) {
            criteria.andCourseNameLike("%" + courseName + "%");
        }
        if (bookName != null && !bookName.isEmpty()) {
            criteria.andBookNameLike("%" + bookName + "%");
        }
        if (college != null && !college.isEmpty()) {
            criteria.andCollegeEqualTo(college);
        }
        
        return (int) courseMaterialMapper.countByExample(example);
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
