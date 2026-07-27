# HANDOFF.md — 交接说明（接手必读）

> 目的：让后续 codex / Claude 在**本机（Windows + Git Bash）** 无障碍接手。
> 顺序：先读本文件 → 再按 `AGENTS.md` §0 读其余文档。当前 WS 链与缺失阶段均已独立复核；整体仍为 CHANGES REQUESTED。
> 当前排期、逐阶段复核矩阵与所有历史计划的合并入口：`docs/CURRENT-EXECUTION-PLAN.md`。

---

## 0. 当前接力快照（2026-07-27）
- 当前分支 `codex/phase00-remediation`；Phase 44 第四轮独立 PASS，U-003 全部关闭。Phase 0 / U-004 第二轮正式结论仍为 **CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 3 Low）**，报告 `docs/reviews/phase-00-second-remediation-rereview-2026-07-27.md`。第三轮产品/测试/门禁候选 `cc5786cc1c1ed1a243152bf306eca6998bbc0eee` 已按原失败条件整改：候选 Git-object 快照贯穿 preflight/formal 且三点绑定 HEAD，verifier 直接以 expected candidate 的 spec blob 为权威；marker 与实际配置、MySQL UUID、Redis run_id、MinIO 身份对象/deployment ID 及 0/0/1/0 初始状态绑定；Windows containment/strict manifest、应用启动 `[~]` 与 32/32 参数矩阵同步收口。离线 gate 60/60、Surefire 274/274、target guard 8/8、9 模块 clean package、前端全绿；真实依赖 exact 7/33、Compose config 与必要浏览器证据未执行，提交材料为 `docs/reviews/phase-00-third-remediation-submission-2026-07-27.md`。状态仍为复核退回，需用户动态证据及独立 PASS 后才放行最终全量审计。`main` 仍为 `e4f8228`，无 remote，禁止 push/擅自 merge。
- Phase 44 第四轮动态闭环：专用全新 MySQL/Redis 栈精确 `Phase44CacheCommitWindowIT` **16/16**，XML SHA-256 `3505ead4877e0d280e56d837d3d7c0c5d2e6038a8279d979c9d9334769394981`，两个第四轮反例均实际执行；棕地库只读 identity preflight 为 `columns=2/2, invalid=0, noncanonical=0, collision=0`；Compose 非默认 `9m/3m` 与默认 `2m/20s` 双向展开成立。独立离线 `platform-system` 应为 **53/53**（用户摘要中的 46/46 已按完整日志/XML 更正），后端 9 模块 package、前端 type-check/build、报告 lint 与 diff check 均 PASS。第三轮 1 Medium 与 4 Low 的原失败条件全部关闭；新增 1 个非阻断 Low：preflight 尚未接入权威发布步骤，包装器执行也缺少数据库/账号/实例 target marker。Phase 44 只放行下一项 Phase 0，不代表 merge、部署或稳定发布 GO。
- Phase 53 候选把四类对象发布到原始 SHA-256 派生的内容版本 key；检查时已存在的污染对象失败关闭且不覆盖，旧固定 key 保留。视频经完整读回、可信 tree 指纹、900 秒/900 帧/H264/策略与探测版本核对后，单个数据库事务统一切换 `file_object`、过程/免考材料、`video_review` 和 `video_upload_session.object_key`。独立聚焦 31/31、证据整改复核全量离线 195/195、9 模块 package、前端 type-check/build、fat JAR 内容与 diff check 均 PASS；12 份关键日志现可由 fresh clone 取得并按已归档哈希复核。
- WS-3 原有预签名直传能力保持不变；整改包新增服务端逐字节读取最终 MinIO 对象、计算受信 SHA-256 tree 指纹并用 JCodec 校验实际 MP4/H.264/时长/首帧，校验失败关闭。秒传只允许同 uploader、同 student、同一受信对象及已有合格视频记录复用，普通响应不再暴露内部内容指纹。
- 2026-07-23 用户在 Claude 不可用期间明确授权 Codex 独立复核。WS-3 第二轮 `32da735` 的独立报告 `docs/reviews/ws-03-second-remediation-rereview-2026-07-23.md` 仍为 **CHANGES REQUESTED**：时间线、fast-hit、review 行锁和 V29 已闭环，但 `SERVER_CHUNK` 崩溃恢复、并发总磁盘预留、可终止探测时限仍有 3 High，另有 lease 续租/生产配置/资源测试 3 Medium。Phase 53 demo 元数据失配是 U-003 的独立 High，未混入本次报告。
- 第三轮 `df22e5b` 已完成上述整改，但独立重核 `docs/reviews/ws-03-third-remediation-rereview-2026-07-23.md` 仍为 **CHANGES REQUESTED（4 High / 5 Medium）**。High 为：Redis fencing token 在序列过期/恢复后可 ABA；server finalize 输给 assign 后永久 MERGING；direct 接管时 multipart 已丢失会永久 MERGING；持久 `video-probe-temp` 无崩溃孤儿清扫。Medium 为：基础设施故障误判内容失败、强杀未确认退出、磁盘双重计数、MinioClient 未受显式超时控制、fat-JAR worker 缺自动化门禁。
- 第四轮 `ee190f3` 已实现：V31 把 `finalization_token` 固化为数据库永久高水位，Redis 改用随机 owner；server/assign、direct 丢失 multipart 及 server 源分片丢失均可离开 MERGING；探测临时卷增加 owner heartbeat、启动/周期 reaper 和独占锁；强杀后确认 PID、活孤儿阻断准入；容量快照与受管写入原子化并补准入二次检查；MinioClient/AWS S3Client 都有正数总调用/连接/读写超时；fat-JAR worker 纳入 Maven verify。
- 第四轮独立重核 `docs/reviews/ws-03-fourth-remediation-rereview-2026-07-23.md` 为 **CHANGES REQUESTED（1 High / 1 Medium / 2 Low）**。第三轮 4 High / 5 Medium 均可按原口径关闭；新 High 是 JCodec 内容解析 `IOException` 被判基础设施故障，损坏 MP4 可永久卡 MERGING；新 Medium 是 FAILED 对象删除无持久 reconciliation，SERVER 还可由旧 owner 迟到 compose 复活稳定 key；两个 Low 为临时清扫公平性与裸机父 JVM 崩溃后的 worker 监管恢复。
- 第五轮整改已完成：worker 升级为 V4，以源通道 I/O 跟踪区分损坏媒体解析异常与真实基础设施故障；V32 增加 generation 候选台账，SERVER 使用 `/g-{generation}.mp4` 独立对象键，失败/失权对象经持久清理、跨节点租约和 `CLEANED` 墓碑周期复查；生产启动回填/首次对账异步投递到单线程防重入执行器，之后每分钟独立触发，不阻塞 readiness。临时工件清扫改为持久公平游标和 O(scanLimit) 候选内存；worker 以父 PID + 精确启动时刻 watchdog 自行终止失联子进程。
- 第五轮动态证据已执行：V32 从 V31 升级 1/1；T-VID-2L 固定损坏 MP4 在 direct/server 均收敛 `VALIDATION_FAILED` 并可重新初始化；T-VID-2M 覆盖首次删除失败、过期清理租约回收、首次 `CLEANED` 后真实迟到 compose 再删除、ACTIVE/REGISTERED/file_object 保护和遗留 FAILED 回填，共 5/5。claim 安全下限/极值及 direct 已认领后回退、取消、缺失 multipart 的候选退休反例 3/3，worker/清扫/调度聚焦单测 19/19。提交材料为 `docs/reviews/ws-03-fifth-remediation-submission-2026-07-23.md`；这些是开发者证据，正式结论由下一条第五轮独立重核记录。
- 第五轮最终门禁：本轮专用空库成功执行 32 个迁移，最终版本 V32；Surefire 148/148、Failsafe 168/168，合计 316/316，0 failure/error/skip；Phase 7 44/44，V32 迁移 IT 1/1；前端 type-check/build、dev/prod Compose config 与 `git diff --check` 均通过。
- 第五轮独立重核 `docs/reviews/ws-03-fifth-remediation-rereview-2026-07-24.md` 为 **CHANGES REQUESTED（2 Medium）**。第四轮 1 High / 1 Medium / 2 Low 全部关闭；新问题为：① reconciliation 实际工作虽有专用 executor，但 cron trigger 仍与同步全量备份/清理共享 Boot 默认单线程 scheduler，长时备份会阻止任务被提交；② V32 `video_finalization_object_candidate` 未加入全量备份显式清单，恢复时会丢历史世代、待清理状态与墓碑。独立安全白名单 7/7、后端 package、前端 type-check/build、dev/prod Compose config、报告 lint 和 diff check 通过。
- 第六轮整改候选与开发者自测已完成：生产同时保留普通 `taskScheduler` 和视频专用 `videoFinalizationReconciliationTaskScheduler`，reconciliation cron 显式绑定后者；真实 scheduling 反例证明默认 scheduler 上的同步备份被 latch 阻塞时，对账仍连续触发并提交。应用逻辑全量备份现覆盖全部 37 个 schema 表（明确排除 `flyway_schema_history`），含 V32 candidate 台账；隔离 scratch schema 回放多个 generation 的 `CLEANUP_PENDING/CLEANING/CLEANED` 全字段后，真实 reconciler 可继续安全清理并释放 claim。
- 第六轮最终开发者门禁：Surefire 149/149、Failsafe 169/169，合计 **318/318**、0 failure/error/skip；Phase 7 44/44，调度配置/隔离 3/3，candidate 备份恢复/续跑 1/1；前端 type-check/build、dev/prod Compose config 与 `git diff --check` 均通过。提交材料为 `docs/reviews/ws-03-sixth-remediation-submission-2026-07-24.md`。
- 第六轮独立重核 `docs/reviews/ws-03-sixth-remediation-rereview-2026-07-24.md` 为 **PASS**：第五轮新增的 2 Medium 全部关闭；仅有“33 个迁移”应为“32 个迁移、最终 V32”的 1 Low 勘误，不阻断 WS-3/U-002。独立安全门禁为后端 9 模块 package、fat JAR class、前端 type-check/build、dev/prod Compose config 与 diff check；按用户要求未重跑 latch、外部 MySQL/MinIO 写入或任何可能属于 cyber 的验证。
- Phase 0、29、35b、36–53 的缺失报告已补齐；经 Phase 42、Phase 39、Phase 41、Phase 47、Phase 53 与 Phase 44 后续增量复核，当前仅 Phase 0 仍复核退回。阶段总审计原始快照为 `docs/reviews/phase-gap-audit-2026-07-23.md`，后续增量报告优先于原始快照。
- Phase 42 原始报告 `docs/reviews/phase-42-review.md` 曾为 **CHANGES REQUESTED**。首轮候选只修其 PG-H3 Major：每个逐行 `REQUIRES_NEW` 事务及失败明细先锁 batch 并确认持久状态仍为 `IMPORTING`；rollback 在单一事务内以 batch `FOR UPDATE` 作为第一条 SQL，等待在途行、锁定完整 ref/业务记录后逆序补偿，并将补偿结果与 `ROLLED_BACK/PARTIAL_ROLLBACK` 原子提交；confirm 收尾 CAS 未命中时读取数据库真实状态并返回“导入已停止”，不得伪报成功。
- Phase 42 开发者证据：两个确定性交错反例 **2/2**，分别覆盖“首行已提交后 rollback 先完成”和“在途行持锁、rollback 等待其提交后补偿完整 3 refs”；`Phase10ExchangeIT` **10/10**。fresh 隔离依赖全量 `clean verify` 为 Surefire **149/149**、Failsafe **171/171**，合计 **320/320**；前端 type-check/build 通过。临时 `tcp-phase42` 容器与网络已移除，无数据卷；未启动常驻应用。
- Phase 42 无 DDL、业务规则、权限点或前端生产代码变化；生产 `ExchangeImportHook` 仅为空操作的确定性交错观察点。本轮不重新定义既有 `PARTIAL_ROLLBACK` 自动重试语义，该存量语义债已在提交材料中明确，不冒充闭环。
- Phase 42 首轮独立增量重核 `docs/reviews/phase-42-remediation-rereview-2026-07-24.md` 为 **CHANGES REQUESTED（1 Medium / 1 Low）**：原 PG-H3 并发 Major 已按原口径关闭；新 Medium 为每个逐行/错误明细事务的 `SELECT * ... FOR UPDATE` 重复装载整批 `preview_json`，万行主路径形成 O(N²) DB→JVM 数据量；新 Low 为错误明细锁屏障缺专用交错反例。第二轮不得移除 batch 锁，只把逐行锁查询收窄为 status-only 或 `id,status`。
- Phase 42 第二轮候选已落实上述最小修复：逐行/错误明细锁为标量 `SELECT status ... FOR UPDATE`，rollback 单次全元数据锁保留；静态 MappedStatement 与真实 confirm 探针共同确认 3 条实际行锁 SQL 均为 status-only。T-IMP-5C 双向交错分别证明 rollback 先提交时无迟到错误明细，以及错误明细先持锁时 rollback 等待其写入/提交后才能取得锁。
- 第二轮开发者证据：`Phase10ExchangeIT` **13/13**；fresh 隔离依赖全量 `clean verify` 为 Surefire **149/149**、Failsafe **174/174**，合计 **323/323**；32 个版本化生产迁移至 V32；前端 type-check/build 与 diff check 通过。生产 hook 仍为空操作，无 DDL、业务规则、权限点或前端生产变化；未启动常驻应用，未执行 cyber/压测。
- Phase 42 第二轮独立重核 `docs/reviews/phase-42-second-remediation-rereview-2026-07-24.md` 为 **PASS**：首轮新增的 1 Medium / 1 Low 全部关闭，没有新增代码 finding。独立执行后端 9 模块 package、前端 type-check/build 与 diff check 均通过。323/323 XML 早于最终测试源码，当前源码虽已重新编译且材料声明只改断言说明文字，仍作为非阻断证据限制保留；严格提交级证明须由用户在最终提交上自行重跑。
- 第四轮门禁证据：整改者专用全新数据卷 Flyway V1–V31，Surefire 140/140、Failsafe 159/159，合计 299/299；Phase 7 36/36。独立复核核对 XML 后另跑安全白名单 clean verify 17/17、后端 package、前端 type-check/build、dev/prod Compose config、MinIO 依赖树与 fat-JAR worker，均通过。按用户要求未执行畸形媒体、破坏性故障或攻击性并发。
- Phase 42、Phase 39、Phase 41、Phase 47、Phase 53 与 Phase 44 已关闭。Phase 39 第二轮独立报告确认 `batch → refs → college IDs 升序 → business child`、deleted-parent fail-closed、delete/rollback 双向反例和实际 query-entered 探针成立；Phase 41 真实 MySQL 8.4 CLI 恢复与账号/授权两项门禁也已独立核验 PASS；Phase 47 的 SDK 缺省合同、安全日志分类及 PASS 后日志 raw 参数测试 Low 均已闭环；Phase 53 的对象升级行为与证据可移植性均已通过独立复核；Phase 44 第四轮关闭 owner-loss 发布窗口与原 4 Low。近端只处理 Phase 0；其 PASS 后执行用户要求的全量审计。
- Phase 41 上一轮独立重核确认 PG-M2 初始化标识符/凭据路径代码级闭环，但以生成列、hex 大行 packet、过早回滚反例和 workflow 路径共 **1 High / 2 Medium / 1 Low** 退回。第二轮候选 `b5ed7f5` 已改为 metadata 驱动写入列、Base64 + 编码前 32 MiB 预算、64 MiB packet 契约、全部正常 INSERT 后的 COMMIT 前故障，并勘误 workflow；5 张生成列表与 13 MiB JSON 反例均进入 `Phase41BackupIT`。
- Phase 41 第二轮独立报告为 `docs/reviews/phase-41-second-remediation-rereview-2026-07-25.md`，结论 **CHANGES_REQUESTED（0 个新增代码 finding；2 个动态证据闸门未满足）**。上一轮生成列 High、packet Medium、回滚 Medium 和 workflow Low 均达到代码级整改，Low 可静态关闭；其余运行闭环及 PG-M2 仍须当前候选的真实 MySQL 8.4 日志。两项门禁可能涉及 schema、对象、账号、容器或匿名卷，只能由用户在专用隔离环境执行；Phase 47 不放行。
- Phase 41 动态证据增量报告 `docs/reviews/phase-41-second-remediation-dynamic-evidence-rereview-2026-07-25.md` 已正式 **PASS**：Surefire 149/149、`Phase41BackupIT` 1/1、真实 MySQL 8.4 CLI 恢复与 sourced/executable 初始化账号/精确授权均通过，上一轮 1 High / 2 Medium / 1 Low 全部关闭。正式 PASS 时新增的 Gate A 冷认证缓存前置 1 Low 已后续修正：示例、runner fail-fast、恢复手册与纯 stub CI 契约一致闭环；不改写原报告当时的 1 Low 计数。Phase 47 已放行进入其既有退回项整改。
- Phase 47 第一轮整改独立重核 `docs/reviews/phase-47-remediation-rereview-2026-07-26.md` 为 **CHANGES_REQUESTED（1 Medium / 1 Low）**。原“非目标读取异常后整桶覆盖”危险侧已经关闭；但锁定的 MinIO SDK 8.5.12 会把 `NoSuchLifecycleConfiguration` 内部归一为 `null`，`aa6f81c` 却把 null 判为非法并返回 false，导致无生命周期配置的新桶永远不能创建托管规则。Mockito 正例模拟了 SDK 不会向业务层抛出的异常，12/12 因合同错层而虚绿。Low 为所有 S3 错误只记录 `ErrorResponseException`，缺少安全白名单分类。Phase 53 继续不放行。
- Phase 47 第二轮独立重核 `docs/reviews/phase-47-second-remediation-rereview-2026-07-26.md` 为 **PASS**：`3bddf6c` 对齐 SDK 8.5.12 高层 null sentinel，异常/畸形响应继续失败关闭，六类固定日志分类关闭上一轮 Low；独立离线模块 16/16、生命周期 13/13、9 模块 package、依赖/字节码与 diff check 均通过。报告当时新增的 1 个非阻断日志测试 Low 已由 `191a3ad` 后续闭环：模块 18/18、生命周期 15/15、9 模块 package 与最终独立只读复核均通过；原报告计数不改写。
- V32 发布必须停写并停止全部第五轮之前的后端与 worker，确认无旧进程后执行 Flyway 至 V32，再启动全部第五轮新实例、核对启动回填/对账与 generation 对象键，最后恢复写流量；禁止 V31/旧稳定 key 协议与第五轮混部，V32 后禁止回滚旧协议二进制。每个后端实例必须独占具有独立配额/文件系统的 probe 卷，禁止共享目录/卷或 `docker compose --scale` 复用当前命名卷。
- 遵守用户安全边界：未执行漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz 或针对现有服务的破坏操作。T-VID 使用固定 44 字节结构夹具、一次受控对象删除失败及本轮专用隔离依赖；任何后续可能属于 cyber 的命令必须明确列出并交由用户亲自决定/执行。
- 2026-07-22 的“缺阶段报告”证据债务已在 2026-07-23 清零；其发现的 CI MinIO/type-check 门禁已在当前整改包补齐并独立重核 PASS。历史结论保留于 `docs/reviews/progress-review-2026-07-22.md`。
- `.claude/audits/`、审计 HTML、`docs/audit-remediation-plan.md`、`docs/prompts/` 是本地未跟踪审计资料，不得纳入功能提交。

