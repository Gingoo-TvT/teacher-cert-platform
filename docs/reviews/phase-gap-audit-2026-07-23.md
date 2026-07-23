# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform 缺失阶段复核（Phase 0、29、35b、36–53）
**Audit mode:** security, configuration, data-integrity, testing-authenticity, release
**Date:** 2026-07-23
**Reviewer:** Codex（Claude 不可用期间，经用户明确授权执行独立复核）

---

## 1. Executive Summary

本轮补齐此前没有标准报告的 Phase 0、29、35b、36–53。复核基于冻结提交、当前源码、阶段规格、两个全新 schema、真实 MySQL/Redis/MinIO、前端门禁、Compose 解析及 `mvn clean verify` 265/265。14 个阶段可判 PASS；Phase 0、39、41、42、44、47、53 判定 CHANGES REQUESTED。

最严重的问题不是测试红灯，而是现有测试没有覆盖的跨事务和恢复路径：学院删除可与子记录创建交错而产生孤儿；应用逻辑备份按手册回放到 Flyway schema 会与种子数据主键冲突；导入进行中允许回滚，回滚快照之后仍可继续提交新行；demo 视频是 528 字节、零样本轨的占位容器，不能证明视频流程可演示。另有阶段完成声明与当前实现不一致、MinIO 生命周期读取失败后可能覆盖其它规则等发布风险。

### Score Dashboard

```
Security        ███████░░░  7.0  A   既有鉴权扎实；初始化脚本仍直接拼接 SQL
Stability       ██████░░░░  6.0  B   主回归绿；删除/回滚竞态与生命周期覆盖风险未测
Testing         ███████░░░  7.0  A   真实依赖 265/265；恢复演练和关键并发反例缺失
Release         █████░░░░░  5.0  B   多阶段证据已补；Phase 0/53 与发布验收仍不闭环
─────────────────────────────────────
Overall         ██████░░░░  6.3  B
```

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 4 | 4 | 0 |
| Medium | 5 | 5 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **9** | **9** | **0** |

## 2. Project Map

本轮阶段跨越前端体验、安全加固、容量、并发、备份、缓存、清理、导入导出和 demo 数据。核心可信边界有三类：父子数据的并发创建/删除，批处理的逐行提交/批次状态，以及“可生成产物”与“可恢复、可播放、可发布”的差别。Phase 29、35b、36–38、40、43、45–46、48–52 在当前范围未发现 Blocker/Major；其余阶段的详细结论见逐阶段报告。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | High | 生产 profile、安全配置、初始化脚本、鉴权与状态守卫 | 未做公网渗透和联网依赖漏洞扫描 |
| Configuration | High | dev/prod Compose、profile 配置、MinIO 生命周期、CI | 未接入真实证书、域名和 secret store |
| Data integrity | High | 组织删除、导入/回滚、备份、证书与视频并发逻辑 | 未执行网络分区和进程强杀故障注入 |
| Testing authenticity | High | 新 schema、真实三依赖、Surefire/Failsafe、前端检查、测试载荷 | 未启动浏览器或应用常驻服务；遵守 headless 禁令 |
| Release | High | 阶段记录、构建、Compose、demo 资源和恢复手册 | 未部署生产镜像、未执行真实灾备切换 |

## 3. Top Risks

1. **PG-H1 · High**：Phase 39 删除学院与创建专业/用户没有共享锁，应用层无外键策略下可产生孤儿。
2. **PG-H2 · High**：Phase 41 数据备份使用普通 `INSERT`，按手册回放到已迁移 schema 会与 Flyway 种子冲突。
3. **PG-H3 · High**：Phase 42 允许活跃 `IMPORTING` 批次回滚，导入线程可在回滚快照之后继续提交数据。
4. **PG-H4 · High**：Phase 53 的视频样例只有空轨道，没有可播放帧，不能完成角色 demo 视频验收。

## 4. Detailed Findings

### Finding: PG-H1 学院删除与子记录创建存在 TOCTOU 竞态

