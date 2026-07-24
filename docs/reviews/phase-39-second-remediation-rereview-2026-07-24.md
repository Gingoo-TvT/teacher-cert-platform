# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 39 second remediation
**Audit mode:** incremental + security / stability / performance / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-24
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结第一轮复核结论 `1a69c70e4de292b111abe9e23d634c3fb7022907` 至第二轮提交材料 `dbd8633cd57bbf30e3ecf40885c9e459f70a0a5d`，其中生产/测试代码冻结点为 `73406edb6c71bc2b0965c53967deb805d66b09e7`。严格增量共 2 个线性提交、13 个文件、`+1020/-73`；代码提交之后只有治理与规格文档变化，没有 Java 漂移。

正式结论为 **PASS**：

- 上一轮 High 已关闭。rollback 在锁定 batch 与完整 ref 集后，从 student、training_profile、certificate 的全部 UPDATE `before_json` 提取恢复目标学院，对有效 ID 去重升序加锁；非法、缺失或已删除父级只形成对应 ref 冲突，不再写入无效 `college_id`。
- 固定顺序为 `batch → refs → college IDs ascending → business child`。父锁通过 `MANDATORY` 加入外层 rollback 事务并保持到提交；删除侧在同一学院父锁后直接统计 student、training_profile 与 certificate，两个方向没有发现新锁序环。
- 上一轮 Testing Authenticity Low 已关闭。竞争方信号从 Mapper 前的业务 Hook 推进到测试专用 `StatementHandler.query` 探针，并同时精确匹配固定父锁 SQL 与目标 collegeId；原 6 个在线交错和新增 5 个历史恢复场景均保留最终数据库状态与孤儿断言。
- 本增量未发现新增或被加重的 Critical、High、Medium、Low finding。Phase 39 可关闭并放行 Phase 41；全项目仍因 Phase 0、41、44、47、53 五个既有退回阶段保持 **CHANGES_REQUESTED**。

本报告保留三个非阻断、范围外的全量审计复查点：普通 training 写入与学生/学院删除的复合交错、有效父级下按 ref 独立部分补偿可能形成跨学院业务图、以及父集合不相交的跨 batch child 锁顺序。这三项在 `1a69c70` 基线已经存在，`1a69c70..73406ed` 没有新增或加重；按 incremental-audit 规则不计入本轮 finding，也不用于重开上一轮 1 High / 1 Low。

### Score Dashboard