> 下方 §1 是 2026-06-14 的历史交接快照，保留用于环境与早期实现追溯；当前状态以上述 §0、`PROGRESS.md` 与 `DEVLOG.md` 顶部为准。

## 1. 历史状态（截至 2026-06-14）
- **Phase 0 与 Phase 1 全部完成，并通过 Claude 阶段复核**（Phase 1 复核 PASS：`docs/reviews/phase-01-review.md`）；**Phase 2 已由 Codex 自测完成并置「待复核」**，等待 Claude 按 `docs/REVIEW-GATE.md` 复核。
- 待确认事项 20 项**已于 2026-06-14 书面确认**（`docs/待确认事项确认单.md`）：原确认的业务取值变更为 `validate.name.mode`→`loose`（迁移 `V6`）；#17/#18 的自动开户与证件号后六位口令已被 2026-07-15 WS-2 安全整改取代为 `false/random`（迁移 `V27`）。
- 仓库：本地 git，**无远程（私有，未开源）**；当前工作分支为 `feature/phase02-T024-authentication`。
- 提交链：
  ```
  08c638f 前端脚手架 + CI [T-008/009/010]   ← Phase 0 完成
  121a489 MinIO文件服务 + 切面 [T-006/007]
  4490c12 DB链路 Flyway/MyBatis-Plus [T-003/004/005]
  698b51f Maven骨架 + common + boot [T-001/002]
  6948682 规划基线
  ```
