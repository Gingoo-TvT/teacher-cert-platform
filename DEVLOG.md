# DEVLOG.md — 开发日志

> 规范见 `AGENTS.md` §9。**倒序追加**（最新在最上）。每完成一个任务或每个工作会话至少一条。
> 条目模板（复制使用）：
>
> ```
> ## [YYYY-MM-DD] T-xxx 标题
> - 做了什么：
> - 关键决策与理由：
> - 问题与解决：
> - 与规格的偏差/疑问：（如有，须同步改 plan/docs 或记入待确认事项）
> - 测试：（测试名/结果）
> - 下一步：
> ```

---

## [2026-06-18] Phase 17 / WP-C 复核通过（Claude · REVIEW-GATE）✅ — 视频退回可重传
- 做了什么：复核 `feature/wp-c-video-return` 单提交 `597cad9`。读 VideoReviewStatus/Controller/ServiceImpl(+164)/IT(+106)/前端最小；clean-room：重置 schema → `mvn verify`（全新 V1–V21）+ 前端 type-check/build。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS、78/78**（Phase7 7→9）；Flyway v21（未新增迁移，退回复用 `video:confirm`/`video:arbitrate`）；前端 `vue-tsc` 无错 + build 绿。核对：① `returnReview` 置 RETURNED、清终分/仲裁/确认、解锁、富审计 `return` old/new/意见、通知学生、`ensureCanWriteReview` 数据范围；CONFIRMED 禁退回、意见必填、仅 WAIT_REVIEW/REVIEWING/NEED_REVIEW/REVIEW_COMPLETED 可退回。② 守卫 `ensureReuploadable` 仅 RETURNED 跳过 taskCount 放行，REVIEWING/NEED_REVIEW/CONFIRMED 仍拒（Phase7 既有反例不变）。③ 退回重传清旧任务(`reviewer_id=id` 避免重指派唯一冲突)/会话/分片/终分→WAIT_REVIEW + 通知。④ IT 覆盖 退回→重传→重新指派→88/84 结算 86 + 审计 + CONFIRMED 双禁。
- 放行：PROGRESS WP-C 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 18(WP-D 评审指定+分组，迁移 V22)。

## [2026-06-18] Phase 17 / WP-C 待复核小结（视频退回可重传）
- 做了什么：从 `main` 切出 `feature/wp-c-video-return`，完成视频评审退回可重传。新增 `RETURNED` 视频评审状态与 `POST /api/video/reviews/{id}/return`，退回意见必填，复用 `video:confirm`/`video:arbitrate` 权限与服务层数据范围校验，未新增 `video:return` 权限点，因此**未新增 V22**，V1~V21 保持冻结。
- 状态机与守卫：`CONFIRMED` 明确禁止退回；允许 `WAIT_REVIEW/REVIEWING/NEED_REVIEW/REVIEW_COMPLETED` 退回到 `RETURNED`。`ensureReuploadable` 仅对 `RETURNED` 放行已有旧任务下的重传，`REVIEWING/NEED_REVIEW/REVIEW_COMPLETED/CONFIRMED` 仍拒绝，Phase 7 原重传守卫反例保持。
- 重传重置：`RETURNED` 重传时软删旧 `video_review_task` 与旧上传会话/分片，清空终分、结论、仲裁人、仲裁模式、确认人、确认时间并解锁；新视频校验通过后回 `WAIT_REVIEW`，可重新指派评审并重新结算，旧分数不参与新一轮评审。
- 通知与审计：退回后通知学生；退回后重传并重新进入待评审时通知学院负责人。退回显式调用 `auditLogService.record("video", bizId, target, "return", old, "RETURNED", comment)`，记录 old/new/意见/操作人/IP/target；审计失败仍沿用既有 best-effort。
- IT 覆盖：`Phase7VideoReviewIT` 新增 `returnedVideoCanBeReuploadedReassignedAndSettledWithAudit`，贯通退回→学生重传→重新指派→评分→结算，并断言退回审计 old/new/意见；新增 `confirmedVideoCannotBeReturnedOrReuploaded`，覆盖 `CONFIRMED` 不可退回且不可重传；原 `REVIEWING/NEED_REVIEW` 重传拒绝反例继续保留。
- 前端最小变更：视频评审状态筛选增加 `RETURNED/已退回`，`video.ts` 预留 `returnVideoReview(id, comment)` API；不做完整退回/重传 UI（留 Phase 21）。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase7VideoReviewIT" verify` 通过，Phase7 **9/9**；全量 `mvn -B -ntp verify` 通过，Failsafe **78/78**；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 17 / WP-C 待复核**，等待 Claude 复核退回态、守卫边界、审计通知与全回归，未自行置 ✅。

## [2026-06-18] Phase 16 / WP-B 复核通过（Claude · REVIEW-GATE）✅ — 测试结果只确认
- 做了什么：复核 `feature/wp-b-test-confirm` 单提交 `f4ce3a8`。读 V21、控制器/Service（确认仅删手工 `save`/`update`，导入内部落库与 import/confirm/查询/有效性均在）、3 个 IT diff；clean-room：重置 dev schema → `mvn verify` 全新应用 V1–V21 + 前端 type-check/build。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS、Failsafe 76/76**（Phase8 由 4→5，新增 `manualCreateAndUpdateEndpointsAreOffline` 反例证 `POST/PUT /api/test` 已下线且不落库）；Flyway「now at v21」证 V21 全新可用；前端 `vue-tsc` 无错 + `built in 6.14s`。契约不变性已验证均经导入路径：前导零成绩文本、免考通过剔除应考科目、确认后锁定拒改（改走 import 仍报「已锁定」）、跨学院写数据范围 403。Phase2 契约钉死 auditor/academic `test:import+test:confirm` 且无 `test:edit`。V21 幂等撤 `test:edit` 保留权限点定义、V1–V20 冻结。
- 放行：PROGRESS WP-B 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 17(WP-C 视频退回可重传，迁移 V22)。

## [2026-06-18] Phase 16 / WP-B 待复核小结（测试结果只确认）
- 做了什么：从 `main` 切出 `feature/wp-b-test-confirm`，完成测试结果“只导入 + 确认”后端收口与前端最小适配。后端下线手工 `POST /api/test` 新建与 `PUT /api/test` 修改入口，保留 `GET /api/test`、`GET /api/test/{studentId}`、`GET /api/test/{studentId}/validity`、`POST /api/test/import`、`POST /api/test/import-file`、`POST /api/test/{id}/confirm`；导入复用的内部落库逻辑未删除。
- V21 撤权清单：新增 `V21__test_confirm_only.sql`，幂等撤销所有角色的 `test:edit` 授权，保留 `sys_permission` 中的 `test:edit` 权限点定义用于历史审计/外键兼容；确保 `COLLEGE_AUDITOR`、`ACADEMIC_ADMIN` 继续拥有 `test:import` 与 `test:confirm`。
- IT 调整：`Phase8TestResultIT` 中原手工 save/update 建结果的用例均改为 `/api/test/import` 导入路径后查询/确认，保留前导零成绩、免考剔除应考科目、结论有效性、锁定拒改、读写数据范围断言；新增手工 POST/PUT 下线反例与锁定后重导入拒绝反例。`Phase14E2EIT` 第 7 阶段改为导入成绩后确认，主流程证书前置仍贯通；`Phase2SecurityIT` 同步 WP-B 后 `test:edit` 不授权、`test:import/test:confirm` 保留的矩阵断言。
- 前端最小变更：移除 `frontend/src/api/testResult.ts` 的 `saveAbilityTest/updateAbilityTest`，移除 `TestResultManageView` 的录入/编辑抽屉与 `test:edit` 入口；菜单与路由不再要求 `test:edit`，保留导入、查询、确认与有效性展示能力，完整只确认 UI 留到 Phase 22 前端重建。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase2SecurityIT,Phase8TestResultIT,Phase9CertificateIT,Phase14E2EIT" verify` 通过，Failsafe 28/28；全量 `mvn -B -ntp verify` 通过，Failsafe **76/76**；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 16 / WP-B 待复核**，等待 Claude 复核 T-121~T-125、V21 撤权与导入+确认契约，未自行置 ✅。

## [2026-06-18] WP-A 复核通过（Claude · REVIEW-GATE）✅ — RBAC 基座
- 做了什么：复核 `feature/wp-a-rbac` 单提交 `6227cdd`（V20 + 9 IT + PROGRESS/DEVLOG，**无业务/main java 改动**）。读 V20 逐角色核对 §1 矩阵；读 9 个 IT diff 核对角色迁移；**clean-room**：重置 dev schema → `mvn -B -ntp verify` 让 Flyway 全新应用 V1–V20 再跑全量 IT。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS，Failsafe 75/75**（61 回归 + Phase14E2EIT 14，全在新 RBAC 模型下绿）；Flyway「Successfully applied 20 migrations, now at v20」证 V20 全新可用且幂等。V20 校验：CLERK=只读+初审、AUDITOR=复审+录入/导入/video:assign/arbitrate、ACADEMIC_ADMIN+=cert:issue、SYS_ADMIN 全权、CERT_ISSUER 软删+账号停用；revoke→regrant 幂等、V1–V19 未改。IT 校验：clerk 失去的 assign/import/edit/secondReview 正确迁移 auditor/academic，跨院写越权用例改用 auditor 仍考数据范围（非缺权），Phase2 契约用例钉死新矩阵。
- 放行：PROGRESS WP-A 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 16(WP-B)。

## [2026-06-18] WP-A 待复核小结（RBAC 基座重定义）
- 做了什么：从 `main` 切出 `feature/wp-a-rbac`，新增 `V20__rbac_regrant.sql`，仅重定义运行期 RBAC 授权矩阵与受影响 IT 登录角色；未改业务代码、数据范围语义、前端 UI 或 V1~V19 既有迁移。
- V20 授权变更：`SYS_ADMIN` 通过动态 `INSERT ... SELECT` 关联当前 `sys_permission` 全表权限；`ACADEMIC_ADMIN` 保留原授权并新增 `cert:issue`；`COLLEGE_AUDITOR` 承接学院侧 `student/training/exchange/test` 录入导入预校验、各业务复审、`video:assign/arbitrate/confirm` 等动作权；`COLLEGE_CLERK` 收敛为 `*:view`、`student:export`、`exchange:export:standard/full`、`material:batchDownload`、`student/training/material/exemption` 相关初审、`notice:view/stats:view/audit:view/dict:view`，移除 edit/import/prevalidate/test/video assign/secondReview/manage 类动作。
- CERT_ISSUER 处理：按 WP-A 要求删除 `CERT_ISSUER` 角色与角色权限/用户角色关联，并停用历史测试账号 `test_cert_issuer`；证书签发统一由 `test_academic_admin` 所属 `ACADEMIC_ADMIN` 执行。
- IT 调整：`Phase2SecurityIT` 改为断言 clerk/auditor/academic/sysadmin 新矩阵；`Phase3StudentIT`、`Phase4TrainingIT` 写侧范围反例改用 `test_college_auditor`；`Phase7VideoReviewIT`、`Phase12NotificationIT`、`Phase14E2EIT` 的 `video:assign` 改用 auditor；`Phase8TestResultIT` 的 test 录入/导入改用 auditor；`Phase9CertificateIT`、`Phase14E2EIT` 的 `cert:issue` 改用 academic admin；`Phase10ExchangeIT` 导入/预校验/确认导入改用 auditor，clerk 保留读/导出。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase2SecurityIT,Phase7VideoReviewIT,Phase8TestResultIT,Phase9CertificateIT,Phase10ExchangeIT,Phase12NotificationIT,Phase14E2EIT" verify` 通过，Failsafe 44/44；全量 `mvn -B -ntp verify` 通过，Failsafe **75/75**；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **WP-A 待复核**，等待 Claude 复核 V20 授权矩阵、CERT_ISSUER 删除策略与全回归结果，未自行置 ✅。

## [2026-06-18] T-115 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：复核 `feature/t115-e2e-split` 单提交 `96980e6`（仅 `Phase14E2EIT.java` + PROGRESS/DEVLOG）。增量基线 `main..HEAD`：无业务代码/迁移/yml/pom/前端改动。首跑因本地 MySQL/Redis/MinIO 未起导致全 IT 上下文加载失败（`Communications link failure`，环境问题非代码问题）；起 `docker-compose.dev.yml`（mysql/redis/minio）后重跑。
- 结论：**PASS**（一轮）。`mvn -B -ntp verify` **BUILD SUCCESS，Failsafe 75/75**（`Phase14E2EIT` 14 个有序阶段子用例全绿 + Phase2~13 回归 61 全绿）。读码确认 `@TestMethodOrder`+`@TestInstance(PER_CLASS)` 共享流程字段、`@BeforeAll/@AfterAll` 整链前后清理、每阶段断言其后置条件；原断言**零删减**全部迁移到位（标准导出 26 列 / H=身份证件号码 / 全列 `@` / 导出逐字段==录入、成绩 `00000000000085`、免考剔除应考科目、视频第三专家终分 83、student/training/exemption secondReview + video confirm + cert 生命周期审计）。
- 放行：PROGRESS T-115 置 ✅；合并 `main`（本地私有、无远程、不 push）。测试可维护性加固达成，主流程覆盖与断言不变。

## [2026-06-18] T-115 待复核小结（Phase14E2EIT 拆分）
- 做了什么：在 `feature/t115-e2e-split` 仅重构 `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase14E2EIT.java`，把原单一 `mainFlowFromImportToArchiveAndStandardExportIsConsistent` 拆为 14 个 `@Order` 有序子用例：导入、学生复审、培养复审、四类材料、免考、视频复评、测试结果、证书前置、生成、签发、导出、归档、标准导出终断言、审计留痕。
- 关键决策与理由：采用 `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` + `@TestInstance(PER_CLASS)` 共享流程字段，`@BeforeAll/@AfterAll` 只做整条链路前后清理；每个子用例重新登录取 token，避免测试运行超过 30 秒后 JWT 失效。未改任何业务代码、迁移、配置、依赖或前端。
- 断言核对：原有断言全部保留并迁移到对应阶段，包括标准导出 A-Z 26 列、H=`身份证件号码`、全列文本格式 `@`、导出 `studentNo/name/idCard/birthDate/teachingSubject/certNo/validUntil` 逐字段等于录入、成绩 `00000000000085`、免考剔除应考科目、视频第三专家终分 83、student/training/exemption/video/cert 审计断言。
- 测试：`mvn -B -ntp -pl platform-boot -am -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase14E2EIT" verify` 通过，`Phase14E2EIT` 14/14 绿；全量 `mvn -B -ntp verify` 通过，Failsafe 共 75/75（Phase14E2EIT 14 + Phase2~13 回归 61）。
- 下一步：T-115 已在 `PROGRESS.md` 置「待复核」，等待 Claude 复核测试拆分质量与全回归结果，未自行置 ✅。

