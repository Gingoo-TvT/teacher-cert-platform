# PROGRESS.md — 开发进度跟踪

> 用法见 `AGENTS.md` §8/§7.5。任务状态：`[ ]` 待开始 · `[~]` 进行中 · `[x]` 完成 · `[!]` 阻塞。
> 阶段状态（汇总表）：待开始 → 进行中 → **待复核**(codex 完工) → **✅ 已复核**(Claude 通过) / **复核退回**。codex 不得自行将阶段置 ✅（见 `docs/REVIEW-GATE.md`）。
> 任务明细见 `tasks.md`，验收见 `docs/phase-NN-*.md`。每次状态变更同时更新下方"阶段汇总"与"AT 跟踪"。

## 当前焦点
- 当前阶段：**🟦 Phase 47 退回整改候选完成，待独立增量复核**（分支：`codex/phase47-lifecycle-fail-closed`；代码冻结点：`aa6f81c`；候选材料：`docs/reviews/phase-47-remediation-submission-2026-07-25.md`；原正式报告：`docs/reviews/phase-47-review.md`）。Phase 41 已正式 PASS，统一入口为 `docs/CURRENT-EXECUTION-PLAN.md`。
- 当前结论：WS-1/WS-2/WS-3/WS-10/WS-13 PASS；U-001 CI 零状态契约 PASS。Phase 42、Phase 39 与 Phase 41 第二轮均已独立 PASS。Phase 41 的真实 MySQL 8.4 CLI 恢复门禁和 sourced/executable 初始化账号/精确授权门禁均已闭合；全项目当前仍由 **Phase 0、44、47、53** 四个退回阶段保持 **CHANGES_REQUESTED**。
- P0 放行项：Phase 41 上一轮 1 High / 2 Medium / 1 Low 全部关闭；正式 PASS 时新增的 1 个非阻断 Low 已在后续闭环——Gate A 示例补齐冷认证参数，runner 在 Maven 前强制受验证 TLS / `allowPublicKeyRetrieval=true` / 受控 RSA 公钥文件三选一，并由纯 stub 静态契约覆盖失败关闭。正式报告仍保留当时的 1 Low 快照。约 24 MiB 以上原始整行 fail-closed、写入侧未同界继续留最终全量审计。
- 最近更新：2026-07-25（Phase 47 代码候选 `aa6f81c` 已完成：仅明确 `NoSuchLifecycleConfiguration` 可按空配置创建；403/500/其它 404、网络/解析异常及空响应均 fail-closed，且不调用 `setBucketLifecycle`。新增纯 Mockito 反例 12/12，`platform-file` 单测 15/15，后端 9 模块跳测 package 通过；两路内部只读终审均为 0 High / 0 Medium / 0 Low）。
- 下一步：冻结 `b2f1f70..aa6f81c` 进行 Phase 47 独立增量复核；正式报告 PASS 前 Phase 47 仍为 **CHANGES_REQUESTED**、Phase 53 不放行。PASS 后依次为 Phase 53 demo 真实元数据/旧对象 reconcile + 浏览器播放 → Phase 44 状态诚实化/事务后缓存逐出 → Phase 0 验收基线。全部退回项关闭后执行最终全量审计。

## 审计整改工作流
| WS | 内容 | 状态 |
|---|---|---|
| WS-1 | IT 计数按自身 fixture 隔离 | `[x]` **独立复核 PASS**（2026-07-23） |
| WS-2 | prod admin bootstrap + STAFF/学生初始口令硬化 | `[x]` **独立复核 PASS**（2026-07-23） |
| WS-3 | MinIO 预签名直传、独立端点与浏览器并行分片 | `[x]` **独立复核 PASS**（2026-07-24；第六轮关闭第五轮 2 Medium；1 Low 勘误不阻断；Phase 53 demo High 未计入） |
| WS-10 | 移除默认 dev profile + profile fail-fast | `[x]` **独立复核 PASS**（2026-07-23） |
| WS-13 | RBAC 授权天花板（防自提权守卫） | `[x]` **独立复核 PASS**（2026-07-23） |

## 收官后阶段复核总览（本轮新增）

| 范围 | 实现状态 | 正式复核状态 | 本轮结论 |
|---|---|---|---|
| Phase 0 | 10/10 | `phase-00-review.md` | ❌ 复核退回（验收基线/门禁欠账） |
| Phase 1–14 | 已完成 | 逐阶段 PASS 报告齐全 | ✅ 保持已复核 |
| Phase 15–18 | WP-A/B/C/D 已完成 | WP 报告齐全 | ✅ 保持已复核 |
| Phase 19–28 | 已完成 | 逐阶段 PASS 报告齐全 | ✅ 保持已复核（已纠正 26/28 滞后状态） |
| Phase 29 | 已合并 | `phase-29-review.md` PASS | ✅ 已复核 |
| Phase 30–35 | 已完成 | 逐阶段报告齐全 | ✅ 保持已复核 |
| Phase 35b | 已合并 | `phase-35b-review.md` PASS | ✅ 已复核 |
| Phase 36–38、40、43、45–46、48–52 | 已合并 | 逐阶段 PASS 报告齐全 | ✅ 已复核 |
| Phase 42 | 第二轮整改 `bd1db49` | `phase-42-second-remediation-rereview-2026-07-24.md` PASS；首轮 1 Medium / 1 Low 全部关闭 | ✅ 独立复核 PASS |
| Phase 39 | 第二轮整改 `73406ed` | `phase-39-second-remediation-rereview-2026-07-24.md` PASS；第一轮 1 High / 1 Low 全部关闭 | ✅ 独立复核 PASS；三个基线债务候选留最终全量审计 |
| Phase 41 | 第二轮整改 `b5ed7f5`；材料/HEAD `ef6b550` | `phase-41-second-remediation-dynamic-evidence-rereview-2026-07-25.md` PASS；真实 MySQL 8.4 恢复与账号/授权两项门禁闭合 | ✅ 独立复核 PASS；上一轮 1 High / 2 Medium / 1 Low 全部关闭；正式 PASS 时的 1 Low 已后续闭环 |
| Phase 44、47、53 | 已合并 | 逐阶段 CHANGES REQUESTED 报告齐全；Phase 53 另有 WS-3 重核补充证据 | ❌ 复核退回 |

逐 Phase 结论、报告路径与后续顺序以 `docs/CURRENT-EXECUTION-PLAN.md` 为准。

## 阶段汇总
| Phase | 名称 | 优先级 | 任务数 | 完成 | 状态 |
|---|---|---|---|---|---|
| 0 | 工程脚手架与基础设施 | P0 | 10 | 10 | ❌ 复核退回(2026-07-23) |
| 1 | 基础数据与字典 | P0 | 12 | 12 | ✅ 已复核(Claude 06-14) |
| 2 | 账号角色权限 | P0 | 7 | 7 | ✅ 已复核(Claude 06-16) |
| 3 | 基本信息 | P0 | 9 | 9 | ✅ 已复核(Claude 06-16) |
| 4 | 专业培养信息 | P0 | 5 | 5 | ✅ 已复核(Claude 06-16) |
| 5 | 文件 + 过程性材料 | P0 | 8 | 8 | ✅ 已复核(Claude 06-17) |
| 6 | 免考 | P0 | 5 | 5 | ✅ 已复核(Claude 06-17) |
| 7 | 视频评审 | P0 | 11 | 11 | ✅ 已复核(Claude 06-17·2轮) |
| 8 | 测试结果 | P0 | 4 | 4 | ✅ 已复核(Claude 06-17) |
| 9 | 证书 | P0 | 8 | 8 | ✅ 已复核(Claude 06-17) |
| 10 | 导入导出与预校验 | P0 | 12 | 12 | ✅ 已复核(Claude 06-17·2轮) |
| 11 | 统计报表 | P1 | 9 | 9 | ✅ 已复核(Claude 06-17) |
| 12 | 通知 | P1 | 3 | 3 | ✅ 已复核(Claude 06-17) |
| 13 | 系统管理与审计 | P0 | 5 | 5 | ✅ 已复核(Claude 06-17·2轮) |
| 14 | 非功能/部署/验收 | P2+收口 | 6 | 6 | ✅ 已复核(Claude 06-18) |
| | **合计** | | **114** | **114** | |

