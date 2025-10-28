package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauCourierIncomeMapper;
import org.linlinjava.litemall.db.domain.SicauCourierIncome;
import org.linlinjava.litemall.db.domain.SicauCourierIncomeExample;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 快递员收入流水服务
 */
@Service
public class SicauCourierIncomeService {
    
    @Resource
    private SicauCourierIncomeMapper incomeMapper;
    
    /**
     * 添加收入记录
     */
    public void addIncome(Integer courierId, Integer orderId, BigDecimal amount, BigDecimal distance) {
        SicauCourierIncome income = new SicauCourierIncome();
        income.setCourierId(courierId);
        income.setOrderId(orderId);
        income.setIncomeAmount(amount);
        income.setDistance(distance);
        income.setSettleStatus((byte) 0); // 未结算
        income.setAddTime(LocalDateTime.now());
        incomeMapper.insertSelective(income);
    }
    
    /**
     * 查询快递员收入记录
     */
    public List<SicauCourierIncome> findByCourierId(Integer courierId) {
        SicauCourierIncomeExample example = new SicauCourierIncomeExample();
        example.or().andCourierIdEqualTo(courierId);
        example.setOrderByClause("add_time DESC");
        return incomeMapper.selectByExample(example);
    }
    
    /**
     * 统计未结算收入
     */
    public BigDecimal getUnsettledIncome(Integer courierId) {
        // 实现统计逻辑
        List<SicauCourierIncome> incomes = findByCourierId(courierId);
        BigDecimal total = BigDecimal.ZERO;
        for (SicauCourierIncome income : incomes) {
            if (income.getSettleStatus() == 0) {
                total = total.add(income.getIncomeAmount());
            }
        }
        return total;
    }
}
