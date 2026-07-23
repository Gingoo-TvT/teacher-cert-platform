# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform — WS-3 第四轮整改增量
**Audit mode:** incremental + security + stability + performance + testing-authenticity + release + configuration + data-integrity + concurrency
**Date:** 2026-07-23
**Reviewer:** OpenAI Codex（GPT-5，独立复核）

---

## 1. Executive Summary

本轮冻结并复核 `df22e5b..ee190f3`：1 个提交、44 个变更文件、增加 2995 行、删除 228 行。结论为 **CHANGES REQUESTED**。第三轮报告的 4 个 High 与 5 个 Medium 均有实质整改：数据库永久世代与随机 Redis owner 消除了 token ABA；server/assign、direct 丢失 multipart 和 server 源分片丢失均能离开 `MERGING`；持久探测卷已有 owner heartbeat、启动/周期清扫与独占锁；进程死亡确认、原子容量快照、两套 MinIO 客户端超时和 fat-JAR 自动门禁也已落地。旧 9 项可以按原问题口径关闭。

第四轮仍新暴露 1 个 High、1 个 Medium 与 2 个 Low。High 是媒体 worker 把 JCodec 在解析损坏 MP4 内部结构时抛出的 `IOException` 留给进程顶层，父进程再把任意非零退出统一判为可重试基础设施故障；最终对象已存在时 direct/server 都会永久保留 `MERGING`，与 Phase 7 “伪 MP4 校验失败并可重传”的契约冲突。Medium 是终态失败后的最终对象清理只调用一次 `removeObject`，失败后没有持久重试；SERVER 还存在旧 owner 的迟到 compose 在 successor 删除后复活稳定 key 的窗口，可遗留最大约 2GB 的未登记对象。两个 Low 分别是临时工件有界扫描缺少游标而可能饿死尾部孤儿，以及非容器/裸机父 JVM 崩溃后 worker 监管状态不可恢复。

独立验证严格遵守用户安全限制：没有生成或执行畸形媒体、fuzz、破坏性 Redis/MinIO 故障、竞争压测或攻击性并发。独立运行了干净构建与 17 个安全白名单测试，全部通过；前端 type-check/build、dev/prod Compose 解析、MinIO 依赖树和 `git diff --check` 均通过。整改者的全量 **299/299** XML 证据在独立清理构建前已核对为 0 failure/error/skip，且报告时间晚于对应源码，但它仍属于整改者自测，不替代本轮缺失边界的独立证明。

### Score Dashboard