- Severity: High
- Confidence: High
- Category: Data Integrity
- Status: Confirmed
- Affected area: Phase 39 组织删除
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/OrganizationServiceImpl.java:108-121,164-177`
  - Relevant behavior: `deleteCollege` 先分别计数专业和用户再软删；`createMajor` 只读取启用学院后插入，没有锁住同一父行。用户/学生创建也没有与学院删除共享串行化协议。
- Problem: 删除检查与删除不是对所有子记录创建者可见的原子不变量。
- Why it matters: 项目明确不加数据库外键后，应用层就是唯一完整性防线；当前并发窗口可留下引用已删除学院的专业或账号。
- Realistic failure scenario: 删除事务完成两次 count=0 后，另一事务用先前读到的启用学院插入专业，随后删除事务软删学院。
- Minimal fix: 删除学院先锁定父行，并让所有创建/移动专业、学生和职工的路径以相同顺序锁定父行后再写。
- Better long-term fix: 将“删除”改为停用，或引入可验证的父状态/约束协议和定期孤儿对账。
- Regression test suggestion: 两线程屏障控制 `deleteCollege` 与 `createMajor/createUser/createStudent` 交错，最终不得出现活跃子记录指向已删除学院。
- Estimated effort: 1–2 days

### Finding: PG-H2 逻辑备份无法按恢复手册回放

- Severity: High
- Confidence: High
- Category: Data Integrity
- Status: Confirmed
- Affected area: Phase 41.2 应用备份与恢复
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:60-76,197-223`
  - File: `docs/备份与恢复手册.md:64-77`
  - Relevant behavior: 备份包含 `sys_region/sys_dict_type/sys_permission/sys_role/sys_param/sys_user` 等 Flyway 已种子化表，并输出普通 `INSERT INTO`；手册要求先 Flyway 建 schema 再回放。
- Problem: Flyway 建表同时已写入相同主键/唯一键种子，回放会在第一批重叠行处发生重复键错误。现有 IT 只检查 gzip 中含 INSERT，没有真实回放。
- Why it matters: 备份存在不等于可恢复；灾难发生时会在最关键路径才暴露不可用。
- Realistic failure scenario: 运维在新库执行迁移后回放产物，`sys_region` 或 RBAC 种子行冲突，mysql 客户端中止，业务数据未恢复完整。
- Minimal fix: 明确定义可执行恢复策略，例如迁移后按依赖顺序清空受管表再回放，或输出安全、确定性的 upsert/replace 脚本。
- Better long-term fix: 在 CI/演练环境把备份恢复到全新 schema，比较关键表行数和校验和，并定期做灾备演练。
- Regression test suggestion: 真实生成备份，迁移 scratch schema，按手册回放并验证全部表、外键语义、逻辑删除行和关键校验和。
- Estimated effort: 2–4 days

### Finding: PG-H3 活跃导入与回滚可同时写入同一批次

