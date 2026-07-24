# Phase 7 · 教学能力视频评审（M07）

> 优先级 P0 · 依赖：Phase 0(文件)、Phase 2 · 任务：T-057~T-067 · plan §6.6 / §6.11 / §15.2-B / §15.5
> 目标：≥2GB MP4 分片上传、格式/大小/时长校验、双教师独立评审（提交前互不可见）、分差判定、复评/仲裁结算、鉴权播放+水印。**AT-08 首验。**

## 1. 范围
分片上传（断点续传/秒传/进度/重传）、视频校验、评审任务分配、独立评分、分差结算、第三专家/学院仲裁、鉴权播放与动态水印。

## 2. 数据库（V13__video.sql / V28 / V29 / V30 / V31 / V32）
- `video_review`：student_id, assessment_year, video_file_id, duration_seconds, format_check, status, final_score, final_conclusion, arbitrate_reviewer, arbitrate_mode。
- `video_review_task`：video_review_id, reviewer_id, score, dimension_scores_json, comment, conclusion(合格/不合格), submitted, submit_time。唯一 `(video_review_id, reviewer_id)`。
- `file_object`：V29 增加 `checksum_algorithm/content_hash_verified/media_codec/media_validation_policy_hash/media_probe_version`，区分客户端声明摘要与服务端读取对象后验真的内容指纹，并记录验证时的编码、策略版本与探测器版本。V29 每个列/索引变更均先查 `information_schema` 再动态执行，允许 MySQL 非事务 DDL 部分成功后安全重跑。
- `video_upload_session`：V30 增加 `finalization_token`；V31 将历史 `NULL` 归一为 `0`，并改为 `BIGINT NOT NULL DEFAULT 0` 的永久高水位。`PRESIGNED_MULTIPART` complete 与 `SERVER_CHUNK` merge 每次认领都在数据库行锁内递增世代，完成/失败后也不回退、不清空。Redis 只保存随机 UUID owner 并承担活跃租约，不再生成可因过期或恢复而 ABA 的数字序列；只有数据库世代与仍存活的 Redis owner 同时匹配才可提交定稿结果。
- `video_finalization_object_candidate`：V32 为每个 `(upload_id, finalization_generation)` 建立唯一对象候选台账，记录对象键、`ACTIVE/CLEANUP_PENDING/CLEANING/REGISTERED/CLEANED` 状态、静默期、跨节点清理租约、重试与登记结果。`CLEANED` 是持续复查墓碑而非一次性终态，用于删除旧 owner 在首次确认对象不存在后仍迟到生成的对象。
- 清理 claim 的有效时长强制不低于 `2 × MINIO_CALL_TIMEOUT_SECONDS + video.finalizationCleanupSafetySeconds`；当前默认 `1860s`，覆盖一轮 `remove + exists` 两次串行对象调用，禁止参数页把租约调到仍有调用在飞时就过期；极端参数组合超过整数秒范围时饱和到整数上限，不能因算术边界让定时对账持续停摆。

## 3. 分片上传（MinIO multipart）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/video/upload/init` | 传 `fileMd5`（兼容字段名，当前为 SHA-256 分片树指纹）/size/chunkSize → uploadId + 已传分片(断点) + 秒传命中标志 |
| POST | `/api/video/upload/chunk` | 上传单分片（index、md5） |
| POST | `/api/video/upload/merge` | 合并 → file_id；触发校验 |
| GET | `/api/video/upload/progress?uploadId=` | 进度 |

