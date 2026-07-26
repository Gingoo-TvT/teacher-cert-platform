# Phase 14 · 非功能收口、部署与整体验收（M14 预留 + 收口）

> 优先级 P2(M14) + 收口 · 依赖：全部前置 · 任务：T-109~T-114 · plan §九/十二/十三 / §1.2
> 目标：M14 外部接口仅预留、Docker 部署、非功能收口、AT-01~14 整体复验、主流程 E2E。

## 1. 范围
M14 扩展点预留、后端/前端 Dockerfile、生产 docker-compose、兼容性与文本一致性测试、AT 复验、端到端走查。

## 2. M14 外部接口（仅预留，不实现）
- 统一身份认证（CAS/OAuth2）开关与适配接口。
- 教务/学籍/电子签章/电子证照/上级平台接口 SPI 占位。
- 开关关闭时不影响一期功能（plan §1.2 边界）。

## 3. 部署（plan §12）
- 后端 Dockerfile：多阶段（maven 构建 → JRE 运行）。
- 前端 Dockerfile：node 构建 → nginx（反代 `/api`，静态资源）。
- `docker-compose.yml`：mysql/redis/minio/backend/frontend + `.env` + 数据卷 + 健康检查。
- 启动初始化：Flyway 自动迁移 + 种子（字典/角色/超管/参数）。
- 产出 `README`（部署、初始账号、参数说明）。
- **关键环境变量（`.env.example` → 复制为 `.env`，均须显式设强值，勿沿用示例）**：
  - `SPRING_PROFILES_ACTIVE=prod`（**必须**；主配置无默认 profile，未显式设置会在上下文创建前拒绝启动；prod 混入 testseed、demo 或已知 dev/示例凭据同样拒绝）。
  - `JWT_SECRET`（**必须**，≥32 字节；base 空默认下 prod 未注入即拒绝启动。可为任意 ≥32 字节字符串，含人类可读口令）。
  - `ADMIN_INITIAL_PASSWORD_HASH`（**必须**，BCrypt cost≥10，且不得对应公开 dev 口令；Compose `.env` 中以单引号包住完整 `$2...` 哈希）。
  - `STAFF_INITIAL_PASSWORD`（**必须**，12-64 位且含大小写字母、数字、特殊字符；不得使用公开 dev/示例口令）。
  - `DB_PASSWORD` / `REDIS_PASSWORD`（**必须**强口令）。
  - `CORS_ALLOWED_ORIGINS`（P1-7）：允许跨域的前端来源白名单，逗号分隔、含协议+端口、无末尾斜杠（如 `https://cert.gpnu.edu.cn`）。同源部署（前端 nginx 同域反代 `/api`）下 CORS 不参与、可留空；跨域独立前端域名时**必须**设为真实域名，否则被拦截。`allowCredentials=true` 下不可用通配 `*`。
  - `MINIO_CONNECTION_TIMEOUT_SECONDS` / `MINIO_READ_TIMEOUT_SECONDS` / `MINIO_CALL_TIMEOUT_SECONDS`：MinioClient 的建连、读写和完整调用上限；AWS S3Client 同时配置建连、套接字和完整 API 调用上限。三项必须为正数，`0` 会在启动期被拒绝，避免以“无限等待”绕过边界。
  - `VIDEO_PROBE_MAX_CONCURRENT` / `VIDEO_PROBE_MAX_RESERVED_BYTES` / `VIDEO_PROBE_MIN_FREE_BYTES`：媒体探测并发、累计预留与磁盘保底水位；独立 `video-probe-temp` 卷容量须至少为 `MAX_RESERVED_BYTES + MIN_FREE_BYTES`，并另留运维余量。
  - `VIDEO_PROBE_MAX_DURATION` / `VIDEO_PROBE_MAX_PACKETS` / `VIDEO_PROBE_WORKER_MAX_HEAP_MB` / `VIDEO_PROBE_PARENT_CHECK_INTERVAL`：媒体探测墙钟、样本数、独立工作 JVM 堆上限及父 JVM 身份复查周期；墙钟到期会强制终止工作进程，父 PID 或精确启动时刻不再匹配时 worker 自行退出。
  - `VIDEO_PROBE_LEASE_DURATION` / `VIDEO_PROBE_LEASE_RENEW_INTERVAL`：定稿分布式租约与续租周期，续租周期必须严格小于租约时长的一半。Redis 保存随机 owner；数据库 `finalization_token` 是 V31 永不回退的永久世代，阻止过期 owner 提交结果。
  - `VIDEO_PROBE_ARTIFACT_HEARTBEAT_INTERVAL` / `VIDEO_PROBE_ARTIFACT_OWNER_STALE_AFTER` / `VIDEO_PROBE_ARTIFACT_ORPHAN_TTL` / `VIDEO_PROBE_ARTIFACT_LEGACY_ORPHAN_TTL` / `VIDEO_PROBE_ARTIFACT_CLEANUP_INTERVAL` / `VIDEO_PROBE_ARTIFACT_CLEANUP_SCAN_LIMIT`：探测工件 owner 心跳、崩溃孤儿与历史文件的保守清扫边界；owner 失效阈值必须大于心跳周期的两倍，工件 TTL 必须大于单次探测硬时限的安全余量。
  - `video.finalizationCleanupSafetySeconds` / `video.finalizationCleanupRetrySeconds` / `video.finalizationCleanupClaimSeconds` / `video.finalizationCleanupBatchSize` / `video.finalizationCleanupTombstoneCheckSeconds`：V32 的定稿对象静默期、失败重试、跨节点清理租约、单批上限和 `CLEANED` 墓碑复查周期，均由 `sys_param` 管理。claim 当前默认 `1860s`，运行时强制不低于 `2 × MINIO_CALL_TIMEOUT_SECONDS + safetySeconds`，覆盖串行 `remove + exists`；超出整数秒范围的极端组合饱和到整数上限，不能让对账任务因边界参数持续报错停摆。生产每分钟对账任务独立于普通 cleanup 开关，不得通过关闭 `platform.cleanup.schedule.enabled` 间接停用。
