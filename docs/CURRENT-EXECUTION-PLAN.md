# 当前统一执行计划（2026-07-23）

> 本文件是项目**当前工作队列与阶段复核状态的唯一入口**。业务规格仍按 `AGENTS.md` 规定的优先级执行：`plan.md §15` > `plan.md` 正文 > `docs/phase-NN-*` > `tasks.md`；本文件不改写业务规则，只合并分散计划、复核证据和后续顺序。
>
> 历史计划保留作决策与实现追溯，其正文中的“待复核/未合并/下一步”等状态均视为当时快照，不再单独维护。状态更新只写入本文件、`PROGRESS.md` 和 `DEVLOG.md`。

## 1. 当前基线与本轮结论

- 当前分支：`feature/ws03-minio-presign`；基线 `main=e4f8228`，当前提交链在其上包含 WS-1、WS-2、WS-10、WS-13、WS-3。
- Claude 当前不可用；用户于 2026-07-23 明确授权 Codex 作为本轮独立复核者。复核者未采信实现自报，重新读码并在隔离真实依赖环境重跑反例和全量门禁；该应急授权只适用于本轮记录，不自动改写 `REVIEW-GATE` 的长期角色约定。
- 原复核隔离空库基线：`mvn -B -ntp clean verify` 通过，Surefire **121/121**、Failsafe **144/144**，合计 **265/265**，Flyway V1–V28 与 `R__testseed` 成功。
- WS-3 第二轮整改独立门禁：全新数据卷执行 Flyway V1–V29 成功，Surefire **121/121**、Failsafe **150/150**，合计 **271/271**；Phase 7 为 **29/29**；前端 type-check/build 通过。独立复核确认时间线、fast-hit、review 行锁和 V29 已闭环，但静态不变量仍发现 3 High / 3 Medium。
- WS-3 第三轮整改已实现并自测：全新数据卷 Flyway V1–V30 成功，Surefire **125/125**、Failsafe **152/152**，合计 **277/277**；Phase 7 **31/31**；前端 type-check/build、生产 Compose config 与 fat-JAR worker 通过。3 High / 3 Medium 均有对应实现和确定性反例，但当前只置“待独立重核”。
- 前端：`npm --prefix frontend run type-check`、`npm --prefix frontend run build` 通过；构建仍有 `echarts`/`naive` 大 chunk 警告。
- 编排：`docker-compose.dev.yml` 与生产 `docker-compose.yml + .env.example` 均通过 `config --quiet`。
- 当前 WS 链判定：**WS-1、WS-2、WS-10、WS-13 PASS；WS-3 第三轮整改完成、待独立重核**。第二轮正式结论仍见 `reviews/ws-03-second-remediation-rereview-2026-07-23.md`；新独立报告 PASS 前不得自行覆盖其 CHANGES REQUESTED。
- 缺失阶段账已完成独立复核：**Phase 29、35b、36–38、40、43、45–46、48–52 PASS；Phase 0、39、41、42、44、47、53 CHANGES REQUESTED**。阶段总审计见 `reviews/phase-gap-audit-2026-07-23.md`。
- 全项目仍为 **CHANGES REQUESTED**：WS-3 第三轮等待独立重核；此外仍有学院删除并发孤儿、备份不可按手册恢复、导入/回滚竞态、Phase 44 完成声明与实现不一致、MinIO 生命周期失败覆盖、Phase 53 demo 对象/元数据升级不一致及 Phase 0 验收基线欠账。
- 本轮是“逐阶段进度真实性 + 测试真实性 + 发布门禁”的复核，不替代最后的全量安全、业务规则、数据一致性、性能与运行期审计。

## 2. 逐阶段复核矩阵

状态口径：

- **✅ 已复核**：存在阶段/WP 复核报告且结论 PASS。
- **⚠️ 证据欠账**：实现或历史状态存在，但缺正式报告、验收勾选或独立对抗性证据。
- **🟦 待复核**：当前工作包自测完成，尚未由独立复核者 PASS。

