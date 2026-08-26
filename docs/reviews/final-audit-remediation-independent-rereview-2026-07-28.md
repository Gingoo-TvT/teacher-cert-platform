# 最终全量审计整改候选独立增量复核（2026-07-28）

> 本报告是最终全量审计结束后的整改候选独立复核，不是重新执行最终全量审计。
>
> 正式结论：**CHANGES_REQUESTED / NO-GO**。原全量审计的
> **0 Critical / 7 High / 13 Medium / 4 Low** 状态不因本地候选自测而自动改写。

## Executive Summary

- 最终全量审计基线：`dfdfb9199215d856b70abb653cb65b6f9ed46282`。
- 当前仓库 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`，分支
  `codex/final-audit-high-remediation`。
- 被复核对象：HEAD 之后的未提交工作树。复核开始时冻结摘要为
  `tracked-diff-git-object=89adebc025c43fd90df2c3a0fb3fd2a09aece5ac`、
  `candidate-file-manifest-git-object=3659f2f7aa389d9cdb7c35497887b5982103632c`；
  离线门禁后 tracked diff 摘要保持不变。
- 本轮不是 immutable-SHA 复核：关键产品代码与测试仍含未跟踪文件，故任何
  `STATIC_CLOSED` 都只表示当前工作树中原失败条件已被移除，不构成正式 SHA 绑定 PASS。
- 24 个原 finding 的当前代码级矩阵为：**6 STATIC_CLOSED / 10 PARTIAL / 8 OPEN**。
  7 个 High 中没有一项达到正式动态关闭条件；其中 F-04 仍存在可确定触发的检查前 JSON
  大对象物化，原 High 风险尚未关闭。
- 本轮确认 **7 个需要整改的增量问题：1 High / 4 Medium / 2 Low**。其中 3 个是原 finding
  的残余，4 个是候选引入或证据层的新问题。
- 独立离线门禁通过：Surefire **48 suites / 339 tests**，0 failure/error/skip；后端
  **9/9 modules package**；前端 lint、type-check、两项 Node 脚本、production build；
  `git diff --check`。这些结果不覆盖真实 MySQL/Redis/MinIO、Failsafe、HTTP、浏览器、
  Docker/Compose 渲染或双连接交错。

## Project Map

| 区域 | 本轮主要复核面 |
|---|---|
| `platform-exchange` | ERROR 导出范围、XLSX 预算、预览/备份兼容、导入错误文本 |
| `platform-boot` | 审计切面、退出与媒体入口、controller 权限、真实 IT 契约 |
| `platform-business` | 六类聚合状态/内容写、敏感投影、媒体授权 |
| `platform-file` | 通用上传退役、intent-first、对象补偿、流式下载 |
| `platform-system` | 审计写、可信代理 IP、备份单飞与单行预算 |
| `platform-statistics` | 批次 owner-only 范围 |
| `frontend` | logout、迟到响应、Dashboard generation、通知键盘语义 |
| 部署/治理 | TLS、端口、MinIO 身份、readiness、镜像证据、Phase 文档状态 |

## Coverage Matrix

| 维度 | 覆盖度 | 已执行 | 未执行及原因 |
|---|---:|---|---|
| Security | High | 权限、DataScope、敏感投影、logout、媒体 Cookie、审计来源逐路径读码；相关 Surefire | 遵守用户安全边界，未做网络、扫描、攻击载荷或真实代理探测 |
| Data Integrity | High | 六类聚合 CAS/列级更新、文件 intent、备份锁、导入预算静态复核 | 未连接数据库，未跑双连接竞态、真实 named lock 或备份回放 |
| Testing Authenticity | High | 独立执行并解析本轮 Surefire XML；核对 Node 脚本与 Failsafe 源码 | 未执行依赖真实服务的 Failsafe |
| Frontend State | High | logout、request、router、Dashboard、通知源码；lint/type-check/build | 未启动浏览器，未运行 Pinia/Vue DOM、Playwright 或 axe |
| Release | Medium | Dockerfile、Compose、Nginx、CI 静态核对 | 未执行 Docker/Compose、镜像构建、TLS 或服务操作 |
| Documentation | High | 当前计划、PROGRESS、DEVLOG、Phase 05/07/14 与正式报告对照 | 外部运行证据未在本轮产生 |

## Finding Statistics

本表统计本轮确认的残余或新增问题，不重复计数仍按计划保持 OPEN 的既有发布项。

| Severity | Count | Residual | New |
|---|---:|---:|---:|
| Critical | 0 | 0 | 0 |
| High | 1 | 1 | 0 |
| Medium | 4 | 1 | 3 |
| Low | 2 | 1 | 1 |
| Info | 0 | 0 | 0 |
| **Total** | **7** | **3** | **4** |

## Top Risks

1. XLSX 预算仍在完整 JSON 字符串创建之后检查；共享字符串复用可用很小 OOXML 表示大量逻辑文本，
   使当前 16/64MiB、20k 行和 4096 字符门禁全部通过后才发生大堆物化。
2. `before=true` 审计只写 operation/operator/IP，不写 target 或 outcome；失败的改密、重置和导入
   在审计 UI 中与成功操作不可区分。
3. Redis 撤销失败时前端静默吞掉异常并跳登录页，用户会把“本地清理成功”误认为“服务端旧令牌已撤销”。
4. 敏感投影的新代码语义与 Phase 3 真实 IT 的明文断言冲突；当前 339/339 没有运行 Failsafe，
   因而完整门禁会确定性红灯。

## 原 24 项关闭矩阵

状态定义：

- `STATIC_CLOSED`：当前工作树静态上移除了原报告的具体失败条件；仍须提交 SHA 并完成适用门禁。
- `PARTIAL`：方向正确，但存在残余问题、测试冲突或必要动态证据缺口。
- `OPEN`：原问题仍未处理，或仅有计划。

| ID | 原 finding | 当前状态 | 独立结论 |
|---|---|---|---|
| F-01 | ERROR 导出跨学院 | PARTIAL | 已绑定 batch、类型、owner/school scope 并精确查询；仅 mock 证据，真实双 owner MySQL 门禁未执行 |
| F-02 | 审计来源伪造/失败开放 | PARTIAL | 可信代理与异常传播方向正确；真实同库回滚和 rendered 代理链未证，另有 `before=true` 新问题 |
| F-03 | 陈旧全实体覆盖状态/内容 | PARTIAL | 六类聚合已改状态 CAS 与列级 patch；真实双连接两种顺序未执行 |
| F-04 | XLSX 无界堆放大 | OPEN | 解析前预算有进展，但预览 JSON 仍先完整物化后检查 |
| F-05 | 敏感默认投影旁路 | PARTIAL | 产品代码默认脱敏；Phase 3 真实 IT 仍断言明文 |
| F-06 | logout 不撤销服务端会话 | PARTIAL | 正常路径已撤销；撤销失败被 UI 静默伪装为成功，真实 Redis/HTTP 门禁未执行 |
| F-07 | 复制预签名 URL 绕过登录 | PARTIAL | GET 已改登录绑定的应用取流；三类 Range/logout/换号动态矩阵未执行 |
| F-08 | HTTP/TLS 与端口过度发布 | OPEN | 443/HSTS 仍是注释，80/8080/9000/9001 仍发布 |
| F-09 | refresh token 暴露给 JavaScript | OPEN | 仍存 `localStorage`，未实现轮换/重放拒绝 |
| F-10 | 通用上传过宽 | STATIC_CLOSED | 通用 controller 已删除，产品走领域入口 |
| F-11 | 对象成功、元数据失败的孤儿 | STATIC_CLOSED | intent-first、唯一 key、FAILED 与精确补偿已落地 |
| F-12 | 后端使用 MinIO root | OPEN | backend 仍复用 MinIO root 身份 |
| F-13 | 统计批次 substring 范围 | STATIC_CLOSED | 非校级统一按精确 operator owner |
| F-14 | 应用可写入备份不可表示的行 | PARTIAL | 默认值可表示；两套可独立覆盖的配置可再次分叉 |
| F-15 | 全量备份无 single-flight | PARTIAL | `GET_LOCK` 代码合理；仅 Mockito，无真实双连接/named-lock 证据 |
| F-16 | liveness 被当 readiness | OPEN | `/api/health` 仍无条件 UP |
| F-17 | Dashboard 旧响应覆盖新学年 | STATIC_CLOSED | 产品代码 generation guard 正确；测试只直测 helper |
| F-18 | 无前端行为/a11y 门禁 | PARTIAL | 新增两个 Node 脚本，但未执行 Pinia/Vue/DOM/路由/axe |
| F-19 | 镜像与 CI 不可复现 | OPEN | 基础镜像未 pin digest、容器无 USER、CI 无最终镜像/SBOM |
| F-20 | 真实发布/灾备证据不完整 | OPEN | PITR、浏览器、真实边界和灾备证据仍待执行 |
| F-21 | 底层异常文本到客户端 | PARTIAL | FileService 已固定消息；Exchange 导入仍返回非 BizException 的原始消息 |
| F-22 | 通知键盘不可达 | STATIC_CLOSED | role/tabindex/Enter/Space/focus-visible 已补齐 |
| F-23 | 多节点本地缓存失效 | OPEN | 仍按正式计划保留 |
| F-24 | 活动验收文档漂移 | STATIC_CLOSED | Phase 14 已纠正；但 Phase 05/07 又提前勾选媒体动态语义 |

## Detailed Findings

### Finding: XLSX 预览预算在大对象物化之后才执行

- Severity: High
- Confidence: High
- Category: Data Integrity
- Status: Confirmed residual of F-04
- Affected area: `platform-exchange` 标准导入预校验
- Evidence:
  - `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:220-222`
    先执行 `writeJson(previews)`，随后才检查 20MiB。
  - `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/support/ExchangeExcelHelper.java:529-605`
    只限制单元格原始字符与模板列数，没有限制共享字符串被引用后的逻辑展开量。
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/exchange/support/ExchangeImportResourceBudgetTest.java:181-188`
    仅对已经构造好的 4/5 字节字符串直接调用预算检查。
