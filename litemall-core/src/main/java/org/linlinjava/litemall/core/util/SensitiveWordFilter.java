package org.linlinjava.litemall.core.util;

import org.linlinjava.litemall.db.service.SicauSensitiveWordService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;

/**
 * 敏感词过滤器 - 基于 DFA（Trie）实现
 *
 * 说明:
 * - 启动时尝试从数据库加载敏感词（通过 SicauSensitiveWordService），
 *   若 Service 不可用则使用内置示例词以保证组件可用性。
 * - 提供检测、获取、替换等常用方法。
 *
 * Author: bmm-dev
 */
@Component
public class SensitiveWordFilter {

    @Resource
    private SicauSensitiveWordService sensitiveWordService;

    /** DFA 根节点 */
    private Map<String, Object> dfaMap = new HashMap<>();

    /** 敏感词集合 */
    private Set<String> sensitiveWords = new HashSet<>();

    @PostConstruct
    public void init() {
        try {
            if (sensitiveWordService != null) {
                List<String> words = sensitiveWordService.getAllWords();
                if (words != null) {
                    sensitiveWords.addAll(words);
                }
            }
        } catch (Throwable ignore) {
            // 如果 service 调用失败，继续使用内置词库以保证功能基本可用
        }

        if (sensitiveWords.isEmpty()) {
            // 备用示例词（避免因数据库不可用导致空行为）
            sensitiveWords.add("代考");
            sensitiveWords.add("代写论文");
            sensitiveWords.add("刷单");
            sensitiveWords.add("黄色");
            sensitiveWords.add("赌博");
            sensitiveWords.add("毒品");
        }

        for (String word : sensitiveWords) {
            addWordToTree(word);
        }
    }

    /** 重新加载敏感词库 */
    public synchronized void reload(Collection<String> words) {
        this.sensitiveWords = new HashSet<>();
        if (words != null) this.sensitiveWords.addAll(words);
        this.dfaMap = new HashMap<>();
        for (String w : this.sensitiveWords) addWordToTree(w);
    }

    private void addWordToTree(String word) {
        if (word == null || word.trim().isEmpty()) return;
        Map<String, Object> now = dfaMap;
        for (int i = 0; i < word.length(); i++) {
            String ch = String.valueOf(word.charAt(i));
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) now.get(ch);
            if (next == null) {
                next = new HashMap<>();
                now.put(ch, next);
            }
            now = next;
            if (i == word.length() - 1) {
                now.put("isEnd", Boolean.TRUE);
            }
        }
    }

    public boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) return false;
        return !getSensitiveWords(text).isEmpty();
    }

    public List<String> getSensitiveWords(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        Set<String> found = new LinkedHashSet<>();
        for (int i = 0; i < text.length(); i++) {
            int len = checkSensitiveWord(text, i);
            if (len > 0) {
                found.add(text.substring(i, i + len));
                i += len - 1;
            }
        }
        return new ArrayList<>(found);
    }

    private int checkSensitiveWord(String text, int start) {
        Map<String, Object> now = dfaMap;
        int matchLen = 0;
        for (int i = start; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) now.get(ch);
            if (next == null) break;
            matchLen++;
            now = next;
            if (Boolean.TRUE.equals(now.get("isEnd"))) return matchLen;
        }
        return 0;
    }

    public String replaceSensitive(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        List<String> words = getSensitiveWords(text);
        for (String w : words) {
            String stars = "*".repeat(w.length());
            int idx = sb.indexOf(w);
            while (idx != -1) {
                sb.replace(idx, idx + w.length(), stars);
                idx = sb.indexOf(w, idx + stars.length());
            }
        }
        return sb.toString();
    }

    public int getWordCount() {
        return sensitiveWords.size();
    }
}