## [2026-06-18] Phase 14 复核通过（Claude · REVIEW-GATE）✅ — 全项目收官
- 做了什么：独立复核 Phase 14 收口增量（单提交 `bccee73`）。`mvn -B -ntp verify` GREEN **62/62**（`Phase14E2EIT 1` + 全回归 Phase2~13 共 61）、前端 type-check/build 绿；全读 851 行 E2E + SPI/开关/空实现 + Dockerfile/compose/nginx + AT复验矩阵；无既有业务代码改动、无新迁移、V1–V19/治理冻结，按比例未另派代理。
- 结论：**PASS**（一轮）。E2E 主流程总闸真实贯通（导入→学生/培养复审→四类材料合格→免考通过且应考剔除→视频 2 评审+需复评+第三专家 83+确认→测试前导零成绩保留+确认锁定→证书前置→18 位生成→签发有效期 2029/6/30→导出→归档），**标准导出 26 列+H+文本 `@`+逐字段==录入**，并断言全流程审计留痕——一条用例联动复验 AT-01/02/05/06/07/08/09/10/11/12/13 + 状态机A/C；AT-01~14 整体复验归档（矩阵复验列全 ✅ + IT 依据）；M14 6 SPI + 开关默认关 + 空实现，一期零影响（61 回归证）；部署物齐备（多阶段后端 Dockerfile/前端 nginx/生产 compose 五服务健康检查+Flyway 种子/README/备份手册）。
- 放行：PROGRESS Phase 14 置 ✅、AT-01~14 复验列全 ✅；合并 `main`（本地私有，无远程，不 push）。**🎉 全项目 14 阶段（Phase 0~14）全部 ✅ 已复核，114/114 任务，AT-01~14 首验+复验全通过——项目收官。** 3 Minor 入 backlog（镜像未在 verify 内构建需交付前手动冒烟、/api/health 端点确认、E2E 单大用例可后续拆分）。

## [2026-06-17] Phase 14 待复核小结（T-109~T-114）
- 做了什么：从 `main` 切出 `feature/phase14-T109-acceptance`，完成最后收口阶段。新增 M14 外部接口空 SPI 与配置开关（统一身份、教务、学籍、电子签章、电子证照、上级平台），默认关闭且不接入既有业务；新增后端多阶段 `Dockerfile`、前端 `frontend/Dockerfile`/`nginx.conf`、生产 `docker-compose.yml`、`.env.example` 与 README 部署说明。
- 关键决策与理由：本阶段未新增 V20 迁移，V1~V19 保持冻结；Docker/compose 作为 build-only 交付物，不在自动验证中构建或启动生产应用容器，避免联网/卡死；M14 只预留接口与开关，空实现返回 disabled，不改变一期运行期行为。
- 验收归档：新增 `docs/AT验收复验矩阵.md`，逐条记录 AT-01~AT-14 的首验阶段、Phase14 复验方式、对应 IT 与结论；`PROGRESS.md` AT 跟踪的 `复验(Phase14)` 列已逐条标 ✅；`docs/phase-14-非功能部署验收.md` 验收清单已按收口结果勾选。
- E2E：新增 `Phase14E2EIT` 纳入 `mvn verify`，贯通导入→学生/培养复审→四类材料→免考→视频分差复评→测试确认→证书前置/生成/签发/导出/归档→标准导出，断言状态、AT-01/02 文本导出一致、AT-07 应考剔除、AT-08 85/60/81 终分 83、AT-09/10/11 证书前置/编号/有效期与 AT-12 留痕。
- 兼容性与非功能：Excel 文本一致性由 POI 机检覆盖，Excel/WPS 双端人工核对要求写入复验矩阵；主流浏览器回归目标记录为 Chrome/Edge/Firefox 最新稳定版；生产 compose 与 dev compose 分离，MySQL/Redis/MinIO/backend/frontend 均配置数据卷与健康检查。
- 测试：定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase14E2EIT" verify` 通过（1/1）；`mvn -B -ntp verify` 通过，Failsafe 共 **62/62**（Phase14E2EIT 1 + Phase2~13 回归 61）；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅 Vite chunk size 警告）；生产 compose 执行 `docker compose -f docker-compose.yml config --quiet` 通过，临时 dev 依赖已停止。
- 下一步：Phase 14 已置「待复核」，等待 Claude 按 `docs/REVIEW-GATE.md` 做最终复核；保持本地私有，不加远程、不 push。

## [2026-06-17] Phase 13 复核通过（Claude · REVIEW-GATE，2轮 PASS）✅
- 做了什么：复核 B1 修复增量（单提交 `3c65d4f`，7 业务 service + AuditLogServiceImpl + IT）。`mvn -B -ntp verify` GREEN **61/61**（Phase13 6/6 新增富审计反例 + 回归 Phase2~12 共 55）；读 7 service diff + AuditLogServiceImpl + 新 IT；未动迁移(V1–V19)/治理/前端。
- 结论：**PASS**。B1 闭环——主要审核/状态流转 op 均显式 `auditLogService.record(bizType,bizId,target,old,new,comment)`（捕获 oldStatus→updateById→record，附加非破坏）：student/training/exemption first+second、material first、cert void/reissue、video settle/thirdReview/arbitrate/confirm、exchange rollback；各加 `xxxTarget()` 可定位；`AuditLogServiceImpl.record` 改 best-effort（try/catch→log.warn，审计失败不中断主业务、非事务边界不污染）。反例 `majorReviewFlowsWriteRichAuditAndCanBeQueriedByStudent`：student/training/exemption 复审退回 old=SECOND_REVIEW/new=SECOND_REJECTED + bizId/comment/operator/IP/target、cert 作废 old=ISSUED/new=VOIDED，且 `/api/audit/log?studentId=` 可查到——AT-12 §7①②达成。55 回归全绿、附加非破坏。
- 放行：PROGRESS Phase 13 置 ✅、**AT-12 首验通过**（全部 P0 完成）；合并 `main`（本地私有，无远程，不 push）；启动 Phase 14 收口。Minor×4 入 backlog。

## [2026-06-17] Phase 13 复核退回修复（B1 · AT-12 审核全留痕）
- 做了什么：在原分支 `feature/phase13-T104-system-audit` 修复 `docs/reviews/phase-13-review.md` 退回项 B1。补全主要审核/状态流转 op 的显式富审计：学生基本信息 firstReview/secondReview、专业培养 firstReview/secondReview、免考 firstReview/secondReview、过程性材料 firstReview（secondReview 已有）、证书 void/reissue、视频自动结算/thirdReview/arbitrate/confirm、导入回滚 rollback。
- 关键决策与理由：沿用既有 `auditLogService.record(bizType,bizId,target,operation,old,new,comment)` 模式，在状态更新后附加记录，不改状态机/数据范围/返回语义；`AuditLogServiceImpl.record` 改为 best-effort，审计落库失败仅告警，避免通知/审计类辅助能力反向中断主流程。
- 问题与解决：证书 `recordAudit` 原 target 只有证书号，已扩展为 `id/assessmentYear/studentId/certNo`，保证作废/重开/更正都能定位业务记录；证书重开不改既有实体状态，仅审计记录 `VOIDED -> REISSUED` 的业务事件，避免触碰已通过状态机。
- 测试：新增 `Phase13SystemAuditIT.majorReviewFlowsWriteRichAuditAndCanBeQueriedByStudent`，覆盖 student/training/exemption 复审退回 old=SECOND_REVIEW/new=SECOND_REJECTED + bizId/comment/operator/IP、cert 作废 old=ISSUED/new=VOIDED + 原因，并验证 `/api/audit/log?studentId=...` 可查到上述复审记录。`mvn -B -ntp verify` GREEN 61/61（含 Phase2~12 回归）、`npm --prefix frontend run type-check` 通过、`npm --prefix frontend run build` 通过。
- 下一步：Phase 13 已在 `PROGRESS.md` 重新置「待复核」，AT-12 跟踪记录 B1 修复覆盖面，交 Claude 复核增量与回归，未自行置 ✅。

## [2026-06-17] Phase 13 复核退回（Claude · REVIEW-GATE）❌
- 做了什么：独立复核 Phase 13 增量（单提交 `8401a7e`）。`mvn -B -ntp verify` GREEN 60/60（Phase13 5/5 + 回归 Phase2~12 共 55）、前端绿；全读 AuditLogAspect/AuditLogServiceImpl/SystemManagementServiceImpl/AuditQueryMapper/V19/IT + 3 个既有改动 diff + `grep auditLogService.record(`。
- 结论：**退回**。无需返工项：参数改即生效（updateParam 校验+仅改可编辑值，反例 video.diffThreshold 12→8 即生效）、审计不可删（普通管理员 403 + 删除尝试留痕）、审计查询数据范围（audit:view 学院只见本院）、脱敏鉴权（明文证件号 403）、登录留痕、V19 backup_record+手册、AuditLogAspect 抽 AuditIp 非破坏重构、55 回归全绿。**但 B1（Major）AT-12 审核全留痕未达成**：`@AuditLog` 切面仅记 bizType/operation/operator/IP/time，**无 bizId/target/前后状态/意见**；完整审计仅 `material.secondReview` + `cert.correct`（+login/delete-rejected）。主要审核流程（student/training/exemption 初审·复审·确认、material 初审、cert 作废·重开、video 复审·确认·仲裁、import 回滚）审计行无法定位记录、无状态变化/意见，且按学生查不到其复审 → 击穿 AT-12 §7①（均留前后状态/意见）+ §7②（按学生查）。
- 退回处理：PROGRESS Phase 13 置 **复核退回**、不并 main、不置 ✅、AT-12 维持 `[~]`；codex 原分支在主要审核 op 显式 `auditLogService.record(bizType,bizId,target,operation,old,new,comment)`（pattern 已有、附加非破坏）或增强切面携带，补 student/training/exemption 复审退回 + cert 作废 的 old/new+bizId+意见 反例，重交只复核增量+回归。Minor×4 入 backlog。

## [2026-06-17] Phase 13 待复核小结（T-104~T-108）
- 做了什么：从 `main` 切出 `feature/phase13-T104-system-audit`，完成系统参数、审计日志、脱敏鉴权治理与备份记录能力。新增 `V19__system_audit.sql` 仅创建 `backup_record`，未修改 V1 `sys_param`/`audit_log` schema 或既有种子；新增 `/api/system/param`、`/api/audit/log`、`/api/system/backup` 接口与前端“参数审计备份”页面。
- 关键决策与理由：系统参数只允许更新可编辑参数的 `paramValue/description`，并按参数类型、分组和已知关键参数枚举校验；既有 `ParamService` 每次读库，更新后天然热生效。备份入口只写 `backup_record` 演练记录并链接 `docs/备份与恢复手册.md`，实际 MySQL dump/binlog/MinIO mirror 由运维执行，避免 Web 应用持有运维级权限。
- AT-12 留痕：复用 `@AuditLog` 基础切面并把 IP 解析抽到 `AuditIp`；登录成功补充 `auth/login` 审计；对材料复审补显式 rich audit，记录 `oldStatus/newStatus/comment/operator/ip/target`；审计删除拒绝自身显式留痕。审计查询支持业务类型、学生、批次、学院、时间、操作人、关键词等维度；学院范围通过操作人学院或关联业务记录学院解析，SCHOOL/SYSTEM 可查全量。
- 安全治理：普通管理员删除审计日志一律 403 且删除尝试自身留痕；无敏感导出权限访问学生明文证件号被拒；既有脱敏/水印/鉴权语义保持不变，Phase2~12 回归覆盖未破坏。产出《备份与恢复手册》，记录 MySQL 全备+binlog 时间点恢复、MinIO 版本化/镜像恢复、逻辑删除恢复和演练记录口径。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，定向 `Phase13SystemAuditIT` 5/5 通过；`mvn -B -ntp verify` 通过，Failsafe 共 60 tests，覆盖登录成功留痕、复审退回 rich audit、审计不可删且拒绝动作留痕、`video.diffThreshold=8` 即时触发需复评、敏感明文无权限拒绝、学院审计范围不含他院，并回归 Phase2~12；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 13 已在 `PROGRESS.md` 与 `docs/phase-13-系统管理与审计.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 首验 AT-12 与参数/审计/脱敏/备份治理，未自行置 ✅。

## [2026-06-17] Phase 12 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 12 增量（单提交 `0c82431`）。`mvn -B -ntp verify` GREEN **55/55**（`Phase12NotificationIT 4/4` + **回归 Phase2~11 共 51**）、前端 type-check/build 绿；全读 V18 + NotificationServiceImpl + NoticeController + ReviewNotificationHelper + **6 个触发点 service diff（逐一确认附加非破坏）** + IT；P1 轻量 + 全回归绿，按比例未另派代理。
- 结论：**PASS**（一轮）。四类触发（提交→学院教务员/负责人、退回→学生+抄送、视频分配→评审教师、导出→发起人）接入正确；**严格附加非破坏**（6 service 仅注入 helper + updateById 后 send，唯一改动为等价局部变量；51 条既有回归全绿）；**send 失败不影响主业务**（helper 每法 + service 每通道 双层 try/catch、非事务边界）；**通知本人可见**（list/unreadCount 按 currentUserId、markRead 以 id+user_id→他人 403、read-all 仅本人）；NotificationService 置 platform-system 无环、通道抽象可扩展；V18 文本字段+索引、notice:view V8 预种、V1–V17/治理未改、PROGRESS 未自 ✅。
- 放行：PROGRESS Phase 12 置 ✅（无新 AT 首验）；合并 `main`（本地私有，无远程，不 push）；启动 Phase 13（AT-12 审核全留痕首验）。4 Minor 入 backlog（send 失败未直接造例、通知在业务事务内同步发送可改 afterCommit、轮询非 SSE、list 非真分页）。

## [2026-06-17] Phase 12 待复核小结（T-101~T-103）
- 做了什么：从 `main` 切出 `feature/phase12-T101-notification`，完成 `V18__notification.sql`、`notification` 实体/Mapper/VO、`NotificationService`、`NotifyChannel` 抽象、站内信落库通道、通知中心接口、前端通知中心、顶栏未读角标与轮询。
- 触发点清单：已在 Phase3/4/5/6 状态流转处接入提交待初审/待复审提醒、退回/不通过提醒；Phase7 视频评审分配提醒评审教师；Phase10 导出完成提醒发起人。接收人通过学生关联账号、学院教务员/负责人角色、评审任务 reviewerId 与当前用户解析。
- 关键决策与理由：通知能力放在 `platform-system`，业务模块只单向依赖系统服务，避免 notification 反向依赖 business；`NotificationService.send` 内部捕获通道异常，通知落库/通道失败不回滚也不中断审核、分配、导出等主业务。
- 数据范围与前端结果：通知列表、未读计数、单条已读、全部已读均按当前登录 `user_id` 过滤；用户 A 不能读取或标记用户 B 通知。前端新增 `/api/notice` API、通知中心页面、菜单入口与未读角标，ID 仍按 string 处理。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，定向 `Phase12NotificationIT` 4/4 通过；`mvn -B -ntp verify` 通过，Failsafe 共 55 tests，覆盖材料提交待初审、退回、视频分配、导出完成、未读计数/标记已读、本人可见反例并回归 Phase2~11；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 12 已在 `PROGRESS.md` 与 `docs/phase-12-通知.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核触发点、本人可见与非破坏回归，未自行置 ✅。

