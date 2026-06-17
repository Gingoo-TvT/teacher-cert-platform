# Phase 10 复核报告 — 导入导出与预校验（T-080~T-091）

| 项 | 值 |
|---|---|
| 阶段 | Phase 10 数据交换中心 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `c074a48`（feat T-080~T-091，单提交；新增 platform-exchange 模块） |
| 增量基线 | `9b6bc12..HEAD`（约 36 文件） |
| 迁移 | 新增 `V17__exchange.sql`（3 表）；V1–V16 未改动 ✓（codex 正确改用 V17，规格里的 V16 已被 Phase 9 占用） |
| **判定（轮次1 · 06-17）** | **❌ 退回（CHANGES REQUESTED）** |
| 计数 | **Blocker × 0 · Major × 2 · Minor × 7（入 backlog）** |

---

## 一、结论

Phase 10 主体质量很高，三项 AT 首验头部全过且生命线稳：**AT-01 文本化三处齐全**（模型 26 字段全 String + 写出单元格 `@` + 读取走 `DataFormatter` 字符串）——学号 `00123`/证件号/出生日期/证书编号/有效期 导入→导出无科学计数、无前导零丢失；**AT-02 26 列 A–Z 固定 + H 列="身份证件号码"**（写表头/读校验严格逐列）；**AT-14 V-01~V-13 逐条**复用既有校验器（NameValidator/IdCardValidator/BirthDateValidator/MajorCodeValidator/TrainingLinkValidator），预校验**不入库**、异常含 行/字段/错误值/原因/建议、可下载报告（13 条各一反例命中并定位、successCount=0、零写入——已验）。导入策略 INSERT_ONLY/SKIP_DUPLICATE、回滚（INSERT→删/UPDATE→还原 before_json）与**冲突判定**（current 快照≠after 快照则跳过、剔除审计字段）、导出**读侧数据范围**（@DataScope 重写 certificate.college_id，clerk 仅见本院）、敏感脱敏（exchange:export:sensitive 才出明文）均正确且反例齐。V17 仅新增、V1–V16 冻结；新模块 platform-exchange 正确注册接入、POI 经 fastexcel 传递且版本受管；`mvn verify` 44/44、前端 type-check/build 绿。**这些无需返工。**

**但导入写侧存在 2 个 Major，须修复后放行：**

- **B1（Major）导入更新写侧数据范围漏校验现有记录归属**：`importOne` 以 `studentByNo(studentNo)` **无范围**地命中任意学院的现有学生，`ensureCanImportCollege(collegeId)` 只校验**解析出的目标学院**、**不校验现有记录的当前学院**；再加 `applyStudent` 无条件 `setCollegeId(目标)`。故 OVERWRITE/UPDATE_EMPTY 策略下，**学院 A 教务员可凭学号覆盖/迁移他院（B）学生**（连带 training/certificate 整图迁到 A）。这是与 Phase 3 同类的跨学院写漏洞。
- **B2（Major）导入逐行事务粒度**：`confirmImport` 为单一 `@Transactional`，行循环只 `catch(BizException)`；`trainingProfileMapper.insert`/`certificateMapper.insert` 抛出的裸 `DataAccessException`（TOCTOU 唯一键冲突/字段超长/死锁）会**逃出 per-row catch、回滚整批**；且 `studentMapper.insert` 捕获 `DuplicateKeyException` 后继续同事务可致 commit 时 `UnexpectedRollbackException`。一行异常即可让整批 500 且批次停留 PREVALIDATED。

