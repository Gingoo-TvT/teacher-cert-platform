# 最终全量审计第五整改候选独立增量复核（2026-07-29）

> 本报告是最终全量审计结束后的第五整改候选独立增量复核，不重新执行或改写最终全量审计。
>
> 本轮结论：**CHANGES_REQUESTED / NO-GO**。第二轮遗留的两个 Medium 均已按原失败条件
> `FUNCTIONALLY_CLOSED`；但真实门禁的候选摘要没有覆盖参与编译的 untracked 源码，新增
> **1 Medium / 1 Low** 证据完整性问题。

## 1. Executive Summary

- 最终全量审计基线：`dfdfb9199215d856b70abb653cb65b6f9ed46282`；其正式结论保持
  **CHANGES_REQUESTED / NO-GO（0 Critical / 7 High / 13 Medium / 4 Low）**。
- 当前仓库 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`，分支
  `codex/final-audit-high-remediation`。
- 本轮冻结对象仍是 HEAD 后未提交工作树。复核开始时为 81 个 tracked 文件、92 个 untracked
  文件；原始字节口径的 `git diff --binary HEAD` SHA-256 为
  `c4473e7638973f1a0d8df3e240f2e2f607c56002aa4cf3a1a88b373ea63294ab`，
  与真实门禁 README 完全相同，离线门禁结束后保持不变。
- 本轮 10 个关键生产/测试文件按“`sha256  path`、路径排序、LF 结尾”冻结的
  `scope-manifest-sha256` 为
  `8e8db81abf7d309e351c7722c0c3d4bd15c0a9a0d834e9339678983fa15c1945`。
  该清单包含 untracked 的 `DatabaseBackupProperties.java`，但它是在真实门禁完成后由本复核生成，
  不能倒推为门禁执行时的严格字节证明。
- 两个原 Medium 均达到功能闭环：
  - 密码重置只在事务化 service 写一条精确 `bizId/target/SUCCESS` 审计；Controller 不再产生
    重复空目标记录；真实 `Phase2SecurityIT` 为 **4/4 PASS**，包含审计失败后的真实 MySQL
    全行回滚反例。
  - `Phase41BackupIT` 已注入生产使用的 `DatabaseBackupProperties`，临时收紧并恢复上限；
    对外固定错误与内部诊断边界一致；正式恢复脚本为 **1/1 PASS**。
- 证据包自身 7/7 SHA-256 一致，两份 XML 的 selector、suite、testcase 和计数与当前源码对应，
  两次日志均 `BUILD SUCCESS`，且 Phase 2 首轮完整重编后 Phase 41 复用同一编译快照。
- 新增 **1 Medium**：门禁只绑定 tracked diff，没有列出或哈希 32 个 untracked 状态分组；其中
  `DatabaseBackupProperties.java` 是 Phase 41 生产和测试的直接编译输入。因此当前摘要不能唯一
  标识完整候选源码。
- 新增 **1 Low**：证据 README 把“81 tracked + 32 untracked = 113 个状态项”写成了
  “113 个 tracked 改动、32 个未跟踪项”。
- 独立本机门禁通过：聚焦 Surefire **33/33**、全仓 Surefire **50 suites / 349 tests**、
  后端 **9/9 modules package**、Checkstyle 0、证据哈希/XML 核验和 `git diff --check`。
  Codex 没有运行 Docker、真实数据库、Redis、MinIO、网络、HTTP、服务或浏览器。

### Score Dashboard

以下分数只衡量本轮两个 Medium 及其证据，不是项目总体发布评分。

| Dimension | Score / 10 | 结论 |
|---|---:|---|
| Security / Audit | 9.0 | 目标、结果、同库事务与失败回滚均有对应证据 |
| Configuration Safety | 9.0 | 生产与 Phase 41 使用同一类型化配置 |
| Error Boundary | 9.0 | 客户端固定错误、内部记录保留诊断 |
| Testing Authenticity | 6.0 | 动态用例通过，但摘要未覆盖关键 untracked 源码 |
| Evidence Accuracy | 6.5 | 产物哈希完整；工作树计数和“严格绑定”表述不准确 |
| Build / Regression | 9.0 | 独立 349/349、9/9 package、Checkstyle 与 diff check 全绿 |
| **Scoped overall** | **8.1** | **功能关闭，证据绑定须整改** |

### Finding Statistics

本表只统计本轮新增问题，不重复计算已经功能关闭的两个 Medium。

| Severity | Count | Residual | New |
|---|---:|---:|---:|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 0 | 1 |
| Low | 1 | 0 | 1 |
| Info | 0 | 0 | 0 |
| **Total** | **2** | **0** | **2** |

## 2. Incremental Change Summary

### Change Summary

- Total tracked files changed in the stacked worktree: 81
- Untracked files present at review freeze: 92
- Lines added: 2669
- Lines deleted: 544
- Commits in range: 0；候选仍位于 `aa3509a` 后的未提交工作树
- Authors: 无不可变提交，不能从 Git 安全归属
- Focused review scope: 密码重置结构化审计、Phase 2 回滚反例、Phase 41 配置夹具与真实证据包

### Change Categories

- Production behavior: 密码重置审计接线
- Configuration/test fixture: Phase 41 使用统一配置 Bean
- Integration tests: Phase 2 第四条真实 MySQL 回滚反例
- Evidence: Phase 2 / Phase 41 日志、Failsafe XML/TXT、SHA256SUMS
- Documentation: 当前计划、进度和开发日志中的候选说明

### Risk Delta

- Existing Mediums functionally closed: 2
- Existing risks made worse: 0
- New evidence-integrity risks: 1 Medium
- New documentation inaccuracies: 1 Low

### Test Coverage Delta

- `Phase2SecurityIT`: 3 条增至 4 条，新增审计写失败后的 HTTP/事务/MySQL 回滚反例。
- `Phase41BackupIT`: 仍为 1 个聚合用例，已恢复真实 MySQL 8.4 CLI、37 表恢复和超限失败关闭门禁。
- External evidence: Phase 2 **4/4**；Phase 41 **1/1**；两次各带出 Surefire **349/349**。
- Independent offline regression: focused **33/33**；full Surefire **349/349**；package **9/9**。
- Remaining gap: 动态执行没有绑定全部 build-affecting untracked 源码字节。

### Approval Recommendation

**Request changes**。功能修改无需继续扩张；先固化完整候选并重跑两个精确真实门禁，再做一次只核
证据绑定的轻量复核。当前不授权 merge、push、deploy 或切流。

## 3. Project Map

| 区域 | 本轮复核面 |
|---|---|
| `platform-security` | `SecurityAdminServiceImpl.resetPassword` 事务与结构化审计 |
| `platform-system` | `AuditLogService` 同事务语义、`DatabaseBackupProperties`、备份错误边界 |
| `platform-boot` | Controller 去重审计、Phase 2/41 集成测试 |
| `docs/reviews/evidence/...fifth-remediation...` | 日志、XML、摘要、SHA256SUMS 与候选绑定 |
| governance docs | 当前计划、进度与开发日志状态 |

### Coverage Matrix

| 维度 | 覆盖度 | 已执行 | 未执行及原因 |
|---|---:|---|---|
| Security / Audit | High | 生产链路读码、单测、Phase 2 XML/日志验真 | Codex 遵守边界，未连接真实 MySQL |
| Configuration | High | Bean 注入、getter/setter、恢复逻辑与生产消费者读码 | Codex 未连接 MinIO/MySQL |
| Testing Authenticity | High | 哈希复算、XML/testcase/selector、编译时序、日志核对 | 没有执行可能涉及真实服务的命令 |
| Build / Regression | High | 聚焦 33/33、全仓 349/349、9/9 package、diff check | 不含 Failsafe 本机重跑 |
| Documentation | High | README、统一计划、进度与 DEVLOG 交叉核对 | README 存在一处计数错误 |

## 4. Two-Medium Closure Matrix

状态定义：

- `FUNCTIONALLY_CLOSED`：当前源码移除原失败条件，且真实动态证据覆盖该失败条件。
- `EVIDENCE_BLOCKED`：功能行为可关闭，但候选完整字节身份仍不足以形成严格独立 PASS。

| 上轮问题 | 严重度 | 本轮状态 | 独立结论 |
|---|---:|---|---|
| 密码重置成功审计没有目标用户 | Medium | FUNCTIONALLY_CLOSED | 单条结构化审计精确绑定两个真实目标；审计写失败使真实 MySQL 密码更新回滚 |
| Phase 41 仍反射已删除配置字段 | Medium | FUNCTIONALLY_CLOSED | 注入同一配置 Bean；正式脚本 1/1，通过真实恢复与超限失败关闭 |
| 完整候选身份 | — | EVIDENCE_BLOCKED | tracked diff 匹配，但关键 untracked 编译输入没有运行前后清单或哈希 |

## 5. Top Risks

1. 修改任何 build-affecting untracked 源码都不会改变 `c4473e...`，现有门禁摘要无法区分修改前后
   两份完整工作树。
2. 候选尚未固化成提交；若直接 stage/commit 时漏掉 `DatabaseBackupProperties.java`，生产构建会
   缺类或回退到与门禁不同的源码集合。
3. Redis token 撤销发生在 MySQL 审计提交前；审计失败时密码和审计会回滚，但旧 token 可能已提前
   失效。这是已明确记录的安全侧跨资源边界，不是本轮新增 finding。

## 6. Detailed Findings

### Finding: 真实门禁摘要未覆盖参与编译的 untracked 源码

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity / Evidence Integrity
- Status: Confirmed new finding
- Affected area: 第五整改候选真实 Phase 2 / Phase 41 门禁的候选身份
- Evidence:
  - `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/README.md:6-10,50-52`
    只记录 HEAD 与 tracked diff SHA-256，却把同一工作树称为“严格绑定”。
  - `platform-system/src/main/java/cn/edu/gpnu/platform/system/config/DatabaseBackupProperties.java:15-21`
    当前为 Git untracked 文件。
  - `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:4,106,493-496`
    生产备份直接编译并读取该类。
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase41BackupIT.java:5,101-102,150,602-629`
    真实门禁也直接编译并修改同一 Bean。
  - 证据目录只有日志、XML/TXT、README 与 SHA256SUMS，没有运行前后的完整源码清单或归档。
