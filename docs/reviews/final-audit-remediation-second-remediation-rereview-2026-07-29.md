# 最终全量审计第二整改候选独立增量复核（2026-07-29）

> 本报告是最终全量审计结束后的第二整改候选独立增量复核，不是重新执行最终全量审计。
>
> 本轮结论：**CHANGES_REQUESTED / NO-GO**。七个原问题中的六个达到当前候选闭环，
> 审计项仍为 `PARTIAL`，并确认一个候选引入的 Medium 测试门禁回归。

## 1. Executive Summary

- 最终全量审计基线：`dfdfb9199215d856b70abb653cb65b6f9ed46282`；其正式结论仍为
  **CHANGES_REQUESTED / NO-GO（0 Critical / 7 High / 13 Medium / 4 Low）**。
- 当前仓库 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`，分支
  `codex/final-audit-high-remediation`。
- 被复核对象仍是 HEAD 之后的未提交工作树。复核开始时冻结
  `tracked-diff-git-object=1fae98fa0784d89a67541f541c5801cc369f119b`；离线门禁完成后该摘要
  保持不变。七项整改涉及的 16 个关键文件另冻结为
  `scope-manifest-git-object=d4f015c2583ad48c97aa06161a5746107a17a4cf`。
- 当前工作树不是独立提交：共有 75 个 tracked 变更和 84 个 untracked 文件，叠加了此前整批整改与
  审计材料。本报告只重放上一轮 **1 High / 4 Medium / 2 Low** 的失败条件，不把总工作树重新解释为
  一轮全量审计。
- 七项原问题的增量矩阵为：**6 CANDIDATE_CLOSED / 1 PARTIAL**。
  - High：有界 UTF-8 JSON 序列化与真实 sharedStrings 低堆反例成立；
  - Medium：logout 警告、Phase 3 脱敏合同、生产单一备份配置达到候选闭环；
  - Low：固定导入错误文本、Phase 05/07 状态纠正达到候选闭环；
  - 审计 Medium：失败误记成功的主要路径已修，但管理员密码重置成功记录仍无目标用户。
- 本轮确认 **2 个 Medium**：一个是审计 finding 的直接残余；一个是配置字段迁移后
  `Phase41BackupIT` 仍反射旧字段造成的确定性门禁回归。
- 独立离线门禁通过：聚焦 Surefire **34/34**（其中 sharedStrings 在 `-Xmx128m` 下 **14/14**）、
  后端 **9/9 modules package**、前端 lint/type-check/logout contract/build、`git diff --check`。
  这些结果不包含真实 MySQL/Redis/MinIO、Failsafe、HTTP 或浏览器。

### Score Dashboard

以下分数只衡量本轮七项整改范围，不是项目总体发布评分。

| Dimension | Score / 10 | 结论 |
|---|---:|---|
| Security / Audit | 6.0 | 失败成功语义显著改善，但密码重置目标仍为空 |
| Data Integrity / Resource Bound | 8.5 | JSON 写入期预算和真实 sharedStrings 反例成立 |
| Configuration Safety | 8.0 | 生产使用单一配置 Bean；测试消费者未同步 |
| Testing Authenticity | 5.0 | 聚焦单测有效，但 Phase 41 真实门禁源码确定性红灯 |
| Frontend State | 7.5 | logout 失败提示已接线；Node 脚本仍主要是源码正则 |
| Documentation | 9.0 | Phase 05/07 已恢复诚实 pending |
| **Scoped overall** | **7.3** | **必须整改后再复核** |

### Finding Statistics

本表只统计本轮仍需处理的残余或新增问题。

| Severity | Count | Residual | New |
|---|---:|---:|---:|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 2 | 1 | 1 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **2** | **1** | **1** |

## 2. Incremental Change Summary

### Change Summary

- Total tracked files changed in the stacked worktree: 75
- Untracked files present: 84
- Lines added: 2334
- Lines deleted: 531
- Commits in range: 0；候选仍停留在 `aa3509a` 后未提交工作树
- Authors: 无不可变提交，不能从 Git 安全归属
- Seven-finding review scope: 16 个关键实现、测试与阶段文档文件

### Change Categories

这些类别按七项范围统计，类别可重叠。

- New features: 0
- Bug fixes / production behavior: 9 files
- Refactoring / configuration consolidation: 2 files
- Dependency updates: 0
- Tests: 5 files
- Documentation: 2 files

### Risk Delta

- Existing risks fixed at candidate level: 6
- Existing risks partially reduced: 1
- New risks introduced: 1
- Existing risks made worse: 0

### Test Coverage Delta

- New or materially changed focused test files: 5
- New production paths with focused tests: bounded JSON、审计切面、import/logout outcome、敏感投影、
  固定错误文本
- New production change without a synchronized existing gate: `DatabaseBackupProperties` 字段迁移与
  `Phase41BackupIT` 反射夹具
- Deleted tests: 0

### Approval Recommendation

**Request changes**。两个 Medium 必须在合并前处理；当前候选不授权 merge、push、deploy 或切流。

## 3. Project Map

| 区域 | 本轮复核面 |
|---|---|
| `platform-exchange` | 有界 preview JSON、sharedStrings、固定导入错误文本、备份兼容预算 |
| `platform-system` | 单一 `DatabaseBackupProperties`、审计写入接口、备份实际消费者 |
| `platform-boot` | 审计事务切面、改密/重置、import/logout outcome、Phase 3/41 IT |
| `frontend` | logout 失败提示与消息 Provider |
| `docs/phase-05` / `phase-07` | 动态媒体证据状态 |

### Coverage Matrix

| 维度 | 覆盖度 | 已执行 | 未执行及原因 |
|---|---:|---|---|
| Security / Audit | High | 逐路径读码、聚焦 Surefire、UI 字段核对 | 真实 MySQL 同事务回滚由用户环境执行 |
| Data Integrity | High | 有界输出流、sharedStrings、配置数学与消费者读码 | 未连接数据库或对象存储 |
| Testing Authenticity | High | 独立低堆 Surefire、IT 源码与调用链核对 | 遵守安全边界，未运行真实 Failsafe |
| Frontend State | Medium | lint、type-check、Node contract、production build | 未启动浏览器或 Vue/Pinia 行为环境 |
| Documentation | High | Phase 05/07 与当前计划交叉核对 | 无缺口 |

## 4. Seven-Finding Closure Matrix

状态定义：

- `CANDIDATE_CLOSED`：当前工作树移除了原失败条件，并通过适用的安全离线检查；仍需不可变提交及
  对应外部门禁。
- `PARTIAL`：主要失败方向已修，但仍有直接代码残余或必要证据缺口。

| 上轮问题 | 严重度 | 本轮状态 | 独立结论 |
|---|---:|---|---|
| XLSX preview JSON 在完整物化后才检查预算 | High | CANDIDATE_CLOSED | Jackson 写入期间按 UTF-8 字节硬限流；真实 sharedStrings 20k×4096 中文字符在低堆下提前终止 |
| `before=true` 使失败尝试呈现为成功样式审计 | Medium | PARTIAL | 改密/重置失败不再写成功行，import/logout 有 outcome；重置成功仍无 target/bizId |
| logout 撤销失败被界面静默伪装成功 | Medium | CANDIDATE_CLOSED | 本地清理保留，界面明确提示服务端退出未确认 |
| Phase 3 明文生日断言与脱敏合同冲突 | Medium | CANDIDATE_CLOSED | 普通详情改为掩码；同一 IT 保留明文权限正例和 403 反例 |
| preview 与 backup statement limit 双配置 | Medium | CANDIDATE_CLOSED | 两个生产消费者注入同一 `DatabaseBackupProperties` |
| Exchange 返回非业务异常原文 | Low | CANDIDATE_CLOSED | 非 `BizException` 固定为“导入失败”，原异常只进服务端日志 |
| Phase 05/07 提前勾选动态媒体语义 | Low | CANDIDATE_CLOSED | 两处恢复 `[ ]` 并明确动态证据待补 |

## 5. Top Risks

1. 管理员成功重置不同用户密码时，通用切面生成的审计行没有 `bizId/target/newStatus`；审计 UI 中
   多次重置记录无法定位被操作账号。
2. `Phase41BackupIT` 在主恢复流程结束后必调 `assertOversizedStatementFailsClosed()`，该方法仍向
   已删除字段写反射值；真实 Phase 41 门禁会在预期反例之前确定性报错。
3. 当前仍是未提交、含关键 untracked 文件的 stacked worktree；即使修复两个 Medium，也必须先固化
   不可变候选再执行外部门禁。

## 6. Detailed Findings

### Finding: 密码重置成功审计仍无法定位目标用户

- Severity: Medium
- Confidence: High
- Category: Security / Audit Integrity
- Status: Confirmed residual
- Affected area: 系统管理员密码重置审计
- Evidence:
  - `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/controller/SystemSecurityController.java:81-84`
    只通过通用 `@AuditLog` 包裹 `resetPassword(id)`。
  - `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/aspect/AuditLogAspect.java:54-60`
    只写 `bizType/operation/operator/IP`，没有从路径参数取得 `id`，也不写 target/outcome。
  - `frontend/src/views/system/SystemAuditView.vue:104-113` 明确显示对象、新旧状态；该记录三列为空。
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/controller/AuthControllerLogoutTest.java:99-109`
    只反射确认 `before=false`，没有断言成功重置记录的目标。
