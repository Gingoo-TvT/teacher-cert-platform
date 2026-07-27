# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 44 fourth remediation
**Audit mode:** incremental + security / stability / performance / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-27
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结第三轮报告/治理基线
`69f7462112e8df7aaffdd8d5f45914d3b030f5c6`、第四轮代码/测试/部署候选
`228a3553607a8fb6ca2db048125d4137e65191ae`，以及其直接子提交、材料
HEAD `9c3da0b7e6dff5faec1cc888ce99c90d5bff81b0`。代码增量为 11 个路径、
`+1136/-27`；`228a355..9c3da0b` 只增加治理与提交材料，没有生产代码、测试或门禁脚本漂移。

正式结论：**PASS（0 Critical / 0 High / 0 Medium / 1 Low）**。

- 第三轮唯一 Medium 已关闭：writers 消失但 `P:` 尚存时，READ 保持失败关闭且不刷新
  positive recovery TTL；writers/version 同时丢失时，进程内 publish guard 仍阻止活跃写事务
  把 MySQL read-your-writes 的未提交 V2 发布到共享 Redis。
- 五条字典 DML 均在 mapper 写入前登记窗口；同步注册失败时数据库尚未产生本次未提交变更。
- 用户在一次性隔离 MySQL/Redis 栈执行的专用门禁为 **16/16 PASS**；当前 XML 的 suite、
  selector、两个第四轮 testcase、summary 与 SHA-256 均和用户结果一致。
- 棕地 `teacher_cert` 只读 preflight 为
  `columns=2/2, invalid=0, noncanonical=0, collision=0`；Compose 非默认 `9m/3m`
  与默认 `2m/20s` 两条展开链均成立。
- 第三轮 4 个 Low 的原问题均已关闭：可复制门禁、Compose/env 参数链、禁混部 runbook
  与本次棕地 identity 前提都有实际证据。
- 新增 1 个非阻断 Low：棕地 preflight 尚未进入 README/Phase 14 的权威发布步骤；本次又通过
  可替换 CLI 包装器执行，脚本没有归档 `DATABASE()/CURRENT_USER()/server UUID` 目标 marker。
  本轮可以结合执行者说明接受，但后续发布的可重复性和目标归属仍需加固。

独立离线门禁为 `platform-system` **53/53 PASS**、后端 **9/9 modules package PASS**、
前端 type-check/build PASS、candidate diff check PASS。用户补充中的
`platform-system 46/46` 是证据文案口误：同轮完整日志和 7 份 XML 聚合均为 **53/53**；
`platform-boot 177/177` 正确。目标 IT 的 `tests=16, errors=0` 不等于“运行日志没有 ERROR”：
owner-loss 反例会按预期记录一次应用级 ERROR，再由测试断言事务回滚。

Phase 44 因此正式放行，并允许进入 Phase 0 退回项复核；这不是 merge、push、部署、切流、
稳定发布或最终全量审计 GO。本轮 Codex 未启动或连接 Docker、MySQL、Redis、MinIO、服务、
浏览器或网络，也未执行故障注入、扫描、fuzz、压力、凭据或权限操作。

### Score Dashboard

```text
Security        █████████░  9.0  A   无权限/凭据/API 增量；只读证据边界明确
Stability       █████████░  9.0  A   owner-loss 与 crash recovery 均失败关闭
Performance     ████████░░  8.5  A   低频写路径有界；未做多实例压力基准
Testing         █████████░  9.2  A   16/16 真依赖 + 53/53 独立离线，证据链完整
Maintainability █████████░  8.8  A   guard、配置、门禁与 runbook 职责清晰
Design          █████████░  8.8  A   本地/跨节点守卫互补；灾难级提交缝仍属长期边界
Release         ████████░░  8.4  A   阻断清零；preflight 权威接线仍有 1 Low
─────────────────────────────────────
Overall         █████████░  8.8  A
```

Each dimension is scored 0.0–10.0. **Higher = better.** Overall is the
arithmetic mean of the seven dimensions, rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **1** | **1** | **0** |

## 2. Project Map

