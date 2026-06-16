# Phase 8 复核报告 — 教师职业能力测试结果（T-068~T-071）

| 项 | 值 |
|---|---|
| 阶段 | Phase 8 测试结果 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `6fca8cb`（feat T-068~T-071 + 跨阶段顺手清理，单提交） |
| 增量基线 | `f297938..HEAD`（约 25 文件；business 11 / boot 3 / frontend 4 / 清理 2 + pom） |
| 迁移 | 新增 `V14__test.sql`；V1–V13 未改动 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | **Blocker × 0 · Major × 0 · Minor × 7（入 backlog）** |

---

## 一、结论

Phase 8 一轮通过。四项验收齐绿且服务端硬判：**免考联动**——`save` 从 Phase 6 `ExemptionService.examSubjects`（`finalStatus==PASSED` 单一真相）取应考口径，`exam_subjects=filter(includedInExam)`、`exemption_relation=filter(isExempted)`，复审通过科目正确移出应考；**成绩文本（AT-01 续接）**——`score` VARCHAR 全链路 String、`FastExcel useScientificFormat(false)`，21 位前导零串经 save+import 往返不丢不转；**结论有效性契约**——`qualified/exempted`→有效、`unqualified/pending_confirm`→无效，为 Phase 9 证书前置预留；**确认锁定守卫**——`test:confirm`→`locked=1`，锁定后改结论被拒（"已锁定"）；**免考≠过程性**——testresult 包对 process_material 零写入，反例证 FAILED/locked 材料不被覆盖。`ability_test_result` **读+写两侧数据范围**到位（写侧 collegeId 取自 student 实体、跨院 403 且不建行、无 SELF 写分支正确——学生无 test:edit；读侧 SELF/COLLEGE/SCHOOL）。**三项跨阶段顺手清理均正确落地且无回归**（C1 `ExemptionStatus.of()` fail-closed、C2 `StudentServiceImpl` 死分支删除、C3 `material:view` 补种+授权）。独立 `mvn verify` GREEN（33/33），type-check/build 绿。7 个 Minor 入 backlog，不阻断。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff f297938..6fca8cb`（单提交；V1–V13 与 AGENTS/REVIEW-GATE/HANDOFF/tasks 未改）。
2. **干净构建 + 独立重跑反例**：`mvn -B -ntp verify`（docker 起 MinIO/MySQL/Redis）→ **BUILD SUCCESS**，Failsafe 自动执行 Phase2 2 + Phase3 7 + Phase4 5 + Phase5 4 + Phase6 4 + Phase7 7 + `Phase8TestResultIT 4` ＝ **33/33**；前端 `type-check`/`build` 绿。回归确认顺手项无破坏：Phase6ExemptionIT 4/4（C1 安全）、Phase3StudentIT 7/7（C2 安全）。
3. **读码裁决**：`AbilityTestResultServiceImpl`（免考联动/validity/confirm-lock/读写硬校验/导入）、`AbilityTestConclusion`/`TestConfirmStatus`、`AbilityTestResultController`、`DataScopeSqlHandler`(ability_test_result 规则)、`V14__test.sql`、清理两文件 diff、`Phase8TestResultIT`。
4. **两路独立代理**（业务/数据范围/导入安全 · 质量/DoD/迁移/前端/清理校验/治理）——均 PASS，逐条确认 C1/C2/C3。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 免考联动/成绩文本/结论有效性/确认锁定/独立性 与 plan + phase-08 一致 |
| D2 验收清单 | ✅ | 四项验收复跑通过 |
| D3 AT 验收 | ✅ | AT-01 采集侧续接 P8；AT-13 ability_test_result 读写续接 |
| D4 红线合规 | ✅ | 数据范围读+写硬校验、文本化 String、留痕、不硬编码（组织方式/结论走字典） |
| D5 代码质量 | ✅ | 分层/事务/Result/BaseEntity；FastExcel 依赖 BOM 管理 |
| D6 安全 | ✅ | 写侧 collegeId 取自实体、跨院 403、学生无写权；导入逐行范围校验 |
| D7 构建与运行 | ✅ | `mvn verify` 33/33、type-check/build 绿；未自起常驻服务 |
| D8 测试 | ✅ | Phase8TestResultIT 4/4 接 failsafe，覆盖联动/AT-01/确认锁/独立/读写范围 |
| D9 数据库 | ✅ | 仅 V14 新增、V1–V13 未改；幂等、索引/唯一键/注释齐、文本 VARCHAR、JSON 快照 |
| D10 回归 | ✅ | Phase2~7 共 29 条回归通过（含 C1/C2 清理后回归） |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅；DEVLOG 含清理清单 |

---

## 四、做得好

- **免考联动单一口径**：复用 Phase 6 exam-subjects（不另立判定），exempted→exemption_relation、included→exam_subjects；反例证 B 科免考通过后应考清单不含 B、A/C 仍在。
- **AT-01 文本一致**：score VARCHAR + `FastExcel useScientificFormat(false)` + CSV 手解析；21 位前导零串经录入与导入往返一致。
- **结论有效性契约**：`AbilityTestConclusion.validForCertificate()` 合格/免考=true、不合格/待确认=false；`/validity` 端点为 Phase 9 证书前置干净对接。
- **确认锁定守卫**：confirm→locked=1；`ensureEditable` 锁定/非 PENDING 拒写；反例证锁定后改结论被拒、DB 不变。
- **读+写数据范围**：ability_test_result 注册进 `DataScopeSqlHandler`；写侧 collegeId 取自 student 实体、跨院 403 且不建行；读侧 SELF/COLLEGE/SCHOOL；导入逐行经写侧硬校验。
- **三项顺手清理真落地**：C1 `ExemptionStatus.of()`→抛错（脏状态 fail-closed，Phase6 回归绿）；C2 删 `StudentServiceImpl` 不可达的 `else if(!existing)` collegeId 死分支（穷举调用方证行为不变，Phase3 回归绿）；C3 `V14` 幂等补种 `material:view` + 四角色授权（ID 命名空间与 V8 不冲突）。

---

## 五、Minor（入 backlog，不阻断）

- `exam_subjects` 为 save 时**快照**；已存在记录 `get` 返回快照（空记录才实时算）——免考在 save 后变动会令快照陈旧。低危：Phase 9 证书前置以**结论有效性**为准、不依赖该快照；下次 save 自愈。可文档化或显示时实时重算。
- `importFile` 无行数/大小上限，`doReadSync`/`lines().toList()` 全量入内存且单事务——大文件内存/长事务压力；建议加行数上限。
- CSV 读入未做公式注入中和（`= + - @` 前缀）——仅在后续导出到表格时才成风险，留 **Phase 10 导出侧**处理。
- "确认后修改需审批+留痕"的审批工作流本阶段未实现（当前 locked 硬拒），属前向需求，行为 fail-closed 安全。
- `material:view` 已种已授但**暂无消费方**（无 `@pms.has('material:view')` 引用）——按清理项要求补种，预留材料读权限；后续接线或 DEVLOG 注明。
- `list()` 全量 `selectList`、total=size 非真分页（复发）。
- 测试覆盖：validity 正例（合格/免考→有效）、importFile（Excel/CSV 解析）、快照陈旧 未直接覆盖。

---

## 六、放行

1. `PROGRESS.md` Phase 8 置 **✅ 已复核**；AT-01 采集侧续接 P8；三项顺手清理标记已闭环。
2. 合并 `main`（本地私有、无远程、不 push）；启动 Phase 9（证书，AT-09/10/11 首验）。
3. 7 个 Minor 进 backlog。
