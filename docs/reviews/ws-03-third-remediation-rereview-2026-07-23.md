# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform — WS-3 第三轮整改增量
**Audit mode:** incremental + security + stability + performance + testing-authenticity + release + configuration + data-integrity + concurrency
**Date:** 2026-07-23
**Reviewer:** OpenAI Codex（GPT-5，独立复核）

---

## 1. Executive Summary

本轮冻结并复核 `32da735..df22e5b`：1 个提交、31 个变更文件、增加 1667 行、删除 236 行。结论为 **CHANGES REQUESTED**。第三轮整改有明确进展：两类定稿路径都接入可续租 Redis lease，`SERVER_CHUNK` 使用稳定对象 key 和三处恢复点，媒体解析移入受限堆子 JVM，容量、生产参数、专用临时卷和 V30 迁移均已落地。独立全新 MySQL/Redis/MinIO 环境执行 Flyway V1–V30 和后端全量门禁 **277/277** 通过，Phase 7 **31/31**，前端 type-check/build、生产 Compose config 和生产 fat-JAR worker 也通过。

但恢复协议仍存在 4 个 High：fencing token 只保存在会过期/丢失的 Redis 序列中，可发生 token ABA；`SERVER_CHUNK` 探测期间若旧评审被分配，定稿异常会把会话永久留在 `MERGING`；direct 接管陈旧 `MERGING` 时若 multipart 已被对象存储清理，同样没有恢复出口；持久临时卷没有崩溃遗留文件清扫，单次进程/容器退出即可耗尽后续探测容量。另有基础设施异常被错误落成内容校验失败、强杀结果未确认、磁盘预留双重计数、MinIO SDK 路径未受新超时参数控制、生产 fat-JAR 分支缺自动化门禁等 Medium。绿灯证明已编写的正常与局部恢复用例成立，不能证明这些未覆盖的不变量成立，因此 WS-3/U-002 不得置 PASS。

### Score Dashboard

