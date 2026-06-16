# Phase 3 复核报告 — 基本信息（T-030~T-038）

| 项 | 值 |
|---|---|
| 阶段 | Phase 3 学生基本信息 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-16 |
| 被复核提交 | `c99c8e3`（feat T-030~T-038，单提交） |
| 增量基线 | `73f0f4c..HEAD`（约 28 文件；platform-business 14 / boot 5 / frontend 5 / system 2） |
| 迁移 | 新增 `V9__student.sql`；V1–V8 未改动 ✓ |
| **判定（轮次1 · 06-16）** | ❌ 退回（CHANGES REQUESTED） |
| 计数（轮次1） | Blocker × 0 · Major × 3 · Minor × 5 |
| **最终判定（轮次2 · 06-16）** | **✅ PASS — 退回项已修复，复核增量+回归通过** |

---

## 〇、复核轮次 2（2026-06-16）：退回项已修复 → ✅ PASS
- **M1/M2 写侧数据范围硬校验**：`StudentServiceImpl` 新增 `DataScopeService` 依赖与 `allowedCollegeId()`，`fill(...,enforceWriteScope=true)` 用于 create/batchCreate/update：解析 `student:edit` 范围，全校放行、COLLEGE 仅放行授权学院、否则 403「无权操作该学院学生」。`Phase3StudentIT` 新增 `collegeClerkCannotWriteStudentOutsideAuthorizedCollege`：clerk 跨院 create → 403 且不建学生/账号；clerk 把本院学生 update 到他院 → 403 且记录仍在原院、不建账号。
- **M3 前端类型**：`StudentManageView.vue` `row-key` 补类型；`npm run type-check`(vue-tsc) 通过。
- **独立验证**：`mvn -B -ntp verify` GREEN — `Phase2SecurityIT 2/2`（回归）+ `Phase3StudentIT 5/5`（含新跨院反例）；`type-check` EXIT 0；`npm run build` 绿。迁移(V9)/治理文档(AGENTS/REVIEW-GATE)未动；单提交 `f3fface`，工作树干净，remote 空。
- 结论：M1/M2/M3 已闭环，读写两侧数据范围 + AT-03/AT-01/状态机/锁定全过 → **PASS**，合并 main 放行 Phase 4。
- **遗留入 backlog（须跟踪，不阻断）**：`StudentConfirmRequest extends StudentSaveRequest` 携带 collegeId，`confirm` 走 `enforceWriteScope=false` 且 collegeId 非 `criticalChanged` 字段 → 学生本人 `confirm` 可改自己的 collegeId（自助换学院，锁定后亦不被拦）。限本人记录、无跨租户读，影响有限。**建议快速补丁/Phase 4 起关闭**：confirm 不写 collegeId（保留实体原值），或把 collegeId 纳入学生不可改集合。

---

## 一、结论（轮次1 退回时的记录，保留备查）

Phase 3 主体质量很高：**AT-03（四类证件校验，身份证出生日期一致性强制、港澳/台胞证正确不校验日期）、AT-01（学号/证件号/出生日期 全链路 String，`birth_date` 为 VARCHAR 非 DATE）头部验收全过**；**AT-13 数据范围的读侧已正确续接**到新 `student` 表（注册进 Phase 2 的 `DataScopeSqlHandler` 表规则，学院只见本院、学生只见本人，并有真实表反例）；两级审核状态机 A、字段锁定、脱敏、`@AuditLog` 留痕、failsafe 自动跑反例均到位。独立 `mvn verify` GREEN（Phase2 2/2 + Phase3 4/4）。

但存在 **3 个 Major** 须修复后放行：写侧数据范围未硬校验（学院账号可跨学院建/迁学生并开账号）+ 前端 `type-check` 不过（DoD/§3.2 禁 any）。按 `REVIEW-GATE §5`（数据范围漏挂 / 后端硬校验 / DoD）判 **退回**。

