# Epic 7 技术上下文：管理后台增强

**Epic ID**: 7  
**Epic 标题**: 管理后台增强  
**优先级**: P1  
**预估工时**: 32 小时  
**依赖关系**: 所有 Epic（需有业务数据后才能管理）  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)

---

## 1. Epic 概述

本 Epic 增强管理后台的内容审核和数据监控能力，为管理员提供高效的运营工具。核心功能包括：

- **学号认证审核**: 批量审核学号认证申请（Epic 1 中已实现前端，本 Epic 完善后台）
- **违规账号封禁**: 对违规用户进行冻结或永久封禁，并记录操作日志
- **交易纠纷处理**: 快速处理用户举报，支持强制退款
- **数据统计大屏**: 查看 DAU/MAU、GMV、热门分类等核心运营指标

**业务价值**: 
- 提升审核效率（批量操作）
- 降低运营成本（自动化数据统计）
- 快速响应用户投诉（纠纷处理）
- 数据驱动决策（运营大屏）

---

## 2. 架构决策引用

基于 `architecture.md` 中的以下关键决策：

### ADR-013: 权限管理设计
- **角色分级**: 
  - **一般管理员**: 审核学号、处理举报、24h 冻结用户
  - **高级管理员**: 永久封禁用户、查看敏感数据
  - **超级管理员**: 系统配置、权限分配
- **权限控制**: 基于 Spring Security + JWT
- **操作日志**: 所有管理操作记录日志（管理员 ID、时间、操作类型）

### ADR-014: 数据统计架构
- **实时数据**: 从 MySQL 实时查询（DAU/订单数）
- **历史数据**: 定时任务每日凌晨统计，存入汇总表
- **图表渲染**: 后端返回 JSON 数据，前端使用 ECharts 渲染

### 技术栈
- **后端框架**: Spring Boot 2.7.x
- **前端框架**: Vue 2.x + Element UI（复用 litemall-admin）
- **图表库**: ECharts 5.x
- **权限框架**: Spring Security

---

## 3. 数据库变更

### 3.1 新增表：sicau_admin_log (管理员操作日志表)