- 已验证（详见 `DEVLOG.md`）：后端 `mvn package` 9 模块 SUCCESS；运行后 Flyway 迁移 v1、4 张表、`sys_param` 17 行、`/api/health`=UP、`/doc.html`=200；MinIO 上传→预签名→下载 内容一致；前端 `vite build` 通过。

### Phase 2 待复核状态（2026-06-14 22:33 CST）
- 当前工作分支：`feature/phase02-T024-authentication`。
- T-023 已提交；T-024~T-029 代码已完成并通过 Codex 自测，`PROGRESS.md` Phase 2 状态为「待复核」（不得由 Codex 自行置 ✅）。
- Codex 本轮未启动任何常驻 8080/5173 服务；`Get-NetTCPConnection -LocalPort 8080,5173` 无监听。
- Flyway 当前版本：`V8` 已成功应用，`flyway_schema_history.version=8` 的 checksum 为 `-193563120`。
- 为保证 AT-13 可重复验证，`V8__rbac_seed.sql` 已新增 Phase 2 专用测试学院：
  - `PHASE2_COLLEGE_A` → `id=800000000000000201`
  - `PHASE2_COLLEGE_B` → `id=800000000000000202`
  - `test_student/test_college_clerk/test_college_auditor/test_review_teacher` 已绑定学院 A。
