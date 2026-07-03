# Phase 34 报告 — 组件拆分（W6 行数红线落地，Claude 亲自执行）

| 项 | 值 |
|---|---|
| 阶段 | Phase 34（把 >400 行大视图拆成子组件，纯内部重构，前端） |
| 执行/复核 | Claude 亲自执行（10 个 worker 子代理并行拆分 + Claude 集中验证/集成/提交；Opus 4.8） |
| 日期 | 2026-07-03 |
| 分支 | `feature/phase34-component-split`（提交 `d90ab5a` wave1 + `4abc6d4` wave2） |
| 范围 | **纯前端视图层**：后端/迁移/api/router/stores/契约零改动；无新依赖 |
| **判定** | **✅ 完成（type-check + build 绿；活体 10 页零 403）** |

---

## 一、成果（原 13 个 >400 文件 → 10 个已拆分）
| 文件 | 前 | 后 | 抽出子组件 |
|---|---|---|---|
| OrganizationManageView | 1006 | **65** | 4 抽屉 + MajorsPanel(471)/ConfigsPanel(238) |
| SecurityManageView | 798 | **398** | User/Role/RolePermission/UserScope 抽屉 |
| MaterialManageView | 697 | **442** | SelfPanel/UploadDrawer/PreviewModal |
| CertificateManageView | 690 | **314** | SelfPanel + Generate/Correct/Issue/Void |
| ExemptionManageView | 620 | **396** | Drawer/Preview/Replace/Exam |
| DictManageView | 610 | **362** | DictType/DictItem 抽屉 |
| video/ManagePanel | 601 | **308** | UploadVideoDrawer/Assign/Arbitrate/Return |
| TrainingManageView | 596 | **347** | TrainingDrawer(含级联)/TrainingDetail |
| SystemAuditView | 523 | **399** | Param/Backup 抽屉 |
| StudentManageView | 456 | **340** | StudentDrawer(含 RegionCascader) |

**新增子组件 33 个**（Phase 34）；views 下 components 合计 37 个。

## 二、仍 >400 的 5 个（判定为合理内聚，不强拆）
| 文件 | 行 | 理由 |
|---|---|---|
| video/MyTaskPanel | 487 | 评分工作台（任务列表+评分区）单一内聚，Phase 33 新建 |
| organization/MajorsPanel | 471 | 学院↔专业主从一体，拆开会割裂选中联动 |
| DashboardView | 445 | 工作台（问候/指标/图表/通知）单一内聚 |
| MaterialManageView | 442 | 表格列定义(58 行)+异步审核处理必须留在父级 |
| SubjectManageView | 401 | 仅超 1 行 |
> 工程判断：400 是启发式阈值而非硬指标；强拆内聚组件会散落逻辑、增加 prop 传递，反损可维护性。已把真正的巨石(1006/798/697/690…)全部大幅瘦身。

## 三、执行方式与验证
- **并行 10 个 worker 子代理**（每个只改 1 个视图 + 其 `components/` 子目录，文件互不相交→无冲突），严格「逐行搬移、行为不变、不跑 build」规约；随后 Claude 集中 `vue-tsc`+`vite build` 一次通过（0 错，仅既有 chunk-size warning），再对 Org 做二次域拆分。
- **行为保全要点**（子代理均逐项说明并被 Claude 核对）：抽屉→子组件经 `defineExpose({open})`+`emit('saved')` 装配，父级 `ref` 调用与 `@saved` 重载；共享 `saving` 拆为每子组件独立（同一时刻仅一个抽屉）不可观测；年度 watch 忠实拆分；Org 二次拆分给 `n-tab-pane` 加 `display-directive="show"` 以保持面板常挂载（与原单体一致，避免切 tab 丢主从选中/延迟加载）；权限门控/`v-model`/校验/文案/样式全部原样。
- **活体 smoke**：SYS_ADMIN 登录，学生/培养/材料/证书/组织(学院·专业)/字典/账号/审计/视频评审 10 个接口全 200 零 403。
- type-check + build 两轮（wave1、wave2 后）均绿。

## 四、放行
1. 合并 `main`（本地私有、无远程、不 push）。
2. 栈在跑（Phase 34 代码），请用户对**被拆分页面的抽屉/表单交互**做目验（新增/编辑/审核/上传弹窗是否照常打开与保存）——这是结构重构唯一需要人眼确认的部分。
3. 前端整改 Phase 30–34 全部完成。

> 备注：为活体走查曾把若干测试账号密码归一化为 `ChangeMe123!`。