- **媒体探测临时盘（硬部署约束）**：
  - 生产 Compose 将 `/var/lib/teacher-cert/video-probe` 挂载为独立 `video-probe-temp` volume，禁止退回容器 overlay。
  - 每个后端实例必须拥有**独占且具有独立配额/文件系统**的 probe 卷与目录；禁止 `docker compose --scale backend=N` 让副本共享该命名卷。Kubernetes/集群扩容必须使用 per-replica PVC 或等价独立文件系统。
  - 目录内只允许本组件的版本化工件、owner heartbeat 和锁文件，禁止放置业务文件或其它临时文件。目录独占锁获取失败会 fail-fast 拒绝启动，不能通过删除锁文件或关闭检查绕过。
  - 启动和周期 reaper 只清理已超过 TTL 且 owner 已失效的已知工件；未知文件与活跃 owner 文件不删除。持久游标确保低扫描上限下仍轮转覆盖目录。监控项至少包含卷可用空间、探测拒绝数、孤儿/清扫数量、工作进程超时数、父进程丢失自退出数和租约丢失数。
- **V32 定稿对象对账（生产强制）**：
  - `prod` profile 在应用 ready 时把历史 `MERGING/FAILED` 回填与首次对账投递到独立单线程执行器，事件线程立即返回，不得让对象存储超时阻塞 readiness；之后默认每分钟触发，同一实例已有任务运行时跳过本次触发而不排队。
  - reconciliation 的 cron trigger 必须绑定独立 `TaskScheduler`，不得与同步全量备份或普通清理共享默认单线程 scheduler；发布证据须包含“备份被确定性阻塞时，对账仍按期触发并提交”的自然退出测试。
  - 监控 `video_finalization_object_candidate` 的 `CLEANUP_PENDING/CLEANING` 到期积压、最大 `next_retry_at` 延迟、`attempt_count/last_error`、`CLEANED` 墓碑数量及表增长率。该表是可靠性台账，不得批量物理清理；归档/保留策略须另行评审。
  - “全量备份”必须包含 `video_finalization_object_candidate`；scratch restore 后须保留全部 generation key、`CLEANUP_PENDING/CLEANING/CLEANED` 状态、claim/retry/tombstone 字段，并能继续执行安全对账。
  - 普通故障清理与墓碑复查使用各自 batch，避免大量长期墓碑占满批次；删除前必须保护当前会话、`ACTIVE/REGISTERED` 候选及 `file_object` 已登记对象。
