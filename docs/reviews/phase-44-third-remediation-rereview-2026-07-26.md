# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 44 third remediation
**Audit mode:** incremental + security / stability / performance / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-26
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结第二轮候选
`25f8b1c0683dae38f930a8cb94baf937a446013d`、第三轮代码/测试候选
`8ff544db37b7cd89a9f8463de4d1696b46c50f45`，以及其直接子提交、材料
HEAD `890aa6ebc919f13036caf3f87b13779a97120539`。代码/测试增量为 16 个路径、
`+952/-172`。复核对象是上一轮正式报告的 2 Medium / 3 Low。

正式结论：**CHANGES REQUESTED（0 Critical / 0 High / 1 Medium / 4 Low）**。

- 原 typeCode canonical identity Medium 已关闭：API、DB、Redis 三类键、两个
  Caffeine cache、pending/逐出、VO 与前端都复用小写 canonical identity，payload
  schema v2 会拒绝旧包络与 identity/version 不符。
- 上一轮 3 个 Low 均关闭：`NotificationMapper` 说明已对齐 MP 3.5.16；
  registration failure 确实进入同步注册异常分支；两个 publishLock 测试先证明
  contender 已真实排队。
- Redis 正常双 writer、跨两个租期续租、最后 owner 才恢复普通版本等主体已经成立。
  但 owner 丢失时，READ Lua 会把仍存在的 `P:ACTIVE` 立即改成普通版本；同一事务随后
  `listItems` 可读到自身未提交 V2，并被 PUT Lua 发布到共享 Redis，直到
  `beforeCommit` 才回滚并由 `afterCompletion` 清理。上一轮 Redis Medium 因此只能
  **部分关闭**。
- 用户在专用真实依赖栈执行的修正门禁为 16/16 PASS；本机 XML、testcase 集合、哈希与
  时序一致。但这 16 条测试的 owner-loss 用例在删 key 后立即提交，没有覆盖
  “删 owner → 同事务回读 → 并发观察 → 回滚”的决定性交错。
- 新增 4 个非阻断 Low：材料中的逐字门禁命令会在 reactor root 因零匹配测试失败；
  两个新租约环境变量没有进入官方 Compose/.env；禁止新旧缓存协议混部只写在候选材料；
  棕地库的 collation/历史 typeCode 没有可移植的发布前 preflight。

独立安全门禁为 `platform-system` **44/44 PASS**、后端 **9/9 modules package
PASS**、前端 type-check/build PASS、`git diff --check` PASS。用户动态证据为
Phase44 IT **16/16**、同轮 Surefire 44/44 + 18/18 + 177/177。动态 XML 不受 Git
跟踪且不内嵌候选 SHA，因此作为高置信本机证据，不冒充 fresh-clone 可移植证明。

Phase 44 继续保持复核退回，Phase 0 不放行。本报告不是 merge、push、部署、切流、
稳定发布或最终全量审计 GO。本轮 Codex 未启动或连接 Docker、MySQL、Redis、MinIO、
服务、浏览器或网络，也未执行故障注入、扫描、fuzz、压力、凭据或权限操作。

### Score Dashboard

```text
Security        █████████░  8.8  A   无权限/凭据增量；未执行 cyber 类验证
Stability       ███████░░░  7.0  B   owner 丢失后的 pending 恢复过早
Performance     ████████░░  8.4  A   低频写路径可接受；未做续租队列基准
Testing         ████████░░  7.7  B   16/16 真实，但漏决定性 owner-loss 读穿
Maintainability ████████░░  8.0  A   identity 集中；协议仍需更强状态边界
Design          ███████░░░  7.0  B   正常路径闭合，失效状态仍被误判为 crash
Release         ██████░░░░  6.5  C   1 Medium 阻断，部署/门禁合同仍不完整
─────────────────────────────────────
Overall         ████████░░  7.6  B
```

Each dimension is scored 0.0–10.0. **Higher = better.** Overall is the
arithmetic mean of the seven dimensions, rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 1 | 0 |
| Low | 4 | 4 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **5** | **5** | **0** |

## 2. Project Map

- `DictServiceImpl`：字典 CRUD、Redis payload/version/writers 三键协议、事务 owner
  生命周期、canonical identity 与两个 Caffeine cache 的统一失效入口。
- `ReferenceCacheInvalidator`：同步打开窗口、`beforeCommit` 守卫、事务完成后隔离逐出
  与最终解除窗口。
