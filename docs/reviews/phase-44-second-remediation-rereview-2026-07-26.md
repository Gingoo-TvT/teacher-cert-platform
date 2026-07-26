# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 44 second remediation
**Audit mode:** incremental + security / stability / performance / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-26
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结基线
`f9aec54ddfce7202bfcbeb576e6d8c989f21a6e7` 与第二轮候选
`25f8b1c0683dae38f930a8cb94baf937a446013d`。两者为直接父子、单提交增量，共
20 个路径、`+2175/-228`。复核对象是上一轮正式报告
`docs/reviews/phase-44-remediation-rereview-2026-07-26.md` 的
4 Medium / 3 Low。

正式结论：**CHANGES REQUESTED（0 Critical / 0 High / 2 Medium / 3 Low）**。

- 上一轮 **Caffeine 精确键发布窗口 Medium 已关闭**：`put` 的 epoch/pending 校验与
  `delegate.put` 已置于同一 `publishLock` 临界区，失效与开/关写窗口使用同一把锁；
  陈旧 loader 校验失败时不再“先写后补删”。
- 上一轮 **Redis 单写事务下的三个具体缺口已关闭**：版本推进与负载删除合并为单
  Lua；读路径原子取回 payload/version 并校验 envelope 版本戳；本地两个逐出与 Redis
  步骤独立失败处理。
- 上一轮 **payload/version 前缀确定性碰撞已关闭**：
  `dict:items:` 与 `dict:items-version:` 在固定字符处不同；写 DTO 与 service 增加了
  typeCode 字符集硬约束。
- 上一轮 **MySQL 池化 session 污染 Medium 已关闭**：锁等待反例保存并恢复
  `innodb_lock_wait_timeout` 与 autocommit，且恢复后同连接复读断言。
- 但 Redis pending 仍只是一个会被覆盖的 `P:<uuid>`，没有事务 owner/refcount；
  两个同 typeCode 写事务重叠时，先完成者会在另一事务仍活跃时无条件恢复普通版本。
  固定 60 秒 pending TTL 也没有对应的强制事务上限。该协议会重新打开本轮试图关闭的
  “数据库提交到 afterCompletion”旧值窗口。
- 数据库采用 `utf8mb4_0900_ai_ci`，typeCode 在数据库中大小写不敏感；Redis 与
  Caffeine 键却大小写敏感，而新后端正则同时允许大写和小写。正常管理输入可以让同一
  字典形成多套缓存键，写窗口与逐出只命中其中一套，旧值最长保留到 12 小时 TTL。
- 上一轮 `@Param` Low **未关闭**：`NotificationMapper` 仍保留与
  MyBatis-Plus 3.5.16 字节码相反的说明，且与提交材料 R2-6 的“已修正”声明直接冲突。
- 新增两个 testing-authenticity Low：一个所谓 registration failure 测试实际只走无事务
  立即分支；两个锁串行测试在没有“竞争线程已开始”探针的情况下，仅以 300ms 未完成
  推断被锁阻塞，存在调度延迟假绿空间。

独立安全门禁为 `platform-system` **46/46 PASS**、后端
**9/9 modules package PASS**、`git diff --check` PASS。只读解析本机候选证据为
Surefire **241/241**、Failsafe **200/200**，合计 **441/441**；源码、编译产物、
XML、前端产物、门禁摘录、提交材料与候选提交的时间链顺序一致，11 个本轮 Java
工作树 blob 与候选提交一致。XML/完整运行日志未受 Git 跟踪且没有内嵌 candidate SHA，
所以只作为高置信本机交叉证据，不冒充 fresh-clone 可移植的严格提交级动态证明。

Phase 44 继续保持复核退回，Phase 0 不放行。全项目仍由 Phase 0、44 保持
**CHANGES_REQUESTED**。本报告不是 merge、push、部署、切流、稳定发布或最终全量
审计 GO。

本轮没有启动服务、Docker、数据库、Redis、MinIO、浏览器或网络，没有执行扫描、
凭据尝试、故障注入、恶意载荷、fuzz、压力、权限变更或任何可能属于 cyber 的动作。

### Score Dashboard

```text
Security        █████████░  9.0  A   无权限/凭据增量；未执行 cyber 类验证
Stability       ███████░░░  6.6  C   pending 所有权与租约寿命仍可重开旧值窗口
Performance     ████████░░  8.4  A   真批量保持；未做大扇出或缓存回源基准
Testing         ████████░░  7.5  B   46/46 与 441/441 可信，但漏两类关键交错
Maintainability ███████░░░  7.4  B   协议结构改善；框架注释仍与锁定版本相反
Design          ██████░░░░  6.4  C   DB 身份、缓存键与事务 pending 尚未统一
Release         ██████░░░░  6.2  C   2 个 Medium 阻断阶段 PASS
─────────────────────────────────────
Overall         ███████░░░  7.4  B
```

