# WS-10 · 删默认 `active=dev` + profile fail-fast 守卫 — 提示词（codex）

你是执行 **WS-10** 的 codex。对应 `docs/audit-remediation-plan.md` §5 WS-10（审计 #9）。**与 WS-2 协同**（共同保证误直启 jar 不落到带默认口令的 dev）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§5 WS-10**；`git remote -v` 空 → `git switch -c feature/ws10-profile-guard`。
- **⚠️ 本 WS 最大隐藏坑（已核实）**：全库 IT **零 `@ActiveProfiles`、`platform-boot/src/test/resources` 无 test `application.yml`** → 20 个 `@SpringBootTest` IT 100% 靠 main `application.yml:8 active: dev` 拿 profile（testseed flyway location、dev 数据源、jwt dev 默认全靠它）。删默认 → 全体 IT 无 profile 即崩。**推荐中心化修**：加 `platform-boot/src/test/resources/application.yml`（置 `spring.profiles.active: dev`，test classpath 覆盖）**或** failsafe/surefire `<systemPropertyVariables>spring.profiles.active=dev`——**别逐个类加 20 个 `@ActiveProfiles`**。

## 任务
- 删 `platform-boot/src/main/resources/application.yml` 的 `spring.profiles.active=dev`。
- 本地/dev 脚本**显式传** `SPRING_PROFILES_ACTIVE=dev`。
- 加**启动守卫**：非 dev 环境若检测到 `db/testseed` / dev 凭证 / 默认 secret **直接失败**。
- **同步更新**（否则破坏"免配启动"便利）：`README.md` 启动说明 + 桌面 `测试账号.txt` 的重启命令（改为带 `SPRING_PROFILES_ACTIVE=dev`）+ 测试配置显式激活 dev。

## 验收
- **无 profile 启动失败**并给出清晰提示。
- dev 脚本仍可启；prod 只加载 `db/migration`。
- **全体 IT 仍绿**（证明测试 profile 已显式激活）。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行；`mvn -B -ntp clean verify` 全绿。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-10 条目。
3. **单 commit**（`feature/ws10-profile-guard`）→ **STOP** 交主控复核。**禁止 merge / push**。
