# Phase 25 复核报告 — 验收修复·403 与图表轴

| 项 | 值 |
|---|---|
| 阶段 | Phase 25（验收修复：组合页 403 + 图表轴，前端） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-18 |
| 被复核提交 | `4b42ed5`（fix T-163，单提交） |
| 增量基线 | `main..feature/phase25-acceptance-fix`（前端 17 文件；**后端/迁移/权限零改动**） |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | Blocker 0 · **Major 1（入 backlog→Phase 27）** · Minor 0 |

---

## 一、结论
Phase 25 一轮通过。组合页对部分角色的 403 已消除（按分区真实权限点条件加载 + 显隐 + 空态），图表 x/y 轴显示问题已在 `ChartBox` 统一修复。纯前端、后端/权限/迁移零改动，`type-check`+`build` 绿。复核中确认一个**既存缺口（非本轮回归）**升级为 Major backlog：学院负责人「按人指派」评审教师选择器为空（候选源自系统管理员端点），需后端端点（Phase 27）。

## 二、关键核对
| 项 | 结论 | 证据 |
|---|---|---|
| 组合页 403 修复 | ✅ | `SystemAuditView`：`loadParams/Audits/Backups` 各 `if(!canXxx)return`；`onMounted` 仅 push 有权分区；StatCard/tab/刷新按钮 `v-if`；无分区 `n-empty` |
| 扫面到位 | ✅ | 同模式应用于 Security/Organization/Video/Exchange/Certificate/Stats/Dashboard；「辅助下拉」(如学院/学生/用户列表) 亦按真实权限守卫 |
| 图表轴修复 | ✅ | `ChartBox` 统一：xAxis `interval:0`+`overflow:'truncate'`+`hideOverlap`+`alignWithLabel`；yAxis `minInterval:1`+起点 0；setOption(true) resize |
| 不放宽后端 | ✅ | 修复在前端加载层，§15.1 权限矩阵 / Phase 24 RBAC 边界不变；后端/迁移未改 |
| 构建 | ✅ | `vue-tsc` 无错；`vite build ✓ built in 6.36s` |

## 三、Major（入 backlog → Phase 27）
- **负责人按人指派评审教师选择器为空**：`VideoReviewView` 候选 `listUsers({status:ENABLED})` 需 `system:user:manage`（仅 SYS_ADMIN）。本轮为消 403 改为「有该权限才拉」→ `reviewers=[]` 对 `COLLEGE_AUDITOR`（有 `video:assign`、无 `system:user:manage`）。
  - **性质**：长期既存缺口——此前该调用对非系统管理员即 403（被 403 掩盖），Phase7/12 IT 以直传 reviewerIds 绕过 UI；**非 Phase 25 回归**。
  - **现状可用**：负责人「按组指派」正常（评审组按 `video:assign` 加载）。
  - **修复（Phase 27，后端）**：新增 `video:assign` 门控的「列本院 `REVIEW_TEACHER`」端点（数据范围限本院），前端按人选择器改用之；补 IT。

## 四、维度结论
D1 需求 ✅（403 消除 + 轴修复）· D4 红线 ✅（不放宽后端权限、数据范围不变）· D6 安全 ✅（前端不再打无权 API）· D7 构建 ✅· D8 ⚠️→backlog（按人指派候选缺端点，Phase 27 补）· D11 文档 ✅（待复核未自 ✅、自检矩阵入 DEVLOG）。

## 五、放行
1. PROGRESS Phase 25 置 **✅ 已复核**；Major 记 backlog→Phase 27。
2. 合并 `main`（本地私有、无远程、不 push）。
3. 下一步 Phase 26（UI 提升）+ Phase 27（评审教师列表端点）。
