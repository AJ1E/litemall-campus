# 用户故事 1.1 开发上下文：微信一键登录

**Story ID**: 1.1  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)  
**目标读者**: bmm-dev (Developer Agent)

本文档为开发人员提供实现"微信一键登录"功能的精确代码指导和实现路径。

---

## 1. 实现概览

### 核心流程
```
小程序调用 wx.login() 获取 code
        ↓
发送 code 到后端 POST /wx/auth/login
        ↓
后端通过 code 调用微信 API 换取 openid
        ↓
查询数据库是否存在该 openid 的用户
        ↓
    存在？
    ├─ 是 → 返回已有用户信息 + 生成 JWT Token
    └─ 否 → 自动创建新用户 → 返回用户信息 + JWT Token
        ↓
前端保存 Token，根据 authStatus 决定跳转
```

---

## 2. 后端代码实现

### 2.1 文件结构

需要修改或创建的文件：
```
litemall-wx-api/
└── src/main/java/org/linlinjava/litemall/wx/
    ├── web/WxAuthController.java          (修改)
    └── service/WxAuthService.java         (新增)
```

### 2.2 WxAuthController.java

**文件位置**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxAuthController.java`

**修改内容**:

```java
package org.linlinjava.litemall.wx.web;

import org.linlinjava.litemall.wx.service.WxAuthService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/wx/auth")
public class WxAuthController {
    
    @Autowired
    private WxAuthService authService;
    
    /**
     * 微信一键登录
     * @param body 包含微信 code
     * @return token 和用户信息
     */
    @PostMapping("/login")
    public Object login(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        
        if (code == null || code.isEmpty()) {
            return ResponseUtil.badArgument();
        }
        
        try {
            // 调用服务层处理登录逻辑
            Map<String, Object> result = authService.login(code);
            return ResponseUtil.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseUtil.fail(501, "微信登录失败，请重试");
        }
    }
}
```

### 2.3 WxAuthService.java (新增)

**文件位置**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/service/WxAuthService.java`

**完整代码**:

```java
package org.linlinjava.litemall.wx.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import me.chanjar.weixin.common.error.WxErrorException;
import org.linlinjava.litemall.core.util.JwtHelper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;
import org.linlinjava.litemall.db.service.SicauStudentAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class WxAuthService {
    
    @Autowired
    private WxMaService wxMaService;
    
    @Autowired
    private LitemallUserService userService;
    
    @Autowired
    private SicauStudentAuthService authService;
    
    /**
     * 微信登录核心逻辑
     * @param code 微信登录凭证
     * @return token 和用户信息
     */
    @Transactional
    public Map<String, Object> login(String code) throws WxErrorException {
        // 1. 通过 code 换取 openid 和 session_key
        WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(code);
        String openid = session.getOpenid();
        
        // 2. 查询数据库是否存在该用户
        LitemallUser user = userService.queryByOid(openid);
        
        if (user == null) {
            // 3. 首次登录，自动创建用户
            user = new LitemallUser();
            user.setUsername("微信用户");
            user.setPassword(openid);  // 使用 openid 作为密码（永远不会用到）
            user.setWeixinOpenid(openid);
            user.setAvatar("https://yanxuan.nosdn.127.net/80841d741d7fa3073e0ae27bf487339f.jpg?imageView&quality=90&thumbnail=64x64");
            user.setNickname("新用户");
            user.setGender((byte) 0);
            user.setUserLevel((byte) 0);
            user.setStatus((byte) 0);
            user.setLastLoginTime(LocalDateTime.now());
            user.setLastLoginIp("0.0.0.0");
            user.setSessionKey(session.getSessionKey());
            user.setCreditScore(100);  // 新增字段：初始信用分 100
            userService.add(user);
        } else {
            // 4. 老用户更新最后登录时间
            user.setLastLoginTime(LocalDateTime.now());
            user.setSessionKey(session.getSessionKey());
            userService.updateById(user);
        }
        
        // 5. 查询学号认证状态
        SicauStudentAuth studentAuth = authService.getByUserId(user.getId());
        int authStatus = (studentAuth != null) ? studentAuth.getStatus() : 0;
        
        // 6. 生成 JWT Token（有效期 30 天）
        String token = JwtHelper.createToken(user.getId(), authStatus);
        
        // 7. 构造返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("authStatus", authStatus);
        userInfo.put("creditScore", user.getCreditScore());
        result.put("userInfo", userInfo);
        
        return result;
    }
}
```

