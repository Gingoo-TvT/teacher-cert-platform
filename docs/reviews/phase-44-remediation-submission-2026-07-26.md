# Phase 44 退回整改提交材料（2026-07-26）

> 身份声明：本文件由**整改者**撰写，只构成**候选证据**，不是独立复核结论。Phase 44 与全项目的正式状态以后续独立
> 增量复核报告为准（`docs/REVIEW-GATE.md`）。

- 依据：`docs/reviews/phase-44-review.md`（CHANGES REQUESTED，2 Major）与阶段总审计 `docs/reviews/phase-gap-audit-2026-07-23.md`
  的 **PG-M3**、**PG-M4**；第二轮另依据 `docs/reviews/phase-44-remediation-rereview-2026-07-26.md`
  （CHANGES_REQUESTED，0 Critical / 0 High / 4 Medium / 3 Low）
- 分支：`codex/phase44-remediation`（自 `codex/phase53-demo-reconcile` 的 `f9ccda3` 起）
- 范围（相对分支基线 `f9ccda3`，与 `git diff --stat` 逐项对齐）：
  - 生产代码 **10 个文件**：`platform-system` 新增 `system/cache` 包 3 个类（`EpochGuardedCache`、
    `EpochGuardedCacheManager`、`ReferenceCacheInvalidator`），修改 `CacheConfig`、`NotificationMapper`、
    `DictServiceImpl`、`InAppNotifyChannel`、`SystemManagementServiceImpl`、`DictTypeSaveRequest`、`DictItemSaveRequest`
  - 构建：`platform-system/pom.xml` 增加 `spring-boot-starter-test`（test 作用域）
  - 测试 **6 个新增测试类**：`platform-system` 离线单测 4 个（`EpochGuardedCacheTest`、
    `ReferenceCacheInvalidatorTest`、`InAppNotifyChannelBatchTest`、`DictCacheKeyTest`）+ `platform-boot`
    真实依赖 IT 2 个（`Phase44NotificationBatchIT`、`Phase44CacheCommitWindowIT`）
  - 无 Flyway 迁移、无 API 路径/返回结构变更、无权限点变更、无前端改动
- 用户在本轮开工前书面确认两项路线：Major-1 走「真正实现同连接批量写」，Major-2 做到「提交后逐出 + 纪元防重填」

---

## 1. Major-1（PG-M3）通知批量交付

### 1.1 问题原样

`InAppNotifyChannel.sendBatch` 在 44f 被回退为逐用户 `insert`（回退本身正确：44a 的 `Db.saveBatch` 以 BATCH 执行器
另开 SqlSession/连接、不参与外层事务，与外层事务持有的行锁互等，间歇 `Lock wait timeout exceeded` ~50s），但
`docs/launch-readiness-plan.md` 仍把 P1-5 记为「已修复：真批量」。复核给出两条路：改为诚实推迟，或实现使用同一事务
连接的真正 multi-values insert 并回归锁等待。

### 1.2 实现

| 文件 | 改动 |
|---|---|
| `system/mapper/NotificationMapper.java` | 新增 `insertBatch(List<Notification>)`：`@Insert` + `<foreach>` 的单条 multi-values INSERT，12 列（`deleted` 交由 DDL `DEFAULT 0`），可空列显式 `jdbcType` |
| `system/service/impl/InAppNotifyChannel.java` | `sendBatch` 改用 `insertBatch`，按 `BATCH_SIZE = 500` 分块；`send`/`sendBatch` 共用 `newNotification(...)` 构造字段 |

三个关键设计点（均写在代码注释里，便于复核对照）：

1. **同连接**：普通 mapper 调用经 `SqlSessionTemplate` 复用**事务绑定的** SqlSession/连接，因此批量写与外层事务同生共死，
   不可能与本事务自身持有的锁互等。这正是与 44a 的本质差别。