```text
Security        ████████░░  8.0  A   未见新增鉴权、注入或敏感信息问题；按用户要求未执行攻击性动态测试
Stability       ████░░░░░░  3.5  C   四条现实故障序列可造成旧 owner 生效、会话永久 MERGING 或临时盘持续拒绝
Performance     ██████░░░░  6.0  B   子进程与并发上限有效，但磁盘双重计数会提前拒绝本可并行的合法任务
Testing         ██████░░░░  6.0  B   277/277 真实回归有价值，但关键 ABA、竞态、清理和生产分支仍缺确定性自动化
Maintainability ███████░░░  7.0  A   支撑组件职责较清楚，但两条定稿路径继续拥有分叉且不完整的失败恢复语义
Design          ████░░░░░░  4.0  C   fencing、恢复和临时文件生命周期的持久不变量没有由单一权威状态机保证
Release         █████░░░░░  4.5  C   构建、迁移和配置门禁全绿，但四项 High 仍可导致生产状态或容量不可恢复
─────────────────────────────────────
Overall         ██████░░░░  5.6  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based. See `rubrics/scoring.md` for anchor descriptions.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 4 | 4 | 0 |
| Medium | 5 | 5 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **9** | **9** | **0** |

## 2. Project Map

本次增量主要改变视频定稿的执行权、媒体探测隔离与生产资源边界：

1. `VideoFinalizeSingleFlight` 使用 Redis Lua 生成 token、设置 lease、续租、校验 owner 和释放；`VideoReviewServiceImpl` 再把 token 写入 `video_upload_session.finalization_token`。
2. `VideoReviewServiceImpl.complete` 处理浏览器 S3 multipart 直传；`merge` 处理服务端分片兼容路径。两者均会进入 `MERGING`，生成/确认最终对象，探测媒体，再写 `file_object` 和 `video_review`。
3. `JcodecVideoMediaProbe` 下载最终对象到 `video-probe-temp`，计算服务端指纹并调用 `ProcessVideoMediaWorker`；后者由 `VideoProbeProcessRunner` 启动受限堆子 JVM。
4. `VideoProbeCapacityGuard` 用信号量、进程内累计预留和文件系统可用空间控制并发；生产 Compose 把临时目录挂载为持久命名卷。
5. V30 给上传会话增加当前 fencing token；Phase 7 IT、容量守卫测试和 worker 边界测试提供新增回归证据。

最高风险边界是：Redis token 是否跨数据丢失仍保持单调、数据库 `MERGING` 是否对所有外部状态变化都有恢复出口、临时卷是否在进程级 finally 无法运行时仍能自愈，以及媒体内容失败与基础设施失败能否被准确区分。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental scope | High | `git diff/log/shortstat 32da735..df22e5b`；逐文件检查 31 个变更文件及关键未变调用方 | 不重复审计与 WS-3 增量无关的全仓业务 |
| Security | Medium | 进程参数构造、对象访问路径、日志/错误映射、生产配置与权限边界静态检查 | 按用户要求未新增或执行畸形载荷、模糊测试、压测、漏洞利用或攻击性并发 |
| Stability | High | direct/server 状态机、Redis Lua、DB fencing、multipart 丢失、临时文件、子进程终止、MinIO 客户端 | 未人为杀 JVM、破坏 Redis 数据或阻塞对象存储；结论来自确定的代码状态序列 |
| Performance | Medium | 2GB 临时盘预算、并发槽位、累计预留、子进程堆和生产卷配置 | 未执行压力、容量或基准测试 |
| Testing Authenticity | High | Surefire/Failsafe XML、Phase 7 31 个 IT、容量/worker/V30 测试、fat-JAR 一次性实测 | 真实 2GB、非允许编码、不可解码首帧和多节点仍是已声明证据债 |
| Configuration | High | `.env.example`、生产/开发 application、Compose、MinIO 两套 SDK 配置、Phase 14 | 未启动生产应用容器或做真实生产发布 |
| Data Integrity | High | token 生成/持久化/清理、session/review 锁与状态流转、V30、S3 multipart 异常映射 | 未做 Redis 备份恢复演练；无需执行即可确认序列过期后的重复 token |
| Release | High | Maven clean verify、前端 type-check/build、Flyway V1–V30、Compose config、fat-JAR worker、环境清理 | 未执行生产发布、滚动升级或回滚 |
| Concurrency | High | Redis acquire/renew/release、DB token 条件、assign/finalize 锁顺序、接管分支、现有交错测试 | 未执行攻击性并发；使用静态 happens-before/状态序列复核 |

### Change Summary

- Total files changed: 31
- Lines added: 1667
- Lines deleted: 236
- Commits in range: 1
- Authors: wenbibuhaoqwq

### Change Categories

- New features / support components: 10 files
- Bug fixes / behavioral changes: 12 files
- Database migration: 1 file
- Configuration / deployment: 6 files
- Tests: 3 files
- Documentation / governance: 9 files

分类允许重叠；例如 V30 同时属于行为变更和迁移，Phase 7 文档同时属于规格追溯和测试契约。

### Risk Delta

- Existing risks fixed: 2（官方生产参数/专用卷已接线；正常超时路径可启动并终止媒体子进程）
- Existing risks partially fixed: 4（统一 lease/fencing、server 崩溃恢复、累计容量、资源边界测试）
- New or newly exposed risks: 4（Redis token ABA、server assign 竞态、direct 丢失 multipart 接管、持久临时卷崩溃遗留）
- Existing changed-path risks still open: 3（基础设施异常分类、终止确认、MinIO SDK 超时覆盖）

### Test Coverage Delta

- New/modified critical code with meaningful tests: 正常 lease 续租与 successor token、server 三个显式 failpoint、低阈值 worker 超时、包数/多轨、V30 迁移。
- New critical code without direct boundary tests: Redis 序列丢失/过期后的旧 owner、server 定稿与 assign 交错、已丢失 multipart 的 direct takeover、跨 JVM 崩溃后的临时卷清扫。
- Packaging coverage: fat-JAR `PropertiesLauncher` 已由本轮独立一次性命令通过，但项目自动测试只覆盖 Surefire classpath 分支。
- Deleted tests: 0。

### Approval Recommendation

**Request changes.** 第 1–4 项 High 必须在下一轮全部闭环并补确定性回归；第 5–9 项至少完成失败分类、终止确认、容量公式和两套 MinIO 客户端的生产契约后，才能重新评估 WS-3/U-002。

## 3. Top Risks

1. **High — Redis fencing token 可在序列过期或数据恢复后重复。** 旧 owner 与新 owner 可拿到相同数字，Redis 和数据库比较均无法识别 ABA。
2. **High — `SERVER_CHUNK` 与 assign 交错可永久卡 `MERGING`。** 评审进入 `REVIEWING` 后定稿抛终态异常，但 server catch 不收敛会话，重试又在租约前被拒绝。
3. **High — direct 接管时 multipart 丢失可永久卡 `MERGING`。** 接管分支的 `claimed=false` 使现有复位条件永远不执行。
4. **High — 持久临时卷没有崩溃遗留文件清扫。** JVM/容器退出绕过 finally 后，大文件会永久占用探测预算并使后续请求持续拒绝。
5. **Medium — worker、S3 或本地 I/O 故障被落成内容校验失败。** 可重试基础设施错误会把会话终态化为 `VALIDATION_FAILED`。
6. **Medium — 子进程强杀后没有确认其已经退出。** 5 秒等待结果被忽略，中断分支甚至不等待，孤儿进程仍可能占用文件和内存。
7. **Medium — 累计预留与实时可用空间双重计算已写入的字节。** 配置宣称 5GB 足够两份 2GB 探测，实际可能在第二任务到达时提前拒绝。
8. **Medium — 显式 MinIO 超时只配置 AWS S3 client。** `SERVER_CHUNK` 和通用对象读取使用的 `MinioClient` 仍走 SDK 默认超时。
9. **Medium — 生产 fat-JAR worker 分支没有自动化门禁。** 手工一次性复验通过，但打包入口回归不会被现有单测发现。

## 4. Detailed Findings

### Finding: Redis fencing token 会在序列过期或数据恢复后发生 ABA

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 视频定稿跨节点执行权与数据库 fencing
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoFinalizeSingleFlight.java:31-40`
  - Function / Module: `TOKEN_RETENTION_MILLIS`、`ACQUIRE_SCRIPT`
  - Relevant behavior: token 只由 Redis `INCR` 生成，序列 key 每次续命 7 天；key 过期、Redis 数据丢失或从不含该 key 的备份恢复后，下一 token 会重新从 1 开始。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoFinalizeSingleFlight.java:41-58,106-179`
  - Function / Module: lease `renewOrThrow`、`assertOwned`、`close`
  - Relevant behavior: owner、续租和释放都只比较可重复使用的数字 token，没有独立随机 owner nonce。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:418-425,542-548,1352-1369`
  - Function / Module: direct/server takeover、`assertSessionOwned`、`clearFinalizationToken`
  - Relevant behavior: 接管直接覆盖数据库当前 token，提交只做相等比较，完成后还把 token 清空；数据库没有不可回退的 high-water mark。
