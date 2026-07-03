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

---

## 7. 二轮深度扫描（2026-07-03，6 维并行审计，均 file:line 依据）
> 覆盖：并发/事务、规模/性能、证书/导入正确性、领域规则合规、前端、部署运维（部署维仍在收尾，见 §7.8）。下列均为 §1–§3 之外的**新发现**，去重后按主题+分级。**好消息在 §7.5/§7.9**：领域业务规则 11/12 后端强校验、无缺失索引、证书序列号并发安全、无 SQL 注入。

### 7.1 并发一致性 —— 系统性无乐观锁（最重要新发现）P0
根因：`platform-common/.../entity/BaseEntity.java` **无 `@Version` 乐观锁列**，所有 mapper 是裸 `BaseMapper`。所有"状态流转"都是 `selectById→Java 查状态→updateById(id)`，**无版本、无 SELECT FOR UPDATE、无 UPDATE...WHERE status=** 前置条件 → **TOCTOU 竞态**（双击/重复请求/双评审都过守卫都提交，后写覆盖先写，PASS 可覆盖 REJECT）。
- 涉及：`StudentServiceImpl:139-220`、`TrainingProfileServiceImpl:116-197`、`ProcessMaterialServiceImpl:133-222`、`ExemptionServiceImpl:210-307`、证书 5 流转 `CertificateServiceImpl:137-268`、视频 `VideoReviewServiceImpl` submitScore:356/settle:824/thirdReview:378/arbitrate:402/confirm:423、`ExchangeServiceImpl.confirmImport:230-271`。
- **缺唯一约束致并发重复**：证书 `(student_id, assessment_year)` 仅非唯一索引（`V15:31-36`）→ 并发生成两张有效证书；`student.id_card_no` 非唯一（`V9:31`）→ 并发建同证件号两人；`training_profile` 导入无唯一码 → confirmImport 双确认产生重复培养行。
- **video merge() 无幂等守卫**（`VideoReviewServiceImpl:211-254`，不同于 uploadChunk 有 status 守卫）→ 重试产生重复 `file_object` 行 + MinIO 孤儿对象 + 重指 videoFileId。
- **video settle 丢失**（`:824-848`）：两评审并发提交最后一分，REPEATABLE_READ 下各自快照只见自己那条 → 都不触发结算 → review 卡在 REVIEWING 且无法重提，需人工。
- **秒传越权**：`FileServiceImpl.getByMd5` 无 bizType/属主校验（`VideoReviewServiceImpl:125-138`）→ 可命中挂载任意他人文件。
→ 修复主线：给 BaseEntity 加 `@Version`（系统性解大部分）+ 关键流转改条件 UPDATE/行锁 + 补 `(student_id,assessment_year)` 等唯一约束。

### 7.2 事务边界与资源 P0/P1
- **P0 MinIO I/O 在 `@Transactional` 内**（`VideoReviewServiceImpl` uploadChunk:162,178 / merge:211,236）→ MinIO 网络往返期间**占用 DB 连接**；截止日并发大上传耗尽 Hikari 连接池（默认 10）→ **全站 DB 阻塞=总瘫**。这是运维上最危险的一条。修：MinIO 调用移出事务，DB 元数据单独短事务。
- **P1 审计/通知写入吞异常**（`AuditLogServiceImpl.record:22-41`、`ReviewNotificationHelper.notifyXxx` 只 log.warn）→ 在 `@Transactional(rollbackFor=Exception)` 内失败**不触发回滚** → 状态改了但无审计（正是要防的不一致）。
- **P1 `ExchangeServiceImpl.confirmImport` 无 `@Transactional`**（`:229-271`）：状态末尾才翻转，双确认全量重导。
- **P1 `FileServiceImpl.upload` InputStream 未关闭**（`:32-59`，无 try-with-resources）→ 高并发泄漏流/socket。

### 7.3 规模/性能 P1（缺失索引=无，见 §7.9）
- **N+1 `toVO`/导出（5 处）**：`VideoReviewServiceImpl.toVO:1011,1034,1060`（三重嵌套）、`TrainingProfileServiceImpl.toVO:329`、`AbilityTestResultServiceImpl.toVO:227,233`、`ExchangeServiceImpl.rowFromCertificate:836-841`/`fullReviewRows:894-899`。正确批量范式在 `ExemptionServiceImpl.toVO:384`/`ProcessMaterialServiceImpl:379` 已有——照抄 `.in()`+Map。
- **`StatsServiceImpl.anomalyReport:319-345`**：对全体学生逐个跑创建期校验器（`TrainingLinkValidator` 每学生 ~7 查询）→ 一次 HTTP 数万次串行查询。
- **POI 全 DOM 进堆 + 无界 rowset**（`ExchangeServiceImpl:159` `file.getBytes()`+`XSSFWorkbook`；导出 `selectCertificates:812` 无界）→ 大批次 OOM。改 SXSSF 流式 + 分页。
- **prevalidate 每行重查字典**（`:147-211` 单大事务，每行 validateSchool/Id/IdentityType/certCode 无批内缓存）→ 一次导入数万次字典查询。
- **非 sargable `%kw%` 前导通配 LIKE 全表扫**（8 表）：Student:74 / Training:230 / Certificate:289 / Material:316 / Exemption:371 / AbilityTest:213 / Video:1003 / Exchange:804。
- **参考数据/权限未缓存**：`ParamServiceImpl` 每 getX 一次 DB（证书生成读 ~6 参数/请求）；`DataScopeServiceImpl.resolve` 每次权限判定最多 3 查询无 memo；`RegionServiceImpl` 全无缓存且 `path()` while 循环逐级查；`OrganizationServiceImpl` majors 列表 ~3N+1（toMajorVO:453 + 每行 3 次字典 selectList）；`NotificationServiceImpl.list:55` 无界。
- **`AuditLogAspect:32-46` 同步阻塞写**（非 @Async），每个 @AuditLog 变更端点多付一次阻塞写。

