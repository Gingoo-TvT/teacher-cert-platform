# Phase 14 · 非功能收口、部署与整体验收（M14 预留 + 收口）

> 优先级 P2(M14) + 收口 · 依赖：全部前置 · 任务：T-109~T-114 · plan §九/十二/十三 / §1.2
> 目标：M14 外部接口仅预留、Docker 部署、非功能收口、AT-01~14 整体复验、主流程 E2E。
>
> **活动状态以 `docs/CURRENT-EXECUTION-PLAN.md` 为唯一入口。** Phase 0 已独立 PASS；最终全量审计
> 当前为 CHANGES_REQUESTED / NO-GO。本文件中的历史阶段完成项不等于稳定发布授权。

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
- WS-7 覆盖层：后端/前端 runtime 分别固定为非 root `10001:10001` / `101:101`；前端容器内部使用
  8080/8443，宿主入口仍映射 80/HTTPS 端口。四个 Dockerfile `FROM` 与生产基础服务镜像按 digest 固定，
  GitHub Actions 按完整 commit SHA 固定；应用镜像必须使用候选源 SHA 派生标签，更新引用须显式审阅 diff。
- CI 顺序固定为 runner 合同、后端和前端质量门禁全绿后再构建双镜像、核验默认 UID 非 0，并配置生成双镜像
  SPDX JSON、镜像身份记录与校验和。该清单不冒充漏洞扫描、签名、provenance、镜像发布或项目 GO。
