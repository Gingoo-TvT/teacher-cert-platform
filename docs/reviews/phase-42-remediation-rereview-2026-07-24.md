# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 42 PG-H3 remediation
**Audit mode:** incremental + security / stability / performance / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-24
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结 `5792025..b604e487b9571699670dd4dc1bb7a98866b5714d`。原 PG-H3 的并发正确性缺陷已经实质闭合：确认导入的每个逐行事务与失败明细事务都先锁同一 batch 并校验持久状态仍为 `IMPORTING`；rollback 从事务第一条 SQL 起持有 batch 行锁，等待在途行提交后锁定完整 refs 和业务记录，在同一事务中完成逆序补偿与终态提交；confirm 收尾 CAS 未命中时会返回数据库真实状态，不再伪报 `IMPORTED/FAILED`。

但本轮新增锁查询使用 `SELECT * ... FOR UPDATE`。`import_export_batch.preview_json` 保存整批 26 列预览，而该查询在每个成功行事务、以及失败行的错误明细事务中重复执行。N 行导入因此会重复传输和映射 N 次 O(N) 的整批 JSON，形成 O(N²) 数据量；这与项目明确的“万行级、5 分钟”同步导入契约冲突，且现有最大两行的交错 IT 无法发现。正式结论为 **CHANGES REQUESTED（1 Medium，1 Low）**；原 PG-H3 功能竞态可关闭，但 Phase 42 不能据此放行 Phase 39。

### Score Dashboard