- `EpochGuardedCache`：进程内 epoch、pending 计数和 publishLock。
- `DictCacheProperties` / `application.yml`：writer lease 与 renew interval。
- `Phase44CacheCommitWindowIT`：真实 MySQL/Redis 的 loader、commit/rollback、双 writer、
  续租、owner loss、alias 与 crash recovery 反例。
- `platform-system` 五组单测：本地 publish、callback 边界、配置校验、键 identity 与批量写。
- 发布面：`docker-compose.yml`、`.env.example`、`docs/phase-14-非功能部署验收.md`。
- 治理面：第三轮提交材料、上一轮正式报告、当前执行计划与本轮证据摘要。

本轮无 Flyway、权限点、API 路径、返回结构或业务状态机变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `25f8b1c..8ff544d` 全 16 路径、材料至 `890aa6e`、调用方与部署邻接面 | 未重开其它 Phase |
| Security | Medium | 后端 typeCode 约束、共享缓存可见性、证据卫生、零权限/API 变化 | 未执行扫描、请求、登录或凭据操作 |
| Stability | High | owner 状态机、续租、callback、crash recovery、异常窗口 | 未做 Redis 中断或进程 crash |
| Performance | Medium | 单线程 renewer、默认租期、写频率、回源行为 | 未做吞吐、延迟或压力基准 |
| Testing Authenticity | High | 16 个 IT、44 个单测、XML/testcase/hash/time chain、门禁命令 | 未由 Codex 重跑真实依赖 IT |
| Release | High | Git 边界、Compose/.env、升级合同、门禁可执行性 | 未 merge/push/deploy |
| Configuration | High | 属性校验、application、Compose/.env 传递链 | 未读取仓库外生产配置 |
| Data Integrity | High | DB read-your-writes、Redis CAS、owner loss、identity/collation | 未写真实数据 |
| Concurrency | High | 双 writer、租约续期、beforeCommit、owner-loss 交错 | 未执行故障注入或压力 |

## 3. Incremental Change Summary

### Change Summary

- Baseline: `25f8b1c0683dae38f930a8cb94baf937a446013d`
- Code/test candidate: `8ff544db37b7cd89a9f8463de4d1696b46c50f45`
- Material / review HEAD: `890aa6ebc919f13036caf3f87b13779a97120539`
- Parent chain: `25f8b1c -> 8ff544d -> 890aa6e`
- Code/test paths: 16
- Code/test lines: +952/-172
- Production Java: 7 changed/added
- Test Java: 5 changed/added
- Frontend: 3 changed
- Configuration: 1 changed
- Flyway / dependency / API / permission changes: 0
- Author identity: `[REDACTED_USER]`

### Previous Finding Closure Matrix

| Previous finding | Candidate evidence | Independent result |
|------------------|--------------------|--------------------|
| M1 Redis pending 无 owner/refcount，固定 60s 无续租 | Redis TIME + owner ZSET + renew + beforeCommit + 4 条真实反例 | **Partially closed**：正常路径关闭；owner 丢失后的 pending 被过早恢复 |
| M2 DB/cache typeCode identity 不同 | 全链路 canonicalizer + schema v2 + alias IT | **Closed** |
| L1 `@Param` 框架说明错误 | 生产注释区分绑定名与 MP 自动填充 | **Closed** |
| L2 registration failure 测试空转 | 可替换 registrar + active synchronization | **Closed** |
| L3 publishLock 测试缺 started 证据 | started latch + `hasQueuedThread` | **Closed** |

### Risk Delta

- Previous Medium fully closed: 1/2
- Previous Medium partially closed: 1/2
- Previous Low fully closed: 3/3
- Remaining blocking findings: 1 Medium
- New non-blocking findings: 4 Low
- Confirmed permission/API/DDL regression: 0
- Confirmed PG-M3 regression: 0

### Test Coverage Delta

- Independent offline: `platform-system` 44/44.
- Independent package: 9/9 modules.
- Independent frontend: type-check/build PASS.
- User-operated real IT: Phase44 cache 16/16.
- User-operated same-run Surefire: 44/44 + 18/18 + 177/177.
- Valuable new negatives:
  - two overlapping owners;
  - live renewal beyond two initial leases;
  - owner absence aborts commit;
  - expired crash owner recovery;
  - case alias commit/rollback;
  - legacy envelope rejected at same version;
  - real synchronization registration failure;
  - real publishLock queue state.
