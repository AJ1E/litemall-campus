package org.linlinjava.litemall.core.util;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

package org.linlinjava.litemall.core.util;

import org.linlinjava.litemall.db.service.SicauSensitiveWordService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;

/**
 * 敏感词过滤器 - 基于DFA算法
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Component
public class SensitiveWordFilter {
    
    @Resource
    private SicauSensitiveWordService sensitiveWordService;
    
    /**
     * DFA算法核心数据结构
     * 使用HashMap构建前缀树（Trie树）
     */
    private Map<String, Object> dfaMap = new HashMap<>();
    
    /**
     * 初始化DFA树（从数据库加载敏感词）
     */
    @PostConstruct
    public void init() {
        List<String> words = sensitiveWordService.getAllWords();
        for (String word : words) {
            addWordToTree(word);
        }
    }
@Component
public class SensitiveWordFilter {
    
    /**
     * DFA 字典树根节点
     * Key: 字符
     * Value: 下一层节点（Map）或结束标志（Boolean）
     */
    private Map<String, Object> dfaMap = new HashMap<>();
    
    /**
     * 敏感词集合（从数据库加载）
     */
    private Set<String> sensitiveWords = new HashSet<>();
    
    /**
     * 初始化 DFA 字典树
     * 在 Spring 容器启动后自动执行
     */
    @PostConstruct
    public void init() {
        // TODO: 从数据库加载敏感词到 sensitiveWords
        // 临时使用硬编码数据演示
        sensitiveWords.add("代考");
        sensitiveWords.add("代写论文");
        sensitiveWords.add("刷单");
        sensitiveWords.add("黄色");
        sensitiveWords.add("赌博");
        sensitiveWords.add("毒品");
        
        // 构建 DFA 树
        for (String word : sensitiveWords) {
            addWordToTree(word);
        }
    }
    
    /**
     * 重新加载敏感词库（管理后台修改后调用）
     * @param words 新的敏感词集合
     */
    public void reload(Set<String> words) {
        this.sensitiveWords = words;
        this.dfaMap = new HashMap<>();
        
        for (String word : words) {
            addWordToTree(word);
        }
    }
    
    /**
     * 添加单个敏感词到字典树
     * @param word 敏感词
     */
    private void addWordToTree(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }
        
        Map<String, Object> nowMap = dfaMap;
        
        for (int i = 0; i < word.length(); i++) {
            String key = String.valueOf(word.charAt(i));
            
            // 获取下一层节点
            @SuppressWarnings("unchecked")
            Map<String, Object> nextMap = (Map<String, Object>) nowMap.get(key);
            
            if (nextMap == null) {
                // 创建新节点
                nextMap = new HashMap<>();
                nowMap.put(key, nextMap);
            }
            
            nowMap = nextMap;
            
            // 最后一个字符，标记结束
            if (i == word.length() - 1) {
                nowMap.put("isEnd", true);
            }
        }
    }
    
    /**
     * 检测文本是否包含敏感词
     * @param text 待检测文本
     * @return 是否包含敏感词
     */
    public boolean containsSensitive(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        return !getSensitiveWords(text).isEmpty();
    }
    
    /**
     * 获取文本中的所有敏感词
     * @param text 待检测文本
     * @return 敏感词列表（去重）
     */
    public List<String> getSensitiveWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> result = new LinkedHashSet<>();
        
        for (int i = 0; i < text.length(); i++) {
            int length = checkSensitiveWord(text, i);
            if (length > 0) {
                result.add(text.substring(i, i + length));
                i += length - 1; // 跳过已匹配的字符
            }
        }
        
        return new ArrayList<>(result);
    }
    
    /**
     * 检查指定位置开始的敏感词长度
     * @param text 文本
     * @param start 起始位置
     * @return 敏感词长度（0 表示不包含）
     */
    private int checkSensitiveWord(String text, int start) {
        Map<String, Object> nowMap = dfaMap;
        int matchLength = 0;
        
        for (int i = start; i < text.length(); i++) {
            String key = String.valueOf(text.charAt(i));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> nextMap = (Map<String, Object>) nowMap.get(key);
            
            if (nextMap == null) {
                // 未匹配，终止
                break;
            }
            
            matchLength++;
            nowMap = nextMap;
            
            // 检查是否到达敏感词结尾
            if (Boolean.TRUE.equals(nowMap.get("isEnd"))) {
                return matchLength;
            }
        }
        
        return 0;
    }
    
    /**
     * 替换敏感词为 ***
     * @param text 原文本
     * @return 替换后的文本
     */
    public String replaceSensitive(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        StringBuilder result = new StringBuilder(text);
        List<String> words = getSensitiveWords(text);
        
        for (String word : words) {
            String replacement = "*".repeat(word.length());
            int index = result.indexOf(word);
            while (index != -1) {
                result.replace(index, index + word.length(), replacement);
                index = result.indexOf(word, index + word.length());
            }
        }
        
        return result.toString();
    }
    
    /**
     * 获取当前敏感词库大小
     * @return 敏感词数量
     */
    public int getWordCount() {
        return sensitiveWords.size();
    }
}