### 7.4 证书/导入领域正确性 P1
- **REISSUED 从不落库（死枚举）**（`CertificateServiceImpl.reissue:215-218` 只 setLocked，never setStatus）→ 原证保持 VOIDED → **作废证书可被反复重开**（无界链）；审计记录了没发生的流转。
- **导入静默改 voided/archived 证书**（`certificateByStudentYear:1202` 无状态过滤，`applyCertificate:717-740` 覆盖 cert_no/validUntil/issuer）——与 `correct()` 的守卫矛盾。
- **往返列复用坏账**：导出把 `status` 写入"备注"列（`:865`），导入把该列当学院id `parseLong`（`:1058`）→ **重导入导出文件必坏**（学院识别失败）。
- **导入 cert_no 不占 `cert_sequence`** → 与自动生成永久撞号，`generate` 撞号回滚又不推进序列 → "证书编号已存在请重试"**永远失败**，需手工改库。
- **导入证书 status=ISSUED 但 issueDate 从不设**（`applyCertificate` 只设 issuer/validUntil），且无补设路径（issue 要 GENERATED、correct 无 issueDate 字段）。
- **P2** `correct()` 接受任意 18 位 cert_no，不校验内嵌码与学生学段/层次一致。

### 7.5 领域规则合规 —— 11/12 后端强校验（核心业务是对的）
逐条 ENFORCED（有 file:line 证据）：身份证 4 类型+18 位+MOD11 校验（`IdCardValidator`，checksum 默认关=决策#16）、证书号 18 位组成（`certCode/nextCertNo`，码取自 dict ext_json 与附录 A 逐字对齐）、有效期 6·30/12·31+3 年、**学段↔学科级联**（`TrainingLinkValidator.requireSubject` save 时无条件校验）、**培养目标↔实习地点级联**（含 V23 中职→其他修正）、免考多科独立+每科佐证+二级+应考口径、学年过滤一致（各表 assessment_year NOT NULL）、**RBAC 矩阵与 V20-23 授权完全一致**（逐角色核对）、测试仅导入+确认、视频退回可改（`reuploadable` 状态集正确）、字典枚举 12 类逐字完整。
- **唯一真 bug — Rule 11 过程性材料合格判定（P1，近 P0）**：`ProcessMaterialServiceImpl.categoryStatus:363-372` 用**全历史行按当前状态**计数，`passed = passedCount>0 && failedCount==0`；而 FAILED 是终态（`editable()` 排除 FAILED+locked=1，delete/replace 都被 `ensureEditable:445` 挡，控制器**无管理员强删/override**）→ 一条 FAILED 永久钉住 `failedCount≥1` → 该类别再也无法 passed，即便重传新材料通过 → **证书合格永久卡死且无恢复路径**。若评审误用 FAIL（应 退回 REJECT）则学生永久不可纠正地不合格。修：合格判定只算每类**最新/有效**行，或加管理员 override。

### 7.6 前端 P1/P2
- **P1 批量下载永远失败**：`api/material.ts:103` 泛型写成 `request.post<Blob>`（只设第一个泛型），拦截器已返回 `response.data`=Blob 本身，消费端 `MaterialManageView:270` 又取 `res.data`=undefined → `createObjectURL(undefined)` 必抛 → 所有有 `material:batchDownload` 的角色点批量下载**必报失败**。修：`request.post<unknown, Blob>` + 直接用 res（对齐 exchange.ts/stats.ts）。
- **P2 Blob 端点错误显示英文 axios 文案**（`request.ts:36-60` 对 blob 响应不解析 `{code,msg}`）→ 模板下载/导出失败显示 "Request failed with status code 500"。
- **P2 未捕获的 `loadOptions()` 白屏**：`CertificateManageView:140`/`TestResultManageView:127` 无 try/catch 且 onMounted 先 await 它再 loadRecords → 字典失败则列表不加载、空页无提示。
- **P2 免考申请非原子**：`ExemptionDrawer:104-116` 先建条目再 Promise.all 传佐证；某佐证失败则条目已存但报"保存失败"，留下缺附件的孤儿免考记录。
- **P2 合格判定取任意学生**：`MaterialManageView:241` 评审无选中学生时静默用列表第一行的学生，弹窗显示不知是谁的结果。
- **P2 死代码/重复**：`v-perm` 指令注册但零使用；`batchCreateStudents` 无调用方；saveBlob/saveStatsBlob/内联 三份重复；死权限 `material:view`/`student:export`/`test:edit`；404 路由无 title。

