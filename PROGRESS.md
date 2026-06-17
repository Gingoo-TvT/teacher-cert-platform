# PROGRESS.md — 开发进度跟踪

> 用法见 `AGENTS.md` §8/§7.5。任务状态：`[ ]` 待开始 · `[~]` 进行中 · `[x]` 完成 · `[!]` 阻塞。
> 阶段状态（汇总表）：待开始 → 进行中 → **待复核**(codex 完工) → **✅ 已复核**(Claude 通过) / **复核退回**。codex 不得自行将阶段置 ✅（见 `docs/REVIEW-GATE.md`）。
> 任务明细见 `tasks.md`，验收见 `docs/phase-NN-*.md`。每次状态变更同时更新下方"阶段汇总"与"AT 跟踪"。

## 当前焦点
- 当前阶段：**Phase 10 导入导出与预校验（T-080~T-091）待开始**；Phase 0~9 已复核通过。
- 阻塞项：无
- 最近更新：2026-06-17（Phase 9 复核通过 PASS·一轮：AT-09 前置缺失清单/AT-10 18位编号并发无重无空(行锁同事务)/AT-11 有效期 全绿，`mvn verify` 40/40；详见 docs/reviews/phase-09-review.md）
- 下一步：启动 Phase 10（导入导出与预校验，AT-02 26列+H表头 / AT-14 异常报告 首验；AT-01 导入导出复验）

## 阶段汇总
| Phase | 名称 | 优先级 | 任务数 | 完成 | 状态 |
|---|---|---|---|---|---|
| 0 | 工程脚手架与基础设施 | P0 | 10 | 10 | ✅ 已复核(自建自验) |
| 1 | 基础数据与字典 | P0 | 12 | 12 | ✅ 已复核(Claude 06-14) |
| 2 | 账号角色权限 | P0 | 7 | 7 | ✅ 已复核(Claude 06-16) |
| 3 | 基本信息 | P0 | 9 | 9 | ✅ 已复核(Claude 06-16) |
| 4 | 专业培养信息 | P0 | 5 | 5 | ✅ 已复核(Claude 06-16) |
| 5 | 文件 + 过程性材料 | P0 | 8 | 8 | ✅ 已复核(Claude 06-17) |
| 6 | 免考 | P0 | 5 | 5 | ✅ 已复核(Claude 06-17) |
| 7 | 视频评审 | P0 | 11 | 11 | ✅ 已复核(Claude 06-17·2轮) |
| 8 | 测试结果 | P0 | 4 | 4 | ✅ 已复核(Claude 06-17) |
| 9 | 证书 | P0 | 8 | 8 | ✅ 已复核(Claude 06-17) |
| 10 | 导入导出与预校验 | P0 | 12 | 0 | 待开始 |
| 11 | 统计报表 | P1 | 9 | 0 | 待开始 |
| 12 | 通知 | P1 | 3 | 0 | 待开始 |
| 13 | 系统管理与审计 | P0 | 5 | 0 | 待开始 |
| 14 | 非功能/部署/验收 | P2+收口 | 6 | 0 | 待开始 |
| | **合计** | | **114** | **79** | |

---

## Phase 0 · 工程脚手架与基础设施 — 10/10 ✅
- [x] T-001 创建 Maven 父工程与 8 子模块骨架
- [x] T-002 platform-common 基础设施（Result/异常/枚举/注解/上下文）
- [x] T-003 platform-boot 启动与全局配置（MyBatis-Plus/Jackson Long→String/数据源）
- [x] T-004 Flyway 接入 + 基础表 V1（已验证 migrate + 17 参数种子）
- [x] T-005 本地依赖 docker-compose（mysql/redis/minio 已起）
- [x] T-006 platform-file MinIO 文件服务（上传/预签名/下载验证通过）
- [x] T-007 审计切面 + 数据权限切面骨架（AuditLogAspect + DataScopeAspect）
- [x] T-008 前端工程脚手架（Vue3+Vite+TS+Naive UI，构建通过）
- [x] T-009 路由权限框架与按钮指令（守卫 + v-perm）
- [x] T-010 CI 与代码规范（GitHub Actions + .editorconfig + .gitattributes）