## [2026-06-17] Phase 11 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 11 增量（单提交 `37b60ef`，新模块 platform-statistics）。`mvn -B -ntp verify` GREEN **51/51**（`Phase11StatsIT 5/5` + 回归 Phase2~10 共 46）、前端 `npm install`(echarts) + type-check/build 绿；读 747 行 `StatsServiceImpl` + 控制器 + IT + 前端；一路独立代理 PASS（逐条确认 8 类统计 fail-closed 收敛、无跨院泄漏）。
- 结论：**PASS**（一轮）。数据范围服务层强制（`resolveScope("stats:view")` + `scopedStudents`，空集/越范围→空、batch 按 operator/scopeJson），学院只见本院（IT 反例 学院A不含B）；材料完成率（四类全 PASSED）按类别 + 证书各状态 **与 DB 分组对账一致**（IT 双对账）；口径复用 Phase5/6/7/9 既有状态与 Phase3/4 校验器、分母 DENOMINATOR_RULE 文档化；异常可定位学生/字段；导出复用 `ExchangeExcelHelper` 文本 `@`；`stats:view` V8 预种无新迁移；新模块注册接入 boot 无环；V1–V17/治理未改、PROGRESS 未自 ✅。
- 放行：PROGRESS Phase 11 置 ✅（AT-13 统计读侧续接，无新首验）；合并 `main`（本地私有，无远程，不 push）；启动 Phase 12。5 Minor 入 backlog（SELF 范围死分支、材料类合格口径较 Phase5 略松、scopeJson 子串匹配、钻取静态、统计全量入内存/明细 200 截断）。

## [2026-06-17] Phase 11 待复核小结（T-092~T-100）
- 做了什么：从 `main` 切出 `feature/phase11-T092-statistics`，新增 `platform-statistics` 模块并接入 boot，完成 8 类统计服务、`/api/stats/{type}` 查询、`/api/stats/{type}/export` 导出、统计报表页（Vue3 + Naive UI + ECharts）、路由菜单和前端 API。
- 关键决策与理由：本阶段未新增 V18 迁移，`stats:view` 已在 V8 预种并按 COLLEGE/SCHOOL 授权；聚合查询跨多张业务表且 `import_export_batch` 无 college_id，统计读侧采用服务层 `DataScopeService.resolve("stats:view")` 明确收敛范围，而不是为多表聚合另造拦截规则。
- 统计口径：分母按 docs §6 默认“当前考核年度在册学生”；由于 `student` 表无年度字段，默认读取 `current_assessment_year` 参数并按当前数据范围内学生集合计，`assessment_year` 过滤应用于材料、免考、视频、测试、证书等带年度字段的业务表。
- 业务结果：学院提交、材料完成率、免考、视频评审、证书生成、任教学段/学科交叉、异常数据、导入导出日志均可查可导出；Excel 导出复用 `ExchangeExcelHelper.writeTableWorkbook`，列格式保持文本 `@`；异常统计复用既有学生/培养/证书校验器定位学生和字段。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，定向 `Phase11StatsIT` 5/5 通过；`mvn -B -ntp verify` 通过，Failsafe 共 51 tests，覆盖材料完成率对账、证书状态对账、学院A不含学院B、异常定位、Excel文本格式导出并回归 Phase2~10；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 11 已在 `PROGRESS.md` 与 `docs/phase-11-统计报表.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核统计口径、对账一致性、数据范围与导出文本格式，未自行置 ✅。

## [2026-06-17] Phase 10 复核通过（Claude · REVIEW-GATE，2轮 PASS）✅
- 做了什么：复核 B1/B2 修复增量（单提交 `e868997`，service +101 / IT +58）。`mvn -B -ntp verify` GREEN **46/46**（Phase10 6/6，新增 B1/B2 反例 + 回归 Phase2~9 共 40）；读 B1/B2 diff 与两条新反例逐条核；未动迁移(V1–V17)/治理/前端。
- 结论：**PASS**。B1：`ensureCanUpdateExisting` 对已存在 student/training/certificate 先校验其当前 collegeId ∈ 调用者 `exchange:import` 写范围、再断言现有学院==目标，`applyStudent` 不再跨学院改写 collegeId → 学院 A 凭学号命中他院 B 学生被 `ensureCanImportCollege(B)` 拒（反例证 B 学生 collegeId/姓名/证件号 与证书学院 不变）。B2：`confirmImport` 去 `@Transactional`，每行 `TransactionTemplate(REQUIRES_NEW)` 独立事务 + `catch(Exception)`，坏行自身回滚、已成功行各自提交，`dbText` 防异常明细二次截断（反例证 83 字超长学号坏行 → code 0、成功1/失败1，OK 行入库、坏行不存在）。
- 放行：PROGRESS Phase 10 置 ✅、AT-01/02/14 首验通过；合并 `main`（本地私有，无远程，不 push）；启动 Phase 11。Minor×7 维持 backlog。

## [2026-06-17] Phase 10 复核退回修复（B1/B2）
- 做了什么：修复 `docs/reviews/phase-10-review.md` 退回项 B1/B2。B1：确认导入更新现有 student/training/certificate 前校验其当前 `collegeId` 是否在调用者 `exchange:import` 写范围内，现有学生不再按导入行跨学院改写 `collegeId`，避免学院 A 凭学号覆盖/迁移学院 B 记录。B2：每行 `importOne` 改为 `TransactionTemplate + PROPAGATION_REQUIRES_NEW` 独立事务，行循环捕获含 `DataAccessException` 在内的异常并记录失败，坏行不回滚已成功行。
- 关键决策与理由：确认导入外层不再包大事务，批次状态和错误明细按逐行结果落库；错误明细写库前按字段长度裁剪，避免“坏行本身超长”导致记录失败原因时再次触发 DB 截断并把接口打成 500。
- 问题与解决：最初 B2 反例用超长学号触发 DB 截断，行内异常已被捕获，但 `import_error_detail.student_no` 再次写入超长值导致 500；已用 `dbText` 保护异常明细字段长度，保留失败定位能力。
- 与规格的偏差/疑问：未改已通过的 AT-01/AT-02/AT-14、读侧数据范围、回滚冲突逻辑和 V17 迁移。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `Phase10ExchangeIT` 6/6 通过；`mvn -B -ntp verify` 通过，Failsafe 共 46 tests（含新增跨学院覆盖拒绝、坏行不回滚已成功行反例）；`npm --prefix frontend run type-check`、`npm --prefix frontend run build` 通过。
- 下一步：Phase 10 已在 `PROGRESS.md` 重新置「待复核」，交 Claude 复核 B1/B2 增量与回归，未自行置 ✅。

## [2026-06-17] Phase 10 复核退回（Claude · REVIEW-GATE）❌
- 做了什么：独立复核 Phase 10 增量（单提交 `c074a48`，新模块 platform-exchange）。`mvn -B -ntp verify` GREEN 44/44、前端 type-check/build 绿；全读 1449 行 `ExchangeServiceImpl` + `ExchangeExcelHelper` + 26 列模型 + 控制器 + V17 + 588 行 IT；两路独立代理（质量 PASS / AT 代理 CONCERNS）。
- 结论：**退回**。AT-01 文本化（模型 String+写 `@`+读字符串）、AT-02 26 列 A–Z+H="身份证件号码"、AT-14 V-01~V-13 复用既有校验器且不入库、INSERT_ONLY/SKIP、回滚冲突判定、导出读侧数据范围+敏感脱敏 全过且反例齐；V17 仅新增、V1–V16 冻结、新模块接入正确、POI 受管。**但 B1（Major）导入更新写侧漏校验现有记录归属**：`importOne` `studentByNo` 无范围命中他院学生、`ensureCanImportCollege` 只校验目标学院、`applyStudent` 无条件改 collegeId → OVERWRITE/UPDATE_EMPTY 下学院 A 凭学号覆盖/迁移他院 B 学生（与 Phase 3 同类跨学院写）；**B2（Major）导入逐行事务粒度**：裸 `DataAccessException` 逃出 per-row catch 回滚整批 + studentMapper dup-catch 致 `UnexpectedRollbackException` 风险。
- 退回处理：PROGRESS Phase 10 置 **复核退回**、不并 main、不置 ✅、AT-01/02/14 维持 `[~]`；codex 原分支修 B1（更新前校验现有记录 collegeId∈写范围、collegeId 不跨范围改）/B2（per-row REQUIRES_NEW 或 catch DataAccessException 隔离坏行）+ 补反例（跨院更新被拒、坏行不污染整批、OVERWRITE/UPDATE_EMPTY 往返），重交只复核增量+回归。Minor×7 入 backlog。

## [2026-06-17] Phase 10 待复核小结（T-080~T-091）
- 做了什么：从 `main` 切出 `feature/phase10-T080-exchange`，完成 `V17__exchange.sql`、`platform-exchange` 模块、导入导出批次/异常/回滚追溯表、26 列标准 Excel 模型、模板下载、预校验中心、异常报告、确认导入/回滚、标准/完整/证书汇总/异常/附件清单导出；前端新增导入中心、导出中心、API、路由与菜单。
- 关键决策与理由：Phase10 文档中的 `V16__exchange.sql` 已过时，磁盘 max 为 V16，按 AGENTS 迁移规则使用 `V17__exchange.sql`；26 列模型全 `String`，写出列格式统一 `@`，读取按字符串取值；预校验复用既有 `NameValidator`、证件/出生日期、专业代码、培养联动、证书段码与有效期规则，避免另造口径。
- 问题与解决：导入回滚初版用完整 JSON 比对，自动审计时间字段会让刚导入且未修改的关联记录误判为冲突；已改为规范化快照比较，忽略 `createdAt/updatedAt/createdBy/updatedBy/deleted`，业务字段后续改动仍会冲突。学院导入跨院反例改为预校验合法、确认导入阶段由写侧数据范围拒绝，覆盖真实边界。
- 业务结果：V-01~V-13 预校验不入库并可下载异常报告；确认导入支持新增/覆盖/跳过重复/仅更新空字段，写批次与 record_ref；回滚支持 INSERT 逻辑删除、UPDATE 还原 before_json、已后续修改跳过并提示；导出按数据范围过滤，敏感汇总导出需要 `exchange:export:sensitive` 才返回明文，否则脱敏。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase10 共 44 tests；`Phase10ExchangeIT` 覆盖 AT-02 26列+H表头、AT-01 文本单元格/证件号/前导零学号导入导出保持、AT-14 V-01~V-13 十三条反例、异常不入库、跳过重复、导入回滚与冲突提示、导出/导入数据范围和敏感导出鉴权；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 10 已在 `PROGRESS.md` 与 `docs/phase-10-导入导出与预校验.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核 AT-01/AT-02/AT-14，未自行置 ✅。

## [2026-06-17] Phase 9 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 9 增量（单提交 `6be5f79`）。`mvn -B -ntp verify` GREEN **40/40**（`Phase9CertificateIT 7/7` + 回归 Phase2~8 共 33）、前端 `type-check`/`build` 绿；全读 595 行 `CertificateServiceImpl` + `CertSequenceMapper`(FOR UPDATE) + 状态机 + V15/V16 + 636 行 IT；两路独立代理，代理 1 给出**并发安全明确结论**。
- 结论：**PASS**（一轮）。**AT-10 18 位编号生命线安全**：`cert_sequence` 行锁(`SELECT…FOR UPDATE`)+同 `@Transactional` 自增，行锁持有至 generate 提交故并发严格串行；序号在所有可失败门之后、唯一一次 insert 之前消费，insert 冲突则整事务回滚序号回退（无空号）；`cert_no` 唯一键兜底；无 Redis INCR/max+1；50×10 线程并发反例断言 certNo 不重且序号连续 1..50。AT-09 前置聚合复用各阶段结论→缺失清单→拒；AT-11 有效期上/下半年（边界==6归上半年）。状态机 C 各流转有守卫、关键字段结构性锁定(仅 cert:correct 可改+留痕)、作废→重开关联原号(原证留 VOIDED 合验收)。读+写数据范围(读 SELF/COLLEGE/SCHOOL、写仅校级、collegeId 取自实体)。V15+V16 幂等、V1–V14 冻结、ID 命名空间不冲突；V16 把"学生看本人证书"作新迁移补授(遵守不改已发布脚本)。
- 放行：PROGRESS Phase 9 置 ✅、AT-09/10/11 首验通过；合并 `main`（本地私有，无远程，不 push）；启动 Phase 10。9 Minor 入 backlog（export/archive 用 cert:view 授权写语义异味、REISSUED 死枚举、correct 双留痕、reissue→generate 权限耦合、学院复审被 PASSED 吸收、V15 重复 seed cert.* 死号、并发例 50 vs 规格 100、list 非真分页、若干覆盖面）。