- 启动初始化：Flyway 自动迁移 + 种子（字典/角色/超管/参数）。
- 产出 `README`（部署、初始账号、参数说明）。
- **关键环境变量（`.env.example` → 复制为 `.env`，均须显式设强值，勿沿用示例）**：
  - `SPRING_PROFILES_ACTIVE=prod`（**必须**；主配置无默认 profile，未显式设置会在上下文创建前拒绝启动；prod 混入 testseed、demo 或已知 dev/示例凭据同样拒绝）。
  - `JWT_SECRET`（**必须**，≥32 字节；base 空默认下 prod 未注入即拒绝启动。可为任意 ≥32 字节字符串，含人类可读口令）。
  - `JWT_ACCESS_TTL_SECONDS` / `JWT_REFRESH_TTL_SECONDS`（WS-5/T2/T3）：默认 `900` / `604800` 秒。access token 仅驻前端内存，默认 15 分钟；refresh token 只经 HttpOnly Cookie 传递，默认 7 天。
  - `IDCARD_ENCRYPTION_KEY` / `IDCARD_HMAC_PEPPER`（WS-8）：前者必须是 Base64 编码的随机 32 字节 AES 密钥，后者至少 32 个 UTF-8 字节；两者必须相互独立且不得复用 `JWT_SECRET`。缺失、示例或非法值必须在迁移前失败关闭。上线后两项值必须保持稳定并在数据库备份之外受控备份；本阶段不提供在线轮换。
  - `ADMIN_INITIAL_PASSWORD_HASH`（**必须**，BCrypt cost≥10，且不得对应公开 dev 口令；Compose `.env` 中以单引号包住完整 `$2...` 哈希）。
  - `STAFF_INITIAL_PASSWORD`（**必须**，12-64 位且含大小写字母、数字、特殊字符；不得使用公开 dev/示例口令）。
  - `DB_PASSWORD` / `REDIS_PASSWORD`（**必须**强口令）。
  - `MYSQL_CPU_V1_IMAGE` / `REDIS_CPU_V1_IMAGE` / `MINIO_CPU_V1_IMAGE`（第二次离线部署）：必须是
    `docker load` 后可直接解析且已绑定 image ID 的本地 tag；CPU-v1 覆盖层不得让 Redis 回退继承根 Compose 的
    `tag@digest` 引用。升级必须叠加 `deploy/compose.existing-volumes.yml`，按预检所得精确名称复用首版四卷。
  - `TLS_SERVER_NAME` / `TLS_CERTIFICATE_DIR` / `TLS_CERTIFICATE_GID` / `FRONTEND_HTTPS_PORT`（WS-5/T1、WS-7）：正式前端域名、宿主机证书目录、宿主专用证书读取组的数字 GID 与 HTTPS 发布端口；同一端口进入 HTTP 301 的目标地址，非 443 部署不会丢失。部署前必须核对该宿主 GID 对应的组名和成员，且不得把容器 nginx 主组 `101:101` 或含无关成员的宿主组直接作为证书读取组。证书目录以只读方式挂载到前端容器，必须包含 UID 101 可经补充组读取的 `fullchain.pem` / `privkey.pem` 实体文件（或完整位于挂载根内的可解析链接）；私钥不得入库或设为全局可读。续期、密钥匹配、`nginx -t`、reload、外部证书核验、30 天提醒和回退步骤见 `README.md`。
  - `MINIO_PUBLIC_ENDPOINT`（WS-3/WS-5 联动）：HTTPS 前端必须配置浏览器可达的单一 `https` origin，不能使用通配、Compose 内部 DNS 或明文 `http`，其证书与反代由独立文件服务器侧提供；同一值同时传给前端 CSP，仅进入 `connect-src`，供预签名直传使用。生产 Compose 的 MinIO API/console 只绑定宿主回环，backend 不发布宿主端口；公网业务入口仅为前端 80/HTTPS 端口。
  - `DICT_CACHE_WRITER_LEASE` / `DICT_CACHE_WRITER_RENEW_INTERVAL`（Phase 44）：字典缓存跨节点写窗口的逐 owner 崩溃回收租约与续租周期，默认 `2m` / `20s`。两者必须为正，且 `RENEW_INTERVAL <= LEASE / 3`；所有后端实例必须使用同一组值，非法组合在启动期失败关闭。
  - `CORS_ALLOWED_ORIGINS`（P1-7）：允许跨域的前端来源白名单，逗号分隔、含协议+端口、无末尾斜杠（如 `https://cert.gpnu.edu.cn`）。同源部署（前端 nginx 同域反代 `/api`）下 CORS 不参与、可留空；跨域独立前端域名时**必须**设为真实域名，否则被拦截。`allowCredentials=true` 下不可用通配 `*`。
  - `MINIO_CONNECTION_TIMEOUT_SECONDS` / `MINIO_READ_TIMEOUT_SECONDS` / `MINIO_CALL_TIMEOUT_SECONDS`：MinioClient 的建连、读写和完整调用上限；AWS S3Client 同时配置建连、套接字和完整 API 调用上限。三项必须为正数，`0` 会在启动期被拒绝，避免以“无限等待”绕过边界。
  - `VIDEO_PROBE_MAX_CONCURRENT` / `VIDEO_PROBE_MAX_RESERVED_BYTES` / `VIDEO_PROBE_MIN_FREE_BYTES`：媒体探测并发、累计预留与磁盘保底水位；独立 `video-probe-temp` 卷容量须至少为 `MAX_RESERVED_BYTES + MIN_FREE_BYTES`，并另留运维余量。
  - `VIDEO_PROBE_MAX_DURATION` / `VIDEO_PROBE_MAX_PACKETS` / `VIDEO_PROBE_WORKER_MAX_HEAP_MB` / `VIDEO_PROBE_PARENT_CHECK_INTERVAL`：媒体探测墙钟、样本数、独立工作 JVM 堆上限及父 JVM 身份复查周期；墙钟到期会强制终止工作进程，父 PID 或精确启动时刻不再匹配时 worker 自行退出。
  - `VIDEO_PROBE_LEASE_DURATION` / `VIDEO_PROBE_LEASE_RENEW_INTERVAL`：定稿分布式租约与续租周期，续租周期必须严格小于租约时长的一半。Redis 保存随机 owner；数据库 `finalization_token` 是 V31 永不回退的永久世代，阻止过期 owner 提交结果。
  - `VIDEO_PROBE_ARTIFACT_HEARTBEAT_INTERVAL` / `VIDEO_PROBE_ARTIFACT_OWNER_STALE_AFTER` / `VIDEO_PROBE_ARTIFACT_ORPHAN_TTL` / `VIDEO_PROBE_ARTIFACT_LEGACY_ORPHAN_TTL` / `VIDEO_PROBE_ARTIFACT_CLEANUP_INTERVAL` / `VIDEO_PROBE_ARTIFACT_CLEANUP_SCAN_LIMIT`：探测工件 owner 心跳、崩溃孤儿与历史文件的保守清扫边界；owner 失效阈值必须大于心跳周期的两倍，工件 TTL 必须大于单次探测硬时限的安全余量。
  - `video.finalizationCleanupSafetySeconds` / `video.finalizationCleanupRetrySeconds` / `video.finalizationCleanupClaimSeconds` / `video.finalizationCleanupBatchSize` / `video.finalizationCleanupTombstoneCheckSeconds`：V32 的定稿对象静默期、失败重试、跨节点清理租约、单批上限和 `CLEANED` 墓碑复查周期，均由 `sys_param` 管理。claim 当前默认 `1860s`，运行时强制不低于 `2 × MINIO_CALL_TIMEOUT_SECONDS + safetySeconds`，覆盖串行 `remove + exists`；超出整数秒范围的极端组合饱和到整数上限，不能让对账任务因边界参数持续报错停摆。生产每分钟对账任务独立于普通 cleanup 开关，不得通过关闭 `platform.cleanup.schedule.enabled` 间接停用。

