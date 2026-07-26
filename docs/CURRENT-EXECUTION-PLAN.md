# 当前统一执行计划（2026-07-26）

> 本文件是项目**当前工作队列与阶段复核状态的唯一入口**。业务规格仍按 `AGENTS.md` 规定的优先级执行：`plan.md §15` > `plan.md` 正文 > `docs/phase-NN-*` > `tasks.md`；本文件不改写业务规则，只合并分散计划、复核证据和后续顺序。
>
> 历史计划保留作决策与实现追溯，其正文中的“待复核/未合并/下一步”等状态均视为当时快照，不再单独维护。状态更新只写入本文件、`PROGRESS.md` 和 `DEVLOG.md`。

## 1. 当前基线与本轮结论

- 当前分支：`codex/phase44-remediation`。Phase 39、Phase 41、Phase 47 与 Phase 53 已独立 PASS；Phase 44 第三轮正式结论为 **CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 4 Low）**，见 `reviews/phase-44-third-remediation-rereview-2026-07-26.md`。用户已在专用全新 MySQL 8.0 / Redis 7 / MinIO 栈取得修正门禁 **16/16**，但独立复核确认 writers 丢失后 READ 过早把尚存 pending 恢复成普通版本，同一事务可把最终回滚的未提交字典值发布到共享 Redis。typeCode canonical identity、逐 owner 正常双 writer/续租与上一轮 3 Low 已关闭。下一步只做第四轮最小整改；`main` 仍为 `e4f8228`，禁止擅自 merge/push。
- Claude 当前不可用；用户于 2026-07-23 明确授权 Codex 作为本轮独立复核者。Phase 42 增量重核未采信实现自报，重新读码、核验现有真实 MySQL XML 与源码/编译时序，并独立执行安全的一次性构建门禁；按用户安全边界未重跑并发/latch、压力或任何可能属于 cyber 的验证。该应急授权只适用于本轮记录，不自动改写 `REVIEW-GATE` 的长期角色约定。
- 原复核隔离空库基线：`mvn -B -ntp clean verify` 通过，Surefire **121/121**、Failsafe **144/144**，合计 **265/265**，Flyway V1–V28 与 `R__testseed` 成功。
- WS-3 第二轮整改独立门禁：全新数据卷执行 Flyway V1–V29 成功，Surefire **121/121**、Failsafe **150/150**，合计 **271/271**；Phase 7 为 **29/29**；前端 type-check/build 通过。独立复核确认时间线、fast-hit、review 行锁和 V29 已闭环，但静态不变量仍发现 3 High / 3 Medium。
- WS-3 第三轮整改独立重核：全新数据卷 Flyway V1–V30 成功，Surefire **125/125**、Failsafe **152/152**，合计 **277/277**；Phase 7 **31/31**；前端 type-check/build、生产 Compose config 与 fat-JAR worker 通过。独立复核确认前轮正常路径有实质闭环，但仍发现 **4 High / 5 Medium**：Redis fencing token 可 ABA、server finalize/assign 竞态永久 MERGING、direct 丢失 multipart 接管永久 MERGING、持久临时卷无崩溃孤儿清扫，以及错误分类/强杀确认/容量公式/MinIO 双 client 超时/fat-JAR 自动化缺口。结论为 **CHANGES REQUESTED**，见 `reviews/ws-03-third-remediation-rereview-2026-07-23.md`。
- WS-3 第四轮整改自测：V31 数据库永久世代、随机 Redis owner、三类不可恢复 MERGING 收敛、临时卷 owner/reaper/独占锁、严格 worker 结果分类、强杀死亡确认、孤儿准入与容量原子快照、MinIO 双 client 正数有界超时、verify 内 fat-JAR 门禁均已落地。专用全新环境 Flyway V1–V31，Surefire **140/140**、Failsafe **159/159**，合计 **299/299**；Phase 7 **36/36**；前端 type-check/build 与 dev/prod Compose config 通过。
- WS-3 第四轮独立重核：冻结 `df22e5b..ee190f3`，确认第三轮 4 High / 5 Medium 均可按原问题口径关闭，但新发现 **1 High / 1 Medium / 2 Low**。High 为 JCodec 内容解析 `IOException` 被父进程误判为基础设施故障，使损坏 MP4 永久卡 `MERGING`；Medium 为 FAILED 最终对象只做一次删除且无持久 reconciliation，SERVER 另有迟到 compose 复活稳定 key 的窗口；两个 Low 为临时工件有界扫描公平性和裸机父 JVM 崩溃后的 worker 监管恢复。独立安全白名单 **17/17**、后端 package、前端 type-check/build、dev/prod Compose config 与依赖树均通过；按用户要求未执行畸形媒体、破坏性故障或攻击性并发。结论 **CHANGES REQUESTED**，见 `reviews/ws-03-fourth-remediation-rereview-2026-07-23.md`。
- WS-3 第五轮整改已实现并完成开发者动态反例：worker V4 将 JCodec 内容解析异常结构化为 invalid，同时保留真实源/结果 I/O 的基础设施语义；V32 为每一数据库世代建立持久对象候选，SERVER 改用 generation-specific key，生产启动回填 + 每分钟独立 reconciliation，`CLEANED` 墓碑持续复查迟到对象；启动对账经单线程防重入执行器异步投递，不阻塞 readiness；临时工件使用持久公平游标和 O(scanLimit) 候选内存；worker 以父 PID + `startInstant` watchdog 处理裸机父 JVM 崩溃。最终专用空库成功执行 **32 个迁移，最终版本 V32**；Surefire **148/148**、Failsafe **168/168**，合计 **316/316**；Phase 7 **44/44**、V32 迁移 IT **1/1**；T-VID-2L/2M 与对象保护/回填 **5/5**、claim/direct 终态反例 **3/3**、聚焦单测 **19/19** 均通过。此项作为整改者提交材料，由下一条第五轮独立重核给出正式结论。
- WS-3 第五轮独立重核：冻结 `ee190f3..88d3136`，确认第四轮 **1 High / 1 Medium / 2 Low 全部按原问题口径关闭**；新发现 **2 Medium**。其一是 reconciliation 虽有专用工作 executor，但 `@Scheduled` 触发仍与同步全量备份/清理共享 Spring Boot 默认单线程 scheduler，长时备份会阻止对账被提交；其二是 V32 关键台账 `video_finalization_object_candidate` 未进入“全量备份”显式表清单，灾难恢复会丢失历史世代、待清理状态与墓碑。独立安全白名单 **7/7**、后端 package、前端 type-check/build、dev/prod Compose config、报告 lint 与 `git diff --check` 均通过；按用户要求未执行畸形媒体、故障注入、进程破坏或攻击性并发。结论 **CHANGES REQUESTED**，见 `reviews/ws-03-fifth-remediation-rereview-2026-07-24.md`。
- WS-3 第六轮整改候选/开发者自测完成：生产显式配置普通 `taskScheduler` 与视频专用 `videoFinalizationReconciliationTaskScheduler`，reconciliation cron 只由后者触发；真实 scheduling 测试将同步备份确定性阻塞在默认 scheduler 时，对账仍至少连续提交两次。V32 candidate 台账已进入 37 表逻辑全量备份；隔离 scratch schema 回放 `CLEANUP_PENDING/CLEANING/CLEANED` 多 generation 全字段后，真实 reconciler 可继续清理、递增 attempt、释放 claim 并保留墓碑。最终 Surefire **149/149**、Failsafe **169/169**，合计 **318/318**、0 failure/error/skip；Phase 7 **44/44**；前端 type-check/build、dev/prod Compose config 与 `git diff --check` 均通过。提交材料见 `reviews/ws-03-sixth-remediation-submission-2026-07-24.md`。
- WS-3 第六轮独立重核：冻结 `88d3136..2886442`，确认第五轮新增的 **2 Medium 全部关闭**。双 scheduler 的 bean 选择和视频 cron 绑定确定；37 项备份清单与 V1–V32 的 37 个业务表精确一致，candidate 全字段恢复后可继续真实对账。整改者 XML 为 Surefire **149/149**、Failsafe **169/169**；独立安全门禁为后端 package、fat JAR class、前端 type-check/build、dev/prod Compose 和 diff check，均通过。仅发现“33 个迁移”应为“32 个迁移、最终 V32”的 **1 Low 非阻断勘误**。结论 **PASS**，见 `reviews/ws-03-sixth-remediation-rereview-2026-07-24.md`。
- 前端：`npm --prefix frontend run type-check`、`npm --prefix frontend run build` 通过；构建仍有 `echarts`/`naive` 大 chunk 警告。
- 编排：`docker-compose.dev.yml` 与生产 `docker-compose.yml + .env.example` 均通过 `config --quiet`。
- 当前 WS 链判定：**WS-1、WS-2、WS-3、WS-10、WS-13 PASS**。WS-3/U-002 的正式依据为 `reviews/ws-03-sixth-remediation-rereview-2026-07-24.md`；1 Low 迁移数量勘误不阻断放行。
- 缺失阶段账已完成独立复核：阶段总审计原始快照中 **Phase 29、35b、36–38、40、43、45–46、48–52 PASS；Phase 0、39、41、42、44、47、53 CHANGES REQUESTED**。Phase 42、Phase 39、Phase 41、Phase 47 与 Phase 53 已由各自后续报告更新为 PASS；当前退回项为 Phase 0、44。原始阶段总审计见 `reviews/phase-gap-audit-2026-07-23.md`。
- Phase 42 首轮独立增量重核：统一 batch 行锁、rollback 单事务补偿与 confirm 收尾 CAS 已关闭原 PG-H3 Major；现有 `Phase10ExchangeIT` **10/10**、全量 XML **149/149 + 171/171 = 320/320**，独立后端 package、前端 type-check/build 与 diff check 通过。但新发现 **1 Medium / 1 Low**：逐行 `SELECT * ... FOR UPDATE` 对每行重复装载整批 `preview_json`，万行主路径形成 O(N²) DB→JVM 数据量；错误明细锁屏障缺专用交错反例。正式结论继续 **CHANGES REQUESTED**，见 `reviews/phase-42-remediation-rereview-2026-07-24.md`。
- Phase 42 第二轮候选：逐行与错误明细事务改用固定大小的 `SELECT status ... FOR UPDATE`，rollback 单次全元数据锁保持不变；Mapper 契约和真实 confirm SQL 探针共同证明成功行、失败行、错误明细均未装载 `preview_json`。T-IMP-5C 新增 rollback 先完成/错误明细先持锁两个真实 MySQL 交错，并在负向等待前确认 rollback 已到达 MyBatis `StatementHandler.query` 执行边界。`Phase10ExchangeIT` **13/13**；全新隔离全量 **149/149 + 174/174 = 323/323**；前端 type-check/build 与 diff check 通过。提交材料为 `reviews/phase-42-second-remediation-submission-2026-07-24.md`；这些是候选证据，不改写首轮正式结论。
- Phase 42 第二轮独立重核：冻结 `ec4ca30..bd1db49`，确认首轮 **1 Medium / 1 Low 全部关闭**且无新增代码 finding。status-only 锁投影从真实逐行/错误明细调用链移除 O(N²) 数据量根因，T-IMP-5C 双向交错关闭错误明细回归缺口，rollback 完整行锁和 batch-first 协议保持。独立后端 package、前端 type-check/build 与 diff check 通过。开发者 323/323 XML 早于最终测试源文件，当前源码已重新编译但无法形成严格字节级提交证明，作为非阻断证据限制保留。结论 **PASS**，见 `reviews/phase-42-second-remediation-rereview-2026-07-24.md`。
- Phase 39 独立增量复核：冻结 `66fd2a9..34aeec7`。集中父锁、学院删除首读锁、直接学生计数，以及专业/STAFF 用户/学生/当前标准导入的在线锁接线成立；但历史跨学院 UPDATE ref 的 rollback 只锁 batch/ref/child，未锁定或验证 `before_json.collegeId`，目标学院已软删时可恢复活跃孤儿学生，原 PG-H1 High 不能关闭。另有 1 Low：并发 IT 的 contender hook 早于真实 JDBC query。独立后端 package、前端 type-check/build 与 diff check 通过；开发者 XML 329/329。结论 **CHANGES REQUESTED**，见 `reviews/phase-39-remediation-rereview-2026-07-24.md`。
- Phase 39 第二轮候选：提交 `73406ed` 在任何业务 child 锁前解析 student/training/certificate UPDATE ref 的 `before_json.collegeId`，对有效目标去重并按 ID 升序父锁；非法、缺失或已删除父级按 ref 明确冲突关闭。学院删除补 active training/certificate 直接计数，保护只恢复其中一类的部分补偿。并发 IT 改用精确 SQL + collegeId 的 `StatementHandler.query` query-entered 屏障，并新增 certificate-only 与 `A2/A1/A2` 多父锁序反例。Phase 39 **11/11**、Phase 10 **13/13**、全新无卷隔离环境全量 **149/149 + 185/185 = 334/334**；生产与最终测试只读审查均 **0 High / 0 Medium / 0 Low**。这些是候选证据；独立报告 PASS 前 Phase 39 和全项目正式状态不变。
- Phase 39 第二轮独立重核：冻结 `1a69c70..dbd8633`，代码点 `73406ed`。三类历史恢复目标已纳入 `batch → refs → college IDs 升序 → business child`，缺失/已删父级按 ref 冲突，删除侧 direct count 与 query-entered 证据成立；第一轮 1 High / 1 Low 全部关闭，本增量 0 High / 0 Medium / 0 Low。独立后端 package、前端 type-check/build、diff check 与报告 lint 通过；开发者 XML 334/334、Phase 39 11/11、Phase 10 13/13，同源时间链成立。结论 **PASS**，见 `reviews/phase-39-second-remediation-rereview-2026-07-24.md`。
- Phase 41 整改候选：代码点 `c69ea73` 将 37 表逻辑备份改为 Flyway 后的事务化整库替换恢复，真实 MySQL scratch 完成 37/37 全字段指纹、双回放、目标外行删除和失败回滚；初始化脚本对数据库名/用户名做严格标识符校验，root 凭据仅进入 0600 一次性 option file。开发者全量为 Surefire **149/149** + Failsafe **185/185** = **334/334**，前端、Compose 与静态初始化契约均通过。真实 MySQL 8.4 账号认证/精确授权回环涉及安全敏感容器和账号操作，Codex 未执行；取得用户手工 PASS 日志并完成独立重核前，Phase 41 不得置 PASS、Phase 47 不放行。
- Phase 41 独立增量重核：冻结 `ca11ecc..274a939`，代码点 `c69ea73`。PG-M2 的初始化标识符、应用口令和 root 凭据路径代码级关闭，真实 MySQL 8.4 回环仍待证；PG-H2 未关闭。新确认 **1 High / 2 Medium / 1 Low**：`SELECT *` 产物显式写入 5 个 MySQL 生成列，正常非空业务表无法恢复；hex 大文本缺客户端/服务端 packet 契约；失败用例在首个 INSERT 即失败且目标同态；仅 `workflow_dispatch` 的新 workflow 不在默认分支时不能作为合并前入口。独立后端 package、前端 type-check/build、生产 Compose 与 diff check 通过；未执行账号、容器、数据卷或任何 cyber 操作。结论 **CHANGES_REQUESTED**，见 `reviews/phase-41-remediation-rereview-2026-07-25.md`。
- Phase 41 第二轮候选：代码点 `b5ed7f5` 用 JDBC metadata 排除生成列并给 5 张生成列表造非空 fixture；文本/二进制改为 Base64，导出器在分配前执行 32 MiB 单语句预算，产物/Compose/手册统一 64 MiB 客户端与服务端 packet 契约；回滚反例先制造目标 drift，在全部正常 INSERT 后、唯一 COMMIT 前注入缺表，并断言 SQLState `42S02`、未提交、显式回滚及 37 表精确复原；旧 workflow 说明已勘误。三路内部只读静态复核为 0 High / 0 Medium / 0 Low，安全门禁为 Surefire 149/149、9 模块 package、前端、Compose 配置解析、脚本语法、stub 初始化契约与 diff check；这些只是候选证据。Codex 未运行 `Phase41BackupIT`、真实 MySQL 8.4 CLI 恢复或账号/授权回环，见 `reviews/phase-41-second-remediation-submission-2026-07-25.md`。
- Phase 41 第二轮独立静态重核：冻结 `17b11aa..ef6b550`，代码点 `b5ed7f5`。上一轮 1 High / 2 Medium / 1 Low 均达到代码级整改，未确认新增代码/配置/测试设计 finding；独立 Surefire 149/149、9 模块 package、前端 type-check/build、dev/prod Compose 配置解析、diff check 与报告 lint 通过。现有 Phase41BackupIT XML 早于第二轮源码且来自 MySQL 8.0；仓库没有 `[phase41-backup-restore-real] PASS` 或 `[phase41-mysql-init-real] PASS`，故正式结论仍为 **CHANGES_REQUESTED（0 个新增代码 finding；2 个动态证据闸门未满足）**。约 24 MiB 以上原始整行按既定整改口径失败关闭，写入侧未同界留最终全量审计。
- Phase 41 动态证据增量重核：归档 Gate A 完整日志/ XML 确认 Surefire **149/149**、`Phase41BackupIT` **1/1**、真实 MySQL 8.4 CLI 恢复和最终 PASS；Gate B 确认 MySQL 8.4 sourced/executable 两路真实账号认证、精确授权及总 PASS。上一轮 1 High / 2 Medium / 1 Low 全部关闭，正式结论 **PASS**。正式 PASS 时新增的 Gate A 冷认证缓存前置 1 Low 已在后续由示例修正、runner fail-fast、恢复手册和纯 stub CI 契约闭环；原独立报告仍保留当时的 1 Low 计数。日志 SHA、XML、源码 blob、候选特征和时间线构成高置信同源交叉证据，日志未内嵌 Git SHA/MySQL 补丁版本/收尾零清单的限制已如实记录。
- Phase 47 退回整改候选：代码 `aa6f81c` 将生命周期读取分流收窄为仅精确 `NoSuchLifecycleConfiguration` 可按无配置处理；403/500/其它 404、网络/解析异常、空错误响应和 null/empty 配置均在任何整桶写入前失败关闭。外部规则快照保留、托管规则替换与幂等跳写均有纯 Mockito 回归；新增 12/12，`platform-file` 单测 15/15，9 模块跳测 package 通过，两路内部只读终审 0 High / 0 Medium / 0 Low。该证据只构成整改候选，不是独立 PASS。
- Phase 47 第一轮整改独立重核：冻结 `b2f1f70..aa6f81c`，材料至 `e81a485`。非目标读取错误在整桶写前失败关闭，原危险侧成立；但锁定的 MinIO SDK 8.5.12 会把 `NoSuchLifecycleConfiguration` 内部转换为 `null`，候选把 null 判为非法，导致新桶永远不能创建托管规则。正向 Mockito 测试模拟了 SDK 不会向业务层抛出的异常；另有错误日志失去安全可操作分类的 1 Low。独立离线 clean test **15/15**、9 模块 package 和依赖树通过，JAR 字节码证据已归档。结论 **CHANGES_REQUESTED（1 Medium / 1 Low）**。
- Phase 47 第二轮整改候选：代码 `3bddf6c` 只把 SDK 8.5.12 高层返回的 null 解释为无配置并允许首次写一次；删除异常形式 NoSuchLifecycleConfiguration 的业务层放行，403/500/其它 404、I/O/XML、无效响应和畸形非空配置继续在整桶写前失败关闭。失败日志只输出固定六类本地枚举；候选当时的 Logback 断言证明现有事件的格式化消息、throwable proxy 与 raw 参数不含已列敏感值，但没有约束未来额外 raw String，这一证据边界已由正式报告更正。最终 `platform-file` **16/16**（生命周期 **13/13**）、9 模块 package、diff check 通过；提交材料为 `reviews/phase-47-second-remediation-submission-2026-07-26.md`，仅构成候选证据。
- Phase 47 第二轮独立重核：冻结 `aa6f81c..3bddf6c` 两个生产/测试文件并核对材料至 `db89d6e`，确认 SDK null sentinel、失败关闭、规则保留/替换与六类安全日志分类成立；第一轮 1 Medium / 1 Low 全部关闭。独立离线 `clean test` **16/16**（生命周期 **13/13**）、后端 9 模块 package、MinIO/SLF4J/Logback 合同和 diff check 均 PASS。正式结论 **PASS（0 Critical / 0 High / 0 Medium / 1 Low 非阻断）**，见 `reviews/phase-47-second-remediation-rereview-2026-07-26.md`。
- Phase 47 PASS 后 Low 闭环：提交 `191a3ad` 将 raw 参数数组收紧为恰含一个生产 `LifecycleFailureCategory`，格式化消息和 raw 值均检查固定/附加敏感哨兵；负向夹具用真实生产枚举证明额外敏感 String 必须被拒绝，并补 unknown code + HTTP 503 回退。模块 **18/18**（生命周期 **15/15**）、后端 9 模块 package、diff check 与独立只读复核 **0 Critical / 0 High / 0 Medium / 0 Low**；原正式报告的 1 Low 计数保持为复核时快照。
- Phase 53 退回整改候选（历史快照）：代码 `b9abc6c` 将四个对象候选所用的三个 classpath 资源按大小/原始 SHA-256 预校验并发布到内容版本 key；版本 key 缺失才上传，已存在则核对大小、Content-Type 与读回摘要，预存污染失败关闭且不覆盖。视频钉死 tree 指纹、900 秒、900 帧、H264、策略哈希和探测版本并复用生产验收策略。旧固定 key 保留；全部对象验证后，单事务切换 `file_object`、过程/免考材料、`video_review` 与 `video_upload_session` 引用/元数据。候选聚焦 31/31、全量离线 195/195、后端 9 模块 package、fat JAR 与 diff check 均 PASS；当时仍待用户动态门禁，后续正式状态以下一条独立重核为准。
- Phase 53 上一轮独立增量重核（历史快照）：冻结 `4996811..b9abc6c` 与材料/证据至 `8a772fe`，确认真实 MP4、内容版本对象、污染失败关闭、可信探测、五类引用原子收敛、旧库升级/回滚/幂等和浏览器播放均按行为口径关闭原 Major/补充 High。独立离线 `clean test` **195/195**、聚焦 **31/31**、后端 9 模块 package、前端 type-check/build、fat JAR 资源与 diff check 均 PASS。但动态报告引用的 12 个启动日志受 `.gitignore` 排除，fresh clone 无法复核关键时序，新增 **1 Medium**；另有真实 packaged MP4 探测未自动化和已失效凭据证据未脱敏 **2 Low**。当时正式结论为 **CHANGES_REQUESTED**，后续状态以下一条证据整改重核为准。
- Phase 53 证据整改独立增量重核：冻结 `8d42dcc..34e4d51`，确认 12 份逐字节 `.log.txt` 与 manifest、上一轮原始清单及 Git blob 四路哈希一致，全部受跟踪且由 `.gitattributes -text -diff` 防止 EOL 改写；旧完整临时口令与完整预签名字段值已从当前证据树移除。独立离线 `clean test` **195/195**、报告 lint 与 diff check 均 PASS。新增 3 个非阻断 Low：packaged MP4 真实探测仍未自动化、扫描说明保留凭据派生片段且 secret-lint 零命中不可复现、11 份日志保留本机用户名/绝对路径。正式结论 **PASS**。
- Phase 44 退回整改独立增量重核：冻结 `f9ccda3..f9aec54`。PG-M3 的同事务/同连接 multi-values 真批量、500 分块、外层回滚与锁等待反例成立，原 Major 可关闭；PG-M4 未关闭。4 个 Medium 为：①本地 cache 第一次纪元检查后先公开陈旧值、二次检查后才补删；②Redis version 推进、payload 删除与两个 Caffeine 逐出非原子且故障耦合，payload hit 不验 version；③`dict:items:` / `dict:items:ver:` 对后端合法 typeCode 可确定碰撞；④锁等待 IT 未恢复池化连接的 `innodb_lock_wait_timeout`。3 个 Low 为事务期间未提交 read-through 可短暂供应、`@Param` 框架说明错误、治理/提交摘要漂移。独立离线 `platform-system` 21/21、9/9 模块 package、diff check 与报告 lint PASS；候选 XML 216/216 + 196/196 只作交叉证据。正式结论 **CHANGES_REQUESTED**。
- Phase 44 第二轮整改独立增量重核：冻结 `f9aec54..25f8b1c`。上一轮 Caffeine 精确键 publish、Redis 单写原子失效/版本戳/步骤隔离、前缀碰撞、MySQL session 污染与精确键未提交 read-through 均按原问题口径关闭；但新增 **2 Medium / 3 Low**。Medium：①Redis pending 只有可覆盖的单个 `P:<uuid>`，无 owner/refcount，固定 60 秒租约也无 transaction timeout/续租保证；②MySQL `_ai_ci` 的 typeCode 大小写等价类与大小写敏感 Redis/Caffeine key 不一致，可漏 pending/逐出。Low：`@Param` 注释仍与 MP 3.5.16 相反；registration failure 测试没有进入注册分支；两个锁测试缺 contender-started 证据。独立离线 `platform-system` 46/46、9/9 模块 package、diff check 与报告 lint PASS；候选 XML 241/241 + 200/200 = 441/441 只作本机交叉证据。正式结论 **CHANGES_REQUESTED**。
- Phase 44 第三轮整改候选（候选时快照）：冻结 `25f8b1c..8ff544d`（16 路径）。候选以 Redis TIME + 逐 owner ZSET + 后台续租 + `beforeCommit` 强校验关闭单值 pending/固定 TTL 问题；DB/Redis/Caffeine/前端统一 `trim + ASCII 校验 + Locale.ROOT 小写` identity，payload schema v2 拒绝旧包络；3 个 Low 均补到真实分支/排队点。整改者安全门禁为 `platform-system` 44/44、boot test-compile、9/9 module package、前端 type-check/build、diff check；当时真实 MySQL/Redis `Phase44CacheCommitWindowIT` 16/16 尚未由 Codex 执行，后续正式状态以下一条独立复核记录为准。
- Phase 44 第三轮独立增量重核：用户逐字执行提交材料门禁时因 parent Failsafe 在 reactor root 零匹配而失败；加入 `-Dfailsafe.failIfNoSpecifiedTests=false` 并精确核对目标计数后，专用真实依赖栈 `Phase44CacheCommitWindowIT` **16/16 PASS**，XML/testcase/hash/time chain 成立。独立离线 `platform-system` **44/44**、9 模块 package、前端 type-check/build 与 diff check PASS。原 typeCode identity Medium 与 3 Low 关闭，逐 owner 正常路径成立；但 owner-loss 用例删 writers 后立即提交，漏掉同事务回读。生产 READ 在 ZSET 空但 `P:ACTIVE` 尚存时立即正常化，允许本事务 read-your-writes V2 被 PUT 到共享 Redis，直至 `beforeCommit` 回滚，故正式结论 **CHANGES_REQUESTED（1 Medium / 4 Low）**。Low 为门禁命令、Compose/.env 参数透传、禁混部 runbook 与棕地 identity preflight。
- 全项目仍因 **Phase 0、44 两个退回阶段**保持 **CHANGES_REQUESTED**：WS-3、Phase 42、Phase 39、Phase 41、Phase 47 与 Phase 53 已独立 PASS；Phase 44 第三轮已取得动态证据但正式独立复核仍退回，Phase 0 验收基线欠账仍在。Phase 7 两项广覆盖证据债、有效预签名链接的 bearer 合同与“未登录失败”规格冲突、Phase 39 三个基线债务候选及 Phase 41 大行/生产灾备边界留最终全量审计复查。
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
| Phase 7 | 历史阶段已完成；当前叠加 WS-3 第六轮整改 | `reviews/phase-07-review.md` 历史 PASS；`reviews/ws-03-sixth-remediation-rereview-2026-07-24.md` PASS | ✅ WS-3 当前覆盖层独立 PASS；真实 2GB/非允许编码/不可解码首帧证据债继续保留 |
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
| Phase 39 | 第二轮整改 `73406ed`（分支 `feature/phase39-college-child-lock`） | `reviews/phase-39-second-remediation-rereview-2026-07-24.md` PASS | ✅ 独立复核 PASS；第一轮 1 High / 1 Low 全部关闭；三个基线债务候选留最终全量审计 |
| Phase 40 | 已合并 | `reviews/phase-40-review.md` PASS | ✅ 已复核 |
| Phase 41 | 第二轮整改 `b5ed7f5`、材料/HEAD `ef6b550`（分支 `codex/phase41-second-remediation`） | `reviews/phase-41-second-remediation-dynamic-evidence-rereview-2026-07-25.md` PASS | ✅ 独立复核 PASS；真实 MySQL 8.4 恢复与账号/授权门禁闭合；正式 PASS 时的 1 Low 已后续闭环 |
| Phase 42 | 第二轮整改 `bd1db49` | `reviews/phase-42-second-remediation-rereview-2026-07-24.md` PASS | ✅ 独立复核 PASS；原 Major 与首轮 1 Medium / 1 Low 全部关闭；严格提交级动态同源性留非阻断限制 |
| Phase 43 | 已合并（含 43.1–43.4） | `reviews/phase-43-review.md` PASS | ✅ 已复核 |
| Phase 44 | 已合并（含 44a–44f）；第三轮整改 `8ff544d` 未合并 | `reviews/phase-44-third-remediation-rereview-2026-07-26.md` CHANGES_REQUESTED（1 Medium / 4 Low） | ❌ 第三轮独立增量复核退回；owner-loss 未提交 Redis 发布阻断，Phase 0 不放行 |
| Phase 45 | 已合并 | `reviews/phase-45-review.md` PASS | ✅ 已复核 |
| Phase 46 | 已合并 | `reviews/phase-46-review.md` PASS | ✅ 已复核 |
| Phase 47 | 原实现已合并；第二轮整改 `3bddf6c`、PASS 后 Low 闭环 `191a3ad` 未合并 | `reviews/phase-47-second-remediation-rereview-2026-07-26.md` PASS | ✅ 独立复核 PASS；第一轮 1 Medium / 1 Low 全部关闭；正式 PASS 时新增的 1 Low 已后续闭环 |
| Phase 48 | 已合并 | `reviews/phase-48-review.md` PASS | ✅ 已复核 |
| Phase 49 | 已合并 | `reviews/phase-49-review.md` PASS | ✅ 已复核 |
| Phase 50 | 已合并 | `reviews/phase-50-review.md` PASS | ✅ 已复核 |
| Phase 51 | 已合并 | `reviews/phase-51-review.md` PASS | ✅ 已复核 |
| Phase 52 | 已合并 | `reviews/phase-52-review.md` PASS | ✅ 已复核 |
| Phase 53 | 原实现已合并；代码整改 `b9abc6c`、证据整改 `34e4d51` 未合并 | `reviews/phase-53-evidence-remediation-rereview-2026-07-26.md` PASS（3 Low，均非阻断） | ✅ 独立增量复核 PASS；上一轮 1 Medium 与完整秘密值证据 Low 关闭，媒体探测自动化及两项证据卫生 Low 留稳定发布前处理 |