### 7.7 校验 fail-open P2
- `StudentStatus/TrainingStatus/MaterialStatus.of` 未知值 `orElse(DRAFT)` → 损坏状态静默变最可编辑草稿（fail-open）；`ExemptionStatus.of` 却 `orElseThrow`（不一致）。
- `BirthDateValidator` 只校验月 1-12 日 1-31 → 接受 2023-02-30。
- training/exemption 双提交唯一约束冲突未捕获 → 裸 500（非 BizException）。

### 7.9 正面确认（审计明确判定"无问题/已正确"，避免误改）
- **无缺失索引**：通读 V1–V23，material/exemption/video/certificate/training/user/student 及参考表热点列索引齐全（仅 certificate 有一处近重复复合索引可删）。
- **证书序列号并发安全**：`nextSequence` 用 `selectByScopeKeyForUpdate`(SELECT FOR UPDATE)+唯一约束+DuplicateKey 捕获，非 MAX+1 竞态。
- **无 SQL 注入**：全 LambdaQueryWrapper，无字符串拼接/`${}`；DataScope 建 jsqlparser Expression 对象。
- **V-01..V-13 校验全部接线**（无 stub return true）；导入 rollback 覆盖与导入写入一致（无残留）。
- **前端**：25 个 Drawer 提交前均 validate()/手工必填校验，按钮均 :loading（无重复提交）；权限串与 V8/V20/V21 授权无幽灵项；深链接/刷新/路由守卫正确；无 console/TODO/mock 残留。

### 7.10 授权 · 会话 · RBAC 安全（好消息：核心 IDOR 面干净）
**执行结论**：逐个核对 23 个控制器——**没有发现新的、普通学院用户可轻易利用的业务 IDOR**：每个 by-id/detail/写路径都被 `@DataScope`（拦截器）或服务层 `ensureCanRead/ensureCanWrite*` 之一守住（逐模块验证）。写侧学院范围**确系服务端从实体取**（非信任请求）。@pms 无通配绕过、JWT 算法安全、登录锁定正确、mustChangePwd 门有效、无 SQL 注入。真正的新风险在**会话/令牌生命周期**与**管理端 RBAC 写授权**：
- **P0（补，第 2 个 IDOR）导入批次越权**：`ExchangeServiceImpl.requireBatch:1297` 只查存在；`confirmImport:230`/`rollback:275`/`errorReport:214`/`batches() 列表:313` **均无 operator/college 过滤** → COLLEGE_CLERK 可枚举/确认/回滚/下载**任意学院**批次的错误报告（含文件名、操作人、学生 PII）。`StatsServiceImpl.batchVisible:549` 证明此处本应 scope，Exchange 漏了。`import_export_batch` 用 operatorId+scopeJson（无 college_id），需手动强制。
- **P1 登出是空操作、令牌不可撤销**：`AuthController.logout:48` 什么都不做；无 Redis/JWT 黑名单 → 泄露的 access token 存活满 1h、refresh 满 7 天。
- **P1 改密/重置密码不失效既有令牌**：`AuthService.changePassword:83`、`SecurityAdminServiceImpl.resetPassword:127` 只改 hash；令牌无 passwordChangedAt/版本 → 被盗后改密，旧令牌仍有效到自然过期。
- **P1 refresh 令牌可重放不轮换**（`AuthService.refresh:74`，7 天无重用检测）。
- **P1 RBAC 管理写无 scope、无权限天花板（自提权）**：`SystemSecurityController:51-152` 全部管理写只有 @PreAuthorize、无 @DataScope；`assignRolePermissions:216`/`assignUserRoles:260` 接受任意 permissionId（含 SYSTEM/SCHOOL 范围）/roleId → 持 `system:role:manage` 者可给自己/任何人授满 SYS_ADMIN 等价权（无"不可编辑更高权用户"守卫）。种子里仅 SYS_ADMIN@SYSTEM 持这些权，故**当前潜伏**，但是设计级授权缺口；若哪天给 COLLEGE 范围自定义角色发了这权，即可跨学院增删改用户/重置密码。
- **P2 验证码形同虚设**：`CaptchaService.svg:55` 把 4 字符明文当可选中 `<text>` 渲染，脚本直接从 SVG 读出（无需 OCR）——`/auth/login` 无真正防自动化（一次性+TTL 是对的）。
- **P2** 管理员建的 STAFF 账号固定初始密码 `ChangeMe123!`（`SecurityAdminServiceImpl:61`，env 未设时的硬编码兜底）；RBAC 关系表 PK 用 `id*1000`（snowflake ~19 位）**long 溢出**→ 可能 PK 碰撞覆盖他人授权行（`:155,220,268`）；`plainIdCard` 忽略自己的 `plain` 参数无条件返回明文（`StudentController:129`）；多个 detail-by-id 仅靠服务层手写校验（模式脆弱，将来漏写即 IDOR）。
- **P2（数据范围）** `StatsServiceImpl.batchVisible:556` 用 `scopeJson.contains(collegeId)` 子串匹配 → collegeId `1` 匹配 `10/11/100`（跨学院泄露）；SELF 范围统计恒空（学生看不到本人统计，`:440`）；`@DataScope` 表未在 TABLE_RULES 则**静默不过滤**（opt-in 非默认拒绝，未来给带属主新表加 @DataScope 漏配则泄露）。