```text
Security        █████████░  9.2  S   权限与批次归属边界未被绕过；未做攻击性动态验证，覆盖受安全约束限制
Stability       █████████░  8.9  A   同一 batch 锁与收尾 CAS 闭合迟到写和伪终态
Performance     ██████░░░░  5.8  B   逐行 SELECT * 重读整批 preview_json，万行路径退化为 O(N²)
Testing         ████████░░  8.2  A   真实 HTTP/MySQL 交错测试可信，但失败明细屏障缺专用回归
Maintainability █████████░  8.7  A   锁协议集中且测试钩子无生产行为，查询投影仍需收窄
Design          █████████░  8.5  A   线性化点清晰；锁查询同时承担整实体装载造成不必要耦合
Release         ███████░░░  6.8  B   构建与既有 320/320 证据可信，但万行主路径风险阻断放行
─────────────────────────────────────
Overall         ████████░░  8.0  A
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based. See `rubrics/scoring.md` for anchor descriptions.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 1 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **2** | **2** | **0** |

## 2. Project Map

本轮生产变更集中在 `platform-exchange`：

- `confirmImport` 先把 batch 从 `PREVALIDATED` 原子认领为 `IMPORTING`，再逐行进入 `REQUIRES_NEW`。
- 每个行事务先锁 batch，再写 student、training、certificate、certificate sequence 和 `import_record_ref`。
- 行失败后，错误明细使用另一个 `REQUIRES_NEW`，同样先锁 batch。
- rollback 在一个外层事务中按 batch → refs → 业务记录的顺序加锁，再提交补偿和批次终态。
- `ExchangeImportHook` 的生产 bean 为空操作；测试 bean 只负责把真实 HTTP/MySQL 事务停在可观察位置。

关键持久边界是 `import_export_batch`、`import_record_ref`、`student`、`training_profile`、`certificate` 和 `cert_sequence`。本轮无 DDL、权限点、前端生产逻辑或业务规则变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | Medium | controller 权限、批次归属校验、锁前后调用顺序、审计入口 | 按用户要求未执行漏洞扫描、攻击性请求或凭据尝试 |
| Stability | High | confirm/rollback 全控制流、事务传播、收尾 CAS、错误分支、现有 MySQL IT XML | 未在本轮重新执行交错测试 |
| Performance | Medium | 锁 SQL 投影、`preview_json` 数据形态、循环调用次数、万行/5 分钟客户端契约 | 未执行压测或大批量运行 |
| Testing Authenticity | High | `Phase10ExchangeIT` 新增测试、hook 位置、真实 HTTP/MySQL 日志、XML 与源码/编译时序 | 未重跑 latch 并发用例；Docker 清理声明未复验 |
| Release | High | commit 边界、320/320 XML 聚合、后端 9 模块 package、前端 type-check/build、diff check | 未独立 clean verify，未启动依赖或应用服务 |
| Configuration | High | 无新增配置键；生产 hook 默认空操作；事务管理器与 mapper 接线 | 未检查环境外部数据库参数 |
| Data Integrity | High | batch/ref/业务行锁顺序、逻辑删除补偿、CAS、数据库索引与约束 | `PARTIAL_ROLLBACK` 重试语义明确排除在 PG-H3 之外 |
| Concurrency | Medium | 两种 happens-before 控制流、InnoDB 锁查询、两个交错 IT 源码与 XML | 遵守安全限制，未重跑并发或故障交错 |
| Maintainability | Medium | 10 个变更文件及直接调用方 | 未做全模块结构审计 |
| Design | Medium | 本轮线性化协议和测试 seam | 未评估 Phase 42 之外的架构 |

## 3. Top Risks

1. **Medium — 逐行锁查询重复读取整批 `preview_json`。** 万行确认导入会产生 O(N²) 的数据库传输、JSON 解码和实体映射，可能超过既有 5 分钟客户端契约。
2. **Low — 失败明细并发守卫缺少专用反例。** 生产代码当前正确，但删除该锁/状态守卫的回归不会被现有 10/10 精确捕获。

## 4. Detailed Findings

### Finding: 逐行 batch 锁查询把万行导入退化为 O(N²)

- Severity: Medium
- Confidence: High
- Category: Performance
- Status: Confirmed
- Affected area: Phase 42 confirmImport 逐行事务与失败明细事务
- Evidence:
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/mapper/ImportExportBatchMapper.java:10-11`
  - Function / Module: `selectByIdForUpdate`
  - Relevant behavior: 锁查询使用 `SELECT *`，会把 batch 的所有列映射成 `ImportExportBatch`。
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:430-447`
  - Function / Module: `importOneInNewTransaction` / `addErrorInNewTransaction`
  - Relevant behavior: 每个成功行至少执行一次该锁查询；失败行写错误明细时还会再执行一次。
  - File: `platform-boot/src/main/resources/db/migration/V17__exchange.sql:11-15`
  - Function / Module: `import_export_batch`
  - Relevant behavior: `preview_json` 是整批预校验成功行 JSON，而不是单行状态。
  - File: `frontend/src/api/exchange.ts:112-115`
  - Function / Module: `BULK_OP_TIMEOUT_MS`
  - Relevant behavior: 产品明确把批量导入定义为万行级，并给出 5 分钟客户端等待窗口。
- Problem: N 行预览形成大小 O(N) 的 `preview_json`；确认循环又执行 N 次 `SELECT * ... FOR UPDATE`，导致数据库到 JVM 的传输、MySQL JSON 序列化、驱动解码和 MyBatis 实体映射达到 O(N²)。锁本身只需要读取 `status`，整实体装载不是串行化协议所需。
- Why it matters: 两行 IT 可以全绿，但在项目明确支持的万行规模，单个 batch JSON 会被重复读取数千至上万次，可能把一次导入变成超时、连接长时间占用和高额 GC/网络开销，并重新诱发用户重复提交。
- Realistic failure scenario: 教务员预校验一份一万行、26 列的工作簿；`preview_json` 保存整份预览。confirm 对每行开启事务并执行 `SELECT *`，累计重复读取整份 JSON 一万次；请求超过 5 分钟后客户端报错，而服务端仍在处理。
- Minimal fix: 为逐行事务新增只投影 `status`（或 `id,status`）的 `SELECT ... FOR UPDATE` mapper 方法，并让 `requireImportingBatchForUpdate` 使用它。rollback 单次需要 batch 元数据时可保留现有全行锁查询。
- Better long-term fix: 把大体积预览与 batch header/state 分离为 staging rows 或对象存储产物，状态锁永远只访问固定大小的 batch header。
- Regression test suggestion: 增加 mapper SQL/拦截器契约测试，断言逐行锁语句不选择 `preview_json`；再用大体积 `preview_json` 的安全定向基准验证行数增长不会导致锁查询返回字节数按 N² 增长。
- Estimated effort: 1–2 小时

### Finding: 失败明细锁屏障没有专用回归

- Severity: Low
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: Phase 10/42 import_error_detail 并发屏障
- Evidence:
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:440-447`
  - Function / Module: `addErrorInNewTransaction`
  - Relevant behavior: 失败明细事务当前正确地先锁 batch 并守卫 `IMPORTING`。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase10ExchangeIT.java:451-615`
  - Function / Module: 两个 Phase 42 PG-H3 交错测试
  - Relevant behavior: 两个新增交错都使用成功行，只覆盖业务写/ref 的迟到提交。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase10ExchangeIT.java:365-389`
  - Function / Module: `importRowsAreCommittedIndependentlyWhenLaterRowHitsDatabaseException`
  - Relevant behavior: 既有失败行测试验证 failCount 和业务数据隔离，但不核对 `import_error_detail`，也不与 rollback 交错。