Each dimension is scored 0.0–10.0. **Higher = better.** Overall is the
arithmetic mean of the seven dimensions, rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 2 | 2 | 0 |
| Low | 3 | 3 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **5** | **5** | **0** |

## 2. Project Map

- `platform-system/cache/EpochGuardedCache`：以进程内 epoch、pending 计数和
  `publishLock` 保护 Caffeine 读穿发布。
- `platform-system/cache/ReferenceCacheInvalidator`：事务内同步开启写窗口，
  `afterCompletion` 逐步执行失效并在 finally 解除本地窗口。
- `platform-system/service/impl/DictServiceImpl`：Redis payload/version 双键、pending
  令牌、Lua 原子读/失效/CAS 回填，以及字典所有写路径的缓存 choke point。
- `platform-system/dto/Dict*SaveRequest`：写路径 typeCode 字符集校验。
- `platform-system/mapper/NotificationMapper`：同事务/同连接 multi-values INSERT
  及其参数/自动填充说明。
- `platform-boot/Phase44CacheCommitWindowIT`：真实 MySQL/Redis 的单写事务窗口、
  rollback、旧 loader、残留负载和跨类型测试。
- `platform-boot/Phase44NotificationBatchIT`：真实 MySQL 的单连接批量写、分块、
  外层回滚、1205 反例和 session 恢复。
- `platform-system` 三个缓存/失效/键测试：46 个离线单测中的 42 个直接覆盖本轮缓存协议。
- 治理/证据：第二轮提交材料、门禁摘录、上一轮正式报告与当前唯一执行计划。

本轮无 Flyway、业务状态、权限点、对外 API 路径、前端生产代码或依赖版本变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `f9aec54..25f8b1c` 全部 20 路径、直接父子、调用方和配置邻接面 | 未重开其它 Phase |
| Security | Medium | 后端 typeCode 约束、证据卫生、权限/API 零变化 | 未执行扫描、登录、请求或凭据操作 |
| Stability | High | transaction callback、pending TTL、失败隔离、池化 session 恢复 | 未做故障注入或服务中断 |
| Performance | Medium | 500 分块、缓存 TTL、失效时回源 | 未做延迟、吞吐或压力基准 |
| Testing Authenticity | High | 5 个变更测试文件、441 XML、源码/产物时间链、探针真实性 | 未独立重跑真实 MySQL/Redis |
| Release | High | commit、工作树、构建、报告、治理状态与未合并边界 | 未 merge/push/deploy |
| Configuration | High | MySQL collation、Redis key/TTL、单实例/Cluster 说明 | 未读取仓库外生产配置 |
| Data Integrity | High | DB typeCode 等价关系、Redis/Caffeine 键、版本戳和逐出 | 未写入真实数据 |
| Concurrency | High | 本地 publish 临界区、重叠写事务、callback 顺序、测试屏障 | 未执行攻击性并发、锁或压力 |
| Maintainability / Design | High | Spring Cache、MP 3.5.16 参数处理字节码、治理材料 | 未做全仓缓存架构重审 |

## 3. Incremental Change Summary

### Change Summary

- Baseline: `f9aec54ddfce7202bfcbeb576e6d8c989f21a6e7`
- Candidate / review HEAD: `25f8b1c0683dae38f930a8cb94baf937a446013d`
- Candidate commits: 1
- Changed paths: 20
- Lines: +2175/-228
- Production Java files changed: 6
- Test Java files changed/added: 5
- Governance/evidence files changed/added: 9
- Flyway / dependency / API / frontend files changed: 0
- Author identity: `[REDACTED_USER]`

### Change Categories

| Category | Files | Summary |
|----------|-------|---------|
| Caffeine protocol | 2 | publishLock、pending 写窗口与独立失效步骤 |
| Redis/dictionary protocol | 3 | 原子脚本、版本 envelope、键前缀与 DTO/service 校验 |
| Notification evidence/comment | 1 | `@Param` 说明调整候选 |
| Tests | 5 | 29 个本地缓存测试、真实缓存 IT 10、通知 IT 5 |
| Governance / evidence | 9 | 上一轮报告归档、第二轮材料、门禁摘录与活动状态 |

### Previous Finding Closure Matrix

| Previous finding | Candidate evidence | Independent result |
|------------------|--------------------|--------------------|
| M1 Caffeine 先公开旧值再补删 | publishLock 覆盖校验、写入与失效；第三读者测试 | **Closed for one exact Java key** |
| M2 Redis SET/DEL 非原子、hit 不验 version、失败耦合 | 单 Lua、envelope 版本戳、步骤隔离 | **Original concrete gaps closed; new pending ownership Medium remains** |
| M3 payload/version key 确定性碰撞 | 不相交前缀 + 后端字符集约束 | **Original collision closed; new DB/case identity Medium remains** |
| M4 IT 污染池化 MySQL session | 保存、恢复、复读 timeout/autocommit | **Closed** |
| L1 精确键写事务内未提交 read-through | pending 期间不供应、不回填，rollback 后逐出 | **Closed for one exact key** |
| L2 `@Param` 框架说明错误 | 材料 R2-6 称已按字节码修正 | **Not closed: production comment is still wrong** |
| L3 分支/范围/测试数量漂移 | 范围改为 10 production + 1 build + 6 new tests；活动分支正确 | **Closed; current status由本报告同步** |

