# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform
**Audit mode:** documentation, testing-authenticity, release
**Date:** 2026-07-22
**Reviewer:** Codex / GPT-5

---

## 1. Executive Summary

本轮对 Phase 0–53、Phase 35b 以及当前 WS-1/2/3/10/13 做了逐阶段进度真实性复核，并把分散计划收口。代码回归结果是扎实的：独立 MySQL schema + Redis + MinIO 环境中，后端 Surefire 121/121、Failsafe 144/144（合计 265/265）全部通过；前端 type-check/build 和两套 Compose config 也通过。Phase 1–28（Phase 0 除外）与 Phase 30–35 有可追溯的正式复核报告，Phase 26/28 的进度账滞后已纠正。

整体判定仍为 **CHANGES REQUESTED（文档/发布闸门）**：GitHub Actions 缺 MinIO，无法复现当前 WS-3 的真实集成测试；Phase 29、35b、36–53 没有符合阶段闸门的标准报告；Phase 0 在进度账中是 10/10 已复核，但 phase 验收清单 10 项仍全未勾选。完整逐阶段矩阵已写入 `docs/CURRENT-EXECUTION-PLAN.md`。本报告不替代最终全量安全、业务规则、数据完整性、性能和运行期审计。

### Score Dashboard

```text
Testing        ███████░░░  7.0  A  后端真实集成回归很强，但测试不自带隔离且前端无行为测试。
Maintainability███████░░░  6.5  B  计划与交接信息丰富，但多份状态源和过时基线造成治理成本。
Release        ██████░░░░  6.0  B  本地构建/编排通过，但 CI 缺 MinIO、逐阶段报告账未闭环。
────────────────────────────────────────────────────────────────────
Focused overall           6.5  B
```

每个维度按 0.0–10.0 评分，分数越高越好。Security、Stability、Performance、Design 未在本次聚焦复核中评分。

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 2 | 2 | 0 |
| Medium | 3 | 3 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **5** | **5** | **0** |

## 2. Project Map

- 后端：Java 17、Spring Boot 3.4.13、Maven 多模块；`platform-boot` 聚合 security/system/business/exchange/statistics/file/common。
- 前端：Vue 3.5 + TypeScript + Vite + Naive UI；当前自动门禁只有 `vue-tsc` 与 production build。
- 持久化与外部依赖：MySQL + Flyway V1–V28、Redis、MinIO；集成测试直接连接外部提供的真实依赖。
- 治理入口：`AGENTS.md`、`plan.md`、`tasks.md`、`docs/phase-NN-*`、`PROGRESS.md`、`DEVLOG.md`、`docs/reviews/*`。
- 当前代码链：`main=e4f8228` 上叠加 WS-1、WS-2、WS-10、WS-13、WS-3；均处于自测完成、待独立复核状态。
- 逐阶段状态：Phase 0–53 每一阶段的报告路径与结论见 `docs/CURRENT-EXECUTION-PLAN.md §2`。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Documentation | High | AGENTS、plan/tasks、HANDOFF、PROGRESS、DEVLOG、Phase 文档、review inventory、分散计划、git history | 未逐字重审所有历史计划正文的业务正确性 |
| Testing Authenticity | High | Surefire/Failsafe 全量实跑、IT 配置、真实 MySQL/Redis/MinIO、frontend scripts、CI workflow | 未做 JaCoCo、mutation testing、浏览器 E2E |
| Release | Medium | CI、Compose config、profile、Flyway、构建与清理流程 | 未在真实 GitHub runner、生产域名/TLS、真实浏览器执行 |

## 3. Top Risks

1. **High — CI 缺 MinIO**：当前后端 job 无法复现依赖真实 MinIO 的 WS-3/Phase5/7/41 集成测试。
2. **High — 阶段复核账断裂**：Phase 29、35b、36–53 已实现或已合并，但没有标准阶段报告。
3. **Medium — Phase 0 状态与清单矛盾**：进度账是已复核，验收清单 10 项全未勾选且部分要求已过时。
4. **Medium — 测试规范高于现实**：文档原称 Testcontainers/Vitest/Playwright，仓库实际为外置依赖 + type-check/build。
5. **Medium — 技术基线过时**：最高规范原声明 Spring Boot 3.2.x、pnpm 与旧依赖，和实际构建清单冲突。

## 4. Detailed Findings

