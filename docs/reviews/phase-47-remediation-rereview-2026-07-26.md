# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 47 remediation
**Audit mode:** incremental + stability / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-26
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结生产/测试代码
`b2f1f701da733d0afa849ed4094042d61adbf338..aa6f81c3518259c30cd7190a8c521cef61108843`，
并核对候选提交材料至
`e81a485c97506eee966e6a2245e830a6cdc0b665`。代码增量为 1 个提交、2
个文件、`+257/-9`；连同治理材料共 2 个提交、8 个文件、`+347/-24`。
代码提交之后只有文档变化，生产/测试源码没有漂移。

正式结论：**CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 1 Low）**。

- 原 Major 的危险侧已关闭：`AccessDenied`、500、其它 404、网络、解析和畸形响应
  不再被当作空配置，控制流会在整桶 `setBucketLifecycle` 前失败关闭。
- 原 Major 的合法正向侧未关闭：仓库锁定的 MinIO Java SDK 8.5.12 会在
  `NoSuchLifecycleConfiguration` 时于 SDK 内部返回 `null`，不会向业务层抛出该异常。
  候选却把 `null` 判为异常，因此没有生命周期配置的新桶会在每次调度时返回
  `false`，托管的 `AbortIncompleteMultipartUpload` 规则永远不能首次创建。
- 新增 12 个 Mockito 测试全部通过，但正向测试模拟了真实 SDK 不会暴露的高层异常；
  另一条 `null` 测试反而把 SDK 的真实“无配置”合同断言成失败，构成明确虚绿。
- 错误日志从原始 message 收敛为异常简单类名，避免直接输出服务端内容；但
  `AccessDenied`、`InternalError` 与 `NoSuchBucket` 最终都只显示
  `ErrorResponseException`，形成 1 个非阻断 Low 可观测性问题。
- 独立离线门禁为 `platform-file` **15/15 PASS**（整改测试 12/12）和后端
  **9/9 modules package PASS**。这些结果证明候选可编译、测试可执行，不改变
  SDK 合同错配的结论。

Phase 47 继续复核退回，Phase 53 不放行；全项目仍由 Phase 0、44、47、53
保持 **CHANGES_REQUESTED**。本轮没有启动服务、Docker、数据库、真实 MinIO，
也没有执行网络访问、权限变更、故障注入、扫描、fuzz、压力或任何可能属于
cyber 的动作。

### Score Dashboard