### Risk Delta

- Previous Medium findings fully closed as originally described: 4/4
- Previous Low findings fully closed as originally described: 2/3
- Previous Low findings still open: 1
- New blocking findings: 2 Medium
- New non-blocking findings: 2 Low
- Confirmed permission/API/DDL regressions: 0
- Confirmed PG-M3 production regressions: 0
- Confirmed cache identity/concurrency gaps: 2

### Test Coverage Delta

- Independent offline tests: `platform-system` 46/46
- Independent backend package/testCompile: 9/9 modules
- Candidate XML cross-check: 241/241 Surefire + 200/200 Failsafe = 441/441
- Candidate Phase 44 cache IT: 10/10
- Candidate Phase 44 notification IT: 5/5
- Candidate existing cache/dict IT: 3/3 + 2/2
- Valuable new negatives:
  - stale envelope with outdated version stamp is rejected;
  - single write window does not serve/refill exact key;
  - rollback clears exact-key uncommitted read-through;
  - namespace prefixes cannot collide;
  - invalid typeCode characters are rejected on writes;
  - MySQL session variable is restored and reread.
- Missing negatives:
  - two overlapping transactions writing different items of the same typeCode;
  - pending lease surviving the maximum permitted transaction lifetime;
  - database-equivalent case aliases sharing one cache identity;
  - deterministic `registerSynchronization` failure;
  - lock contender “started” proof before timeout-based non-completion assertion.

### Approval Recommendation

**Request changes / keep Phase 44 returned.**

下一候选不需要重写 PG-M3，也不需要撤销已经成立的单 Lua、envelope 版本戳或
session 恢复。最小重交范围：

1. 让 Redis pending 具备同 typeCode 多事务所有权/计数，或用数据库父行锁串行化同类型写；
2. 给 pending 租约设置可证明的事务寿命上界，或提供 owner 续租/安全回收；
3. 统一数据库 typeCode 身份与所有 Redis/Caffeine 读、写、pending、逐出键；
4. 修正 `@Param` 注释，并补两个测试真实性缺口；
5. 由用户在获授权的隔离环境补两条真实 MySQL/Redis 反例，再做下一轮独立增量复核。

## 4. Top Risks

1. **Medium — Redis pending 没有 owner/refcount：** 重叠写事务会被先完成者提前解除，
   60 秒租约也没有事务 timeout 兜底。
2. **Medium — 数据库等价键与缓存键不等价：** `_ai_ci` 视大小写变体为同一字典，
   Redis/Caffeine 却形成多套缓存，逐出可漏掉旧别名。
3. **Low — 框架说明仍错误：** 生产注释与 MP 3.5.16 字节码及提交材料相互矛盾。
4. **Low — 测试声明大于实际覆盖：** registration failure 分支没有被执行。
5. **Low — 锁测试缺竞争线程 started 证据：** 300ms 未完成不一定等于已经在锁上等待。
6. **Carry-forward — 多实例 Caffeine 无广播：** 当前生产 Compose 为单 backend，按
   已登记 long-term fix 继续跟踪，不升级为本轮新增 finding。

## 5. Detailed Findings

### Finding: Redis 写窗口没有并发事务所有权且租约寿命不可证明

