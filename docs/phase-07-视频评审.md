# Phase 7 · 教学能力视频评审（M07）

> 优先级 P0 · 依赖：Phase 0(文件)、Phase 2 · 任务：T-057~T-067 · plan §6.6 / §6.11 / §15.2-B / §15.5
> 目标：≥2GB MP4 分片上传、格式/大小/时长校验、双教师独立评审（提交前互不可见）、分差判定、复评/仲裁结算、鉴权播放+水印。**AT-08 首验。**

## 1. 范围
分片上传（断点续传/秒传/进度/重传）、视频校验、评审任务分配、独立评分、分差结算、第三专家/学院仲裁、鉴权播放与动态水印。

## 2. 数据库（V13__video.sql / V28 / V29 / V30）
- `video_review`：student_id, assessment_year, video_file_id, duration_seconds, format_check, status, final_score, final_conclusion, arbitrate_reviewer, arbitrate_mode。
- `video_review_task`：video_review_id, reviewer_id, score, dimension_scores_json, comment, conclusion(合格/不合格), submitted, submit_time。唯一 `(video_review_id, reviewer_id)`。
- `file_object`：V29 增加 `checksum_algorithm/content_hash_verified/media_codec/media_validation_policy_hash/media_probe_version`，区分客户端声明摘要与服务端读取对象后验真的内容指纹，并记录验证时的编码、策略版本与探测器版本。V29 每个列/索引变更均先查 `information_schema` 再动态执行，允许 MySQL 非事务 DDL 部分成功后安全重跑。
- `video_upload_session`：V30 增加 `finalization_token`。`PRESIGNED_MULTIPART` complete 与 `SERVER_CHUNK` merge 均把当前 Redis 租约的递增 fencing token 写入会话；只有数据库 token 与仍存活的 Redis owner 同时匹配才可提交定稿结果。

## 3. 分片上传（MinIO multipart）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/video/upload/init` | 传 `fileMd5`（兼容字段名，当前为 SHA-256 分片树指纹）/size/chunkSize → uploadId + 已传分片(断点) + 秒传命中标志 |
| POST | `/api/video/upload/chunk` | 上传单分片（index、md5） |
| POST | `/api/video/upload/merge` | 合并 → file_id；触发校验 |
| GET | `/api/video/upload/progress?uploadId=` | 进度 |

- 单文件上限 `file.maxSize.video`（默认 2GB，可配）；分片失败可重传。
- 秒传只命中 `content_hash_verified=1` 且属于同一上传人、同一学生的对象；还必须匹配当前 `video.allowedCodecs` 等媒体策略哈希与探测器版本，并通过 MinIO HEAD 确认对象存在且大小精确一致。8/32 位旧摘要只用于同一会话兼容恢复，不参与秒传。普通 `VideoReviewVO` 不返回内部去重指纹。
- 定稿由 Redis 原子脚本实现跨节点单飞；租约自动续期、按 token 校验 owner/释放，并以数据库 fencing token 拒绝过期执行者提交。`SERVER_CHUNK` 在认领事务内持久化稳定最终 object key；进程在认领、对象生成或探测后退出，下一请求均可取得更高 token，复用同一对象或安全重合并后继续。
- 探测前通过容量守卫限制并发数、**全部活跃任务的累计临时盘预留**和磁盘余量；对象读取配置显式建连/套接字超时。JCodec 解复用、逐样本扫描和首帧解码在受限堆的独立 JVM 中运行，超过总墙钟时限会被主进程强制终止。

## 4. 视频校验（§6.11）
定稿/合并后由服务端流式读取最终对象并计算 SHA-256 分片树指纹，再交由独立工作 JVM 以 JCodec 探测实际 MP4 容器，要求恰好一个视频轨道并校验 `video.allowedCodecs`（默认 H264）与可解码首帧。探测器完整扫描样本，将容器头时长、样本时间线跨度和样本时长累计值按 `video.timelineToleranceSeconds`（默认 2s）两两交叉核对；三者一致后，可信实际时长才可继续校验 `video.durationTarget`（默认 900s）± `video.durationTolerance`（默认 60s）。实际大小必须与会话严格一致；客户端声明的 MIME、摘要和时长均不能单独使校验通过。失败置"校验失败"并提示，可重传。

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
> 2026-07-23 WS-3 第二轮独立重核结论仍为 **CHANGES REQUESTED**；第三轮已按该报告完成 3 High / 3 Medium 的实现与自测，当前仅置“待独立重核”，不得自行改判 PASS。下列两项继续因真实 2GB 传输及非允许编码/不可解码首帧的专项自动化证据债保持未完成，原退回依据见 `reviews/ws-03-second-remediation-rereview-2026-07-23.md`。

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
- T-VID-2D（并发/恢复反例）：同一 uploadId 并发 complete → 只执行一次全对象探测；无有效租约的陈旧 MERGING 会话可恢复。
- T-VID-2E（资源边界反例）：两个活跃探测按累计预留与真实磁盘水位准入；拒绝/关闭后并发槽位和预留均完整释放；`maxPackets` 低阈值、多视频轨道稳定拒绝。
- T-VID-2F（硬时限反例）：工作进程阻塞超过墙钟后被强制终止；生产 fat JAR 的 `PropertiesLauncher` 可正常执行媒体 worker。
- T-VID-2G（租约/崩溃恢复反例）：短 TTL 下活跃 owner 自动续租，释放后 successor 获得更高 fencing token；`SERVER_CHUNK` 分别在 CLAIMED / OBJECT_READY / PROBED 后模拟退出，重试均完成且最终只有一份 `file_object` 和一份 `video_review`。
- T-VID-3：教师 A(85,合格) 提交 → 教师 B 查看任务看不到 A 的分。
- T-VID-4：A=85 B=80（差 5，均合格）→ 终分 83 合格。
- T-VID-5（关键）：A=85 B=60（差 25 > 12）→ 需复评。
- T-VID-6（关键）：A=85合格 B=58不合格 → 需复评（结论冲突）。
- T-VID-7：`thirdExpert` 模式 s3=81，三分 85/60/81 → 取 85 与 81 均值 83（两两分差最小对 |85−81|=4）。
- T-VID-8（反例）：未登录用预签名链接播放 → 失败。
- T-VID-9（并发反例）：媒体探测尚未提交 review 更新时并发分配评委 → 行锁串行化，定稿不得把已进入评审的状态覆盖回待评审。
- T-VID-10（迁移恢复反例）：V29 前两条 DDL 已落库但 Flyway 尚未记成功 → 重跑 V29 可补齐剩余列/索引/参数并成功记录历史。

## 10. DoD
分片上传 + 校验 + 双盲评审 + 分差/复评结算 + 鉴权水印播放全部可用；AT-08 自测（含两类需复评反例）通过。

## 11. 风险
- "提交前互不可见"必须在**接口层**屏蔽（不能只前端隐藏），否则可绕过——T-VID-3 专门覆盖。
- 大文件上传的内存/磁盘/超时：分片走流式、合并用 MinIO 服务端 ComposeObject；媒体探测已采用可续租 fencing lease、累计容量守卫、显式 S3 超时和可强杀的受限堆工作进程。第三轮自测已覆盖低阈值资源与三处崩溃恢复点，但仍须由独立复核者确认不变量后才能改判。
- 复评结算 `thirdExpert` 的"两两分差最小对"算法要单测，避免边界取错对。