- Problem: 当前门禁可以在 OOXML 层保持很小，却在 JSON 序列化时把重复 sharedStrings 展开为远大于
  20MiB 的完整 Java String；异常是在高额堆分配完成后才产生。
- Why it matters: 合法导入权限仍可触发进程级 OOM 或长时间 GC，原 High 的核心“工作量必须在物化前
  有界”没有闭合。
- Realistic failure scenario: 20k 行复用若干个接近 4096 字符的共享字符串；ZIP 展开量、行数和
  单元格限制均通过，但 `writeJson` 在检查前生成数百 MiB JSON。
- Minimal fix: 使用带 20MiB+1 硬上限的输出流让 Jackson 直接写 UTF-8；超过上限立即终止，
  只有完整输出未超限时才转换为最终 String。
- Better long-term fix: 在 SAX 读取阶段同时累计“逻辑字段 UTF-8 预算”，并把预览持久化改成分行或
  分页结构，避免整批 JSON 单行。
- Regression test suggestion: 构造 sharedStrings 被 20k 行重复引用的工作簿，在低堆 JVM 中验证
  于 JSON 大对象创建前以稳定业务异常退出；同时保留边界内成功样本。
- Estimated effort: 0.5–1 day

### Finding: `before=true` 使失败尝试呈现为成功样式审计