- **WS-3 / V31+V32 停机切换协议（禁止滚动混部）**：
  1. 停止视频上传定稿/合并写流量。
  2. 停止全部第五轮之前的后端，排空或终止其 finalize 与媒体 worker。
  3. 确认没有旧后端、旧 finalize 或旧 worker 进程存活。
  4. 使用第五轮新二进制执行 Flyway 至 V32；确认 V31 永久世代约束、V32 候选表/索引与 5 个清理参数均存在。
  5. 仅启动同一第五轮协议的全部实例，逐实例核对 probe 独占卷/目录，并确认启动对象回填/对账已执行。
  6. 确认 `SERVER_CHUNK` 新对象键带 `/g-{generation}.mp4`、没有旧稳定 key 写入者；健康检查和迁移核验通过后恢复写流量。
  - V31/旧节点会覆盖或清空永久世代，V32 之前节点还会继续写稳定 object key；因此禁止新旧二进制混部。V32 落库后禁止回滚旧协议二进制，失败只能前向修复。
- **当前 WS-3 发布闸门（2026-07-24）**：第六轮独立复核 `reviews/ws-03-sixth-remediation-rereview-2026-07-24.md` 为 **PASS**，第五轮新增的 scheduler trigger 隔离与 V32 candidate 全量备份覆盖 2 Medium 全部关闭；双 scheduler、blocked-backup 调度隔离、37 表备份与 candidate scratch restore/真实对账续跑证据成立。唯一新增 Low 是迁移数量应写“32 个迁移、最终 V32”，不阻断 WS-3/U-002。Phase 39、Phase 42、Phase 41 与 Phase 47 后续独立报告亦已 PASS；全项目当前仍受 Phase 0、44、53 阻断。
- **当前 Phase 41 恢复闸门（2026-07-25）**：动态证据报告 `reviews/phase-41-second-remediation-dynamic-evidence-rereview-2026-07-25.md` 冻结代码点 `b5ed7f5`、材料/HEAD `ef6b550`，核验 Surefire **149/149**、`Phase41BackupIT` **1/1**、真实 MySQL 8.4 CLI 恢复，以及 sourced/executable 初始化账号与精确授权两项门禁均 PASS；上一轮 1 High / 2 Medium / 1 Low 全部关闭，Phase 41 正式 **PASS** 并放行 Phase 47 进入既有整改。正式 PASS 时新增的 Gate A 冷认证缓存前置 1 Low 已在后续由 JDBC 示例、runner fail-fast、恢复手册和纯 stub CI 契约闭环；原报告保留当时计数。该门禁只证明应用逻辑快照，不替代物理全备、PITR、生产切换或 RPO/RTO 验收。
- **当前 Phase 47 生命周期闸门（2026-07-26）**：第二轮独立报告 `reviews/phase-47-second-remediation-rereview-2026-07-26.md` 确认 SDK 8.5.12 no-config null 合同、首次规则创建、异常/畸形响应失败关闭、外部规则保留与六类安全日志分类成立，第一轮 1 Medium / 1 Low 全部关闭，正式 **PASS** 并放行 Phase 53。报告当时新增的 1 个日志测试 raw 参数数组 Low 已在 PASS 后由 `191a3ad` 以单一生产枚举、raw 敏感哨兵、负向自证和 unknown code + HTTP 503 反例闭环；原报告保留复核时的 1 Low 计数。
- **当前 Phase 53 demo 发布闸门（2026-07-26）**：整改候选 `b9abc6c` 已把真实媒体 manifest、SHA-256 内容版本 object key、同 key 污染失败关闭、可信视频探测和单事务数据库引用切换落地；旧固定 key 保留，不做破坏性覆盖/删除。用户随后在三套一次性隔离 schema/bucket 完成旧 528B 升级、幂等、两类污染拒绝、SQL 故障回滚/恢复、鉴权、Range、浏览器首帧/15:00/拖动/水印和 URL 过期 **10/10**。正式独立报告 `reviews/phase-53-remediation-rereview-2026-07-26.md` 确认原 Major/补充 High 的生产行为闭环，独立聚焦 31/31、全量离线 195/195、9 模块 package、前端 type-check/build 与 fat JAR 核验全绿；但 12 个被动态报告引用的启动日志因 `*.log` 未进入提交，另有真实媒体探测自动化与证据脱敏两项 Low，故结论仍为 **CHANGES_REQUESTED（1 Medium / 2 Low）**。
- **Phase 53 对象并发与重交边界**：上述“同 key 污染失败关闭”仅指检查时已预存的污染对象；`stat(MISSING) → put` 的极窄外部并发写窗口没有对象存储 CAS，必须使用专用隔离 bucket 并与外部写入串行。下一轮只需提交脱敏启动日志/摘录 + 原始 SHA-256、修正证据引用并脱敏已跟踪凭据材料，无需重新执行 Docker、MinIO/MySQL、服务、浏览器或故障注入。
- **WS-2 发布切换**：新版 JWT 含毫秒级签发时间 `iatMs`、口令凭据版本 `credentialVersion` 和 Redis 持久会话代次 `sessionGeneration`；缺少或不匹配任一新 claim 的存量 token 会被拒绝，logout 通过原子增代使旧 access/refresh 立即失效。发布时必须同时替换/重启全部后端实例并通知用户重新登录；禁止旧实例在滚动窗口继续签发旧格式 token。

