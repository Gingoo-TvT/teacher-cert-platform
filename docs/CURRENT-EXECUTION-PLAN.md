# 当前统一执行计划（2026-08-11）

> 本文件是项目**当前工作队列与阶段复核状态的唯一入口**。业务规格仍按 `AGENTS.md` 规定的优先级执行：`plan.md §15` > `plan.md` 正文 > `docs/phase-NN-*` > `tasks.md`；本文件不改写业务规则，只合并分散计划、复核证据和后续顺序。
>
> 历史计划保留作决策与实现追溯，其正文中的“待复核/未合并/下一步”等状态均视为当时快照，不再单独维护。状态更新只写入本文件、`PROGRESS.md` 和 `DEVLOG.md`。

## 1. 当前基线与本轮结论

- 当前分支：`feature/ws08-idcard-encryption`，基线 HEAD `aa3509a`，工作树含未提交整改。
  Phase 39、Phase 41、Phase 47、Phase 53、Phase 44 与 Phase 0 均保持独立 PASS；Phase 0 /
  U-004 最终 HEAD `ea760bf` 的既有门禁结论不变。最终全量审计已冻结在 `dfdfb91`，正式结论为
  **CHANGES_REQUESTED / NO-GO（0 Critical / 7 High / 13 Medium / 4 Low）**，见
  `reviews/audit-report-teacher-cert-platform-2026-07-28.md`。当前未提交工作树只构成 7 High
  与第一批确定性 Medium/Low 的代码级整改候选，不自行改判项目 PASS；禁止擅自 merge/push。
- 整改者提交材料称 7 High 已按原失败条件完成最小收口：ERROR 导出绑定 owner/scope、审计可信代理与失败关闭、
  陈旧实体写改为业务键锁/CAS、XLSX SAX 与展开预算、敏感投影默认脱敏、服务端 logout，以及
  登录绑定的应用取流。随后退役通用上传，增加文件 intent/FAILED 补偿，统计批次统一
  owner-only，绑定导入预览与备份单行预算，给全量备份加 MySQL 单飞锁，并让 Dashboard 只提交
  最新请求；同时关闭固定客户端错误、通知键盘可达与活动验收文档漂移。离线 Surefire 全仓
  **339/339**、High 聚焦 **31/31**、Medium 聚焦 **21/21**、`-Xmx128m` 资源反例 **21/21**、
  后端 9 模块 offline package、前端 lint/type-check/两个契约脚本/build 和 `git diff --check`
  均通过。
- 最终审计后首轮独立增量复核已完成：当前 24 项代码级矩阵为
  **6 STATIC_CLOSED / 10 PARTIAL / 8 OPEN**，正式状态继续
  **CHANGES_REQUESTED / NO-GO**。新增或确认 **1 High / 4 Medium / 2 Low**：预览 JSON
  仍在完整物化后才检查预算；`before=true` 失败尝试与成功审计不可区分；logout 撤销失败被前端
  静默伪装为成功；Phase 3 明文旧断言与新脱敏合同冲突；预览/备份限制存在双配置分叉；
  Exchange 仍返回底层异常文本；Phase 05/07 提前勾选媒体动态语义。独立离线 Surefire
  **339/339**、9 模块 package、前端五门禁与 diff check 均 PASS，但不能覆盖上述静态失败条件或
  真实依赖门禁。详见 `reviews/final-audit-remediation-independent-rereview-2026-07-28.md`。
- 首轮退回项的第二整改候选已按原失败条件完成最小修改：JSON 预览使用有界输出流在序列化期间
  截断，真实 sharedStrings 反例验证不会先物化完整 JSON；预览/备份预算统一读取
  `platform.backup`；改密/重置使用同事务审计，导入与 logout 显式记录目标和
  `PENDING → IMPORTED/FAILED/SUCCESS/ERROR`；logout 撤销失败在前端明确警告；Phase 3
  普通权限断言改为脱敏；非业务导入异常只返回固定文本；Phase 05/07 未获动态证据的条目恢复
  未勾选。离线 Surefire **347/347**，聚焦回归 `platform-system` **2/2**、
  `platform-boot` **27/27**，`-Xmx128m` sharedStrings 预算反例 **14/14**，后端 9 模块
  offline package、前端五门禁和 diff check 均通过。以上只构成未提交整改候选，正式状态仍为
  **CHANGES_REQUESTED / NO-GO**，须由下一轮独立增量复核判定。
- 第二整改候选独立增量复核已完成：冻结 `aa3509a` 后
  `tracked-diff-git-object=1fae98fa0784d89a67541f541c5801cc369f119b`，七项原问题为
  **6 CANDIDATE_CLOSED / 1 PARTIAL**。High、logout、Phase 3、生产单一备份配置、固定错误文本和
  Phase 05/07 状态达到当前候选闭环；审计项仍因 resetPassword 成功记录没有目标用户而
  `PARTIAL`。另确认 **1 个新增 Medium**：`DatabaseBackupService` 把单语句上限迁入
  `DatabaseBackupProperties` 后，`Phase41BackupIT` 仍反射已删除字段，真实 Phase 41 门禁会
  确定性失败。独立低堆聚焦 Surefire **34/34**、9 模块 package、前端四门禁及 diff check
  均 PASS，但不覆盖该 Failsafe 源码错误。正式状态继续
  **CHANGES_REQUESTED / NO-GO**；报告见
  `reviews/final-audit-remediation-second-remediation-rereview-2026-07-29.md`。
- 两个 Medium 的第三整改候选已完成最小修改。`Phase41BackupIT` 注入生产实际使用的
  `DatabaseBackupProperties`，通过 getter/setter 临时收紧并在 `finally` 恢复原值，默认常量也
  直接引用配置类，删除对已不存在私有字段的反射。`SecurityAdminServiceImpl.resetPassword`
  在既有 `@Transactional` 边界中于密码 CAS 与旧 token 撤销成功后写一条结构化审计，精确绑定
  `bizId=id`、`target=user:<id>` 和 `newStatus=SUCCESS`；Controller 的通用空目标注解已移除，
  临时口令不进入审计。离线聚焦 **39/39**、全仓 Surefire
  **50 suites / 349 tests**、后端 9 模块 package、Checkstyle 0 与 diff check 均通过。
  该工作树仍未固化为不可变提交，真实 Phase 41 与同库事务回滚门禁未执行，正式状态继续
  **CHANGES_REQUESTED / NO-GO**。
- 第三整改候选的真实依赖反馈已收到。用户在不发布主机端口、无卷的一次性 MySQL 8.4.11 /
  Redis 7.4 / MinIO 栈上确认：密码重置成功审计及审计插入失败后的真实 MySQL 回滚
  **2/2 PASS**；Phase 41 正式脚本 **2/2 FAIL**，唯一失败是 `Phase41BackupIT` 错误地要求
  对外通用 `BizException` 包含只应留在 `backup_record.error_message` 的内部超限明细。诊断
  run 在容器内临时更正该断言后 PASS，但不计正式证据。第四整改候选已仅把对外断言改为
  “备份执行失败，请查看备份记录”，保留内部表名、预算和脱敏断言；并在既有
  `Phase2SecurityIT` 中增加两次重置各自的 `bizId/target/SUCCESS` 精确审计断言。未把会创建
  全局 Trigger 的一次性专项测试纳入默认 `*IT`，避免中断时污染常规 Failsafe 环境。当前仍须
  对新 diff 重跑正式 Phase 41 和精确 Phase 2 门禁；离线 349/349、9/9 package、Checkstyle
  与 diff check 已通过，正式状态保持
  **CHANGES_REQUESTED / NO-GO**。