### 2.4 修改 JwtHelper.java

**文件位置**: `litemall-core/src/main/java/org/linlinjava/litemall/core/util/JwtHelper.java`

**修改内容**（在现有代码基础上添加）:

```java
// 在 createToken 方法中添加 authStatus 参数
public static String createToken(Integer userId, Integer authStatus) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", userId);
    claims.put("authStatus", authStatus);  // 新增：学号认证状态
    
    return Jwts.builder()
        .setClaims(claims)
        .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
        .signWith(SignatureAlgorithm.HS512, SECRET)
        .compact();
}

// 新增：从 Token 中解析 authStatus
public static Integer getAuthStatus(String token) {
    return Jwts.parser()
        .setSigningKey(SECRET)
        .parseClaimsJws(token)
        .getBody()
        .get("authStatus", Integer.class);
}
```

---

## 3. 前端代码实现

### 3.1 文件结构

需要创建或修改的文件：
```
litemall-wx/
├── pages/
│   └── auth/
│       └── login/
│           ├── login.js       (新增)
│           ├── login.wxml     (新增)
│           └── login.wxss     (新增)
└── app.js                     (修改)
```

### 3.2 login.js

**文件位置**: `litemall-wx/pages/auth/login/login.js`

**完整代码**:

```javascript
const util = require('../../../utils/util.js');
const api = require('../../../config/api.js');

Page({
  data: {
    loading: false
  },

  onLoad: function (options) {
    // 页面加载时自动触发登录
    this.wxLogin();
  },

  /**
   * 微信登录
   */
  wxLogin: function () {
    const that = this;
    
    // 显示加载提示
    wx.showLoading({
      title: '登录中...',
      mask: true
    });

    // 1. 调用微信登录接口获取 code
    wx.login({
      success: function (res) {
        if (res.code) {
          // 2. 将 code 发送到后端
          that.loginByCode(res.code);
        } else {
          wx.hideLoading();
          wx.showToast({
            title: '获取登录凭证失败',
            icon: 'none'
          });
        }
      },
      fail: function () {
        wx.hideLoading();
        wx.showToast({
          title: '微信登录失败',
          icon: 'none'
        });
      }
    });
  },

  /**
   * 通过 code 登录
   */
  loginByCode: function (code) {
    const that = this;
    
    util.request(api.AuthLogin, {
      code: code
    }, 'POST').then(res => {
      wx.hideLoading();
      
      if (res.errno === 0) {
        // 3. 保存 token 和用户信息到本地存储
        wx.setStorageSync('token', res.data.token);
        wx.setStorageSync('userInfo', res.data.userInfo);
        
        // 4. 保存到全局数据
        const app = getApp();
        app.globalData.userInfo = res.data.userInfo;
        app.globalData.token = res.data.token;
        
        // 5. 根据认证状态跳转页面
        if (res.data.userInfo.authStatus === 0) {
          // 未认证，跳转到学号绑定页
          wx.redirectTo({
            url: '/pages/auth/bind-student/bind-student'
          });
        } else if (res.data.userInfo.authStatus === 1) {
          // 已认证，跳转到首页
          wx.switchTab({
            url: '/pages/index/index'
          });
        } else {
          // authStatus === 2（认证被拒绝），提示用户
          wx.showModal({
            title: '提示',
            content: '您的学号认证未通过，请联系管理员',
            showCancel: false
          });
        }
      } else {
        wx.showToast({
          title: res.errmsg || '登录失败',
          icon: 'none'
        });
      }
    }).catch(err => {
      wx.hideLoading();
      wx.showToast({
        title: '网络错误，请重试',
        icon: 'none'
      });
    });
  }
});
```

### 3.3 login.wxml

**文件位置**: `litemall-wx/pages/auth/login/login.wxml`

```xml
<view class="container">
  <view class="login-box">
    <image class="logo" src="/static/images/logo.png" mode="aspectFit"></image>
    <text class="title">四川农业大学校园闲置交易</text>
    <text class="subtitle">正在为您登录...</text>
  </view>
</view>
```

### 3.4 login.wxss