```text
Security        █████████░  9.4  S   无权限、敏感输出或新攻击面变化
Stability       █████████░  9.1  S   非法历史父级按 ref 收敛，不污染外层事务
Performance     █████████░  9.0  S   目标父 ID 去重升序，新增删除计数均有索引
Testing         █████████░  9.1  S   真实 MySQL 11/11；query/ID 探针与终态互证
Maintainability █████████░  9.0  S   父锁与冲突规划集中，职责清晰
Design          █████████░  9.1  S   补偿路径纳入同一父级线性化协议
Release         █████████░  9.2  S   同源时间链完整，一次性构建门禁通过
Configuration   █████████░  9.5  S   无新增配置、密钥、DDL 或环境分叉
Data Integrity  █████████░  9.3  S   上轮可确定孤儿恢复路径已关闭
Concurrency     █████████░  9.1  S   父锁全量前置且顺序固定
─────────────────────────────────────
Overall         █████████░  9.2  S
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

本轮生产变化集中在两个事务边界：

- `platform-exchange`：rollback 锁 batch 与 refs 后，生成恢复父级计划，统一锁学院，再进入 student/training/certificate 子行锁与逆序补偿。
- `platform-system`：`CollegeParentGuard` 提供可空的父级状态锁；学院删除在父锁后补充 training_profile 与 certificate 的活跃记录计数。
- `platform-boot` 测试：`Phase39CollegeIntegrityIT` 通过真实 Spring 事务、MyBatis Mapper 与 MySQL 8 覆盖在线子写、历史 rollback、学院删除和多父锁序。
- 治理文档：保持候选与正式结论分离，并在本报告前未提前放行 Phase 41。

本轮无 DDL/Flyway、对外 API、权限点、角色矩阵、状态集合、前端生产代码、依赖或运行配置变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | Medium | rollback 权限/批次归属调用链、错误信息、生产 Hook、增量 API 面 | 按用户要求未执行扫描、攻击性请求、恶意载荷、fuzz 或凭据尝试 |
| Stability | High | 外层事务、`MANDATORY` 传播、JSON 失败关闭、部分冲突、删除计数 | 未启动应用或执行故障注入 |
| Performance | High | ref 遍历、父 ID 去重/排序、锁 SQL 投影、删除计数索引 | 未执行压力或万行基准 |
| Testing Authenticity | High | `Phase39CollegeIntegrityIT` 全文件、MyBatis 探针、XML、源码/class/XML/提交时序 | 未独立重跑并发/latch；query 信号仍先于 `invocation.proceed()` |
| Release | High | 提交拓扑、blob 同源、334/334 XML、后端 package、前端 type-check/build、diff check | 未独立 clean verify；未复验开发者的容器/端口清理声明 |
| Configuration | High | 全增量配置/依赖/迁移差异、Spring bean 与事务配置 | 未读取部署环境外部配置 |
| Data Integrity | High | 三类历史快照、父状态、冲突隔离、软删与最终孤儿断言 | 未读写用户真实业务数据 |
| Concurrency | High | batch/ref/parent/child 锁序、双向交错、MySQL RR 可见性 | 未执行压力、破坏性交错或故障注入 |
| Maintainability | Medium | 13 个增量文件及直接调用方 | 未重开全仓结构审计 |
| Design | High | 无 FK 模式下的父状态协议与补偿边界 | 基线既有的全库其他 `college_id` 写入口留最终全量审计 |

## 3. Incremental Change Summary

### Change Summary

- Total files changed: 13
- Lines added: 1020
- Lines deleted: 73
- Commits in range: 2
- Authors: `wenbibuhaoqwq <wenbibuhaoqwq@localhost>`
- Production/test code commit: `73406ed`
- Documentation submission commit: `dbd8633`

### Change Categories

| Category | Files | Summary |
|----------|-------|---------|
| Bug fixes | 4 | rollback 父级预锁、可空状态锁、删除直接计数 |
| Tests | 1 | Phase 39 从 6 项扩展为 11 项，改用 query-entered 探针 |
| Documentation | 8 | 提交材料、规格、进度、交接与统一计划 |
| Dependencies / Configuration / Migration | 0 | 无变化 |

### Risk Delta

- New risks introduced: 0
- Existing risks fixed: 2（上一轮 1 High / 1 Low）
- Existing risks made worse: 0

### Test Coverage Delta

- New code with tests: 4 个生产文件由 1 个真实 MySQL IT 文件覆盖
- New code without tests: 0
- Deleted tests: 0
- `Phase39CollegeIntegrityIT`: 6 → 11
- Full XML: 329 → 334

### Approval Recommendation

**Approve / PASS。** 上一轮 1 High / 1 Low 均按原问题口径关闭，未发现直接回归。Phase 39 可更新为“✅ 独立复核 PASS”，下一整改阶段为 Phase 41。

## 4. Top Risks

本增量没有发布阻断风险。以下是证据或范围边界，不是新 finding：

1. `StatementHandler.query` 探针在 `invocation.proceed()` 前发信号，`countDown → proceed` 之间仍有极小调度窗口；精确 SQL/ID、胜方真实取锁、释放后的业务结果和持久终态提供互补证据。
2. XML、源码/class 时间和 Git blob 建立了强同源链，但不是带源码哈希的可复现构建证明。
3. “全新无卷依赖、容器自动移除、端口/进程清理”属于开发者过程声明，本轮没有调用 Docker 或启动服务独立复验。
4. 基线既有的普通 training 写入复合交错、按 ref 部分补偿图一致性、跨 batch child 锁序留最终全量审计；本增量没有修改或加重这些路径。

## 5. Detailed Findings

**None.** 本轮没有新增或被加重的 Critical / High / Medium / Low finding。

### Previous Finding Closure

| Previous finding | Previous severity | Closure evidence | Result |
|------------------|-------------------|------------------|--------|
| 历史 UPDATE ref 可恢复到已删除学院 | High | 三类 `before_json.collegeId` 预解析、`distinct().sorted()` 父锁、缺失父级按 ref 冲突、删除侧 direct count、双向 MySQL 终态 | Closed |
| contender 信号位于 JDBC 查询之前 | Low | 精确 status-only SQL + collegeId 的 `StatementHandler.query` 单次探针、胜方真实取锁、持久终态/孤儿断言 | Closed |

## 6. Security Concerns

- Coverage: Medium
- Inspected evidence: rollback controller/service 权限、batch 可访问性、冲突消息、生产 Hook 和增量外部接口。
- Exclusions / limits: 未执行漏洞扫描、恶意载荷、攻击性请求、fuzz、凭据尝试或任何可能属于 cyber 的操作。

本增量没有新增端点、权限点、敏感字段输出、外部命令、动态脚本或配置密钥。未发现安全 finding。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: rollback 外层事务、父锁传播、非法 JSON/ID 失败关闭、逐 ref 冲突隔离、学院删除分支。
- Exclusions / limits: 未运行故障注入、进程中断或常驻服务。

`lockStatusForUpdate` 对缺失/已删除父级返回 `null`，避免先抛事务异常再捕获造成 rollback-only；其他有效 ref 可以继续补偿并诚实落为 `ROLLED_BACK` 或 `PARTIAL_ROLLBACK`。未发现新的崩溃、资源泄漏或异常吞噬路径。

## 8. Performance Concerns

- Coverage: High
- Inspected evidence: ref 规模、父 ID 集合、锁 SQL 投影、training/certificate 索引与删除查询次数。
- Exclusions / limits: 未执行压力、容量或万行基准。

父目标只建立 O(N) 映射并去重排序，学院数通常远小于 ref 数；父锁仍只投影固定大小的 `status`。新增两个删除计数均命中既有 `college_id` 索引。未发现新的渐进性能问题。

## 9. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 11 个 Phase 39 IT、SQL 探针、受控 Hook、线程清理、Failsafe XML、MySQL 日志及源码/class/XML/提交时间链。
- Exclusions / limits: 遵守用户安全边界，未独立重跑并发/latch；未启动 MySQL、Redis、MinIO 或应用服务。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 已删除历史父级 fail-closed | High | 三类 ref 与最终孤儿数均直接断言 | Keep |
| rollback 先于 delete | High | 父锁、部分补偿、删除拒绝和最终学院分布互证 | Keep |
| delete 先于 rollback | High | query 边界、三 ref 冲突和无孤儿互证 | Keep |
| 多父锁序 | High | `A2/A1/A2` 去重升序且父锁早于首 child query | Keep |
| 原 6 个在线交错 | High | 精确 query/ID 探针与父子终态共同验证 | Keep |
| 数学意义的 InnoDB wait 观测 | Medium | `countDown → proceed` 有极小调度窗口 | 接受为证据限制 |

### Valuable Tests

- `rollbackToDeletedHistoricalCollegeFailsClosedForAllThreeUpdateRefs`
- `rollbackLocksHistoricalCollegeFirstThenDeleteRejectsTrainingAndCertificateChildren`
- `rollbackLocksHistoricalCollegeFirstThenDeleteRejectsCertificateOnlyChild`
- `rollbackLocksDistinctHistoricalParentsInAscendingOrderBeforeAnyChildQuery`
- `collegeDeleteLocksHistoricalCollegeFirstThenRollbackConflictsWithoutOrphans`
- 原专业、STAFF 用户、无自动账号学生的 6 个双向交错

### Suspicious Tests

没有发现可导致本轮假绿的 mock、空断言或只验证实现细节的新增测试。`Future.isDone=false` 只作为辅助负向断言，核心结论同时依赖正向 query 屏障和持久终态。

### Missing Tests

本轮原问题口径内无阻断缺测。普通业务写入口的全库父引用协议和跨 batch 补偿顺序属于最终全量审计范围。

## 10. Release Concerns

- Coverage: High
- Inspected evidence: `1a69c70..dbd8633` 提交拓扑、13 个文件、源码 blob、334/334 XML、当前源码 package、前端 type-check/build 与 diff check。
- Exclusions / limits: 未独立 clean verify；未复验临时容器、进程与端口清理。

独立一次性门禁：

```text
mvn -B -ntp -DskipTests package        BUILD SUCCESS（9 modules）
npm --prefix frontend run type-check   PASS
npm --prefix frontend run build        PASS（仅既有 >900 kB chunk 警告）
git diff --check 1a69c70..dbd8633      PASS
```

开发者 XML 经独立解析为 Surefire **149/149** + Failsafe **185/185** = **334/334**，0 failure/error/skip；Phase 39 **11/11**，Phase 10 **13/13**。两个目标 XML 均记录 `jdbc:mysql://127.0.0.1:33397/teacher_cert ... (MySQL 8.0)`。5 个生产/测试源 blob 与 `73406ed` 完全一致；源码早于 class、XML 早于代码提交，提交后只有文档变化。