- Decisive missing negative:
  - owner disappears, the same write transaction reads through, another request observes
    the shared payload, and the original transaction later rolls back.

### Approval Recommendation

**Request changes / keep Phase 44 returned.**

下一候选只需修正 owner-loss recovery，不需要撤销 ZSET、续租、canonical identity、
schema v2 或已经关闭的 3 个 Low。最小重交应做到：

1. writers 为空但 version 仍为 pending 时继续失败关闭，不能立即恢复普通版本；
2. 当前事务已写该 typeCode 时，即使 Redis 状态同时丢失，也不得发布本事务回源值；
3. 补“删 owner → 同事务回读 → 并发观察 → beforeCommit 回滚”的真实反例；
4. 修正门禁命令，并将配置、禁混部与棕地 preflight 纳入发布合同。

## 4. Top Risks

1. **Medium — owner 丢失后可发布未提交值：** READ 把仍存在的 pending 过早正常化，
   本事务 read-your-writes 结果可写入共享 Redis。
2. **Low — 逐字门禁命令不可执行：** reactor root 会在目标 IT 运行前因零匹配测试失败。
3. **Low — 新租约配置没有进入官方部署链：** Compose/.env 无法覆盖两个声明的环境变量。
4. **Low — 新旧缓存协议禁混部未进入权威部署文档：** 候选材料不是生产 runbook。
5. **Low — 棕地 identity 前提未做 preflight：** 实际 collation 与历史非法/非 canonical
   typeCode 缺少可移植检查。

## 5. Detailed Findings

### Finding: owner 丢失后 pending 被过早恢复并可发布未提交字典值

- Severity: Medium
- Confidence: High
- Category: Data Integrity / Concurrency / Stability
- Status: Confirmed
- Affected area: `DictServiceImpl.listItems` 与字典写事务的 Redis 写窗口
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:115-127`
  - Function / Module: `READ_ITEMS_WITH_VERSION`
  - Relevant behavior: ZSET 清理后只要 `ZCARD == 0`，仍为 `P:` 的 version 会立即被替换为普通随机版本。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:275-289,457-515`
  - Function / Module: `listItems` / `readCachedItems` / `putCachedItems`
  - Relevant behavior: DB 回源可读到当前事务自身未提交写；PUT 只检查 Redis writers/version，不知道本事务已写该 typeCode。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:596-611`
  - Function / Module: `WriteWindowOwner.renewOrThrow`
  - Relevant behavior: owner 丢失直到 `beforeCommit` 才强制中止事务。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase44CacheCommitWindowIT.java:415-447`
  - Function / Module: `ownerLossBeforeCommitAbortsTheTransactionAndRollsBackDb`
  - Relevant behavior: 删除 writers key 后立即放行提交，没有同事务 read-through 与并发观察。
- Problem: 当前实现把“没有可见 owner”直接解释为“原 writer 已崩溃，可以恢复缓存”，但
  owner 也可能在事务仍活跃时丢失。pending version 本身仍在，说明系统知道写窗口曾经存在，
  却在租约/`beforeCommit` 作出最终裁决前主动解除失败关闭。
- Why it matters: 写事务最终会因 owner 丢失回滚，但在回滚前可把 V2 发布给其它请求。
  其它业务可能依据最终不存在的字典值通过校验、显示错误标签或做出后续写入。窗口长度由
  外层事务控制，不是只存在几个 CPU 指令的理论缝隙。
- Realistic failure scenario:
  1. T 更新字典 V1→V2，登记 owner 与 `P:ACTIVE`；
  2. writers ZSET 因过期、驱逐、故障恢复或删除而消失，version 仍为 `P:ACTIVE`；
  3. T 在 `beforeCommit` 前调用 `listItems`；
  4. READ 把 `P:ACTIVE` 改为普通 N；
  5. MySQL read-your-writes 返回未提交 V2；
  6. PUT 在无 owner、version=N 下写入共享 Redis；
  7. 并发请求命中 V2；
  8. `beforeCommit` 才发现 owner 不存在，DB 回滚，`afterCompletion` 随后清理。
- Minimal fix: writers 为空但 version 仍以 `P:` 开头时，READ 继续返回 pending 且不回填；
  等 pending 的 recovery TTL 自然过期后，version 缺失的下一次读才从已提交 DB 重建。
  同时以事务绑定的本地写 identity 阻止本事务在 writers/version 同时丢失时发布回源值。
