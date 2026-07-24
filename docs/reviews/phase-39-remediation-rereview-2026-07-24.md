# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 39 PG-H1 remediation
**Audit mode:** incremental + security / stability / performance / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-24
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结 `66fd2a9c1ab20a505deaf55d029c5059974f6e93..34aeec705aa51acb47b5d0c0b1dd9f042b89ce60`，共 21 个变更文件、`+1094/-41`。候选新增的 `CollegeParentGuard`、学院删除首读父锁、学生直接计数，以及专业、STAFF 用户、学生单条/批量/迁移和当前标准导入写入的父锁接线，能够关闭这些在线路径原有的 check-then-delete 窗口；固定的 `RBAC → college（多学院升序）→ child` 顺序也未发现新的生产死锁。

但是 Phase 10 持久化的历史 `import_record_ref` 仍可由当前 rollback 重放。旧版本曾允许导入把既有学生从学院 A 更新到学院 B，并持久保存 `before_json.collegeId=A`、`after_json.collegeId=B`；当前 rollback 只锁 batch、ref 和学生行，在快照一致时直接把 `before_json` 更新回数据库，既不锁 A，也不校验 A 仍存在。A 在中间被合法软删后，回滚会把活跃学生恢复为指向已删除学院的孤儿。该路径本质上仍是 PG-H1 所要求覆盖的“移动学生”入口，因此原 High 不能关闭。

正式结论为 **CHANGES_REQUESTED（1 High，1 Low）**。High 是持久数据完整性阻断项；Low 是新并发 IT 在 contender 的 `beforeLock` 回调处发信号，该位置仍早于 JDBC 查询，不能确定证明线程已经进入真实锁等待。Phase 39 保持复核退回，Phase 41 不放行，全项目继续为 **CHANGES REQUESTED**。

### Score Dashboard

