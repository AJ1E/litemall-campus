# Litemall 架构文档

**文档版本:** 2.0  
**更新日期:** 2025-10-27  
**架构类型:** 前后端分离 + 微信小程序  
**架构模式:** 分层架构（Layered Architecture）

---

## 🏛️ 系统架构概览

### 整体架构图

```
┌──────────────────────────────────────────────────────────────┐
│                        客户端层                               │
├──────────────┬──────────────┬───────────────────────────────┤
│              │              │                                │
│ 管理后台     │  移动H5商城  │      微信小程序               │
│ (Vue+Element)│  (Vue+Vant)  │    (WeChat Mini)              │
│              │              │                                │
└──────┬───────┴──────┬───────┴────────────┬──────────────────┘
       │              │                    │
       │    HTTP/HTTPS Requests            │
       │              │                    │
┌──────▼──────────────▼────────────────────▼──────────────────┐
│                      API 网关层 (Nginx)                      │
└──────┬──────────────────────────────────────┬───────────────┘
       │                                      │
┌──────▼────────────┐              ┌─────────▼─────────────────┐
│  管理后台 API     │              │   微信小程序 API          │
│  litemall-admin-api│              │  litemall-wx-api          │
│  (Spring Boot)    │              │  (Spring Boot)            │
└──────┬────────────┘              └─────────┬─────────────────┘
       │                                      │
       │              ┌───────────────────────┘
       │              │
┌──────▼──────────────▼────────────────────────────────────────┐
│                      业务逻辑层                               │
│                   litemall-core (公共模块)                    │
│           工具类 | 存储 | 验证器 | 快递 | 二维码             │
└──────┬───────────────────────────────────────────────────────┘
       │
┌──────▼───────────────────────────────────────────────────────┐
│                      数据访问层                               │
│                    litemall-db (MyBatis)                      │
│      Service层 (39个服务) | DAO层 (34个Mapper)               │
└──────┬───────────────────────────────────────────────────────┘
       │
┌──────▼───────────────────────────────────────────────────────┐
│                      数据存储层                               │
│                   MySQL 8.0 / TiDB Cloud                      │
│                      34张业务表                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 📐 架构模式

### 1. 分层架构（Layered Architecture）

#### 表示层 (Presentation Layer)
**职责:** 用户界面和API端点

**组件:**
- **管理后台:** Vue.js + Element UI
- **移动商城:** Vue.js + Vant
- **微信小程序:** WXML + WXSS + JS
- **REST Controllers:** 35个管理端控制器 + 27个微信端控制器

**关键技术:**
- Vue Router - 路由管理
- Vuex - 状态管理
- Axios - HTTP客户端
- Spring MVC - REST API

#### 业务逻辑层 (Business Logic Layer)
**职责:** 核心业务规则和流程

**组件:**
- **Service层:** 业务服务实现
- **DTO层:** 数据传输对象
- **Validator:** 业务规则验证
- **Job:** 定时任务（订单自动取消、优惠券过期等）

**核心服务:**
- `AdminGoodsService` - 商品管理逻辑
- `AdminOrderService` - 订单处理逻辑
- `WxOrderService` - 微信下单逻辑

#### 数据访问层 (Data Access Layer)
**职责:** 数据持久化和查询

**组件:**
- **Domain:** 实体类（34个）
- **Mapper:** MyBatis接口（34个）
- **Service:** 数据服务（39个）

**关键技术:**
- MyBatis - ORM框架
- Druid - 数据库连接池
- PageHelper - 分页插件

#### 数据存储层 (Data Storage Layer)
**职责:** 数据持久化

**组件:**
- MySQL 8.0 / TiDB Cloud
- 34张业务表
- 索引优化
- 事务管理

---

## 🔧 核心模块设计

### 模块依赖关系

```
litemall (Parent POM)
├── litemall-core (核心工具模块)
│   └── 被所有模块依赖
│
├── litemall-db (数据访问模块)
│   └── 依赖: litemall-core
│
├── litemall-admin-api (管理后台API)
│   ├── 依赖: litemall-db
│   └── 依赖: litemall-core
│
├── litemall-wx-api (微信API)
│   ├── 依赖: litemall-db
│   └── 依赖: litemall-core
│
├── litemall-all (打包模块 - JAR)
│   ├── 依赖: litemall-admin-api
│   └── 依赖: litemall-wx-api
│
└── litemall-all-war (打包模块 - WAR)
    ├── 依赖: litemall-admin-api
    └── 依赖: litemall-wx-api
