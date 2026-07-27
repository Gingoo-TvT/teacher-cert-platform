# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform
**Audit mode:** full
**Date:** 2026-07-28
**Reviewer:** OpenAI Codex / GPT-5

---

## 1. Executive Summary

本次最终全量审计冻结在提交 `dfdfb9199215d856b70abb653cb65b6f9ed46282`。Phase 0 / U-004 的独立正式结论仍为 **PASS（0 finding）**；本报告不推翻该阶段结论，而是在其放行后重新覆盖全项目安全、业务规则、并发一致性、资源边界、运行发布、前端状态和证据真实性。项目当前正式裁定为 **CHANGES_REQUESTED / NO-GO**，不得据此 merge、push、deploy 或切流。

允许范围内的离线门禁全部通过：Phase 0 gate 单元测试 79/79（Windows 下 2 个 POSIX 用例按设计跳过）、Maven Surefire 201/201、后端 9 模块离线 package、前端 lint/type-check/build，以及 `git diff --check`。这些结果证明基础构建与既有离线回归稳定，但不能覆盖本轮确认的跨学院异常导出、审计留痕失败关闭、陈旧全实体并发写、100MB XLSX 堆放大、敏感字段投影、登出撤销和预签名 bearer URL 等路径。

共确认 **24 项 finding：0 Critical / 7 High / 13 Medium / 4 Low**。优先级应保持克制：先关闭 7 个 High，再关闭会阻断稳定发布的 Medium；多节点缓存、服务拆分和 bundle 优化不应抢占当前数据泄漏、状态机和审计正确性的修复顺序。

### Score Dashboard

```text
Security        ████░░░░░░  4.0  C   跨学院异常导出、审计可伪造、敏感明文与 bearer URL
Stability       ████░░░░░░  4.5  C   陈旧实体覆盖状态机、导入 OOM、备份边界不闭合
Performance     ██████░░░░  6.0  B   常规构建正常，但 XLSX 堆放大与大媒体未完成容量证据
Testing         ██████░░░░  6.0  B   后端回归较强，前端行为/a11y 与关键动态反例缺失
Maintainability ██████░░░░  6.0  B   模块边界清楚，但两个超大服务承担过多职责
Design          █████░░░░░  5.5  B   多数流程 fail-closed，部分写模型与权限投影不一致
Release         ███░░░░░░░  3.5  C   TLS、镜像证明、PITR、浏览器/Office/2GB 证据未闭环
─────────────────────────────────────
Overall         █████░░░░░  5.0  B
```

每项 0.0–10.0，分数越高越好。分数用于排序风险，不替代 finding 严重度或正式发布门禁。

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 7 | 7 | 0 |
| Medium | 13 | 13 | 0 |
| Low | 4 | 4 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **24** | **24** | **0** |

## 2. Project Map

项目是一个 Spring Boot 3.4 模块化单体，而不是微服务。`platform-boot` 负责装配、HTTP、Flyway、数据范围拦截与集成测试；`platform-security` 管理认证、JWT 与会话代次；`platform-system` 管理 RBAC、字典、参数、审计、通知和备份；`platform-file` 封装 MinIO/S3 文件元数据与预签名；`platform-business` 承载学生、培养、材料、免考、视频、能力测试和证书状态机；`platform-exchange` 负责标准 Excel 导入导出；`platform-statistics` 提供统计投影；`platform-common` 提供统一模型和基础设施合同。Vue 3 前端通过 Axios 调用 `/api`，Pinia 保存认证与页面状态，生产形态由 Nginx 托管。

核心业务链为“学生基本信息 → 培养信息 → 过程材料/免考/视频/能力测试并行轨道 → 证书预校验 → 生成/签发/导出/归档”。MySQL 是业务事实源，Redis 用于会话与字典发布窗口，Caffeine 是单 JVM 读缓存，MinIO 保存附件、视频、导出和备份产物。风险集中在跨层边界：控制器权限与 mapper 数据范围的连接、内容编辑与状态转换的并发写模型、MySQL 与对象存储的双写、前端本地状态与服务端会话、以及代码门禁与真实部署目标之间的证据绑定。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Architecture / data integrity | High | 8 个模块、主要 service/mapper、状态机、事务、缓存、备份 | 未执行真实双连接竞态；结论由确定性读写序列证明 |
| Security / privacy | High | RBAC migration、controller 权限、DataScope、审计、token、文件访问、敏感投影 | 未做攻击性载荷、漏洞扫描或真实网关探测 |
| Stability / performance | High | XLSX、视频、对象双写、调度、健康检查、堆配置 | 未做 2GB、堆压测、soak 或依赖故障注入 |
| Backend testing | High | Surefire、既有 Failsafe 结构、Phase 0 正式证据 | 本轮未连接 MySQL/Redis/MinIO，未重跑全量 Failsafe |
| Frontend state / accessibility | Medium | store、Axios、Dashboard、通知、构建与 CI | 未启动浏览器、未跑 Playwright/axe |
| Release / supply chain | High | Dockerfile、Compose、Nginx、CI、lockfile、runbook | 未构建镜像、未扫镜像或依赖、未核验线上 TLS |
| Documentation / evidence | High | 权威执行计划、Phase 14、AT 矩阵、备份恢复手册、Phase 0 报告 | 外部运维、Office/WPS、浏览器截图与生产证据未提供 |
| AI safety | Not assessed | 全仓搜索与项目地图未发现生产 AI/LLM 调用 | 无 AI 功能，不适用 |

## 3. Top Risks

1. **High — 异常导出跨学院泄漏：**学院角色可下载全表 `import_error_detail`，包含其他学院原始证件号、生日等错误值。
2. **High — 审计可伪造且失败开放：**不可信 XFF 可决定审计 IP，审计插入失败又被两层 catch 静默吞掉。
3. **High — 陈旧全实体写覆盖状态机：**更正/保存与签发/提交并发时可回退终态或丢失业务内容。
4. **High — XLSX 合法入口可打穿堆：**100MB 压缩工作簿被多份全量物化，默认 1GB heap 无法提供上界。
5. **High — 敏感明文权限旁路：**仅持有 `cert:view` 的学院角色可取得完整身份证号，出生日期也未默认脱敏。
6. **High — 前端登出不撤销服务端会话：**用户看到“退出”后，旧 access/refresh token 仍可继续使用。
7. **High — 复制后的预签名 URL 不再校验登录：**当前实现与“未登录复制链接不可访问”的明确规格相冲突。
8. **Medium — 生产边界按仓库原样为 HTTP：**TLS/HSTS/CSP 只是注释模板，后端和 MinIO 端口直接发布。
9. **Medium — 备份无法表示应用允许写入的超大 `preview_json`：**单行 Base64 SQL 超 32MiB 后整次备份失败。
10. **Medium — 稳定发布证据未闭环：**PITR、真实 2GB/异常编码、浏览器和 Excel/WPS 仍只有计划或间接证据。

## 4. Detailed Findings

### Finding: ERROR 导出绕过学院数据范围