- Severity: High
- Confidence: High
- Category: Data Integrity
- Status: Confirmed
- Affected area: Phase 42.2 导入原子认领与回滚
- Evidence:
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:239-292,295-335`
  - Relevant behavior: 每行通过 `REQUIRES_NEW` 单独提交；`rollback` 接受 `IMPORTING`，一次读取当前引用后回滚；confirm 收尾的条件更新不检查受影响行数。
- Problem: 回滚读完引用后，导入线程仍可提交新行。批次最终显示 `ROLLED_BACK`，但晚提交数据留存；confirm 还会返回本地计算的 `IMPORTED/FAILED` 状态。
- Why it matters: 批次状态、回滚审计和实际业务数据不一致，且常规重试无法可靠判断残留范围。
- Realistic failure scenario: confirm 已导入两行，rollback 撤销这两行并改终态；confirm 随后提交第三行，最终状态守卫命中 0 行但结果仍返回成功。
- Minimal fix: 不允许回滚仍在运行的 `IMPORTING`；先使用租约/心跳确认工作者已失效，或原子切换到 `ROLLING_BACK` 并让导入者逐行检查后停止，再做完整对账。
- Better long-term fix: 使用持久任务状态机、幂等行键和可重复执行的补偿器，批次终态由单一协调者结算。
- Regression test suggestion: 两线程屏障让 rollback 在 confirm 行循环中间进入，最终批次状态与实际记录必须严格一致且无晚提交。
- Estimated effort: 2–4 days

### Finding: PG-H4 demo 视频不可播放

- Severity: High
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: Phase 53 demo/mock 数据
- Evidence:
  - File: `scripts/gen-demo-samples.py:5-9,71-96`
  - Resource: `platform-boot/src/main/resources/db/demo/sample-video.mp4`（528 bytes）
  - Relevant behavior: 生成器明确创建 0 samples、0-length track，并声明无法生成真正可播放帧、浏览器播放未经验证。
- Problem: 阶段目标写明各角色 demo/mock 数据“含视频”，但交付的是文件类型占位符，不是可用于评审和播放演示的视频。
- Why it matters: 最终用户验收会在播放器和角色流程上直接失败；现有 demo-on 数据计数与幂等测试无法发现该缺口。
- Realistic failure scenario: 演示账号进入视频评审页面，下载/请求成功但播放器无画面、无可评审内容。
- Minimal fix: 纳入一个体积受控、许可明确、含真实视频帧和音视频时长元数据的 MP4 样例。
- Better long-term fix: demo 初始化后执行浏览器播放冒烟并验证 duration、首帧和鉴权 URL。
- Regression test suggestion: 媒体探测断言样例至少一条视频轨、样本数大于 0、时长大于 0；最终浏览器验收能播放首帧。
- Estimated effort: 2–6 hours

### Finding: PG-M1 Phase 0 验收基线与当前工程漂移且未闭环

- Severity: Medium
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: Phase 0 脚手架验收与 CI
- Evidence: `docs/phase-00-脚手架.md:19-21,62-83` 仍要求 pnpm、mock 登录、Spotless/Checkstyle/ESLint，十项清单均未勾选；当前项目采用 npm、真实认证，CI 也未执行所列 lint。
- Problem: 无法从权威规格判断哪些条款已被正式替代，且明确 lint 门禁不存在。
- Why it matters: 阶段 PASS 将建立在推断而不是可追溯验收上。
- Realistic failure scenario: 新提交引入格式或静态规则错误，本地 build 仍通过，CI 也不执行文档承诺的 lint；团队却依据“Phase 0 已完成”继续发布。
- Minimal fix: 对照当前架构修订 Phase 0 验收项并补 lint/Swagger/预签名过期证据。
- Better long-term fix: 将阶段验收命令直接纳入 CI，文档从报告引用机器产物。
- Regression test suggestion: fresh runner 执行修订后的全部 Phase 0 清单。
- Estimated effort: 0.5–1 day

### Finding: PG-M2 MySQL 初始化脚本直接把环境变量拼入 SQL

- Severity: Medium
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: Phase 41.1 数据库账号初始化
- Evidence: `deploy/mysql-init/01-app-user.sh:19-20,23-39` 明示要求口令避免单引号/反斜杠，并直接插值到 heredoc SQL。
- Problem: 合法随机 secret 可能令初始化失败；受污染变量还能改变 SQL 语义。
- Why it matters: 生产 secret 轮换和首次建卷不应依赖人工避开字符集。
- Realistic failure scenario: secret store 生成含单引号的高熵口令，首次启动 MySQL 卷时 heredoc SQL 语法中断，应用账号未创建且部署失败。
- Minimal fix: 严格校验用户名/库名标识符，并通过安全转义或参数化机制处理口令。
- Better long-term fix: 由受控 secrets/bootstrap 工具创建账号，不在 shell 中拼接 SQL。
- Regression test suggestion: 覆盖单引号、反斜杠、空格和 shell 特殊字符的合法口令。
- Estimated effort: 2–4 hours

### Finding: PG-M3 Phase 44 的“通知批量插入完成”声明已被后续实现撤销

- Severity: Medium
- Confidence: High
- Category: Performance
- Status: Confirmed
- Affected area: Phase 44a/44f 通知扇出
- Evidence: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/InAppNotifyChannel.java:36-50` 的 `sendBatch` 当前仍逐用户 `insert`；阶段计划仍将 P1-5 记为批量插入已修复。
- Problem: 44f 为正确性回退是合理的，但它同时撤销了 44a 的性能交付，文档却仍声称闭环。
- Why it matters: 性能债务被错误隐藏，后续容量评估会基于不存在的优化。
- Realistic failure scenario: 某评审事件需要通知大量成员，应用按用户执行 N 次 INSERT，容量测试或截止日流量下出现延迟，而上线清单仍把该项视为已经优化。
- Minimal fix: 将 P1-5 状态改为诚实推迟，或在同一事务/连接上实现真正 multi-values insert 并补锁等待回归。
- Better long-term fix: 持久化 outbox 后异步批量扇出。
- Regression test suggestion: 大收件人集验证 SQL 次数/耗时，并回归二审锁等待。
- Estimated effort: 0.5–2 days

### Finding: PG-M4 参考缓存逐出存在提交前重填旧值窗口