```text
Security        █████████░  8.5  A   未见新增越权或凭据问题；未登记对象带来有限的数据保留风险
Stability       ██████░░░░  5.5  B   损坏媒体可永久卡 MERGING，裸机场景仍有少量恢复债
Performance     ███████░░░  7.0  A   容量公式已闭环，但未登记大对象与扫描饥饿可持续占用存储
Testing         ███████░░░  6.5  B   299 项自测可信且 17 项独立白名单通过，关键新边界仍缺回归
Maintainability ████████░░  7.5  A   组件职责显著清晰，异常域与对象生命周期仍未完全显式化
Design          ██████░░░░  6.0  B   fencing 已正确持久化，但内容/基础设施分类和清理尚非持久状态机
Release         ██████░░░░  5.5  B   V31 切换协议与构建门禁可用，1 个 High 阻止稳定发布
Configuration   █████████░  8.5  A   双客户端超时、桶初始化、专用卷及 Compose 契约均闭环
Data Integrity  ██████░░░░  6.0  B   DB 世代正确，但失败会话与对象存储仍可能永久不一致
─────────────────────────────────────
Overall         ███████░░░  6.8  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based. See `rubrics/scoring.md` for anchor descriptions.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 1 | 1 | 0 |
| Medium | 1 | 1 | 0 |
| Low | 2 | 0 | 2 |
| Info | 0 | 0 | 0 |
| **Total** | **4** | **2** | **2** |

## 2. Project Map

第四轮增量围绕四条持久边界展开：

1. `video_upload_session.finalization_token` 由 V31 固化为数据库永久 high-water；`VideoFinalizeSingleFlight` 的 Redis lease 使用随机 UUID owner。
2. `VideoReviewServiceImpl` 在 direct/server 认领事务内递增数据库世代，在完成与失败事务中同时校验世代和 Redis owner，并为已知不可恢复状态收敛到 `FAILED`。
3. `VideoProbeTempArtifactManager` 统一管理媒体、参数和结果工件，通过 heartbeat、TTL、目录锁和原子磁盘快照支撑崩溃恢复与准入。
4. `VideoProbeWorkerMain` 在子 JVM 内解析媒体并输出结构化结果；`ProcessVideoMediaWorker` 将进程退出、结果协议与内容结论映射回主业务状态机。
5. `MinioConfig` 为 MinioClient 与 AWS S3Client 设置有界超时，`FatJarVideoProbeWorkerIT` 将生产打包入口纳入 Maven verify。

当前最高风险边界已经从 fencing 本身转移到两处跨域翻译：第三方解析器异常如何区分“内容损坏”和“基础设施故障”，以及数据库已进入终态后对象存储清理如何获得持久、可重试的 reconciliation。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental scope | High | `git diff/log/shortstat df22e5b..ee190f3`；逐文件检查 44 个变更文件与关键未变调用方 | 不重复审计与 WS-3 增量无关的全仓业务 |
| Security | Medium | 对象 key、敏感视频残留、进程参数、凭据/端点配置和异常日志静态检查 | 按用户要求未执行畸形载荷、模糊测试、漏洞利用或攻击性并发 |
| Stability | High | direct/server 全状态机、DB/Redis owner、媒体 worker、临时工件、进程终止和对象清理 | 未杀 JVM、破坏 Redis/MinIO、阻塞进程或制造异常媒体 |
| Performance | Medium | 2GB 上限、原子容量快照、清扫上限、对象残留和子进程准入 | 未执行压力、容量或基准测试 |
| Testing Authenticity | High | Maven 配置、CI、Surefire/Failsafe XML、17 个独立白名单测试、V31/fat-JAR/MinIO 配置测试 | 未独立重跑畸形媒体、破坏性故障与竞争交错用例 |
| Configuration | High | `.env.example`、application dev/prod、两份 Compose、MinIO 两套 SDK、桶初始化和 Phase 14 | 未启动生产应用容器或做真实发布 |
| Data Integrity | High | V31、认领/提交/失败事务、稳定 object key、`file_object` 登记和孤儿扫描能力 | 未执行数据库恢复或对象存储故障注入 |
| Release | High | 后端 package/定向 verify、前端 type-check/build、Compose config、fat-JAR IT 和发布停机协议 | 未执行生产发布、回滚或新旧二进制混部 |
| Concurrency | High | owner/generation happens-before、assign/finalize、旧/新 server compose 与终态清理序列 | 未动态触发竞争；结论来自代码顺序和持久状态不变量 |

### Change Summary

- Total files changed: 44
- Lines added: 2995
- Lines deleted: 228
- Commits in range: 1
- Audited head: `ee190f3a5a8d04a2ca479a6ec0391f6223776eab`
- Authors: wenbibuhaoqwq

### Previous Finding Closure Matrix

| 第三轮 finding | 第四轮复核 |
|---|---|
| Redis token 过期/恢复后 ABA | **Closed** — 随机 Redis owner + 数据库事务内永久递增世代 |
| SERVER_CHUNK 输给 assign 后永久 MERGING | **Closed** — 当前 owner/generation 下收敛到 FAILED |
| direct 接管时 multipart/final object 均丢失 | **Closed** — 清理 direct 状态并进入可重新初始化的 FAILED |
| 持久 probe 卷无崩溃孤儿清扫 | **Closed（核心）** — heartbeat、TTL reaper、目录独占锁已落地；尾部扫描公平性另列 Low |
| 基础设施错误误判内容失败 | **Closed（原方向）** — 进程/结果/I/O 默认可重试；反方向的解析异常误判另列新 High |
| 强杀后未确认退出 | **Closed（官方容器部署）** — 强杀后等待并登记活孤儿；裸机父 JVM 崩溃另列 Low |
| 磁盘预留双重计数 | **Closed** — 活跃写入与 usable 快照在同一锁内核算未来增长 |
| MinioClient 缺显式超时 | **Closed** — MinioClient/AWS S3Client 均受正数超时配置约束 |
| fat-JAR worker 缺自动门禁 | **Closed** — Failsafe 在 verify 阶段打包后运行 `PropertiesLauncher` |

### Test Coverage Delta

- 整改者全量证据：Surefire **140/140**、Failsafe **159/159**，合计 **299/299**；Phase 7 **36/36**。独立复核在清理构建前核对 XML 为 0 failure/error/skip，源码时间早于报告时间。
- 独立安全白名单：`MinioConfigTest` 3、`ProcessVideoMediaWorkerTest` 2、`VideoProbeCapacityGuardTest` 5、`VideoProbeTempArtifactManagerTest` 3、`VideoProbeWorkerBoundaryTest` 2、`V31FinalizationHighWaterMigrationIT` 1、`FatJarVideoProbeWorkerIT` 1，共 **17/17**。
- 独立一次性门禁：后端 `package`、前端 type-check/build、dev/prod Compose config、MinIO 依赖树、`git diff --check` 全部通过。
- 未覆盖关键边界：顶层 box 合法但内部结构损坏并触发 JCodec `IOException`；`removeObject` 失败后的持久重试；旧 server compose 在 successor 删除后迟到完成；小扫描上限下尾部孤儿公平性；裸机父 JVM 崩溃后的子进程回收。

### Approval Recommendation

**Request changes.** Finding 1 必须在第五轮修复并由开发者执行确定性内容反例；Finding 2 必须建立持久对象 reconciliation，不能继续依赖一次性删除和并不存在的兜底扫描。两个 Low 可与第五轮一并补强，或明确进入带负责人和期限的发布前债务清单。WS-3/U-002 与全项目继续保持 CHANGES REQUESTED。

## 3. Top Risks

1. **High — 损坏 MP4 的解析 `IOException` 被判为基础设施故障并永久卡 `MERGING`。** 用户无法取消、重传或重新初始化，重试只会探测同一对象并重复失败。
2. **Medium — FAILED 对象清理没有持久 reconciliation。** 一次 MinIO 删除失败即可永久留下未登记对象；SERVER 的迟到 compose 还能在成功删除后复活稳定 key。
3. **Low — 有界临时工件扫描可能长期饿死尾部孤儿。** 领先的未过期或不可删除候选反复耗尽 scan limit。
4. **Low — 裸机父 JVM 崩溃后 worker 监管状态不可恢复。** 官方 Docker 部署受容器进程边界保护，但非容器运行可能遗留未登记子进程。

## 4. Detailed Findings

### Finding: JCodec 内容解析 IOException 被误判为基础设施故障并永久保留 MERGING

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 视频内容校验、worker 协议与 direct/server 定稿恢复
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeWorkerMain.java:36-84`
  - Function/module: `main`、`inspectTrack`
  - Relevant behavior: `inspectTrack` 声明 `throws IOException`；顶层只预检 ftyp/moov/mdat 与 box 边界，catch 不包含 `IOException`，所以内部 MP4 结构触发的解析 IOException 会越过结果写入并使子进程非零退出。
  - File: Maven dependency `org.jcodec:jcodec:0.2.5`
  - Function/module: `MP4Demuxer.findMovieBox`、`DemuxerTrack.nextFrame`、`FrameGrab`
  - Relevant behavior: 独立 `javap` 检查确认 JCodec 解析 API 声明 IOException，且 `findMovieBox` 会以 IOException 报告缺失的 movie 元信息；这类异常可由文件内容而非主机故障触发。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/ProcessVideoMediaWorker.java:24-71`
  - Function/module: `inspect`
  - Relevant behavior: 任意非零 worker exit 都转为 `VideoProbeInfrastructureException`，只有 exit 0 且结果协议完整的 `valid=false` 才属于内容失败。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:478-498,592-636`
  - Function/module: direct complete、server merge 异常恢复
  - Relevant behavior: 最终对象已存在时，该基础设施异常既不生成 `VALIDATION_FAILED`，也不收敛到 `FAILED`；会话保留 `MERGING`，下次重试继续探测同一对象。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/business/video/support/VideoProbeWorkerBoundaryTest.java:93-104`
  - Function/module: `malformedMp4IsReportedAsStructuredContentFailure`
  - Relevant behavior: 现有畸形样本在顶层 box 预检即失败，没有进入 JCodec 内部并覆盖 IOException 分支。
- Problem: 第四轮把“非零退出”可靠地隔离为基础设施故障，却没有在 worker 内把第三方解析器的内容型 IOException 转成结构化 `valid=false`。同一种不合法媒体会因异常类型不同进入相反的持久状态。
- Why it matters: Phase 7 明确要求伪 MP4/不可解析内容校验失败并允许重传。当前路径把合法用户可能上传的损坏文件或不兼容编码器产物变成永久 `MERGING`；init 返回原会话、cancel 拒绝 MERGING、重试又无法替换最终对象，需人工改库/删对象恢复。
- Realistic failure scenario: 学生上传一个顶层 ftyp/moov/mdat 和 box 长度均合理、但 moov 内部 movie/track 元信息损坏的 MP4。worker 在 JCodec 解复用阶段抛 IOException、未写结果并以非零退出；父进程判为基础设施故障。direct/server 均已持有最终对象，因此会话持续 MERGING，每次重试都对同一对象重复该序列。
- Minimal fix: 在 worker 内建立明确的解析阶段边界：文件打开、结果写入等本地 I/O 继续作为基础设施错误；JCodec 解复用、轨道读取和首帧解析阶段的内容型失败转换为结构化 `valid=false`。不要只在父进程按 exit code 猜测错误域。
- Better long-term fix: 将 worker 输出协议扩展为带稳定 error domain/code 的 sealed outcome（VALID_CONTENT、INVALID_CONTENT、INFRA_IO、RESOURCE_LIMIT、PROTOCOL_ERROR），各解析适配器负责把第三方异常映射到域；主业务只按协议状态转换。
- Regression test suggestion: 由开发者构造或保存一个“顶层 box 合法、内部 movie/track 结构损坏并能稳定触发 JCodec IOException”的固定 fixture，分别走 worker 与 HTTP 定稿；断言 worker exit 0 + structured invalid、会话进入可重传的 `VALIDATION_FAILED`，且本地文件打开/结果写入失败仍保持可重试基础设施语义。该测试因用户安全限制未由本轮复核执行。
- Estimated effort: 1 day

### Finding: FAILED 后的最终对象清理不是持久任务，SERVER 还可迟到复活稳定 key

- Severity: Medium
- Confidence: High
- Category: Data Integrity
- Status: Confirmed
- Affected area: 视频失败收敛、MinIO 对象生命周期与存储对账
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:1290-1350`
  - Function/module: `convergeFailedFinalization`、`cleanupUnregisteredFinalizationObject`
  - Relevant behavior: 数据库先提交 FAILED，事务后仅调用一次 `removeObject`；任意异常只记录“孤儿扫描将兜底”并返回，没有重试记录或持久任务。
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/mapper/FileObjectScanMapper.java:10-38`
  - Function/module: Phase 47 orphan scan
  - Relevant behavior: 扫描起点是已存在的 `file_object` 行；完全未登记的 MinIO 最终对象不可见，且该扫描只报告不删除。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:579-588,619-635,1447-1449,1847-1875`
  - Function/module: SERVER_CHUNK compose/put、owner 复核、稳定 object key
  - Relevant behavior: 外部 compose/put 使用每 uploadId 固定 key，owner 只在调用返回后再次校验；旧 owner 的已发出写可在 successor 收敛 FAILED 并删除对象后迟到完成，从而复活同一 key。