- Severity: High
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: platform-exchange / `POST /api/exchange/export/ERROR`
- Evidence:
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/controller/ExchangeController.java:102-116`
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:1088-1098`
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/config/DataScopeSqlHandler.java:49-58`
  - Function / Module: `ExchangeServiceImpl.exportErrors`
  - Relevant behavior: ERROR 导出全表查询错误明细；`certificate` alias 不匹配该表，且没有批次、操作人或学院过滤。
- Problem: 学院教务员/审核员持有 `exchange:export:full`，但该权限没有把错误行限制在可见批次内。错误表保存原始失败值，包括证件号和生日。
- Why it matters: 这是明确的跨学院个人信息泄漏，并直接违反 AT-13 与项目数据范围红线。
- Realistic failure scenario: 学院 A 用户调用 ERROR 导出，Excel 中同时出现学院 B 批次的学号、姓名、错误字段和原始证件号码。
- Minimal fix: ERROR 导出必须指定批次；校级可见全部，非校级复用 `ensureBatchAccessible` 的 owner-only 口径，并按 batch id 查询错误行。
- Better long-term fix: 如确需学院共享，在错误表增加规范化学院归属并以 SQL 精确过滤；不要解析自由 JSON。
- Regression test suggestion: 学院 A/B 各建错误批次，断言 A 只能导出 A，校级可导出全部，并覆盖证件号/生日错误值。
- Estimated effort: 0.5–1 day

### Finding: 审计来源可伪造且写入失败仍放行业务

- Severity: High
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: platform-system / 审计切面与反向代理
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/support/AuditIp.java:13-28`
  - File: `frontend/nginx.conf:37-43`
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/aspect/AuditLogAspect.java:32-45`
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/AuditLogServiceImpl.java:22-40`
  - Function / Module: `AuditIp.clientIp`, `AuditLogAspect.around`, `AuditLogServiceImpl.record`
  - Relevant behavior: 后端盲信 XFF 首项；Nginx 保留客户端 XFF；业务先成功，审计异常被两层吞掉。
- Problem: 已认证调用者可以伪造审计 IP；过长/非法值或数据库异常还可让审计插入失败，而业务请求仍返回成功。
- Why it matters: 项目的合规核心是可归因、不可缺失的全操作留痕；当前日志既可能归错人机来源，也可能整条缺失。
- Realistic failure scenario: 用户提交敏感导出或状态变更时附带自定义 XFF；审计行记录伪造地址，或因列长度/数据库错误未落库，业务仍提交。
- Minimal fix: 只在 `remoteAddr` 属于配置的可信代理时解析转发头；入口覆盖不可信 XFF；IP 规范化/限长；关键业务审计失败必须回滚同一事务。
- Better long-term fix: 为不能同事务写入的边界建立可靠持久队列前，保持同步 fail-closed；不要先上异步复杂度。
- Regression test suggestion: 覆盖伪造/过长 XFF、非可信直连、可信代理链和 mapper 抛错；关键写失败时业务不得提交。
- Estimated effort: 1–2 days

### Finding: 陈旧全实体更新可回退状态机或丢失内容

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: certificate、student、training、material、exemption、ability 聚合
- Evidence:
  - File: `platform-common/src/main/java/cn/edu/gpnu/platform/common/entity/BaseEntity.java:17-39`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/certificate/service/impl/CertificateServiceImpl.java:153-230`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/certificate/service/impl/CertificateServiceImpl.java:267-313`
  - Function / Module: `CertificateServiceImpl.correct` 与状态转换方法
  - Relevant behavior: `correct` 读取整行后无条件 `updateById`；状态转换虽按旧 status CAS，却仍提交整份陈旧实体；基础实体无版本号。
- Problem: 内容编辑与状态转换使用不同并发合同。任一方提交整份先前读到的实体，都会覆盖另一事务刚写入的状态或内容。
- Why it matters: 证书、审核和确认是法律/业务终态；被回写为旧状态或丢失更正会破坏不可逆流程和审计一致性。
- Realistic failure scenario: `correct` 与 `issue` 都读取 GENERATED；issue 先提交 ISSUED，随后 correct 把 status 写回 GENERATED；反序提交则 issue 可覆盖更正字段。
- Minimal fix: 同一聚合的所有修改统一使用短事务行锁，或统一版本/CAS；内容更新只写请求字段和预期状态，禁止全实体陈旧覆盖。
- Better long-term fix: 为各聚合形成单一 mutation gateway，并显式区分“内容修订”和“状态迁移”命令。
- Regression test suggestion: 用两个真实连接和 barrier 覆盖两种提交顺序；证书、能力测试至少各一组，断言终态不回退且内容不丢失。
- Estimated effort: 2–4 days

### Finding: 100MB XLSX 导入存在无界堆放大

- Severity: High
- Confidence: High
- Category: Performance
- Status: Confirmed
- Affected area: platform-exchange / 导入预校验
- Evidence:
  - File: `platform-boot/src/main/resources/application.yml:7-12`
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:158-222`
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/support/ExchangeExcelHelper.java:138-161`
  - File: `docker-compose.yml:129`
  - Function / Module: `ExchangeServiceImpl.prevalidate`, `ExchangeExcelHelper.readStandardRows`
  - Relevant behavior: 100MB 文件先 `getBytes`，再以 XSSFWorkbook DOM、rows、两个计数 map、preview/result、整包 JSON 多次物化；默认 heap 1GB。
- Problem: 没有行数、单元格长度或解压后预算。普通高压缩/高行数 XLSX 即可让支持范围内的请求造成 OOM。
- Why it matters: 单个授权用户或误上传即可重启整个后端，并中断其他学院的审核、上传和签发。
- Realistic failure scenario: 100MB 内的高压缩工作簿展开后超过 heap，POI 与多份 Java 对象同时驻留，进程在预校验阶段退出。
- Minimal fix: 先增加独立导入字节、行数、单元格长度和展开预算，并在构建 rows/maps 前拒绝；上限与业务 20k 量级对齐。
- Better long-term fix: 使用事件流读取，分批校验并分页持久化预览，避免在响应中返回全部成功行。
- Regression test suggestion: 在受限 heap 中提交高压缩高行数夹具，断言在固定预算点返回领域错误且进程存活。
- Estimated effort: caps 0.5–1 day; streaming 2–4 days

### Finding: 敏感字段默认投影绕过明文权限

- Severity: High
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: certificate、student、exchange 响应与导出
- Evidence:
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/controller/CertificateController.java:37-50`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/certificate/service/impl/CertificateServiceImpl.java:593-619`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/student/service/impl/StudentServiceImpl.java:570-592`
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:1049-1064`
  - Function / Module: VO 与标准导出投影
  - Relevant behavior: `cert:view` 列表/详情返回完整身份证号；学生普通投影和标准导出返回完整出生日期。
- Problem: 项目已有 `exchange:export:sensitive` 明文权限，但多个普通读取路径未使用统一敏感投影。
- Why it matters: 学院角色可分页获取本院完整身份证号；生日也在不必要场景中明文流出，违反默认脱敏要求。
- Realistic failure scenario: 只持有 `cert:view` 的账号批量调用证书列表，收集完整身份证号，无需敏感权限或专门审计。
- Minimal fix: 证书 VO 默认遮蔽证件号，普通学生详情和标准导出遮蔽生日；明文只走已有敏感权限并留痕的专用路径。
- Better long-term fix: 建立唯一敏感投影器，按字段和用途显式返回 masked/plain，避免各 service 自行决定。
- Regression test suggestion: 仅普通权限的三条路径都只见脱敏值；敏感权限路径见明文且产生完整审计。
- Estimated effort: 0.5–1 day

### Finding: 前端登出没有撤销服务端会话

- Severity: High
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: frontend authentication
- Evidence:
  - File: `frontend/src/layouts/MainLayout.vue:207-210`
  - File: `frontend/src/stores/user.ts:55-75`
  - File: `frontend/src/api/auth.ts:55-56`
  - Function / Module: `onUserMenu`, user store `logout`
  - Relevant behavior: UI 只清 localStorage；现成的 `/auth/logout` API 没有调用方。
- Problem: “退出登录”只改变浏览器本地状态，服务端 session generation 不推进，旧 token 保持有效。
- Why it matters: 共享电脑、复制 token 或浏览器扩展泄露场景下，用户无法通过退出终止旧会话。
- Realistic failure scenario: 用户退出并离开；已复制的 refresh token 在默认 7 天窗口内继续换取 access token。
- Minimal fix: store 增加远端 logout，UI await 调用并在 `finally` 清本地；401 处理只调用本地 clear，避免递归。
- Better long-term fix: 与 refresh cookie/轮换整改合并为单一会话状态机。
- Regression test suggestion: 点击退出必须发送 POST；退出后旧 access/refresh 都被拒；服务端失败时本地仍清理。
- Estimated effort: 0.5–1 day