### 3.1 WS-8 / V33 身份证件号存储协议停机切换（禁止滚动混部）

`WS8_V33_RELEASE_ORDER: FREEZE_WRITES > STOP_ALL_OLD > BACKUP_V32 > MIGRATE_V33 > START_ALL_NEW > VERIFY > OPEN_TRAFFIC`

本合同仅用于已有 V32 数据库升级 V33。首次空库部署不需要模拟旧节点，但仍须满足密钥护栏和 V33 数据验收。
V33 删除旧明文生成列/唯一键并改由新 binary 写 AES-GCM 密文与 HMAC；旧 binary 不理解该协议，会重新写明文和
空 HMAC，因此本次升级必须是停机切换，不支持滚动发布或应用二进制单独回滚。

1. **FREEZE_WRITES**：进入获批维护窗，在网关/前端及所有外部入口冻结 Student、Certificate、Exchange 的新增、
   修改、导入、回滚与导出批次写入；排空已进入事务。外层 ingress 先进入固定维护状态（如有），再先于旧 backend
   停止首版 frontend，并从独立客户端证明公开入口不能转发业务；frontend/入口必须持续关闭到
   `OPEN_TRAFFIC`。记录 V32 旧镜像身份、V33 新镜像 digest、目标数据库身份、当前 Flyway 最高成功版本和受控
   AES key/HMAC pepper 版本引用，记录中不得出现 secret 值。
2. **STOP_ALL_OLD**：停止所有宿主上的全部旧 backend/worker，确认其进程、任务、连接均退出，并由数据库只读
   连接视图留存“无旧应用写连接”的证据。未证明旧写入者为零，不得继续；禁止仅摘流量但保留旧 worker。
3. **BACKUP_V32**：在旧节点全停且数据库最高成功版本仍为 V32 时，完成全库 schema+data 备份、必要的 MinIO
   对象快照与校验和复算；把数据库/对象恢复点和原 AES key/HMAC pepper 的受控版本引用绑定到同一变更单。
   未获得可恢复、可校验的 V33 前恢复点，不得开始迁移。
4. **MIGRATE_V33**：入口继续关闭，只启动一台未接收业务流量的 V33 新 backend，让该 Spring binary 依次执行
   Flyway V33 与 `AFTER_MIGRATE` 回填；只执行外部 Flyway CLI 的 DDL 不算完成。任何一步失败都保持维护状态。