- Problem: 数据库终态和对象清理没有共同的持久 reconciliation。一次普通的 MinIO timeout/503 就可能使对象永久残留；即使删除成功，SERVER 的旧在途写也可能在删除之后再次发布对象。
- Why it matters: 每个失败会话可遗留最大约 2GB 的敏感视频对象，既不受业务引用管理，也不会被现有孤儿扫描发现。积累后造成存储成本、保留期合规和容量压力；对象状态与数据库事实永久不一致。
- Realistic failure scenario: finalize 与 assign 冲突后，当前 owner 正确把 session 提交为 FAILED；随后 MinIO 删除返回 503，代码只记录 WARN。另一个 SERVER 交错中，owner A 的 compose 在网络中阻塞，owner B 取得更高世代、提交 FAILED 并成功删除稳定 key，A 的 compose 最后完成并把对象写回；A 已失权且不会清理，FAILED 又阻止后续接管。
- Minimal fix: 基于 FAILED session 保留的 `object_key` 建立持久启动/定时 reconciliation。候选年龄须大于对象调用总 timeout 加安全余量；每次重查 session 仍为 FAILED 且无 `file_object` 后幂等删除，失败可持续重试并有指标/告警。
- Better long-term fix: SERVER 每个数据库 generation 使用独立 candidate key；获胜事务只登记当前 generation 的 candidate，旧 generation candidate 由持久 reaper 清理。这样迟到写不能覆盖或复活 winner/terminal 共用 key。
- Regression test suggestion: 开发者用确定性 hook 覆盖两条序列：`removeObject` 首次失败、后续 reconciliation 成功；旧 generation compose 在 successor FAILED 清理后才完成，最终仍无未登记对象。该并发故障验证因用户安全限制未由本轮执行。
- Estimated effort: 1–2 days