## Phase 1 · 基础数据与字典 — 12/12 ✅ 已复核（Claude 2026-06-14，PASS · docs/reviews/phase-01-review.md）
- [x] T-011 字典/区划/学科/组织表（分支：`feature/phase01-T011-dict-schema`）
- [x] T-012 字典管理 CRUD + Redis 缓存（分支：`feature/phase01-T012-dict-crud-cache`）
- [x] T-013 字典标准值种子（分支：`feature/phase01-T013-dict-seed`）
- [x] T-014 行政区划三级联动（分支：`feature/phase01-T014-region-service`）
- [x] T-015 行政区划种子（分支：`feature/phase01-T015-region-seed`）
- [x] T-016 任教学科标准库服务 + 导入器（分支：`feature/phase01-T016-subject-service`）
- [x] T-017 任教学科种子（示例库）（分支：`feature/phase01-T017-subject-seed`）
- [x] T-018 学校/学院/专业 + 多培养目标 + 联动配置（分支：`feature/phase01-T018-org-major-config`）
- [x] T-019 字典管理页（分支：`feature/phase01-T019-dict-page`）
- [x] T-020 行政区划维护 + 级联组件（分支：`feature/phase01-T020-region-page`）
- [x] T-021 任教学科库页 + 选择组件（分支：`feature/phase01-T021-subject-page`）
- [x] T-022 学校/学院/专业维护页（分支：`feature/phase01-T022-organization-page`）

## Phase 2 · 账号角色权限 — 7/7 ✅ 已复核（Claude 2026-06-16，PASS · docs/reviews/phase-02-review.md）
> 退回项已按增量修复并重交；本轮补修学院范围 `IN` 表达式缺少括号导致真实 `sys_user` 过滤返回 0 条的问题。`mvn -B -ntp verify` GREEN，Failsafe 自动跑 `Phase2SecurityIT` 2/2 通过。
- [x] T-023 RBAC 表（分支：`feature/phase02-T023-rbac-schema`）
- [x] T-024 认证：登录/JWT/验证码/锁定（分支：`feature/phase02-T024-authentication`）
- [x] T-025 授权：RBAC + 数据范围落地
- [x] T-026 角色与权限点种子（§15.1 矩阵）
- [x] T-027 用户/角色/权限/数据范围 管理接口
- [x] T-028 登录页
- [x] T-029 用户/角色/权限/数据范围管理页

## Phase 3 · 基本信息 — 9/9 ✅ 已复核（Claude 2026-06-16，PASS·2轮 · docs/reviews/phase-03-review.md）
> Phase 3 backlog 已在 Phase 4 附带修复：confirm 不写请求 collegeId，并补 `Phase3StudentIT` 反例防止学生自助换学院。
> 复核退回项已修：create/batchCreate/update 写侧按 `student:edit` 解析数据范围，COLLEGE 账号只能写授权学院，跨学院 create/update 返回 403 且不创建学生/账号；前端 `row-key` 补类型，`npm type-check` 通过。AT-03/AT-01/读侧 AT-13/状态机A/字段锁定/脱敏/留痕 未返工。
> `V9__student.sql` 落地 student 表；新增 platform-business 业务模块并接入 boot；`Phase3StudentIT` 覆盖 AT-03 四类证件/出生日期、AT-01 文本化、脱敏、锁定、简单批量录入/删除、账号开通、student 表读侧数据范围与写侧跨学院拦截。`mvn -B -ntp verify` 通过，待 Claude 复核。
- [x] T-030 student 表（分支：`feature/phase03-T030-student-basic-info`）
- [x] T-031 证件号码校验器（4 类）
- [x] T-032 出生日期一致性校验器
- [x] T-033 姓名格式校验器
- [x] T-034 学生 CRUD + 本人确认/补充 + 字段锁定
- [x] T-035 学生信息两级审核（状态机A）
- [x] T-036 敏感信息脱敏
- [x] T-037 学生信息管理页（教务员）
- [x] T-038 学生本人信息页（学生端）

