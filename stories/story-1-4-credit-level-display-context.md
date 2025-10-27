# 用户故事 1.4 开发上下文：信用等级展示

**Story ID**: 1.4  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)  
**目标读者**: bmm-dev (Developer Agent)

本文档为开发人员提供实现"信用等级可视化展示"功能的精确代码指导和实现路径。

---

## 1. 实现概览

### 核心设计理念
信用等级展示是一个**可复用的 UI 组件**，通过颜色、星标、文字三种视觉元素，直观展示用户的信用状况。

### 等级计算逻辑（复用 Story 1.3）
```
查询用户积分（litemall_user.credit_score）
        ↓
根据积分范围判断等级
        ↓
返回等级信息（levelName, levelIcon, levelColor）
        ↓
前端渲染 CreditBadge 组件
```

---

## 2. 后端代码实现

### 2.1 文件结构

```
litemall-wx-api/
└── src/main/java/org/linlinjava/litemall/wx/
    └── web/
        └── WxUserController.java        (扩展 - 添加信用详情接口)

litemall-db/
└── src/main/java/org/linlinjava/litemall/db/
    └── service/
        └── CreditScoreService.java      (已在 Story 1.3 实现)
```

---

## 2.2 Controller 层扩展

### WxUserController.java

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxUserController.java`

在现有代码基础上添加：

```java
@Autowired
private CreditScoreService creditScoreService;

@Autowired
private SicauCreditLogService creditLogService;

@Autowired
private LitemallUserService userService;

/**
 * 查询用户信用详情
 */
@GetMapping("/creditDetail")
public Object getCreditDetail(@LoginUser Integer loginUserId,
                               @RequestParam(required = false) Integer userId) {
    // 如果不传 userId，查询当前登录用户
    Integer targetUserId = userId != null ? userId : loginUserId;
    
    if (targetUserId == null) {
        return ResponseUtil.unlogin();
    }
    
    // 查询用户信息
    LitemallUser user = userService.findById(targetUserId);
    if (user == null) {
        return ResponseUtil.fail(404, "用户不存在");
    }
    
    // 获取信用等级
    int creditScore = user.getCreditScore();
    CreditLevelEnum level = CreditLevelEnum.getByScore(creditScore);
    
    // 查询本月积分变动
    LocalDateTime monthStart = LocalDateTime.of(
        LocalDate.now().withDayOfMonth(1), 
        LocalTime.MIN
    );
    
    List<SicauCreditLog> monthLogs = creditLogService.queryByUserIdAndTime(
        targetUserId, 
        monthStart, 
        LocalDateTime.now()
    );
    
    int monthGain = 0;
    int monthLoss = 0;
    
    for (SicauCreditLog log : monthLogs) {
        if (log.getScoreChange() > 0) {
            monthGain += log.getScoreChange();
        } else {
            monthLoss += Math.abs(log.getScoreChange());
        }
    }
    
    // 计算距离下一等级
    CreditLevelEnum nextLevel = getNextLevel(level);
    Integer nextLevelScore = nextLevel != null ? nextLevel.getMinScore() : null;
    
    // 组装响应数据
    Map<String, Object> data = new HashMap<>();
    data.put("userId", targetUserId);
    data.put("creditScore", creditScore);
    data.put("level", level.getLevelName());
    data.put("levelCode", level.ordinal() + 1);  // 1-新手, 2-良好, 3-优秀, 4-信誉商家
    data.put("levelIcon", level.getIcon());
    data.put("levelColor", getLevelColor(level));
    data.put("minScore", level.getMinScore());
    data.put("maxScore", level.getMaxScore());
    data.put("monthGain", monthGain);
    data.put("monthLoss", monthLoss);
    data.put("nextLevelScore", nextLevelScore);
    data.put("nextLevelName", nextLevel != null ? nextLevel.getLevelName() : null);
    
    return ResponseUtil.ok(data);
}

/**
 * 批量查询用户信用等级
 */