### 7.11 部署 · 运维 · 依赖 · TLS
- **P0 Spring Boot 3.2.11 已 EOL + 命中方法级鉴权 CVE 影响面**（`pom.xml:10`）：3.2.x 2024-11 停 OSS 支持、2025-12 全面 EOL；传递 Spring Security 6.2.x，2025 年鉴权绕过族（CVE-2025-41249/41248/41232）正打 `@EnableMethodSecurity`+`@PreAuthorize`——**本平台 RBAC 的根基**。配置无法缓解，**上线前须升到受支持的 3.3+/3.4+**。
- **P0 全链路无 TLS**：`nginx.conf:1` 只听 `:80`，仓库无 443/证书/HSTS；`docker-compose.yml:107` 前端 :80 直发主机。登录密码、`Authorization: Bearer` JWT、身份证 PII **明文传输**——面向数千学生 PII 的公网服务不可接受。
- **P1 无 `.dockerignore` → 真实 `.env` 被打进镜像层**（后端 `Dockerfile:17` `COPY . .`，README:49 让运维在构建上下文放含生产密码的 `.env`）→ 密钥随镜像/推送泄露；还带入 `.git/target/*.log`。
- **P1 生产把 DB/Redis/MinIO 端口发布到主机 + Redis 无密码**（`docker-compose.yml:14/29/47`，`:28` redis 无 requirepass）→ 主机网可达=无认证 Redis（存登录锁/验证码/会话）。后端只需内部 DNS，无需 publish。
- **P1 nginx `/api` 无 proxy 超时**（默认 60s）→ 万行导入/大视频上传 502/504，与 `client_max_body_size 2048m` 自相矛盾（`nginx.conf:8-17`）。
- **P1 nginx 无安全响应头**（无 X-Frame-Options/CSP/X-Content-Type-Options/HSTS）。
- **P1 健康端点是"静态谎言"**：`HealthController:23` 无条件返回 UP，却被用作容器 healthcheck+依赖门（`compose:94,108`）→ MySQL/Redis/MinIO 挂了后端仍"健康"，无重启/就绪信号。无 actuator/可观测性（无 metrics/trace）。
- **P1 容器日志无上限无轮转**（compose 无 logging 块→ json-file 无 max-size）→ 磁盘涨满；无 prod 日志配置。
- **P1 生产库被种入已知口令测试账号**（README:61 + V8/V13 seed `test_*` 全 `ChangeMe123!`）；**容器以 root 运行**（两个 Dockerfile 无 USER）。
- **P2**：镜像 `:latest` 不定；dev/prod MySQL 漂移（8.0 vs 8.4，迁移只在 8.0 验证）；Flyway prod 未显式 `clean-disabled`/`baseline-on-migrate`（迁移本身无破坏性 DDL，已确认）；无优雅停机；JVM 无 `-XX:+ExitOnOutOfMemoryError`；nginx 无 gzip/缓存头；MyBatis-Plus 3.5.7 偏旧；dev compose 无 healthcheck/depends_on（"后端早于 mysql"竞态在 dev 仍可能，prod 已正确处理）。
- **发现的密钥（均 dev/占位，无生产真密钥入库；`.env` 已 gitignore）**：`docker-compose.dev.yml:8` root123、`:37` minioadmin123；`.env.example:22` 弱 JWT 占位；**`.github/workflows/ci.yml:43` 提交了一个真 64-hex JWT_SECRET 明文**；~14 个 IT + AGENTS.md/HANDOFF.md 里的 `0123...` JWT 示例；备份手册硬编码 dev 口令。
- **数据持久化**：命名卷齐全（`mysql/redis/minio-data`）——**无重启即丢数据的 bug（好）**；残留风险：单主机 local 驱动 + 假备份 + 无 @Scheduled → 主机/磁盘故障全丢无异地副本；`down -v` 永久清空业务数据/附件/视频；MinIO 版本化未在 compose 启用 → 覆盖/删除不可恢复。
- **正面**：prod compose `depends_on: service_healthy`（解决了"后端早于 mysql"）、多阶段构建、`JwtService` 对空/短密钥 fail-fast、无破坏性迁移。