- Problem: fencing 的安全性要求新 owner 的 token 在系统生命周期内严格大于所有旧 token。当前单调性只依赖一个主动设置 7 天 TTL 的 Redis key，数据库既不生成 token，也不保留历史最大值。序列重置后，新旧 owner 可以同时持有相同数字。
- Why it matters: 一旦 ABA 发生，旧 owner 的 `assertOwned`、数据库 token 相等条件、续租和 `close` 都可能把新 owner 当成自己；旧 owner 可提交晚结果或删除/续租新 owner 的锁，fencing 失去设计目的。
- Realistic failure scenario: owner A 获得 token=1 后在外部 I/O 中暂停；Redis 数据丢失或序列 key 过期并重建，新 owner B 再获 token=1 并写入数据库。A 恢复后看到 Redis lock=1、DB token=1，所有相等校验通过；A 的 close 还可删除 B 的锁。
- Minimal fix: 在 `video_upload_session` 行内用原子 SQL 递增并永久保留 fencing high-water mark，认领时只接受严格更大的 token；Redis lock 值另用不可重复随机 owner id，续租/释放比较 owner id。
- Better long-term fix: 把“生成世代、认领执行权、校验当前 owner”封装成数据库权威的 finalize coordinator；Redis 仅作有期限的活跃互斥优化，丢失 Redis 不能降低世代。
- Regression test suggestion: owner A 取得 token 后删除/重建序列状态，让 B 接管；断言 B 的数据库世代严格更大，A 的 renew/assert/close/提交全部被拒绝，且 A 不能删除 B 的锁。
- Estimated effort: 1–2 days

### Finding: SERVER_CHUNK 与评审分配交错后会永久卡在 MERGING

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 服务端分片定稿、评审状态机与重传守卫
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:508-550`
  - Function / Module: `merge`、server claim
  - Relevant behavior: merge 在取得 lease 前先检查 `ensureReviewReuploadable`，claim 后把会话持久化为 `MERGING` 并执行外部 compose/探测。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:586-610,686-719,1465-1484`
  - Function / Module: server finalize transaction、`assign`、`ensureReviewReuploadable`
  - Relevant behavior: 探测窗口内，旧 `WAIT_REVIEW` 记录仍可被 assign 推进为 `REVIEWING`；随后定稿 upsert 抛 `DirectUploadTerminalException`，server catch 只重抛并保留 `MERGING`。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:193-194,316-317,512`
  - Function / Module: init、cancel、merge retry
  - Relevant behavior: init 只返回现有 MERGING，cancel 拒绝 MERGING，重试又在取得 lease/清理前被重传守卫拒绝。
- Problem: review 行锁只让 finalize 与 assign 最终落库有顺序，不保证输掉竞争的 server 定稿会收敛上传会话。direct 路径有 `failCompletedDirectSession`，server 路径没有等价终态或回滚逻辑。
- Why it matters: 一次合法的管理员分配动作即可把学生上传会话变成永久不可重试/不可取消状态；数据库、最终对象和评审记录之间长期不一致。
- Realistic failure scenario: server merge 认领会话并完成对象探测；管理员同时对旧 review 分配评委并提交，状态变为 `REVIEWING`；merge 的短事务锁到 review 后拒绝重传，异常向外抛出但会话仍是 `MERGING`。后续所有用户入口都无法恢复。
- Minimal fix: server catch 对确定性终态冲突执行带 token 条件的会话收敛：若最终对象已完成则置 `FAILED/VALIDATION_FAILED` 并保留原因；若未完成则安全回到可重试态。重试入口必须先允许 owner 接管并执行该恢复。
- Better long-term fix: 让 upload session 和 review 共享一个显式 finalize outcome 状态机；“评审已开始”是可持久判定的业务终态，而不是从任意路径抛出的异常类型。
- Regression test suggestion: 用现有安全 hook 在 server PROBED 后暂停，另事务 assign 旧 review，再释放 finalize；断言 session 不留 MERGING，重试/取消行为明确，review 不被覆盖且对象/元数据无重复。
- Estimated effort: 1 day

### Finding: direct 接管时 multipart 已丢失会永久卡在 MERGING

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 浏览器直传 multipart 的陈旧会话恢复
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:385-433`
  - Function / Module: `completeDirectUpload`
  - Relevant behavior: 初始状态为 `MERGING` 时只更新 token 接管，局部变量 `claimed` 仍为 false。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:450-493`
  - Function / Module: multipart list/complete 与异常恢复
  - Relevant behavior: multipart 不存在且最终对象不存在时，只有 `claimed && !objectExists` 才把会话复位为 `UPLOADING`；接管分支永远不满足该条件。
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/S3MultipartObjectService.java:84-105,109-139,323-328`
  - Function / Module: `listUploadedParts`、`completeMultipartUpload`、`minioFailure`
  - Relevant behavior: MinIO/S3 `NoSuchUpload` 被明确映射为 `MultipartUploadNotFoundException`，是对象生命周期清理后的正常可预期失败。
- Problem: 当前恢复测试只覆盖 multipart 仍存在的 stale MERGING。真实对象存储可按生命周期或人工运维清理未完成 multipart；此时接管者没有可完成对象，也没有把会话恢复到重新初始化/上传的路径。
- Why it matters: 进程崩溃与对象存储生命周期叠加后，学生永久失去该年度上传槽位，必须手工改库才能继续。
- Realistic failure scenario: owner A 将 direct 会话置为 MERGING 后退出；MinIO 清理未完成 multipart，最终对象尚未生成；owner B 接管并在 list/complete 收到 NoSuchUpload。catch 发现 `claimed=false`，不复位状态；init/cancel/再次 complete 均继续卡住。
- Minimal fix: 基于“当前 owner + 最终对象不存在 + multipart 不存在”判断恢复，而不是基于本次是否从 UPLOADING claim；用 token 条件将会话重置为可重新初始化的状态并清理失效 s3UploadId/parts。
- Better long-term fix: 为 direct 定稿记录明确的对象阶段（MULTIPART_ACTIVE / OBJECT_READY / PROBED），接管者按对象事实做幂等 reconciliation，而非依赖单次调用的局部布尔值。
- Regression test suggestion: 预置 MERGING 会话并确保 multipart/final object 都不存在，调用 complete 接管；断言会话离开 MERGING、旧 uploadId 不再复用、学生可安全重新 init。
- Estimated effort: 4–8 hours