- Problem: `git diff --binary HEAD` 不包含 untracked 文件。虽然该摘要与当前 tracked 差异完全匹配，
  但它不能唯一确定门禁实际编译的完整源码。
- Why it matters: 本轮结论依赖真实 Phase 41 和真实 MySQL 回滚。若关键配置类在门禁后改变，摘要仍
  不变，独立消费者无法仅凭证据包判断当前候选是否就是被测试版本。
- Realistic failure scenario: 在门禁完成后修改 untracked 的 `DatabaseBackupProperties.java`，
  HEAD 和 `c4473e...` 都不会变化；原证据包仍会看似绑定当前候选。
- Minimal fix: 先把所有 intended、build-affecting 候选文件固化为一个提交，再在该 SHA 上重跑
  `Phase2SecurityIT` 与 `scripts/test-phase41-backup-restore-real.sh`；若暂时不能提交，则在运行
  前后生成覆盖 tracked 与 build-affecting untracked 文件的路径+SHA-256 manifest，并要求一致。
- Better long-term fix: 真实门禁入口拒绝存在 build-affecting untracked 文件，或只从
  `git archive <candidate-sha>` 的源码运行。
- Regression test suggestion: 门禁 preflight 记录 HEAD、cleanliness 和源码 tree/manifest digest；
  运行结束再次复算，不一致则失败。