| 阶段 | 实现/合并状态 | 正式复核证据 | 本轮复核结论 |
|---|---|---|---|
| Phase 0 | 原始任务 10/10 | `reviews/phase-00-review.md` | ❌ 复核退回：验收基线漂移、lint/运行证据未闭环 |
| Phase 1 | 已完成 | `reviews/phase-01-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 2 | 已完成 | `reviews/phase-02-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 3 | 已完成 | `reviews/phase-03-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 4 | 已完成 | `reviews/phase-04-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 5 | 已完成 | `reviews/phase-05-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 6 | 已完成 | `reviews/phase-06-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 7 | 历史阶段已完成；当前叠加 WS-3 第三轮增量 | `reviews/phase-07-review.md` 历史 PASS；`reviews/ws-03-second-remediation-rereview-2026-07-23.md` | 🟦 第三轮整改待独立重核；历史阶段 PASS 结论保留 |
| Phase 8 | 已完成 | `reviews/phase-08-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 9 | 已完成 | `reviews/phase-09-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 10 | 已完成 | `reviews/phase-10-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 11 | 已完成 | `reviews/phase-11-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 12 | 已完成 | `reviews/phase-12-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 13 | 已完成 | `reviews/phase-13-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 14 | 已完成 | `reviews/phase-14-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 15 | WP-A 已完成 | `reviews/wp-a-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 16 | WP-B 已完成 | `reviews/wp-b-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 17 | WP-C 已完成 | `reviews/wp-c-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 18 | WP-D 已完成 | `reviews/wp-d-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 19 | 已完成 | `reviews/phase-19-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 20 | 已完成 | `reviews/phase-20-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 21 | 已完成 | `reviews/phase-21-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 22 | 已完成 | `reviews/phase-22-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 23 | 已完成 | `reviews/phase-23-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 24 | 已完成 | `reviews/phase-24-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 25 | 已完成 | `reviews/phase-25-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 26 | 已完成 | `reviews/phase-26-review.md` PASS | ✅ 已复核；`PROGRESS.md` 原状态滞后，本轮纠正 |
| Phase 27 | 已完成 | `reviews/phase-27-review.md` PASS | ✅ 已复核；当前全量回归通过 |
| Phase 28 | 已完成 | `reviews/phase-28-review.md` PASS | ✅ 已复核；`PROGRESS.md` 原状态滞后，本轮纠正 |
| Phase 29 | 已合并（提交 `8beb8e6`） | `reviews/phase-29-review.md` PASS | ✅ 已复核；最终审计补浏览器截图 |
| Phase 30 | 已完成 | `reviews/phase-30-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 31 | 已完成 | `reviews/phase-31-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 32 | 已完成 | `reviews/phase-32-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 33 | 已完成 | `reviews/phase-33-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 34 | 已完成 | `reviews/phase-34-review.md` 有完成结论与证据 | ✅ 按既有复核记录认可；最终审计复查报告格式一致性 |
| Phase 35 | 已完成 | `reviews/phase-35-review.md` PASS | ✅ 已复核；前端构建通过 |
| Phase 35b | 已合并（实现 `e688f9a`，文档 `8d3234f`） | `reviews/phase-35b-review.md` PASS | ✅ 已复核；性能 trace 留最终审计 |
| Phase 36 | 已合并 | `reviews/phase-36-review.md` PASS | ✅ 已复核 |
| Phase 37 | 已合并（含 37a/37b/37c） | `reviews/phase-37-review.md` PASS | ✅ 已复核；线上 TLS 仍属 WS-5 |
| Phase 38 | 已合并 | `reviews/phase-38-review.md` PASS | ✅ 已复核；容量压测留最终审计 |
| Phase 39 | 已合并 | `reviews/phase-39-review.md` | ❌ 复核退回：学院删除与子记录创建竞态 |
| Phase 40 | 已合并 | `reviews/phase-40-review.md` PASS | ✅ 已复核 |
| Phase 41 | 已合并（含 41.1/41.2/41.3） | `reviews/phase-41-review.md` | ❌ 复核退回：备份不可按手册恢复；初始化 SQL 拼接 |
| Phase 42 | 已合并（含 42.1–42.4） | `reviews/phase-42-review.md` | ❌ 复核退回：confirm/rollback 并发不一致 |
| Phase 43 | 已合并（含 43.1–43.4） | `reviews/phase-43-review.md` PASS | ✅ 已复核 |
| Phase 44 | 已合并（含 44a–44f） | `reviews/phase-44-review.md` | ❌ 复核退回：通知批量交付已撤销；缓存逐出竞态 |
| Phase 45 | 已合并 | `reviews/phase-45-review.md` PASS | ✅ 已复核 |
| Phase 46 | 已合并 | `reviews/phase-46-review.md` PASS | ✅ 已复核 |
| Phase 47 | 已合并 | `reviews/phase-47-review.md` | ❌ 复核退回：生命周期读取失败可能覆盖其它规则 |
| Phase 48 | 已合并 | `reviews/phase-48-review.md` PASS | ✅ 已复核 |
| Phase 49 | 已合并 | `reviews/phase-49-review.md` PASS | ✅ 已复核 |
| Phase 50 | 已合并 | `reviews/phase-50-review.md` PASS | ✅ 已复核 |
| Phase 51 | 已合并 | `reviews/phase-51-review.md` PASS | ✅ 已复核 |
| Phase 52 | 已合并 | `reviews/phase-52-review.md` PASS | ✅ 已复核 |
| Phase 53 | 已合并 | `reviews/phase-53-review.md`；WS-3 重核补充证据 | ❌ 复核退回：新样本已可播放，但 demo SQL 仍写 528B/旧摘要，旧对象升级不会替换 |