### Finding: 持久探测临时卷没有崩溃遗留文件清扫

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 2GB 媒体探测临时文件生命周期与生产可用性
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:49-85`
  - Function / Module: `inspect`
  - Relevant behavior: 最终媒体临时文件只在当前 JVM 的 finally 中删除。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/ProcessVideoMediaWorker.java:25-68`
  - Function / Module: `inspect`
  - Relevant behavior: worker 结果文件同样只靠当前调用 finally；删除失败注释声称由“生命周期清理兜底”，仓库中没有对应清理器。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeProcessRunner.java:32-71`
  - Function / Module: `execute`
  - Relevant behavior: 参数文件也只靠当前进程 finally 清理。
  - File: `docker-compose.yml:95-104,121-122,154-158`
  - Function / Module: backend volume/config
  - Relevant behavior: 生产临时目录挂载为持久命名卷，容器重启不会删除遗留文件；默认两并发、每任务最大 2GB。
- Problem: finally 只能处理正常返回和可捕获异常，无法处理 JVM 崩溃、容器强制终止、宿主掉电。持久卷使遗留大文件跨重启保留，但没有启动扫描、TTL reaper、owner 标记或空间 reconciliation。
- Why it matters: 两个 2GB 探测任务在崩溃时即可留下约 4GB；文档建议的 5GB 卷只剩保底 1GB，后续所有探测会被容量守卫持续拒绝，自动崩溃恢复名义上存在但实际无法继续工作。
- Realistic failure scenario: 两个探测下载接近完成时后端容器被重启；finally 未运行，命名卷保留两个 `video-probe-*.mp4`。新容器的进程内 reservation 从 0 开始，但真实 usable 已降至 min-free，任何新任务都失败，且没有自动回收路径。
- Minimal fix: 临时文件名写入 uploadId/owner nonce 和创建时间；启动时及定时任务只清理不属于当前活跃 owner、超过安全 TTL 的已知前缀文件，并记录清理指标。容量守卫准入前可触发一次有界 reconciliation。
- Better long-term fix: 为临时工件建立持久 manifest/lease，文件创建与 owner 状态可对账；生产使用受容量限制的专用挂载并配套告警、清理 runbook 和启动自愈。
- Regression test suggestion: 在临时目录预置过期已知前缀文件和一个仍活跃文件，启动清理器；断言只删除过期孤儿、可用空间恢复、活跃文件不受影响。无需真实杀进程即可确定性覆盖。
- Estimated effort: 1–2 days

### Finding: 基础设施故障被错误持久化为视频内容不合法

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 媒体探测错误分类与重试语义
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeProcessRunner.java:48-62`
  - Function / Module: `execute`
  - Relevant behavior: 进程启动 I/O 错误或线程中断抛业务异常。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/ProcessVideoMediaWorker.java:37-59`
  - Function / Module: `inspect`
  - Relevant behavior: 非零退出、结果文件 I/O 和任意 RuntimeException 都被折叠为 `TrackInspection.invalid`。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:49-76`
  - Function / Module: `inspect`
  - Relevant behavior: 对象下载、本地临时盘和工作进程异常可进入统一“不可解析媒体”结果。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:1625-1629`
  - Function / Module: 媒体验证结果持久化
  - Relevant behavior: invalid 会把会话/评审落成终态校验失败。
- Problem: 内容错误（非法 MP4、编码不允许）与平台错误（java 无法启动、S3 超时、磁盘 I/O、线程中断）没有类型边界，所有失败都可能变成学生可见的“视频不合法”。
- Why it matters: 短暂基础设施故障会错误终态化业务记录，阻止自动重试并误导用户；运维也无法从状态和指标区分内容质量与平台故障。
- Realistic failure scenario: 部署镜像缺少可执行 Java 或临时卷短暂只读；worker 无法启动，父进程返回 invalid，服务将本来合法的视频标为 `VALIDATION_FAILED`，学生只能重新上传。
- Minimal fix: 定义 `InvalidMedia`、`ProbeTimeout`、`InfrastructureFailure` 三类结果；只有确定内容失败进入校验失败，基础设施失败保留可接管 MERGING 或转为显式 retryable 状态并告警。
- Better long-term fix: 统一 finalize error taxonomy，并让状态机、API 错误码、重试策略和指标共享同一枚举。
- Regression test suggestion: 注入一个抛启动 I/O 异常的 runner 和一个返回非法内容的 worker；断言前者不写 VALIDATION_FAILED、后者稳定写内容失败。
- Estimated effort: 4–8 hours

### Finding: 子进程强杀后没有确认已经退出

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 媒体 worker 硬时限与资源释放
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeProcessRunner.java:48-60`
  - Function / Module: `execute`
  - Relevant behavior: 超时后调用 `destroyForcibly` 并等待 5 秒，但忽略第二次 `waitFor` 的 boolean；中断分支强杀后不等待。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/business/video/support/VideoProbeWorkerBoundaryTest.java:23-36`
  - Function / Module: worker timeout test
  - Relevant behavior: 测试只断言 `timedOut`、exitCode 和耗时，没有保留/断言子进程已经死亡。
- Problem: “发出强杀请求”不等于“进程已终止”。当前方法即使子进程仍存活也返回，外层随后释放并发 permit、预留字节并尝试删除其仍可能使用的文件。
- Why it matters: 罕见的终止失败会破坏资源计数，形成超配、文件锁冲突或孤儿 JVM；硬时限承诺没有完整兑现。
- Realistic failure scenario: Windows/容器运行时暂时未在 5 秒内完成终止；runner 返回 timedOut，guard 释放槽位并启动新任务，旧 worker 仍占用堆和媒体文件，实际并发超过配置。
- Minimal fix: 检查二次 `waitFor`；仍存活时抛基础设施错误并保留资源/登记 orphan，或升级到可追踪的进程清扫。中断分支也要有界等待并记录 PID。
- Better long-term fix: 引入受管理的 worker supervisor，统一记录 PID、owner、deadline、终止结果和孤儿回收指标。
- Regression test suggestion: 把 Process 创建封装为可替换工厂，模拟拒绝在 grace 内终止的进程；断言 runner 不报告已安全释放，且触发 orphan 处理。
- Estimated effort: 4–8 hours

### Finding: 累计预留与实时可用空间双重计算已写入字节

- Severity: Medium
- Confidence: High
- Category: Performance
- Status: Confirmed
- Affected area: 并发媒体探测磁盘准入
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeCapacityGuard.java:59-69`
  - Function / Module: `acquire`
  - Relevant behavior: 先把全部活跃任务最大字节计入 `totalReserved`，再与当前 `usable - minFree` 比较；当前 usable 已经因活跃任务实际写入而下降。
  - File: `.env.example:47-53`
  - Function / Module: 生产容量说明
  - Relevant behavior: 文档宣称卷容量至少 `MAX_RESERVED_BYTES + MIN_FREE_BYTES` 即满足配置。
  - File: `docs/phase-14-非功能部署验收.md:28-31`
  - Function / Module: 媒体探测生产资源契约
  - Relevant behavior: 默认 4GB 总预留 + 1GB 保底，预期支持两个 2GB 活跃任务。