## [2026-06-17] Phase 9 待复核小结（T-072~T-079）
- 做了什么：从 `main` 切出 `feature/phase09-T072-certificate`，完成证书域后端与前端。新增 `V15__certificate.sql`（`certificate`、`cert_sequence`、证书状态字典、`cert.seq.scope`/学校码/省码参数）与 `V16__certificate_student_view.sql`（学生 `cert:view` SELF 授权补充）；新增证书实体/Mapper/Service/Controller、证书管理页、证书签发页、API、路由与菜单。
- 关键决策与理由：18 位编号按 `year + cert.school.code + education_level.ext_json.certLevelCode + cert.province.code + teaching_segment.ext_json.certSegmentCode + seq(5)` 生成；序列使用 `cert_sequence` 行、`INSERT ... ON DUPLICATE KEY UPDATE` 初始化、同事务 `SELECT ... FOR UPDATE` 自增并写证，避免 Redis INCR 或 `max(seq)+1` 空号/重号；V15 已本地应用后发现学生查看授权缺口，按 Flyway 不改已发布脚本原则用 V16 补种。
- 业务结果：前置聚合复用 Phase3/4/5/7/8 既有结论与 `AbilityTestResultService.validity()`，缺项返回缺失清单；有效期按签发上下半年文本计算；状态机 C 覆盖生成、签发、导出、归档、作废、重开与 `cert:correct` 更正留痕；`certificate` 接入 `DataScopeSqlHandler`，生成/签发/作废/重开/更正服务层防御为校级范围。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase9 共 40 tests；`Phase9CertificateIT` 7/7 覆盖 AT-09 缺过程性拒绝、AT-10 编号示例/作用域切换/50并发连续不重无空号、AT-11 有效期、锁定后直接重生成拒绝且更正留痕、作废重开、证书读+写数据范围；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 9 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核 AT-09/AT-10/AT-11，未自行置 ✅。

## [2026-06-17] Phase 8 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 8 增量（单提交 `6fca8cb`）。`mvn -B -ntp verify` GREEN **33/33**（`Phase8TestResultIT 4/4` + 回归 Phase2~7 共 29）、前端 `type-check`/`build` 绿；读 V14 + service + 控制器 + 枚举 + IT + 清理两文件；两路独立代理（业务/数据范围/导入 · 质量/迁移/前端/清理）均 PASS。
- 结论：**PASS**（一轮）。免考联动（复用 Phase6 exam-subjects，PASSED 科目剔除应考）、成绩文本 AT-01（`FastExcel useScientificFormat(false)`，21 位前导零往返一致）、结论有效性契约（合格/免考有效，为 Phase9 预留）、确认锁定守卫、免考≠过程性（独立）、读+写数据范围（写侧 collegeId 取自实体、跨院 403、学生无写权）全过。三项跨阶段顺手清理 C1（`ExemptionStatus.of()` fail-closed）/C2（`StudentServiceImpl` 死分支删除）/C3（`material:view` 补种授权）经代理逐条确认落地且回归无破坏。V1–V13/治理未改，PROGRESS 未自 ✅。
- 放行：PROGRESS Phase 8 置 ✅、AT-01 采集侧续接 P8；合并 `main`（本地私有，无远程，不 push）；启动 Phase 9。7 Minor 入 backlog（exam_subjects 快照陈旧、importFile 无行数上限、CSV 公式注入留 Phase10 导出侧、审批留痕工作流前向、material:view 暂无消费方、list 非真分页、validity/importFile 覆盖面）。

## [2026-06-17] Phase 8 待复核小结（T-068~T-071）
- 做了什么：从 `main` 切出 `feature/phase08-T068-test-result`，完成 `V14__test.sql`、`ability_test_result` 实体/DTO/VO/Mapper/Service/Controller、测试结果录入/修改/轻量导入、免考联动应考科目、确认锁定、有效性查询；前端新增测试结果 API、管理页、路由与菜单。
- 关键决策与理由：`score` 全链路 `String/VARCHAR`，只做外层空白规整、不做数值/日期转换；应考科目直接复用 Phase 6 `ExemptionService.examSubjects(studentId, year, segment)`，只按 `finalStatus=PASSED` 剔除免考科目；`ability_test_result` 接入 `DataScopeSqlHandler`，写侧 collegeId 固定取 student 实体并按 `test:edit/import/confirm` 硬校验。
- 跨阶段复检顺手项已清理：`ExemptionStatus.of()` 未知值改 fail-closed 抛错；删除 `StudentServiceImpl.fill` 中 `enforceWriteScope=false && !existing` 的未校验 collegeId 死分支；V14 幂等补种 `material:view` 权限并授予学生/学院/教务处查看范围。
- 与规格的偏差/疑问：全量 26 列预校验/批次/回滚仍按 Phase 10 实现；本阶段提供 JSON 批量导入与 xls/xlsx/csv 轻量文件导入，字段保持文本。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase8 共 33 tests；`Phase8TestResultIT` 4/4 覆盖 B 科免考通过剔除、成绩文本前导零/长串读写一致、待确认有效性为否、确认锁定拒改、免考结论不覆盖过程性、院/本人读范围和跨院写 403；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 8 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核，未自行置 ✅。

## [2026-06-17] Phase 7 复核通过（Claude · REVIEW-GATE，2轮 PASS）✅
- 做了什么：复核 B1/B2 修复增量（单提交 `75b7fa7`，4 文件）。`mvn -B -ntp verify` GREEN **29/29**（Phase7 7/7，新增重传拒绝 + 3 评委结算反例 + 回归 Phase2~6 共 22）；读 B1/B2 diff 与新反例逐条核。
- 结论：**PASS**。B1 重传守卫三入口（`initUpload` 秒传前 / `merge` / `upsertReviewAfterValidation`）：REVIEWING/NEED_REVIEW/已结算/已有任务 拒绝重传，反例证 status/videoFileId/taskCount 不变（不串分、不卡死）；B2 `settleIfReady` 泛化 N 评委（`allPairDiffWithin`+`sameConclusion`），N=2 不变、N=3 结算 82；`of()` 改 fail-closed、`locked()` 不再死代码。未动迁移/治理/已通过逻辑，前端未改。
- 放行：PROGRESS Phase 7 置 ✅、AT-08 首验通过；合并 `main`（本地私有，无远程，不 push）；启动 Phase 8。Minor 维持 backlog；跨阶段复检顺手项并入 Phase 8 派发。

