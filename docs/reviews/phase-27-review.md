# Phase 27 复核报告 — 负责人评审教师列表端点（闭环 Phase 25 Major）

| 项 | 值 |
|---|---|
| 阶段 | Phase 27（后端 + 前端：video:assign 评审教师候选端点，修负责人按人指派） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-18 |
| 被复核提交 | `716b7c9`（feat T-172，单提交） |
| 增量基线 | `main..feature/phase27-reviewer-list`（控制器/Service/Impl/VO + 前端 + Phase7 IT；**无新迁移**） |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | Blocker 0 · Major 0 · Minor 0（闭环 Phase 25 的 1 Major） |

---

## 一、结论
Phase 27 一轮通过，闭环 Phase 25 遗留 Major（学院负责人按人指派评审教师选择器为空）。新增 `video:assign` 门控的 `GET /api/video/reviewer-candidates`，按数据范围返回本院/全校 ENABLED 且具 `REVIEW_TEACHER` 角色的候选；前端按人选择器改用之，移除对 `system:user:manage` 端点的依赖。无新权限点/迁移（复用 `video:assign`），V1–V23 冻结。clean-room `mvn verify` **83/83**（Phase7 11）+ 前端绿。

## 二、关键核对
| 项 | 结论 | 证据 |
|---|---|---|
| 端点鉴权 | ✅ | `GET /video/reviewer-candidates` `@PreAuthorize("@pms.has('video:assign')")` |
| 数据范围 | ✅ | `reviewerCandidateCollegeIds`：NONE→403；allSchool→全校(collegeId null)；COLLEGE→scope.collegeIds；否则 403 |
| 候选过滤 | ✅ | `selectEnabledByRoleAndCollege(REVIEW_TEACHER, collegeId)`（既存 SysUserMapper:42）→ ENABLED+REVIEW_TEACHER；按 id dedup(LinkedHashMap) |
| VO 最小化 | ✅ | `ReviewerCandidateVO` 仅 id/realName/workNo，无敏感字段 |
| 前端切换 | ✅ | `canAssign ? listReviewerCandidates() : null`；移除 `listUsers`/`canManageSystemUsers`；负责人(video:assign)现可加载候选 |
| 无迁移/冻结 | ✅ | 无新权限点、无迁移；V1–V23 未改；Flyway v23 |
| IT 覆盖(T-174) | ✅ | 负责人得本院 reviewerA/B、排除跨院 REVIEWER_D 与负责人自身；REVIEW_TEACHER 无 video:assign→403；按人指派 2 人→84/80→结算 82 PASS |
| 构建 | ✅ | clean-room `mvn verify` 83/83；前端 `vue-tsc`+build 绿 |

## 三、维度结论
D1 需求 ✅（闭环按人指派）· D4 红线 ✅（数据范围限本院、写侧不取请求）· D6 安全 ✅（video:assign 门控、候选不含敏感、不再借用 system:user:manage）· D7 构建 ✅· D8 测试 ✅（范围/跨院/无权/按人结算）· D9 ✅（无迁移）· D10 回归 ✅· D11 ✅（待复核未自 ✅）。

## 四、放行
1. PROGRESS Phase 27 置 **✅ 已复核**；Phase 25 Major 闭环。
2. 合并 `main`（本地私有、无远程、不 push）。
3. 下一步 Phase 26（UI 设计提升·按 `frontend/DESIGN.md`）。
