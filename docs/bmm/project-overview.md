# Litemall 项目概览

**项目名称:** litemall - 小商场系统  
**项目类型:** 电商平台（B2C）  
**架构模式:** 前后端分离 + 微信小程序  
**当前版本:** 0.1.0  
**开发语言:** Java 8, JavaScript (ES6+), WXML  

---

## 📖 项目简介

litemall 是一个完整的小商场电商系统，提供三端应用：

1. **管理后台** - 商家管理系统（Web）
2. **移动商城** - H5 移动端商城（Web）
3. **微信小程序** - 用户购物小程序（WeChat Mini Program）

### 核心理念

- **全栈技术栈** - 覆盖前后端和小程序开发
- **企业级架构** - 可扩展、可维护的代码结构
- **开箱即用** - 完整的业务功能实现
- **学习参考** - 适合学习 Spring Boot + Vue 整合

---

## 🏢 业务领域

### 目标用户

**B端（商家）:**
- 商城管理员
- 运营人员
- 客服人员

**C端（消费者）:**
- 普通用户
- 会员用户

### 业务场景

- **商品销售** - 在线商品展示和销售
- **订单管理** - 完整的订单生命周期
- **营销活动** - 优惠券、团购、促销
- **用户运营** - 用户管理、反馈处理
- **数据分析** - 销售统计、用户分析

---

## 🎯 功能模块

### 1. 小商城功能（C端）

#### 商品浏览
- ✅ 首页推荐
- ✅ 分类导航
- ✅ 品牌专区
- ✅ 新品首发
- ✅ 人气推荐
- ✅ 商品搜索
- ✅ 商品详情
- ✅ 商品评价

#### 购物流程
- ✅ 加入购物车
- ✅ 购物车管理
- ✅ 立即购买
- ✅ 下单结算
- ✅ 在线支付（微信支付）
- ✅ 订单追踪

#### 用户中心
- ✅ 个人信息
- ✅ 收货地址
- ✅ 我的订单
- ✅ 我的收藏
- ✅ 浏览足迹
- ✅ 我的优惠券

#### 营销活动
- ✅ 优惠券领取
- ✅ 优惠券使用
- ✅ 团购活动
- ✅ 专题活动

#### 其他功能
- ✅ 订单售后
- ✅ 在线客服
- ✅ 意见反馈
- ✅ 帮助中心

### 2. 管理平台功能（B端）

#### 会员管理
- ✅ 会员列表
- ✅ 会员详情
- ✅ 收货地址管理
- ✅ 会员收藏
- ✅ 会员足迹
- ✅ 搜索历史
- ✅ 意见反馈

#### 商城管理
- ✅ 行政区域
- ✅ 品牌制造商
- ✅ 订单管理
- ✅ 订单详情
- ✅ 售后管理
- ✅ 评价管理
- ✅ 问题管理

#### 商品管理
- ✅ 商品列表
- ✅ 商品添加/编辑
- ✅ 商品分类
- ✅ 商品规格
- ✅ 库存管理
- ✅ 评论管理

#### 推广管理
- ✅ 广告管理
- ✅ 优惠券管理
- ✅ 专题管理
- ✅ 团购管理
- ✅ 团购规则

#### 系统管理
- ✅ 管理员管理
- ✅ 角色管理
- ✅ 权限管理
- ✅ 操作日志
- ✅ 通知管理

#### 配置管理
- ✅ 商城配置
- ✅ 运费配置
- ✅ 订单配置
- ✅ 小程序配置

#### 统计报表
- ✅ 用户统计
- ✅ 订单统计
- ✅ 商品统计
- ✅ 仪表盘

---

## 🛠️ 技术栈详解

### 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Spring Boot** | 2.1.5.RELEASE | 核心框架 |
| **MyBatis** | 1.3.2 | ORM 框架 |
| **MySQL** | 8.0.28 | 关系型数据库 |
| **Druid** | 1.2.1 | 数据库连接池 |
| **Apache Shiro** | 1.6.0 | 安全框架 |
| **PageHelper** | 1.2.5 | 分页插件 |
| **Hibernate Validator** | 6.2.0 | 参数验证 |
| **WeiXin Java Pay** | 4.1.0 | 微信支付 SDK |