2. **参数刻意不加 `@Param`**：MyBatis 把单个 `List` 包成含 `list`/`collection` 键的 ParamMap，MyBatis-Plus 的
   `MybatisParameterHandler.getParameters` 按这些键取集合并对**每个元素**执行 `populateKeys`（`ASSIGN_ID` 主键）与
   `MetaObjectHandler.insertFill`（审计字段）——与逐行 `insert` 完全同一条填充链。加 `@Param` 会让填充失效、主键为 null。
   该行为已由本轮字节码核对（`mybatis-plus-core-3.5.16` 的 `MybatisParameterHandler` 常量池含 `collection`/`coll`/`list`/`array`）
   与真实数据库断言双向确认。
3. **不用 MP 内置批量 API**：3.5.16 的 `BaseMapper.insert(Collection)` 与 `Db.saveBatch` 一样经
   `MybatisBatchUtils.execute(SqlSessionFactory, ...)` 从工厂**新开** SqlSession（BATCH 执行器），正是 44a 的事故成因；
   且 JDBC batch 仍是 N 条语句，不是 multi-values。已核对字节码确认，不是推测。

### 1.3 证据（真实 MySQL 8.0，`Phase44NotificationBatchIT` 5/5）

| 用例 | 断言口径 |
|---|---|
| `sendBatchIssuesOneMultiValuesInsertOnTheTransactionConnection` | 3 收件人只在 `StatementHandler.prepare` 边界出现 **1** 条 `insert into notification`，占位符 **36 = 12 列 × 3 行**；该语句拿到的 `Connection` 与 `DataSourceUtils.getConnection(dataSource)` **isSameAs** |
| `sendBatchChunksLargeFanOutAndStaysInOneTransaction` | 1001 收件人 → **3** 条语句（500/500/1），三条全部落在同一连接对象上 |
| `sendBatchRollsBackWithTheOuterTransaction` | 外层事务内可见 4 行；`setRollbackOnly` 后**一行不留**（44a 的另开连接会独立提交、留孤儿通知） |
| `batchInsertDoesNotWaitWhileOuterTransactionHoldsUserRowLocks` | 外层事务 `SELECT ... WHERE user_id = ? FOR UPDATE` 持锁期间：**反例**——另开连接插同一 `user_id`（`innodb_lock_wait_timeout=1`）必得 **1205 ER_LOCK_WAIT_TIMEOUT`**；**正例**——本实现同连接批量写 < 5s 完成且行数 = 种子 1 + 批量 1 |
| `batchRowsCarryTheSameGeneratedKeysAndAuditFieldsAsPerRowInsert` | 批量行 `id` 非空、`created_at/updated_at` 非空、`created_by/updated_by` 与逐行 `send()` 相同、`read_flag=0`、`deleted=0`（DDL 默认生效） |

其中「1205 反例」是本项的关键：它复刻了 44a 的事故机制，同时证明「同连接不等待」这条断言不是空转。

离线单测 `InAppNotifyChannelBatchTest` 4/4 另行覆盖：批量路径**不再**调用逐行 `insert`、⌈N/500⌉ 分块、批量行与
`send()` 逐字段等价、空/`null` 收件人零语句。

---

## 2. Major-2（PG-M4）参考数据缓存逐出

### 2.1 问题原样

`DictServiceImpl` 在写事务**内**手工逐出，`SystemManagementServiceImpl.updateParam` 用 `@CacheEvict` 逐出：都在提交之前，
并发读可在逐出后把提交前旧值重填进缓存，提交后继续供旧值直到 TTL（字典 12h / 参数 30m）。

复核要求「移动到事务提交后，并补并发重填反例」。**只做「提交后逐出」并不足够**：读线程只要在提交前完成数据库读、
在逐出之后执行 put，仍然重填旧值。因此本轮按用户确认的强度同时做两件事——供应端时序 + 回填端守卫。

### 2.2 实现

| 文件 | 改动 |
|---|---|
| `system/cache/ReferenceCacheInvalidator.java`（新增） | 事务感知失效器：有事务则注册 `afterCompletion` 回调，无事务立即执行；失效动作抛异常只记 ERROR（事务已完成，不能反噬业务） |
| `system/cache/EpochGuardedCache.java`（新增） | 纪元守卫装饰器：`get` 未命中记录线程本地装载纪元；失效动作先推进纪元再委托；`put` 前后各比对一次，任一不等即拒绝/撤销写入 |
| `system/cache/EpochGuardedCacheManager.java`（新增） | 按缓存名包装且保证同名单例（纪元计数器是包装实例状态） |
| `system/config/CacheConfig.java` | `cacheManager` bean 对外只暴露包装后的管理器，生产逐出、Spring 缓存切面、测试 helper 全部走同一守卫，无绕过路径 |
| `system/service/impl/DictServiceImpl.java` | `evictItemsCache` 改为 `afterCompletion`：先推进 Redis 内容版本（随机 UUID）再删负载，然后逐出两个 Caffeine 缓存；`listItems` 在**数据库读之前**捕获版本，回填走 Lua CAS，只有版本未变才写入 |
| `system/service/impl/SystemManagementServiceImpl.java` | `updateParam` 去掉 `@CacheEvict`，改 `clearAfterCompletion(SYS_PARAM)` |

三个设计取舍：

1. **`afterCompletion` 而非 `afterCommit`**：回滚也必须清理。冷缓存下写事务自身的读穿会把**未提交**值装进共享缓存，
   `afterCommit` 语义会把该脏值留到 TTL。已由 `paramRollbackClearsValuesCachedInsideTheUncommittedTransaction` 与
   `dictEvictionAlsoRunsOnRollbackSoUncommittedValuesNeverSurvive` 两个用例正面证明。
2. **不再在事务内逐出**：事务内逐出会把「本事务未提交的新值」经读穿发布给全部请求；代价是写请求自身在同一事务里仍读到
   旧缓存值。核查过 `createItem/updateItem/deleteItem/updateType/deleteType/updateParam` 均不在同一事务内再读这些缓存，
   故无实际影响，该取舍已写入代码注释。
3. **Redis 用版本 token CAS**（先 `SET ver <uuid>` 再 `DEL payload`）：顺序反了会留下「删后、置新版本前」的回填缝隙。
   随机 UUID 而非 `INCR`：版本键被内存压力驱逐后重建也不可能与此前捕获值相等。该保护**跨节点成立**。

### 2.3 证据（真实 MySQL + Redis，`Phase44CacheCommitWindowIT` 6/6）

| 用例 | 断言口径 |
|---|---|
| `preCommitDictLabelsLoadIsNotRefilledAfterEviction` | MyBatis `StatementHandler.query` 屏障把并发读停在「数据库读已完成、缓存回填未发生」；期间 `updateItem` 提交并逐出；放行后读线程确实持 `V1`，但 `dictLabels` 缓存条目**为空**（回填被纪元守卫拒绝），提交后首读得 `V2` |
| `preCommitRedisItemsLoadIsNotRefilledAfterEviction` | 同一屏障用于 `listItems`：Redis 负载键**为空**（版本 CAS 拒绝），版本键非空；随后正常读**必须**能回填（证明守卫没有把缓存变成永久禁用） |
| `dictEvictionIsDeferredUntilCommitAndFirstReadAfterCommitIsFresh` | 写事务内断言缓存**仍持有**旧条目（负向断言：改回事务内逐出即红）；提交后条目消失、首读得新值 |
| `dictEvictionAlsoRunsOnRollbackSoUncommittedValuesNeverSurvive` | 事务内读穿装入未提交 `V2`；回滚后条目消失、读回 `V1` |
| `paramCacheEvictionIsDeferredUntilCommitAndFirstReadAfterCommitIsFresh` | 参数缓存同上，键为 `getInt:video.diffThreshold:-424242`；用例结束复原原值 |
| `paramRollbackClearsValuesCachedInsideTheUncommittedTransaction` | 冷缓存事务内读穿看到未提交值；回滚后条目消失、读回原值 |

屏障是否命中有显式断言（未命中直接失败并打印 armed 期间看到的 `sys_dict_item` SQL），避免 SQL 漂移把并发用例变成
空转绿灯——本轮确实借此发现并修正了一次 `ORDER BY` 分隔符匹配漂移。

离线单测 `EpochGuardedCacheTest` 10/10 + `ReferenceCacheInvalidatorTest` 7/7 覆盖：同键/异键逐出、`clear`、
`evictIfPresent`、`invalidate` 期间的回填拒绝；**写入后**才发生逐出的 check-then-act 交错撤销；`valueLoader` 路径；
缓存 null 命中；`retrieve` 刻意不支持；无事务立即失效、事务内推迟、回滚也失效、失效异常不外泄、未知缓存名 no-op、
同名包装单例。

---

## 3. 文档一致性（原 D11 不通过项）

- `docs/launch-readiness-plan.md` P1-5 行与 §11 新增「Phase 44 退回整改」条目：不再声称 `Db.saveBatch` 真批量，改为
  记录 44a→44f→本轮的完整轨迹与当前实现口径。
- §7.3 参考数据缓存条目补记逐出时序与纪元守卫。
- `docs/CURRENT-EXECUTION-PLAN.md`、`PROGRESS.md`、`HANDOFF.md`、`DEVLOG.md` 同步为「Phase 44 整改候选待独立复核」。

---

## 4. 门禁与环境

- 依赖：**本轮专用隔离栈**（容器 `tcp-p44-mysql` / `tcp-p44-redis` / `tcp-p44-minio`，compose 项目 `tcp-phase44`，
  数据卷全新，与既有 `tcp-*` 开发栈容器名区分、互不覆盖）。全量跑前 `down -v` 重建，跑前核对
  `information_schema.tables` 为 **0** 张表。
- 全量：`mvn -B -ntp -o clean verify`（结果见 §5）。
- 前端：`npm --prefix frontend run type-check`、`npm --prefix frontend run build`（本轮无前端改动，仅作回归）。
- `git diff --check` 通过。
- 用完销毁：`docker compose -p tcp-phase44 down -v`，不留容器/网络/数据卷。

### 安全边界

未执行漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz，也未对既有服务做破坏操作。唯一涉及并发/锁的操作是
`Phase44NotificationBatchIT` 在**本轮专用隔离库**上用 1 秒 `innodb_lock_wait_timeout` 做的一次受控行锁等待反例，
只影响测试自建的 `biz_type='phase44batch'` 行，用例结束即清理。未启动常驻服务（`java -jar` / `npm run dev` 一次都没有），
Docker 依赖均以 `-d` 分离方式运行。

---

## 5. 测试计数

本轮专用隔离依赖（跑前 `information_schema` 0 张表）上的一次 `mvn -B -ntp -o clean verify`：

| 门禁 | 第一轮 | 第二轮（最终） |
|---|---|---|
| Flyway（空库→最终版本） | 33 个迁移（32 版本化 + `R__testseed`），V32 | 同左 |
| Surefire（单测） | **216/216**（system 21 + file 18 + boot 177） | **241/241**（system 46 + file 18 + boot 177） |
| Failsafe（IT） | **196/196** | **200/200** |
| 合计 | **412/412** | **441/441**，0 failure/error/skip |
| 9 个 reactor 模块 | 全部 SUCCESS | 全部 SUCCESS |
| `Phase44NotificationBatchIT` | 5/5 | 5/5（新增会话变量还原断言） |
| `Phase44CacheCommitWindowIT` | 6/6 | **10/10**（新增 Redis 写窗口、残留负载、污染 typeCode 拒绝、跨类型互不影响） |
| `platform-system` 离线单测 | 21/21 | **46/46**（19 + 10 + 13 + 4） |
| `Phase44CachingIT`（既有逐出正确性） | 3/3 | 3/3，未改动 |
| `Phase1DictIT`（既有字典写路径） | 2/2 | 2/2（覆盖新增 typeCode 硬校验后的既有写路径） |
| `Phase5MaterialIT` / `Phase12NotificationIT` / `Phase7VideoReviewIT` | 18/18 · 5/5 · 44/44 | 18/18 · 5/5 · 44/44 |
| 前端 `type-check` / `build` | 通过 | 通过（build 仍有既有大 chunk 警告） |
| `git diff --check` | PASS | PASS |

**一次诚实记录的中途失败**：首跑 `Phase7VideoReviewIT.presignedUploadCompleteResumeAndInstantHitWorkWithValidation`
失败（`Expecting empty but was: "https://evil.example"`）。根因是我为本轮临时写的隔离 compose **漏了**
`docker-compose.dev.yml` 里的 `MINIO_API_CORS_ALLOW_ORIGIN`，MinIO 遂对任意 Origin 放行，与该用例「非白名单 Origin 不得
回 CORS 允许头」的断言冲突——是我的依赖环境缺配，不是代码回归。补齐该环境变量、`down -v` 重建全新数据卷后整轮 412/412
通过。上表所有计数取自补齐后的那一次完整运行。

## 6. 未覆盖与已知边界（诚实记录）

1. **进程内缓存的跨节点失效**仍缺失：A 节点的字典/参数写不会逐出 B 节点的 Caffeine 缓存，B 最长陈旧到 TTL。本轮的纪元
   守卫是单 JVM 语义；Redis 侧的版本 CAS 才是跨节点成立的。审计自己把「统一参考数据版本号 + 多节点广播失效协议」列为
   long-term fix，故本轮不实现，改为登记为独立开放项，不冒充闭环。
2. **长事务读视图早于写提交**时（REPEATABLE READ 快照早于本次缓存 `get`），读到旧值却未跨越任何逐出，其回填会被接受。
   该窗口由长事务自身快照语义决定，缓存层无法判别；已写入 `EpochGuardedCache` 类注释。
3. **大扇出性能门禁**只证明语句条数与分块边界，未做延迟/吞吐基准；PG-M3 的「容量测试」层面留最终全量审计。
4. `PARTIAL_ROLLBACK`、`DataScope` 授权缓存等既有存量语义债不在本轮范围，未做改动也未重新定义。
5. 本轮未验证 Redis 集群部署：`dict:items:` 与 `dict:items:ver:` 无 hash tag，集群下 Lua 会跨槽。当前部署为单实例
   Redis，已在代码注释中标明改造点。

---

## 7. 门禁记录（原始输出摘录）

`docs/reviews/evidence/phase44-remediation-2026-07-26/gates-2026-07-26.txt`：隔离栈 compose 全文、跑前空 schema、
Flyway 收尾行、逐测试类计数、reactor 结果、前端与 diff check。已按 Phase 53 的证据卫生口径脱敏——仓库绝对路径与
本机用户名替换为 `<REPO>`/`<HOME>`/`<USER>`，本地一次性栈的口令占位替换（与仓库已跟踪的 `docker-compose.dev.yml` 同值）。

---

# 第二轮整改（针对 `phase-44-remediation-rereview-2026-07-26.md` 的 4 Medium / 3 Low）

第一轮结论：PG-M3 已关闭（同事务、同连接的 multi-values 批量写成立）；PG-M4 未关闭。以下逐条对应复核给出的
「Minimal fix」与「Regression test suggestion」。

## R2-1 关闭 `EpochGuardedCache` 的陈旧值公开窗口（Medium，Concurrency/Data Integrity）

- **复核指出**：`put` 先 `delegate.put` 再二次比对补删，只保证最终状态，不保证陈旧值从未被读到；补删还会误删
  另一线程刚发布的新值。要求「对同一 cache 的 get/publish/invalidate 建立真正线性化的锁或等价原子协议」。
- **改法**：`put` 与全部失效动作（`evict`/`evictIfPresent`/`clear`/`invalidate`/`beginPendingInvalidation`/
  `endPendingInvalidation`）在同一把 `publishLock` 下串行；校验 epoch/写窗口与写入底层之间<b>不可能</b>插入失效，
  **校验不过就直接不写**，补删逻辑整体删除。读路径 `get` 刻意不加锁：底层缓存线程安全，而任何被写入的值都已在锁内
  校验过，读者不可能看到未经校验的值。
- **另加写窗口**：从失效登记（写事务内、提交前）到事务完成，受影响的键进入 pending——`get` 一律按未命中返回、
  `put` 一律拒绝。这同时关闭了「提交完成到逐出执行之间仍供应旧值」的窗口，以及下面 R2-5 的 Low。
  窗口内的 `get` 仍记录装载纪元，避免「窗口结束后迟到的 put」绕过 epoch 校验。
- **反例**：`staleValueNeverReachesTheUnderlyingCacheSoNoReaderCanObserveIt`（RecordingCache 断言 STALE 从未进入底层，
  故不存在「能否被读到」的时间窗）、`rejectedStaleLoaderDoesNotDeleteFreshValuePublishedByAnotherThread`
  （旧 loader 迟到发布不得删除另一线程的 FRESH）、`invalidationCannotInterleaveBetweenTheCheckAndThePublish` 与
  `windowCannotOpenBetweenTheCheckAndThePublish`（发布线程停在 `delegate.put` 内时，另一线程的逐出/开窗必须被锁挡住）。

## R2-2 Redis 版本与负载的原子协议 + 故障隔离（Medium，Data Integrity/Stability）

- **复核指出**：`SET version`、`DEL payload`、两个 Caffeine evict 串在同一个 Runnable，前一步异常会跳过全部后续动作；
  正常执行也存在「version 已更新、旧 payload 尚未删除」的可读窗口；payload 命中不校验 version。
- **改法**三条，与 Minimal fix 一一对应：
  1. **单 Lua 原子**：`BUMP_VERSION_AND_DROP_PAYLOAD` 在一个脚本内完成版本推进 + 负载删除，中间态不可观测；
  2. **步骤隔离**：失效动作拆成 3 个具名步骤（`caffeine:dictLabels`、`caffeine:orgDictItems`、
     `redis:version-and-payload`），由 `ReferenceCacheInvalidator.runIsolated` 逐个执行，任一异常只记 ERROR 且**不影响**
     其余步骤；本地逐出排在 Redis 之前，Redis 故障绝不会跳过本地逐出；写窗口解除在 `finally`；
  3. **负载自带版本戳**：payload 存 `{"v":"<token>","items":[...]}`，读路径一次 EVAL 原子取回负载 + 版本，
     **只有戳与当前版本相同才作为有效命中**。于是即便删除因故未生效，残留负载也永远不会被当作有效数据。
     旧格式（裸数组）没有版本戳，升级后自然判定为无效并重新装载。
- **反例**：`stalePayloadWithOutdatedVersionStampIsNeverServed`（真实 Redis 上把旧负载“复活”，断言读取仍得新值）、
  `failingStepDoesNotBlockTheRemainingSteps`、`windowIsReleasedEvenWhenEveryStepFails`。

## R2-3 Redis 键命名空间碰撞（Medium，Configuration/Data Integrity）

- **复核指出**：`version(foo)` 与 `payload(ver:foo)` 都是 `dict:items:ver:foo`；typeCode 仅 `@NotBlank`，字符集限制只在前端。
- **改法**（两条 Minimal fix 都做）：
  1. 版本前缀改为 `dict:items-version:`，与负载前缀 `dict:items:` 在同一位置分别为 `-` 与 `:`，**任何** typeCode
     都无法让两类键相等；
  2. typeCode 字符集变成**后端硬约束**（红线 R7）：`DictServiceImpl.normalizeTypeCode` 对全部写路径
     （createType/updateType/createItem/updateItem）强制 `^[A-Za-z0-9_]{1,64}$`，DTO 另加 `@Pattern` 给出 400 级提示。
     已核对迁移种子与运行库：`sys_dict_type`/`sys_dict_item` 现存 type_code 全部满足该模式，不影响存量维护。
- **反例**：`DictCacheKeyTest` 13 例（对抗性 typeCode 的键不相交、旧前缀确实会碰撞、前缀在首个分歧字符处不同）、
  `typeCodesThatWouldPolluteTheRedisKeyspaceAreRejectedByTheBackend`（经生产 service 拒绝并断言不落库）、
  `writesToOneDictTypeDoNotDisturbAnotherTypesCache`。

## R2-4 锁等待 IT 归还池化连接前还原会话变量（Medium，Testing）

- **复核指出**：`SET SESSION innodb_lock_wait_timeout = 1` 后直接归还 Hikari 连接，污染后续使用者。
- **改法**：先读 `@@SESSION.innodb_lock_wait_timeout` 与 `autoCommit` 原值，`finally` 中还原并**再读一次断言**还原生效；
  还原失败直接让用例失败，不让污染静默扩散。

## R2-5 事务期间未提交读穿值可见（Low）

由 R2-1 的写窗口一并关闭：窗口自写事务内开启，本事务自身的读穿不再把未提交值发布到共享缓存。
IT `dictWriteWindowNeitherServesStaleValueNorPublishesUncommitted` 断言写事务内的并发读只看到旧的**已提交**值，
本事务自己看到的未提交值不进缓存；`dictWriteWindowClosesOnRollbackAndLeavesNoUncommittedValue` 覆盖回滚侧。

## R2-6 `@Param` 说明与锁定版本不符（Low）

- **复核指出**：MP 3.5.16 的 `processParameter` 走 `extractParameters(Map)`——遍历 ParamMap values、去重后逐项转集合，
  因此加 `@Param("rows")` **仍然**会逐元素填充；原注释「加了就不填充、主键为 null」是错误的框架模型。
- **核对**：本轮重新反编译 `MybatisParameterHandler`，确认 `processParameter` 调用的是 `extractParameters`
  （遍历 Map values + HashSet 去重 + `toCollection`），而不是按 `collection/coll/list/array` 取键的 `getParameters`。
  复核结论成立，注释已按事实重写：真正需要保持一致的是 `<foreach collection='list'>` 与参数绑定名
  （无注解的单个 List 由 MyBatis 暴露为 `list`/`collection`），自动填充合同由真实 IT
  `batchRowsCarryTheSameGeneratedKeysAndAuditFieldsAsPerRowInsert` 保证。

## R2-7 候选元数据漂移（Low）

`HANDOFF.md` 与 `CURRENT-EXECUTION-PLAN.md` 的「当前分支」已由复核方同步改正；本提交材料的范围摘要改为与
`git diff --stat f9ccda3` 逐项对齐的 10 个生产文件 + 1 个构建文件 + **6 个新增测试类**（此前写作 3 个，只算了
`platform-system` 侧）。

## R2-8 第二轮门禁

见 §5 表格的第二轮列与 `evidence/phase44-remediation-2026-07-26/gates-round2-2026-07-26.txt`。

## R2-9 第二轮新增的已知边界

- 写窗口是**单 JVM + 本节点 Redis 令牌**语义：B 节点的进程内 Caffeine 仍不受 A 节点写的影响（TTL 收敛），
  这与第一轮登记的跨节点广播失效开放项是同一项，未扩大也未关闭。
- 写窗口内该 typeCode/参数缓存全部回源数据库；字典与参数写是低频管理操作，未做压测量化。
- typeCode 字符集只在**写路径**强制；读路径（`listItems` 等）仍接受任意字符串，只会产生一次查空并按新协议缓存，
  不造成键碰撞，但理论上可被大量无效 typeCode 撑大 Redis 键空间——属既有行为，本轮未改，单独记录。