**文件位置**: `litemall-wx/pages/auth/login/login.wxss`

```css
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo {
  width: 200rpx;
  height: 200rpx;
  margin-bottom: 40rpx;
}

.title {
  font-size: 36rpx;
  color: #fff;
  font-weight: bold;
  margin-bottom: 20rpx;
}

.subtitle {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.8);
}
```

### 3.5 修改 app.js

**文件位置**: `litemall-wx/app.js`

在 `globalData` 中添加用户信息和认证状态：

```javascript
App({
  globalData: {
    userInfo: null,
    token: '',
    hasLogin: false
  },

  onLaunch: function () {
    // 尝试从本地存储恢复登录状态
    const token = wx.getStorageSync('token');
    const userInfo = wx.getStorageSync('userInfo');
    
    if (token && userInfo) {
      this.globalData.token = token;
      this.globalData.userInfo = userInfo;
      this.globalData.hasLogin = true;
    }
  }
});
```

### 3.6 配置 API 地址

**文件位置**: `litemall-wx/config/api.js`

添加登录接口地址：

```javascript
const WxApiRoot = 'https://api.yourdomain.com/wx/';

module.exports = {
  AuthLogin: WxApiRoot + 'auth/login',
  // ...其他接口
};
```

---

## 4. 配置文件修改

### 4.1 application-wx.yml

**文件位置**: `litemall-all/src/main/resources/application-wx.yml`

添加微信小程序配置：

```yaml
wx:
  miniapp:
    configs:
      - appid: wx1234567890abcdef        # 替换为你的小程序 AppID
        secret: your-wx-app-secret-here  # 替换为你的小程序 AppSecret
        token: your-token                # 可选
        aesKey: your-aes-key             # 可选
        msgDataFormat: JSON
```

---

## 5. 数据库准备

### 5.1 确认 litemall_user 表已添加 credit_score 字段

执行以下 SQL（如果尚未执行）：

```sql
ALTER TABLE `litemall_user` 
ADD COLUMN `credit_score` INT NOT NULL DEFAULT 100 COMMENT '信用积分' AFTER `status`;
```

---

## 6. 测试指南

### 6.1 后端单元测试

创建 `WxAuthServiceTest.java`:

```java
@RunWith(SpringRunner.class)
@SpringBootTest
public class WxAuthServiceTest {
    
    @Autowired
    private WxAuthService authService;
    
    @Test
    public void testLogin_NewUser() throws Exception {
        // Mock 微信 API 返回
        String mockCode = "test_code_12345";
        
        // 调用登录方法
        Map<String, Object> result = authService.login(mockCode);
        
        // 断言
        assertNotNull(result.get("token"));
        Map<String, Object> userInfo = (Map<String, Object>) result.get("userInfo");
        assertEquals(0, userInfo.get("authStatus"));
        assertEquals(100, userInfo.get("creditScore"));
    }
}
```

### 6.2 前端测试

1. **微信开发者工具**中打开小程序项目
2. 点击"编译"
3. 在"模拟器"中点击登录页面
4. 观察控制台是否有网络请求发出
5. 检查 Storage 中是否正确保存了 token 和 userInfo

### 6.3 集成测试检查清单

- [ ] 首次登录能自动创建用户
- [ ] 老用户登录能返回已有信息
- [ ] authStatus = 0 时跳转到学号绑定页
- [ ] authStatus = 1 时跳转到首页
- [ ] Token 正确保存在 Storage 中
- [ ] 全局 globalData 正确更新

---

## 7. 常见问题

### Q1: 微信登录返回 40029 错误
**原因**: code 已被使用或已过期  
**解决**: code 只能使用一次，测试时每次都需要重新获取

### Q2: 前端提示"网络错误"
**原因**: 小程序未配置服务器域名  
**解决**: 在微信公众平台 > 开发 > 开发设置 > 服务器域名中添加后端 API 域名

### Q3: Token 解析失败
**原因**: SECRET 密钥不一致  
**解决**: 确保 JwtHelper 中的 SECRET 与配置文件一致

---

## 8. 下一步

完成此故事后，继续开发：
- **故事 1.2**: 学号实名认证（依赖此故事的 authStatus 字段）

---

**状态**: 开发上下文已生成，等待开发人员实现  
**预计完成时间**: 4 小时