### Finding: 有界临时工件扫描缺少进度游标，尾部过期孤儿可能长期饥饿

- Severity: Low
- Confidence: Medium
- Category: Performance
- Status: Suspected
- Affected area: 持久 probe 卷的周期清扫公平性
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeTempArtifactManager.java:138-200`
  - Function/module: `cleanupOrphans`
  - Relevant behavior: 每轮重新从 `DirectoryStream` 起点遍历；已识别但未过期、owner 仍活跃或删除失败的候选也消耗 scan limit，没有持久游标或轮转起点。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeTempArtifactManager.java:248-253`
  - Function/module: `deleteKnownArtifact`
  - Relevant behavior: 删除失败只记录 WARN，下一轮仍可能在同一前缀位置再次消耗预算。
  - File: `platform-boot/src/main/resources/application.yml:47-48`
  - Function/module: artifact cleanup defaults
  - Relevant behavior: 默认每 5 分钟扫描最多 10000 个已知候选；默认值降低了日常风险，但低配、异常积压或手工下调后仍可能暴露。
- Problem: 清扫具有数量上限但没有跨轮进度。如果文件系统反复返回相同顺序，领先的不可清理候选可让后续已过期大文件永远不被访问。
- Why it matters: 该问题不会破坏当前 owner 安全性，但会削弱第四轮引入的崩溃自愈保证；低扫描上限或大量积压时，尾部 2GB 文件可能持续占用容量并触发拒绝服务式的正常准入失败。
- Realistic failure scenario: scan limit 被运维下调为 1；目录最前的已知工件属于仍活跃 owner 或因权限问题无法删除，后面存在已过期 2GB 孤儿。每轮从同一起点开始，第一项持续耗尽预算，尾部孤儿不被处理。
- Minimal fix: 保存安全的轮转游标/上次文件名，或每轮从不同分桶开始；删除失败候选应有退避或隔离，不应每轮占用首个预算。
- Better long-term fix: 用持久 manifest 驱动按过期时间分页清扫，目录扫描只作为 manifest 修复兜底，并暴露 backlog age/bytes 指标。
- Regression test suggestion: scan limit=1，前置一个长期不可删除或仍活跃的已知候选，后置一个已过期工件；连续多轮清扫必须最终访问并删除尾部工件。
- Estimated effort: 4–8 hours

