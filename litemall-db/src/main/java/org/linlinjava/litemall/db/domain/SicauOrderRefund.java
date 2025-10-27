package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单退款记录实体
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
public class SicauOrderRefund {
    
    private Integer id;
    
    /**
     * 订单ID
     */
    private Integer orderId;
    
    /**
     * 退款单号
     */
    private String refundSn;
    
    /**
     * 退款金额
     */
    private BigDecimal refundAmount;
    
    /**
     * 退款原因
     */
    private String refundReason;
    
    /**
     * 退款类型: 1-用户主动取消, 2-超时未支付, 3-举报退款
     */
    private Byte refundType;
    
    /**
     * 退款状态: 0-待退款, 1-退款中, 2-退款成功, 3-退款失败
     */
    private Byte refundStatus;
    
    /**
     * 退款成功时间
     */
    private LocalDateTime refundTime;
    
    private LocalDateTime addTime;
    
    private LocalDateTime updateTime;

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

    public String getRefundSn() {
        return refundSn;
    }

    public void setRefundSn(String refundSn) {
        this.refundSn = refundSn;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public Byte getRefundType() {
        return refundType;
    }

    public void setRefundType(Byte refundType) {
        this.refundType = refundType;
    }

    public Byte getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(Byte refundStatus) {
        this.refundStatus = refundStatus;
    }

    public LocalDateTime getRefundTime() {
        return refundTime;
    }

    public void setRefundTime(LocalDateTime refundTime) {
        this.refundTime = refundTime;
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

    @Override
    public String toString() {
        return "SicauOrderRefund{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", refundSn='" + refundSn + '\'' +
                ", refundAmount=" + refundAmount +
                ", refundReason='" + refundReason + '\'' +
                ", refundType=" + refundType +
                ", refundStatus=" + refundStatus +
                ", refundTime=" + refundTime +
                ", addTime=" + addTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
