# Epic 4 数据库设置指南

**重要提示**: 在继续开发 Story 4.2-4.5 之前，需要先在 TiDB Cloud 数据库中创建以下表。

---

## 需要执行的 SQL 文件

### 1. sicau_building 表（楼栋坐标）

**文件位置**: `/workspaces/litemall-campus/litemall-db/sql/sicau_building.sql`

**执行命令**:
```bash
mysql -h gateway01.eu-central-1.prod.aws.tidbcloud.com \
      -P 4000 \
      -u '3gw7gessje9T2Cv.root' \
      -p'IWe7WiolqqAMoZ4z' \
      -D litemall \
      < /workspaces/litemall-campus/litemall-db/sql/sicau_building.sql
```

**表结构**:
- 33 个楼栋坐标（雅安本部 27 个 + 成都校区 6 个）
- 包含纬度、经度、楼栋类型等字段

---

### 2. sicau_courier 表（快递员）

**状态**: ✅ 已创建（Story 4.1 已完成）

**验证命令**:
```bash
mysql -h gateway01.eu-central-1.prod.aws.tidbcloud.com \
      -P 4000 \
      -u '3gw7gessje9T2Cv.root' \
      -p'IWe7WiolqqAMoZ4z' \
      -D litemall \
      -e "SHOW TABLES LIKE 'sicau_%'"
```

---

## 为什么需要手动创建？

**问题**: TiDB Cloud 数据库连接在容器中被拒绝  
**原因**: 数据库访问控制或网络限制  
**解决方案**: 

### 选项 1: 通过本地 MySQL 客户端连接 ⭐ (推荐)

在您的**本地电脑**上执行：

```bash
# 1. 创建 sicau_building 表
mysql -h gateway01.eu-central-1.prod.aws.tidbcloud.com \
      -P 4000 \
      -u '3gw7gessje9T2Cv.root' \
      -p'IWe7WiolqqAMoZ4z' \
      -D litemall \
      < litemall-db/sql/sicau_building.sql

# 2. 验证表创建成功
mysql -h gateway01.eu-central-1.prod.aws.tidbcloud.com \
      -P 4000 \
      -u '3gw7gessje9T2Cv.root' \
      -p'IWe7WiolqqAMoZ4z' \
      -D litemall \
      -e "SELECT COUNT(*) FROM sicau_building"
```

**预期结果**: 33 rows

---

### 选项 2: 通过 TiDB Cloud Web Console

1. 登录 TiDB Cloud Console
2. 进入 SQL Editor
3. 复制粘贴 `sicau_building.sql` 内容
4. 点击 "Run" 执行

---

### 选项 3: 使用代码中的临时解决方案

我可以创建一个简化版本，不依赖 sicau_building 表：
- 硬编码常用楼栋坐标
- 使用简单的距离估算
- 后续再迁移到数据库方案

---

## 创建表后的下一步

1. ✅ 表创建成功后，重新运行 MyBatis Generator：
   ```bash
   cd litemall-db && mvn mybatis-generator:generate
   ```

2. ✅ 继续实现 Story 4.2 的 Service 层和 API 层

3. ✅ 测试距离计算和订单查询功能

---

## 联系开发者

如果遇到数据库连接问题，请：
1. 检查 TiDB Cloud 白名单配置
2. 确认用户名密码正确
3. 尝试从本地电脑连接（而非容器）