### Finding: CI 缺 MinIO，当前 WS-3 门禁无法在 GitHub Actions 复现

- Severity: High
- Confidence: High
- Category: Testing / Release
- Status: Confirmed
- Affected area: `.github/workflows/ci.yml` backend job；Phase5/7/41 与 WS-3 MinIO 集成测试
- Evidence: `.github/workflows/ci.yml:8-44` 只声明 MySQL、Redis 后执行全量 `mvn verify`；`platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase7VideoReviewIT.java:873` 直接请求真实 MinIO 预签名 URL，Phase5/Phase41 亦注入真实 `MinioClient`。
- Problem: 本地隔离环境绿，但 CI 没有被测依赖，门禁不可复现。
- Why it matters: “本地 265/265”不能推出 pull request 的 CI 绿，发布闸门会在最需要时失效。
- Realistic failure scenario: 当前分支触发 Actions，Spring 上下文或 MinIO 相关用例连接 `localhost:9000` 失败，整批 IT 因环境缺失红灯。
- Minimal fix: backend job 增加 MinIO service、健康检查、测试凭据和桶准备，并显式传入 endpoint。
- Better long-term fix: 用 Testcontainers/测试 harness 自管理 MySQL、Redis、MinIO，使本机与 CI 使用同一测试编排。
- Regression test suggestion: 在无缓存、无预置服务的 runner 上执行 `mvn -B -ntp clean verify`，确认 265/265 及预签名过期反例通过。
- Estimated effort: 0.5–1 天

### Finding: Phase 29、35b、36–53 缺标准阶段复核报告

- Severity: High
- Confidence: High
- Category: Documentation / Release
- Status: Confirmed
- Affected area: `docs/reviews`、`PROGRESS.md`、Phase 29/35b/36–53 提交链
- Evidence: `docs/REVIEW-GATE.md` 要求每 Phase 产出报告且无 Blocker/Major 才 PASS；`docs/reviews` 有 Phase 1–28（缺 29）和 30–35 报告，但无 35b、36–53；git history 存在相应实现提交。
- Problem: “已实现/已合并/当前回归通过”和“独立复核 PASS”被混用。
- Why it matters: 最终审计无法确认各阶段是否真正重跑过关键反例，也无法可靠定位回归责任阶段。
- Realistic failure scenario: 某个已合并阶段存在只在其特定边界触发的问题，全量 happy-path 回归为绿，但进度账误导后续人员跳过该阶段对抗性检查。
- Minimal fix: 优先补 Phase 29、35b、36–53 报告；相邻小阶段可共享一次构建，但每个阶段必须有独立验收项、反例与结论。
- Better long-term fix: 在阶段合并流程中校验 review report、PROGRESS 状态和提交三者一致，缺任一项禁止标 PASS。
- Regression test suggestion: 增加文档门禁脚本，扫描所有已合并 Phase 与 `docs/reviews/phase-NN-review.md` 的一一对应关系。
- Estimated effort: 2–5 天（取决于需重跑的对抗性用例）

### Finding: Phase 0 显示 10/10 已复核，但验收清单 10 项全未勾选

- Severity: Medium
- Confidence: High
- Category: Documentation / Testing
- Status: Confirmed
- Affected area: `PROGRESS.md` Phase 0；`docs/phase-00-脚手架.md` 验收清单
- Evidence: `PROGRESS.md:43-59` 把 Phase 0 标为 10/10 已复核；`docs/phase-00-脚手架.md:54-63` 仍是 10 个 `- [ ]`，且含 pnpm、mock 登录和当前不存在的 lint 门禁。
- Problem: 阶段状态与原始验收证据冲突，部分验收契约已被后续架构替代。
- Why it matters: 直接补勾会伪造证据，保持不动又会让“已复核”缺乏可审计依据。
- Realistic failure scenario: 最终审计按进度账跳过 Phase 0，却在 CI、Swagger、预签名过期或 lint 环节发现基础门禁从未按当前栈验证。
- Minimal fix: 按当前实现逐项复验；过时条目写明替代关系、证据和新的负责工作包后再更新。
- Better long-term fix: 阶段验收清单使用稳定能力描述，工具名/命令放到可更新的运行手册，减少技术迁移造成的历史清单失真。
- Regression test suggestion: 对 Phase 0 建一份当前基线验证记录，至少覆盖 package、Flyway、统一异常、DataScope、审计、文件预签名和前端构建。
- Estimated effort: 0.5–1 天