### Finding: 裸机父 JVM 崩溃后 worker 监管状态不可恢复

- Severity: Low
- Confidence: Medium
- Category: Stability
- Status: Suspected
- Affected area: 非容器运行的媒体子进程生命周期
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeProcessSupervisor.java:14-27`
  - Function/module: in-memory orphan registry
  - Relevant behavior: 监管器只在当前 JVM 内保存 `Process` 对象；重启后没有 PID/start-token manifest 或系统级重新发现。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeProcessRunner.java:33-85`
  - Function/module: `execute`、`terminateAndConfirm`
  - Relevant behavior: 正常 timeout/interrupt 会强杀、等待并登记未退出进程；父 JVM 被直接终止时这些分支不会运行。
  - File: `docker-compose.yml`
  - Function/module: official backend deployment
  - Relevant behavior: 官方容器部署由容器进程边界清理同容器子进程，显著降低生产风险；问题主要落在裸机/直接 JAR 运行。
- Problem: 第四轮的“活孤儿阻断准入”依赖当前 JVM 已经观察并登记该 Process。父 JVM 自身崩溃时，裸机操作系统可能保留子 Java 进程，而重启后的 supervisor 为空。
- Why it matters: 裸机运维或开发环境中，遗留 worker 可继续持有媒体文件、CPU/内存和句柄，新实例又会恢复准入。官方 Docker 生产路径已有缓解，因此定为 Low。
- Realistic failure scenario: 后端直接以 JAR 运行，worker 正在 JCodec 解析时父 JVM 被强制终止；子进程继续存活。后端重启后 in-memory registry 为空，capacity guard 不知道旧 worker，允许新探测进入。
- Minimal fix: 明确官方只支持容器/cgroup 管理的生产运行，并在裸机启动脚本使用 systemd cgroup 或 Windows Job Object 绑定子进程生命周期。
- Better long-term fix: 为所有活跃 worker 持久记录 PID、进程启动令牌与工件 owner；启动时先安全核验并回收同一部署实例留下的进程，再开放准入。
- Regression test suggestion: 由开发者在受控外部终端验证父进程退出时子进程由容器/cgroup/Job Object 自动终止；若保留裸机支持，再增加启动 reconciliation 测试。本轮未执行进程破坏验证。
- Estimated effort: 4–8 hours