## 3. 当前审计整改工作包

| 工作包 | 当前提交 | 自测 | 状态/放行条件 |
|---|---|---|---|
| WS-1 IT fixture 隔离 | `25be4bd` | demo 常驻双跑 27/27；纳入 265/265 | ✅ 独立复核 PASS；`reviews/ws-01-review-2026-07-23.md` |
| WS-2 凭据硬化 | `9bdee8f` | 目标单测 105/105；专项真实依赖组合 50/50；纳入 265/265 | ✅ 独立复核 PASS；`reviews/ws-02-review-2026-07-23.md` |
| WS-10 profile fail-fast | `32905a5` | 启动型单测、Compose；纳入 265/265 | ✅ 独立复核 PASS；`reviews/ws-10-review-2026-07-23.md` |
| WS-13 RBAC 授权天花板 | `acfc3b6` | WS13 IT 4/4、授权矩阵单测；纳入 265/265 | ✅ 独立复核 PASS；`reviews/ws-13-review-2026-07-23.md` |
| WS-3 MinIO 预签名直传 | 原实现 `338bc91`；首批 `ca400f1`；第二轮 `32da735`；第三轮 `df22e5b`；第四轮 `ee190f3`；第五轮 `88d3136`；第六轮 `2886442` | 149/149 + 169/169 = 318/318；Phase 7 44/44；调度隔离 3/3；candidate restore 1/1；独立 package/前端/Compose/diff 通过 | ✅ **独立复核 PASS**；第五轮 2 Medium 已关闭；1 Low 迁移数量勘误不阻断；Phase 53 demo High 与后续证据 Medium 均已关闭 |