- 当前后端已具备：
  - 认证：`/api/auth/captcha`、`/api/auth/login`、`/api/auth/refresh`、`/api/auth/change-pwd`、`/api/auth/me`
  - JWT + BCrypt + 首次改密拦截 + 登录失败锁定参数化
  - `@PreAuthorize` 权限校验
  - `DataScopeContext` / `DataScopeAspect` / `DataScopeService` 真正落地
  - 系统管理接口：`/api/system/user|role|permission/tree`
  - 探针接口：`/api/phase2/probe/*` 用于 401/403/AT-13 反例验证
- 当前前端已具备：
  - `frontend/src/views/LoginView.vue` 真实验证码登录 + 首次强制改密弹窗
  - `stores/user.ts` / `router/index.ts` / `layouts/MainLayout.vue` 已接入真实 `me`、refresh、权限守卫与动态菜单
  - `frontend/src/views/system/SecurityManageView.vue` 覆盖用户/角色/权限/数据范围页
- 当前自测证据：
  - `mvn -B -ntp -pl platform-boot -am -Dtest=Phase2SecurityIT -Dsurefire.failIfNoSpecifiedTests=false test` 通过（2 tests，随机端口，自然退出）
  - `mvn -B -ntp package` 通过
  - `npm --prefix frontend run type-check` 通过
  - `npm --prefix frontend run build` 通过（仅 Vite large chunk warning）
  - DB 核验 7 角色、52 权限、104 角色权限映射；关键矩阵正反例通过；测试账号锁定状态已清理
- 下一步：
  1. 提交当前 Phase 2 待复核版本（若尚未提交）。
  2. 交 Claude 按 `docs/REVIEW-GATE.md` 复核 AT-13、§15.1 矩阵和越权反例。
  3. 若 Claude 退回，只修 Blocker/Major 并重交；PASS 后才可置 Phase 2 为 ✅ 并进入 Phase 3。

