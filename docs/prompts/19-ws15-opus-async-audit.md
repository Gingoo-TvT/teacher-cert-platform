# WS-15 ·（可选）审计写异步重试 — 提示词（Opus 4.8）

你是执行 **WS-15**（可选）的 opus。对应 `docs/audit-remediation-plan.md` §5 WS-15（Phase 44b 诚实推迟项，44f 根因修复后已解锁）。**纯收益优化**——现状同步写可接受，若消融复验不过就**再次诚实回退**。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§5 WS-15**；`git remote -v` 空 → `git switch -c feature/ws15-async-audit`。

## 背景
Phase 44b 曾完整实现 `@Async` 审计落库（请求线程捕获 ThreadLocal → 异步写 + dev/test 用 `SyncTaskExecutor` 保 IT 确定性），但 `@EnableAsync` 后 Phase5/6 通知路径间歇 `Lock wait timeout` 而诚实回退。**Phase 44f 已根治该路径根因**（`Db.saveBatch` 另开 SqlSession 与外层事务自锁互等）——当时"未查明干扰源"大概率就是它。

## 任务
- **现状核实**：44b 的异步代码**已完全回退**——全库现**无** `@EnableAsync`/`@Async`/`SyncTaskExecutor`（删净、非注释）→ **从零重建**，不是取消注释。
- **44f 修复已确认在位**（`InAppNotifyChannel.sendBatch` `:37-52` 已用**同事务逐行 `notificationMapper.insert`**、全文件**无 `Db.saveBatch`**/另开 SqlSession；DEVLOG 44f「连续 2 次 114/114 绿、Lock wait 0 命中」）→ 前提的**必要条件满足**。
- **但"@Async 锁等待 == 该 self-lock"仍只是假设、未证实**：DEVLOG 把 44b 时 `@EnableAsync` 的锁等待记为「**无关 / 未查明干扰源**」，44f 是**事后另查到**的通知路径自锁——两者是否同一根因没证据。**尤其审计异步写落 `audit_log`（非 `notification`）**，若它另有连接/锁争用，44f 帮不上。故把"已解锁"当**待验假设**，**3 次全量消融是唯一裁判**——抓出争用即诚实回退。
- 重提 44b 原方案（`@Async` + `SyncTaskExecutor` for dev/test + 请求线程捕获 ThreadLocal）。
- 让每个 `@AuditLog` 写端点少一次同步阻塞落库。

## 验收（硬门槛：消融复验）
- `@EnableAsync` 开启后，连续 **3 次**全量 `mvn verify` **零锁等待**才可合并。
- 仍复现 `Lock wait timeout` → **再次诚实回退**并在 DEVLOG 记录消融结论。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行；连续 3 次 `mvn -B -ntp clean verify` 全绿（消融证据）。
2. `DEVLOG.md`（倒序，含 3 次 verify 消融结果或回退说明）+ `docs/launch-readiness-plan.md` §11 追加 WS-15 条目。
3. **单 commit**（`feature/ws15-async-audit`）→ **STOP** 交主控复核。**禁止 merge / push**。
