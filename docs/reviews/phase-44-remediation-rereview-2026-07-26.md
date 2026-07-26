# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 44 remediation
**Audit mode:** incremental + security / stability / performance / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-26
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结基线 `f9ccda310c479cd9a7c9dd39b8e1c73494a71cb4` 与候选
`f9aec54ddfce7202bfcbeb576e6d8c989f21a6e7`。两者是直接父子、单提交增量，
共 21 个路径、`+2063/-44`。复核对象是上一轮
`docs/reviews/phase-44-review.md` 的两项 Major：

1. PG-M3：恢复同事务/同连接的真正通知批量写，或诚实推迟；
2. PG-M4：把参考缓存逐出移动到事务完成后，并补并发陈旧回填反例。

正式结论：**CHANGES REQUESTED（0 Critical / 0 High / 4 Medium / 3 Low）**。

- **PG-M3 生产行为可以关闭。** `NotificationMapper.insertBatch` 是单条
  multi-values INSERT，`InAppNotifyChannel` 按 500 分块；候选真实 MySQL IT
  5/5 证明 SQL 条数、事务连接 identity、外层回滚、1205 反例和自动填充。独立
  离线单测 4/4 与 9 模块 testCompile/package 也通过。
- **PG-M4 仍未关闭。** `EpochGuardedCache` 在第一次纪元检查后先把旧值写入
  delegate，再做第二次检查和补删；第三线程可在两者之间直接读到旧值。现有测试只在
  补删完成后断言缓存为空，没有覆盖“旧值已被供应”的第三读者。
- Redis 侧 `SET version → DEL payload → evict Caffeine` 是一个普通 Runnable：
  既不原子，也没有步骤间故障隔离。payload 命中路径完全不校验 version；部分失败可让
  旧 payload 和本地旧条目继续供应到 12 小时 TTL。
- 新 version key namespace 与合法字典 typeCode 可确定碰撞：
  `version(foo) == payload(ver:foo)`。后端只做 `@NotBlank`，前端字符集规则不是
  服务端硬约束。
- 新 MySQL 锁等待反例把池化连接的 `innodb_lock_wait_timeout` 改为 1 后未恢复；
  Hikari 归还连接不会重置任意 session variable，可能污染后续 IT。
- 三个 Low 是：事务尚未结束时仍可短暂供应事务内读穿的未提交值；新增
  `@Param` 说明与锁定框架字节码不符；候选范围/当前分支文档存在轻微漂移。

独立安全门禁为 `platform-system` **21/21**、后端 **9/9 modules package PASS**、
`git diff --check` PASS。只读解析候选 XML 为 Surefire **216/216**、Failsafe
**196/196**；这些动态 XML 没有内嵌 candidate SHA，故只作为高置信交叉证据，不冒充
本轮独立数据库执行。

Phase 44 继续保持复核退回，Phase 0 不放行。全项目仍由 Phase 0、44 保持
**CHANGES_REQUESTED**。本报告不是 merge、push、部署、切流、稳定发布或最终全量
审计 GO。

本轮没有启动服务、Docker、数据库、Redis、MinIO、浏览器或网络，没有执行扫描、
凭据尝试、故障注入、恶意载荷、fuzz、压力、权限变更或任何可能属于 cyber 的动作。

### Score Dashboard

```text
Security        █████████░  9.2  A   无权限/凭据增量；未执行 cyber 类验证
Stability       ███████░░░  6.8  C   缓存失效部分失败与连接池会话污染仍存在
Performance     ████████░░  8.4  A   PG-M3 真批量成立；缓存补删可制造额外 miss
Testing         ███████░░░  7.4  B   21/21 与 412/412 证据真实，但漏三类关键交错
Maintainability ████████░░  7.5  B   结构清楚；缓存协议复杂且一处框架说明错误
Design          ███████░░░  6.5  C   本地/Redis 发布与失效尚未形成原子协议
Release         ███████░░░  6.6  C   4 个 Medium 阻断阶段 PASS
─────────────────────────────────────
Overall         ████████░░  7.5  B
```

Each dimension is scored 0.0–10.0. **Higher = better.** Overall is the
arithmetic mean of the seven dimensions, rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 4 | 4 | 0 |
| Low | 3 | 3 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **7** | **7** | **0** |

## 2. Project Map

- `platform-system/mapper/NotificationMapper`：新增 12 列 multi-values
  `insertBatch`。