### Finding: 测试规范描述高于仓库现实

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity / Documentation
- Status: Confirmed
- Affected area: `docs/README.md`、`frontend/package.json`、后端 IT 依赖启动方式
- Evidence: 原 `docs/README.md:28-29` 声明 Testcontainers、Vitest、Playwright；`frontend/package.json:6-11` 只有 dev/build/preview/type-check；`CredentialHardeningIT.java:57` 等测试默认连接宿主服务。
- Problem: 规范让开发者误以为测试天然隔离且前端已有行为级回归，实际并非如此。
- Why it matters: 共享环境污染、CI 缺依赖和前端交互回归容易被低估。
- Realistic failure scenario: 新开发者直接运行 verify，连接到已有数据库并受残留数据影响；UI 交互破坏仍能通过 type-check/build。
- Minimal fix: 先把文档改为真实现状，并明确每次复核必须使用独立 schema/Compose。
- Better long-term fix: WS-6 建立 ESLint/Vitest/Playwright，后端逐步迁移到测试自管理容器和自动清理。
- Regression test suggestion: CI 新增 frontend type-check、lint、最小 Vitest/E2E；后端重复两次从空环境运行 verify。
- Estimated effort: 文档 1 小时；完整门禁 3–7 天

### Finding: 最高规范中的技术栈版本已经过时

- Severity: Medium
- Confidence: High
- Category: Documentation / Maintainability
- Status: Confirmed
- Affected area: `AGENTS.md`、`plan.md`、`HANDOFF.md`
- Evidence: 原规范声明 Spring Boot 3.2.x、pnpm、MyBatis-Plus 3.5.7/Flyway 9.22；`pom.xml:7-11,37-46` 实际为 Spring Boot 3.4.13、MyBatis-Plus 3.5.16，前端存在 npm lockfile。
- Problem: 最高执行规范与真实构建清单冲突。
- Why it matters: 后续代理可能按“锁定版本”回退安全升级，或执行本机不存在的 pnpm，造成返工和错误变更。
- Realistic failure scenario: 新 Phase 依 AGENTS 选用 3.2.x 兼容写法或 pnpm 命令，CI/本机构建与历史升级决策出现分叉。
- Minimal fix: 同步 AGENTS、plan、HANDOFF；以 POM/package-lock 为实际版本真源。
- Better long-term fix: 用脚本从 POM/package.json 生成工具链摘要并在文档门禁中检测漂移。
- Regression test suggestion: 增加轻量脚本比较规范声明与 POM/package manager lockfile；CI 漂移即失败。
- Estimated effort: 1–2 小时

## 5. Documentation Analysis

- Coverage: High
- Inspected evidence: 最高规范、业务计划、任务账、Phase 文档、HANDOFF、PROGRESS、DEVLOG、review inventory、六份历史计划、git history
- Exclusions / limits: 未把所有历史计划的每一条业务判断重新与代码逐行核对；最终全量审计再做业务规格审计

本轮确认项目文档“信息量足，但状态源过多”。新增 `docs/CURRENT-EXECUTION-PLAN.md` 后，业务规格仍由 plan/phase 文档负责，当前排期与复核状态只在统一计划、PROGRESS、DEVLOG 三处同步。六份历史计划加了归档提示，不删除历史依据。技术基线和测试现状已按实际清单纠正。

## 6. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: Maven 全量实跑、Surefire/Failsafe 计数、Flyway 迁移、真实 MySQL/Redis/MinIO、测试配置、CI workflow、frontend scripts
- Exclusions / limits: 未做覆盖率、mutation test、浏览器 E2E、生产域名下预签名/CORS 活体验证

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 后端核心单元测试（121） | High | 未以覆盖率证明所有分支 | Keep，并补 JaCoCo |
| 后端真实集成测试（144） | High（本轮环境） | 依赖外置环境，CI 目前不完整 | Keep，迁移到自管理容器 |
| WS-3 MinIO 预签名反例 | High（本地） | GitHub Actions 无 MinIO | 修 CI 后保留 |
| 前端 type-check/build | Medium | 只能证明类型/打包，不能证明交互 | Keep，增加行为测试 |
| 前端 Vitest/Playwright | None | 交互、权限显隐、表单流程可逃逸 | WS-6 建立 |