@PostMapping("/batchCreditLevel")
public Object batchCreditLevel(@LoginUser Integer userId, @RequestBody Map<String, Object> body) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    @SuppressWarnings("unchecked")
    List<Integer> userIds = (List<Integer>) body.get("userIds");
    
    if (userIds == null || userIds.isEmpty()) {
        return ResponseUtil.badArgument();
    }
    
    if (userIds.size() > 100) {
        return ResponseUtil.fail(400, "单次查询用户数量不能超过100");
    }
    
    Map<Integer, Map<String, Object>> result = new HashMap<>();
    
    for (Integer uid : userIds) {
        LitemallUser user = userService.findById(uid);
        if (user != null) {
            int creditScore = user.getCreditScore();
            CreditLevelEnum level = CreditLevelEnum.getByScore(creditScore);
            
            Map<String, Object> levelInfo = new HashMap<>();
            levelInfo.put("creditScore", creditScore);
            levelInfo.put("level", level.getLevelName());
            levelInfo.put("levelIcon", level.getIcon());
            levelInfo.put("levelColor", getLevelColor(level));
            
            result.put(uid, levelInfo);
        }
    }
    
    return ResponseUtil.ok(result);
}

/**
 * 获取等级颜色
 */
private String getLevelColor(CreditLevelEnum level) {
    switch (level) {
        case NEWBIE:
            return "#999999";
        case GOOD:
            return "#1890FF";
        case EXCELLENT:
            return "#FAAD14";
        case TRUSTED:
            return "#F5222D";
        default:
            return "#999999";
    }
}

/**
 * 获取下一等级
 */
private CreditLevelEnum getNextLevel(CreditLevelEnum currentLevel) {
    switch (currentLevel) {
        case NEWBIE:
            return CreditLevelEnum.GOOD;
        case GOOD:
            return CreditLevelEnum.EXCELLENT;
        case EXCELLENT:
            return CreditLevelEnum.TRUSTED;
        case TRUSTED:
            return null;  // 已是最高等级
        default:
            return null;
    }
}
```

---

## 2.3 Service 层扩展

### SicauCreditLogService.java

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauCreditLogService.java`

在现有代码基础上添加：

```java
/**
 * 查询指定时间范围内的积分日志
 */
public List<SicauCreditLog> queryByUserIdAndTime(Integer userId, 
                                                   LocalDateTime startTime, 
                                                   LocalDateTime endTime) {
    return logMapper.selectByUserIdAndTime(userId, startTime, endTime);
}
```

### SicauCreditLogMapper.java

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/dao/SicauCreditLogMapper.java`

```java
List<SicauCreditLog> selectByUserIdAndTime(@Param("userId") Integer userId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);
```

### SicauCreditLogMapper.xml

**文件**: `litemall-db/src/main/resources/org/linlinjava/litemall/db/dao/SicauCreditLogMapper.xml`

```xml
<select id="selectByUserIdAndTime" resultMap="BaseResultMap">
  SELECT * FROM sicau_credit_log 
  WHERE user_id = #{userId} 
    AND create_time BETWEEN #{startTime} AND #{endTime}
  ORDER BY create_time DESC
</select>
```

---

## 3. 前端代码实现

### 3.1 CreditBadge 组件

#### 组件结构

**文件**: `litemall-wx/components/credit-badge/credit-badge.js`

```javascript
Component({
  properties: {
    userId: {
      type: Number,
      value: null
    },
    score: {
      type: Number,
      value: null
    },
    level: {
      type: Number,
      value: null
    },
    showDetail: {
      type: Boolean,
      value: true
    },
    size: {
      type: String,
      value: 'medium'  // small, medium, large
    }
  },

  data: {
    levelInfo: null,
    loading: false
  },

  attached: function() {
    // 如果传入了 score 和 level，直接计算等级信息
    if (this.properties.score !== null && this.properties.level !== null) {
      this.calculateLevelInfo(this.properties.score, this.properties.level);
    } else if (this.properties.userId) {
      // 否则通过 userId 查询
      this.fetchCreditDetail();
    }
  },

  methods: {
    /**
     * 查询用户信用详情
     */
    fetchCreditDetail: function() {
      const that = this;
      this.setData({ loading: true });

      const api = require('../../config/api.js');
      const util = require('../../utils/util.js');

      util.request(api.CreditDetail, {
        userId: this.properties.userId
      }, 'GET').then(res => {
        that.setData({ loading: false });
        
        if (res.errno === 0) {
          that.setData({ levelInfo: res.data });
        }
      }).catch(() => {
        that.setData({ loading: false });
      });
    },

    /**
     * 计算等级信息
     */
    calculateLevelInfo: function(score, level) {
      const levelConfig = this.getLevelConfig(level);
      
      this.setData({
        levelInfo: {
          creditScore: score,
          level: levelConfig.levelName,
          levelIcon: levelConfig.icon,
          levelColor: levelConfig.color
        }
      });
    },

    /**
     * 获取等级配置
     */
    getLevelConfig: function(level) {
      const configs = [
        { levelName: '新手', icon: '⭐', color: '#999999' },
        { levelName: '良好', icon: '⭐⭐', color: '#1890FF' },
        { levelName: '优秀', icon: '⭐⭐⭐', color: '#FAAD14' },
        { levelName: '信誉商家', icon: '⭐⭐⭐⭐', color: '#F5222D' }
      ];
      
      return configs[level - 1] || configs[0];
    },

    /**
     * 点击查看详情
     */
    handleTap: function() {
      if (!this.properties.showDetail) {
        return;
      }

      this.triggerEvent('showdetail', {
        userId: this.properties.userId,
        levelInfo: this.data.levelInfo
      });
    }
  }
});
```

---

#### 组件模板

**文件**: `litemall-wx/components/credit-badge/credit-badge.wxml`

```xml
<view class="credit-badge {{size}}" bindtap="handleTap">
  <block wx:if="{{!loading && levelInfo}}">
    <text class="stars" style="color: {{levelInfo.levelColor}}">{{levelInfo.levelIcon}}</text>
    <text class="level-name">{{levelInfo.level}}</text>
    <text class="score" wx:if="{{size !== 'small'}}">({{levelInfo.creditScore}}分)</text>
  </block>
  
  <block wx:if="{{loading}}">
    <text class="loading">加载中...</text>
  </block>