5. **START_ALL_NEW**：迁移与回填成功后，仅用同一 V33 镜像 digest、同一 AES key/HMAC pepper 版本启动全部
   backend/worker；逐节点核对镜像身份、配置引用和健康状态，禁止任何旧 binary 回到负载均衡或连接数据库。
6. **VERIFY**：只读核对 Flyway V33 成功且旧 `student.idcard_key` 不存在；Student/Certificate 非空证件号均为
   `v1:` 密文，未删 Student 及 Certificate HMAC 均为 64 位小写 hex 且无冲突，已删 Student HMAC 为 NULL，
   Exchange 预览/范围/错误/快照无可读证件号。再抽样验证可解密、普通投影脱敏、授权明文/敏感导出留审计、
   同一证件号查重仍拒绝。
7. **OPEN_TRAFFIC**：全部新节点和上述验收均通过后，才最后启动新 frontend、恢复网关与外部写入口；记录放流时点并观察启动、
   迁移、重复键、解密失败和审计告警。

**回滚边界**：在 V33-capable 进程开始执行迁移之前，且确认数据库仍为 V32，可中止变更并恢复旧 fleet。一旦
V33-capable 进程开始执行迁移（即使 `AFTER_MIGRATE` 回填失败），严禁 V32/旧 binary 再连接该数据库，严禁
app-only rollback、手工降级或用 Flyway repair 冒充回滚；写入口保持冻结，只能以前向修复的 V33-compatible
binary 收敛。若必须回到 V32，只能在隔离替换环境完整恢复 V33 前数据库及必要对象快照、复算校验和并确认
Flyway 最高版本为 32 后，才可启动旧 fleet。V33 放流后已有新写时默认只允许前向修复；任何全量恢复及潜在数据
损失必须另行审批、对账，不属于自动回滚。

<!-- WS8_V33_RELEASE_CONTRACT_END -->

- **媒体探测临时盘（硬部署约束）**：
  - 生产 Compose 将 `/var/lib/teacher-cert/video-probe` 挂载为独立 `video-probe-temp` volume，禁止退回容器 overlay。
  - 从旧 root runtime 升级到 WS-7 非 root 镜像前，必须在维护窗停止全部旧 backend/媒体 worker，保留命名卷并
    一次性把目录及既有内容调整为 `10001:10001`，验证默认用户可写后再启动新镜像；禁止删卷或在线改属主。
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
- **Phase 44 字典缓存协议停机切换（禁止滚动混部）**：
  1. 在所有目标实例上固定同一 `DICT_CACHE_WRITER_LEASE` / `DICT_CACHE_WRITER_RENEW_INTERVAL`（默认 `2m` / `20s`，且续租周期不超过租约三分之一），先完成配置审查，不得让部分实例回退应用内默认值、部分实例使用覆盖值。
  2. 在网关或运维入口停止字典管理写请求（字典类型/字典项的新增、修改、删除），排空已进入的字典写事务；读流量可保持到旧节点停机。
  3. 在字典写入已冻结、旧节点尚未停止时，按 `docs/第二次部署发布手册.md` 由获授权操作者运行
     `scripts/preflight-second-release.sh`。包装器以一次性 `--rm` 客户端共享已 inspect MySQL 完整 container ID 的
     网络命名空间，不发布宿主 3306；授权清单中的 expected server UUID 必须与查询值精确相等。发布证据必须
     归档 Compose project、五容器镜像 ID、四卷名、新 Redis 本地 tag/image ID、查询前后不变的 MySQL
     container/image ID，以及 `DATABASE()`、脱敏 `CURRENT_USER()` 摘要、MySQL server UUID 和 Phase 44 PASS marker；任何目标
     marker 缺失或不匹配都立即中止发布。
  4. 停止**全部**旧后端节点并确认进程、任务与连接均已退出；从此刻起不得再有旧节点创建 `P:<uuid>` pending、旧 `{v,items}` 包络或大小写别名键。
  5. 在没有任何后端写入者的前提下，等待上一协议 pending 的 60 秒 TTL 到期，并核对 `dict:items-version:*` 不再存在 `P:*` 值。若因历史人工配置导致残留，只能在维护窗口内按已确认的具体 typeCode 定向清理对应 `dict:items:<typeCode>` payload 与 `dict:items-version:<typeCode>`；禁止 `FLUSHDB`/`FLUSHALL`、禁止删除无关 Redis 数据，也不得在新节点启动后人工删除 `dict:items-writers:*`。
  6. 使用同一 Phase 44 新 binary 一次性启动全部后端实例；在全部实例健康前不得恢复字典写流量，也不得把任何旧 binary 放回负载均衡。
  7. 逐实例核对镜像 digest/候选版本一致、启动日志没有字典租约配置校验错误，且 `/api/health` 通过；再用只读字典查询确认大小写别名返回相同数据、Redis 旧包络被 schema v2 失败关闭并重建为 canonical 小写 identity。
  8. 全部检查通过后才恢复字典管理写流量，并观察租约丢失、Redis 回源/回填失败和健康状态告警。
  9. Phase 44 新协议上线后，禁止回滚旧 binary 承接任何字典读写；故障时保持字典写入口冻结并以前向修复或同协议新构建替换。旧 binary 会重新产生大小写敏感键和旧包络，不能作为回滚路径。