## 5. Security Concerns

- Coverage: Medium
- Inspected evidence: 预签名对象边界、稳定/候选 object key、未登记敏感视频残留、MinIO 凭据与端点、进程参数列表、异常日志。
- Exclusions: 按用户明确要求未执行漏洞扫描、恶意媒体、路径攻击、权限绕过、凭据探测或任何可能触发 cyber 安全限制的动作。

本轮未发现新的越权、公开对象 URL、命令注入或密钥硬编码。Finding 2 主要是数据生命周期和容量问题，但未登记视频对象超出业务保留/删除控制，具有次要隐私合规影响。报告没有把未验证的可访问性扩大解释为安全漏洞。

## 6. Stability Concerns

- Coverage: High
- Inspected evidence: direct/server 状态机、DB generation、Redis owner、worker exit/result 映射、JCodec API 异常、进程终止、临时卷清扫和对象删除。
- Exclusions: 未制造损坏媒体、进程卡死、Redis 丢失、MinIO 503 或真实节点崩溃；对发现的判断来自静态可达路径与安全白名单测试。

旧 4 个 High 已按原问题闭环，说明第四轮恢复设计有实质提升。当前唯一 High 位于错误域翻译：内容解析异常被错当成基础设施故障，使“保留 MERGING 供恢复”的正确策略在不可恢复内容上反而形成永久状态。Finding 4 只影响非官方裸机生命周期，官方 Docker 路径有进程边界缓解。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: 容量守卫快照、活跃字节核算、2GB 上限、清扫周期/scan limit、未登记对象、子进程并发和前端生产构建。
- Exclusions: 未执行 2GB 真实传输、容量压测、故障积压或吞吐基准。

第三轮的磁盘双重计数问题已闭环。剩余性能风险主要来自清理债：FAILED 对象每会话可残留约 2GB，临时卷尾部孤儿在极端目录顺序与低 scan limit 下可能长期不被处理。前端 build 仅保留既有大 chunk 警告，不属于本次 WS-3 发布阻断。

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: CI workflow、父/boot/file POM、Surefire/Failsafe XML、V31 migration IT、fat-JAR IT、MinIO config tests、worker/capacity/temp tests、前端与 Compose 一次性门禁。
- Exclusions: 未独立重跑可能被视为攻击性或破坏性的畸形媒体、竞争交错、Redis 状态破坏、对象删除故障和进程遗留测试。

### Confidence Assessment

整改者的 299/299 不是仅文档声明：独立复核在 clean 前解析了 140 个 Surefire 与 159 个 Failsafe 结果，均为 0 failure/error/skip，且源码修改时间早于报告。CI 的后端 job 执行 Maven verify，fat-JAR IT 确实在 package/repackage 后由 Failsafe 启动 `PropertiesLauncher`。因此旧问题的正常与已写恢复用例有较高可信度。