```text
Security        █████████░  8.8  A   未新增权限/密钥面，静态证据受无动态安全操作边界限制
Stability       ███████░░░  6.8  B   非目标错误已失败关闭，但合法首次配置路径确定失效
Performance     ████████░░  7.8  A   无热路径回归；规则永不安装会造成长期残片积压
Testing         ██████░░░░  5.8  B   15/15 可执行，但核心正例违反锁定 SDK 的真实合同
Maintainability ████████░░  8.0  A   改动局部清晰；第三方合同未隔离为显式适配层
Design          ███████░░░  7.0  A   未知失败 fail-closed 正确，已知“无配置”状态建模错误
Release         ██████░░░░  6.2  B   构建通过，但生产默认启用的清理能力无法首次落地
─────────────────────────────────────
Overall         ███████░░░  7.2  A
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit
mountain).** Overall is the arithmetic mean of the seven displayed dimensions,
rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 1 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **2** | **2** | **0** |

## 2. Project Map

- `platform-file`：`FileMaintenanceService` 读取、合并并整体写回桶生命周期规则；
  `MinioConfig` 直接构建 SDK `MinioClient`，没有改变生命周期返回语义的适配层。
- `platform-file` 测试：新增 12 个纯 Mockito 用例，覆盖错误分流、规则保留、
  托管规则替换和幂等跳写。
- `platform-boot`：`CleanupScheduleConfig` 在生产门禁开启后每日读取
  `cleanup.minio.abortIncompleteDays` 并调用生命周期确保服务。
- 根 POM：锁定 `io.minio:minio:8.5.12`；离线依赖树和独立 Surefire XML
  均确认实际测试类路径使用该版本。
- 治理文档：候选材料、统一计划、进度与交接均保持“候选不等于 PASS”，没有提前
  merge、push、部署或放行 Phase 53。

本轮无 DDL/Flyway、数据库数据、对外 API、权限点、状态机、前端生产代码、运行
配置或依赖版本变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `b2f1f70..aa6f81c` 全 diff、两个提交拓扑、源码 blob、材料至 `e81a485` | 未重开其它 Phase |
| Security | Medium | 日志数据面、配置/凭据未变化、异常信息收敛 | 未执行扫描、攻击性请求、凭据尝试或动态安全操作 |
| Stability | High | SDK 字节码、同步/异步调用链、异常矩阵、空/畸形响应和调度调用 | 未运行真实 MinIO 或故障注入 |
| Performance | Medium | 调度频率、生命周期缺失后的残片增长方向、算法复杂度 | 未执行容量、压力或对象规模基准 |
| Testing Authenticity | High | 12 个测试全文件、Mockito 层级、SDK 8.5.12 字节码、独立 clean test/XML | 未运行真实对象存储 |
| Release | High | 分支/提交、工作区、离线 clean test、9 模块 package、治理门禁 | 未 merge/push、部署、切流或回滚演练 |
| Configuration | High | prod 开关、cron、`sys_param` 参数、Compose profile 和依赖版本 | 未读取仓库外生产配置 |
| Data Integrity | Medium | 整桶合并、外部规则保留、错误时不写 | 未读取或修改真实桶生命周期/业务对象 |
| Concurrency | Medium | get→merge→set 边界、普通 scheduler 与已披露运维串行要求 | 未执行并发、锁、压力或故障交错 |
| Maintainability / Design | High | 变更类、直接调用方、SDK 注入点和候选文档 | 既有全仓 MinIO 抽象留最终审计 |

## 3. Incremental Change Summary

### Change Summary

- Production/test code files changed: 2
- Code lines added: 257
- Code lines deleted: 9
- Code commits in range: 1
- Documentation files added/changed after code freeze: 6
- Total candidate commits through submission: 2
- Production/test code commit: `aa6f81c`
- Submission commit / current HEAD at review start: `e81a485`
- Author: `wenbibuhaoqwq <wenbibuhaoqwq@localhost>`

### Change Categories

| Category | Files | Summary |
|----------|-------|---------|
| Bug fix | 1 | 收窄生命周期读取错误分流并清理日志输出 |
| Tests | 1 | 新增 12 个 Mockito 生命周期控制流用例 |
| Documentation / governance | 6 | 候选材料、进度、计划、交接与开发日志 |
| Dependencies / configuration / migration | 0 | 无变化 |

### Previous Finding Closure Matrix

| Previous requirement | Candidate evidence | Independent result |
|----------------------|-------------------|--------------------|
| 读取 403/500/其它 404/网络/解析错误时拒绝整桶写入 | 异常传播至外层，负例均断言 `never(setBucketLifecycle)` | **Closed** |
| 只有明确“无生命周期配置”时允许按空规则创建 | 业务层捕获精确错误码；测试 mock 高层客户端抛异常 | **Open**：8.5.12 在 SDK 内转换为 `null`，候选拒绝该真实返回值 |
| 成功读取时保留外部规则并只替换托管 ID | 参数捕获断言非托管对象保留、托管 ID 唯一 | **Closed for a successful read snapshot** |
| 错误日志不输出原始服务端 message/endpoint | 只记录异常简单类名 | **Security side closed；新增 1 Low operability finding** |

原 1 Major 因合法“无配置”分支未闭环，不能整体标记 Closed。

### Risk Delta

- New risks introduced: 2（1 Medium 功能/测试合同回归；1 Low 可观测性回归）
- Existing dangerous behaviors fixed: 1（非目标读取失败后不再整桶写入）
- Previous finding fully closed: 0
- Existing baseline risks made worse: 0

### Test Coverage Delta

- New tests: 12
- New production methods: 0；修改方法 2
- Deleted tests: 0
- Valuable negative cases: 403、500、其它 404、空错误响应、I/O、XML、null/empty
  结构、外部规则保留、托管替换、幂等跳写
- False-positive contract case: 1（mock 高层 `MinioClient` 抛出 SDK 会内部消费的
  `NoSuchLifecycleConfiguration`）
- Contradictory real-contract case: 1（`thenReturn(null)` 被断言为失败不写）
- Real MinIO tests: 0（候选材料已诚实披露）

### Approval Recommendation

**Reject / CHANGES_REQUESTED。** 修正 SDK 8.5.12 的 `null == 明确无生命周期配置`
合同并重写对应正例后，再提交最小增量复核。日志分类 Low 可与该轮一并关闭，但其本身
不构成单独发布阻断。

## 4. Top Risks

1. **Medium — lifecycle bootstrap permanently disabled：** 新桶没有生命周期配置时，
   SDK 返回 `null`；候选每次都返回 `false`，规则永不安装。
2. **Low — failures are not actionable：** 权限、桶不存在和服务端错误在日志中都只显示
   `ErrorResponseException`，日常重试无法直接指导处置。
3. **Testing confidence risk：** 12/12 绿证明的是 Mockito 设定的业务层分支，不是锁定
   SDK 实际交付给业务层的状态。
4. **Carry-forward baseline boundary：** 成功读取后的 get→整桶 set 没有 CAS；外部并发
   生命周期变更仍须运维串行。该风险在本候选前已经存在且材料已披露，不计本轮 finding。

## 5. Detailed Findings

### Finding: SDK 将“无生命周期配置”归一为 null，候选因此永远不能创建首条规则

- Severity: Medium
- Confidence: High
- Category: Stability / Testing Authenticity
- Status: Confirmed
- Affected area: MinIO 生命周期初始化、未完成 multipart 残片治理、Phase 47 发布门禁
- Evidence:
  - File: `pom.xml:46,73`
  - Function/module: dependency management
  - Relevant behavior: 仓库锁定并解析 `io.minio:minio:8.5.12`。
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/config/MinioConfig.java:33-39`
  - Function/module: `minioClient`
  - Relevant behavior: 生产直接构建标准 SDK 客户端，没有适配层改变返回合同。
  - Evidence file:
    `docs/reviews/evidence/phase47-remediation-rereview-2026-07-26/minio-8.5.12-lifecycle-contract.txt`
  - Relevant behavior: SHA-256 为
    `9519FF2FD284AC0FC5C22D1091F21A9C9298C9C7CAC85615DE1FF10F1710786E`
    的本地 8.5.12 JAR 字节码显示，`lambda$getBucketLifecycle$50` 对
    `NoSuchLifecycleConfiguration` 直接 `aconst_null; areturn`；同步客户端原样返回
    Future 结果。
  - File:
    `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceService.java:94-111`
  - Function/module: `currentRules`
  - Relevant behavior: 候选仅允许捕获到的精确异常码返回空列表，却在 `cfg == null` 时
    抛 `IllegalStateException`。
  - File:
    `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceService.java:79-90`
  - Function/module: `ensureAbortIncompleteMultipartLifecycle`
  - Relevant behavior: 上述异常在 `setBucketLifecycle` 之前被捕获并返回 `false`。
  - File:
    `platform-file/src/test/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceServiceTest.java:52-72`
  - Function/module: `noLifecycleConfigurationCreatesManagedRule`
  - Relevant behavior: mock 高层客户端直接抛出 SDK 实际会内部消费的异常。
  - File:
    `platform-file/src/test/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceServiceTest.java:135-141`
  - Function/module: `nullConfigurationWhileReadingFailsClosedWithoutWriting`
  - Relevant behavior: 测试把真实 SDK 的“无配置”返回合同明确断言为失败且不写。