- **当前 WS-3 发布闸门（2026-07-24）**：第六轮独立复核 `reviews/ws-03-sixth-remediation-rereview-2026-07-24.md` 为 **PASS**，第五轮新增的 scheduler trigger 隔离与 V32 candidate 全量备份覆盖 2 Medium 全部关闭；双 scheduler、blocked-backup 调度隔离、37 表备份与 candidate scratch restore/真实对账续跑证据成立。唯一新增 Low 是迁移数量应写“32 个迁移、最终 V32”，不阻断 WS-3/U-002。Phase 39、Phase 42、Phase 41、Phase 47、Phase 53、Phase 44 与 Phase 0 后续独立报告均已 PASS；最终全量审计已另行给出 CHANGES_REQUESTED / NO-GO，当前状态只在统一执行计划维护。
- **当前 Phase 41 恢复闸门（2026-07-25）**：动态证据报告 `reviews/phase-41-second-remediation-dynamic-evidence-rereview-2026-07-25.md` 冻结代码点 `b5ed7f5`、材料/HEAD `ef6b550`，核验 Surefire **149/149**、`Phase41BackupIT` **1/1**、真实 MySQL 8.4 CLI 恢复，以及 sourced/executable 初始化账号与精确授权两项门禁均 PASS；上一轮 1 High / 2 Medium / 1 Low 全部关闭，Phase 41 正式 **PASS** 并放行 Phase 47 进入既有整改。正式 PASS 时新增的 Gate A 冷认证缓存前置 1 Low 已在后续由 JDBC 示例、runner fail-fast、恢复手册和纯 stub CI 契约闭环；原报告保留当时计数。该门禁只证明应用逻辑快照，不替代物理全备、PITR、生产切换或 RPO/RTO 验收。
- **当前 Phase 47 生命周期闸门（2026-07-26）**：第二轮独立报告 `reviews/phase-47-second-remediation-rereview-2026-07-26.md` 确认 SDK 8.5.12 no-config null 合同、首次规则创建、异常/畸形响应失败关闭、外部规则保留与六类安全日志分类成立，第一轮 1 Medium / 1 Low 全部关闭，正式 **PASS** 并放行 Phase 53。报告当时新增的 1 个日志测试 raw 参数数组 Low 已在 PASS 后由 `191a3ad` 以单一生产枚举、raw 敏感哨兵、负向自证和 unknown code + HTTP 503 反例闭环；原报告保留复核时的 1 Low 计数。
- **当前 Phase 53 demo 发布闸门（2026-07-26）**：整改候选 `b9abc6c` 已把真实媒体 manifest、SHA-256 内容版本 object key、同 key 污染失败关闭、可信视频探测和单事务数据库引用切换落地；旧固定 key 保留，不做破坏性覆盖/删除。上一轮正式报告 `reviews/phase-53-remediation-rereview-2026-07-26.md` 已确认原 Major/补充 High 行为闭环，但因 12 个日志未提交以 1 Medium / 2 Low 退回。证据整改提交 `34e4d51` 经 `reviews/phase-53-evidence-remediation-rereview-2026-07-26.md` 独立复核确认 12/12 日志四路哈希同源、tracked-path 与 `-text -diff` 成立、旧完整秘密值移除，独立离线 195/195、报告 lint 与 diff check 全绿；正式结论 **PASS（0 Critical / 0 High / 0 Medium / 3 Low，均非阻断）**。
- **Phase 53 对象并发与发布边界**：上述“同 key 污染失败关闭”仅指检查时已预存的污染对象；`stat(MISSING) → put` 的极窄外部并发写窗口没有对象存储 CAS，必须使用专用隔离 bucket 并与外部写入串行。packaged MP4 自动探测、凭据派生片段/secret-lint 可复现性和日志本机路径清理 3 个 Low 留稳定发布前处理；Phase 53 PASS 当时只放行 Phase 44 整改，后续 Phase 44 已独立 PASS，但两者均不构成 merge、部署、切流或项目发布 GO。
- **当前 Phase 44 字典缓存闸门（2026-08-22）**：第四轮独立报告 `reviews/phase-44-fourth-remediation-rereview-2026-07-27.md` 为 **PASS（0 Critical / 0 High / 0 Medium / 1 Low）**。专用全新 MySQL/Redis 栈精确 16/16、棕地只读 identity preflight 与 Compose 默认/覆盖值双向展开成立，第三轮 owner-loss Medium 与原 4 Low 的失败条件关闭。原非阻断 Low 所要求的权威步骤与 target marker 已进入上方第 3 步及第二次部署只读包装器；是否正式关闭仍由最终发布候选的独立复核裁定。
- **当前第二次部署准备闸门（2026-08-23）**：R1 fingerprint `bb3a31dc...925f88` 的正式报告为
  `CHANGES_REQUESTED（0C/0H/4M/0L）`，R1 不得物化 carrier。R2 已按原失败条件补齐绑定已 inspect MySQL
  container ID + expected UUID、Redis 本地 tag/image ID、公开入口先关后开、首版原样 Compose/CPU/init、旧五镜像
  归档流程与 external 原卷覆盖层。R2 fingerprint `f190ddde...80e4` 的正式报告为
  `CHANGES_REQUESTED（0C/0H/2M/0L）`，R2 不得物化 carrier。R3 关闭 login-path finding，但 fingerprint
  `9383ccc2...f09c2e` 的正式结论仍为 `CHANGES_REQUESTED（0C/0H/1M/0L）`。R4 fingerprint
  `0ee662b7...355ed9` 已让 helper 同时探测正式根路径与同源 `/api/health`，并以“根 503+marker、API 200”反例
  失败关闭，正式取得 `INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/0L）`。该 PASS 只覆盖源码增量；clean-store、
  真实入口/Linux login-path、V32 迁移前首版回退、V32→V35 与同候选 Hosted 仍须独立动态门禁，不构成发布 GO。