- `DictServiceImpl`：字典 CRUD、Redis payload/version/writers 三键协议、事务 owner 生命周期、
  canonical identity 与两个 Caffeine cache 的统一失效入口。
- `DictRedisPublishGuard`：按 canonical typeCode 计数的进程内写窗口；把 writer begin 与整段
  Redis PUT 在线性化锁内互斥。
- `ReferenceCacheInvalidator` / `EpochGuardedCache`：同步打开窗口、`beforeCommit` 守卫、
  `afterCompletion` 逐出与本地 cache publishLock。
- `Phase44CacheCommitWindowIT`：真实 MySQL/Redis 的 loader、commit/rollback、双 writer、
  owner loss、全部 Redis 状态丢失与 crash recovery 交错。
- `scripts/test-phase44-cache-commit-window-real.sh`：精确 suite、计数、selector、testcase 与
  Failsafe summary 门禁。
- `scripts/preflight-phase44-dict-identity.sh`：棕地 schema/identity 的只读失败关闭检查。
- `.env.example` / `docker-compose.yml` / README / Phase 14：配置与不兼容协议停机切换合同。

本轮无 Flyway、权限点、API 路径、返回结构或业务状态机变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `69f7462..228a355` 全 11 路径、材料至 `9c3da0b`、调用方与治理状态 | 未重开其它 Phase |
| Security | Medium | 只读账号边界、凭据入口、脚本参数引用、共享缓存可见性 | 未执行扫描、请求、登录或凭据操作 |
| Stability | High | pending TTL、owner 丢失、callback、异常清理与 crash recovery | 未杀进程、断 Redis 或做多实例灾难注入 |
| Performance | Medium | 固定 32 stripe、单线程 renewer、pending 回源与 TTL | 未做吞吐、延迟或压力基准 |
| Testing Authenticity | High | 源码/XML/testcase/hash/time chain、完整日志、53/53 独立重跑 | 精确容器 patch 与资源清理取自用户陈述 |
| Release | High | Git 边界、精确门禁、Compose/env、禁混部、棕地 preflight | 未 merge、push、deploy 或 rollback |
| Configuration | High | Spring 绑定、属性校验、Compose 双层默认与非默认展开 | 未读取仓库外生产配置 |
| Data Integrity | High | MySQL read-your-writes、Redis READ/PUT/COMPLETE、identity/collation | 未写棕地数据 |
| Concurrency | High | PUT/begin 线性化、DML 顺序、beforeCommit 与 afterCompletion | 未做 jcstress 或多 JVM 压测 |

## 3. Incremental Change Summary

### Change Summary

- Baseline: `69f7462112e8df7aaffdd8d5f45914d3b030f5c6`
- Code/test/deployment candidate: `228a3553607a8fb6ca2db048125d4137e65191ae`
- Material / review HEAD: `9c3da0b7e6dff5faec1cc888ce99c90d5bff81b0`
- Production Java: 2 paths
- Test Java: 3 paths
- Scripts: 2 paths
- Deployment / documentation: 4 paths
- Flyway / dependency / API / permission changes: 0
- Author identity: `[REDACTED_USER]`

### Previous Finding Closure Matrix

| Previous finding | Candidate and dynamic evidence | Independent result |
|------------------|--------------------------------|--------------------|
| M1 owner 丢失后可发布未提交值 | pending TTL 不提前恢复 + local publish guard + 决定性交错 16/16 | **Closed** |
| L1 逐字 Maven 门禁不可执行/可假绿 | 专用脚本删除旧报告并校验 suite、selector、16 testcase 与 summary | **Closed** |
| L2 租约配置不进入 Compose/.env | Spring → Compose → env 双层默认；`9m/3m` 与 `2m/20s` 均展开 | **Closed** |
| L3 禁混部只在候选材料 | README + Phase 14 停写、停旧节点、同版本启动、禁止旧 binary 回滚 | **Closed** |
| L4 棕地 collation/identity 未预检 | 本次 `teacher_cert` READ ONLY preflight 2/2、三类异常均 0 | **Closed for this target** |

### Risk Delta