```text
Security        █████████░  9.2  S   无权限/敏感数据增量；按用户要求未做攻击性动态验证
Stability       ███████░░░  7.0  B   在线写锁协议成立，但历史回滚可恢复非法父引用
Performance     █████████░  8.8  A   父锁为固定投影，批量父 ID 去重升序，未见新增渐进退化
Testing         ████████░░  7.6  B   真实 MySQL 终态断言有价值，contender 信号尚未到 query 边界
Maintainability ████████░░  8.4  A   父锁集中封装；rollback 的通用恢复器缺少父引用策略
Design          ███████░░░  7.1  B   在线协议清晰，但补偿写未纳入同一不变量
Release         ██████░░░░  6.2  B   一次性构建门禁通过，High 阻断阶段放行
Data Integrity  ██████░░░░  5.9  B   可确定产生活跃 student → deleted college 孤儿
Concurrency     ███████░░░  6.9  B   主路径锁序正确；rollback 与学院删除没有共同线性化点
─────────────────────────────────────
Overall         ███████░░░  7.4  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 1 | 1 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **2** | **2** | **0** |

## 2. Project Map

本增量的生产变更横跨四个写入口：

- `platform-system`：集中父锁、学院删除、专业新增/迁移。
- `platform-security`：STAFF 用户新增/迁移，在既有 RBAC 锁后取得学院锁。
- `platform-business`：学生单条/批量新增与迁移；批量按学院 ID 去重升序预锁。
- `platform-exchange`：当前标准导入的直接学生写入在 batch 锁后取得目标学院锁。

关键持久边界是 `sys_college`、`sys_major`、`sys_user`、`student`、`import_export_batch` 和 `import_record_ref`。项目既定不使用数据库外键，因此应用层的所有创建、移动、补偿和恢复入口共同构成唯一引用完整性边界。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | Medium | 权限入口、数据范围先后顺序、生产 hook、审计边界 | 未执行漏洞扫描、恶意请求、fuzz、凭据或攻击性探测 |
| Stability | High | 四模块生产控制流、事务传播、逻辑删除、历史 rollback | 未启动应用或依赖服务 |
| Performance | Medium | 锁 SQL 投影、批量父 ID 排序、循环调用形态 | 未执行压测或容量测试 |
| Testing Authenticity | High | `Phase39CollegeIntegrityIT`、hook 位置、Failsafe XML、class/source/commit 时序 | 按安全边界未独立重跑并发/latch |
| Release | High | 冻结提交、329/329 XML、后端 package、前端 type-check/build、diff check | 未独立 clean verify；未复验临时容器清理声明 |
| Configuration | High | 本增量无配置键、profile、密钥或 Compose 变化 | 未读取部署环境外部配置 |
| Data Integrity | High | 父锁、直接计数、历史 ref 快照、rollback 写回、软删语义 | 未修改或探测真实业务数据 |
| Concurrency | High | MySQL RR 可见性、全部当前写路径、rollback 锁序、测试 happens-before | 未执行压力、故障注入或破坏性交错 |
| Maintainability | Medium | 21 个增量文件及直接调用方 | 未重开全仓结构审计 |
| Design | Medium | 无 FK 模式下的父状态协议和补偿边界 | `DemoDataInitializer` 归 Phase 53 既有发布问题 |

## 3. Top Risks

1. **High — 历史跨学院导入批次可经 rollback 恢复到已删除学院。** 这是无需并发即可触发的持久数据完整性缺陷，并存在与学院删除交错的同类竞态。
2. **Low — contender 测试信号早于真实查询边界。** 6 个测试的最终数据库断言仍有价值，但“释放胜方前 contender 正在等待行锁”的证据可被线程调度替代。

## 4. Detailed Findings

### Finding: 历史导入 rollback 可绕过学院父锁并恢复孤儿学生

- Severity: High
- Confidence: High
- Category: Data Integrity / Concurrency
- Status: Confirmed
- Affected area: Phase 10 rollback 与 Phase 39 学院父子完整性
- Evidence:
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:314-359`
  - Function / Module: `rollback`
  - Relevant behavior: rollback 先锁 batch 和 refs，随后逐项补偿；没有预取或锁定 UPDATE 快照中的目标学院。
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:866-907`
  - Function / Module: `rollbackOne` / `rollbackEntity`
  - Relevant behavior: student 分支只对学生行 `FOR UPDATE`；当当前快照等于 `after_json` 时，直接反序列化 `before_json` 并 `updateById`。
  - File: `platform-boot/src/main/resources/db/migration/V17__exchange.sql:49-57`
  - Function / Module: `import_record_ref`
  - Relevant behavior: `before_json` / `after_json` 是持久 JSON，没有版本或过期边界。
  - Historical commit: `7df9bbd0de1efb502f237545a4474f6ce1ac5b5d`（`e868997` 的父提交）
  - Relevant behavior: 旧 `applyStudent` 对既有学生也无条件执行 `student.setCollegeId(collegeId)`，因此可合法产生 before=A、after=B 的跨学院 UPDATE ref；后续修复只阻止新 ref，不会清除旧 ref。
- Problem: 当前 Phase 39 只把在线新增/迁移接入学院父锁，补偿式迁移仍可在没有父锁和存在性校验的情况下写回历史学院 ID。
- Why it matters: 项目不使用数据库外键；一旦 rollback 写回已删除学院，数据库没有第二道防线。列表、数据范围、统计和后续审核会面对一个仍活跃但父组织不可见的学生。
- Realistic failure scenario:
  1. 旧批次把一个未绑定 `sys_user` 的学生 S 从学院 A 导入更新到学院 B，并保存 before=A / after=B。
  2. A 此后没有活跃专业、用户或学生，`deleteCollege(A)` 合法成功。
  3. 当前版本回滚旧批次，发现 S 当前快照仍等于 after=B，于是直接写回 before=A。
  4. S 仍为活跃记录，但 A 已 `deleted=1`。
- Concurrent variant: 删除事务先锁 A，并由随后的普通 count 建立 RR 一致性读视图；rollback 在不锁 A 的情况下把 S 从 B 写回 A。删除事务之后的 student count 仍可基于旧视图看到 0 并删除 A。
- Minimal fix: rollback 在锁定 batch/ref 后、取得任何业务子行锁之前，解析所有 UPDATE ref 的 `before_json`；对其中所有带 `collegeId` 的 student/training/certificate 目标学院去重并按 ID 升序取得父锁。目标学院不存在或已删除时必须 fail closed，或把该 ref 记为明确冲突，禁止恢复。固定顺序应为 `batch → refs → college IDs ascending → business child`。
- Better long-term fix: 把“恢复快照”从无类型通用 `updater.apply(before)` 改成按实体定义的补偿策略；每种策略显式声明父引用、状态与唯一性不变量，并在写回前统一预检。
- Regression test suggestion:
  - 直接构造历史合法 ref：student before=A、after=B；删除 A 后 rollback，断言不得产生孤儿，并断言批次终态/冲突信息诚实。
  - 真实 MySQL 覆盖 rollback 先取得父锁和 deleteCollege 先取得父锁两个方向；无论顺序都断言活跃 `student LEFT JOIN sys_college` 孤儿数为 0。
  - 若 training/certificate 的 `before_json` 含学院 ID，同样参数化覆盖，避免只修 student。
- Estimated effort: 4–8 小时

### Finding: Phase39 contender 信号发生在 JDBC 锁查询之前

- Severity: Low
- Confidence: High
- Category: Testing Authenticity
- Status: Confirmed
- Affected area: `Phase39CollegeIntegrityIT` 的 6 个双向交错
- Evidence:
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase39CollegeIntegrityIT.java:654-663`
  - Function / Module: `ControlledCollegeParentLockHook.beforeLock`
  - Relevant behavior: contender 在调用 mapper 之前就递增事件并释放 `contenderBeforeLock`。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase39CollegeIntegrityIT.java:665-678`
  - Function / Module: `afterLock`
  - Relevant behavior: winner 的信号位于锁查询返回后，确实证明胜方已取得锁。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase39CollegeIntegrityIT.java:214-222,252-260`
  - Relevant behavior: 主线程收到 contender 的 pre-query 信号后，只检查 Future 尚未完成就释放 winner。
