package org.linlinjava.litemall.admin.service;

import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.db.dao.SicauAdminLogMapper;
import org.linlinjava.litemall.db.domain.SicauAdminLog;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理员操作日志服务
 */
@Service
public class AdminLogService {
    
    @Resource
    private SicauAdminLogMapper adminLogMapper;
    
    /**
     * 记录操作日志
     * @param adminId 管理员ID
     * @param adminName 管理员用户名
     * @param actionType 操作类型
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @param actionDetail 操作详情
     */
    public void log(Integer adminId, String adminName, String actionType,
                    String targetType, Integer targetId, Map<String, Object> actionDetail) {
        SicauAdminLog log = new SicauAdminLog();
        log.setAdminId(adminId);
        log.setAdminName(adminName);
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setActionDetail(JacksonUtil.toJson(actionDetail));
        log.setIpAddress(getClientIp());
        log.setAddTime(LocalDateTime.now());
        
        adminLogMapper.insertSelective(log);
    }
    
    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        return ip;
    }
}