## 2. 环境与工具链（本机特性，务必注意）
| 项 | 值 |
|---|---|
| 仓库根 | `C:\Users\wenbibuhaoqwq\Desktop\teacher-cert-platform`（Bash: `/c/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform`） |
| Shell | **Git Bash（POSIX sh）**，不是 PowerShell |
| JDK17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`（路径含空格，命令需引号） |
| Maven 3.9.9 | `C:\Users\wenbibuhaoqwq\tools\apache-maven-3.9.9`（手动装，winget 无 `Apache.Maven`） |
| 其它 | git 2.54、Docker Desktop、Node v24.15 + npm（**pnpm 未装，用 npm**） |

- **坑①（PATH）**：`mvn`/`java`/`git` 已写入用户级 PATH，但旧进程继承旧环境 → **新开终端**才直接可用；脚本里用全路径最稳（见 §3）。
- **坑②（GBK 控制台）**：中文 Windows 控制台默认 GBK，java/mvn 的**中文告警会显示乱码**（如 `δ֪`），**不影响编译**（源码已 UTF-8，父 POM 强制）；HTTP/DB 中文正常。
- **坑③（Docker）**：依赖容器需 Docker Desktop 引擎运行；常规容器名 `tcp-mysql`/`tcp-redis`/`tcp-minio`。用户已明确弃用 bigdata 环境：`hdp11/hdp12/hdp13` 容器于 2026-07-22 删除，`bigdata:3.3.0` 镜像及 9 个 `bigdata_*` 数据卷于 2026-07-23 删除；复核仍应使用独立 Compose 和独立 schema，避免污染共享 dev 数据。

## 3. 一键操作命令（本机已验证，Git Bash 复制即用）
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"
MVN="/c/Users/wenbibuhaoqwq/tools/apache-maven-3.9.9/bin/mvn"
REPO="C:/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform"

# 启动依赖（MySQL/Redis/MinIO）
docker compose -f "$REPO/docker-compose.dev.yml" up -d

# 后端：构建；运行期服务必须在 Codex 外部终端启动
"$MVN" -f "$REPO/pom.xml" -B -ntp -DskipTests package
export JWT_SECRET="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
export SPRING_PROFILES_ACTIVE="dev"
cd "$REPO"
# 下列 dev-serve 命令只能在外部 Git Bash 窗口执行，禁止由 Codex/exec 直接调用：
bash scripts/dev-serve.sh backend http://127.0.0.1:8080/api/health "$JAVA_HOME/bin/java" -jar "$REPO/platform-boot/target/teacher-cert-platform.jar"
#   健康 curl http://localhost:8080/api/health   文档 http://localhost:8080/doc.html
bash scripts/dev-stop.sh backend

# 前端；dev 服务同样只能在 Codex 外部终端启动
npm --prefix "$REPO/frontend" install
bash "$REPO/scripts/dev-serve.sh" frontend http://127.0.0.1:5173 npm --prefix "$REPO/frontend" run dev -- --host 127.0.0.1
bash "$REPO/scripts/dev-stop.sh" frontend
npm --prefix "$REPO/frontend" run build

# 如 Git Bash 因系统资源/权限异常不可用，可用 PowerShell 等价脚本：
# $env:JWT_SECRET='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
# .\scripts\dev-serve.ps1 backend http://127.0.0.1:8080/api/health 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe' -jar platform-boot\target\teacher-cert-platform.jar
# .\scripts\dev-stop.ps1 backend

# DB 查看 / 停依赖
docker exec tcp-mysql mysql -uroot -proot123 teacher_cert -e "SHOW TABLES;"
docker compose -f "$REPO/docker-compose.dev.yml" down      # 加 -v 连数据卷删除

# git（本地，勿加远程/勿 push，保持私有）
git -C "$REPO" add -A && git -C "$REPO" commit -m "feat(T-0xx): ..."
```

## 4. 既定约定（不可擅改，否则破坏一致性）
- 基础包 `cn.edu.gpnu.platform`；groupId `cn.edu.gpnu`；version `1.0.0-SNAPSHOT`。
- 8 模块：业务模块均依赖 `platform-common`；`platform-boot` 聚合 common/system/security/file（business/exchange/statistics 待各自 Phase 接入 boot 依赖）。
- **Lombok 已在父 POM 统一声明**——新模块用 Lombok 无需再加依赖。
- 版本锁定在**父 pom**：Spring Boot 3.4.13 / MyBatis-Plus 3.5.16 / Knife4j 4.5.0 + springdoc 2.8.17 / FastExcel 1.1.0 / MinIO 8.5.12 / jjwt 0.12.6；Flyway 由 Boot 3.4.13 管理并显式引入 `flyway-mysql`。前端使用 npm + `package-lock.json`。
- DB：库 `teacher_cert`，`root`/`root123`；MinIO `minioadmin`/`minioadmin123`，bucket `teacher-cert`。
- **Flyway 迁移**：`platform-boot/src/main/resources/db/migration/`，**V1~V8 已用**（V1 base / V2 dict / V3 dict_seed / V4 region_seed / V5 subject_seed / V6 confirmed_params / V7 rbac / V8 rbac_seed）→ **后续从 `V9__student.sql` 起**（Phase 3）；版本号以**磁盘 max+1** 为准、不改已发布脚本、种子幂等。
- 实体继承 `BaseEntity`（id/审计字段/逻辑删除自动）；Mapper 放 `**/mapper`（已 `@MapperScan("cn.edu.gpnu.platform.**.mapper")`）；统一返回 `Result`；写操作 `@AuditLog`；列表/导出/统计查询 `@DataScope`；当前用户取 `UserContext`。
- **文本化字段全链路 String + Excel `@`**（学校代码/学号/证件号/出生日期/证书编号/有效期限）——AT-01 生命线，勿用数值/日期类型。

