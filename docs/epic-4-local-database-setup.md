# Epic 4 本地数据库方案

由于 GitHub Codespace 网络限制无法安装 MariaDB，我们有以下替代方案：

## 🎯 推荐方案：使用 H2 数据库（内存数据库）

Spring Boot 内置支持，无需安装！

### 步骤 1: 添加 H2 依赖

在 `litemall-db/pom.xml` 添加：
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 步骤 2: 配置 H2

在 `application-dev.yml` 添加：
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:litemall
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
```

### 步骤 3: 执行 SQL 初始化

H2 会自动执行 `schema.sql` 和 `data.sql`

---

## ⚡ 更简单方案：硬编码楼栋坐标

**优点**: 
- 零配置，立即可用
- 不依赖数据库
- 适合开发阶段快速迭代

**实现**: 
创建 `BuildingCoordinates.java` 工具类，硬编码 33 个楼栋坐标

---

## 🔄 生产环境方案

**最终部署时**使用您的 TiDB Cloud 或 MySQL 数据库

---

**建议**: 现在使用硬编码方案继续开发，等 Epic 4 完成后再迁移到真实数据库