- 第四整改候选的真实门禁由 Claude 在全新隔离栈完成：Phase 41 **1/1 PASS**，
  `Phase2SecurityIT` **3/3 PASS**，当前候选上的外部 `PasswordResetAuditIT` 审计成功/失败回滚
  **2/2 PASS**，相关 Surefire 349/349；一次性容器、镜像与卷已清理，线上稳定版未触碰。原
  Phase 41 错误边界阻断和密码重置目标审计已有真实动态证据。第五整改候选仅把仍在仓库外的
  MySQL 回滚语义常驻为 `Phase2SecurityIT` 第 4 条：通过测试局部 `@MockitoSpyBean` 注入
  `AuditLogService.record` 异常，真实 HTTP/事务/MySQL 全行回滚保持不变，且无需 Trigger/DDL。
  离线 test-compile、Surefire 349/349、9/9 package、Checkstyle 与 diff check 已通过；当前
  只缺新增第 4 条在隔离 MySQL 上的精确动态执行。项目总体仍保留最终审计的其它 OPEN/PARTIAL
  项，因此正式状态继续
  **CHANGES_REQUESTED / NO-GO**。
- 第五整改候选真实门禁证据已归档：Phase 2 **4/4 PASS**、Phase 41 **1/1 PASS**，证据包
  7/7 SHA-256 一致；两次各自带出的 Surefire 均为 349/349。独立增量复核确认上轮两个 Medium
  已按原失败条件 `FUNCTIONALLY_CLOSED`，但新增 **1 Medium / 1 Low** 证据问题：门禁摘要
  `c4473e...` 只覆盖 tracked diff，未覆盖直接参与 Phase 41 编译的 untracked
  `DatabaseBackupProperties.java`；README 还把 81 tracked + 32 untracked 的 113 个总状态项
  误写为 113 个 tracked 改动。结论继续 **CHANGES_REQUESTED / NO-GO**；下一步只需固化完整候选
  SHA 并在其上重跑 Phase 2 / Phase 41 exact gate，不再扩大生产整改。报告见
  `reviews/final-audit-remediation-fifth-remediation-rereview-2026-07-29.md`。
- 第六整改候选仅收口上述证据问题，不再修改生产逻辑。新增纯离线
  `scripts/candidate_source_manifest.py`：清单绑定完整 HEAD、固定参数生成的 tracked binary
  diff 原始字节，以及 `git ls-files --others --exclude-standard -z` 枚举出的每个非忽略
  untracked 普通文件路径/大小/SHA-256；捕获时双读拒绝漂移，清单强制写到仓库外，门禁后
  `verify` 必须得到同一完整候选指纹。该口径明确覆盖
  `DatabaseBackupProperties.java`。6 个纯离线正反例已通过；历史证据 README 已改正为
  “81 tracked + 32 untracked 状态分组 = 113 个状态项”，并撤回“完整严格绑定”的错误表述。
  真实 Phase 2 / Phase 41 尚未在此新指纹上重跑，因此当前为
  **DYNAMIC_EVIDENCE_PENDING，正式状态仍 CHANGES_REQUESTED / NO-GO**。
- 第六整改候选独立增量复核已完成：README 的 81/32/113 Low 按原失败条件关闭；source manifest
  实现、6/6 离线正反例和当前工作树 capture/verify 均成立，关键 untracked 配置类已进入指纹。
  但旧 Phase 2 4/4 与 Phase 41 1/1 早于新 manifest，候选绑定 Medium 继续
  `DYNAMIC_EVIDENCE_PENDING`。新增 **1 Medium**：历史证据 `SHA256SUMS` 引用的两份
  `run3-*.log` 命中 `.gitignore:7` 且未受 Git 跟踪，普通提交/fresh clone 会缺件。正式结论仍为
  **CHANGES_REQUESTED / NO-GO**；报告为
  `reviews/final-audit-remediation-sixth-remediation-rereview-2026-07-29.md`。
- 第七整改候选只修复上述证据归档可移植性，不改生产逻辑。两份历史原始日志已逐字节复制为
  `.log.txt` 规范证据，`SHA256SUMS` 改为引用可提交路径；`.gitattributes` 对最终审计
  `.log.txt` 固定 `-text -diff`，避免 fresh checkout 换行漂移。原 ignored `.log` 保留为本机
  历史来源，但不再属于规范证据清单。当前仅达到
  **EVIDENCE_PORTABILITY_REMEDIATION_READY / REVIEW_PENDING**：文件尚未进入不可变 Git tree，
  也未做 fresh checkout 独立核验；候选绑定 Medium 仍为 `DYNAMIC_EVIDENCE_PENDING`，项目继续
  **CHANGES_REQUESTED / NO-GO**。
- R7 正式冻结包独立增量复核已完成。唯一正式冻结对象为
  `final-audit-r7-freeze-raw/final-audit-r7.bundle`（SHA-256
  `3c0bbce56f3c6df3f85a9f15b514366b9b259ade6a6478fc27ed75e0942a121d`，commit
  `a66eda76e4d6257a16311d478cb6939c7066ce32`）；原 `final-audit-r7-freeze` 只作为失败反例，
  明确排除在正式验收之外。从 raw bundle fresh 重建得到的 manifest 与门禁前 capture 逐字节
  一致（fingerprint `0f9f2fef...bf6d`，82 tracked / 99 untracked），Phase 2 4/4 与
  Phase 41 1/1 之间各有同一 manifest verify，外部证据 11/11 哈希闭合。上轮候选绑定与日志
  可移植性两个 Medium 均 `CLOSED`，本 R7 增量 **PASS（0 finding）**。项目最终全量审计其它
  OPEN/PARTIAL 项及 **CHANGES_REQUESTED / NO-GO** 基线不变；报告见
  `reviews/final-audit-r7-freeze-raw-independent-rereview-2026-07-29.md`。