### 前端技术栈（管理后台）

| 技术 | 版本 | 用途 |
|------|------|------|
| **Vue** | 2.6.10 | 核心框架 |
| **Element UI** | 2.15.6 | UI 组件库 |
| **Vue Router** | 3.0.2 | 路由管理 |
| **Vuex** | 3.1.0 | 状态管理 |
| **Axios** | >=0.21.2 | HTTP 客户端 |
| **ECharts** | 4.2.1 | 图表库 |
| **TinyMCE** | 3.0.1 | 富文本编辑器 |

### 前端技术栈（移动商城）

| 技术 | 版本 | 用途 |
|------|------|------|
| **Vue** | 2.5.17 | 核心框架 |
| **Vant** | 2.0.6 | 移动端 UI 库 |
| **Vue Router** | 3.0.1 | 路由管理 |
| **Vuex** | 3.4.0 | 状态管理 |
| **Axios** | >=0.21.1 | HTTP 客户端 |

### 微信小程序

- **原生小程序框架** - WXML + WXSS + JavaScript
- **WeiXin APIs** - 微信官方 API

---

## 📁 项目结构

### Maven 多模块结构

```
litemall/
├── pom.xml                          # 父 POM
│
├── litemall-core/                   # 核心模块
│   ├── src/main/java/
│   │   └── org/linlinjava/litemall/core/
│   │       ├── util/                # 工具类
│   │       ├── storage/             # 文件存储
│   │       ├── qcode/               # 二维码生成
│   │       ├── validator/           # 自定义验证器
│   │       └── express/             # 物流追踪
│   └── pom.xml
│
├── litemall-db/                     # 数据访问层
│   ├── src/main/java/
│   │   └── org/linlinjava/litemall/db/
│   │       ├── domain/              # 实体类（34个表）
│   │       ├── dao/                 # MyBatis Mapper
│   │       └── service/             # 数据服务（39个）
│   ├── src/main/resources/
│   │   ├── application-db.yml       # 数据库配置
│   │   └── org/linlinjava/litemall/db/dao/
│   │       └── *.xml                # MyBatis XML
│   ├── sql/                         # SQL 脚本
│   │   ├── litemall_schema.sql
│   │   ├── litemall_table.sql
│   │   └── litemall_data.sql
│   └── pom.xml
│
├── litemall-admin-api/              # 管理后台 API
│   ├── src/main/java/
│   │   └── org/linlinjava/litemall/admin/
│   │       ├── web/                 # REST Controllers (35+)
│   │       ├── service/             # 业务逻辑层
│   │       ├── dto/                 # 数据传输对象
│   │       ├── job/                 # 定时任务
│   │       └── config/              # 配置类
│   ├── src/main/resources/
│   │   └── application.yml          # 应用配置
│   └── pom.xml
│
├── litemall-wx-api/                 # 微信小程序 API
│   ├── src/main/java/
│   │   └── org/linlinjava/litemall/wx/
│   │       ├── web/                 # REST Controllers (27+)
│   │       ├── service/             # 业务逻辑层
│   │       ├── dto/                 # 数据传输对象
│   │       └── config/              # 配置类
│   └── pom.xml
│
├── litemall-all/                    # 打包模块（JAR）
│   └── pom.xml
│
├── litemall-all-war/                # 打包模块（WAR）
│   └── pom.xml
│
├── litemall-admin/                  # 管理后台前端
│   ├── src/
│   │   ├── api/                     # API 接口封装
│   │   ├── views/                   # 页面组件（54+）
│   │   ├── components/              # 公共组件
│   │   ├── router/                  # 路由配置
│   │   ├── store/                   # Vuex 状态
│   │   ├── styles/                  # 样式文件
│   │   └── utils/                   # 工具函数
│   ├── public/
│   ├── package.json
│   └── vue.config.js
│
├── litemall-vue/                    # 移动端前端
│   ├── src/
│   │   ├── api/                     # API 接口
│   │   ├── views/                   # 页面组件
│   │   ├── components/              # 公共组件
│   │   ├── router/                  # 路由配置
│   │   ├── store/                   # Vuex 状态
│   │   └── utils/                   # 工具函数
│   ├── package.json
│   └── vue.config.js
│
├── litemall-wx/                     # 微信小程序
│   ├── pages/                       # 小程序页面
│   ├── components/                  # 小程序组件
│   ├── utils/                       # 工具函数
│   ├── lib/                         # 第三方库
│   ├── static/                      # 静态资源
│   ├── app.js
│   ├── app.json
│   └── project.config.json
│
└── renard-wx/                       # 备用小程序
    └── (同 litemall-wx 结构)
```

