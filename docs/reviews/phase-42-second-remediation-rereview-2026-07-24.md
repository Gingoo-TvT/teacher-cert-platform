# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 42 second remediation
**Audit mode:** incremental + security / stability / performance / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-24
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结 `ec4ca30e8c09babd074b8c917b446a8881cb6d8f..bd1db49471a327a5cb35cf734d4f44ad436d893a`，只复核首轮新增的 1 Medium / 1 Low 及其直接回归。结论为 **PASS**：

- 首轮 Medium 已关闭。逐行导入和错误明细事务现在只执行固定大小的 `SELECT status ... FOR UPDATE`；保存整批 26 列预览的 `preview_json` 不再进入逐行锁查询，原 O(N²) DB→JVM 数据量根因已经从真实调用路径移除。
- 首轮 Low 已关闭。T-IMP-5C 增加错误明细与 rollback 的双向真实 MySQL 交错，分别证明 rollback 先提交时不会出现迟到错误明细，以及错误明细先持锁时 rollback 必须等待其提交。
- 原 PG-H3 的并发屏障没有被性能修复削弱。rollback 仍以完整 batch 行锁作为事务内第一条生产数据库访问，锁顺序继续是 batch → refs/business；生产 hook 仍为空操作。
- 本增量未发现新的 Blocker、High、Medium、Low 代码 finding。Phase 42 可以关闭并放行下一项 Phase 39；全项目仍因 Phase 0、39、41、44、47、53 六个既有退回项保持 **CHANGES REQUESTED**。

有一项必须保留的非阻断证据限制：开发者全量 XML 为 323/323，但最终 `Phase10ExchangeIT.java` 的修改时间晚于该 XML；提交材料声明 gate 后只改了两处断言说明文字，当前源码已重新编译且静态控制流成立，但缺少 gate 前源码快照，无法把 323/323 严格证明为 `bd1db49` 的字节级提交证明。若发布门禁要求严格 commit attestation，应由用户在该提交上自行重跑全量门禁。

### Score Dashboard