- **WS-2 发布切换**：新版 JWT 含毫秒级签发时间 `iatMs`、口令凭据版本 `credentialVersion` 和 Redis 持久会话代次 `sessionGeneration`；缺少或不匹配任一新 claim 的存量 token 会被拒绝，logout 通过原子增代使旧 access/refresh 立即失效。发布时必须同时替换/重启全部后端实例并通知用户重新登录；禁止旧实例在滚动窗口继续签发旧格式 token。

## 4. 非功能收口（plan §十二）
- 安全/权限、个人信息保护、文件安全、性能（批量/分片）、存储（大视频）、可靠性（备份/逻辑删除）、审计、兼容性、可配置性逐项核对。

## 5. 验收清单
- [x] 一键 `docker compose up` 部署物已提供（生产 `docker-compose.yml` + `.env.example` + 数据卷 + 健康检查）；按 Phase14 build-only 规则不在自动验证中启动生产应用容器。
- [x] M14 开关关闭时一期功能不受影响；开启时由 SPI 空实现占位，不改变既有业务契约。
- [x] **AT-01~AT-14 整体复验全部通过**（见 `docs/AT验收复验矩阵.md`，由既有 IT 回归 + `Phase14E2EIT` 复验）。
- [ ] 导出文件机检为文本格式，证件号/前导零/编号/有效期不被转换；Excel/WPS 双端人工核对要求已归档到复验矩阵，但真实双端执行证据仍待用户/授权环境归档。
- [x] 主流程 E2E 贯通：导入→确认→培养→材料→初复审→免考→视频→测试→教务处确认→证书生成→签发→导出/归档。
- [ ] 主流浏览器（Chrome/Edge/Firefox）回归目标已记录；前端 type-check/build 已作为自动门禁，真实三浏览器行为与可访问性证据仍待用户/授权环境归档。
- [x] 媒体探测临时目录使用每实例独占数据卷；S3/MinIO 完整调用、工作进程墙钟/堆/父身份与死亡确认、累计磁盘预留、公平孤儿清扫、数据库永久 fencing 世代及 V32 对象台账/墓碑均有显式生产配置和自动反例。
- [x] WS-5/T1–T3 已完成独立范围化复核：原 fingerprint `c15d8ace...b124ce` 的 **CHANGES_REQUESTED（0 High / 2 Medium / 1 Low）** 保留为历史；整改 fingerprint `9e33d20f...a83a` 获 `INDEPENDENT_INCREMENTAL_PASS（0 open finding）`。T1 为 `DYNAMIC_CLOSED`，T2/T3 保持 `SCOPED_DYNAMIC_PASS`，WS-5 为 `INDEPENDENT_SCOPED_PASS`。
- [x] T1 三项最小整改及增量证据已通过：backend 无宿主端口，MinIO API/console 只绑定回环；301 的目标端口与 `FRONTEND_HTTPS_PORT` 一致；README 已记录证书域名/期限/密钥匹配、`nginx -t`、reload/recreate、线上 serial/fingerprint/notAfter 核验、30 天提醒和失败回退。
- [x] WS-6 前端自动化门禁已取得独立范围化 PASS：第二轮 fingerprint `f5d63378...8607` 为 0 open finding；lint、tests/config type-check、production build、fresh-cache Chromium 5/5、候选一致性与端口清理成立。该结论不冒充 hosted CI、真实后端或项目发布 GO。
- [x] WS-7 覆盖层已实现非 root 双 runtime、digest/SHA 固定、门禁后双镜像构建与 SBOM/校验 artifact 工作流；
  fingerprint `d5ea9863...9e24` 已取得 `INDEPENDENT_STAGE_PASS（0C/0H/0M/1L）`。Hosted run
  `31507732334` 的双 SPDX、镜像身份、校验和与 artifact 已独立核验，唯一 Low（临时 evidence remote）已后续
  清除。该结论只放行 WS-8，不替代生产部署、切流或项目级发布 GO。