</view>
```

---

#### 组件样式

**文件**: `litemall-wx/components/credit-badge/credit-badge.wxss`

```css
.credit-badge {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
}

.credit-badge.small {
  font-size: 24rpx;
}

.credit-badge.small .stars {
  font-size: 28rpx;
}

.credit-badge.medium {
  font-size: 28rpx;
}

.credit-badge.medium .stars {
  font-size: 32rpx;
}

.credit-badge.large {
  font-size: 32rpx;
}

.credit-badge.large .stars {
  font-size: 40rpx;
}

.stars {
  font-weight: bold;
  line-height: 1;
}

.level-name {
  color: #333333;
  font-weight: 500;
}

.score {
  color: #999999;
  font-size: 0.9em;
}

.loading {
  color: #999999;
  font-size: 24rpx;
}
```

---

#### 组件配置

**文件**: `litemall-wx/components/credit-badge/credit-badge.json`

```json
{
  "component": true,
  "usingComponents": {}
}
```

---

### 3.2 积分详情弹窗组件

**文件**: `litemall-wx/components/credit-detail-modal/credit-detail-modal.js`

```javascript
Component({
  properties: {
    visible: {
      type: Boolean,
      value: false
    },
    userId: {
      type: Number,
      value: null
    }
  },

  data: {
    detailInfo: null
  },

  observers: {
    'visible': function(visible) {
      if (visible && this.properties.userId) {
        this.fetchCreditDetail();
      }
    }
  },

  methods: {
    fetchCreditDetail: function() {
      const that = this;
      const api = require('../../config/api.js');
      const util = require('../../utils/util.js');

      util.request(api.CreditDetail, {
        userId: this.properties.userId
      }, 'GET').then(res => {
        if (res.errno === 0) {
          that.setData({ detailInfo: res.data });
        }
      });
    },

    close: function() {
      this.triggerEvent('close');
    },

    viewHistory: function() {
      wx.navigateTo({
        url: '/pages/user/credit-history/credit-history'
      });
      this.close();
    }
  }
});
```

---

**文件**: `litemall-wx/components/credit-detail-modal/credit-detail-modal.wxml`

```xml
<view class="modal-mask" wx:if="{{visible}}" bindtap="close">
  <view class="modal-content" catchtap="">
    <view class="modal-header">
      <text class="title">信用积分详情</text>
      <text class="close-btn" bindtap="close">✕</text>
    </view>

    <view class="modal-body" wx:if="{{detailInfo}}">
      <view class="level-row">
        <text class="label">当前等级：</text>
        <text class="stars" style="color: {{detailInfo.levelColor}}">{{detailInfo.levelIcon}}</text>
        <text class="level-name">{{detailInfo.level}}</text>
      </view>

      <view class="info-row">
        <text class="label">总积分：</text>
        <text class="value">{{detailInfo.creditScore}} 分</text>
      </view>

      <view class="info-row">
        <text class="label">本月获得：</text>
        <text class="value positive">+{{detailInfo.monthGain}} 分</text>
      </view>

      <view class="info-row">
        <text class="label">本月扣除：</text>
        <text class="value negative">-{{detailInfo.monthLoss}} 分</text>
      </view>

      <view class="divider"></view>

      <view class="next-level" wx:if="{{detailInfo.nextLevelScore}}">
        <text class="hint">距离下一等级还需：{{detailInfo.nextLevelScore - detailInfo.creditScore}} 分</text>
        <text class="hint-sub">(升至"{{detailInfo.nextLevelName}}"需 {{detailInfo.nextLevelScore}} 分)</text>
      </view>

      <view class="next-level" wx:else>
        <text class="hint">🎉 您已达到最高等级</text>
      </view>
    </view>

    <view class="modal-footer">
      <button class="btn-secondary" bindtap="close">关闭</button>
      <button class="btn-primary" bindtap="viewHistory">查看详细历史</button>
    </view>
  </view>