### Valuable Tests

- Phase5/7 的真实 MinIO 上传、完成、过期预签名与失败路径。
- Phase2/3/13 的数据范围、凭据并发、RBAC 越权反例。
- 从空 schema 执行 V1–V28 后再跑全量 IT，能同时覆盖迁移与业务回归。

### Suspicious Tests

- 未发现大量“只断言 mock 调用”的伪集成测试；主要真实性问题是环境所有权和共享状态，而非断言本身。
- 存量 IT 默认依赖 localhost，测试失败可能混合“代码失败”和“依赖缺失”，需要 harness 明确区分。

### Missing Tests

- 前端组件、权限显隐、关键表单与完整用户流程没有自动行为测试。
- 当前 CI 缺 MinIO 服务，导致本地有效的 MinIO 测试无法成为稳定门禁。
- 没有仓库级覆盖率报告证明“核心模块 ≥80%”。

## 7. Release Concerns

- Coverage: Medium
- Inspected evidence: GitHub Actions、dev/prod Compose config、profile fail-fast、Flyway V1–V28、构建日志、临时环境清理
- Exclusions / limits: 未在真实 Actions runner、生产 TLS/域名、浏览器、备份恢复环境执行

发布阻断点不是当前代码编译，而是“同一门禁能否在无状态环境重现”。U-001 必须先补 MinIO 和前端 type-check；当前 WS 链只有在独立复核报告 PASS 后才可合并。Phase 29/35b/36–53 的文档债不会直接让服务崩溃，但会破坏最终审计的可追溯性，因此被列为发布前证据门禁。

## 8. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Single Source of Truth | 2 | High/Medium | 多份计划状态、阶段复核账 |
| Reproducible Builds/Tests | 1 | High | CI 缺 MinIO、IT 依赖外置环境 |
| Documentation Matches Reality | 3 | Medium | 测试规范、技术栈、Phase 0 清单 |
| Fail-Fast | 1 | Medium | CI 未在 job 启动前明确检查 MinIO 依赖 |

### Principles Respected

- 阶段闸门明确禁止实现者自评 PASS，已有 Phase 报告大多保留了反例、修补和回归证据。
- 当前代码链使用 profile fail-fast、凭据守卫和 RBAC 天花板等边界防护，并有真实数据库反例覆盖。
- Flyway 从空库迁移和全量回归可以自然退出，符合 headless 验证与可重复清理要求。

## 9. Recommended Fix Order

### Fix Immediately

1. U-001：CI 增加 MinIO、明确测试配置，并在 frontend job 增加 type-check。
2. 对当前 WS-1/2/10/13/3 做独立复核，Blocker/Major 清零后再合并。

### Fix Before Stable Release

1. 补 Phase 0、29、35b、36–53 的阶段复核证据。
2. WS-5 完成 TLS/HSTS/CSP 与会话安全。
3. WS-6 建立前端 lint/Vitest/Playwright 门禁。

### Schedule Later

1. 后端 IT 迁移到 Testcontainers 或统一测试 harness。
2. WS-8/9/11/12/14 与可选 WS-15。

### Ignore for Now

- 既有 echarts/naive chunk warning 不阻塞本次进度复核，但已由 WS-12 管理，不能永久忽略。

## 10. Quick Wins

1. CI frontend job 增加一行 `npm run type-check`。
2. CI 增加 MinIO service 与健康检查，复用现有 dev 测试凭据。
3. 增加脚本核对“已复核 Phase ↔ review report ↔ PROGRESS 状态”。
4. 为 Phase 0 的过时条目写替代映射，避免机械补勾。

## 11. Long-term Refactor Plan

1. **测试环境自管理**：将外置 localhost 依赖迁移到 Testcontainers/统一 harness。收益是本机与 CI 同构；风险是测试耗时和 Windows Docker 稳定性；通过重复空环境 verify 验证。
2. **文档状态自动化**：从 review report 与 Git 元数据生成阶段矩阵，人工只维护结论与证据。风险是历史非标准命名；先从 Phase 29+ 增量启用。
3. **前端行为门禁**：Vitest 覆盖共享组件和权限守卫，Playwright 覆盖登录、学生提交、审核、证书与 MinIO 直传。风险是 E2E 夹具维护；使用独立 schema 和角色化固定数据降低波动。
