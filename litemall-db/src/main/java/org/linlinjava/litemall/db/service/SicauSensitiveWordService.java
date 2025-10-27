package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.SicauSensitiveWordMapper;
import org.linlinjava.litemall.db.domain.SicauSensitiveWord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 敏感词服务
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Service
public class SicauSensitiveWordService {
    
    @Resource
    private SicauSensitiveWordMapper sensitiveWordMapper;
    
    /**
     * 获取所有有效敏感词（用于过滤器初始化）
     */
    public List<String> getAllWords() {
        return sensitiveWordMapper.selectAllWords();
    }
    
    /**
     * 分页查询敏感词
     */
    public List<SicauSensitiveWord> querySelective(String word, Byte type, Integer page, Integer limit) {
        PageHelper.startPage(page, limit);
        return sensitiveWordMapper.selectByCondition(word, type, null, null);
    }
    
    /**
     * 统计敏感词数量
     */
    public int countSelective(String word, Byte type) {
        return sensitiveWordMapper.countByCondition(word, type);
    }
    
    /**
     * 根据ID查询
     */
    public SicauSensitiveWord findById(Integer id) {
        return sensitiveWordMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 添加敏感词
     */
    public int add(SicauSensitiveWord word) {
        word.setAddTime(LocalDateTime.now());
        word.setUpdateTime(LocalDateTime.now());
        word.setDeleted(false);
        return sensitiveWordMapper.insertSelective(word);
    }
    
    /**
     * 更新敏感词
     */
    public int updateById(SicauSensitiveWord word) {
        word.setUpdateTime(LocalDateTime.now());
        return sensitiveWordMapper.updateByPrimaryKeySelective(word);
    }
    
    /**
     * 删除敏感词
     */
    public int deleteById(Integer id) {
        return sensitiveWordMapper.logicalDeleteByPrimaryKey(id);
    }
}