```sql
CREATE TABLE `sicau_admin_log` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `admin_id` INT NOT NULL COMMENT '管理员ID',
  `admin_name` VARCHAR(50) COMMENT '管理员用户名',
  `action_type` VARCHAR(50) NOT NULL COMMENT '操作类型: audit_auth, ban_user, handle_report',
  `target_type` VARCHAR(50) COMMENT '目标类型: user, order, report',
  `target_id` INT COMMENT '目标ID',
  `action_detail` TEXT COMMENT '操作详情（JSON）',
  `ip_address` VARCHAR(50) COMMENT '操作IP',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_admin_id` (`admin_id`),
  INDEX `idx_action_type` (`action_type`),
  INDEX `idx_add_time` (`add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志表';
```

### 3.2 新增表：sicau_daily_statistics (每日数据统计表)

```sql
CREATE TABLE `sicau_daily_statistics` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `dau` INT DEFAULT 0 COMMENT '日活用户数',
  `new_users` INT DEFAULT 0 COMMENT '新增用户数',
  `total_orders` INT DEFAULT 0 COMMENT '订单数',
  `paid_orders` INT DEFAULT 0 COMMENT '支付订单数',
  `gmv` DECIMAL(12,2) DEFAULT 0.00 COMMENT '成交额（元）',
  `avg_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '客单价（元）',
  `new_goods` INT DEFAULT 0 COMMENT '新增商品数',
  `add_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日数据统计表';
```

### 3.3 复用 litemall_user 表

**新增字段**:
```sql
ALTER TABLE `litemall_user` 
ADD COLUMN `ban_status` TINYINT DEFAULT 0 COMMENT '封禁状态: 0-正常, 1-24h冻结, 2-永久封禁' AFTER `status`,
ADD COLUMN `ban_reason` VARCHAR(200) COMMENT '封禁原因' AFTER `ban_status`,
ADD COLUMN `ban_time` DATETIME COMMENT '封禁时间' AFTER `ban_reason`,
ADD COLUMN `ban_expire_time` DATETIME COMMENT '解封时间（24h冻结时有效）' AFTER `ban_time`,
ADD INDEX `idx_ban_status` (`ban_status`);
```

---

## 4. 核心代码实现指导

### 4.1 管理员操作日志服务

创建 `litemall-admin-api/src/main/java/org/linlinjava/litemall/admin/service/AdminLogService.java`：

```java
package org.linlinjava.litemall.admin.service;

import com.alibaba.fastjson.JSON;
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
        log.setActionDetail(JSON.toJSONString(actionDetail));
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
```

### 4.2 用户封禁服务

在 `litemall-admin-api/src/main/java/org/linlinjava/litemall/admin/web/AdminUserController.java` 中新增：

```java
/**
 * 封禁用户
 */
@PostMapping("/ban")
public Object banUser(@LoginAdmin Integer adminId, 
                       @RequestBody Map<String, Object> body) {
    Integer userId = (Integer) body.get("userId");
    Integer banType = (Integer) body.get("banType"); // 1-24h冻结, 2-永久封禁
    String reason = (String) body.get("reason");
    
    // 1. 检查权限（永久封禁需要高级管理员）
    if (banType == 2 && !isHighLevelAdmin(adminId)) {
        return ResponseUtil.fail(403, "永久封禁需要高级管理员权限");
    }
    
    // 2. 更新用户封禁状态
    LitemallUser user = userService.findById(userId);
    user.setBanStatus(banType.byteValue());
    user.setBanReason(reason);
    user.setBanTime(LocalDateTime.now());
    
    if (banType == 1) {
        // 24h 冻结
        user.setBanExpireTime(LocalDateTime.now().plusHours(24));
    }
    
    userService.updateById(user);
    
    // 3. 记录操作日志
    Map<String, Object> detail = new HashMap<>();
    detail.put("userId", userId);
    detail.put("banType", banType);
    detail.put("reason", reason);
    adminLogService.log(adminId, getAdminName(), "ban_user", "user", userId, detail);
    
    // 4. 推送通知
    String banMsg = banType == 1 ? "您的账号已被冻结 24 小时" : "您的账号已被永久封禁";
    notifyService.notify(userId, banMsg + "，原因：" + reason);
    
    return ResponseUtil.ok();
}

/**
 * 检查是否高级管理员
 */
private boolean isHighLevelAdmin(Integer adminId) {
    LitemallAdmin admin = adminService.findById(adminId);
    // 假设 roleIds 包含 1（超级管理员）或 2（高级管理员）
    return admin.getRoleIds().contains(1) || admin.getRoleIds().contains(2);
}
```

### 4.3 交易纠纷处理

在 `litemall-admin-api/src/main/java/org/linlinjava/litemall/admin/web/AdminReportController.java` 中：

```java
package org.linlinjava.litemall.admin.web;

import org.linlinjava.litemall.admin.annotation.LoginAdmin;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.SicauReport;
import org.linlinjava.litemall.db.service.SicauReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 举报管理接口
 */
@RestController
@RequestMapping("/admin/report")
public class AdminReportController {
    
    @Autowired
    private SicauReportService reportService;
    
    @Autowired
    private AdminLogService adminLogService;
    
    /**
     * 举报列表
     */
    @GetMapping("/list")
    public Object list(@LoginAdmin Integer adminId,
                       @RequestParam(defaultValue = "0") Integer status,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size) {
        List<SicauReport> reports = reportService.queryByStatus(status, page, size);
        return ResponseUtil.okList(reports);
    }
    
    /**
     * 处理举报
     */
    @PostMapping("/handle")
    public Object handle(@LoginAdmin Integer adminId,
                         @RequestBody Map<String, Object> body) {
        Integer reportId = (Integer) body.get("reportId");
        Integer handleType = (Integer) body.get("handleType"); // 1-强制退款, 2-驳回举报, 3-协商
        String handleResult = (String) body.get("handleResult");
        
        SicauReport report = reportService.findById(reportId);
        
        // 1. 更新举报状态
        report.setStatus((byte) 2); // 已解决
        report.setHandlerAdminId(adminId);
        report.setHandleResult(handleResult);
        report.setHandleTime(LocalDateTime.now());
        reportService.update(report);
        
        // 2. 执行处理操作
        if (handleType == 1) {
            // 强制退款
            LitemallOrder order = orderService.findById(report.getOrderId());
            order.setOrderStatus((short) 103); // 已取消
            orderService.update(order);
            
            // 调用微信退款 API
            wxPayService.refund(order.getOrderSn(), order.getActualPrice());
        }
        
        // 3. 记录操作日志
        Map<String, Object> detail = new HashMap<>();
        detail.put("reportId", reportId);
        detail.put("handleType", handleType);
        detail.put("handleResult", handleResult);
        adminLogService.log(adminId, getAdminName(), "handle_report", "report", reportId, detail);
        
        // 4. 推送通知给举报人和被举报人
        notifyService.notify(report.getReporterId(), "您的举报已处理：" + handleResult);
        notifyService.notify(report.getReportedId(), "订单纠纷已处理：" + handleResult);
        
        return ResponseUtil.ok();
    }
}
```

### 4.4 数据统计服务

创建 `litemall-admin-api/src/main/java/org/linlinjava/litemall/admin/service/StatisticsService.java`：

```java
package org.linlinjava.litemall.admin.service;

import org.linlinjava.litemall.db.dao.LitemallOrderMapper;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.dao.SicauDailyStatisticsMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计服务
 */
@Service
public class StatisticsService {
    
    @Resource
    private LitemallUserMapper userMapper;
    
    @Resource
    private LitemallOrderMapper orderMapper;
    
    @Resource
    private SicauDailyStatisticsMapper dailyStatMapper;
    
    /**
     * 获取运营大屏数据
     */
    public Map<String, Object> getDashboard() {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 今日数据
        LocalDate today = LocalDate.now();
        result.put("today", getTodayStats(today));
        
        // 2. 本周订单趋势（最近 7 天）
        result.put("weekTrend", getWeekTrend());
        
        // 3. 热门分类（TOP 5）
        result.put("topCategories", getTopCategories());
        
        // 4. 信用等级分布
        result.put("creditDistribution", getCreditDistribution());
        
        return result;
    }
    
    /**
     * 今日数据
     */
    private Map<String, Object> getTodayStats(LocalDate date) {
        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime startTime = date.atStartOfDay();
        LocalDateTime endTime = date.plusDays(1).atStartOfDay();
        
        // DAU（日活用户）
        int dau = userMapper.countActiveUsers(startTime, endTime);
        stats.put("dau", dau);
        
        // 订单数
        int orderCount = orderMapper.countByDateRange(startTime, endTime);
        stats.put("orderCount", orderCount);
        
        // GMV（成交额）
        BigDecimal gmv = orderMapper.sumGmvByDateRange(startTime, endTime);
        stats.put("gmv", gmv);
        
        // 新增用户
        int newUsers = userMapper.countByAddTime(startTime, endTime);
        stats.put("newUsers", newUsers);
        
        return stats;
    }
    
    /**
     * 本周趋势（最近 7 天）
     */
    private List<Map<String, Object>> getWeekTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            SicauDailyStatistics stat = dailyStatMapper.selectByDate(date);
            
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            item.put("orderCount", stat != null ? stat.getTotalOrders() : 0);
            item.put("gmv", stat != null ? stat.getGmv() : 0);
            
            trend.add(item);
        }
        
        return trend;
    }
    
    /**
     * 热门分类 TOP 5
     */
    private List<Map<String, Object>> getTopCategories() {
        return orderMapper.selectTopCategories(5);
    }
    
    /**
     * 信用等级分布
     */
    private Map<String, Integer> getCreditDistribution() {
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("level1", userMapper.countByCreditLevel(1)); // 0-59
        distribution.put("level2", userMapper.countByCreditLevel(2)); // 60-69
        distribution.put("level3", userMapper.countByCreditLevel(3)); // 70-89
        distribution.put("level4", userMapper.countByCreditLevel(4)); // 90-100
        return distribution;
    }
}
```

### 4.5 每日统计定时任务

创建 `litemall-admin-api/src/main/java/org/linlinjava/litemall/admin/task/DailyStatisticsTask.java`：

```java
package org.linlinjava.litemall.admin.task;

import org.linlinjava.litemall.db.domain.SicauDailyStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日数据统计定时任务
 */
@Component
public class DailyStatisticsTask {
    
    @Autowired
    private StatisticsService statisticsService;
    
    /**
     * 每天凌晨 1 点执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void statisticsDaily() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        Map<String, Object> stats = statisticsService.getTodayStats(yesterday);
        
        SicauDailyStatistics dailyStat = new SicauDailyStatistics();
        dailyStat.setStatDate(yesterday);
        dailyStat.setDau((Integer) stats.get("dau"));
        dailyStat.setNewUsers((Integer) stats.get("newUsers"));
        dailyStat.setTotalOrders((Integer) stats.get("orderCount"));
        dailyStat.setGmv((BigDecimal) stats.get("gmv"));
        
        dailyStatMapper.insertSelective(dailyStat);
    }
}
```

---

## 5. API 契约定义

### 5.1 GET /admin/user/authList - 学号认证审核列表

**请求参数**:
- `status`: 审核状态（0-待审核, 1-已通过, 2-已拒绝）
- `page`, `size`

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "total": 10,
    "list": [
      {
        "id": 1,
        "userId": 123,
        "nickname": "川农学子",
        "studentNo": "2021****",
        "college": "信息工程学院",
        "idCardPhoto": "https://...",
        "addTime": "2025-10-27 10:00:00"
      }
    ]
  }
}
```

### 5.2 POST /admin/user/ban - 封禁用户

**请求体**:
```json
{
  "userId": 123,
  "banType": 1,
  "reason": "发布违规内容"
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "封禁成功"
}
```

### 5.3 GET /admin/report/list - 举报列表

**请求参数**:
- `status`: 处理状态（0-待处理, 1-处理中, 2-已解决, 3-已驳回）
- `page`, `size`

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "total": 5,
    "list": [
      {
        "id": 1,
        "orderId": 100,
        "orderSn": "20251027123456",
        "reporterNickname": "买家A",
        "reportedNickname": "卖家B",
        "type": 1,
        "typeName": "描述不符",
        "reason": "商品描述与实物不符...",
        "images": ["https://...", "https://..."],
        "addTime": "2025-10-27 10:00:00"
      }
    ]
  }
}
```

