# Phase 53 用户动态门禁执行记录（2026-07-26）

- 执行日期：2026-07-26 11:28–12:04 (+08:00)
- 执行方式：用户授权的本机会话按 `docs/reviews/phase-53-remediation-submission-2026-07-26.md` §5 的 10 步动态门禁逐项执行
- 性质：**用户侧动态证据记录，不是独立复核报告**。Phase 53 正式状态仍为 **CHANGES REQUESTED**，须由后续正式独立增量复核（冻结 `4996811..b9abc6c`）判定 PASS 后方可放行。
- 证据目录：`docs/reviews/evidence/phase-53-dynamic-2026-07-26/`（下文文件号均指该目录）

## 1. 候选同源性

- 工作树 HEAD `724e07d`，与代码候选 `b9abc6c` 的差异仅 7 个文档文件（`00-env-meta.txt`）；跟踪文件无未提交修改。
- `sample-video.mp4` blob = `31205d6cc42d9c850713e806b744446326cc807a`，与提交材料冻结值一致。
- 构建产物 `teacher-cert-platform.jar` SHA-256 `be0cbea35040eee2af04e1c0ea6a3975e35d5604aaf066803d15dfc2ffeb74ca`（`mvn -o -DskipTests package`，`01`）。全程使用该单一 JAR。

## 2. 隔离环境

| 环境 | MySQL schema | MinIO bucket | Redis db | 用途 |
|---|---|---|---|---|
| A | `phase53_demo_a` | `phase53-demo-a` | 15 | 旧 528B 升级主路径 + 幂等 + 浏览器/鉴权 |
| B | `phase53_demo_b` | `phase53-demo-b` | 14 | 预存污染拒绝（两种变体） |
| C | `phase53_demo_c` | `phase53-demo-c` | 13 | 实体 SQL 故障回滚 + 恢复链 |

依赖为本机 `docker-compose.dev.yml` 的 tcp-mysql/tcp-minio/tcp-redis（会话前为停止状态，会话后恢复停止）。共享 `teacher_cert` schema、`teacher-cert` bucket、redis db0 全程未触碰。

## 3. 门禁步骤 → 证据 → 结果（10/10 符合预期）

### 3.1 升级前快照（提交材料步骤 1）
- 旧 528B 视频、659B PDF、99B PNG 自 `e4f8228` 提取（`old-baseline/`，528B 视频 SHA-256 `f8ae9833…`），按旧固定 key（`teaching-video/demo-teaching-video.mp4` 等 4 个）上传隔离 bucket（`02`）。
- 旧 demo 实体 SQL 取自 `4996811`（仅把硬编码 bucket 字面量 `'teacher-cert'` 改为隔离 bucket 名），在候选 Flyway V1–V32 + testseed 之后注入。`03` 确认旧引用：旧固定 key、528 字节、假 MD5（`a1b2c3d4…` 等）、905/890/910 秒、`video_upload_session.object_key=NULL`。

### 3.2 候选首启与切换（步骤 2、3、5、6）
- `logs/envA-run1-demo-on.log`：四个内容版本对象逐一「已新建并验证」，随后「内容版本对象新建 4、一致跳过 0，实体 SQL 执行=成功」。
- `04`：旧固定 key 四个对象 ETag/大小/Content-Type 与升级前完全一致（528B 视频 ETag `f1b2a9fd…` 不变）；新版本 key 四个对象大小/Content-Type 正确。
- `05`：`file_object`、`process_material`、`exemption_material`、`video_review`、`video_upload_session` 全部引用版本 key；视频行 1,605,702 字节、`SHA256_TREE_V1`、`content_hash_verified=1`、H264、策略哈希 `450225d2…`、`JCODEC_PROCESS_V4`、900 秒；`video_upload_session.object_key/uploaded_bytes/duration_seconds` 收敛为版本 key/1,605,702/900；**旧 key/旧摘要/旧时长残留引用 0**；各表行数与升级前一致（收敛非新增）。
- `06`：读回对象本地复核 SHA-256 精确等于清单值（视频 `0a15c2…`、PDF `53aece…`、PNG `c589a0…`）；ffprobe 8.1.2 独立探测：h264、320x180、1fps、**900 帧、900.000000 秒**，ffmpeg 抽取首帧成功（`06b`）；对照组旧 528B 对象 ffprobe 无法解析 header（原 Major 的直接对照）。

### 3.3 幂等（步骤 8 前半，环境 A 二启）
- `logs/envA-run2-…log`：「新建 0、一致跳过 4，实体 SQL 执行=成功」。
- `07/08/09`：DB 快照与 run1 逐字段 diff 为空；8 个对象 Name/Size/ETag diff 为空，对象 Last-Modified 仍为各自首次写入时刻（未重传）。

