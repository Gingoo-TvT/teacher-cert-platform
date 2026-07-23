# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform — WS-3 第二轮整改增量
**Audit mode:** incremental + security + stability + performance + testing-authenticity + release + configuration + data-integrity + concurrency
**Date:** 2026-07-23
**Reviewer:** OpenAI Codex（GPT-5，独立复核）

---

## 1. Executive Summary

本轮冻结并复核 `ca400f1..32da735`：1 个提交、21 个变更文件、增加 1511 行、删除 132 行。结论为 **CHANGES REQUESTED**。第二轮整改不是无效工作：此前“只信媒体头时长”、fast-hit 未绑定当前策略/对象状态、定稿覆盖已分配评审、V29 部分 DDL 不可收敛等问题已实质闭环；独立全新依赖环境也复现了后端 **271/271**、前端 type-check/build 全绿。

但 T-057 的资源与恢复不变量仍未闭环：兼容 `SERVER_CHUNK /merge` 路径未接入 Redis 租约，进程在认领后退出会永久卡 `MERGING`；临时盘检查只比较单任务大小而非并发总预留量；所谓 `max-duration` 仅在阻塞调用返回后检查，不能形成硬时限。另有 direct 租约无续租/fencing、生产 Compose 未暴露新资源参数以及资源边界无确定性反例。绿灯证明现有用例通过，不能证明这些静态不变量成立，因此 WS-3/U-002 仍不得置 PASS。

### Score Dashboard