### 5.4 POST /admin/report/handle - 处理举报

**请求体**:
```json
{
  "reportId": 1,
  "handleType": 1,
  "handleResult": "经核实，商品确实与描述不符，已强制退款"
}
```

**响应体**:
```json
{
  "errno": 0,
  "errmsg": "处理成功"
}
```

### 5.5 GET /admin/statistics/dashboard - 数据统计大屏

**响应体**:
```json
{
  "errno": 0,
  "data": {
    "today": {
      "dau": 2350,
      "orderCount": 128,
      "gmv": 15320.00,
      "newUsers": 56
    },
    "weekTrend": [
      {"date": "2025-10-21", "orderCount": 95, "gmv": 12000.00},
      {"date": "2025-10-22", "orderCount": 110, "gmv": 13500.00}
    ],
    "topCategories": [
      {"categoryName": "教材教辅", "count": 50, "percentage": 0.40},
      {"categoryName": "数码产品", "count": 38, "percentage": 0.30}
    ],
    "creditDistribution": {
      "level1": 100,
      "level2": 200,
      "level3": 300,
      "level4": 50
    }
  }
}
```

---

## 6. 配置文件变更

### 6.1 application-admin.yml

```yaml
# 管理后台配置
admin:
  # 权限配置
  permission:
    high_level_roles: [1, 2]  # 高级管理员角色ID
  
  # 统计配置
  statistics:
    cache_ttl: 300  # 缓存 5 分钟
```