- Estimated effort: 10–20 minutes 固化候选，外加两项既有真实门禁运行时间

### Finding: README 把 113 个总状态项误写成 113 个 tracked 改动

- Severity: Low
- Confidence: High
- Category: Documentation / Evidence Accuracy
- Status: Confirmed new finding
- Affected area: 第五整改候选证据身份说明
- Evidence:
  - `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/README.md:9`
    写“113 个 tracked 改动、32 个未跟踪项”。
  - 本轮冻结时有 81 个 tracked 状态项和 33 个 untracked 状态分组；去掉门禁后新增的证据目录，
    执行时应为 81 tracked + 32 untracked = 113 个总状态项。
- Problem: 文档把总状态项数量误标成 tracked 数量。
- Why it matters: 不影响 XML 或日志结果，但会误导后续复核者对候选规模与摘要覆盖范围的理解。
- Realistic failure scenario: 后续人员按“113 tracked”核对时得到 81，误判工作树在归档后发生大面积
  丢失或漂移。
- Minimal fix: 改为“113 个工作树状态项：81 tracked、32 untracked 分组”。
- Better long-term fix: 证据生成脚本分别输出 tracked 文件数、untracked 状态分组数和 untracked
  文件数，不手工相加描述。
- Regression test suggestion: README 计数由同一 preflight 结构化输出生成。
- Estimated effort: 5 minutes