```text
Security        ███████░░░  7.0  A   未见新增越权/泄密，但不可信媒体仍在不可硬终止的进程内解析；动态安全覆盖受限
Stability       █████░░░░░  4.5  C   merge 崩溃不可恢复、磁盘总预留失效、探测硬时限不成立
Performance     ██████░░░░  5.5  B   direct 正常期单飞有效，但 TTL 窗口和不可中断探测仍可重复或长期占满槽位
Testing         ███████░░░  6.5  B   271/271 真实回归有价值，但资源、TTL、merge 恢复与多轨边界缺少反例
Maintainability ████████░░  8.0  A   新职责已拆成独立组件，主要问题是两条定稿路径的恢复语义继续分叉
Design          ██████░░░░  5.5  B   租约、容量和时限接口存在，但实现没有兑现声明的统一硬边界
Release         █████░░░░░  5.0  B   构建与迁移门禁通过；生产配置入口和三项稳定性阻断仍不允许放行
─────────────────────────────────────
Overall         ██████░░░░  6.0  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based. See `rubrics/scoring.md` for anchor descriptions.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 3 | 3 | 0 |
| Medium | 3 | 3 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **6** | **6** | **0** |

## 2. Project Map

本次增量围绕视频定稿链路展开：

1. `VideoReviewServiceImpl.complete` 处理浏览器直传定稿，先取 Redis 单飞租约和进程内容量租约，再完成对象、下载探测并短事务落库。
2. `VideoReviewServiceImpl.merge` 处理仍受支持的服务端分片兼容路径，使用数据库状态 CAS 和同一容量守卫，但未使用 Redis 租约。
3. `JcodecVideoMediaProbe` 将最终对象下载到临时文件，计算服务端内容指纹，完整扫描唯一视频轨并核对三种时长，再解码首帧。
4. `VideoProbeCapacityGuard` 管理单进程并发槽位、临时字节预留及磁盘余量；`VideoFinalizeSingleFlight` 管理 direct 路径的 Redis 令牌租约。
5. `file_object` 由 V29 增加媒体验证策略/探测器版本字段，fast-hit 依据当前策略、同上传人/同学生及对象 HEAD 复用。
6. `Phase7VideoReviewIT` 和 `V29MigrationRecoveryIT` 提供真实 MySQL/Redis/MinIO 集成证据。

风险最高的边界是：数据库 `MERGING` 状态与 Redis 租约的恢复关系、容量预留与真实文件系统余量的原子关系，以及 Java 进程内媒体解析能否被硬终止。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental scope | High | `git diff/name-status/numstat/log ca400f1..32da735`；逐个检查 21 个变更文件及相关未变调用方 | 不重复审计与本增量无关的全仓业务 |
| Security | Medium | 最终对象可信边界、策略绑定、对象 HEAD、媒体解析资源边界、配置与日志 | 按用户要求未新增或单独执行畸形载荷、模糊测试、压测、利用或攻击性并发 |
| Stability | High | complete/merge 状态机、Redis lease、容量租约、临时文件清理、迁移恢复；全量标准回归 | 未模拟真实进程崩溃、磁盘写满或对象存储长时间阻塞 |
| Performance | Medium | 2GB 下载/临时盘路径、并发槽位、包数、重复探测、前端生产构建 | 未做基准、容量或压力测试 |
| Testing Authenticity | High | 两个新增 IT、Phase 7 全文件、Surefire/Failsafe XML、真实依赖全量门禁 | 未做多节点测试；真实 2GB、非允许编码和不可解码首帧仍是已声明证据债 |
| Configuration | High | `application.yml`、`docker-compose.yml`、`.env.example`、Phase 7/14 运维文档 | 未部署生产 Compose 应用容器 |
| Data Integrity | High | review 行锁、student 创建互斥、session 状态、fast-hit、V29 SQL 与恢复 IT | 未在长期共享库验证旧 V29 checksum；当前仓库无 remote，V29 未进入 main |
| Release | High | Maven clean verify、前端 type-check/build、Flyway V1–V29、Compose 配置和清理 | 未执行真实生产发布或回滚 |
| Concurrency | Medium | Redis SET NX/token release、DB CAS/FOR UPDATE、单实例确定性交错测试 | 未做多节点、TTL 实时等待或攻击性并发测试 |

### Change Summary

- Total files changed: 21
- Lines added: 1511
- Lines deleted: 132
- Commits in range: 1
- Authors: wenbibuhaoqwq

### Change Categories

- New features: 5 files
- Bug fixes / behavioral changes: 7 files
- Refactoring: 0 files
- Dependency updates: 0 files
- Configuration: 1 file
- Tests: 2 files
- Documentation / governance: 9 files

分类允许重叠；例如 Phase 7 IT 同时属于测试和行为契约证据。

### Risk Delta

- Existing risks fixed: 4（媒体时间线、fast-hit 当前策略/HEAD、定稿与 assign 串行、V29 结构恢复）
- Existing risks partially fixed: 2（定稿单飞/资源硬边界、唯一视频轨的测试证据）
- New or newly exposed risks: 2（并发总预留计算、direct lease 过期窗口）
- Existing changed-path risk still open: 1（`SERVER_CHUNK /merge` 崩溃恢复）

### Test Coverage Delta

- New/modified critical code with meaningful tests: 媒体时间线、direct 单飞、fast-hit 失效、finalize/assign、V29 恢复
- New critical code without direct boundary tests: 容量守卫、探测硬时限/包数、租约续期/TTL、server-chunk 崩溃恢复
- Deleted tests: 0

### Approval Recommendation

**Request changes.** 修复第 1–3 项 High，并至少以安全、低阈值、确定性的方式覆盖第 6 项测试缺口后，再做增量重核。

## 3. Top Risks

1. **High — `SERVER_CHUNK /merge` 崩溃后永久卡 `MERGING`。** 数据库认领成功后进程退出不会进入 catch，后续 init/merge/cancel 均无法恢复。
2. **High — 并发探测可突破最低磁盘余量。** 守卫预留了总字节，却只以当前任务大小检查文件系统余量。
3. **High — `max-duration` 不是硬时限。** 对象读取、demux、单包读取和首帧解码均可在一次阻塞调用中越过截止时间。
4. **Medium — direct Redis lease 可能早于活跃执行者到期。** TTL 不包含无界的 S3 前置/收尾工作，也没有续租或 fencing。
5. **Medium — 新资源参数没有进入官方生产部署入口。** Compose/.env/Phase 14 未转发或说明，临时文件默认落容器临时层。
6. **Medium — 资源与恢复边界没有确定性反例。** 271/271 不包含容量拒绝、permit 释放、TTL、包数、时限、server-chunk 崩溃或多轨。

## 4. Detailed Findings

### Finding: SERVER_CHUNK 定稿在进程退出后永久卡在 MERGING

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 视频服务端分片兼容定稿与会话恢复
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:468-553`
  - Function / Module: `merge`、`mergeWithReservedCapacity`、`handleNonClaimableMerge`
  - Relevant behavior: `/merge` 只用 `UPLOADING -> MERGING` 数据库 CAS；只有当前进程捕获异常时才复位，未使用 `VideoFinalizeSingleFlight`，而后续对 `MERGING` 一律拒绝。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:191-192,314-315`
  - Function / Module: `initUpload`、`cancelUpload`
  - Relevant behavior: init 只回放 MERGING 信息，cancel 明确拒绝 MERGING，会话没有业务 API 恢复出口。
  - File: `plan.md:319`
  - Function / Module: §6.11 定稿资源边界
  - Relevant behavior: 规格要求同一 uploadId 单节点执行且进程崩溃后可超时接管。
- Problem: 第二轮只把 Redis 租约接到 `complete`，但 `MINIO_DIRECT_UPLOAD_ENABLED=false` 时仍正式使用的 `SERVER_CHUNK /merge` 没有租约和超时接管。CAS 能防并发，却不能处理认领者消失。
- Why it matters: 一次正常的进程重启、节点故障或宿主退出即可形成不可恢复业务状态；学生无法取消或重新提交，运维只能手工改库，违反 Flyway/留痕和状态机治理要求。
- Realistic failure scenario: 节点 A 成功把会话 CAS 为 `MERGING`，在 compose、探测或短事务前退出；catch 未运行。重启后请求再次 merge，CAS 返回 0 并进入“正在合并”错误；init 继续返回 MERGING，cancel 继续拒绝。
- Minimal fix: 对 `merge` 使用与 `complete` 相同的 token lease；无活跃 lease 的 MERGING 必须按持久化步骤恢复或安全回滚。对象 key/合并进度须在认领事务中持久化，避免退出后丢失恢复定位。
- Better long-term fix: 合并 complete/merge 为统一的、带 fencing token 的可恢复定稿状态机；两种上传模式只负责生成最终对象，探测与落库共用同一恢复协议。
- Regression test suggestion: 用安全 failpoint 在 CAS 后、compose 后和探测后分别中断当前执行流程，清除/到期租约后由第二次调用接管；断言最终一个 file_object、一个 review、会话非 MERGING。
- Estimated effort: 1–2 days

### Finding: 磁盘余量检查忽略并发任务总预留量

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 视频探测临时盘容量守卫
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeCapacityGuard.java:40-57`
  - Function / Module: `acquire`
  - Relevant behavior: 第 49 行先累计 `reservedBytes`，第 54 行却只比较 `usable - minFreeBytes < expectedBytes`，没有比较累计预留值。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeCapacityGuard.java:73-82`
  - Function / Module: `reserveBytes`
  - Relevant behavior: 总预留上限仅受 `maxReservedBytes` 约束；默认 4 GiB，允许两个 2 GiB 任务同时预留。
  - File: `platform-boot/src/main/resources/application.yml:35-38`
  - Function / Module: `platform.video.probe`
  - Relevant behavior: 默认并发 2、总预留 4 GiB、最低余量 1 GiB。
- Problem: 代码维护了总预留计数，却没有把它用于真实文件系统余量判定，因此“最低保留空间”只对每个请求单独成立，不对并发总量成立。
- Why it matters: 两个合法大视频即可把宿主/容器临时盘写到最低阈值以下甚至写满，影响日志、数据库临时文件或同宿主其他服务；这不是攻击前提下才会发生。
- Realistic failure scenario: 文件系统可用 3.5 GiB、最低保留 1 GiB。两个 2 GiB 请求在开始写盘前均看到 `3.5 - 1 >= 2` 并通过；随后总写入 4 GiB，突破最低余量并可能触发 ENOSPC。
- Minimal fix: 让 `reserveBytes` 返回 CAS 后的累计预留值，并校验 `usableSpace - minFreeBytes >= totalReservedBytes`；失败时原子撤销当前预留和 permit。对同一临时目录的多进程部署还需使用卷配额或共享配额来源。
- Better long-term fix: 将媒体探测放入有磁盘 quota/ephemeral-storage limit 的独立 worker，并以队列 admission control 统一管理集群级并发和字节预算。
- Regression test suggestion: 抽象可用空间提供器，以小数字模拟两个并发租约；断言只有总预留仍满足最低余量的请求可成功，失败和异常路径均精确释放字节与 permit。
- Estimated effort: 2–4 hours

### Finding: 探测 max-duration 不能中断单次阻塞调用

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 最终对象下载、MP4 demux、样本扫描与首帧解码
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:97-129`
  - Function / Module: `downloadAndFingerprint`
  - Relevant behavior: 截止时间在 `InputStream.read` 返回后才检查；阻塞中的读取不受该 deadline 控制。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:145-176`
  - Function / Module: `inspectMp4`
  - Relevant behavior: `createMP4Demuxer` 与 `FrameGrab.getFrameFromFile` 在调用期间没有可终止时限，检查只发生在前后。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:194-218`
  - Function / Module: `inspectTimeline`
  - Relevant behavior: `nextFrame` 返回后才检查 deadline；包数上限也无法中断单次读取。