---

## Phase 0 · 工程脚手架与基础设施 — 10/10 实现完成，❌ 复核退回
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
> 历史 Phase 7 结论保留；当前分支叠加的 WS-3 第六轮整改已由 `docs/reviews/ws-03-sixth-remediation-rereview-2026-07-24.md` 独立复核 **PASS**，第五轮新增 2 Medium 全部关闭。真实 2GB、非允许编码与不可解码首帧的广覆盖证据债继续保留，不由本次 PASS 补勾。
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

## Phase 10 · 导入导出与预校验 — 12/12 ✅ 已复核（Claude 2026-06-17，PASS·2轮 · docs/reviews/phase-10-review.md）
> 复核结论：轮次1退回 B1（跨学院覆盖/迁移）+ B2（逐行裸 DB 异常回滚整批），轮次2 单提交 `e868997` 已闭环（B1 `ensureCanUpdateExisting` 现有记录归属校验、collegeId 不跨范围改；B2 每行 `REQUIRES_NEW` 隔离 + `catch(Exception)` + `dbText` 防明细二次截断）；`Phase10ExchangeIT` 6/6 含跨院覆盖拒绝 + 坏行不污染整批反例，`mvn verify` 46/46；AT-01/02/14 首验通过、读侧范围/回滚冲突未回归；Minor×7 维持 backlog。
> 复核退回项已修：**B1** 导入更新前校验现有 student/training/certificate 当前 `collegeId` 属于调用者 `exchange:import` 写范围，现有学生禁止凭学号跨学院迁移；**B2** 每行导入改为 `REQUIRES_NEW` 独立事务，裸 DB 异常被记为该行失败，已成功行不回滚，错误明细落库前做长度保护。新增 `Phase10ExchangeIT` 跨学院覆盖拒绝 + 坏行不回滚已成功行反例；`mvn verify` 46/46、前端 type-check/build 通过。AT-01/AT-02/AT-14、读侧范围、回滚冲突逻辑未返工。
> `V17__exchange.sql` 落地 import_export_batch/import_error_detail/import_record_ref；新增 `platform-exchange` 模块并接入 boot，完成模板下载、26列标准模型、预校验 V-01~V-13、异常报告、确认导入/回滚、标准/完整/汇总/异常/附件清单导出与前端导入/导出中心。`Phase10ExchangeIT` 覆盖 AT-01 文本单元格与前导零、AT-02 A-Z 26列+H表头、AT-14 13条V反例、异常不入库、跳过重复、回滚恢复/冲突、读写数据范围与敏感导出鉴权；`mvn verify` 44/44、前端 type-check/build 通过，待 Claude 复核。
- [x] T-080 26列 Excel 模型 + 文本格式策略（分支：`feature/phase10-T080-exchange`）
- [x] T-081 模板下载（年度/学院/专业 + 内置下拉 + 补H表头）
- [x] T-082 预校验中心 V-01~V-13（不入库）
- [x] T-083 异常报告生成
- [x] T-084 批次/异常明细/回滚追溯表
- [x] T-085 确认导入（策略 + 批次 + 事务）
- [x] T-086 导入回滚
- [x] T-087 标准导出（范围筛选 + 保留条件）
- [x] T-088 完整审核表/汇总表/附件清单表导出
- [x] T-089 附件与视频批量打包导出
- [x] T-090 导入中心页
- [x] T-091 导出中心页

## Phase 11 · 统计报表 — 9/9 ✅ 已复核（Claude 2026-06-17，PASS·一轮 · docs/reviews/phase-11-review.md）
> 复核结论：8 类统计**数据范围服务层 fail-closed**（全部经 scopedStudents 收敛、空集/越范围→空、batch 按 operator/scopeJson），学院只见本院（IT 反例）；材料完成率+证书状态**与 DB 分组对账一致**；口径复用 Phase5/6/7/9 既有状态与校验器、分母文档化；导出复用 `ExchangeExcelHelper` 文本 `@`；`stats:view` V8 预种无新迁移、新模块接入无环；`mvn verify` 51/51、前端绿。5 个 Minor 入 backlog。
> 未新增 V18：`stats:view` 已在 V8 预种并授权（学院 COLLEGE、教务处 SCHOOL）。统计查询采用服务层 `DataScopeService.resolve("stats:view")` 明确收敛多表聚合范围；分母口径按 docs §6 默认“当前考核年度在册学生”，因 student 表无年度字段，按当前数据范围内学生集合计，年度过滤用于有 `assessment_year` 的业务表。
> 新增 `platform-statistics` 模块并接入 boot，完成 `/api/stats/{type}` 与 `/api/stats/{type}/export`、前端 ECharts 报表页。`Phase11StatsIT` 覆盖材料完成率对账、证书状态对账、学院 A 不含学院 B、异常定位到学生/字段、Excel 文本格式导出；`mvn verify` 51/51，前端 type-check/build 通过，待 Claude 复核。
- [x] T-092 学院提交进度统计（分支：`feature/phase11-T092-statistics`）
- [x] T-093 材料完成率统计
- [x] T-094 免考统计
- [x] T-095 视频评审进度统计
- [x] T-096 证书生成统计
- [x] T-097 任教学段/学科交叉统计
- [x] T-098 异常数据统计
- [x] T-099 导入导出日志统计
- [x] T-100 统计报表页（ECharts + 导出）

## Phase 12 · 通知 — 3/3 ✅ 已复核（Claude 2026-06-17，PASS·一轮 · docs/reviews/phase-12-review.md）
> 复核结论：四类触发（提交→教务员/退回→学生/视频分配→教师/导出→发起人）接入**严格附加非破坏**（51 条既有回归全绿）、send 双层 try/catch 失败不影响主业务、通知**本人可见**（list/read 按 user_id、他人 403）、未读角标；NotificationService 置 platform-system 无环、V18 文本字段、notice:view V8 预种；`mvn verify` 55/55、前端绿。4 个 Minor 入 backlog。
> `V18__notification.sql` 落地 notification 表；站内信服务放 platform-system，NotifyChannel 预留邮件/短信，send 内部吞通知通道异常不影响主业务。已接入 Phase3/4/5/6 提交/退回/复审提醒、Phase7 视频评审分配、Phase10 导出完成；通知中心按当前 user_id 过滤本人可见，`Phase12NotificationIT` 覆盖触发、未读计数、标记已读与他人通知不可读/不可标记。
- [x] T-101 notification 表（分支：`feature/phase12-T101-notification`）
- [x] T-102 站内信服务 + 触发点接入
- [x] T-103 通知中心 + 未读角标

