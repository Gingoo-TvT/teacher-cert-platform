# T-115 复核报告 — Phase14E2EIT 单一大用例拆分（收官后·测试可维护性加固）

| 项 | 值 |
|---|---|
| 任务 | T-115（收官后维护，非新 Phase）：把 851 行单一 E2E 拆为按阶段的有序子用例 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-18 |
| 被复核提交 | `96980e6`（test T-115，单提交） |
| 增量基线 | `main..HEAD`（3 文件：`Phase14E2EIT.java` + PROGRESS + DEVLOG；**无业务代码/迁移/yml/pom/前端改动**） |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | **Blocker × 0 · Major × 0 · Minor × 0** |

---

## 一、结论

T-115 一轮通过。`Phase14E2EIT` 由唯一的 851 行 `mainFlowFromImportToArchiveAndStandardExportIsConsistent` 拆为 **14 个 `@Order` 有序阶段子用例**（导入→学生复审→培养复审→四类材料→免考→视频复评→测试结果→证书前置→生成→签发→导出→归档→标准导出终断言→审计留痕），单点失败可定位到具体阶段。采用 `@TestMethodOrder(OrderAnnotation)` + `@TestInstance(PER_CLASS)` 以实例字段跨有序方法共享流程状态，`@BeforeAll/@AfterAll` 仅做整条链路前后清理。**原有断言零删减**，全部迁移到对应阶段。`mvn -B -ntp verify` **BUILD SUCCESS，Failsafe 75/75**（`Phase14E2EIT` 14 + Phase2~13 回归 61）。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff main..96980e6`——仅 `Phase14E2EIT.java`（+150/−25）及 PROGRESS/DEVLOG；`grep` 确认无 migration / `application*.yml` / `/main/java/` / `pom.xml` 改动。
2. **干净构建 + 全回归**：首跑全 IT 报 `ApplicationContext failure threshold exceeded`，根因为本地 MySQL/Redis/MinIO 未启动（失败报告含 `Communications link failure`/`Connection refused`），属环境问题；起 `docker-compose.dev.yml` 后重跑 → **BUILD SUCCESS 75/75**。
3. **读码裁决（100% 增量）**：逐一核对 14 个 `@Order` 子用例与其断言、`PER_CLASS` 共享字段（`importedRow/student/training/exemptionId/videoId/testId/certId/issuedCertNo/certAuditBefore`）、`@BeforeAll/@AfterAll` 的 `resetData()` 幂等清理；对照原断言清单确认零删减。

---

## 三、断言保全核对（原 → 拆分后所在阶段）

| 原关键断言 | 拆分后位置 | 保全 |
|---|---|---|
| 标准导出 26 列 + H="身份证件号码" + 全列文本格式 `@` | `@Order(13)` `assertStandardExport` | ✅ |
| 导出 studentNo/name/idCard/birthDate/teachingSubject/certNo/validUntil **逐字段==录入** | `@Order(13)` | ✅ |
| 测试成绩前导零 `00000000000085` + 确认锁定 | `@Order(7)` | ✅ |
| 免考通过剔除应考科目（`includedInExam=false`） | `@Order(5)` | ✅ |
| 视频 85/60→NEED_REVIEW→第三专家→**终分 83**+CONFIRMED | `@Order(6)` | ✅ |
| 证书 18 位 + 前缀 `202610588` / 签发有效期 `2029/6/30` / 归档 ARCHIVED | `@Order(9/10/12)` | ✅ |
| 审计 student/training/exemption secondReview + video confirm + cert 生命周期 ≥+4 | `@Order(14)`（基线于 `@Order(8)` 捕获 `certAuditBefore`） | ✅ |

---

## 四、维度结论

| 维度 | 结论 | 说明 |
|---|---|---|
| D5 代码质量 | ✅ | JUnit5 有序 + PER_CLASS 共享状态用法正确；辅助方法抽取、命名清晰，失败定位到阶段 |
| D7 构建与运行 | ✅ | `mvn verify` 75/75；未自起常驻服务（build-only） |
| D8 测试 | ✅ | 主流程覆盖与断言**不变**，仅结构化；回归 61 全绿 |
| D9 数据库 | ✅ | 无迁移；V1–V19 未改 |
| D10 回归 | ✅ | Phase2~13 共 61 条全绿 |
| D11 文档/进度 | ✅ | 治理未改；PROGRESS 置「待复核」未自 ✅；本报告归档 |

---

## 五、放行

1. `PROGRESS.md` T-115 置 **✅ 已复核**。
2. 合并 `main`（本地私有、无远程、不 push）。
3. 无 Minor。测试可维护性加固达成，E2E 覆盖广度与断言强度不变。