- Problem: 候选把第三方 SDK 的“已识别缺省状态”误当作不可信畸形响应，同时用不可达
  的 mock 行为证明正向分支，导致实现与测试共同偏离生产依赖。
- Why it matters: Phase 47 的 MinIO 部分不是“可选诊断”，而是负责确保服务端自动
  abort 超期未完成分片。首次部署或任何尚未配置生命周期的桶都会永久跳过该设置，
  残片持续积压，阶段宣称的 P1-9 能力实际上不可用。
- Realistic failure scenario: 生产桶存在但没有 lifecycle configuration；每日 03:45
  调度调用 SDK；SDK 把服务端 `NoSuchLifecycleConfiguration` 转为 `null`；业务代码
  抛 `IllegalStateException` 并返回 `false`；`setBucketLifecycle` 从不调用；次日重复
  同一路径，没有自愈机会。
- Minimal fix: 在当前锁定的 SDK 合同下将 `cfg == null` 解释为明确“无配置”并返回空
  规则；其它实际抛出的 `ErrorResponseException`、I/O、解析和畸形非空配置继续
  fail-closed。将正例改为 `thenReturn(null)`，断言返回 `true` 且
  `setBucketLifecycle` 精确调用一次；删除或反转现有矛盾的 null 负例。
- Better long-term fix: 在 `MinioClient` 外增加很薄的生命周期读取适配器，以显式
  `ABSENT / PRESENT / FAILED` 结果承载 SDK 版本语义；服务层不再直接猜测第三方异常
  与 sentinel 返回值。
