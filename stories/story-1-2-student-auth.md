# 用户故事 1.2: 学号实名认证

**Story ID**: 1.2  
**Epic**: Epic 1 - 用户认证与信用体系  
**优先级**: P0  
**预估工时**: 8 小时  
**状态**: drafted  
**创建日期**: 2025-10-27  
**依赖**: 故事 1.1 (需要用户先完成微信登录)

---

## 用户故事描述

**作为** 普通用户  
**我想要** 绑定学号并提交真实姓名、学院信息  
**以便** 通过审核后发布商品和参与交易

---

## 验收标准

- [ ] 输入 12 位学号、真实姓名、学院、专业
- [ ] 上传学生证照片（≤ 5MB）
- [ ] 提交后状态显示"审核中"，3 个工作日内完成
- [ ] 审核通过后开放发布商品权限

---

## 技术实现

### 后端实现

#### API 1: 绑定学号

**接口**: `POST /wx/auth/bindStudentNo`

**请求头**:
```
X-Litemall-Token: eyJhbGciOiJIUzUxMiJ9...
```

**请求参数**:
```json
{
  "studentNo": "202112345678",
  "realName": "张三",
  "college": "信息工程学院",
  "major": "计算机科学与技术",
  "studentCardUrl": "https://litemall-campus.oss-cn-chengdu.aliyuncs.com/student-cards/xxx.jpg"
}
```

**响应数据**:
```json
{
  "errno": 0,
  "errmsg": "提交成功，请等待审核"
}
```

#### API 2: 查询认证状态

**接口**: `GET /wx/auth/status`

**请求头**:
```
X-Litemall-Token: eyJhbGciOiJIUzUxMiJ9...
```

**响应数据**:
```json
{
  "errno": 0,
  "data": {
    "status": 0,  // 0-待审核, 1-通过, 2-拒绝
    "studentNo": "202112345678",
    "realName": "张三",
    "college": "信息工程学院",
    "major": "计算机科学与技术",
    "auditReason": null  // 拒绝时才有值
  }
}
```

#### API 3: 管理员审核接口

**接口**: `POST /admin/user/auditAuth`

**请求参数**:
```json
{
  "userId": 123,
  "status": 1,  // 1-通过, 2-拒绝
  "reason": ""  // 拒绝时填写原因
}
```

**响应数据**:
```json
{
  "errno": 0,
  "errmsg": "审核成功"
}
```

### 前端实现

#### 文件位置
- `litemall-wx/pages/auth/bind-student/bind-student.js`
- `litemall-wx/pages/auth/bind-student/bind-student.wxml`
- `litemall-wx/pages/auth/bind-student/bind-student.wxss`

#### 关键功能
1. **表单输入**: 学号、姓名、学院、专业（学院专业支持选择器）
2. **图片上传**: 直传阿里云 OSS（使用 STS Token）
3. **表单验证**: 
   - 学号必须是 12 位数字
   - 真实姓名 2-10 个汉字
   - 学生证照片必须上传
4. **提交反馈**: 提交后显示"审核中"状态，禁止重复提交

---

## 数据库影响

### 新增表：sicau_student_auth

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

## 依赖项

### 后端依赖
- AES 加密工具类 (`AesUtil.java`) - 在 Epic 1 Context 中定义
- 阿里云 OSS SDK（用于生成 STS Token）
- `sicau_student_auth` 表已创建

### 前端依赖
- 阿里云 OSS 直传组件（需配置 Bucket 和 STS 服务）
- 四川农业大学学院列表数据

---

## 测试用例

### 单元测试
1. 测试 AES 加密和解密学号、姓名
2. 测试学号重复提交时返回错误
3. 测试管理员审核通过/拒绝逻辑

### 集成测试
1. 测试完整认证流程：提交 → 审核通过 → 状态更新
2. 测试审核拒绝后用户重新提交
3. 测试审核通过后用户可以发布商品

### 前端测试
1. 测试表单验证规则（学号格式、姓名长度）
2. 测试图片上传成功和失败场景
3. 测试提交后状态正确显示

### 安全测试
1. 测试数据库中学号和姓名是否加密存储
2. 测试未登录用户无法访问 API
3. 测试普通用户无法访问管理员审核接口

---

## 完成定义 (Definition of Done)

- [ ] 后端三个 API 实现并通过单元测试
- [ ] 前端页面实现并完成 UI 评审
- [ ] AES 加密工具类实现并测试
- [ ] 数据库表创建并执行迁移脚本
- [ ] 管理后台审核页面实现
- [ ] 集成测试通过（包括加密验证）
- [ ] 代码已提交并通过 Code Review
- [ ] 已部署到测试环境并验证

---

## 备注

### 安全注意事项
- 学号和真实姓名**必须**使用 AES-256-GCM 加密后存储
- 加密密钥存储在配置文件中，生产环境使用 Vault 管理
- 学生证照片 URL 不加密，但需设置 OSS 私有读权限

### 四川农业大学学院列表
```javascript
const colleges = [
  "农学院",
  "动物科技学院",
  "动物医学院",
  "林学院",
  "园艺学院",
  "资源学院",
  "环境学院",
  "生命科学学院",
  "理学院",
  "信息工程学院",
  "水利水电学院",
  "机电学院",
  "食品学院",
  "经济学院",
  "管理学院",
  "风景园林学院",
  "马克思主义学院",
  "人文学院",
  "法学院",
  "艺术与传媒学院",
  "体育学院",
  "商学院"
];
```

### 审核工作量预估
- 预计每天 100+ 学号认证申请
- 平均审核时间 1-2 分钟/条
- 建议配置 2-3 名管理员专职审核
- 高峰期（开学季）需增加人手

---

**下一步**: 生成开发上下文 (`story-1-2-student-auth-context.md`)