- Problem: contender 可能只执行到 Java hook，尚未进入 MyBatis/JDBC 查询。此时 `Future.isDone()==false` 也可仅由线程调度解释，不能确定证明它正因 InnoDB 父行锁而等待。
- Why it matters: 测试的最终父子计数和 `orphan=0` 仍能验证两种操作顺序，但对“共享真实父行锁就是阻塞原因”的证据强度低于提交材料声明。
- Realistic failure scenario: 后续重构意外把父锁查询移到子写之后；测试线程在 `beforeLock` 后暂未被调度，主线程释放 winner，随后 contender 顺序执行并得到预期终态，6 个测试仍可能通过。
- Minimal fix: 在 MyBatis `StatementHandler.query` 或等价 JDBC execute 边界捕获 `SysCollegeMapper.selectStatusForUpdate` 的 contender 查询已进入信号；主线程只有收到该信号后才检查未完成并释放 winner。
- Better long-term fix: 提取复用 Phase 42 已采用的 query-entered SQL 探针，并让锁协议测试同时断言 mapper ID、SQL 投影、查询进入、锁取得和最终数据库状态。
- Regression test suggestion: 对三类子记录的两个方向保留终态断言；至少每个方向有一条用例在释放 winner 前证明 contender 已到真实 query 边界。
- Estimated effort: 2–4 小时

## 5. Security Concerns

- Coverage: Medium
- Inspected evidence: 数据范围校验在父锁前完成、STAFF/学生 RBAC 锁顺序、controller/service 既有权限边界、生产 hook 默认空操作。
- Exclusions / limits: 未执行漏洞扫描、攻击性请求、恶意载荷、fuzz、凭据尝试或任何可能属于 cyber 的动作。

本增量没有改变权限点、认证、敏感字段、预签名访问或审计模型。没有发现新的安全 finding。High finding 是合法业务历史数据和合法 rollback 的完整性问题，不依赖越权或恶意输入。

## 6. Stability Concerns

- Coverage: High
- Inspected evidence: 学院删除、专业/用户/学生创建与迁移、学生批量、当前标准导入、历史 rollback、事务传播。
- Exclusions / limits: 未启动应用、未执行运行期故障注入。

当前在线写路径的 `MANDATORY` 父锁属于调用方事务，能够保持到子写/删除提交；删除的第一条数据库读取也是锁定读，规避了等待后才建立旧快照的原问题。稳定性缺口仅来自 Finding 1 的补偿入口。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: `SELECT status ... FOR UPDATE`、学生批量去重升序预锁、各写入口调用次数。
- Exclusions / limits: 未执行压测、批量基准或资源压力。

