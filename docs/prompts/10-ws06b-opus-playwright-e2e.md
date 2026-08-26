# WS-6b · Playwright E2E 冒烟 + axe a11y 基线 — 提示词（Opus 4.8）

你是执行 **WS-6 E2E** 的 opus。对应 `docs/audit-remediation-plan.md` §4 WS-6（审计 #6）。codex 并行搭 Vitest/CI 脚手架（`09-ws06a-codex-vitest-ci.md`）——E2E 接入同一 CI job。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§4 WS-6**；`git remote -v` 空 → `git switch -c feature/ws06-playwright-e2e`。
- **承接 WS-5 T3**：E2E 要覆盖登录/刷新/401 重试，给 refresh HttpOnly 改造兜回归。
- **衔接 WS-4 D6**：axe 基线与 D6 的可访问性收尾对齐。
- **⚠️ 复用 WS-4 D0 的 playwright（避免重复引入）**：WS-4 D0 已加 `playwright` devDep + `frontend/scripts/ui-shots.mjs`（含各角色 **API 登录** + captcha SVG 解析〔照 `Phase2SecurityIT.captchaCode`〕+ storageState）——E2E **复用其登录 helper**，用 **`@playwright/test`**（项目测试框架，**非**主控本机的 playwright-MCP）写用例。若 WS-4 D0 尚未落地，则本 WS 自引 `@playwright/test` 并把登录 helper 留给 WS-4 复用（先协调）。
- **⚠️ E2E 需活体栈**：登录/视频/导入等要真「后端 jar + 前端产物 + mysql/redis/minio」在跑——CI 里须先起栈（复用 `docker-compose.dev.yml` 的 mysql/redis/minio + 起后端 + 前端 preview/nginx），本地同理；账号用 demo/testseed（§0 口令漂移复位礼仪）。

## 任务
- **Playwright** E2E 冒烟（3–5 条）：登录、首登改密、RBAC 菜单、导入预校验、视频任务、敏感字段权限。
- 接入 **axe** 可访问性基线。
- CI **PR 阻断**（接 codex 的 CI job）。

## 验收
- CI 跑 3–5 条 E2E + axe 基线；失败**阻断合并**。
- 各 E2E 用 demo/testseed 账号活体跑通（注意 §0 口令漂移复位礼仪）。

## 收尾（硬门槛）
1. 前端 `type-check` + `build` 绿；E2E 本地跑通。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-6 E2E 条目。
3. **单 commit**（`feature/ws06-playwright-e2e`）→ **STOP** 交主控复核。**禁止 merge / push**。