## 5. 当前整改入口 → Phase 0 / U-004 第三轮
1. 先读 `AGENTS.md` → `docs/REVIEW-GATE.md` → `docs/CURRENT-EXECUTION-PLAN.md` → `docs/reviews/phase-00-second-remediation-rereview-2026-07-27.md` → `docs/reviews/phase-00-third-remediation-submission-2026-07-27.md` → `docs/phase-00-脚手架.md`。第二轮正式状态仍为 CHANGES_REQUESTED（2 Medium / 3 Low），第三轮候选 `cc5786c` 已完成代码/测试/文档整改；下一动作只能由用户在获授权的全新一次性 MySQL/Redis/MinIO 隔离栈执行包含提交材料的最终 SHA exact 7/33 gate，并补 Compose/browser 必要证据，随后独立复核。Codex 不执行 Docker、数据库、网络、浏览器或其它可能属于 cyber 的指令；Phase 0 独立 PASS 后才执行最终全量审计。
2. 已闭环：服务端 `SHA256_TREE_V1` 可信指纹；同 uploader/student + 既有 PASS 的秒传边界；普通 VO 去 `fileMd5`；GitHub Actions 真实 MinIO/桶初始化与前端 type-check。U-001 可视为 PASS。
3. 第二轮已闭环：JCodec 三时长交叉核验与唯一视频轨；当前策略/探测器版本 + MinIO HEAD 的 fast-hit；定稿与 assign 共用 review 行锁；V29 逐项检测恢复。
4. 第三轮已实现：`SERVER_CHUNK /merge` 使用可续租 lease、稳定 object key 和数据库当前 token；磁盘按活跃任务累计总预留准入；JCodec 在受限堆独立 JVM 内运行；S3 超时、官方 Compose/.env/专用临时卷一并落地。这些正常路径已确认有效，但不能视为完整 fencing/recovery。
5. 第四轮已实现且旧 9 项关闭：数据库持久 high-water fencing + Redis 随机 owner；server finalize/assign、direct NoSuchUpload 和 server 源分片丢失收敛；临时卷 owner/reaper/独占锁；worker 死亡确认与活孤儿准入；原子容量公式；MinioClient/AWS S3Client 正数超时；fat-JAR smoke 纳入 verify。发布采用 V31 停机切换且每实例 probe 卷独占。
6. 第五轮已实现：V4 worker 区分 JCodec 内容解析异常与真实源/结果 I/O；V32 generation 候选台账、独立 SERVER 对象键、持久清理/墓碑复查和生产启动回填/每分钟调度；持久公平清扫游标；父 PID + 启动时刻 watchdog。
7. 第五轮证据：本轮专用空库 32 个迁移、最终 V32；Surefire 148/148、Failsafe 168/168，合计 316/316；Phase 7 44/44、V32 迁移 IT 1/1；T-VID-2L/2M + 对象保护/遗留回填 5/5、claim/direct 终态反例 3/3、聚焦单测 19/19、预签名端点/CORS 1/1；前端 type-check/build、dev/prod Compose config 与 `git diff --check` 均通过。第五轮独立复核确认旧 4 项关闭并新增 2 Medium。
8. 第六轮候选：生产双 `TaskScheduler` 隔离普通定时任务与视频对账 trigger；blocked-backup 真实 scheduling 反例通过。candidate 台账进入 37 表逻辑全量备份，scratch restore 保留多个 generation 的 claim/retry/tombstone 全字段并可继续真实对账。开发者全量 149/149 + 169/169 = 318/318；Phase 7 44/44；前端/Compose/diff 均通过。
9. 正式状态：第六轮独立复核 **PASS**，第五轮新增 2 Medium 已关闭；1 Low 迁移数量勘误不阻断。Phase 41 整库恢复已于后续动态证据复核关闭；Phase 53 demo High 的生产行为与证据归档 Medium 均已由后续独立报告关闭并正式 PASS；Phase 7 广覆盖证据债继续保留。
10. 安全边界：本轮动态反例只使用固定小型结构夹具和专用隔离依赖，不包含漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz 或对既有服务的破坏。任何后续可能属于 cyber 的命令必须先明确指出并交由用户亲自决定/执行。若需要启动后端/前端做运行期验证，仍必须遵守 `AGENTS.md §6.1`，Codex/headless exec 不启动常驻服务。
11. Phase 42 首轮候选：batch 行锁串行化屏障、rollback 单事务补偿、confirm 收尾 CAS 失败关闭均已落地；独立重核确认原 Major 功能闭环，但因逐行 `SELECT *` 重读整批 `preview_json` 的 O(N²) Medium 与错误明细测试 Low 退回。
12. Phase 42 第二轮候选：status-only 行锁投影、真实调用链 SQL 探针和 T-IMP-5C 双向交错已落地；Phase 10 IT 13/13、全量 323/323，提交材料为 `docs/reviews/phase-42-second-remediation-submission-2026-07-24.md`。
13. Phase 42 第二轮独立重核 **PASS**：首轮 1 Medium / 1 Low 全部关闭且无新增代码 finding；正式报告为 `docs/reviews/phase-42-second-remediation-rereview-2026-07-24.md`。323/323 的最终提交同源性限制已记录，不阻断本阶段。
14. 第一轮状态：Phase 39 候选 `34aeec7` 的在线父锁主路径经独立复核成立，但正式报告 `docs/reviews/phase-39-remediation-rereview-2026-07-24.md` 以 **1 High / 1 Low** 退回。High：旧版本可持久化 before=A/after=B 的跨学院 student UPDATE ref，rollback 可绕过目标学院父锁；Low：`beforeLock` contender 信号早于 mapper/JDBC query。
15. Phase 39 第二轮候选：代码提交 `73406ed` 在任何 child 锁前解析 student/training/certificate 的 UPDATE `before_json.collegeId`，去重升序预锁，非法/缺失/已删除父级按 ref 冲突关闭；`deleteCollege` 补 active training/certificate 直接计数；并发证据推进到精确 SQL + collegeId 的 `StatementHandler.query` 边界，并新增 certificate-only 与 `A2/A1/A2` 多父锁序反例。开发者证据为 Phase 39 **11/11**、Phase 10 **13/13**、全量 **334/334**。
16. Phase 39 第二轮独立重核 **PASS**：冻结 `1a69c70..dbd8633`，代码点 `73406ed`；第一轮 1 High / 1 Low 全部关闭，本增量 0 High / 0 Medium / 0 Low。正式报告为 `docs/reviews/phase-39-second-remediation-rereview-2026-07-24.md`。三个基线既有债务候选只留最终全量审计，不阻断本次原问题关闭。
17. Phase 41 上一轮候选 `c69ea73` 的独立报告 `docs/reviews/phase-41-remediation-rereview-2026-07-25.md` 以 **1 High / 2 Medium / 1 Low** 退回：5 个生成列被显式回放、hex 大行缺 packet 契约、失败注入过早、合并前 workflow 路径不可用。
18. 第二轮候选 `b5ed7f5` 已逐项整改，提交材料为 `docs/reviews/phase-41-second-remediation-submission-2026-07-25.md`。普通安全门禁为 Surefire **149/149**、9 模块 package、前端 type-check/build、Compose 配置解析、脚本语法、stub 初始化契约及 diff check；未运行 `Phase41BackupIT` 或任何真实基础设施写入。
19. 第二轮独立静态重核：冻结 `17b11aa..ef6b550`，代码点 `b5ed7f5`。生成列、packet、后段故障回滚和 workflow 说明均达到代码级闭环，本增量 0 个代码 finding；独立 Surefire 149/149、9 模块 package、前端 type-check/build、Compose 配置解析、diff check 与报告 lint 通过。现有 Phase41BackupIT XML 早于候选且为 MySQL 8.0，不能作为当前动态证据，正式结论为 CHANGES_REQUESTED。
20. 用户已在专属一次性隔离环境完成两项真实门禁并归档证据：Gate A 为 Surefire 149/149、`Phase41BackupIT` 1/1、真实 MySQL 8.4 CLI 恢复与最终 PASS；Gate B 为 sourced `0644`、executable `0755` 及总 PASS，覆盖真实账号认证与精确授权。scratch schema、专属对象、五个专属容器、六个匿名卷及临时 runner 镜像/文件均已按执行者收尾核验清理，共享 `tcp-*` 未触碰；宿主 `mysql:8.4` 镜像缓存保留。
21. Phase 41 动态证据独立重核 **PASS**：根目录/归档日志哈希一致，源码/runner blob 与 `ef6b550` 无差异，候选特有行为、XML 和时间线构成高置信同源证据。日志未内嵌 Git SHA、MySQL 补丁版本和运行后零清单，按证据限制记录；门禁 A 示例 URL 遗漏冷认证缓存所需 TLS/RSA 参数形成的 1 个非阻断 Low 已在 PASS 后由示例、runner fail-fast、手册与纯 stub CI 契约闭环，生产 Compose 不受影响。
22. Phase 47 第一轮候选代码冻结为 `aa6f81c`，提交材料为 `docs/reviews/phase-47-remediation-submission-2026-07-25.md`；独立重核确认非目标读取错误均在整桶写前失败关闭，但 MinIO 8.5.12 的真实 no-config 返回值为 null，候选将其拒绝，故以 1 Medium / 1 Low 退回。离线 JAR/字节码证据归档在 `docs/reviews/evidence/phase47-remediation-rereview-2026-07-26/`。
23. Phase 47 第二轮独立重核 **PASS**：冻结 `aa6f81c..3bddf6c` 的两个生产/测试文件，源码与提交材料 `db89d6e` 同源；上一轮 1 Medium / 1 Low 全部关闭，正式报告当时新增 1 个非阻断日志测试参数数组 Low。该 Low 已由 `191a3ad` 后续闭环，模块 18/18、生命周期 15/15、9 模块 package 与最终独立只读复核均通过；正式报告和离线证据分别为 `docs/reviews/phase-47-second-remediation-rereview-2026-07-26.md` 与 `docs/reviews/evidence/phase47-second-remediation-rereview-2026-07-26/`。Phase 53 已放行进入整改，但 merge、push、部署、切流和项目发布仍未获 GO。
24. Phase 53 整改候选 `b9abc6c` 的代码/行为闭环已由正式独立报告确认：四个对象候选使用三个不同 classpath 资源，manifest、有界读取、内容版本 key、污染不覆盖、可信视频探测、生产策略复用和 SQL 单事务引用切换成立；用户动态门禁 10/10 的 tracked 快照/截图与当前机器原始日志相互一致。独立门禁为聚焦 31/31、全量离线 195/195、9 模块 package、前端 type-check/build、fat JAR 资源与 diff check 全绿。
25. Phase 53 证据整改独立增量复核 **PASS**：冻结 `8d42dcc..34e4d51`，12/12 归档日志四路哈希同源且全部受跟踪，旧完整秘密值已移除；独立离线 `clean test` 195/195、报告 lint 与 diff check 均 PASS。3 个非阻断 Low 为 packaged MP4 自动探测、凭据派生片段/secret-lint 可复现性和本机路径证据卫生。`stat(MISSING) → put` 无 CAS、旧新节点禁止混部和 Phase 7 有效预签名链接合同冲突留既有边界/最终全量审计。该 PASS 仅放行 Phase 44 整改；merge、push、部署、切流和项目发布仍未获 GO。
26. Phase 44 退回整改独立增量复核 **CHANGES_REQUESTED（4 Medium / 3 Low）**：冻结 `f9ccda3..f9aec54`。PG-M3 真批量关闭；PG-M4 因本地 publish 窗口、Redis 非原子/故障耦合与 namespace 碰撞未关闭，另有 MySQL 池化 session 污染 Medium。正式报告与独立证据分别为 `docs/reviews/phase-44-remediation-rereview-2026-07-26.md`、`docs/reviews/evidence/phase44-remediation-rereview-2026-07-26/`。仅运行本地只读检查与自然退出的离线 Maven；没有 cyber 类动作。
27. Phase 44 第二轮整改独立增量复核 **CHANGES_REQUESTED（2 Medium / 3 Low）**：冻结 `f9aec54..25f8b1c`。上一轮 4 Medium 的具体缺口与 2 Low 已关闭；新阻断为 Redis pending 无 owner/refcount 且 60 秒租约无事务上界、MySQL `_ai_ci` identity 与大小写敏感缓存键不一致。上一轮 `@Param` Low 仍未关闭，另有 registration 分支空转测试和锁 contender 无 started 探针两个 Low。正式报告与证据分别为 `docs/reviews/phase-44-second-remediation-rereview-2026-07-26.md`、`docs/reviews/evidence/phase44-second-remediation-rereview-2026-07-26/`。仅运行本地只读检查与自然退出的离线 Maven；没有 cyber 类动作。
28. Phase 44 第三轮整改候选 `8ff544d` 与独立重核：Redis pending 改为 Redis TIME 驱动的逐 owner ZSET 租约，后台续租且 `beforeCommit` 丢 owner 即中止事务；typeCode 统一 canonical identity，payload 升 schema v2；`@Param`、registration 与锁排队 Low 闭环。用户修正 reactor 命令后真实 MySQL/Redis IT **16/16**，独立离线 `platform-system` 44/44、9 模块 package、前端 type-check/build、diff check PASS。但正式重核仍为 **CHANGES_REQUESTED（1 Medium / 4 Low）**：READ 在 writers 丢失但 pending 尚存时过早恢复普通版本，可让同事务未提交值短暂进入共享 Redis；下一步只做第四轮最小整改。
29. Phase 44 第四轮整改候选 `228a355`（候选时快照）：残留 `P:` fail-closed 到自然到期，固定 stripe 本地 guard 线性化 active writer/Redis PUT 并覆盖 Redis 状态整体丢失，字典 DML 在同步注册前不落库；owner-loss/P-TTL 反例、精确 16/16 门禁、Compose/env、禁混部 runbook 与棕地只读 preflight 已落地。整改者当时只执行离线 53/53、Boot test-compile、9 模块 package、前端与脚本语法门禁；真实 MySQL/Redis/棕地/Docker 命令全部明确留给用户，当时尚无独立 PASS。
30. Phase 44 第四轮独立增量复核 **PASS（0 Critical / 0 High / 0 Medium / 1 Low）**：冻结 `69f7462..228a355` 并核对材料 HEAD `9c3da0b`；用户专用栈精确 16/16、棕地只读 preflight 与 Compose 双向展开证据成立，独立离线 53/53、后端 package、前端与报告门禁全绿。第三轮 1 Medium / 4 Low 原失败条件关闭；新增 preflight 权威接线/target marker 1 Low 非阻断。Phase 44 只放行 Phase 0，不构成 merge、部署或稳定发布 GO。
31. Phase 0 / U-004 首轮整改独立增量复核 **CHANGES_REQUESTED（3 Medium / 4 Low）**：冻结 `dd04f21..715b5f1`；两端 lint、api-docs 修复、统一异常/审计/TTL 与 DataScope 分支测试主体成立，但 T-FILE-1 假闭环、CI 必需 suite 可静默消失、权威基线仍冲突。4 Low 为 prod 静态 doc UI、证据 provenance、10/10 锚点过度与 T-DS-1 漂移。正式报告/离线证据位于 `docs/reviews/phase-00-remediation-rereview-2026-07-27.md` 与 `docs/reviews/evidence/phase00-remediation-rereview-2026-07-27/`。本轮未运行 Docker、数据库、网络、服务或任何 cyber 类操作。
32. Phase 0 / U-004 第二轮整改候选：产品/测试/门禁点 `3bfe83d`。通用摘要秒传按 R10 完整退役并补同字节二次上传真反例；exact gate 固定 6 suite / 25 testcase、候选 SHA、clean source、完整日志/XML/manifest/哈希与无秘密 target marker；权威文档、prod docs 404、24 项参数矩阵与 Mapper/DataScope 组合链同步闭环。离线 gate 21/21、Surefire 266/266、9 模块 package、前端 lint/type-check/build 与两路内部只读对抗检查全绿；这些不是正式独立复核。真实依赖 exact gate 仍须用户执行，正式状态继续 CHANGES_REQUESTED。
33. Phase 0 / U-004 第二轮独立增量复核 **CHANGES_REQUESTED（2 Medium / 3 Low）**：冻结 `8e3217d..c426480`，产品/测试/门禁点 `3bfe83d`。首轮 3 Medium 原失败条件及 prod docs/DataScope Low 关闭；新 Medium 为 gate 不复核结束 HEAD、target marker 不绑定实际 datasource/Redis/MinIO。Low 为 verifier 来源/Windows 路径边界、应用上下文 `[x]` 过早、参数矩阵 24/32。独立离线 gate 21/21、聚焦 18/18、Surefire 266/266、9 模块 package、前端和 diff check PASS；真实依赖 exact 6/25 未执行。报告/证据为 `docs/reviews/phase-00-second-remediation-rereview-2026-07-27.md` 与 `docs/reviews/evidence/phase00-second-remediation-rereview-2026-07-27/`。
34. Phase 0 / U-004 第三轮整改候选 `cc5786c`：candidate Git-object 快照在 preflight/formal 两侧复核并重物化，start/after-preflight/end HEAD 全绑定；target marker 与实际连接配置、MySQL UUID、Redis run_id、MinIO 身份对象/deployment ID 及 0/0/1/0 fresh 状态绑定；Windows/strict manifest、candidate spec blob、应用上下文 `[~]` 与参数 32/32 同轮收口。离线 gate 60/60、Surefire 274/274、target guard 8/8、9 模块 clean package、前端与 diff check PASS；candidate-spec 最终加固的独立只读增量检查无 finding，但不构成正式 PASS。动态 exact 7/33、Compose/browser 与隔离栈销毁均明确交由用户执行。