- Better long-term fix: 把 revision、active writer、recovery state 和 payload 放进一个显式
  状态记录；用事务 token/持久提交事件区分 active、aborted、committed 与 crashed，而不是
  从“owner 当前不可见”猜测事务已经结束。
- Regression test suggestion: 在真实 MySQL/Redis 中用屏障执行
  `updateItem -> 删除 writers -> 同事务 listItems -> 第二线程 listItems -> 放行 commit`；
  断言两次读期间 payload 始终为空、version 仍 pending、第二线程只见 V1，最后
  `beforeCommit` 精确回滚。另将 crash recovery 用例改为 pending TTL 到期后才允许重建。
- Estimated effort: 0.5–1 day

### Finding: 候选材料给出的 Phase 44 门禁命令无法逐字运行

- Severity: Low
- Confidence: High
- Category: Testing / Release
- Status: Confirmed
- Affected area: Phase 44 动态门禁 runbook
- Evidence:
  - File: `docs/reviews/phase-44-third-remediation-submission-2026-07-26.md:104-111`
  - Function / Module: 提交材料 §5
  - Relevant behavior: 命令使用 `-pl platform-boot -am -Dit.test=... verify`，未关闭 reactor 非目标模块的“指定测试零匹配即失败”。
  - File: `pom.xml:93-110`
  - Function / Module: parent `maven-failsafe-plugin`
  - Relevant behavior: Failsafe 在 parent/root 也绑定 `integration-test` 与 `verify`。
- Problem: 用户逐字执行时 root POM 在 0.5 秒内失败，目标 IT 尚未运行。只有加入
  `-Dfailsafe.failIfNoSpecifiedTests=false` 后，platform-boot 才执行 16 条测试。
- Why it matters: 门禁文档应可复制执行。若只加入该开关却不再断言目标计数，它又可能把
  将来类名漂移或测试消失伪装成成功。
- Realistic failure scenario: 独立复核者按材料执行，看到 BUILD FAILURE；或者机械加入
  failIfNoSpecifiedTests=false 后未检查 XML/summary，实际目标模块也为零测试却误判 PASS。
- Minimal fix: 材料/DEVLOG/权威计划统一使用带
  `-Dfailsafe.failIfNoSpecifiedTests=false` 的命令，并强制校验
  `failsafe-summary.xml completed=16` 与目标 XML `tests=16`。
- Better long-term fix: 在 `platform-boot` 提供一个专用 Maven profile/script，profile 自带
  reactor 零匹配处理并在 verify 后验证目标 suite 计数。
- Regression test suggestion: 在无目标类的临时类名下，专用门禁必须失败；使用正确类名时
  必须报告且校验 16/16。
- Estimated effort: 15–30 minutes

### Finding: writer 租约配置未进入官方 Compose 与 .env 链

- Severity: Low
- Confidence: High
- Category: Configuration / Release
- Status: Confirmed
- Affected area: 生产 Compose 的字典缓存租约调优
- Evidence:
  - File: `platform-boot/src/main/resources/application.yml:32-35`
  - Function / Module: `platform.cache.dictionary`
  - Relevant behavior: 声明 `DICT_CACHE_WRITER_LEASE` 与 `DICT_CACHE_WRITER_RENEW_INTERVAL`。
  - File: `docker-compose.yml:75-120`
  - Function / Module: backend environment
  - Relevant behavior: 未把上述两个环境变量注入容器。
  - File: `.env.example:48-73`
  - Function / Module: backend environment template
  - Relevant behavior: 未列出两个字典 writer 参数及其约束。
- Problem: Compose 的 `.env` 只参与插值，不会自动进入 backend 容器。当前默认 2m/20s
  能启动，但官方 `.env + docker compose` 路径无法按环境延迟/事务上界调整新协议。
- Why it matters: 租期过短会放大 owner loss，过长会延迟 crash recovery；该参数是正确性
  边界，不只是性能旋钮。
- Realistic failure scenario: 运维在 `.env` 设置参数，Compose 不转发，应用仍使用默认值；
  排障时看到配置文件值与运行行为不一致。
- Minimal fix: 在 backend environment 显式透传两个变量，在 `.env.example` 记录默认值、
  `renew <= lease/3` 与单位，并对 `docker compose config` 做静态断言。
- Better long-term fix: 启动日志以非敏感结构化字段输出最终 lease/renew/recovery TTL，并暴露
  owner 数、renew lag 与丢租约计数。