- `platform-system/service/impl/InAppNotifyChannel`：按 500 行分块，单条和批量
  共用实体构造。
- `platform-system/cache/ReferenceCacheInvalidator`：事务有同步上下文时注册
  `afterCompletion`，提交与回滚后执行失效。
- `platform-system/cache/EpochGuardedCache*`：包装全部 Caffeine cache，以进程内
  epoch 拒绝陈旧 loader 回填。
- `platform-system/service/impl/DictServiceImpl`：Redis payload 增加 version token，
  loader 回填使用 Lua CAS。
- `platform-system/service/impl/SystemManagementServiceImpl`：参数写移除
  `@CacheEvict`，改为事务完成后清空。
- `platform-boot` 两个新 IT：真实 MySQL 通知批量写 5 个用例、真实
  MySQL/Redis 缓存提交窗口 6 个用例。
- `platform-system` 三个新测试类：纪元守卫 10、失效器 7、通知批量 4。
- 治理/证据：候选提交材料、门禁摘录和当前计划状态。

本轮无 Flyway、业务状态、权限点、对外 API、前端生产代码或依赖版本升级。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `f9ccda3..f9aec54` 全部 21 路径、直接父子关系、调用方和邻接实现 | 未重开其它 Phase |
| Security | Medium | 后端 typeCode 校验、证据卫生、权限/API 零变化 | 未执行扫描、登录、请求或凭据操作 |
| Stability | High | transaction callback、Redis/Caffeine 失败顺序、连接池 session 状态 | 未做故障注入或服务中断 |
| Performance | Medium | 500 分块、6000 参数、SQL 次数和缓存抖动 | 未做延迟、吞吐或压力基准 |
| Testing Authenticity | High | 5 个新测试文件、XML、源码/产物时间链、反例命中条件 | 未独立重跑真实 MySQL/Redis |
| Release | High | commit、工作树、构建、报告、治理状态与未合并边界 | 未 merge/push/deploy |
| Configuration | Medium | CacheManager 包装、Redis key/TTL、MySQL session 变量 | 未读取仓库外生产配置 |
| Data Integrity | High | 参考数据提交/回填/逐出和 namespace 映射 | 未写入真实数据 |
| Concurrency | High | Caffeine 三线程交错、Redis步骤交错、事务完成顺序 | 未执行攻击性并发、锁或压力 |
| Maintainability / Design | High | Spring Cache API、MyBatis-Plus 3.5.16、HikariCP 5.1.0 字节码 | 未做全仓缓存架构重审 |

## 3. Incremental Change Summary

### Change Summary

- Baseline: `f9ccda310c479cd9a7c9dd39b8e1c73494a71cb4`
- Candidate / review HEAD: `f9aec54ddfce7202bfcbeb576e6d8c989f21a6e7`
- Candidate commits: 1
- Changed paths: 21
- Lines: +2063/-44
- Production files added/changed: 9
- Test files added: 5
- Governance/evidence files added/changed: 7
- Author identity: `[REDACTED_USER]`

### Change Categories

| Category | Files | Summary |
|----------|-------|---------|
| Notification production fix | 2 | mapper multi-values INSERT + channel chunking |
| Cache production fix | 7 | invalidator、epoch wrapper、manager、config、dict/param writes |
| Tests | 5 | 21 offline tests + 11 real-dependency IT cases |
| Module test dependency | 1 | `spring-boot-starter-test` test scope |
| Governance / evidence | 6 | candidate state, submission and gate excerpt |
| Flyway / API / permission / frontend | 0 | no change |

### Previous Finding Closure Matrix

| Previous requirement | Candidate evidence | Independent result |
|----------------------|--------------------|--------------------|
| PG-M3 不得把逐行写冒充真批量 | mapper multi-values、500 分块、SQL 探针 | **Closed** |
| PG-M3 必须复用外层事务连接 | Connection `isSameAs`、外层 rollback 0 残留 | **Closed** |
| PG-M3 必须回归 44a 锁等待 | 另连接 1205、同连接及时完成 | **Closed behaviorally; test session cleanup has new Medium** |
| PG-M4 逐出不得发生在提交前 | `afterCompletion` + commit/rollback IT | **Closed for normal callback timing** |
| PG-M4 提交前旧 loader 不得回填 | epoch + Redis version CAS | **Not closed: local publish window and Redis protocol gaps** |
| D11 完成状态必须诚实 | 候选/独立结论分离 | **Mostly closed; two metadata Low items remain** |