---

## 🔌 API 架构

### RESTful API 设计

**基础路径:**
- 管理后台: `http://localhost:8080/admin/*`
- 微信小程序: `http://localhost:8080/wx/*`

**认证方式:**
- Header: `X-Litemall-Admin-Token` (管理后台)
- Header: `X-Litemall-Token` (微信小程序)

**响应格式:**
```json
{
  "errno": 0,
  "errmsg": "成功",
  "data": {...}
}
```

### 主要 API 模块

#### 管理后台 API (`/admin/*`)

| 模块 | 端点前缀 | 说明 |
|------|---------|------|
| 认证 | `/admin/auth` | 登录、登出、验证码 |
| 仪表盘 | `/admin/dashboard` | 统计数据 |
| 用户管理 | `/admin/user` | 会员管理 |
| 商品管理 | `/admin/goods` | 商品 CRUD |
| 订单管理 | `/admin/order` | 订单处理 |
| 优惠券 | `/admin/coupon` | 优惠券管理 |
| 系统管理 | `/admin/admin`, `/admin/role` | 管理员、角色 |

#### 微信小程序 API (`/wx/*`)

| 模块 | 端点前缀 | 说明 |
|------|---------|------|
| 认证 | `/wx/auth` | 微信登录 |
| 首页 | `/wx/home` | 首页数据 |
| 分类 | `/wx/catalog` | 商品分类 |
| 商品 | `/wx/goods` | 商品详情 |
| 购物车 | `/wx/cart` | 购物车操作 |
| 订单 | `/wx/order` | 订单提交、查询 |
| 用户 | `/wx/user` | 用户中心 |

---

## 💾 数据库设计

### 数据库表分类

**用户相关（5张表）**
- `litemall_user` - 用户基本信息
- `litemall_address` - 收货地址
- `litemall_footprint` - 浏览足迹
- `litemall_search_history` - 搜索历史
- `litemall_feedback` - 用户反馈

**商品相关（7张表）**
- `litemall_goods` - 商品主表
- `litemall_goods_product` - 商品货品（SKU）
- `litemall_goods_attribute` - 商品属性
- `litemall_goods_specification` - 商品规格
- `litemall_category` - 商品分类
- `litemall_brand` - 品牌
- `litemall_comment` - 商品评论

**订单相关（4张表）**
- `litemall_order` - 订单主表
- `litemall_order_goods` - 订单商品
- `litemall_cart` - 购物车
- `litemall_aftersale` - 售后

**营销相关（7张表）**
- `litemall_coupon` - 优惠券
- `litemall_coupon_user` - 用户优惠券
- `litemall_groupon` - 团购
- `litemall_groupon_rules` - 团购规则
- `litemall_ad` - 广告
- `litemall_topic` - 专题
- `litemall_collect` - 收藏

**系统相关（8张表）**
- `litemall_admin` - 管理员
- `litemall_role` - 角色
- `litemall_permission` - 权限
- `litemall_log` - 操作日志
- `litemall_storage` - 文件存储
- `litemall_system` - 系统配置
- `litemall_notice` - 通知
- `litemall_notice_admin` - 通知已读

**其他（3张表）**
- `litemall_region` - 行政区域
- `litemall_issue` - 常见问题
- `litemall_keyword` - 关键词

---

## 🔐 安全设计

### 认证机制

**Apache Shiro 框架**
- 基于 Token 的无状态认证
- JWT Token 生成和验证
- Token 过期时间: 30天（可配置）

### 权限控制

**RBAC 模型（基于角色的访问控制）**
- 管理员 → 角色 → 权限
- 细粒度的 API 权限控制
- 菜单级别的前端权限

