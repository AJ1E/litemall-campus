# Litemall 项目文档索引

**文档版本:** 2.0  
**生成日期:** 2025-10-27  
**扫描级别:** Deep Scan  
**项目类型:** Multi-Module Monorepo (Java Spring Boot + Vue.js)

---

## 📋 快速导航

### 核心文档
- [项目概览](./project-overview.md) - 项目整体介绍、技术栈、功能模块
- [架构文档](./architecture.md) - 系统架构、模块设计、技术决策
- [源代码结构分析](./source-tree-analysis.md) - 目录结构详解
- [开发指南](./development-guide.md) - 本地开发环境配置和运行指南

### 后端文档
- [后端 API 接口](./api-contracts-backend.md) - RESTful API 端点列表
- [数据模型](./data-models.md) - 数据库表结构和实体类
- [服务层组件](./backend-services.md) - 业务逻辑服务清单

### 前端文档
- [管理后台组件清单](./component-inventory-admin.md) - Admin 前端组件
- [移动端组件清单](./component-inventory-vue.md) - Vue 移动端组件
- [前端路由](./frontend-routes.md) - 路由配置说明

### 集成文档
- [模块集成架构](./integration-architecture.md) - 前后端及微信小程序集成方式

---

## 🏗️ 项目结构概览

```
litemall/
├── litemall-db/              # 数据访问层（MyBatis）
├── litemall-core/            # 核心工具类和公共组件
├── litemall-admin-api/       # 管理后台 API（Spring Boot REST）
├── litemall-wx-api/          # 微信小程序 API（Spring Boot REST）
├── litemall-admin/           # 管理后台前端（Vue + Element UI）
├── litemall-vue/             # 移动端前端（Vue + Vant）
├── litemall-wx/              # 微信小程序用户端
├── litemall-all/             # 打包模块（可执行 JAR）
└── renard-wx/                # 备用微信小程序
```

---

## 🔑 关键特性

### 技术栈
- **后端:** Spring Boot 2.1.5, MyBatis, Druid, Shiro
- **前端:** Vue 2.x, Element UI 2.15, Vant 2.0
- **数据库:** MySQL 8.0 / TiDB Cloud
- **小程序:** 微信小程序原生框架
- **构建工具:** Maven 3.x, npm/Vue CLI

### 核心功能模块
1. **用户系统** - 用户注册、登录、个人中心
2. **商品管理** - 商品分类、品牌、SKU、库存
3. **订单系统** - 购物车、下单、支付、售后
4. **营销推广** - 优惠券、团购、广告
5. **内容管理** - 专题、评论、反馈
6. **系统管理** - 管理员、角色权限、配置

---

## 📊 项目规模

| 指标 | 数量 |
|------|------|
| **后端模块** | 6 个 |
| **前端应用** | 3 个（Admin + Vue + 小程序） |
| **数据库表** | 34 张 |
| **REST API 端点** | ~200+ |
| **后端 Service 类** | 39 个 |
| **管理后台 Vue 组件** | 54+ 个 |
| **代码行数估算** | 50,000+ LOC |

---

## 🚀 快速开始

### 启动后端服务
```bash
cd litemall
mvn clean install
java -Dfile.encoding=UTF-8 -jar litemall-all/target/litemall-all-0.1.0-exec.jar
```
**访问:** http://localhost:8080

### 启动管理后台
```bash
cd litemall-admin
npm install
npm run dev
```
**访问:** http://localhost:9527

### 启动移动端
```bash
cd litemall-vue
npm install
npm run dev
```
**访问:** http://localhost:6255

---

## 🗄️ 数据库配置

**当前环境:** TiDB Cloud  
**连接地址:** gateway01.eu-central-1.prod.aws.tidbcloud.com:4000  
**数据库名:** litemall  
**字符集:** UTF-8  
**时区:** Asia/Shanghai  

---

## 📦 Maven 模块依赖关系

```
litemall-all (可执行 JAR)
  ├── litemall-admin-api (管理后台 API)
  │   ├── litemall-db (数据访问)
  │   └── litemall-core (核心工具)
  └── litemall-wx-api (微信 API)
      ├── litemall-db (数据访问)
      └── litemall-core (核心工具)
```

---

## 🔐 安全机制

- **认证:** Apache Shiro + JWT Token
- **权限:** 基于角色的访问控制（RBAC）
- **加密:** BCrypt 密码加密
- **防护:** XSS、CSRF、SQL 注入防护

---

## 📱 小程序集成

- **微信登录:** 支持微信授权登录
- **微信支付:** 集成微信支付 SDK (weixin-java-pay 4.1.0)
- **消息推送:** 模板消息、订阅消息

---

## 🛠️ 开发工具

- **IDE:** IntelliJ IDEA (推荐), VSCode
- **API 测试:** Postman, curl
- **数据库管理:** MySQL Workbench, DBeaver
- **版本控制:** Git
- **微信开发工具:** 微信开发者工具

---

## 📖 相关文档链接

- [官方 GitBook 文档](https://linlinjava.gitbook.io/litemall)
- [API 接口文档](https://linlinjava.gitbook.io/litemall/api)
- [常见问题 FAQ](https://linlinjava.gitbook.io/litemall/faq)

---

## 🔄 最近更新

- **2025-10-27:** 完成项目深度扫描文档化
- **数据库:** 已迁移至 TiDB Cloud
- **依赖升级:** Axios >=0.21.2, XLSX >=0.17.0

---

## 📝 文档使用说明

### 为 AI 代理优化

本文档专为 BMAD AI 代理工作流设计，提供：

1. **快速上下文检索** - 通过索引快速定位所需信息
2. **结构化数据** - 清晰的表格和层次结构
3. **代码示例** - 实际运行命令和配置
4. **关联引用** - 文档间的交叉引用

### 使用建议

- **规划新功能时:** 先阅读架构文档和数据模型
- **实现 API 时:** 参考 API 接口文档和服务层组件
- **前端开发时:** 查看组件清单和路由配置
- **问题排查时:** 查看开发指南和集成架构

---

## 📧 联系方式

**项目维护者:** linlinjava  
**邮箱:** linlinjava@163.com  
**GitHub:** https://github.com/linlinjava/litemall  
**码云:** https://gitee.com/linlinjava/litemall

---

*本文档由 BMAD document-project 工作流自动生成*  
*扫描方式: Deep Scan | 分析师: Mary (BMM Analyst)*