- Severity: Medium
- Confidence: High
- Category: Concurrency / Data Integrity / Stability
- Status: Confirmed
- Affected area: `DictServiceImpl.listItems` Redis payload/version 与同 typeCode 的全部字典写事务
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:74-75`
  - Function/module: pending constants
  - Relevant behavior: pending 只由 `P:` 前缀表示，固定 TTL 60 秒。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:275-287`
  - Function/module: `evictItemsCache`
  - Relevant behavior: 每个事务完成回调都无条件写入新的普通随机版本并删除 payload。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:299-307`
  - Function/module: `beginWriteWindow`
  - Relevant behavior: 每次 begin 直接覆盖为一个新的 `P:<uuid>`，没有 owner、集合或计数。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:94-101,409-431`
  - Function/module: `READ_ITEMS_WITH_VERSION` / `PUT_IF_VERSION_UNCHANGED`
  - Relevant behavior: pending 缺失或被恢复为普通版本后，读路径即可建立/捕获普通版本并 CAS 发布。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase44CacheCommitWindowIT.java:249-278`
  - Function/module: `redisWriteWindowNeitherServesStaleValueNorPublishesUncommitted`
  - Relevant behavior: 只覆盖一个写事务，没有两个同 typeCode 活跃事务的完成顺序。
  - Evidence file:
    `docs/reviews/evidence/phase44-second-remediation-rereview-2026-07-26/offline-gates-and-evidence-integrity.txt`
- Problem: Redis 写窗口是一枚可覆盖的单值，而本地 `EpochGuardedCache` 已使用计数。
  当两个写事务同时影响同一 typeCode 时，先完成者无法知道还有另一个活跃 owner，却会
  把版本恢复为普通值。固定 60 秒 TTL 同样无法证明覆盖无显式 timeout 的整个事务寿命。
- Why it matters: 候选的核心不变量是“从写登记到事务完成回调，旧值不供应、新值不回填”。
  单值 pending 不能表达两个重叠窗口，因此原 PG-M4 的 commit→callback 缝隙会重新出现。
- Realistic failure scenario:
  1. T1、T2 分别更新同一 typeCode 下的不同字典项，数据库行不同，事务可重叠；
  2. T1 写 `P1`，T2 覆盖为 `P2`；
  3. T1 先提交，T1 callback 无条件写普通版本 `N1`，T2 仍活跃；
  4. 正常请求在 `N1` 下把仅含 T1 已提交状态的 payload 缓存；
  5. T2 数据库提交到 T2 callback 之间，请求可以命中旧 payload；若进程恰在物理提交后、
     callback 前退出，旧 payload 可保留至 12 小时 TTL。
  同根变体是单事务超过 60 秒：pending 过期后 READ Lua 建立普通版本，窗口在事务仍活跃时重开。
- Minimal fix: 二选一并保持事务级可证明：
  1. 每个字典写在修改任何 item 前锁定对应 `sys_dict_type` 父行，使同 typeCode 写事务严格串行；
  2. Redis 使用带事务 token 的 SET/ZSET/HASH：begin 原子加入自己的 token 并删 payload，
     completion 只移除自己的 token，仅当 active owner 为空时推进普通版本。租约要大于强制
     transaction timeout，或支持 owner 续租和崩溃安全回收。
- Better long-term fix: 把 revision、active writer ownership 与 payload 放进一个同槽的
  reference-data record，并将 callback 失败接入可重试/告警队列。
- Regression test suggestion: 两个真实事务更新同 typeCode 的不同 item；用屏障让 T1
  callback 完成而 T2 仍活跃，断言 version 仍为 pending、payload 不可命中/回填；再提交 T2，
  断言只有最后 owner 离开时恢复普通版本且首次读取为完整新状态。另加事务持续超过最小租约的反例。
- Estimated effort: 0.5–1.5 days

### Finding: MySQL 大小写等价的 typeCode 会形成多套大小写敏感缓存键

- Severity: Medium
- Confidence: High
- Category: Configuration / Data Integrity / Concurrency
- Status: Confirmed
- Affected area: 字典 Redis payload/version、`dictLabels`、`orgDictItems` 与全部字典写失效
- Evidence:
  - File: `docker-compose.yml:15-16`
  - Function/module: production MySQL command
  - Relevant behavior: `utf8mb4` + `utf8mb4_0900_ai_ci`，字符串比较大小写不敏感。
  - File: `platform-boot/src/main/resources/db/migration/V2__dict.sql:7-43`
  - Function/module: `sys_dict_type` / `sys_dict_item`
  - Relevant behavior: 两表 `type_code` 未声明 binary/case-sensitive collation，继承数据库/表默认。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:87,549-558`
  - Function/module: `TYPE_CODE_PATTERN` / `normalizeTypeCode`
  - Relevant behavior: 新硬约束允许 `A-Z` 与 `a-z`，只 trim，不做 canonicalization。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:195-229`
  - Function/module: `listItems` / `createItem`
  - Relevant behavior: SQL 会按 `_ai_ci` 命中等价类型，实体与缓存逐出却使用请求原始大小写。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DictServiceImpl.java:262-287,331-346`
  - Function/module: `evictItemsCache` / two `@Cacheable` methods
  - Relevant behavior: pending/evict 和 Caffeine key 都是 trim 后的精确 Java String。
  - File: `platform-system/src/test/java/cn/edu/gpnu/platform/system/service/impl/DictCacheKeyTest.java:34-46`
  - Function/module: `payloadKeyNeverCollidesWithVersionKeyOfAnyOtherTypeCode`
  - Relevant behavior: 测试把“不相等的 Java String”一律视为不同类型，没有使用数据库等价关系。
- Problem: 数据库把 `material_category` 与 `MATERIAL_CATEGORY` 当作同一 typeCode，
  Redis/Caffeine/pending map 则把它们当成不同键。后端现在明确允许两种输入，服务没有在
  DB identity 与 cache identity 之间建立 canonical mapping。
- Why it matters: 写事务只会为请求/实体当时大小写的 key 开窗口并逐出；同一数据库字典的
  其它大小写别名从未进入 pending，也不会被清理。旧值或事务内读穿值可以绕过本轮全部精确键守卫。