---

## 7. 前端开发要点

### 7.1 管理后台关键文件

- `litemall-admin/src/views/user/authList.vue` - 学号认证审核页
- `litemall-admin/src/views/user/userList.vue` - 用户管理（增加封禁功能）
- `litemall-admin/src/views/report/list.vue` - 举报列表页
- `litemall-admin/src/views/statistics/dashboard.vue` - 数据统计大屏

### 7.2 ECharts 图表示例

```vue
<template>
  <div>
    <div ref="weekTrendChart" style="width: 100%; height: 400px"></div>
  </div>
</template>

<script>
import * as echarts from 'echarts';

export default {
  mounted() {
    this.renderWeekTrend();
  },
  
  methods: {
    renderWeekTrend() {
      const chart = echarts.init(this.$refs.weekTrendChart);
      
      // 从接口获取数据
      this.$http.get('/admin/statistics/dashboard').then(res => {
        const weekTrend = res.data.data.weekTrend;
        
        const option = {
          title: { text: '本周订单趋势' },
          xAxis: {
            type: 'category',
            data: weekTrend.map(item => item.date)
          },
          yAxis: { type: 'value' },
          series: [{
            name: '订单数',
            type: 'line',
            data: weekTrend.map(item => item.orderCount),
            smooth: true
          }]
        };
        
        chart.setOption(option);
      });
    }
  }
}
</script>
```