- R7 后首个新候选任务为 `FINAL-F01-DYN`：只为 F-01（ERROR 导出跨学院）补常驻 Phase 10
  真实 MySQL 回归，覆盖学院 A/B 各自错误批次、非校级 exact-batch owner-only、校级可指定任一
  批次，以及证件号/出生日期错误值的脱敏/敏感权限合同。首轮真实门禁绑定 fingerprint
  `5749abd8...18e1`，Phase 10 **15 个执行、5 失败 / 10 通过**；12 次非法
  `FOR UPDATE LIMIT 1` 均源于候选新增的五个 `LIMIT 1 FOR UPDATE` 锁定查询，门禁判定 FAIL。
  第二轮已做最小修复：四个唯一键查询只保留 `FOR UPDATE`；证书 student-year 查询锁定完整集合，
  优先唯一活跃证书、仅有历史时仍返回终态记录交由 Phase 48 守卫拒绝。离线 test-compile、
  聚焦单测 13/13 与 diff check 通过。第二轮真实门禁绑定完整候选 fingerprint
  `48a6c264d80824082d9fde1b5c1aaa5f26a6845605bf71cdbbbef88069ba5929`（HEAD `aa3509a`，
  manifest 为 82 tracked / 101 untracked）；主机入栈前、栈内及门禁后 capture/verify 均一致。
  执行者报告 `Phase10ExchangeIT` **15/15**、`Phase48CertImportGuardIT` **2/2**、Failsafe
  **17/17**、Surefire **349/349**、BUILD SUCCESS；首轮五个失败逐个 PASS，非法
  `near 'LIMIT 1'` 日志为 0。外部证据包 9/9 哈希闭合，一次性栈已销毁，线上稳定版未触碰。
  上一轮锁定 SQL Blocker 已按原失败条件由执行者动态证据关闭；F-01 当前为
  **EXECUTOR_GATE_PASS / INDEPENDENT_REVIEW_PENDING**，项目仍为 **CHANGES_REQUESTED / NO-GO**。
- 下一项 `FINAL-F02-DYN` 已按原审计顺序领取，只补 F-02（审计 IP 伪造/审计失败开放）的最小
  常驻动态反例，不改生产架构。`Phase13SystemAuditIT` 新增两条：其一在默认 `@AuditLog` 的
  `systemParam/update` 成功写后注入审计失败，要求 HTTP 500、真实 MySQL 参数整行回滚且无成功样式
  审计；其二由真实 HTTP 请求发送伪造及超长 XFF/X-Real-IP，要求落库 IP 仍为未受信直连地址且
  不超过 45 字符。离线 9 模块 test-compile、Checkstyle 0、审计聚焦单测 14/14 与 diff check
  已通过。独立环境随后在完整 fingerprint
  `3cddcb748e43d94c9c944b80b50ee0586a773a83686711200a291eab9c9f98d2`（HEAD `aa3509a`，
  83 tracked / 101 untracked）完成 capture → 真实门禁 → verify：Surefire **349/349**、
  `Phase13SystemAuditIT` **11/11**、BUILD SUCCESS，候选前后未漂移。专用 `f02audit` Compose
  从 frontend/Nginx 入口发送伪造 XFF `203.0.113.66` 和 X-Real-IP `203.0.113.77` 后，Nginx
  `$remote_addr` 与 `audit_log.ip` 均为 `172.31.99.1`；伪造值未落库。一次性资源已全部销毁，
  线上稳定版未触碰。F-02 独立结论为
  **DYNAMIC_CLOSED / INDEPENDENT_INCREMENTAL_PASS**；该 scoped PASS 不改写项目总体
  **CHANGES_REQUESTED / NO-GO**，也不授权 merge、push 或 deploy。
- F-03 首轮独立真实门禁绑定 fingerprint
  `2756786973cf7f51704c7774676306d0ce3d25ed4a7ba52097a1de51ca140977`，capture/verify 一致；
  Surefire 349/349、Phase 9 10/10，但 Phase 8 7/9。两条 ability 用例的年度值为 21/20 字符，
  违反 `@Size(max=16)`，在首次导入时 HTTP 400、未到达 SQL 屏障，因此只有证书两组
  `CONNECTION_ID()` 有效。一次性资源已清理、线上未触碰；F-03 结论为 **CHANGES_REQUESTED**。
- F-03 第二轮历史本地真实门禁绑定 fingerprint `34eb829...14007d`，Phase 8 **9/9**、Phase 9
  **10/10**，四组双连接交错均使用不同真实 `CONNECTION_ID()`；当前候选随后修改 Phase 9 测试，故须在
  统一新冻结候选重跑。F-04 已在当前工作树重跑 `-Xmx128m` sharedStrings 资源门禁 **14/14 PASS**，
  原始 JVM 标志确认 `MaxHeapSize=134217728`，仍待正式 manifest 绑定。
- F-05 已补证书普通列表/详情脱敏与跨学院 403，并复用 Phase 10 STANDARD 普通/敏感导出合同；聚焦单测
  **12/12 PASS**。F-06 已常驻 logout 后旧 access `/auth/me` 401，前端可执行退出流程及实际 store 接线合同
  PASS。F-07 已把媒体 Cookie 寿命截断到 JWT 剩余寿命，并为过程材料、免考材料、视频补齐 Range、换号、
  跨账号与 logout 反例，Cookie 单测 **6/6 PASS**。三项新增动态反例均尚未运行真实依赖门禁。
- 当前工作树离线全量 Surefire **50 suites / 352 tests**、9 模块 test-compile、前端 logout contract、lint、
  type-check、production build 与 diff check 全部 PASS；这只构成统一冻结前的候选准备，不签发 F-03～F-07
  独立 PASS。
- WS-4 / D0 已完成静态全站盘点与可执行基线工具：74 个 Vue、21 个认证业务页、23 Drawer、
  22 Modal；`ui-routes.json` 覆盖 6 角色、20 个认证路由、2 个公共路由和 5 个断点，
  `ui-shots.mjs` 自动截图并检查页面水平溢出与 Console warning/error。用户已确认并应用 10 组
  重复入口口径。D1 改动前没有真实运行态 before-state，继续明确标为不可补造的证据限制；D0 当前为
  **STATIC_BASELINE_COMPLETE / BEFORE_STATE_UNAVAILABLE_NONBLOCKING**。
- WS-4 / D1 已完成共享设计基线和两个样板的源码实现及本地真实运行门禁：核心间距/字号/圆角/语义色、三档
  Overlay、共享加载/空/错误状态与审核防重已收口；`StudentManage` 和
  `TrainingManage + TrainingDrawer` 已按 Naive UI 容器响应式改造，API、路由和权限语义未变。
  前端 lint/type-check/build 均 PASS；三角色 × 双样板 × 五断点 30/30、移动导航与双 Drawer 5/5。
  该切片已纳入后续 D1–D6 统一候选独立复核并取得 **INDEPENDENT_SCOPED_PASS**。
- WS-4 / D2–D6 已完成全站剩余产品 UI 的分批铺开：高频 Grid、Overlay 写动作、布局导航、非 Overlay
  错误/陈旧/空态，以及字典、区划、学科、组织专业、导入和视频上传复合流程均已做最小收口。D6 当前
  前端 lint/type-check/build 与 diff check 通过；隔离静态前端以 API 拦截执行六页五断点 30/30 和
  组织依赖恢复、导入互斥/失败重试、视频手机 Drawer 6/6。D2–D6 均已纳入后续统一候选独立复核并取得
  **INDEPENDENT_SCOPED_PASS**。
- **历史首轮结论（已被下条 2026-08-06 更正取代）**：WS-4 / D1–D6 统一候选曾完成独立批量复核，当时结论为
  **CHANGES_REQUESTED（0 High / 5 Medium / 0 Low）**。学生、证书和视频主列表的聚焦修复成立，但仍有：
  材料/统计请求乱序与查询漂移、培养/免考 Drawer 依赖选项 freshness 缺口、视频首错伪零值、冷启动
  `loadMe()` 后未重检首次改密，以及最终同指纹 gate 未覆盖 D1–D5 独有行为。报告为
  `reviews/ws-04-d1-d6-batch-independent-review-2026-08-05.md`。