### 7.12 二轮新增"上线阻断"P0 汇总（合入 §1 优先级）
1. **P0-7 导入批次越权 IDOR**（§7.10）——与 P0-1 文件 IDOR 同级，学院教务员可跨学院操作批次。
2. **P0-8 Spring Boot EOL + 方法鉴权 CVE**（§7.11）——升级 3.3+/3.4，动摇整个 RBAC。
3. **P0-9 无 TLS**（§7.11）——PII/JWT/密码明文。
4. **P0-10 并发无乐观锁**（§7.1）——评审/证书/视频状态可被竞态覆盖（PASS 覆盖 REJECT），加 `@Version`+条件更新+缺失唯一约束。
5. **P0-11 MinIO I/O 在事务内**（§7.2）——大上传耗尽连接池致全站瘫。
> 执行序相应调整：**Phase 37 拆成 37a 安全急修（P0-1/7/9 授权+TLS+IDOR+会话）与 37b 依赖升级（P0-8 Spring Boot）**；并发无锁（P0-10）与 MinIO 出事务（P0-11）并入 Phase 38/39。前端"批量下载永远失败"（§7.6 P1）建议随手修（纯前端泛型笔误）。

---

## 8. 活体渗透/回归测试结果（2026-07-04，栈起，脚本实测，非静态推断）
以 demo 数据实测，测完已还原。**两个 P0 现场证实可利用**，多条正面项现场证实有效，并纠正 1 处审计过报。

### 8.1 🔴 P0-1 文件预签名 IDOR —— 现场证实可利用（最高优先修）
- 手法：`test_student`(学院A) 本人上传一份材料 → 得 `file_id`；**`test_student_b`(学院B 的学生)** 携自己 token 调 `GET /api/file/{file_id}/url` → **HTTP200 拿到预签名 URL，并实际下载到文件字节** `%PDF FAKE private material of student 9001 (college A)`。
- 结论：**任一登录用户可凭 file_id 下载任意他人（跨学院跨学生）材料/证件/视频**。根因 `FileController:50 presignedUrl` 仅 `@PreAuthorize("isAuthenticated()")`、无 @DataScope、无属主校验。
- 修复验收：修后同一手法必须 **403**。
- **纠正审计**：过长有效期 `expiry=10年` 实测**被拒**（后端有上限）——§7.10 里"expiry 无上限"一项**不成立**，删除该子项，只保留"无属主校验"。

### 8.2 🔴 P0-10 并发无乐观锁 —— 现场证实竞态
- 手法：6 线程用 barrier 同时对同一 `FIRST_REVIEW` 学生发 `POST /student/{id}/first-review` PASS → **6 次全部 HTTP200 成功**，`audit_log` 产生 **6 条**同一逻辑审核记录，`first_review_comment` 最终为 `race-2`（last-writer-wins，非确定）。
- 结论：状态流转无 `@Version`/无条件更新，重复/并发提交都过守卫都提交 → 若一 PASS 一 REJECT 并发，最终态不确定；审计与实际状态可背离。修复验收：同样 6 并发应仅 1 次成功、审计 1 条。

### 8.3 🟢 正面项现场证实（避免误修）
- **数据范围 SELF/COLLEGE 生效**：学生 `GET /student` 列表只返回本人 1 条(id 9001)；学生读他人学生详情(990..005)→404；学院A教务员读学院B学生(990..009)→404。
- **越权访问被拒**：学生 `GET /system/user` → 403。
- 即 §7.10 的"核心业务 by-id/list 面干净"经活体复核成立；风险确实集中在 **文件预签名**（非 @DataScope 表）与 **导入批次**（§7.10 P0，未活体因缺 B 学院导出账号，代码已确认）两处非常规入口，以及会话/并发。

### 8.4 🔴 P1 会话不可撤销 —— 现场证实
- 手法：登录得 token → `POST /auth/logout`(HTTP200) → 用**同一 token** 调 `GET /auth/me` → **仍 HTTP200 code=0**。证实 `AuthController.logout` 是空操作、无黑名单，登出/改密后旧 access/refresh token 存活到自然过期（access 1h、refresh 7d）。修复验收：登出后旧 token 必须 401。

### 8.5 待补活体项（Phase 37 修复时成对"复现→阻断"）
- 导入批次 IDOR（需构造 xlsx + 跨学院导出账号，代码已确认）；前端批量下载泛型 bug（需浏览器）；refresh 令牌重放。

---

## 9. 三轮深扫（数据模型/迁移 · 系统域模块 · 测试覆盖）

