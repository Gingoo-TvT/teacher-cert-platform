# 阶段文档体系（docs/）

> 配套 `../plan.md`（总体方案 + §15 增补）与 `../tasks.md`（114 原子任务）。
> 本目录把 15 个 Phase 各自展开为**独立的详细设计 + 验收文档**，codex 每次领取一个 `phase-NN-*.md` 即可闭环开发与自检。

## 1. 文档体系与关系
| 文件 | 作用 |
|---|---|
| `../AGENTS.md` | **开工前必读**：红线、代码/Git/测试规范、执行流程、进度与日志规范 |
| `../plan.md` | 总体方案、数据模型、业务规则、§15 设计增补（**权威规格**） |
| `../tasks.md` | 全量原子任务（T-001~T-114）、依赖、关键路径 |
| `docs/phase-NN-*.md` | 每阶段：目标/数据库/接口/逻辑/前端/**详细验收**/**测试用例**/DoD |
| `docs/待确认事项确认单.md` | 交学校书面确认的开放项（含证书序列作用域） |

冲突时优先级（详见 `../AGENTS.md`）：**规范类**（流程/代码/Git/测试）以 AGENTS 为准；**规格类** plan §15 > plan 正文 > phase 文档 > tasks.md；若 phase 文档与 plan §15 矛盾，以 plan §15 为准并修正 phase 文档。

## 2. 全局完成定义（Definition of Done，所有任务通用）
一个任务/阶段"完成"必须同时满足：
1. 编译通过，无新增 lint 错误（后端 Spotless/Checkstyle、前端 ESLint）。
2. 单元测试覆盖核心规则；集成测试覆盖主接口；**关键校验规则与状态流转必须有反例用例**。
3. Swagger/Knife4j 文档同步更新，接口可在 `/doc.html` 调通。
4. 数据库变更走 Flyway 版本脚本（不手改库），脚本可重复执行（幂等种子用 `INSERT ... ON DUPLICATE` 或先判存在）。
5. 关键写操作（审核/状态流转/作废/导出）落 `audit_log`。
6. 本阶段"验收清单"逐条勾选通过；涉及的 `AT-xx` 自测记录归档。
7. 不破坏既有阶段的回归用例。

## 3. 测试与验收规范
- **后端**：JUnit5 + Mockito + Testcontainers（MySQL/Redis/MinIO）；命名 `XxxServiceTest`、`XxxControllerIT`。覆盖率目标：核心校验/状态/编号/导入导出 ≥ 80% 行覆盖。
- **前端**：Vitest 组件单测 + 关键页面 E2E（Playwright，可选）。
- **验收用例**：每条以 `given/when/then` 描述，必含**正例 + 反例 + 边界**。验收勾选项以 `- [ ]` 列出，验收时逐条置 `- [x]` 并附证据（截图/日志/测试名）。
- **AT 验收**：14 条验收标准（plan §10）在对应 Phase 内完成首验，Phase 14 做整体复验。

## 4. 验收标准追溯矩阵（AT-01 ~ AT-14 → 阶段）
| 验收 | 内容 | 首验阶段 | 复验 |
|---|---|---|---|
| AT-01 | 文本字段导入导出一致（无科学计数/日期序列/前导零丢失） | Phase 3（采集）/ **Phase 10**（导入导出） | Phase 14（WPS/Excel 兼容） |
| AT-02 | 标准导出 A–Z 26 列、顺序名称一致、修复 H 列表头 | **Phase 10** | Phase 14 |
| AT-03 | 证件号码校验 + 出生日期一致拦截 | **Phase 3** | Phase 10（预校验） |
| AT-04 | 教育类研究生专业代码 0401/0451/0453 校验 | **Phase 4** | Phase 10 |
| AT-05 | 任教学段→学科联动、禁自由填写 | Phase 1（库）/ **Phase 4** | Phase 10 |
| AT-06 | 四类材料未全复审通过不可"合格" | **Phase 5** | Phase 9（前置） |
| AT-07 | 任选一/多科免考、每科独立佐证 + 二级审核 | **Phase 6** | Phase 9 |
| AT-08 | 视频 ≥2 教师独立评审、分差超阈值进复评/仲裁 | **Phase 7** | Phase 9 |
| AT-09 | 满足全部前置条件才允许生成证书 | **Phase 9** | Phase 14 E2E |
| AT-10 | 证书编号 18 位自动生成、同年同校（学段）连续不重 | **Phase 9** | Phase 10（导出） |
| AT-11 | 有效期上半年+3年6/30、下半年+3年12/31 | **Phase 9** | Phase 10 |
| AT-12 | 全审核操作记录操作人/时间/意见/前后状态 | Phase 0（切面）/ **Phase 13**（查询） | 各业务 Phase |
| AT-13 | 学生仅本人、学院仅授权范围、敏感导出鉴权 | **Phase 2** | 各业务 Phase |
| AT-14 | 导入失败生成 行号/字段/错误值/原因/建议 异常报告 | **Phase 10** | Phase 14 |