```text
Security        █████████░  9.3  S   无权限、鉴权、敏感输出或新攻击面变化
Stability       █████████░  9.2  S   原 batch 线性化点与 rollback 原子边界保持
Performance     █████████░  9.3  S   逐行锁查询固定大小，O(N²) 根因关闭
Testing         █████████░  8.7  A   真实 HTTP/MySQL 与双向交错充分；最终提交同源证明有限
Maintainability █████████░  9.0  S   全行锁与标量锁职责分开，调用点集中
Design          █████████░  9.1  S   性能修复未牺牲串行化协议
Release         █████████░  8.6  A   构建门禁通过；动态 XML 非严格最终提交证明
─────────────────────────────────────
Overall         █████████░  9.0  S
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based. See `rubrics/scoring.md` for anchor descriptions.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **0** | **0** | **0** |

## 2. Project Map

本轮生产变更仍集中于 `platform-exchange`：

- `ImportExportBatchMapper.selectStatusByIdForUpdate` 为逐行/错误明细提供固定投影的 batch 行锁。
- `ExchangeServiceImpl.requireImportingBatchForUpdate` 只读取标量状态，并在任何业务写或错误明细写之前拒绝非 `IMPORTING`。
- rollback 独占使用 `selectByIdForUpdate` 读取完整 batch 元数据，然后按既有 batch → refs → business 顺序补偿。
- `ExchangeImportHook` 增加错误明细与 rollback 的测试观察点；生产实现全部为空操作。
- `Phase10ExchangeIT` 同时验证静态 MappedStatement、真实 confirm SQL、T-IMP-5C 两种交错以及线程清理。

本轮无 DDL/Flyway、状态集合、权限点、业务规则、前端生产代码或运行配置变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | Medium | 权限/归属调用链、增量 API 面、生产 hook | 按用户要求未执行扫描、攻击性请求、凭据尝试或恶意载荷 |
| Stability | High | 逐行/错误明细/rollback 事务边界、异常分支、锁顺序 | 未在本轮重跑 latch/并发测试 |
| Performance | High | Mapper 精确投影、全部调用方、真实 SQL 探针、`preview_json` 数据形态 | 未执行万行压测 |
| Testing Authenticity | High | RANDOM_PORT HTTP、MySQL 8、MyBatis 探针、13 个 Phase 10 IT、44 份 XML | XML 早于最终测试源文件；详见证据限制 |
| Release | High | 单提交边界、323/323 XML、当前源码 package、前端 type-check/build、diff check | 未独立 clean verify，未启动依赖或应用 |
| Configuration | High | 本轮配置差异、Spring bean、事务管理器 | 未读取部署环境外部参数 |
| Data Integrity | High | batch/ref/business 锁、CAS、逻辑删除与终态 | `PARTIAL_ROLLBACK` 自动重试语义明确排除 |
| Concurrency | High | 双向 T-IMP-5C 源码、真实锁边界、事件顺序与持久终态 | 未动态重跑交错、压力或故障注入 |
| Maintainability | High | 增量 10 个文件及直接调用方 | 未做全仓结构审计 |
| Design | High | 状态锁与元数据锁职责、线性化点 | 不重开 Phase 42 之外的架构主题 |

## 3. Incremental Change Summary

### Change Categories

| Category | Change | Review result |
|----------|--------|---------------|
| Performance | 逐行/错误明细锁由全实体改为标量 `status` 投影 | PASS；固定返回大小且保留 `FOR UPDATE` |
| Concurrency | 为错误明细/rollback 增加测试观察点 | PASS；生产实现为空操作 |
| Testing | 增加 SQL 投影契约、真实调用链 SQL 探针、T-IMP-5C 双向交错 | PASS；不存在只测未使用 Mapper 的假绿 |
| Documentation | 同步 Phase 10、进度、交接与提交材料 | PASS；候选阶段未提前宣称正式通过 |

### Risk Delta

- 性能风险下降：逐行锁查询不再随整批 `preview_json` 大小增长。
- 并发风险不变且受控：batch 行锁、事务传播和锁顺序均未移除或重排。
- 测试回归风险下降：错误明细的两种线性化顺序已有专用行为反例。
- 发布证据仍有轻微限制：323/323 不是最终提交的严格字节级证明。

### Test Coverage Delta

- `Phase10ExchangeIT` 从 10 项增至 13 项。
- 新增 1 项 Mapper/真实 SQL 投影契约和 2 项 T-IMP-5C 交错。
- 既有测试未删除或弱化；XML 显示 Phase 10 13/13、Surefire 149/149、Failsafe 174/174。

### Approval Recommendation

**PASS。** 首轮 1 Medium / 1 Low 均按原问题口径关闭，未发现直接回归。Phase 42 可从“复核退回”改为“独立复核 PASS”，下一整改项为 Phase 39。

## 4. Top Risks

本增量没有发布阻断风险。以下仅为边界，不是新 finding：

1. 323/323 XML 早于最终测试源文件；若要求严格提交级门禁，需在 `bd1db49` 上重新执行。
2. `StatementHandler.query` 探针在实际 JDBC 调用前发出“已进入查询”信号，500ms 负向等待仍存在极低调度窗口；释放后的实际取锁顺序与持久终态提供了独立正向证据。
3. `PARTIAL_ROLLBACK` 自动重试语义仍是既有范围外债务，本报告不宣称关闭。

## 5. Detailed Findings

**None.** 本轮没有新增 Critical / High / Medium / Low finding。

### Previous Finding Closure

| Previous finding | Previous severity | Closure evidence | Result |
|------------------|-------------------|------------------|--------|
| 逐行 `SELECT * ... FOR UPDATE` 重复装载整批 `preview_json` | Medium | `selectStatusByIdForUpdate`、全部生产调用点、MappedStatement 精确 SQL、真实 confirm 三条锁 SQL | Closed |
| 错误明细 batch 锁/状态守卫缺少专用交错反例 | Low | T-IMP-5C rollback-first 与 error-detail-first 两种真实 MySQL 顺序 | Closed |

## 6. Security Concerns

- Coverage: Medium
- Inspected evidence: 增量接口面、controller 权限与批次归属调用链、生产 hook、日志/错误响应。
- Exclusions / limits: 未执行漏洞扫描、攻击性探测、恶意载荷、fuzz、凭据尝试或任何可能触发 cyber 限制的操作。

本增量没有新增端点、权限点、敏感字段输出、外部命令、反序列化入口或配置密钥。未发现安全 finding。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: `confirmImport`、两个 `REQUIRES_NEW` 写入口、rollback 事务、收尾 CAS、异常清理与线程终止断言。
- Exclusions / limits: 未动态重跑 latch/并发测试。

标量查询只改变投影，不改变主键行锁。`status` 为 `NOT NULL`，因此标量返回 `null` 仍可无歧义表示批次不存在或已逻辑删除。rollback 继续读取完整实体，补偿范围与状态提交未受影响。

## 8. Performance Concerns

- Coverage: High
- Inspected evidence: 两个 Mapper 锁查询、全仓调用追踪、逐行循环、`preview_json` 列、实际 SQL 捕获。
- Exclusions / limits: 未执行压力或万行容量测试。

首轮 O(N²) 根因已经结构性消失：成功行、失败行和错误明细每次只返回固定大小的 `status`，而不是 O(N) 的整批预览。保留行锁不会重新引入数据量放大。未发现新的性能 finding。

## 9. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: `Phase10ExchangeIT` 全部新增测试、MyBatis 插件、测试 hook、44 份 Surefire/Failsafe XML、源码/编译/XML/提交时序。
- Exclusions / limits: 本轮未重跑并发测试或 clean verify。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 固定锁投影 | High | 静态 SQL 与真实 confirm 三次锁查询互相印证 | Keep |
| rollback 先于错误明细 | High | 真实 HTTP/MySQL，终态与无迟到数据共同断言 | Keep |
| 错误明细先于 rollback | High | query 边界、锁取得事件、持久明细与终态共同断言 | Keep |
| 线程/闩锁清理 | High | Future 取消、finally 释放与 executor 终止硬断言 | Keep |
| 最终提交动态同源性 | Medium | XML 早于最终测试源码，缺 gate 前快照 | User rerun only if strict attestation is required |

### Valuable Tests

- `rowBatchLockQueryUsesFixedStatusOnlyProjection`
- `importRowsAreCommittedIndependentlyWhenLaterRowHitsDatabaseException`
- `rollbackBeforeErrorDetailLockPreventsLateErrorDetail`
- `rollbackWaitsForLockedErrorDetailAndPreservesCommittedDetail`

### Suspicious Tests

没有发现会直接导致假绿的测试。500ms 负向等待不是数学上的确定性证明，但它之前有 query-entered 正向边界，之后还有真实取锁事件顺序和数据库终态，因此作为辅助断言可接受。

### Missing Tests

本整改口径内无阻断缺测。若发布流程要求 commit-scoped attestation，需在最终提交上重跑现有门禁，而不是新增测试逻辑。

## 10. Release Concerns

- Coverage: High
- Inspected evidence: `ec4ca30..bd1db49` 直接父子单提交、10 个变更文件、tracked/index clean、`git diff --check`、当前源码 9 模块 package、前端 type-check/build、323/323 XML。
- Exclusions / limits: 未独立执行 clean verify；现有动态 XML不是最终测试源码的严格字节级证明。

本轮独立执行：

- 后端 `mvn -B -ntp -DskipTests package`：9 模块 BUILD SUCCESS。
- 前端 `npm --prefix frontend run type-check`：PASS。
- 前端 `npm --prefix frontend run build`：PASS，仅保留既有大 chunk 警告。
- `git diff --check ec4ca30..bd1db49`：PASS。

开发者 XML 经独立聚合为 Surefire 149/149 + Failsafe 174/174 = 323/323，0 failure/error/skip；Phase 10 为 13/13，日志使用 MySQL 8 和隔离 schema，并验证 32 个版本化迁移至 V32及测试 repeatable。它们是强行为证据，但不是最终 commit 的严格同源证明。

## 11. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: 增量配置差异、Spring bean、MyBatis mapper、事务管理器。
- Exclusions / limits: 未检查部署环境外部数据库参数。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| SchemaValidation | 0 | 无 DDL/配置变化 | 无 |
| UnsafeDefault | 0 | 无新增默认值 | 无 |
| EnvironmentSeparation | 0 | `ExchangeImportHook` | 生产实现保持空操作 |
| SecretConfig | 0 | 无 | 无 |
| FeatureFlag | 0 | 无 | 无 |
| ConfigDocs | 0 | Phase 10 并发/投影约束 | 保持 |

## 12. Data Integrity Analysis

- Coverage: High
- Inspected evidence: batch/ref/business 锁顺序、`REQUIRES_NEW`、rollback 补偿、CAS、逻辑删除和唯一约束。
- Exclusions / limits: `PARTIAL_ROLLBACK` 自动重试不在本轮范围。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 0 | 行写/错误明细与状态锁同事务 | 保持 |
| Idempotency | 0 | 双 confirm 原子认领 | 保持 |
| ConcurrencyConsistency | 0 | confirm/rollback 同 batch 锁 | 保持 |
| MigrationSafety | 0 | 本轮无迁移 | 无 |
| InvariantValidation | 0 | 非 `IMPORTING` 在写前停止 | 保持 |
| BackupRestore | 0 | 不在本增量范围 | 无 |
| Reconciliation | 0 | 收尾 CAS 读取真实终态 | 保持 |

## 13. Concurrency Analysis

- Coverage: High
- Inspected evidence: InnoDB 主键行锁、两个 Mapper 查询、锁顺序、T-IMP-5C 双向控制流、MyBatis query 探针和持久终态。
- Exclusions / limits: 未重跑并发、压力、故障注入或破坏性测试。

线性化协议保持成立：

1. rollback 先取得 batch 锁时，错误明细事务随后观察到非 `IMPORTING`，在写入前停止。
2. 错误明细先取得锁时，rollback 必须等待其提交，再取得同一 batch 锁并落回滚终态。
3. 逐行业务事务与 rollback 仍按相同 batch-first 顺序竞争，不会因 status-only 投影改变 InnoDB 行锁语义。
4. rollback 单次完整实体读取保留，补偿所需元数据没有被性能优化裁掉。

## 14. Principles Compliance

### Principles Violated

None in this incremental scope.

### Principles Respected

- Fail-Fast：非 `IMPORTING` 在任何业务/错误明细写入前终止。
- Test Behavior, Not Implementation：静态 Mapper 契约同时由真实 HTTP/MySQL 行为与持久终态验证。
- Unbounded Resources Must Not Grow Forever：逐行状态锁返回固定大小，不再随整批预览增长。
- Transaction Boundary：状态锁与对应写入处于同一 `REQUIRES_NEW`；rollback 补偿与终态同事务。
- Consistent Lock Ordering：confirm/error-detail/rollback 均从 batch 锁进入。

## 15. Recommended Fix Order

### Fix Immediately

无。Phase 42 本轮整改已满足放行口径。

### Fix Before Stable Release

- 若最终发布要求严格提交级动态证明，由用户在 `bd1db49` 或其治理后继提交上自行重跑 clean verify；本轮不会代为执行并发/latch 或可能触发安全限制的验证。

### Schedule Later

- 先按统一计划整改 Phase 39，再依次处理 Phase 41、47、53、44、0。
- `PARTIAL_ROLLBACK` 自动重试若要改变，需单独补规格、幂等设计与独立反例。

### Ignore for Now

- 500ms 负向等待的极低调度窗口；现有正向锁事件与数据库终态已提供互补证据。

## 16. Quick Wins

- 将本报告并入 `CURRENT-EXECUTION-PLAN.md`，把 Phase 42 更新为独立 PASS。
- 下一轮只冻结 Phase 39 的整改提交，避免把其他阶段或最终全量审计混入同一增量。