### Risk Delta

- Previous blocking findings closed: PG-M3 Major 1
- Previous blocking findings not closed: PG-M4 Major 1
- New blocking findings: 4 Medium
- New non-blocking findings: 3 Low
- Confirmed permission/API/DDL regressions: 0
- Confirmed production notification regression: 0
- Confirmed cache correctness gaps: 3

### Test Coverage Delta

- Independent offline tests: 21/21
- Independent backend package/testCompile: 9/9 modules
- Candidate XML cross-check: 216/216 Surefire + 196/196 Failsafe
- New notification IT: 5/5
- New cache IT: 6/6
- Valuable negatives: outer rollback, separate-connection 1205, pre-commit loaded V1,
  commit/rollback eviction, 1001-row chunking
- Missing negatives:
  - stale value visible between delegate put and compensating delete;
  - Redis version advanced while old payload is still readable;
  - `foo` / `ver:foo` namespace collision;
  - Redis exception must not suppress local eviction;
  - pooled session variable restoration.

### Approval Recommendation

**Request changes / keep Phase 44 returned.**

下一候选无需重写 PG-M3 生产批量方案。最小重交范围：

1. 让本地 cache 的 publish/read/invalidate 真正原子，补第三读者交错；
2. 让 Redis version 推进与 payload 失效成为原子协议，并让 payload 命中验证版本；
3. 隔离 Redis 故障与两个本地 Caffeine 逐出；
4. 消除 Redis key namespace 碰撞并后端硬校验 typeCode；
5. 恢复 MySQL IT 修改的 session 变量；
6. 修正三个 Low 文档/边界，并跑安全离线回归。

## 4. Top Risks

1. **Medium — Caffeine 旧值先发布后补删：** 第三线程可以在二次检查前读到提交前旧值。
2. **Medium — Redis 失效协议非原子：** 旧 payload 可与新 version 并存，部分失败后
   最长供应 12 小时。
3. **Medium — Redis key namespace 可碰撞：** 两个后端允许的 typeCode 会共享
   payload/version key。
4. **Medium — MySQL session 变量污染连接池：** 后续 IT 可能无关地在 1 秒时报 1205。
5. **Carry-forward — 多实例 Caffeine 无广播：** 审计已明确列为 long-term fix，
   本轮不升级为新增 finding。

## 5. Detailed Findings

### Finding: `EpochGuardedCache` 会在二次纪元检查前公开陈旧值

- Severity: Medium
- Confidence: High
- Category: Concurrency / Data Integrity
- Status: Confirmed
- Affected area: `sysParam`、`dictLabels`、`orgDictItems` 及全部被包装的 Caffeine cache
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/cache/EpochGuardedCache.java:108-120`
  - Function/module: `put`
  - Relevant behavior: 第一次 epoch 比对通过后先 `delegate.put`，之后才二次比对并
    `evictIfPresent`。
  - File: `platform-system/src/test/java/cn/edu/gpnu/platform/system/cache/EpochGuardedCacheTest.java:70-79`
  - Function/module: `putIsUndoneWhenEvictionInterleavesAfterTheEpochCheck`
  - Relevant behavior: 测试只在 put/evict 全部完成后检查最终为空，没有在陈旧值已写入、
    补删尚未执行时插入第三读者。
  - Evidence file:
    `docs/reviews/evidence/phase44-remediation-rereview-2026-07-26/offline-gates-and-evidence-integrity.txt`
- Problem: “写后发现 epoch 改变再删除”只保证最终状态，不保证陈旧值从未被读取。
- Why it matters: 参数和字典参与后端硬校验；一次短暂旧值命中也可能让请求按已失效规则
  通过，不能以随后删除缓存抵消已经返回的业务结果。
- Realistic failure scenario: loader 已取得旧视频阈值；管理员更新并提交，逐出完成；
  loader 将旧阈值写回，另一个上传请求立即命中旧值并通过；loader 随后才补删。
- Minimal fix: 对同一 cache 的 get/publish/invalidate 建立真正线性化的锁或等价原子
  协议；至少保证旧 loader 的 epoch 检查和底层 publish 不能与失效及第三方 get 穿插。
- Better long-term fix: cache entry 携带 reference-data revision，读取时验证当前 revision；
  多节点用统一版本/广播协议。
- Regression test suggestion: 使用可控 delegate 在 `delegate.put` 后阻塞旧 loader，启动
  第三线程 get，断言它不能拿到 STALE；再放行并断言不会删除另一线程刚写入的 FRESH。
- Estimated effort: 0.5–1 day

### Finding: Redis 版本推进、负载删除和本地逐出没有形成原子且故障隔离的协议

- Severity: Medium
- Confidence: High
- Category: Data Integrity / Stability / Concurrency
- Status: Confirmed
- Affected area: `DictServiceImpl.listItems` Redis payload、两个字典 Caffeine cache
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:212-218`
  - Function/module: `evictItemsCache`
  - Relevant behavior: `SET version`、`DEL payload`、两个 Caffeine evict 串行放在同一个
    Runnable；前一步异常会跳过所有后续动作。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:270-299`
  - Function/module: `getCachedItems` / `putCachedItems`
  - Relevant behavior: payload 命中直接反序列化返回，不读取或比较 version；只有 miss
    后的回填走 CAS。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/cache/ReferenceCacheInvalidator.java:50-57`
  - Function/module: `afterCompletion`
  - Relevant behavior: 回调任一 RuntimeException 被整体记录并吞掉，事务已提交。
