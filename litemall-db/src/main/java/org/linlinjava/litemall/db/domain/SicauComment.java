package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * 互评实体
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
public class SicauComment {
    
    private Integer id;
    
    /**
     * 订单ID
     */
    private Integer orderId;
    
    /**
     * 评价者用户ID
     */
    private Integer fromUserId;
    
    /**
     * 被评价者用户ID
     */
    private Integer toUserId;
    
    /**
     * 评价者角色: 1-买家评卖家, 2-卖家评买家
     */
    private Byte role;
    
    /**
     * 评分: 1-5星
     */
    private Byte rating;
    
    /**
     * 标签（JSON数组）
     */
    private String tags;
    
    /**
     * 文字评价
     */
    private String content;
    
    /**
     * 回复评价
     */
    private String reply;
    
    /**
     * 是否匿名评价
     */
    private Boolean isAnonymous;
    
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

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public Integer getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Integer fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Integer getToUserId() {
        return toUserId;
    }

    public void setToUserId(Integer toUserId) {
        this.toUserId = toUserId;
    }

    public Byte getRole() {
        return role;
    }

    public void setRole(Byte role) {
        this.role = role;
    }

    public Byte getRating() {
        return rating;
    }

    public void setRating(Byte rating) {
        this.rating = rating;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public Boolean getIsAnonymous() {
        return isAnonymous;
    }

    public void setIsAnonymous(Boolean isAnonymous) {
        this.isAnonymous = isAnonymous;
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
        return "SicauComment{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", fromUserId=" + fromUserId +
                ", toUserId=" + toUserId +
                ", role=" + role +
                ", rating=" + rating +
                ", tags='" + tags + '\'' +
                ", content='" + content + '\'' +
                ", reply='" + reply + '\'' +
                ", isAnonymous=" + isAnonymous +
                ", addTime=" + addTime +
                ", updateTime=" + updateTime +
                ", deleted=" + deleted +
                '}';
    }
}
