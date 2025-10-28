package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日数据统计实体类
 */
public class SicauDailyStatistics {
    private Integer id;
    private LocalDate statDate;
    private Integer dau;
    private Integer newUsers;
    private Integer totalOrders;
    private Integer paidOrders;
    private BigDecimal gmv;
    private BigDecimal avgPrice;
    private Integer newGoods;
    private LocalDateTime addTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Integer getDau() {
        return dau;
    }

    public void setDau(Integer dau) {
        this.dau = dau;
    }

    public Integer getNewUsers() {
        return newUsers;
    }

    public void setNewUsers(Integer newUsers) {
        this.newUsers = newUsers;
    }

    public Integer getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Integer totalOrders) {
        this.totalOrders = totalOrders;
    }

    public Integer getPaidOrders() {
        return paidOrders;
    }

    public void setPaidOrders(Integer paidOrders) {
        this.paidOrders = paidOrders;
    }

    public BigDecimal getGmv() {
        return gmv;
    }

    public void setGmv(BigDecimal gmv) {
        this.gmv = gmv;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public Integer getNewGoods() {
        return newGoods;
    }

    public void setNewGoods(Integer newGoods) {
        this.newGoods = newGoods;
    }

    public LocalDateTime getAddTime() {
        return addTime;
    }

    public void setAddTime(LocalDateTime addTime) {
        this.addTime = addTime;
    }
}