> 退回聚焦 3 个 Major；AT-03/AT-01/读侧 AT-13/状态机/锁定/迁移 无需返工。修复后只复核增量 + 回归。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff 73f0f4c..HEAD`（单提交 c99c8e3）。
2. **干净构建 + 独立重跑反例**：`mvn -B -ntp verify` → **BUILD SUCCESS**，Failsafe 自动执行 `Phase2SecurityIT`(2/2，回归) + `Phase3StudentIT`(4/4)；前端 `npm run build` 绿。
3. **读码裁决**：`StudentController` / `StudentServiceImpl` / `IdCardValidator` / `BirthDateValidator` / `NameValidator` / `DataScopeSqlHandler`(student 规则) / `V9__student.sql` / `Phase3StudentIT`。
4. **三路独立代理**（AT-01·AT-03 / 数据范围·状态机·锁定 / 质量·DoD·迁移·模块接入）。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 证件四类 / 校验规则 / 状态机 与 plan + phase-03 一致 |
| D2 验收清单 | ⚠️ | AT-03/AT-01 复跑通过；写侧数据范围未达"后端硬校验" |
| D3 AT 验收 | ✅ | AT-03 通过；AT-01(部分) 通过；AT-13 读侧续接通过（写侧见 M1/M2） |
| D4 红线合规 | ❌ | "数据范围 + 后端硬校验"写侧漏挂（M1/M2）；其余红线（文本化 String、留痕、不硬编码、逻辑删除）合规 |
| D5 代码质量 | ⚠️ | 分层/事务/Result/BaseEntity 规范；`type-check` 隐式 any（M3） |
| D6 安全 | ✅ | 证件脱敏、明文接口受 `export:sensitive` + `@AuditLog` 管控 |
| D7 构建与运行 | ✅ | `mvn verify` 绿、前端 build 绿；未自起常驻服务 |
| D8 测试 | ✅ | 反例接入 failsafe，`mvn verify` 自动跑 Phase3StudentIT 4/4（含真实表数据范围反例） |
| D9 数据库 | ✅ | 仅 V9 新增、V1–V8 未改；幂等、索引/注释齐、文本字段 VARCHAR |
| D10 回归 | ✅ | Phase2SecurityIT 2/2 回归通过；既有未破坏 |
| D11 文档/进度 | ✅ | 治理文档(AGENTS/REVIEW-GATE)未改；PROGRESS 置「待复核」未自 ✅ |

---

## 四、问题清单

| 编号 | 级别 | 维度 | 问题 / 证据 | 期望 |
|---|---|---|---|---|
| **M1** | Major | 数据范围/后端硬校验 | **create / batchCreate 写侧无数据范围校验**：`StudentController.create`(`:56-62`)、`batchCreate`(`:64-70`) 只有 `@PreAuthorize('student:edit')`，**无 `@DataScope`**；`StudentServiceImpl.fill` 直接 `entity.setCollegeId(request.getCollegeId())`（约 :230）。COLLEGE 范围的学院教务员可 POST 任意 `collegeId` → 在他院建学生，并经 `ensureStudentAccount` 在他院开通账号。读侧 AT-13 隔离虽成立，但这是其"写侧反向漏洞"。 | create/update 对 COLLEGE 范围调用者断言 `request.collegeId ∈ 当前用户授权学院`（读 `DataScopeContext`/`resolve("student:edit")`），否则拒绝；或对院级账号强制 `collegeId`=本院。 |
| **M2** | Major | 数据范围 | **update 可把本院学生迁到他院**：`update`(`:72-80`) 有 `@DataScope` 故目标须在范围内才载入，但 `fill` 仍用 request 的 `collegeId` 覆盖（`StudentServiceImpl` :104-111/230），院级账号可将在范围学生改到他院（此后该记录脱离其范围）。 | 同 M1：非 SCHOOL 调用者不得改 `collegeId` 越出授权范围。 |
| **M3** | Major | 代码质量/DoD | **前端 `npm run type-check` 失败**：`StudentManageView.vue:236` `:row-key="(row) => row.id"` 形参 `row` 隐式 `any`（vue-tsc TS7006），违 AGENTS §3.2「禁止 any/类型齐全」与 §12 DoD「lint/CI 全绿」。CI 因只跑 `vite build`(不类型检查) 而仍绿，但仓库自带类型门红。 | `(row: Student) => row.id`，并确保 `npm run type-check` 绿。 |
| m1 | Minor | 工作流 | `confirm`(`StudentServiceImpl` :123-131) 无状态守卫：学生在 待初审/待复审/通过 态仍可改非关键字段而不重置审核态。关键字段已被 `criticalChanged` 冻结，仅工作流卫生问题。 | confirm 限定 status ∈ {DRAFT, 退回态}。 |
| m2 | Minor | 契约 | `plainIdCard` 的 `plain` 参数失效（恒返回明文，`:135-139`）。已受 `export:sensitive` + `@AuditLog` 管控、无泄漏（反例 403 通过），但 `?plain=1` 契约未守。 | 仅 `plain==1` 返明文，否则脱敏；或从契约删除该参数。 |
| m3 | Minor | AT-01 | `IdCardValidator.validate` 对港澳/台通行证 `normalize` 时整体 `toLowerCase`，持久化/回显把 `H12345678` 存成 `h12345678`（仅大小写折叠，无截断/数值化）。 | 仅用于匹配/校验时小写；持久化保留录入原值（字母大写或原样）。 |
| m4 | Minor | 代码质量 | `list`(`:42-46`) 返回 PageResult 但未真分页（全量 `selectList`，total=size），用户量大不可扩展。 | 接 `page/size` + MyBatis-Plus `Page`。 |
| m5 | Minor | 健壮性 | `BirthDateValidator` 日校验 1–31 不按月（02/30 通过格式）；但随后与证件号 7–14 位比对，真实身份证不会匹配错误日期，影响低。 | 可选：按月历严格校验。 |

---

## 五、做得好（无需返工）

- **AT-03**：`IdCardValidator` 四类证件（居民身份证 `^\d{17}[\dXx]$` + 可选 MOD11-2 / 港澳台居住证 / 港澳通行证 `^[A-Za-z]\d{8}$` / 台胞证 `^\d{8}$`），与 V3 字典 `id_type` 编码逐字一致；`BirthDateValidator` 对身份证/居住证比对证件号 7–14 位与填写出生日期，不一致拦截；港澳/台胞通行证**正确不做日期校验**。服务端强制（create/update/confirm 调用）。
- **AT-01**：`student_no/id_card_no/birth_date` 在 SQL 为 VARCHAR、Java 为 String；出生日期为文本（非 LocalDate）；前导零/长证件号不丢、不科学计数。
- **AT-13 读侧续接**：`DataScopeSqlHandler.TABLE_RULES` 新增 `student`（college_id 院范围 / id 本人范围），列表/详情/读路径挂 `@DataScope`；`Phase3StudentIT` 用真实 `student` 表验证学院A 只见本院、学生 9001 只见本人（9002→404）。
- **状态机 A**：草稿→待初审→待复审→通过/退回 服务端强制；初审=学院教务员、复审=学院负责人；非法流转抛错；退回去向走 `review.return.target` 参数。
- 字段锁定（通过后 locked=1 关键字段禁改、删锁记录被拒）；证件脱敏前6后4；`@AuditLog` 覆盖全部写操作；platform-business 正确接入 boot；姓名校验走 `validate.name.mode`(loose) 不硬编码；前端经 api 层 + string ID。

---

## 六、退回处理

1. 阶段在 `PROGRESS.md` 置 **复核退回**；**不合并 main、不置 ✅**。
2. codex 仅修 **M1 / M2 / M3**（Minor 进 backlog，可一并捎带）；并补一条反例：**院级账号 create/update 越学院写入 → 被拒**（纳入 `Phase3StudentIT`，确保 `mvn verify` 仍绿）；在 `DEVLOG.md` 记修复。
3. 重交后复核方**只复核增量 + 回归**，重点复跑：写侧 collegeId 范围校验、`type-check` 绿、数据范围读写两侧隔离。直至 PASS。
