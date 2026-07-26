# Phase 44 第三轮整改提交材料（2026-07-26）

> 身份声明：本文件由整改者撰写，只构成候选证据，不是独立复核结论。Phase 44 仍按
> `docs/REVIEW-GATE.md` 保持“待独立复核”，不得由 Codex 自行置 PASS。

- 退回依据：`docs/reviews/phase-44-second-remediation-rereview-2026-07-26.md`
  （CHANGES_REQUESTED，0 Critical / 0 High / 2 Medium / 3 Low）
- 基线：`25f8b1c0683dae38f930a8cb94baf937a446013d`
- 候选：`8ff544db37b7cd89a9f8463de4d1696b46c50f45`
- 精确增量：`25f8b1c..8ff544d`，16 个路径，952 insertions / 172 deletions
- 分支：`codex/phase44-remediation`
- 范围：仅字典缓存写窗口、typeCode identity、相关前端收敛、3 个 Low 与反例；不重写上一轮已经关闭的
  Caffeine 发布窗口、Redis 单 Lua/版本戳/故障隔离、键前缀碰撞、MySQL session 恢复和未提交 read-through。
- 无 Flyway、API 路径、返回结构、权限点或业务规则变更。

## 1. 2 个 Medium 的整改

### 1.1 Redis pending 改为逐 owner 可续租 ZSET

`DictServiceImpl` 为每次失效登记生成独立 UUID owner，Redis ZSET 的 member 为 owner、score 为使用 Redis
`TIME` 计算的租约到期毫秒：

1. `BEGIN_WRITE_WINDOW` 原子清过期 owner、加入本 owner、置 `P:ACTIVE`、删除 payload。
2. 后台线程按配置周期只续租仍存在的同 owner；`RENEW_WRITE_WINDOW` 不会把已丢失 owner 重新创建。
3. `TransactionSynchronization.beforeCommit` 强制再续租一次；登记失败、后台续租不确定或 owner 丢失均抛
   `BizException` 阻止事务提交。
4. `COMPLETE_WRITE_WINDOW` 只移除自己的 owner；仍有 owner 时继续 pending，最后一个 owner 才恢复普通随机
   version 并删除 payload。
5. `READ_ITEMS_WITH_VERSION` 与 `PUT_IF_VERSION_UNCHANGED` 都先原子清理过期 owner 并检查 `ZCARD`；有活跃
   owner 时既不供应也不回填。
6. 进程崩溃或 completion 失败后，续租停止；逐 owner score 到期，下一次 READ/PUT 原子清理并从已提交数据库真值重建。

配置为 `platform.cache.dictionary.writer-lease`（默认 2m）和
`writer-renew-interval`（默认 20s）。启动期拒绝 null、非正、亚毫秒、续租周期大于租约三分之一及恢复 TTL
倍增溢出。后台线程为 daemon，并在 Bean 销毁时停止。

### 1.2 typeCode 使用唯一 canonical identity

所有 DB 查询/写入、Redis payload/version/writers 键、pending、两个 Caffeine `@Cacheable` key/逐出、VO 输出与
前端保存统一使用：

```text
trim → ASCII [A-Za-z0-9_]{1,64} 后端硬校验 → Locale.ROOT 小写
```

因此 MySQL `_ai_ci` 视为同一 identity 的 `material_category` / `MATERIAL_CATEGORY` 不再形成两套缓存。
Redis payload 包络升级为 schema v2，命中必须同时满足：

- `schema == 2`
- `typeCode == canonical typeCode`
- `v == 当前 version`

旧裸数组、旧 `{v,items}`、大小写 identity 不一致或版本不一致的 payload 全部失败关闭并从 DB 重载，避免上一候选
留下的同 version 旧包络继续命中。

发布前提：所有后端实例必须使用同一候选版本完成重启，禁止旧、新 binary 混跑；既有多实例 Caffeine 广播债仍按原
long-term/final-audit 边界处理。

## 2. 3 个 Low 的整改

- `NotificationMapper` 注释已准确区分 MyBatis `<foreach collection>` 参数绑定与 MP 3.5.16
  `extractParameters(Map)` 自动填充；删除了“增加 `@Param` 必然破坏自动填充”的错误结论。