### Finding: 复制后的预签名 URL 不再校验登录

- Severity: High
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: platform-file / 视频和敏感附件访问
- Evidence:
  - File: `plan.md:315-322`
  - File: `plan.md:470-475`
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileServiceImpl.java:292-315`
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/controller/VideoReviewController.java:206-212`
  - Function / Module: `FileServiceImpl.presignedGet`
  - Relevant behavior: 应用入口校验权限后返回原始 S3 presigned URL；MinIO 后续只校验签名，不知道当前应用登录状态。
- Problem: 实现是短时 bearer capability，而权威规格明确要求“复制链接未登录不可访问”。现有测试只测应用入口 401，不测直接 URL。
- Why it matters: 视频、师德材料和附件属于敏感数据；URL 被复制、日志记录或屏幕共享后，可在有效期内绕过应用身份。
- Realistic failure scenario: 评审者取得播放 URL 后复制到隐身窗口；即使未登录，MinIO 仍返回对象。
- Minimal fix: 采用登录绑定的应用代理/一次性 ticket，在每次取流时校验当前会话；不要把原始对象 URL交给浏览器。
- Better long-term fix: 若学校明确接受短时 bearer，必须走 R10 正式变更规格；在此之前不能用文档降级替代修复。
- Regression test suggestion: 取得播放地址后清除登录态，在另一客户端直接请求必须失败；原用户有效会话仍可播放。
- Estimated effort: 2–3 days

### Finding: 生产部署边界按仓库原样仍是明文且端口过度发布

- Severity: Medium
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: Nginx / Docker Compose
- Evidence:
  - File: `frontend/nginx.conf:4-31`
  - File: `docker-compose.yml:46-58`
  - File: `docker-compose.yml:136-161`
  - Function / Module: production deployment boundary
  - Relevant behavior: 443/HSTS 是注释模板，实际监听 80；backend、MinIO API/console 和 frontend 都发布宿主端口。
- Problem: 仓库没有强制可信 TLS 终止器、HTTP 跳转、HSTS/CSP 或内部端口隔离的可验证部署合同。
- Why it matters: 按仓库原样部署会明文传输登录和敏感业务数据，并扩大后端/对象存储暴露面。
- Realistic failure scenario: 运维直接使用 production compose，对公网发布 80/8080/9000/9001，误以为仓库已完成 WS-5。
- Minimal fix: 固定唯一 TLS 入口并强制 80 跳转；启用 HSTS/CSP；backend 和 MinIO 只进内部/管理网络。
- Better long-term fix: 将真实网关策略、证书挂载和允许拓扑做成发布门禁，不需要引入服务网格。
- Regression test suggestion: 用户在真实入口核验 TLS、跳转、安全头和外部端口不可达。
- Estimated effort: 1–3 days plus external certificate input

### Finding: 长生命周期 refresh token 暴露给同源 JavaScript

- Severity: Medium
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: frontend store / refresh flow
- Evidence:
  - File: `frontend/src/stores/user.ts:6-20`
  - File: `frontend/src/stores/user.ts:48-67`
  - File: `platform-boot/src/main/resources/application.yml:59-62`
  - Function / Module: user store persistence
  - Relevant behavior: access/refresh token 都进 localStorage；refresh 默认 604800 秒。
- Problem: 任意同源脚本均可读取长生命周期 bearer token；refresh 后旧 token 也没有一次性重放拒绝合同。
- Why it matters: 一次 XSS、恶意依赖或浏览器扩展读取即可形成长窗口会话接管。
- Realistic failure scenario: 页面脚本读取 refresh token 并外带；用户改页或关闭浏览器不能阻止其继续刷新。
- Minimal fix: refresh 改为 `HttpOnly; Secure; SameSite` cookie，access 仅内存保存，并轮换/拒绝旧 refresh。
- Better long-term fix: 结合服务端 session generation 提供设备会话列表和定向撤销，但不作为本轮前置。
- Regression test suggestion: JavaScript 读不到 refresh；cookie 属性完整；刷新后旧 token 重放失败。
- Estimated effort: 2–4 days

### Finding: 通用上传接口授权与业务绑定过宽

- Severity: Medium
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: platform-file / generic upload
- Evidence:
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/controller/FileController.java:32-43`
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileServiceImpl.java:60-89`
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileServiceImpl.java:622-626`
  - Function / Module: generic multipart upload
  - Relevant behavior: 任意登录者可传任意 bizType；无业务权限、对象绑定、用户配额或通用 READY 清理。
- Problem: 未见前端调用者的通用入口比材料/视频专用流程更宽，允许认证用户持续创建不可归属对象。
- Why it matters: 可绕过业务文件大小/类型/归属合同，形成存储滥用和数据保留死角。
- Realistic failure scenario: 普通学生重复提交 100MB 文件和任意 bizType，数据库与 bucket 持续增长但没有业务实体负责删除。
- Minimal fix: 确认无调用方后删除；否则增加明确权限、bizType 白名单、参数化大小上限和业务实体绑定。
- Better long-term fix: 所有上传只经领域专用入口，不保留“万能文件 API”。
- Regression test suggestion: 普通学生无领域权限、非法 bizType、超限文件均拒绝；合法材料/视频不回归。
- Estimated effort: 0.5–1 day

### Finding: 对象写成功而元数据失败会留下不可发现孤儿

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: platform-file / MySQL-MinIO 双写
- Evidence:
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileServiceImpl.java:60-89`
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/mapper/FileObjectScanMapper.java:23-40`
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/config/CleanupScheduleConfig.java:72-88`
  - Function / Module: `FileServiceImpl.upload`
  - Relevant behavior: MinIO put 后直接 insert；insert 失败无补偿；清理扫描从已有 `file_object` 行出发且只报告。
- Problem: 对象存储和数据库没有原子性，当前失败路径会产生既无元数据也不在扫描候选中的对象。
- Why it matters: 孤儿可能包含敏感附件，既无法授权访问，也无法按业务删除或保留期治理。
- Realistic failure scenario: MinIO put 成功，随后数据库瞬时失败；请求报错，但对象永久留在 bucket。
- Minimal fix: 对本次唯一 key 做 best-effort 精确补删；补删失败必须记录可告警、可重试的清理项。
- Better long-term fix: 统一对象台账状态机，先登记 intent、再上传、最后 READY，并由对账器处理终态。
- Regression test suggestion: 注入 mapper insert 失败，断言对象被删除；删除再失败时断言持久/可观测清理记录存在。
- Estimated effort: 0.5–1 day

### Finding: 后端以 MinIO root 身份运行

- Severity: Medium
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: production Compose / object storage identity
- Evidence:
  - File: `docker-compose.yml:46-58`
  - File: `docker-compose.yml:89-94`
  - Function / Module: MinIO credentials wiring
  - Relevant behavior: 后端使用与 MinIO root 相同的身份，而不是 bucket-scoped 应用账号。
- Problem: 应用只需要指定 bucket 的对象、分片和生命周期权限，却持有实例管理员权限。
- Why it matters: 后端进程或凭据失陷时，影响面从单 bucket 扩大到所有 bucket 和管理操作。
- Realistic failure scenario: 应用配置泄露后，攻击者可使用 root 身份删除其他 bucket 或修改服务级配置。
- Minimal fix: 创建仅限 `teacher-cert` bucket 所需动作的服务账号，root 只用于受控初始化。
- Better long-term fix: 按应用/备份职责拆分两个最小权限账号并定期轮换。
- Regression test suggestion: 应用正常完成上传/下载/分片/生命周期；跨 bucket 与 admin API 被拒。
- Estimated effort: 0.5–1 day

### Finding: 统计批次范围使用字符串包含判断