- Severity: Medium
- Confidence: High
- Category: Data Integrity
- Status: Confirmed
- Affected area: Phase 44c 参数/字典缓存
- Evidence: `DictServiceImpl.java:159-176` 在事务方法内手工逐出；DEVLOG 已记录“逐出后、提交前被并发读重填旧值”的微竞态。
- Problem: 写事务未提交时逐出缓存，另一请求可从旧数据库值重新填充，提交后缓存继续返回旧值直到 TTL。
- Why it matters: 参数与字典属于后端硬校验输入，短时陈旧可能造成规则不一致。
- Realistic failure scenario: 管理员提高视频最小时长并触发缓存逐出；并发请求在提交前读回旧阈值并重填缓存，提交后新的不合规视频仍按旧规则通过数分钟。
- Minimal fix: 在事务提交后逐出，或用事务同步回调/可靠消息触发缓存失效。
- Better long-term fix: 统一参考数据版本号和多节点广播失效协议。
- Regression test suggestion: 屏障控制更新、并发读取和 commit 顺序，提交后首次读取必须得到新值。
- Estimated effort: 0.5–1 day

### Finding: PG-M5 生命周期读取失败被当作空配置并整体覆盖

- Severity: Medium
- Confidence: High
- Category: Configuration
- Status: Confirmed
- Affected area: Phase 47 MinIO multipart 清理
- Evidence: `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceService.java:49-99` 捕获任意读取异常并返回空规则，随后整体 `setBucketLifecycle`。
- Problem: 非“无配置”的权限、网络或服务错误也被视为空；若后续 set 成功，会删除桶上其它生命周期规则。
- Why it matters: 可能静默取消备份保留、对象过期或合规规则。
- Realistic failure scenario: MinIO 读取生命周期配置短暂返回 500，紧接着写请求恢复成功；应用以只有 multipart abort 的规则整体覆盖桶配置，原备份到期/归档规则被移除。
- Minimal fix: 只对明确的 `NoSuchLifecycleConfiguration` 返回空，其它读取失败应 fail-closed。
- Better long-term fix: 用期望配置的声明式合并、版本/ETag 防并发覆盖和运维差异告警。
- Regression test suggestion: 模拟读取 500/AccessDenied 后断言绝不调用 set；仅 NoSuchLifecycleConfiguration 可创建规则。
- Estimated effort: 2–6 hours

## 5. Security Concerns

- Coverage: High
- Inspected evidence: 生产 profile、RBAC/鉴权回归、数据库初始化脚本、文件/视频入口和敏感配置。
- Exclusions / limits: 未做公网渗透、TLS 握手和联网依赖漏洞扫描。

Phase 37、40、45、46 的安全加固在当前回归中保持有效。新增问题集中于运维 bootstrap 的 SQL 拼接；它不构成在线攻击面，但会削弱生产密钥可用字符集和首次初始化可靠性。

## 6. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: dev/prod Compose、profile fail-fast、CORS、MinIO 生命周期、CI 和 demo 门禁。
- Exclusions / limits: 未持有真实生产域名、证书和 secret store。

Phase 47 的读取失败兜底违反 fail-closed；Phase 53 的 demo gate 本身正确，仅内容不满足可播放验收。Phase 37 的 TLS/HSTS 仍是供证后启用模板，不在本轮宣称为线上已启用。

## 7. Data Integrity Analysis

- Coverage: High
- Inspected evidence: 组织软删除、唯一约束、视频/证书状态机、导入逐行事务、回滚补偿、备份导出。
- Exclusions / limits: 未做真实断电、网络分区或进程强杀。

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 1 | 导入/回滚跨 `REQUIRES_NEW` 的终态一致性 | 修复 PG-H3 |
| Idempotency | 0 | 本轮未发现新的重复执行缺陷 | 保持现有 CAS/唯一键回归 |
| ConcurrencyConsistency | 2 | 学院父子完整性、导入终态 | 修复 PG-H1/H3 |
| MigrationSafety | 0 | V1–V28 在新 schema 成功 | 保持不可修改已发布迁移 |
| InvariantValidation | 1 | demo 视频实际可用性 | 修复 PG-H4 |
| BackupRestore | 1 | 数据产物无法按手册回放 | 修复 PG-H2 |
| Reconciliation | 1 | 批次终态与晚提交行缺对账 | 引入持久补偿/对账 |

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 两个 fresh schema、真实 MySQL/Redis/MinIO、Surefire/Failsafe、demo 常驻双跑、前端 type-check/build。
- Exclusions / limits: 未运行浏览器视觉验收、真实恢复切换和大文件压力。

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 全量后端回归 | High | 既有断言漏掉新的交错路径 | 保留 265 条并补三类对抗测试 |
| 前端编译/类型 | High | 无浏览器视觉/交互证据 | 最终全量审计补浏览器验收 |
| 备份生成 | Medium | 只验证产物，不验证回放 | 增加 scratch restore drill |
| demo 初始化 | Medium | 数据存在且幂等，但视频不可播放 | 换真实样例并做媒体/浏览器检查 |
| 清理任务 | Medium | happy path 有覆盖，读取失败覆盖缺失 | 补 fail-closed 单测 |

