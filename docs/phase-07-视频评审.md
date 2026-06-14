# Phase 7 · 教学能力视频评审（M07）

> 优先级 P0 · 依赖：Phase 0(文件)、Phase 2 · 任务：T-057~T-067 · plan §6.6 / §6.11 / §15.2-B / §15.5
> 目标：≥2GB MP4 分片上传、格式/大小/时长校验、双教师独立评审（提交前互不可见）、分差判定、复评/仲裁结算、鉴权播放+水印。**AT-08 首验。**

## 1. 范围
分片上传（断点续传/秒传/进度/重传）、视频校验、评审任务分配、独立评分、分差结算、第三专家/学院仲裁、鉴权播放与动态水印。

## 2. 数据库（V13__video.sql）
- `video_review`：student_id, assessment_year, video_file_id, duration_seconds, format_check, status, final_score, final_conclusion, arbitrate_reviewer, arbitrate_mode。
- `video_review_task`：video_review_id, reviewer_id, score, dimension_scores_json, comment, conclusion(合格/不合格), submitted, submit_time。唯一 `(video_review_id, reviewer_id)`。

## 3. 分片上传（MinIO multipart）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/video/upload/init` | 传 fileMd5/size/chunkSize → uploadId + 已传分片(断点) + 秒传命中标志 |
| POST | `/api/video/upload/chunk` | 上传单分片（index、md5） |
| POST | `/api/video/upload/merge` | 合并 → file_id；触发校验 |
| GET | `/api/video/upload/progress?uploadId=` | 进度 |

- 单文件上限 `file.maxSize.video`（默认 2GB，可配）；分片失败可重传；MD5 秒传。

## 4. 视频校验（§6.11）
合并后校验：格式=MP4、大小≤上限、时长=15min ± `video.durationTolerance`（默认 60s）。失败置"校验失败"并提示，可重传。

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
- [ ] 2GB MP4 分片上传成功；中断后续传成功；相同 MD5 秒传。
- [ ] 非 MP4/超大小/时长超容差 → 校验失败并提示，可重传。
- [ ] 一个视频由 ≥2 位教师独立评审；**教师 A 提交后教师 B 仍看不到 A 的分与意见**。
- [ ] 分差 ≤ 阈值且结论一致 → 终分=均分，自动判合格线。
- [ ] **分差 > 阈值 或 一合格一不合格 → 进入"需复评"**（AT-08 关键）。
- [ ] 复评（第三专家或学院仲裁）产生唯一终分与结论并留痕。
- [ ] 合格线/阈值/评审人数/容差均来自 `sys_param`，改参数即生效。
- [ ] 播放需鉴权；复制播放链接未登录不可访问；播放页有动态水印。

## 9. 测试用例
- T-VID-1：2GB 文件分 N 片上传，断网后 `init` 返回已传分片并续传成功。
- T-VID-2（反例）：上传 avi / 时长 18min → 校验失败。
- T-VID-3：教师 A(85,合格) 提交 → 教师 B 查看任务看不到 A 的分。
- T-VID-4：A=85 B=80（差 5，均合格）→ 终分 83 合格。
- T-VID-5（关键）：A=85 B=60（差 25 > 12）→ 需复评。
- T-VID-6（关键）：A=85合格 B=58不合格 → 需复评（结论冲突）。
- T-VID-7：`thirdExpert` 模式 s3=81，三分 85/60/81 → 取 85 与 81 均值 83（两两分差最小对 |85−81|=4）。
- T-VID-8（反例）：未登录用预签名链接播放 → 失败。

## 10. DoD
分片上传 + 校验 + 双盲评审 + 分差/复评结算 + 鉴权水印播放全部可用；AT-08 自测（含两类需复评反例）通过。

## 11. 风险
- "提交前互不可见"必须在**接口层**屏蔽（不能只前端隐藏），否则可绕过——T-VID-3 专门覆盖。
- 大文件上传的内存/超时：分片走流式、合并用 MinIO 服务端 ComposeObject，避免后端 OOM。
- 复评结算 `thirdExpert` 的"两两分差最小对"算法要单测，避免边界取错对。