- Problem: `maxDuration` 是协作式轮询，不是“硬上限”。任何一个底层调用持续阻塞或极慢时，10 分钟到期不会终止当前执行。
- Why it matters: 默认只有两个 permit；两个越时任务即可长期阻止全校后续视频定稿，同时继续持有临时文件和 Redis lease 相关状态。
- Realistic failure scenario: 对象存储读取长时间不返回，或媒体解析/首帧解码在单次调用内运行超过时限。deadline 无机会被检查，容量租约直到调用最终返回才释放。
- Minimal fix: 为 S3 get/read 配置明确的 API/read 超时；把解析/解码移到可被操作系统终止的受限 worker 进程，并在超时后 kill、清理临时文件和归还容量租约。
- Better long-term fix: 使用隔离媒体探测服务/作业，配置 CPU、内存、临时盘、墙钟时限和任务取消；主业务只消费结构化探测结果。
- Regression test suggestion: 使用可控的阻塞输入和可控探测 worker，在很短安全阈值下验证调用按时失败、worker 被终止、临时文件与 permit 被释放；无需构造畸形媒体或做压力测试。
- Estimated effort: 1–3 days

### Finding: direct 租约无续租与 fencing，可能早于活跃执行者到期

- Severity: Medium
- Confidence: High
- Category: Performance
- Status: Confirmed
- Affected area: 浏览器直传 complete 的跨节点单飞
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoFinalizeSingleFlight.java:23-51`
  - Function / Module: `tryAcquire`
  - Relevant behavior: TTL 固定为 `probe.maxDuration + 1 minute`，没有 token-safe 续租；lease 也没有写入数据库作为 fencing generation。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:355-453`
  - Function / Module: `complete`、`completeWithReservedCapacity`
  - Relevant behavior: lease 覆盖 list parts、S3 complete、HEAD、完整 inspect 和数据库收尾，而 `maxDuration` 只从 inspect 内开始计算。
