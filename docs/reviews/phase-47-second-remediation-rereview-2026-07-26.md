# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 47 second remediation
**Audit mode:** incremental + security / stability / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-26
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮以第一轮代码点
`aa6f81c3518259c30cd7190a8c521cef61108843` 为基线，冻结第二轮生产/测试代码
`3bddf6ce7aab8c7640e54675ee7d17aadcedfe93`，并核对提交材料至
`db89d6e7110eb636f3fffac3d71c906d8ac84674`。路径限定后的代码增量为 1 个提交、
2 个文件、`+141/-30`；代码点之后只有文档变化，两份源码 blob 与当前 HEAD 一致。

正式结论：**PASS（0 Critical / 0 High / 0 Medium / 1 Low，Low 非阻断）**。

- 第一轮 Medium 已关闭：锁定的 MinIO SDK 8.5.12 把明确
  `NoSuchLifecycleConfiguration` 归一为高层 `null`，当前实现将该 sentinel 解释为
  ABSENT 并首次创建托管规则；其它异常和畸形非空配置继续在整桶写入前失败关闭。
- 第一轮测试真实性缺口已关闭：正例改为 `thenReturn(null)`，异常形式的同名错误被
  明确反向拒绝；外部规则保留、托管规则替换与幂等跳写仍成立。
- 第一轮日志 Low 已关闭：生产 WARN 只接受六类固定本地枚举，不传异常、原始
  message/code/URL/path/trace。
- 新发现 1 个非阻断 Low：日志测试只证明格式化消息不含敏感哨兵、参数数组不含
  `Throwable`，却没有证明参数数组仅含一个固定分类，也未逐项检查非 Throwable 参数。
  当前生产调用安全，但未来多传一个敏感 String 时，Logback 可保留原始参数而格式化
  消息仍然干净，现有助手可能虚绿。
- 独立离线门禁为 `platform-file` **16/16 PASS**（生命周期测试 13/13）、后端
  **9/9 modules package PASS**、MinIO 8.5.12 依赖/字节码复核与 diff check PASS。

Phase 47 可置独立复核 PASS 并放行 Phase 53 整改；全项目仍因 Phase 0、44、53
保持 **CHANGES_REQUESTED**，本报告不是 merge、部署、切流或项目发布 GO。本轮没有
启动服务、Docker、数据库或真实 MinIO，也没有执行网络、权限、故障注入、扫描、
fuzz、压力或任何可能属于 cyber 的动作。

### Score Dashboard