- Severity: Medium
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: platform-statistics
- Evidence:
  - File: `platform-statistics/src/main/java/cn/edu/gpnu/platform/statistics/service/impl/StatsServiceImpl.java:370-400`
  - File: `platform-statistics/src/main/java/cn/edu/gpnu/platform/statistics/service/impl/StatsServiceImpl.java:591-600`
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:1329-1340`
  - Function / Module: `StatsServiceImpl.batchVisible`
  - Relevant behavior: `scopeJson.contains(String.valueOf(collegeId))` 用于学院可见性；同项目另一处已明确避免该模式。
- Problem: 数字子串不是身份比较，学院 `1` 可匹配 `10` 或 JSON 中其他字段。
- Why it matters: 可泄露其他批次的文件名、操作人、时间和成功/失败统计。
- Realistic failure scenario: 学院 1 用户查看统计，命中 `scopeJson` 中学院 10 或 keyword/date 的字符 `1`。
- Minimal fix: 非校级先统一 owner-only；若必须学院共享，增加规范化 college_id 并精确 SQL 比较。
- Better long-term fix: 删除自由 JSON 上的授权判断，权限事实必须落规范列/关联表。
- Regression test suggestion: 覆盖学院 1/10、keyword 含 1、空 scope、owner 和校级。
- Estimated effort: 0.5 day

### Finding: 应用可写入备份格式无法表示的单行

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: exchange preview / logical backup
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:88-100`
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:330-392`
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:492-501`
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:191-221`
  - Function / Module: backup SQL literal writer / `preview_json`
  - Relevant behavior: 单语句限制 32MiB，文本 Base64 膨胀约 4/3；预校验可持久化全部成功行 JSON且无共享上限。
- Problem: 约 24MiB 以上的单行 JSON 可被业务正常写入，却使每次逻辑备份在该行确定性失败。
- Why it matters: 一次合法导入可长期毒化所有后续自动备份，直到数据被人工处理。
- Realistic failure scenario: 大批成功预览写入一行 `preview_json`；夜间备份每次都在 Base64 预算检查失败。
- Minimal fix: 写入前复用备份字节合同并留足余量；确认/过期后清理 preview，或把预览拆成有界子行。
- Better long-term fix: 预览数据规范化、分页读取，备份格式只表示受控行大小。
- Regression test suggestion: 边界内预览可备份；边界外在导入阶段被拒，不能先写入后毒化备份。
- Estimated effort: 0.5–1 day

### Finding: 全量备份没有单飞约束

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: platform-system backup scheduling
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:127-188`
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/config/BackupScheduleConfig.java:25-38`
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/SystemManagementServiceImpl.java:165-170`
  - Function / Module: manual and scheduled backup
  - Relevant behavior: 每个调用直接开始全量扫描、临时文件和上传；没有进程内或集群租约。
- Problem: 手工与定时、两个手工请求或多个实例在 03:00 可并发执行完整备份。
- Why it matters: 重叠扫描增加数据库/磁盘/MinIO 压力，并生成难以解释的重复快照与告警。
- Realistic failure scenario: 运维手工备份跨过 03:00，定时任务同时启动；两份任务争用资源并一起超时。
- Minimal fix: 用数据库唯一运行标记或短租约 CAS；第二个请求明确返回“已有备份运行中”。
- Better long-term fix: 只有在需要队列化多个备份类型时再引入任务调度器；当前不需要复杂编排。
- Regression test suggestion: 两线程同时触发，仅一个进入导出，另一个确定性拒绝；过期租约可恢复。
- Estimated effort: 1 day

### Finding: 健康检查把 liveness 当 readiness