## 3. 当前审计整改工作包

| 工作包 | 当前提交 | 自测 | 状态/放行条件 |
|---|---|---|---|
| WS-1 IT fixture 隔离 | `25be4bd` | demo 常驻双跑 27/27；纳入 265/265 | ✅ 独立复核 PASS；`reviews/ws-01-review-2026-07-23.md` |
| WS-2 凭据硬化 | `9bdee8f` | 目标单测 105/105；专项真实依赖组合 50/50；纳入 265/265 | ✅ 独立复核 PASS；`reviews/ws-02-review-2026-07-23.md` |
| WS-10 profile fail-fast | `32905a5` | 启动型单测、Compose；纳入 265/265 | ✅ 独立复核 PASS；`reviews/ws-10-review-2026-07-23.md` |
| WS-13 RBAC 授权天花板 | `acfc3b6` | WS13 IT 4/4、授权矩阵单测；纳入 265/265 | ✅ 独立复核 PASS；`reviews/ws-13-review-2026-07-23.md` |
| WS-3 MinIO 预签名直传 | 原实现 `338bc91`；首批整改 `ca400f1`；第二轮整改 `32da735`；第三轮为本次提交 | Phase 7 31/31；全新数据卷 V1–V30 全量 277/277；前端/Compose/fat-JAR worker 通过 | 🟦 第三轮整改完成、待独立重核；Phase 53 的 demo High 仍留 U-003 |

统一风险证据与修复顺序见 `reviews/current-ws-chain-audit-2026-07-23.md`。

## 4. 统一优先队列

### P0：复核与发布门禁