- Previous blocking findings closed: 1/1 Medium
- Previous Low findings closed on their original failure condition: 4/4
- New blocking findings: 0
- New non-blocking findings: 1 Low
- Confirmed permission/API/DDL regression: 0
- Phase gate: **PASS**

### Test Coverage Delta

- Independent offline: `platform-system` **53/53**.
- Independent package: **9/9 modules**.
- Independent frontend: type-check/build PASS（仅既有大 chunk advisory）。
- User-operated real IT: Phase44 cache **16/16**, 0 failure/error/skip, `flakes=0`.
- User-operated same-run Surefire: `platform-system` **53/53**, `platform-boot` **177/177**.
- User-operated brownfield preflight: `columns=2/2`, invalid/noncanonical/collision **0/0/0**.
- User-operated Compose expansion: override **9m/3m**, defaults **2m/20s**.
- Valuable fourth-round negatives:
  - owner disappears, same transaction reads V2, concurrent reader sees V1, payload stays absent;
  - writers and version both disappear, local guard still blocks V2 publication;
  - `beforeCommit` precisely aborts and rolls back;
  - crash pending PTTL declines across repeated reads and only recovers after natural expiry;
  - five dictionary DML paths perform no mapper mutation when synchronization registration fails.

### Approval Recommendation

**Approve Phase 44 / release Phase 0 review.**

本结论只放行阶段依赖。代码候选仍未合并，仓库仍无 remote；不得把本报告解释为 merge、push、
部署或最终发布授权。项目继续受 Phase 0 退回项和最终全量审计约束。

## 4. Top Risks

1. **Low — 棕地 preflight 的权威接线与目标证明不足：** 当前脚本与本次运行可接受，但未来发布
   可能跳过脚本；包装器执行时缺少可归档的目标身份 marker。
2. **Long-term boundary — 物理提交极窄灾难缝：** 若 `beforeCommit` 已成功后 Redis 全状态丢失、
   另一节点装入旧提交值，同时提交节点在 DB commit 后、`afterCompletion` 前崩溃，现协议没有
   durable outbox/commit revision 完成最终清理。该复合灾难边界不是第四轮新增回归，也不属于
   第三轮 Medium 的触发条件，留最终全量审计与长期设计处理。

## 5. Detailed Findings

### Finding: 棕地 preflight 尚未进入权威发布步骤且包装器目标 marker 不足

- Severity: Low
- Confidence: High
- Category: Release / Testing Authenticity / Configuration
- Status: Confirmed
- Affected area: Phase 44 棕地升级前检查与证据归档
- Evidence:
  - File: `scripts/preflight-phase44-dict-identity.sh:11-13,59-76`
  - Function / Module: 可替换 MySQL CLI/login-path 与连接入口
  - Relevant behavior: 脚本允许通过环境变量替换两个客户端；这次确实使用 host 包装器。
  - File: `scripts/preflight-phase44-dict-identity.sh:77-91,168-241`
  - Function / Module: READ ONLY、collation/identity marker 与 PASS 输出
  - Relevant behavior: 会话只读与检查计数被验证，但 PASS 输出不包含 `DATABASE()`、
    `CURRENT_USER()` 或 server identity。
  - File: `README.md:68-72`
  - Function / Module: Phase 44 发布摘要
  - Relevant behavior: 只描述停机切换，没有要求运行 preflight。
  - File: `docs/phase-14-非功能部署验收.md:53-61`
  - Function / Module: Phase 44 权威停机切换步骤
  - Relevant behavior: 未引用 preflight 脚本，也未规定目标 marker、授权摘要和证据归档。
- Problem: 标准 mysql 客户端会遵守脚本传入的 host/port/database，本次又有用户提供的包装器
  实现与目标说明，因此 `teacher_cert` 的结果可接受；但候选材料不是长期权威 runbook，
  而可替换包装器理论上可以改变参数解释。当前 PASS 文本本身不能独立重建“检查了哪一个实例、
  哪一个 schema、以哪个只读主体连接”。
- Why it matters: 下一次发布者可能只执行停机步骤而遗漏 preflight，或归档一份无法证明目标归属
  的 PASS。错误 schema 的全绿结果不能保护真正的棕地库。