- [x] WS-8 R5 fingerprint `c649a065...d601` 的 Hosted run `31808005960` 已整体 4/4 success；历史六-suite
  **46/46**、0 failure/error/skip，WS-7/V33 合同、最终双镜像/双 SPDX、身份与根/嵌套校验和均通过。正式报告只判
  R5 scoped 合同整改与 Hosted evidence PASS；WS-8 full stage 仍为
  **CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 1 Low）**。R6 已同批最小整改 V-13 规范化计数、含 NULL
  的完整 UPDATE 快照回滚和失败行统计；R6 独立功能增量报告确认三项全部关闭、0 open finding，真实依赖
  六-suite **49/49**、Phase 39 + Phase 10 **29/29**。用户按功能完整性放行 WS-9 开发；WS-9 七个目标职责已
  等价抽取。fingerprint `df1f1950...b2f45` 的正式增量复核确认 Surefire **400/400**、Phase 7/10/14/39
  **87/87**，原唯一 Medium `WS9-INT-M1` CLOSED，状态为 `INDEPENDENT_INCREMENTAL_PASS（0 open finding）`。
  WS-11 R2 已取得正式独立增量 PASS，当前进入 WS-12；Hosted/供应链只作附加发布证据，不改变下一条项目发布门槛。
- [x] WS-11 R2 正式独立增量报告绑定 fingerprint `85606aed...9161f`（报告 SHA-256
  `38c9896d...9d50a`），确认首轮三项 Medium 全部 CLOSED，裁定
  `INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/0L）`。Surefire **411/411**、Phase 14 + WS-11 Failsafe
  **18/18**；该结论不等于项目发布 GO。