### 数据安全

- **密码加密:** BCrypt 哈希算法
- **SQL 注入防护:** MyBatis 参数化查询
- **XSS 防护:** 输入过滤和输出编码
- **CSRF 防护:** Token 验证

---

## 📱 小程序特性

### 微信能力集成

- **微信登录:** 一键授权登录
- **微信支付:** JSAPI 支付
- **模板消息:** 订单通知
- **客服消息:** 在线客服
- **分享功能:** 商品、专题分享

### 小程序架构

- **目录结构:** 基于微信小程序规范
- **组件化:** 可复用的自定义组件
- **状态管理:** 全局数据共享
- **网络请求:** 统一的请求封装

---

## 🚀 部署架构

### 开发环境

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Vue Admin  │────▶│ Spring Boot │────▶│   MySQL     │
│  :9527      │     │  :8080      │     │   :3306     │
└─────────────┘     └─────────────┘     └─────────────┘
                           │
┌─────────────┐            │
│  Vue Mobile │────────────┤
│  :6255      │            │
└─────────────┘            │
                           │
┌─────────────┐            │
│ WeChat Mini │────────────┘
│  Program    │
└─────────────┘
```

### 生产环境（推荐）

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│   Nginx     │────▶│ Spring Boot  │────▶│ MySQL Master │
│   :80/443   │     │  Cluster     │     │              │
└─────────────┘     └──────────────┘     └──────┬───────┘
                           │                     │
                           │              ┌──────▼───────┐
                           │              │ MySQL Slave  │
                           │              │  (Read Only) │
                           │              └──────────────┘
                           │
                    ┌──────▼───────┐
                    │    Redis     │
                    │   (Cache)    │
                    └──────────────┘
```

---

## 📊 性能考虑

### 数据库优化

- **索引优化:** 主键、外键、常用查询字段
- **分页查询:** PageHelper 插件
- **连接池:** Druid 连接池（10-50 连接）
- **查询缓存:** MyBatis 二级缓存（可选）

### 应用层优化

- **异步处理:** Spring @Async 注解
- **定时任务:** Spring Schedule
- **文件存储:** 本地存储 / 阿里云 OSS
- **日志系统:** SLF4J + Logback

---

## 🧪 测试策略

### 单元测试

- **JUnit 4** - Java 单元测试
- **Mockito** - Mock 对象
- **目标覆盖率:** >60%

### 集成测试

- **Spring Boot Test** - 集成测试框架
- **H2 Database** - 内存数据库测试

---

## 📚 学习资源

### 官方文档

- [项目 GitBook](https://linlinjava.gitbook.io/litemall)
- [GitHub Repository](https://github.com/linlinjava/litemall)
- [码云 Gitee](https://gitee.com/linlinjava/litemall)

### 技术文档

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Vue.js 官方文档](https://cn.vuejs.org/)
- [Element UI 文档](https://element.eleme.cn/)
- [微信小程序文档](https://developers.weixin.qq.com/miniprogram/dev/framework/)

---

## 🎯 适用场景

### 适合作为

✅ **学习项目** - 学习 Spring Boot + Vue 全栈开发  
✅ **毕业设计** - 完整的电商系统实现  
✅ **技术参考** - 企业级代码结构和最佳实践  
✅ **快速原型** - 基于此项目快速开发定制化电商系统  

### 不适合场景

❌ 大规模高并发电商平台（需要微服务架构）  
❌ 多商户入驻平台（当前是单商户）  
❌ 跨境电商（需要多语言、多货币支持）  

---

## 🔮 扩展方向

### 功能扩展

- **会员系统:** 会员等级、积分系统
- **营销工具:** 秒杀、拼团、砍价
- **分销系统:** 三级分销
- **直播功能:** 小程序直播带货
- **社区功能:** 用户评论、问答

### 技术升级

- **缓存层:** Redis 缓存
- **消息队列:** RabbitMQ / Kafka
- **搜索引擎:** Elasticsearch
- **微服务化:** Spring Cloud
- **容器化:** Docker + Kubernetes

---

*本文档由 BMAD document-project 工作流生成*  
*扫描日期: 2025-10-27 | 分析师: Mary*