- Problem: 正常执行存在 `version 已更新、旧 payload 尚未删除` 的可读窗口；部分失败会把
  该状态延长到 TTL。Redis 异常还会阻止与 Redis 无依赖的本地逐出。
- Why it matters: 写接口可以成功返回而三个缓存层仍供应旧字典，最长 12 小时；这正是
  PG-M4 要消除的“提交后旧规则继续生效”。
- Realistic failure scenario: 数据库已提交新字典；version SET 成功，DEL 因短暂连接错误
  失败。异常被 afterCompletion 吞掉，本地两个 cache 未清；Redis 恢复后旧 JSON 与本地
  旧标签继续命中。
- Minimal fix: 用单个 Lua 原子执行 version 推进和 payload 删除；Redis 和两个本地逐出
  分别 best-effort，至少在 finally 中保证本地逐出不被 Redis 异常跳过。payload 命中需
  携带/核验 version，避免部分状态被当作有效。
- Better long-term fix: 持久 outbox/retry 驱动参考数据 revision，所有节点按 revision
  验证缓存；失效失败有可观察和可重试状态。
- Regression test suggestion: 确定性阻塞在 version SET 后、DEL 前并发读取；分别让 SET、
  DEL 抛错，断言本地 cache 必清且旧 payload 永不作为有效命中。
- Estimated effort: 0.5–1.5 days

### Finding: Redis version key 与合法字典 typeCode 的 payload key 可碰撞

- Severity: Medium
- Confidence: High
- Category: Configuration / Data Integrity
- Status: Confirmed
- Affected area: Redis `dict:items:*` keyspace 与字典管理 API
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:44-59`
  - Function/module: `CACHE_PREFIX` / `CACHE_VERSION_PREFIX`
  - Relevant behavior: payload prefix 为 `dict:items:`，version prefix 为
    `dict:items:ver:`。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:311-317`
  - Function/module: `cacheKey` / `versionKey`
  - Relevant behavior: typeCode 原样拼接，没有长度前缀、编码或分隔转义。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/dto/DictTypeSaveRequest.java:12-13`
  - Function/module: backend request validation
  - Relevant behavior: 仅 `@NotBlank`。
  - File: `frontend/src/views/system/components/dict/DictTypeDrawer.vue:35-36`
  - Function/module: client-only validation
  - Relevant behavior: 字符集限制只存在于前端，不能保护直接 API 调用。
- Problem: `version(foo)` 与 `payload(ver:foo)` 都是 `dict:items:ver:foo`。
- Why it matters: 两个合法后端字典类型会互相把 JSON、UUID、删除和 CAS 状态覆盖；
  最终可能反序列化失败、重复回源、误删版本或让陈旧 loader 通过。
- Realistic failure scenario: 管理员或集成调用创建 `foo` 与 `ver:foo`；更新 `foo` 时设置
  version，等价于覆盖另一个字典的 payload；随后任一侧逐出又删除另一侧状态。
- Minimal fix: 后端冻结 typeCode 字符集并拒绝保留前缀；同时改为绝对不相交且编码后的
  data/version namespace。
- Better long-term fix: 用单个 Redis hash/hash-tag 保存同一 typeCode 的 revision 与
  payload，兼顾 namespace 隔离和未来 Redis Cluster 同槽原子脚本。
- Regression test suggestion: 通过后端 service/API 创建 `foo` 与 `ver:foo`，断言两者
  payload/version key 永不相同，更新/逐出互不影响。
- Estimated effort: 2–4 hours

### Finding: 锁等待 IT 未恢复池化连接的 MySQL session 变量

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity / Stability
- Status: Confirmed
- Affected area: `Phase44NotificationBatchIT` 之后复用同一 Hikari 物理连接的所有 IT
- Evidence:
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase44NotificationBatchIT.java:266-290`
  - Function/module: `insertFromSeparateConnection`
  - Relevant behavior: 设置 `SET SESSION innodb_lock_wait_timeout = 1`，finally 只 rollback
    和 close，没有读取/恢复旧值。
  - Evidence file:
    `docs/reviews/evidence/phase44-remediation-rereview-2026-07-26/offline-gates-and-evidence-integrity.txt`
  - Relevant behavior: 锁定 HikariCP 5.1.0 bytecode 只恢复标准 JDBC connection state，
    不恢复任意 MySQL session variable。