独立白名单 clean verify 只选择 17 项，目的是遵守用户禁止 cyber 风险动作的边界。它证明当前源码可干净编译、V31 升级保值、生产 fat-JAR worker、超时配置、容量/临时工件基本行为和安全的进程边界用例成立，不证明本报告列出的未覆盖交错。

### Valuable Tests

- `V31FinalizationHighWaterMigrationIT` 从 V30 隔离 schema 升级并验证 NULL→0、正世代保值和列约束。
- `FatJarVideoProbeWorkerIT` 在 verify 生命周期使用真实重打包 JAR 与 `PropertiesLauncher`。
- `ProcessVideoMediaWorkerTest` 区分结构化 invalid 与非零退出/协议错误。
- `VideoProbeCapacityGuardTest` 覆盖活跃字节快照、准入回滚和孤儿二次检查。
- `VideoProbeTempArtifactManagerTest` 覆盖未知文件保留、过期孤儿、活 owner 和共享目录拒绝。

### Suspicious Tests

- `malformedMp4IsReportedAsStructuredContentFailure` 的样本没有合法 moov，命中的是 JCodec 之前的顶层检查；测试名比实际覆盖面更宽。
- assign/finalize 与源分片丢失测试使用确定性 hook，结构本身真实，但没有覆盖旧/新 SERVER compose 同时在对象存储中飞行。
- 现有临时工件 scanLimit=1 用例只证明未知文件不消耗预算，没有证明已知但不可清理的前置项不会饿死尾部。

### Missing Tests

- JCodec 内部内容型 IOException 必须成为 structured invalid，并允许重新上传。
- FAILED 对象删除失败后持久重试成功。
- 旧 generation server compose 在 successor 清理后迟到完成，reconciliation 最终仍删除对象。
- 多轮有界扫描对尾部过期孤儿的公平性。
- 若继续支持裸机生产，父 JVM 异常退出后的 worker 生命周期。

## 9. Release Concerns

- Coverage: High
- Inspected evidence: Maven package/定向 verify、前端 type-check/build、两份 Compose config、V31 发布顺序、fat-JAR worker、依赖树和隔离依赖环境。
- Exclusions: 未启动常驻后端/前端，未部署生产、未执行回滚或新旧版本混部。

V31 的停写→停止全部旧节点/worker→迁移→全量新实例→恢复写流量协议是必要且充分描述的；旧二进制不得在 V31 后回滚。MinioClient/AWS S3Client 超时均为正数且依赖树只解析到预期版本。尽管发布机械门禁通过，Finding 1 会让普通损坏文件进入不可自愈业务状态，因此 WS-3 不能发布为稳定完成。

## 10. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `application.yml`、dev/prod profile、`.env.example`、docker-compose.yml、docker-compose.dev.yml、`MinioProperties`、`MinioConfig`、`MinioBucketInitializer` 和 `VideoProbeProperties`。
- Exclusions: 未连接真实生产凭据、外部对象存储或生产网络。

双客户端共享明确的连接/读取/写入/总调用超时约束，非法非正数配置会 fail-fast；桶初始化复用唯一受管 MinioClient，没有 `@PostConstruct` 自调用 `@Bean` 的循环风险。probe 卷独占和 V31 切换要求已同步到 Phase 14。配置维度无新的 Major。

## 11. Data Integrity Analysis

- Coverage: High
- Inspected evidence: V31、session 行锁、generation 递增、Redis owner、finalize 成功/失败事务、stable server key、`file_object` 登记和 Phase 47 orphan scan。
- Exclusions: 未执行数据库备份恢复、对象存储删除失败或跨节点真实故障注入。

数据库 fencing 已从临时 Redis 序列迁移为永久 high-water，旧 owner 无法用相同 generation 提交或释放 successor，属于本轮最重要的正确性闭环。剩余完整性缺口是 Finding 2：FAILED 已成为数据库真值，但外部对象删除没有持久事实和重试，日志声称的 orphan scanner 又无法看见无 `file_object` 对象。

## 12. Concurrency Analysis