## 9. Release Concerns

- Coverage: High
- Inspected evidence: 阶段提交、当前工作树、门禁命令、Compose、恢复手册、demo 资源。
- Exclusions / limits: 未部署生产镜像、未执行远端 CI 和真实灾备切换。

当前不能把缺失阶段整体标记为全部复核通过。至少应先清零 PG-H1–H4，并修复 Phase 47 生命周期覆盖；Phase 0 与 Phase 44 需要把真实状态同步回验收/执行计划。WS-3 另有独立报告中的 3 个 High，仍共同阻断稳定发布。

## 10. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Atomicity across business invariant | 2 | High | Phase 39、42 |
| Backups must be restored, not merely produced | 1 | High | Phase 41 |
| Evidence before completion claim | 3 | Medium/High | Phase 0、44、53 |
| Fail closed | 1 | Medium | Phase 47 |
| Safe secret handling | 1 | Medium | Phase 41 |

### Principles Respected

阶段拆分和提交边界总体清楚；Flyway 在全新 schema 成功；Phase 37/40/43/48 的安全、状态与证书规则有真实依赖反例；Phase 44f 对锁等待选择回退正确性优先是合理决策，只是完成状态必须诚实同步。

## 11. Recommended Fix Order

### Fix Immediately

1. 修复 Phase 42 导入/回滚并发状态机。
2. 修复 Phase 39 学院删除与全部子记录创建的共享串行化。
3. 定义并真实演练 Phase 41 恢复协议。

### Fix Before Stable Release

1. 换入可播放 demo MP4 并做浏览器冒烟。
2. Phase 47 生命周期读取异常改 fail-closed。
3. 对齐 Phase 0/44 的验收与实际完成状态，并补 CI 门禁。

### Schedule Later

- 将缓存逐出迁到事务提交后并设计多节点一致性；改造通知为 outbox 异步批量扇出；最终审计补大文件、故障注入、TLS 和依赖扫描。

### Ignore for Now

- 不把现有前端 chunk 体积告警计为本轮阶段阻断；已有单独 WS-12 队列。

## 12. Quick Wins

- `currentRules` 仅吞明确的 NoSuchLifecycleConfiguration，其余异常直接返回失败。
- 把 Phase 44 的 P1-5 状态改为“诚实推迟”，避免误导容量验收。
- 采用一个几秒钟、许可清楚的小型真实 MP4 替换 528 字节空轨资源。
- confirm 收尾必须检查受影响行数并返回数据库真实终态，先消除错误成功响应。

## 13. Long-term Refactor Plan

1. 为长批处理建立带租约/心跳的持久任务状态机，导入、取消、回滚和补偿由单一协调者结算。
2. 为无外键软删除模型建立统一父状态锁协议和孤儿巡检，避免各 service 自行 count-then-delete。
3. 将备份恢复演练纳入定期门禁，用行数、校验和和关键业务查询验证 RTO/RPO。
4. 统一事务后缓存失效和多节点广播，避免参考数据旧值回填。
5. 建立可执行的 demo/E2E 资产标准：文件必须真实可解析、可播放，且由浏览器流程验证。

## 14. Final Verdict

**CHANGES REQUESTED**

Phase 29、35b、36–38、40、43、45–46、48–52 可独立判 PASS；Phase 0、39、41、42、44、47、53 必须退回。当前 265/265 全绿是真实且有价值的回归证据，但不能覆盖恢复可用性、跨事务交错和真实媒体可用性。上述阶段修复后应先跑对应对抗反例，再在 fresh schema 重跑全量门禁；WS-3 的独立退回项也必须在稳定发布前同时清零。