- Problem: close 把物理连接归还池，而不是关闭 session；1 秒上限继续留在该连接。
- Why it matters: 后续无关 IT 可依连接池分配和执行顺序偶发提前 1205，使全量门禁不稳定，
  也可能把真实生产锁问题误判为随机测试失败。
- Realistic failure scenario: Phase44 反例归还连接，后续 Phase47/48 测试恰好借到它；
  正常应在数秒内完成的锁等待在 1 秒失败，单跑通过、全量偶发失败。
- Minimal fix: 修改前读取原 session 值，并在同一个 finally、close 之前恢复；恢复失败应
  明确使该连接失效而不是归还污染 session。
- Better long-term fix: 为会修改 session 的反例使用专用非池化 DataSource/连接，并在
  测试基础设施统一提供 session-state guard。
- Regression test suggestion: 反例结束前/后读取 session 值，断言恢复；再从池中借连接
  验证默认合同未漂移。
- Estimated effort: 15–30 minutes

### Finding: afterCompletion 只能事后清理，不能阻止事务期间供应未提交读穿值

- Severity: Low
- Confidence: High
- Category: Data Integrity / Design Boundary
- Status: Confirmed
- Affected area: 在写事务内再次调用缓存读方法的组合调用
- Evidence:
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase44CacheCommitWindowIT.java:219-238`
  - Function/module: `dictEvictionAlsoRunsOnRollbackSoUncommittedValuesNeverSurvive`
  - Relevant behavior: 测试明确把未提交 V2 装入共享 labels cache，只在 rollback
    afterCompletion 后清除。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/cache/ReferenceCacheInvalidator.java:14-28`
  - Function/module: class contract
  - Relevant behavior: 注释容易被读成“其它请求不会读到未提交值”，实际只保证完成后清除。
- Problem: rollback 后没有残留不等于事务执行期间从未泄漏。
- Why it matters: 将来若一个事务在字典写后又经代理调用缓存读，另一个请求可在事务结束前
  命中未提交数据。
- Realistic failure scenario: 新编排服务 updateItem 后为构造响应调用 dictLabels；
  缓存发布 V2，另一个请求命中，之后事务回滚。
- Minimal fix: 收紧注释和调用合同，明确写事务内禁止共享缓存读穿；可用事务上下文让
  cache put 失效或延迟到 commit。
- Better long-term fix: 以 committed revision 作为 cache publication 前置条件。
- Regression test suggestion: 在事务内装入未提交值后启动第二读者，要求其不能从共享
  cache 取得 V2。
- Estimated effort: 1–3 hours

### Finding: `@Param` 会破坏 MyBatis-Plus 填充的说明与锁定版本不符