```

### litemall-core 模块

**包结构:**
```
org.linlinjava.litemall.core/
├── util/                 # 工具类
│   ├── JacksonUtil       # JSON处理
│   ├── ResponseUtil      # 统一响应
│   ├── bcrypt/           # 密码加密
│   └── ...
├── storage/              # 文件存储
│   ├── StorageService    # 存储接口
│   ├── LocalStorage      # 本地存储
│   └── AliyunStorage     # 阿里云OSS
├── qcode/                # 二维码
│   └── QCodeService
├── validator/            # 自定义验证器
│   ├── Order             # 排序验证
│   └── Sort              # 排序验证
└── express/              # 物流
    └── ExpressService
```

**职责:**
- 提供公共工具类
- 文件上传和存储
- 二维码生成
- 物流信息查询
- 自定义验证器

### litemall-db 模块

**包结构:**
```
org.linlinjava.litemall.db/
├── domain/               # 实体类 (34个)
│   ├── LitemallGoods
│   ├── LitemallOrder
│   ├── LitemallUser
│   └── ...
├── dao/                  # MyBatis Mapper (34个)
│   ├── LitemallGoodsMapper
│   ├── LitemallOrderMapper
│   └── ...
└── service/              # 数据服务 (39个)
    ├── LitemallGoodsService
    ├── LitemallOrderService
    ├── StatService       # 统计服务
    └── ...
```

**关键Service:**
- `LitemallGoodsService` - 商品数据操作
- `LitemallOrderService` - 订单数据操作
- `LitemallUserService` - 用户数据操作
- `LitemallCouponService` - 优惠券数据操作
- `StatService` - 统计数据查询

### litemall-admin-api 模块

**包结构:**
```
org.linlinjava.litemall.admin/
├── web/                  # REST Controllers (35个)
│   ├── AdminAuthController       # 认证
│   ├── AdminGoodsController      # 商品管理
│   ├── AdminOrderController      # 订单管理
│   ├── AdminUserController       # 用户管理
│   ├── AdminDashbordController   # 仪表盘
│   └── ...
├── service/              # 业务服务
│   ├── AdminGoodsService
│   ├── AdminOrderService
│   └── LogHelper         # 日志助手
├── dto/                  # 数据传输对象
│   ├── GoodsAllinone
│   └── OrderVo
├── job/                  # 定时任务
│   ├── OrderJob          # 订单任务
│   ├── CouponJob         # 优惠券任务
│   └── DbJob             # 数据库任务
└── config/               # 配置类
    └── ShiroConfig       # Shiro安全配置
```

**核心Controller:**
```java
@RestController
@RequestMapping("/admin")
public class AdminGoodsController {
    @PostMapping("/goods/list")
    @RequiresPermissions("admin:goods:list")
    public Object list(@RequestBody String body) {
        // 商品列表查询
    }
}
```

### litemall-wx-api 模块

**包结构:**
```
org.linlinjava.litemall.wx/
├── web/                  # REST Controllers (27个)
│   ├── WxAuthController        # 微信登录
│   ├── WxHomeController        # 首页
│   ├── WxGoodsController       # 商品详情
│   ├── WxCartController        # 购物车
│   ├── WxOrderController       # 订单
│   └── ...
├── service/              # 业务服务
│   ├── WxOrderService    # 订单业务
│   ├── UserInfoService   # 用户信息
│   └── GetRegionService  # 区域服务
├── dto/                  # 数据传输对象
│   └── ...
└── config/               # 配置类
    └── WxConfig          # 微信配置