</view>
```

---

**文件**: `litemall-wx/components/credit-detail-modal/credit-detail-modal.wxss`

```css
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-content {
  width: 600rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  overflow: hidden;
}

.modal-header {
  padding: 40rpx 30rpx 30rpx;
  border-bottom: 1rpx solid #EEEEEE;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-size: 36rpx;
  font-weight: 600;
  color: #333333;
}

.close-btn {
  font-size: 48rpx;
  color: #999999;
  line-height: 1;
}

.modal-body {
  padding: 40rpx 30rpx;
}

.level-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 30rpx;
}

.level-row .label {
  font-size: 28rpx;
  color: #666666;
}

.level-row .stars {
  font-size: 36rpx;
  font-weight: bold;
}

.level-row .level-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #333333;
}

.info-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.info-row .label {
  font-size: 28rpx;
  color: #666666;
}

.info-row .value {
  font-size: 28rpx;
  color: #333333;
  font-weight: 500;
}

.info-row .value.positive {
  color: #52C41A;
}

.info-row .value.negative {
  color: #F5222D;
}

.divider {
  height: 1rpx;
  background: #EEEEEE;
  margin: 30rpx 0;
}

.next-level {
  text-align: center;
}

.hint {
  display: block;
  font-size: 28rpx;
  color: #FF6B6B;
  margin-bottom: 8rpx;
}

.hint-sub {
  display: block;
  font-size: 24rpx;
  color: #999999;
}

.modal-footer {
  padding: 20rpx 30rpx 40rpx;
  display: flex;
  gap: 20rpx;
}

.modal-footer button {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 12rpx;
  font-size: 28rpx;
}

.btn-secondary {
  background: #F5F5F5;
  color: #666666;
}

.btn-primary {
  background: #FF6B6B;
  color: #FFFFFF;
}
```

---

### 3.3 使用示例

#### 在商品详情页使用

**文件**: `litemall-wx/pages/goods/goods.wxml`

```xml
<view class="seller-info">
  <text class="seller-label">卖家：</text>
  <text class="seller-name">{{goods.sellerName}}</text>
  <credit-badge userId="{{goods.userId}}" size="small" bind:showdetail="handleShowCreditDetail" />
</view>

<!-- 积分详情弹窗 -->
<credit-detail-modal visible="{{showCreditModal}}" userId="{{goods.userId}}" bind:close="closeCreditModal" />
```

**文件**: `litemall-wx/pages/goods/goods.js`

```javascript
const app = getApp();

Page({
  data: {
    goods: {},
    showCreditModal: false
  },

  handleShowCreditDetail: function(e) {
    this.setData({ showCreditModal: true });
  },

  closeCreditModal: function() {
    this.setData({ showCreditModal: false });
  }
});
```

**文件**: `litemall-wx/pages/goods/goods.json`

```json
{
  "usingComponents": {
    "credit-badge": "/components/credit-badge/credit-badge",
    "credit-detail-modal": "/components/credit-detail-modal/credit-detail-modal"
  }
}
```

---

## 4. 配置文件

### API 地址配置

**文件**: `litemall-wx/config/api.js`

```javascript
module.exports = {
  CreditDetail: WxApiRoot + 'user/creditDetail',
  BatchCreditLevel: WxApiRoot + 'user/batchCreditLevel',
  // ...
};
```

---

## 5. 性能优化（Redis 缓存）

### 5.1 添加 Redis 配置

**文件**: `litemall-core/src/main/java/org/linlinjava/litemall/core/config/RedisConfig.java`

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        
        // 使用 Jackson 序列化
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
        serializer.setObjectMapper(mapper);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}
```

