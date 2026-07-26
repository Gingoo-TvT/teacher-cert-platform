# Phase 44 第四轮整改提交材料（2026-07-27）

> 身份声明：本文件由整改者撰写，只构成候选证据，不是独立复核结论。Phase 44 仍按
> `docs/REVIEW-GATE.md` 保持“待独立复核”，不得由 Codex 自行置 PASS；Phase 0 继续不放行。

- 退回依据：`docs/reviews/phase-44-third-remediation-rereview-2026-07-26.md`
  （CHANGES_REQUESTED，0 Critical / 0 High / 1 Medium / 4 Low）
- 第三轮报告/治理基线：`69f7462112e8df7aaffdd8d5f45914d3b030f5c6`
- 第四轮代码/测试/部署合同候选：`228a3553607a8fb6ca2db048125d4137e65191ae`
- 精确增量：`69f7462..228a355`，11 个路径，1136 insertions / 27 deletions
- 分支：`codex/phase44-remediation`
- 范围：只修第三轮 1 Medium / 4 Low，并加固同一根因的同步注册失败边界；不重写此前已闭环的
  Caffeine 纪元、Redis 逐 owner ZSET/续租、schema v2、canonical identity、批量通知与 MySQL 锁路径。
- 无 Flyway、API 路径、返回结构、权限点或业务规则变更。

## 1. 阻断 Medium：owner 丢失后不得发布未提交字典值

### 1.1 残留 pending 保持失败关闭

`READ_ITEMS_WITH_VERSION` 清理过期 writer 后，若 writers 已空但 version 仍以 `P:` 开头：

1. 继续返回 pending，不再提前改成普通随机 version；
2. 删除任何残留 payload，不供应也不接受回填；
3. positive recovery TTL 不被 READ 刷新，避免读流量让崩溃状态永久续命；
4. 仅对历史异常的“无 TTL pending”补一次有界 recovery TTL；
5. 只有 `P:` 自然到期、下一次 READ 看到 version 真正缺失时，才建立普通 version 并从已提交 DB 重建。

状态收敛为：

```text
ACTIVE(P + writers) -> RECOVERING(P + no writers)
                     -> P TTL 自然到期
                     -> EMPTY -> CLEAN(normal version)
```

### 1.2 本 JVM 发布守卫覆盖 Redis 协调状态整体丢失

新增 `DictRedisPublishGuard`：

- 使用固定 32 个 stripe，每个 stripe 是 `ReentrantLock + canonical typeCode activeCount`，不存在按 key
  锁删除/重建的 ABA 两把锁竞态；
- `begin(typeCode)` 与 `publishIfIdle(typeCode, Redis PUT action)` 在同一 stripe 锁内线性化；
- writer 先进入时，Redis PUT action 完全不执行；PUT 先进入时，writer 等 PUT 完成后再登记，随后
  Redis BEGIN 会删除刚写入的 payload；
- handle 幂等关闭；本地 guard 从写窗口登记保持到 Redis completion、两个 Caffeine 窗口清理全部结束之后；
- `markLost` 只停止续租并阻止提交，不提前释放本地 guard。因此即使 writers 与 version 同时丢失，
  同一事务 read-your-writes 得到 V2，也不能把 V2 发布到共享 Redis。

所有字典 DML 路径又统一调整为“完成业务校验 → 登记写窗口 → mapper DML”。若
`registerSynchronization` 异常，数据库尚无本次未提交变更；外层事务即使捕获异常继续，也不存在可发布的 V2。

## 2. 决定性交错与离线反例

真实依赖 suite 仍为 16 个测试方法，但强化了第三轮漏测的两个既有用例：

| 用例 | 第四轮必须证明的交错 |
|---|---|
| `ownerLossCannotPublishUncommittedValueBeforeCommitRollback` | `update V2 → 删 writers → 同事务读 V2 → 并发只见 V1 → payload 空且 version 仍 P → 再删 version → 同事务仍读 V2但本地 guard 拒绝 PUT → beforeCommit 精确回滚 → V1 可重新回填` |
| `crashPendingRemainsFailClosedUntilRecoveryTtlExpires` | 预置过期 owner、4 秒 `P:` 与旧 payload；两次间隔 READ 都只回源 V2，三次 PTTL 严格递减，原截止点自然到期后才建立普通 version 并回填 V2 |

新增离线单元反例：

- `DictRedisPublishGuardTest` 4/4：嵌套 writer、幂等 close、PUT 与 begin 的真实排队线性化、action
  异常后锁释放；
- `DictWriteWindowOrderingTest` 5/5：create/update/delete item 与 update/delete type 五条 DML
  在同步注册失败时均未执行 mapper mutation。

专用门禁会先删除该 suite 的旧 XML/text/summary；除精确 16/16 与 summary `completed=16` 外，还强制
XML 包含上述两个第四轮 testcase 名。当前工作区已有的旧 16/16 XML 含第三轮旧方法名，明确不能作为本轮证据。

## 3. 4 个 Low 的整改

1. **Maven 门禁可复制且不假绿：** 新增 `scripts/test-phase44-cache-commit-window-real.sh`，固定
   `-Dfailsafe.failIfNoSpecifiedTests=false`，只放过 reactor 非目标模块的零匹配；目标模块仍需精确
   suite/classname、16 个唯一 testcase、0 failure/error/skip、summary `completed=16` 和第四轮方法名。
