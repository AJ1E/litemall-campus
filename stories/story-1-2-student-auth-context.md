# 用户故事 1.2 开发上下文：学号实名认证

**Story ID**: 1.2  
**生成日期**: 2025-10-27  
**生成者**: bmm-sm (Sprint Master)  
**目标读者**: bmm-dev (Developer Agent)

本文档为开发人员提供实现"学号实名认证"功能的精确代码指导和实现路径。

---

## 1. 实现概览

### 核心流程（用户端）
```
用户登录后 authStatus = 0（未认证）
        ↓
进入学号绑定页面
        ↓
填写学号、姓名、学院、专业
        ↓
上传学生证照片（直传阿里云 OSS）
        ↓
提交到后端 POST /wx/auth/bindStudentNo
        ↓
后端使用 AES 加密学号、姓名后存入数据库
        ↓
返回"提交成功，等待审核"
        ↓
用户可查看审核状态（GET /wx/auth/status）
```

### 核心流程（管理端）
```
管理员登录管理后台
        ↓
进入"学号认证审核"列表
        ↓
查看待审核申请（学生证照片、学号、姓名等）
        ↓
点击"通过"或"拒绝"
        ↓
调用 POST /admin/user/auditAuth
        ↓
更新 sicau_student_auth 表的 status 字段
        ↓
发送微信模板消息通知用户
```

---

## 2. 后端代码实现

### 2.1 文件结构

```
litemall-db/
└── src/main/java/org/linlinjava/litemall/db/
    ├── domain/
    │   └── SicauStudentAuth.java           (新增 - 实体类)
    ├── dao/
    │   └── SicauStudentAuthMapper.java     (新增 - Mapper 接口)
    └── service/
        └── SicauStudentAuthService.java    (新增 - 服务类)

litemall-wx-api/
└── src/main/java/org/linlinjava/litemall/wx/
    ├── web/
    │   └── WxAuthController.java           (修改 - 添加新接口)
    └── service/
        └── WxAuthService.java              (修改 - 添加认证逻辑)

litemall-admin-api/
└── src/main/java/org/linlinjava/litemall/admin/
    ├── web/
    │   └── AdminUserController.java        (修改 - 添加审核接口)
    └── service/
        └── AdminAuthService.java           (新增 - 审核服务)

litemall-core/
└── src/main/java/org/linlinjava/litemall/core/
    └── util/
        └── AesUtil.java                    (参考 Epic 1 Context)
```

---

## 2.2 数据库实体类

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/domain/SicauStudentAuth.java`

```java
package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

public class SicauStudentAuth {
    private Integer id;
    private Integer userId;
    private String studentNo;        // AES 加密存储
    private String realName;         // AES 加密存储
    private String college;
    private String major;
    private String studentCardUrl;
    private Byte status;             // 0-待审核, 1-通过, 2-拒绝
    private Integer auditAdminId;
    private LocalDateTime auditTime;
    private String auditReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    
    public String getStudentCardUrl() { return studentCardUrl; }
    public void setStudentCardUrl(String studentCardUrl) { this.studentCardUrl = studentCardUrl; }
    
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
    
