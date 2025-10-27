package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauStudentAuthMapper;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 川农学生认证服务类
 */
@Service
public class SicauStudentAuthService {
    
    @Resource
    private SicauStudentAuthMapper authMapper;

    /**
     * 根据主键查询认证信息
     * @param id 主键ID
     * @return 认证信息
     */
    public SicauStudentAuth findById(Integer id) {
        return authMapper.selectByPrimaryKey(id);
    }

    /**
     * 根据用户ID查询认证信息
     * @param userId 用户ID
     * @return 认证信息
     */
    public SicauStudentAuth findByUserId(Integer userId) {
        return authMapper.selectByUserId(userId);
    }

    /**
     * 根据学号查询认证信息（学号已加密）
     * @param encryptedStudentNo 加密后的学号
     * @return 认证信息
     */
    public SicauStudentAuth findByStudentNo(String encryptedStudentNo) {
        return authMapper.selectByStudentNo(encryptedStudentNo);
    }

    /**
     * 根据状态查询认证列表
     * @param status 认证状态
     * @return 认证列表
     */
    public List<SicauStudentAuth> listByStatus(Byte status) {
        return authMapper.selectByStatus(status);
    }

    /**
     * 获取用户的认证状态
     * @param userId 用户ID
     * @return 认证状态：0-未认证，1-审核中，2-已认证，3-认证失败
     */
    public int getAuthStatus(Integer userId) {
        SicauStudentAuth auth = findByUserId(userId);
        if (auth == null) {
            return 0; // 未认证
        }
        return auth.getStatus() != null ? auth.getStatus().intValue() : 0;
    }

    /**
     * 添加认证记录
     * @param auth 认证信息
     */
    public void add(SicauStudentAuth auth) {
        auth.setAddTime(LocalDateTime.now());
        auth.setUpdateTime(LocalDateTime.now());
        auth.setDeleted(false);
        authMapper.insertSelective(auth);
    }

    /**
     * 更新认证记录
     * @param auth 认证信息
     * @return 影响行数
     */
    public int updateById(SicauStudentAuth auth) {
        auth.setUpdateTime(LocalDateTime.now());
        return authMapper.updateByPrimaryKeySelective(auth);
    }

    /**
     * 删除认证记录（逻辑删除）
     * @param id 主键ID
     * @return 影响行数
     */
    public int deleteById(Integer id) {
        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setId(id);
        auth.setDeleted(true);
        auth.setUpdateTime(LocalDateTime.now());
        return authMapper.updateByPrimaryKeySelective(auth);
    }

    /**
     * 提交认证申请
     * @param auth 认证信息
     */
    public void submitAuth(SicauStudentAuth auth) {
        auth.setStatus((byte) 1); // 审核中
        auth.setSubmitTime(LocalDateTime.now());
        add(auth);
    }

    /**
     * 审核通过
     * @param id 主键ID
     * @param adminId 审核管理员ID
     * @param auditorName 审核人姓名
     */
    public void approveAuth(Integer id, Integer adminId, String auditorName) {
        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setId(id);
        auth.setStatus((byte) 2); // 已认证
        auth.setAuditAdminId(adminId);
        auth.setAuditor(auditorName);
        auth.setAuditTime(LocalDateTime.now());
        updateById(auth);
    }

    /**
     * 审核拒绝
     * @param id 主键ID
     * @param adminId 审核管理员ID
     * @param auditorName 审核人姓名
     * @param failReason 失败原因
     */
    public void rejectAuth(Integer id, Integer adminId, String auditorName, String failReason) {
        SicauStudentAuth auth = new SicauStudentAuth();
        auth.setId(id);
        auth.setStatus((byte) 3); // 认证失败
        auth.setAuditAdminId(adminId);
        auth.setAuditor(auditorName);
        auth.setAuditTime(LocalDateTime.now());
        auth.setFailReason(failReason);
        updateById(auth);
    }
}