## 5. 阶段索引
| Phase | 名称 | 模块 | 优先级 | 任务 | 关键验收 | 文档 |
|---|---|---|---|---|---|---|
| 0 | 工程脚手架与基础设施 | — | P0 | T-001~010 | 启动/审计切面 | `phase-00-脚手架.md` |
| 1 | 基础数据与字典 | M01 | P0 | T-011~022 | AT-05 基础 | `phase-01-字典与标准数据.md` |
| 2 | 账号角色权限 | M02 | P0 | T-023~029 | AT-13 | `phase-02-认证与权限.md` |
| 3 | 基本信息 | M03 | P0 | T-030~038 | AT-01/AT-03 | `phase-03-基本信息.md` |
| 4 | 专业培养信息 | M04 | P0 | T-039~043 | AT-04/AT-05 | `phase-04-专业培养信息.md` |
| 5 | 文件 + 过程性材料 | M05 | P0 | T-044~051 | AT-06 | `phase-05-文件与过程性材料.md` |
| 6 | 免考 | M06 | P0 | T-052~056 | AT-07 | `phase-06-免考.md` |
| 7 | 视频评审 | M07 | P0 | T-057~067 | AT-08 | `phase-07-视频评审.md` |
| 8 | 测试结果 | M08 | P0 | T-068~071 | 证书前置联动 | `phase-08-测试结果.md` |
| 9 | 证书 | M09 | P0 | T-072~079 | AT-09/10/11 | `phase-09-证书.md` |
| 10 | 导入导出与预校验 | M10 | P0 | T-080~091 | AT-01/02/14 | `phase-10-导入导出与预校验.md` |
| 11 | 统计报表 | M11 | P1 | T-092~100 | 统计口径一致 | `phase-11-统计报表.md` |
| 12 | 通知 | M12 | P1 | T-101~103 | 四类触发 | `phase-12-通知.md` |
| 13 | 系统管理与审计 | M13 | P0 | T-104~108 | AT-12 | `phase-13-系统管理与审计.md` |
| 14 | 非功能/部署/验收 | M14+收口 | P2 | T-109~114 | 全部 AT 复验 | `phase-14-非功能部署验收.md` |

## 6. 系统参数清单（`sys_param`，贯穿各阶段，Phase 13 统一管理）
| param_key | 默认 | 说明 | 引用阶段 |
|---|---|---|---|
| `cert.seq.scope` | `SCHOOL_YEAR_SEGMENT` | 证书序列作用域 | Phase 9 |
| `cert.province.code` | `44` | 省级行政区划代码 | Phase 9 |
| `cert.school.code` | `10588` | 高校代码 | Phase 9 |
| `video.passLine` | `60` | 视频合格线 | Phase 7 |
| `video.diffThreshold` | `12` | 两评委分差阈值 | Phase 7 |
| `video.reviewerCount` | `2` | 评审教师数 | Phase 7 |
| `video.durationTolerance` | `60`(秒) | 时长容差 | Phase 7 |
| `video.arbitrate.mode` | `thirdExpert` | 复评模式 | Phase 7 |
| `video.required` | `true` | 视频是否必过才能发证 | Phase 9 |
| `file.maxSize.video` | `2147483648`(2GB) | 视频单文件上限 | Phase 7 |
| `file.maxSize.material` | `52428800`(50MB) | 材料单附件上限 | Phase 5 |
| `review.return.target` | `FIRST_REVIEW` | 复审退回目标态 | Phase 3/5/6 |
| `validate.name.mode` | `loose` | 姓名校验模式（确认单#15 放宽，V6 落地；原默认 strict） | Phase 3 |
| `validate.idcard.checksum` | `false` | 身份证校验码开关 | Phase 3 |
| `student.autoCreateAccount` | `false` | 是否导入即创建学生账号（WS-2 安全默认关闭） | Phase 3 |
| `student.defaultPwd` | `random` | 随机占位哈希时账号停用待重置；禁止 PII 派生 | Phase 3 |
| `current_assessment_year` | 当前年 | 当前考核年度 | 全局 |

> 以上默认值若学校另有口径，改 `sys_param` 即可，无需改代码。开放项见 `待确认事项确认单.md`（**20 项已于 2026-06-14 确认**；原业务取值变更 `validate.name.mode`→`loose`，见 `V6__confirmed_params.sql`；#17/#18 后由 WS-2 安全整改取代为 `false/random`，见 `V27__ws02_credential_hardening_defaults.sql`）。