- Realistic failure scenario:
  1. 正常业务预热 `dictLabels("material_category")` 与
     `dict:items:material_category`；
  2. 管理 API 以合法输入 `MATERIAL_CATEGORY` 创建新字典项；
  3. `requireType` 在 `_ai_ci` 库中命中已有小写类型，新 item 以大写值落库；
  4. `evictItemsCache("MATERIAL_CATEGORY")` 只清大写键；
  5. 正常小写键继续返回缺少新项的旧数据，最长到 Caffeine/Redis 12 小时 TTL。
  纯读也可先制造大小写别名缓存，再由另一大小写写路径漏逐出。
- Minimal fix: 先确定一个不分叉的 identity 方案：
  1. 以 `sys_dict_type` 中实际持久化的 typeCode 作为 canonical key，所有读/写先解析并
     使用该精确值，大小写别名请求失败关闭或映射回唯一持久值；或
  2. 通过新 Flyway 把相关列改为 case-sensitive collation，并让 service 精确验证 item
     引用的 type 存在；或
  3. 冻结为单一大小写，迁移/校验存量后让读、写、pending、Redis/Caffeine key 全部统一。
- Better long-term fix: 新增稳定的 `dict_type_id` 作为 item 外键和缓存 identity，
  typeCode 只作为可校验的业务编码展示，避免数据库 collation 决定缓存身份。
- Regression test suggestion: 先预热小写 key，再用大写变体 create/update；测试应证明
  请求被拒绝，或所有入口映射到同一 canonical key 且提交/回滚后没有任何别名旧缓存。
- Estimated effort: 0.5–1 day（若含 collation/存量迁移则 1–2 days）

### Finding: `NotificationMapper` 的 `@Param` 说明仍与 MP 3.5.16 字节码相反