## Phase 4 · 专业培养信息 — 5/5 ✅ 已复核（Claude 2026-06-16，PASS · docs/reviews/phase-04-review.md）
> Phase 3 confirm backlog 已闭环（confirm 不写 collegeId + 反例）。新 backlog：training/student "可编辑态守卫"统一化（在审/锁定记录禁写非关键字段）。
- [x] T-039 training_profile 表（分支：`feature/phase04-T039-training-profile`）
- [x] T-040 专业代码校验器
- [x] T-041 培养目标→实习地点/学段/学科 联动校验
- [x] T-042 培养信息 CRUD + 多培养目标 + 两级审核
- [x] T-043 专业培养信息表单

## Phase 5 · 文件 + 过程性材料 — 8/8 ✅ 已复核（Claude 2026-06-17，PASS · docs/reviews/phase-05-review.md）
> 可编辑态守卫 backlog 已收口（material + 回填 student/training + 回归反例），Phase3/4 同类 Minor 一并清除。
> `V11__process_material.sql` 落地 process_material 表与材料白名单参数；材料模块复用 platform-file 的 MinIO 上传/预签名/对象读取，process_material 接入 Phase 2 数据权限规则；`Phase5MaterialIT` 覆盖 AT-06、上传限制、通过后禁替换、读写数据范围、批量下载清单。同步收口 student/training/material 可编辑态守卫：仅 DRAFT/FIRST_REJECTED/SECOND_REJECTED 可写，在审/已通过/locked 拒绝。
- [x] T-044 通用附件上传（PDF/图片）+ 大小限制（分支：`feature/phase05-T044-process-material`）
- [x] T-045 process_material 表
- [x] T-046 材料上传/替换/删除（含锁规则）
- [x] T-047 材料两级审核（通过/退回/不通过）
- [x] T-048 过程性考核聚合"合格"判定
- [x] T-049 材料批量下载 + 清单元数据
- [x] T-050 材料上传页（学生端）
- [x] T-051 材料审核页（教务员/副院长）

## Phase 6 · 免考 — 5/5 ✅ 已复核（Claude 2026-06-17，PASS·一轮 · docs/reviews/phase-06-review.md）
> 复核结论：AT-07 五条逐条服务端硬判 + 反例充分（三科 PASS/REJECT/FAIL 并存互不影响、漏佐证按科拒提、仅复审通过移出应考、不覆盖过程性、读+写数据范围）；`mvn verify` 22/22、type-check/build 绿；权限点 V8 预种无缺口；8 个 Minor 入 backlog。
> `V12__exemption.sql` 落地 exemption_request/exemption_material，复用 platform-file MinIO 与 Phase 2 数据权限；`Phase6ExemptionIT` 覆盖 AT-07 多科互不影响、漏佐证、应考口径、过程性独立、可编辑态、读写数据范围。
- [x] T-052 exemption_request/material 表（分支：`feature/phase06-T052-exemption`）
- [x] T-053 免考申请（多科 + 每科佐证 + 依据）
- [x] T-054 免考两级审核（每科）+ 应考联动
- [x] T-055 免考申请页（学生端）
- [x] T-056 免考审核页

## Phase 7 · 视频评审 — 11/11 ✅ 已复核（Claude 2026-06-17，PASS·2轮 · docs/reviews/phase-07-review.md）
> 轮次1退回 B1（重传未挂可编辑态守卫）+ B2（reviewerCount>2 结算卡死）；轮次2 单提交 `75b7fa7` 已闭环：B1 三入口重传守卫（已有任务/REVIEWING/NEED_REVIEW/已结算 拒绝，不串分不卡死）、B2 `settleIfReady` 泛化 N 评委（N=2 不变、N=3 结算 82）、of() 改 fail-closed；`Phase7VideoReviewIT` 7/7 含重传拒绝 + 3 评委结算反例，`mvn verify` 29/29、前端绿。Minor 维持 backlog。
- [x] T-057 分片上传服务（init/chunk/merge）（分支：`feature/phase07-T057-video-review`）
- [x] T-058 视频校验（格式/大小/时长）
- [x] T-059 video_review/task 表
- [x] T-060 评审任务分配（默认2，可配多专家）
- [x] T-061 独立评分（提交前互不可见）
- [x] T-062 分差判定 + 结算（状态机B）
- [x] T-063 复评/仲裁（第三专家/学院仲裁）
- [x] T-064 视频鉴权播放 + 水印
- [x] T-065 视频上传页（学生端，分片进度）
- [x] T-066 视频评审页（评审教师）
- [x] T-067 视频评审管理页（学院）