## 6. 已知坑与规避（别重复踩）
- Lombok `optional` 不向子模块传递 → 已在父 POM 解决。
- `@Configuration` 的 `@PostConstruct` **勿调 `@Bean` 方法**（CGLIB 循环依赖）→ MinIO 桶初始化已拆到 `MinioBucketInitializer`，并复用受管的唯一 `MinioClient`。
- Write 工具不一定建父目录 → 新源码包先 `mkdir -p`。
- Bash 执行含空格路径（JAVA_HOME）务必加引号。
- `target/`、`node_modules/`、`dist/`、`.env` 已被 `.gitignore` 排除；`.gitattributes` 统一 LF（CRLF 警告已消除）。
- **坑④（后端启动看似卡住）**：本机 `Start-Process` / 工具层经常显示 `aborted`，但 `java` 进程其实已经在后台成功启动。**不要重复盲启**；每次启动后先自检：
  - `Get-Process java`
  - `netstat -ano | Select-String ':8080'`
  - `Get-Content -Tail 100 backend.out.log`
- **坑⑤（2分钟无反馈必须自检）**：如果后端启动或接口验证超过 2 分钟没有反馈，必须立刻检查 `java` 进程、`8080` 端口、`backend.out.log/backend.err.log`，不要继续空等。
- **坑⑥（Codex/exec 常驻服务管道卡死）**：禁止直接执行 `java -jar`、`npm run dev`、`vite dev` 这类常驻服务。它们会继承 headless exec 的 stdout/stderr 管道，服务不退出则 EOF 永不到，表现为 Codex 一直 working 且不再发请求。也不要让 Codex/exec 调用包装启动脚本启动常驻服务；`scripts/dev-serve.sh` / `scripts/dev-serve.ps1` 只供外部人工终端、watchdog 或 Claude 复核环境使用。Codex 本轮只能跑会自然退出的一次性命令；若遗留服务导致卡住，可在外部运行 `~/Desktop/codex-watchdog.sh` 兜底。

