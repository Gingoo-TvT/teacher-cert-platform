# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform — WS-3 第六轮整改增量
**Audit mode:** incremental + security + stability + performance + testing-authenticity + release + configuration + data-integrity + concurrency
**Date:** 2026-07-24
**Reviewer:** OpenAI Codex（独立复核；Claude 当前不可用）

---

## 1. Executive Summary

本轮冻结并复核 `88d3136..2886442`：1 个提交、16 个变更文件、增加 1005 行、删除 28 行。第五轮新增的 2 个 Medium 均已按原问题口径有效关闭：

1. 生产环境同时显式提供默认 `taskScheduler` 与视频专用 `videoFinalizationReconciliationTaskScheduler`，reconciliation cron 通过 `@Scheduled.scheduler` 精确绑定专用调度器；实际对象工作继续投递到独立 worker executor。真实 Spring scheduling 测试证明默认 scheduler 上的同步备份被阻塞时，视频专用 trigger 仍连续提交。
2. `video_finalization_object_candidate` 已加入应用逻辑全量备份。静态解析 V1–V32 的 32 个迁移文件得到 37 个唯一业务表，与 37 项备份清单精确一致；scratch restore 测试用真实 gzip 产物回放 candidate 全字段，并由真实 reconciler 继续清理三个 generation。

本轮正式结论为 **PASS**。未发现 Blocker、High、Major 或 Medium；仅有 1 个不阻断 Low：提交材料及活动日志把 Flyway 迁移数量写成 33，实际为 **32 个迁移、最终版本 V32**。该口径误差不影响 schema、37 表备份或恢复续跑结论，按 `REVIEW-GATE.md` 作为 Minor/backlog 管理。

独立验证严格遵守用户安全边界：没有执行畸形媒体、fuzz、攻击性并发、故障注入、进程杀伤、凭据尝试、漏洞扫描或其他可能被视为 cyber 的动作；没有启动常驻后端/前端或临时依赖服务。独立安全门禁为后端 9 模块 package、前端 type-check/build、dev/prod Compose 解析和 `git diff --check`，全部通过。整改者的 Surefire **149/149**、Failsafe **169/169**（合计 **318/318**）XML 已逐项聚合核对为 0 failure/error/skip，时间链晚于新增源码；受安全边界和当前无运行依赖限制，本轮没有重新执行 latch 阻塞或外部 MySQL/MinIO 写入测试。

WS-3/U-002 可据此独立放行，Phase 7 历史 PASS 及当前 WS-3 覆盖层均无退回项。但这不等于全项目发布就绪：Phase 7 的真实 2GB/非允许编码/不可解码首帧广覆盖证据债，以及 Phase 0、39、41、42、44、47、53 的独立退回项继续保留；全项目总状态仍为 **CHANGES REQUESTED**。

### Score Dashboard