- 上述 5 Medium 的首轮整改候选 `745e9986...d4d9` 已完成独立增量复核；复核者在 2026-08-06 重新校准
  严重度后，正式更正为 **WS-4 scoped PASS（0 High / 0 Medium）**。原四类产品缺陷已按原口径关闭，且
  最终候选覆盖广度与证据闭包成立。材料批量下载使用 loaded query snapshot、四个函数级写屏障的额外证明、
  视频成功空态逐项断言五个 0 均降为非阻断 Low/建议，不要求为其继续设计或重跑候选。报告顶部更正说明为
  `reviews/ws-04-d1-d6-remediation-independent-rereview-2026-08-05.md`。该 scoped PASS 不自动授权全项目发布。
- TLS/生产端口、refresh token 与跨标签页迟到刷新已由 F07/WS-5 scoped 门禁关闭；MinIO 服务身份、
  readiness、WS-6 scoped 边界外的真实后端全链/全站 WCAG、镜像证明及真实灾备证据继续排队。
  功能优先阶段不为其提前引入复杂会话或基础设施架构。
- 用户已部署的线上稳定版本 `dfdfb91` 是冻结边界：本地后续工作不得连接服务器、操作线上容器、
  镜像 tag、卷或数据，不部署、不切流。本地允许非攻击性的产品构建和一次性隔离依赖回归；任何
  cyber/攻击性操作仍须明确交给用户决定并亲自执行。
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
- 编排历史/通用基线：`docker-compose.dev.yml` 与生产 `docker-compose.yml + .env.example` 曾通过
  `config --quiet`；第九轮最终 SHA `ea760bf` 的 dev Compose 再次 exit 0，一次性 `tcp-phase00-r9`
  容器、网络、数据卷与临时目录均已由执行者清理，共享 tcp 栈保持 Exited 未触碰。