- Realistic failure scenario: 运维在维护窗口按 Phase 14 操作但没有阅读候选提交材料，未运行脚本；
  或包装器连接到另一个同结构空库，输出仍为 2/2 与零异常，真正目标库的历史 collision 未被发现。
- Minimal fix:
  1. 把 preflight 插入 Phase 14 的“冻结字典写流量后、启动新 binary 前”，并要求每个目标 schema 必跑；
  2. 脚本输出并校验 `DATABASE()`，同时输出脱敏的 `CURRENT_USER()` 与 server UUID；
  3. 归档退出码、只读授权摘要和清理结果，不归档口令或完整 `.env`；
  4. 用无网络 stub 覆盖错误 schema、marker 缺失/重复和客户端非零退出。
- Better long-term fix: 让发布流水线以受控只读身份自动执行带候选 SHA、目标实例 ID 与签名结果的
  pre-deploy gate，而不是依赖临时候选材料和人工包装器。
- Regression test suggestion: 用纯 stub mysql wrapper 分别返回正确目标、错误目标、重复目标 marker、
  缺失 marker 与非零退出，只有精确单一目标可通过。
- Estimated effort: 0.5 day

## 6. Security Concerns

- Coverage: Medium
- Inspected evidence: shell 参数引用、login-path、MYSQL_PWD 拒绝、只读账号、临时目录与输出内容
- Exclusions / limits: 未执行扫描、请求、认证、凭据尝试或任何 cyber 类动作

- 本轮没有新增权限点、鉴权、外部 URL、敏感字段或生产凭据。
- preflight 的固定 SQL 不拼接环境输入；schema 通过 mysql argv 选择，值输出为 HEX，未发现 shell/SQL
  注入问题。
- 用户声明一次性只读账号只有 `SELECT ON teacher_cert.*`，口令未进入命令行/环境变量，并在事后删除；
  本轮未连接 MySQL 独立重验账号或清理状态。
- XML 可能包含环境 properties，因此只归档脱敏摘要与哈希，不把原 XML/完整 Compose 输出纳入 Git。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: READ/BEGIN/RENEW/PUT/COMPLETE Lua、local guard、callback、PTTL 与真实交错
- Exclusions / limits: 未实际中断 Redis、杀进程、注入网络故障或做多实例压力

- writers 为空但 pending 仍在时，READ 不再把不确定状态误判为 crash 已完成。
- local guard 从 DML 前保持到 Redis completion 与两个 Caffeine 窗口清理之后；owner lost 只停止续租
  并阻止提交，不提前开放 PUT。
- Redis/Caffeine 清理失败均有 finally 路径解除本地 guard，避免一次故障把进程永久旁路。
- `P:` 无 TTL 的补 recovery TTL 分支缺专用动态反例，但逻辑短小、失败关闭，不单列 finding。

## 8. Performance Concerns

- Coverage: Medium
- Inspected evidence: 32 stripe、锁持有范围、单线程 renewer、pending DB fallback
- Exclusions / limits: 未做吞吐、延迟、缓存击穿或压力基准

- 字典写为低频管理操作；固定 32 stripe 有明确内存上界。
- PUT 在 stripe 锁内包含一次 Redis 往返，同 stripe 的不同 identity 会保守串行；当前没有容量数据证明
  需要单列性能 finding。
- pending recovery 期间读请求回源 DB 是安全优先的有界退化；默认 recovery TTL 为 `2 × lease`。

## 9. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 源码方法、compiled class、XML/text/summary、完整日志、哈希、时间链与 Git blob
- Exclusions / limits: Codex 未重跑真实依赖；容器精确 patch、卷重建和最终清理取自用户陈述

### Confidence Assessment

- 目标 XML：suite 精确为 `cn.edu.gpnu.platform.boot.Phase44CacheCommitWindowIT`，
  `tests=16, failures=0, errors=0, skipped=0`。
- Failsafe summary：`completed=16, failures=0, errors=0, skipped=0, flakes=0`。
- XML properties：`it.test=Phase44CacheCommitWindowIT`，
  `failsafe.failIfNoSpecifiedTests=false`。