- Problem: 一分钟 grace 不是整个定稿链路的可证明上界。原执行者仍活跃时 Redis key 可到期，第二节点取得新 token 后会再次下载/探测；token-safe release 只能避免旧 owner 删除新 key，不能阻止旧 owner继续工作。
- Why it matters: 在对象存储抖动或接近探测时限的大文件上，同 uploadId 单飞会失效，重复消耗带宽、临时盘和 CPU，并放大第 2、3 项资源风险。
- Realistic failure scenario: S3 定稿/HEAD 花费超过 1 分钟，随后探测接近 10 分钟；第 11 分钟 lease 到期但 A 仍运行，B 获取新 lease 并从 MERGING 恢复，两个节点并行探测同一对象。
- Minimal fix: 以 token-safe Lua 定期续租，并把 TTL 预算覆盖整个 complete 生命周期；每个阶段检查 lease 所有权，失去 lease 立即停止后续工作。
- Better long-term fix: 在数据库保存单调递增 fencing token，所有状态写入带 token 条件；Redis watchdog 只负责 liveness，数据库 token 负责阻止过期 owner 提交。
- Regression test suggestion: 使用可控时钟/短 TTL 和阻塞存储桩验证续租、旧 token 不可续租、失去 lease 后旧 owner 不再探测或落库；另做两个应用上下文共享 Redis 的确定性测试。
- Estimated effort: 4–8 hours

### Finding: 探测资源参数未接入官方生产 Compose 和运维文档

