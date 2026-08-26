# WS-6 前端自动化门禁阶段提交（2026-08-11）

> 状态：`LOCAL_STAGE_CANDIDATE_READY / INDEPENDENT_REVIEW_PENDING`
>
> 本文是开发侧提交说明，不是独立复核结论，也不授权 merge、push、deploy、cutover 或项目发布。
>
> 后续正式复核：fingerprint `3146a0c4...a45c7` 于 2026-08-11 获
> `CHANGES_REQUESTED（0 High / 1 Medium / 2 Low）`；本文件保留当时提交快照，整改见
> `ws-06-remediation-submission-2026-08-11.md`。

## 1. 阶段范围

- Vitest/jsdom：覆盖 user store 会话清理与恢复、Axios 401 单飞刷新和一次重试、router 登录/首改密/权限守卫、`v-perm` allow/deny。
- Playwright Chromium：5 条产品冒烟，覆盖登录与 401 Cookie 刷新、首登改密、RBAC 与敏感字段脱敏、导入预校验与确认、视频任务评分。
- axe：在登录页、首登改密弹窗和代表业务稳定态扫描，阻断 `critical` / `serious`；不宣称全站 WCAG 审计。
- CI：前端 job 顺序执行 `npm ci`、lint、type-check、unit、4 个既有合同、production build、Chromium 安装与 E2E；失败上传 `target/ws6-playwright`。

明确不纳入：全仓 Prettier/Spotless、覆盖率配额、bundle budget/拆包、多浏览器矩阵、真实后端或数据库联调、生产功能重构。bundle budget 保留 WS-12。

## 2. 产品门禁

| 门禁 | 结果 | 核心断言 |
|---|---:|---|
| Vitest | 4 files / 15 tests PASS | store、request、router、permission directive |
| Playwright | 5/5 PASS | 完整登录 payload/刷新、改密、RBAC/脱敏、导入非默认策略、视频评分 |
| axe | PASS | 上述稳定态无 critical/serious violation |
| ESLint | PASS | `--max-warnings 0` |
| TypeScript | PASS | `vue-tsc --noEmit` |
| 既有前端合同 | 4/4 PASS | CSP、auth/logout、dashboard latest request、WS-4 video/auth state |
| Production build | PASS | 4977 modules；仅保留既有 Naive UI/ECharts 大 chunk warning |
| Diff hygiene | PASS | `git diff --check` 无输出 |

## 3. 关键实现边界

- E2E 服务 Vite production build 产物，由测试进程内的本地静态服务器启动并在 suite 结束后关闭；Codex 未启动常驻 dev/preview 服务。
- API route 使用严格 mock：未登记请求、`pageerror` 或非预期 console error 均使测试失败。该证据证明前端产品行为，不冒充后端数据权限、真实 Cookie 属性、MySQL/Redis/MinIO 或网络链路证据。
- 原 `frontend/scripts/ui-shots.mjs` 全站人工走查器保持不变；WS-6 CI 只跑 5 条稳定、可重复的产品冒烟。
- axe 只对限定产品容器精确排除 Naive UI 装饰性 `.n-icon`；未关闭全局规则。首轮发现并修复首改密 Modal 可访问名称、DataPanel 计数文字对比度和表单标签文字颜色。

## 4. 独立复核入口

候选身份冻结在仓库外：

`C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws6-stage-candidate-2026-08-11.json`

建议在该 manifest 对应快照上自然退出地执行：

```powershell
npm --prefix frontend run lint
npm --prefix frontend run type-check
npm --prefix frontend run test:unit
npm --prefix frontend run test:csp-contract
npm --prefix frontend run test:auth-logout-contract
npm --prefix frontend run test:dashboard-latest-request
npm --prefix frontend run test:ws4-video-auth-state-contract
npm --prefix frontend run build
npm --prefix frontend run test:e2e
git diff --check
```

独立复核前状态保持 `INDEPENDENT_REVIEW_PENDING`；只有用户独立复核正式 PASS 后，才可更新 WS-6 为 scoped PASS 并进入 WS-7。