- 单文件上限 `file.maxSize.video`（默认 2GB，可配）；分片失败可重传。
- 秒传只命中 `content_hash_verified=1` 且属于同一上传人、同一学生的对象；还必须匹配当前 `video.allowedCodecs` 等媒体策略哈希与探测器版本，并通过 MinIO HEAD 确认对象存在且大小精确一致。8/32 位旧摘要只用于同一会话兼容恢复，不参与秒传。普通 `VideoReviewVO` 不返回内部去重指纹。
- 定稿由 Redis 原子脚本实现跨节点单飞；租约自动续期并按随机 owner 校验/释放，数据库永久世代拒绝旧执行者晚提交。V32 后 `SERVER_CHUNK` 每一世代使用 `teaching-video/server-finalized/{uploadId}/g-{generation}.mp4` 独立对象键；认领事务同步建立 `ACTIVE` 候选，旧候选进入带静默期的持久清理。成功登记与候选 `REGISTERED` 在同一元数据事务提交；失败/失权候选即使即时删除失败也保留清理事实，不再依赖一次性 `removeObject`。server 定稿与 assign 冲突、direct 接管时 multipart 与最终对象均消失等不可继续情形会收敛为 `FAILED`，不再永久停留 `MERGING`。
- 生产 `prod` profile 在启动时补录遗留 `MERGING/FAILED` 会话，并以独立于普通生命周期清扫开关的每分钟任务处理到期清理；普通清理与 `CLEANED` 墓碑复查使用独立批次，避免墓碑积压饿死故障恢复。删除前再次保护当前会话引用、同键 `ACTIVE/REGISTERED` 候选和 `file_object` 已登记对象。
- 探测前通过容量守卫限制并发数、**全部活跃任务的累计临时盘预留**和磁盘余量；活跃文件已写字节与未来增长分开计算，文件写入和容量快照在同一管理锁下完成。无法确认退出的 worker 会登记为活孤儿，并在准入前后双重检查，孤儿退出前禁止新任务复用其槽位。
- JCodec 解复用、逐样本扫描和首帧解码在受限堆的独立 JVM 中运行；超过总墙钟时限后强制终止，并必须在宽限期内确认 PID 已退出。V4 worker 在打开源文件后以跟踪通道区分真实源 I/O 与第三方解析器内容异常：JCodec 在损坏 movie/track 内部抛出的 `IOException` 输出结构化 `valid=false` 且 exit 0；源文件打开/读取、结果写入、异常退出或畸形结果仍属于可重试基础设施故障。
- worker 同时持有父 JVM 的 PID 与精确 `startInstant`，按 `VIDEO_PROBE_PARENT_CHECK_INTERVAL` 周期核验父进程身份；父 JVM 消失或 PID 被复用时子进程自行退出，避免裸机重启后旧 worker 脱离监管。
- 探测工件由唯一管理器写入专用目录：严格版本化命名、owner heartbeat、启动/周期 TTL 清扫和目录独占锁共同防止崩溃残留耗尽空间。清扫使用持久词法游标轮转，单轮仅保留不超过 `scanLimit` 的两个有界候选堆；活跃、未过期或本轮删除失败的候选也推进游标，避免目录尾部长期饥饿。每个后端实例必须使用具有独立配额的专属目录/卷；目录锁失败即拒绝启动，禁止多实例共享，也禁止放置无关文件。

## 4. 视频校验（§6.11）
定稿/合并后由服务端流式读取最终对象并计算 SHA-256 分片树指纹，再交由独立工作 JVM 以 JCodec 探测实际 MP4 容器，要求恰好一个视频轨道并校验 `video.allowedCodecs`（默认 H264）与可解码首帧。探测器完整扫描样本，将容器头时长、样本时间线跨度和样本时长累计值按 `video.timelineToleranceSeconds`（默认 2s）两两交叉核对；三者一致后，可信实际时长才可继续校验 `video.durationTarget`（默认 900s）± `video.durationTolerance`（默认 60s）。实际大小必须与会话严格一致；客户端声明的 MIME、摘要和时长均不能单独使校验通过。V4 探测协议把解析器报告的损坏媒体归为明确内容不合格，置“校验失败”并允许重传；真实对象存储、源/结果 I/O、进程、磁盘和结果协议故障保留 `MERGING` 恢复语义并提示稍后重试。

## 5. 评审流程与结算（§15.2-B / §15.5）
- 分配 `video.reviewerCount`（默认 2）位教师 → "评审中"，任务下发，**提交前互不可见**他人分数/意见。
- 维度评分（字典 `video_score_dimension`，9 维，权重在 `ext_json`），合格线 `video.passLine`（默认 60）。
- 全部提交后判定：
  - `|s1−s2| ≤ video.diffThreshold`（默认 12）**且**结论一致 → 终分 `round((s1+s2)/2)`，结论取一致结论。
  - `|s1−s2| > 阈值` **或** 一合格一不合格 → "需复评"。
- 复评 `video.arbitrate.mode`：
  - `thirdExpert`：第三专家给 s3 → 终分=三者中两两分差最小的两者均值；
  - `collegeArbitrate`：学院仲裁人直接裁定终分与结论（留痕）。
- 终分 ≥ 合格线 → 合格；学院负责人 `video:confirm` 确认结果 → 进入证书前置。

## 6. 鉴权播放与水印
- 播放地址=限时预签名，`video:play` 鉴权后下发；未登录/越权无法获取（复制链接失效）。
- 播放页动态水印：用户名+工号+时间戳浮层（前端渲染，随机移动）。

## 7. 前端
- 学生端上传页：分片进度、断点续传、失败重传、校验结果。
- 评审教师页：鉴权播放器+水印、9 维评分表单、意见、提交（提交前不可见他人）。
- 学院管理页：分配、进度看板、复评/仲裁、结果确认。