- XML SHA-256：
  `3505ead4877e0d280e56d837d3d7c0c5d2e6038a8279d979c9d9334769394981`，
  与用户提供值完全一致。
- 源码 16 个 `@Test` 与 XML 16 个 testcase 集合精确相等；两个第四轮方法耗时分别为
  0.144s 与 4.125s。
- 完整日志显示回环 JDBC、空 schema、33 次迁移至 V32、BUILD SUCCESS、结构化 XML verified
  与脚本退出码 0。
- `228a355..9c3da0b` 没有修改生产代码、IT、脚本、Compose 或 env；报告生成时间晚于源码与提交。
- 证据仍是本机 ignored/target 文件且不内嵌 Git SHA；通过 blob 相等、哈希和时间链交叉增强，
  不冒充 fresh-clone 不可变制品。

### Valuable Tests

- `ownerLossCannotPublishUncommittedValueBeforeCommitRollback`
- `crashPendingRemainsFailClosedUntilRecoveryTtlExpires`
- `DictRedisPublishGuardTest.publisherAndWriterBeginAreLinearized`
- `DictWriteWindowOrderingTest` 的五条注册失败反例
- `ReferenceCacheInvalidatorTest` 的 registration/callback/failure isolation 反例

### Suspicious Tests

- 未发现第四轮目标测试空转、仅断言 mock 调用或复用旧 XML 的证据。
- 日志中的应用级 ERROR 是 owner-loss 反例的预期信号，不是 Failsafe error；报告不得写成
  “全日志 0 个 ERROR”。
- 用户所述 `platform-system 46/46` 与同轮日志/XML 不一致，已按 **53/53** 更正。

### Missing Tests

- 无 TTL `P:` 第一次 READ 只补一次 recovery TTL。
- 标准/包装器 preflight 的错误目标与 marker 缺失/重复失败关闭。
- 多 JVM 与 `beforeCommit → DB commit → afterCompletion` 的复合灾难交错。

这些缺口不推翻已验证的第三轮 Medium 闭环；后两项分别进入本报告 Low 与长期设计边界。

## 10. Release Concerns

- Coverage: High
- Inspected evidence: 提交父子链、无 remote、门禁脚本、Compose/env、README/Phase 14 与用户动态输出
- Exclusions / limits: 未 merge、push、部署、切流或执行回滚

- Phase 44 与旧 binary 不兼容，必须停写、排空、停止全部旧节点、确认旧 pending 消失、全量启动同版本
  新节点后再恢复流量；禁止旧 binary 回滚。
- “等待旧 60 秒 pending”同时带有“核对不存在 `P:*`”的状态条件，因此固定时间不会直接假放行。
- 本报告允许进入 Phase 0 复核，不允许跳过 Phase 0 或最终全量审计。
- preflight 的长期发布接线是当前唯一 Low。

## 11. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `application.yml`、`DictCacheProperties`、Compose、`.env.example` 与两次 config 输出
- Exclusions / limits: 未读取真实生产 `.env`

### Configuration Summary

| Setting | Spring default | Compose default | `.env.example` | Override proof |
|---------|----------------|-----------------|----------------|----------------|
| `DICT_CACHE_WRITER_LEASE` | `2m` | `2m` | `2m` | `9m` |
| `DICT_CACHE_WRITER_RENEW_INTERVAL` | `20s` | `20s` | `20s` | `3m` |

`DictCacheProperties` 对 null/非正、低于 1ms、乘 2 溢出与 `renew > lease/3` 均启动期失败关闭。
用户双向 Compose 输出证明环境覆盖优先级与 env-file 默认链均生效。

## 12. Data Integrity Analysis

- Coverage: High
- Inspected evidence: DML 顺序、MySQL isolation、Redis CAS、owner loss、payload schema 与棕地 identity
- Exclusions / limits: 未修改或重跑棕地数据

### Integrity Summary

- 当前事务可在 MySQL 连接内读到未提交 V2，这是数据库正常 read-your-writes；安全责任在共享 cache
  publication guard，而不是禁止事务内读取。