### 3.4 预存污染拒绝（步骤 4，环境 B，两变体）
- 变体一（同长度错内容）：向 material 版本 key 预存 659B 随机内容（SHA-256 `9a8debeb…`，Content-Type 正确，`10`）。demo 启动：视频对象正常新建后，在 material 处失败关闭，进程退出、无 8080 监听（`logs/envB-run1-…log`，仅输出异常类别）。`11`：污染对象 ETag/Date/读回 SHA-256 与注入时完全一致（未被覆盖也未被“修复”）；**全部 demo 表 0 行（实体 SQL 未执行）**；仅遗留无引用视频版本对象（设计允许）。
- 变体二（错 Content-Type）：image 版本 key 预存正确 99 字节但 `application/octet-stream`（`12`）。启动在 image 处拒绝（video 一致跳过、material 重建后失败），进程退出；`13`：污染对象属性原样，demo 表仍 0 行。

### 3.5 实体 SQL 故障回滚（步骤 7，环境 C）
- 环境 C 重建 3.1 同款旧快照（`14`），并在隔离 schema 内创建 `notification` 表 BEFORE INSERT 触发器，对 demo 通知 id 段 SIGNAL 45000（故障注入不触碰候选构建产物）。
- demo 启动：四个版本对象新建成功后，实体脚本失败（`ScriptStatementFailedException` 类别），启动终止（`logs/envC-run1-…log`）。
- `15/16`：故障后 DB 快照与 `14` **diff 为空**——file_object 等前序已执行语句全部随事务回滚，引用完整回到旧固定 key/528/905'890'910/假 MD5；旧固定对象 ETag/Date 不变；仅遗留 4 个无引用版本对象。「新对象+旧元数据」半切换被事务原子性排除。

### 3.6 故障清除后恢复 + 再重启（步骤 8 全链，环境 C）
- DROP 触发器后两次启动均「新建 0、一致跳过 4，实体 SQL 执行=成功」（`logs/envC-run2/run3-…log`）；`17`：旧 key 残留引用 0、`file_object` 8 行、`video_review` 3 行均 900 秒——与环境 A 终态同构，行数稳定无重复。

### 3.7 浏览器与鉴权（步骤 9，环境 A run3）
- API 矩阵（`18`，含首登强制改密流程，密码仅在已销毁的隔离 schema 内生效）：
  - 正例 5：评审教师（ASSIGNED，含跨学院 9107）3 条、demo_student 本人（SELF）、教务员本院（COLLEGE）→ 均取得播放地址 + 水印 + expiry=300。
  - 反例：demo_student→他人 9104 / 跨学院 9107、教务员→跨学院 9107、test_student→无关联 9101 → **403 无权访问该视频且无 URL**；未认证 → **401**；篡改签名 → **403**。
  - Range `bytes=0-1023`/`800000-800999` → **206**（拖动传输层）。
- 浏览器（vite dev + Playwright，`19/20/21/22`）：评审教师登录后工作台见 2 条指派通知；播放 S9104 视频，`<video>` src 为版本 key 预签名 URL，duration=900.000；截图 19 首帧/前段帧清晰（帧内计数 0045/0900、播放器 0:43/15:00）且动态水印 overlay =「评审教师测试账号 test_review_teacher 2026-07-26T03:57:02.351361900Z」；currentTime=450 拖动后 onseeked 触发并续播至 0473/0900（截图 20），网络日志对 MinIO 为 206 Partial Content。
- 限时 URL（`18b` + `18` 末尾）：03:53:52Z 签发的 300 秒 URL 即时 GET=200；04:00:13Z（自然过期后）GET=**403 AccessDenied "Request has expired"**。未修改任何参数。

### 3.8 证据归档与清理（步骤 10）
- 证据归档于本目录；`23`：清理前逐项列出目标（3 schema、3 bucket、redis db13/14/15、`/tmp/phase53-runtime`、Playwright 会话工件），确认 `teacher_cert`/`teacher-cert`/db0 不在范围后执行，清理后复核仅剩既有共享资源；dev 容器恢复会话前的停止状态（容器/卷为既有共享资源，未删除）。

## 4. 与提交材料口径的差异披露

1. 旧 SQL 的 bucket 字面量按隔离环境改名（否则旧行指向不存在的桶，快照失真）；其余逐字节取自 `4996811`。
2. SQL 故障用隔离 schema 内触发器注入（提交材料未规定方式；不改动候选产物与共享库）。
3. 隔离环境整体销毁包含旧固定对象——「旧对象本阶段不删除」的语义适用于真实升级环境，隔离复现环境按步骤 10 整体丢弃。
4. `test_*` 账号首登改密仅存在于已 DROP 的隔离 schema，共享环境凭据无变化。

## 5. 结论

提交材料 §5 的 10 项用户动态门禁全部执行完毕，无一项出现与候选声明不符的行为；上述证据与 `b9abc6c` 候选同源（§1）。本记录连同证据目录供正式独立增量复核采信或复跑；在该报告 PASS 之前，Phase 53 与项目整体维持 CHANGES REQUESTED，不放行 Phase 44、merge、push、部署或发布。
