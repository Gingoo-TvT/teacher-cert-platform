# Phase 20 复核报告 — 前端重建·基础数据/学生/培养（WP-F-2）

| 项 | 值 |
|---|---|
| 阶段 | Phase 20 / WP-F-2（前端重建第 2 包：基础数据/学生/培养，前端） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-18 |
| 被复核提交 | `6341cb0`（feat phase20，单提交） |
| 增量基线 | `main..feature/phase20-base-student-training`（前端 7 页面 + 进度日志；**后端/迁移/IT 零改动**） |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | Blocker 0 · Major 0 · Minor 0 |

---

## 一、结论
Phase 20 一轮通过。重建「基础数据 4 页 + 学生 manage/self + 专业培养」共 7 页：字段枚举/联动按附录 A 落到字典下拉与 `/training/options` server-driven 联动；**「教务员不能新增专业」**通过 `v-if="canManageMajor"` 在视图层硬隐（与 WP-A V20 矩阵自然一致）；StudentSelf 锁定态守卫全字段 disabled；动作按 perm 显隐。`src/api/*`/`stores/user` 未改，后端/迁移零改动。`type-check` 无错 + `vite build ✓ built`。

## 二、关键核对
| 项 | 结论 | 证据 |
|---|---|---|
| 字段下拉 | ✅ | StudentManage/Self: gender/idCardType/identityType 经 `listDictItems('gender'/'idCardType'/'identityType', true)` 字典下拉 + 必填校验；Training: trainingGoal/internshipLocation/teachingSegment/teachingSubject 等同理 |
| 联动 server-driven | ✅ | `watch(form.trainingGoal)` → `reloadTrainingOptions(goal, segment)` 调 `/training/options`；返回 `allowedSegments`/`allowedInternshipLocations`，当前值若被剔除则自动 reset；前端不硬编「中职→其他/高中→中小学」 |
| 教务员不能新增专业 | ✅ | `canManageMajor = hasPerm('major:manage')` → 「新增专业」按钮 `v-if="canManageMajor"`；WP-A V20 矩阵 COLLEGE_CLERK 无该权限 → 按钮整体不渲染；新增学院/培养目标配置同模式 |
| StudentSelf 锁定守卫 | ✅ | `locked = student?.locked===1`；全可改字段 `:disabled="locked"`；submit/save 按钮 `:disabled="!canSubmit"`/`:disabled="locked"`；并附 `n-alert` 提示 |
| RBAC 动作显隐 | ✅ | `canEdit/canFirstReview/canSecondReview` 按 student:edit/info:firstReview/info:secondReview 分别 hasPerm |
| 真实集成保留 | ✅ | `src/api/*`、`stores/user` diff 名单空；继续走真实 student/training/dict/region/subject/organization API |
| 后端/迁移冻结 | ✅ | `git diff` 无 backend/migration/IT 改动；V1–V22 未改 |
| 构建 | ✅ | `vue-tsc` 无错；`vite build ✓ built in 5.49s` |

## 三、维度结论
D1 需求 ✅（4 基础数据 + 学生/培养 + 字段规范 + 联动 + 教务员不能新增专业 + RBAC 显隐 + 学年/学院过滤） · D5 质量 ✅（dict 与后端联动单源） · D6 安全 ✅（按 perm 显隐 + 锁定守卫 + 后端硬校验不弱化） · D7 构建 ✅ · D11 文档 ✅（待复核未自 ✅）。

## 四、放行
1. PROGRESS Phase 20 置 **✅ 已复核**。
2. 合并 `main`（本地私有、无远程、不 push）。
3. 放行 Phase 21（前端·材料/免考/视频）。
