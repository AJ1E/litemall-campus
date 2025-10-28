package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauCourierWithdrawMapper;
import org.linlinjava.litemall.db.domain.SicauCourierWithdraw;
import org.linlinjava.litemall.db.domain.SicauCourierWithdrawExample;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 快递员提现服务
 */
@Service
public class SicauCourierWithdrawService {
    
    @Resource
    private SicauCourierWithdrawMapper withdrawMapper;
    
    /**
     * 创建提现记录
     * 
     * @param courierId 快递员用户ID
     * @param withdrawAmount 提现金额
     * @param feeAmount 手续费
     * @return 提现记录
     */
    public SicauCourierWithdraw createWithdraw(Integer courierId, BigDecimal withdrawAmount, BigDecimal feeAmount) {
        SicauCourierWithdraw withdraw = new SicauCourierWithdraw();
        withdraw.setCourierId(courierId);
        withdraw.setWithdrawSn(generateWithdrawSn(courierId));
        withdraw.setWithdrawAmount(withdrawAmount);
        withdraw.setFeeAmount(feeAmount);
        withdraw.setActualAmount(withdrawAmount.subtract(feeAmount));
        withdraw.setStatus((byte) 0); // 待处理
        withdraw.setAddTime(LocalDateTime.now());
        withdraw.setDeleted(false);
        
        withdrawMapper.insertSelective(withdraw);
        return withdraw;
    }
    
    /**
     * 生成提现单号
     * 格式: WD + yyyyMMddHHmmss + 用户ID后4位
     */
    private String generateWithdrawSn(Integer courierId) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String userIdSuffix = String.format("%04d", courierId % 10000);
        return "WD" + timestamp + userIdSuffix;
    }
    
    /**
     * 查询快递员提现记录
     * 
     * @param courierId 快递员用户ID
     * @return 提现记录列表（按时间倒序）
     */
    public List<SicauCourierWithdraw> findByCourierId(Integer courierId) {
        SicauCourierWithdrawExample example = new SicauCourierWithdrawExample();
        example.or()
            .andCourierIdEqualTo(courierId)
            .andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        
        return withdrawMapper.selectByExample(example);
    }
    
    /**
     * 计算已提现总金额（包括处理中的）
     * 
     * @param courierId 快递员用户ID
     * @return 已提现金额（status=0或1）
     */
    public BigDecimal getTotalWithdrawn(Integer courierId) {
        SicauCourierWithdrawExample example = new SicauCourierWithdrawExample();
        example.or()
            .andCourierIdEqualTo(courierId)
            .andStatusIn(List.of((byte) 0, (byte) 1))  // 待处理 + 已到账
            .andDeletedEqualTo(false);
        
        List<SicauCourierWithdraw> withdraws = withdrawMapper.selectByExample(example);
        
        return withdraws.stream()
            .map(SicauCourierWithdraw::getWithdrawAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 根据ID查询
     */
    public SicauCourierWithdraw findById(Integer id) {
        return withdrawMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 更新提现记录
     */
    public int updateById(SicauCourierWithdraw withdraw) {
        withdraw.setUpdateTime(LocalDateTime.now());
        return withdrawMapper.updateByPrimaryKeySelective(withdraw);
    }
}