- Regression test suggestion: 使用非默认 `.env` 解析 Compose，断言容器环境与 Spring
  `DictCacheProperties` 的最终值一致。
- Estimated effort: 30–60 minutes

### Finding: 不兼容缓存协议的禁止混部要求未进入权威部署文档

- Severity: Low
- Confidence: High
- Category: Release / Stability
- Status: Confirmed
- Affected area: Phase 44 上线与回滚
- Evidence:
  - File: `docs/reviews/phase-44-third-remediation-submission-2026-07-26.md:56-57`
  - Function / Module: 发布前提
  - Relevant behavior: 只有候选材料声明所有后端实例必须同版本重启、禁止混部。
  - File: `docs/phase-14-非功能部署验收.md:44-57`
  - Function / Module: 权威停机切换协议
  - Relevant behavior: 已记录 WS-3/WS-2 的不兼容切换，但没有 Phase 44。
  - File: `25f8b1c:DictServiceImpl.java:94-131,300-314`
  - Function / Module: 旧读写协议
  - Relevant behavior: 旧 binary 不认识 writers ZSET；新 binary 又会把旧 `P:<uuid>` 在无 ZSET 时正常化。
- Problem: 新旧协议对 active writer 的来源不同。混部时旧 writer 没有 ZSET，新 READ 会把
  旧 pending 恢复；旧 completion/PUT 也不检查新 ZSET，可绕过新 owner 保护。
- Why it matters: 候选材料是审计快照，不是运维长期入口。滚动发布或旧 binary 回滚可能重新
  打开本轮修复的缓存一致性窗口。
- Realistic failure scenario: 两个后端滚动升级；旧实例开启单值 pending，新实例因 writers
  为空将其恢复为普通版本；读穿结果被发布，直到旧事务完成或 TTL。
- Minimal fix: 将 Phase 44 加入 `docs/phase-14-非功能部署验收.md` 与 README：停写字典管理、
  停全部旧 backend、清理/等待旧 pending、全量启动新 binary、健康检查后恢复；明确禁止旧
  binary 回滚。
- Better long-term fix: 为缓存协议增加版本化 key namespace 或兼容状态机，使旧实例无法写入
  新协议命名空间。
- Regression test suggestion: 用静态部署契约测试检查 Phase 44 停机切换步骤与禁止回滚条款；
  若未来支持混部，再增加 old/new compatibility IT。
- Estimated effort: 30–60 minutes

### Finding: canonical identity 的棕地数据与 collation 前提缺少发布前 preflight