- Severity: Medium
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: platform-boot / Compose healthcheck
- Evidence:
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/controller/HealthController.java:21-27`
  - File: `docker-compose.yml:147-164`
  - Function / Module: `/api/health`
  - Relevant behavior: 无条件返回 UP；Compose 以其决定 backend healthy 和 frontend 启动。
- Problem: MySQL、Redis、MinIO 或 Flyway 不可用时，容器仍可能被标记健康。
- Why it matters: 发布门和流量入口无法区分“进程活着”与“能安全服务”。
- Realistic failure scenario: Redis 或 MySQL 在启动后中断，health 仍 200，负载均衡继续发送登录/审核请求。
- Minimal fix: 保留轻量 liveness，新增 readiness 检查 MySQL、Redis、MinIO 与迁移状态；Compose 改用 readiness。
- Better long-term fix: 逐步补关键队列/租约/备份指标，不必一次引入完整可观测平台。
- Regression test suggestion: 逐项断开隔离依赖，断言 liveness UP、readiness DOWN，恢复后自动 UP。
- Estimated effort: 1–2 days

### Finding: Dashboard 旧响应可覆盖当前学年

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: frontend dashboard
- Evidence:
  - File: `frontend/src/views/DashboardView.vue:96-102`
  - File: `frontend/src/views/DashboardView.vue:129-150`
  - Function / Module: `loadDashboard`
  - Relevant behavior: 学年 watch 可并发发起请求，没有世代/取消；旧请求最后返回会覆盖新学年；错误时旧数据不清理。
- Problem: UI 标题使用当前学年，但内容可能来自之前的慢请求或之前成功状态。
- Why it matters: 用户可能依据错误学年的统计做审核、催办或导出决定。
- Realistic failure scenario: 快速从 2025 切到 2026；2026 先返回，2025 后返回并覆盖卡片。
- Minimal fix: 捕获请求学年并递增 request id，只允许最新请求提交；错误时清空或显式标陈旧。
- Better long-term fix: 在 API client 层支持 AbortController，但本轮一个 generation guard 即足够。
- Regression test suggestion: deferred promise 构造逆序响应与部分失败，断言旧请求不能提交。
- Estimated effort: 0.5 day

### Finding: 前端没有行为与可访问性自动门禁

- Severity: Medium
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: frontend CI
- Evidence:
  - File: `frontend/package.json:6-29`
  - File: `.github/workflows/ci.yml:166-182`
  - File: `docs/README.md:22-35`
  - Function / Module: frontend scripts and CI
  - Relevant behavior: 只有 lint、type-check、build；没有 Vitest、Playwright 或 axe。
- Problem: 静态门禁不能证明登出请求、刷新竞态、路由权限、上传交互和键盘语义。
- Why it matters: 本轮两个真实前端行为缺陷在现有 CI 全绿时仍存在。
- Realistic failure scenario: 小改动删除 logout API 调用或引入旧响应覆盖，lint/build 均通过并进入发布。
- Minimal fix: 先加认证 store、Dashboard 请求世代和权限路由单测，再加一条关键流程 Playwright + axe smoke。
- Better long-term fix: 按高风险用户旅程逐步增加 E2E，不以覆盖率数字驱动大规模测试。
- Regression test suggestion: logout 撤销、refresh 单飞、Dashboard 逆序、无权限路由和通知键盘操作。
- Estimated effort: 3–5 days

### Finding: 发布镜像与 CI 缺少可复现证明

- Severity: Medium
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: Dockerfiles / Compose / GitHub Actions
- Evidence:
  - File: `Dockerfile:3-28`
  - File: `frontend/Dockerfile:3-14`
  - File: `docker-compose.yml:3-157`
  - File: `.github/workflows/ci.yml:16-182`
  - Function / Module: release supply chain
  - Relevant behavior: 基础镜像和 Actions 只固定 tag/major，应用默认 latest，容器无非 root USER，CI 不构建最终镜像或产出 SBOM。
- Problem: 同一源码 SHA 在不同时间可解析到不同基础镜像，测试过的源码也未绑定最终部署 digest。
- Why it matters: 无法可靠回答“运行的到底是哪份产物”，回滚、漏洞处置和审计都缺确定对象。
- Realistic failure scenario: 基础 tag 漂移或 latest 被覆盖，重新部署同一 SHA 得到不同镜像。
- Minimal fix: 固定基础镜像 digest、应用不可变 tag/digest、Actions commit SHA；容器非 root；CI 构建一次并输出 SBOM/校验。
- Better long-term fix: 在现有 CI 上加 provenance/签名；不需要先引入新制品平台。
- Regression test suggestion: 静态策略拒绝 tag-only/USER 缺失；启动镜像验证非 root、health 和 SHA/digest 标签。
- Estimated effort: 2–4 days

### Finding: 稳定发布的真实环境与灾备证据仍不完整

- Severity: Medium
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: final acceptance / operations
- Evidence:
  - File: `docs/CURRENT-EXECUTION-PLAN.md:89-121`
  - File: `docs/CURRENT-EXECUTION-PLAN.md:159-176`
  - File: `docs/AT验收复验矩阵.md:22-25`
  - File: `docs/备份与恢复手册.md:118-135`
  - Function / Module: final release evidence
  - Relevant behavior: 真实 2GB、异常编码/首帧、Chrome/Edge/Firefox、Excel/WPS、容量/性能 trace、物理全备/binlog/PITR 均明确留待外部执行。
- Problem: 这些是已声明但未归档的发布证据；现有 build、5MiB multipart 或应用逻辑备份不能替代。
- Why it matters: 大媒体、兼容性和灾难恢复正是静态/小夹具最难覆盖的失败面。
- Realistic failure scenario: 上线后才发现 2GB 路径超时/磁盘不足、Office 转换文本字段，或单向迁移后无法按时间点恢复。
- Minimal fix: High/Medium 代码整改完成后，由用户/授权复核者在隔离目标执行既有清单并归档版本、目标身份、日志、截图和清理证明。
- Better long-term fix: 将可自动化部分接入候选 SHA 绑定门禁；Office、真实浏览器和 PITR 保留人工签字。
- Regression test suggestion: 真实 2GB/异常媒体矩阵、三浏览器关键流程、Excel/WPS 文本核对、隔离 PITR 演练。
- Estimated effort: 1–3 days plus environment and data transfer time

### Finding: 底层异常文本直接进入客户端响应

- Severity: Low
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: global error responses
- Evidence:
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileServiceImpl.java:313-329`
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/handler/GlobalExceptionHandler.java:27-31`
  - Function / Module: `BizException` construction
  - Relevant behavior: 多处把 `e.getMessage()` 拼进领域异常，全局处理器原样返回。
- Problem: MinIO、解析器或数据库异常可能带 endpoint、bucket、object key 或内部结构。
- Why it matters: 信息泄露本身通常有限，但会帮助定位内部资源，并使 API 错误合同不稳定。
- Realistic failure scenario: S3 SDK 返回包含 endpoint/object key 的异常，浏览器收到相同文本。
- Minimal fix: 客户端返回固定领域消息和 correlation id，详细异常仅内部日志并脱敏。
- Better long-term fix: 建立小型错误码表，不需要包装所有第三方异常层级。
- Regression test suggestion: 注入含哨兵文本的底层异常，响应体不得出现哨兵。
- Estimated effort: 2–4 hours

### Finding: 通知列表的点击目标键盘不可达

- Severity: Low
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: Dashboard / Notice Center
- Evidence:
  - File: `frontend/src/views/DashboardView.vue:324-335`
  - File: `frontend/src/views/NoticeCenterView.vue:173-181`
  - Function / Module: clickable `n-list-item`
  - Relevant behavior: 只有 click handler，没有 button/link、tabindex、role 或 Enter/Space 行为。
- Problem: 鼠标用户可打开通知，键盘用户不能聚焦或激活同一动作。
- Why it matters: 关键通知入口对部分用户不可用，也说明现有 a11y 门禁缺失。
- Realistic failure scenario: 用户仅用键盘 Tab 浏览页面，焦点跳过所有通知。
- Minimal fix: 使用真实 button/link，或补完整键盘语义、焦点样式和 Enter/Space 处理。
- Better long-term fix: 关键交互统一使用语义组件，并用 axe + keyboard smoke 防回归。
- Regression test suggestion: Tab 可达，Enter/Space 只触发一次，焦点可见。
- Estimated effort: 1–2 hours

### Finding: 多实例部署时本地缓存缺少跨节点失效

- Severity: Low
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: platform-system Caffeine cache
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/cache/EpochGuardedCache.java:45-47`
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/config/CacheConfig.java:60-71`
  - Function / Module: sysParam / dict / org caches
  - Relevant behavior: 写后只逐出当前 JVM；参数 TTL 30 分钟，字典/组织可达 12 小时。
- Problem: 当前单实例 Compose 不触发，但若直接水平扩容，节点 B 会继续使用节点 A 已修改/停用的规则或字典值。
- Why it matters: 登录、证书、视频参数和字典有效性在多节点下可能短期不一致。
- Realistic failure scenario: 管理员在节点 A 停用字典项，节点 B 仍按旧缓存接受请求。
- Minimal fix: 稳定发布文档明确单实例约束；要扩容时先缩短/移除关键本地缓存或加入简单跨节点失效。
- Better long-term fix: 只有确认多实例需求后再统一缓存事件协议，现阶段不引入复杂消息系统。
- Regression test suggestion: 双实例共享 Redis/MySQL，节点 A 修改后节点 B 在合同时间内必须看到新值。
- Estimated effort: documentation 1 hour; multi-node support 1–2 days

### Finding: 活动验收文档与当前权威状态存在漂移

- Severity: Low
- Confidence: High
- Category: Maintainability
- Status: Confirmed
- Affected area: Phase 14 / release governance
- Evidence:
  - File: `docs/phase-14-非功能部署验收.md:62-80`
  - File: `docs/CURRENT-EXECUTION-PLAN.md:157-176`
  - File: `docs/AT验收复验矩阵.md:22-25`
  - Function / Module: release status documentation
  - Relevant behavior: Phase 14 仍称全项目只受 Phase 0 阻断，并把浏览器/Office 目标勾为完成；权威计划已进入最终审计且保留对应证据债。
- Problem: 两份活动文档对当前门禁和“已执行/待执行”的表达不同。
- Why it matters: 运维或评审者可能依据旧勾选误判发布状态。
- Realistic failure scenario: 阅读 Phase 14 的人员认为 Phase 0 PASS 后即可上线，跳过最终审计和人工验收。
- Minimal fix: 更新 Phase 14 为 Phase 0 已 PASS、最终审计 CHANGES_REQUESTED，并把未执行证据恢复为 pending。
- Better long-term fix: 只在 `CURRENT-EXECUTION-PLAN.md` 维护活动状态，Phase 文档保留历史结论并链接权威入口。
- Regression test suggestion: 文档门禁检查只允许一个活动状态来源，扫描矛盾的 PASS/pending 表述。
- Estimated effort: 0.5 hour

## 5. Architecture Concerns

- Coverage: High
- Inspected evidence: 模块依赖、主要 controller/service/mapper、MySQL/Redis/MinIO 边界、状态机、缓存和备份。
- Exclusions / limits: 未运行多节点或双连接动态竞态。

模块化单体边界总体清楚，避免了分布式事务的额外复杂度。主要架构问题不是模块数量，而是同一聚合存在两套写模型：状态迁移使用 status CAS，内容编辑仍提交陈旧全实体。另一个边界问题是 MinIO 与 MySQL 双写缺少统一 intent/compensation。`ExchangeServiceImpl`（约 1855 行）和 `VideoReviewServiceImpl`（约 2602 行）承担解析、授权、状态、存储和投影多重职责，建议只在当前 High/Medium 关闭后按真实变更热点拆分。

## 6. Security Concerns

- Coverage: High
- Inspected evidence: RBAC migrations、controller 权限、DataScope、审计、JWT/session、Nginx、文件与敏感字段投影。
- Exclusions / limits: 未执行攻击性载荷、真实网关/证书探测、依赖或镜像漏洞扫描。

发布阻断为 F-01、F-02、F-05、F-06、F-07。正向控制包括生产 profile fail-fast、JWT 类型区分、会话代次撤销、精确 CORS、文档端点生产禁用，以及材料/视频应用入口的数据范围校验。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: 状态迁移、导入内存、备份、对象双写、调度、健康检查、缓存。
- Exclusions / limits: 未执行真实并发、依赖断开、长期运行或故障积压。

F-03、F-04、F-11、F-14、F-15、F-16 是稳定性核心。项目多个复杂流程已经使用 CAS/租约/墓碑并能失败关闭，但同一套纪律没有覆盖普通内容编辑、Excel 展开预算和备份互斥。

## 8. Performance Concerns

- Coverage: High
- Inspected evidence: XLSX 解析、堆参数、大媒体临时空间、前端 build 产物、备份流式写。
- Exclusions / limits: 未执行 heap profile、2GB 实传、容量压测或性能 trace。

当前唯一直接 High 是 XLSX 堆放大。前端本轮 build 成功，但 `echarts` 约 1035kB、`naive` 约 1340kB，继续触发 chunk 告警；这是已知 WS-12，当前不应先于数据安全和状态机修复。

## 9. Testing Gaps

- Coverage: High
- Inspected evidence: Maven/Surefire、Phase 0 正式证据、Failsafe 用例结构、frontend scripts/CI、媒体夹具。
- Exclusions / limits: 本轮未连接真实依赖，未启动浏览器或 Office/WPS。

后端离线单测 201/201 通过，Phase 0 的正式 7/33 真实门禁保持有效。缺口集中在：陈旧实体的双事务两种提交顺序、ERROR 跨学院导出、audit mapper 失败、登出撤销、Dashboard 逆序响应、直接 presigned URL、真实 2GB/编码/首帧、浏览器和 Office。

## 10. Maintainability Concerns

- Coverage: High
- Inspected evidence: 大文件统计、服务职责、文档入口、重复权限/脱敏模式。
- Exclusions / limits: 未做逐方法复杂度和全量重复率量化。

最大维护风险是超大 service 中的隐式合同分叉：相邻入口对数据范围、脱敏、并发更新和对象生命周期采用不同规则。最小策略是先抽小型共享 guard/projector/mutation helper；不建议在发布阻断未清前进行大规模重写。

## 11. Design / Principles Concerns

- Coverage: High
- Inspected evidence: fail-fast、事务、CAS、权限边界、缓存和错误处理。
- Exclusions / limits: 未进行形式化模型检查。

项目在缓存发布窗口、视频 finalize、证书序列和生产配置上大量采用 fail-closed。违反点主要是 F-02 的 audit fail-open、F-03 的写模型不一致、F-01/F-13 的授权事实使用错误载体，以及 F-04 的无界输入工作量。

## 12. Release Concerns

- Coverage: High
- Inspected evidence: Dockerfiles、Compose、Nginx、CI、runbook、Phase 14、最终计划。
- Exclusions / limits: 未构建/启动生产镜像，未访问线上入口或 registry。

当前为 **NO-GO**。F-08、F-09、F-12、F-17、F-20 与全部 High 必须在稳定发布前关闭或由正式风险接受。Phase 0 PASS 只解除此前 U-004 阻断，不自动转化为项目 GO。

### 只能由用户或授权复核者执行的动态命令/动作

以下项目本轮**均未执行**；它们涉及 Docker、真实数据库/Redis/MinIO、网络、浏览器、故障注入、恢复或安全扫描，必须由用户本人或授权复核者在修复后的新最终 SHA 上执行：

```powershell
# 1) 专用隔离栈。确认 tcp-final-audit 名下无重要数据后，由用户执行。
docker compose -p tcp-final-audit -f docker-compose.dev.yml down -v
docker compose -p tcp-final-audit -f docker-compose.dev.yml up -d mysql redis minio