- Regression test suggestion: 纯单元合同测试至少覆盖
  `null→create once`、有效规则→preserve/replace、403/500/其它 404/I/O/XML→never set、
  非空配置中的 null/empty rules→never set。该整改不要求 Codex 执行真实 MinIO。
- Estimated effort: 1–2 hours

### Finding: 日志把所有 S3 失败折叠为同一个异常类名

- Severity: Low
- Confidence: High
- Category: Stability / Observability
- Status: Confirmed
- Affected area: Phase 47 日常调度告警、MinIO 权限/配置/服务故障诊断
- Evidence:
  - File:
    `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceService.java:86-90`
  - Function/module: `ensureAbortIncompleteMultipartLifecycle`
  - Relevant behavior: 告警只记录 `e.getClass().getSimpleName()`。
  - File:
    `platform-file/src/test/java/cn/edu/gpnu/platform/file/service/impl/FileMaintenanceServiceTest.java:74-102`
  - Function/module: AccessDenied / InternalError / NoSuchBucket tests
  - Relevant behavior: 三种需要不同运维动作的服务端错误均为
    `ErrorResponseException`；独立测试输出中的告警完全同形。
  - File:
    `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/config/CleanupScheduleConfig.java:61-69`
  - Function/module: `scheduledMinioIncompleteAbort`
  - Relevant behavior: 调用层只再记录 `ok=false`，没有补充安全分类。
- Problem: 为避免泄露原始服务端 message 而完全删除结构化错误语义，使永久权限错误、
  桶配置错误与可重试服务端错误无法从日志区分。
- Why it matters: 作业每日自动重试且不会打断服务；如果没有可操作分类，失败可能长期
  存在，只能依赖额外人工复现或提高日志敏感度才能定位。
- Realistic failure scenario: 桶名错误与 MinIO 500 分别连续发生一周，日志均只有
  `type=ErrorResponseException` 和 `ok=false`；运维无法判断应修配置、权限还是等待
  服务恢复。
- Minimal fix: 使用本地白名单映射输出
  `ACCESS_DENIED / NO_SUCH_BUCKET / SERVER_ERROR / TRANSPORT / PARSE /
  INVALID_RESPONSE`，可附数值 HTTP status；继续禁止原始 message、endpoint、path
  和异常对象进入日志。
- Better long-term fix: 为定时作业建立结构化结果与指标，区分成功、缺省创建、幂等
  命中、永久配置失败和临时依赖失败，并对连续失败设置告警阈值。
- Regression test suggestion: 使用日志捕获器验证三种 `ErrorResponseException` 产生
  不同白名单分类，同时断言日志不包含模拟 message、endpoint、bucket path 或 trace。
- Estimated effort: 1 hour

## 6. Security Concerns

- Coverage: Medium
- Inspected evidence: 异常日志内容、候选增量配置/依赖差异、MinIO Bean 与凭据边界。
- Exclusions / limits: 未执行扫描、攻击性请求、恶意载荷、凭据尝试、权限变更、网络
  探测或任何可能触发 cyber 安全限制的操作。