- Severity: Low
- Confidence: Medium
- Category: Data Integrity / Release
- Status: Confirmed
- Affected area: 存量 `sys_dict_type/sys_dict_item.type_code`
- Evidence:
  - File: `platform-boot/src/main/resources/db/migration/V2__dict.sql:7-43`
  - Function / Module: 字典表 DDL
  - Relevant behavior: `type_code` 继承数据库默认 collation，没有列级约束。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:792-805`
  - Function / Module: `normalizeTypeCode`
  - Relevant behavior: 新 binary 会对每个读写 identity 强制 ASCII 模式与小写。
  - File: `25f8b1c..8ff544d`
  - Function / Module: candidate migration set
  - Relevant behavior: 无 Flyway 回填、碰撞检查或启动 preflight。
- Problem: 规范内 `utf8mb4_0900_ai_ci` 环境下，历史大写行仍能被小写查询命中，功能主路径
  安全；但候选没有可移植地证明实际生产列 collation 与规范一致，也没有枚举历史非法码。
- Why it matters: 若棕地库发生 collation 漂移或存在旧版曾允许、现在不合法的 typeCode，
  新服务可能查不到、无法列表或无法维护这些行。
- Realistic failure scenario: 某升级库的列为 case-sensitive，历史保存 `MATERIAL_CATEGORY`；
  新服务统一查询 `material_category`，返回不存在或漏项；另一变体是历史 `foo-bar` 在 VO
  归一时直接抛业务异常。
- Minimal fix: 发布前只读核对两列实际 collation，枚举非 canonical 与不符合新模式的值；
  若需要物理归一，先做碰撞检查，再通过独立 Flyway 迁移处理。
- Better long-term fix: 在数据库层冻结明确 collation/check constraint，并以稳定
  `dict_type_id` 作为缓存 identity。
- Regression test suggestion: 隔离库直接植入物理大写历史 type/item，验证大小写 API 的
  查改删与缓存；再对错误 collation/非法历史码断言 preflight 失败关闭。
- Estimated effort: 0.5 day

## 6. Security Concerns

- Coverage: Medium
- Inspected evidence: typeCode 后端边界、共享 Redis 未提交值可见性、权限/API/凭据增量、证据内容
- Exclusions / limits: 未执行扫描、请求、认证、凭据尝试或任何 cyber 类动作

- 本轮没有新增权限点、鉴权、外部 URL、凭据或敏感字段处理。
- Medium 的直接风险是最终回滚的参考数据短暂跨请求可见，主要归类为数据完整性；若字典用于
  授权/校验分支，也可能形成间接安全影响。
- typeCode 已从前端体验约束收敛为 service 硬约束，schema v2 对旧/错 identity payload
  失败关闭，这是有效改进。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: Redis 四段 Lua、owner 生命周期、续租线程、Spring synchronization、crash recovery
- Exclusions / limits: 未实际中断 Redis、杀进程或注入网络故障

- 正常双 writer 与长事务续租已闭合，先完成者不会删除其它 owner。
- 当前根因是把“owner 不存在”过早等同于“事务已经不可再运行”。实际事务要到
  `beforeCommit` 才被判 rollback，因此 recovery 必须在这段时间继续保持 fail-closed。
- 单线程 renewer 在默认 2m/20s、低频字典写下没有足够证据单列 finding；建议后续增加
  active-owner、renew-lag、queue-depth 指标。
- completion 失败仍由 TTL 自愈并记 ERROR；此边界可接受，但修复 Medium 后 pending TTL
  才真正承担安全恢复而不是立即被 READ 绕过。

## 8. Performance Concerns

- Coverage: Medium
- Inspected evidence: lease/renew 默认值、单线程 scheduler、Redis EVAL 次数、pending 回源
- Exclusions / limits: 未做并发、延迟、吞吐、缓存击穿或压力基准

- 字典写为低频管理操作；pending 期间回源 DB 的代价相对可接受。
- 修复 Medium 后 crash recovery 最多延迟一个 recovery TTL，这是安全优先的有界可用性
  取舍，不应为立即恢复而泄露未提交值。
- 若未来提高写并发，单线程 renewer 的排队延迟应量化；当前不以无数据的容量猜测计 finding。

## 9. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 源码 16 个 IT、目标 XML/text/summary、SHA-256、时间链、44 个单测、独立构建
- Exclusions / limits: Codex 未连接真实依赖；容器 patch 版本与清理取自用户当轮陈述

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 双 owner / 最后 owner 恢复 | High | 正常交错回归 | Keep |
| 跨两个租期续租 | High | scheduler/READ prune 回归 | Keep |
| owner loss aborts commit | Medium | 证明回滚，但不证明回滚前不发布 | Extend |
| crash owner recovery | Medium | 当前断言固化了过早恢复 | Rewrite |
| alias identity/schema v2 | High | DB/Redis/Caffeine identity 回归 | Keep |
| registration / publishLock | High | 已到真实分支/排队点 | Keep |
| 门禁命令 | Medium | 正确 flag 后可信，原 runbook 不可复制 | Fix runbook |

### Valuable Tests

- `overlappingWritersKeepRedisPendingUntilTheLastOwnerCompletes` 真实观测 2 owner、先后完成和中间快照。
- `liveWriterRenewsItsOwnerBeyondTwoLeasePeriods` 以 READ Lua 持续 prune，不是只睡眠看 TTL。
- alias commit/rollback 同时覆盖 DB、Redis 三键与两个 Caffeine cache。
- registration failure 与 publishLock 用例已经排除上一轮的分支/调度假绿。
- XML 的 16 个 testcase 与源码集合一致，目标属性与 summary 计数明确。

### Suspicious Tests

- `ownerLossBeforeCommitAbortsTheTransactionAndRollsBackDb` 的名字只承诺回滚，确实做到；
  但不能外推为“owner loss 期间未提交值不可见”。
- `expiredCrashOwnerAndPendingVersionRecoverFromCommittedDbTruth` 把 expired owner + 尚存 pending
  直接恢复视为正确，实际无法区分 crash 与仍活跃但即将被 beforeCommit 回滚的事务。

### Missing Tests

1. 删除 writers 后，同一事务 read-through 不得向共享 Redis 写入 V2。
2. 上述 read-through 与第二请求的确定性交错。
3. pending 尚存时不得恢复；pending TTL 到期后才允许从 committed DB 重建。
4. 官方门禁对错误 suite 名称必须失败，而正确 suite 必须精确 16。
5. 棕地物理大写/非法 typeCode 与实际 collation preflight。

证据文件：
`docs/reviews/evidence/phase44-third-remediation-rereview-2026-07-26/offline-gates-and-evidence-integrity.txt`。

## 10. Release Concerns

- Coverage: High
- Inspected evidence: Git parent chain、工作树、构建、动态证据、Compose/.env、Phase 14 runbook
- Exclusions / limits: 未 stage、commit、merge、push、deploy 或切流

- `8ff544d` 仍是未合并候选，`890aa6e` 只增加材料；本轮报告/治理变更也未提交。
- 1 个 Medium 阻止 Phase 44 PASS，Phase 0 继续不放行。
- 新旧协议不得混部，但权威部署手册尚未承接该前提。
- 动态 XML 高可信但被 `target/` 忽略、不含候选 SHA；下一候选应归档最小摘要、哈希与命令。
- 即使下一轮 Phase 44 PASS，也只关闭阶段闸门；Phase 0 和最终全量审计仍未完成。

## 11. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `DictCacheProperties` 校验、application defaults、Compose backend environment、`.env.example`
- Exclusions / limits: 未读取实际生产环境变量或运行容器配置

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| SchemaValidation | 0 | `DictCacheProperties` | 保持启动 fail-fast |
| UnsafeDefault | 0 | 2m / 20s | 默认值本身未证实不安全 |
| EnvironmentSeparation | 1 | `DICT_CACHE_WRITER_*` | Compose 显式透传 |
| SecretConfig | 0 | 本轮无新增 | 无动作 |
| FeatureFlag | 0 | 无 feature flag | 无动作 |
| ConfigDocs | 1 | `.env.example` / Phase 14 | 记录约束与部署步骤 |

- 配置类拒绝 null、非正、亚毫秒、溢出及 `renew > lease/3`，边界质量良好。
- 问题在运行时传递链与文档，而不是 Java 属性校验。

## 12. Data Integrity Analysis

- Coverage: High
- Inspected evidence: MySQL 事务可见性、Redis READ/PUT/COMPLETE、payload v2、canonical DB/cache identity
- Exclusions / limits: 未执行真实数据写入或故障注入

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 1 | 未提交字典值不得跨请求可见 | pending 保持至安全恢复 |
| Idempotency | 0 | owner complete 幂等 | 保持 |
| ConcurrencyConsistency | 1 | owner loss 与 cache publish | 本地事务 guard + 新 IT |
| MigrationSafety | 1 Low | 棕地 identity/collation | preflight / 必要时 Flyway |
| InvariantValidation | 0 | typeCode canonical identity | 保持 |
| BackupRestore | 0 | 本轮无变化 | 不适用 |
| Reconciliation | 0 | TTL crash recovery | 修正恢复判定 |

- schema v2 和统一 canonicalizer 实质关闭上一轮 alias identity Medium。
- 数据库 rollback 最终正确并不足够：共享 cache 在 rollback 前可见同样违反事务边界。

## 13. Concurrency Analysis

- Coverage: High
- Inspected evidence: owner ZSET、Redis TIME、renew/complete、beforeCommit、双 writer IT、owner-loss IT
- Exclusions / limits: 未执行 jcstress、Redis 故障或高并发压力

- 正常状态机：
  `begin(owner) -> periodic renew -> beforeCommit renew -> physical completion -> remove(owner)`，
  多 owner 的最后离开规则成立。
- 失效状态机当前是：
  `owner missing + P present -> immediately normal`，这一步早于 Spring transaction 的最终
  rollback/commit 裁决，破坏 happens-before。
- 安全状态机应是：
  `owner missing + P present -> remain pending`；只有 P recovery TTL 到期或有可证明的
  completion token 时才能恢复。
- `beforeCommit` 之后、物理 commit 之前仍存在极窄的 owner-loss 边界；保留 pending 至 TTL
  可覆盖“writers 单独丢失”情形。更强保证需要显式 commit record/outbox，而不是缓存侧租约猜测。

## 14. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Fail closed while state is ambiguous | 1 | Medium | Redis pending recovery |
| Tests must prove the claimed interleaving | 1 | Low | owner-loss/crash recovery tests |
| Runbooks are executable contracts | 3 | Low | Maven command、Compose/.env、Phase 14 |
| Storage and cache share one identity | 0 | — | 本轮已关闭 |

### Principles Respected

- **Canonical identity:** DB 条件/写入、Redis、Caffeine、VO 和前端复用同一实现。
- **Owner-scoped completion:** 一个事务只删除自己的 token，最后 owner 才结束正常窗口。
- **Backend validation:** typeCode 不能由绕过前端的调用污染 keyspace。
- **Deterministic concurrency tests:** publishLock 测试观测真实 queue state。
- **Honest candidate status:** 提交材料明确不是独立 PASS，治理文档此前没有提前放行。

## 15. Recommended Fix Order

### Fix Immediately

1. 修复 `P:` 在 writers 为空时的恢复规则，继续失败关闭到 recovery TTL。
2. 增加事务绑定的本地 write identity，阻止同事务未提交 read-through 发布。
3. 补决定性 owner-loss 同事务/并发读 IT，并改写 crash recovery 断言。

### Fix Before Stable Release

1. 修正门禁命令并精确断言目标 suite 16/16。
2. 透传/记录两个 `DICT_CACHE_WRITER_*` 参数。
3. 把 Phase 44 停机全量切换、禁止混部/旧 binary 回滚写入 Phase 14/README。
4. 执行棕地 collation 与历史 typeCode preflight，必要时用 Flyway 归一。
5. 归档与候选 SHA 绑定的目标 XML/hash/summary。

### Schedule Later

1. 增加 active owner、renew lag、丢租约和 recovery 次数指标。
2. 若计划多实例/Redis Cluster，使用同槽原子 record 与跨节点 revision 通知。
3. 为单线程 renewer 建立并发上界 × Redis RTT 的容量预算。

### Ignore for Now

- 不重写已经成立的 PG-M3 multi-values/500 分块方案。
- 不撤销 owner ZSET、Redis TIME、schema v2 或 canonical identity。
- 不把当前无负载证据的 renewer 容量猜测升级为额外 finding。
- 不为本轮复核启动服务、Docker、数据库、网络或任何 cyber 类验证。

## 16. Quick Wins

| Quick win | Effort | Value |
|-----------|--------|-------|
| 修正 Maven 门禁命令并校验 completed=16 | 15–30 min | 消除复制即失败与零测试假绿 |
| Compose/.env 透传两个 lease 参数 | 30–60 min | 让生产调优合同真实可用 |
| Phase 14 加 Phase 44 禁混部步骤 | 30–60 min | 阻止滚动发布重开一致性窗口 |
| READ 对仍存 `P:` 保持 pending | 1–2 h | 关闭 Medium 的首要路径 |

## 17. Long-term Refactor Plan

1. **Explicit cache transaction state：** 把 revision、writers、recovery deadline 与 payload
   放进一个版本化、同槽 record；动机是消除从缺失 key 猜测事务状态。迁移风险是新旧协议，
   必须以新 namespace 停机切换；用双 writer、owner loss、commit crash 状态机 IT 验证。
2. **Transaction-bound publish guard：** 在 Spring transaction resource 中记录已写
   typeCode；任何共享 cache put 先检查该 guard。动机是覆盖 Redis 状态整体丢失；风险是
   cleanup 泄漏，需 commit/rollback/registration-failure 单测。
3. **Durable post-commit revision：** 若未来要求 callback crash 后仍强一致，引入
   DB revision/outbox，使数据库提交与失效事件可恢复。风险是写放大与重放顺序；需 crash、
   retry、duplicate 与跨节点测试。
4. **Portable gate profile：** 提供 Phase 44 Maven profile，内建正确 reactor 行为、目标计数、
   candidate SHA 与最小 XML 归档，避免每轮人工拼接命令。

---

**Final verdict: CHANGES REQUESTED（0 Critical / 0 High / 1 Medium / 4 Low）。**

第三轮实质关闭 typeCode identity Medium 和上一轮 3 Low，也关闭 Redis 多 writer/续租的
正常路径；但 owner 丢失后的 pending recovery 仍能把最终回滚的未提交值发布到共享 Redis。
修复该单一阻断根因并补精确交错后，可做第四轮最小增量复核。Phase 44 当前不得 PASS，
Phase 0 与最终全量审计继续不放行。