## [2026-06-17] Phase 7 复核退回修复（B1/B2）
- 做了什么：修复 `docs/reviews/phase-07-review.md` 退回项 B1/B2。B1：在 `initUpload` 秒传分支、断点会话入口、`merge` 与 `upsertReviewAfterValidation` 兜底处统一执行视频重传守卫；同一学生/年度已有评审任务，或状态不属于 `WAIT_UPLOAD/VALIDATING/VALIDATION_FAILED/WAIT_REVIEW且无任务` 时拒绝重传，避免 REVIEWING/NEED_REVIEW 被学生再传重置并串用陈旧任务分。B2：`settleIfReady` 去掉两评委硬编码，按 `video.reviewerCount` 个初评做全体两两分差 ≤ 阈值且结论全一致时均分结算，否则进入 NEED_REVIEW，N=2 行为保持不变。
- 关键决策与理由：选择泛化 N 评委结算而不是限制配置，保持 T-060/确认单#11 的“可配多专家”能力；`VideoReviewStatus` 新增 `reuploadable()`，并把 `VideoReviewStatus.of()`/`VideoUploadStatus.of()` 未知值 fail-open 改为抛错，避免脏状态被当作可上传态。
- 问题与解决：新增重传反例最初使用过长 assessmentYear 触发字段截断，已按 V13 字段约束缩短测试年度；业务断言验证 REVIEWING 下 init 秒传与 merge 均拒绝、任务数与视频文件 ID 不变，NEED_REVIEW 下秒传也拒绝。
- 与规格的偏差/疑问：未改已通过的双盲、第三专家两两最小对、数据范围和鉴权逻辑；Minor 中仅低成本收紧 `of()` 未知值容错，其余继续按复核报告入 backlog。
- 测试：`mvn -B -ntp -pl platform-boot -am "-Dtest=Phase7VideoReviewIT" "-Dsurefire.failIfNoSpecifiedTests=false" test` 通过，`Phase7VideoReviewIT` 7/7；`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase7 共 29 tests（Phase7 7/7，含重传拒绝与 3 评委结算新增反例）；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 7 已在 `PROGRESS.md` 重新置「待复核」，提交后交 Claude 只复核 B1/B2 增量 + 回归。

## [2026-06-17] Phase 7 复核退回（Claude · REVIEW-GATE）❌
- 做了什么：独立复核 Phase 7 增量（单提交 `6318470`）。`mvn -B -ntp verify` GREEN（`Phase7VideoReviewIT 6/6` + 回归 Phase2~6 共 22 = 28/28）、前端 `type-check`/`build` 绿；全读 1062 行 `VideoReviewServiceImpl` + 控制器 + 状态机 + V13 + IT；三路独立代理（数据范围 PASS；业务/质量各独立判出 B1/B2）。
- 结论：**退回**。AT-08 头部全过且生命线稳：双盲"提交前互不可见"接口层硬屏蔽（无泄漏路径）、状态机B 分差/结论冲突→需复评、第三专家两两最小对 85/60/81→83、鉴权播放 401/限时预签名/水印、读+写+ASSIGNED 数据范围、大文件不进内存、权限 V8 预种、V1–V12/治理未改。**但 B1（Blocker）重传未挂可编辑态守卫**：REVIEWING/NEED_REVIEW（locked=0）下学生再传/秒传静默重置评审且不清任务 → 以陈旧分结算或永久卡死，击穿"唯一终分"；**B2（Major）`settleIfReady` 硬编码 2** 与可配 `video.reviewerCount` 矛盾（设 3 卡死）。
- 退回处理：PROGRESS Phase 7 置 **复核退回**、不并 main、不置 ✅、AT-08 维持 `[~]`；codex 原分支修 B1/B2 + 补反例（重传被拒/正确重置不串分、N 评委结算），重交后只复核增量+回归。Minor×7 入 backlog（arbitrate 忽略 conclusion、死代码、of() 容错许可态、格式/时长声明可信、list 非真分页、前端 quickHash 整文件+非 MD5、覆盖面）。

## [2026-06-17] Phase 7 待复核小结（T-057~T-067）
- 做了什么：从 `main` 切出 `feature/phase07-T057-video-review`，完成 `V13__video.sql`、分片上传会话/分片/video_review/video_review_task 表，视频上传 init/chunk/merge/progress、秒传/断点续传、MP4/大小/时长校验、评审任务分配、9 维独立评分、分差结算、第三专家/学院仲裁、结果确认、鉴权播放与动态水印；前端新增视频评审页、API、路由与菜单。
- 关键决策与理由：视频业务放 `platform-business` 并复用 `platform-file` MinIO；大文件合并优先 MinIO `composeObject`，测试小分片 fallback 仍按流拼接避免整文件入内存；合格线/分差阈值/评审人数/时长容差/视频上限/仲裁模式/播放过期时间均走 `sys_param`，V13 幂等补种；`video_review`/`video_review_task`/`video_upload_session` 接入 `DataScopeSqlHandler`，评审教师按 `reviewer_id` 只能看自己任务。
- 问题与解决：Phase 1 已存在 `video_score_dimension` 字典项，V13 只补 9 维 `ext_json` 权重而不重复造字典；提交前互不可见在服务层按当前 reviewer 过滤任务详情，管理/结算视图才返回全量评分；播放接口先做登录、权限和数据范围校验，再发限时预签名 URL 与水印信息。
- 与规格的偏差/疑问：IT 按要求不真传 2GB，使用小文件/小分片验证协议、秒传、续传，用元数据边界覆盖格式/大小/时长规则；9 维权重按 plan §15.5 默认值落地，后续学校正式模板可继续覆盖字典扩展字段。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase7 共 28 tests；`Phase7VideoReviewIT` 覆盖分片成功/秒传/断点续传、非 MP4/时长超容差拒绝、教师 A 提交后教师 B 看不到 A 分数意见、分差≤阈值结算、分差>阈值需复评、结论冲突需复评、thirdExpert 85/60/81→83、sys_param 改参生效、鉴权播放反例、院/学生/评审教师数据范围与写侧跨院 403；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 7 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核 AT-08，未自行置 ✅。

## [2026-06-17] Phase 6 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 6 增量（单提交 `df13e97`）。`mvn -B -ntp verify` GREEN（`Phase6ExemptionIT 4/4` + 回归 Phase2 2/2 + Phase3 7/7 + Phase4 5/5 + Phase5 4/4 = 22/22）、前端 `type-check`/`build` 绿；读码核 AT-07 五条 + 读写数据范围 + 可编辑态守卫 + 权限种子；三路独立代理复核均 PASS。
- 结论：**PASS**（一轮）。AT-07：三科 PASS/REJECT/FAIL 并存互不影响、漏佐证按科拒提、仅复审通过移出应考（`includedInExam=0` 唯一写在 `secondReview` PASS）、免考不写过程性表（不覆盖）、读+写数据范围（写侧 collegeId 取自 student 实体、跨学生/跨院 403 且不建行）；可编辑态守卫复用；`exemption:*` 权限点 V8 §15.1 预种无缺口；V1–V11/治理文档未动；PROGRESS 未自 ✅。
- 放行：PROGRESS Phase 6 置 ✅、AT-07 首验通过；合并 `main`（本地私有，无远程，不 push）；启动 Phase 7（视频评审，AT-08）。
- backlog（不阻断，8 Minor）：`ExemptionStatus.of()` 未知值 fail-open、`submit` 跳过 locked 检查、`list` 非真分页、404-vs-403 存在性探测、`ExemptionQuery.collegeId` 边界注释、`readPermission()` 口径、退回重提/初审 FAIL 测试覆盖、前端 segment 接线。

## [2026-06-17] Phase 6 待复核小结（T-052~T-056）
- 做了什么：从 `main` 切出 `feature/phase06-T052-exemption`，完成 `V12__exemption.sql`、免考申请/佐证实体 DTO/VO/Mapper/Service/Controller，多科免考申请、每科独立佐证上传/替换/删除/预览、提交、初审/复审三态、复审通过应考口径移出；前端新增免考管理页、API、菜单与路由。
- 关键决策与理由：免考属 `platform-business`，佐证复用 `platform-file` 的 MinIO `FileService`；读侧把 `exemption_request`/`exemption_material` 加入 `DataScopeSqlHandler`，写侧按 `student` 实体归属用 `DataScopeService.resolve(...)` 硬校验，避免 request 覆盖 collegeId；Phase 8 表未建前，以 `included_in_exam` 与 `/api/exemption/exam-subjects` 作为应考科目口径预留。
- 问题与解决：确认单 #12 说明可免科目/免考依据后续模板导入，本阶段在 `V12` 留 TODO 并放入示例字典值用于联调和反例；最初目标 IT 的 helper 重复提交导致状态机正确拒绝，已改为显式 `submit -> firstReview -> secondReview` 流转。
- 与规格的偏差/疑问：无阻塞。`ability_test_result` 仍属 Phase 8，本阶段不建表、不覆盖过程性材料结论，仅提供应考口径接口供 Phase 8 消费。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 7/7 + `Phase4TrainingIT` 5/5 + `Phase5MaterialIT` 4/4 + `Phase6ExemptionIT` 4/4（合计 22 tests）；覆盖 AT-07 多科 A 通过/B 退回/C 不通过互不影响、漏佐证拒提交、通过科目移出应考、不覆盖过程性结论、可编辑态守卫、读写数据范围；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 6 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核，未自行置 ✅。

## [2026-06-17] Phase 5 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 5 增量。`mvn -B -ntp verify` GREEN（`Phase5MaterialIT 4/4` + 回归 `Phase2 2/2`+`Phase3 7/7`+`Phase4 5/5` = 18/18）、`type-check`/`build` 绿；读码核 AT-06 聚合/上传校验/替换锁/数据范围读写/可编辑态守卫回填。
- 结论：**PASS**（一轮）。AT-06（四类缺一/不通过→不合格、全过→合格）、上传限制、替换锁、两级三态审核、process_material 读+写数据范围、MinIO 复用全过；**可编辑态守卫 backlog 跨 material+student+training 真收口**（带回归反例，清除 Phase3/4 同类 Minor）；V11/治理文档未动。
- 放行：PROGRESS Phase 5 置 ✅、AT-06 首验通过；合并 `main`（本地私有，无远程）；启动 Phase 6。
- backlog（不阻断，6 Minor）：类别合格口径是否配 sys_param、material:view 权限点种子、跨院写返 404 vs 403 等。

## [2026-06-17] Phase 5 待复核小结（T-044~T-051）
- 做了什么：从 `main` 切出 `feature/phase05-T044-process-material`，完成 `V11__process_material.sql`、过程性材料实体/DTO/VO/Mapper/Service/Controller，材料上传/预览/替换/删除、初审/复审、AT-06 四类聚合合格判定、批量下载 ZIP+清单；前端新增过程性材料页面与菜单入口。
- 关键决策与理由：材料业务放 `platform-business` 并复用 `platform-file` 的 MinIO `FileService`/预签名/对象读取；上传大小与 MIME 白名单走 `sys_param`，读侧复用 `DataScopeSqlHandler` 新增 `process_material` 表规则，写侧按 student 归属学院/本人硬校验。
- 问题与解决：用户口径中的四类材料名称与现有 `material_category` 字典/phase 文档不完全一致，本阶段按已落库标准字典四类实现并记录口径；批量下载清单采用 ZIP 内 `manifest.csv`，字段覆盖学号/姓名/类别/文件名/状态/审核人/审核时间。附带收口可编辑态守卫：student/training/material 仅 DRAFT/FIRST_REJECTED/SECOND_REJECTED 可写，在审/已通过/locked 拒绝，并补 Phase3/Phase4 反例。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 7/7 + `Phase4TrainingIT` 5/5 + `Phase5MaterialIT` 4/4（合计 18 tests）；覆盖 AT-06 缺一/不通过/全通过、上传类型/大小超限、通过后替换拒绝、material 读写数据范围、批量下载清单；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 5 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核，未自行置 ✅。

## [2026-06-16] Phase 4 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 4 增量。`mvn -B -ntp verify` GREEN（`Phase4TrainingIT 4/4` + `Phase3StudentIT 6/6`(含新 confirm 反例) + `Phase2SecurityIT 2/2` = 12/12）、`type-check`/`build` 绿；读码核 `MajorCodeValidator`/`TrainingLinkValidator`/写侧 `allowedCollegeId`/`training_profile` 数据范围规则/Phase3 confirm backlog 修复。
- 结论：**PASS**（一轮）。AT-04（专业代码 0401/0451/0453）、AT-05（学段-学科联动禁自由填、中职类别节点拒绝）、培养目标-实习地点联动、读+写两侧数据范围、状态机A 全过；Phase 3 confirm backlog 已闭环；V10/治理文档未动。
- 放行：PROGRESS Phase 4 置 ✅、AT-04/AT-05 首验通过；合并 `main`（本地私有，无远程）；启动 Phase 5。
- backlog（不阻断）：training/student「可编辑态守卫」统一化（在审/锁定记录禁写非关键字段）+ TrainingStatus.of 未知值应抛错（3 Minor）。

## [2026-06-16] Phase 4 待复核小结（T-039~T-043）
- 做了什么：从 `main` 切出 `feature/phase04-T039-training-profile`，新增 `V10__training_profile.sql`、专业培养信息实体/DTO/VO/Mapper/Service/Controller、`MajorCodeValidator` 与培养目标/学段/学科/实习地点联动校验，`training_profile` 接入 Phase 2 数据权限规则；前端新增专业培养信息表单与菜单路由；顺带修复 Phase 3 backlog：学生 confirm 不再写请求中的 `collegeId`。
- 关键决策与理由：培养信息属于 `platform-business`，写侧用 `DataScopeService.resolve("training:edit"/"training:confirm")` 做硬校验，全校放行、学院仅授权学院、学生仅本人；读侧继续复用 `DataPermissionInterceptor + DataScopeSqlHandler`，`training_profile` 院范围走 `college_id`，本人走 `student_id`；专业/培养目标/学段/地点/学科均从字典与 Phase 1 标准库/配置表取数。
- 问题与解决：首次 `mvn verify` 中 Phase4 测试姓名含 ASCII `A/B`，触发 Phase 3 姓名校验，已改纯中文测试姓名，不改业务规则；本地 V10 已应用后未再修改迁移脚本，避免 Flyway checksum 变化。Phase3 confirm 自助换学院反例已加入 `Phase3StudentIT`。
- 与规格的偏差/疑问：无阻塞。企业实习按培养目标 `secondary_vocational_school_teacher` 限制，海外实习按校内专业名称“汉语国际教育专业”限制；完整中职专业课库仍沿用 Phase 1 确认单#13 的示例库/模板导入口径。
- 测试：`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 6/6 + `Phase4TrainingIT` 4/4（合计 12 tests）；覆盖 AT-04 非法前缀拒绝/合法前缀放行、普通师范试点专业匹配、AT-05 自由填学科拒绝/类别节点拒绝/联动 options、企业/海外地点限制、多培养目标保存、两级审核、锁定拒改、写侧跨院 403、training_profile 列表学院/学生数据范围；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 4 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核，未自行置 ✅。

## [2026-06-16] Phase 3 复核通过（Claude · REVIEW-GATE，2 轮）✅
- 做了什么：第 2 轮复核 Phase 3 退回复修增量。独立 `mvn -B -ntp verify` GREEN（`Phase3StudentIT 5/5` 含新跨院反例 + `Phase2SecurityIT 2/2` 回归）、`npm run type-check` 与 `build` 绿；读码核 `allowedCollegeId` 写侧范围校验、`StudentConfirmRequest`、新反例。
- 结论：**PASS**。M1/M2（create/batchCreate/update 写侧 collegeId 按数据范围硬校验，跨院 403 且不落库/不开账号）+ M3（前端 type-check）已闭环；AT-03/AT-01/读写两侧数据范围/状态机A/字段锁定/脱敏/留痕全过；治理文档与 V9 未动。
- 放行：`PROGRESS.md` Phase 3 置 ✅、AT-03 首验通过；合并 `main`（本地私有，无远程）；启动 Phase 4。
- backlog（不阻断）：confirm 可改本人 collegeId（学生自助换学院）→ 建议快速补丁：confirm 不写 collegeId。

## [2026-06-16] Phase 3 退回复修：写侧数据范围硬校验
- 做了什么：按 `docs/reviews/phase-03-review.md` 只修 M1/M2/M3；`StudentServiceImpl` 在 create/batchCreate/update 写侧用 `DataScopeService.resolve("student:edit")` 做学院范围硬校验，COLLEGE 账号只能写入授权学院，SCHOOL/SYSTEM/LOGIN_ALL 放行；前端 `StudentManageView` 的 `row-key` 补 `Student` 类型。
- 关键决策与理由：校验放在 service 层，确保 create、batchCreate、update 共用同一规则，且 `ensureStudentAccount` 只会基于已校验的 `collegeId` 开通学生账号；学生本人 confirm 不纳入本次 `student:edit` 写侧校验，避免扩大退回范围。
- 问题与解决：原实现读侧 `@DataScope` 已生效，但写侧 `request.collegeId` 可被院级账号直接覆盖，导致跨学院建/迁学生并开账号；新增 `Phase3StudentIT.collegeClerkCannotWriteStudentOutsideAuthorizedCollege` 验证学院A教务员 create/update 指向学院B 被 403 拒绝，且学生和账号均不落库。
- 与规格的偏差/疑问：Minor 未处理，继续进 backlog。`PROGRESS.md` Phase 3 已重新置「待复核」，未置 ✅。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过；`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 5/5（合计 7 tests）。
- 下一步：交 Claude 只复核 Phase 3 退回复修增量 + 回归。

## [2026-06-16] Phase 3 待复核小结（T-030~T-038）
- 做了什么：从 `main` 切出 `feature/phase03-T030-student-basic-info`，完成 `V9__student.sql`、首个 `platform-business` 业务模块接入、学生基本信息 CRUD/简单批量录入/本人确认、四类证件校验、出生日期一致性、姓名参数化校验、脱敏、两级审核、字段锁定、学生账号自动开通、student 表接入 Phase 2 数据范围拦截器，以及教务员/学生端前端页面与菜单路由。
- 关键决策与理由：`student_no/id_card_no/birth_date` 全链路按 `String/VARCHAR` 处理，出生日期比较只做归一不改存储文本；姓名校验读取 `sys_param.validate.name.mode`；数据范围复用 `DataPermissionInterceptor + DataScopeSqlHandler`，新增 `student` 表规则，COLLEGE 走 `college_id IN (...)`，SELF 走 `student.id = current.studentId`。
- 问题与解决：完整 `mvn verify` 初次发现 Phase 2 旧探针被 Phase 3 集成测试动态创建的学生账号污染，学院教务员探针从期望 1 条变成多条；已在 Phase2/Phase3 IT 中清理 `P3%/00P3%` 测试造数，保证反例相互隔离，不改业务权限逻辑。
- 与规格的偏差/疑问：批量导入按 Phase 3 文档说明留到 Phase 10 标准导入中心；本阶段提供单条录入与学生本人确认。Phase 3 已在 `PROGRESS.md` 置「待复核」，未置 ✅。
- 测试：`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 4/4；覆盖 AT-03 四类证件正反例、身份证出生日期不一致、港澳通行证/台胞证跳过日期校验、AT-01 前导零/文本出生日期、脱敏/明文权限、锁定拒改/拒删、简单批量录入、学院A/学生本人数据范围；`npm --prefix frontend run build` 通过（仅 Vite 既有 large chunk warning）。
- 下一步：交 Claude 按 `docs/REVIEW-GATE.md` 复核 Phase 3。

## [2026-06-16] Phase 2 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：第 3 轮复核 Phase 2 退回修复增量。独立 `mvn -B -ntp clean package` 绿 + `mvn -B -ntp verify` 绿（Failsafe 自动跑 `Phase2SecurityIT` **2/2 通过**）；读码核 `DataScopeSqlHandler`/拦截器/CORS/Failsafe/CI；DB 核 §15.1 与数据范围真实表隔离。
- 结论：**PASS**。B1 数据范围经 `DataPermissionInterceptor + ParenthesedExpressionList` 在真实 `sys_user/sys_college/sys_major` 表上行级隔离生效（学院A 只见本院、学生只见本人、教务处全校）；M1 CORS 白名单、M3 反例接 CI、M4 探针真实表、M2/m7 均已修；§15.1 零误差、认证安全无回归。
- 放行：`PROGRESS.md` Phase 2 置 ✅ 已复核、AT-13 首验通过；合并 `main`（本地私有，无远程）；启动 Phase 3。轮次1 的 7 个 Minor 转 backlog。