2. **Compose/.env 透传租约：** `docker-compose.yml` backend 显式透传
   `DICT_CACHE_WRITER_LEASE` / `DICT_CACHE_WRITER_RENEW_INTERVAL`；`.env.example` 记录默认
   `2m/20s`、正值、`renew <= lease/3` 与多实例一致性约束。
3. **权威禁混部/回滚合同：** `docs/phase-14-非功能部署验收.md` 与 README 明确停写、排空、停止全部
   旧节点、等待旧 60 秒 pending、定向清理边界、全量同版本启动、逐实例核验、恢复流量及禁止旧 binary
   回滚；禁止 `FLUSHDB` / `FLUSHALL` 和新节点启动后人工删 writers。
4. **棕地 identity preflight：** 新增 `scripts/preflight-phase44-dict-identity.sh`，只接受
   `mysql_config_editor` login-path 与只读事务，核对两列 `utf8mb4_0900_ai_ci`，枚举非法值、
   非 canonical 物理值及 canonical collision；任一异常失败关闭，脚本不修改数据，必要修复必须走独立 Flyway。

## 4. 整改者已执行的安全离线门禁

以下命令均自然退出，未连接或启动 Docker、MySQL、Redis、MinIO、服务或网络：

| 门禁 | 结果 |
|---|---|
| `mvn -o -B -ntp -pl platform-system -am test` | PASS，53/53；0 failure/error/skip |
| `mvn -o -B -ntp -pl platform-boot -am -DskipTests test-compile` | PASS，9/9 modules；49 个 Boot 测试源编译通过 |
| `mvn -o -B -ntp -DskipTests package` | PASS，9/9 modules |
| `npm --prefix frontend run type-check` | PASS |
| `npm --prefix frontend run build` | PASS（仅既有大 chunk advisory） |
| Git Bash `-n` 检查两个新脚本 | PASS |
| `git diff --check` / staged diff check | PASS |

定向只读对抗复核另确认：DML 前登记、guard completion 清理可观测性、PTTL 单调下降和旧 XML 防复用均已
落实；这仍不是 REVIEW-GATE 的独立 PASS。

## 5. 未执行：必须由用户或独立复核者运行

按用户安全边界，Codex **没有**运行以下会连接真实依赖、操作测试数据或调用 Docker 的命令。

### 5.1 用户执行：真实 MySQL/Redis owner-loss 动态门禁

仅在获授权、全新、隔离且可销毁的回环 MySQL 8 / Redis 7 环境执行；先通过既有秘密注入机制设置
`SPRING_DATASOURCE_*` 与 `SPRING_DATA_REDIS_*`，不得把凭据写入仓库、命令参数或报告：

```bash
export PHASE44_ALLOW_CACHE_COMMIT_WINDOW_IT=1
export PHASE44_ISOLATED_ENVIRONMENT_ACK=1
export PHASE44_DEDICATED_TARGETS_ACK=1
bash scripts/test-phase44-cache-commit-window-real.sh
```

脚本会执行：

```bash
mvn -f pom.xml -B -ntp -o -pl platform-boot -am \
  "-Dit.test=Phase44CacheCommitWindowIT" \
  "-Dfailsafe.failIfNoSpecifiedTests=false" \
  verify
```

预期：目标 suite 精确 **16/16**，0 failure/error/skip，summary `completed=16`，并包含两个第四轮
testcase 名。不要指向共享开发库或生产 Redis；该 suite 会创建/清理固定 `p44_commit_window*` fixture，
并执行删除测试 owner/version 的故障交错。

### 5.2 用户执行：棕地只读 identity preflight

```bash
mysql_config_editor set --login-path=phase44-preflight \
  --host=127.0.0.1 --port=3306 --user='<只读账号>' --password

export PHASE44_ALLOW_IDENTITY_PREFLIGHT=1
export PHASE44_AUTHORIZED_TARGET_ACK=1
export PHASE44_READ_ONLY_ACCOUNT_ACK=1
export PHASE44_MYSQL_HOST=127.0.0.1
export PHASE44_MYSQL_PORT=3306
export PHASE44_MYSQL_DATABASE='<目标 schema>'
bash scripts/preflight-phase44-dict-identity.sh
```

预期：`columns=2/2`、collation 为 `utf8mb4_0900_ai_ci`，invalid/noncanonical/collision 均为 0。
远端棕地库应由获授权执行者在目标主机内运行，或使用已批准的安全回环入口。失败时禁止发布和手改库。

### 5.3 用户执行：Compose 非默认值静态展开

该命令只做配置展开，不启动容器；由于它仍调用 Docker CLI，Codex 未执行：

```powershell
$env:DICT_CACHE_WRITER_LEASE = '9m'
$env:DICT_CACHE_WRITER_RENEW_INTERVAL = '3m'
docker compose --env-file .env.example -f docker-compose.yml config
```

请在输出的 backend environment 中核对两项分别为 `9m` / `3m`，并避免把使用真实 `.env` 展开的完整配置
归档到报告。

完成上述动态证据后，只能提交第四轮最小独立增量复核。在正式报告给出 PASS 前：

- Phase 44 维持“待独立复核/不放行”；
- Phase 0 不放行；
- 不 merge、push、部署、切流或宣称稳定发布。
