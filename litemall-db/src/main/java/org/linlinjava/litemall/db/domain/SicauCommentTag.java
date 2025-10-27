package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * 评价标签实体
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
public class SicauCommentTag {
    
    private Integer id;
    
    /**
     * 标签角色: 1-买家评卖家, 2-卖家评买家
     */
    private Byte role;
    
    /**
     * 标签名称
     */
    private String tagName;
    
    /**
     * 排序权重
     */
    private Integer sortOrder;
    
    /**
     * 添加时间
     */
    private LocalDateTime addTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 逻辑删除标记
     */
    private Boolean deleted;

    // Getters and Setters
    
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Byte getRole() {
        return role;
    }

    public void setRole(Byte role) {
        this.role = role;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
        return "SicauCommentTag{" +
                "id=" + id +
                ", role=" + role +
                ", tagName='" + tagName + '\'' +
                ", sortOrder=" + sortOrder +
                ", addTime=" + addTime +
                ", updateTime=" + updateTime +
                ", deleted=" + deleted +
                '}';
    }
}
