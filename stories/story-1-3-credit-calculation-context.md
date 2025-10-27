# 用户故事 1.3 开发上下文：信用积分计算

**Story ID**: 1.3  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)  
**目标读者**: bmm-dev (Developer Agent)

本文档为开发人员提供实现"信用积分自动计算系统"的精确代码指导和实现路径。

---

## 1. 实现概览

### 核心设计理念
信用积分系统是一个**事件驱动**的自动化系统，通过监听用户行为（完成交易、收到评价、取消订单等）自动触发积分变动。

### 积分计算流程
```
用户触发行为（如：确认收货）
        ↓
业务代码调用 CreditScoreService.updateCredit()
        ↓
计算积分变动（+10）
        ↓
应用范围限制（0 ≤ score ≤ 1000）
        ↓
使用乐观锁更新 litemall_user.credit_score
        ↓
插入 sicau_credit_log 日志记录
        ↓
发送微信服务通知
        ↓
检查是否升级（新手 → 良好 → 优秀 → 信誉商家）
```

---

## 2. 后端代码实现

### 2.1 文件结构

```
litemall-db/
└── src/main/java/org/linlinjava/litemall/db/
    ├── domain/
    │   └── SicauCreditLog.java              (新增 - 积分日志实体)
    ├── dao/
    │   ├── SicauCreditLogMapper.java        (新增 - Mapper 接口)
    │   └── LitemallUserMapper.java          (修改 - 添加乐观锁更新)
    └── service/
        ├── SicauCreditLogService.java       (新增 - 日志服务)
        └── CreditScoreService.java          (新增 - 核心积分服务)

litemall-core/
└── src/main/java/org/linlinjava/litemall/core/
    ├── util/
    │   └── WxNotifyUtil.java                (新增 - 微信通知工具)
    └── enums/
        ├── CreditChangeReasonEnum.java      (新增 - 积分变动原因枚举)
        └── CreditLevelEnum.java             (新增 - 信用等级枚举)

litemall-wx-api/
└── src/main/java/org/linlinjava/litemall/wx/
    ├── web/
    │   └── WxUserController.java            (修改 - 添加积分接口)
    └── service/
        └── WxOrderService.java              (修改 - 确认收货时调用积分更新)
```

---

## 2.2 数据库实体类

### SicauCreditLog.java

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/domain/SicauCreditLog.java`

```java
package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

