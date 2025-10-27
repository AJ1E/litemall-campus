package org.linlinjava.litemall.admin.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.util.SensitiveWordFilter;
import org.linlinjava.litemall.db.domain.SicauSensitiveWord;
import org.linlinjava.litemall.db.service.SicauSensitiveWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词管理 Controller
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@RestController
@RequestMapping("/admin/sensitive")
@Validated
public class AdminSensitiveWordController {
    
    private final Log logger = LogFactory.getLog(AdminSensitiveWordController.class);
    
    @Autowired
    private SicauSensitiveWordService sensitiveWordService;
    
    @Autowired
    private SensitiveWordFilter sensitiveWordFilter;
    
    /**
     * 查询敏感词列表
     */
    @RequiresPermissions("admin:sensitive:list")
    @GetMapping("/list")
    public Object list(String word,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        List<SicauSensitiveWord> wordList = sensitiveWordService.querySelective(word, null, page, limit);
        int total = sensitiveWordService.countSelective(word, null);
        
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("items", wordList);
        
        return ResponseUtil.ok(data);
    }
    
    /**
     * 添加敏感词
     */
    @RequiresPermissions("admin:sensitive:create")
    @PostMapping("/create")
    public Object create(@RequestBody SicauSensitiveWord sensitiveWord) {
        if (StringUtils.isEmpty(sensitiveWord.getWord())) {
            return ResponseUtil.badArgument();
        }
        
        int result = sensitiveWordService.add(sensitiveWord);
        if (result > 0) {
            // 重新加载敏感词库
            reloadFilter();
            return ResponseUtil.ok();
        }
        
        return ResponseUtil.fail();
    }
    
    /**
     * 更新敏感词
     */
    @RequiresPermissions("admin:sensitive:update")
    @PostMapping("/update")
    public Object update(@RequestBody SicauSensitiveWord sensitiveWord) {
        if (sensitiveWord.getId() == null) {
            return ResponseUtil.badArgument();
        }
        
        int result = sensitiveWordService.updateById(sensitiveWord);
        if (result > 0) {
            // 重新加载敏感词库
            reloadFilter();
            return ResponseUtil.ok();
        }
        
        return ResponseUtil.fail();
    }
    
    /**
     * 删除敏感词
     */
    @RequiresPermissions("admin:sensitive:delete")
    @PostMapping("/delete")
    public Object delete(@RequestBody SicauSensitiveWord sensitiveWord) {
        Integer id = sensitiveWord.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        
        int result = sensitiveWordService.deleteById(id);
        if (result > 0) {
            // 重新加载敏感词库
            reloadFilter();
            return ResponseUtil.ok();
        }
        
        return ResponseUtil.fail();
    }
    
    /**
     * 查看敏感词详情
     */
    @RequiresPermissions("admin:sensitive:read")
    @GetMapping("/read")
    public Object read(@NotNull Integer id) {
        SicauSensitiveWord word = sensitiveWordService.findById(id);
        return ResponseUtil.ok(word);
    }
    
    /**
     * 手动重载敏感词库
     */
    @RequiresPermissions("admin:sensitive:reload")
    @PostMapping("/reload")
    public Object reload() {
        reloadFilter();
        return ResponseUtil.ok();
    }
    
    /**
     * 测试敏感词检测
     */
    @RequiresPermissions("admin:sensitive:test")
    @PostMapping("/test")
    public Object test(@RequestBody Map<String, String> params) {
        String text = params.get("text");
        if (StringUtils.isEmpty(text)) {
            return ResponseUtil.badArgument();
        }
        
        boolean hasSensitive = sensitiveWordFilter.containsSensitive(text);
        List<String> words = sensitiveWordFilter.getSensitiveWords(text);
        String filtered = sensitiveWordFilter.replaceSensitive(text);
        
        Map<String, Object> result = new HashMap<>();
        result.put("hasSensitive", hasSensitive);
        result.put("sensitiveWords", words);
        result.put("filteredText", filtered);
        
        return ResponseUtil.ok(result);
    }
    
    /**
     * 重新加载敏感词过滤器
     */
    private void reloadFilter() {
        List<String> words = sensitiveWordService.getAllWords();
        sensitiveWordFilter.reload(words);
        logger.info("敏感词库已重载，当前词数：" + words.size());
    }
}