```

**核心Controller:**
```java
@RestController
@RequestMapping("/wx")
public class WxOrderController {
    @PostMapping("/order/submit")
    public Object submit(@RequestBody String body, @LoginUser Integer userId) {
        // 订单提交
    }
}
```

---

## 🔒 安全架构

### 认证流程

#### 管理后台认证流程

```
┌─────────┐         ┌──────────┐         ┌──────────┐
│ Admin   │         │ Shiro    │         │ Database │
│ Client  │         │ Realm    │         │          │
└────┬────┘         └────┬─────┘         └────┬─────┘
     │                   │                     │
     │ 1. Login Request  │                     │
     ├──────────────────>│                     │
     │   (username/pwd)  │                     │
     │                   │ 2. Query Admin      │
     │                   ├────────────────────>│
     │                   │                     │
     │                   │ 3. Return Admin     │
     │                   │<────────────────────┤
     │                   │                     │
     │                   │ 4. Verify Password  │
     │                   │    (BCrypt)         │
     │                   │                     │
     │ 5. Return Token   │                     │
     │<──────────────────┤                     │
     │   (X-Litemall-    │                     │
     │    Admin-Token)   │                     │
     │                   │                     │
     │ 6. Subsequent     │                     │
     │    Requests       │                     │
     ├──────────────────>│                     │
     │   (with Token)    │ 7. Validate Token   │
     │                   │                     │
     │ 8. Response       │                     │
     │<──────────────────┤                     │
```

#### 微信小程序认证流程

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ WeChat   │    │ litemall │    │ WeChat   │    │ Database │
│ Mini App │    │ wx-api   │    │ Server   │    │          │
└────┬─────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘
     │               │               │               │
     │ 1. wx.login() │               │               │
     ├──────────────>│               │               │
     │  Get Code     │               │               │
     │               │ 2. code2Session              │
     │               ├──────────────>│               │
     │               │   (code)      │               │
     │               │               │               │
     │               │ 3. openid +   │               │
     │               │    session_key│               │
     │               │<──────────────┤               │
     │               │               │               │
     │               │ 4. Query/Create User         │
     │               ├─────────────────────────────>│
     │               │   (openid)    │               │
     │               │               │               │
     │               │ 5. User Info  │               │
     │               │<─────────────────────────────┤
     │               │               │               │
     │               │ 6. Generate Token            │
     │               │   (JWT)       │               │
     │               │               │               │
     │ 7. Return     │               │               │
     │    Token      │               │               │
     │<──────────────┤               │               │
     │  X-Litemall-  │               │               │
     │  Token        │               │               │
```

### 权限控制

#### RBAC模型

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│  Admin   │────▶│   Role   │────▶│Permission│
│ 管理员    │  N:M │   角色   │  N:M │   权限   │
└──────────┘     └──────────┘     └──────────┘
                                        │
                                        │
                                   ┌────▼─────┐
                                   │   API    │
                                   │  Endpoint│
                                   └──────────┘
```

**权限示例:**
```java
@RequiresPermissions("admin:goods:create")
public Object create(@RequestBody LitemallGoods goods) {
    // 需要 admin:goods:create 权限
}
```

**角色与权限映射:**
```
超级管理员 (Super Admin)
├── admin:*:*              # 所有权限

商品管理员 (Goods Manager)
├── admin:goods:list
├── admin:goods:create
├── admin:goods:update
└── admin:goods:delete

运营人员 (Operator)
├── admin:order:list
├── admin:order:detail
├── admin:user:list
└── admin:stat:*
```

---

## 💾 数据架构

### 数据库设计原则

1. **第三范式 (3NF)** - 消除冗余数据
2. **外键约束** - 保证数据完整性（部分表）
3. **索引优化** - 主键 + 常用查询字段
4. **逻辑删除** - deleted字段标记删除

### 核心表关系

```
litemall_user (用户表)
    ↓ 1:N
litemall_order (订单表)
    ↓ 1:N
litemall_order_goods (订单商品表)
    ↓ N:1
litemall_goods (商品表)
    ↓ 1:N
litemall_goods_product (货品SKU表)

litemall_user
    ↓ 1:N
litemall_cart (购物车)
    ↓ N:1
litemall_goods

litemall_user
    ↓ 1:N
litemall_coupon_user (用户优惠券)
    ↓ N:1
