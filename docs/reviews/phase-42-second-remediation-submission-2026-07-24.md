# Phase 42 第二轮整改提交材料

- 日期：2026-07-24
- 独立复核基线：`ec4ca30`
- 分支：`feature/phase42-import-rollback-barrier`
- 首轮独立报告：`docs/reviews/phase-42-remediation-rereview-2026-07-24.md`
- 首轮正式结论：**CHANGES REQUESTED（1 Medium / 1 Low）**
- 本材料性质：**第二轮整改候选与开发者自测证据，不是独立 PASS**

## 1. 整改范围

本轮只处理首轮独立报告新增的两项：

1. Medium：逐行及错误明细事务使用 `SELECT * ... FOR UPDATE`，每行重复装载保存整批 26 列预览的 `preview_json`，把 N 行确认路径退化为 O(N²) DB→JVM 数据量。
2. Low：错误明细的 batch 锁与 `IMPORTING` 状态守卫没有专用 rollback 交错反例。

原 PG-H3 已确认闭合的 batch 串行化协议、rollback 单事务补偿和 confirm 收尾 CAS 保持不变。Phase 39、41、47、53、44、0 及全项目正式状态均不随本候选自行改变。

## 2. Medium 修正：固定大小的行锁投影

新增 mapper：

```sql
SELECT status
FROM import_export_batch
WHERE id = ?
  AND deleted = 0
FOR UPDATE
```

- 逐行 `REQUIRES_NEW` 和失败明细 `REQUIRES_NEW` 均通过该标量查询取得 batch 行锁。
- `null` 继续表示批次不存在或已逻辑删除；非 `IMPORTING` 继续抛导入停止异常，未放宽状态守卫。
- rollback 仍通过原锁查询一次读取补偿所需批次元数据；没有为性能移除 `FOR UPDATE` 或拆分事务。
- 无预览 JSON、实体批次或其他变长字段进入逐行锁查询，故锁查询返回量相对行数为固定 O(1)，原 O(N²) 控制流已从代码结构上消除。

### 2.1 防回归证据

1. 通过 MyBatis `MappedStatement/BoundSql` 精确断言 SQL 为上述 status-only 投影，结果类型为 `String`，且不包含 `*`/`preview_json`。
2. 在真实 RANDOM_PORT HTTP confirm 路径安装测试专用 `StatementHandler` 探针：一条成功行、一条数据库失败行及该失败行的错误明细，共捕获 3 条 batch `FOR UPDATE`，全部精确等于 status-only SQL。
3. rollback 锁语句只要求仍从 `import_export_batch` 取得 `FOR UPDATE`；测试不再把 `SELECT *` 固化为永久实现，保留未来显式列投影优化空间。

未执行万行压测。该 finding 是由确定性的查询投影和循环次数证明，整改也由静态契约与真实 SQL 调用链证明；本轮不以压力运行代替结构性修复。

## 3. Low 修正：T-IMP-5C 失败明细双向交错

### 3.1 rollback 先线性化

测试：`rollbackBeforeErrorDetailLockPreventsLateErrorDetail`

1. 让导入行在数据库约束处失败并完成行事务回滚。
2. 在错误明细事务竞争 batch 锁之前暂停 confirm。
3. rollback 先提交 `ROLLED_BACK`，且补偿计数为 0。
4. 释放错误明细事务；其 status-only 行锁查询读到非 `IMPORTING` 后停止。
5. 断言无迟到 `import_error_detail`、无 ref、无失败行业务记录，confirm 返回数据库真实 `ROLLED_BACK`。

### 3.2 错误明细先线性化

测试：`rollbackWaitsForLockedErrorDetailAndPreservesCommittedDetail`