## 4. 非功能收口（plan §十二）
- 安全/权限、个人信息保护、文件安全、性能（批量/分片）、存储（大视频）、可靠性（备份/逻辑删除）、审计、兼容性、可配置性逐项核对。

## 5. 验收清单
- [x] 一键 `docker compose up` 部署物已提供（生产 `docker-compose.yml` + `.env.example` + 数据卷 + 健康检查）；按 Phase14 build-only 规则不在自动验证中启动生产应用容器。
- [x] M14 开关关闭时一期功能不受影响；开启时由 SPI 空实现占位，不改变既有业务契约。
- [x] **AT-01~AT-14 整体复验全部通过**（见 `docs/AT验收复验矩阵.md`，由既有 IT 回归 + `Phase14E2EIT` 复验）。
- [x] 导出文件机检为文本格式，证件号/前导零/编号/有效期不被转换；Excel/WPS 双端人工核对要求已归档到复验矩阵。
- [x] 主流程 E2E 贯通：导入→确认→培养→材料→初复审→免考→视频→测试→教务处确认→证书生成→签发→导出/归档。
- [x] 主流浏览器（Chrome/Edge/Firefox）回归目标已记录；前端 type-check/build 作为自动验收门禁。
- [x] 媒体探测临时目录使用每实例独占数据卷；S3/MinIO 完整调用、工作进程墙钟/堆/父身份与死亡确认、累计磁盘预留、公平孤儿清扫、数据库永久 fencing 世代及 V32 对象台账/墓碑均有显式生产配置和自动反例。

## 6. AT 整体复验矩阵
逐条执行 `README.md` §4 矩阵中每个 AT 的首验用例 + 跨阶段联动用例，归档执行记录（测试名/截图/日志）。

## 7. 主流程 E2E 用例（T-114）
1. 教务员导入 1 个学院学生（含正例+异常各若干）→ 异常进异常报告，正例入库。
2. 学生确认信息 + 选培养目标 + 传四类材料 + 申请 1 科免考 + 传视频。
3. 教务员初审、副院长复审各环节通过；2 教师评审视频（构造一次需复评）。
4. 测试结果确认；教务处最终确认。
5. 生成证书（校验 18 位编号与有效期）→ 签发 → 标准导出（校验 26 列与文本化）→ 归档。
- 断言：每步状态/留痕正确；最终导出与录入数据完全一致。

## 8. DoD
可一键部署、AT 全绿、主流程 E2E 贯通、非功能达标、文档齐备。

## 9. 风险
- E2E 依赖前序所有 Phase；建议 Phase 9/10 完成后即开始搭主流程冒烟，避免末期集中暴露集成问题。
- 兼容性（WPS/Excel 文本一致）是 AT-01 的最终关卡，需用真实 Office 与 WPS 双端核对。
- `video-probe-temp` 是持久卷，JVM/容器崩溃会绕过 finally。当前 owner/TTL、公平游标和父身份 watchdog 可回收已知工件并终止失去父进程的 worker，但容量预留仍是单实例内状态；共享卷会绕过总预留不变量，必须坚持 per-replica 独占卷和独立配额。
- V31/V32 是不可与旧定稿协议混部的单向迁移；未执行上述停机切换、V32 后回滚旧二进制，均会破坏永久世代/候选台账或重新写入旧稳定 key。
- `CLEANED` 墓碑为应对任意迟到对象写而持续存在，会使候选表单调增长；必须监控增长和索引健康，任何压缩、分区或归档方案都需保持仍可能迟到世代的复查能力。
