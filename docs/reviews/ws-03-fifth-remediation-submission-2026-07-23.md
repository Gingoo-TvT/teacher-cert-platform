# WS-3 第五轮整改提交材料（2026-07-23）

**范围：** `ee190f3..HEAD`，仅处理第四轮报告的 1 High / 1 Medium / 2 Low。
**基线报告：** `ws-03-fourth-remediation-rereview-2026-07-23.md`。
**提交方结论：** 整改与开发者反例已完成，请求独立复核。
**正式状态：** 第五轮独立报告 PASS 前继续沿用 **CHANGES REQUESTED**，本材料不是复核 PASS。

## 1. 整改摘要

| 第四轮 finding | 第五轮实现 | 主要证据 |
|---|---|---|
| High：JCodec 内容解析 `IOException` 被误判为基础设施故障，损坏媒体永久卡 `MERGING` | worker V4 把源打开与真实源 I/O 保留在基础设施边界；以跟踪通道识别第三方解析器内部异常，并输出结构化 `valid=false`、exit 0 | `VideoProbeWorkerBoundaryTest`；`Phase7VideoReviewIT.damagedMovieStructureIsValidationFailureForBothUploadModesAndAllowsRetry` |
| Medium：FAILED 最终对象仅一次删除；SERVER 旧 owner 可迟到复活稳定 key | V32 generation 候选台账；SERVER generation-specific key；失败/失权持久重试；跨节点清理租约；`CLEANED` 持续墓碑；生产启动回填与每分钟独立 reconciliation | V32 migration IT；首次删除失败回收；首次 CLEANED 后真实迟到 compose 再删除；对象保护与遗留回填 |
| Low：有界临时工件扫描可能饿死目录尾部 | 持久词法游标轮转；两个最大堆各不超过 `scanLimit`，内存 O(scanLimit)；任何已检查候选都推进游标 | `VideoProbeTempArtifactManagerTest` 的重启轮转与逆字典序大于 limit 反例 |
| Low：裸机父 JVM 崩溃后旧 worker 脱离内存监管 | 父进程 PID + 精确 `ProcessHandle.startInstant` 身份令牌；worker 周期复查，父消失/PID 复用时 `halt(74)` | `VideoProbeWorkerBoundaryTest` 真实父辅助 JVM 退出反例；fat-JAR 参数接线 IT |

## 2. High：媒体内容错误域

- `VideoProbeWorkerMain` 只在成功打开源文件后进入媒体解析错误域。
- `TrackingSeekableByteChannel` 记录真实底层通道 I/O；此类异常继续上抛并由父进程视为可恢复基础设施故障。
- JCodec 在 box/movie/track 内容解析内部抛出的 `IOException` 被转换为结构完整的无效媒体结果，worker 正常 exit 0。
- 结果文件创建/写入不在内容捕获范围内，避免磁盘故障伪装为“用户文件不合格”。
- 探测版本由 V3 升为 V4，旧可信对象不会跨错误域策略直接秒传命中。

T-VID-2L 使用固定 44 字节 MP4 结构夹具，不使用 fuzz、随机畸形载荷或恶意样本。真实子进程返回结构化 invalid；direct 与 server 两种入口均持久化 `VALIDATION_FAILED`，同年度可重新初始化。缺失源文件仍返回基础设施退出语义。

## 3. Medium：定稿对象持久对账

### 3.1 V32 台账与对象身份

`video_finalization_object_candidate` 以 `(upload_id, finalization_generation)` 唯一标识候选对象，状态为：

`ACTIVE → REGISTERED`，或 `ACTIVE → CLEANUP_PENDING → CLEANING → CLEANED`。

`CLEANED` 表示“本次确认对象不存在并保留持续复查”，不是可丢弃的终态。

SERVER 对象键从稳定 key 改为：

`teaching-video/server-finalized/{uploadId}/g-{generation}.mp4`

旧 owner 的迟到写不能覆盖 successor 对象。每次认领在会话行锁事务内递增永久世代并建立候选；成功 `file_object` 登记与候选 `REGISTERED` 同事务提交；终态失败与候选清理事实同事务提交。

### 3.2 对账、重试与保护

- 清理任务使用数据库 claim owner/expiry，过期 `CLEANING` 可被其它节点回收。
- claim 有效时长在运行时强制不低于 `2 × MinIO call timeout + safety`（当前默认 `1860s`），覆盖串行 `remove + exists`，避免对象调用仍在飞时被其它节点提前重复领取；极端参数组合超过整数秒范围时饱和到整数上限，避免 reconciliation 因算术边界持续停摆。
- MinIO 调用在数据库事务之外执行；结果通过短事务落账。
- `CLEANUP_PENDING`/过期 `CLEANING` 与 `CLEANED` 墓碑使用独立批次，历史墓碑不会占满紧急故障清理配额。
- 删除前重新检查当前会话引用、同键 `ACTIVE/REGISTERED` 候选和 `file_object`；任何一项成立都禁止删除并收敛为登记态。
- direct 已认领后回退到 `UPLOADING`、主动取消及 multipart 缺失收敛为 `FAILED` 时，均在会话行锁事务内同步把遗留 `ACTIVE` 候选退休为 `CLEANUP_PENDING`；对象删除仍在提交后执行，失败事实不会随进程退出丢失。
- `prod` profile 在 `ApplicationReadyEvent` 把遗留 `MERGING/FAILED` 回填与首次对账投递给独立单线程执行器并立即返回，不阻塞 readiness；之后默认每分钟触发，同一实例防重入且不排队。该任务不受普通 lifecycle cleanup 开关控制。
- V32 新增 5 个可调参数：静默期、失败重试、claim 时限、batch 上限和墓碑复查周期。