1. **U-001 CI 可复现性（WS-3 Major-3）—✅ 独立重核 PASS**：后端 job 的固定版本 MinIO、健康检查、桶初始化与前端 `npm run type-check` 已静态核对；独立 CI 等价环境全量 266/266、前端 type-check/build 通过。WS-6 仍负责 ESLint、Vitest、Playwright。
2. **U-002 WS-3 退回整改—🟦 第三轮整改完成、待独立重核**：已补 `SERVER_CHUNK` 可接管恢复点、累计磁盘准入、可强杀媒体 worker、direct/merge 续租与 fencing、生产 Compose/.env/专用临时卷，以及容量/TTL/包数/多轨/三阶段崩溃恢复反例。第二轮报告仍为 `reviews/ws-03-second-remediation-rereview-2026-07-23.md`，新独立报告 PASS 前不改判。
3. **U-003 阶段退回整改**：按依赖/风险顺序修 Phase 42 导入回滚状态机 → Phase 39 父子记录串行化 → Phase 41 可恢复备份与安全初始化 → Phase 47 生命周期 fail-closed → Phase 53 demo 真实元数据/旧对象 reconcile + 浏览器播放 → Phase 44 状态诚实化/缓存事务后逐出。每项只修报告中的 Major，并补对应反例。
4. **U-004 Phase 0 复核退回整改**：逐项处理 `docs/phase-00-脚手架.md` 的 10 个验收项；被现架构取代的旧要求要记录替代依据，补 lint/Swagger/预签名过期等可重复证据后重交。

### P1：当前产品需求与上线安全

5. **WS-4 全站 UI 优化**：先完成 D0 设计基线与代表页面验收，再铺开全站；不得只做换色/圆角。
6. **WS-5 传输与会话安全**：TLS/HSTS/CSP 与 refresh token HttpOnly；公网域名/证书属于外部输入，未提供时先完成可验证配置与部署契约。
7. **WS-6 前端测试/lint/a11y 门禁**：先让文档承诺与仓库现实一致，再引入可持续的最小门禁。
8. **WS-7 容器/CI 供应链硬化**：非 root、镜像 digest、SBOM/依赖审计。

### P2/P3：结构与运维债务

9. **WS-8** 身份证号应用层加密 + HMAC 唯一键。
10. **WS-9** 拆分 `ExchangeServiceImpl` / `VideoReviewServiceImpl`。
11. **WS-11** health liveness/readiness 与指标。
12. **WS-12** 前端 bundle 拆分与体积预算。
13. **WS-14** 备份生命周期与归档；可播放 demo 视频已提升到 U-003 的 Phase 53 发布整改。
14. **WS-15（可选）** 审计异步重试，仅在不破坏事务/顺序/可靠性的方案有实验证据时实施。

## 5. 分散计划合并映射

| 原计划 | 覆盖范围 | 现行处理 |
|---|---|---|
| `plan.md` / `tasks.md` / `docs/phase-NN-*` | 原始业务规格与 Phase 0–14 | 保持规格权威，不作为当前排期账本 |
| `refactor-ui-rbac-plan.md` | Phase 15–24 | 已完成；保留历史与验收依据 |
| `frontend-quality-plan.md` | Phase 30–33 | 已完成；保留历史与 UI 质量约束 |
| `frontend-fix-plan.md` | Phase 35 | 已完成；保留用户验收缺陷追溯 |
| `launch-readiness-plan.md` | Phase 36–53 与上线债务 | 实现历史保留；开放项迁入本文件 §4 |
| `remaining-p0-execution-plan.md` | Phase 40+ P0/近 P0 | 已实施内容保留；未闭环复核迁入 U-003 |
| `audit-remediation-plan.md` | WS-1–WS-15 + 两条新需求 | 当前 WS 状态与剩余工作迁入本文件 §3/§4 |

## 6. 更新纪律

1. 新任务只在本文件登记编号、优先级、依赖、验收与状态；不要再创建新的顶层“计划”文档。
2. 业务规格变化仍必须先改 `plan.md`/对应 phase 文档，并按 R10 写 `DEVLOG.md`；本文件只同步执行项。
3. 阶段实现完成只能置“待复核”；独立复核报告 PASS 后，才能在本文件与 `PROGRESS.md` 同步置“✅ 已复核”。
4. 每次复核同时记录：代码提交、测试命令/计数、运行环境、反例、未覆盖项、结论。
5. 最终全量审计前，U-001–U-004 必须全部闭环；U-001 已独立重核 PASS，U-002 第三轮待独立重核，U-003/U-004 均已有逐阶段退回报告。未闭环前不得宣称稳定发布就绪。