- Severity: Medium
- Confidence: High
- Category: Security
- Status: Confirmed new
- Affected area: 审计切面及改密、密码重置、导入入口
- Evidence:
  - `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/aspect/AuditLogAspect.java:35-37,54-60`
    先写仅含 operation/operator/IP 的审计行，再执行目标。
  - `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/controller/AuthController.java:67-71`、
    `SystemSecurityController.java:81-84`、`ExchangeController.java:76-91` 使用该模式。
  - `frontend/src/views/SystemAuditView.vue:104-113` 没有 outcome 字段可区分尝试、成功与失败。
- Problem: 旧密码错误、目标不存在/越权/CAS 冲突、批次越权或认领失败时，已提交的审计行仍显示
  `changePwd`、`resetPassword` 或 `import`，且没有目标和终态。
- Why it matters: 审计记录不再可靠表达“实际发生的写操作”，会误导事件调查与责任认定。
- Realistic failure scenario: 管理员重置不存在的用户失败，审计 UI 仍出现一条与成功重置无法区分的记录。
- Minimal fix: 对普通同库写恢复默认同事务；确属外部/多事务边界的操作记录明确的 `Attempt/PENDING`
  及 target，并在结束时写 `SUCCEEDED/FAILED` 终态。
- Better long-term fix: 为审计模型增加结构化 outcome、request/correlation id 和失败类别，避免靠
  operation 文本推断结果。