```text
Security        █████████░  9.2  A   当前日志只传固定枚举；新增 Low 仅是测试防回归不完整
Stability       █████████░  9.0  A   SDK 缺省合同对齐，异常与畸形响应继续失败关闭
Performance     ████████░░  8.2  A   无新增热路径回归；规模与长期残片边界未动态测
Testing         ████████░░  8.2  A   16/16 独立通过；参数数组断言仍有一个明确盲点
Maintainability █████████░  8.6  A   分类纯函数局部清晰，第三方合同仍直接耦合服务层
Design          █████████░  8.6  A   ABSENT/PRESENT/FAILED 语义成立，尚未显式类型化
Release         █████████░  8.8  A   阶段阻断项关闭；全项目仍有三个独立退回阶段
─────────────────────────────────────
Overall         █████████░  8.7  A
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit
mountain).** Overall is the arithmetic mean of the seven displayed dimensions,
rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **1** | **1** | **0** |

## 2. Project Map

- `platform-file` 生产代码：`FileMaintenanceService` 读取、合并并整体写回桶生命周期
  规则；`MinioConfig` 直接注入标准 SDK `MinioClient`。
- `platform-file` 测试：13 个 `FileMaintenanceServiceTest` 用例覆盖缺省、错误矩阵、
  畸形结构、规则保留/替换、幂等与日志分类；`MinioConfigTest` 另有 3 个。
- `platform-boot`：既有 `CleanupScheduleConfig` 在生产门禁开启后调用生命周期确保
  服务，本轮没有变更调度开关、cron 或其它清理作业。
- 根 POM：继续锁定 `io.minio:minio:8.5.12`；没有依赖升级。
- 治理文档：候选提交没有提前宣称 PASS；本报告完成后才同步 Phase 47 与统一队列。

本轮无 DDL/Flyway、数据库数据、对外 API、权限点、状态机、前端生产代码、运行配置
或依赖版本变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `aa6f81c..3bddf6c` 两文件路径限定 diff、`81397c3..3bddf6c` 提交、源码 blob、材料至 `db89d6e` | 未重开其它 Phase |
| Security | Medium | WARN 调用实参、分类器、Logback 事件助手、依赖字节码 | 未执行扫描、攻击请求、凭据或权限操作 |
| Stability | High | SDK null sentinel、全部异常分支、畸形响应、setter 前控制流 | 未运行真实 MinIO 或故障注入 |
| Performance | Medium | 单次规则合并复杂度、调度调用和既有整桶写边界 | 未执行容量、压力或对象规模基准 |
| Testing Authenticity | High | 13 个测试全文件、Mockito 层级、SDK/SLF4J/Logback 字节码、独立 clean test/XML | 未运行真实对象存储 |
| Release | High | 分支、提交、工作区、离线 test/package、依赖树、治理门禁 | 未 merge/push、部署、切流或回滚演练 |
| Configuration | High | 依赖版本、prod 调度门禁和本轮配置零 diff | 未读取仓库外生产配置 |
| Data Integrity | Medium | 整桶规则保留、托管 ID 替换、错误时不写 | 未读取或修改真实桶配置 |
| Concurrency | Medium | get→merge→set 边界及既有运维串行约束 | 未执行并发、锁、压力或故障交错 |
| Maintainability / Design | High | 变更类、分类纯函数、直接调用方与候选材料 | 全仓 MinIO 适配抽象留最终审计 |

## 3. Incremental Change Summary

### Change Summary

- Production/test code files changed: 2
- Code lines added: 141
- Code lines deleted: 30
- Second-remediation code commits: 1
- Documentation-only commits after code freeze: 1
- Production/test code commit: `3bddf6c`
- Submission commit / current HEAD at review start: `db89d6e`
- Author: `wenbibuhaoqwq <wenbibuhaoqwq@localhost>`

### Change Categories

| Category | Files | Summary |
|----------|-------|---------|
| Bug fix | 1 | 对齐 8.5.12 缺省 sentinel，并增加固定错误分类 |
| Tests | 1 | 重写真实 null 正例，扩展异常/日志/畸形响应反例 |
| Documentation / governance | 1 | 第二轮候选提交材料 |
| Dependencies / configuration / migration | 0 | 无变化 |

### Previous Finding Closure Matrix

| Previous requirement | Second-remediation evidence | Independent result |
|----------------------|-----------------------------|--------------------|
| SDK no-config null 必须允许首次创建 | `currentRules` 对 `cfg == null` 返回空工作集；正例断言 setter 一次 | **Closed** |
| 不得在业务层放行不可达的同名异常 | 异常形式 `NoSuchLifecycleConfiguration` 返回 false 且 never set | **Closed** |
| 403/500/其它 404/I/O/XML/畸形响应必须 fail-closed | 对应负例均在 setter 前停止 | **Closed** |
| 成功读取时保留外部规则并只替换托管 ID | 参数捕获证明对象保留、托管 ID 唯一且天数更新 | **Closed for a successful read snapshot** |
| 日志需安全且可操作分类 | 生产仅输出六类固定枚举，不传原始异常数据 | **Closed** |

第一轮 1 Medium / 1 Low 均按原问题口径关闭。新 Low 只影响测试对未来日志调用改动的
保护强度，不推翻当前生产日志的安全结论。

### Risk Delta

- Previous blocking findings closed: 1 Medium
- Previous non-blocking findings closed: 1 Low
- New blocking findings: 0
- New non-blocking findings: 1 Low
- Existing baseline risks made worse: 0

### Test Coverage Delta

- Current `FileMaintenanceServiceTest`: 13
- Current module total: 16
- Corrected false-positive contract cases: 2（不可达异常正例、真实 null 负例）
- Valuable negative cases: 403、500、其它 404、空 ErrorResponse、I/O、XML、
  InvalidResponse、异常形式 no-config、null/empty rules
- Valuable state cases: null→create、外部规则保留、托管规则替换、幂等跳写
- Remaining focused gap: Logback `argumentArray` 未限制 cardinality/固定枚举/敏感字符串
- Real MinIO tests: 0（本轮不需要且按安全边界未执行）

### Approval Recommendation

**Approve / PASS Phase 47。** 新增 Low 进入非阻断测试债清单；建议在进入稳定发布前用
一次小改动收紧日志事件参数数组断言。Phase 53 可按统一计划开始整改，但全项目仍不是
发布 GO。

## 4. Top Risks

1. **Low — logging test can miss a raw extra String argument：** 格式化消息只消费一个
   占位符，Logback 事件仍可保留额外原始参数；当前测试只排除 Throwable。
2. **Carry-forward concurrency boundary：** 生命周期 API 是 get→merge→whole-bucket
   set，没有 CAS；应用任务与人工/外部生命周期修改仍须运维串行。
3. **Carry-forward semantic boundary：** 幂等命中只核托管 ID、abort 对象和 days，
   未比较 status/filter/其它 action 或重复托管 ID。
4. **Project-level blockers remain：** Phase 0、44、53 未因本报告关闭。

后 3 项均是本候选前已有边界，不计本轮 finding，留最终全量审计或对应 Phase 处理。

## 5. Detailed Findings

### Finding: 日志回归助手未验证非 Throwable 原始参数数组

- Severity: Low
- Confidence: High
- Category: Testing Authenticity / Security Regression Protection
- Status: Confirmed
- Affected area: `FileMaintenanceService` 失败日志未来改动的敏感信息防回归测试
- Evidence:
  - File:
    `platform-file/src/test/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceServiceTest.java:286-304`
  - Function/module: `assertFailureLogged`
  - Relevant behavior: 助手检查格式化消息内的哨兵、`throwableProxy == null`，但对
    `argumentArray` 只做 `noneMatch(Throwable.class::isInstance)`。
  - File:
    `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceService.java:90-94`
  - Function/module: `ensureAbortIncompleteMultipartLifecycle`
  - Relevant behavior: 当前生产 WARN 只有一个 `{}` 和一个固定枚举实参，因此当前
    生产调用没有泄露。
  - Evidence file:
    `docs/reviews/evidence/phase47-second-remediation-rereview-2026-07-26/offline-gates-and-logging-contract.txt`
  - Relevant behavior: 锁定的 SLF4J 2.0.17 `MessageFormatter` 在格式化后仍把原始
    `Object[]` 放入 `FormattingTuple`；Logback 1.5.22 `LoggingEvent` 单独保存并返回
    `argumentArray`。
  - File:
    `docs/reviews/phase-47-second-remediation-submission-2026-07-26.md:77-78`
  - Function/module: 候选证据声明
  - Relevant behavior: 材料称参数数组层已证明不含敏感内容；实际测试只证明不含
    `Throwable`，声明强于证据。
- Problem: 测试没有断言参数数组恰有一个元素、该元素就是允许的固定分类，也没有
  对每个非 Throwable 参数应用 message/code/URL/path/trace 哨兵检查。
- Why it matters: 日志框架可能不把多余参数渲染进格式化消息，却仍在事件中保留原始
  参数。未来维护者若在一个占位符后追加异常 message 或 URL，现有测试可能继续通过，
  而 appender、encoder 或下游采集器仍可能读取该原始值。
- Realistic failure scenario: WARN 被改为
  `log.warn("...category={}", category, failure.getMessage())`；格式化消息只显示固定
  category，`throwableProxy` 仍为空，参数数组也没有 Throwable；当前助手通过，但
  第二个 String 仍保留在 `LoggingEvent.argumentArray`。
- Minimal fix: 断言 `argumentArray` 恰有一个元素，且该元素等于预期固定分类；同时
  对数组中每个值的字符串形式执行全部敏感哨兵检查。
- Better long-term fix: 将分类作为结构化、类型安全的日志字段，通过一个小型日志
  适配器集中保证允许字段集合；测试同时验证 formatted message、raw arguments、
  key-value pairs 与 throwable proxy。
- Regression test suggestion: 增加一个仅用于证明助手有效的负向 fixture，构造
  “一个占位符 + 分类 + 额外敏感 String”的事件并断言助手必失败；另补 unknown
  code + HTTP 503 对 `SERVER_ERROR` 的 fallback 分支。
- Estimated effort: 30–60 minutes

## 6. Security Concerns

- Coverage: Medium
- Inspected evidence: 当前 WARN 实参、六类白名单映射、日志事件断言、SLF4J/Logback
  锁定版本和字节码。
- Exclusions / limits: 未执行扫描、攻击性请求、恶意载荷、凭据尝试、权限变更、
  网络探测或任何可能触发 cyber 安全限制的操作。
- Positive evidence: 当前生产日志不传异常对象、服务端 message/code、endpoint、
  bucket path 或 trace；分类只由本地枚举生成。
- Finding: 只有测试助手对 raw argument array 的非 Throwable 内容检查不完整。

未发现新的认证、授权、密钥、对象访问控制或公开链接问题；本轮代码不改变这些面。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: 8.5.12 SDK 返回合同、`currentRules`、分类器、外层 catch、setter
  位置、13 个控制流测试。
- Positive evidence:
  - `cfg == null` 唯一映射为 ABSENT；
  - 非空配置的 null/empty rules 映射为无效响应并失败关闭；
  - 实际抛出的 SDK、I/O、XML 与未知异常都不会触发 setter；
  - 正常快照保留非托管规则，替换稳定托管 ID。
- Evidence limitation: 未验证真实 MinIO 动态写入；当前 finding 已可由锁定 JAR
  合同、生产注入路径与独立单测确定，不要求用户额外执行安全敏感操作。

## 8. Performance Concerns

- Coverage: Medium
- Inspected evidence: 规则列表线性遍历、每日调度路径和既有幂等分支。
- Result: 本轮没有新增 DB、网络调用次数、循环层级或对象复制数量级回归。
- Existing boundary: 生命周期规则很多时仍是 O(R) 合并；实际桶通常规则数很小。
- Exclusion: 未执行压力、容量、延迟或长期残片增长实验。

## 9. Testing Authenticity Analysis

### Confidence Assessment

| Test claim | Confidence | Independent assessment |
|------------|------------|------------------------|
| null 表示 8.5.12 no-config | High | 本地锁定 JAR 字节码与生产标准 Bean 一致 |
| null 可首次创建一次 | High | 真实 sentinel 正例 + 参数捕获 + 默认一次 verify |
| 异常/畸形响应从不写 | High | 8 类负例 + 静态控制流 |
| 外部规则保留/托管替换/幂等 | High | 参数对象与列表内容断言 |
| 六类当前日志不泄露原始值 | High | 当前调用只传枚举；formatted message/throwable 反例 |
| 参数数组对未来改动完全防泄露 | Low | 未限制非 Throwable 参数，确认 1 Low |

### Valuable Tests

- `noLifecycleConfigurationCreatesManagedRule` 使用真实高层 null sentinel。
- 403、500、其它 404、空 ErrorResponse、I/O、XML、InvalidResponse 和异常形式
  no-config 均断言 `never(setBucketLifecycle)`。
- null/empty rules 把“不可信成功响应”与“明确 ABSENT”区分开。
- 更新测试证明无关规则对象保留，幂等测试证明相同 days 不写。

### Suspicious Tests

没有发现为了变绿而删除断言、扩大容错或捕获异常吞测试的问题。唯一可疑处是
`assertFailureLogged` 对参数数组的声明强度大于实际断言，已记为 Low。

### Missing Tests

- 参数数组必须恰为单一允许枚举，并逐项禁止敏感 String。
- unknown S3 code + HTTP 503 的 status fallback。
- `ServiceUnavailable`、`SlowDown`、`ServerException`、
  `InsufficientDataException` 的直接分类分支。

这些缺口不会改变当前生产控制流判定；首项建议稳定发布前补，后两项可与其合并。

## 10. Release Concerns

- Phase gate: **PASS**。
- Project gate: **CHANGES_REQUESTED**，仍由 Phase 0、44、53 阻断。
- Allowed next action: 进入 Phase 53 的 demo 元数据、旧对象 reconcile 与浏览器播放
  整改/复核。
- Not authorized by this report: merge、push、部署、切流、生产生命周期写入或项目发布。
- Rollback boundary: 本轮无 DDL/config 变化；若未来升级 MinIO SDK，必须重核
  no-config sentinel，不得假设 8.5.12 合同永久不变。

## 11. Configuration Safety Analysis

### Configuration Summary

| Item | Change | Assessment |
|------|--------|------------|
| MinIO SDK | none; 8.5.12 | 合同已按精确版本重核 |
| Cleanup enable/cron | none | 既有 prod 门禁保持 |
| Bucket/endpoint/credentials | none | 未读取仓库外值，未输出秘密 |
| Flyway/schema | none | 无迁移风险 |
| Frontend/API | none | 无合同变化 |

没有新增默认值、环境变量、密钥或生产自动启用范围。

## 12. Data Integrity Analysis

### Integrity Summary

- 读取明确失败或响应畸形时不写，避免用空规则覆盖桶上其它 lifecycle。
- 明确 ABSENT 时创建单一托管规则，不误判为读取失败。
- 成功读取时保留当前快照中的非托管规则，只替换稳定托管 ID。
- 整桶 API 无 CAS，读取后外部并发修改仍可能被覆盖；这是既有边界，必须继续运维
  串行，并在最终全量审计复核。
- 本轮没有读取、写入或删除任何真实对象、生命周期配置、数据库行或数据卷。

## 13. Concurrency Analysis

- Coverage: Medium
- Current behavior: 单次调用内部顺序为 get→merge→set；异常在 set 前终止。
- Existing race: 多实例调度或人工同时修改同一桶 lifecycle 时没有版本/CAS。
- Current mitigation: 治理文档要求应用任务与人工/外部生命周期修改串行。
- Assessment: 第二轮没有扩大此窗口，也未声称解决它；因此不作为本增量 finding。
- Exclusion: 按用户安全边界未执行并发写入、故障交错或真实 MinIO 操作。

## 14. Principles Compliance

### Principles Violated

- **Trust evidence, not green badges：** 候选材料对 raw 参数数组的证明略有过度；
  本报告将其降回实际证据并记录 Low。
- **Make invalid states unrepresentable：** ABSENT/PRESENT/FAILED 仍用 null/list/exception
  隐式表达，未来 SDK 升级时有再次错层的可能。

### Principles Respected

- **Fail closed：** 未识别异常与畸形非空配置不会触发整桶写。
- **Preserve unrelated state：** 成功快照中的非托管规则不被删除。
- **Least information in logs：** 当前 WARN 只输出固定本地分类。
- **Honest gates：** 候选、独立 PASS、项目发布 GO 分开记录；未把单测冒充真实 MinIO。
- **Scoped change：** 无 DDL、配置、API、权限、依赖或前端扩散。

## 15. Recommended Fix Order

### Fix Immediately

无 Critical / High / Medium，Phase 47 不需要再次退回。

### Fix Before Stable Release

1. 收紧 `assertFailureLogged`：参数数组恰为一个固定分类，并逐项检查敏感哨兵。
2. 同次补 unknown code + 503 fallback，避免 HTTP 5xx 分支长期无直接覆盖。

### Schedule Later

1. 用显式 `ABSENT / PRESENT / FAILED` 适配器隔离第三方 SDK 合同。
2. 为生命周期规则建立结构化指标与连续失败告警。
3. 评估多实例/人工修改时的单写者或版本化协调方案。

### Ignore for Now

- 不为本次 PASS 强制真实 MinIO、Docker、网络故障或权限错误动态门禁。
- 不把 Phase 44/53/0 或最终全量审计问题混入 Phase 47 增量 finding。

## 16. Quick Wins

| Action | Effort | Benefit |
|--------|--------|---------|
| 参数数组断言 `singleElement()` 且等于 category | 15 min | 关闭核心 Low |
| 对每个 raw argument 执行敏感哨兵检查 | 15 min | 防额外 String 绕过 |
| 增加 unknown code + 503 分类用例 | 15–30 min | 覆盖 status fallback |
| 更正候选材料“参数数组已完全证明”的表述 | 5 min | 证据与声明一致 |

## 17. Long-term Refactor Plan

1. **Lifecycle read adapter：** 将 SDK 结果转换为显式三态，服务层只处理项目内合同。
2. **Structured operation result：** 区分 CREATED、UNCHANGED、UPDATED、RETRYABLE、
   PERMANENT_CONFIG_ERROR，并由调度层生成指标/告警。
3. **Single-writer coordination：** 为多实例定时任务和人工 lifecycle 变更建立唯一写者
   或外部版本检查，减少无 CAS 的整桶覆盖窗口。
4. **Contract tests on dependency upgrade：** 每次 MinIO/SLF4J/Logback 升级都重跑
   sentinel 与日志 raw-event 合同测试。
5. **Final full audit：** 统一复查幂等语义、重复托管 ID、非默认 filter/action、
   多实例调度和运维串行可执行性，不在本阶段提前宣称关闭。

---

**Final verdict: PASS（0 Critical / 0 High / 0 Medium / 1 Low，Low 非阻断）。**

第一轮 1 Medium / 1 Low 已关闭；新增 Low 进入稳定发布前测试债。Phase 47 阶段门禁
关闭并放行 Phase 53，但全项目继续由 Phase 0、44、53 保持 CHANGES_REQUESTED，
全部退回项关闭后仍需执行用户要求的最终全量审计。