- 当前 WS 链判定：**WS-1、WS-2、WS-3、WS-10、WS-13 PASS**。WS-3/U-002 的正式依据为 `reviews/ws-03-sixth-remediation-rereview-2026-07-24.md`；1 Low 迁移数量勘误不阻断放行。
- 缺失阶段账已完成独立复核：阶段总审计原始快照中 **Phase 29、35b、36–38、40、43、45–46、48–52 PASS；Phase 0、39、41、42、44、47、53 CHANGES REQUESTED**。Phase 42、Phase 39、Phase 41、Phase 47、Phase 53、Phase 44 与 Phase 0 均已由后续增量报告更新为 PASS，当前无阶段退回项。原始阶段总审计见 `reviews/phase-gap-audit-2026-07-23.md`。
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
- Phase 44 第四轮整改候选：冻结 `69f7462..228a355`（11 路径）。READ 在 writers 为空但 positive `P:` 尚存时继续 pending 且不续期；固定 stripe 的进程内 guard 在线性化锁内把 active writer 与整段 Redis PUT 互斥，并保持到 afterCompletion 全部清理结束。五条字典 DML 在 mapper 写前登记窗口，注册失败不产生未提交 DB 变更。owner-loss 用例覆盖删 writers/同事务读/并发读/再删全部 Redis 状态/回滚/V1 重新回填，crash 用例以 PTTL 单调下降证明自然恢复。4 Low 由精确门禁脚本、Compose/env、Phase 14/README 与只读棕地 preflight 收口；整改者离线 53/53、9 模块 package、前端与脚本语法门禁 PASS，真实依赖尚待用户执行。
- Phase 44 第四轮独立增量重核：正式冻结 `69f7462112e8df7aaffdd8d5f45914d3b030f5c6..228a3553607a8fb6ca2db048125d4137e65191ae`，材料 HEAD `9c3da0b7e6dff5faec1cc888ce99c90d5bff81b0` 只增加治理/提交材料，产品、测试与门禁路径未漂移。用户专用全新 MySQL/Redis 栈精确 **16/16**，XML SHA-256 `3505ead4877e0d280e56d837d3d7c0c5d2e6038a8279d979c9d9334769394981`，两个第四轮 testcase 均在 XML；棕地只读 preflight 为 `columns=2/2, invalid=0, noncanonical=0, collision=0`；Compose 非默认 `9m/3m` 与默认 `2m/20s` 双向展开。独立离线 `platform-system` **53/53**（更正用户摘要中的 46/46）、9 模块 package、前端 type-check/build、报告 lint 与 diff check PASS。第三轮 1 Medium / 4 Low 原失败条件全部关闭；新增 1 个非阻断 Low：preflight 尚未接入权威发布步骤，包装器证据缺数据库/账号/实例 target marker。正式结论 **PASS**。
- Phase 0 / U-004 首轮独立增量重核：冻结 `dd04f21387f76adc7fc5d1443903ada0bec4d4c1..715b5f1c67a9943c5914eda08e485a1463c0524d`。两端 lint 与 api-docs 回归修复成立，Phase00 6/6、DataScope 9/9 的 XML/源码时间链可交叉支持运行事实；但同 MD5 用例只上传一次，宽泛 `mvn verify` 不锁定两个必需 suite，且全局 DoD/tasks/README/review gate 仍保留过时 Phase 0 口径。新增 4 Low 为 prod 静态 doc UI、证据 provenance、10/10 锚点过度与 T-DS-1 口径/层级漂移。独立离线 Surefire **257/257**、后端 9 模块 package、前端 lint/type-check/build、报告 lint 与 diff check PASS；正式结论 **CHANGES_REQUESTED（3 Medium / 4 Low）**。
- Phase 0 / U-004 第二轮整改候选：冻结产品/测试/门禁点 `3bfe83d9eaa4486bb39cc89fc3f05a975319e3af`。按 R10 退役通用摘要秒传，两个测试层级均真实执行相同字节二次上传；exact gate 固定 6 suite / 25 testcase、候选 SHA、clean source、完整脱敏日志/XML、target marker、源码/工件哈希、manifest 与 `SHA256SUMS`；同步权威文档、prod docs 404、24 项参数矩阵和真实 Mapper/DataScope 组合链。整改者只执行离线 gate 21/21、Surefire 266/266、9 模块 package、前端 lint/type-check/build 与内部只读对抗检查；动态 exact gate 明确留给用户。
- Phase 0 / U-004 第二轮独立增量重核：冻结 `8e3217d..c426480`，产品/测试/门禁点为 `3bfe83d`。首轮 3 Medium 全部按原失败条件关闭，prod docs 与 DataScope Low 关闭；独立离线 gate **21/21**、聚焦 **18/18**、Surefire **266/266**、9 模块 package、前端与 diff check 全绿。但 gate 只在 Maven 前绑定 HEAD，target marker 也未与真实连接变量/服务身份绑定，形成 **2 Medium**；3 Low 为 verifier 来源/Windows 路径边界、应用上下文 `[x]` 过早、参数矩阵仅 24/32。真实依赖 exact 6/25 尚未执行，正式结论 **CHANGES_REQUESTED（2 Medium / 3 Low）**。
- Phase 0 / U-004 第三轮整改候选：冻结第二轮报告归档 `5d3aa88` 至产品/测试/门禁点 `cc5786c`（14 路径，`+7669/-323`）。候选不再从可切换工作树构建：preflight 后复核全部 candidate blob、删除构建树并从相同 Git objects 重物化 formal，formal 后再复核；start/after-preflight/end HEAD 与 snapshot 三段证明均为 PASS 前置。target preflight 在 Spring 启动前绑定实际 MySQL/Redis/MinIO 身份与 0/0/1/0 初始状态，正式 initializer 每次 context refresh 前复核解析目标；exact 合同扩为 7 suite / 33 testcase。Windows 路径/strict manifest、candidate/live/archive spec blob、应用上下文 `[~]` 与 32/32 参数矩阵同步收口。整改者离线 gate **60/60**、Surefire **274/274**、target guard **8/8**、9 模块 clean package、前端 lint/type-check/build 与 diff check PASS；候选 spec 最终加固的独立只读增量检查 0 High / 0 Medium / 0 Low，但不是正式阶段复核。动态证据明确留给用户。
- Phase 0 / U-004 第三轮独立增量重核：冻结 `5d3aa88abc7e596a5b98c402a0c4aff2894bbc3a..30a76c8a52d053f39c1b974287434c1390e150ee`，实现点 `cc5786c`。第二轮 2 Medium / 3 Low 全部按原失败条件关闭；独立离线 gate **60/60**、Surefire **32 suites / 274 tests**、9 模块 clean package、Checkstyle 0、前端 lint/type-check/build 与 diff check PASS。新发现 **1 Medium / 2 Low**：自校验只捕获 `GateError`，普通 `OSError` / `PermissionError` 可在失败退出后留下 PASS manifest；evidence containment 仍有 check-then-open TOCTOU；MinIO `Server` / deployment ID 外部响应头进入 identity JSON，却无条件声明 `containsSecrets=false`。内存级反例稳定得到 `OSError PASS ['PASS']`。真实依赖 exact 7/33 未执行；当前 SHA 不应先跑动态门禁。正式结论 **CHANGES_REQUESTED**。
- Phase 0 / U-004 第四轮整改候选：第三轮正式报告归档提交 `df888c1` 后形成实现点 `74c1de5`（7 路径，`+2391/-317`），提交材料为 `reviews/phase-00-fourth-remediation-submission-2026-07-27.md`。PASS 状态机在源码快照 context 内只保留内存 `PendingPassEvidence`；context 清理完成后才捕获最终 artifact snapshot、secret scan、构造虚拟 manifest/checksum 并执行完整 verifier，任何 `Exception` 都降级，磁盘 checksum-first、PASS manifest-last，原子 rename 后输出异常不再翻转结果。evidence verifier 在 POSIX 使用 `openat/O_NOFOLLOW`，Windows 持有从卷/共享根到 evidence root 的完整无 reparse handle 链，并以同一 handle 的 final path/type/file ID/size/mtime 校验后才读取；所有消费者只使用一次 `EvidenceSnapshot`。MinIO schema v4 不读取 `Server`，deployment header 必须恰为一个 lowercase canonical UUID，并与本次 nonce 派生 domain-separated SHA-256 指纹；原始 header 不进入 JSON、record 或错误文本。纯离线 gate **70/70**、Surefire **274/274**、9 模块 package、Checkstyle 0、diff check PASS；状态机、Windows handle 与 MinIO schema 三项专项只读检查均为 0 Critical / 0 High / 0 Medium / 0 Low。该结论只证明候选静态/离线闭环，不替代最终 SHA 的真实依赖 7/33 与正式阶段复核。
- Phase 0 / U-004 第四轮动态门禁：最终材料 `191c395` 在用户一次性 MySQL 8.0.46 / Redis 7.4.9 / MinIO 隔离栈执行。候选/spec/source snapshot 绑定成立，Compose 与浏览器 supporting evidence 通过且资源已销毁；但预检的 Redis INFO 多行载荷被 `requiredText → cleanText` 拒绝，manifest 为 FAIL、formal `run.executed=false`、**0/33**。离线 verify 同样 FAIL，未出现状态机误放行。同一 SHA 不重跑；完整归档见 `reviews/phase-00-fourth-remediation-dynamic-failure-2026-07-27.md`。
- Phase 0 / U-004 第五轮整改候选：失败归档 `613ac50` 后形成实现点 `6a5577d`。`parseRedisInfo` 允许协议 CRLF/LF 多行载荷，整段非空且禁 NUL，拆行后的行/key/value 继续单行控制字符校验，并拒绝无分隔符、空 key 与重复 key；CRLF/LF、NUL、裸 CR、畸形行、重复 key、空载荷反例并入既有第 8 个 target-guard 测试，exact 7/33 的 suite/method 集合不变。gate 的 Windows 默认 Maven 解析优先非空 `MVN`，否则解析 `mvn.cmd`；解析失败会在工具版本预检 fail-closed。纯离线 Python **71/71**、聚焦 Java **8/8**、顺序全量 Surefire **32 suites / 274 tests**、9 模块 package 与 Checkstyle 0 均通过；Java/Python 两路独立静态增量终审均为 0 finding。一次并发 Maven 因共享 `target/` 互删产生 13 个 `NoClassDefFoundError`，停止并发后同源码顺序复跑全绿，确认为测试调度污染而非产品信号。
- Phase 0 / U-004 第五轮动态门禁：最终材料 `6509409` 在用户全新一次性 MySQL 8.0.46 / Redis 7.4.9 / MinIO 隔离栈执行。Redis INFO、Windows `mvn.cmd`、MySQL UUID、Redis run_id、bucket 空状态与身份对象逐字节校验均通过；随后同版 MinIO 的 S3 `GetObject` 响应不含候选强制要求的唯一 canonical `x-minio-deployment-id`，preflight FAIL、formal `run.executed=false`、**0/33**。离线 verify 同样 FAIL，资源由执行者销毁；同一 SHA 不重跑。完整归档见 `reviews/phase-00-fifth-remediation-dynamic-failure-2026-07-27.md`。
- Phase 0 / U-004 第六轮最小整改候选：第五轮失败归档 `b4bf841` 后形成实现点 `b123238`。删除 MinIO S3 deployment header、UUID 解析和 `instanceFingerprintSha256` 全链路，target evidence 升为 schema v5 / `provisioned-object-challenge-v2`；仍逐字段绑定 canonical endpoint、bucket、一次性身份对象及其 SHA、candidate/runContext、nonce、issuedAt 与 0/0/1/0，并在每个 Spring context refresh 重读比较。该合同只证明当前配置目标持有本次挑战对象，不宣称唯一 MinIO 管理实例 UUID；不接 Admin API、不新增管理凭据、不添加冗余同源哈希。exact 仍为 7/33；纯离线 Python **71/71**、target guard **8/8**、Surefire **274/274**、9 模块 package、Checkstyle 0，两路独立静态增量复核均为 0 finding。
- Phase 0 / U-004 第六轮动态门禁：最终材料 `1625600` 在用户全新一次性隔离栈执行。preflight exit 0，正式 Maven exit 0 / BUILD SUCCESS，manifest 与 7 份 XML 精确证明 **7/7 suites、33/33 testcases、0 failure/error/skip**。随后 runtime identity freshness 被应用 `Long→String` 定制写成 `"0"`，Python 严格 JSON integer 校验使 manifest 正确降级为 FAIL；runtime identity 未归档。13 个 artifact 哈希一致，同一 SHA 不重跑；完整归档见 `reviews/phase-00-sixth-remediation-dynamic-failure-2026-07-27.md`。
- Phase 0 / U-004 第七轮最小整改候选：第六轮失败归档 `7f02635` 后形成实现点 `b8cd171`。`Phase00ScaffoldIT` 保留应用 mapper 读取身份对象，但 runtime evidence 写出改用裸 mapper；既有第 8 个 target-guard testcase 直接调用同一 package-private helper，解析实际字节并逐项断言 freshness 为 JSON integer 0/0/1/0。Python schema、证据结构、生产配置、suite/method 集合均未改。纯离线 Python **71/71**、target guard **8/8**、Surefire **274/274**、9 模块 package、Checkstyle 0；独立静态增量终审 0 finding。
- Phase 0 / U-004 第七轮正式独立增量复核：当前 HEAD r7 evidence 的 manifest/checksums/XML/identity 与 source tree 均通过仓库 verifier，真实 exact 7/33 PASS，第六轮 JSON integer 缺陷和 MinIO header Low 关闭；但原 `P00-R3-M1` 仍可在内存验证后、PASS 持久化前改变 artifact 并得到 `verified=true + PASS manifest + checksum mismatch`，原 `P00-R3-L1` 的 POSIX evidence-root 非末段祖先也未逐段锚定。正式结论 **CHANGES_REQUESTED（1 Medium / 1 Low）**，见 `reviews/phase-00-seventh-remediation-rereview-2026-07-27.md`。
- Phase 0 / U-004 第八轮候选：实现 `7810715` 从已验证 snapshot bytes 重建私有 bundle，final 以 withheld manifest 完成 exact capture 后才原子暴露 PASS；异常路径无 canonical PASS。POSIX root 从 `/` 起逐段 openat 并保留祖先 fd。四类发布反例、两类 POSIX 反例及独立静态复审已提交，见 `reviews/phase-00-eighth-remediation-submission-2026-07-27.md`。
- Phase 0 / U-004 第八轮正式独立增量复核：冻结 `9b97741..1c9d416`。L1 的 POSIX 逐段 ancestor fd 与两项 Linux-only 原失败条件测试达到代码/合同关闭；M1 在 final capture 后、canonical marker rename 前仍可被定向改变 artifact，独立反例得到成功返回和 checksum 不一致的 canonical PASS。正式结论 **CHANGES_REQUESTED（1 Medium）**，见 `reviews/phase-00-eighth-remediation-rereview-2026-07-27.md`。
- Phase 0 / U-004 第九轮候选：实现 `f3fa054` 删除 withheld marker、final-path capture 与文件级 marker rename；完整 canonical bundle 在 producer-private sibling 做 exact match/full verifier 后以单次目录 rename 发布，rename 后无 fallible success 步骤。Python 79 methods 为 Windows 77/2，Surefire 274/274、9 模块 package、Checkstyle 0，三路静态复审 0 finding；材料为 `reviews/phase-00-ninth-remediation-submission-2026-07-27.md`。
- Phase 0 / U-004 第九轮正式独立增量复核：冻结 `1c9d416..ea760bf`，确认私有完整 bundle 的全量 verifier 与单次目录发布成立，rename 后没有 final-path 操作；`P00-R3-M1` CLOSED，本轮 0 finding，整改增量 PASS。独立 Python 77/2、发布专项 8/8、Surefire 274/274、9 模块 package 与 diff check 通过；报告为 `reviews/phase-00-ninth-remediation-rereview-2026-07-27.md`。
- Phase 0 / U-004 第九轮最终动态证据复核：Linux/POSIX 79/79、全新隔离栈 exact 7/33、同 bundle 离线 verifier、Compose `config --quiet` 与专属资源清理五项均 PASS。Codex 独立复算 14/14 checksum、15-file exact closure、7 XML/33 testcase、candidate/tree/spec/source/worktree 与 preflight/runtime target identity，全量一致；首次 dirty worktree 运行按设计失败，成功 evidence 窗口三段 tracked clean，治理文档仅在 bundle 发布后恢复。正式结论 **PASS，0 finding**，见 `reviews/phase-00-ninth-remediation-dynamic-evidence-rereview-2026-07-28.md`。
- 所有逐阶段复核闸门已关闭；最终全量审计随后给出 **CHANGES_REQUESTED / NO-GO**。浏览器不属于
  Phase 0 门禁；逐阶段 PASS 与当前本地整改自测都不等于项目级 merge、push、deploy、切流或
  稳定发布 GO。
