# 上线就绪计划（Demo → 正式上线过渡）

> **定位声明：本项目已从 demo 阶段进入"以正式上线、正常对外服务为唯一目标"的过渡期。**
> 此后所有工作的验收口径是"上线后真实师生规模（数千学生、截止日高并发）下正常服务"，而非"演示可用"。
> 本文档 = 全库上线就绪审计结论（2026-07-03，Opus 4.8 复核 + Sonnet 5 审计代理逐文件扫描，均有 file:line 依据）+ 分级修复计划。
> 执行模式：**Opus 4.8 亲自做关键/安全/架构项，Sonnet 5 代理做机械铺开项**；每项完成按门禁流程验证合并。

---

## 0. 已在 Phase 36 顺手落地的
- ✅ Spring multipart 上限（原 P0-3）：`application.yml` 增 `max-file-size: 100MB / max-request-size: 120MB`——此前框架默认 1MB/10MB，扫描件/名册 Excel 超 1MB 直接被拒，业务上限（50MB）形同虚设。
- ✅ 审计日志去噪（用户反馈#3）：删 23 处冗余 `@AuditLog`（分片级/薄厚双记/纯读），审计只留有价值记录；`mvn verify` 83/83 绿。

## 1. P0 —— 上线阻断项（不修不能上线）
| # | 问题 | 位置 | 危害 | 修复 | 归属 |
|---|---|---|---|---|---|
| P0-1 | **文件预签名 IDOR**：`presignedGet` 只查文件存在，**无属主/数据范围校验**，expiry 客户端任填无上限 | `FileController.java:50-54`、`FileServiceImpl.java:62-77` | 任何登录学生可凭任意 fileId 下载他人证件照/视频/材料，还能签发超长期链接 | 按文件所属业务记录做权限校验（复用 @DataScope 口径）；服务端强制 expiry 上限（如 ≤15min） | **Opus（安全关键）** |
| P0-2 | **批量下载整包进堆**：`batchDownload` 无行数上限，全部文件读进 `ByteArrayOutputStream` 再返回 byte[] | `ProcessMaterialServiceImpl.java:246-277` | 一次"下载全院材料"即可 OOM（-Xmx1024m） | zip 流式写响应 + 批量上限/分批 | Opus |
| P0-3 | ~~multipart 上限缺失~~ | ~~application.yml~~ | — | **已修（见 §0）** | 完成 |
| P0-4 | **无 prod profile，Swagger/Knife4j 对公网全开**：`SecurityConfig` permitAll `/doc.html`/`/v3/api-docs`，且无 `application-prod.yml`（compose 却默认 `SPRING_PROFILES_ACTIVE=prod`） | `application.yml:17-20`、`SecurityConfig.java:61`、`docker-compose.yml:56` | 生产接口文档+试调界面公开 | 新增 `application-prod.yml`：关 knife4j/springdoc；prod 下移除文档路径放行 | Opus |
| P0-5 | **应用用 root 连 MySQL** | `docker-compose.yml:68-69` | 任一注入/依赖沦陷=整库沦陷 | 建 `teacher_cert` 专用最小权限账号 | Sonnet（模板化） |
| P0-6 | **备份是"演练记录"非真备份**：triggerBackup 仅插一行 COMPLETED；全库 0 处 `@Scheduled`；运维手册命令指向 dev 容器/密码 | `SystemManagementServiceImpl.java:132-146`、`docs/备份与恢复手册.md` | 真事故时无备份可恢复、手册照抄即失败 | 定时 mysqldump+MinIO mirror 脚本（宿主 cron 或容器 sidecar）；手册改指 prod 栈；backup_record 关联真实产物 | Opus 设计 + Sonnet 脚本 |

