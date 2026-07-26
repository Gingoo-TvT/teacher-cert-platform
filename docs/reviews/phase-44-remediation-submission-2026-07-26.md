# Phase 44 退回整改提交材料（2026-07-26）

> 身份声明：本文件由**整改者**撰写，只构成**候选证据**，不是独立复核结论。Phase 44 与全项目的正式状态以后续独立
> 增量复核报告为准（`docs/REVIEW-GATE.md`）。

- 依据：`docs/reviews/phase-44-review.md`（CHANGES REQUESTED，2 Major）与阶段总审计 `docs/reviews/phase-gap-audit-2026-07-23.md`
  的 **PG-M3**、**PG-M4**
- 分支：`codex/phase44-remediation`（自 `codex/phase53-demo-reconcile` 的 `f9ccda3` 起）
- 范围：`platform-system` 生产代码 6 文件 + 新增 `system/cache` 包 3 类 + 新增 3 个测试类；无 Flyway 迁移、无 API 变更、
  无权限点变更、无前端改动
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

| 门禁 | 结果 |
|---|---|
| Flyway（空库→最终版本） | **33 个迁移**（32 个版本化 + 1 个可重复 `R__testseed`），最终 **V32** |
| Surefire（单测） | **216/216**（`platform-system` 21 + `platform-file` 18 + `platform-boot` 177），0 failure/error/skip |
| Failsafe（IT） | **196/196**，0 failure/error/skip |
| 合计 | **412/412** |
| 9 个 reactor 模块 | 全部 SUCCESS |
| `Phase44NotificationBatchIT`（新增） | 5/5 |
| `Phase44CacheCommitWindowIT`（新增） | 6/6 |
| `platform-system` 新增离线单测 | 21/21（`EpochGuardedCacheTest` 10 + `ReferenceCacheInvalidatorTest` 7 + `InAppNotifyChannelBatchTest` 4） |
| `Phase44CachingIT`（既有逐出正确性） | 3/3，未改动 |
| `Phase5MaterialIT` / `Phase12NotificationIT` / `Phase7VideoReviewIT` | 18/18 · 5/5 · 44/44（历史上因 `sendBatch` 锁等待间歇失败的正是 `Phase5MaterialIT` 的二审通知路径） |
| 前端 `type-check` / `build` | 均通过（build 仍有既有 `echarts`/`naive` 大 chunk 警告） |
| `git diff --check` | PASS |

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
