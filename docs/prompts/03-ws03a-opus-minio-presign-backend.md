# WS-3a · MinIO 预签名·后端/架构（直传/直下） — 提示词（Opus 4.8）

你是执行 **WS-3 后端/架构**的 opus（需求① MinIO 独立文件服务器）。对应 `docs/audit-remediation-plan.md` §3 WS-3（C1/C2）。**codex 并行做 C3/C4**（`04-ws03b-codex-minio-config-frontend.md`）——你产出后端契约，codex 接线；**C3 内外双端点配置需先打通**，你和 codex 约好 endpoint 契约再并进。

## 目标
MinIO 作为**独立文件服务器**：浏览器**直接**与 MinIO 传字节（上传/下载/播放**不经应用服务器**），应用只签发**短时效预签名 URL** + 做最终定稿/落库/鉴权。完成之前推迟的"P1-2 阶段2 直传"。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§3 WS-3**；`git remote -v` 空 → `git switch -c feature/ws03-minio-presign-backend`。
- **顺序红线**：本 WS **先于 WS-9**（WS-9 拆 VideoReviewServiceImpl，需等你把直传逻辑挪出后再拆）。
- **分小步**：**C2（下载直下）多数已实现、剩余小（主要收口证书文件）→ 真正工作与风险在 C1（上传直传）**。仍分小步、每步 verify + 活体。
- **保留回退**：保住 Phase 49 的服务端合并语义作为回退/兜底。

## 任务
### C1 预签名上传（分片直传）
- **⚠️ 新增依赖**：仓库当前**只有 `io.minio:minio:8.5.12`**（原生 SDK，无法逐片预签 multipart UploadPart URL）→ C1 须引入 **`software.amazon.awssdk:s3` v2（`S3Presigner`）**指向 MinIO（S3 兼容）；与 codex 协调版本/BOM、写进 DEVLOG。
- `init`：后端用 `S3Presigner`（读 `minio.public-endpoint` 出 host）签发 **multipart UploadPart 预签名 URL 列表** → 浏览器并发直传 → `complete`：回传 `[partNumber, ETag]`，后端 `CompleteMultipartUpload` 定稿 + 落 `file_object`/`video_*` + 触发既有校验。
- 改造 `platform-file/.../service/FileService.java`(+`FileServiceImpl.java`，`upload:~32`/`presignedGet:~65`) 与 `platform-business/.../video/service/impl/VideoReviewServiceImpl.java`（`uploadChunk:~171` 现逐片 `minioClient.putObject`、`merge:~223`、`composeServerSide:~831`、`registerComposedFile:~792`）的**分片直传**路径——替换"字节过应用"的分片/合并；**`FileService.upload(InputStream)` 保留**给服务端/小文件单文件上传（见 C2/验收 IT 注意）。
- 预签名**短时效 + 限型限长 + 限 bucket/key 前缀**。

### C2 预签名下载/预览/播放（**多数已实现，先摸清再动**）
- **⚠️ 现状核实**：材料预览（`ProcessMaterialServiceImpl:~137`）、免考预览（`ExemptionServiceImpl:~213`）、**视频播放**（`VideoReviewServiceImpl.playback:~523→presignedGet:531`，`GET /reviews/{id}/play`）**已经**走 `presignedGet` 浏览器直取（Phase 37a IDOR 修复模式：删通用 `/file/{id}/url`、改由带范围校验的业务端点内部 presign）→ **无需重做**。
- **真正剩余的过应用单对象**：**证书文件**（`CertificateServiceImpl` 未用 `presignedGet`）+ 任何仍流式返回的**单个已存 MinIO 对象** → 改签预签名 GET。
- **明确排除**：ZIP 打包下载、生成式 Excel/证书导出（无单一 objectKey、无法一个预签名 GET，合法保持过应用）。
- **鉴权与审计边界不破**：签发前校验数据范围/权限（尤其敏感文件），TTL 短。

## 与 codex 的接口约定（写进 DEVLOG 供 codex 对齐）
- 后端签名必须用 **`minio.public-endpoint`（浏览器可达）** 生成 host，而应用内部访问 MinIO 用 `minio.endpoint`——这两个 property 由 codex 在 C3 落 `MinioProperties`/yml，你的 `S3Presigner` 需读取 public host 出签。
- 明确 init/complete 的**请求/响应契约**（字段名、分片大小、ETag 回传格式）给 codex 前端接线。
- **迁移**：**扩展现有 `video_upload_session`（V13，已含 `upload_id`/`status`、`video_upload_chunk.object_key`）**承载 S3 multipart uploadId / part-ETag / 新状态——**别新建重复的"直传会话表"**；`file_object`（V1）**无 status 列**故那是真新列。编号取磁盘 max+1（⚠️ **WS-3/8（必）+ WS-2（若走迁移）都新增迁移、别都假定自己是 V27**，先 `ls .../db/migration | sort -V | tail -1` 确认，主控合并时顺延 V27/V28/V29）。

## 验收（活体）
- 学生**分片直传**材料/视频、评审看/播视频、下载**已存单对象**：字节**不经应用**（抓包/日志证明请求打到 MinIO **public endpoint**）。**导出（Excel/ZIP 打包）合法保持过应用**——不列入"字节不经应用"验收。
- 预签名**过期后 URL 失效**；无敏感权限用户**签不出**敏感文件 URL（复现被拒）。
- **⚠️ IT 契约会变（"契约不破"是错的，别照抄）**：上传/合并的 **HTTP 契约必然改**——`Phase7VideoReviewIT` 的 `uploadChunk`(`:~845`)/`merge` helper **须改写**为 init→**presigned PUT 直传 MinIO**→complete(`[partNumber,ETag]`)；其对合并对象的断言（`stat.size`==原字节、`contentType=="video/mp4"`、`etag` 含 `-`、字节逐一相等，`:~768-776`）在 `CompleteMultipartUpload` 定稿为**单个 video/mp4、字节不变**时仍成立、须保住。`Phase5MaterialIT`/`Phase6ExemptionIT` 的**单文件多段上传**（走 `FileService.upload(InputStream)`）与 `Phase10ExchangeIT` 的**生成式导出**（读 HTTP body 工作簿字节）**保持过应用不改**。验收 = **IT 重写到新直传契约后 `mvn verify` 全绿（现 119 基线 + 改写/新增的直传 IT）、被测语义（秒传/续传/校验/合并对象正确性）不破**。
- **可用真 MinIO**：verify 无 testcontainers、MinIO 是活体外部依赖（docker-compose.dev `minio`，Phase7 已做真 `statObject`/`getObject`）→ 重写的直传 IT **可对该 MinIO 做真 presigned PUT，别 mock**。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行。
2. `mvn -B -ntp clean verify` 全绿；若已联动前端另跑 `cd frontend && npm run type-check && npm run build`。
3. `DEVLOG.md`（倒序，含给 codex 的契约）+ `docs/launch-readiness-plan.md` §11 追加 WS-3 后端条目。
4. **单 commit**（`feature/ws03-minio-presign-backend`）→ **STOP** 交主控复核。**禁止 merge / push**。