## [2026-06-16] Phase 2 退回复修：学院数据范围真实表过滤
- 做了什么：在上一轮未提交修复基础上，只修 `Phase2SecurityIT.phase2AuthRbacAndDataScope` 第 113 行失败；`DataScopeSqlHandler` 的 COLLEGE 分支改用 JSQLParser `ParenthesedExpressionList` 构造 `IN (...)` 右值，保留 MyBatis-Plus `DataPermissionInterceptor + DataScopeSqlHandler` 方向。
- 关键决策与理由：按要求先临时打印运行期 `scopeType/collegeIds/expression`。诊断结果为 `scopeType=COLLEGE`、`collegeIds=[800000000000000201]`，并非上下文为空或走 `denyExpression`；实际 SQL 片段为 `sys_user.college_id IN 800000000000000201`，少括号导致 MySQL 语法错，接口落为 0 条。
- 问题与解决：将 `InExpression` 右值从裸 `ExpressionList` 改为 `ParenthesedExpressionList` 后，学院教务员按 `college_id IN (800000000000000201)` 过滤，可见学院 A 本院学生且不含学院 B；SELF/SCHOOL/无 `@DataScope` 分支未改。临时 `DATA_SCOPE_DEBUG` 已移除。
- 与规格的偏差/疑问：无业务规格变更。`PROGRESS.md` Phase 2 重新置「待复核」，未置 ✅。
- 测试：`mvn -B -ntp -pl platform-boot -am "-Dtest=Phase2SecurityIT" "-Dsurefire.failIfNoSpecifiedTests=false" test` 通过（2 tests）；`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 通过（clerk=1、不含学院B；student本人=1；academic全校=2）。
- 下一步：交 Claude 只复核 Phase 2 增量 + 回归。

## [2026-06-14] Phase 2 待复核小结（T-024~T-029）
- 做了什么：在 `feature/phase02-T024-authentication` 上完成 Phase 2 剩余任务：认证/JWT/验证码/refresh/失败锁定/首次改密、RBAC 权限校验、数据范围真过滤、系统用户/角色/权限/数据范围接口、前端真实登录/强制改密/动态菜单与账号权限管理页。修复 `@PreAuthorize` 抛出的 `AccessDeniedException` 被兜底为 500 的问题，补 403 映射；修复过期/无效 JWT 在 filter 中抛出 servlet error 的问题；修复 MyBatis-Plus 默认不写 null 导致账号解锁后 `locked_until` 未清空的问题。
- 关键决策与理由：运行期验证不再从 Codex 启动 8080/5173 常驻服务；Phase 2 反例改用 `@SpringBootTest(webEnvironment=RANDOM_PORT)` 集成测试承载，测试进程自然退出，不继承长期 stdout/stderr 管道。
- 问题与解决：错密锁定测试后发现 `test_sys_admin.locked_until` 残留，已在登录成功、锁定过期、改密、重置密码和测试清理中改用 `LambdaUpdateWrapper#set(..., null)` 显式清空。
- 与规格的偏差/疑问：无业务规格变更。按阶段闸门仅将 Phase 2 置「待复核」，不自行置 ✅；AT-13 记为 Codex 自测通过、待 Claude 复核。
- 测试：`mvn -B -ntp -pl platform-boot -am -Dtest=Phase2SecurityIT -Dsurefire.failIfNoSpecifiedTests=false test` 通过（2 tests）；`mvn -B -ntp package` 通过；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅 Vite 既有 large chunk warning）；DB 核验 V7/V8 success、7 角色、52 权限、104 角色权限映射、关键矩阵正反例通过；`Get-NetTCPConnection -LocalPort 8080,5173` 无监听。
- 下一步：提交 Phase 2 待复核版本，交 Claude 按 `docs/REVIEW-GATE.md` 独立复核 AT-13 与 §15.1 矩阵。

## [2026-06-14] 运行期自测防卡死规则与脚本
- 做了什么：确认 Codex/headless exec 会等待子进程 stdout/stderr 管道 EOF；若直接启动 `java -jar`、`vite dev` 等常驻服务，服务继承管道且不退出，会导致 Codex 一直 working。新增 `scripts/dev-serve.sh` / `scripts/dev-stop.sh` 与 PowerShell 等价脚本 `scripts/dev-serve.ps1` / `scripts/dev-stop.ps1`，统一用后台进程、日志重定向、健康检查、PID 文件管理常驻服务。同步更新 `AGENTS.md §6.1` 硬规则与 `HANDOFF.md` 本机操作说明。
- 关键决策与理由：保留 Bash 脚本以匹配 Git Bash 工作流，同时补 PowerShell fallback；本机当前 Git Bash 启动出现 `Bash/Service/CreateInstance/E_ACCESSDENIED`，仅 Bash 脚本无法覆盖 Windows 受限场景。脚本只管理临时目录中记录的命名服务 PID，不扫描和误杀无关进程。
- 问题与解决：本轮曾遗留后端 `java` PID 27544，经 `jcmd VM.command_line` 确认为本仓库 jar 后停止；未确认归属的 `node` 进程没有监听 5173，未做误杀。
- 修正：后续实测表明由 Codex/exec 直接调用 `dev-serve.ps1` 仍会卡住当前命令链路；因此规则已收紧为 **Codex/exec 内禁止启动任何常驻服务或包装启动脚本**，`dev-serve.*` 仅供外部终端/watchdog/Claude 复核环境使用。
- 与规格的偏差/疑问：无业务规格变更；这是执行流程与本机开发安全规则补丁。
- 测试：`dev-serve.ps1` / `dev-stop.ps1` PowerShell AST 解析通过；`dev-stop.ps1 backend/frontend` 在无服务时正确返回；`Get-NetTCPConnection -LocalPort 8080,5173` 无监听。Git Bash 当前因 `E_ACCESSDENIED` 无法执行 `bash -n`，已记录使用 PowerShell fallback。
- 下一步：继续 Phase 2；Codex 内只允许运行会自然退出的一次性命令，`scripts/dev-serve.*` 仅供外部终端/watchdog/Claude 复核环境使用。

## [2026-06-14] Phase 2 中途交接记录（T-024~T-029 未完成）
- 做了什么：继续推进 Phase 2。已在 working tree 落下认证/JWT/验证码/锁定、RBAC 鉴权、`DataScopeContext` 真过滤、系统用户/角色/权限/数据范围接口、`/api/phase2/probe` 探针接口；`mvn -B -ntp -DskipTests package` 通过。为修复本地 Flyway checksum mismatch，执行了 **仅本地开发库** 的 V8 回退（删除 `id>=800000000000000000` 的 RBAC 种子数据 + `flyway_schema_history.version='8'`），随后按最新 `V8__rbac_seed.sql` 重迁移。
- 关键决策与理由：本地 Phase 1 没保留启用学院数据，原先 `V8` 用 `MIN(sys_college)` 绑定测试账号会得到 `NULL`，导致学院范围反例不稳定。已改 `V8__rbac_seed.sql`，新增 Phase 2 专用测试学院 `PHASE2_COLLEGE_A/B`，并将学生/学院教务员/学院负责人/评审教师测试账号显式绑定到学院 A，确保 AT-13 可重复验证。
- 问题与解决：PowerShell / 工具层多次把 `Start-Process` 显示为 `aborted`，但后台 `java` 进程实际已成功启动。已确认后续续做时必须先查 `Get-Process java`、`netstat :8080`、`backend.out.log`，避免重复启动把验证环境搅乱。
- 与规格的偏差/疑问：当前后端主体实现已接近 T-027，但 **仍未完成整阶段反例验证、前端真实登录与系统安全管理页、docs/phase-02 勾选、分任务提交与 Phase 2 待复核收尾**，因此不能标记任何后续任务完成。
- 测试：`mvn -B -ntp -DskipTests package` SUCCESS；`/api/health`=200；`flyway_schema_history` 当前 V8 checksum=`-193563120`；DB 核验 7 角色、52 权限、104 角色权限映射；测试账号 `test_student/test_college_clerk/test_college_auditor/test_review_teacher` 已绑定 `college_id=800000000000000201`；`test_student` 使用 `ChangeMe123!` + 验证码登录成功，返回 `mustChangePwd=true`。
- 下一步：下个 Codex 先跑完 Phase 2 反例（401/403/首次改密/refresh/错密锁定/学生仅本人/学院 A 查不到学院 B），再补前端 `LoginView`、`stores/user.ts`、`router/index.ts`、`MainLayout.vue` 和 T-029 管理页，最后更新 `PROGRESS/docs/phase-02/DEVLOG` 并按任务循环提交。