统一风险证据与修复顺序见 `reviews/current-ws-chain-audit-2026-07-23.md`。

## 4. 统一优先队列

### P0：复核与发布门禁

1. **U-001 CI 可复现性（WS-3 Major-3）—✅ 独立重核 PASS**：后端 job 的固定版本 MinIO、健康检查、桶初始化与前端 `npm run type-check` 已静态核对；独立 CI 等价环境全量 266/266、前端 type-check/build 通过。WS-6 仍负责 ESLint、Vitest、Playwright。
2. **U-002 WS-3 退回整改—✅ 独立重核 PASS**：`88d3136..2886442` 已确认关闭第五轮新增的 2 Medium：① reconciliation trigger 绑定独立 `TaskScheduler`，真实 scheduling + blocked backup 证据证明普通备份不再阻断对账提交；② `video_finalization_object_candidate` 纳入 37 表逻辑全量备份，scratch restore 后历史 generation、claim/retry/tombstone 完整且真实对账可续跑。唯一新增 Low 是提交材料把 32 个迁移写成 33 个，已在正式报告和活动文档勘误，不阻断。该报告只证明 candidate slice；当时仍独立退回的 Phase 41 整体备份问题现已由后续动态证据报告关闭。V32 发布继续遵守停写、停全部旧节点/worker、迁移、全量启动新实例后再放流，禁止 V31/旧稳定 key 协议混部与旧二进制回滚。独立报告为 `reviews/ws-03-sixth-remediation-rereview-2026-07-24.md`；未执行任何 cyber 指令，后续任何可能属于 cyber 的命令必须明确交由用户决定并亲自执行。
3. **U-003 阶段退回整改—进行中**：Phase 42、Phase 39、Phase 41、Phase 47 与 Phase 53 均已独立复核 PASS。Phase 44 第三轮真实 `Phase44CacheCommitWindowIT` 已 16/16，但独立报告仍有 1 Medium / 4 Low：下一动作是保持仍存 `P:` fail-closed 到 recovery TTL、增加事务本地 publish guard，并补删 writers 后的同事务/并发读交错。门禁命令需加 reactor safeguard 且核对目标计数；Compose 参数、Phase 44 禁混部 runbook 与棕地 identity preflight 一并收口。Phase 44 PASS 前不进入 Phase 0。
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
15. **最终全量审计保留项**：裁定 Phase 7 有效预签名 URL 的 bearer 合同与“拿到链接后未登录也失败”的规格冲突；复核 Phase 53 外部精确同-key 写无 CAS、旧新初始化器禁止混部，以及大媒体/异常编码的广覆盖证据。

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
5. 最终全量审计前，U-001–U-004 必须全部闭环；U-001/U-002 已独立重核 PASS，U-003 的 Phase 42、Phase 39、Phase 41、Phase 47、Phase 53 已关闭，Phase 44 第三轮独立复核仍退回、待第四轮最小整改；U-004 最后处理 Phase 0。未闭环前不得宣称稳定发布就绪。