## Phase 13 · 系统管理与审计 — 5/5 ✅ 已复核（Claude 2026-06-17，PASS·2轮 · docs/reviews/phase-13-review.md）
> 轮次1退回 B1（AT-12 审核全留痕未达成：完整审计仅 material 复审+cert 更正）；轮次2 单提交 `3c65d4f` 已闭环——student/training/exemption first+second、material first、cert void/reissue、video settle/thirdReview/arbitrate/confirm、exchange rollback 均补 `record(bizId/target/old/new/comment)`，AuditLogServiceImpl 改 best-effort；反例证主要审核 old/new/bizId/意见 + 按学生可查；`mvn verify` 61/61（含 55 回归）、前端绿、附加非破坏。参数改即生效/审计不可删/查询数据范围/脱敏鉴权 一并通过。Minor×4 维持 backlog。
> 复核退回 B1 已修：在 student/training/exemption/material 初审与复审、cert 作废/重开、video 自动结算/第三专家复评/学院仲裁/确认、exchange 回滚状态流转后显式 `auditLogService.record(...)`，补齐 `bizId/target/oldStatus/newStatus/comment/operator/IP`；`AuditLogServiceImpl.record` 改为 best-effort，审计失败不打断主流程。新增 `Phase13SystemAuditIT` 反例覆盖 student/training/exemption 复审退回与 cert 作废，并验证按 `studentId` 查询可查到上述复审记录；`mvn verify` 61/61、前端 type-check/build 通过，待 Claude 复核。
> `V19__system_audit.sql` 落地 `backup_record`；系统参数管理、审计日志查询、备份记录/触发占位与前端管理页已完成。AT-12 首验自测覆盖登录成功留痕、复审退回 old/new/意见/操作人/IP/target，普通管理员删审计拒绝且拒绝动作留痕，改 `video.diffThreshold` 即时影响结算，敏感明文无权限拒绝，学院审计范围不含他院；待 Claude 复核，未自行置 ✅。
- [x] T-104 系统参数管理（§11/§15 全量，分支：`feature/phase13-T104-system-audit`）
- [x] T-105 审计日志查询（多维）
- [x] T-106 全局水印与脱敏治理
- [x] T-107 备份与可恢复策略
- [x] T-108 系统参数/审计/备份 管理页

## Phase 14 · 非功能/部署/验收 — 6/6 ✅ 已复核（Claude 2026-06-18，PASS·一轮 · docs/reviews/phase-14-review.md）
> 收官：`Phase14E2EIT` 主流程端到端贯通（导入→审核→免考→视频→测试→证书→签发→导出/归档）且**导出逐字段==录入**、联动复验 AT-01~12/状态机；AT-01~14 整体复验归档（复验列全 ✅ + 对应 IT 依据 + Excel/WPS 兼容口径）；M14 SPI 预留默认关、空实现不影响一期；后端多阶段 Dockerfile + 前端 nginx + 生产 compose(五服务健康检查+Flyway迁移种子) + README + 备份手册；`mvn verify` 62/62（含全回归 61）、前端绿、无业务代码改动、V1–V19 冻结。3 个 Minor 入 backlog。
- [x] T-109 M14 外部接口扩展点（仅预留）
- [x] T-110 后端/前端 Dockerfile
- [x] T-111 生产 docker-compose + 初始化
- [x] T-112 兼容性与文本一致性测试
- [x] T-113 AT-01~14 验收用例归档
- [x] T-114 主业务流程端到端走查

## 收官后 · 测试可维护性加固
- T-115 `Phase14E2EIT` 单一大用例拆分为 14 个有序阶段子用例：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/t115-e2e-split`）。仅改测试与进度日志，不改业务代码/迁移/配置；断言零删减，`Phase14E2EIT` 14/14、全量 `mvn -B -ntp verify` **75/75**（含 61 回归）全绿。详见 `docs/reviews/t115-review.md`。

## 收官后 · WP-A RBAC 基座重定义
- WP-A `RBAC` 角色权限矩阵重定义：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/wp-a-rbac`）。新增 `V20__rbac_regrant.sql` 幂等重配运行期授权：`SYS_ADMIN` 全权，`ACADEMIC_ADMIN` 增 `cert:issue`，`COLLEGE_AUDITOR` 承接学院侧录入/导入/复审/视频分配等动作权，`COLLEGE_CLERK` 收敛为只读+初审+只读导出/批量下载，`CERT_ISSUER` 删除并停用 `test_cert_issuer`。受影响 Phase2/3/4/7/8/9/10/12/14 IT 已按新角色模型调整。**clean-room（重置 schema，V1–V20 全新迁移）`mvn verify` 75/75 全绿**、无业务代码改动、V1–V19 冻结。详见 `docs/reviews/wp-a-review.md`。

## 收官后 · Phase 16 / WP-B 测试结果只确认
- Phase 16 / WP-B `测试结果只确认`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/wp-b-test-confirm`）。新增 `V21__test_confirm_only.sql` 幂等撤销所有角色的 `test:edit` 授权，保留权限点定义用于历史审计/外键兼容，并确保 `COLLEGE_AUDITOR`、`ACADEMIC_ADMIN` 拥有 `test:import`/`test:confirm`。后端下线手工 `POST/PUT /api/test`（保留导入+确认+查询+有效性，导入内部落库不动），前端移除录入入口。**clean-room（重置 schema，V1–V21 全新）`mvn verify` 76/76 全绿**（含新增「手工端点已下线」反例）、前端 type-check/build 绿。详见 `docs/reviews/wp-b-review.md`。
- [x] T-121 后端下线手工录入/编辑入口：移除 `POST /api/test` 与 `PUT /api/test` 控制器方法，保留查询、导入、批量导入、确认锁定与有效性接口；导入内部落库 service 保留。
- [x] T-122 权限收口：V21 撤销 `test:edit` 运行期授权；`Phase2SecurityIT` 同步断言 WP-B 后权限矩阵。
- [x] T-123 契约不变性：成绩文本化、免考通过剔除应考科目、测试结论有效性供证书前置消费均沿导入+确认路径验证。
- [x] T-124 IT 调整：`Phase8TestResultIT` 与 `Phase14E2EIT` 改为导入成绩后确认；新增手工 POST/PUT 下线反例、锁定后重导入拒绝反例；跨学院写侧仍用 WP-A 后动作角色验证数据范围。
- [x] T-125 前端最小收口：移除 `testResult.ts` 的 save/update 调用与测试结果管理页录入/编辑入口，菜单/路由不再依赖 `test:edit`。
- 验证：定向 IT 28/28 通过；全量 `mvn -B -ntp verify` **76/76** 通过；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。等待 Claude 复核，未自行置 ✅。

## 收官后 · Phase 17 / WP-C 视频退回可重传
- Phase 17 / WP-C `视频退回可重传`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/wp-c-video-return`）。后端新增 `RETURNED` 态 + `POST /api/video/reviews/{id}/return`（CONFIRMED 禁退回、意见必填、富审计 old/new/意见、数据范围校验）；`ensureReuploadable` 仅放行退回态、REVIEWING/NEED_REVIEW/CONFIRMED 仍拒（Phase 7 守卫不破）；退回重传清旧任务/会话/分片/终分→WAIT_REVIEW + 通知。退回动作复用 `video:confirm`/`video:arbitrate`，**未新增 V22**。**clean-room `mvn verify` 78/78 全绿**（Phase7 9 含退回全链+CONFIRMED 反例）、前端绿。详见 `docs/reviews/wp-c-review.md`。
- [x] T-125 视频评审状态机增 `RETURNED` 退回态与 `/api/video/reviews/{id}/return` 退回动作，退回意见必填，`CONFIRMED` 禁退回，富审计记录 old/new/意见/target。
- [x] T-126 可编辑守卫放行 `RETURNED` 重传；`REVIEWING/NEED_REVIEW/REVIEW_COMPLETED/CONFIRMED` 仍拒绝重传，保留 Phase7 原反例。
- [x] T-127 `RETURNED` 重传软删旧评分任务与旧上传会话/分片，清终分/结论/仲裁/确认字段，校验通过后回 `WAIT_REVIEW`，重新分配后再评审；退回通知学生、重传入待评审通知负责人。
- [x] T-128 `Phase7VideoReviewIT` 覆盖退回→学生重传→重新指派→评分→结算、REVIEWING/NEED_REVIEW/CONFIRMED 重传拒绝、退回审计 old/new/意见可查、`CONFIRMED` 不可退回。
- 验证：定向 `Phase7VideoReviewIT` 9/9 通过；全量 `mvn -B -ntp verify` **78/78** 通过；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。等待 Claude 复核，未自行置 ✅。