- Problem: 当前生产实现满足 PG-H3，但若未来重构删除 `addErrorInNewTransaction` 的 batch 锁或 `IMPORTING` 守卫，现有 10 个 Phase 10 IT 仍可能全绿。
- Why it matters: rollback 终态之后出现迟到错误明细会让异常报告与批次终态时间线不一致，降低审计可信度。
- Realistic failure scenario: 后续维护者保留业务行锁，却把错误明细恢复为普通自动提交；失败行事务回滚后 rollback 先结束，错误明细随后插入，现有两条成功行交错测试不报错。
- Minimal fix: 增加一条失败行专用交错 IT，在行失败后、错误明细取 batch 锁前暂停，让 rollback 先完成，再断言没有迟到 `import_error_detail`。
- Better long-term fix: 将业务行、失败明细和终态的状态协议提炼为统一的 batch-state guard，并对三个写入口做参数化契约测试。
- Regression test suggestion: 新增 T-IMP-5C，验证 rollback 先线性化与失败明细先线性化两种顺序。
- Estimated effort: 2–4 小时

## 5. Security Concerns

- Coverage: Medium
- Inspected evidence: `ExchangeController` 的 `exchange:import` 权限、`ensureBatchAccessible`、rollback 锁后权限解析、审计注解与服务内富审计。
- Exclusions / limits: 未执行漏洞扫描、恶意请求、凭据尝试或任何可能触发 cyber 安全限制的操作。

本增量没有修改权限点、认证、输入解析或敏感字段输出。rollback 在读取 refs 和业务记录之前仍执行批次归属校验；先锁 batch 再解析数据范围不会扩大可读取的数据。未发现新的安全 finding。

## 6. Stability Concerns

- Coverage: High
- Inspected evidence: `confirmImport` 全循环、两个 `REQUIRES_NEW`、rollback 外层事务、收尾 CAS、逻辑删除补偿、真实 MySQL IT 日志。
- Exclusions / limits: 未在本轮重新运行并发交错。

原 PG-H3 的迟到业务写、迟到 ref、rollback 一次性快照和 confirm 伪终态均已由统一 batch 锁闭合。错误明细的生产路径也采用相同屏障，但其测试证据存在 Finding 2 所述缺口。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: mapper SQL 投影、batch 实体、`preview_json` 写入与读取、逐行循环次数、前端批量超时契约。
- Exclusions / limits: 未执行压力、容量或大批量测试。

Finding 1 是本轮阻断项。问题来自固定可追踪的 O(N²) 控制流，不依赖压测才能成立；动态测试只用于量化实际阈值，不改变其存在性。

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: `Phase10ExchangeIT` 全文件、测试 hook、Failsafe XML、全仓 Surefire/Failsafe 聚合、源码/编译/报告/提交时间链。
- Exclusions / limits: 未独立重跑 latch 测试；临时 Docker 资源清理声明未通过 Docker API 复验。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| rollback 先线性化 | High | 可捕获第二行迟到业务写/ref 与伪终态 | Keep |
| 在途行先线性化 | High | 可捕获 rollback 未等待及 refs 不完整 | Keep |
| 失败明细屏障 | Medium | 删除错误明细锁/守卫可能逃逸 | Add T-IMP-5C |
| Phase10ExchangeIT 全类 | High | 真实 RANDOM_PORT HTTP + MySQL 8，10/10 | Keep |
| 全量回归 | High | Surefire 149/149 + Failsafe 171/171，0 failure/error/skip | Keep |

### Valuable Tests

- `confirmAndRollbackInterleavingCannotLeaveLateCommittedRows`：第一行提交后让 rollback 先完成，随后释放 confirm，核对第二行无业务数据/ref。
- `rollbackWaitsForInFlightRowAndCompensatesItsCommittedRefs`：行事务持锁时 rollback 不得完成，释放后必须补偿完整 3 refs。
- 原子双 confirm、坏行逐行隔离、回滚冲突与数据范围回归仍保留，没有因本轮新增测试被删除或弱化。

### Suspicious Tests

第二条测试用 500ms 负向等待证明 rollback 尚未完成，理论上存在极低的线程调度假阴性窗口；但测试先确认 rollback 已进入紧邻锁查询的 hook，且随后要求真实补偿 3 refs，风险被明显收窄。本项作为证据限制，不另列 finding。

### Missing Tests

- T-IMP-5C：失败行回滚与错误明细落库的双向交错。
- 万行路径不应通过本轮复核者压测验证；应先用 SQL 投影契约消除确定性的 O(N²)，再在受控环境补容量证据。

## 9. Release Concerns

- Coverage: High
- Inspected evidence: `5792025..b604e48` 全部 10 个变更文件、现有 320/320 XML、后端 9 模块 package、前端 type-check/build、`git diff --check`。
- Exclusions / limits: 未独立执行 clean verify、未启动 MySQL/Redis/MinIO 或常驻应用。

后端 package、前端 type-check/build 均独立通过；现有 XML 与当前源码/编译产物时序一致。但是 Finding 1 直接影响明示的万行导入主路径，Phase 42 不具备放行条件。