## 7. Security and Audit Analysis

- `SystemSecurityController` 已移除 resetPassword 的通用 `@AuditLog`，不会再写重复空目标记录。
- `SecurityAdminServiceImpl.resetPassword` 的 `@Transactional(rollbackFor=Exception.class)` 覆盖
  密码 CAS、token 撤销调用和 `auditLogService.record`；审计异常未被吞掉。
- 结构化审计固定写入 `bizType=systemUser`、`bizId=id`、`target=user:{id}`、
  `operation=resetPassword`、`newStatus=SUCCESS`，不包含临时口令或密码哈希。
- `Phase2SecurityIT` 对 STUDENT/STAFF 两次重置分别核对目标，并在审计故障时比较
  `sys_user` 全行、审计计数和旧口令登录。
- Redis 撤销不参与 MySQL 事务；失败时可能多撤销旧 token。该方向偏向安全侧，本轮不引入分布式
  事务或消息系统。

## 8. Data Integrity and Configuration Analysis

- `DatabaseBackupService.statementLimit()` 只通过 `DatabaseBackupProperties.validate/get` 读取上限。
- `Phase41BackupIT` 保存原值、收紧至 1 MiB，并在 `finally` 恢复；旧反射字段已完全删除。
- 对外异常固定为“备份执行失败，请查看备份记录”；内部 `backup_record.error_message` 仍保留
  table/maxBytes，且测试验证不泄漏大字段。
- 真实 Phase 41 日志栈指向当前 `assertOversizedStatementFailsClosed` 行号，1/1 PASS；功能层面
  没有发现新的配置或恢复回归。

## 9. Testing Authenticity Analysis

| 证据 | 独立核验 | 边界 |
|---|---|---|
| `SHA256SUMS` | 7/7 匹配 | 只覆盖归档产物，不覆盖源码 |
| Phase 2 XML/TXT | 4/4，selector/suite/testcase 精确 | 执行者提供的真实依赖结果 |
| Phase 41 XML/TXT | 1/1，selector/suite/testcase 精确 | 执行者提供的真实依赖结果 |
| 两份 run3 日志 | BUILD SUCCESS；各自带出 Surefire 349/349 | 不含完整源码 manifest |
| 编译时序 | Phase 2 完整重编，Phase 41 复用同栈产物 | 支持同工作树声明，但不是字节级绑定 |
| tracked diff | 当前与归档均为 `c4473e...` | 不包含 untracked 文件 |
| 独立聚焦 Surefire | 33/33 | 纯离线，不含 Failsafe |
| 独立全仓 Surefire | 50 suites / 349 tests | 纯离线，不含真实依赖 |

首次聚焦 Maven 命令因 PowerShell 未引用
`-Dsurefire.failIfNoSpecifiedTests=false`，在聚合 POM 以 “Unknown lifecycle phase” 失败，0 个
测试执行；引用该参数后同一选择器重跑 **33/33 PASS**。前一次是命令构造问题，不计产品失败。

## 10. Frontend State and Documentation Analysis

- 本轮没有前端代码增量或前端行为结论，不重复运行浏览器/Node 门禁。
- 证据 README 正确区分执行者环境事实与 Codex 本机核验，也明确不授权部署；问题仅在完整候选身份
  和 tracked 计数表述。