父锁仅投影固定大小的 `status`，学生批量先去重再锁定，没有发现新的 O(N²) 或无界资源问题。High 修复应一次性预解析 refs 并去重排序学院 ID，禁止逐 ref 重复锁同一父行。

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: `Phase39CollegeIntegrityIT` 全文件、生产/测试 hook、Surefire/Failsafe XML、编译产物与提交时间线。
- Exclusions / limits: 遵守用户安全边界，未独立重跑并发/latch；XML 不能独立证明临时依赖从全新状态启动或无挂载卷。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 专业双向交错 | Medium | 终态真实，但 contender 未证明已进入 JDBC 等锁 | Strengthen probe |
| STAFF 用户双向交错 | Medium | 真实服务/事务/DB，存在同一调度窗口 | Strengthen probe |
| 无账号学生双向交错 | Medium | 直接计数与无账号断言有价值，存在同一调度窗口 | Strengthen probe |
| 当前在线父锁接线 | High | 静态控制流、事务注解和 mapper SQL 相互印证 | Keep |
| 历史 rollback | None | 现有 6 个测试没有构造持久历史 ref | Add blocking regression |
| 全量回归 | High | XML 聚合 329/329，0 failure/error/skip | Keep, rerun after fix |

### Valuable Tests

- 三类子记录分别覆盖“子写先赢”和“删除先赢”，没有把多个方向塞进一个测试。
- 每场景都检查父/子有效数及 `LEFT JOIN` 孤儿数；学生场景显式验证 `autoCreateAccount=false` 和没有生成 `sys_user`。
- winner 的 `afterLock` 位于真实 mapper 返回之后；线程池、Future 和 latch 有失败清理。

### Suspicious Tests

Finding 2 所述 contender 信号只能证明 Java 调用已到 mapper 之前，不能证明 JDBC 查询已执行或正在等锁。`Future.isDone()` 是辅助负向证据，不能替代 query-entered 正向事件。

### Missing Tests

- 历史跨学院 UPDATE ref 在目标学院已删除时的 rollback。
- rollback 与 deleteCollege 针对 `before_json.collegeId` 的双向真实 MySQL 交错。
- 业务子行锁之前的全部目标学院升序预锁及缺失父级 fail-closed。

## 9. Release Concerns

- Coverage: High
- Inspected evidence: 冻结范围、Git 状态、`git diff --check`、开发者 XML、独立后端 package、前端 type-check/build。
- Exclusions / limits: 未独立执行 clean verify；未启动 MySQL/Redis/MinIO 或常驻后端/前端；未做部署、回滚或演练。

独立一次性门禁：

```text
mvn -B -ntp -DskipTests package        BUILD SUCCESS（9 modules）
npm --prefix frontend run type-check   PASS
npm --prefix frontend run build        PASS（仅既有 >900 kB chunk 警告）
git diff --check 66fd2a9..34aeec7      PASS
```

开发者测试 XML 聚合为 Surefire **149/149** + Failsafe **180/180** = **329/329**，0 failure/error/skip；`Phase39CollegeIntegrityIT` **6/6**，数据库 URL 指向 MySQL 8。11 个变更 Java 源与当前候选提交一致，XML/编译产物时间晚于源文件且早于提交。该证据支持候选曾完成全量门禁，但 Finding 1 仍阻断发布和阶段 PASS。

## 10. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: 21 个增量文件、Spring bean 接线、事务传播、测试 hook。
- Exclusions / limits: 未读取环境外部参数或连接生产设施。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| SchemaValidation | 0 | 无 DDL/Flyway 变化 | 无 |
| UnsafeDefault | 0 | 无新增配置 | 无 |
| EnvironmentSeparation | 0 | `CollegeParentLockHook` | 生产实现保持空操作 |
| SecretConfig | 0 | 无 | 无 |
| FeatureFlag | 0 | 无 | 无 |
| ConfigDocs | 0 | Phase 1/2/3/10 文档 | 修复后同步 rollback 父锁契约 |

`DemoDataInitializer` 的直接 SQL 写入是默认关闭的 dev/demo 初始化路径，生产 profile 由既有 `RuntimeProfileGuard` 禁止。本轮不把它重复计为新 finding；它继续属于 Phase 53 既有 demo/发布风险，不能用来证明生产在线父锁闭环。