    public Integer getAuditAdminId() { return auditAdminId; }
    public void setAuditAdminId(Integer auditAdminId) { this.auditAdminId = auditAdminId; }
    
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    
    public String getAuditReason() { return auditReason; }
    public void setAuditReason(String auditReason) { this.auditReason = auditReason; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
```

---

## 2.3 Mapper 接口

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/dao/SicauStudentAuthMapper.java`

```java
package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;

import java.util.List;

public interface SicauStudentAuthMapper {
    int insert(SicauStudentAuth record);
    int updateByPrimaryKey(SicauStudentAuth record);
    SicauStudentAuth selectByPrimaryKey(Integer id);
    SicauStudentAuth selectByUserId(Integer userId);
    SicauStudentAuth selectByStudentNo(String studentNo);
    List<SicauStudentAuth> selectByStatus(@Param("status") Byte status);
}
```

**XML 文件**: `litemall-db/src/main/resources/org/linlinjava/litemall/db/dao/SicauStudentAuthMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.linlinjava.litemall.db.dao.SicauStudentAuthMapper">
  <resultMap id="BaseResultMap" type="org.linlinjava.litemall.db.domain.SicauStudentAuth">
    <id column="id" jdbcType="INTEGER" property="id" />
    <result column="user_id" jdbcType="INTEGER" property="userId" />
    <result column="student_no" jdbcType="VARCHAR" property="studentNo" />
    <result column="real_name" jdbcType="VARCHAR" property="realName" />
    <result column="college" jdbcType="VARCHAR" property="college" />
    <result column="major" jdbcType="VARCHAR" property="major" />
    <result column="student_card_url" jdbcType="VARCHAR" property="studentCardUrl" />
    <result column="status" jdbcType="TINYINT" property="status" />
    <result column="audit_admin_id" jdbcType="INTEGER" property="auditAdminId" />
    <result column="audit_time" jdbcType="TIMESTAMP" property="auditTime" />
    <result column="audit_reason" jdbcType="VARCHAR" property="auditReason" />
    <result column="create_time" jdbcType="TIMESTAMP" property="createTime" />
    <result column="update_time" jdbcType="TIMESTAMP" property="updateTime" />
  </resultMap>

  <insert id="insert" parameterType="org.linlinjava.litemall.db.domain.SicauStudentAuth" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO sicau_student_auth (user_id, student_no, real_name, college, major, student_card_url, status)
    VALUES (#{userId}, #{studentNo}, #{realName}, #{college}, #{major}, #{studentCardUrl}, #{status})
  </insert>

  <update id="updateByPrimaryKey" parameterType="org.linlinjava.litemall.db.domain.SicauStudentAuth">
    UPDATE sicau_student_auth
    SET status = #{status},
        audit_admin_id = #{auditAdminId},
        audit_time = #{auditTime},
        audit_reason = #{auditReason}
    WHERE id = #{id}
  </update>

  <select id="selectByPrimaryKey" parameterType="java.lang.Integer" resultMap="BaseResultMap">
    SELECT * FROM sicau_student_auth WHERE id = #{id}
  </select>

  <select id="selectByUserId" parameterType="java.lang.Integer" resultMap="BaseResultMap">
    SELECT * FROM sicau_student_auth WHERE user_id = #{userId} LIMIT 1
  </select>

  <select id="selectByStudentNo" parameterType="java.lang.String" resultMap="BaseResultMap">
    SELECT * FROM sicau_student_auth WHERE student_no = #{studentNo} LIMIT 1
  </select>

  <select id="selectByStatus" parameterType="java.lang.Byte" resultMap="BaseResultMap">
    SELECT * FROM sicau_student_auth WHERE status = #{status} ORDER BY create_time ASC
  </select>
</mapper>
```

---

## 2.4 Service 层

**文件**: `litemall-db/src/main/java/org/linlinjava/litemall/db/service/SicauStudentAuthService.java`

```java
package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.dao.SicauStudentAuthMapper;
import org.linlinjava.litemall.db.domain.SicauStudentAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SicauStudentAuthService {
    
    @Autowired
    private SicauStudentAuthMapper authMapper;
    
    public int add(SicauStudentAuth auth) {
        return authMapper.insert(auth);
    }
    
    public int updateById(SicauStudentAuth auth) {
        return authMapper.updateByPrimaryKey(auth);
    }
    
    public SicauStudentAuth getById(Integer id) {
        return authMapper.selectByPrimaryKey(id);
    }
    
    public SicauStudentAuth getByUserId(Integer userId) {
        return authMapper.selectByUserId(userId);
    }
    
    public SicauStudentAuth getByStudentNo(String encryptedStudentNo) {
        return authMapper.selectByStudentNo(encryptedStudentNo);
    }
    
    public List<SicauStudentAuth> listByStatus(Byte status) {
        return authMapper.selectByStatus(status);
    }
}
```

---

## 2.5 用户端 API 实现

**文件**: `litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/web/WxAuthController.java`

在现有代码基础上添加：

```java
@Autowired
private SicauStudentAuthService studentAuthService;

@Autowired
private AesUtil aesUtil;

/**
 * 绑定学号
 */
@PostMapping("/bindStudentNo")
public Object bindStudentNo(@LoginUser Integer userId, @RequestBody Map<String, String> body) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    String studentNo = body.get("studentNo");
    String realName = body.get("realName");
    String college = body.get("college");
    String major = body.get("major");
    String studentCardUrl = body.get("studentCardUrl");
    
    // 参数验证
    if (studentNo == null || !studentNo.matches("\\d{12}")) {
        return ResponseUtil.badArgumentValue("学号格式错误，应为12位数字");
    }
    if (realName == null || realName.length() < 2 || realName.length() > 10) {
        return ResponseUtil.badArgumentValue("真实姓名长度应为2-10个字");
    }
    if (college == null || major == null || studentCardUrl == null) {
        return ResponseUtil.badArgument();
    }
    
    try {
        // 检查是否已提交过
        SicauStudentAuth existing = studentAuthService.getByUserId(userId);
        if (existing != null) {
            if (existing.getStatus() == 0) {
                return ResponseUtil.fail(601, "您已提交认证申请，请等待审核");
            } else if (existing.getStatus() == 1) {
                return ResponseUtil.fail(602, "您已通过认证，无需重复提交");
            }
            // status == 2 时允许重新提交
        }
        
        // AES 加密学号和姓名
        String encryptedStudentNo = aesUtil.encrypt(studentNo);
        String encryptedRealName = aesUtil.encrypt(realName);
        
        // 检查学号是否已被使用
        SicauStudentAuth duplicate = studentAuthService.getByStudentNo(encryptedStudentNo);
        if (duplicate != null && !duplicate.getUserId().equals(userId)) {
            return ResponseUtil.fail(603, "该学号已被其他用户绑定");
        }
        
        // 创建或更新认证记录
        SicauStudentAuth auth = existing != null ? existing : new SicauStudentAuth();
        auth.setUserId(userId);
        auth.setStudentNo(encryptedStudentNo);
        auth.setRealName(encryptedRealName);
        auth.setCollege(college);
        auth.setMajor(major);
        auth.setStudentCardUrl(studentCardUrl);
        auth.setStatus((byte) 0); // 待审核
        auth.setAuditReason(null);
        
        if (existing != null) {
            studentAuthService.updateById(auth);
        } else {
            studentAuthService.add(auth);
        }
        
        return ResponseUtil.ok("提交成功，请等待审核");
        
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseUtil.fail(500, "提交失败，请稍后重试");
    }
}

/**
 * 查询认证状态
 */
@GetMapping("/status")
public Object getAuthStatus(@LoginUser Integer userId) {
    if (userId == null) {
        return ResponseUtil.unlogin();
    }
    
    SicauStudentAuth auth = studentAuthService.getByUserId(userId);
    if (auth == null) {
        return ResponseUtil.ok(new HashMap<String, Object>() {{
            put("status", -1); // 未提交
        }});
    }
    
    try {
        // 解密学号和姓名
        String studentNo = aesUtil.decrypt(auth.getStudentNo());
        String realName = aesUtil.decrypt(auth.getRealName());
        
        Map<String, Object> data = new HashMap<>();
        data.put("status", auth.getStatus());
        data.put("studentNo", studentNo);
        data.put("realName", realName);
        data.put("college", auth.getCollege());
        data.put("major", auth.getMajor());
        data.put("auditReason", auth.getAuditReason());
        
        return ResponseUtil.ok(data);
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseUtil.fail(500, "查询失败");
    }
}
```

---

## 2.6 管理端 API 实现

**文件**: `litemall-admin-api/src/main/java/org/linlinjava/litemall/admin/web/AdminUserController.java`

```java
@Autowired
private SicauStudentAuthService studentAuthService;

@Autowired
private AesUtil aesUtil;

/**
 * 获取待审核列表
 */
@GetMapping("/auth/list")
public Object getAuthList() {
    List<SicauStudentAuth> authList = studentAuthService.listByStatus((byte) 0);
    
    // 解密学号和姓名
    List<Map<String, Object>> result = new ArrayList<>();
    for (SicauStudentAuth auth : authList) {
        try {
            Map<String, Object> item = new HashMap<>();
            item.put("id", auth.getId());
            item.put("userId", auth.getUserId());
            item.put("studentNo", aesUtil.decrypt(auth.getStudentNo()));
            item.put("realName", aesUtil.decrypt(auth.getRealName()));
            item.put("college", auth.getCollege());
            item.put("major", auth.getMajor());
            item.put("studentCardUrl", auth.getStudentCardUrl());
            item.put("createTime", auth.getCreateTime());
            result.add(item);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    return ResponseUtil.ok(result);
}

/**
 * 审核学号认证
 */
@PostMapping("/auth/audit")
@RequiresPermissions("admin:user:update")
public Object auditAuth(@LoginAdmin Integer adminId, @RequestBody Map<String, Object> body) {
    Integer userId = (Integer) body.get("userId");
    Integer status = (Integer) body.get("status"); // 1-通过, 2-拒绝
    String reason = (String) body.get("reason");
    
    if (userId == null || status == null) {
        return ResponseUtil.badArgument();
    }
    
    if (status != 1 && status != 2) {
        return ResponseUtil.badArgumentValue("状态值错误");
    }
    
    if (status == 2 && (reason == null || reason.isEmpty())) {
        return ResponseUtil.badArgumentValue("拒绝时必须填写原因");
    }
    
    SicauStudentAuth auth = studentAuthService.getByUserId(userId);
    if (auth == null) {
        return ResponseUtil.fail(404, "认证记录不存在");
    }
    
    auth.setStatus(status.byteValue());
    auth.setAuditAdminId(adminId);
    auth.setAuditTime(LocalDateTime.now());
    auth.setAuditReason(reason);
    
    studentAuthService.updateById(auth);
    
    // TODO: 发送微信模板消息通知用户
    
    return ResponseUtil.ok("审核成功");
}
```

---

## 3. 前端代码实现

### 3.1 学号绑定页面

**文件**: `litemall-wx/pages/auth/bind-student/bind-student.js`

```javascript
const util = require('../../../utils/util.js');
const api = require('../../../config/api.js');

Page({
  data: {
    studentNo: '',
    realName: '',
    college: '',
    major: '',
    studentCardUrl: '',
    
    colleges: [
      "农学院", "动物科技学院", "动物医学院", "林学院", "园艺学院",
      "资源学院", "环境学院", "生命科学学院", "理学院", "信息工程学院",
      "水利水电学院", "机电学院", "食品学院", "经济学院", "管理学院",
      "风景园林学院", "马克思主义学院", "人文学院", "法学院",
      "艺术与传媒学院", "体育学院", "商学院"
    ],
    collegeIndex: 0,
    
    uploading: false,
    submitting: false
  },

  onLoad: function () {
    // 检查是否已提交过
    this.checkAuthStatus();
  },

  /**
   * 检查认证状态
   */
  checkAuthStatus: function () {
    util.request(api.AuthStatus, {}, 'GET').then(res => {
      if (res.errno === 0) {
        if (res.data.status === 0) {
          wx.showModal({
            title: '提示',
            content: '您已提交认证申请，请等待审核',
            showCancel: false,
            success: () => {
              wx.switchTab({ url: '/pages/index/index' });
            }
          });
        } else if (res.data.status === 1) {
          wx.showModal({
            title: '提示',
            content: '您已通过认证',
            showCancel: false,
            success: () => {
              wx.switchTab({ url: '/pages/index/index' });
            }
          });
        } else if (res.data.status === 2) {
          // 可以重新提交
          wx.showModal({
            title: '认证未通过',
            content: '原因：' + res.data.auditReason + '\n请重新提交',
            showCancel: false
          });
        }
      }
    });
  },

  /**
   * 学号输入
   */
  onStudentNoInput: function (e) {
    this.setData({ studentNo: e.detail.value });
  },

  /**
   * 姓名输入
   */
  onRealNameInput: function (e) {
    this.setData({ realName: e.detail.value });
  },

  /**
   * 专业输入
   */
  onMajorInput: function (e) {
    this.setData({ major: e.detail.value });
  },

  /**
   * 学院选择
   */
  onCollegeChange: function (e) {
    this.setData({
      collegeIndex: e.detail.value,
      college: this.data.colleges[e.detail.value]
    });
  },

  /**
   * 上传学生证
   */
  uploadStudentCard: function () {
    const that = this;
    
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
      success: function (res) {
        const tempFilePath = res.tempFilePaths[0];
        
        // 检查文件大小
        wx.getFileInfo({
          filePath: tempFilePath,
          success: function (fileInfo) {
            if (fileInfo.size > 5 * 1024 * 1024) {
              wx.showToast({
                title: '图片不能超过5MB',
                icon: 'none'
              });
              return;
            }
            
            that.setData({ uploading: true });
            
            // 上传到阿里云 OSS
            that.uploadToOSS(tempFilePath);
          }
        });
      }
    });
  },

  /**
   * 上传到阿里云 OSS
   */
  uploadToOSS: function (filePath) {
    const that = this;
    
    // 1. 先获取 STS Token
    util.request(api.OssToken, {}, 'GET').then(res => {
      if (res.errno === 0) {
        const ossData = res.data;
        
        // 2. 上传文件
        wx.uploadFile({
          url: ossData.uploadUrl,
          filePath: filePath,
          name: 'file',
          formData: {
            key: ossData.key,
            policy: ossData.policy,
            OSSAccessKeyId: ossData.accessKeyId,
            signature: ossData.signature
          },
          success: function () {
            const imageUrl = ossData.host + '/' + ossData.key;
            that.setData({
              studentCardUrl: imageUrl,
              uploading: false
            });
            wx.showToast({
              title: '上传成功',
              icon: 'success'
            });
          },
          fail: function () {
            that.setData({ uploading: false });
            wx.showToast({
              title: '上传失败',
              icon: 'none'
            });
          }
        });
      }
    });
  },

  /**
   * 提交认证
   */
  submitAuth: function () {
    const { studentNo, realName, college, major, studentCardUrl } = this.data;
    
    // 表单验证
    if (!studentNo || !/^\d{12}$/.test(studentNo)) {
      wx.showToast({
        title: '请输入正确的12位学号',
        icon: 'none'
      });
      return;
    }
    
    if (!realName || realName.length < 2 || realName.length > 10) {
      wx.showToast({
        title: '请输入2-10个字的真实姓名',
        icon: 'none'
      });
      return;
    }
    
    if (!college || !major) {
      wx.showToast({
        title: '请完整填写学院和专业',
        icon: 'none'
      });
      return;
    }
    
    if (!studentCardUrl) {
      wx.showToast({
        title: '请上传学生证照片',
        icon: 'none'
      });
      return;
    }
    
    // 提交
    this.setData({ submitting: true });
    
    util.request(api.AuthBind, {
      studentNo,
      realName,
      college,
      major,
      studentCardUrl
    }, 'POST').then(res => {
      this.setData({ submitting: false });
      
      if (res.errno === 0) {
        wx.showModal({
          title: '提交成功',
          content: '请等待管理员审核（3个工作日内）',
          showCancel: false,
          success: () => {
            wx.switchTab({ url: '/pages/index/index' });
          }
        });
      } else {
        wx.showToast({
          title: res.errmsg || '提交失败',
          icon: 'none'
        });
      }
    }).catch(() => {
      this.setData({ submitting: false });
    });
  }
});
```

### 3.2 WXML 页面

**文件**: `litemall-wx/pages/auth/bind-student/bind-student.wxml`

```xml
<view class="container">
  <view class="form-box">
    <view class="form-title">学号实名认证</view>
    <view class="form-subtitle">完成认证后可发布商品</view>
    
    <view class="form-item">
      <text class="label">学号 *</text>
      <input class="input" type="number" maxlength="12" 
             placeholder="请输入12位学号" 
             value="{{studentNo}}" 
             bindinput="onStudentNoInput" />
    </view>
    
    <view class="form-item">
      <text class="label">真实姓名 *</text>
      <input class="input" type="text" maxlength="10" 
             placeholder="请输入真实姓名" 
             value="{{realName}}" 
             bindinput="onRealNameInput" />
    </view>
    
    <view class="form-item">
      <text class="label">学院 *</text>
      <picker mode="selector" range="{{colleges}}" value="{{collegeIndex}}" bindchange="onCollegeChange">
        <view class="picker">{{college || '请选择学院'}}</view>
      </picker>
    </view>
    
    <view class="form-item">
      <text class="label">专业 *</text>
      <input class="input" type="text" 
             placeholder="请输入专业名称" 
             value="{{major}}" 
             bindinput="onMajorInput" />
    </view>
    
    <view class="form-item">
      <text class="label">学生证照片 *</text>
      <view class="upload-box" bindtap="uploadStudentCard">
        <image wx:if="{{studentCardUrl}}" class="preview" src="{{studentCardUrl}}" mode="aspectFill"></image>
        <view wx:else class="upload-placeholder">
          <text class="iconfont icon-camera"></text>
          <text class="upload-text">{{uploading ? '上传中...' : '点击上传'}}</text>
        </view>
      </view>
    </view>
    
    <button class="submit-btn" 
            bindtap="submitAuth" 
            loading="{{submitting}}"
            disabled="{{submitting}}">
      提交认证
    </button>
  </view>
</view>
```

---

## 4. 配置文件修改

### 4.1 添加 API 地址

**文件**: `litemall-wx/config/api.js`

```javascript
module.exports = {
  AuthBind: WxApiRoot + 'auth/bindStudentNo',
  AuthStatus: WxApiRoot + 'auth/status',
  OssToken: WxApiRoot + 'storage/stsToken',  // 获取 OSS STS Token
  // ...
};
```

### 4.2 添加 AES 密钥配置

**文件**: `litemall-all/src/main/resources/application.yml`

```yaml
sicau:
  aes:
    key: "your-32-byte-secret-key-here!!"  # 32字节密钥
```

---

## 5. 数据库迁移脚本

**文件**: `litemall-db/sql/V1.2__create_student_auth_table.sql`

```sql
CREATE TABLE `sicau_student_auth` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '关联 litemall_user.id',
  `student_no` VARCHAR(255) NOT NULL COMMENT '学号 (AES-256 加密存储)',
  `real_name` VARCHAR(255) NOT NULL COMMENT '真实姓名 (AES-256 加密存储)',
  `college` VARCHAR(100) COMMENT '学院',
  `major` VARCHAR(100) COMMENT '专业',
  `student_card_url` VARCHAR(255) COMMENT '学生证照片 URL',
  `status` TINYINT DEFAULT 0 COMMENT '0-待审核, 1-通过, 2-拒绝',
  `audit_admin_id` INT COMMENT '审核管理员 ID',
  `audit_time` DATETIME COMMENT '审核时间',
  `audit_reason` VARCHAR(255) COMMENT '拒绝原因',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生实名认证表';
```

---

## 6. 测试指南

### 6.1 单元测试

**文件**: `litemall-wx-api/src/test/java/org/linlinjava/litemall/wx/WxAuthServiceTest.java`

```java
@Test
public void testBindStudentNo_Success() throws Exception {
    // 准备数据
    Map<String, String> body = new HashMap<>();
    body.put("studentNo", "202112345678");
    body.put("realName", "张三");
    body.put("college", "信息工程学院");
    body.put("major", "计算机科学与技术");
    body.put("studentCardUrl", "https://...");
    
    // 调用接口
    Object result = wxAuthController.bindStudentNo(1, body);
    
    // 验证
    assertNotNull(result);
    
    // 验证数据库中学号已加密
    SicauStudentAuth auth = studentAuthService.getByUserId(1);
    assertNotNull(auth);
    assertNotEquals("202112345678", auth.getStudentNo()); // 应该是加密后的
    assertEquals("张三", aesUtil.decrypt(auth.getRealName()));
}
```

---

## 7. 安全检查清单

- [ ] 学号和姓名在数据库中已加密存储
- [ ] AES 密钥不在代码中硬编码，从配置文件读取
- [ ] 管理员审核接口已添加权限验证 (`@RequiresPermissions`)
- [ ] 防止重复提交（检查 userId 唯一性）
- [ ] 防止学号被多人绑定（检查 studentNo 唯一性）
- [ ] OSS 图片上传使用 STS 临时凭证
- [ ] API 调用需要 JWT Token 验证

---

**状态**: 开发上下文已生成，等待开发人员实现  
**预计完成时间**: 8 小时  
**下一步**: 实现故事 1.3 (信用积分计算)