- Coverage: High
- Inspected evidence: owner acquire/renew/assert/close、DB generation claim、direct/server takeover、assign 行锁、compose 外部调用、FAILED 收敛和清理顺序。
- Exclusions: 按用户限制未执行多线程竞争、lease 抢占、对象存储阻塞或破坏性故障；使用 happens-before 与持久状态序列复核。

第三轮报告的 ABA、assign/server 永久 MERGING 和 direct NoSuchUpload 永久 MERGING 已闭环。新 Medium 的 SERVER 变体来自“外部写先发出、owner 后校验”：generation 能阻止旧 owner登记元数据，却不能撤回已经发送给 MinIO 的稳定-key 写。持久 reconciliation 是必要的最小安全网，generation-specific candidate key 才能从设计上隔离迟到写。

## 13. Principles Compliance

### Principles Respected

- **数据库是真值源：** fencing generation 在行锁内单调递增，Redis 仅作活跃 owner 互斥。
- **Fail closed：** 结果协议缺失/畸形、worker 非零退出和配置非法均不会被当作成功内容。
- **短事务：** 2GB 对象 compose、下载和探测继续位于数据库事务外。
- **可重复发布：** V31 迁移保留历史正世代，并提供明确停机切换协议。
- **测试真实性：** 真实 MySQL/Redis/MinIO、打包 JAR 和 Failsafe 生命周期均有可核验证据。

### Principles Violated

- **错误域应由产生异常的一层判定：** 父进程只见 exit code，无法准确区分 JCodec 内容损坏与基础设施失败。
- **外部副作用必须可对账：** FAILED 对象删除既无 outbox/retry 状态，也没有能枚举未登记对象的 reconciliation。
- **有界后台任务应保证进度：** 临时工件扫描有上限但无跨轮公平性。
- **生命周期约束应由运行环境保证：** 裸机子进程只靠父 JVM 内存监管，父进程崩溃时没有系统级所有权。

## 14. Recommended Fix Order

### Fix Immediately

1. 修复 worker 内容型 IOException 分类，补固定 fixture 与 HTTP/状态机反例；确认损坏内容进入可重传 `VALIDATION_FAILED`，真实本地/结果 I/O 仍可重试。
2. 为 FAILED session/object_key 建持久 reconciliation，覆盖删除重试和 SERVER 迟到 compose；没有此兜底不得声称对象生命周期闭环。

### Fix Before Stable Release

3. 给 server compose 使用 generation-specific candidate key，避免不同 owner 写同一稳定 key。
4. 让临时工件有界扫描具备轮转/分页进度，并补尾部公平性测试。
5. 明确生产只支持容器进程边界；若支持裸机，补 cgroup/Job Object 或启动 PID reconciliation。

### Schedule Later

- 真实 2GB 传输、非允许编码与不可解码首帧仍按 Phase 7 证据债处理。
- Phase 53 demo 元数据/旧对象 reconcile 继续留在 U-003，不并入本次 WS-3 finding 计数。

### Ignore for Now

- 前端 Vite 大 chunk warning 属既有 WS-12 性能债，本次增量没有放大。

## 15. Quick Wins

1. 把现有 malformed test 改名为“top-level box malformed”，避免测试名暗示已覆盖 JCodec 内部 IOException。
2. 在 `cleanupUnregisteredFinalizationObject` 的 WARN 中移除“孤儿扫描将兜底”的错误承诺，直到持久 reconciliation 实际存在。
3. 为 FAILED 未登记对象、reconciliation 重试次数、最老待删年龄和临时卷清扫 backlog 增加指标。
4. 在 Phase 7 新增 T-VID-2L（解析 IOException 分类）与 T-VID-2M（FAILED 对象持久清理/迟到 compose）。

## 16. Long-term Refactor Plan

1. 把上传定稿建模为显式持久阶段：CLAIMED、OBJECT_CANDIDATE_READY、PROBED、REGISTERED、TERMINAL_CLEANUP_PENDING、DONE。
2. 每个 generation 使用独立 candidate object key；数据库事务只选择并登记 winner，reaper 清理 loser。
3. worker 使用版本化 outcome schema 与稳定 error code，不让业务状态机依赖异常类名、消息或单一 exit code。
4. 将临时文件、子进程和对象存储副作用统一纳入可观测 reconciliation：有 owner、generation、期限、重试和最终确认。