## [2026-06-14] T-023 RBAC 表
- 做了什么：新增 `V7__rbac.sql`，创建 `sys_user`、`sys_role`、`sys_user_role`、`sys_permission`、`sys_role_permission`、`sys_user_data_scope`；`PROGRESS.md` Phase 2 置为进行中，T-023 置完成；`docs/phase-02-认证与权限.md` 记录 T-023 验收。
- 关键决策与理由：迁移版本严格按磁盘 max+1 使用 V7，未修改 V1~V6；`sys_user` 增加 `must_change_pwd`、`failed_login_count`、`locked_until` 支撑 T-024 首次改密和登录锁定；`sys_role_permission.scope_type` 保存 `SELF/COLLEGE/SCHOOL/SYSTEM/LOGIN_ALL/ASSIGNED`，用于 T-025 解释 §15.1 的本/院/校/系/✓/分配范围。
- 问题与解决：Phase 文档标题仍写“V5/V6 迁移”，与 HANDOFF/任务要求的 V7/V8 冲突；按 AGENTS §4“迁移版本以磁盘 max+1 为准”和本轮用户要求执行，并在记录中说明。Windows 环境仍存在 `Path/PATH` 重复键，`Start-Process` 初次失败；规整当前进程环境后以后端 detached + `backend.out.log`/`backend.err.log` 方式完成启动验证。
- 与规格的偏差/疑问：无阻塞。V8 需包含各角色测试账号，但账号命名/默认绑定学院专业未在规格中定义；后续 T-026 按确认单默认值实现并继续记录。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health` 返回 UP；日志显示 Flyway `Migrating schema teacher_cert to version "7 - rbac"` 且成功；Docker MySQL 只读核验 `sys_user/sys_role/sys_permission/sys_user_data_scope` 存在，`flyway_schema_history` V1~V7 `success=1`；临时后端进程已停止。
- 下一步：T-024 认证：登录/JWT/验证码/锁定。

## [2026-06-14] 待确认事项落地（学校书面确认）+ 迁移版本顺延
- 做了什么：`docs/待确认事项确认单.md` 20 项按学校 2026-06-14 书面确认回填；新增 `V6__confirmed_params.sql`（**唯一取值变更**：`validate.name.mode` strict→loose，确认单#15 放宽，兼容少数民族/外文姓名）；其余 19 项确认采用既有默认值，无需改参数。同步更新 `docs/README.md §6`、`docs/phase-03`（NameValidator 默认 loose）、`HANDOFF.md`（状态/起点/迁移）。
- 迁移版本顺延：发现 `tasks.md`/phase 文档存在版本漂移——codex 早前插入 `V4__region_seed` 使 subject_seed 顺延 V5，但下游 RBAC 仍标 `V5__rbac.sql`（与 subject_seed 冲突），且 student/training/… 全部 off-by-one。本次统一顺延：RBAC→V7、rbac_seed→V8、student→V9、…、notification→V17（磁盘 V1–V6 + 计划 V7–V17 连续无冲突）。并把 `AGENTS.md §4`/`HANDOFF §4` 改为「**迁移版本以磁盘 max+1 为准，文档编号指示性**」，避免再漂移。
- 关键确认结论：证书序列作用域 `SCHOOL_YEAR_SEGMENT`（按学段，与示例一致，解决需求 9.1 文字/示例冲突）；姓名 `loose`；身份证不加 MOD11-2；复审退回「待初审」；学生导入即开通（用户名=学号/初始密码=证件后6位）；部署 docker-compose 校内。待学校后续提供（不阻塞）：完整中职专业课库、免考依据/可免科目清单（#12/#13，模板导入）；性能指标(#19)仍待提供。
- 测试：重打 jar（确认 V6 已打入 `BOOT-INF/classes/db/migration/`）→ 启动 Flyway「Migrating … to version 6 - confirmed params / Successfully applied 1 migration, now at v6」、`flyway_schema_history` V6 `success=1`、DB `validate.name.mode=loose`。✅
- 下一步：交接 codex 从 Phase 2（`V7__rbac.sql`）开始；Claude 阶段复核。

## [2026-06-14] Phase 1 复核通过（Claude · REVIEW-GATE）
- 做了什么：按 `docs/REVIEW-GATE.md` 独立复核 Phase 1（T-011~T-022），产出 `docs/reviews/phase-01-review.md`，判定 **PASS**（无 Blocker、无 Major）。
- 独立复跑：①干净重建 `mvn clean package` 9 模块 SUCCESS + 前端 `type-check`/`vite build` 通过；②子代理按 UTF-8 代码点核对 V3 字典 vs `plan §5.2` → 12 类逐字一致、17 类型、3 类型（免考依据/科目/签发人）正确置空；③启动应用（Flyway 校验 5 迁移、schema v5、`/api/health`=UP）后运行 **15 条反例/关键用例全过**：字典逐字（全角括号）、区划完整文本、学科计数 1/23/28/27、中职类别节点禁选「任教学科类别节点不可选择」、跨学段/自由填写被拒、缓存刷新 rv1→rv2 无残留、重复学院编码被拒「学院编码已存在」。
- 维度结论：D1~D11 全过。
- Minor backlog（不阻断放行）：审计切面前后态字段未填（**Phase 3 开工前必须接入**，AT-12 生命线）；`toMajorVO` N+1；dict 删除计数口径；无 dup-key 专用异常 handler；RegionCascader `check-strategy=all`（UX）；**V5 学科为示例合成、非官方库（上线前以官方库按模板导入替换，确认单#13）**。
- 放行：`PROGRESS.md` Phase 1 → ✅ 已复核；AT-05 首验（P1 基础）记 ✅；main 快进合并至复核提交（本地私有、无远程/不 push）；准予开始 Phase 2。
- 下一步：Phase 2（T-023 RBAC 表起）由 codex 实现，Claude 阶段复核。

## [2026-06-14] Phase 1 待复核小结
- 做了什么：Phase 1（T-011~T-022）全部完成，覆盖字典/区划/任教学科/组织专业/培养目标联动配置的 Flyway 迁移、标准种子、后端接口、缓存、导入器和前端维护页；`PROGRESS.md` 已将 Phase 1 置为“待复核”，未自行标记 ✅。
- 关键决策与理由：所有字典标准值按 `plan.md §5.2/附录B` 和阶段文档核对；学校、省码、培养目标、学段、实习地点均走字典或参数，不在前端硬编码业务值；T-020 区划页因后端仅定义查询接口，按只读维护/查看页交付并记录边界。
- 问题与解决：中职完整专业课库本地无 358 项/107 类别清单，按确认单第 13 项保留模板导入，不伪造完整库；证书序列作用域冲突仍按确认单默认值 `SCHOOL_YEAR_SEGMENT` 处理，已在 Phase 0 参数中落地；Windows `PATH/Path` 重复导致 `Start-Process` 不稳定，验证时已规整环境并记录处理方式。
- 与规格的偏差/疑问：无新增阻塞；待学校后续提供完整中职专业课模板、免考依据/可免科目、签发人等可维护字典内容。
- 测试：T-011~T-022 均已完成对应 `mvn -B -ntp -DskipTests package`、前端 `type-check/build` 或运行验证；Phase 1 文档验收清单已勾选，T-DICT/T-REGION/T-SUBJ/T-GOAL 用例均有正反例记录；临时后端/前端服务已停止。
- 下一步：交给 Claude 按 `docs/REVIEW-GATE.md` 复核 Phase 1；PASS 后再进入 Phase 2。

## [2026-06-14] T-022 学校/学院/专业维护页
- 做了什么：新增 `frontend/src/api/organization.ts` 与 `OrganizationManageView.vue`；接入 `/system/organizations` 路由和“基础数据/组织与专业”菜单；页面支持学校字典展示、学院维护、专业维护、试点标识、年度版本、专业多培养目标和培养目标联动配置编辑。
- 关键决策与理由：学校、培养目标、任教学段、实习地点候选均从字典接口加载，保存时只提交 `item_code`，保持“不硬编码”和“文本化编码”口径；ID 在前端按 `string` 接收，已复核后端 `JacksonConfig` 将 `Long` 序列化为字符串，避免 JS 大整数精度风险；停用学院可查看但前端禁用新增专业入口，后端仍保留硬约束。
- 问题与解决：重启后 Docker 容器未运行，已重新 `docker compose up -d`；PowerShell 环境同时存在 `PATH/Path`，`Start-Process` 会报重复键，后续通过规整环境变量和 Node 绝对路径完成前端 dev 验证。subagent 提醒停用学院仍可发起新增专业，已增加 `canCreateMajor` 和函数入口拦截。
- 与规格的偏差/疑问：无。T-022 不新增 Flyway 和后端代码，复用 T-018 已完成且带 `@AuditLog`/`@DataScope` 的组织接口。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过；启动后 `/api/health`=UP，`/system/organizations` 返回 200，Vite 代理 `/api/dict/school/items` 与 `/api/college` 正常；API 正例验证新增学院/专业、Long id 字符串返回、专业配置 `[primary_school_teacher,junior_middle_school_teacher]` 查询返回两项、联动配置保存与查询成功；反例验证重复学院、重复专业、停用学院下新增专业、中文显示值作为培养目标/学段、默认学段不在 allowed、超长 `yearVersion` 均返回业务错误；临时数据已清理，临时服务已停止。
- 下一步：提交 T-022；执行 Phase 1 待复核闸门。

## [2026-06-14] T-021 任教学科库页 + 选择组件
- 做了什么：新增 `frontend/src/api/subject.ts`、`SubjectSelect.vue` 和 `SubjectManageView.vue`；接入 `/system/subjects` 路由与“基础数据/任教学科库”菜单；页面支持学段/年度/分类/关键词查询、学科选择校验、最近使用、Excel 导入与错误明细展示。
- 关键决策与理由：学段选项从 `teaching_segment` 字典加载，学科候选从 `/api/subject` 加载，前端不硬编码业务标准值；类别节点继续在候选中展示但禁选，和后端 `selectable=false`、`validateSelectable` 硬约束保持一致；最近使用只在通过后端校验后记录。
- 问题与解决：运行验证时先用错中职学段编码 `secondary_vocational`，实际字典/种子编码为 `secondary_vocational_school`；前端实现本身不写死学段编码，验证脚本改用字典标准编码后通过。页面分类名称解析优先使用类别节点，避免同一 `categoryNode` 下具体学科排在前面时显示成子学科名。
- 与规格的偏差/疑问：无。完整中职专业课仍按 T-017 记录的确认单第 13 项，以学校模板导入补齐；T-021 完成 AT-05 的标准库和类别禁选前端基础，后续 Phase 4 还需在培养信息表单中复用并校验。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过；启动后端与 Vite dev，`/system/subjects` 返回 200；幼儿园 1、小学 23、初中 28、高中/中职文化课 27；`keyword=电子商务` 命中 `sv_ecommerce` 与 `sv_cat_finance_commerce`，类别节点 `selectable=false`；分类 `sv_cat_finance_commerce` 返回 4 项；选择 `sv_ecommerce` 校验与最近使用记录成功；选择类别节点、跨学段、自由填写不存在学科均返回业务错误；非法学段 Excel 导入返回 `failCount=1` 且错误定位第 2 行 `segment_code`。
- 下一步：T-022 学校/学院/专业维护页。

## [2026-06-14] T-020 行政区划维护 + 级联组件
- 做了什么：新增 `frontend/src/api/region.ts`、可复用 `RegionCascader.vue` 和 `RegionManageView.vue`；接入 `/system/regions` 路由与“基础数据/行政区划”菜单；页面支持区划下级浏览、代码反查完整文本、路径标签展示和级联选择回显。
- 关键决策与理由：T-020 不新增 Flyway 和后端接口，复用 T-014/T-015 已有只读区划接口；当前后端没有区划新增/编辑/删除/停用接口，因此页面按只读维护/查看口径交付，避免做没有审计日志支撑的前端伪 CRUD；区划代码全链路保持字符串。
- 问题与解决：东莞/中山等无区县子节点的地市会返回空 children，组件在懒加载到空数组时将当前节点置为可选末级，不伪造第三级区划；Naive UI 大 chunk 警告为既有全量组件库打包警告，本任务未扩大处理范围。
- 与规格的偏差/疑问：`tasks.md` 写“维护页”，但 Phase 1 后端接口清单仅有区划查询接口，无写接口；本任务按只读维护/查看页实现，并记录边界，后续如需区划 CRUD 应单独补后端写接口、权限和 `@AuditLog`。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过；启动后端与 Vite dev，`/system/regions` 返回 200；`path?code=440106` 返回 `广东省广州市天河区`；`children?parent=440000` 返回 21 个市，`children?parent=440100` 返回 11 个区且包含 `440106`，`children?parent=441900` 返回空数组；反例 `path?code=449999`、`path?code=44010601`、`children?parent=999999` 均返回业务错误。
- 下一步：T-021 任教学科库页 + 选择组件。

## [2026-06-14] T-019 字典管理页
- 做了什么：新增 `frontend/src/api/dict.ts` 与 `DictManageView.vue`；接入 `/system/dicts` 路由和侧栏菜单；页面支持字典类型/字典项双栏维护、搜索、启停、年度版本、排序、父级编码、扩展 JSON 校验和保存后刷新。
- 关键决策与理由：管理页查询字典项固定传 `onlyEnabled=false`，确保停用项仍可维护；开发态暂不对按钮挂 `v-perm`，因为 Phase 2 前登录页不加载权限，直接挂会隐藏管理按钮；使用现有 Naive UI 和 Axios 封装，不新增前端依赖。
- 问题与解决：运行反例发现后端 `extJson` 非法 JSON 会落到 MySQL JSON 字段并返回 500，已在 `DictServiceImpl` 增加 `objectMapper.readTree` 业务校验，返回“扩展JSON格式不正确”；`NInputNumber` 严格类型可能返回 null，页面表单状态改为非空 UI state，再组装 API payload。
- 与规格的偏差/疑问：无。权限点 `dict:view`/`dict:manage` 的真实按钮权限与菜单权限仍等待 Phase 2 RBAC 接入，本任务只完成页面入口和维护闭环。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过（保留既有 Naive UI chunk 警告）；启动后端与 Vite dev，`/system/dicts` 返回 200，`/api/health` 代理成功；接口验证新增类型/项、重复类型编码、重复 `(typeCode,itemCode,yearVersion)`、非法 `extJson`、有项删除类型被拒、更新项值后缓存刷新、停用后默认查询隐藏且管理查询可见，临时数据已按逻辑删除清理。
- 下一步：T-020 行政区划维护 + 级联组件。

## [2026-06-14] T-018 学校/学院/专业 + 多培养目标 + 联动配置
- 做了什么：新增学院、专业、专业培养目标、培养目标联动配置实体/Mapper/DTO/VO、`OrganizationService` 与 `/api/college`、`/api/major`、`/api/training-goal-config` 接口；专业详情带学院名称与培养目标列表；写接口接 `@AuditLog`，查询接口接 `@DataScope`。
- 关键决策与理由：T-018 不新增 Flyway，复用 T-011 的 V2 表；`training_goal_config` 存字典 `item_code`，展示名由字典翻译；V2 未定义外键和 JSON schema，引用完整性由 service 校验；多培养目标替换采用“更新/恢复现有关系 + 软删移除项”，避免逻辑删除行仍占唯一键导致重复保存失败。
- 问题与解决：subagent 复核发现 `major_training_goal` 先删后插会被 `(major_id, training_goal_code)` 唯一键挡住，已改为读取含 deleted 的历史行后恢复/停用；学院/专业同类逻辑删除唯一键问题改为创建/更新前按含 deleted 口径检测并返回业务错误；`yearVersion` 超过 V2 `VARCHAR(16)` 曾触发数据库截断，已按表结构补 DTO 长度校验。
- 与规格的偏差/疑问：无新规格偏差。DataScope 当前仍是 Phase 0 骨架切面，T-018 已按红线完成标注，真实范围过滤依赖 Phase 2 T-025 落地；中职教师默认学段/实习地点未写种子，联动配置通过接口维护，后续 Phase 4 使用时按字典配置读取。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=UP，Flyway 校验 V1-V5 且无新迁移；接口正例验证新增学院/专业、专业配置 `[primary_school_teacher,junior_middle_school_teacher]` 查询返回两项、重复保存同一组和移除后恢复均通过、保存/查询 `primary_school_teacher` 联动配置可返回默认/可选学段与实习地点；反例验证重复学院编码、重复 `(internal_major_code, yearVersion)`、停用/不存在学院、非法培养目标、默认学段不在 allowed、中文显示值作为学段编码、超长 `yearVersion`、逻辑删除后同编码重建均返回业务错误；审计日志出现 `college:create`、`major:create`、`major:replaceTrainingGoals`、`trainingGoalConfig:save`；临时业务数据接口可见计数为 0。
- 下一步：T-019 字典管理页。

## [2026-06-14] T-017 任教学科种子（示例库）
- 做了什么：新增 Flyway `V5__subject_seed.sql`，预置 `GLOBAL` 年度任教学科示例库：幼儿园 1、小学 23、初级中学 28、高级中学/中职文化课 27、中职专业课示例 10（含 3 个类别节点、7 个具体学科）。
- 关键决策与理由：`subject_code` 使用学段前缀命名，避免同名学科跨学段触发 `(subject_code, year_version)` 唯一键冲突；类别节点 `is_category=1` 且 `category_node=subject_code`，具体学科 `is_category=0` 并归到父类别；种子用 `ON DUPLICATE KEY UPDATE` 保持幂等。
- 问题与解决：本地文档只明确数量和少量示例，没有给出小学 23、初中 28、高中 27 的完整逐项清单，也未给完整中职 358 项/107 类别清单；按确认单第 13 项“以模板为基准导入字典，年度版本”处理，T-017 只作为基础验收示例库，完整库待学校提供模板后通过 T-016 导入器导入。
- 与规格的偏差/疑问：无阻塞。普通学段为满足数量验收补足了示例项；完整中职专业课不伪造为官方完整库。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 Flyway V5 `subject seed` success=1；DB 校验 `kindergarten/primary_school/junior_middle_school/senior_middle_school` 数量为 `1/23/28/27`，中职示例 10 且 3 个类别节点；无 `(subject_code, year_version)` 重复；接口验证幼儿园仅“幼儿园”、初中 28、小学包含 `书法/舞蹈/心理健康教育/体育与健康`、高中包含 `思想政治/通用技术`；`keyword=电子商务` 命中类别与具体学科且类别 `selectable=false`；选择类别节点、跨学段、自由填写不存在学科均返回业务错误，选择 `sv_ecommerce` 成功；临时后端已停止。
- 下一步：T-018 学校/学院/专业 + 多培养目标 + 联动配置。

## [2026-06-13] T-016 任教学科标准库服务 + 导入器
- 做了什么：新增 `TeachingSubject` 实体、Mapper、`TeachingSubjectService` 与 `/api/subject` 接口；实现按学段/关键词/类别/年度查询、FastExcel 导入、可选校验、最近使用记录；导入结果返回 `total/successCount/failCount/errors`。
- 关键决策与理由：接口补可选 `yearVersion` 参数，以满足“学科库支持年度版本”验收；类别节点不从结果中过滤，而是返回 `selectable=false`，让前端置灰，同时后端 `validateSelectable` 和最近使用记录硬拒绝类别节点；最近使用当前不新增表，按 Redis key `subject:recent:{userId}:{yearVersion}:{segmentCode}` 保存，因 T-016 既有 schema 未定义持久化表。
- 问题与解决：父 POM 锁定 `cn.idev.excel:fastexcel:1.1.0`，实际读写 API 位于传递依赖 `fastexcel-core`；已在 `platform-system` 声明 `fastexcel` 并通过编译。PowerShell 直写中文 SQL 会污染编码，运行验证改用 UTF-8 `.xlsx` + 导入接口，并用 DB HEX 确认中文正确。
- 与规格的偏差/疑问：`AGENTS.md` 表格写 EasyExcel/FastExcel 3.x，但父 POM 和 HANDOFF 锁定 FastExcel 1.1.0，本任务按父 POM 锁定版本实现；完整学科数量验收依赖 T-017 `V5__subject_seed.sql`，T-016 先交付服务与导入器。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；临时 Excel 导入 3 条 `T016_IMPORT` 数据成功，HEX 验证“电子商务/电子商务类/幼儿园” UTF-8 正确；错误 Excel 返回“学段非法”和“文件内重复”两条错误且无 `T016_BAD` 入库；`keyword=电子商务` 命中具体学科和类别节点，类别节点 `selectable=false`；跨学段、自由填写不存在学科、类别节点选择/记录最近使用均返回业务错误；`subject:recent` Redis 仅保存具体学科；临时数据、审计日志、Redis key 已清理，临时后端已停止。
- 下一步：T-017 任教学科种子（示例库）。

## [2026-06-13] T-015 行政区划种子
- 做了什么：新增 Flyway `V4__region_seed.sql`，预置广东省县级以上行政区划数据：省级 1 条、地级市 21 条、县级区划 122 条；同步更新 Phase 1 文档验收记录，并将后续任教学科种子版本从 `V4__subject_seed.sql` 顺延为 `V5__subject_seed.sql`。
- 关键决策与理由：区划 `code/parent_code` 全部按字符串写入，保持文本化口径；种子使用 `ON DUPLICATE KEY UPDATE` 保持幂等；东莞市、中山市按县级以上行政区划口径作为无区县级子节点的地级市处理，不伪造第三级节点。
- 问题与解决：本地文档只给出“广东省三级数据完整可联动”和 `440106` 示例，没有完整区划清单；按 T-015 的 GB/T 2260 口径补充广东县级以上数据，并在文档记录直筒子市处理口径。Flyway 版本因 T-015 占用 V4，已同步更新 `tasks.md` 与 Phase 1 文档，T-017 改用 V5。
- 与规格的偏差/疑问：无。东莞/中山无区县级子节点属于县级以上行政区划数据口径差异，已记录在验收说明。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V4 `region seed` success=1；DB 校验广东 `level=1/2/3` 数量为 `1/21/122`；`children?parent=440000` 返回 21 个市，`children?parent=440100` 返回 11 个区且含 `440106/天河区`；`path?code=440106` 返回“广东省广州市天河区”，UTF-8 HEX 为 `E5B9BFE4B89CE79C81E5B9BFE5B79EE5B882E5A4A9E6B2B3E58CBA`；反例 `449999`、`44010601`、`parent=999999` 均返回业务错误；临时后端已停止。
- 下一步：T-016 任教学科标准库服务 + 导入器。

## [2026-06-13] T-014 行政区划三级联动
- 做了什么：新增 `SysRegion`、`SysRegionMapper`、`RegionService`、`RegionController` 与区划 VO；实现 `/api/region/children` 和 `/api/region/path`；补 `CorsConfig` 的 JSON UTF-8 charset，确保 HTTP 中文响应可被客户端正确识别。
- 关键决策与理由：区划 `code/parentCode` 全链路 `String`，避免前导零和编码语义丢失；`parent` 为空查省级，传父级查下级；`path` 拼 root→leaf 的完整文本，供后续生源地导出复用；预留 `validateTriplet` 给 Phase 3/10 生源地校验。
- 问题与解决：初次 HTTP 验证时 PowerShell 将 JSON 中文误解码，数据库 UTF-8 HEX 正确；通过为 Jackson JSON converter 增加 `application/json;charset=UTF-8` 支持解决。
- 与规格的偏差/疑问：无。T-014 使用临时区划数据验证接口，正式广东省种子在 T-015 落地。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；临时插入 `440000/440100/440106` 验证 children 省→市→区县、叶子空列表、path 3 级路径；`fullName` UTF-8 HEX 对应“广东省广州市天河区”；反例 `449999`、`44010601`、`parent=999999` 均返回业务错误；验证数据已清理，临时后端已停止。
- 下一步：T-015 行政区划种子。

## [2026-06-13] T-013 字典标准值种子
- 做了什么：新增 Flyway `V3__dict_seed.sql`，创建 17 个字典类型，预置学校 `10588/广东技术师范大学`、省码 `44/广东`，以及 plan §5.2 明确给出的标准字典项。
- 关键决策与理由：种子使用 `INSERT ... ON DUPLICATE KEY UPDATE`，可重跑且不手改库；`exemption_subject`、`exemption_basis`、`cert_issuer` 仅创建类型、不插业务项，依据确认单第 12 项“免考依据/可免科目：字典维护，初始置空”和 plan 中“学校维护”口径。
- 问题与解决：PowerShell/MySQL CLI 中文比较会受控制台编码影响，逐字一致性由 gpt-5.5/xhigh 只读 subagent 对 UTF-8 文件核对，运行侧改用 type_code/item_code/数量与 HEX 抽查验证，避免编码误判。
- 与规格的偏差/疑问：`plan.md §5.2` 的 `exemption_subject` 行示例写有“幼儿园：综合素质（幼儿园）、保教知识与能力”，但确认单第 12 项裁定初始置空；按确认单默认值实现，后续由学校提供清单维护。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V1/V2/V3 `success=1`；运行侧校验 17 个类型、学校/省码、明确值数量（5/4/4/5/2/5/5/2/2/4/4/9）通过；HEX 抽查确认全角括号与 `课件/板书` 落库。
- 下一步：T-014 行政区划三级联动。

## [2026-06-13] T-012 字典管理 CRUD + Redis 缓存
- 做了什么：新增字典类型/字典项实体、DTO/VO、Mapper、`DictService` 与 `/api/dict` 接口；接入 Redis，`GET /api/dict/{typeCode}/items` 走 `dict:items:{typeCode}` 缓存；维护字典项后删除缓存；写操作接 `@AuditLog`，查询入口接 `@DataScope`。
- 关键决策与理由：缓存用 `StringRedisTemplate` + Jackson JSON，避免引入额外缓存抽象；`onlyEnabled` 默认 true，满足按类型取启用项；有子项的字典类型禁止删除或修改编码，避免产生孤儿字典项。
- 问题与解决：`platform-system` 编译期需要 Redis/Jackson 类型，已在模块 POM 显式声明依赖；一次重打包失败由运行中的 jar 占用导致，停止临时 Java 进程后重新 `mvn package` 通过。
- 与规格的偏差/疑问：无。权限点 `dict:view`/`dict:manage` 认证鉴权将在 Phase 2 RBAC 接入，T-012 先提供接口与切面标注。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；T-DICT-1 接口验证通过：首次查询写缓存、更新后缓存 `1→0`、再查返回 `updated-value` 并重新写缓存；反例：重复字典项、有子项删除类型、有子项修改类型编码均返回业务错误；中文值写入/查询成功；审计日志产生 5 条 dict 记录；验证数据与缓存已清理，临时后端已停止。
- 下一步：T-013 字典标准值种子。

## [2026-06-13] T-011 字典/区划/学科/组织表
- 做了什么：创建分支 `feature/phase01-T011-dict-schema`；新增 Flyway `V2__dict.sql`，建 `sys_dict_type`、`sys_dict_item`、`sys_region`、`teaching_subject`、`sys_college`、`sys_major`、`major_training_goal`、`training_goal_config` 8 张表；更新 `PROGRESS.md` 与 `docs/phase-01-字典与标准数据.md` 的 T-011 验收记录。
- 关键决策与理由：T-011 仅交付表结构，不提前写 V3/V4 种子，避免越界到 T-013/T-017；带年度版本的唯一键统一使用非空默认 `GLOBAL`，避免 MySQL 唯一索引允许多个 `NULL` 导致重复项绕过；学校/专业/区划等代码字段均用 `VARCHAR`，符合文本化红线。
- 问题与解决：验证时发现年度版本若可空会削弱唯一约束，已改为 `NOT NULL DEFAULT 'GLOBAL'`；临时启动后端触发 Flyway 后已停止 8080 进程，避免遗留后台服务。
- 与规格的偏差/疑问：无。完整字典标准值、行政区划种子、任教学科示例库按任务拆分留给 T-013/T-015/T-017。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V1/V2 `success=1`；information_schema 验证 8 张表与关键索引存在；反例：重复 `sys_dict_type.type_code`、重复 `(type_code,item_code,year_version)` 均触发 `ERROR 1062 Duplicate entry`，临时验证数据计数为 0。
- 下一步：T-012 字典管理 CRUD + Redis 缓存。

## [2026-06-13] Phase 0 完成（T-003~T-010）
- 做了什么：
  - DB 链路：MyBatis-Plus(分页/乐观锁/逻辑删除/自动填充) + MySQL + Flyway V1(sys_param/audit_log/file_object + 17 参数种子) + Jackson Long→String。
  - docker-compose.dev（mysql/redis/minio）。
  - platform-file：MinIO 客户端 + FileService(上传/预签名/删除/秒传) + FileController。
  - 切面：AuditLogAspect(boot, 写 audit_log) + DataScopeAspect(security, 骨架) + UserContext(common)。
  - 前端：Vue3+Vite+TS+Naive UI 脚手架（router 守卫 + Pinia + Axios 封装 + v-perm + 登录/首页/404）。
  - CI：GitHub Actions(后端 mvn + 前端 vite build) + .editorconfig + .gitattributes(LF 规范)。
- 验证（全部通过）：`mvn package` 9 模块 SUCCESS；运行 Flyway migrate v1、4 表、sys_param 17 行、/api/health=UP、/doc.html=200；MinIO bucket 自动建 + 文件 上传→预签名→下载 内容一致；前端 `npm install` + `vite build`(2864 模块) 成功。
- 问题与解决：①Lombok optional 不向子模块传递 → 父 POM 统一声明；②MinioConfig @PostConstruct 调 @Bean 循环依赖 → 改独立 client 初始化 bucket。
- 注意：前端 2 个 npm 漏洞(dev 依赖)，后续 `npm audit`；naive-ui 全量导入主包偏大，后续按需引入。
- 下一步：Phase 1 字典与标准数据（T-011 表 → 种子 → 学科库/区划 → 前端字典页）。

## [2026-06-13] Phase 0 · Maven 骨架构建通过（T-001/T-002）
- 做了什么：父 POM + 8 子模块；`platform-common` 核心(Result/ResultCode/PageResult/BizException/BaseEntity/@AuditLog/@DataScope)；`platform-boot`(PlatformApplication/OpenApiConfig/CorsConfig/GlobalExceptionHandler/HealthController/application.yml)。
- 验证：`mvn -B -ntp -DskipTests package` → **BUILD SUCCESS**（9 模块），产出可运行 jar `platform-boot/target/teacher-cert-platform.jar`。
- 关键决策：父 POM 强制 UTF-8；锁定 Spring Boot 3.2.11 / MyBatis-Plus 3.5.7 / Knife4j 4.5.0 / FastExcel 1.1.0 / MinIO 8.5.12 / jjwt 0.12.6。
- 已知告警（无害）：platform-common 仅引 `mybatis-plus-annotation`，javac 提示找不到 `org.apache.ibatis.type.JdbcType`（注解默认值引用），警告非错误；待 system/business 引入 mybatis-plus-starter 后消失。控制台中文告警乱码=GBK 控制台渲染，源码 UTF-8 编译正常。
- 状态：T-001 ✅ / T-002 ✅ / T-003 🟦（boot 起步完成，MyBatis-Plus·Jackson·数据源待续）。
- 下一步：T-003 续(MyBatis-Plus 配置/Jackson Long→String) + T-004 Flyway V1 + T-005 docker-compose；冒烟验证 /api/health 与 /doc.html。

## [2026-06-13] 环境搭建 · 工具链 + git 初始化
- 做了什么：安装 Maven 3.9.9 到 `C:\Users\wenbibuhaoqwq\tools`，配置用户级 JAVA_HOME(Temurin JDK17)/MAVEN_HOME/PATH；`git init` 本地私有仓库(main 分支)，提交规划基线 24 文件(6948682)；新增 `.gitignore`。
- 关键决策：Maven 官方未上架 winget → 用官方二进制 + 用户级环境变量，免管理员；git 仅本地、**无远程**，确保不开源。
- 问题与解决：①winget 无 `Apache.Maven` → 改官方二进制；②安装脚本含 `Remove-Item`+`C:\Program Files` 触发保护拦截 → 删去 `Remove-Item`。
- 注意：JDK 平台默认编码 **GBK**（中文 Windows）→ 父 POM 必须强制 UTF-8（`project.build.sourceEncoding` + 编译器编码），否则中文注释/资源乱码。
- 版本：JDK 17.0.19 / Maven 3.9.9 / git 2.54.0 / Docker 已装 / Node v24.15。
- 下一步：Phase 0 T-001 Maven 多模块骨架 → 验证 `mvn package`。

## [2026-06-13] 规划阶段 · 文档体系建立（非编码）
- 做了什么：完成需求分析 → 产出 `plan.md`（含 §15 二次详查增补）、`tasks.md`（114 原子任务）、`docs/`（README + 待确认事项确认单 + phase-00~14 详细设计与验收）、`AGENTS.md`/`PROGRESS.md`/`DEVLOG.md` 流程文件。
- 关键决策与理由：
  - 证书顺序号作用域默认 `SCHOOL_YEAR_SEGMENT`（与需求 9.1 示例一致），并列入确认单待学校书面确认。
  - ORM 选 MyBatis-Plus、Excel 选 EasyExcel/FastExcel（保文本）、大视频用 MinIO 分片、迁移用 Flyway。
  - 补齐 10 项执行缺口（plan §15）：状态机不合格终止态、权限矩阵、补充表、校验精化、视频结算、导出子表列、导入两步+回滚、账号/年度、M03/M04 两级审核、序列作用域。
- 问题与解决：docx 为二进制，已解压 `word/document.xml` 提取全文分析。
- 与规格的偏差/疑问：见 `docs/待确认事项确认单.md`（20 项）。
- 测试：N/A（规划阶段）。
- 下一步：执行 Phase 0（T-001~T-010）搭建工程骨架；开工前先读 `AGENTS.md`。
