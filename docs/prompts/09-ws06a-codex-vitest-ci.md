# WS-6a · 前端 type-check + Vitest + CI 门禁 — 提示词（codex）

你是执行 **WS-6 前端测试脚手架**的 codex。对应 `docs/audit-remediation-plan.md` §4 WS-6（审计 #6）。opus 并行做 E2E（`10-ws06b-opus-playwright-e2e.md`）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§4 WS-6**；`git remote -v` 空 → `git switch -c feature/ws06-vitest-ci`。
- CI 文件：`.github/workflows/ci.yml`（现 **2 个并行 job**：后端 `mvn -B -ntp verify` + 前端**仅** `npm ci && npm run build`——前端 job **现无 type-check、无 test**，本 WS 补上）。前端根：`frontend/`（`package.json` 现**无 vitest**、有 pinia/vue-router；`stores/` = `user/notice/year` ✓、`api/request.ts`/`router/index.ts`/`directives/perm.ts` 均在）。
- **与 WS-6b(opus) 协同 playwright**：E2E 引入的 `playwright` 与本 WS 的 `vitest` 是两套（unit vs e2e），别混；bundle budget 与 WS-12 对齐。

## 任务
- CI 前端 job 加 `npm run type-check`。
- 引入 **Vitest**，覆盖 `frontend/src/stores/`（user/notice/year）、`frontend/src/api/request.ts`、`frontend/src/router/index.ts` 的**权限逻辑**（`v-perm`/路由守卫）。
- 加 **bundle budget**（与 WS-12 并入；本 WS 先搭门禁位，阈值可与 WS-12 对齐）。
- CI **PR 阻断**：type-check + unit 失败即阻断合并。

## 验收
- CI 跑 `type-check` + unit（Vitest）；失败**阻断合并**。
- 本地 `cd frontend && npm run type-check && npm run test`（或等价脚本）绿。

## 收尾（硬门槛）
1. 前端 `type-check` + `build` + 新 unit 绿。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-6 脚手架条目。
3. **单 commit**（`feature/ws06-vitest-ci`）→ **STOP** 交主控复核。**禁止 merge / push**。