## 7. 待确认事项（不阻塞开发，已设默认值并参数化）
见 `docs/待确认事项确认单.md`（20 项，**已于 2026-06-14 书面确认**）。原结论中**姓名校验改 `loose` 放宽**（#15，`V6` 落地），**证书序列作用域确认 `SCHOOL_YEAR_SEGMENT`**（按学段，与示例一致）；#17/#18 后由 WS-2 安全整改收敛为默认不开学生账号、禁止 PII 派生口令（`false/random`，`V27` 落地）。待学校后续提供（不阻塞）：完整中职专业课库、免考依据/可免科目清单（#12/#13，模板导入）；性能指标 #19 仍待提供。

## 8. 验收基线 与 阶段复核闸门
- **每个 Phase 完工后 codex 不自行置完成**：置「待复核」，由 **Claude 按 `docs/REVIEW-GATE.md` 复核**通过才算完成（产出 `docs/reviews/phase-NN-review.md`，PASS 才放行下一阶段）。
- 每阶段对照 `docs/phase-NN` 验收清单 + `docs/README.md` §4 的 **AT-01~AT-14 追溯矩阵**；状态在 `PROGRESS.md` 的「AT 跟踪」表登记；Phase 14 整体复验。
- 全局 DoD 见 `AGENTS.md` §12（提交前逐条勾）。