- [x] WS-12 整改 fingerprint `1c8b8821...0ae7b` 已取得正式
  `INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/0L）`；上一轮唯一生产/预算 gzip 等级分叉 Medium CLOSED。
  gzip 闭包为入口 **119.6 KiB**、登录 **148.0 KiB**、普通管理 **291.6 KiB**、charts **172.5 KiB**；该 PASS
  只放行 WS-14，不等于项目 GO。
- [x] WS-14 R2 fingerprint `4a167463...9cb80` 已取得正式
  `INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/1L）`，首轮两项 Medium CLOSED。托管 MinIO 规则同时设置当前版本
  N 天与非当前版本 1 天并精确匹配；V34 在 fresh schema 种入 editable int
  `cleanup.backup.retentionDays=30`，管理端拒绝 0/负数，对象和终态记录侧均验证读取 45 天。Phase 53 demo
  样本无需重做。九模块正确口径为 **66 suites / 421 tests**、focused **27/27**；唯一 Low 是历史 XML 混入原统计，
  已勘误且不阻断。隔离 Phase00/Phase47 IT 留稳定发布前证据；本 PASS 不等于项目发布 GO。
- [ ] 上述范围化 PASS 不构成生产部署或发布授权；正式域名、受信证书、公网 DNS、真实外部 MinIO TLS gateway 与生产切流仍须在授权发布环境核验。

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
- 旧 root runtime 写入的 `video-probe-temp` 若未经停机属主迁移，会被新 UID 10001 只读遮蔽并导致启动锁或首次探测失败；
  TLS bind 若缺补充组读取/目录穿越权限，或只挂载了指向目录外的证书链接，会使非 root Nginx 启动失败。
- V31/V32 是不可与旧定稿协议混部的单向迁移；未执行上述停机切换、V32 后回滚旧二进制，均会破坏永久世代/候选台账或重新写入旧稳定 key。
- Phase 44 字典缓存 payload/owner/canonical identity 同样是不可混部协议；跳过停写、旧节点排空与 legacy pending 处置，或让旧 binary 回滚写入，会重新引入大小写别名旧值与提前解除写保护。
- V33 身份证件号密文/HMAC 是不可与 V32 旧写入者混部的单向协议；V33-capable 进程开始迁移后，即使回填失败，
  也不得让旧 binary 连接该库。必须保持写冻结并前向修复，或完整恢复已校验的 V33 前恢复点后再启旧 fleet。
- `CLEANED` 墓碑为应对任意迟到对象写而持续存在，会使候选表单调增长；必须监控增长和索引健康，任何压缩、分区或归档方案都需保持仍可能迟到世代的复查能力。