## 2. P1 —— 上线前应修（规模/一致性风险）
| # | 问题 | 位置 | 修复 | 归属 |
|---|---|---|---|---|
| P1-1 | **全站假分页**：11 文件 14 处 `new PageResult<>(records.size(), records)` 全量返回，前端客户端分页；千级学生后列表接口变慢、内存放大 | student/training/material/exemption/cert/test/video/exchange/security/notification/system 各 ServiceImpl | 切 MyBatis-Plus `selectPage` 真分页（契约已有 total 字段，前端 DataPanel 已用后端 total——改动集中在后端 + 前端各页把筛选改为服务端参数） | **Sonnet 铺开 + Opus 定契约与抽查** |
| P1-2 | **视频上传全量过应用服务器** + 512KB 分片致 composeObject 永远失败走"逐片拉回重传"慢路径 | `VideoReviewController`、`VideoReviewServiceImpl:211-254,750`、前端 chunkSize | 见 §4 上传直传方案（分两阶段） | Opus |
| P1-3 | 统计模块 Java 全表聚合（8 处全表 select 后内存 group） | `StatsServiceImpl.java:247-527` | 下推 SQL `GROUP BY/COUNT` | Sonnet 铺开 + Opus 抽查 |
| P1-4 | 万行 Excel 导入同步逐行处理 vs 前端 30s 全局超时 → 客户端先超时、疑似失败重复提交 | `ExchangeServiceImpl.java:230-271`、`request.ts:13` | 导入改异步（返回批次号轮询）或该请求单独长超时 + 幂等防重 | Opus 设计 |
| P1-5 | 通知扇出逐条 INSERT | `ReviewNotificationHelper.java:100-105` | 批量插入 | Sonnet |
| P1-6 | N+1：`attachmentRows` 循环 selectById | `ExchangeServiceImpl.java:952-985` | 改 selectBatchIds | Sonnet |
| P1-7 | CORS 生产域名未配置且无文档，默认仅 localhost | `CorsConfig.java:27`、compose/.env.example | compose+.env.example 增 `CORS_ALLOWED_ORIGINS` 并写入部署文档 | Sonnet |
| P1-8 | 学生初始密码=身份证后 6 位（可预测；已有强制首登改密+锁定缓解，但存在抢先注册窗口） | `StudentServiceImpl.java:351-361` | 初始密码加盐随机后缀经名册下发，或首登绑定学号+证件双因子核验 | Opus 定策略 |
| P1-9 | 全库无任何定时任务：上传残片/孤儿对象、audit_log/notification 只增不清 | 全局 | `@EnableScheduling`：残片清理、MinIO 未完成分片 abort（生命周期规则）、日志归档策略 | Sonnet 按 Opus 规格 |
| P1-10 | 兜底异常返回 HTTP 200（监控/告警失明） | `GlobalExceptionHandler.java:46-50` | 未知异常返回 500（保留 code/msg 结构；前端拦截器兼容验证） | Opus（契约敏感） |

## 3. P2 —— 上线后迭代
- compose 无资源限额（`deploy.resources`）；Hikari 未调优（按并发压测定 pool）；
- 前端：localStorage token 的 XSS 面（已有 TTL 缓解）、全局 errorHandler/router.onError 兜底、请求重试；
- naive-ui 全量注册（已 manualChunks 隔离缓存，可再按需引入）；echarts 按需引入（仅 bar/pie）；
- 播放/下载走 CDN；MinIO 纠删码集群或云对象存储。

## 4. 视频/文件上传上线方案（承接"上传慢"结论，两阶段）
**阶段1（过 app 优化，内测够用）**：前端分片 512KB→8MB + 并发 4–6 + `crypto.subtle` 哈希（Worker）；后端 ≥5MB 走 `composeObject` 服务端合并（修掉必然降级的慢路径）。
**阶段2（上线本体，字节不经 app）**：`init` 由后端（AWS S3 SDK `S3Presigner` 指向 MinIO）发 **multipart 预签名 UploadPart URL 列表** → 浏览器并发直传 MinIO → `complete` 交回 `[partNumber, ETag]` 由后端 `CompleteMultipartUpload` 定稿+落库+触发校验；预签名短时效+限型限长；MinIO 生命周期自动 abort 未完成 multipart；配套异步转码（FFmpeg 统一 H.264/AAC+缩略图+服务端真实时长）与削峰限流。
> 秒传/断点续传语义保留：init 前指纹查重（大小+分块采样哈希，Worker 计算）；进度=已完成分片集。

## 5. 执行序（建议）
1. **Phase 37（安全急修，Opus）**：P0-1 IDOR + P0-4 prod profile/文档关闭 + P1-10 异常状态码 → 全量 verify + 活体渗透自测。
2. **Phase 38（容量正确性）**：P1-1 真分页（Sonnet 铺开 11 文件 + 前端参数化，Opus 契约与门禁）+ P0-2 流式 zip + P1-3 SQL 聚合。
3. **Phase 39（上传直传阶段2 + P1-4 异步导入）**：契约改造，Opus 主刀。
4. **Phase 40（运维基建）**：P0-5/P0-6/P1-7/P1-9（账号、真备份、CORS、定时清理），Sonnet 脚本化 + Opus 审。
5. 上线冒烟：`docker compose up -d --build` 全栈 + README 双端核对 + 压测（列表/导入/上传/统计并发基线）。

## 6. 门禁口径（不变 + 加严）
- 每阶段：`mvn -B -ntp verify` 全绿 + `type-check/build` 绿 + 活体多角色走查零 403/500；涉契约改动加"前端旧行为兼容"检查。
- 新增：安全项修复必须附**攻击复现→修复后阻断**的活体证据（如 IDOR：学生 token 取他人 fileId 必须 403）。
- git 仍本地私有（无 remote/不 push），上线部署另行导出。