- 本轮是“逐阶段进度真实性 + 测试真实性 + 发布门禁”的复核，不替代最后的全量安全、业务规则、数据一致性、性能与运行期审计。

## 2. 逐阶段复核矩阵

状态口径：

- **✅ 已复核**：存在阶段/WP 复核报告且结论 PASS。
- **⚠️ 证据欠账**：实现或历史状态存在，但缺正式报告、验收勾选或独立对抗性证据。
- **🟦 待复核**：当前工作包自测完成，尚未由独立复核者 PASS。

| 阶段 | 实现/合并状态 | 正式复核证据 | 本轮复核结论 |
|---|---|---|---|
| Phase 0 | 原始任务 10/10；第九轮实现 `f3fa054`、最终材料 `ea760bf` 未合并 | `phase-00-ninth-remediation-dynamic-evidence-rereview-2026-07-28.md` PASS | ✅ 已复核；0 finding，放行最终全量审计 |
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
| Phase 44 | 已合并（含 44a–44f）；第四轮整改 `228a355` 未合并 | `reviews/phase-44-fourth-remediation-rereview-2026-07-27.md` PASS（1 Low 非阻断） | ✅ 独立复核 PASS；第三轮 1 Medium / 4 Low 原失败条件关闭，放行 Phase 0 |
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
| FINAL-HIGH 最终审计 7 High | F07 最终候选 `091538b7...c0f8e7`；其余 finding 见各自正式证据 | F01–F07 动态子门禁全部关闭；F07 定向 2/2、69/69、完整 116/116、0 open finding | ✅ FINAL-HIGH scoped 动态闭环；项目正式基线仍为 CHANGES_REQUESTED / NO-GO |
| FINAL-MEDIUM-1 确定性 6 Medium / 3 Low | 同一未提交工作树 | 前端五门禁、diff check；静态/真实性复核 | 🟥 独立复核 CHANGES_REQUESTED；本轮 1H/4M/2L 见正式报告 |
| FINAL-EVIDENCE-R7 候选指纹与 exact gate | raw bundle `a66eda76` / tree `321a3093` | bundle verify/fsck；manifest 逐字节重现；11/11 hash；4/4 + 1/1 exact gate | ✅ 独立增量 PASS；0 finding；旧 freeze 明确排除 |
| FINAL-F01-DYN ERROR 导出双 owner 门禁 | 统一候选 `d2e3a7b1...c4105c` | Phase 10 15/15；候选/source/marker/checksum/teardown 闭合 | ✅ DYNAMIC_CLOSED（独立复核） |
| FINAL-F02-DYN 审计失败关闭与可信代理门禁 | fingerprint `3cddcb74...f98d2` | 独立真实门禁：Surefire 349/349、Phase 13 11/11、manifest 前后相同；rendered Nginx forged-XFF 通过 | ✅ DYNAMIC_CLOSED / INDEPENDENT_INCREMENTAL_PASS；隔离资源已清理，线上未触碰 |
| FINAL-F03-DYN 陈旧聚合写交错门禁 | 统一候选 `d2e3a7b1...c4105c` | Phase 8 9/9 + Phase 9 10/10；四组双连接交错 | ✅ DYNAMIC_CLOSED（独立复核） |
| FINAL-F04-DYN XLSX 预览资源门禁 | 统一候选 `d2e3a7b1...c4105c` | `-Xmx128m` 14/14；`MaxHeapSize=134217728` | ✅ DYNAMIC_CLOSED（独立复核） |
| FINAL-F05-DYN 敏感投影门禁 | 统一候选 `d2e3a7b1...c4105c` | Phase 3/9/10 34/34 | ✅ DYNAMIC_CLOSED（独立复核） |
| FINAL-F06-DYN logout 合同门禁 | 统一候选 `d2e3a7b1...c4105c` | Phase 2 4/4 | ✅ DYNAMIC_CLOSED（独立复核） |
| FINAL-F07-DYN 媒体 Cookie 退出失效 | fingerprint `091538b7...c0f8e7` / HEAD `aa3509a` | 定向 2/2；Phase 5/6/7 69/69；完整 8-suite 116/116；Surefire 352/352 | ✅ DYNAMIC_CLOSED / INDEPENDENT_INCREMENTAL_PASS；0 open finding |
| WS-5/T1 HTTPS/HSTS 配置合同 | fingerprint `9e33d20f...a83a` / HEAD `aa3509a` | Compose 端口、443/18443 跳转、证书手册原失败条件均闭环 | ✅ DYNAMIC_CLOSED / INDEPENDENT_INCREMENTAL_PASS；0 open finding |
| WS-5/T2 CSP 与短期 access | 原候选 `c15d8ace...b124ce` | 产品兼容 CSP、浏览器门禁与 access TTL 900 秒 | ✅ SCOPED_DYNAMIC_PASS；不随 T1 整改重开 |
| WS-5/T3 HttpOnly refresh | 原候选 `c15d8ace...b124ce` | host-only HttpOnly/prod Secure/Strict Cookie；真实 Phase 2 4/4 与浏览器会话链 | ✅ SCOPED_DYNAMIC_PASS；不随 T1 整改重开 |
| WS-5/STAGE T1–T3 | 正式报告 `ws05-remediation-independent-evidence-20260811` | T1 独立增量 PASS + T2/T3 scoped PASS | ✅ INDEPENDENT_SCOPED_PASS；WS-5 范围 0 open finding，不等于项目 GO |
| WS-6/STAGE 前端测试门禁 | fingerprint `f5d63378...8607` / HEAD `aa3509a` | multipart 大小写反例与同一 helper 正/反路径；fresh transform Chromium 5/5 | ✅ INDEPENDENT_SCOPED_PASS；0 Critical / 0 High / 0 Medium / 0 Low，不等于项目 GO |
| WS-7/STAGE 容器/CI 供应链硬化 | fingerprint `d5ea9863...9e24`；Hosted run `31507732334` | 4/4 jobs；双 SPDX、镜像身份、校验和与 artifact 闭环；唯一 Low 的临时 remote 已后续清除 | ✅ INDEPENDENT_STAGE_PASS（0C/0H/0M/1L）；只放行 WS-8，不等于项目 GO |
| WS-8/STAGE 身份证号加密 + HMAC 唯一键 | R2 fingerprint `09ee0c39...42ba`；Hosted run `31661893931` | 六-suite 46/46 scoped PASS；最新正式退回 0C/0H/1M/1L，过期 WS-7 当前态合同导致整条 workflow 红灯 | ◐ LOCAL_REMEDIATION_R3_READY / HOSTED_WHOLE_WORKFLOW_PENDING；WS-9 不启动 |
| WS-4 / D0 全站 UI 基线 | 静态基线；无真实改前运行态 | 74 Vue 静态盘点；6 角色×22 路由×5 断点矩阵；重复入口口径已确认并静态应用 | ✅ STATIC_BASELINE_COMPLETE / BEFORE_STATE_UNAVAILABLE_NONBLOCKING |
| WS-4 / D1 设计基线与双样板 | fingerprint `745e9986...d4d9` / HEAD `aa3509a` | 共享组件、Student 列表、Training Drawer；lint/type-check/build；三角色五断点 30/30；导航与双 Drawer 5/5 | ✅ INDEPENDENT_SCOPED_PASS |
| WS-4 / D2 高频 Grid | 同上统一候选 | 七页五断点 55/55；Grid 实际列数 37/37 | ✅ INDEPENDENT_SCOPED_PASS |
| WS-4 / D3 Overlay 与异步动作 | 同上统一候选 | 相关页五断点 110/110；代表 Overlay 16/16；写反例 2/2 | ✅ INDEPENDENT_SCOPED_PASS |
| WS-4 / D4 布局导航与公共门面 | 同上统一候选 | 五断点 25/25；导航/权限/首改密 10/10 | ✅ INDEPENDENT_SCOPED_PASS |
| WS-4 / D5 非 Overlay 反馈与真实空态 | 同上统一候选 | 隔离前端状态机与键盘检查 24/24 | ✅ INDEPENDENT_SCOPED_PASS |
| WS-4 / D6 复合页与上传流程 | 同上统一候选 | 六页五断点 30/30；依赖恢复、导入互斥/失败重试、视频手机流程 6/6；证据校验和闭合 | ✅ INDEPENDENT_SCOPED_PASS |
| WS-4 / D1–D6 集中整改 | fingerprint `745e9986...d4d9` / HEAD `aa3509a` | 原 5 Medium 已关闭；同候选 gap 18/18、D5 24/24、D6 36/36、增量 85/85 与 62/62 checksum 成立；原追加 2 Medium / 1 Low 已经正式更正撤销阻断定级 | ✅ INDEPENDENT_SCOPED_PASS（0 High / 0 Medium）；不等同项目发布 GO |