---

## 8. 测试策略

### 8.1 单元测试

- 权限验证测试（一般管理员无法永久封禁）
- 数据统计准确性测试（DAU/GMV 计算）
- 日志记录完整性测试

### 8.2 集成测试

- 完整封禁流程测试（封禁 → 通知 → 日志记录）
- 举报处理流程测试（强制退款 → 退款成功）
- 定时统计任务测试（凌晨 1 点执行）

### 8.3 性能测试

- 数据大屏加载性能（< 1s）
- 举报列表查询性能（1000+ 举报 < 500ms）

---

## 9. 依赖关系

### 前置条件
- 所有 Epic 已完成（需要有业务数据）
- 管理后台框架已搭建（litemall-admin）

### 后续依赖
- 无直接依赖

---

## 10. 风险提示

1. **权限滥用**: 高级管理员权限过大，需严格控制人员
2. **误封风险**: 永久封禁不可逆，需谨慎操作
3. **数据安全**: 运营数据敏感，需权限控制
4. **举报滥用**: 恶意举报可能增加管理员工作量
5. **图表性能**: 大量数据可能导致图表渲染缓慢

---

## 11. Story 任务分解

### Story 7.1: 学号认证审核 (6h)
- Task 1: 完善 `GET /admin/user/authList` 接口（Epic 1 中已实现部分）
- Task 2: 前端审核列表页开发（查看大图）
- Task 3: 批量通过/拒绝功能
- Task 4: 审核通知推送

### Story 7.2: 违规账号封禁 (8h)
- Task 1: 扩展 `litemall_user` 表（ban_status 字段）
- Task 2: 创建 `sicau_admin_log` 表
- Task 3: 实现 `AdminLogService`
- Task 4: 实现 `ban()` 接口
- Task 5: 前端用户管理页增加封禁按钮
- Task 6: 权限验证（高级管理员才能永久封禁）

### Story 7.3: 交易纠纷处理 (10h)
- Task 1: 创建 `AdminReportController`
- Task 2: 实现 `list()` 接口
- Task 3: 实现 `handle()` 接口（强制退款/驳回/协商）
- Task 4: 前端举报列表页开发
- Task 5: 查看订单详情和证据图片
- Task 6: 推送处理结果通知

### Story 7.4: 数据统计大屏 (8h)
- Task 1: 创建 `sicau_daily_statistics` 表
- Task 2: 实现 `StatisticsService`
- Task 3: 创建 `DailyStatisticsTask` 定时任务
- Task 4: 实现 `getDashboard()` 接口
- Task 5: 前端统计大屏页面开发（ECharts）
- Task 6: 支持导出 Excel 报表（可选）

---

## 12. 验收清单

- [ ] 管理后台可查看待审核学号认证
- [ ] 支持批量通过/拒绝学号认证
- [ ] 一般管理员可 24h 冻结用户
- [ ] 高级管理员可永久封禁用户
- [ ] 所有封禁操作记录操作日志
- [ ] 举报列表正确显示待处理举报
- [ ] 可强制退款处理举报
- [ ] 数据统计大屏正确显示 DAU/GMV/订单趋势
- [ ] 热门分类和信用等级分布图表正常渲染
- [ ] 每日定时任务正常执行（凌晨 1 点）
- [ ] 所有 API 无编译错误

---

**Epic 状态**: 准备就绪，等待开发  
**下一步**: 由 bmm-dev 开发者开始实施