- Problem: `totalReserved` 包含“已写入 + 尚未写入”的最大预算，而实时 usable 已扣除已写入部分；直接比较会把已写入字节计算两次。逻辑安全但比声明更保守。
- Why it matters: 正好按文档配置的卷不能稳定兑现 `MAX_CONCURRENT=2`。任务到达时序不同会产生不一致拒绝，吞吐和运维容量规划失真。
- Realistic failure scenario: 5GB 卷上任务 A 已写 2GB，当前 usable 约 3GB；任务 B 请求 2GB 后 totalReserved=4GB，而 usable-minFree=2GB，B 被拒绝。实际上最终 2GB+2GB+1GB 正好可容纳。
- Minimal fix: 仅预留每个任务“预计总量减已落盘量”的未来增长，或用启动时/基准容量与总 reservation 比较；确保公式不会同时扣减同一字节。
- Better long-term fix: 守卫维护每个 owner 的 expected/written 状态并与文件 manifest 对账，文档给出确定的容量公式和时序无关不变量。
- Regression test suggestion: 使用会随写入下降的 fake disk space：A 已写 2GB 后 B 到达，在 5GB 总盘、4GB max reserved、1GB min free 下仍应允许；超过预算的第三任务才拒绝。
- Estimated effort: 4–8 hours

### Finding: 显式 MinIO 超时没有覆盖 SERVER_CHUNK 使用的 MinioClient