## 8. 验收清单（AT-08）
> 2026-07-24 WS-3 第六轮独立复核 **PASS**，确认第五轮新增的 scheduler trigger 隔离与 V32 candidate 全量备份覆盖 **2 Medium 全部关闭**，见 `reviews/ws-03-sixth-remediation-rereview-2026-07-24.md`。唯一新增 Low 是提交材料把 32 个迁移误写成 33 个，不阻断；T-VID-2L/2M 的开发者动态证据保留。下列两项仍因真实 2GB 传输、非允许编码与不可解码首帧证据债保持未完成，不能由本轮小样本替代。

- [ ] 2GB MP4 分片上传成功；中断后续传成功；同上传人/同学生且服务端验真指纹相同可秒传。
- [ ] 伪 MP4、非允许编码、不可解码首帧、超大小、实际媒体时长超容差、客户端指纹不匹配 → 校验失败并提示，可重传。
- [x] 一个视频由 ≥2 位教师独立评审；**教师 A 提交后教师 B 仍看不到 A 的分与意见**。
- [x] 分差 ≤ 阈值且结论一致 → 终分=均分，自动判合格线。
- [x] **分差 > 阈值 或 一合格一不合格 → 进入"需复评"**（AT-08 关键）。
- [x] 复评（第三专家或学院仲裁）产生唯一终分与结论并留痕。
- [x] 合格线/阈值/评审人数/容差均来自 `sys_param`，改参数即生效。
- [x] 播放需鉴权；复制播放链接未登录不可访问；播放页有动态水印。