历史 WS 链风险证据见 `reviews/current-ws-chain-audit-2026-07-23.md`；当前 FINAL 动态闭环顺序以本节下方队列为准。

## 4. 统一优先队列

### P0：复核与发布门禁

1. **U-001 CI 可复现性（WS-3 Major-3）—✅ 独立重核 PASS**：后端 job 的固定版本 MinIO、健康检查、桶初始化与前端 `npm run type-check` 已静态核对；独立 CI 等价环境全量 266/266、前端 type-check/build 通过。Phase 0 候选已落地最小 ESLint；后续 WS-6 已补 Vitest、Playwright 产品冒烟与 scoped axe，格式化和更严格规则不在该最小产品门禁内。
2. **U-002 WS-3 退回整改—✅ 独立重核 PASS**：`88d3136..2886442` 已确认关闭第五轮新增的 2 Medium：① reconciliation trigger 绑定独立 `TaskScheduler`，真实 scheduling + blocked backup 证据证明普通备份不再阻断对账提交；② `video_finalization_object_candidate` 纳入 37 表逻辑全量备份，scratch restore 后历史 generation、claim/retry/tombstone 完整且真实对账可续跑。唯一新增 Low 是提交材料把 32 个迁移写成 33 个，已在正式报告和活动文档勘误，不阻断。该报告只证明 candidate slice；当时仍独立退回的 Phase 41 整体备份问题现已由后续动态证据报告关闭。V32 发布继续遵守停写、停全部旧节点/worker、迁移、全量启动新实例后再放流，禁止 V31/旧稳定 key 协议混部与旧二进制回滚。独立报告为 `reviews/ws-03-sixth-remediation-rereview-2026-07-24.md`；未执行任何 cyber 指令，后续任何可能属于 cyber 的命令必须明确交由用户决定并亲自执行。
3. **U-003 阶段退回整改—✅ 独立重核 PASS**：Phase 42、Phase 39、Phase 41、Phase 47、Phase 53 与 Phase 44 均已独立复核 PASS。Phase 44 第四轮 `69f7462..228a355` 的精确 16/16、棕地只读 identity preflight、Compose 双向展开与独立离线门禁成立，正式报告为 `reviews/phase-44-fourth-remediation-rereview-2026-07-27.md`；1 Low 非阻断，稳定发布前接入权威发布步骤并补 target marker。
4. **U-004 Phase 0 复核退回整改—✅ 独立重核 PASS**：第九轮最终 SHA `ea760bf` 的 Linux 79/79、全新隔离栈 exact 7/33、同 bundle verifier、Compose 与资源清理全部闭环；checksum/XML/source/target/worktree 绑定经独立复算，0 finding。正式报告为 `reviews/phase-00-ninth-remediation-dynamic-evidence-rereview-2026-07-28.md`。