1. 错误明细事务取得 batch 行锁后、写入前暂停。
2. 发起 rollback；先确认线程已经到达 MyBatis `StatementHandler.query` 执行边界，再以短超时辅助断言其不能完成。
3. 释放错误明细写入；记录“事务持锁期间已写完”和“rollback 实际取得 batch 锁”的事件顺序，前者必须先发生。
4. 错误明细提交后 rollback 才能取得锁并提交 `ROLLED_BACK`。
5. 断言恰有 1 条已线性化错误明细，无 ref/业务记录，confirm 不伪报成功。

同一 query-entered 正向观察也加到原 T-IMP-5B，避免反向行事务用例只证明 rollback 到达 SQL 前测试 hook。该观察点位于 `invocation.proceed()` 前，500ms 仍是锁阻塞的辅助时间证据，存在极低调度窗口；本材料将其保留为非阻断证据限制，不宣称完全消除时间型断言。核心正向证据仍包括释放持锁事务后 rollback 实际取得 batch 锁、完整补偿/错误明细保留与数据库终态。生产 `ExchangeImportHook` 的新增观察点全部为空操作，不参与状态决策或数据库访问。

## 4. 测试门禁

- `Phase10ExchangeIT`：**13/13**，0 failure/error/skip。
- 全新隔离 MySQL 8 / Redis 7 / MinIO：
  - Surefire：**149/149**
  - Failsafe：**174/174**
  - 合计：**323/323**
  - failures/errors/skipped：**0/0/0**
- Flyway：**32 个版本化生产迁移，最终 V32**；测试环境另加载 repeatable seed。
- 后端 9 模块 package：PASS。
- 前端 `npm --prefix frontend run type-check`：PASS。
- 前端 `npm --prefix frontend run build`：PASS，仅既有 large chunk warning。
- `git diff --check`：PASS。

最终 323/323 gate 后只把两处断言说明从“真实 JDBC”诚实化为“MyBatis `StatementHandler.query` 执行边界”，未改测试控制流或生产逻辑；随后对当前源码重新执行 9 模块 `-DskipTests package`，编译与打包通过。全量 gate 的行为字节码已包含 query 探针、Future 取消与线程池二次等待。

所有动态反例只使用本轮临时、隔离的 MySQL/Redis/MinIO。未由 Codex 启动常驻项目服务。

## 5. 变更边界

- 无 DDL/Flyway、业务规则、权限点、前端生产代码或状态集合变化。
- 未新增或修改对外 API。
- 未修改 `PARTIAL_ROLLBACK` 自动重试语义；该存量债务不属于本轮 1 Medium / 1 Low。
- 提交前只读交叉审查保留 1 个非阻断测试债：`shutdownExecutor` 在外层 JUnit 中断分支执行 `shutdownNow()` 后未再等待终止，极端失败路径可能短暂与 `@AfterEach` 清理重叠；正常路径已有终止硬断言，不影响生产实现、本次 13/13 或首轮两项 finding 的闭环判断。
- 未执行 cyber 指令、漏洞扫描、攻击性探测、恶意载荷、fuzz、凭据尝试、压力或针对既有服务的破坏操作。
- 本候选不会把第二轮自测结果写成正式 finding 关闭；独立报告 PASS 前 Phase 42 与全项目继续保持 **CHANGES REQUESTED**，Phase 39 不放行。

## 6. 独立增量复核请求

请冻结 `ec4ca30..本候选提交`，重点核对：

1. 逐行与错误明细真实调用链是否只执行固定大小的 status-only batch 锁查询。
2. rollback 是否仍以单次 batch 行锁开始同一补偿事务，原 PG-H3 不被性能修正重开。
3. MappedStatement 契约和真实 SQL 探针能否阻止未使用 mapper、重新选择 `preview_json` 等回归。
4. T-IMP-5C 两个方向是否分别阻止 rollback 后迟到错误明细，并证明错误明细持锁时 rollback 等待。
5. 323/323 全量门禁和 Phase10 13/13 是否与提交源码同源。

只有新的独立报告给出 PASS，才能关闭 Phase 42 并进入 Phase 39。