## 9. 测试用例
- T-VID-1：2GB 文件分 N 片上传，断网后 `init` 返回已传分片并续传成功。
- T-VID-2（反例）：上传 avi、伪 MP4、短视频却声明 15min、指纹不匹配 → 服务端按真实对象校验失败。
- T-VID-2A（越权反例）：学生 B 重放学生 A 的已知指纹 → 不得秒传；正常详情响应不含内部去重指纹。
- T-VID-2B（时间线反例）：篡改 MP4 头部时长但保留原样本时间线 → 容器头、样本跨度与样本累计时长不一致，校验失败。
- T-VID-2C（策略/对象反例）：改变 `video.allowedCodecs` 或删除已有 MinIO 对象 → 旧验证结果不得秒传命中。
- T-VID-2D（并发/恢复反例）：同一 uploadId 并发 complete → 只执行一次全对象探测；无有效租约的陈旧 MERGING 会话可恢复或收敛为可重新初始化的明确失败态。
- T-VID-2E（资源边界反例）：两个活跃探测按累计预留与真实磁盘水位准入；已写字节不重复计数，写入不得穿过原子容量快照；拒绝/关闭后并发槽位和预留均完整释放；`maxPackets` 低阈值、多视频轨道稳定拒绝。
- T-VID-2F（硬时限反例）：工作进程阻塞超过墙钟后被强制终止且 PID 已退出；拒绝退出时登记孤儿并阻止后续准入；生产 fat JAR 的 `PropertiesLauncher` 由 Maven verify 自动执行媒体 worker。
- T-VID-2G（租约/崩溃恢复反例）：短 TTL 下活跃 owner 自动续租，释放后 successor 在数据库行锁内取得更高永久世代；`SERVER_CHUNK` 分别在 CLAIMED / OBJECT_READY / PROBED 后模拟退出，重试均完成且最终只有一份 `file_object` 和一份 `video_review`。
- T-VID-2H（持久 fencing 反例）：旧 owner 取得执行权后模拟 Redis 序列丢失/恢复，successor 的数据库世代仍严格更大；旧 owner 的 renew/assert/close/提交均不得影响 successor。
- T-VID-2I（状态收敛反例）：server 在 PROBED 后与 assign 确定性交错、server 陈旧 MERGING 的源分片被清理，以及 direct 陈旧 MERGING 的 multipart/final object 同时不存在时，会话均必须离开 MERGING 并保留明确可重试/终态语义。
- T-VID-2J（临时资源反例）：启动/定时清扫只删除过期孤儿临时文件，不删除活跃 owner 或未知文件；同一目录的第二实例启动失败；worker 超时后必须确认进程退出，进程/S3/磁盘/结果协议故障不得误记为内容校验失败。
- T-VID-2K（配置与容量反例）：实时 usable 随活跃文件下降时，`MAX_RESERVED + MIN_FREE` 仍兑现配置并发；孤儿在首次检查与取得槽位之间登记时，迟到准入必须回滚；AWS S3Client 与 MinioClient 均受大于零的显式总调用/连接/读写超时控制；Maven verify 自动执行 fat-JAR worker smoke。
- T-VID-2L（解析错误域反例，开发者动态通过）：固定 44 字节 MP4 结构具备合法顶层 box，但损坏 movie/track 并触发 JCodec 内容解析异常；真实子进程输出结构化内容失败且 exit 0，direct/server 会话均进入可重新初始化的 `VALIDATION_FAILED`。缺失源文件等真实基础设施 I/O 仍以非零退出保留恢复语义。
- T-VID-2M（终态对象对账反例，开发者动态通过）：确定性注入一次对象删除失败，持久候选保留错误与尝试次数，过期 `CLEANING` 租约可被后续任务回收并清理；两个 SERVER generation 先进入 `CLEANED`，再真实执行旧 generation 迟到 compose，只有该墓碑到期时仍能重新删除复活对象。另验证 claim 不短于两次对象调用总上限且极值不让对账停摆，`ACTIVE`、同键 `REGISTERED` 与 `file_object` 三类对象保护，遗留 FAILED 会话启动回填，以及 direct 已认领回退、取消、multipart 缺失时 `ACTIVE` 候选均被事务性退休。
- T-VID-2N（调度隔离反例，开发者动态通过、独立复核确认可信）：在真实 Spring scheduling 上下文中将同步全量备份阻塞于普通 `taskScheduler`，保持备份未释放时，视频对账的专用 trigger scheduler 仍至少连续触发并提交两次；测试自然退出且不执行真实备份 I/O。
- T-VID-2O（candidate 恢复反例，开发者动态通过、独立复核确认可信）：应用逻辑全量备份的 37 个 section 与当前 schema 表集合一致并包含 candidate；隔离 scratch schema 回放多个 generation 的 `CLEANUP_PENDING/CLEANING/CLEANED` 全字段，保留 attempt、claim、error 与 tombstone，随后真实 reconciler 可继续清理、释放 claim 并递增 attempt。对象删除使用安全内存替身；此项不替代 Phase 41 整份脚本恢复。
- T-VID-3：教师 A(85,合格) 提交 → 教师 B 查看任务看不到 A 的分。
- T-VID-4：A=85 B=80（差 5，均合格）→ 终分 83 合格。
- T-VID-5（关键）：A=85 B=60（差 25 > 12）→ 需复评。
- T-VID-6（关键）：A=85合格 B=58不合格 → 需复评（结论冲突）。
- T-VID-7：`thirdExpert` 模式 s3=81，三分 85/60/81 → 取 85 与 81 均值 83（两两分差最小对 |85−81|=4）。
- T-VID-8（反例）：未登录用预签名链接播放 → 失败。
- T-VID-9（并发反例）：媒体探测尚未提交 review 更新时并发分配评委 → 行锁串行化，定稿不得把已进入评审的状态覆盖回待评审。
- T-VID-10（迁移恢复反例）：V29 前两条 DDL 已落库但 Flyway 尚未记成功 → 重跑 V29 可补齐剩余列/索引/参数并成功记录历史。
- T-VID-11（高水位迁移反例）：从 V30 升 V31 时历史 `NULL` 归一为 0、已有正世代保持不变，并断言列为 `NOT NULL DEFAULT 0`、Flyway 历史成功。
- T-VID-12（对象台账迁移反例）：从 V31 隔离 schema 升 V32，校验候选表、唯一键/到期/对象/会话索引、5 个清理参数和 Flyway 成功历史；预置已逻辑删除的同键参数时保留原 ID、恢复启用并更新默认值。

## 10. DoD
分片上传 + 校验 + 双盲评审 + 分差/复评结算 + 鉴权水印播放全部可用；AT-08 自测（含两类需复评反例）通过。

## 11. 风险
- "提交前互不可见"必须在**接口层**屏蔽（不能只前端隐藏），否则可绕过——T-VID-3 专门覆盖。
- 大文件上传的内存/磁盘/超时：分片走流式、合并用 MinIO 服务端 ComposeObject。第五轮已补齐 V4 内容错误域、V32 generation 候选台账/持久墓碑、清扫公平游标和父进程身份 watchdog；第六轮补齐对账 trigger 调度隔离与 candidate 全量备份并由独立报告 PASS，当前 WS-3 无阻断 finding。真实 2GB/非允许编码/不可解码首帧证据债继续保留。
- V31/V32 共同改变定稿协议与 SERVER 对象键。旧节点会回写/清空世代或继续写稳定 key，故禁止与旧二进制混部，也禁止 V32 后回滚旧版本；发布必须按 Phase 14 的停写、停全部旧节点/worker、执行 V32、全量新节点、再放流顺序执行。
- 容量预留是单实例内状态。每个实例必须独占具有独立配额/文件系统的探测卷；共享卷、共享目录或在目录内放置其它文件均不受支持。
- 复评结算 `thirdExpert` 的"两两分差最小对"算法要单测，避免边界取错对。