- 统一计划、进度与 DEVLOG 在本报告后同步本轮结论；最终全量审计基线不改写。

## 11. Principles Compliance

### Principles Violated

- **Evidence must identify the tested candidate**：只绑定 tracked diff，遗漏直接参与编译的 untracked
  生产源码。
- **Evidence metadata must be internally consistent**：113 个总状态项被误写为 113 个 tracked 改动。

### Principles Respected

- **Audit target must be explicit**：密码重置审计已精确绑定被操作用户。
- **Transactional failure must fail closed**：审计写失败会回滚 MySQL 密码更新并向外返回失败。
- **Single source of truth**：生产备份与 Phase 41 测试使用同一配置 Bean。
- **Error boundary separation**：客户端通用消息与内部诊断各自保留正确职责。
- **Bounded remediation**：没有引入 Trigger、DDL 权限、消息队列、ACL 或新基础设施。

## 12. Recommended Fix Order

### Fix Immediately

1. 固化全部 intended、build-affecting 文件为不可变候选 SHA，确保
   `DatabaseBackupProperties.java` 等 untracked 源码被纳入。
2. 由用户在该 SHA 的隔离环境精确重跑 `Phase2SecurityIT` 与 Phase 41 正式脚本，并归档候选 SHA。

### Fix Before Stable Release

3. 修正 README 的 81/32/113 计数表述。
4. 复核固化提交与本报告 10 文件 scope manifest 内容一致；若一致，本轮功能读码无需重复。

### Schedule Later

- 将真实门禁 preflight 的源码身份输出自动化，避免后续手工描述。

### Ignore for Now

- 不引入 ACL、不可变对象存储、远程签名、消息队列或分布式事务。
- 不重跑与本轮两个 Medium 无关的浏览器、媒体、TLS 或供应链门禁。

## 13. Quick Wins

- 最小动作是“完整提交 + 两个 exact gate”，不需要再改生产逻辑。
- 固化前可用本报告的 10 文件 scope manifest 检查内容是否漂移。
- README 计数修正是一行文档改动，不与功能整改混在一起。

## 14. 独立门禁结果

| 命令/核验 | 结果 |
|---|---|
| 证据包 `SHA256SUMS` | PASS；7/7 |
| Phase 2 XML/TXT/日志离线验真 | PASS；4/4，0 failure/error/skip |
| Phase 41 XML/TXT/日志离线验真 | PASS；1/1，0 failure/error/skip |
| 聚焦 Maven Surefire | PASS；33/33，0 failure/error/skip |
| `mvn -B -ntp -o test` | PASS；50 suites / 349 tests |
| `mvn -B -ntp -o -DskipTests package` | PASS；9/9 modules，Checkstyle 0 |
| `git diff --check HEAD` | PASS |
| tracked diff SHA-256 | `c4473e7638973f1a0d8df3e240f2e2f607c56002aa4cf3a1a88b373ea63294ab` |
| 10-file current scope manifest | `8e8db81abf7d309e351c7722c0c3d4bd15c0a9a0d834e9339678983fa15c1945` |
| Docker / DB / Redis / MinIO / 网络 / 服务 / 浏览器 | 未执行，遵守用户安全边界 |

## 15. 最终裁定

本轮独立增量复核结论为 **CHANGES_REQUESTED / NO-GO**：

- 不重跑或改写已经结束的最终全量审计；
- 第二轮遗留的两个 Medium 在当前源码和执行者动态证据下均可标记
  `FUNCTIONALLY_CLOSED`，无需继续修改生产逻辑；
- 新增 **1 Medium** 阻断严格 PASS：真实门禁摘要未覆盖 build-affecting untracked 源码；
- 新增 **1 Low**：证据 README 的工作树计数表述错误；
- 下一轮只需核对不可变候选身份和两个 exact gate，不再扩大复核范围；
- 不授权 merge、push、deploy 或切流；
- 未执行 Docker、数据库、Redis、MinIO、网络、HTTP、浏览器、服务、扫描、fuzz、凭据或权限操作。
  需要真实依赖的命令继续由用户在授权隔离环境执行。