- Problem: 失败重置已不再留下成功样式行，但成功记录无法回答“重置了哪个账号”，原 finding 的目标
  绑定部分没有闭合。
- Why it matters: 密码重置属于高敏感写操作。多个管理员或连续重置发生后，无法从审计日志完成对象级
  事件还原。
- Realistic failure scenario: 管理员先后重置用户 A、B；审计 UI 出现两条同时间段
  `systemUser/resetPassword`，但对象、业务 ID 和状态均为空，调查者无法确定对应关系。
- Minimal fix: 仅对重置路径写一条与业务同事务的结构化审计，至少包含
  `bizId=id`、`target=user:{id}`、`newStatus=SUCCESS`；移除该路径重复的通用空目标记录，且不得记录
  临时口令。
- Better long-term fix: 后续再为 `@AuditLog` 增加明确的 target/outcome 绑定合同；本轮无需引入新的
  事件系统或外部存储。
- Regression test suggestion: 成功重置两个不同 ID，断言审计目标分别精确绑定；目标不存在、CAS 失败
  和审计写失败时，不得留下成功记录，并在真实 MySQL 下验证密码更新与审计同回滚。
- Estimated effort: 2–4 hours，外加用户侧真实 MySQL 门禁

### Finding: Phase 41 真实备份 IT 仍反射已迁移的旧字段

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity / Configuration
- Status: Confirmed new regression
- Affected area: `Phase41BackupIT` 单语句超限失败关闭反例
- Evidence:
  - `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:102-106,493-496`
    已删除 `maxSqlStatementBytes` 字段，改为从注入的 `DatabaseBackupProperties` 读取。
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase41BackupIT.java:599-600,624-625`
    仍执行 `ReflectionTestUtils.setField(backupService, "maxSqlStatementBytes", ...)`。
  - 同文件 `284` 在主恢复用例中无条件调用该方法；错误发生在预期的备份超限断言之前。
- Problem: 生产配置统一是正确修复，但既有真实门禁消费者未同步。Surefire 347/347 和跳测 package
  都不会执行 `*IT`，因此候选自测无法发现此错误。
- Why it matters: 当前候选无法证明它保持了已归档的 Phase 41 恢复保证；后续真实脚本会因测试夹具
  自身错误失败，而不是给出产品信号。
- Realistic failure scenario: 用户按既有真实 MySQL 8.4 恢复脚本执行，完整恢复路径到达第 284 行后，
  `ReflectionTestUtils` 抛出找不到字段，门禁红灯。
- Minimal fix: 在 `Phase41BackupIT` 注入同一个 `DatabaseBackupProperties` Bean，临时修改
  `maxSqlStatementBytes` 并在 `finally` 恢复；不要再反射生产服务的已删除私有字段。
- Better long-term fix: 配置边界测试只通过公开类型化配置或 Spring 属性绑定驱动，避免测试与实现私有
  字段名耦合。
- Regression test suggestion: 修正后由用户在授权隔离环境重跑
  `scripts/test-phase41-backup-restore-real.sh`，要求真实恢复、超限失败关闭、XML 和资源清理全部 PASS。
- Estimated effort: 小于 1 hour，外加用户侧真实门禁运行时间

## 7. Security and Audit Analysis

- 默认 `AuditLogAspect` 已用 `TransactionTemplate` 包住普通业务写和审计；业务异常不写成功行，
  审计异常向外传播并触发回滚。
- import/logout 使用带 target 的独立 `PENDING → outcome`，异常路径写 `ERROR`；进程中断会留下
  `PENDING`，不会伪装成成功。
- 现有审计测试主要使用 Mockito 或模拟事务管理器，不能替代真实 MyBatis 与 `audit_log` 同资源回滚。
- 密码重置目标缺失是本轮唯一确认的审计代码残余；不要求引入 ACL、消息队列或不可变审计存储。

## 8. Data Integrity and Configuration Analysis

- `BoundedPreviewJsonWriter` 在 Jackson 写入时限制字节，内部缓冲区不超过上限；精确边界成功、超一字节
  失败，避免先创建完整 preview JSON。
- sharedStrings 反例使用真实 OOXML `xl/sharedStrings.xml`，20,000 行复用 4096 个中文字符；getter
  调用数小于总行数，证明超限时没有访问完整预览集合。
- `ExchangeImportProperties` 与 `DatabaseBackupService` 注入同一个
  `DatabaseBackupProperties`，命令行、环境变量或 YAML 最终都落到单一 Spring 绑定结果。
- Base64 膨胀与 1 MiB SQL 余量在启动期校验；向下收紧备份上限会同步导致配置失败关闭。

## 9. Testing Authenticity Analysis

| 证据 | 独立结果 | 真实性边界 |
|---|---|---|
| 聚焦 Surefire | system 3/3 + boot 31/31，合计 34/34 | 不含 `*IT` |
| sharedStrings 低堆 | `-Xmx128m` 14/14 | 真实 OOXML + Jackson；不含 HTTP/数据库 |
| Audit tests | PASS | Mockito/模拟事务管理器，不证明真实数据库回滚 |
| Phase 3 contract | 源码一致 | 未生成新鲜真实 MySQL Failsafe XML |
| Phase 41 gate | 源码确定性失败 | 旧反射字段在 Failsafe 执行时才暴露 |
| logout Node contract | PASS | 主要读取源码正则；未实例化 Pinia/Axios/Vue/Naive Message |

首次聚焦 Maven 命令因 PowerShell 未引用 `-Dsurefire.failIfNoSpecifiedTests=false`，在聚合 POM 以
“Unknown lifecycle phase”失败，0 个测试执行；加引号后同范围重跑 34/34。前一次只属于命令构造错误，
不计产品失败，也不被隐去。

## 10. Frontend State and Documentation Analysis

- `userStore.logout()` 在 `finally` 清除本地秘密并继续传播远端失败；
  `MainLayout` 捕获后明确提示“服务端退出结果未确认”，再进入登录页。
- `App.vue` 的消息 Provider 覆盖主布局，警告调用存在运行上下文；但真实 Redis 故障 + 浏览器显示仍是
  后续动态门禁，不能据 Node 正则脚本宣称 F-06 整体 PASS。
- Phase 3 普通详情与敏感明文端点的源码合同已经一致；真实 Phase 3 Failsafe 仍由用户执行。
- Phase 05/07 已将媒体访问条目恢复 `[ ]`，并写明 HTTP/MinIO/浏览器证据待补，与统一执行计划一致。

## 11. Principles Compliance

### Principles Violated

- **Tests must verify the real contract**：生产配置字段迁移后，真实 Phase 41 IT 仍依赖旧私有字段。
- **Audit records must identify the operation target**：密码重置成功记录缺少被操作用户。

### Principles Respected

- **Fail closed**：JSON 超预算、审计写失败、logout 撤销失败均向失败侧收敛。
- **Single source of truth**：生产 preview 与 backup 读取同一类型化配置。
- **Evidence before status**：Phase 05/07 没有把代码候选写成动态 PASS。
- **Bounded change**：本轮修复没有引入新服务、队列或额外基础设施。

## 12. Recommended Fix Order

### Fix Immediately

1. 同步 `Phase41BackupIT` 到 `DatabaseBackupProperties`，先消除确定性门禁红灯。
2. 为 resetPassword 的成功审计补精确 user target/bizId，并避免重复空目标记录。

### Fix Before Stable Release

3. 固化不可变候选 SHA。
4. 由用户在授权隔离环境执行 Phase 41 真实恢复、审计真实 MySQL 回滚、Phase 3 Failsafe，以及
   Redis logout + 浏览器警告门禁。

### Schedule Later

- 将通用审计注解的 target/outcome 绑定结构化，减少 controller 手工差异。
- 用真实 Vue/Pinia 组件测试替代 logout 源码正则合同。

### Ignore for Now

- 不为这两个 Medium 引入消息队列、外部审计存储、ACL 或新的会话基础设施。

## 13. Quick Wins

- `Phase41BackupIT` 直接注入现有配置 Bean，改动范围应只包含夹具字段及恢复逻辑。
- resetPassword 结构化审计只记录用户 ID/目标和结果，明确禁止口令进入日志。

## 14. 独立门禁结果

| 命令/核验 | 结果 |
|---|---|
| 聚焦 Maven Surefire（`-DargLine=-Xmx128m`） | PASS；34/34，0 failure/error/skip |
| `ExchangeImportResourceBudgetTest` | PASS；14/14，真实 sharedStrings |
| `mvn -B -ntp -o -DskipTests package` | PASS；9/9 modules，Checkstyle 0 |
| `npm --prefix frontend run lint` | PASS |
| `npm --prefix frontend run type-check` | PASS |
| `npm --prefix frontend run test:auth-logout-contract` | PASS，真实性有限 |
| `npm --prefix frontend run build` | PASS；仅既有大 chunk warning |
| `git diff --check HEAD` | PASS |
| 候选源码前后 tracked diff 摘要 | `1fae98fa0784d89a67541f541c5801cc369f119b`，保持不变 |

## 15. 最终裁定

本轮独立增量复核结论为 **CHANGES_REQUESTED / NO-GO**：

- 不重跑或改写已经结束的最终全量审计；
- 不接受当前第二整改候选整体 PASS：审计目标仍有 1 个 Medium 残余，另新增 1 个 Medium
  Phase 41 门禁回归；
- 历史 Phase 41 PASS 报告仍是其当时 SHA 的有效快照；本结论只说明当前 stacked candidate
  破坏了该门禁源码，不能据历史 PASS 放行当前候选；
- 不授权 merge、push、deploy 或切流；
- 未执行 Docker、数据库、Redis、MinIO、网络、HTTP、浏览器、服务、扫描、fuzz、凭据或权限操作。
  需要真实依赖的命令继续交由用户在授权隔离环境执行。