### 9.1 数据模型 / 迁移 / 种子完整性
- **全库无任何 DB 外键约束**（23 迁移 grep `FOREIGN KEY/REFERENCES/ON DELETE` = 0）：父子关系全靠应用码维护，级联删除 100% 手动且各服务不一致。软删统一 `deleted TINYINT`(@TableLogic)，生产码无硬删（正面）。**根因性**：无 DB 安全网，下列孤儿问题无兜底。
- **P0 删除父实体产生静默孤儿**：`OrganizationServiceImpl.deleteCollege` 只查 major 数、`deleteMajor` **零守卫**；而 `MajorCodeValidator` 让 `education_master` 学生**跳过 sys_major 存在校验** → 学院可有真实学生却无 major 行 → 通过 deleteCollege 唯一守卫，student/training/material/exemption/video/cert/sys_user 仍指向已删学院，无报错永久悬挂。
- **P0 删除学生不停用其登录账号**：`StudentServiceImpl.delete` 只 `deleteById(student)`，不级联、**不停用关联 `sys_user`**（sys_user.student_id）→ 已删/退学学生仍有可用登录（访问撤销失败）。
- **P1 sys_user/sys_role 唯一键漏 `deleted`**（`V7:28-30,49`，与 V9-V22 惯例不一致）+ create 未捕获 DuplicateKey → **删了再建同名用户/工号/角色码 直接崩**（裸 DuplicateKeyException，对比 `CertificateServiceImpl:129` 有捕获）。
- **P1 无 Flyway prod 配置**：`baseline-on-migrate` 只在 application-dev（prod 走默认 false）→ 首次部署若 schema 非全空 Flyway 硬失败无恢复文档。
- **P1 种子污染生产参考数据**：`V12:74` 免考依据/科目 seed 了 7+2 占位值（违背 `待确认事项确认单` 决策#12"初始置空"）；`V8` 把 `PHASE2_COLLEGE_A/B`(id 201/202) + V10 假专业 seed 进 `sys_college/sys_major` → 真实下拉里出现假学院/专业。
- **P1 MinIO 分片/源文件从不真正删除**：`archiveUploadSessions:670` 只软删 DB 行，`merge:211` 合并后不删源分片 → **每个分片永久留存 + 合并视频双份**（无界存储增长）。
- **P1 `student.college_id` 写时不校验存在**（`allowedCollegeId` 只解析范围权限）→ 可存到不存在/已删学院。
- **P1 VARCHAR 日期无补零**：`CertificateServiceImpl.normalizeDate/validUntil` 产出 `2026/7/3` → 将来对 issue_date/valid_until 做 `ORDER BY`/范围比较字典序错（`2026/10/1` < `2026/2/1`）。当前无查询触发，是种子格式里的雷。
- **VARCHAR 存日期/枚举清单**：birth_date、全表 assessment_year、cert issue_date/valid_until、ability_test.score 均 VARCHAR；status 类枚举全是无 CHECK 的自由 VARCHAR（脏值只在下次读时抛异常）。
- **P2**：role/user 删除不级联 junction 表（经查**非提权洞**——权限解析都 re-join `r.deleted=0 AND status=1`，但留孤儿行扰乱审计）；归档视频任务把 `reviewer_id` 覆盖成自身 PK（毁"谁评审过"痕迹）；cert_sequence 若将来被软删可致重号（当前不可达）。**正面**：证书序列号 `SELECT FOR UPDATE` 并发安全；JWT 密钥 fail-fast 稳。

### 9.2 系统域/参考数据模块
**跨切 P0（新，数据/授权正确性）**：
- **P0-14 RBAC 授权 upsert 静默损坏**：`SysUserRoleMapper/SysRolePermissionMapper/SysUserDataScopeMapper` 用"先全禁 → 逐项 `INSERT...ON DUPLICATE KEY UPDATE`，id=parentId*1000+序号"，**冲突时 SET 从不更新区分列(role_id/permission_id/college_id)**，且唯一键无 `deleted`(`V7:63,99,116`)。实迹：把角色 [A,B,C] 改存 [A,C] → 用户最终得 **{A,B} 而非 {A,C}**（B 被复活、C 被丢弃），无报错无回滚，且每请求重查即时生效。`SecurityAdminServiceImpl.replaceUserRoles/assignRolePermissions/assignUserDataScope` 全用此模式。**日常改权就触发**。
- **P0-15 角色授权弹窗改一个权限，静默改写所有权限的数据范围**：`RolePermissionDrawer.vue:29-78` 丢弃后端真实 per-permission `scopeType`，保存时用硬编码 3 桶猜测 `defaultScopeFor` 重建全量 payload，后端 `assignRolePermissions` 只校验枚举照单全收 → 种子里 ASSIGNED 等真实范围被改一次全被冲掉。每次保存必触发。
- **P0-16 重新指派视频评审从不撤销旧评审**：`VideoReviewServiceImpl.assign:296/resolveAssignReviewerIds:1123` 只**增**任务不禁旧的；被移除评审仍可读/评分，其残留分数可被 `settleIfReady` 计入结算；无单独取消指派 API；`AssignReviewerModal.vue:45` 还把旧评审一直显示为"已指派"。

