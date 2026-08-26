# WS-1 · 恢复 `mvn verify` 绿：IT 计数按自身 fixture 隔离 — 提示词（Opus 4.8）

你是执行 **WS-1** 的 opus。这是**整个整改的第一步、P0**：不修好它，后续所有 WS 的 verify 都会被驻留 demo 数据污染误判。对应 `docs/audit-remediation-plan.md` §2 / 审计 #2 High。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0（铁律）** 与 **§2 WS-1**；本 WS **无迁移**。
- `git remote -v` 确认为空 → 从 `main` 切分支：`git switch -c feature/ws01-verify-green-it-isolation`。
- 本 WS 特殊：修复**落地前**跑 verify 要先清 demo 行；**落地后**目标是 demo 常驻共享库 verify 仍绿。

## 背景（成因）
`mvn verify` 当前不绿，因为 Phase 53 的 demo 数据（学院A demo 学生、reviewerA 视频任务）驻留共享 dev 库，撑翻了**按全局计数/总数**断言的两个 IT：
- `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase2SecurityIT.java`（约 `:141-145`：断言某学院 clerk 数据范围只 1 条 → 被 demo 学生撑成 2）。
- `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase7VideoReviewIT.java`（约 `:590-608`：断言 reviewerA total=3、第二页 1 条 → 被 demo 视频任务撑成 6）。

## 任务
把这两个（**以及全库其余按全局 count/total 断言的同类 IT**——自查）改成**只统计"本测试用唯一前缀创建的 fixture"**：
1. 给本测试造的数据加**唯一业务前缀/标记**（如测试专属用户名/学号前缀）。
2. 断言改为"结果里**属于本测试标记**的记录数 = N"且"**不含**学院B/他人记录"，而不是 `total == N`。
3. `@AfterEach` 清理自身 fixture。
4. **核心红线（不得弱化被测语义）**：既要对驻留 demo/他测数据健壮，又要**保住原断言真正校验的越权/数据范围隔离含义**——是"按自身范围断言"，**不是"放宽阈值/改大期望数"**。

## 验收（诚实）
- ① 在**有 demo 数据驻留**的 dev 库、② 在**全新库**上，各**连续跑两次** `mvn -B -ntp verify`，均绿且 Phase2/Phase7 计数稳定；CI（全新 mysql service）保持绿。
- 交付说明里**逐个列出**改动的 IT + 改法理由，证明是"按自身范围断言"而非"放宽阈值"。
- **落地收益**：demo 数据从此可常驻共享库（手测与门禁共存）。

## Stretch（时间允许再做，可选）
根治"口令漂移"：IT 的 `readyLogin` 首登改密流转把共享种子账号口令改成 `Changed123!`+翻 `must_change_pwd`——改为**用运行期自建一次性账号**演练首登改密，或**套件收尾恢复种子口令哈希**；做完则手测账号口令永不漂移。做不做都要诚实标注。

## 长期（不阻塞本 WS，仅在 DEVLOG 记一句建议）
verify 改用 Testcontainers / 每次独立 schema，一劳永逸摆脱共享库状态耦合——**另行立项**。

## 收尾（硬门槛）
1. 跑 verify 前按精确 PID 释放 :8080；确认无其他 verify 在跑（门禁串行）。
2. `mvn -B -ntp clean verify` **全绿**（按上面验收①②两种库各跑两次）。
3. 写 `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-1 条目（带证据）。
4. **单 commit**（分支 `feature/ws01-verify-green-it-isolation`）→ **STOP**，交主控复核合并。**禁止自行 merge / push**。
