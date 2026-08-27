# WS-12 gzip 口径整改提交材料（2026-08-21）

> **材料性质**：开发方增量提交材料，不是独立复核报告，也不签发 WS-12 PASS。
> **当前状态**：`LOCAL_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REREVIEW_PENDING`。

## 1. 正式退回项

- 首轮正式报告绑定 fingerprint
  `21f784c39023d6b7dae4c46b94ad3c78f5a01ea4208e80ebcbbd7a860ebbef4e`、HEAD
  `aa3509a19151e2caad03014324b9b36e98bc0f05`，结论为
  `CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 0 Low）`。
- 报告路径：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff958-9b0d-72f1-b15b-2487f4fd3e2b\ws12-bundle-split-independent-review-20260821-21f784c3-local\review.md`；
  SHA-256 为 `3d39b2660f6731fbd5e3d4677de7ae6fc86d0906ef362a2a4b79051f3ea5564e`。
- 报告目录只有 `review.md`，没有 `SHA256SUMS`；上述摘要是本次直接复算结果，不声称存在独立证据包闭包。
- 唯一 Medium：预算脚本使用 zlib 默认 level 6，生产 Nginx 未显式设置压缩等级而使用默认 level 1。同一
  charts 文件在 level 1 为 199,104 B，已超过 194,560 B 预算，但 CI 按 level 6 得到 176,597 B 并通过。

## 2. 最小整改

1. `frontend/nginx.conf` 显式配置 `gzip_comp_level 6;`。
2. `frontend/scripts/check-bundle-budget.mjs` 定义并使用 `GZIP_LEVEL = 6`，调用
   `gzipSync(bytes, { level: GZIP_LEVEL })`，不再依赖库默认值。
3. `frontend/scripts/test-bundle-budget.mjs` 读取生产 Nginx 配置，要求存在单一显式压缩等级且与预算常量相等。

未修改图表功能、路由、API、后端、数据库、权限或业务规则，也未增加预压缩管线或其它架构。

## 3. 开发方门禁

| 门禁 | 结果 |
|---|---|
| frontend lint | PASS |
| app / tests-config type-check | PASS / PASS |
| production build | PASS |
| Vitest | 4 files / 16 tests PASS |
| Playwright 产品冒烟 | 6/6 PASS |
| bundle budget 正例与合同 | PASS |
| 强制 1-byte 超预算、charts 登录路径泄漏反例 | 均被拒绝，测试 PASS |
| `git diff --check` | PASS |

新构建 gzip level 6 闭包：入口 119.6 KiB、登录 148.0 KiB、普通管理 291.6 KiB、charts 172.5 KiB、
全部 JavaScript 662.6 KiB。登录与普通管理关键路径仍不包含 charts/echarts。

## 4. 候选身份与复核请求

- 本文与治理材料全部写定后，使用 `scripts/candidate_source_manifest.py` 在仓库外生成唯一整改 manifest；生成后
  不再修改候选字节。最终 fingerprint 以该外置 manifest 为准。
- 请复核者先 verify 新 manifest，再重跑 build、`bundle:check`、`test:bundle-budget`，并确认生产 Nginx 与预算
  计算对同一 charts 文件使用相同 level 6。
- 本轮只请求增量复核首轮 1 个 Medium。独立报告明确 PASS 前，不把该 finding 标记 CLOSED，不放行 WS-14，
  不构成项目发布 GO。