- Severity: Low
- Confidence: High
- Category: Maintainability / Documentation Accuracy
- Status: Confirmed
- Affected area: `NotificationMapper.insertBatch` 维护契约与整改提交材料
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/mapper/NotificationMapper.java:19-27`
  - Function/module: `insertBatch` JavaDoc
  - Relevant behavior: 仍声称 `@Param("rows")` 会使逐元素填充失效、主键与审计字段为 null。
  - File: `docs/reviews/phase-44-remediation-submission-2026-07-26.md:259-267`
  - Function/module: R2-6
  - Relevant behavior: 同一候选材料正确说明 MP 3.5.16 的 `processParameter` 调用
    `extractParameters(Map)` 遍历 ParamMap values，并宣称注释已按事实重写。
  - Locked bytecode:
    `com.baomidou.mybatisplus.core.MybatisParameterHandler.processParameter`
  - Relevant behavior: `processParameter -> extractParameters(Object)`；Map 分支遍历 values、
    用 HashSet 去重并对 collection 展开，不依赖 `collection/coll/list/array` 固定键。
- Problem: 上一轮 Low 的事实错误仍在生产源码；提交材料内部还同时给出相反结论。
- Why it matters: 后续维护者可能基于错误模型拒绝合法 mapper 参数命名，或误把真正需要保持的
  `<foreach collection>` 绑定名合同与 MP 自动填充合同混为一谈。
- Realistic failure scenario: 为统一 mapper 风格而增加 `@Param("rows")` 时，评审者依据
  当前注释误判自动填充一定失败；真正需要同步修改的是 `<foreach collection='rows'>`，
  自动填充本身仍会遍历 rows 集合。
- Minimal fix: 删除“`@Param("rows")` 会破坏自动填充”的断言；准确区分
  MyBatis `<foreach collection>` 绑定名与 MP `extractParameters` 自动填充行为。
- Better long-term fix: 用一条参数绑定单测覆盖“无注解 List”与“`@Param("rows")` +
  matching foreach”两种合法形态，注释只记录本仓选择，不虚构框架限制。
- Regression test suggestion: 对锁定 MP 版本构造 `@Param("rows")` 的测试 mapper，断言
  生成主键与审计字段仍填充；若本仓仍选择无注解参数，只需把该测试作为框架合同证据。
- Estimated effort: 10–20 minutes

### Finding: registration failure 测试没有进入它声称覆盖的注册失败分支

- Severity: Low
- Confidence: High
- Category: Testing Authenticity / Stability
- Status: Confirmed
- Affected area: `ReferenceCacheInvalidator` 注册异常后的窗口解除保证
- Evidence:
  - File: `platform-system/src/test/java/cn/edu/gpnu/platform/system/cache/ReferenceCacheInvalidatorTest.java:129-141`
  - Function/module: `registrationFailureReleasesTheWindowImmediately`
  - Relevant behavior: 测试没有初始化 transaction synchronization，随后调用 invalidator。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/cache/ReferenceCacheInvalidator.java:63-70`
  - Function/module: `invalidateAfterCompletion`
  - Relevant behavior: synchronization 未激活时直接执行 steps/finally/end 并 return。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/cache/ReferenceCacheInvalidator.java:72-87`
  - Function/module: registration catch
  - Relevant behavior: 测试宣称覆盖的 `registerSynchronization` 与 catch 根本不可达。
- Problem: 测试名和注释声称“注册失败后解除窗口”，实际只证明“无事务时立即失效并解除”。
- Why it matters: 测试绿灯不能支撑候选对 registration exception 路径的声明；未来重构
  catch/finally 时该测试不会变红。
- Realistic failure scenario: 注册调用因框架状态异常抛出时，若 end 被误删，窗口会永久旁路；
  当前测试仍会走无事务分支并通过。
- Minimal fix: 把现有测试重命名为 `withoutSynchronizationRunsImmediatelyAndReleasesWindow`；
  再通过可注入 registrar/test seam 或可控静态替身让 registration 确定抛出，并断言 end
  恰执行一次、异常语义符合设计。
- Better long-term fix: 把“begin → register → completion → end”封装成可替换的小协议对象，
  分别测试无事务、提交、回滚、注册失败和 callback 步骤失败。
- Regression test suggestion: 让 registrar 在 begin 已成功后抛异常，断言 pending 已解除，
  steps 未冒充执行，并验证异常是否应传播。
- Estimated effort: 30–60 minutes

### Finding: 两个锁串行测试没有证明竞争线程已经开始尝试目标操作

- Severity: Low
- Confidence: High
- Category: Testing Authenticity / Concurrency
- Status: Confirmed
- Affected area: `EpochGuardedCache` 校验+发布与 evict/begin 的线性化反例
- Evidence:
  - File: `platform-system/src/test/java/cn/edu/gpnu/platform/system/cache/EpochGuardedCacheTest.java:136-160`
  - Function/module: `invalidationCannotInterleaveBetweenTheCheckAndThePublish`
  - Relevant behavior: submit evict 后直接以 `future.get(300ms)` timeout 作为“被锁挡住”证明。
  - File: `platform-system/src/test/java/cn/edu/gpnu/platform/system/cache/EpochGuardedCacheTest.java:322-343`
  - Function/module: `windowCannotOpenBetweenTheCheckAndThePublish`
  - Relevant behavior: begin pending 用例使用相同模式。
  - File: `platform-system/src/test/java/cn/edu/gpnu/platform/system/cache/EpochGuardedCacheTest.java:361-370`
  - Function/module: `awaitDone`
  - Relevant behavior: helper 只区分 300ms 内完成/未完成，没有 started/attempted 探针。
- Problem: “任务未完成”可能来自线程尚未被调度，而不是已经尝试 evict/begin 并阻塞在
  `publishLock`。在极端负载或慢调度环境中，去掉锁的缺陷版本也可能假绿。
- Why it matters: 这两条测试是上一轮最关键 Medium 的确定性交错证据；它们应证明
  happens-before，而不是依赖调度概率。
- Realistic failure scenario: CI 执行器 300ms 内未调度 invalidator 线程；即使生产方法无锁，
  `awaitDone` 仍返回 false，测试随后放行 publisher，竞争任务再执行并通过最终状态断言。
- Minimal fix: 给竞争任务增加 started/attempted latch，主线程先确认任务已开始，再断言
  release 之前 completion latch 不可到达；适当保留最终状态断言。
- Better long-term fix: 使用可控 lock/probe seam 或 jcstress 风格状态机测试，将“已到达竞争点”
  与“尚未完成”分别观测，避免 wall-clock 作为唯一并发证据。
- Regression test suggestion: 临时移除共享锁时，这两条测试必须在 started 已确认后确定失败，
  而不是依赖线程调度速度。
- Estimated effort: 30–60 minutes

## 6. Security Concerns

- 本轮没有新增权限点、认证、外部 URL、凭据、文件访问或数据范围逻辑。
- typeCode 大小写 finding 可由正常已授权字典管理输入触发，归类为数据完整性/配置身份，
  不需要恶意载荷。
- 读路径仍接受任意非空 typeCode，理论上可制造额外 Redis version key；这是候选已披露的
  既有资源边界，本轮未主动验证，也不升级为新增 security finding。
- 没有执行漏洞扫描、端口/服务探测、登录、请求、凭据尝试或其它可能属于 cyber 的动作。

## 7. Stability Concerns

- Redis 单 Lua、payload 版本戳与步骤隔离显著改善了单写事务下的稳定性。
- pending owner/refcount 与 TTL/事务上界不匹配仍会在并发写或长事务时重开旧值窗口。
- Redis callback 失败不能回滚已提交数据库；现有 ERROR 日志可见但没有持久重试，继续作为
  stable-release 前的既有运维边界。
- `Phase44NotificationBatchIT` 正常 1205 路径已恢复 session 状态。若 rollback/restore 本身
  异常，测试会红但没有显式淘汰物理连接；不构成本轮虚绿，建议以后统一测试资源 guard。

## 8. Performance Concerns

- PG-M3 的 500 行 multi-values 分块保持成立，没有退回 N 条逐行 INSERT。
- 字典写是低频管理操作；pending 期间回源数据库的成本可接受，但未做量化。
- 大小写别名会增加重复 Redis/Caffeine entry 与无效回源，修正 canonical identity 后可自然消除。
- 未执行延迟、吞吐、扇出、缓存击穿或压力基准；大扇出通知与多节点 reference cache 继续留
  最终全量审计/稳定发布前验证。

## 9. Testing Authenticity Analysis

### Confidence Assessment

- Static production reasoning: High
- Independent offline test/build: High
- Candidate XML/source time-chain: High on this machine
- Fresh-clone dynamic evidence portability: Medium
- Real MySQL/Redis two-writer and case-alias behavior: Not independently executed

### Valuable Tests

- `EpochGuardedCacheTest` 的 recording delegate 能证明 STALE 未实际写入。
- 单写窗口 commit/rollback IT 覆盖精确键不供应、不回填与恢复可用。
- stale payload envelope 的版本戳反例直接覆盖“删除失败但旧负载残留”。
- notification IT 的连接 identity、外层 rollback、500/500/1 分块和 session 恢复均有价值。
- `DictCacheKeyTest` 对 payload/version 固定前缀不相交给出机械证明。

### Suspicious Tests

- `registrationFailureReleasesTheWindowImmediately` 没有触发 registration。
- 两个 300ms 非完成断言缺 contender started 证据。
- `pendingWindowsAreReentrantAndCountedPerKey` 只证明本地 Caffeine 计数，没有对应 Redis
  owner/refcount，因此不能外推为整个字典缓存协议可重入。
- `DictCacheKeyTest` 以 Java String 不等价替代数据库 collation identity，漏掉 case alias。

### Missing Tests

1. 同 typeCode 两个并发写事务，先完成者不得解除后完成者 pending。
2. pending lease 与 transaction timeout/续租的边界。
3. typeCode 大小写别名的 commit/rollback 与 Redis/Caffeine 双层逐出。
4. registration 真实失败分支。
5. contender started + completion 双探针的锁反例。

## 10. Release Concerns

- `25f8b1c` 仍是未合并候选；本轮没有 stage、commit、merge、push 或部署。
- 2 个 Medium 使 Phase 44 不能置 PASS，Phase 0 继续不放行。
- 开发者 441/441 是可信候选证据，但原始日志/XML 未跟踪、未内嵌 candidate SHA；下一候选
  最好归档最小原始 XML/摘要与提交哈希。
- 即使下一轮 Phase 44 PASS，也只关闭阶段闸门；Phase 0 与最终全量审计仍未完成，不能宣称
  stable-release ready。

## 11. Configuration Safety Analysis

### Configuration Summary

| Item | Current value / behavior |
|------|--------------------------|
| MySQL charset/collation | `utf8mb4` / `utf8mb4_0900_ai_ci` |
| Caffeine dict TTL | 12h |
| Redis payload TTL | 12h |
| Redis normal version TTL | 24h |
| Redis pending TTL | 60s |
| Transaction timeout | 本切片未见强制上界 |
| Redis deployment | 当前 Compose 单实例；Cluster 未验证 |
| Redis key prefixes | `dict:items:` / `dict:items-version:` |
| Backend write typeCode | `[A-Za-z0-9_]{1,64}`，不做大小写 canonicalization |

关键配置问题不是单个参数值，而是 `_ai_ci` 的数据库 identity 与大小写敏感 cache identity
不一致，以及 pending TTL 没有与 transaction timeout 建立可证明关系。

## 12. Data Integrity Analysis

### Integrity Summary

- Redis payload 自带 version stamp，旧格式/旧版本残留不再作为有效命中，成立。
- version 推进 + payload 删除为单 Lua，单实例 Redis 内原子，成立。
- 本地两个 cache 的精确键逐出不再被 Redis 异常跳过，成立。
- 同 typeCode 重叠 writer 的 pending ownership 不成立。
- typeCode 的数据库等价类与缓存等价类不一致。
- PG-M3 插入字段、主键/审计填充与外层回滚生产行为没有发现回归。

## 13. Concurrency Analysis

- 本地 `publishLock` 把“校验 epoch/pending + delegate publish”与 evict/begin/end 串行，
  关闭上一轮第三读者窗口。
- 本地 pending 使用计数，可表达同键多个窗口；Redis pending 使用单值，两个层次的协议语义
  不一致。
- `afterCompletion` 运行在物理事务完成后；pending 必须持续到最后一个同 identity writer
  的 callback 完成，不能由任意先完成 writer 恢复普通版本。
- 60 秒自愈租约只在“进程崩溃后避免永久旁路”方向有益；若没有 transaction timeout 或续租，
  它也会在活事务中提前解除保护。
- 大小写别名意味着即使每个精确 Java key 都线性化，数据库同一业务 identity 仍可从另一 key
  绕过临界区。

## 14. Principles Compliance

### Principles Violated

- **Canonical identity must be shared across storage and cache:** DB 与 cache 对 typeCode 的等价关系不同。
- **A lease needs an owner and a bounded lifetime:** pending 没有 owner/refcount，也无事务寿命证明。
- **Tests must prove the branch/interleaving they name:** registration 与两个 lock 测试证据过强表述。
- **Comments are part of the contract:** `@Param` 注释与锁定字节码及提交材料冲突。

### Principles Respected

- **Never publish before validation:** 精确键 Caffeine 已做到校验失败不写。
- **Versioned payloads fail closed:** envelope stamp 不符时不供应旧负载。
- **Failure isolation:** Redis 失败不再跳过两个本地逐出，callback 不反噬已提交业务事务。
- **Backend validation:** typeCode 字符集已从前端体验规则提升为 service 硬约束。
- **Honest gates:** 候选材料明确不是独立 PASS，活动状态没有提前放行 Phase 0。

## 15. Recommended Fix Order

### Fix Immediately

1. 统一 typeCode canonical identity，并让读、写、pending、Redis/Caffeine key、逐出全部使用它。
2. 为 Redis pending 增加 owner/refcount，或先用 `sys_dict_type` 父行锁串行化同类型写。
3. 给 transaction timeout 与 pending lease 建立明确不变量，补续租/安全回收或强制上界。
4. 修正 `NotificationMapper` 的 `@Param` 说明。
5. 修正 registration 与两个锁测试的真实性。

### Fix Before Stable Release

1. 由用户在专用隔离环境执行两个同类型写事务的真实 MySQL/Redis 交错。
2. 由用户执行大小写别名的 commit/rollback 双层缓存反例。
3. 给 Redis 失效 callback 失败增加可操作告警和可重试策略。
4. 归档与候选 SHA 绑定的最小动态 XML/完整摘要，避免只依赖本机 ignored target。
5. 若计划多实例部署，实现 reference-data revision/broadcast，不依赖 12 小时本地 TTL。

### Schedule Later

1. `EpochGuardedCache` 的 ThreadLocal epoch 改为显式 load token/每 key 栈，防未来同线程同键嵌套 loader。
2. 若部署 Redis Cluster，使用 hash tag/单 hash 让 revision、writers、payload 同槽。
3. 为读路径无效 typeCode 增加长度/字符集约束或负缓存配额，控制 keyspace 增长。
4. 建立大扇出通知、写窗口回源与缓存击穿性能基准。

### Ignore for Now

- 不重写已成立的 PG-M3 multi-values/500 分块方案。
- 不撤销 `dict:items-version:` 前缀或 envelope version stamp。
- 不把当前单 backend 的跨节点广播债误报为本轮新回归。
- 不为本轮 findings 启动服务、Docker、数据库、Redis、网络或任何 cyber 类验证。

## 16. Quick Wins

| Quick win | Effort | Value |
|-----------|--------|-------|
| 修正 `@Param` JavaDoc 与提交材料 §1.2 | 10–20 min | 关闭上一轮遗留 Low |
| 重命名无事务测试并补 registration failure seam | 30–60 min | 消除虚假分支覆盖 |
| 给两个锁测试加 contender started/completed 探针 | 30–60 min | 让关键并发证据确定化 |
| `requireType` 返回持久化 canonical typeCode | 30–90 min | 为大小写统一提供最小入口 |
| 同 typeCode 写前锁定字典类型父行 | 0.5 day | 最小化关闭 overlapping writer 窗口 |

## 17. Long-term Refactor Plan

1. **Canonical dictionary identity：** 用稳定 `dict_type_id` 或严格 canonical typeCode 贯穿
   DB、DTO、service、Redis、Caffeine、审计与测试。
2. **Reference-data revision record：** 每个 identity 用一个原子 record 保存 revision、
   active writer tokens、payload 与 TTL；读写 Lua/事务协议只操作该 record。
3. **Cross-node coherence：** 提交后发布 revision event；节点本地 cache entry 携带 revision，
   命中时验证或由广播精确失效。
4. **Transactional cache test kit：** 提供可复用的 begin/commit/rollback/crash、双 writer、
   alias identity、registration failure 与 callback failure 确定性交错框架。
5. **Portable evidence：** 每个候选归档命令、退出码、测试计数、关键 XML、源码 blob/hash 和
   candidate SHA；候选证据与独立复核证据明确分层。

---

**Final verdict: CHANGES REQUESTED（0 Critical / 0 High / 2 Medium / 3 Low）。**

第二轮已实质关闭上一轮 4 个 Medium 的具体实现缺口和 2 个 Low，但 Redis pending
所有权/租约与 typeCode canonical identity 又暴露出两个可复现的缓存一致性 Medium；
`@Param` Low 也仍未真正修正。完成上述最小整改并补两条真实交错后，可只做第三轮增量
复核。Phase 44 当前不得 PASS，Phase 0 与最终全量审计仍不放行。
