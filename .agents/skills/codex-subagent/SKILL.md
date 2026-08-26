---
name: codex-subagent
description: Drive the codex CLI headlessly as an implementation subagent that Codex schedules and monitors, then gate-reviews per docs/REVIEW-GATE.md. Use when the user wants codex to do the coding/phase work on teacher-cert-platform while Codex orchestrates, watches progress live, and reviews before release. Triggers — "用codex/让codex做", "codex作为subagent", "codex实现这个阶段/任务", "调度并监控codex".
---

# codex-subagent — 用 codex 作为受 Codex 调度的实现子代理

Codex 负责 **调度 + 监控 + 复核放行**；codex（`codex exec`，gpt-5.5/xhigh）负责 **实现**。闭环：Codex 派活 → codex 写代码并自测、置「待复核」→ Codex 监控全程 → 按 `docs/REVIEW-GATE.md` 复核 → PASS 放行 / 退回。

## 0. 前置（每次开跑前确认）
- 仓库根：`C:/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform`（始终用 `--cd` 指向它）。
- codex 已登录：`codex login status` 应显示 "Logged in"。未登录则提示用户 `! codex login`。
- 依赖容器在跑：`docker compose -f docker-compose.dev.yml up -d`（codex 自测要 mysql/redis/minio）。
- **工作树独占**：codex 跑时 Codex 不并发改文件、不占用 8080（codex 可能 `mvn package` + 起应用自测）。先把 Codex 自己启动的 app 关掉（`taskkill //F //IM java.exe`）。
- git 干净起点：确保要并入的前序阶段已合到 `main`，codex 从 `main` 起分支。

## 1. 核心调度命令（后台 + 全程可监控）
```bash
REPO="C:/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform"
codex exec \
  --cd "$REPO" \
  --dangerously-bypass-approvals-and-sandbox \   # 本机=外部受控环境：放行 mvn/npm/docker/git/起应用自测
  -o /tmp/codex_last.txt \                        # 末条总结落盘，便于收尾读取
  "<TASK_PROMPT>" \
  < /dev/null > /tmp/codex_run.log 2>&1           # </dev/null 防 "Reading from stdin" 卡顿
```
- **用 `run_in_background: true` 跑**（别前台阻塞）；退出时会收到 task 通知。
- Sandbox 取舍：实现任务用 `--dangerously-bypass-approvals-and-sandbox`（需跑构建/容器/网络）；纯只读探查可改 `-s read-only`；想限制可用 `-s workspace-write`（注意默认禁网络，拉新依赖会失败）。
- 续跑上次会话：`codex exec resume --last "<追加指令>"`。
- 模型/努力度：默认走 codex 配置（gpt-5.5 / xhigh）；如需覆盖 `-m <model>`。

## 2. TASK_PROMPT 模板（让 codex 走既定流程）
codex 会读 `AGENTS.md` 并自走任务循环；prompt 只需锚定目标 + 重申红线 + 收尾动作：
```
你在仓库 C:/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform 工作。
先读 HANDOFF.md → AGENTS.md（红线R1-R10 / §7 任务循环 / §7.5 复核闸门 / §4 迁移版本以磁盘 max+1 为准）→ docs/phase-NN-*.md → 相关 plan.md 章节。
执行 **Phase N（T-aaa~T-bbb）**，严格按 AGENTS §7：每任务 建分支 → PROGRESS 置[~] → 写迁移(磁盘 max+1，当前下一个 V7)+分层代码+前端 → mvn package + 起应用运行验证(含反例) → 勾 docs/phase-NN 验收清单 → 写 DEVLOG → 提交(Conventional Commits)。
红线必须遵守：文本化字段全链路 String + Excel @；不硬编码(走 sys_param/字典)；写操作 @AuditLog；列表/导出/统计 @DataScope；Flyway 幂等;
  §15.1 权限点编码为唯一来源(R8 冻结，勿增删改)；后端硬校验；保持本地私有(勿加远程/勿 git push)。
**完成整阶段所有任务后：把该阶段在 PROGRESS 置「待复核」，写 DEVLOG 阶段小结并提交，最后用 3-5 行报告(做了什么/构建与自测结果/置待复核)。严禁自行把阶段标记为 ✅。**
若遇规格冲突/缺口：记 DEVLOG + PROGRESS 标 ⚠️，按确认单默认值实现，不擅自拍板。
```

## 3. 监控（"全程"）
- 周期 `Read /tmp/codex_run.log`（codex 会打印它读的文件、跑的命令、diff、构建结果）。关注里程碑：建分支 → 写迁移 → mvn 绿 → 反例通过 → 提交 → 置待复核。
- 偏离即介入：发现 codex 违红线 / 破坏既有 / 卡死 → `TaskStop` 该后台任务，纠偏后重派（或 `codex exec resume --last` 追加纠正指令）。
- 退出后 `Read /tmp/codex_last.txt` 取末条总结，再核对 `git -C $REPO log --oneline` 与 `PROGRESS.md` 是否置「待复核」。

## 4. 完工 → Codex 复核闸门（不轻信自报）
codex 置「待复核」后，按 `docs/REVIEW-GATE.md` **独立**复核：
1. 取增量 `git diff <上阶段末>..HEAD --stat`；
2. **干净重建** `mvn clean package` + 前端 `type-check`/`build`；起依赖+应用；
3. **重跑该阶段必跑反例**（REVIEW-GATE §6，如 Phase 2：§15.1 矩阵逐格、学院A越权查学院B被拒、学生只见本人、无权限接口 403）；
4. 读码核对红线/规格；跑 `/code-review`、`/security-review`；
5. 产出 `docs/reviews/phase-NN-review.md` → **PASS**：PROGRESS 置 ✅、更新 AT 跟踪、`main` 快进合并放行；**退回**：列 Blocker/Major 交 codex 修，只复核增量+回归。

## 5. 安全 / 边界
- 始终 `--cd` 仓库根；codex 只在仓库内写。
- **保持本地私有**：prompt 明确"勿加远程/勿 push"；复核时 `git remote -v` 必须为空。
- 大阶段可拆成几段 codex 调度（按子阶段），每段监控 + 轻核，整阶段末做正式闸门——比一次性无人值守更可控（贴合"全程监控"）。
- 一次只让一个写工作树；codex 跑时 Codex 只读不写。
- `--dangerously-bypass-approvals-and-sandbox` 仅因本机是用户自有受控开发机；换环境需重新评估。
