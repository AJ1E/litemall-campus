package org.linlinjava.litemall.wx.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauCourseMaterial;
import org.linlinjava.litemall.db.service.SicauCourseMaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程教材搜索
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/wx/course")
@Validated
public class WxCourseController {
    
    private final Log logger = LogFactory.getLog(WxCourseController.class);
    
    @Autowired
    private SicauCourseMaterialService courseMaterialService;
    
    /**
     * 搜索课程教材
     * 
     * @param keyword 搜索关键词（课程名或教材名）
     * @param limit 返回数量限制（默认10）
     * @return 教材列表
     */
    @GetMapping("/search")
    public Object search(@RequestParam String keyword,
                        @RequestParam(defaultValue = "10") Integer limit) {
        if (StringUtils.isEmpty(keyword)) {
            return ResponseUtil.badArgument();
        }
        
        List<SicauCourseMaterial> materials = courseMaterialService.search(keyword, limit);
        
        return ResponseUtil.ok(materials);
    }
    
    /**
     * 根据课程名称搜索教材
     * 
     * @param courseName 课程名称
     * @param limit 返回数量限制
     * @return 教材列表
     */
    @GetMapping("/searchByCourse")
    public Object searchByCourse(@RequestParam String courseName,
                                 @RequestParam(defaultValue = "10") Integer limit) {
        if (StringUtils.isEmpty(courseName)) {
            return ResponseUtil.badArgument();
        }
        
        List<SicauCourseMaterial> materials = courseMaterialService.searchByCourseName(courseName, limit);
        
        return ResponseUtil.ok(materials);
    }
    
    /**
     * 根据教材名称搜索
     * 
     * @param bookName 教材名称
     * @param limit 返回数量限制
     * @return 教材列表
     */
    @GetMapping("/searchByBook")
    public Object searchByBook(@RequestParam String bookName,
                              @RequestParam(defaultValue = "10") Integer limit) {
        if (StringUtils.isEmpty(bookName)) {
            return ResponseUtil.badArgument();
        }
        
        List<SicauCourseMaterial> materials = courseMaterialService.searchByBookName(bookName, limit);
        
        return ResponseUtil.ok(materials);
    }
    
    /**
     * 获取教材详情
     * 
     * @param id 教材ID
     * @return 教材详情
     */
    @GetMapping("/detail")
    public Object detail(@RequestParam Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        SicauCourseMaterial material = courseMaterialService.findById(id);
        if (material == null) {
            return ResponseUtil.fail(404, "教材不存在");
        }
        
        return ResponseUtil.ok(material);
    }
}
