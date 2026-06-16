# Phase 4 复核报告 — 专业培养信息（T-039~T-043）

| 项 | 值 |
|---|---|
| 阶段 | Phase 4 专业培养信息 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-16 |
| 被复核提交 | `863cbea`（feat T-039~T-043，单提交） |
| 增量基线 | `41fe154..HEAD`（约 20 文件；business 12 / boot 5 / frontend 4） |
| 迁移 | 新增 `V10__training_profile.sql`；V1–V9 未改动 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | **Blocker × 0 · Major × 0 · Minor × 3（入 backlog）** |

---

## 一、结论

Phase 4 一轮通过。**AT-04（教育类研究生专业代码 0401/0451/0453 强制、非法报错）、AT-05（学段→学科联动、学科取自标准库禁自由填、中职类别节点不可选）、培养目标→实习地点/学段/学科 联动（企业/海外实习地点受限）均服务端硬校验且反例齐全**；training_profile **读+写两侧数据范围**都到位（写侧 collegeId 取自 student 实体 + `allowedCollegeId` 硬校验、跨院 create/update 双向 403——比要求更稳，未重蹈 Phase 3 写侧覆辙）；两级审核状态机 A 强制；**Phase 3 遗留 backlog（confirm 改本人 collegeId）已闭环**。独立 `mvn verify` GREEN（12/12），`type-check`/`build` 绿。3 个 Minor（工作流卫生）入 backlog，不阻断。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff 41fe154..HEAD`（单提交 863cbea）。
2. **干净构建 + 独立重跑反例**：`mvn -B -ntp verify` → **BUILD SUCCESS**，Failsafe 自动执行 `Phase2SecurityIT 2/2`（回归）+ `Phase3StudentIT 6/6`（含新 confirm 反例）+ `Phase4TrainingIT 4/4`＝12/12；`npm run type-check` EXIT 0；`npm run build` 绿。
3. **读码裁决**：`MajorCodeValidator` / `TrainingLinkValidator` / `TrainingProfileServiceImpl`(写侧 allowedCollegeId) / `DataScopeSqlHandler`(training_profile 规则) / `StudentServiceImpl.confirm`(backlog 修复) / `V10__training_profile.sql` / `Phase4TrainingIT`。
4. **独立代理**（AT-04·AT-05·联动 / 数据范围·状态机·backlog）；质量·DoD·迁移·治理维度由 verify 日志 + 快照自核（迁移仅 V10、治理文档未动、IT 接 failsafe、type-check 绿）。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 专业代码/学段-学科-实习联动/培养目标 与 plan + phase-04 一致 |
| D2 验收清单 | ✅ | AT-04/AT-05 复跑通过 |
| D3 AT 验收 | ✅ | AT-04 通过；AT-05 通过（P4 联动）；AT-13 训练表读+写续接 |
| D4 红线合规 | ✅ | 数据范围读+写硬校验、文本化 String、留痕、不硬编码（走标准库/字典/sys_param） |
| D5 代码质量 | ✅ | 分层/事务/Result/BaseEntity；3 个工作流 Minor |
| D6 安全 | ✅ | 写侧 collegeId 取自实体 + 范围硬校验，跨院 403 |
| D7 构建与运行 | ✅ | `mvn verify` 绿、type-check/build 绿；未自起常驻服务 |
| D8 测试 | ✅ | Phase4TrainingIT 4/4 接入 failsafe，覆盖 AT-04/AT-05/联动/写侧 scope 反例 |
| D9 数据库 | ✅ | 仅 V10 新增、V1–V9 未改；幂等、索引/注释、文本字段 VARCHAR |
| D10 回归 | ✅ | Phase2 2/2 + Phase3 6/6 回归通过 |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅ |

---

## 四、做得好

- **AT-04**：`MajorCodeValidator` 服务端校验，`education_master`（教育类研究生）专业代码须前缀 ∈ {0401,0451,0453}，非法（如 030101 法学）报错；反例 `Phase4TrainingIT` 验证。
- **AT-05**：`TrainingLinkValidator.requireSubject` 要求学科存在于标准库 `teaching_subject`(学段+编码+启用)，自由填/越库/类别节点（`is_category=1`）一律拒绝；学段须属培养目标 `allowed_segments_json`。
- **培养目标联动**：多培养目标；目标限定可选实习地点（企业仅中职专业课教师、海外仅汉语国际教育），`validateLocationRestriction` 强制，正/反例齐全。
- **数据范围读+写**：`training_profile` 注册进 `DataScopeSqlHandler`（college_id 院 / student_id 本人）；写侧 collegeId 取自 student 实体 + `allowedCollegeId` 范围硬校验，跨院 create/update→403。
- **Phase 3 backlog 闭环**：`StudentServiceImpl.confirm` 不再写 collegeId（仅 `!existing` 时设）；反例 `studentConfirmCannotMoveOwnCollege`（confirm 传他院 collegeId → 不变）。Phase3StudentIT 6/6。
- 状态机A 强制、@AuditLog 覆盖写操作、文本化 String、不硬编码。

---

## 五、Minor（入 backlog，不阻断）

| 编号 | 问题 | 建议 |
|---|---|---|
| b1 | `TrainingProfileServiceImpl` save/confirm 仅对 `criticalChanged` 加锁；PASSED/在审记录的非关键字段（实习地点/方式/面试方式/能力测试结论）仍可被改写且 status 不变 | save/confirm 增"可编辑态"守卫：status ∈ {DRAFT, *_REJECTED} 或 locked!=1 才允许写 |
| b2 | 教务 `save` 覆盖在审（FIRST/SECOND_REVIEW）记录时不重置状态，审核人可能看到被静默改动的数据 | 同 b1：在审记录禁写 |
| b3 | `TrainingStatus.of()` 把未知状态静默映射为 DRAFT | 未知值抛错（防脏数据回流） |

> 注：b1/b2 与 Phase 3 已记的"confirm 无状态守卫"同类工作流卫生；建议在材料/免考等后续业务模块统一引入"可编辑态守卫"基类，一并解决。

---

## 六、放行

1. `PROGRESS.md` Phase 4 置 **✅ 已复核**；AT-04/AT-05 首验通过；Phase 3 confirm backlog 标记已闭环。
2. 合并 `main`（本地私有、无远程、不 push）；启动 Phase 5。
3. 3 个 Minor + "可编辑态守卫"统一化进 backlog。