public class SicauCreditLog {
    private Integer id;
    private Integer userId;
    private Integer scoreChange;
    private String reason;
    private String relatedType;    // order, comment, goods, donation, delivery_order
    private Integer relatedId;
    private Integer beforeScore;
    private Integer afterScore;
    private LocalDateTime createTime;
    
    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    
    public Integer getScoreChange() { return scoreChange; }
    public void setScoreChange(Integer scoreChange) { this.scoreChange = scoreChange; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public String getRelatedType() { return relatedType; }
    public void setRelatedType(String relatedType) { this.relatedType = relatedType; }
    
    public Integer getRelatedId() { return relatedId; }
    public void setRelatedId(Integer relatedId) { this.relatedId = relatedId; }
    
    public Integer getBeforeScore() { return beforeScore; }
    public void setBeforeScore(Integer beforeScore) { this.beforeScore = beforeScore; }
    
    public Integer getAfterScore() { return afterScore; }
    public void setAfterScore(Integer afterScore) { this.afterScore = afterScore; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
```

---

## 2.3 枚举类定义

### CreditChangeReasonEnum.java

**文件**: `litemall-core/src/main/java/org/linlinjava/litemall/core/enums/CreditChangeReasonEnum.java`

```java
package org.linlinjava.litemall.core.enums;

public enum CreditChangeReasonEnum {
    COMPLETE_ORDER(10, "完成交易", "order"),
    GOOD_REVIEW(5, "收到好评", "comment"),
    BAD_REVIEW(-5, "收到差评", "comment"),
    CANCEL_ORDER(-5, "取消订单", "order"),
    VIOLATION_GOODS(-50, "违规商品下架", "goods"),
    COMPLETE_DONATION(20, "完成捐赠", "donation"),
    ON_TIME_DELIVERY(2, "准时配送", "delivery_order"),
    LATE_DELIVERY(-10, "配送超时", "delivery_order");
    
    private final int scoreChange;
    private final String description;
    private final String relatedType;
    
    CreditChangeReasonEnum(int scoreChange, String description, String relatedType) {
        this.scoreChange = scoreChange;
        this.description = description;
        this.relatedType = relatedType;
    }
    
    public int getScoreChange() { return scoreChange; }
    public String getDescription() { return description; }
    public String getRelatedType() { return relatedType; }
}
```

### CreditLevelEnum.java

**文件**: `litemall-core/src/main/java/org/linlinjava/litemall/core/enums/CreditLevelEnum.java`

```java
package org.linlinjava.litemall.core.enums;

public enum CreditLevelEnum {
    NEWBIE(0, 100, "新手", "⭐"),
    GOOD(101, 300, "良好", "⭐⭐"),
    EXCELLENT(301, 500, "优秀", "⭐⭐⭐"),
    TRUSTED(501, 1000, "信誉商家", "⭐⭐⭐⭐");
    
    private final int minScore;
    private final int maxScore;
    private final String levelName;
    private final String icon;
    
    CreditLevelEnum(int minScore, int maxScore, String levelName, String icon) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.levelName = levelName;
        this.icon = icon;
    }
    
    public static CreditLevelEnum getByScore(int score) {
        if (score <= 100) return NEWBIE;
        if (score <= 300) return GOOD;
        if (score <= 500) return EXCELLENT;
        return TRUSTED;
    }
    
    public int getMinScore() { return minScore; }
    public int getMaxScore() { return maxScore; }
    public String getLevelName() { return levelName; }
    public String getIcon() { return icon; }
}
```

---

## 2.4 Mapper 层

### SicauCreditLogMapper.java

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/dao/SicauCreditLogMapper.java`

```java
package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauCreditLog;

import java.util.List;

public interface SicauCreditLogMapper {
    int insert(SicauCreditLog log);
    List<SicauCreditLog> selectByUserId(@Param("userId") Integer userId, 
                                         @Param("offset") Integer offset, 
                                         @Param("limit") Integer limit);
    int countByUserId(Integer userId);
}
```

**XML 文件**: `litemall-db/src/main/resources/org/linlinjava/litemall/db/dao/SicauCreditLogMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.linlinjava.litemall.db.dao.SicauCreditLogMapper">
  <resultMap id="BaseResultMap" type="org.linlinjava.litemall.db.domain.SicauCreditLog">
    <id column="id" jdbcType="INTEGER" property="id" />
    <result column="user_id" jdbcType="INTEGER" property="userId" />
    <result column="score_change" jdbcType="INTEGER" property="scoreChange" />
    <result column="reason" jdbcType="VARCHAR" property="reason" />
    <result column="related_type" jdbcType="VARCHAR" property="relatedType" />
    <result column="related_id" jdbcType="INTEGER" property="relatedId" />
    <result column="before_score" jdbcType="INTEGER" property="beforeScore" />
    <result column="after_score" jdbcType="INTEGER" property="afterScore" />
    <result column="create_time" jdbcType="TIMESTAMP" property="createTime" />
  </resultMap>

  <insert id="insert" parameterType="org.linlinjava.litemall.db.domain.SicauCreditLog">
    INSERT INTO sicau_credit_log (user_id, score_change, reason, related_type, related_id, before_score, after_score)
    VALUES (#{userId}, #{scoreChange}, #{reason}, #{relatedType}, #{relatedId}, #{beforeScore}, #{afterScore})
  </insert>

  <select id="selectByUserId" resultMap="BaseResultMap">
    SELECT * FROM sicau_credit_log 
    WHERE user_id = #{userId} 
    ORDER BY create_time DESC 
    LIMIT #{offset}, #{limit}
  </select>

  <select id="countByUserId" resultType="java.lang.Integer">
    SELECT COUNT(*) FROM sicau_credit_log WHERE user_id = #{userId}
  </select>
</mapper>
```

### LitemallUserMapper 扩展

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/dao/LitemallUserMapper.java`

在现有接口中添加：

```java
/**
 * 使用乐观锁更新用户积分
 * @param userId 用户ID
 * @param oldScore 旧积分（用于乐观锁）
 * @param newScore 新积分
 * @return 更新行数（1表示成功，0表示失败）
 */
int updateCreditScoreWithOptimisticLock(@Param("userId") Integer userId, 
                                         @Param("oldScore") Integer oldScore, 
                                         @Param("newScore") Integer newScore);
```

**XML 文件**: `litemall-db/src/main/resources/org/linlinjava/litemall/db/dao/LitemallUserMapper.xml`

```xml
<update id="updateCreditScoreWithOptimisticLock">
  UPDATE litemall_user
  SET credit_score = #{newScore}
  WHERE id = #{userId} AND credit_score = #{oldScore}
</update>
```

---

## 2.5 Service 层

### SicauCreditLogService.java

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauCreditLogService.java`

```java
package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauCreditLogMapper;
import org.linlinjava.litemall.db.domain.SicauCreditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SicauCreditLogService {
    
    @Autowired
    private SicauCreditLogMapper logMapper;
    
    public int add(SicauCreditLog log) {
        return logMapper.insert(log);
    }
    
    public List<SicauCreditLog> queryByUserId(Integer userId, Integer page, Integer limit) {
        int offset = (page - 1) * limit;
        return logMapper.selectByUserId(userId, offset, limit);
    }
    
    public int countByUserId(Integer userId) {
        return logMapper.countByUserId(userId);
    }
}
```

---

### CreditScoreService.java（核心服务）

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/service/CreditScoreService.java`

```java
package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.core.enums.CreditChangeReasonEnum;
import org.linlinjava.litemall.core.enums.CreditLevelEnum;
import org.linlinjava.litemall.core.util.WxNotifyUtil;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.SicauCreditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditScoreService {
    
    private static final Logger logger = LoggerFactory.getLogger(CreditScoreService.class);
    
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 1000;
    private static final int MAX_RETRY = 3;  // 乐观锁最大重试次数
    
    @Autowired
    private LitemallUserMapper userMapper;
    
    @Autowired
    private SicauCreditLogService creditLogService;
    
    @Autowired
    private WxNotifyUtil wxNotifyUtil;
    
    /**
     * 更新用户积分（带重试机制）
     * @param userId 用户ID
     * @param reason 积分变动原因
     * @param relatedId 关联实体ID（如订单ID、评论ID等）
     * @return true-成功, false-失败
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCredit(Integer userId, CreditChangeReasonEnum reason, Integer relatedId) {
        int retryCount = 0;
        
        while (retryCount < MAX_RETRY) {
            try {
                // 1. 查询当前积分
                LitemallUser user = userMapper.selectByPrimaryKey(userId);
                if (user == null) {
                    logger.error("用户不存在: userId={}", userId);
                    return false;
                }
                
                int beforeScore = user.getCreditScore();
                int scoreChange = reason.getScoreChange();
                
                // 2. 计算新积分（应用范围限制）
                int afterScore = beforeScore + scoreChange;
                int actualChange = scoreChange;
                
                if (afterScore < MIN_SCORE) {
                    afterScore = MIN_SCORE;
                    actualChange = afterScore - beforeScore;
                } else if (afterScore > MAX_SCORE) {
                    afterScore = MAX_SCORE;
                    actualChange = afterScore - beforeScore;
                }
                
                // 3. 使用乐观锁更新积分
                int updateCount = userMapper.updateCreditScoreWithOptimisticLock(userId, beforeScore, afterScore);
                
                if (updateCount == 0) {
                    // 乐观锁失败，重试
                    retryCount++;
                    logger.warn("乐观锁冲突，重试第 {} 次: userId={}", retryCount, userId);
                    Thread.sleep(50);  // 等待 50ms 后重试
                    continue;
                }
                
                // 4. 插入积分日志
                SicauCreditLog log = new SicauCreditLog();
                log.setUserId(userId);
                log.setScoreChange(actualChange);
                log.setReason(reason.getDescription());
                log.setRelatedType(reason.getRelatedType());
                log.setRelatedId(relatedId);
                log.setBeforeScore(beforeScore);
                log.setAfterScore(afterScore);
                creditLogService.add(log);
                
                // 5. 发送微信通知
                sendWxNotify(user, actualChange, reason.getDescription(), afterScore);
                
                // 6. 检查是否升级
                checkLevelUpgrade(user, beforeScore, afterScore);
                
                logger.info("积分更新成功: userId={}, reason={}, change={}, before={}, after={}", 
                           userId, reason.getDescription(), actualChange, beforeScore, afterScore);
                
                return true;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("积分更新被中断: userId={}", userId, e);
                return false;
            } catch (Exception e) {
                logger.error("积分更新异常: userId={}, reason={}", userId, reason, e);
                throw e;
            }
        }
        
        logger.error("积分更新失败（超过最大重试次数）: userId={}, reason={}", userId, reason);
        return false;
    }
    
    /**
     * 发送微信服务通知
     */
    private void sendWxNotify(LitemallUser user, int scoreChange, String reason, int afterScore) {
        try {
            String message;
            if (scoreChange > 0) {
                message = String.format("恭喜您获得 +%d 积分！\n原因：%s\n当前总积分：%d", 
                                       scoreChange, reason, afterScore);
            } else {
                message = String.format("您的积分被扣除 %d 分\n原因：%s\n当前总积分：%d", 
                                       scoreChange, reason, afterScore);
            }
            
            wxNotifyUtil.sendServiceNotify(user.getWeixinOpenid(), "积分变动通知", message);
        } catch (Exception e) {
            logger.error("发送微信通知失败: userId={}", user.getId(), e);
        }
    }
    
    /**
     * 检查是否升级
     */
    private void checkLevelUpgrade(LitemallUser user, int beforeScore, int afterScore) {
        CreditLevelEnum beforeLevel = CreditLevelEnum.getByScore(beforeScore);
        CreditLevelEnum afterLevel = CreditLevelEnum.getByScore(afterScore);
        
        if (beforeLevel != afterLevel) {
            logger.info("用户信用等级升级: userId={}, {} → {}", 
                       user.getId(), beforeLevel.getLevelName(), afterLevel.getLevelName());
            
            // 发送升级通知
            try {
                String message = String.format("恭喜您的信用等级提升至【%s】%s", 
                                              afterLevel.getLevelName(), afterLevel.getIcon());
                wxNotifyUtil.sendServiceNotify(user.getWeixinOpenid(), "信用等级提升", message);
            } catch (Exception e) {
                logger.error("发送升级通知失败: userId={}", user.getId(), e);
            }
        }
    }
    
    /**
     * 查询用户当前信用等级
     */
    public CreditLevelEnum getCreditLevel(Integer userId) {
        LitemallUser user = userMapper.selectByPrimaryKey(userId);
        if (user == null) {
            return CreditLevelEnum.NEWBIE;
        }
        return CreditLevelEnum.getByScore(user.getCreditScore());
    }
}
```

---

## 2.6 微信通知工具类

**文件**: `litemall-core/src/main/java/org/linlinjava/litemall/core/util/WxNotifyUtil.java`

```java
package org.linlinjava.litemall.core.util;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WxNotifyUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(WxNotifyUtil.class);
    
    @Autowired
    private WxMaService wxMaService;
    
    /**
     * 发送微信订阅消息（服务通知）
     * @param openid 用户 openid
     * @param title 通知标题
     * @param content 通知内容
     */
    public void sendServiceNotify(String openid, String title, String content) {
        try {
            WxMaSubscribeMessage message = new WxMaSubscribeMessage();
            message.setToUser(openid);
            message.setTemplateId("YOUR_TEMPLATE_ID");  // TODO: 替换为实际模板ID
            message.setPage("pages/user/user");
            
            List<WxMaSubscribeMessage.MsgData> data = new ArrayList<>();
            data.add(new WxMaSubscribeMessage.MsgData("thing1", title));
            data.add(new WxMaSubscribeMessage.MsgData("thing2", content));
            message.setData(data);
            
            wxMaService.getMsgService().sendSubscribeMsg(message);
            logger.info("微信通知发送成功: openid={}, title={}", openid, title);
            
        } catch (Exception e) {
            logger.error("微信通知发送失败: openid={}", openid, e);
        }
    }
}
```

---

## 2.7 Controller 层

### WxUserController 扩展

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxUserController.java`

```java
@Autowired
private SicauCreditLogService creditLogService;

@Autowired
private CreditScoreService creditScoreService;

/**
 * 查询积分历史
 */
@GetMapping("/creditHistory")
public Object getCreditHistory(@LoginUser Integer userId,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "20") Integer limit) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    List<SicauCreditLog> logs = creditLogService.queryByUserId(userId, page, limit);
    int total = creditLogService.countByUserId(userId);
    
    Map<String, Object> data = new HashMap<>();
    data.put("total", total);
    data.put("items", logs);
    
    return ResponseUtil.ok(data);
}

/**
 * 查询信用详情
 */
@GetMapping("/creditDetail")
public Object getCreditDetail(@LoginUser Integer userId) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    LitemallUser user = userService.findById(userId);
    if (user == null) {
        return ResponseUtil.fail(404, "用户不存在");
    }
    
    CreditLevelEnum level = creditScoreService.getCreditLevel(userId);
    
    Map<String, Object> data = new HashMap<>();
    data.put("creditScore", user.getCreditScore());
    data.put("level", level.getLevelName());
    data.put("levelIcon", level.getIcon());
    data.put("minScore", level.getMinScore());
    data.put("maxScore", level.getMaxScore());
    
    return ResponseUtil.ok(data);
}
```

---

## 2.8 业务集成示例

### WxOrderService 集成（确认收货时触发积分）

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/service/WxOrderService.java`

在 `confirm()` 方法中添加积分更新逻辑：

```java
@Autowired
private CreditScoreService creditScoreService;

/**
 * 确认收货
 */
@Transactional
public Object confirm(Integer userId, Integer orderId) {
    // ...现有确认收货逻辑...
    
    // 更新订单状态
    order.setOrderStatus(OrderUtil.STATUS_CONFIRM);
    order.setConfirmTime(LocalDateTime.now());
    orderService.update(order);
    
    // 【新增】给卖家增加积分
    Integer sellerId = order.getUserId();  // 假设订单表有卖家ID字段
    creditScoreService.updateCredit(sellerId, CreditChangeReasonEnum.COMPLETE_ORDER, orderId);
    
    return ResponseUtil.ok();
}
```

---

## 3. 数据库迁移脚本

**文件**: `litemall-db/sql/V1.3__create_credit_log_table.sql`

```sql
-- 创建积分日志表
CREATE TABLE `sicau_credit_log` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `score_change` INT NOT NULL COMMENT '积分变动（正数为增加，负数为扣除）',
  `reason` VARCHAR(100) NOT NULL COMMENT '变动原因',
  `related_type` VARCHAR(50) COMMENT '关联实体类型（order/comment/goods/donation/delivery_order）',
  `related_id` INT COMMENT '关联实体 ID',
  `before_score` INT NOT NULL COMMENT '变动前积分',
  `after_score` INT NOT NULL COMMENT '变动后积分',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用积分日志表';

-- 为 litemall_user 表添加积分字段（如果尚未添加）
ALTER TABLE `litemall_user` 
ADD COLUMN `credit_score` INT NOT NULL DEFAULT 100 COMMENT '信用积分，初始值100';

-- 添加索引
CREATE INDEX `idx_credit_score` ON `litemall_user`(`credit_score`);
```

---

## 4. 前端代码实现

### 4.1 积分历史页面

**文件**: `litemall-wx/pages/user/credit-history/credit-history.js`

```javascript
const util = require('../../../utils/util.js');
const api = require('../../../config/api.js');

Page({
  data: {
    logs: [],
    page: 1,
    limit: 20,
    total: 0
  },

  onLoad: function () {
    this.getCreditHistory();
  },

  getCreditHistory: function () {
    wx.showLoading({ title: '加载中...' });
    
    util.request(api.CreditHistory, {
      page: this.data.page,
      limit: this.data.limit
    }, 'GET').then(res => {
      wx.hideLoading();
      
      if (res.errno === 0) {
        this.setData({
          logs: this.data.logs.concat(res.data.items),
          total: res.data.total
        });
      }
    });
  },

  onReachBottom: function () {
    if (this.data.logs.length < this.data.total) {
      this.setData({ page: this.data.page + 1 });
      this.getCreditHistory();
    }
  }
});
```

**文件**: `litemall-wx/pages/user/credit-history/credit-history.wxml`

```xml
<view class="container">
  <view class="log-list">
    <view class="log-item" wx:for="{{logs}}" wx:key="id">
      <view class="log-info">
        <text class="reason">{{item.reason}}</text>
        <text class="time">{{item.createTime}}</text>
      </view>
      <view class="score-change {{item.scoreChange > 0 ? 'positive' : 'negative'}}">
        {{item.scoreChange > 0 ? '+' : ''}}{{item.scoreChange}}
      </view>
    </view>
  </view>
  