- Severity: Low
- Confidence: High
- Category: Documentation / Maintainability
- Status: Confirmed
- Affected area: `NotificationMapper.insertBatch` 的维护合同
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/mapper/NotificationMapper.java:19-23`
  - Function/module: method documentation
  - Relevant behavior: 声称加 `@Param` 后只剩自定义键，MP 不再逐元素填充。
  - Evidence file:
    `docs/reviews/evidence/phase44-remediation-rereview-2026-07-26/offline-gates-and-evidence-integrity.txt`
  - Relevant behavior: MP 3.5.16 `extractParameters(Map)` 遍历 ParamMap values、去重并
    逐项转 collection。
- Problem: 当前实现正确，但给出的框架原因错误；真正需要保持一致的是
  `<foreach collection='list'>` 与参数绑定名。
- Why it matters: 维护者可能按错误框架模型排查自动填充问题，或误以为任何 `@Param`
  都天然不兼容。
- Realistic failure scenario: 后续为可读性加 `@Param("rows")` 同时改 foreach；填充其实
  仍可工作，但错误注释促使团队绕开可验证路径或引入另一套 ID 填充。
- Minimal fix: 修正文案，只陈述当前无注解 List 提供 `list`/`collection` 绑定，并以
  真实 IT 作为自动填充合同。
- Better long-term fix: 把 mapper 参数名与填充行为做成小型 slice test，避免依赖长注释。
- Regression test suggestion: 可选增加 `@Param("rows")` + `collection='rows'` 对照测试，
  验证锁定版本实际行为。
- Estimated effort: 15 minutes

### Finding: 候选范围与当前分支元数据有轻微漂移

- Severity: Low
- Confidence: High
- Category: Release / Documentation
- Status: Confirmed
- Affected area: 接手入口和 candidate scope 摘要
- Evidence:
  - File: `HANDOFF.md:10`
  - Function/module: current handoff snapshot at review start
  - Relevant behavior: 实际分支为 `codex/phase44-remediation`，但“当前分支”仍写
    Phase 53 分支。
  - File: `docs/CURRENT-EXECUTION-PLAN.md:9`
  - Function/module: authoritative current baseline at review start
  - Relevant behavior: 同样保留旧当前分支。
  - File: `docs/reviews/phase-44-remediation-submission-2026-07-26.md:9-10`
  - Function/module: scope summary
  - Relevant behavior: 摘要称新增 3 个测试类，实际另有 platform-boot 两个 IT，共 5 个。
- Problem: 后文能找到正确事实，但入口摘要不自洽。
- Why it matters: 接手者可能在错误分支审查，或低估变更和测试边界。
- Realistic failure scenario: 后续复核按“3 个测试类”只核 platform-system，遗漏两个真实
  依赖 IT 的连接状态与清理副作用。
- Minimal fix: 当前治理同步改正分支；下一提交材料将测试边界写成 5 个新增测试类。
- Better long-term fix: 提交材料的分支、基线、HEAD、路径数由脚本生成。
- Regression test suggestion: 报告 lint 增加当前 branch 与 `git branch --show-current`、
  changed test path count 的一致性检查。
- Estimated effort: 10 minutes

## 6. Security Concerns

- 本轮没有认证、授权、DataScope、敏感字段、预签名 URL、上传或权限点变化。
- Redis namespace collision 可由正常管理 API 输入触发，归类为数据完整性，不需要恶意
  载荷即可复现。
- 候选门禁摘录使用占位符，没有在本报告重复任何凭据值。
- 未运行 secret scanner 或其它扫描；因此不声称全仓无秘密，只确认本次读到的候选材料
  没有必要暴露值。
- 未执行任何 cyber 类动作。

## 7. Stability Concerns

- Redis afterCompletion 失败不能反向回滚已提交数据库，因此缓存失效必须独立、可观察、
  可重试；当前只写 ERROR 且跳过后续本地清理。
- Hikari session 变量污染会让全量 IT 对顺序和连接池选择敏感。
- `EpochGuardedCache` 的补偿删除可能删掉另一个线程刚写入的 FRESH；虽不供应旧值，
  仍会造成 cache miss 与潜在 DB stampede。
- ThreadLocal 未配对 load 最多保留 32 个 key；当前 key 都很小且属次要维护边界，不单列
  finding。

## 8. Performance Concerns

- PG-M3 将 N 条 insert 收敛为 `ceil(N/500)`，1001 行是 3 条；6000 参数低于常见
  prepared-statement 参数上限，最坏 payload 低于项目 64 MiB packet 合同。
- 本轮未执行大扇出延迟/吞吐基准，不能把语句数证明扩大成容量证明。
- cache-wide epoch 会因任一 key 逐出使全部在途 loader 作废，方向安全但可能增加回源；
  当前写少读多，暂不单列性能 finding。
- 补偿 `evictIfPresent` 删除 FRESH 会加重并发 miss，修复线性化协议时应一并消除。

## 9. Testing Authenticity Analysis

### Confidence Assessment

- **High：** 源码与 candidate blob、直接父子提交、diff、独立 testCompile/package。
- **High：** 新增 21 个离线测试由独立复核实际执行。
- **High but not cryptographically bound：** 候选 216/196 XML、源码/编译/XML/提交时间链。
- **Not independently rerun：** MySQL 1205、真实 transaction connection、Redis/Caffeine
  提交窗口。按用户安全边界只读核验。

### Valuable Tests

- `sendBatchRollsBackWithTheOuterTransaction`
- `sendBatchIssuesOneMultiValuesInsertOnTheTransactionConnection`
- `batchInsertDoesNotWaitWhileOuterTransactionHoldsUserRowLocks`
- `preCommitDictLabelsLoadIsNotRefilledAfterEviction`
- `preCommitRedisItemsLoadIsNotRefilledAfterEviction`
- commit/rollback 两类 afterCompletion 断言

### Suspicious Tests

- `putIsUndoneWhenEvictionInterleavesAfterTheEpochCheck` 只验证最终 cache 空，不验证中间
  陈旧值不可见。
- `invalidationFailureDoesNotEscapeTheCallback` 把“吞掉异常”视为目标，但没有断言 Redis
  失败后独立 Caffeine 清理仍执行。
- rollback 清理测试证明未提交值会进入共享 cache，却没有并发读者断言其事务期间不可见。
- 锁等待 IT 没有验证 session variable 恢复。

### Missing Tests

1. Caffeine stale publish 窗口的第三读者。
2. Caffeine stale loader 不得删除 FRESH。
3. Redis SET/DEL 中间读和 DEL failure。
4. Redis failure 后两个本地 cache 仍清理。
5. `foo` / `ver:foo` namespace 隔离。
6. pooled MySQL session state restore。

## 10. Release Concerns

- Phase 44 不得置 PASS，不得放行 Phase 0。
- 候选提交尚未 merge；本报告未授权 stage/commit/push/merge/deploy。
- 当前生产 Compose 是单 backend；多实例 Caffeine 广播缺失按原审计 long-term fix
  保留，不冒充本次关闭。
- Redis Cluster 未验证且两个 key 没有 hash tag；当前单实例部署边界已披露，下一版若用
  Lua 原子失效应顺带设计同槽 key。
- 最终全量审计仍需在 Phase 0 与 Phase 44 全部关闭后执行。

## 11. Configuration Safety Analysis

### Configuration Summary

| Item | Result |
|------|--------|
| CacheManager bean | 全部通过 EpochGuardedCacheManager，单 JVM wrapper 单例成立 |
| Caffeine TTL | param 30m；dict 12h |
| Redis payload TTL | 12h |
| Redis version TTL | 24h |
| Redis deployment | 当前单实例；cluster 未验证 |
| MySQL max packet | 仓库契约 64 MiB |
| IT lock wait session | 临时改 1s，但未恢复 |
| Backend typeCode validation | 仅 NotBlank，不足以保护 Redis namespace |

配置默认值本身没有新秘密；问题集中在 key 设计和会话状态恢复。

## 12. Data Integrity Analysis

### Integrity Summary

- 通知批量行字段、ID、审计时间、deleted default 与逐行路径等价。
- 通知批量在外层事务内同生共死；PG-M3 原完整性问题关闭。
- 参数/字典写的 afterCompletion 时点优于基线提交前逐出。
- 但本地旧值公开窗口、Redis 部分状态和 namespace collision 仍可让已提交新规则没有
  立即成为唯一可供应版本。
- rollback afterCompletion 会清除最终残留，但不能撤回事务期间已被其它线程读取的
  未提交值。

## 13. Concurrency Analysis

- Caffeine 的 epoch 只是一致性信号；没有把 delegate get/put/evict 组成临界区。
- “二次检查 + 补删”能恢复最终状态，但不是线性化 publish。
- Redis loader CAS 只保护 miss 后回填；hit path 不看 version，因此 version 不能作为
  完整的读取有效性合同。
- Redis invalidation 的三个远端/本地步骤没有原子性或独立失败处理。
- 事务同步回调在方法返回前执行正常成立；本报告不质疑 afterCompletion 本身的时点，
  只指出回调内部协议。
- 多实例本地 cache 广播仍缺失，按原审计 long-term fix 继续跟踪。

## 14. Principles Compliance

### Principles Violated

- **Do not publish before validation：** Caffeine 先写陈旧值，再验证并补删。
- **Cache invalidation is a protocol：** Redis version、payload 与本地缓存没有形成一个
  原子/可重试合同。
- **Namespaces must be injective：** 两类 key 构造对后端合法 typeCode 不是一一映射。
- **Tests must clean their state：** IT 修改池化 session 后未恢复。
- **Backend validation is authoritative：** typeCode 字符限制只在前端。

### Principles Respected

- **Candidate is not PASS：** 提交材料明确是整改者证据。
- **Transactions are explicit：** PG-M3 使用事务绑定 mapper connection。
- **Bound work：** 通知批量按 500 分块。
- **Negative evidence matters：** 1205、rollback、提交前 V1 和 rollback V2 均有反例。
- **Historical reports are immutable：** 本报告不改写上一轮 CHANGES_REQUESTED 快照。
- **Safety boundary is explicit：** 独立复核没有执行 cyber 类动作。

## 15. Recommended Fix Order

### Fix Immediately

1. 线性化 `EpochGuardedCache` 的 get/publish/invalidate，并补第三读者与 FRESH 不被误删
   两个确定性交错。
2. 用 Lua 原子推进 Redis version + 删除 payload；payload 命中验证 version。
3. Redis 与两个 Caffeine 逐出分别执行，Redis 失败不能跳过本地清理。
4. 改造 key namespace，并在后端硬校验 typeCode；补 `foo` / `ver:foo` 反例。
5. IT 在 close 前恢复 `innodb_lock_wait_timeout`。

完成后只需对上述增量与必要回归做第二轮独立复核；PG-M3 生产实现无需重写。

### Fix Before Stable Release

1. 明确禁止写事务内共享 cache read-through，或让 cache publication 延迟到 commit。
2. 增加 Redis 失效失败的可重试/告警门禁。
3. 把大扇出延迟/吞吐从“SQL 条数证据”升级为有资源上限的容量门禁。
4. 修正 MyBatis-Plus 参数说明和提交材料测试数量。

### Schedule Later

1. 统一参考数据 revision 与多节点 Caffeine 广播。
2. 若部署 Redis Cluster，采用 hash tag/单 hash 使脚本同槽。
3. 把缓存交错模型做成可复用 deterministic concurrency test harness。

### Ignore for Now

- 不要求在当前已知阻断候选上启动 Docker、MySQL、Redis 或 MinIO。
- 不把 Phase 0 或最终全量审计的其它问题混入本轮修复。
- 不重跑任何可能属于 cyber 的动态验证；若下一候选需要真实依赖门禁，由用户自行执行
  并归档证据。

## 16. Quick Wins

| Action | Effort | Benefit |
|--------|--------|---------|
| 恢复 IT 的 session 变量 | 15–30 min | 关闭测试稳定性 Medium |
| 后端 typeCode 加 Pattern + 保留前缀拒绝 | 30–60 min | 先阻断当前 namespace 冲突 |
| Redis/Caffeine 清理用独立 try/finally | 30–60 min | Redis 故障不再连带本地旧缓存 |
| 修正 `@Param` 注释和测试数量 | 15 min | 关闭两个文档 Low |
| 增第三读者阻塞测试 | 30–60 min | 防止 epoch 算法再次虚绿 |

## 17. Long-term Refactor Plan

1. **Reference revision model：** 字典/参数每次提交生成持久 revision；payload、进程内条目
   和节点广播都绑定同一 revision。
2. **Atomic Redis record：** 同一 typeCode 的 revision 与 payload 放在不可碰撞、同槽的
   单一记录中，读写均以原子脚本验证。
3. **Linearizable local adapter：** 用明确锁或版本化 entry 让 get/publish/invalidate
   有可证明的线性化点，不依赖写后补偿删除。
4. **Test resource guard：** 数据库 session、Redis DB、cache、系统属性等测试修改统一
   注册 snapshot/restore。
5. **Final full audit：** Phase 44 与 Phase 0 都 PASS 后，再统一执行全项目安全、业务规则、
   数据一致性、性能、部署和证据审计。

---

**Final verdict: CHANGES REQUESTED（0 Critical / 0 High / 4 Medium / 3 Low）。**

PG-M3 原 Major 已按生产行为关闭；PG-M4 仍因本地旧值公开窗口、Redis 非原子/故障耦合
和 key namespace 碰撞而未关闭。新增锁等待 IT 还会污染池化 MySQL session。修复上述
4 个 Medium 并补对应反例后，可只做第二轮增量复核。Phase 44 当前不得 PASS，Phase 0
不放行，全部退回项关闭后仍需执行用户要求的最终全量审计。