**模块 P1/P2**：
- **P1 终审通过从不通知学生**：4 个非视频复审流程的二审分支**只在非 PASS 才发通知**（`Exemption:303`/`Material:218`/`Student:216`/`Training:193` 都是 `if(!PASS) notify...`）→ 学生材料**最终通过时静默无通知**，只在被退回时收到消息；视频结算路径同样无通过通知。
- **P1 多个 `@DataScope` 是误导性空操作/恒拒**：`training_goal_config`/`sys_dict_type`/`sys_dict_item`/`sys_region` 别名**不在 TABLE_RULES** → 不加任何过滤（失败开放，好在这些是全局参考数据）；而 `major_training_goal` 对 COLLEGE/SELF 调用者**恒拒返回空**（`GET /major/{id}/training-goals` 学生/学院职员永远看不到，仅 SCHOOL 走 allSchool 才有数据）。
- **P1 字典编码唯一性软删盲 → 复建报 500**：`DictServiceImpl.existsTypeCode/existsItem` 用 `selectCount`(过滤 deleted=0)，但唯一键不含 deleted → 删了再建同码 → 裸 DuplicateKey → 无 `DataIntegrityViolationException` 处理器 → "系统异常"。Organization 的同类检查用了 `*IncludingDeleted` 正确，Dict/RBAC 没跟上（**修复不一致**）。
- **P1 禁用字典"类型"对其"项"零级联**：`queryItems/listItems` 只看 item.status 不查父 type.status → 禁用类型纯装饰，项仍可服务可缓存可新增。
- **P1 组织删除守卫不全**：`deleteMajor` 零使用校验（且 training_profile 用 code/name 快照非 major_id，无法常规校验）；`deleteCollege` 只查 major 数不查人；编辑专业培养目标联动时 `getMajorTrainingGoals` 过滤掉"已禁用但仍生效"的联动 → 全量替换保存时**静默软删管理员没看见的联动**。
- **P1 param 解析静默兜底**：`ParamServiceImpl.getInt/Boolean` 解析失败静默返回默认值**无日志** → 管理员把阈值(如视频过线分)打错成非数字，系统静默按默认跑，毫无提示。
- **P1 审计查询硬编码 `LIMIT 500` 无分页无 offset 无截断提示**（`AuditQueryMapper:144`）→ 大量审计静默截尾（合规/取证缺口；SQL 本身参数化不可注入）。
- **P1 work_no/student_id 复用烧号**：`sys_user` 唯一键无 deleted，username 有友好校验但 work_no/student_id 没 → 重发离职工号撞裸约束 → 500。
- **P2**：`UserDrawer.vue:87` 改用户重复写两次角色（updateUser 内已 replaceUserRoles，又单独 assignUserRoles）；deleteUser/deleteRole 无级联清 junction、deleteRole 无在用守卫；permissionTree 无状态过滤；role code 更新可改无不可变守卫。
- **验证正面**：Region 三级级联防环+链一致；Subject 类别节点排除+recent 自愈；`NotificationServiceImpl.markRead` 原子 `WHERE id AND user_id` 防越权；`DataScopeAspect` finally 清 ThreadLocal 无泄漏；reviewer_group DataScope 正确接线+指派时活体校验学院；证书/College/Major 编码用软删包含检查正确；审计删除被拒并留痕。



### 9.3 测试覆盖缺口（84 个 IT，全是 boot 层集成测试）
- **安全网范围**：`platform-boot` 下 14 个 IT 共 84 个 @Test；**business/exchange/statistics 无任何单测；前端零测试（无 *.spec、package.json 无 test 脚本）**。全靠 DB 支撑的集成测试兜底。
- **P0 三个已知 bug 全部 UNTESTED，会静默上线**：①材料 FAILED 永久污染——Phase5 唯一涉 FAILED 的用例用"换学年"绕过恢复场景，**同年同类重传复活从未被测**；②`confirmImport` 双确认——**全套无任何二次 confirm 调用**（连顺序都没有，更别说并发）；③视频双结算——Phase7 11 个用例全顺序、无 ExecutorService 并发。
- **P0 全库仅 1 个并发测试**（Phase9 证书序列号 50 线程，也是唯一乐观/悲观锁验证）；reissue/评审/结算等 check-then-act 全无并发覆盖。
- **⚠️ reissue 死枚举被测试"固化"**：Phase9 `voidAndReissue...` **断言了当前错误行为**（原证保持 VOIDED）→ 修复该 bug 会让此测试假失败，改时需同步改测试（否则误判回归）。
- **P1 RBAC 负向测试失衡**：14 文件里只有 COLLEGE_CLERK(3 端点)、REVIEW_TEACHER(1 端点)有活体 403；SYS_ADMIN/ACADEMIC_ADMIN/COLLEGE_AUDITOR/STUDENT **零活体拒绝测试**（只有静态权限串断言，不证服务端强制）→ 谁的 `@PreAuthorize` 丢了都不会被测出。
- **P1 跨学院夹具单向**：所有 IDOR 测试只测"A 学院职员→B 数据"，无 B 学院职员账号 → 反向/职员对职员对称性无法测。
- **P1 业务 IDOR 其实测得不错**（Phase3-11 各模块跨学院 DB 验证）——与本次活体"业务 by-id 安全"一致；漏的是**文件预签名**（非业务端点）与导入批次。
- **P1 覆盖漏项**：证书下半年有效期(7-12 月)分支未测（用例名自己标了）；导入导出真往返(导出再导入)未测、`UPDATE_EMPTY` 策略零覆盖、V-01..13 只松散聚合断言非逐行；exchange 跨学院是 soft-fail(code=0/failCount=1) 而非 403（与全站惯例不一致）；未指派但同学院评审教师碰他人任务的 IDOR 轴未测。
- **P2**：**全库零 404 测试、零分页/大数据测试**（所有数据集 1-5 条）；系统参数只测了 1 个的即时生效；审计只在部分流程断言、时间字段从不断言。
- **测试质量红旗**：Phase24:314 `auditLogMapper.selectCount(空条件)>0`（表里有任意行就过）；Phase3:119 拿 nanoTime 随机 id 比硬编码常量（恒不等，形同虚设）；多处仅断 HTTP200 不查字段/DB；14 文件共用硬编码种子 id + `@Order`/PER_CLASS 实例字段跨用例传递 → **不可并行/分片，一个早失败级联假失败**；Phase5/7 的 file_object 清理按 biz_type 全删（越界）。

