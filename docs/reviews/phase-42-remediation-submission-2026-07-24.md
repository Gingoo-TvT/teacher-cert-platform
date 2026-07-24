# Phase 42 PG-H3 整改提交材料

- 日期：2026-07-24
- 整改基线：`5792025`
- 分支：`feature/phase42-import-rollback-barrier`
- 原正式报告：`docs/reviews/phase-42-review.md`
- 原正式结论：**CHANGES REQUESTED**
- 本材料性质：**整改候选与开发者自测证据，不是独立 PASS**

## 1. 整改范围

本轮只处理原报告的 1 个 Major（PG-H3）：`confirmImport` 逐行 `REQUIRES_NEW` 提交时，rollback 可能只补偿一次性读取到的 refs，随后 confirm 继续提交迟到业务数据；confirm 最终条件更新命中 0 行仍返回本地 `IMPORTED/FAILED`。

原报告已通过的唯一约束、视频并发结算/合并与证书重开不重开。Phase 39、41、47、53、44、0 及全项目正式状态均不随本候选改变。

## 2. 串行化协议

1. confirm 在解析策略和预览后，原子认领 `PREVALIDATED → IMPORTING`。
2. 每个逐行 `REQUIRES_NEW` 事务首先对 batch 执行 `SELECT ... FOR UPDATE`，确认数据库持久状态仍为 `IMPORTING` 后，才读取/写入 student、training、certificate 与 `import_record_ref`。
3. 行失败后的 `import_error_detail` 也使用独立事务并取得同一 batch 行锁，禁止 rollback 终态后出现迟到错误明细。
4. rollback 在单个事务内运行，batch `SELECT ... FOR UPDATE` 是该事务第一条数据库语句。它会等待已在途的行事务提交，并阻断后续行进入；取得锁后锁定当前完整 ref 集及对应业务记录、按 ref id 逆序补偿，最后将补偿计数与 `ROLLED_BACK/PARTIAL_ROLLBACK` 原子提交。
5. confirm 收尾 `IMPORTING → IMPORTED/FAILED` CAS 必须恰好命中 1 行；若未命中，重读数据库真实状态并返回“导入已停止”，不返回本地累计终态。

生产 `ExchangeImportHook` 是无行为的并发交错观察点，只供集成测试确定性停在“行已持锁未写入”和“行已提交”两个位置，不参与业务决策。

## 3. 线性化结果

| 先取得 batch 锁的一方 | 可观察结果 |
|---|---|
| rollback | rollback 完成并提交终态；后续行取得锁时看到非 `IMPORTING` 并停止；confirm 收尾不得伪成功 |
| 在途行事务 | rollback 等待该行提交，随后读取其完整 refs 并在同一事务内补偿；后续行停止 |
| confirm 收尾 CAS | 批次先进入 `IMPORTED/FAILED`；随后 rollback 按既有可回滚状态取得锁并补偿 |

## 4. 确定性交错反例

测试类：`platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase10ExchangeIT.java`

### 4.1 `confirmAndRollbackInterleavingCannotLeaveLateCommittedRows`

- 准备两条有效导入行。
- 第一行事务提交后暂停 confirm。
- rollback 完成并持久化 `ROLLED_BACK`。
- 释放 confirm 后，confirm 返回“导入已停止/ROLLED_BACK”。
- 断言两行均无活跃 student/training/certificate，第二行无迟到 ref。

### 4.2 `rollbackWaitsForInFlightRowAndCompensatesItsCommittedRefs`

- 第一行取得 batch 锁、尚未写业务数据时暂停。
- 发起 rollback，断言其在行事务持锁期间不能完成。
- 释放行事务后，rollback 读取并补偿该行完整 3 条 refs。
- 断言终态 `ROLLED_BACK`、三类业务数据均无活跃记录、3 条 refs 保留用于追溯。

两个交错反例 **2/2**，完整 `Phase10ExchangeIT` **10/10**。

## 5. 门禁证据

- 后端模块 package：9 模块 `BUILD SUCCESS`。
- fresh 隔离 MySQL/Redis/MinIO 执行 `mvn -B -ntp clean verify`：
  - Surefire：**149/149**
  - Failsafe：**171/171**
  - 合计：**320/320**
  - failures/errors/skipped：**0/0/0**
  - 生产迁移：**32 个，最终 V32**
- 前端 `npm --prefix frontend run type-check`：PASS。
- 前端 `npm --prefix frontend run build`：PASS，仅既有 large chunk warning。

首次全量运行的唯一失败是临时 MinIO 缺少正式 dev 编排已有的 CORS 白名单，使 Phase 7 恶意 Origin 环境契约断言得到错误响应。只补齐临时隔离编排后，该用例定向 **1/1**，随后从 clean 状态重跑全量 **320/320**；未修改或放宽应用断言。

本轮临时 `tcp-phase42` 容器与网络已移除，未创建或删除数据卷，未启动常驻后端/前端。

## 6. 变更边界

- 无 DDL/Flyway 迁移。
- 无业务规则、权限点或前端生产代码变化。
- 未新增 `ROLLING_BACK` 状态，未修改 `ExchangeBatchStatus`。
- 未修改 `ImportRecordRefMapper`。
- 既有 `PARTIAL_ROLLBACK` 自动重试是否跳过已成功补偿 ref 的语义未重新设计；该存量语义债不属于原 PG-H3，不在本材料中宣称闭环。
- 失败明细事务已复用同一 batch 锁与 `IMPORTING` 守卫并纳入全量回归，但本轮没有为“行失败后、错误明细落库前”再增加第三条专用 latch 交错；这是非阻断的测试证据边界。
- 未执行任何 cyber 指令、漏洞扫描、攻击性探测、恶意载荷、fuzz、凭据尝试、压力或对既有服务的破坏操作；当前复核不需要用户执行此类命令。

## 7. 独立增量复核请求

请独立复核者只核：

1. rollback 的第一条数据库语句是否确为 batch 锁，且补偿与终态是否在同一事务提交。
2. 所有逐行业务写入、ref 和失败明细是否均在同一 batch 锁及 `IMPORTING` 守卫之后。
3. 两种确定性交错是否真实覆盖 rollback 先线性化和在途行先线性化。
4. confirm 收尾 CAS 命中 0 行时是否返回数据库真实状态，而非伪成功。
5. 全量回归是否保持 320/320，且没有把其他阶段欠账误写为关闭。

在新的独立报告给出 PASS 前，Phase 42 与全项目均继续保持 **CHANGES REQUESTED**，Phase 39 不放行。