## 11. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: 全增量配置、依赖、迁移、Spring bean 与事务传播。
- Exclusions / limits: 未读取部署环境外部配置。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| SchemaValidation | 0 | 无 DDL/Flyway 变化 | 无 |
| UnsafeDefault | 0 | 无新增默认值 | 无 |
| EnvironmentSeparation | 0 | 测试专用 `@TestConfiguration` | 保持测试上下文隔离 |
| SecretConfig | 0 | 无 | 无 |
| FeatureFlag | 0 | 无 | 无 |
| ConfigDocs | 0 | Phase 1/10 锁契约 | 已同步 |

## 12. Data Integrity Analysis

- Coverage: High
- Inspected evidence: 历史 before/after 快照、三类父引用、逻辑删除、直接计数、冲突状态与孤儿断言。
- Exclusions / limits: 未读取或修改用户真实业务数据；基线既有全库引用协议留最终全量审计。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 0 | 父锁与补偿同一事务 | 保持 |
| Idempotency | 0 | 本轮未改既有 partial retry 语义 | 保持范围说明 |
| ConcurrencyConsistency | 0 | rollback/delete 共用学院父锁 | 保持 |
| MigrationSafety | 0 | 本轮无迁移 | 无 |
| InvariantValidation | 0 | 无效历史父级写回 | 已 fail closed |
| BackupRestore | 0 | 不在本增量范围 | Phase 41 独立整改 |
| Reconciliation | 0 | 本轮无新对账逻辑 | 最终全量审计复查存量债 |

