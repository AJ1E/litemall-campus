package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.SicauOrderRefundMapper;
import org.linlinjava.litemall.db.domain.SicauOrderRefund;
import org.linlinjava.litemall.db.domain.SicauOrderRefundExample;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * 订单退款服务
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Service
public class SicauOrderRefundService {
    
    @Resource
    private SicauOrderRefundMapper refundMapper;
    
    /**
     * 生成退款流水号
     * 格式: RF + yyyyMMddHHmmss + 6位随机数
     */
    private String generateRefundSn() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        Random random = new Random();
        int randomNum = random.nextInt(900000) + 100000; // 6位随机数
        return "RF" + timestamp + randomNum;
    }
    
    /**
     * 根据ID查询退款记录
     */
    public SicauOrderRefund findById(Integer id) {
        return refundMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 根据订单ID查询退款记录
     */
    public SicauOrderRefund findByOrderId(Integer orderId) {
        SicauOrderRefundExample example = new SicauOrderRefundExample();
        example.or().andOrderIdEqualTo(orderId);
        example.setOrderByClause("add_time DESC");
        List<SicauOrderRefund> list = refundMapper.selectByExample(example);
        return list.isEmpty() ? null : list.get(0);
    }
    
    /**
     * 根据退款流水号查询退款记录
     */
    public SicauOrderRefund findByRefundSn(String refundSn) {
        SicauOrderRefundExample example = new SicauOrderRefundExample();
        example.or().andRefundSnEqualTo(refundSn);
        List<SicauOrderRefund> list = refundMapper.selectByExample(example);
        return list.isEmpty() ? null : list.get(0);
    }
    
    /**
     * 查询退款列表（按状态，分页）
     */
    public List<SicauOrderRefund> queryByStatus(Byte refundStatus, Integer page, Integer limit) {
        SicauOrderRefundExample example = new SicauOrderRefundExample();
        example.or().andRefundStatusEqualTo(refundStatus);
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(page, limit);
        return refundMapper.selectByExample(example);
    }
    
    /**
     * 统计退款记录数量（按状态）
     */
    public long countByStatus(Byte refundStatus) {
        SicauOrderRefundExample example = new SicauOrderRefundExample();
        example.or().andRefundStatusEqualTo(refundStatus);
        return refundMapper.countByExample(example);
    }
    
    /**
     * 创建退款记录
     * @param orderId 订单ID
     * @param refundAmount 退款金额
     * @param refundReason 退款原因
     * @param refundType 退款类型 1-用户主动取消 2-超时未支付 3-举报退款
     * @return 影响行数
     */
    @Transactional
    public int createRefund(Integer orderId, BigDecimal refundAmount, 
                            String refundReason, Byte refundType) {
        SicauOrderRefund refund = new SicauOrderRefund();
        refund.setOrderId(orderId);
        refund.setRefundSn(generateRefundSn());
        refund.setRefundAmount(refundAmount);
        refund.setRefundReason(refundReason);
        refund.setRefundType(refundType);
        refund.setRefundStatus((byte) 0); // 0-待退款
        refund.setAddTime(LocalDateTime.now());
        refund.setUpdateTime(LocalDateTime.now());
        return refundMapper.insertSelective(refund);
    }
    
        /**
     * 更新退款状态（仅状态）
     * 
     * @param id 退款记录ID
     * @param refundStatus 新状态
     * @return 影响行数
     */
    @Transactional
    public int updateRefundStatus(Integer id, Byte refundStatus) {
        SicauOrderRefund refund = new SicauOrderRefund();
        refund.setId(id);
        refund.setRefundStatus(refundStatus);
        
        // 若状态变更为"退款成功"，记录退款完成时间
        if (refundStatus == 2) {
            refund.setRefundTime(LocalDateTime.now());
        }
        
        refund.setUpdateTime(LocalDateTime.now());
        return refundMapper.updateByPrimaryKeySelective(refund);
    }
    
    /**
     * 更新退款状态（含管理员备注）
     * 
     * @param id 退款记录ID
     * @param refundStatus 新状态
     * @param adminNote 管理员备注
     * @return 影响行数
     */
    @Transactional
    public int updateRefundStatusWithNote(Integer id, Byte refundStatus, String adminNote) {
        SicauOrderRefund refund = new SicauOrderRefund();
        refund.setId(id);
        refund.setRefundStatus(refundStatus);
        refund.setAdminNote(adminNote);
        
        // 若状态变更为"退款成功"，记录退款完成时间
        if (refundStatus == 2) {
            refund.setRefundTime(LocalDateTime.now());
        }
        
        refund.setUpdateTime(LocalDateTime.now());
        return refundMapper.updateByPrimaryKeySelective(refund);
    }
    
    /**
     * 确认退款（记录第三方退款ID）
     * 
     * @param id 退款记录ID
     * @param status 新状态（通常为 2-退款成功）
     * @param refundId 第三方退款交易号
     * @return 影响行数
     */
    @Transactional
    public int confirmRefund(Integer id, Byte status, String refundId) {
        SicauOrderRefund refund = new SicauOrderRefund();
        refund.setId(id);
        refund.setRefundStatus(status);
        refund.setRefundTime(LocalDateTime.now());
        refund.setUpdateTime(LocalDateTime.now());
        // TODO: 如需记录第三方退款ID，需在 domain 中添加字段
        return refundMapper.updateByPrimaryKeySelective(refund);
    }
    
    /**
     * 删除退款记录（逻辑删除，通过将 refundStatus 设为特定值实现）
     * 
     * @param id 退款记录ID
     * @return 影响行数
     */
    @Transactional
    public int deleteById(Integer id) {
        // 方案1：逻辑删除，设置状态为已取消（3）
        SicauOrderRefund refund = new SicauOrderRefund();
        refund.setId(id);
        refund.setRefundStatus((byte) 3); // 3-退款失败/已取消
        refund.setUpdateTime(LocalDateTime.now());
        return refundMapper.updateByPrimaryKeySelective(refund);
        
        // 方案2：如果表有 deleted 字段，可以用以下代码
        // refund.setDeleted(true);
        // return refundMapper.updateByPrimaryKeySelective(refund);
    }
    
    /**
     * 更新退款状态（根据订单ID）
     */
    @Transactional
    public int updateRefundStatusByOrderId(Integer orderId, Byte refundStatus) {
        SicauOrderRefund existingRefund = findByOrderId(orderId);
        if (existingRefund == null) {
            return 0;
        }
        return updateRefundStatus(existingRefund.getId(), refundStatus);
    }
}
