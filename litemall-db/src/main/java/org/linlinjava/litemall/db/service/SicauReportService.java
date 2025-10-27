package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.SicauReportMapper;
import org.linlinjava.litemall.db.domain.SicauReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 举报与申诉服务
 * 
 * @author bmm-dev
 * @date 2025-10-27
 */
@Service
public class SicauReportService {
    
    @Resource
    private SicauReportMapper reportMapper;
    
    /**
     * 根据ID查询举报
     */
    public SicauReport findById(Integer id) {
        return reportMapper.selectByPrimaryKey(id);
    }
    
    /**
     * 根据订单ID查询举报列表
     */
    public List<SicauReport> findByOrderId(Integer orderId) {
        return reportMapper.selectByOrderId(orderId);
    }
    
    /**
     * 查询用户发起的举报列表（分页）
     */
    public List<SicauReport> queryByReporter(Integer reporterId, Byte status, 
                                              Integer page, Integer limit) {
        PageHelper.startPage(page, limit);
        return reportMapper.selectByReporter(reporterId, status, 
                                              (page - 1) * limit, limit);
    }
    
    /**
     * 查询针对某用户的举报列表（分页）
     */
    public List<SicauReport> queryByReported(Integer reportedId, Byte status, 
                                              Integer page, Integer limit) {
        PageHelper.startPage(page, limit);
        return reportMapper.selectByReported(reportedId, status, 
                                              (page - 1) * limit, limit);
    }
    
    /**
     * 查询所有举报列表（管理员用，分页）
     */
    public List<SicauReport> queryAllReports(Byte status, Byte type, 
                                              Integer page, Integer limit) {
        PageHelper.startPage(page, limit);
        return reportMapper.selectAllReports(status, type, 
                                              (page - 1) * limit, limit);
    }
    
    /**
     * 统计用户发起的举报数量
     */
    public long countByReporter(Integer reporterId, Byte status) {
        return reportMapper.countByReporter(reporterId, status);
    }
    
    /**
     * 统计针对某用户的举报数量
     */
    public long countByReported(Integer reportedId, Byte status) {
        return reportMapper.countByReported(reportedId, status);
    }
    
    /**
     * 统计所有举报数量（管理员用）
     */
    public long countAllReports(Byte status, Byte type) {
        return reportMapper.countAllReports(status, type);
    }
    
    /**
     * 提交举报
     */
    @Transactional
    public int addReport(SicauReport report) {
        report.setStatus((byte) 0); // 0-待处理
        report.setAddTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        report.setDeleted(false);
        return reportMapper.insertSelective(report);
    }
    
    /**
     * 更新举报状态
     */
    @Transactional
    public int updateStatus(Integer id, Byte status) {
        SicauReport report = new SicauReport();
        report.setId(id);
        report.setStatus(status);
        report.setUpdateTime(LocalDateTime.now());
        return reportMapper.updateByPrimaryKeySelective(report);
    }
    
    /**
     * 处理举报（管理员）
     */
    @Transactional
    public int handleReport(Integer id, Integer handlerAdminId, String handleResult, Byte status) {
        SicauReport report = new SicauReport();
        report.setId(id);
        report.setHandlerAdminId(handlerAdminId);
        report.setHandleResult(handleResult);
        report.setStatus(status); // 2-已解决 或 3-已驳回
        report.setHandleTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        return reportMapper.updateByPrimaryKeySelective(report);
    }
    
    /**
     * 删除举报（逻辑删除）
     */
    @Transactional
    public int deleteById(Integer id) {
        SicauReport report = new SicauReport();
        report.setId(id);
        report.setDeleted(true);
        report.setUpdateTime(LocalDateTime.now());
        return reportMapper.updateByPrimaryKeySelective(report);
    }
}