litemall_coupon (优惠券)
```

### 表设计模式

**通用字段:**
```sql
id INT PRIMARY KEY AUTO_INCREMENT,
add_time DATETIME DEFAULT CURRENT_TIMESTAMP,
update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
deleted TINYINT(1) DEFAULT 0
```

**软删除:**
```java
// MyBatis查询自动过滤deleted=1的记录
@Select("SELECT * FROM litemall_goods WHERE id = #{id} AND deleted = 0")
LitemallGoods selectByPrimaryKey(Integer id);
```

---

## 🔌 API设计

### RESTful API规范

**URL设计:**
```
GET    /admin/goods/list       # 查询列表
GET    /admin/goods/detail     # 查询详情
POST   /admin/goods/create     # 创建
POST   /admin/goods/update     # 更新
POST   /admin/goods/delete     # 删除
```

**统一响应格式:**
```json
{
  "errno": 0,          // 错误码, 0表示成功
  "errmsg": "成功",    // 错误消息
  "data": {            // 业务数据
    "total": 100,
    "items": [...]
  }
}
```

**错误码设计:**
```
0       成功
401     未登录
402     参数错误
403     禁止访问
501     内部服务错误
502     服务不可用
6xx     业务错误码
```

---

## 🚀 部署架构

### 单机部署（开发/测试）

```
┌────────────────────────────────────┐
│         服务器 (Ubuntu/CentOS)      │
├────────────────────────────────────┤
│                                    │
│  ┌──────────┐    ┌──────────┐     │
│  │ Nginx    │────│ Java 8   │     │
│  │ :80      │    │ :8080    │     │
│  └──────────┘    └──────────┘     │
│                        │           │
│                  ┌─────▼──────┐    │
│                  │   MySQL    │    │
│                  │   :3306    │    │
│                  └────────────┘    │
│                                    │
│  静态文件: /var/www/litemall       │
└────────────────────────────────────┘
```

### 生产部署（推荐）

```
┌─────────────────────────────────────────────────────────┐
│                      负载均衡层                          │
│                   Nginx / LVS / HAProxy                  │
└──────────────┬─────────────────┬────────────────────────┘
               │                 │
    ┌──────────▼──────┐   ┌──────▼──────────┐
    │  App Server 1   │   │  App Server 2   │
    │  Spring Boot    │   │  Spring Boot    │
    │  :8080          │   │  :8080          │
    └──────────┬──────┘   └──────┬──────────┘
               │                 │
               └────────┬────────┘
                        │
            ┌───────────▼───────────┐
            │    Redis Cluster      │
            │    (Session/Cache)    │
            └───────────┬───────────┘
                        │
            ┌───────────▼───────────┐
            │   MySQL Master        │
            │   (Read/Write)        │
            └───────────┬───────────┘
                        │
            ┌───────────▼───────────┐
            │   MySQL Slave(s)      │
            │   (Read Only)         │
            └───────────────────────┘
```

---

## 📊 性能优化

### 数据库优化

**索引策略:**
```sql
-- 主键索引
PRIMARY KEY (id)

-- 唯一索引
UNIQUE KEY uk_mobile (mobile)

-- 普通索引
KEY idx_user_id (user_id)
KEY idx_add_time (add_time)

-- 复合索引
KEY idx_user_deleted (user_id, deleted)
```

**分页优化:**
```java
// 使用PageHelper插件
PageHelper.startPage(page, size);
List<LitemallGoods> goodsList = goodsService.querySelective(...);
PageInfo<LitemallGoods> pageInfo = PageInfo.of(goodsList);
```

### 应用层优化

**异步处理:**
```java
@Async
public void sendNotification(Integer userId, String message) {
    // 异步发送通知,不阻塞主流程
}
```

**缓存策略:**
```java
// MyBatis二级缓存(可选)
@CacheNamespace
public interface LitemallGoodsMapper {
    // 缓存查询结果
}
```

---

## 🔄 技术演进路径

### 当前架构优势

✅ **简单明了** - 容易理解和维护  
✅ **快速开发** - 适合中小型项目  
✅ **成本低** - 单机部署即可  
✅ **易于调试** - 单体应用便于排查问题

### 未来演进方向

#### 阶段1: 性能优化（短期）
- 引入Redis缓存
- 数据库主从分离
- 静态资源CDN

#### 阶段2: 可扩展性提升（中期）
- 拆分读写API
- 引入消息队列(RabbitMQ)
- Elasticsearch搜索引擎

#### 阶段3: 微服务化（长期）
- Spring Cloud微服务架构
- 服务注册与发现
- 配置中心
- API网关
- 分布式事务

---

*本文档由 BMAD document-project 工作流生成*  
*架构分析: Deep Scan | 生成日期: 2025-10-27*
