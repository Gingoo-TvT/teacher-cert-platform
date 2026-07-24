# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform — WS-3 第五轮整改增量
**Audit mode:** incremental + security + stability + performance + testing-authenticity + release + configuration + data-integrity + concurrency
**Date:** 2026-07-24
**Reviewer:** OpenAI Codex（独立复核；Claude 当前不可用）

---

## 1. Executive Summary

本轮冻结并复核 `ee190f3..88d3136`：1 个提交、39 个变更文件、增加 3358 行、删除 180 行。第五轮对第四轮的 1 个 High、1 个 Medium、2 个 Low 均作出了有效整改：JCodec 内容型 `IOException` 已在 worker 内结构化为无效媒体；失败/失权对象已进入 V32 持久候选台账并使用世代隔离 key；临时工件扫描具备跨轮游标；父 JVM 异常退出后的 worker 具备 PID、启动时刻和工件身份三重校验与恢复。按原问题口径，上一轮 4 项全部 **Closed**。

本轮结论仍为 **CHANGES REQUESTED**。增量集成检查新发现 2 个 Medium：

1. 生产环境的视频对象对账虽然使用了专用执行器执行实际工作，但 `@Scheduled` 触发方法仍与同步全量备份、清理任务共用 Spring Boot 默认单线程调度器。每日备份或其他同步任务长时间运行时，每分钟对账触发甚至无法被调用，专用执行器也就收不到任务。
2. V32 新增的关键持久候选台账 `video_finalization_object_candidate` 未加入“全量备份”的显式表清单。备份恢复会丢失已清理墓碑、待清理候选及历史世代 key；现有启动回填只覆盖少数当前会话状态和当前 `object_key`，无法重建历史世代。

这两项都不是第五轮核心算法本身的失败，而是典型的“新增生产可靠性组件没有同步接入既有调度/备份消费者”问题。它们会让对象对账在最需要恢复能力的生产环境中失去时效性或持久历史，因此阻止 WS-3/U-002 放行。

独立验证严格遵守用户安全边界：未执行畸形媒体、fuzz、破坏 Redis/MinIO、恶意凭据、竞争压测、进程杀伤或任何攻击性动作；也未启动常驻后端/前端服务。安全的一次性验证结果为：定向测试 **7/7**、后端 package、前端 type-check/build、dev/prod Compose 解析及 `git diff --check` 全部通过。整改者的全量 **316/316** XML 结果已核对为 0 failure/error/skip，但仍属于整改者自测，不替代本轮两个新增边界的回归证明。

### Score Dashboard

