# Phase 0 独立复核报告

- 复核日期：2026-07-23
- 复核者：Codex（Claude 不可用，用户明确授权临时承担独立复核）
- 结论：**CHANGES REQUESTED（证据/发布闸门）**
- 范围：`docs/phase-00-脚手架.md`、工程骨架、基础配置与当前 CI

## 独立证据

- fresh schema 上 Flyway V1–V28 成功，`mvn clean verify` 265/265；前端 type-check/build 与 dev/prod Compose 解析通过。
- 当前轮未由 headless exec 启动常驻应用，遵守 `AGENTS.md §6.1`；因此不把 `/doc.html`、预签名过期和前端交互写成新鲜活体证据。

## D1–D11

| 维度 | 结论 | 证据/说明 |
|---|---|---|
| D1–D2 规格/清单 | 不通过 | 文档仍写 pnpm、mock 登录和 Spotless/Checkstyle/ESLint，十项清单全部未勾选；当前工程已改为 npm 与真实认证。 |
| D3–D6 AT/红线/质量/安全 | 通过（当前态） | 后续正式阶段报告与 265 条回归覆盖统一响应、审计、数据范围、文件和鉴权。 |
| D7–D10 构建/测试/数据库/回归 | 部分通过 | 构建、Flyway、真实依赖回归通过；缺本轮 Swagger/过期 URL/浏览器活体，CI 也没有所列 lint。 |
| D11 文档/进度 | 不通过 | 权威验收项未按当前架构修订，不能追溯哪些条款被正式替代。 |

## 退回项

1. 对齐 Phase 0 验收基线：npm/真实认证若替代 pnpm/mock，应在规格和 DEVLOG 明确记录依据。
2. 落实或正式修订后端/前端 lint 门禁，并在 CI 执行。
3. 补 `/doc.html`、统一异常、审计、数据范围、预签名与过期反例的可重复运行证据后再交。

## 判定

当前工程骨架可构建、可迁移且回归绿，但 Phase 0 的独立验收链没有闭环，不予 PASS。完整发现见 `phase-gap-audit-2026-07-23.md` 的 PG-M1。