## 13. Concurrency Analysis

- Coverage: High
- Inspected evidence: batch/ref/parent/child 获取顺序、`FOR UPDATE` SQL、事务传播、删除计数与 11 个确定性交错。
- Exclusions / limits: 未动态重跑并发、压力、故障注入或破坏性交错。

两个目标方向成立：

1. rollback 先取得历史学院父锁时，delete 等待；rollback 提交有效补偿后，delete 的后续计数能看到 training/certificate 并拒绝删除。
2. delete 先取得历史学院父锁并提交时，rollback 的锁定读看到父级已失效，三类 ref 均只记冲突，不进入业务子行写回。

多父目标统一 `distinct().sorted()`，且测试事件轨迹证明全部有效父锁返回早于首个业务 child 查询。未发现本增量引入的死锁环。

## 14. Principles Compliance

### Principles Violated

None in this incremental scope.

### Principles Respected

- Validate Externalized Invariants：持久 `before_json` 不再被视为天然可信。
- Fail Closed：非法、缺失、非正数、溢出或已删除目标都禁止恢复。
- Consistent Lock Ordering：`batch → refs → college IDs ascending → business child`。
- Explicit Transaction Boundary：父锁以 `MANDATORY` 加入 rollback 外层事务。
- Test Behavior, Not Scheduling：query/ID 正向边界与最终数据库状态共同验证。
- Preserve Evidence：候选材料没有提前宣告 PASS，首次环境失败和证据限制均保留。

## 15. Recommended Fix Order

### Fix Immediately

无。Phase 39 第二轮整改满足放行口径。

### Fix Before Stable Release

- 按统一计划继续 Phase 41 → 47 → 53 → 44 → 0；全部退回项关闭后再执行用户要求的全量审计。
- 若发布流程要求严格 commit-scoped 动态证明，由用户在最终提交上自行重跑 clean verify；本轮不会代为执行并发/latch 或可能触发安全限制的操作。

### Schedule Later

- 最终全量审计复查普通 training 写入与学生/学院删除的复合交错。
- 由规格先明确有效父级下部分 rollback 是否必须保持 student/training/certificate 同学院，再决定是否按导入行/学生聚合补偿。
- 复查父集合不相交的跨 batch rollback 是否需要全局 child 预锁顺序。

### Ignore for Now

- `countDown → invocation.proceed()` 的极小调度窗口；现有精确 query/ID 边界、真实胜方锁和持久终态已提供互补证据。

## 16. Quick Wins

- 将 Phase 39 更新为独立复核 PASS，并把下一整改入口切换到 Phase 41。
- 在最终全量审计清单中保留三个基线债务复查点，避免把本轮增量 PASS 误解为“全库所有 college_id 路径已审完”。