```text
Security        █████████░  9.0  A   未见新增鉴权、凭据、对象公开或删除越界
Stability       █████████░  9.2  A   关键对账 trigger 与普通长时调度已明确隔离
Performance     █████████░  8.7  A   队头阻塞已消除；未执行压力或大数据量基准
Testing         █████████░  8.8  A   318 项 XML 与关键新增用例可信；迁移数口径有一处 Low
Maintainability █████████░  8.8  A   bean 命名、职责和 37 表契约清晰
Design          █████████░  9.0  A   trigger scheduler、worker executor、持久台账边界完整
Release         █████████░  8.6  A   本工作包门禁通过；全项目仍受其他阶段退回阻断
Configuration   █████████░  9.2  A   prod 双 scheduler 路由确定，普通任务保留默认入口
Data Integrity  █████████░  9.3  A   37 表精确覆盖，candidate 全字段恢复后可继续对账
─────────────────────────────────────
Overall         █████████░  9.0  A
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **1** | **1** | **0** |

## 2. Project Map

第六轮只改变两条生产可靠性集成链：

1. `VideoFinalizationReconciliationTaskSchedulerConfig` 显式注册普通和视频专用两个 scheduler；`VideoFinalizationReconciliationScheduleConfig` 把视频 cron 绑定到专用 bean，再把实际工作提交到既有单线程 worker executor。
2. `DatabaseBackupService.BACKUP_TABLES` 加入 V32 candidate 台账；`Phase41BackupIT` 锁定 37 表，`Ws03CandidateBackupRestoreIT` 用 scratch schema 验证真实 gzip section、全字段回放和状态机续跑。

其余改动为测试、备份手册和治理状态；没有改变业务权限、媒体规则、对象可见性或 V32 发布协议。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental scope | High | `git log/diff/shortstat 88d3136..2886442`；逐文件核对 16 个变更文件及未改调用方 | 不重复审计与两个第五轮 finding 无关的全仓业务 |
| Security | Medium | scheduler bean、candidate 对象删除替身、MinIO 备份精确 key、配置入口 | 未执行漏洞扫描、恶意载荷、凭据尝试、攻击性并发或对象破坏 |
| Stability | High | 默认/专用 scheduler、worker executor、单飞复位、shutdown/error handler、真实 scheduling 测试 | 未独立阻塞线程、注入外部故障或启动生产 profile |
| Performance | Medium | 长时同步备份与 trigger 隔离、线程池大小、对账提交路径 | 未执行压测、大库备份、慢 MinIO 或容量基准 |
| Testing Authenticity | High | Surefire/Failsafe XML、源码/报告时间链、T-VID-2N/2O、Phase 7/41/V32 | 未重跑 latch 或外部 MySQL/MinIO 写入测试 |
| Release | High | 后端 package、fat JAR 内容、前端 type-check/build、dev/prod Compose、治理边界 | 未部署、启动常驻服务、灾难恢复或回滚 |
| Configuration | High | prod profile、scheduler bean 名、cron、Boot 3.4.13/Spring 6.2 调度解析 | 未修改运行配置或验证外部调度平台 |
| Data Integrity | High | 32 个迁移、37 个业务表、37 项备份清单、candidate 22 列与恢复续跑 | 未独立创建/删除 scratch schema；采用整改者 XML 动态证据 |
| Concurrency | High | 默认 scheduler 阻塞模型、专用 trigger、worker 单飞、candidate claim/过期回收 | 未执行攻击性竞争、压力或真实长时阻塞 |

### Change Summary

- Total files changed: 16
- Lines added: 1005
- Lines deleted: 28
- Commits in range: 1
- Baseline: `88d313630564feb3cd33a8573b9d4a4145c49127`
- Audited head: `28864428aefcc8fdeff9f01bed276e568c4797af`
- Commit: `fix(ws-3): 提交第五轮退回项第六轮整改`

### Change Categories

| Category | Main changes | Audit result |
|---|---|---|
| Scheduling | 默认 `taskScheduler` + 视频专用 trigger scheduler，cron 显式绑定 | 第五轮 scheduler Medium **Closed** |
| Worker execution | 继续使用独立 executor 与 `AtomicBoolean` 单飞 | 无回归 |
| Backup coverage | candidate 加入稳定有序 allowlist，表数锁定 37 | 第五轮备份 Medium **Closed** |
| Restore evidence | 真实 gzip、schema section 精确集合、candidate 全字段回放、真实 reconciler | 恢复切片证据成立 |
| Documentation | Phase 7/14、备份手册、提交材料和统一计划 | 边界总体诚实；迁移数量一处 Low |

### Previous Finding Closure Matrix

| 第五轮 finding | 第六轮复核 |
|---|---|
| reconciliation trigger 与同步备份共享默认单线程 scheduler | **Closed（Medium）** — 双 scheduler 明确注册，视频 cron 精确绑定专用 bean，T-VID-2N 使用真实 scheduling 上下文证明隔离 |
| V32 candidate 台账未进入应用逻辑全量备份 | **Closed（Medium）** — 37 表集合与迁移 schema 精确一致，T-VID-2O 证明 candidate 全字段恢复及真实对账续跑 |

### Risk Delta

- 已移除：2 Medium。
- 新增：1 Low（测试证据数量口径）。
- 净变化：WS-3 当前无阻断 finding，风险峰值降为 Low。
- 未重新打开第四、第五轮已关闭问题。
- Phase 41 整份普通 `INSERT` 与 Flyway 种子冲突、Phase 53 demo 升级等属于其他工作包，继续独立阻断全项目。

### Test Coverage Delta

- 整改者 XML：Surefire **149/149**（17 suites，platform-boot 146 + platform-file 3）、Failsafe **169/169**（27 suites），全部 0 failure/error/skip。
- 新增关键用例：`VideoFinalizationSchedulingIsolationTest` **1/1**、`VideoFinalizationReconciliationScheduleConfigTest` **2/2**、`Ws03CandidateBackupRestoreIT` **1/1**。
- 回归证据：`Phase7VideoReviewIT` **44/44**、`Phase41BackupIT` **1/1**、V32 migration IT **1/1**。
- 动态日志：scratch schema 成功验证并应用 **32** 个迁移至 V32；备份 `tables=37`；对账 `scanned=3, cleaned=3, failed=0`。
- 独立安全门禁：后端 9 模块 `package`、fat JAR class 检查、前端 type-check/build、dev/prod Compose config、`git diff --check` 全部通过。
- 安全限制：未重跑 latch 阻塞、外部 MySQL/MinIO 写入、畸形媒体、故障注入、进程或攻击性并发测试；这些结论来自源码、已落盘 XML 及时间链交叉核对。

### Approval Recommendation

**Approve WS-3/U-002.** 第五轮新增的 2 个 Medium 均已关闭，唯一 Low 不阻断阶段放行。活动治理文档应把 Flyway 数量统一改为“32 个迁移至 V32”，历史提交材料保留为当时快照。

本批准只覆盖 WS-3 第六轮增量。全项目继续执行统一队列：Phase 42 → 39 → 41 → 47 → 53 → 44 → 0，完成后再进行用户要求的全量审计。

## 3. Top Risks

1. **Low — Flyway 迁移数量口径错误。** 提交材料写 33，实际 migration 文件和运行日志均为 32；不影响 V32 或 37 表结论，但会干扰证据复现。
2. **Scope boundary — Phase 41 整份逻辑备份仍不可按当前手册直接回放。** 这是已知独立退回项，不由 candidate section 的成功恢复关闭。
3. **Evidence debt — Phase 7 仍缺真实 2GB、非允许编码和不可解码首帧的广覆盖证据。** 本轮没有执行或冒充这些验证。

## 4. Detailed Findings

### Finding: 第六轮材料把 32 个 Flyway 迁移写成 33 个

- Severity: Low
- Confidence: High
- Category: Testing Authenticity
- Status: Confirmed
- Affected area: 第六轮提交材料、活动开发日志、证据复现口径
- Evidence:
  - File: `docs/reviews/ws-03-sixth-remediation-submission-2026-07-24.md:44`
  - Relevant behavior: 声称专用空库执行了 33 个 Flyway 迁移至 V32。
  - File: `DEVLOG.md:32,50`
  - Relevant behavior: 第六轮及第五轮开发者日志沿用了同一“33 个迁移”口径；顶部 GOV-009 已追加正式勘误。
  - File: `platform-boot/src/main/resources/db/migration/`
  - Relevant behavior: 目录中实际有 V1–V32 共 32 个版本迁移文件。
  - File: `platform-boot/target/failsafe-reports/TEST-cn.edu.gpnu.platform.boot.Ws03CandidateBackupRestoreIT.xml:101,315`
  - Relevant behavior: 运行日志明确记录 validated/applied 32 migrations，最终版本 v32。
- Problem: 证据摘要把“最终版本 V32”误写成“33 个迁移”，与仓库和测试日志不一致。
- Why it matters: 虽不改变 schema 或功能结果，但审计者可能误以为有未归档迁移、重复迁移或测试环境与仓库不一致，降低复现效率。
- Realistic failure scenario: 后续审计按“33”核对迁移目录，只找到 32 个文件，误判构建产物缺失，或继续把错误数字复制到交接与阶段报告。
- Minimal fix: 活动计划、交接和后续日志统一写“32 个版本迁移，最终版本 V32”；历史提交材料保留为快照并由本报告勘误。
- Better long-term fix: 从 Flyway history/XML 或 migration 目录自动生成迁移数量，不在人工提交说明中手工维护计数。
- Regression test suggestion: 治理门禁读取 `flyway_schema_history` 成功记录数并与版本迁移资源数核对，报告同时输出“数量”和“最高版本”两个独立字段。
- Estimated effort: < 15 minutes

## 5. Security Concerns

- Coverage: Medium
- Inspected evidence: 双 scheduler bean、candidate bucket/object key、测试对象存储替身、MinIO 备份产物精确 key、prod 配置与敏感对象生命周期。
- Exclusions / limits: 未执行漏洞扫描、恶意媒体、路径攻击、权限绕过、凭据探测、利用代码、攻击性并发或真实对象破坏。

未发现新增鉴权绕过、公开视频 URL、凭据硬编码或对象删除越界。`Ws03CandidateBackupRestoreIT` 的 candidate 删除由 `@Primary` 内存替身承接；真实 MinIO 仅保存本测试创建的 gzip 备份对象，并按返回的精确 key 清理。

## 6. Stability Concerns

- Coverage: High
- Inspected evidence: 默认/专用 scheduler 注册、`@Scheduled.scheduler`、worker executor、单飞标志、提交失败复位、scheduler shutdown/error handler、T-VID-2N。
- Exclusions / limits: 未重新制造线程阻塞、对象存储超时、进程崩溃或外部服务故障。

第五轮调度问题已关闭。未指定 scheduler 的备份/清理任务由约定名 `taskScheduler` 承载；视频 cron 精确绑定专用 scheduler。实际工作仍在独立 executor 中执行，因此 trigger 和 work 两层均不再与同步备份共用执行线程。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: 两个单线程 scheduler、同步全量备份、reconciliation 提交路径、37 表 JDBC 导出、线程命名与关闭策略。
- Exclusions / limits: 未执行压力、吞吐、容量、大库备份或慢 MinIO 基准。

长时备份对视频对账 trigger 的队头阻塞已移除。两个 scheduler 都为单线程是有意的串行策略；reconciliation 自身还有 worker 单飞保护，不会因短 cron 周期无限排队。生产仍应补 scheduler lag、last success 和 candidate backlog 指标，但不是本轮放行条件。

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 全模块 Surefire/Failsafe XML、测试源码、文件时间、编译/提交时间、Phase 7/41/V32、新增 T-VID-2N/2O 和独立一次性门禁。
- Exclusions / limits: 按用户安全要求，未重新运行 latch 阻塞、外部 MySQL/MinIO 写入、故障注入、畸形媒体、进程或攻击性并发测试。

### Confidence Assessment

318/318 来自 44 个 XML suite，可聚合到 Surefire 149 与 Failsafe 169，均为 0 failure/error/skip。新增源码写入时间早于 test report，test report 又早于提交，时间链一致。T-VID-2N 使用真实 Spring scheduling，而非直接调用；T-VID-2O 使用真实 gzip、schema 集合和 reconciler，而非手工拼造结果。因此可高置信关闭两个 Medium。

唯一真实性缺口是文档把 32 写成 33；这属于证据摘要错误，不是缺测或假测试。

### Valuable Tests

- `VideoFinalizationSchedulingIsolationTest`：真实 scheduling + blocked default backup + 两次专用 trigger。
- `VideoFinalizationReconciliationScheduleConfigTest`：prod profile、scheduler 注解绑定和 worker 单飞。
- `Ws03CandidateBackupRestoreIT`：37 section 精确集合、candidate 全字段回放、真实 reconciler 续跑。
- `Phase41BackupIT`：真实 gzip/MinIO 产物包含 candidate section。
- `Phase7VideoReviewIT` 与 V32 migration IT：WS-3 核心回归及迁移不变量。

### Missing Tests

本轮两个 finding 所需自动化已补齐。仍有但不属于本工作包的证据债：

1. Phase 7 真实 2GB 上传。
2. 非允许编码与不可解码首帧的广覆盖运行证据。
3. Phase 41 整份备份在含 Flyway 种子的恢复库中的可回放性。

## 9. Release Concerns

- Coverage: High
- Inspected evidence: 后端 package、fat JAR class、前端 type-check/build、dev/prod Compose config、V32 发布协议、备份/恢复手册和治理状态。
- Exclusions / limits: 未启动生产服务、执行发布、混部、回滚、整库恢复或 MinIO mirror。

WS-3 本轮发布门禁通过，但全项目不可发布。Phase 41、42、39、47、53、44、0 仍有正式退回项；V32 仍必须按停写、停止全部旧节点/worker、迁移、全量新节点、再放流的协议执行，禁止旧协议混部或 V32 后回滚旧二进制。

## 10. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `prod` profile、两个 scheduler bean 名、cron、默认任务路由、worker executor、application-prod.yml 与 Compose。
- Exclusions / limits: 未修改、热加载或在生产 profile 启动配置。

配置选择是确定的：显式 `taskScheduler` 为普通未限定任务提供约定默认 bean，视频 cron 使用专用 bean 名。已有 `TaskScheduler` 时 Boot 自动配置回退，不会再创建第三个歧义 scheduler。

## 11. Data Integrity Analysis

- Coverage: High
- Inspected evidence: V1–V32 migration、37 表 allowlist、V32 candidate 22 列、section 集合、三种状态/多 generation、claim/retry/error/tombstone 和真实对账续跑。
- Exclusions / limits: 未独立创建或删除 scratch schema、执行整库恢复或破坏真实对象存储。

第五轮备份完整性问题已关闭。迁移静态集合、备份 allowlist 和动态 `information_schema` 三方均为 37 个表；candidate 回放前后 `SELECT *` 递归相等，并能从 `CLEANUP_PENDING`、过期 `CLEANING` 和 `CLEANED` 墓碑继续收敛。Phase 41 整份脚本的种子重复键冲突仍单独保留。

## 12. Concurrency Analysis

- Coverage: High
- Inspected evidence: 默认 scheduler 阻塞、专用 trigger、worker executor、`AtomicBoolean` 单飞、candidate 过期 claim 和真实 reconciler。
- Exclusions / limits: 未动态制造攻击性竞态、长时阻塞或压力。

调度层的共享资源竞争已解除；删除专用绑定、误用同一 scheduler 或缺少默认 bean 都会使 T-VID-2N 失败。candidate 恢复后过期 claim 可被重新领取，三行 attempt 均递增且 claim 释放，证明灾难恢复不会让状态机永久停在旧 owner。

## 13. Principles Compliance

| Principle | Status | Evidence / note |
|---|---|---|
| R1 文本化 | Pass | 本轮未改变受控文本字段；candidate 标识和对象 key 为 VARCHAR |
| R2 不硬编码 | Pass | cron 可配；scheduler bean/线程前缀属于技术常量 |
| R3 留痕 | Not applicable | 未新增用户写接口；后台 candidate 本身保留状态、尝试和错误 |
| R4 逻辑删除 | Pass | candidate 含 `deleted`，历史以墓碑保留 |
| R5 Flyway | Pass | V32 迁移未改写；32 个迁移最终到 V32 |
| R6 数据范围 | Not applicable | 未新增用户列表、导出或统计接口 |
| R7 后端校验 | Pass | 本轮未放宽媒体或对象校验 |
| 测试真实性 | Pass with Low | 318/318 XML 与新增动态证据可信；迁移数量摘要需勘误 |
| 可恢复性 | Pass for WS-3 | candidate slice 完整；Phase 41 整份脚本仍是独立退回项 |
| 生产可观测性 | Partial | 线程名和日志存在；scheduler lag/backlog 指标留长期改进 |

## 14. Recommended Fix Order

1. 在活动治理文档中把“33 个迁移”更正为“32 个迁移，最终 V32”；历史提交材料由本报告勘误，不改写证据原件。
2. 正式把 WS-3/U-002 置为独立 PASS，保留 Phase 7 两项广覆盖证据债。
3. 进入统一队列 Phase 42 → 39 → 41 → 47 → 53 → 44 → 0。
4. 所有阶段退回项关闭后执行全量安全、业务规则、数据一致性、性能和运行期审计。

## 15. Quick Wins

- 后续提交材料同时写“迁移数量”和“最高版本”，避免把 V32 误当成第 33 个迁移。
- 保留 schema 与备份 allowlist 的精确集合契约，新增业务表时 CI 自动失败。
- 为关键对账增加 scheduler lag、last success、candidate backlog count/age 指标。

## 16. Long-term Refactor Plan

1. 将 schema 表的“备份包含/明确排除理由”做成机器可读 registry，由迁移门禁自动核对。
2. 为关键后台任务统一声明 trigger scheduler、worker executor、超时、misfire、shutdown 和可观测契约。
3. 把“备份 → scratch restore → 关键状态机续跑”提升为 Phase 14 的持续发布门禁。

---

**Final decision:** **PASS**
**Previous findings:** 2 Medium Closed
**New findings:** 1 Low Confirmed（non-blocking）
**Next gate:** Phase 42 整改与独立复核；全项目仍为 CHANGES REQUESTED
