# Phase 6 复核报告 — 免考（T-052~T-056）

| 项 | 值 |
|---|---|
| 阶段 | Phase 6 免考 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `df13e97`（feat T-052~T-056，单提交） |
| 增量基线 | `fc28ca0..HEAD`（约 25 文件；business 14 / boot 4 / frontend 4） |
| 迁移 | 新增 `V12__exemption.sql`；V1–V11 未改动 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | **Blocker × 0 · Major × 0 · Minor × 8（入 backlog）** |

---

## 一、结论

Phase 6 一轮通过，且为迄今最干净的一阶段。**AT-07 五条逐条服务端硬判且反例充分**：①一名学生多科免考各为独立行、每科独立佐证；②某科缺佐证 → 该科不能提交（`materialCount` 按 `exemption_request_id` 计数、非全局）；③每科独立两级审核（PASS/REJECT/FAIL），三科并存不同结论（通过/退回/不通过）**互不影响**（全程单行 `selectById`+`updateById`、无批量改、无跨行 WHERE）；④**仅复审 PASS** 才把该科移出应考（`includedInExam=0` 唯一写在 `secondReview` PASS 分支；`examSubjects` 以 `finalStatus==PASSED` 直推，草稿/初审通过都不会误移）；⑤免考通过**不触碰** `process_material`/`ability_test_result`（exemption 包内对过程性表零引用，独立性是结构性的）。exemption_request/material **读+写两侧数据范围**到位（两表注册进 `DataScopeSqlHandler`；写侧 collegeId 取自 student 实体、跨学生/跨学院 403 且不建行；读侧 `@DataScope` + 服务层 `ensureCanReadStudent` 双保险）；**可编辑态守卫复用**（仅 DRAFT/*_REJECTED 可写、locked/在审/PASSED 禁写）；`exemption:apply/firstReview/secondReview` 权限点**已在 V8 §15.1 矩阵预种**并按角色授权（STUDENT→apply SELF、COLLEGE_CLERK→firstReview COLLEGE、COLLEGE_AUDITOR→secondReview COLLEGE），无 Phase 5 `material:view` 式缺种子。佐证走 platform-file 的 MinIO、类型/大小走 sys_param、可免科目/依据走字典（含确认单#12 待导入 TODO，未硬编码）。独立 `mvn -B -ntp verify` GREEN（22/22），前端 `type-check`/`build` 绿。8 个 Minor（多为复发/潜在项与测试覆盖面）入 backlog，不阻断。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff fc28ca0..df13e97`（单提交 df13e97；V1–V11 与 AGENTS/REVIEW-GATE/HANDOFF/tasks 未改）。
2. **干净构建 + 独立重跑反例**：`mvn -B -ntp verify`（先 docker 起 MinIO/MySQL/Redis）→ **BUILD SUCCESS**，Failsafe 自动执行 `Phase2SecurityIT 2/2` + `Phase3StudentIT 7/7` + `Phase4TrainingIT 5/5` + `Phase5MaterialIT 4/4` + `Phase6ExemptionIT 4/4` ＝ **22/22**；前端 `npm run type-check`(vue-tsc) 干净、`npm run build` ✓。
3. **读码裁决**：`ExemptionServiceImpl`（多科 apply/per-科 submit 佐证校验/两级审核三态/examSubjects 口径/写侧硬校验/ensureEditable）、`ExemptionController`（@DataScope+@AuditLog+@PreAuthorize）、`ExemptionStatus.editable()`、`DataScopeSqlHandler`(exemption_request/material 规则)、`V12__exemption.sql`、`V8__rbac_seed.sql`(权限种子)、`Phase6ExemptionIT`。
4. **三路独立代理**（AT-07 业务/状态机 · 数据范围/安全/可编辑态/权限装配 · 质量/DoD/迁移/前端/治理）——均 PASS。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 多科免考/每科佐证/每科两级审核/应考联动/不覆盖过程性 与 plan + phase-06 一致 |
| D2 验收清单 | ✅ | AT-07 六项验收复跑通过 |
| D3 AT 验收 | ✅ | AT-07 首验通过；exemption 读+写数据范围续接 AT-13 |
| D4 红线合规 | ✅ | 数据范围读+写硬校验、文本化 String、留痕、不硬编码（科目/依据走字典、限制走 sys_param） |
| D5 代码质量 | ✅ | 分层/事务/Result/BaseEntity；Minor 多为复发/潜在项 |
| D6 安全 | ✅ | 写侧 collegeId 取自实体、跨学生/跨院 403；佐证预览走限时预签名 URL；内容类型规范化抗伪造 |
| D7 构建与运行 | ✅ | `mvn verify` 绿、type-check/build 绿；未自起常驻服务 |
| D8 测试 | ✅ | Phase6ExemptionIT 4/4 接 failsafe，三科 PASS/REJECT/FAIL 并存 + 漏佐证 + 不覆盖过程性 + 读写范围反例 |
| D9 数据库 | ✅ | 仅 V12 新增、V1–V11 未改；幂等、索引/注释齐、文本字段 VARCHAR、软删除 |
| D10 回归 | ✅ | Phase2/3/4/5 共 18 条回归通过 |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅；DEVLOG 有 Phase 6 小结 |

---

## 四、做得好

- **AT-07 多科互不影响（生命线）**：`Phase6ExemptionIT.multiSubjectReviewResultsAreIndependentAndExamSubjectsExcludeOnlyPassed` 对同一学生三科分别驱动 PASS/REJECT/FAIL，断言三态独立（PASSED/FIRST_REJECTED/FAILED）且 exam-subjects 仅移除复审通过的 A 科——强反例，非平凡断言。
- **应考口径单一真相**：`examSubjects` 以 `finalStatus==PASSED` 直推 `includedInExam=!passed`，不依赖可被中间态污染的字段；`includedInExam=0` 全局仅 `secondReview` PASS 一处赋值。
- **不覆盖过程性（结构性独立）**：`exemptionDoesNotOverwriteProcessMaterialConclusion` 预置 FAILED+locked 的 process_material，免考审批通过后该记录仍 FAILED+locked；exemption 包对过程性表零写入。
- **漏佐证按科判**：`submit` 的 `materialCount(entity.getId())` 按 `exemption_request_id` 计数，某科漏传只拦该科，不会借他科佐证过关。
- **读+写数据范围**：两表注册进 `DataScopeSqlHandler`（college_id 院 / student_id 本人）；写侧 `ensureCanWriteStudent` collegeId 取自 student 实体、SELF 跨学生 403、COLLEGE 跨院 403 且不建行；读侧 `@DataScope` + 服务层 readable 双保险。
- **可编辑态守卫复用**：`ExemptionStatus.editable()`={DRAFT,FIRST_REJECTED,SECOND_REJECTED} + `ensureEditable`(含 locked) 覆盖 update/upload/replace/delete/re-apply/submit。
- 权限点 V8 预种 + 角色授权到位（无缺种子）；@AuditLog 覆盖全部 8 个写端点；前端经 api 层、ID 用 string、`row-key` 显式类型（未重蹈 Phase 3 隐式 any）。

---

## 五、Minor（入 backlog，不阻断）

| 编号 | 问题 | 建议 |
|---|---|---|
| 1 | `ExemptionStatus.of()` 未知值静默映射 DRAFT（fail-open，脏数据会变可编辑/可提交） | 未知非空值抛错或映射为不可编辑哨兵（与 Phase 4 b3 同类） |
| 2 | `submit()` 直接用 `status.editable()` 未走 `ensureEditable`，跳过 locked 检查（当前不可利用，locked 态均已非可编辑） | 改调 `ensureEditable(entity)` 统一 |
| 3 | `list()` 全量 `selectList`、total=size 非真分页（数据量大不可扩展） | 接 `page/size`（复发，Phase 3 m4 同类） |
| 4 | id 定向端点 NOT_FOUND 先于范围校验 → 跨范围探测可由 404/403 推断存在性 | 内部工具可接受；如需统一可先解析+范围校验再归一 404（复发，Phase 5 同类） |
| 5 | `ExemptionQuery.collegeId` 调用方可传（仅收窄、拦截器才是真边界） | 加注释说明边界靠 `@DataScope` 拦截器 |
| 6 | `readPermission()` 取最高权限解析读范围，与 `@DataScope(permission="student:view")` 口径不同源 | 现各角色两口径一致；如未来出现更宽 review 范围需收敛，留维护提示 |
| 7 | 测试覆盖面：退回后重提（`returnTargetFromSecondRejected` SECOND_REJECTED→config 分支）、初审 FAIL 分支、"B 漏证 A 有证" 强反例 未直接断言（读码均正确） | 补充回归断言 |
| 8 | 前端 segment 过滤 `@update:value` 直传，类型兼容但接线略松（cosmetic） | 可显式整理 |

---

## 六、放行

1. `PROGRESS.md` Phase 6 置 **✅ 已复核**；AT-07 首验通过；exemption 读+写数据范围续接 AT-13。
2. 合并 `main`（本地私有、无远程、不 push）；启动 Phase 7（视频评审，AT-08）。
3. 8 个 Minor 进 backlog。
