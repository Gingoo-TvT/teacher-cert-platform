# WS-12 前端 bundle 拆分与体积预算提交材料（2026-08-21）

> **后续状态**：本材料对应的 fingerprint `21f784c3...bef4e` 已被独立报告裁定
> `CHANGES_REQUESTED（0C/0H/1M/0L）`。本文件保留为首轮提交历史；当前整改状态与复核请求以
> `ws-12-remediation-submission-2026-08-21.md` 为准。
>
> **材料性质**：开发方提交材料，不是独立复核报告，也不签发 WS-12 PASS。
> **当时状态**：`LOCAL_IMPLEMENTATION_COMPLETE / INDEPENDENT_REVIEW_PENDING`。

## 1. 前置门槛

- WS-11 R2 正式独立增量报告绑定 fingerprint
  `85606aed4cd42b28dc66245a5013bf28263657d6e46eda3cff49a334c129161f`，报告 SHA-256
  `38c9896dff8ca133cff994140bb04c748c7c8797e7c7d9fb7e6c5e30f409d50a`，裁定
  `INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/0L）`。
- 该结论只关闭 WS-11 并放行 WS-12，不等于项目发布 GO。

## 2. 本轮范围

- 保持 Dashboard、统计报表的路由级懒加载，并把 `ChartBox` 改成第二层异步组件边界。
- ECharts 从整包引入改为按需注册 Bar/Pie、Grid/Legend/Tooltip、Canvas renderer 与必要 feature。
- 移除 Naive UI 全局插件，使用自动按需导入；生成的组件声明纳入类型检查。
- Vite 输出 manifest；CI 对真实产物计算入口、登录、普通管理、charts 与总体 JS 的 gzip 依赖闭包，超预算失败。
- 新增预算正常路径、强制 1-byte 超预算和 charts 泄漏到登录路径的确定性反例。
- 修正按需导入揭示的通知中心无效 `n-segmented`，最小替换为 Naive UI 真实存在的 radio 组件并覆盖运行期交互。

未改后端、业务 API、数据库、Flyway、权限编码或业务规则；未做全站 UI 重写、依赖大版本升级或额外安全框架。

## 3. 产物预算结果

| 闭包 | 实测 gzip | 预算 | 结果 |
|---|---:|---:|---|
| 应用入口 | 119.6 KiB | 135 KiB | PASS |
| 登录 | 148.0 KiB | 210 KiB | PASS |
| 普通管理 | 291.6 KiB | 430 KiB | PASS |
| charts | 172.5 KiB | 190 KiB | PASS |
| 全部 JS | 662.6 KiB | 850 KiB | PASS |

登录与普通管理关键路径均不包含 charts/echarts；产物不存在单一 `naive-*` 整包 chunk。

## 4. 开发方门禁

| 门禁 | 结果 |
|---|---|
| frontend lint | PASS |
| frontend type-check | PASS |
| tests/config type-check | PASS |
| production build | PASS |
| Vitest | 4 files / 16 tests PASS |
| CSP / logout / Dashboard latest-request / WS-4 video-auth / WS-7 supply-chain contracts | PASS |
| bundle budget 正例 | PASS |
| 强制超预算、charts 登录路径泄漏反例 | 均按预期失败，测试 PASS |
| Playwright 产品冒烟 | 6/6 PASS |
| `git diff --check` | PASS |

可选 `test:ws4-gap-gate` 依赖人工预先启动的 18099 服务；本轮按项目规则未由 exec 启动常驻服务，因此不把该非
WS-12/CI 门禁记作已执行。正式 WS-6 产品冒烟由 Playwright 自管临时服务并已自然退出，6/6 通过。

## 5. 候选身份与复核请求

- 本文及全部治理材料写入完成后，使用 `scripts/candidate_source_manifest.py` 生成仓库外最终 manifest；该 manifest
  是本轮唯一权威候选身份。生成后不再修改候选字节。
- 请独立复核者按最终 manifest 逐字节 verify，并独立重跑 build、预算检查、预算反例与 6 项产品冒烟。
- 请重点确认：登录/普通管理闭包不含 charts/echarts；图表页运行时确实加载 `charts-*` 且渲染 canvas；通知中心
  未读筛选仍会发出 `read=false`；CI 的超预算与泄漏反例不能假绿。
- 独立报告明确 PASS 前，WS-12 保持 `INDEPENDENT_REVIEW_PENDING`，WS-14 不放行，项目不构成发布 GO。