## 10. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: 新增 mapper、`ExchangeImportHook` bean、事务管理器注入、Phase 10 文档与提交材料。
- Exclusions / limits: 未读取部署环境外部数据库参数。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| SchemaValidation | 0 | 无新增配置 | 无 |
| UnsafeDefault | 0 | 无新增配置 | 无 |
| EnvironmentSeparation | 0 | `ExchangeImportHook` | 生产实现保持空操作 |
| SecretConfig | 0 | 无 | 无 |
| FeatureFlag | 0 | 无 | 无 |
| ConfigDocs | 0 | Phase 10 文档已记录锁协议 | 修复后同步查询投影约束 |

## 11. Data Integrity Analysis

- Coverage: High
- Inspected evidence: batch/ref/业务行锁、事务传播、CAS、逻辑删除、数据库索引/唯一约束、两个交错测试。
- Exclusions / limits: `PARTIAL_ROLLBACK` 自动重试语义按提交材料明确排除，不在本轮宣称闭环。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 0 | 补偿与终态同事务 | 保持 |
| Idempotency | 0 | 双 confirm 原子认领 | 保持 |
| ConcurrencyConsistency | 0 | confirm/rollback 同 batch 锁 | 保持 |
| MigrationSafety | 0 | 本轮无迁移 | 无 |
| InvariantValidation | 0 | 非 `IMPORTING` 行在业务写前停止 | 保持 |
| BackupRestore | 0 | 不在本增量范围 | 无 |
| Reconciliation | 0 | 收尾 CAS 重读真实终态 | 保持 |

## 12. Concurrency Analysis

- Coverage: Medium
- Inspected evidence: InnoDB batch 主键锁、`idx_import_ref_batch`、业务唯一约束、lock order、两个真实交错 IT 与 XML。
- Exclusions / limits: 未重跑并发、故障注入、压力或破坏性测试。

并发协议的线性化点成立：

1. rollback 先取得 batch 锁时，后续行只能看到非 `IMPORTING` 并在业务写前终止。
2. 在途行先取得锁时，rollback 等待该行将业务数据与 refs 同事务提交，再读取完整 refs 并补偿。
3. confirm 收尾先取得行锁时先落 `IMPORTED/FAILED`；之后 rollback 可按既有允许状态补偿。

Finding 1 的修复必须保留 `FOR UPDATE`，只收窄投影；直接移除逐行 batch 锁会重新打开原 PG-H3。

---

## 13. Principles Compliance

本增量遵守 fail-fast、显式事务边界、固定锁顺序和生产/测试 seam 分离。主要偏差是状态锁查询没有保持固定大小的资源边界。

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Unbounded Resources Must Not Grow Forever (10.2) | 1 | Medium | 逐行 `SELECT *` 重复装载 O(N) 的 `preview_json` |
| Test Behavior, Not Implementation (8.1) | 1 | Low | 错误明细状态屏障缺少可观察行为回归 |

### Principles Respected

- Fail-Fast：非 `IMPORTING` 在任何逐行业务写之前终止。
- Explicit Dependencies：事务管理器和测试 hook 均显式注入。
- No Shared Mutable State Without Synchronization：生产 hook 无状态；测试状态仅存在于测试主 bean。
- Transaction Boundary：rollback 的 batch 锁、refs、业务补偿和终态位于同一事务。
- Consistent Lock Ordering：confirm 与 rollback 都从 batch 锁进入，避免原先的锁外状态快照。

---

## 14. Recommended Fix Order

### Fix Immediately

1. 将逐行/错误明细的 batch 锁查询改为只投影 `status` 或 `id,status`，保留 `FOR UPDATE`。
2. 用静态 SQL 契约或 mapper 测试证明该路径不再选择 `preview_json`。

### Fix Before Stable Release

1. 修复后重跑 `Phase10ExchangeIT` 10/10、后端全量 verify、前端 type-check/build。
2. 重新提交 Phase 42 独立增量复核；PASS 前不放行 Phase 39。

### Schedule Later

- 补 T-IMP-5C 失败明细交错反例。
- 若要改变 `PARTIAL_ROLLBACK` 自动重试语义，先补规格、幂等标记和独立反例；本轮不顺带改写。

### Ignore for Now

- 第二条交错测试 500ms 负向等待的极低调度假阴性，仅作为证据限制保留，不阻断本轮问题定位。

## 15. Quick Wins

- mapper 增加一个 status-only `FOR UPDATE` 查询，并让 `requireImportingBatchForUpdate` 使用它；不需要 DDL、前端或状态枚举变化。
- 在 Phase 10 文档补一条固定约束：“逐行 batch 锁查询不得装载 `preview_json`”。