## 11. Data Integrity Analysis

- Coverage: High
- Inspected evidence: 父行锁、逻辑删除、三类计数、持久快照、通用 rollback、历史实现。
- Exclusions / limits: 未对用户真实数据做任何写入或探测。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 0 | 当前在线子写与父锁同事务 | 保持 |
| Idempotency | 0 | 本轮无新幂等问题 | 保持 |
| ConcurrencyConsistency | 1 High | rollback 与学院删除无共同父锁 | 预锁 before target |
| MigrationSafety | 0 | 无迁移 | 无 |
| InvariantValidation | 1 High | `before_json.collegeId` 未验证存在 | fail closed / conflict |
| BackupRestore | 0 | 不在本增量范围 | 无 |
| Reconciliation | 0 | 孤儿巡检为后续 P1-9 | 不替代写前约束 |

## 12. Concurrency Analysis

- Coverage: High
- Inspected evidence: `SELECT ... FOR UPDATE`、Spring 事务传播、MySQL RR 读视图、RBAC/college/child 顺序、batch/ref/child 顺序。
- Exclusions / limits: 未重跑并发、故障注入、压力或破坏性交错。

当前在线协议的两个线性化方向成立：

1. 子写先取得学院锁并提交，删除等待后能统计到子记录并拒绝。
2. 删除先取得学院锁并提交，子写等待后由 `deleted=0 FOR UPDATE` 得到不存在并整体失败。

Finding 1 破坏的是第三个入口：历史 rollback 已先持有 batch/ref，随后直接锁 child 并写回旧父 ID。修复不能在已经锁住 child 后再临时锁 college，否则会形成与正常 `batch → college → child` 相反的顺序。必须在任何业务 child 锁之前，完成所有目标学院的去重升序预锁。

---

## 13. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Validate Every Externalized Invariant | 1 | High | 持久 `before_json` 恢复未校验父学院 |
| Consistent Lock Ordering | 1 | High | rollback 跳过 college，直接进入 child |
| Test Behavior, Not Scheduling | 1 | Low | contender pre-query hook 被当作锁等待边界 |

### Principles Respected

- Explicit Transaction Boundary：父锁以 `MANDATORY` 绑定调用方写事务。
- Fail-Fast：当前新增/迁移在子写前验证父学院存在；专业额外验证启用。
- Bounded Work：父锁固定投影；批量学院 ID 去重升序。
- Single Source of Truth：父锁集中在 `CollegeParentGuard`，生产 hook 无业务行为。
- Preserve Evidence：提交材料保留首次 CORS 环境失败及后续重跑，没有隐藏红灯。

---

## 14. Recommended Fix Order

### Fix Immediately

1. 在 rollback 取得任何 student/training/certificate 行锁之前，预解析所有 UPDATE ref 的 `before_json.collegeId`。
2. 对目标学院去重、升序、`deleted=0 FOR UPDATE`；缺失父学院一律 fail closed 或记录冲突，禁止写回。
3. 保持 `batch → refs → college IDs ascending → business child`，不要采用 `child → college` 的补丁。

### Fix Before Resubmission

1. 增加历史 ref 已指向删除学院的确定性反例，以及 rollback/delete 两个方向的真实 MySQL 交错。
2. 把 contender 探针推进到 MyBatis/JDBC query-entered 边界，保留最终数据库断言。
3. 重跑 Phase 10、Phase 39、后端全量 verify、前端 type-check/build，并提交新的同源证据。

### Schedule Later

- 将通用 JSON snapshot rollback 重构为按实体声明不变量的补偿策略。
- Phase 53 继续独立处理 demo 初始化和对象元数据问题；不要混入本次最小整改。

### Ignore for Now

- 无新增 DDL、权限点、前端逻辑或配置变化，不需要为本 finding 顺带扩展范围。

## 15. Quick Wins

- 先增加一个纯解析 helper，收集 UPDATE refs 的学院 ID，再统一调用 `CollegeParentGuard` 升序预锁。
- 复用 Phase 42 已有的 MyBatis query-entered 探针模式，收紧 Phase 39 的锁等待证据。
- 把 Phase 10 文档中的 rollback 契约明确为“父引用预锁在业务子行锁之前”。