  <view wx:if="{{logs.length === 0}}" class="empty">
    暂无积分记录
  </view>
</view>
```

---

## 5. 配置文件

### API 地址配置

**文件**: `litemall-wx/config/api.js`

```javascript
module.exports = {
  CreditHistory: WxApiRoot + 'user/creditHistory',
  CreditDetail: WxApiRoot + 'user/creditDetail',
  // ...
};
```

---

## 6. 测试指南

### 6.1 单元测试

**文件**: `litemall-db/src/test/java/org/linlinjava/litemall/db/CreditScoreServiceTest.java`

```java
@RunWith(SpringRunner.class)
@SpringBootTest
public class CreditScoreServiceTest {
    
    @Autowired
    private CreditScoreService creditScoreService;
    
    @Autowired
    private LitemallUserMapper userMapper;
    
    @Test
    public void testUpdateCredit_CompleteOrder_Success() {
        // 准备数据
        Integer userId = 1;
        LitemallUser user = userMapper.selectByPrimaryKey(userId);
        int beforeScore = user.getCreditScore();
        
        // 执行
        boolean result = creditScoreService.updateCredit(
            userId, 
            CreditChangeReasonEnum.COMPLETE_ORDER, 
            123
        );
        
        // 验证
        assertTrue(result);
        
        LitemallUser updatedUser = userMapper.selectByPrimaryKey(userId);
        assertEquals(beforeScore + 10, updatedUser.getCreditScore().intValue());
    }
    