按 `REVIEW-GATE §5`（写侧数据范围/后端硬校验）判 **退回**。退回聚焦 B1/B2；AT-01/02/14 头部、读侧范围、回滚冲突、迁移/模块 无需返工。修复后只复核增量 + 回归。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff 9b6bc12..c074a48`（单提交；V1–V16 与 AGENTS/REVIEW-GATE/HANDOFF/tasks 未改）。
2. **干净构建 + 重跑反例**：`mvn -B -ntp verify` → **BUILD SUCCESS 44/44**（Phase2~9 共 40 + `Phase10ExchangeIT 4`）；前端 `type-check`/`build` 绿。
3. **读码裁决**：`ExchangeServiceImpl`(1449 行：prevalidate/V-01~V-13/importOne/rollback/export/数据范围)、`ExchangeExcelHelper`(文本格式/下拉/读)、`ExchangeColumn`(26 列模型)、`ExchangeController`、`V17__exchange.sql`、`Phase10ExchangeIT`。
4. **两路独立代理**（AT/导入/范围 · 质量/迁移/模块/前端/治理）：质量代理 PASS；AT 代理 CONCERNS（独立判出 B1/B2）。亲核 `importOne` 行 357-391 确认 B1 可达。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 模板/预校验/导入/回滚/异常/5 类导出 与 plan + phase-10 一致 |
| D2 验收清单 | ✅ | AT-01/02/14 复跑通过（含 13 V 反例） |
| D3 AT 验收 | ✅ | AT-01 文本化、AT-02 26 列+H、AT-14 13 条 V 全过 |
| D4 红线合规 | ❌ | **导入更新写侧数据范围漏校验现有记录归属（B1）**；读侧范围/文本化/留痕合规 |
| D5 代码质量 | ⚠️ | 分层/事务/Result/BaseEntity 规范；导入逐行事务粒度（B2） |
| D6 安全 | ⚠️ | 读侧范围+敏感脱敏到位；导入跨学院更新可越权（B1） |
| D7 构建与运行 | ✅ | `mvn verify` 44/44、type-check/build 绿；未自起常驻服务 |
| D8 测试 | ⚠️ | 13 V 反例/INSERT_ONLY/SKIP/回滚冲突/读写范围齐；OVERWRITE/UPDATE_EMPTY/全量导出/跨院更新 未覆盖 |
| D9 数据库 | ✅ | 仅 V17 新增、V1–V16 未改；幂等、JSON、文本 VARCHAR、唯一键/索引 |
| D10 回归 | ✅ | Phase2~9 共 40 条回归通过 |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅ |

---

## 四、做得好（无需返工）

- **AT-01 文本化生命线**：`ExchangeExcelHelper` textStyle `@`、所有单元格 `CellType.STRING`+文本样式、读用 `DataFormatter.formatCellValue`；无任何 numeric/date setCellValue 路径；IT 断言 26 列样式 `@` + 学号 `00123`/证件号/编号/有效期 往返一致。
- **AT-02 26 列**：`ExchangeColumn` A–Z 固定、H="身份证件号码"；写表头/读校验严格逐列。
- **AT-14 V-01~V-13**：12 个校验方法覆盖 13 条（V-09/V-10 同 TrainingLinkValidator），**复用既有校验器**、预校验只写 batch+error_detail（不写业务表）、错误含行/字段/原因/建议；13 反例全命中、零入库。
- **回滚冲突判定**：`snapshot(current)≠after_json`（剔除 createdAt/updatedAt/createdBy/updatedBy/deleted）则跳过提示冲突，反例证后续修改不被覆盖。
- **导出读侧数据范围 + 敏感脱敏**：`@DataScope(certificate)` 重写 college_id（clerk 仅本院）、`exchange:export:sensitive` 才出明文证件号；反例齐。
- **迁移/模块**：V17 幂等、V1–V16 冻结（codex 正确改用 V17）；platform-exchange 新模块正确注册接入、无环；POI 经 fastexcel 传递受管；前端 `row-key` 显式类型、无 any。

---

## 五、问题清单（须修）

| 编号 | 级别 | 维度 | 问题 / 证据 | 期望 |
|---|---|---|---|---|
| **B1** | Major | 写侧数据范围 | `ExchangeServiceImpl.importOne`(:359) `studentByNo` 无范围命中任意学院现有学生；(:374-375) `ensureCanImportCollege` 只校验解析出的目标学院、不校验 `existingStudent.getCollegeId()`；`applyStudent`(:650) 无条件 `setCollegeId(目标)`。OVERWRITE/UPDATE_EMPTY 下学院 A 教务员凭学号即可覆盖/迁移他院 B 学生及其 training/certificate。 | 更新现有 student/training/certificate 前，断言**现有记录当前 collegeId ∈ 调用者写范围**（如 `ensureCanImportCollege(existing.getCollegeId())`），跨范围拒绝；collegeId 不跨范围改写。补反例：clerk A 导入他院学号 → 拒、B 学生不变。 |
| **B2** | Major | 健壮性/事务 | `confirmImport`(:222-263) 单事务、行循环仅 `catch(BizException)`；`trainingProfileMapper.insert`/`certificateMapper.insert` 的裸 `DataAccessException` 逃出→回滚整批；`studentMapper.insert` 捕获 `DuplicateKeyException` 后继续同事务可致 commit 时 `UnexpectedRollbackException`。一行异常即让整批失败。 | 每行 `importOne` 用 `REQUIRES_NEW` 子事务隔离，或 per-row `catch(DataAccessException)` 记为该行失败；保证 VO 计数与已提交一致、坏行不污染整批。 |

---

## 六、Minor（入 backlog，可随 B1/B2 一并清）

- OVERWRITE/UPDATE_EMPTY 两种更新策略零测试覆盖（仅 INSERT_ONLY/SKIP_DUPLICATE 验过）；补往返用例。
- V-12 有效期在 certNo 已未过 V-11 时仍会跑（同行多一条噪声错误）；建议 V-11 失败则跳 V-12。
- `student` 无 `id_card_no` 唯一约束，跨批/并发去重仅靠应用查询；建议加唯一索引或文档化。
- 全量审核表/附件清单/zip 导出路径未在 IT 断言；补覆盖。
- `batches()` 全量 `selectList` 包 `PageResult`（非真分页）且不按操作人/学院隔离（批次为操作元数据、非 PII，规格未强制；可加 operatorId 过滤）。
- 导出/读取用内存 `XSSFWorkbook`（非 SXSSF 流式）——当前数据量可接受，大规模再改流式。
- 导入对已 `ISSUED locked=1` 证书的 OVERWRITE 覆盖无额外审计标记；建议覆盖既有终态证书时显式留痕/告警。

---

## 七、退回处理

1. 阶段在 `PROGRESS.md` 置 **复核退回**；**不合并 main、不置 ✅**；AT-01/02/14 维持 `[~]`。
2. codex 在原分支 `feature/phase10-T080-exchange` 仅修 **B1 / B2**（Minor 可一并捎带），补反例：clerk 跨院更新被拒、坏行不污染整批（per-row 隔离）、OVERWRITE/UPDATE_EMPTY 往返；确保 `mvn verify` 仍绿；DEVLOG 记修复。
3. 重交后复核方**只复核增量 + 回归**，重点复跑：导入更新写侧范围（现有记录归属）、逐行事务隔离、AT-01/02/14 未回归。直至 PASS。