候选没有新增端点、权限点、凭据、动态命令或公开对象访问面。删除原始异常 message
降低了把服务端可控文本或内部 endpoint/path 写入日志的风险；Low finding 要求的是
本地白名单分类，不要求恢复原始 message。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: 所有读取/写入异常分支、SDK 同步/异步字节码、null/empty 结构、
  调度调用与返回值。
- Exclusions / limits: 未运行真实对象存储、服务中断或故障注入。

候选对未知或失败读取采用 fail-closed 是正确方向；403、500、其它 404、网络、解析
和畸形配置均不会触发整桶写入。阻断点只在被 SDK 明确定义为“无配置”的 `null`
sentinel：该合法状态也被关闭，导致能力永久不可初始化。

## 8. Performance Concerns

- Coverage: Medium
- Inspected evidence: 调度频率、规则安装语义、循环/集合复杂度和长期存储方向。
- Exclusions / limits: 未执行容量、压力、multipart 负载或成本测量。

增量没有引入新的 CPU、内存或请求放大。Medium finding 的性能影响是时间累积型：
服务端 abort 规则缺失后，未完成 multipart 残片不再由本阶段能力设定回收期限，桶
空间与列举/维护成本可持续增长；具体速率依赖真实上传失败量，本轮不做数值推断。

## 9. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 12 个新增测试、独立离线 clean test、Surefire XML、锁定 SDK
  字节码、生产 Bean 注入路径与源码/提交范围。
- Exclusions / limits: 未运行真实 MinIO、Docker、网络故障或权限错误。

### Confidence Assessment

| Test area | Test result | Real confidence | Assessment |
|-----------|-------------|-----------------|------------|
| 403/500/其它 404 不写 | PASS | High | 高层 SDK 确实会传播这些非目标错误 |
| I/O/XML 错误不写 | PASS | High | 方法签名与异常传播链一致 |
| null/empty 畸形结构不写 | PASS | Mixed | null 不是畸形；empty/null rules 仍是防御性负例 |
| 无配置时创建 | PASS | None | mock 绕过 SDK 内部的异常→null 归一化 |
| 外部规则保留/托管替换 | PASS | High for snapshot | 参数对象直接捕获，能证明单次合并结果 |
| 幂等跳写 | PASS | Medium | 只验证既有 ID+days 语义，未覆盖基线 status/filter 边界 |

### Valuable Tests

- `accessDeniedWhileReadingFailsClosedWithoutWriting`
- `serverErrorWhileReadingFailsClosedWithoutWriting`
- `differentNotFoundCodeWhileReadingFailsClosedWithoutWriting`
- `networkErrorWhileReadingFailsClosedWithoutWriting`
- `parserErrorWhileReadingFailsClosedWithoutWriting`
- `updatePreservesUnrelatedRuleAndReplacesOnlyManagedRule`
- `matchingManagedRuleSkipsWrite`

### Suspicious Tests

- `noLifecycleConfigurationCreatesManagedRule`：在错误抽象层 mock 了锁定 SDK 不会向
  调用方抛出的异常，是本轮虚绿根因。
- `nullConfigurationWhileReadingFailsClosedWithoutWriting`：名称和期望把 SDK 的真实
  no-config sentinel 误分类为畸形响应。

### Missing Tests

- `getBucketLifecycle(...) == null` 时必须创建并只写一次。
- 可选增强：对日志白名单分类和敏感原始文本不落日志做捕获断言。
- 真实 MinIO 动态测试不是确认本 finding 的必要条件；若团队另要求真实桶写入验证，
  按用户安全边界由用户自行决定和执行。

独立安全门禁：

```text
mvn -o -B -ntp -pl platform-file -am clean test
  BUILD SUCCESS；15/15，FileMaintenanceServiceTest 12/12

mvn -o -B -ntp -DskipTests package
  BUILD SUCCESS；9/9 reactor modules

mvn -o -B -ntp -pl platform-file dependency:tree
  "-Dincludes=io.minio:minio"
  io.minio:minio:8.5.12:compile；BUILD SUCCESS
```