- Severity: Medium
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: 生产部署配置与临时存储
- Evidence:
  - File: `platform-boot/src/main/resources/application.yml:30-39`
  - Function / Module: `platform.video.probe`
  - Relevant behavior: 新增六个 `VIDEO_PROBE_*` 环境变量入口，并声明生产可覆盖。
  - File: `docker-compose.yml:71-106,140-143`
  - Function / Module: backend environment / volumes
  - Relevant behavior: backend 未转发任何 `VIDEO_PROBE_*`，且没有探测临时目录挂载；Compose `.env` 不会自动注入未列出的变量。
  - File: `.env.example:40-64`
  - Function / Module: 后端环境变量示例
  - Relevant behavior: 未列出探测并发、总预留、最低余量、时限、包数或临时目录。
  - File: `docs/phase-14-非功能部署验收.md:20-27`
  - Function / Module: 关键生产环境变量
  - Relevant behavior: 运维清单没有新资源边界。
- Problem: 按项目官方 `.env + docker compose` 路径部署时，新参数无法进入 backend 容器，2GB 临时文件默认写入容器 `${java.io.tmpdir}`/overlay 层，运维也不知道需要为其预留容量。
- Why it matters: 不同学校与宿主磁盘容量差异很大；无法按部署规模调整边界会让默认值成为隐藏容量假设，并使故障排查与扩容不可控。
- Realistic failure scenario: 运维在 `.env` 中增加 `VIDEO_PROBE_TEMP_DIR` 或降低并发，自认为已生效；Compose 未转发，应用仍使用默认 2 并发、4 GiB 预留和容器临时层。
- Minimal fix: 在 backend environment 显式转发六个变量；补 `.env.example` 和 Phase 14 说明；为 `VIDEO_PROBE_TEMP_DIR` 提供专用容量受控挂载及 sizing 指引。
- Better long-term fix: 交付启动期配置摘要（只显示非敏感限制）和磁盘容量 readiness 检查，使实际生效值可观测、可验收。
- Regression test suggestion: 对生产 Compose 运行 config 展开检查，断言所有 `VIDEO_PROBE_*` 被转发且临时目录挂载存在；用启动测试验证覆盖值被 `VideoProbeProperties` 绑定。
- Estimated effort: 2–4 hours

### Finding: 资源、TTL 与兼容 merge 恢复没有确定性反例