    @Test
    public void testUpdateCredit_ScoreNotBelowZero() {
        // 准备数据：用户积分为 3
        Integer userId = 2;
        
        // 执行：扣除 5 分
        boolean result = creditScoreService.updateCredit(
            userId, 
            CreditChangeReasonEnum.CANCEL_ORDER, 
            456
        );
        
        // 验证：积分应为 0，而非 -2
        assertTrue(result);
        
        LitemallUser user = userMapper.selectByPrimaryKey(userId);
        assertEquals(0, user.getCreditScore().intValue());
    }
    
    @Test
    public void testUpdateCredit_ScoreNotAbove1000() {
        // 准备数据：用户积分为 995
        Integer userId = 3;
        
        // 执行：增加 10 分
        boolean result = creditScoreService.updateCredit(
            userId, 
            CreditChangeReasonEnum.COMPLETE_ORDER, 
            789
        );
        
        // 验证：积分应为 1000，而非 1005
        assertTrue(result);
        
        LitemallUser user = userMapper.selectByPrimaryKey(userId);
        assertEquals(1000, user.getCreditScore().intValue());
    }
}
```

---

## 7. 调用示例汇总

### 场景 1: 买家确认收货，卖家获得积分

```java
// 在 WxOrderService.confirm() 中
creditScoreService.updateCredit(
    sellerId, 
    CreditChangeReasonEnum.COMPLETE_ORDER, 
    orderId
);
```

### 场景 2: 用户收到好评，增加积分

```java
// 在 WxCommentService.submitComment() 中
if (starRating == 5) {
    creditScoreService.updateCredit(
        sellerId, 
        CreditChangeReasonEnum.GOOD_REVIEW, 
        commentId
    );
}
```

### 场景 3: 用户取消订单，扣除积分

```java
// 在 WxOrderService.cancel() 中
// 仅当付款后 5 分钟后取消才扣分
if (order.getPayTime() != null && 
    ChronoUnit.MINUTES.between(order.getPayTime(), LocalDateTime.now()) > 5) {
    creditScoreService.updateCredit(
        userId, 
        CreditChangeReasonEnum.CANCEL_ORDER, 
        orderId
    );
}
```

### 场景 4: 管理员下架违规商品，扣除积分

```java
// 在 AdminGoodsService.delete() 中
if (isViolation) {
    creditScoreService.updateCredit(
        goods.getUserId(), 
        CreditChangeReasonEnum.VIOLATION_GOODS, 
        goodsId
    );
}
```

---

## 8. 常见问题与解决方案

### Q1: 乐观锁冲突频繁发生怎么办？
**A**: 增加重试次数（MAX_RETRY），或在高并发场景使用分布式锁（Redis）。

### Q2: 微信通知发送失败怎么办？
**A**: 使用消息队列（RabbitMQ）异步发送，失败后重试 3 次。

### Q3: 如何防止同一订单重复加分？
**A**: 在 `sicau_credit_log` 表添加唯一索引：`UNIQUE(user_id, related_type, related_id)`。

### Q4: 积分日志表数据量过大怎么办？
**A**: 按月分表（如 `sicau_credit_log_202510`），保留最近 12 个月数据。

---

## 9. 安全检查清单

- [ ] 积分更新接口不对外暴露（仅内部调用）
- [ ] 使用乐观锁防止并发冲突
- [ ] 使用数据库事务保证原子性
- [ ] 积分范围限制已实现（0 ≤ score ≤ 1000）
- [ ] 同一订单只能触发一次积分变动
- [ ] 日志表包含完整审计信息（before/after）

---

**状态**: 开发上下文已生成，等待开发人员实现  
**预计完成时间**: 12 小时  
**下一步**: 实现故事 1.4 (信用等级展示)