- writers/version 都丢失时，写事务所在 JVM 的 local guard 拒绝 V2 PUT；其它 JVM无法读取该连接的
  未提交 V2，最多读取已提交 V1。
- `beforeCommit` 找不到 owner 时强制回滚；`afterCompletion` 再删除可能存在的 payload 并推进 version。
- 棕地两列均为 `utf8mb4_0900_ai_ci`，invalid/noncanonical/collision 为 0；当前 canonical identity
  发布前提成立。

## 13. Concurrency Analysis

- Coverage: High
- Inspected evidence: stripe 锁、active count、writer handle、Lua owner、事务 callback 与真实 latches
- Exclusions / limits: 未做 jcstress、多 JVM、压力或 Redis 故障注入

- `begin` 与 `publishIfIdle` 使用同一 stripe 的同一把锁；检查 idle 与整个 Redis PUT 之间没有 writer
  插入窗口。
- writer 先到时 PUT action 不执行；PUT 先到时 writer 等待，随后在 DML 前执行 Redis BEGIN，
  删除刚写入的 payload。
- hash 碰撞只让不同 identity 短暂串行；map 内计数仍按完整 canonical identity 分离，不会串键。
- handle close 幂等，nested writers 只有最后一个关闭才使本地 PUT 可执行。
- 本轮 16/16 已覆盖 owner-loss 关键交错；跨节点结论中“其它节点看不到未提交连接数据”由 MySQL
  事务隔离与共享 Redis 协议组合证明。

## 14. Principles Compliance

### Principles Violated

- **Explicit over implicit / Evidence-driven release：** preflight 没有进入权威发布步骤，包装器运行又缺少
  自证目标 marker（Low）。

### Principles Respected

- **Correctness over convenience：** 不确定 pending 保持失败关闭到 recovery TTL，而不是立即恢复缓存。
- **Defense in depth：** Redis owner/version 与本地 publish guard 分别覆盖跨节点和本 JVM 状态丢失。
- **Fail closed：** owner 丢失、Redis 不确定、非法 identity、preflight 异常都拒绝提交或发布。
- **Test what matters：** 真实 MySQL/Redis 用例覆盖同事务 read-your-writes、并发观察和最终回滚。
- **No fake green：** 门禁删除旧报告并校验 selector、testcase 集合、summary 与哈希。
- **Operational honesty：** 候选、自测、用户动态证据、独立 PASS 与最终发布 GO 保持分离。

## 15. Recommended Fix Order

### Fix Immediately

- 无 Critical / High / Medium。

### Fix Before Stable Release

1. 把 `scripts/preflight-phase44-dict-identity.sh` 接入 README/Phase 14 权威发布步骤。
2. 增加并严格校验目标 marker，归档脱敏的只读授权与退出码摘要。

### Schedule Later

1. 以 durable commit revision/outbox 消除 `beforeCommit` 后复合灾难缝。
2. 增加 owner 数、renew lag、pending recovery 与 publish 拒绝指标。
3. 在容量需求出现时量化 stripe head-of-line 与单线程 renewer 队列。

### Ignore for Now

- 不为低频字典写路径引入无数据支撑的高复杂度锁分片扩容。
- 不把本轮阶段 PASS 误写成项目稳定发布就绪。

## 16. Quick Wins

| Action | Effort | Impact |
|--------|--------|--------|
| Phase 14 增加一条强制 preflight 步骤与报告路径 | 15–30 min | 防止未来发布遗漏 |
| 输出/校验 `DATABASE()` 与脱敏连接身份 marker | 30–60 min | 让包装器结果可独立归属 |
| 加纯 stub 的错误目标/marker 失败关闭测试 | 1–2 h | 提高门禁可移植性 |

## 17. Long-term Refactor Plan

1. 把 Redis revision、active writer、recovery state 与 payload 收敛为明确状态记录。
2. 由数据库提交产生 durable cache revision/outbox，消费者幂等推进共享 cache。
3. 把 brownfield preflight 变成带候选 SHA、目标实例 ID、schema 与脱敏主体的正式 pre-deploy 制品。
4. 在最终全量审计统一复查多实例、灾难恢复、真实发布 runbook 演练与监控告警。