- Severity: Medium
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: Phase 7 集成测试与核心资源规则 DoD
- Evidence:
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase7VideoReviewIT.java:365-475`
  - Function / Module: 第二轮新增四个 IT
  - Relevant behavior: 覆盖时间线失配、单实例短时 SET NX、fast-hit 策略/对象失效及 finalize/assign 交错。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoProbeCapacityGuard.java:26-106`
  - Function / Module: 全组件
  - Relevant behavior: 全仓测试没有直接引用容量守卫，也没有验证并发拒绝、总预留、磁盘不足或异常释放。
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:194-203,270-279`
  - Function / Module: 包数和时限边界
  - Relevant behavior: 没有以安全低阈值验证 `maxPackets` 或 `maxDuration`。
  - File: `docs/phase-07-视频评审.md:50-53`
  - Function / Module: 验收清单
  - Relevant behavior: 真实 2GB、非允许编码和不可解码首帧继续未勾选，文档对此保持诚实。
- Problem: 现有 271/271 对主流程很有价值，却不能捕获本报告第 1–4 项；单实例 10 秒内并发通过不等于跨节点/TTL/崩溃恢复成立。
- Why it matters: 资源与恢复代码最容易在“所有功能测试都绿”时带病合并，随后只在磁盘紧张、节点退出或存储变慢时暴露。
- Realistic failure scenario: 后续重构再次只对 `expectedBytes` 做检查或移除 release；现有测试仍全部通过，错误直到生产并发大文件时出现。
- Minimal fix: 抽象 clock、可用空间、worker 和 lease backend，用小文件/小阈值补容量、释放、TTL、包数、merge 接管和多视频轨确定性反例。
- Better long-term fix: 建立媒体探测 contract test 套件，让 direct/server-chunk 两种入口复用同一组恢复、幂等、资源和策略用例。
- Regression test suggestion: 至少新增：总预留拒绝、异常后 permit/字节归零、超时 worker 终止、maxPackets 拒绝、旧 lease owner fencing、SERVER_CHUNK stale MERGING 接管、双视频轨拒绝。
- Estimated effort: 1–2 days

## 5. Security Concerns

- Coverage: Medium
- Inspected evidence: 最终对象服务端摘要、同上传人/同学生 fast-hit、当前策略哈希、对象 HEAD、媒体解析资源边界、错误日志。
- Exclusions / limits: 按用户要求未设计或单独执行畸形载荷、模糊测试、攻击性并发、压测或利用；只运行项目既有标准门禁。

fast-hit 的跨主体边界已闭环：当前实现要求同上传人、同学生、可信服务端摘要、当前策略/探测器版本、READY 状态及 MinIO HEAD 精确大小。普通 VO 不返回去重指纹。未发现本增量新增的授权绕过、敏感信息泄露或注入路径。

剩余安全相关风险是可用性边界：认证学生上传的最终媒体仍由主 JVM 内 JCodec 解析，且第 3 项证明墙钟时限不能硬终止底层调用。本报告按稳定性 High 记录，未将其夸大为已验证的可利用漏洞。

## 6. Stability Concerns

- Coverage: High
- Inspected evidence: complete/merge 全控制流、Redis token lease、DB CAS/FOR UPDATE、临时文件和容量租约清理、V29 恢复、全量一次性回归。
- Exclusions / limits: 未实际杀进程、填满磁盘、阻塞对象存储或运行异常媒体。

核心阻断为 Finding 1–3。Finding 4 进一步说明 direct 默认路径虽然在正常租约期内单飞，但租约过期并不等价于旧执行者停止。时间线三值核对、唯一视频轨代码约束、fast-hit 失效与 review 行锁均已正确改善稳定性。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: 2GB 文件下载/临时落盘、探测包循环、Semaphore、Redis lease 生命周期、重复 complete、前端生产构建。
- Exclusions / limits: 未做吞吐、基准、真实 2GB 或压力测试。

容量上限使单进程最多两个探测、总声明预留 4 GiB，方向正确；但 Finding 2 使真实磁盘预算失真，Finding 3 使 permit 占用时间无硬上界，Finding 4 允许 lease 过期后重复整对象探测。三者叠加时，配置数字不能代表可证明的资源上限。

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: Phase 7 新旧测试、V29 恢复 IT、Surefire/Failsafe XML、全新真实 MySQL/Redis/MinIO 回归、前端门禁。
- Exclusions / limits: 未做多节点、实时 TTL、真实 2GB、非允许编码、不可解码首帧或多视频轨动态验证。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 媒体头/样本时间线不一致 | High | 能捕获只信 header 的回归 | Keep |
| direct 重复 complete | Medium | 只证明单实例、短租约期内 SET NX | Keep but augment |
| fast-hit 策略与对象失效 | High | 真实参数缓存与真实 MinIO 删除 | Keep |
| finalize/assign 交错 | High | 确定性 latch 证明 review 不被旧快照覆盖 | Keep |
| V29 部分 DDL 收敛 | High | 真实 MySQL 从 V28 + 两列前置收敛 | Keep but document repair boundary |
| 容量/硬时限/TTL/merge 恢复 | None | Finding 1–4 均可逃逸 | Add contract tests |

### Valuable Tests

- `mediaHeaderDurationMustMatchDecodedSampleTimeline`
- `duplicateCompleteRunsExactlyOneProbe`
- `currentCodecPolicyAndObjectExistenceInvalidateFastHit`
- `assigningWhileProbeRunsCannotBeOverwrittenByFinalize`
- `V29MigrationRecoveryIT.v29ConvergesWhenItsFirstColumnsAlreadyExist`

### Suspicious Tests

未发现为绿灯而绕过生产逻辑的测试分支。`CountingVideoMediaProbe` 只在测试配置中包装真实 JCodec 实现并用于确定性交错，设计合理；其不足是只覆盖单实例短时间窗口。

### Missing Tests

见 Finding 6。缺口可以通过可控 clock、空间提供器和小阈值完成，不要求本地生成攻击载荷、做压力测试或真实写满磁盘。

## 9. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `VideoProbeProperties`、启动校验、base/dev/prod 配置、生产 Compose、`.env.example`、Phase 7/14 文档。
- Exclusions / limits: 未启动生产应用容器。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|--------------------|
| SchemaValidation | 0 | `VideoProbeCapacityGuard.initialize` | 保留正数/非空启动校验 |
| UnsafeDefault | 0 | 默认 2/4GiB/1GiB/10m/2M | 默认值本身有界，问题在执行语义 |
| EnvironmentSeparation | 1 | `docker-compose.yml` | 显式转发 `VIDEO_PROBE_*` |
| SecretConfig | 0 | 无新增密钥 | 无 |
| FeatureFlag | 0 | 无新增 flag | 无 |
| ConfigDocs | 1 | `.env.example`、Phase 14 | 补容量、挂载和 sizing |

Finding 5 是本维度的主要问题。另需注意：若任何未披露的长期数据库已应用 `ca400f1` 版 V29，当前修改过的脚本会产生 checksum mismatch；已知仓库无 remote、V29 未进 main、旧测试卷已销毁，因此这是一项合并前部署边界确认，不构成当前现存 High。

## 10. Data Integrity Analysis

- Coverage: High
- Inspected evidence: V29 SQL/IT、file_object 媒体字段、review/student/session 行锁、fast-hit 源选择、assign/finalize 交错。
- Exclusions / limits: 未验证未知外部长寿命数据库或真实滚动发布。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|--------------------|--------------------|
| TransactionBoundary | 0 | MinIO I/O 在短事务外，落库在短事务内 | 保持 |
| Idempotency | 1 | SERVER_CHUNK stale MERGING | 统一 lease + 恢复状态机 |
| ConcurrencyConsistency | 1 | direct lease 过期 owner | 加续租与 DB fencing |
| MigrationSafety | 0 | 当前未发布 V29 的部分 DDL 收敛 | 确认无旧 checksum 长寿命库 |
| InvariantValidation | 0 | 媒体三时长/唯一视频轨/当前策略 | 保持并补测试 |
| BackupRestore | 0 | 本增量不涉及 | 不评 |
| Reconciliation | 1 | crash 后对象/session 步骤 | 持久化恢复点与孤儿对账 |

V29 的原 High 在当前发布边界下闭环：所有 `ADD COLUMN/ADD INDEX` 先查 `information_schema`，其余 MODIFY 可重复，参数为 upsert；真实 MySQL IT 成功收敛并记录 V29。review 行锁也阻止了 finalize 用旧快照覆盖 REVIEWING。

## 11. Release Concerns

- Coverage: High
- Inspected evidence: Maven/Flyway 全量门禁、前端 type-check/build、生产 Compose/.env、迁移边界、阶段文档与测试计数。
- Exclusions / limits: 未执行真实生产发布、滚动升级或回滚。

独立门禁结果：

- `mvn -B -ntp clean verify`：BUILD SUCCESS，Surefire 121/121、Failsafe 150/150、0 failure/error/skip，合计 271/271。
- `Phase7VideoReviewIT`：29/29；`V29MigrationRecoveryIT`：1/1。
- `npm --prefix frontend run type-check`：PASS。
- `npm --prefix frontend run build`：PASS，仅保留既有大 chunk 警告。
- 测试只启动自然退出的随机端口 IT；未启动常驻后端/前端。
- 隔离 `teacher-cert-ws03` MySQL/Redis/MinIO 在测试后已 `down -v`。

即使门禁全绿，Finding 1–3 仍是发布阻断，Finding 5 使生产部署无法按官方入口调优新边界。

## 12. Concurrency Analysis

- Coverage: Medium
- Inspected evidence: Redis SET NX/token release Lua、Semaphore/AtomicLong、DB CAS/FOR UPDATE、complete/assign 确定性交错。
- Exclusions / limits: 未执行多节点、TTL 实时等待、压力或攻击性并发。

review 行锁方案成立：assign 先锁 review，finalize upsert 先以 student 作为创建互斥点再锁 review；后到者读取最新状态并拒绝覆盖。Redis release 也按 token 删除，不会误删新 owner。

未闭环处是生命周期而非互斥原语本身：server-chunk 完全没有 lease（Finding 1）；direct lease 过期后没有续租/fencing（Finding 4）；磁盘检查没有基于同一原子总预留值作 admission decision（Finding 2）。

## 13. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Fail-Fast / truthful boundaries | 1 | High | `max-duration` 被声明为上限但不能硬终止 |
| Timeout Every External Call | 1 | High | 最终对象读取缺少本链路可证明的墙钟终止 |
| Unbounded Resources Must Not Grow Forever | 1 | High | permit 持有时长无硬上界，磁盘最低余量可被并发突破 |
| Cancel Safety | 1 | High | merge owner 消失后 MERGING 无接管协议 |
| Configuration Over Hardcoding | 1 | Medium | 参数存在但官方部署入口无法传入 |
| Test Behavior, Not Implementation | 0 | — | 新增交错测试主要验证外部行为 |

### Principles Respected

- 媒体探测、容量守卫和单飞租约已拆分为独立职责。
- 内容真值来自服务端读取最终对象，不信任客户端 MIME、时长或摘要。
- fast-hit 绑定当前策略、探测器版本、同上传人/同学生和对象存储状态。
- Redis release 使用 token-safe Lua；数据库写入使用短事务和行锁。
- Phase 7 文档没有虚报真实 2GB、非允许编码和不可解码首帧证据。

---

## 14. Recommended Fix Order

### Fix Immediately

1. 统一 `complete` 与 `merge` 的 lease/接管协议，先消除永久 MERGING。
2. 用累计总预留量校验磁盘最低余量，并补异常释放测试。
3. 将媒体解析移入真正可终止的有界 worker；至少同时补对象读取超时。

### Fix Before Stable Release

1. 为 direct lease 增加续租、阶段所有权检查和 DB fencing。
2. 把 `VIDEO_PROBE_*`、专用临时目录挂载和容量说明接入官方 Compose/.env/Phase 14。
3. 补 Finding 6 的小阈值确定性反例，并重跑 Phase 7/14/24、全量 Maven 和前端门禁。

### Schedule Later

1. 把媒体探测演进为独立 worker/队列，提供 CPU、内存、磁盘和墙钟配额。
2. 增加 lease contention、probe duration、temp reserved/free、reject reason 等非敏感指标。

### Ignore for Now

- 真实 2GB、非允许编码和不可解码首帧仍可按 Phase 7 已记录的人工/专项证据债管理，但不得因此勾选对应验收。
- V29 旧 checksum 仅在存在未披露长寿命库时需要处置；当前已知发布边界不把它列为缺陷。

## 15. Quick Wins

1. 将磁盘判断改为累计预留值，并增加纯单元并发测试。
2. 在 Compose、`.env.example` 和 Phase 14 一次性补齐六个 `VIDEO_PROBE_*`。
3. 为 `SERVER_CHUNK` 先复用现有 token lease，随后再收敛统一状态机。
4. 抽出 `Clock`/空间提供器/lease backend，让 TTL 和容量分支可用毫秒级安全测试覆盖。

## 16. Long-term Refactor Plan

1. **统一定稿状态机**
   - Motivation: direct 与 server-chunk 的 liveness、幂等和恢复语义已分叉。
   - Approach: 持久化 finalization attempt、object key、phase、fencing token；两种入口共用 finalize worker。
   - Risk: 迁移旧 MERGING 会话时需避免重复对象/重复 review。
   - Testing strategy: 对每个持久化阶段做退出后接管 contract test。

2. **隔离媒体探测**
   - Motivation: 主 JVM 线程无法硬终止 JCodec 单次调用。
   - Approach: 独立进程/worker，设置 CPU、内存、临时盘和墙钟限制，结果签名/版本化。
   - Risk: 增加部署组件与队列状态。
   - Testing strategy: 小文件、短 deadline、受控阻塞 worker，验证 kill、清理、重试和单飞。

3. **集群级容量与可观测性**
   - Motivation: 当前 Semaphore/AtomicLong 只表达单进程预算。
   - Approach: 每实例硬配额 + 调度层全局并发；暴露非敏感 gauge/counter 与 readiness。
   - Risk: 配额过紧会降低峰值吞吐。
   - Testing strategy: 多实例确定性 admission 测试和配置展开验收，不依赖压力或攻击性载荷。
