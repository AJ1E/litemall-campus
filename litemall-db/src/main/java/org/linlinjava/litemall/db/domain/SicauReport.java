package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * 订单举报申诉实体
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
public class SicauReport {
    
    private Integer id;
    
    /**
     * 订单ID
     */
    private Integer orderId;
    
    /**
     * 举报人用户ID
     */
    private Integer reporterId;
    
    /**
     * 被举报人用户ID
     */
    private Integer reportedId;
    
    /**
     * 举报类型: 1-描述不符, 2-质量问题, 3-虚假发货, 4-其他
     */
    private Byte type;
    
    /**
     * 举报原因详细描述
     */
    private String reason;
    
    /**
     * 证据图片URL数组（JSON）
     */
    private String images;
    
    /**
     * 处理状态: 0-待处理, 1-处理中, 2-已解决, 3-已驳回
     */
    private Byte status;
    
    /**
     * 处理管理员ID
     */
    private Integer handlerAdminId;
    
    /**
     * 处理结果说明
     */
    private String handleResult;
    
    /**
     * 处理时间
     */
    private LocalDateTime handleTime;
    
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

    public Integer getReporterId() {
        return reporterId;
    }

    public void setReporterId(Integer reporterId) {
        this.reporterId = reporterId;
    }

    public Integer getReportedId() {
        return reportedId;
    }

    public void setReportedId(Integer reportedId) {
        this.reportedId = reportedId;
    }

    public Byte getType() {
        return type;
    }

    public void setType(Byte type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public Integer getHandlerAdminId() {
        return handlerAdminId;
    }

    public void setHandlerAdminId(Integer handlerAdminId) {
        this.handlerAdminId = handlerAdminId;
    }

    public String getHandleResult() {
        return handleResult;
    }

    public void setHandleResult(String handleResult) {
        this.handleResult = handleResult;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(LocalDateTime handleTime) {
        this.handleTime = handleTime;
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
        return "SicauReport{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", reporterId=" + reporterId +
                ", reportedId=" + reportedId +
                ", type=" + type +
                ", reason='" + reason + '\'' +
                ", images='" + images + '\'' +
                ", status=" + status +
                ", handlerAdminId=" + handlerAdminId +
                ", handleResult='" + handleResult + '\'' +
                ", handleTime=" + handleTime +
                ", addTime=" + addTime +
                ", updateTime=" + updateTime +
                ", deleted=" + deleted +
                '}';
    }
}