- `ReferenceCacheInvalidatorTest` 通过可替换的 package-visible registrar 确定进入 active synchronization 的
  registration exception 分支，断言 begin/end、steps 不执行、原异常传播；另补 `beforeCommit` 守卫回调测试。
- 两个锁测试不再只依赖 300ms 未完成：捕获 contender 线程，并通过
  `ReentrantLock.hasQueuedThread(thread)` 包内只读 probe 证明它已真实排队等待 `publishLock`，再断言 release 前未完成。

## 3. 新增/强化反例

`Phase44CacheCommitWindowIT` 现有 16 个测试方法；本轮新增或强化的关键动态反例：

| 反例 | 证明目标 |
|---|---|
| `overlappingWritersKeepRedisPendingUntilTheLastOwnerCompletes` | 同 typeCode 两个重叠事务有 2 个独立 owner；先完成者只删自己，仍 pending；最后完成者才恢复 |
| `liveWriterRenewsItsOwnerBeyondTwoLeasePeriods` | 6s 测试租约下保持事务 13s；每轮走真实 READ Lua prune，owner/pending 持续存在且旧快照不回填 |
| `ownerLossBeforeCommitAbortsTheTransactionAndRollsBackDb` | 确认窗口打开后删除真实 owner；提交必须以精确 `BizException` 失败，DB 保持 V1 |
| `expiredCrashOwnerAndPendingVersionRecoverFromCommittedDbTruth` | 预置过期 owner + pending + 旧 payload；READ 清理后从已提交 DB V2 重建 |
| `caseAliasesShareCanonicalDbRedisAndCaffeineIdentityOnCommitAndRollback` | 大小写别名共用 DB、Redis 三类键及两个 Caffeine cache；commit/rollback 均不留别名旧值 |
| `legacyEnvelopeWithMatchingVersionCannotSurviveCaseAliasUpgrade` | 同 version 的旧 `{v,items}` 仍被拒绝，并重写为 canonical schema v2 |

单元测试另覆盖：owner 配置边界、真实 registration failure、beforeCommit 失败、publishLock 真实排队、键空间与
canonical identity。

## 4. 本会话已执行的安全门禁

以下均为本地、自然退出、无外部服务/网络/数据库/Redis/Docker 的门禁：

| 门禁 | 结果 |
|---|---|
| `mvn -o -B -ntp -pl platform-system -am test` | PASS，44/44 |
| `mvn -o -B -ntp -pl platform-boot -am -DskipTests test-compile` | PASS，9/9 modules；49 个 boot 测试源编译通过 |
| `mvn -o -B -ntp -DskipTests package` | PASS，9/9 modules |
| `npm --prefix frontend run type-check` | PASS |
| `npm --prefix frontend run build` | PASS（仅既有大 chunk warning） |
| `git diff --check` / staged diff check | PASS |

三路实现会话内只读交叉复核分别检查 Redis 状态机、canonical identity 与 Low 测试真实性，最终均未报告
Critical/High/Medium/Low；这不是 REVIEW-GATE 的独立 PASS。

## 5. 明确未执行、必须由用户/独立复核者运行的动态门禁

按用户安全要求，Codex **没有**连接或启动 MySQL/Redis/Docker/服务，也没有执行并发事务、Redis owner 删除、崩溃状态
注入等可能属于 cyber 的动作，因此不得把上述 16 个 IT 写成已 PASS。

请用户在**获授权、全新、隔离且可销毁**的 MySQL 8 / Redis 7 复核环境中执行：

```powershell
mvn -B -ntp -o -pl platform-boot -am "-Dit.test=Phase44CacheCommitWindowIT" verify
```

预期：`Phase44CacheCommitWindowIT` **16/16**，0 failure / 0 error / 0 skip；重点核对上表 6 条反例真实运行。
不要对共享开发库或生产 Redis 执行此命令；该类会创建/清理 `p44_commit_window*` fixture，并包含删除测试 owner 的故障注入。

独立复核者还应自行重跑必要回归并检查 `25f8b1c..8ff544d`；在动态证据与独立报告给出 PASS 前：

- Phase 44 只能标记“第三轮整改候选待复核”；
- Phase 0 不放行；
- 不 merge、push、部署、切流或宣称稳定发布。