T-VID-2M 的确定性交错：

1. 第一次对象删除被测试存储适配器精确失败一次，候选保留 `last_error/attempt_count`。
2. 将该候选置为过期 `CLEANING`，后续对账重新认领并删除，状态进入 `CLEANED`。
3. 两个 SERVER generation 先清理至 `CLEANED`。
4. 在首次 CLEANED 之后，用原分片真实执行旧 generation 的 MinIO compose，复活旧对象。
5. 只使旧 generation 墓碑到期；对账再次删除复活对象并继续保留墓碑。
6. 另行验证 ACTIVE、同键 REGISTERED、`file_object` 三类保护，以及遗留 FAILED 会话启动回填的幂等性。
7. 在 direct `CLAIMED` 后确定性注入回退，并分别覆盖主动取消与 multipart 缺失终态，验证 `ACTIVE` 候选均退休、迟到对象最终进入 `CLEANED`。

## 4. 两个 Low

### 4.1 临时工件公平性

清扫器不再收集并排序整个目录。它单次遍历目录元数据，以持久游标将候选分为“游标后”和“回绕”两组，每组只保留不超过 `scanLimit` 的最大堆；实际处理量不超过 limit。无论候选活跃、未过期、删除失败或删除成功，游标均前进，因此低 limit 与不稳定 `DirectoryStream` 顺序下也能跨轮覆盖尾部。

### 4.2 父进程生命周期

父进程创建 worker 时传入当前 PID、精确启动时刻和检查周期。worker 启动前先核验一次，运行期间由 daemon watchdog 重复核验；父进程不存在、启动时刻缺失或不匹配时立即退出。PID 复用不能冒充原父进程。

## 5. 已执行证据

- V32 从 V31 隔离 schema 升级：**1/1**。
- T-VID-2L/2M + ACTIVE/REGISTERED/file_object 保护 + 遗留回填：**5/5**。
- claim 安全下限/整数极值 + direct 已认领回退、取消与 multipart 缺失终态：**3/3**。
- worker 错误域/父进程、临时工件公平性、生产对账调度聚焦单测：**19/19**；其中阻塞回填替身证明 `ApplicationReady` 监听器在 **500ms** 内返回且运行中周期触发不排队。
- 预签名端点/CORS 环境可移植性修正后单项：**1/1**。
- 本轮专用空库成功执行 **33 个 Flyway 迁移至 V32**；V32 迁移 IT **1/1**。
- 修复启动 readiness 阻塞后重新从空库执行最终后端 `clean verify`：Surefire **148/148**、Failsafe **168/168**，合计 **316/316**，0 failure/error/skip；Phase 7 **44/44**。
- 前端 type-check/build、dev/prod Compose config 与 `git diff --check` 均通过；前端仅保留既有大 chunk 警告。

全部命令均为会自然退出的一次性构建/测试，未由 Codex 启动常驻后端或前端。

## 6. 发布顺序

V32 不是滚动兼容迁移：

1. 停止视频上传、complete/merge/assign 写流量。
2. 停止全部第五轮之前的后端、finalize 与媒体 worker，并确认无旧进程存活。
3. 使用第五轮二进制执行 Flyway 至 V32。
4. 核验 V31 永久世代约束、V32 候选表/索引与 5 个参数。
5. 启动全部第五轮实例，核验启动回填/对账、每实例独占 probe 卷及 generation-specific 对象键。
6. 健康检查通过后恢复写流量。

禁止 V31/旧稳定 key 协议与第五轮混部；V32 后禁止回滚旧二进制，只能前向修复。

## 7. 安全边界与未覆盖项

遵照用户要求，本轮未执行漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz、攻击性并发或针对现有服务的破坏操作。损坏媒体仅为固定结构夹具；删除失败和迟到 compose 仅作用于本轮专用隔离 MinIO 对象。

仍未由本轮关闭：

- 真实 2GB 上传、非允许编码和不可解码首帧的专项证据债。
- Phase 53 demo 对象/元数据升级不一致。
- Phase 0、39、41、42、44、47、53 各自正式退回项。

## 8. 独立复核请求

请冻结 `ee190f3..HEAD`，优先复核：

1. 内容解析异常与真实源/结果 I/O 是否仍存在误分类路径。
2. 候选状态机的事务边界、锁顺序、claim 回收及对象保护是否会误删成功对象。
3. `CLEANED` 墓碑能否覆盖首次确认不存在后的任意迟到 compose，且不饿死普通故障清理。
4. 公平游标在低 scan limit、逆目录顺序、重启和删除失败下是否持续推进。
5. 父 PID + 启动时刻 watchdog 在 fat JAR 与官方部署路径是否完整接线。
6. V32 停机发布协议、生产定时任务与参数文档是否一致。

独立复核者应产出新的第五轮报告；只有该报告 PASS 后，WS-3/U-002 才能更新为已复核。
