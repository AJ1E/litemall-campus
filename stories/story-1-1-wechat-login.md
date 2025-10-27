# 用户故事 1.1: 微信一键登录

**Story ID**: 1.1  
**Epic**: Epic 1 - 用户认证与信用体系  
**优先级**: P0  
**预估工时**: 4 小时  
**状态**: drafted  
**创建日期**: 2025-10-27

---

## 用户故事描述

**作为** 普通用户  
**我想要** 通过微信授权快速登录小程序  
**以便** 无需额外注册即可使用系统

---

## 验收标准

- [ ] 调用微信 `wx.login()` API 获取 code
- [ ] 后端通过 code 换取 openid 并自动创建用户账号
- [ ] 首次登录跳转至学号绑定页
- [ ] 登录状态保持 30 天（JWT Token）

---

## 技术实现

### 后端实现

**接口**: `POST /wx/auth/login`

**请求参数**:
```json
{
  "code": "081xYz0w3H7FZn2RFH3w3SJlFh2xYz0o"
}
```

**响应数据**:
```json
{
  "errno": 0,
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "userInfo": {
      "id": 123,
      "username": "微信用户",
      "avatar": "https://...",
      "authStatus": 0,
      "creditScore": 100
    }
  }
}
```

### 前端实现

**文件位置**: `litemall-wx/pages/auth/login/login.js`

**关键代码**:
```javascript
// 调用微信登录
wx.login({
  success: (res) => {
    if (res.code) {
      // 发送 code 到后端
      wx.request({
        url: 'https://api.example.com/wx/auth/login',
        method: 'POST',
        data: { code: res.code },
        success: (result) => {
          // 保存 token 和用户信息
          wx.setStorageSync('token', result.data.token);
          wx.setStorageSync('userInfo', result.data.userInfo);
          
          // 根据认证状态跳转
          if (result.data.userInfo.authStatus === 0) {
            wx.navigateTo({ url: '/pages/auth/bind-student/bind-student' });
          } else {
            wx.switchTab({ url: '/pages/index/index' });
          }
        }
      });
    }
  }
});
```

---

## 数据库影响

无（复用 `litemall_user` 表，由 litemall 原有逻辑处理用户创建）

---

## 依赖项

- 微信小程序已配置 AppID 和 AppSecret
- `application.yml` 中已添加微信配置项

---

## 测试用例

### 单元测试
1. 测试微信 code 换取 openid 成功场景
2. 测试微信 code 无效场景
3. 测试 JWT Token 生成和解析

### 集成测试
1. 测试首次登录自动创建用户账号
2. 测试老用户登录返回已有信息
3. 测试 Token 过期后重新登录

### 前端测试
1. 测试小程序授权弹窗显示
2. 测试登录后正确跳转（已认证 vs 未认证）

---

## 完成定义 (Definition of Done)

- [ ] 后端接口实现并通过单元测试
- [ ] 前端页面实现并完成 UI 评审
- [ ] 集成测试通过
- [ ] 代码已提交并通过 Code Review
- [ ] 已部署到测试环境并验证

---

## 备注

- 首次登录的用户，`authStatus` 默认为 0（未认证）
- 登录成功后需在 `app.js` 的 `globalData` 中保存用户信息
- Token 有效期为 30 天，存储在小程序的 `Storage` 中