## Phase 8 · 测试结果 — 4/4 ✅ 已复核（Claude 2026-06-17，PASS·一轮 · docs/reviews/phase-08-review.md）
> 复核结论：四项验收齐绿（免考联动剔除应考/成绩文本 AT-01/结论有效性契约/确认锁定/免考≠过程性独立）+ 读+写数据范围；三项跨阶段顺手清理（`ExemptionStatus.of()` fail-closed、`StudentServiceImpl` 死分支删除、`material:view` 补种授权）已闭环且回归无破坏；`mvn verify` 33/33；7 个 Minor 入 backlog。
> `V14__test.sql` 落地 ability_test_result、exam_org_mode 字典与 material:view 权限补种；测试结果模块复用 Phase 6 `/api/exemption/exam-subjects` 应考口径，score 全链路 String，ability_test_result 接入 Phase 2 数据权限规则。`Phase8TestResultIT` 覆盖免考通过科目剔除、成绩文本前导零/长串保留、待确认有效性为否、确认锁定拒改、免考结论不覆盖过程性、读写数据范围与跨院写 403；`mvn verify` 33/33、前端 type-check/build 通过。
- [x] T-068 ability_test_result 表（分支：`feature/phase08-T068-test-result`）
- [x] T-069 测试结果录入/导入 + 免考联动
- [x] T-070 测试结果确认与锁定
- [x] T-071 测试结果管理页

## Phase 9 · 证书 — 8/8 ✅ 已复核（Claude 2026-06-17，PASS·一轮 · docs/reviews/phase-09-review.md）
> 复核结论：AT-09 前置聚合缺失清单 / AT-10 18 位编号**并发无重号·回滚无空号**（cert_sequence 行锁 + 同事务 + 唯一键兜底，无 Redis INCR/max+1）/ AT-11 有效期 全绿；段码取自 V3 字典 ext_json、作用域参数化；状态机 C 字段锁定 + 作废重开关联原号；读+写数据范围（写仅校级）；V15+V16 幂等、V1–V14 冻结；`mvn verify` 40/40。9 个 Minor 入 backlog。
> `V15__certificate.sql` 落地 certificate/cert_sequence、证书状态字典与 cert.* 参数；`V16__certificate_student_view.sql` 补学生 `cert:view` SELF 授权（V15 已本地应用后按 Flyway 不改已发布脚本原则新增补充迁移）。`Phase9CertificateIT` 覆盖 AT-09/10/11、并发连续无重号、状态机C、作废重开、更正留痕、读写数据范围；`mvn verify` 40/40、前端 type-check/build 通过，待 Claude 复核。
- [x] T-072 certificate/cert_sequence 表（分支：`feature/phase09-T072-certificate`）
- [x] T-073 证书编号生成器（18位+作用域+锁）
- [x] T-074 有效期计算器
- [x] T-075 证书生成前置条件校验
- [x] T-076 证书状态机（生成/签发/导出/归档/作废/重开）
- [x] T-077 证书编号导入/更正（权限受控）
- [x] T-078 证书管理页（教务处）
- [x] T-079 证书签发页（签发人）

## Phase 10 · 导入导出与预校验 — 0/12
- [ ] T-080 26列 Excel 模型 + 文本格式策略
- [ ] T-081 模板下载（年度/学院/专业 + 内置下拉 + 补H表头）
- [ ] T-082 预校验中心 V-01~V-13（不入库）
- [ ] T-083 异常报告生成
- [ ] T-084 批次/异常明细/回滚追溯表
- [ ] T-085 确认导入（策略 + 批次 + 事务）
- [ ] T-086 导入回滚
- [ ] T-087 标准导出（范围筛选 + 保留条件）
- [ ] T-088 完整审核表/汇总表/附件清单表导出
- [ ] T-089 附件与视频批量打包导出
- [ ] T-090 导入中心页
- [ ] T-091 导出中心页

## Phase 11 · 统计报表 — 0/9
- [ ] T-092 学院提交进度统计
- [ ] T-093 材料完成率统计
- [ ] T-094 免考统计
- [ ] T-095 视频评审进度统计
- [ ] T-096 证书生成统计
- [ ] T-097 任教学段/学科交叉统计
- [ ] T-098 异常数据统计
- [ ] T-099 导入导出日志统计
- [ ] T-100 统计报表页（ECharts + 导出）

