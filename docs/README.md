# 四川农业大学校园闲置物品交易系统 - 文档导航

**项目名称**: litemall-campus  
**开发方法**: BMAD Method v6-alpha  
**文档结构版本**: v2.0 (2025-10-27 优化)

---

## 📁 文档结构说明

本项目采用 **Epic 级别文档** 作为开发指导，不再为每个 Story 创建单独文件。

```
docs/
├── README.md                           # 📖 本文档（文档导航）
├── PRD.md                              # 📋 产品需求文档
├── epics.md                            # 📦 Epic 和 Story 拆分
├── architecture.md                     # 🏗️ 架构决策记录 (ADR)
├── sprint-status.yaml                  # 📊 Sprint 状态跟踪
│
├── epic-1-context.md                   # 🎯 Epic 1 技术上下文
├── epic-1-implementation-summary.md    # ✅ Epic 1 实施总结
│
├── epic-2-context.md                   # 🎯 Epic 2 技术上下文
├── epic-2-implementation-summary.md    # ✅ Epic 2 实施总结（待创建）
│
└── bmm/                                # BMAD 方法论文档
    ├── method-overview.md              # BMAD 方法概述
    └── workflow.md                     # 工作流程说明
```

---

## 📚 核心文档说明

### 1. PRD.md - 产品需求文档
- **作用**: 定义产品目标、用户画像、功能需求
- **读者**: 全体团队成员
- **更新频率**: 需求变更时

### 2. epics.md - Epic 和 Story 拆分
- **作用**: 将 PRD 拆分为 7 个 Epic、34 个 Story
- **读者**: Sprint Master、Developer
- **更新频率**: Sprint 规划时

### 3. architecture.md - 架构决策记录
- **作用**: 记录关键技术决策（ADR）
- **读者**: 架构师、Developer
- **更新频率**: 技术选型或架构变更时

### 4. sprint-status.yaml - Sprint 状态跟踪
- **作用**: 实时跟踪每个 Epic 和 Story 的状态
- **读者**: 全体团队成员
- **更新频率**: 每个 Story 完成时

### 5. epic-N-context.md - Epic 技术上下文
- **作用**: 包含该 Epic 所有 Story 的详细技术实现指导
- **内容**:
  - Epic 概述
  - 架构决策引用
  - 数据库变更（SQL 脚本）
  - 核心代码实现（完整示例）
  - API 契约定义
  - 配置文件变更
  - 测试策略
  - Story 任务分解
- **读者**: Developer (bmm-dev)
- **生成者**: Sprint Master (bmm-sm)

### 6. epic-N-implementation-summary.md - Epic 实施总结
- **作用**: 记录 Epic 完成后的交付成果
- **内容**:
  - 进度总览
  - 每个 Story 的实施细节
  - 代码文件清单
  - API 文档
  - 验收清单
  - 业务价值
- **读者**: 全体团队成员
- **生成者**: Developer (bmm-dev)

---

## 🔄 BMAD 工作流程

### Phase 1: Epic 规划（Sprint Master）
1. 阅读 `PRD.md` 和 `epics.md`
2. 阅读 `architecture.md` 了解技术约束
3. 生成 `epic-N-context.md` 技术上下文文档
4. 更新 `sprint-status.yaml` 状态为 `contexted`

### Phase 2: Story 开发（Developer）
1. 阅读 `epic-N-context.md`
2. 按 Story 顺序实施（使用 Todo List 跟踪）
3. 每完成一个 Story，更新 `sprint-status.yaml` 状态为 `completed`
4. 验证代码无编译错误

### Phase 3: Epic 总结（Developer）
1. 创建 `epic-N-implementation-summary.md`
2. 记录所有代码变更、API、测试结果
3. 更新 `sprint-status.yaml` Epic 状态为 `completed`

---

## 📊 当前进度（2025-10-27）

| Epic ID | Epic 名称 | 状态 | Story 进度 | 文档 |
|---------|---------|------|-----------|------|
| Epic 1 | 用户认证与信用体系 | ✅ Completed | 5/5 (100%) | [Context](epic-1-context.md) · [Summary](epic-1-implementation-summary.md) |
| Epic 2 | 商品发布与管理 | 📝 Contexted | 0/6 (0%) | [Context](epic-2-context.md) |
| Epic 3 | 交易流程与支付 | 📋 Backlog | 0/7 (0%) | - |
| Epic 4 | 学生快递员配送系统 | 📋 Backlog | 0/5 (0%) | - |
| Epic 5 | 限时拍卖模块 | 📋 Backlog | 0/4 (0%) | - |
| Epic 6 | 公益捐赠通道 | 📋 Backlog | 0/3 (0%) | - |
| Epic 7 | 管理后台增强 | 📋 Backlog | 0/4 (0%) | - |

**总进度**: 5/34 Stories (14.7%)

---

## 🎯 快速开始

### 如果你是 Sprint Master
1. 阅读 `PRD.md` 和 `epics.md`
2. 选择下一个要规划的 Epic
3. 生成 `epic-N-context.md` 技术上下文
4. 通知 Developer 开始开发

### 如果你是 Developer
1. 阅读对应的 `epic-N-context.md`
2. 按 Story 顺序实施
3. 完成后创建 `epic-N-implementation-summary.md`
4. 更新 `sprint-status.yaml`

### 如果你是新成员
1. 阅读 `PRD.md` 了解产品目标
2. 阅读 `architecture.md` 了解技术栈
3. 查看 `sprint-status.yaml` 了解当前进度
4. 阅读已完成的 Epic 总结文档

---

## 📝 文档编写规范

### Epic Context 文档必须包含
- [ ] Epic 概述（目标、工时、依赖）
- [ ] 架构决策引用（从 architecture.md）
- [ ] 数据库变更（完整 SQL 脚本）
- [ ] 核心代码实现（完整可运行的示例）
- [ ] API 契约定义（请求/响应示例）
- [ ] 配置文件变更（YAML 配置）
- [ ] 测试策略（单元/集成/性能）
- [ ] Story 任务分解（每个 Story 的详细 Task）
- [ ] 验收清单

### Epic Summary 文档必须包含
- [ ] 总体进度表
- [ ] 每个 Story 的实施内容
- [ ] 代码文件清单（新增/修改）
- [ ] 代码统计（行数）
- [ ] 核心特性列表
- [ ] API 文档（完整请求/响应）
- [ ] 验收清单（已勾选）
- [ ] 业务价值说明

---

## 🔗 相关链接

- **litemall 原项目**: https://github.com/linlinjava/litemall
- **微信小程序文档**: https://developers.weixin.qq.com/miniprogram/dev/framework/
- **Spring Boot 文档**: https://spring.io/projects/spring-boot
- **BMAD 方法论**: [bmm/method-overview.md](bmm/method-overview.md)

---

## 📞 联系方式

- **项目负责人**: Ajie
- **开发周期**: 2025.10 - 2026.04
- **目标**: 四川农业大学毕业设计项目

---

**最后更新**: 2025-10-27  
**文档版本**: v2.0 (优化后的 Epic 级别文档结构)