**当前下一步：F01–F07、WS-5、WS-6 与 WS-7 门禁均已关闭；WS-8 R2 Hosted 六-suite 46/46 仅获 scoped PASS，最新阶段结论仍退回 1 Medium / 1 Low。Low 已在外部证据包补成 17/17 exact closure；过期 WS-7 当前态合同已最小修复。下一步冻结 R3、取得整体绿灯 Hosted workflow 并独立重核；阶段 PASS 前不领取 WS-9。**
2026-08-10，F07 候选 `091538b7...c0f8e7` 取得 `INDEPENDENT_INCREMENTAL_PASS`：定向 2/2、F07
69/69、完整 116/116、Surefire 352/352、F04 低堆 14/14，0 open finding。该证据只绑定已复核 manifest；
后续治理和 WS-5 变更是新候选，不得挪用 F07 绿灯。WS-5 整改候选 `9e33d20f...a83a` 已取得
`INDEPENDENT_INCREMENTAL_PASS（0 open finding）`：T1 `DYNAMIC_CLOSED`，结合 T2/T3 scoped PASS，
WS-5 为 `INDEPENDENT_SCOPED_PASS`。WS-6 第二轮候选 `f5d63378...8607` 已取得
`INDEPENDENT_INCREMENTAL_PASS（0 Critical / 0 High / 0 Medium / 0 Low）`：原 multipart Medium 正式关闭，
两个 Low 不重开；WS-6 为 `INDEPENDENT_SCOPED_PASS`。WS-7 最终候选 `d5ea9863...9e24` 已取得
`INDEPENDENT_STAGE_PASS（0 Critical / 0 High / 0 Medium / 1 Low）`：Hosted run `31507732334` 的 4/4 jobs、
双 SPDX、镜像身份、校验和与 artifact 已独立核验；唯一 Low（临时 evidence remote）已后续清除。该 PASS 仅放行
WS-8；当前不执行依赖/镜像扫描或攻击性检查。项目仍为
**CHANGES_REQUESTED / NO-GO**，
不 merge、push、deploy、切流，线上 `dfdfb91` 保持冻结。

### P1：当前产品需求与上线安全

5. **WS-4 全站 UI 优化—✅ scoped PASS**：D0 静态基线与 D1–D6 统一候选复核已完成；真实改前运行态不可补造且非阻断，不再为建议项修改产品代码。
6. **WS-5 传输与会话安全—✅ independent scoped PASS**：T1–T3 范围 0 open finding；不等于项目发布 GO。
7. **WS-6 前端测试/a11y 门禁—✅ independent scoped PASS**：第二轮候选范围 0 open finding；不扩到全仓 Prettier、bundle 拆分或真实后端栈，也不等于项目 GO。
8. **WS-7 容器/CI 供应链硬化—✅ independent stage PASS**：fingerprint `d5ea9863...9e24`，Hosted run `31507732334` 已闭环双 SPDX、镜像身份、校验和与 artifact；唯一 Low 的临时 remote 已后续清除。依赖/镜像扫描仍不在本阶段范围，PASS 不等于项目 GO。

### P2/P3：结构与运维债务

9. **WS-8—◐ R3 整体 Hosted 绿灯待形成** 身份证号应用层加密 + HMAC 唯一键；R2 run
   `31661893931` 的六-suite 46/46 只构成 scoped PASS，最新正式状态为
   `CHANGES_REQUESTED（0C/0H/1M/1L）`。过期 WS-7 当前态合同和证据包 Low 均已最小整改，当前为
   `LOCAL_REMEDIATION_R3_READY / HOSTED_WHOLE_WORKFLOW_PENDING`。
10. **WS-9—未启动** 拆分 `ExchangeServiceImpl` / `VideoReviewServiceImpl`；依赖 WS-8 正式独立 PASS。
11. **WS-11** health liveness/readiness 与指标。
12. **WS-12** 前端 bundle 拆分与体积预算。
13. **WS-14** 备份生命周期与归档；可播放 demo 视频已提升到 U-003 的 Phase 53 发布整改。
14. **WS-15（可选）** 审计异步重试，仅在不破坏事务/顺序/可靠性的方案有实验证据时实施。
15. **最终全量审计当前保留项**：裁定 Phase 7 有效预签名 URL 的 bearer 合同与“拿到链接后未登录也失败”的规格冲突；复核 Phase 53 外部精确同-key 写无 CAS、旧新初始化器禁止混部，以及大媒体/异常编码的广覆盖证据；同时重新审计项目级安全、业务规则、数据一致性、性能、运行期与发布 GO。

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
5. U-001–U-004 均已独立重核 PASS，逐阶段整改链已关闭；最终全量审计现为
   `CHANGES_REQUESTED / NO-GO`。只有整改后的正式全量重审可以裁定 merge、push、deploy、切流或
   稳定发布 GO。