独立 Surefire XML SHA-256：
`94158690EDC53C2BB1F9201A2AA73BD44A5DD2A82801E9294CF2658EC75742F5`。

## 10. Release Concerns

- Coverage: High
- Inspected evidence: 当前分支、提交范围、工作区、源码 blob、离线 clean test、
  9 模块 package、候选/治理文档和发布顺序。
- Exclusions / limits: 未 merge、push、部署、切流、真实基础设施验证或回滚演练。

当前分支为 `codex/phase47-lifecycle-fail-closed`；代码点 `aa6f81c`，材料/HEAD
`e81a485`，`main` 仍为 `e4f8228`。已知未跟踪文件在审计前已经存在并保持原样。
Phase 47 不可合并或放行 Phase 53，因为 prod profile 默认开启清理调度，而全新/无
生命周期配置的正常部署正好命中 Medium。

不得通过回滚到 `b2f1f70` 止损：旧版本会恢复“读取失败后整桶覆盖”的原风险。正确
处置是提交最小前向修复，或在部署前明确禁用 Phase 47 cleanup schedule，直到修复
通过独立增量复核。

## 11. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `application-prod.yml`、`CleanupScheduleConfig`、Compose profile、
  `sys_param` 读取和全增量配置/依赖差异。
- Exclusions / limits: 未读取生产环境变量、仓库外配置或运行时参数值。

### Configuration Summary

| Subtype | Count | Affected keys / files | Recommended action |
|---------|-------|-----------------------|--------------------|
| SchemaValidation | 0 | 无 DDL/Flyway | 无 |
| UnsafeDefault | 0 | 本增量无默认值变化 | 无 |
| EnvironmentSeparation | 0 | prod 显式启用；dev/test 默认不注册 | 保持 |
| SecretConfig | 0 | MinIO 凭据配置未变化 | 保持环境变量注入 |
| FeatureFlag | 0 | 本增量未改 cleanup gate | 修复前禁止发布候选 |
| ConfigDocs | 0 | 候选状态与限制已诚实记录 | 下一候选同步新报告 |

配置链本身成立：`platform.cleanup.schedule.enabled` 默认缺省关闭，prod 配置置 true；
Compose 默认激活 prod。正因发布路径会真实启用该作业，Medium 不是未使用代码。

## 12. Data Integrity Analysis

- Coverage: Medium
- Inspected evidence: 读取失败时不写、成功快照的外部规则保留、托管 ID 替换和整桶
  写入边界。
- Exclusions / limits: 未读取或修改真实桶生命周期、对象、数据库或用户数据。

### Integrity Summary

| Subtype | Count | Invariant at risk | Recommended action |
|---------|-------|-------------------|--------------------|
| TransactionBoundary | 0 | 非数据库事务；单次 SDK get/set | 保持失败前不写 |
| Idempotency | 0 new | 基线只按 ID+days 判断 | 留最终全量审计 |
| ConcurrencyConsistency | 0 new | 成功读取后外部并发修改窗口 | 运维串行，最终审计复查 |
| MigrationSafety | 0 | 无迁移 | 无 |
| InvariantValidation | 1 Medium | “无配置”必须允许首次建立托管规则 | 修正 null 合同 |
| BackupRestore | 0 | 不在本增量范围 | 无 |
| Reconciliation | 0 new | 生命周期确保每日重试 | 修复后恢复自愈 |

非目标读取错误不再覆盖外部规则，是明确的数据完整性改进。成功读取快照后保留非托管
规则也成立；但 get→set API 没有 CAS，外部同时修改仍可能丢更新，该限制为基线既有且
候选已披露，不计本轮 finding。

## 13. Concurrency Analysis

- Coverage: Medium
- Inspected evidence: 生命周期 read-modify-write、调度器、单实例 Compose 和候选运维
  串行声明。
- Exclusions / limits: 未执行并发、压力、锁、故障交错或多实例运行。

本增量没有新增并发原语，也没有加重成功读取后的整桶替换窗口。以下三项继续作为最终
全量审计 carry-forward，不计本轮 finding：