- Severity: Medium
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: 对象存储超时配置与两种上传模式
- Evidence:
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/config/MinioProperties.java:22-26`
  - Function / Module: MinIO timeout properties
  - Relevant behavior: 新增 connection/read timeout 配置。
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/config/MinioConfig.java:32-50`
  - Function / Module: `minioClient`、`s3Client`
  - Relevant behavior: 超时只设置在 AWS `S3Client` 的 `UrlConnectionHttpClient`；通用 `MinioClient` 仍只设置 endpoint/credentials。
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/config/MinioConfig.java:75-81`
  - Function / Module: bucket initializer
  - Relevant behavior: 初始化时另建的 MinioClient 同样没有显式超时。
  - File: `docker-compose.yml:93-104`
  - Function / Module: backend environment
  - Relevant behavior: 生产入口暴露同一组 MINIO 超时，给运维造成全路径均受控的预期。
- Problem: direct multipart 使用 AWS S3 client，兼容 server chunk 上传/compose/通用对象读取则仍使用 MinIO Java SDK client。新参数只覆盖前者，不能兑现“对象读取永久占用探测任务”的全路径保护。
- Why it matters: 在 MinIO 网络半开或响应卡住时，server 路径可越过 worker 墙钟前置阶段，长期持有 finalize lease/容量；生产参数看似可调但实际无效。
- Realistic failure scenario: MinIO 接受连接但 server-chunk compose 或对象读取不返回；S3 socket timeout 不参与该调用，任务持续占用会话和资源，直到 SDK 默认行为结束。
- Minimal fix: 为 `MinioClient` 注入带相同 connect/read/call timeout 的 OkHttpClient，并让 bucket initializer 复用受管 bean 或同一工厂。
- Better long-term fix: 把对象存储客户端构造集中到一个配置工厂，所有 API 路径共享可验证的 timeout/retry budget。
- Regression test suggestion: 单元检查两套 client 的底层 HTTP timeout，或以本地可控延迟服务验证两种上传模式都在配置期限内返回 retryable infrastructure error。
- Estimated effort: 2–4 hours

### Finding: 生产 fat-JAR worker 分支没有自动化门禁

- Severity: Medium
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: 打包产物中的媒体子进程启动
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeProcessRunner.java:80-91`
  - Function / Module: worker command construction
  - Relevant behavior: 生产 fat JAR 使用 `org.springframework.boot.loader.launch.PropertiesLauncher`，Surefire/IDE 使用普通 classpath，属于两条不同启动分支。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/business/video/support/VideoProbeWorkerBoundaryTest.java:20-89`
  - Function / Module: worker boundary tests
  - Relevant behavior: Maven 测试进程只覆盖测试 classpath 启动，没有在 `verify` 后对打包 JAR 执行 worker。
  - Runtime evidence: `java -Dloader.main=...VideoProbeWorkerMain -cp platform-boot/target/teacher-cert-platform.jar org.springframework.boot.loader.launch.PropertiesLauncher ...`
  - Function / Module: 本轮独立一次性 fat-JAR 验证
  - Relevant behavior: 对仓库短视频样本返回 exit 0、`valid=true`、H264、3 秒、3 帧，证明当前构建可用，但该证据不由项目门禁自动重复。
- Problem: 关键生产专属分支只能靠手工命令证明。后续 Boot loader、打包布局、模块依赖或 Dockerfile 变更可能让生产 worker 无法启动，而单测仍全绿。
- Why it matters: 该分支失效后所有生产视频都会被当前错误映射标为内容失败；CI 无法在发布前发现。
- Realistic failure scenario: Spring Boot 打包插件或 loader 主类名称变化；Surefire classpath 测试继续通过，生产镜像启动 worker 时才报 class not found。
- Minimal fix: 在 Failsafe/package 后增加一个自然退出的 smoke IT，用构建出的 fat JAR 和小型合法样本调用 PropertiesLauncher，断言结果文件字段。
- Better long-term fix: 把 worker 作为独立、版本化可执行 artifact，并在 CI/镜像构建中运行契约 smoke test。
- Regression test suggestion: 新增 Maven verify 阶段 `FatJarVideoProbeWorkerIT`，直接执行目标 JAR；测试必须有硬超时、临时目录清理和 exit/result 双断言。
- Estimated effort: 2–4 hours

## 5. Security Concerns

- Coverage: Medium
- Inspected evidence: ProcessBuilder 参数列表、固定 worker 主类、对象存储端点/凭据接线、普通响应边界、异常日志和本轮变更文件。
- Exclusions / limits: 遵守用户明确约束，未执行畸形载荷、模糊测试、压测、漏洞利用、攻击性并发或外部网络探测。

本轮未发现新增鉴权绕过、路径注入、命令注入、密钥落库或敏感响应泄漏。worker 命令由固定 Java 可执行、固定 loader/main class 和结构化参数列表构造，没有经过 shell；用户提供的文件名也不进入命令。媒体仍按服务端对象读取和实际探测决定结果，客户端声明不能直接绕过校验。

安全覆盖受限于动态对抗测试禁区，因此 8.0 表示“静态边界良好且无新增发现”，不表示完成最终安全审计。第 1 项 fencing ABA 首要影响是数据一致性与旧 owner 提交，不据此重复计为独立安全发现。

## 6. Stability Concerns

- Coverage: High
- Inspected evidence: complete/merge 全状态机、Redis Lua、DB token 条件、multipart 异常映射、临时文件 finally、生产卷、子进程 timeout/interrupt、两套 MinIO client。
- Exclusions / limits: 未实际杀 JVM、清空 Redis 或制造网络半开；所有 High 都由可达且无分支歧义的状态序列确认。

稳定性是本轮主要阻断面：

- 第 1 项使 lease/fencing 在 Redis 序列重置后失去身份区分。
- 第 2、3 项分别让 server 和 direct 在常见外部状态变化后永久停留 `MERGING`。
- 第 4 项让“进程崩溃可接管”被持久临时盘耗尽反向破坏。
- 第 5、6、8 项使基础设施故障难以正确重试，或无法保证资源已真正释放。

现有三处 server hook 测试只模拟业务方法内抛异常，finally 仍会运行；它们不能代替跨 JVM 崩溃后的文件生命周期验证。正常续租测试也只证明 Redis 序列仍存在时 token 递增，不证明 fencing 的持久单调性。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: 容量守卫算法、默认 2GB/两并发/4GB 总预留/1GB 保底配置、临时文件下载路径、子进程堆上限和前端生产构建。
- Exclusions / limits: 未进行基准、压力、2GB 实传或长期 soak test。

第 7 项是安全侧的过度保守而非超卖：不会打穿 min-free，却会因双重计数降低可用并发。生产文档的容量公式与实际准入时序不一致，运维若按 5GB 配置，最坏时只能稳定处理一个 2GB 探测。

子进程最大堆、最大包数、最大并发和总预留均已显式配置，是实质进步。前端构建仍报告 `echarts` 与 `naive` 超过 900kB 的既有 chunk 警告，与本 WS 增量无直接因果，本报告不升级为新 finding。

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: fresh schema Maven clean verify、Surefire/Failsafe XML、Phase7VideoReviewIT、VideoProbeCapacityGuardTest、VideoProbeWorkerBoundaryTest、V29/V30 迁移输出、前端门禁、Compose config、fat-JAR 一次性验证。
- Exclusions / limits: 未做真实 2GB、非允许编码、不可解码首帧、多节点或攻击性并发；这些限制已在 Phase 7 清单中诚实保留。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| Flyway V1–V30 fresh schema | High | 新增列和完整迁移链回归 | Keep |
| V29 部分 DDL 恢复 + V30 后续迁移 | High | 迁移重跑和版本推进 | Keep |
| Phase 7 正常上传/评审/三个 server hook | Medium | finally 内异常路径有价值，但不覆盖进程级崩溃和外部状态交错 | Extend |
| Redis lease 续租与 successor | Medium | 只覆盖序列连续存在，ABA 会逃逸 | Extend |
| 容量守卫单测 | Medium | 使用恒定 usable，双重计数时序会逃逸 | Extend |
| worker timeout 单测 | Medium | 证明超时返回，不证明进程已死亡 | Extend |
| fat-JAR worker | Medium | 当前手工实测通过，CI 不自动执行 | Automate |
| 真实 2GB/编码/首帧 | None | Phase 7 两个宽泛验收项仍不可勾选 | Add |

### Valuable Tests

- 独立全新依赖环境的 277/277 和 Flyway V1–V30 证明迁移、模块装配及现有业务回归没有被第三轮破坏。
- Phase 7 的 CLAIMED / OBJECT_READY / PROBED hooks 对同 JVM 的异常恢复有真实价值，能保护稳定 object key 和幂等落库。
- `maxPackets=1`、多视频轨、250ms 阻塞 worker、容量 lease 释放都是低风险、确定性的边界测试，符合用户不执行攻击性测试的要求。
- 本轮直接调用打包 JAR 的 `PropertiesLauncher` 返回合法媒体结果，确认当前生产分支确实可用。

### Suspicious Tests

- `VideoProbeWorkerBoundaryTest` 的 timeout 用例把“方法及时返回”当成“子进程已经退出”，断言不足。
- `VideoProbeCapacityGuardTest` 固定返回同一 usable space，无法模拟活跃任务已经写入磁盘后第二任务到达的实际公式。
- lease successor 测试先正常关闭 owner 再获取更高 token，没有覆盖序列 key 过期/丢失和旧 owner 恢复。

### Missing Tests

- Redis 序列重置后的 token ABA 与旧 owner close/renew/提交拒绝。
- server PROBED 与 assign 的确定性交错，及异常后的 session 收敛。
- stale direct MERGING + multipart/final object 均不存在的恢复。
- 启动/定时临时文件 reaper 对过期孤儿与活跃文件的区分。
- 进程启动 I/O、S3/磁盘异常的 retryable 分类。
- 强杀 grace 结束后仍存活进程的处理。
- 动态 usable space 下的容量公式。
- Maven verify 对 fat-JAR worker 的自动 smoke。

## 9. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: application.yml/dev/prod、`.env.example`、生产 Compose、VideoProbeProperties 校验、MinioProperties/MinioConfig、Phase 14 运维说明。
- Exclusions / limits: 未启动完整生产栈；只执行 `docker compose --env-file .env.example -f docker-compose.yml config --quiet`。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|--------------------|
| SchemaValidation | 0 | probe lease/renew/heap/packet properties | 保持现有启动校验 |
| UnsafeDefault | 1 | 临时卷容量说明与实际双重计数 | 修正算法并明确容量公式 |
| EnvironmentSeparation | 0 | dev/prod/application/Compose | 参数已完整转发 |
| SecretConfig | 0 | MinIO/JWT/admin bootstrap | 本增量未引入明文密钥 |
| FeatureFlag | 0 | direct upload mode | 两种模式仍受支持并有文档 |
| ConfigDocs | 2 | MinIO timeout 覆盖、临时卷清理/容量 | 说明两套 client 与清理 runbook |

第三轮已把 probe 并发、总预留、min-free、墙钟、包数、lease、续租、worker heap 和 MinIO timeout 送入官方 Compose/.env，解决上一轮“代码可配但生产不可配”的问题。剩余配置缺口不是缺少键，而是键的实际作用范围/容量语义与文档不一致。

## 10. Data Integrity Analysis

- Coverage: High
- Inspected evidence: Redis token Lua、V30、session token 写入/清空、direct/server takeover、review 行锁、assign、multipart 异常与现有 IT。
- Exclusions / limits: 未执行 Redis 数据破坏或多节点运行；ABA 与两个永久 MERGING 序列可从代码直接证明。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|--------------------|--------------------|
| TransactionBoundary | 1 | server finalize 输给 assign 后 session 必须收敛 | 在同一权威状态机提交 outcome |
| Idempotency | 1 | direct 丢失 multipart 后应可重新初始化 | 按对象事实 reconciliation |
| ConcurrencyConsistency | 2 | fencing 世代严格递增；review/session 一致 | DB high-water + owner nonce；交错测试 |
| MigrationSafety | 0 | V30 fresh/recovery | 保持新增版本、勿改已发布脚本 |
| InvariantValidation | 1 | retryable 与 invalid media 分类 | 强类型错误与状态规则 |
| BackupRestore | 1 | Redis 恢复不能复用旧 token | 不把持久 fencing 单调性托付给 Redis |
| Reconciliation | 2 | stale multipart、孤儿 temp | 增加对象/临时工件对账 |

V30 本身是结构安全的新增迁移，fresh schema 和 V29 恢复后继续迁移均成功；问题在于 `finalization_token` 只存“当前值”并在完成后清空，无法承担永久 fencing high-water。修复应新增迁移或复用明确的世代列，不应修改已执行的 V30。

## 11. Release Concerns

- Coverage: High
- Inspected evidence: 后端 clean verify、前端 type-check/build、生产 Compose config、fat-JAR worker、Dockerfile、V30、环境清理与端口检查。
- Exclusions / limits: 未执行生产部署、滚动升级、回滚和真实对象存储故障演练。

发布门禁结果：

- 后端：Surefire 125/125 + Failsafe 152/152 = **277/277**，0 failure/error/skip。
- Phase 7：**31/31**。
- Flyway：全新 schema 从 V1 成功迁移到 V30；V29 恢复场景随后也成功到 V30。
- 前端：type-check、production build 通过；仅既有大 chunk warning。
- 配置：生产 Compose + `.env.example` 展开通过。
- 打包：fat-JAR `PropertiesLauncher` worker 对合法短视频返回 exit 0 和有效 H264 结果。
- 清理：隔离 `teacher-cert-ws03` 容器、网络和数据卷已删除；相关服务端口无监听，未启动常驻应用。

这些门禁不足以覆盖第 1–4 项 High，因此发布建议仍是 **阻断**。下一轮不需要重复扩大功能范围，应先把恢复不变量转成确定性测试，再跑同一套全量门禁。

## 12. Concurrency Analysis

- Coverage: High
- Inspected evidence: Redis acquire/renew/assert/release Lua、token 存储、direct/server claim/takeover、review FOR UPDATE、assign、session 条件更新和现有并发 hooks。
- Exclusions / limits: 未执行攻击性并发或多节点压力；采用逐分支状态序列和已有安全 hook 证据。

正常 Redis 数据连续存在时，lease 续租和 token 条件更新能拒绝不同 token 的旧 owner；server 稳定 object key 也能避免三处同 JVM 异常后的重复业务记录。这部分设计方向正确。

真正的并发缺口集中在“身份世代”和“跨聚合状态”：

- token 只比较相等而没有不可回退世代，Redis 重置即可 ABA。
- review 行锁只保护 review 最终更新，没有保证输掉 assign 竞争的 upload session 收敛。
- direct takeover 的恢复分支依赖局部 `claimed`，没有依据当前 owner 和对象事实做幂等判断。

下一轮应优先把这三种交错写成低风险确定性测试，不需要压测、fuzz 或攻击性载荷。

---

## 13. Principles Compliance

第三轮遵守了若干良好原则：媒体工作进程、容量守卫、磁盘空间和 finalize hook 被拆成明确组件；配置参数集中且有启动校验；V30 采用新增迁移而非改写已发布脚本；真实依赖集成测试和治理文档同步较完整。

主要违例不是代码风格，而是状态所有权不完整：Redis、数据库 session、对象存储 multipart/final object、review 和临时卷分别拥有部分真相，却缺少单一 reconciliation 协议。局部 finally、局部布尔值和相等 token 无法替代持久不变量。

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Fail-Fast / Fail-Accurately | 2 | Medium | worker/对象存储异常被折叠为 invalid；强杀未确认 |
| Single Source of Truth | 3 | High | fencing 世代、upload/review 状态、对象阶段 |
| Idempotency | 2 | High | direct multipart 丢失、server assign 竞态 |
| Resource Lifecycle Ownership | 2 | High | persistent temp files、orphan worker |
| Configuration Truthfulness | 2 | Medium | MinIO timeout 覆盖、磁盘容量公式 |
| Test the Production Path | 1 | Medium | fat-JAR PropertiesLauncher 仅手工验证 |

### Principles Respected

- 受限堆子进程把第三方媒体解析与主 JVM 隔离，方向正确。
- ProcessBuilder 使用参数列表而非 shell 字符串，避免命令拼接。
- V30 作为独立版本追加，符合 Flyway 不改已发布脚本的要求。
- 生产资源参数已进入 Compose/.env，配置不再只存在于代码默认值。
- 真实 MySQL/Redis/MinIO 全量门禁、前端构建和打包 worker 复验提供了比 mock 更真实的正常路径信心。

---

## 14. Recommended Fix Order

### Fix Immediately

1. 把 fencing 世代移到数据库持久 high-water mark，Redis owner 改为随机 nonce；补 ABA 回归。
2. 收敛 `SERVER_CHUNK` 输给 assign 后的 session 状态，确保任何异常都不永久留 MERGING。
3. 修复 direct stale MERGING 在 multipart/final object 均不存在时的 recovery。
4. 增加持久临时卷 startup/scheduled orphan reaper 和明确的 owner/TTL 规则。

### Fix Before Stable Release

1. 区分内容失败、超时和基础设施失败，基础设施错误不得终态化为学生内容不合法。
2. 确认 `destroyForcibly` 后进程确实退出，并对 orphan 建立处理路径。
3. 修正容量双重计数，使文档容量公式与配置并发一致。
4. 给 `MinioClient` 和 AWS `S3Client` 同时配置显式 timeout。
5. 把 fat-JAR worker smoke 纳入 Maven verify/CI。

### Schedule Later

- 真实 2GB 续传、非允许编码、不可解码首帧和多节点接管证据仍按 Phase 7 未完成项排期。
- 为 finalize、probe timeout、temp cleanup 增加结构化指标、告警和操作手册。
- 拆分 `VideoReviewServiceImpl` 中上传定稿与评审结算职责，降低跨聚合状态爆炸。

### Ignore for Now

- 前端既有 `echarts`/`naive` 大 chunk warning 与本次 WS-3 恢复阻断无直接关系，可在性能专项统一处理。
- 不为本轮增加攻击性测试、fuzz 或压测；用户已明确要求避免此类操作，当前发现可用安全确定性测试覆盖。

## 15. Quick Wins

- 将 direct catch 的复位条件从 `claimed` 改为“当前 token owner + final object 不存在 + NoSuchUpload”，并清理失效 multipart 元数据。
- 检查强杀后的第二次 `waitFor` 返回值，记录 PID 和终止失败。
- 给 `MinioClient` 注入与 S3 client 相同的 timeout 配置工厂。
- 为 worker 失败增加最小错误枚举，避免所有 RuntimeException 都返回 `TrackInspection.invalid`。
- 在 Maven verify 后复用当前短视频样本运行 fat-JAR worker smoke。

## 16. Long-term Refactor Plan

1. **统一 Finalization Coordinator**
   - Motivation: complete/merge 当前重复并分叉 claim、对象恢复、探测、review upsert 和失败收敛。
   - Approach: 以数据库 session 行为权威，持久记录 generation、owner、object stage、probe outcome；Redis 只做有期限互斥。
   - Risk: 状态迁移涉及现有会话兼容，需要新增迁移和清晰的旧状态映射。
   - Testing strategy: 对每个 stage 做 takeover、重复调用、owner 丢失、assign 交错和对象事实组合的表驱动 IT。

2. **建立临时工件与 worker supervisor**
   - Motivation: finally 无法覆盖进程/容器崩溃，持久卷和孤儿子进程缺少 owner。
   - Approach: 工件 manifest 记录 uploadId/owner/PID/created/deadline；启动和定时 reconciliation 清扫过期孤儿，暴露指标。
   - Risk: 错删活跃文件会破坏探测，必须以 owner lease 和年龄双重保护。
   - Testing strategy: 使用临时目录和假时钟覆盖活跃、过期、未知前缀、清理失败及重复清扫。

3. **统一错误分类与重试契约**
   - Motivation: 内容错误和平台错误混为一谈，导致不可重试终态。
   - Approach: 定义 sealed/enum outcome：VALID、INVALID_MEDIA、TIMEOUT、INFRASTRUCTURE_FAILURE、OWNERSHIP_LOST；由状态机统一映射 API/DB/指标。
   - Risk: 前端提示与历史 validation message 兼容。
   - Testing strategy: 每类 outcome 至少一个服务层反例，断言状态、是否可重试、对象保留和审计日志。