## 收官后 · Phase 18 / WP-D 评审指定与分组
- Phase 18 / WP-D `评审指定与分组`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/wp-d-reviewer-group`）。新增 `V22__reviewer_group.sql`（reviewer_group + reviewer_group_member，幂等 DDL、唯一约束防同组重复/同院重名），复用 `video:assign`（不新增权限点）；CRUD 与成员增删按 `video:assign` 写范围校验、组归属取负责人学院（非请求）、成员须本院启用 `REVIEW_TEACHER`；`assign` 支持 `reviewerIds` 或 `groupId` 二选一，解析后校验人数=`video.reviewerCount` 且各评审本院+启用+REVIEW_TEACHER；`reviewer_group` 入数据范围 TABLE_RULES。**clean-room `mvn verify` 79/79**（Phase7 10：按组结算88/按人结算81/人数不足/跨院成员·组·评审全拒）+ 前端绿。详见 `docs/reviews/wp-d-review.md`。
- [x] T-129 评审组模型与接口：`GET/POST/PUT/DELETE /api/video/reviewer-groups`、成员增删，注册数据范围规则，写操作留 `@AuditLog`。
- [x] T-130 指派增强：`POST /api/video/reviews/{id}/assign` 支持 `reviewerIds` 或 `groupId` 二选一；按组解析成员后复用原任务分配、通知和 `video.reviewerCount` 校验；按人路径保持向后兼容并新增同学院/评审教师校验。
- [x] T-131 数据范围硬校验：建组/改组/加成员/按组指派均限本学院；跨院成员、跨院评审人、跨院视频/组指派拒绝（跨院视频由读写范围可返回 404）。
- [x] T-132 IT 覆盖：`Phase7VideoReviewIT` 新增建组→按组指派→评分→结算、按人指派兼容、跨院成员/跨院指派拒绝、组人数不等于 `video.reviewerCount` 报错；同步调整 Phase12 通知测试为真实评审教师分配。
- 验证：定向 `Phase7VideoReviewIT,Phase12NotificationIT` **14/14** 通过；全量 `mvn -B -ntp verify` **79/79** 通过；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。等待 Claude 复核，未自行置 ✅。

## 收官后 · Phase 19 / 前端重建·基座与设计系统
- Phase 19 / WP-F-1 `前端重建·基座与设计系统`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/phase19-frontend-shell`）。以前瞻版视觉为底重建登录页与主外壳，迁入设计系统基础组件(PageContainer/StatusTag/ChartBox/StatCard/.mono)和全局主题；生产版 `src/api/*`、`api/request.ts`、`stores/user.ts` 与 `meta.perms + hasAnyPerm` 鉴权守卫保持真实集成，不回退 mock；登录走真实 captcha/login/首登改密；菜单按权限过滤并**修复空壳父菜单**（子项全过滤→隐藏父级，菜单 perms 同步 WP-B 去 test:edit）；既有业务页在新外壳下保持可用。`type-check` + `build` 绿（仅既有 chunk-size 警告）；api/stores/后端/迁移均未改。详见 `docs/reviews/phase-19-review.md`。
- [x] T-133 设计系统与主题：新增 `PageContainer`、`StatusTag`、`ChartBox`、`StatCard`、全局 `.mono` 与角色色板，Naive UI 全局主题接入主色/圆角/中文 locale。
- [x] T-134 保留真实集成层：沿用生产版 axios 拦截器、401 refresh、Blob 下载能力与 `useUserStore(token/perms/roles/hasPerm/hasAnyPerm)`。
- [x] T-135 登录与鉴权：登录页改为前瞻版品牌分栏视觉，但仍走真实 `/auth/captcha`、`/auth/login` 与首登 `/auth/change-pwd`；路由守卫继续按权限点判断。
- [x] T-136 按权限菜单：采用前瞻版分组导航视觉，过滤按 `perms`，父菜单在可见子项为 0 时隐藏，解决“基础数据/系统管理”空壳问题。
- [x] T-137 既有业务页过渡与 build-only：现有业务路由/视图在新外壳下保持可用；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。