1. get→set 没有 CAS/版本条件，应用任务与人工/外部变更必须串行。
2. 幂等命中只检查托管 ID 与 days，未核 `Status.ENABLED`、空前缀 filter、额外动作
   或重复托管 ID。
3. 当前 Compose 为单 backend；未来多实例部署需要分布式调度互斥或幂等协议重审。

## 14. Principles Compliance

### Principles Violated

- **Test the collaborator's real contract：** 在高层 mock 中构造 SDK 实际会消费的异常，
  使核心正例与生产依赖分叉。
- **Make invalid states explicit：** “不存在配置”与“读取失败/畸形响应”没有形成显式
  类型，而由 null/异常猜测承担。
- **Operational errors must be actionable：** 安全删减原始 message 后未保留白名单错误
  分类。

### Principles Respected

- **Fail closed for unknown failures：** 403、500、其它 404、I/O、解析和畸形非空配置
  均禁止整桶写入。
- **Preserve unrelated state：** 成功快照中的非托管规则原样保留，只替换稳定托管 ID。
- **Evidence honesty：** 候选材料没有把 Mockito 冒充真实 MinIO，也没有提前宣告 PASS。
- **Minimal blast radius：** 本轮没有修改配置、依赖、DDL、API 或其它阶段代码。

## 15. Recommended Fix Order

### Fix Immediately

1. 修正 `cfg == null` 的 SDK 8.5.12 语义，保留其它异常和畸形非空配置的 fail-closed。
2. 把 no-config 正例改为 `thenReturn(null)`，断言返回 true、桶名/规则内容正确且
   `setBucketLifecycle` 精确一次；移除矛盾 null 负例。
3. 离线重跑 `platform-file -am clean test`、9 模块跳测 package 与 `git diff --check`，
   冻结新代码提交后重交独立增量复核。

### Fix Before Stable Release

- 增加安全白名单错误分类并补日志断言，关闭本轮 Low。
- 不得回滚到旧的“任意读取失败按空处理”实现；若前向修复尚未复核，部署时保持清理
  调度关闭。

### Schedule Later

- 用显式生命周期适配器返回 `ABSENT / PRESENT / FAILED`，避免 SDK 升级再次改变
  sentinel/异常合同。
- 最终全量审计复查 status/filter 幂等完整性、无 CAS 的并发窗口、共享普通 scheduler
  延迟与多实例调度互斥。

### Ignore for Now

- 本轮不要求 Codex 执行真实 MinIO 权限错误、网络故障或生命周期写入；静态字节码和
  生产注入路径已足以确认阻断 finding。

## 16. Quick Wins

- 生产修复可控制在 `FileMaintenanceService` 的 null 分支和两条相互矛盾的测试期望，
  不需要改配置、迁移或调用方。
- 日志 Low 可用一个纯函数把 SDK 异常映射为固定枚举；既提升可操作性，也不恢复原始
  message 泄露。
- 下一提交材料应把 “NoSuchLifecycleConfiguration 正例” 写成 “SDK 8.5.12 返回
  null 的正例”，避免再次把第三方内部异常误当业务层合同。

## 17. Long-term Refactor Plan

在 `platform-file` 内引入很薄的 `BucketLifecycleReader`/adapter：

1. 只在适配器中理解 SDK 版本的异常与 sentinel；
2. 对服务层返回 `ABSENT`、`PRESENT(rules)` 或 `FAILED(category)`；
3. 单独以锁定 JAR 合同测试适配器，以行为测试验证服务层；
4. 为未来 SDK 升级建立显式 contract gate；
5. 若将来需要多实例或人工共管生命周期，再评估分布式互斥、外部配置所有权或带版本
   条件的替代接口，避免整桶最后写入者获胜。

该重构不是本轮最小修复的前置条件。

---

**Final verdict:** `CHANGES_REQUESTED`

**Finding count:** `0 Critical / 0 High / 1 Medium / 1 Low / 0 Info`

**Previous finding:** `Partially closed; non-target failures fail closed, real no-config path remains open`

**Phase state:** `Phase 47 继续复核退回`

**Release gate:** `Phase 53、merge、push、部署、切流和项目发布均不放行`