- Regression test suggestion: 分别让旧密码校验、用户 CAS、批次认领失败，验证不存在成功样式记录，
  或存在明确 FAILED/Attempt 记录。
- Estimated effort: 0.5–1 day

### Finding: 服务端 logout 撤销失败被界面静默伪装成成功

- Severity: Medium
- Confidence: High
- Category: Frontend State
- Status: Confirmed new
- Affected area: 前端用户 store 与主布局退出流程
- Evidence:
  - `frontend/src/stores/user.ts:91-97` 在远端请求失败后通过 `finally` 清空本地会话并继续抛错。
  - `frontend/src/layouts/MainLayout.vue:207-215` 完全吞掉该异常并无条件跳到登录页。
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/controller/AuthControllerLogoutTest.java:48-63`
    明确覆盖了服务端撤销失败，但前端脚本没有验证失败提示。
- Problem: 本地秘密清理是正确的，但 UI 把“本地退出”展示成“服务端撤销已确认”；Redis 故障时旧
  access/refresh token 仍可能有效。
- Why it matters: 用户无法得知其他标签页、复制 token 或已泄漏 token 尚未被撤销。
- Realistic failure scenario: logout 时 Redis 不可用，当前页进入登录页；另一标签页或已复制 token
  继续访问，用户没有收到任何风险提示。
- Minimal fix: 继续清理本地会话，但明确提示“服务端撤销未确认”，且审计不得记录为成功 logout。
- Better long-term fix: 将 session generation 置于可持久化、可恢复的撤销源，并为撤销失败提供受控重试。
- Regression test suggestion: 在组件/store 行为测试中让 logout API 失败，断言本地清理、警告展示、
  无成功审计三者同时成立。
- Estimated effort: 2–4 hours

### Finding: 新脱敏语义与 Phase 3 真实 IT 确定性冲突

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity
- Status: Confirmed new gate blocker
- Affected area: 学生详情敏感投影与 Failsafe
- Evidence:
  - `platform-business/src/main/java/cn/edu/gpnu/platform/business/student/service/impl/StudentServiceImpl.java:615-624`
    普通详情无条件遮蔽身份证和生日。
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/business/student/service/impl/SensitiveProjectionTest.java:54-69`
    新 Surefire 明确要求普通详情保持脱敏。
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase3StudentIT.java:435-438`
    对同一详情接口仍断言生日明文 `2002/01/03`。
- Problem: 339/339 只覆盖 Surefire；完整 `verify` 运行 Phase 3 时会因旧契约断言直接失败。
- Why it matters: 不能用“真实依赖待执行”掩盖已经可从源码确定的红灯；测试套件当前表达两套相反合同。
- Realistic failure scenario: 在隔离 MySQL 上运行 Phase 3，产品正确返回掩码生日，但 IT 以明文期望失败。
- Minimal fix: 把普通详情断言改为掩码，并新增授权明文专用接口的正反例和定向审计断言。
- Better long-term fix: 把敏感投影合同抽成共享测试矩阵，普通/明文端点和权限组合只维护一份期望。
- Regression test suggestion: 修正后运行完整 Phase 3/10 Failsafe，要求普通接口始终脱敏、明文接口仅
  敏感权限和正确 DataScope 可用。
- Estimated effort: 1–2 hours plus real-service gate

### Finding: 预览与备份上限由两套可独立覆盖的配置控制

- Severity: Medium
- Confidence: High
- Category: Configuration
- Status: Confirmed residual of F-14
- Affected area: 导入预览与数据库备份兼容
- Evidence:
  - `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/support/ExchangeImportProperties.java:28-45`
    用自身复制的 `backupMaxSqlStatementBytes` 校验预览上限。
  - `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:120-121`
    备份实际读取 `platform.backup.max-sql-statement-bytes`。
  - `platform-boot/src/main/resources/application.yml:46-48` 仅在默认 YAML/约定环境变量路径上把两者接到
    同一个值，Spring 命令行或规范化环境属性仍可单独覆盖实际备份上限。
- Problem: 向下收紧实际备份单语句上限时，Exchange 启动校验仍可看到 32MiB 副本并允许更大的
  preview_json。
- Why it matters: 应用可再次写入备份无法表示的数据，直到灾备任务才失败。
- Realistic failure scenario: 运维设置 `platform.backup.max-sql-statement-bytes=8388608` 做 fail-closed
  演练；预览仍按 32MiB 校验，随后逻辑备份在该行失败。
- Minimal fix: 删除复制字段，让导入校验与备份服务注入同一个类型化配置对象和同一个最终解析值。
- Better long-term fix: 建立启动期跨模块配置不变量，并对所有支持的属性来源做绑定测试。
- Regression test suggestion: 用命令行属性和规范化环境属性分别向下覆盖上限，应用应启动失败或同步
  收紧预览预算。
- Estimated effort: 2–4 hours

### Finding: Exchange 导入仍把底层异常文本返回客户端

- Severity: Low
- Confidence: High
- Category: Security
- Status: Confirmed residual of F-21
- Affected area: 导入确认返回值与错误明细
- Evidence:
  - `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:277-283`
    将 `importFailureMessage` 加入客户端 `messages` 并写错误明细。
  - 同文件 `1822-1833` 对非 `BizException` 直接返回任意 `e.getMessage()`。
- Problem: SQL、驱动或内部异常可把表名、约束、SQL 片段或数据细节带入 API 响应和可下载错误报告。
- Why it matters: 原 Low 只修了 FileService，项目级错误合同仍不稳定并泄漏内部实现。
- Realistic failure scenario: 单行导入触发数据库约束或驱动异常，响应的“第 N 行”消息包含底层 SQL 文本。
- Minimal fix: 非 BizException 只返回固定领域消息；完整 cause 仅写内部日志并附 correlation id。
- Better long-term fix: 为导入失败建立稳定错误码表，不从异常文本推导客户端合同。
- Regression test suggestion: 注入含唯一哨兵的底层异常，API messages、错误明细与报告均不得出现哨兵。
- Estimated effort: 1–2 hours

### Finding: Phase 05/07 在动态媒体门禁前提前勾选

- Severity: Low
- Confidence: High
- Category: Documentation
- Status: Confirmed new
- Affected area: 活动验收文档
- Evidence:
  - `docs/phase-05-文件与过程性材料.md:42` 与
    `docs/phase-07-视频评审.md:68` 已把新媒体 Cookie/退出语义标为 `[x]`。
  - `docs/CURRENT-EXECUTION-PLAN.md:23-26` 同时明确 Range、退出、换号动态门禁尚未完成。
- Problem: 同一工作树中的权威状态与阶段验收勾选互相矛盾。
- Why it matters: 后续审计者可能把代码候选误当作已执行的真实 HTTP/Redis/媒体证据。
- Realistic failure scenario: 复核者只读 Phase 文档，误判媒体访问已完成独立动态验证并放行。
- Minimal fix: 改回 `[ ]`/`[~]`，或明确写“代码候选完成；独立动态证据待补”。
- Better long-term fix: 阶段勾选引用单一证据索引及候选 SHA，避免正文重复维护状态。
- Regression test suggestion: 文档 gate 检查 `[x]` 项必须引用存在且候选绑定的正式 PASS 证据。
- Estimated effort: 15–30 minutes

## Security Analysis

- F-01 的产品查询已按 `batchId` 精确绑定 owner/school scope；没有发现新的静态跨学院旁路。
- F-02 的可信代理 allowlist、右向左 XFF 解析与审计异常传播方向正确，但真实同库回滚和实际代理链
  尚未证明。
- F-05 普通投影默认脱敏、明文专用入口权限/范围/审计方向正确；当前阻断是旧 Failsafe 合同。
- F-06/F-07 正常路径有实质改进，但撤销失败提示、三类资源真实 Range/logout/换号仍未闭环。
- F-09 refresh token、F-12 MinIO root、TLS/端口继续 OPEN。

## Data Integrity Analysis

- F-03 原报告列举的 Certificate、Student、Training、Ability、Exemption、Material 已使用状态 CAS
  和列级 patch，未再发现“状态流转提交陈旧全实体”的原路径。
- 同状态内容编辑仍是 last-write-wins，这是更宽的一致性边界，不冒充本轮原 finding 未修；
  但后续可在明确业务版本合同后单列处理。
- F-11 的 intent-first 和 FAILED 台账让补偿失败可观察，原“不可发现孤儿”静态关闭。
- F-15 的 MySQL named lock 设计可覆盖同库多实例，但当前两项 Mockito 没有 acquisition-positive、
  双线程或真实连接行为。

## Configuration Analysis

- 默认 YAML 和文档化的 `BACKUP_MAX_SQL_STATEMENT_BYTES` 路径会同时设置导入与备份上限。
- Spring 命令行、`SPRING_APPLICATION_JSON` 或规范化属性环境变量仍可只覆盖真正的备份属性，
  因而 F-14 需要改为单一类型化配置来源。
- 媒体 Cookie 的 Max-Age 仍只按请求值与 3600 秒上限计算，未绑定 access token 剩余寿命；
  JWT 到期后后端仍会拒绝，所以这是 carry-forward Medium，不是越权延寿 High。

## Testing Authenticity Analysis

| 证据 | 本轮结论 | 真实性边界 |
|---|---|---|
| Surefire | 48 suites / 339 tests，0 failure/error/skip | 独立执行并解析本轮新鲜 XML；不含 `*IT` |
| `-Xmx128m` 资源反例 | 测试本身可通过 | 50k 短行在 `maxRows=100` 早拒；preview 预算只测 4/5 字节字符串，不覆盖 sharedStrings 或 prevalidate |
| High/Medium focused | 测试存在且由全仓 Surefire覆盖 | 多数为 Mockito/source-contract，不能替代真实 MySQL/Redis/HTTP |
| Dashboard Node 脚本 | PASS | 只直测 helper；组件删除 helper 接线仍可绿 |
| Logout Node 脚本 | PASS | 主要读取源码做正则；未执行 Pinia/Axios/router |
| Phase 10 换绑 IT | 源码存在 | 本候选没有对应新鲜 Failsafe XML，且用例应先断言 prevalidate 成功/错误原因 |
| Phase 3 敏感投影 IT | 源码冲突 | 当前完整门禁确定性失败 |
| Backup single-flight | 2 个 Mockito PASS | 无两个真实连接或实际 MySQL named lock |

首次 Maven 调用被复核工具的 1 秒超时中断，没有产生测试结论；随后完整重跑成功。前端 build
在沙箱内因 esbuild `spawn EPERM` 未开始构建，获准仅在沙箱外重跑同一离线命令后成功；均为
执行环境事实，不计产品失败。

## Frontend State Analysis

- Dashboard 产品组件对 success/failure/finally 都校验 generation，F-17 代码级关闭。
- 通知行具备 role、tabindex、Enter/Space 和 focus-visible，F-22 代码级关闭。
- 两个 Node 脚本不是组件行为门禁，因此 F-18 仍 PARTIAL；没有 Vitest、Playwright、axe、路由权限、
  上传或键盘 DOM 门禁。
- logout 正常路径调用服务端并清理本地；失败提示缺失形成新增 Medium。

## Release Analysis

- 仍未启用实际 443/HSTS/CSP，Compose 仍发布 backend 与 MinIO 端口。
- backend 仍复用 MinIO root；health 仍是 liveness；镜像 tag、容器用户、Actions/SBOM 仍未闭环。
- 本轮没有执行 Docker、Compose config、镜像、网络、服务、浏览器、数据库、Redis 或 MinIO。
- 当前候选没有不可变提交 SHA，不能进入部署、切流或稳定发布判断。

## Documentation Analysis

- Phase 14 已正确保留最终审计 `CHANGES_REQUESTED / NO-GO`，原 F-24 主问题代码级关闭。
- Phase 05/07 对媒体动态语义提前 `[x]`，形成新的 Low 状态漂移。
- 当前计划诚实保留 TLS、refresh、MinIO 身份、readiness、完整前端门禁、镜像与灾备证据，
  这些不得被本轮离线结果覆盖。

## Recommended Fix Order

1. 修复 High：让 preview JSON 在受限输出流中序列化，补真实 sharedStrings/低堆 prevalidate 反例。
2. 修复三个确定性 Medium：审计 outcome/target、logout 撤销失败提示、Phase 3 脱敏 IT 冲突。
3. 合并 F-14 的配置来源；修复 Exchange 原始异常文本与 Phase 05/07 状态。
4. 提交不可变候选并绑定 SHA 后，由用户或获授权环境执行真实 MySQL/Redis/HTTP/媒体/Compose 门禁。
5. 再按原队列处理 7 个 OPEN Medium 与多节点缓存 Low；不为本轮问题引入额外服务或复杂架构。

## Quick Wins

- `importFailureMessage` 的非 BizException 分支改为固定领域文本并增加哨兵测试。
- 修正 Phase 3 普通详情生日断言，并补明文专用端点断言。
- Phase 05/07 的媒体条目恢复为 pending。
- Dashboard 脚本至少增加“组件仍 import/调用 helper”的接线断言；这仍不能替代后续真实组件测试。

## 独立门禁结果

| 命令/核验 | 结果 |
|---|---|
| `mvn -B -ntp -o test` | PASS；48 suites / 339 tests / 0 failure/error/skip |
| `mvn -B -ntp -o -DskipTests package` | PASS；9/9 modules，Checkstyle 0 |
| `npm --prefix frontend run lint` | PASS |
| `npm --prefix frontend run type-check` | PASS |
| `npm --prefix frontend run test:dashboard-latest-request` | PASS，真实性有限 |
| `npm --prefix frontend run test:auth-logout-contract` | PASS，真实性有限 |
| `npm --prefix frontend run build` | PASS；仅既有大 chunk warning |
| `git diff --check HEAD` | PASS |

## 最终裁定

本轮独立复核结论为 **CHANGES_REQUESTED / NO-GO**：

- 不重跑最终全量审计，也不推翻其已结束的事实；
- 不接受“7 High 已全部完成”的候选表述：F-04 仍有直接残余，其他 High 仍需对应动态证据，
  F-05 还存在确定性 Failsafe 冲突；
- 不接受“6 Medium / 3 Low 已全部关闭”的候选表述：F-14、F-15、F-21 未正式闭环，并新增
  审计语义、logout 失败语义、测试合同和文档状态问题；
- 不授权 merge、push、deploy 或切流；
- 未执行任何用户禁止的 cyber 类动作；所有真实依赖/网络/浏览器门禁继续交由用户或获授权环境。
