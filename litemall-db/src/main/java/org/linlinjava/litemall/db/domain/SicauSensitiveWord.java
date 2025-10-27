package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * 敏感词实体类
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
public class SicauSensitiveWord {
    
    private Integer id;
    
    private String word;
    
    /**
     * 类型: 1-违规交易, 2-黄赌毒, 3-政治敏感, 4-其他
     */
    private Byte type;
    
    private LocalDateTime addTime;
    
    private LocalDateTime updateTime;
    
    private Boolean deleted;
    
    // Getters and Setters
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getWord() {
        return word;
    }
    
    public void setWord(String word) {
        this.word = word;
    }
    
    public Byte getType() {
        return type;
    }
    
    public void setType(Byte type) {
        this.type = type;
    }
    
    public LocalDateTime getAddTime() {
        return addTime;
    }
    
    public void setAddTime(LocalDateTime addTime) {
        this.addTime = addTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    public Boolean getDeleted() {
        return deleted;
    }
    
    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
    
    @Override
    public String toString() {
        return "SicauSensitiveWord{" +
                "id=" + id +
                ", word='" + word + '\'' +
                ", type=" + type +
                ", addTime=" + addTime +
                ", updateTime=" + updateTime +
                ", deleted=" + deleted +
                '}';
    }
}