```text
Security        █████████░  8.5  A   未见新增鉴权、注入或凭据问题；残留风险主要是敏感对象生命周期
Stability       ███████░░░  7.0  A   四个旧故障已闭环，但共享调度线程会推迟关键对账触发
Performance     ███████░░░  6.5  B   对账工作已隔离，触发器仍可能被长时全量备份队头阻塞
Testing         ████████░░  7.5  A   316 项自测与 7 项独立白名单可信，新增集成边界尚无自动回归
Maintainability ████████░░  7.5  A   生命周期状态机清晰，但生产任务与备份表依赖仍靠隐式人工同步
Design          ███████░░░  6.5  B   世代候选设计正确，调度和恢复边界未成为同一可靠性契约
Release         ██████░░░░  5.5  B   一次性构建门禁通过，但恢复产物不完整阻止生产放行
Configuration   ███████░░░  6.5  B   核心参数存在，缺少专用 TaskScheduler 或明确调度池配置
Data Integrity  ██████░░░░  5.5  B   在线台账正确，备份恢复会丢失关键历史世代和清理状态
─────────────────────────────────────
Overall         ███████░░░  6.8  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 2 | 2 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **2** | **2** | **0** |

## 2. Project Map

第五轮增量形成了四条主要闭环：

1. `VideoProbeWorkerMain` 用 tracking channel 区分内容解析阶段与源文件/结果文件基础设施 I/O，内容型解析失败输出结构化 invalid。
2. V32 引入 `video_finalization_object_candidate`，SERVER 使用 generation-specific key；生命周期服务持久认领、重试、清理与墓碑核对候选对象。
3. `VideoProbeTempArtifactManager` 的有界扫描保存轮转进度，使小 `scan-limit` 下尾部孤儿最终可达。
4. `VideoProbeParentWatchdog`、runner 和边界测试为非容器运行补充父进程崩溃后的 worker 身份核验与恢复。

两个新增问题位于这四条闭环之外的既有生产消费者：

- Spring 调度基础设施决定“周期对账何时能被提交”。
- 数据库全量备份清单决定“V32 台账是否能跨灾难恢复保留”。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental scope | High | `git diff/log/shortstat ee190f3..88d3136`；逐文件核对 39 个变更文件，并搜索调度、备份和恢复调用方 | 不重复审计与 WS-3 无关的全仓业务 |
| Security | Medium | 候选 key、对象删除、worker 参数、凭据配置、敏感对象恢复语义 | 未执行恶意媒体、漏洞利用、凭据尝试、路径攻击或攻击性并发 |
| Stability | High | worker 异常域、candidate 状态机、调度触发、备份同步任务、父进程 watchdog | 未杀进程、阻塞线程、破坏 Redis/MinIO 或制造故障 |
| Performance | Medium | 默认调度线程数、长时 JDBC/MinIO 备份、对账执行器、扫描上限 | 未执行压力、容量、长耗时备份或基准测试 |
| Testing Authenticity | High | Surefire/Failsafe XML、定向测试、V32/Phase7/watchdog/fat-JAR 测试、一次性构建门禁 | 未独立重跑畸形媒体、父进程崩溃、竞争交错和故障注入 |
| Release | High | package、前端构建、Compose 解析、迁移、备份清单和恢复边界 | 未执行真实发布、灾难恢复或回滚 |
| Configuration | High | prod 调度开关、cron、执行器、Spring Boot 调度默认值、Compose | 未启动生产应用，未改变任何运行配置 |
| Data Integrity | High | V32 表、候选认领/清理/墓碑、启动回填、备份导出表清单 | 未执行真实数据库备份恢复或对象存储故障注入 |
| Concurrency | High | 旧/新世代 key、candidate 行锁、claim owner、父进程 PID 复用保护、调度线程竞争 | 未动态制造竞态；结论来自代码顺序、框架默认值和持久不变量 |

### Change Summary

- Total files changed: 39
- Lines added: 3358
- Lines deleted: 180
- Commits in range: 1
- Baseline: `ee190f3a5a8d04a2ca479a6ec0391f6223776eab`
- Audited head: `88d313630564feb3cd33a8573b9d4a4145c49127`
- Commit: `fix(ws-3): 闭环第四轮退回项第五轮整改`

### Change Categories

| Category | Main changes | Audit result |
|---|---|---|
| Content validation | tracking channel、结构化 invalid、direct/server 可重传状态 | 原 High 已关闭 |
| Object lifecycle | V32 candidate ledger、generation key、claim/retry/tombstone/backfill | 原 Medium 核心已关闭；备份消费者漏接产生新 Medium |
| Temp artifact cleanup | 跨轮游标和公平扫描 | 原 Low 已关闭 |
| Worker supervision | PID + startInstant + artifact identity、startup recovery | 原 Low 已关闭 |
| Scheduling | 对账专用工作执行器、单飞保护 | 工作线程隔离成立；触发线程仍共享，产生新 Medium |
| Release/recovery | V32 migration、fat-JAR/IT、自测证据 | 在线迁移成立；全量备份漏表产生新 Medium |

### Previous Finding Closure Matrix

| 第四轮 finding | 第五轮复核 |
|---|---|
| JCodec 内容型 IOException 被判基础设施故障 | **Closed（High）** — 内容解析边界内捕获并结构化 invalid，源/结果 I/O 仍保留基础设施语义 |
| FAILED/失权最终对象缺少持久 reconciliation | **Closed（Medium）** — V32 候选台账、世代 key、持久 claim/retry/tombstone 已落地 |
| 有界临时工件扫描可能饿死尾部孤儿 | **Closed（Low）** — 跨轮游标与确定性小上限回归已覆盖 |
| 裸机父 JVM 崩溃后 worker 监管状态丢失 | **Closed（Low）** — 持久 watchdog 身份记录与安全核验恢复已落地 |

### Risk Delta

- 已移除：1 High、1 Medium、2 Low。
- 新增：2 Medium。
- 净变化：风险峰值由 High 降为 Medium，但 WS-3 仍存在生产恢复与调度集成阻断。
- 未发现 Critical/High，也未重新打开第四轮原问题。

### Test Coverage Delta

- 整改者证据：Surefire **148/148**、Failsafe **168/168**，合计 **316/316**；全部 0 failure/error/skip。
- 关键整改者用例：`Phase7VideoReviewIT` **44/44**、`VideoProbeWorkerBoundaryTest` **9/9**、`VideoProbeTempArtifactManagerTest` **5/5**、`VideoFinalizationReconciliationScheduleConfigTest` **2/2**、V32 migration IT **1/1**、fat-JAR worker IT **1/1**。
- 独立安全白名单：`VideoFinalizationReconciliationScheduleConfigTest` 2、`VideoProbeTempArtifactManagerTest` 5，共 **7/7**。
- 独立一次性门禁：后端 `package`、前端 type-check/build、dev/prod Compose config 与 `git diff --check` 全部通过。
- 新缺口：没有用真实 Spring scheduler 证明长时备份不会阻止 reconciliation trigger；没有恢复到 scratch schema 后证明 V32 candidate 行、状态和历史 key 完整保留。

### Approval Recommendation

**Request changes.** 第六轮至少应完成两项：

1. 为视频对象 reconciliation 的触发建立真正独立的 `TaskScheduler`，并用确定性测试证明同步备份被阻塞时对账仍按期提交。
2. 将 `video_finalization_object_candidate` 纳入全量备份与恢复验证，证明 `CLEANUP_PENDING`、`CLEANING`、`CLEANED` 及历史世代 key 在 scratch restore 后仍可继续对账。

在这两项关闭前，WS-3/U-002 和 Phase 7 当前覆盖层保持 **CHANGES REQUESTED / 复核退回**。

## 3. Top Risks

1. **Medium — reconciliation 触发仍受默认单线程调度器队头阻塞。** 实际工作有专用 executor 不等于触发隔离；同步备份不返回时，`@Scheduled` 方法根本没有机会提交任务。
2. **Medium — V32 candidate ledger 不在全量备份清单。** 灾难恢复后历史对象世代和清理状态消失，启动回填无法从当前 session 重建这些信息。

## 4. Detailed Findings

### Finding: 对账触发仍与同步全量备份共用默认单线程 TaskScheduler

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 生产定时调度、视频最终对象 reconciliation、全量备份
- Evidence:
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/config/VideoFinalizationReconciliationScheduleConfig.java:24,32-41,49-70`
  - Function/module: `reconcileOnSchedule`、`submitReconciliation`
  - Relevant behavior: reconciliation 的实际工作提交给专用 `Executor`，但 `@Scheduled` 触发方法本身未绑定专用 scheduler。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/config/BackupScheduleConfig.java:24-38`
  - Function/module: `scheduledFullBackup`
  - Relevant behavior: 生产开启 scheduling 后，全量备份在 `@Scheduled` 方法内同步执行到完成。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:101-152,155-225`
  - Function/module: `backup`、`createArtifact`、`dumpTable`
  - Relevant behavior: 作业逐表执行全量 JDBC 查询、压缩并上传 MinIO；`Statement` 未设置 query timeout，运行时间可长且外部依赖可能延迟。
  - Repository-wide search: 未发现自定义 `TaskScheduler`、`SchedulingConfigurer` 或 `spring.task.scheduling.pool.size`。
  - Framework evidence: 独立检查项目使用的 Spring Boot 3.4.13 `TaskSchedulingProperties.Pool` 字节码，默认调度池大小为 1。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/config/VideoFinalizationReconciliationScheduleConfigTest.java`
  - Function/module: schedule config tests
  - Relevant behavior: 测试直接调用触发方法，并验证已提交 reconciliation 的单飞/异步行为；没有让真实 scheduler 同时运行长时备份和对账触发。
- Problem: 专用 executor 只隔离了 `runReconciliation`，没有隔离调用 `submitReconciliation` 的调度线程。默认单线程 scheduler 一旦被同步备份或其他同步定时清理占住，reconciliation 的 cron 触发会在队列中等待。
- Why it matters: V32 的修复价值依赖“启动与周期对账最终会运行”。备份通常正是数据量大、MinIO 或数据库变慢时耗时更久；同一时期也是未登记对象和失败清理需要及时收敛的场景。当前实现没有可证明的最大延迟。
- Realistic failure scenario: 生产 03:00 开始全量备份，JDBC 读取大表或 MinIO 上传长时间不返回。默认 scheduler 唯一线程一直执行 `scheduledFullBackup`；03:01、03:02 等 reconciliation cron 不能调用 `reconcileOnSchedule`，专用 executor 始终空闲但没有任务可执行。清理候选持续滞留，直到备份返回。
- Minimal fix: 为 reconciliation 声明独立、命名的 `TaskScheduler`，并通过 `@Scheduled(scheduler = "...")` 或等价 `SchedulingConfigurer` 明确绑定。线程名、池大小、shutdown 等待和错误处理应可观测。
- Better long-term fix: 建立统一的生产作业注册表：按“关键对账、备份、普通清理”分离 scheduler/worker pool，声明每类作业的并发策略、超时、最大调度延迟和指标；避免每个配置类隐式依赖 Boot 默认调度器。
- Regression test suggestion: 在 Spring 测试上下文中启用真实 scheduling，用 latch 阻塞备份任务而不做真实 I/O；证明至少两个 reconciliation 周期仍能在独立 scheduler 上触发并提交。测试应自然退出，不启动常驻应用。
- Estimated effort: 4–8 hours

### Finding: V32 关键候选台账未进入全量备份清单

- Severity: Medium
- Confidence: High
- Category: Data Integrity
- Status: Confirmed
- Affected area: 全量备份、灾难恢复、视频最终对象生命周期
- Evidence:
  - File: `platform-boot/src/main/resources/db/migration/V32__video_finalization_object_reconciliation.sql:1-30`
  - Function/module: `video_finalization_object_candidate`
  - Relevant behavior: V32 创建每个 upload/generation 唯一的持久候选台账，保存 bucket、object key、状态、重试、claim 和墓碑信息。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:56-79`
  - Function/module: `BACKUP_TABLES`
  - Relevant behavior: 注释声明清单覆盖“全部业务域”，视频域只列出 `video_review`、`video_review_task`、`video_upload_session`、`video_upload_chunk`，没有 `video_finalization_object_candidate`。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:155-225`
  - Function/module: `createArtifact`、`dumpTable`
  - Relevant behavior: 全量备份只遍历 `BACKUP_TABLES`，因此遗漏表不会进入产物。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/mapper/VideoFinalizationObjectCandidateMapper.java:50-65`
  - Function/module: `selectLegacyUploadIdsForBackfill`
  - Relevant behavior: 启动回填仅选择当前 session 为 `MERGING`/`FAILED`、当前 `object_key` 非空且完全没有 candidate 的 upload。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoFinalizationObjectLifecycleService.java:118-178`
  - Function/module: legacy backfill
  - Relevant behavior: 回填只能依据 session 当前 generation/current object key 生成有限候选，不能恢复旧世代 key、已清理墓碑或其他状态历史。
  - File: `docs/phase-14-非功能部署验收.md:40`
  - Function/module: V32 deployment contract
  - Relevant behavior: 文档把 candidate table 定义为可靠性台账，并要求保留历史而非物理删除。
- Problem: 新增的关键持久状态已进入在线事务和对账逻辑，却没有进入显式维护的恢复数据集合。所谓“全量备份”在 V32 后不再包含全部业务恢复事实。
- Why it matters: candidate ledger 的目的正是让对象存储与数据库能跨失败、重启和迟到写持续收敛。灾难恢复丢失它后，系统看不见旧世代对象，也不知道哪些 key 待删除、正被 claim 或已形成 tombstone；恢复后的数据库与 MinIO 可能永久不一致。
- Realistic failure scenario: generation 1 已进入 `CLEANED` 或 `CLEANUP_PENDING`，session 当前指向 generation 2。系统做全量备份后数据库损坏，使用该产物恢复；candidate 表无任何行。启动回填最多根据当前 session/generation 2 建一行，generation 1 的 server key 无法从 session 推导。若该对象仍存在或之后因迟到写出现，对账永远不可见。
- Minimal fix: 将 `video_finalization_object_candidate` 加入 `BACKUP_TABLES` 的视频域，并更新备份表数/清单测试。
- Better long-term fix: 让迁移新增业务表时必须显式声明“备份包含/有依据排除”，由 schema/备份契约测试比较 Flyway 业务表与恢复注册表；同时建立 scratch restore 门禁验证表结构、行数、关键状态和继续对账能力。
- Regression test suggestion: 创建隔离数据集，写入多个 upload/generation 及 `CLEANUP_PENDING`、`CLEANING`、`CLEANED` 行；生成备份并恢复到 scratch schema，断言所有 candidate 行、key、claim/retry/tombstone 字段完整，随后执行一次安全的 mock reconciliation 验证状态机可继续。
- Estimated effort: 4–8 hours

## 5. Security Concerns

- Coverage: Medium
- Inspected evidence: 候选对象 key、删除与墓碑语义、worker 子进程参数、MinIO/JWT 配置入口、敏感视频在备份恢复后的生命周期。
- Exclusions / limits: 按用户明确要求，未执行漏洞扫描、恶意媒体、路径攻击、权限绕过、凭据探测、利用代码或攻击性并发。

未发现新的鉴权绕过、公开对象 URL、命令注入或密钥硬编码。Finding 2 的主要分类是数据完整性，但灾难恢复后失去敏感视频对象的清理台账也会扩大数据保留风险；本报告没有把未验证的对象可访问性推断为安全漏洞。

## 6. Stability Concerns

- Coverage: High
- Inspected evidence: worker 内容/基础设施错误边界、direct/server 终态、candidate reconciliation、定时触发、备份同步执行、父进程 watchdog。
- Exclusions / limits: 未制造损坏媒体、线程永久阻塞、进程崩溃、Redis/MinIO 故障或真实节点中断。

第四轮唯一 High 已可靠关闭，worker 对内容型 `IOException` 与真实 channel I/O 的区分有固定 fixture 和 HTTP 级重传证据。当前稳定性阻断来自调度层：专用执行器只能保证已提交作业不占用 scheduler，无法保证 cron 触发获得线程。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: 全量 JDBC 导出、gzip/MinIO 上传、Statement timeout、默认调度线程数、reconciliation executor、临时工件有界扫描。
- Exclusions / limits: 未执行大数据量备份、MinIO 慢上传、压力测试、容量测试或吞吐基准。

临时工件扫描的公平性债已关闭。新性能风险是调度队头阻塞，不是 reconciliation 算法吞吐本身：一个耗时备份可让多个不同周期作业串行等待，且当前没有调度延迟指标或最大等待契约。

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: Surefire/Failsafe XML、测试源码、Maven 生命周期、V32 migration IT、Phase7 IT、worker/watchdog/temp/schedule/fat-JAR 用例及独立一次性门禁。
- Exclusions / limits: 未独立重跑畸形媒体、故障注入、父进程崩溃、PID 复用、恶意并发或真实备份恢复；这些动作按用户要求留给开发者在受控环境执行。

### Confidence Assessment

整改者的 **316/316** 具有可核对的 XML 证据，并非只写在提交说明中；关键报告均为 0 failure/error/skip。独立复核随后重新运行了 7 个安全白名单用例，并完成后端、前端与 Compose 的一次性门禁。由此可以高置信关闭第四轮 4 项，但不能把“直接调用 schedule 方法通过”外推为“真实 scheduler 隔离”，也不能把 V32 migration 成功外推为“备份恢复包含 V32 数据”。

### Valuable Tests

- `VideoProbeWorkerBoundaryTest`：固定内容 fixture、真实 channel I/O 分类及 watchdog 边界。
- `Phase7VideoReviewIT`：direct/server 内容校验失败后可重新初始化。
- V32 migration IT：候选表结构、迁移和数据约束。
- candidate lifecycle/reconciler tests：claim owner、重试、墓碑和世代隔离。
- `VideoProbeTempArtifactManagerTest`：小 scan limit 下跨轮公平性。
- fat-JAR IT：生产打包后的 worker 入口可执行。

### Missing Tests

1. 真实 Spring scheduling 环境中，备份 trigger 被 latch 阻塞时 reconciliation trigger 仍可按期提交。
2. 全量备份产物包含 V32 candidate table，并能恢复到 scratch schema。
3. 恢复后继续执行 candidate reconciliation，验证历史 generation key 仍可见。

## 9. Release Concerns

- Coverage: High
- Inspected evidence: 后端 package、前端 type-check/build、dev/prod Compose config、V32 migration、备份产物生成逻辑和 Phase 14 恢复契约。
- Exclusions / limits: 未启动生产服务、执行发布、混部、回滚、真实备份或灾难恢复。

构建与静态部署配置均通过，V32 迁移本身也有隔离测试。发布阻断点是恢复闭环：当前全量备份会静默遗漏新表，且没有“迁移新增表必须被备份策略处理”的自动门禁。该缺口应在第六轮修复后再放行。

## 10. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `platform.backup.schedule.enabled/cron`、视频 reconciliation cron/executor、Spring Boot 3.4.13 调度默认值、application dev/prod 和 Compose。
- Exclusions / limits: 未修改或热加载配置，未在生产 profile 启动应用，也未验证外部调度平台。

备份默认关闭、生产显式开启的门禁本身清晰；问题是开启后所有 `@Scheduled` 方法仍隐式依赖框架默认单线程 scheduler。只增加 worker executor 不会改变 trigger 所在线程，必须显式绑定或配置并用测试锁定。

## 11. Data Integrity Analysis

- Coverage: High
- Inspected evidence: V32 DDL、candidate 唯一键/索引、session/candidate 锁顺序、claim owner、状态转换、legacy backfill、备份表清单与逐表导出。
- Exclusions / limits: 未实际破坏数据库、删除 MinIO 对象、执行灾难恢复或对对象存储注入故障。

在线状态机按 upload/generation 唯一并使用随机 claim owner，保护当前候选、`file_object` 登记和历史墓碑的设计成立。Finding 2 说明这套不变量只存在于在线数据库；一旦按官方“全量备份”恢复，关键历史事实会消失。备份完整性必须被视为状态机的一部分。

## 12. Concurrency Analysis

- Coverage: High
- Inspected evidence: session→candidate 锁顺序、generation-specific key、claim lease/owner、迟到写清理、worker PID identity、reconciliation 单飞和 scheduler/worker 两级执行。
- Exclusions / limits: 未动态触发竞争、锁等待、迟到 compose、PID 复用或调度线程阻塞；结论来自代码顺序、持久状态和框架默认值。

第五轮已把旧 server 写与当前世代从 key 层隔离，并用持久 candidate 状态处理迟到对象，原并发风险关闭。新调度问题属于执行资源竞争：单飞标志只防止 reconciliation 自身重叠，不能让它绕过被备份占用的调度线程。

## 13. Principles Compliance

| Principle | Status | Evidence / note |
|---|---|---|
| R1 文本化 | Pass | 本轮未改变受控文本字段；V32 upload/object 标识使用 VARCHAR |
| R2 不硬编码 | Pass with note | cron、间隔、claim 等可配置；调度池归属仍为隐式框架默认 |
| R3 留痕 | Not applicable | 本轮为后台技术对账与恢复设施，未新增用户业务写接口 |
| R4 逻辑删除 | Pass | candidate 表含 `deleted`，清理历史以墓碑保留 |
| R5 Flyway | Pass | V32 以迁移创建候选台账 |
| R6 数据范围 | Not applicable | 未新增用户列表/导出/统计查询 |
| R7 后端校验 | Pass | 媒体内容校验由 worker/后端硬约束 |
| 测试真实性 | Pass with gaps | 316/316 与 7/7 可核；新增调度/恢复边界未覆盖 |
| 可恢复性 | Fail | 全量备份遗漏 candidate ledger |
| 生产可观测性 | Partial | candidate retry 有状态；缺少 scheduler delay/backup competition 的明确指标 |

## 14. Recommended Fix Order

1. **先修备份完整性。** 把 `video_finalization_object_candidate` 纳入 `BACKUP_TABLES`，补备份表清单与 scratch restore 测试；这是最直接的数据不可恢复风险。
2. **再修 scheduler 隔离。** 为 reconciliation trigger 绑定专用 `TaskScheduler`，补真实 scheduling + blocked backup 的确定性测试。
3. **运行第六轮完整安全门禁。** Maven 全量、前端 type-check/build、Compose config、V32 migration、备份恢复和 scheduler isolation。
4. **独立复核。** 只复核第五轮新增 2 项的增量与 WS-3 核心回归；PASS 后再更新 Phase 7/WS-3/U-002 状态。

## 15. Quick Wins

- 在 `BACKUP_TABLES` 的视频域立即加入 `video_finalization_object_candidate`，同步更新期望表数。
- 给 reconciliation scheduler 使用明确 bean 名和线程名前缀，避免以后再次被默认 scheduler 吞并。
- 为所有 Flyway 新业务表增加“已备份或有明确排除理由”的契约测试。
- 暴露 `scheduled_lag_seconds`、last success、candidate backlog age/count，方便在生产发现调度饥饿。

## 16. Long-term Refactor Plan

1. 将关键后台任务注册为显式作业类别，分离 trigger scheduler 与 work executor。
2. 为每类作业定义超时、并发、错过触发、shutdown 与可观测契约。
3. 由 schema registry 派生备份覆盖清单，减少迁移与人工 allowlist 漂移。
4. 把“备份 → scratch restore → 关键状态机续跑”提升为 Phase 14 发布门禁。

---

**Final decision:** **CHANGES REQUESTED**
**Previous findings:** 1 High + 1 Medium + 2 Low 全部 Closed
**New findings:** 2 Medium Confirmed
**Next gate:** 第六轮整改完成后进行增量独立复核