## Phase 12 · 通知 — 0/3
- [ ] T-101 notification 表
- [ ] T-102 站内信服务 + 触发点接入
- [ ] T-103 通知中心 + 未读角标

## Phase 13 · 系统管理与审计 — 0/5
- [ ] T-104 系统参数管理（§11/§15 全量）
- [ ] T-105 审计日志查询（多维）
- [ ] T-106 全局水印与脱敏治理
- [ ] T-107 备份与可恢复策略
- [ ] T-108 系统参数/审计/备份 管理页

## Phase 14 · 非功能/部署/验收 — 0/6
- [ ] T-109 M14 外部接口扩展点（仅预留）
- [ ] T-110 后端/前端 Dockerfile
- [ ] T-111 生产 docker-compose + 初始化
- [ ] T-112 兼容性与文本一致性测试
- [ ] T-113 AT-01~14 验收用例归档
- [ ] T-114 主业务流程端到端走查

---

## AT 验收跟踪（首验通过后置 ✅，Phase 14 复验）
| AT | 内容 | 首验阶段 | 首验 | 复验(Phase14) |
|---|---|---|---|---|
| AT-01 | 文本字段导入导出一致 | P3/P10 | [~] P3采集侧自测通过：student_no/id_card_no/birth_date 为 VARCHAR，前导零/出生日期文本回显保留；P8 采集侧续验：ability_test_result.score 为 VARCHAR/String，`000000000000123456789` 录入读出一致；P10 导入导出复验 | [ ] |
| AT-02 | 26 列 + H 表头 | P10 | [ ] | [ ] |
| AT-03 | 证件 + 出生日期 | P3 | [✅] P3复核通过(Claude 06-16)：四类证件正反例、身份证出生日期不一致拦截、港澳/台胞证不校验日期 | [ ] |
| AT-04 | 专业代码 0401/0451/0453 | P4 | [✅] P4复核通过(Claude 06-16)：教育类研究生非法前缀拒绝、0401/0451/0453 放行 | [ ] |
| AT-05 | 学段→学科联动禁自由填 | P1/P4 | [✅] P4复核通过(Claude 06-16)：学段-学科联动、自由填/中职类别节点拒绝、培养目标-地点联动 | [ ] |
| AT-06 | 四类材料全过才合格 | P5 | [✅] P5复核通过(Claude 06-17)：四类缺一/某类不通过→不合格、四类全通过→合格 | [ ] |
| AT-07 | 多科免考 + 每科佐证二级审核 | P6 | [✅] P6复核通过(Claude 06-17)：三科 PASS/REJECT/FAIL 并存互不影响、漏佐证拒提交、仅复审通过移出应考、不覆盖过程性、读+写数据范围 全绿 | [ ] |
| AT-08 | 视频≥2教师独立评审 + 复评 | P7 | [✅] P7复核通过(Claude 06-17·2轮)：接口层互不可见、分差>阈值/结论冲突需复评、thirdExpert 两两最小对、N 评委结算、重传守卫、鉴权播放、读+写数据范围 全绿 | [ ] |
| AT-09 | 证书前置条件 | P9 | [✅] P9复核通过(Claude 06-17)：前置聚合复用各阶段结论，缺项→明确缺失清单→生成拒绝 | [ ] |
| AT-10 | 18位编号连续不重 | P9 | [✅] P9复核通过(Claude 06-17)：18位分段示例可复现、作用域参数化、**50×10线程并发序号连续1..50不重无空**（行锁同事务、回滚无空号、唯一键兜底） | [ ] |
| AT-11 | 有效期规则 | P9 | [✅] P9复核通过(Claude 06-17)：上半年→+3年6/30、下半年→+3年12/31（边界==6归上半年） | [ ] |
| AT-12 | 审核全留痕 | P13 | [ ] | [ ] |
| AT-13 | 数据范围权限 | P2 | [✅] P2复核通过(Claude 06-16) | [ ] |
| AT-14 | 异常报告 | P10 | [ ] | [ ] |