### 9.4 三轮 P0 汇总（并入优先级）
- **P0-12 无 DB 外键 + 删父静默孤儿 + 删学生不停登录**（§9.1）——数据完整性靠应用码且不一致，上线前需补关键级联/停用逻辑或 DB 约束。
- **P0-13 关键 bug 零测试**（§9.3）——修 P0 时必须**同步补并发/复活/双确认测试**，否则改完无从证明、且 reissue 测试会假失败。
> 迁移建议随手做的低风险项：sys_user/sys_role 唯一键补 `deleted`（V24 新迁移）、create 捕获 DuplicateKey、免考种子占位值清空、Flyway prod 配置。

---

## 10. 执行摘要与结论（给决策）

### 10.1 整体判断
**核心业务逻辑是可靠的**（好消息，别推倒重来）：领域规则 12 条 11 条后端强校验、业务 by-id/数据范围 IDOR 面经活体+单测双证干净、证书序列号并发安全、无 SQL 注入、字段/证书/级联规则正确。**问题集中在"从 demo 到生产"的产品化层**——安全加固、并发正确性、RBAC 管理端一致性、运维基建、测试网——这正是过渡期该补的，现已全部摸清并带 file:line + 修复验收口径。

### 10.2 上线阻断 P0 全清单（16 项，按修复批次）
**批次 A · 安全急修（Phase 37a，多为独立改动，建议 Claude 亲自 + 复现/阻断活体证据）**
1. 文件预签名 IDOR（§8 已现场利用）— 加属主/范围校验。
2. 导入批次 IDOR（§7.10）— batches/confirm/rollback/errorReport 加 operator+学院过滤。
3. RBAC 授权 upsert 静默损坏（§9.2 P0-14）— 改权把用户改坏，日常触发。
4. 角色授权弹窗改一权限清掉全部数据范围（§9.2 P0-15）。
5. 会话不可撤销（§8 已现场证）— 登出/改密令牌黑名单或 token 版本。
6. 无 TLS（§7.11）— 前置 443 终端 + HSTS。
7. Swagger 生产公开 + 无 prod profile（§1 P0-4）。

**批次 B · 依赖与并发（Phase 37b/38）**
8. Spring Boot 3.2.11 EOL + 方法鉴权 CVE（§7.11）— 升 3.3+/3.4。
9. 并发无乐观锁（§8 已现场证 6/6）— BaseEntity 加 `@Version` + 条件更新 + 补唯一约束(证书 student+year、id_card_no)。
10. 重新指派评审不撤销旧评审（§9.2 P0-16）。
11. MinIO I/O 在事务内（§7.2）— 移出事务防连接池耗尽。

**批次 C · 数据/运维/容量（Phase 38/40）**
12. 无 DB 外键 + 删父孤儿 + 删学生不停登录（§9.1）。
13. 批量下载整包进堆 OOM（§1 P0-2）— 流式。
14. app 用 mysql root（§1 P0-5）— 专用最小权限账号。
15. 假备份 + 全库无 @Scheduled（§1 P0-6）— 真定时备份 + 清理任务。
16. 关键 bug 零测试（§9.3）— 修 P0 时**同步补并发/复活/双确认测试**（否则改完无从证明，且 reissue 测试会假失败）。

### 10.3 强烈建议
- **修 P0 必须配测试**：当前 3 个已知 P0 bug（材料 FAILED 污染 / confirmImport 双确认 / 视频双结算）零覆盖，改完无回归网 → 每修一项补对应测试。
- **顺手低风险快赢**（可先做，风险极低、收益明确）：前端批量下载泛型 bug（一行）、终审通过通知学生、param 解析失败记日志、审计查询去 500 硬顶、唯一键补 deleted、种子占位清空、`.dockerignore`、Redis 加密码/不暴露端口、nginx 安全头+代理超时。
- **本清单是活文档**：随修随勾；每个 P0 修复以"复现→阻断"活体证据验收（§8 已示范）。

> 三轮扫描 + 活体测试累计：~10 个专项审计代理（授权/业务/并发/性能/前端/部署/领域/模块/测试/数据模型）+ 4 项现场证实（文件 IDOR、并发竞态、登出空操作 = 可利用；数据范围/越权 = 已防护）。git 全程本地私有。