## 收官后 · Phase 20 / 前端重建·基础数据/学生/培养
- Phase 20 / WP-F-2 `前端重建·基础数据/学生/培养`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/phase20-base-student-training`）。重建 4 基础数据页（字典/区划/学科库/组织专业）、学生基本信息（manage+self）、专业培养信息：性别/证件类型/身份类型/培养目标/实习地点/学段/学科 等枚举均字典下拉；**培养目标↔学段↔学科 联动 server-driven**（消费后端 `/training/options`，违规自动重置）；**`major:manage` 控「新增专业」按钮**（学院教务员 v-if 隐藏，与 WP-A V20 矩阵一致）；StudentSelf 全字段 `:disabled="locked"` 锁定态守卫；按 perm 显隐 edit/firstReview/secondReview 动作。`src/api/*`/`stores/user` 未改，后端/迁移零改动。`type-check`+`build` 绿。详见 `docs/reviews/phase-20-review.md`。
- [x] T-138 基础数据页：字典/区划/学科库/组织与专业改为新设计系统主从布局，接真实 `dict/region/subject/organization` API；新增/编辑/删除按 `dict:manage`、`subject:import`、`college:manage`、`major:manage` 显隐，教务员无 manage 权时无新增专业入口。
- [x] T-139 学生基本信息：列表支持关键词、状态、学院与学年/年级/班级筛选，提供详情、编辑、提交、初审、复审；动作按 `student:edit`、`info:firstReview`、`info:secondReview` 显隐，性别/证件类型/身份类型从字典下拉。
- [x] T-140 学生本人信息：自助确认页改用 `PageContainer/StatusTag/.mono`，保存与提交继续走真实 `confirmStudent/submitStudent`；`locked=1` 时表单只读并提示证书生成后锁定态。
- [x] T-141 专业培养信息：列表支持关键词、状态、考核年度、学院、学段筛选，提供详情、编辑、提交、初审、复审；培养目标通过 `/training/options` 限制实习地点和任教学段，任教学科复用 `SubjectSelect` 且不可自由填。
- [x] T-142 字段规范对齐：性别、身份证件类型、身份类型、学历层次、专业培养目标、实习组织方式、实习地点、任教学段、面试组织方式等均从字典或后端联动接口取值；前端 type-check/build 通过。

## 收官后 · Phase 21 / 前端重建·材料/免考/视频
- Phase 21 / WP-F-3 `前端重建·材料/免考/视频`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/phase21-material-exemption-video`）。材料：`<object>/<iframe>` 内联 PDF/JPG/PNG 预览 + 非可览类型「打开文件」下载兜底 + 四类合格 + 初/复审 + 批量下载，按 material perm 显隐；免考：多科逐行申请(科目+依据+佐证必填) + 二级审核 + 应考科目剔除展示；视频：退回态显「重新上传」(WP-C)、指派对话框 `mode` 切按人/按组 XOR + 评审组 CRUD(WP-D)、第三专家/仲裁/确认/退回/水印播放 全按 video perm 显隐。`src/api/*`/`stores/user` 未改（沿用 WP-C/WP-D 新增 API），后端/迁移零改动。`type-check`+`build` 绿。详见 `docs/reviews/phase-21-review.md`。
- [x] T-143 过程性材料：四类材料上传/替换/提交、初审/复审、批量下载与四类合格判定重建；`/material/preview/{id}` 返回的预签名 URL 在弹窗内联预览 PDF/JPG/PNG，非可预览文件退化为打开链接。
- [x] T-144 免考：多科申请、每科佐证上传/替换/删除/预览、二级审核重建；应考口径弹窗展示复审通过科目剔除结果，多科记录互不影响。
- [x] T-145 视频评审：视频分片上传/秒传/进度保留；`RETURNED`、校验失败、待上传状态显示上传/重传入口；负责人可按人或按评审组指派，评审组 CRUD 与成员维护接真实 `/video/reviewer-groups`。
- [x] T-146 视频播放与结算动作：鉴权播放页保留动态水印；我的评审支持 9 维评分；负责人侧支持第三专家复评、学院仲裁、确认、退回（意见必填，`CONFIRMED` 不显示退回入口）。
- [x] T-147 字段/状态机对齐：三域列表、统计卡、弹窗均使用 `PageContainer`、`StatusTag`、`.mono`；动作按 `material:*`、`exemption:*`、`video:*` 权限显隐；前端 type-check/build 通过。

## 收官后 · Phase 22 / 前端重建·测试/证书/导入导出/统计
- Phase 22 / WP-F-4 `前端重建·测试/证书/导入导出/统计`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/phase22-test-cert-exchange-stats`）。测试结果落实 WP-B 只确认——无手工 save/update 入口（imports 仅 import/importFile/confirm），perms 仅 test:import/test:confirm（无 test:edit），页内明示"不提供手工新建/编辑"；证书签发并入证书管理（canIssue=hasPerm('cert:issue')，**无 CERT_ISSUER 角色依赖**，对教务处管理员开放，item-12）+ precheck 四项 + generate/correct/void/reissue 生命周期按 perm；导入四步向导(prevalidate+策略+confirm+rollback+错误表)；导出标准/完整/附件文本化；统计 StatCard+ChartBox/ECharts。`src/api/*`/`stores/user`/后端/迁移零改动；`type-check`+`build` 绿。详见 `docs/reviews/phase-22-review.md`。
- [x] T-148 测试结果：重建列表、导入、确认、有效性提示与应考科目弹窗；成绩以 `.mono` 文本只读展示，前导零不经前端数值化；动作按 `test:import`、`test:confirm` 显隐。
- [x] T-149 证书管理：重建证书列表、前置校验、生成 18 位编号、更正、作废、重开；动作按 `cert:view/generate/correct/void/reissue` 显隐。
- [x] T-150 证书签发：签发、标记导出、归档整合进证书管理生命周期，同时保留签发队列辅助页；签发动作按 `cert:issue` 显隐，不依赖 `CERT_ISSUER` 角色入口。
- [x] T-151 导入导出中心：导入页改为模板选择→上传预校验→V-01~V-13 错误表→策略确认四步向导，支持批次、异常报告下载与回滚；导出页支持标准/完整/证书汇总/异常表和附件视频打包。
- [x] T-152 统计报表：重建 8 类统计，使用 `StatCard` 指标卡与 `ChartBox` ECharts，保留明细钻取与 Excel 导出，继续接真实 `/api/stats/{type}`。
- 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。等待 Claude 复核，未自行置 ✅。

## 收官后 · Phase 23 / 前端重建·系统管理/通知/工作台/全局学年
- Phase 23 / WP-F-5 + WP-E `前端重建·系统管理/通知/工作台/全局学年`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/phase23-system-notice-dashboard-year`）。系统管理(账号/角色/权限/数据范围/参数/审计/备份)、通知中心、角色感知工作台用 Phase 19 设计系统；**WP-E 全局学年**——新增 `stores/year.ts`(persist+yearOptions)+顶栏选择器，学生/培养/材料/免考/视频/测试/证书/导入导出/统计 9 列表 `watch(yearStore.assessmentYear)→reload` 切换即全局生效；通知红点(菜单项 noticeCenter + 列表项 notice-dot)+头部角标；工作台按 6 角色(SYS_ADMIN/ACADEMIC_ADMIN/COLLEGE_AUDITOR/COLLEGE_CLERK/REVIEW_TEACHER/STUDENT)展示卡+图。评审组入口留视频域(video:assign 工作流，已说明)。仅新增前端内部 year store，后端/迁移/API 零改动；`type-check`+`build` 绿。详见 `docs/reviews/phase-23-review.md`。
- [x] T-153 系统管理：账号/角色/权限矩阵/数据范围页接真实 `security` API，参数/审计/备份页接真实 `systemAudit` API；SYS_ADMIN 拥有相关权限时全功能可见，写按钮按 `system:user/role/perm:manage`、`system:param:manage`、`audit:view`、`system:backup` 显隐。
- [x] T-154 通知中心：重建列表、全部已读、已读/未读与类型过滤；顶栏通知角标保留，菜单“通知中心”和列表未读项新增红点，列表和布局均轮询未读/通知。
- [x] T-155 角色感知工作台：按学生、学院教务员、学院负责人、评审教师、教务处管理员、系统管理员角色展示不同关注项、指标卡、ECharts 图表和最近通知。
- [x] T-156 全局学年选择器：新增 `useYearStore`，顶栏统一选择“考核学年”；学生、培养、材料、免考、视频、测试、证书、导入导出、统计页面消费该学年作为默认筛选/模板年度，切换后只读列表自动刷新或同步筛选。
- [x] T-157 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。等待 Claude 复核，未自行置 ✅。

## 收官后 · Phase 24 / 字段规范收口 + 整体验收
- Phase 24 `字段规范收口 + 整体验收`：**✅ 已复核（Claude 2026-06-18，PASS·一轮）**（分支：`feature/phase24-acceptance`）。新增 `V23__field_acceptance.sql` 幂等修正中职培养目标默认/允许实习地点为 `other`（附录 A：中职→其他），V1-V22 冻结；其余字段枚举核对与字典种子一致（Phase4/Phase9 夹具同步对齐，非弱化）。新增 `Phase24AcceptanceIT`(3 用例)：① 字段字典(gender/id_card_type 等)与 `/training/options` 联动(中职→other containsExactly、高中→中小学) 对附录 A；② 新 RBAC 全角色边界(SYS_ADMIN 全权/教务处含 cert:issue/负责人复审+assign+test:import/教务员只查看+初审且 assign·test 写被拒/评审/学生)；③ 主流程贯通 + **标准导出 26 列逐列(1–25)==录入**(校码/姓名/学号/性别/证件类型号/出生/身份/生源/学科专业/层次/目标/实习/学段学科/面试/证书号/有效期/签发人/状态)+H+全列@。**clean-room `mvn verify` 82/82**（V1–V23 全新迁移）+ 前端绿。详见 `docs/reviews/phase-24-review.md`。
- [x] T-158 字段规范终校：核对身份证件类型、身份类型、学历层次、培养目标、实习组织方式、实习地点、任教学段、面试组织方式、性别等字典；通过 V23 修正中职培养目标联动为“其他”，并同步既有 Phase4/Phase9 测试夹具。
- [x] T-159 新 RBAC 全角色边界：`Phase24AcceptanceIT` 断言 SYS_ADMIN 全权可达、ACADEMIC_ADMIN 含 `cert:issue`、COLLEGE_AUDITOR 可复审/视频指派/测试导入确认、COLLEGE_CLERK 仅查看+初审且 secondReview/assign/test 写被拒、REVIEW_TEACHER 可评分、STUDENT 本人可见/确认。
- [x] T-160 收口主流程 E2E：复用既有真实接口链路跑通标准导入、学生/培养/材料/免考/视频/测试/证书/标准导出/归档，并断言标准导出逐字段等于录入，覆盖 AT-01/AT-02 文本化与导出一致性收口。
- [x] T-161 整体验收：定向 `Phase4TrainingIT,Phase9CertificateIT,Phase24AcceptanceIT` 15/15 通过；全量 `mvn -B -ntp verify` **82/82** 通过；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。等待 Claude 复核，未自行置 ✅。

## 收官后 · Phase 25 / 验收修复·403 与图表轴
- Phase 25 `验收修复·403 与图表轴`：**✅ 已复核（Claude 06-18，PASS·一轮，含 1 Major backlog→Phase 27）**（分支：`feature/phase25-acceptance-fix`）。纯前端修复，不改后端/接口/权限/迁移；组合页按真实权限点条件加载 API 并显隐 tab/区块/按钮，全无可见分区给空态；ChartBox/StatsReportView/Dashboard 按 `frontend/DESIGN.md` §7 统一轴配置与 resize。
- [x] T-163 组合页分区权限条件加载 + 显隐：`SystemAuditView` 参数/审计/备份分别按 `system:param:manage`/`audit:view`/`system:backup` 加载；`SecurityManageView` 用户/角色/权限树分别按 `system:user:manage`/`system:role:manage`/`system:perm:manage` 加载；同类多权限入口 `OrganizationManageView`、`VideoReviewView`、导入导出、证书、统计、工作台补条件加载，避免任一部分权限角色进入页面触发无权 API。
- [x] T-164 图表轴修复：`ChartBox` 统一 xAxis `interval:0`、`width`、`overflow:'truncate'`、`hideOverlap`、`axisTick.alignWithLabel:true`，yAxis `min:0`、`minInterval:1`、浅色 splitLine；按标签长度自适应 rotate/grid bottom，并用 `ResizeObserver + window.resize` 保证 resize 生效；`StatsReportView` 与 `DashboardView` 去除分散轴配置。
- [x] T-165 全角色自检矩阵：已在 `DEVLOG.md` 记录 STUDENT/COLLEGE_CLERK/COLLEGE_AUDITOR/REVIEW_TEACHER/ACADEMIC_ADMIN/SYS_ADMIN 菜单入口与分区 API 条件加载口径；`CERT_ISSUER` 已在 WP-A 停用，签发入口按 `cert:issue` 并入教务处管理员。
- 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）。Claude 06-18 复核 PASS 并合并 main；Major backlog 转 Phase 27。

## 收官后 · Phase 26 / UI 设计提升·按 DESIGN.md
- Phase 26 `UI 设计提升·按 frontend/DESIGN.md`：**✅ 已复核（Claude PASS，一轮；`docs/reviews/phase-26-review.md`）**（分支：`feature/phase26-ui-uplift`）。纯前端视觉/排版层提升，不改路由、接口、权限、状态机或迁移；目标是政务·教育级专业后台质感，色板/字阶/间距/圆角/阴影/表格/表单/登录页按 `frontend/DESIGN.md` 收口。
- [x] T-166 主题 token 收口：`frontend/src/theme/global.css` 扩展设计 token；新增 `frontend/src/theme/naive.ts` 集中 `themeOverrides`(common/Button/DataTable/Menu/Tag/Card/Input/Form 等)；`ChartBox` 改用 `theme/tokens.ts` 色板；页面/组件层不再写散落硬编码色。
- [x] T-167 外壳导航：`MainLayout` 调整为 240/64 侧栏、白底右边线、分组选中 soft 底 + 左 3px 色条、顶栏 60、内容区 24 留白；`PageContainer` 统一页标题/描述/操作区和响应式。
- [x] T-168 列表表格：全局 DataTable 表头浅底 sticky、行高/hover/分页右对齐；`.mono/.numeric` 在表格内右对齐；操作列新增 `renderTableActions`，多于 3 个动作收进“更多”，主行动作实心、次动作文字。
- [x] T-169 表单/卡片/标签/指标卡/空载态：表单 label top 和禁用只读态统一；`StatusTag` 扩展英文/中文枚举 soft 配色；`StatCard` 改为语义 `tone`；空态、加载容器、卡片阴影/圆角统一。
- [x] T-170 登录页：品牌分栏按 token 重做对比、层级与响应式；保留真实 captcha/login/change-pwd 流程，不改鉴权逻辑。
- [x] T-171 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）；硬编码色自查：页面/组件/布局层无 `#`/`rgba` 色值，色值集中在 `frontend/src/theme/*` token/overrides。

## 收官后 · Phase 27 / 后端·负责人评审教师列表端点（修按人指派）
- Phase 27 `后端·负责人评审教师列表端点`：**✅ 已复核（Claude 06-18，PASS·一轮）**（分支：`feature/phase27-reviewer-list`）。修复 Phase 25 backlog：视频“按人指派”候选不再依赖 `system:user:manage` 的 `/system/user`，改为 `video:assign` 门控的本院/全校评审教师候选端点。无新增权限点、无 Flyway 迁移，V1-V23 冻结。
- [x] T-172 新增 `GET /api/video/reviewer-candidates`：`@PreAuthorize("@pms.has('video:assign')")`；返回 `id/realName/workNo`；服务端按 `dataScopeService.resolve("video:assign")` 解析调用者数据范围，COLLEGE 仅查授权学院，SCHOOL 查全校；候选复用评审组成员校验口径，仅 ENABLED 且具 `REVIEW_TEACHER` 角色用户。
- [x] T-173 前端 VideoReviewView 按人指派候选改用 `listReviewerCandidates()`；移除对 `listUsers`/`system:user:manage` 的依赖，负责人/教务处具 `video:assign` 即可加载候选；按组指派和评审组 CRUD 保持原流程。
- [x] T-174 IT 覆盖：`Phase7VideoReviewIT` 新增候选端点范围与贯通用例，断言学院负责人只能看到本院 `REVIEW_TEACHER`、看不到跨院评审教师和非评审教师，评审教师访问候选端点 403；随后用端点返回的 2 名评审教师按人指派→评分→结算 PASS。
- 验证：定向 `mvn -B -ntp -pl platform-boot -am "-Dit.test=Phase7VideoReviewIT" "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dspring-boot.repackage.skip=true" verify` **11/11** 通过；全量 `mvn -B -ntp verify` **83/83** 通过；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）。未启动常驻前端/后端服务；等待 Claude 复核，未自行置 ✅。

## 收官后 · Phase 28 / UI 视觉重做·明亮圆润(青绿/友好 SaaS)
- Phase 28 `UI 视觉重做·按新 frontend/DESIGN.md`：**✅ 已复核（Claude PASS，一轮；`docs/reviews/phase-28-review.md`）**（分支：`feature/phase28-ui-bright-rounded`）。纯前端视觉/排版层重做，不改路由、接口、权限、状态机、业务逻辑、后端或迁移；目标是从 Phase 26 政务克制蓝/小圆角明显切换到青绿主色、大圆角、宽松留白、柔和阴影的友好 SaaS 风。
- [x] T-175 主题 token 重写：`theme/global.css` 改为青绿/暖中性变量、`--page-bg:#f6f8f7`、控件 10px、卡片/表格 14px、柔和阴影和更宽间距；`theme/naive.ts` 重写 common/Button/DataTable/Menu/Tag/Card/Input/Form/Select/Pagination/Empty；`theme/tokens.ts` 改明快图表色板。
- [x] T-176 外壳/导航：`MainLayout` 侧栏 248/64，选中态改青绿圆角 pill（无左色条），项间距加宽；顶栏页标题 20px、学年/通知青绿角标、青绿圆形头像；内容区 28 留白，`PageContainer` 间距加宽。
- [x] T-177 列表/表格：全局 DataTable 圆角 14px 卡片化，表头浅青底、sticky、行高约 50、hover 青绿浅底、分页右对齐；操作列继续保持 ≤3/更多 popover；筛选条控件 34px 等高、10px 圆角。
- [x] T-178 卡片/表单/标签/指标卡/空载态：白卡 14px + 柔和阴影；表单 label top 和禁用态沿 token；StatusTag pill soft 全枚举；StatCard 改青绿色点 + 32px 大数值；空/载/错态和弹层圆角统一。
- [x] T-179 登录页：明亮青绿品牌分栏、浅暖背景、圆角白卡表单；保留真实 captcha/login/change-pwd 流程。
- [x] T-180 图表：`ChartBox` 保留 Phase25 轴修复，新增明快色板、柱图顶圆角 `itemStyle.borderRadius:[6,6,0,0]`、`barMaxWidth:34`、圆角 tooltip 与浅色轴线。
- [x] T-181 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）；硬编码色自查：页面/组件/布局层无 `#`/`rgba` 色值，色值集中在 `frontend/src/theme/*`。

## 收官后 · Phase 30 / 体验基建 + 全局观感
- Phase 30 `体验基建 + 全局观感`：**✅ 已复核（Claude 2026-07-02，PASS·一轮+1 处复核修补）**（分支：`feature/phase30-ux-foundation`）。gate ①-⑥ 全过；复核修补 ReviewDialog 退回必填意见校验（P6）。详见 `docs/reviews/phase-30-review.md`。纯前端；新依赖仅 `@vicons/ionicons5`。
- [x] T-190 依赖与工具：新增 `@vicons/ionicons5`；新增 `frontend/src/utils/format.ts` 与 `frontend/src/constants/statusLabels.ts`；`global.css` 增 `.tabular-nums`。
- [x] T-191 通用组件：新增 `FilterBar.vue`、`DataPanel.vue`、`EmptyState.vue`、`TableSkeleton.vue`、`DetailPanel.vue`、`ReviewDialog.vue`；`StatCard` 增 icon slot/青绿圆底/tabular 数字。
- [x] T-192 外壳：菜单组接 ionicons；通知按钮改铃铛角标；`router.afterEach` 同步标题；新增 `frontend/public/favicon.svg`；学年选择器限制 4 位年份且移除自由输入。
- [x] T-193 全局文案：登录页 chips 改用户价值文案，品牌副标题为中文校名；黑名单文案清理。
- [x] T-194 图表：`tokens.ts` 使用附录 C 色板；`ChartBox` 内置 tooltip/grid/legend/单系列柱规则；Dashboard/统计页去掉类目换行拼接。
- [x] T-195 数据正确性：Dashboard 指标不再用 `records.length`；学生列表年级筛选独立为空，新增/编辑表单不再默认取全局学年。
- 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）。等待 Claude 复核，未自行置阶段通过。

## 收官后 · Phase 31 / 业务域列表页铺开
- Phase 31 `业务域列表页铺开`：**✅ 已复核（Claude 2026-07-03，PASS·一轮·0 修补）**（分支：`feature/phase31-list-rollout`）。12 页结构件全接入 + W4 格式化/状态中文 + W3 黑名单空 + 活体 3 角色 API 走查零 403/数据范围正确。Minor 2 非阻断。详见 `docs/reviews/phase-31-review.md`。纯前端；未新增依赖。
- [x] 学生/培养：接入统一筛选、DataPanel、状态映射与操作收敛；学生空态引导“去导入”；培养详情与审核弹窗统一并保持联动字段中文展示。
- [x] 材料/免考：接入统一筛选、DataPanel、StatCard、ReviewDialog；材料类别/文件大小格式化与预览 icon；免考科目/依据中文与佐证文件徽标。
- [x] 视频/测试：视频评审列表、我的评审、评审组卡化；`RETURNED` 显示“已退回”；任务提交时间格式化。测试成绩 mono 右对齐，结论/确认状态 StatusTag。
- [x] 证书/签发：证书号 mono；签发日期/有效期 `formatDate`；生命周期状态映射；签发队列卡化。
- [x] 导入/导出/统计/通知：导入批次时间/策略中文，异常表卡化；导出学院筛选改学院下拉并继续传 id；统计表格卡化且 ChartBox 保持附录 C；通知改 `n-list`、未读圆点/标题加粗/灰色时间、行点击标记已读并打开全文抽屉。
- 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅 Vite chunk-size warning）；附录 A 黑名单与纯前端范围检查输出为空。等待 Claude 复核，未自行置阶段通过。

## 收官后 · Phase 32 / 系统域 + 表单/详情/审核体验
- Phase 32 `系统域 + 表单/详情/审核体验`：**✅ 已复核（Claude 2026-07-03，PASS·一轮·0 修补）**（分支：`feature/phase32-form-detail-review`）。系统域两栏 + 抽屉 P4 双列 + 详情 P5 + 审核 P6 + 审计中文 + 权限树可读名；W3 空；活体 SYS_ADMIN 10 系统接口零 403。Minor 1 非阻断。详见 `docs/reviews/phase-32-review.md`。纯前端；未新增依赖。
- [x] 系统域 6 页：字典/学科/区划/组织改主从两栏，接 `FilterBar + DataPanel + DetailPanel`；账号权限用户/角色/权限三 tab 接规范表格卡；参数审计备份保留三 tab 并补审计筛选提示。
- [x] P4 抽屉表单：系统域字典/组织/账号/参数/备份抽屉统一 560 宽、双列栅格、分组标题与底部取消/保存；学生/培养/材料/免考/证书相关表单抽屉同步收敛。
- [x] P5/P6：学生与培养查看继续走 `DetailPanel` 只读面板，查看不复用编辑表单；学生/培养/材料/免考审核入口统一 `ReviewDialog`。
- [x] 审计中文化：审计 old/new 状态继续 `statusLabel + StatusTag`；操作名走 `operationLabel`；审计学院筛选为学院下拉，学生筛选占位「学生ID（数字）」并带数字提示；参数/审计/备份时间列均 `formatDateTime`。
- 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）；附录 A 黑名单、纯前端范围、旧抽屉宽度、系统页裸表格扫描输出为空。等待 Claude 复核，未自行置复核通过。

## 收官后 · Phase 33 / 工作台 v2 + 视频评审工作台 + 学生端友好化
- Phase 33 `工作台 v2 + 视频评审工作台 + 学生端友好化`：**✅ 已复核（Claude 2026-07-03，功能 PASS·一轮·0 修补）**（分支：`feature/phase33-workbench-student`）。视频 915→49 拆 4 面板 + 评分工作台 + 学生三页卡片化 + 工作台 v2；W3 空、活体 5 角色零 403。W6 行数系统性遗留(13 文件)转可选 Phase 34。详见 `docs/reviews/phase-33-review.md`。纯前端；未新增依赖。
- [x] 工作台 v2：页头改问候语（姓名/角色/日期），指标卡继续使用统计/通知真实返回值并带 icon；新增 6 角色快捷入口卡；最近通知改列表组件；图表继续走 `ChartBox`。
- [x] 视频页拆分：新增 `views/video/components/{UploadPanel,MyTaskPanel,ManagePanel,GroupPanel}.vue`；`VideoReviewView.vue` 缩至 49 行；`MyTaskPanel` 改为左任务列表 + 右评分区工作台，替代弹窗评分。
- [x] 学生端友好化：材料 `selfMode` 改四类卡片网格；视频 `selfMode` 改步骤条 + 退回意见 + 重新上传；证书 `selfMode` 改青绿描边证书卡，展示证书号、有效期和状态。
- 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）；附录 A 黑名单、纯前端范围、视频主文件行数和组件命中证据已写入 `DEVLOG.md`。等待 Claude 复核，未自行置复核通过。

## 收官后 · Phase 35 / 前端验收缺陷修复
- Phase 35 `前端验收缺陷修复`：**✅ 已复核（Claude 2026-07-03，PASS + Claude 亲自返工 F4 图高 1 处）**（分支：`feature/phase35-acceptance-fix`）。F1–F5 全落地，关键共享件由 codex 完成、Claude 核验；F4 ChartBox 容器高度由 Claude 返工。详见 `docs/reviews/phase-35-review.md`。纯前端；未新增依赖。
- [x] F1 列表横滚：删除 27 处视图硬编码 `:scroll-x`，`DataPanel` 未显式传值时按列 `width/minWidth` 自动推导表宽。
- [x] F2 学生下拉：新增 `StudentSelect` 远程搜索；材料、免考、视频、证书、培养、测试导入、学生本人页去掉无参全量学生预载。
- [x] F3 上传入口：视频、材料、免考、导入中心、测试结果、学科库导入使用 `n-upload-dragger`；视频上传两处用浏览器 metadata 自动识别时长，失败才显示手填。
- [x] F4 统计报表：`ChartBox` 长类目 45 度旋转并加大底部空间；统计表扩展值列放宽、数值 tabular 对齐。
- [x] F5 首页指标：首页问候去掉说明尾巴；`StatCard` 新增内联 `unit`，Dashboard/StatsReport 单位不再占副行，并按 label 去重首页指标。
- 验证：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）。等待 Claude 按 Phase35 gate 复核，未自行置复核通过。

---

## AT 验收跟踪（首验通过后置 ✅，Phase 14 复验）
| AT | 内容 | 首验阶段 | 首验 | 复验(Phase14) |
|---|---|---|---|---|
| AT-01 | 文本字段导入导出一致 | P3/P10 | [✅] P3/P8 采集侧 + P10 导入导出 复核通过(Claude 06-17)：String模型 + 单元格文本 `@` + 读取按 String，证件号/前导零学号/日期/编号 全链路不丢格式 | [✅] Phase14复验：`Phase10ExchangeIT` + `Phase14E2EIT` 标准导出读回文本化、导出与录入一致 |
| AT-02 | 26 列 + H 表头 | P10 | [✅] P10复核通过(Claude 06-17)：模板/标准导出 A-Z 26列逐列一致、H列=`身份证件号码`、全列文本格式 | [✅] Phase14复验：`Phase10ExchangeIT` + `Phase14E2EIT` 断言 A-Z 26列与 H 表头 |
| AT-03 | 证件 + 出生日期 | P3 | [✅] P3复核通过(Claude 06-16)：四类证件正反例、身份证出生日期不一致拦截、港澳/台胞证不校验日期 | [✅] Phase14复验：`Phase3StudentIT` 回归 |
| AT-04 | 专业代码 0401/0451/0453 | P4 | [✅] P4复核通过(Claude 06-16)：教育类研究生非法前缀拒绝、0401/0451/0453 放行 | [✅] Phase14复验：`Phase4TrainingIT` 回归 |
| AT-05 | 学段→学科联动禁自由填 | P1/P4 | [✅] P4复核通过(Claude 06-16)：学段-学科联动、自由填/中职类别节点拒绝、培养目标-地点联动 | [✅] Phase14复验：`Phase4TrainingIT` + `Phase14E2EIT` 标准学科正向联动 |
| AT-06 | 四类材料全过才合格 | P5 | [✅] P5复核通过(Claude 06-17)：四类缺一/某类不通过→不合格、四类全通过→合格 | [✅] Phase14复验：`Phase5MaterialIT` + `Phase14E2EIT` 四类材料全通过后合格 |
| AT-07 | 多科免考 + 每科佐证二级审核 | P6 | [✅] P6复核通过(Claude 06-17)：三科 PASS/REJECT/FAIL 并存互不影响、漏佐证拒提交、仅复审通过移出应考、不覆盖过程性、读+写数据范围 全绿 | [✅] Phase14复验：`Phase6ExemptionIT` + `Phase14E2EIT` 免考科目从应考清单剔除 |
| AT-08 | 视频≥2教师独立评审 + 复评 | P7 | [✅] P7复核通过(Claude 06-17·2轮)：接口层互不可见、分差>阈值/结论冲突需复评、thirdExpert 两两最小对、N 评委结算、重传守卫、鉴权播放、读+写数据范围 全绿 | [✅] Phase14复验：`Phase7VideoReviewIT` + `Phase14E2EIT` 85/60/81 复评终分 83 |
| AT-09 | 证书前置条件 | P9 | [✅] P9复核通过(Claude 06-17)：前置聚合复用各阶段结论，缺项→明确缺失清单→生成拒绝 | [✅] Phase14复验：`Phase9CertificateIT` + `Phase14E2EIT` 前置聚合通过后生成 |
| AT-10 | 18位编号连续不重 | P9 | [✅] P9复核通过(Claude 06-17)：18位分段示例可复现、作用域参数化、**50×10线程并发序号连续1..50不重无空**（行锁同事务、回滚无空号、唯一键兜底） | [✅] Phase14复验：`Phase9CertificateIT` 并发回归 + `Phase14E2EIT` 18位编号前缀断言 |
| AT-11 | 有效期规则 | P9 | [✅] P9复核通过(Claude 06-17)：上半年→+3年6/30、下半年→+3年12/31（边界==6归上半年） | [✅] Phase14复验：`Phase9CertificateIT` + `Phase14E2EIT` 上半年签发有效期 `2029/6/30` |
| AT-12 | 审核全留痕 | P13 | [✅] P13复核通过(Claude 06-17·2轮)：主要审核 op（student/training/exemption 初审复审、material 初审、cert 作废重开、video 结算/复评/仲裁/确认、exchange 回滚）均富审计 old/new/bizId/target/意见/操作人/IP；按学生可查；审计不可删、参数改即生效、脱敏鉴权、查询数据范围 均过 | [✅] Phase14复验：`Phase13SystemAuditIT` + `Phase14E2EIT` 富审计与证书生命周期审计 |
| AT-13 | 数据范围权限 | P2 | [✅] P2复核通过(Claude 06-16)；P11 统计读侧按 `stats:view` 服务层范围收敛并补学院A不含学院B反例；P12 通知按当前 `user_id` 本人可见并补他人不可读/不可标记反例 | [✅] Phase14复验：Phase2~13 各范围 IT 全回归 + E2E 授权角色主流程 |
| AT-14 | 异常报告 | P10 | [✅] P10复核通过(Claude 06-17)：V-01~V-13 各一反例命中并定位行/字段/原因、异常行不入库、异常报告字段齐全 | [✅] Phase14复验：`Phase10ExchangeIT` 回归 + `Phase14E2EIT` 正例批次链路 |