# 2) 修复后真实依赖聚焦门禁。由用户设置隔离目标环境变量后执行。
mvn -B -ntp -pl platform-boot -am "-Dit.test=Phase2SecurityIT,Phase7VideoReviewIT,Phase14E2EIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify

# 3) 修复 readiness 后的隔离故障反例；只能针对 tcp-final-audit。
docker compose -p tcp-final-audit -f docker-compose.dev.yml stop redis
curl.exe -fsS -i http://127.0.0.1:8080/api/ready
docker compose -p tcp-final-audit -f docker-compose.dev.yml start redis

# 4) 真实入口 TLS/响应头，由用户替换 release-host。
curl.exe -fsS -I https://<release-host>/

# 5) 依赖/镜像扫描会访问网络或 registry，由用户在批准的环境执行。
mvn -B -ntp org.owasp:dependency-check-maven:check
npm --prefix frontend audit --omit=dev
trivy image <immutable-image-digest>

# 6) 所有证据完成后，仅销毁该专用项目。
docker compose -p tcp-final-audit -f docker-compose.dev.yml down -v
```

此外须由用户人工完成：Chrome/Edge/Firefox 关键流程、Excel/WPS 文本字段核对、近 2GB 视频、非允许编码、合法容器但首帧不可解码、容量/性能 trace，以及 `docs/备份与恢复手册.md` 规定的隔离 PITR 演练。不得对共享开发栈、生产 schema 或生产 bucket 执行这些反例。

## 13. Documentation Analysis

- Coverage: High
- Inspected evidence: AGENTS、plan、tasks、CURRENT-EXECUTION-PLAN、Phase 14、AT 矩阵、备份恢复手册、复核报告。
- Exclusions / limits: 未核验组织外部 SOP。

### Documentation Summary

| Subtype | Count | Affected Docs | Recommended Action |
|---------|-------|---------------|-------------------|
| UserDocs | 0 | - | 保持现有业务说明 |
| OperatorDocs | 1 | 备份/PITR、TLS | 将待执行证据与已验证能力分开 |
| DeveloperDocs | 0 | AGENTS/HANDOFF | 当前入口较完整 |
| ApiDocs | 1 | 预签名链接合同 | 实现必须服从当前规格，或正式走 R10 |
| DecisionRecord | 1 | 单实例/多实例缓存 | 明确当前部署约束 |
| StaleDocs | 1 | Phase 14 | 修正 Phase 0 与最终审计状态 |

## 14. Privacy / Data Governance Analysis

- Coverage: High
- Inspected evidence: 证件号/出生日期投影、异常值、文件访问、审计、备份与保留。
- Exclusions / limits: 未检查学校外部数据分类表或法定保留期限。

### Privacy Summary

| Subtype | Count | Affected Data | Recommended Action |
|---------|-------|---------------|-------------------|
| DataInventory | 0 | 已识别主要敏感字段 | 继续维护 |
| Minimization | 1 | certificate/student/export | 默认脱敏 |
| AccessBoundary | 2 | ERROR 导出、presigned URL | 精确范围与登录绑定 |
| Retention | 2 | preview_json、孤立对象 | 有界保存与清理 |
| Deletion | 1 | 无元数据对象 | 补偿/台账 |
| Export | 1 | ERROR workbook | 指定批次与 owner |
| TelemetryPrivacy | 1 | 底层错误文本 | 固定外部错误消息 |

## 15. Accessibility / UX Correctness Analysis

- Coverage: Medium
- Inspected evidence: Dashboard、通知中心、loading/error、现有 CI。
- Exclusions / limits: 未做屏幕阅读器、对比度、响应式或浏览器动态检查。

### Accessibility Summary

| Subtype | Count | Affected Workflows | Recommended Action |
|---------|-------|-------------------|-------------------|
| SemanticStructure | 1 | 通知列表 | 使用 button/link |
| KeyboardFocus | 1 | 通知打开 | Tab/Enter/Space |
| ResponsiveVisual | 0 | 未发现静态阻断 | 浏览器复核 |
| ErrorState | 1 | Dashboard | 清空或标记陈旧 |
| LoadingState | 1 | Dashboard | latest-request-wins |
| UXStateCorrectness | 2 | Dashboard、logout | 与服务端状态对齐 |

## 16. Supply Chain / Reproducibility Analysis

- Coverage: High
- Inspected evidence: Maven/npm lock、Dockerfiles、Compose、Actions。
- Exclusions / limits: 未访问仓库、registry 或漏洞数据库，未生成 SBOM。

### Supply Chain Summary

| Subtype | Count | Affected Surface | Recommended Action |
|---------|-------|------------------|-------------------|
| DependencyProvenance | 1 | base images/actions | 固定 digest/SHA |
| Reproducibility | 1 | latest/tag-only | 不可变镜像标识 |
| CIIntegrity | 1 | workflow actions | commit SHA 与最小权限 |
| ArtifactProvenance | 1 | release image | build once + SBOM |
| RegistryHygiene | 1 | root containers | 非 root USER |

正向证据：`package-lock.json` v3 含 integrity，CI 使用 `npm ci`；Maven 父 POM 集中锁定大部分版本。

## 17. Cost / Resource Economics Analysis

- Coverage: High
- Inspected evidence: XLSX heap、视频临时空间、备份并发、对象孤儿、bundle。
- Exclusions / limits: 未取得生产请求量、存储账单或资源监控。

### Cost Summary

| Subtype | Count | Cost Driver | Recommended Action |
|---------|-------|-------------|-------------------|
| UnboundedWork | 3 | XLSX、备份重叠、孤立对象 | 上限、单飞、补偿 |
| ExternalApiCost | 0 | 无计费外部 API | - |
| LLMCost | 0 | 无 LLM | - |
| InfrastructureSizing | 2 | 1GB heap、2GB media | 受控容量验证 |
| ObservabilityCost | 0 | 未见高基数链路 | 后续再评估 |
| CostVisibility | 1 | 存储/备份 | 补容量与增长率指标 |

## 18. AI / LLM Safety Analysis

- Coverage: Not assessed
- Inspected evidence: 项目地图、依赖与源码命名搜索未发现生产 AI/LLM、RAG、agent 或模型工具调用。
- Exclusions / limits: 项目当前没有 AI 功能。

### AI Safety Summary

| Subtype | Count | Boundary Crossed | Recommended Action |
|---------|-------|------------------|-------------------|
| PromptInjection | 0 | 不适用 | - |
| ToolAuthorization | 0 | 不适用 | - |
| RAGLeakage | 0 | 不适用 | - |
| ModelFallback | 0 | 不适用 | - |
| OutputValidation | 0 | 不适用 | - |
| EvalGap | 0 | 不适用 | - |
| AbuseCost | 0 | 不适用 | - |

## 19. Observability / Operability Analysis

- Coverage: High
- Inspected evidence: health、日志、审计、备份记录、cache/video scheduler。
- Exclusions / limits: 未连接指标后端或告警系统。

### Signal Summary

| Subtype | Count | Critical Signals Missing | Recommended Action |
|---------|-------|--------------------------|-------------------|
| Logging | 2 | 审计失败、对象补偿失败 | 结构化且可告警 |
| Metrics | 3 | readiness、备份单飞、容量 | counter/gauge |
| Tracing | 1 | correlation id | 先贯通 HTTP/日志 |
| HealthCheck | 1 | dependency readiness | 拆 liveness/readiness |
| Alerting | 3 | 备份失败、孤儿、缓存陈旧 | 明确阈值与 runbook |
| Runbook | 1 | 最终发布执行 | 绑定候选与目标 |
| Debuggability | 1 | 外部错误不可泄漏 | correlation id |

## 20. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: application yml、prod profile、Compose、Nginx、Dockerfiles。
- Exclusions / limits: 未读取任何真实密钥、证书或生产环境变量。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| SchemaValidation | 1 | import resource caps | 启动校验正数/关系 |
| UnsafeDefault | 3 | HTTP、latest、root identity | 移除或失败关闭 |
| EnvironmentSeparation | 1 | single vs multi instance | 明确合同 |
| SecretConfig | 1 | MinIO root wiring | scoped service identity |
| FeatureFlag | 0 | 已有 guard 较完整 | 保持 |
| ConfigDocs | 2 | TLS/cache topology | 更新权威 runbook |

## 21. Data Integrity Analysis

- Coverage: High
- Inspected evidence: 事务、CAS、行锁、唯一键、逻辑备份、对象台账。
- Exclusions / limits: 未执行真实并发和恢复。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 2 | audit、object metadata | 同事务/补偿 |
| Idempotency | 1 | backup trigger | 单飞 |
| ConcurrencyConsistency | 1 | aggregate state/content | lock/version/CAS |
| MigrationSafety | 1 | 单向协议 | 前向修复与实演 |
| InvariantValidation | 2 | row size、scope identity | 写侧上限/精确列 |
| BackupRestore | 2 | representability、PITR | 共享合同与演练 |
| Reconciliation | 1 | object without metadata | durable cleanup |

## 22. Fallback / Defensive Code Analysis

- Coverage: High
- Inspected evidence: catch/fallback、审计、文件、备份、缓存和视频异常路径。
- Exclusions / limits: 未做全仓异常注入。

### Fallback Summary

| Subtype | Count | KeepWithAlert | FailFast | Remove |
|---------|-------|---------------|----------|--------|
| SilentFallback | 2 | 0 | 2 | 0 |
| EmptyCatch | 0 | 0 | 0 | 0 |
| CompatibilityBranch | 1 | 1 | 0 | 0 |
| SilentCorrection | 0 | 0 | 0 | 0 |
| DefensiveGuess | 1 | 0 | 1 | 0 |

审计写失败必须 fail-fast；对象补偿失败可保留业务错误，但必须产生持久清理信号。当前不建议恢复曾被回退的审计异步方案。

## 23. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: Phase 0 正式证据、Surefire/Failsafe 结构、测试夹具、当前离线执行结果。
- Exclusions / limits: 未重跑真实依赖、2GB、浏览器、Office 或 PITR。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| Phase 0 exact 7/33 | High | 证据绑定已独立 PASS | Keep |
| 后端 Surefire 201/201 | High | 不覆盖真实依赖 | Keep |
| 历史真实依赖 IT | Medium | 未绑定本轮完整最终审计 SHA 的新增发现 | Extend |
| 前端 lint/type/build | Medium | 行为错误可逃逸 | Keep + add behavior |
| Phase 7 “large” media | Low | 约 5MiB 不代表 2GB | Controlled environment |
| Browser/Office/PITR | None | 无本轮归档证据 | Add external evidence |

### Valuable Tests

Phase 0 source/target/evidence gate、数据范围 mapper 链、视频 finalize/worker、证书序列与迁移恢复测试提供了真实回归价值。

### Suspicious Tests

当前媒体 demo 探测使用 mock 或另一份短夹具，不能代表打包 demo、异常编码和 2GB；前端完全缺少行为测试。

### Missing Tests

F-01、F-02、F-03、F-06、F-07、F-15、F-17 对应的确定性反例必须加入候选门禁。

## 24. Type Safety Analysis

- Coverage: Medium
- Inspected evidence: TypeScript build、Java DTO/VO、JSON scope 与 status 字符串。
- Exclusions / limits: 未做全量 AST 类型断言盘点。

### Summary

| Subtype | Count | Critical | High | Medium | Low |
|---------|-------|----------|------|--------|-----|
| UnsafeBlock | 0 | 0 | 0 | 0 | 0 |
| TypeAssertion | 0 | 0 | 0 | 0 | 0 |
| InputBoundary | 2 | 0 | 0 | 2 | 0 |
| OutputLeak | 1 | 0 | 1 | 0 | 0 |
| BooleanTrap | 0 | 0 | 0 | 0 | 0 |
| StringlyTyped | 2 | 0 | 0 | 1 | 1 |
| ErrorType | 1 | 0 | 0 | 0 | 1 |

TypeScript `type-check` 通过。主要问题不是编译类型，而是用自由字符串/JSON 表达授权身份，以及错误文本和敏感投影缺少显式输出类型。

## 25. Frontend State Analysis

- Coverage: High
- Inspected evidence: user store、Axios refresh、router、Dashboard、Notice Center。
- Exclusions / limits: 未启动浏览器或执行组件测试。

### Summary

| Subtype | Count | Affected Components |
|---------|-------|---------------------|
| ComponentSize | 0 | 未作为当前 blocker |
| StateDuplication | 1 | local token vs server session |
| PropDrilling | 0 | 未发现突出问题 |
| EffectChain | 1 | year watch → concurrent load |
| UIBusinessCoupling | 1 | logout 只改本地 |
| DOMasState | 0 | 未发现 |
| RequestState | 2 | Dashboard、refresh/logout |
| RenderPerf | 1 | large vendor chunks |

## 26. Backend API Analysis

- Coverage: High
- Inspected evidence: 主要 controller、service、DataScope、VO、统一异常。
- Exclusions / limits: 未对 161 个 endpoint 全部发送 HTTP 请求。

### Summary

| Subtype | Count | Affected Endpoints |
|---------|-------|--------------------|
| ApiConsistency | 2 | logout、play URL |
| Validation | 2 | XLSX budgets、bizType |
| Auth | 4 | ERROR、certificate、generic upload、presigned |
| NplusOne | 0 | 未确认发布阻断 |
| Caching | 1 | multi-node conditional |
| ErrorResponse | 1 | raw exception text |
| BusinessLogic | 2 | aggregate mutation、backup single-flight |
| DataFlow | 3 | object metadata、stats scope、preview backup |

## 27. Dependency Weight Analysis

- Coverage: Medium
- Inspected evidence: POM、package-lock、frontend build chunks、Docker base images。
- Exclusions / limits: 未跑依赖树大小或漏洞数据库查询。

### Dependency Scoreboard

| Dependency | Status | Weight | Transitives | Used For | Recommended Action |
|------------|--------|--------|-------------|----------|-------------------|
| Spring Boot platform | Healthy | backend baseline | 未量化 | Web/data/security | Keep |
| Apache POI XSSF | Overweight on hot path | heap-sensitive | 未量化 | Excel import | Keep, switch reading mode |
| Naive UI | Overweight bundle | ~1340kB chunk | 未量化 | frontend UI | Keep, split |
| ECharts | Overweight bundle | ~1035kB chunk | 未量化 | dashboard charts | Lazy-load |
| AWS S3 SDK | Healthy | 未量化 | 未量化 | MinIO/S3 | Keep |

## 28. Code Consistency Analysis

- Coverage: High
- Inspected evidence: 相邻数据范围、脱敏、状态更新、上传和异常处理实现。
- Exclusions / limits: 未对所有格式/命名规则逐项计数。

同类入口存在明显合同漂移：`ensureBatchAccessible` 已避免 JSON substring，但统计仍使用；直传校验 bizType，通用上传不校验；状态转换用 CAS，内容编辑仍全实体更新；学生证件号脱敏，证书投影不脱敏。修复应优先复用已有正确模式。

## 29. Comment Coverage Analysis

- Coverage: Medium
- Inspected evidence: 高风险方法、迁移说明、缓存/视频/备份注释。
- Exclusions / limits: 未计算注释覆盖率。

复杂并发和发布协议已有较多“为什么”注释，这是正向资产。部分注释却会放大误解，例如 TLS “就绪”与实际注释模板、Phase 14 已勾选人工目标。应修正不真实的状态注释，不需要增加一般性行内注释。

## 30. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Single Responsibility (SRP) | 2 | Low | ExchangeServiceImpl、VideoReviewServiceImpl |
| Fail-Fast | 3 | High | audit、XLSX budget、readiness |
| Least Privilege | 4 | High | ERROR export、certificate PII、generic upload、MinIO root |
| Single Source of Truth | 3 | Medium | aggregate writes、scopeJson、release docs |
| Bounded Work | 2 | High | XLSX、backup overlap |
| Atomicity / Consistency | 3 | High | stale entity、audit、object metadata |

### Principles Respected

项目遵守分层与模块化单体、统一 Result/BizException、Flyway、逻辑删除、生产 secret fail-fast、Redis pending owner/refcount、视频 finalize fencing、证书序列行锁和大量真实依赖 IT。现有正确模式足以支持大部分最小修复，无需重建架构。

## 31. Architecture Analysis

### Architecture Summary

| Subtype | Count | Affected Areas | Recommended Action |
|---------|-------|----------------|--------------------|
| ModuleBoundary | 2 | exchange/video large services | 仅按职责逐步拆 |
| DependencyDirection | 0 | 模块依赖总体合理 | 保持 |
| StateOwnership | 3 | aggregate、session、object metadata | 单一 mutation/owner |
| BoundaryContract | 5 | scope、PII、presigned、backup、readiness | 显式合同与测试 |
| EvolutionRisk | 3 | multi-node cache、release image、docs | 固定部署边界 |

最优修复路线不是引入微服务、消息总线或复杂分布式锁。当前架构下，用精确 SQL 范围、短事务行锁/CAS、有界输入、对象补偿、服务端登出和可验证部署合同即可关闭主要风险。

## 32. Recommended Fix Order

### Fix Immediately

1. F-01 ERROR 导出跨学院泄漏。
2. F-02 审计 IP/审计失败开放。
3. F-03 陈旧实体覆盖状态机。
4. F-04 XLSX 堆放大。
5. F-05 敏感字段明文旁路。
6. F-06 服务端 logout 未调用。
7. F-07 预签名 URL 与登录绑定规格冲突。

### Fix Before Stable Release

F-08 至 F-20：TLS/端口、refresh token、文件入口与孤儿、MinIO 最小权限、统计范围、备份边界与单飞、readiness、Dashboard、前端行为测试、镜像证明，以及真实环境/灾备证据。

### Schedule Later

多节点 Caffeine 支持、超大 service 拆分、前端 chunk 预算和更完整的指标平台。只有确认多实例或性能目标后再扩展。

### Ignore for Now

不引入微服务、通用事件总线、复杂审计异步管线或 MinIO admin API 依赖；这些不是关闭当前 finding 的必要条件。

## 33. Quick Wins

- ERROR 导出强制 batchId + `ensureBatchAccessible`。
- 前端 logout 调用现有 API，并 `finally` 清本地。
- Dashboard 增加 request generation guard。
- 证书 VO 和生日统一默认脱敏。
- 通知列表改为语义 button/link。
- 修正 Phase 14 当前状态与 pending 证据。
- generic upload 无调用方则直接删除；有调用方再补最小权限。
- 为 import 先加保守行数/单元格/展开预算，之后再流式化。

## 34. Long-term Refactor Plan

1. **聚合写入统一化。** 动机是消除全实体陈旧覆盖；每个聚合建立一个 mutation 入口，采用行锁或统一 version/CAS，并以双事务测试保护。风险是 SQL 更新字段遗漏，测试需覆盖状态和内容两类写。
2. **导入预览有界化。** 动机是同时关闭 heap 与备份单行问题；先设上限，再以流式解析和分页预览替换全量 JSON。风险是错误行顺序与确认合同变化，需保持 batch/rowNo 可重复。
3. **文件台账一致化。** 动机是消除孤立对象与万能上传入口；先补精确补偿，再统一 intent → upload → READY 状态。风险是失败重试重复对象，需以唯一 key/idempotency 测试。
4. **发布证明产品化。** 动机是将源码 SHA、镜像 digest、目标身份、动态日志和清理证明绑定；在现有 CI/门禁上增量实现，不引入新平台。风险是外部环境差异，保留浏览器/Office/PITR 人工签字。
