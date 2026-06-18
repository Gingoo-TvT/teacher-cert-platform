# WP-D 复核报告 — 评审指定与分组（Phase 18）

| 项 | 值 |
|---|---|
| 阶段 | WP-D / Phase 18（评审指定与分组，后端为主 + 前端最小） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-18 |
| 被复核提交 | `4999233`（feat，单提交） |
| 增量基线 | `main..feature/wp-d-reviewer-group`（V22 + 评审组模型/服务/控制器 + assign 扩展 + DataScope + Phase7 IT + 前端最小） |
| 迁移 | 新增 `V22__reviewer_group.sql`；V1–V21 未改 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | Blocker 0 · Major 0 · Minor 0 |

---

## 一、结论
WP-D 一轮通过。新增「评审组」模型（`reviewer_group` + `reviewer_group_member`），学院负责人可建组并按组或按人指派视频评审；组与成员、指派全部限本学院。复用 `video:assign` 权限，未新增权限点。clean-room `mvn -B -ntp verify` **79/79 全绿**（Phase7 9→10），前端 `vue-tsc` + `vite build` 绿。

## 二、关键核对
| 项 | 结论 | 证据 |
|---|---|---|
| V22 模型 | ✅ | `reviewer_group`/`reviewer_group_member` 幂等 `CREATE IF NOT EXISTS`；唯一键防同院重名(`college_id,name,deleted`)与同组重复成员(`group_id,reviewer_user_id,deleted`)；索引齐；无 DML、V1–V21 未改 |
| 组 CRUD 数据范围 | ✅ | 全程 `ensureCanManageCollege(group.collegeId)`；`create` 的 collegeId 取负责人 `video:assign` scope（**非请求**）；`list` 按 scope（allSchool/COLLEGE）显式过滤 |
| 成员校验 | ✅ | `addMember` → `requireReviewTeacherInCollege`：存在+ENABLED+**同院**(403)+具 REVIEW_TEACHER 角色 |
| 指派扩展 | ✅ | `reviewerIds` XOR `groupId`（同时/都无→报错）；组解析校验存在+**同院**(FORBIDDEN)+ENABLED；解析后 size 须=`video.reviewerCount` |
| 评审硬校验 | ✅ | `requireReviewerForReview` 对每位评审校验 ENABLED+**同院**(FORBIDDEN)+REVIEW_TEACHER —— 连带硬化原按人路径 |
| 鉴权/留痕 | ✅ | Controller 7 端点全 `@pms.has('video:assign')`；create/update/delete/add/removeMember 带 `@AuditLog(reviewerGroup,*)` |
| 数据范围注册 | ✅ | `DataScopeSqlHandler` 注册 `reviewer_group`(college_id)；成员经组访问、服务层显式范围，规则无副作用 |
| 唯一冲突处理 | ✅ | delete/removeMember 用 `name#id` / `reviewer_user_id=id` 改写，避免重建/重加唯一键冲突 |
| 向后兼容 | ✅ | 既有 `reviewerIds` 指派不变；Phase7 既有/ WP-C 反例不回归 |
| 前端最小 | ✅ | `video.ts` 增评审组 API；`vue-tsc`/`build` 绿（完整组管理与分组指派 UI 在 Phase 21） |

## 三、IT 覆盖（`reviewerGroupAssignAndDirectAssignBothSettleWithScopeChecks`）
① 建组+2 本院成员 → 按组指派 → 任务数 2 → 90/86 结算 88；② 按人 `reviewerIds` 指派 → 82/80 结算 81（向后兼容）；③ 人数不足组按组指派 → 「人数需等于系统参数」；④ 跨院成员加入 → 403「不属于本学院」；⑤ 跨院组指派他院视频 → 403/404；⑥ 按人含跨院评审 → 403「不属于该视频学院」。回归 Phase7 双盲/分差/需复评/第三专家结算不破。

## 四、维度结论
D1 需求 ✅（按人/按组+分组达成）· D4 红线 ✅（数据范围写侧硬校验、collegeId 非请求）· D5/6 质量安全 ✅（唯一冲突处理、按人路径连带硬化）· D7 构建 ✅（79/79+前端绿）· D8 测试 ✅（六类场景+向后兼容）· D9 数据库 ✅（V22 幂等、V1–V21 冻结）· D10 回归 ✅· D11 文档进度 ✅（待复核未自 ✅）。

## 五、放行
1. PROGRESS WP-D 置 **✅ 已复核**。
2. 合并 `main`（本地私有、无远程、不 push）。
3. 放行 Phase 19（前端重建·基座与设计系统）——进入前端「以前瞻版为底重建」主体。