---

### 5.2 在 CreditScoreService 中使用缓存

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/service/CreditScoreService.java`

```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;

private static final String CACHE_KEY_PREFIX = "credit_level:";
private static final long CACHE_TTL = 300;  // 5 分钟

/**
 * 查询用户信用等级（带缓存）
 */
public CreditLevelEnum getCreditLevelWithCache(Integer userId) {
    String cacheKey = CACHE_KEY_PREFIX + userId;
    
    // 尝试从缓存获取
    Object cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return (CreditLevelEnum) cached;
    }
    
    // 缓存未命中，查询数据库
    LitemallUser user = userMapper.selectByPrimaryKey(userId);
    if (user == null) {
        return CreditLevelEnum.NEWBIE;
    }
    
    CreditLevelEnum level = CreditLevelEnum.getByScore(user.getCreditScore());
    
    // 写入缓存
    redisTemplate.opsForValue().set(cacheKey, level, CACHE_TTL, TimeUnit.SECONDS);
    
    return level;
}

/**
 * 清除用户信用等级缓存（积分变动时调用）
 */
public void clearCreditLevelCache(Integer userId) {
    String cacheKey = CACHE_KEY_PREFIX + userId;
    redisTemplate.delete(cacheKey);
}
```

在 `updateCredit()` 方法中添加缓存清除：

```java
@Transactional
public boolean updateCredit(Integer userId, CreditChangeReasonEnum reason, Integer relatedId) {
    // ...现有积分更新逻辑...
    
    // 清除缓存
    clearCreditLevelCache(userId);
    
    return true;
}
```

---

## 6. 测试指南

### 6.1 单元测试

**文件**: `litemall-wx-api/src/test/java/org/linlinjava/litemall/wx/WxUserControllerTest.java`

```java
@Test
public void testGetCreditDetail_Success() throws Exception {
    // 准备数据
    Integer userId = 1;
    
    // 调用接口
    mockMvc.perform(get("/wx/user/creditDetail")
            .param("userId", userId.toString())
            .header("X-Litemall-Token", "test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errno").value(0))
        .andExpect(jsonPath("$.data.creditScore").exists())
        .andExpect(jsonPath("$.data.level").exists())
        .andExpect(jsonPath("$.data.levelIcon").exists())
        .andExpect(jsonPath("$.data.levelColor").exists())
        .andExpect(jsonPath("$.data.monthGain").exists())
        .andExpect(jsonPath("$.data.monthLoss").exists());
}

@Test
public void testBatchCreditLevel_Success() throws Exception {
    // 准备数据
    Map<String, Object> body = new HashMap<>();
    body.put("userIds", Arrays.asList(1, 2, 3));
    
    // 调用接口
    mockMvc.perform(post("/wx/user/batchCreditLevel")
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(body))
            .header("X-Litemall-Token", "test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errno").value(0))
        .andExpect(jsonPath("$.data.1").exists())
        .andExpect(jsonPath("$.data.2").exists())
        .andExpect(jsonPath("$.data.3").exists());
}
```

---

## 7. 常见问题与解决方案

### Q1: 如何确保等级计算的一致性？
**A**: 使用统一的 `CreditLevelEnum.getByScore()` 方法，避免在多处重复实现计算逻辑。

### Q2: 批量查询 100 个用户会影响性能吗？
**A**: 使用 Redis 缓存可大幅减少数据库查询。建议前端分批请求（每批 20 个）。

### Q3: 如何处理本月积分统计的性能问题？
**A**: 可创建数据库视图 `v_user_month_credit_stats`，或使用定时任务每小时更新 Redis 缓存。

---

## 8. 部署检查清单

- [ ] 后端接口 `creditDetail` 和 `batchCreditLevel` 已实现
- [ ] CreditBadge 组件已创建并测试
- [ ] 积分详情弹窗组件已创建并测试
- [ ] Redis 缓存已配置并生效
- [ ] 本月积分统计查询性能 < 100ms
- [ ] 等级颜色在不同等级下正确显示
- [ ] 单元测试通过
- [ ] 已在商品详情页、个人主页正确展示

---

**状态**: 开发上下文已生成，等待开发人员实现  
**预计完成时间**: 8 小时  
**下一步**: 实现故事 1.5 (个人主页)
