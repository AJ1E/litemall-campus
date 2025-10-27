package org.linlinjava.litemall.admin.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.authz.annotation.RequiresPermissions;
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
 * 课程教材管理 Controller
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/admin/course")
@Validated
public class AdminCourseController {
    
    private final Log logger = LogFactory.getLog(AdminCourseController.class);
    
    @Autowired
    private SicauCourseMaterialService courseMaterialService;
    
    /**
     * 查询教材列表
     */
    @RequiresPermissions("admin:course:list")
    @GetMapping("/list")
    public Object list(String courseName,
                       String bookName,
                       String college,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        List<SicauCourseMaterial> materialList = courseMaterialService.querySelective(
                courseName, bookName, college, page, limit);
        int total = courseMaterialService.countSelective(courseName, bookName, college);
        
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("items", materialList);
        
        return ResponseUtil.ok(data);
    }
    
    /**
     * 添加教材
     */
    @RequiresPermissions("admin:course:create")
    @PostMapping("/create")
    public Object create(@RequestBody SicauCourseMaterial material) {
        if (StringUtils.isEmpty(material.getCourseName())) {
            return ResponseUtil.badArgumentValue("课程名称不能为空");
        }
        if (StringUtils.isEmpty(material.getBookName())) {
            return ResponseUtil.badArgumentValue("教材名称不能为空");
        }
        
        int result = courseMaterialService.add(material);
        if (result > 0) {
            return ResponseUtil.ok();
        }
        
        return ResponseUtil.fail();
    }
    
    /**
     * 更新教材
     */
    @RequiresPermissions("admin:course:update")
    @PostMapping("/update")
    public Object update(@RequestBody SicauCourseMaterial material) {
        if (material.getId() == null) {
            return ResponseUtil.badArgument();
        }
        
        int result = courseMaterialService.updateById(material);
        if (result > 0) {
            return ResponseUtil.ok();
        }
        
        return ResponseUtil.fail();
    }
    
    /**
     * 删除教材
     */
    @RequiresPermissions("admin:course:delete")
    @PostMapping("/delete")
    public Object delete(@RequestBody SicauCourseMaterial material) {
        Integer id = material.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        int result = courseMaterialService.deleteById(id);
        if (result > 0) {
            return ResponseUtil.ok();
        }
        
        return ResponseUtil.fail();
    }
    
    /**
     * 查看教材详情
     */
    @RequiresPermissions("admin:course:read")
    @GetMapping("/read")
    public Object read(Integer id) {
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        SicauCourseMaterial material = courseMaterialService.findById(id);
        return ResponseUtil.ok(material);
    }
}
