# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 53 evidence remediation
**Audit mode:** incremental + security + stability + testing-authenticity + release + configuration + data-integrity + concurrency
**Date:** 2026-07-26
**Reviewer:** OpenAI Codex

---

## 1. Executive Summary

本轮冻结上一份独立报告归档提交
`8d42dcc146e272d9a170b8b64837395c5758853b`，只复核证据整改提交
`34e4d51789892f6737c1e9cfaaf7b998f390fa9f`。该提交是基线的直接子提交，
共改动 22 个证据/治理文件（`+152/-13`），没有生产源码、测试源码、依赖、Flyway、
API、前端或运行配置变化。

上一轮唯一阻断 Medium 已关闭：12 个启动日志全部以 Git blob 归档；提交字节的
大小与 SHA-256 同时匹配 manifest 的原始栏、归档栏和上一轮独立 raw inventory；
目标 Git 属性实际为 `text: unset`、`diff: unset`。动态报告的 6 个明确日志路径和
整个证据目录的 46 个提交文件均可从 `34e4d51` 直接取得。目标临时口令和两个预签名
字段的完整旧值也已从当前 Phase 53 证据树移除。

本轮没有 Critical、High 或 Medium，正式结论为
**PASS（0 Critical / 0 High / 0 Medium / 3 Low，均非阻断）**。两个新 Low 是：
凭据派生的主体片段仍被写进扫描规则说明且“零命中”没有归档可复现命令；11 份后端
日志保留本机用户名和绝对工作区路径。原有 packaged MP4 真实探测自动化 Low 继续留在
稳定发布前。Phase 53 PASS 只放行 Phase 44 进入其既有退回项整改，不等于 merge、
部署、切流、稳定发布或项目 GO；全项目仍由 Phase 0、44 保持
`CHANGES_REQUESTED`，最后仍需全量审计。

### Score Dashboard

```text
Security        █████████░  9.2  S   完整旧凭据已移除；扫描说明仍含凭据派生片段，日志含本机路径
Stability       ██████████  10.0 S   无运行逻辑变化，独立离线回归 195/195
Performance     ██████████  10.0 S   纯证据增量，不改变启动或请求路径资源成本
Testing         █████████░  8.8  A   195/195 真实执行，但 packaged MP4 探测仍由动态证据承担
Maintainability ██████████  9.7  S   manifest、Git 属性和 tracked-path 证据链清晰
Design          ██████████  9.8  S   候选、用户证据、独立复核和发布 GO 继续分层
Release         █████████░  9.3  S   Phase 53 证据已可移植；3 个 Low 留稳定发布前
─────────────────────────────────────
Overall         ██████████  9.5  S
```

每个维度按 0.0–10.0 计分，分数越高越好。本分数只评价 Phase 53 当前增量，
不代表项目整体已具备发布条件。

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 3 | 3 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **3** | **3** | **0** |

## 2. Project Map

本轮仅涉及以下边界：

- Git 证据序列化：`.gitattributes` 对 12 个 `.log.txt` 设置 `-text -diff`；
- 动态日志归档：12 个日志、双栏 SHA-256 manifest 和 tracked-path 检查；
- 证据脱敏：`18b`、`21`、`22` 三个文本记录；
- 证据索引：Phase 53 用户动态报告；
- 候选治理状态：`PROGRESS.md`、`HANDOFF.md`、`DEVLOG.md`。

生产实现仍冻结在 `b9abc6c`；本轮没有重新审计 Phase 44、Phase 0 或全仓发布面。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `8d42dcc..34e4d51` 全部 22 文件、父子关系、blob、stat、diff check | 不重开既有生产实现 |
| Security | High | 三个脱敏文件、12 个日志的定向敏感模式、扫描记录、路径元数据 | 未执行漏洞/凭据扫描器或任何网络行为 |
| Stability | High | 生产/测试 blob 零变化、独立 `clean test` 195/195 | 未启动应用或外部依赖 |
| Performance | High | 确认增量仅为证据与 Git 属性 | 未做压测，因无运行逻辑变化 |
| Testing Authenticity | High | 当前 Surefire XML、真实命令、manifest 与上一轮独立清单四方核对 | 未重跑用户动态环境 |
| Release | High | fresh-checkout 可取得性、tracked-path、哈希、治理状态 | 未 merge/push、部署、切流或回滚 |
| Configuration | High | `.gitattributes` 实际解析、`.gitignore` 交互 | 未改变应用配置 |
| Data Integrity | High | 归档字节、对象 manifest 与上一轮 raw inventory | 未写数据库或对象存储 |
| Concurrency | Medium | 确认无并发代码变化并复核既有边界 | 未运行并发、latch 或外部写交错 |
| Maintainability / Design | High | 证据索引、状态分层、历史报告不可变性 | 全仓证据工具化留最终审计 |

## 3. Incremental Change Summary

### Change Summary

- Total files changed: 22
- Lines added: 152
- Lines deleted: 13
- Commits in range: 1
- Commit: `34e4d51`
- Parent: `8d42dcc`
- Author: `[REDACTED_USER] <[REDACTED_USER]@localhost>`
- Production/test/build files changed: 0

### Change Categories

| Category | Files | Summary |
|----------|-------|---------|
| Evidence archives | 17 | 12 日志、manifest、tracked-path 记录、3 个脱敏记录 |
| Documentation / governance | 4 | 动态报告、PROGRESS、HANDOFF、DEVLOG |
| Git evidence serialization | 1 | `.gitattributes` 精确路径属性 |
| Source / tests / dependencies / Flyway / frontend | 0 | 无变化 |

### Risk Delta

- New Critical/High/Medium risks introduced: 0
- Existing blocking risks fixed: 1 Medium（日志不可移植）
- Existing Low fixed by original issue definition: 1（完整临时口令和完整预签名字段值已移除）
- New evidence-hygiene Low: 2
- Existing unchanged Low: 1（真实 packaged MP4 探测自动化）

### Test Coverage Delta

- New production code with tests: 0
- New production code without tests: 0
- Deleted tests: 0
- Independent regression: `mvn -o -B -ntp -pl platform-boot -am clean test`
  **195/195 PASS**（platform-file 18/18、platform-boot 177/177）

### Approval Recommendation

**Approve with comments / Phase 53 PASS.**

12 个提交日志已形成可从 fresh checkout 取得的同源证据链，阻断 Medium 已关闭。
3 个 Low 不满足阶段退回阈值，进入稳定发布前清单。Phase 44 只被放行到整改与再次
独立复核，不得据此宣称项目可发布。

## 4. Top Risks

1. **Low — 扫描说明仍含凭据派生主体片段且零命中记录不可复现。**
2. **Low — packaged MP4 没有真实媒体探测自动回归。**
3. **Low — 11 份后端日志携带本机用户名和绝对工作区路径。**

## 5. Detailed Findings

### Finding: 扫描说明仍含凭据派生主体片段且零命中记录不可复现

- Severity: Low
- Confidence: High
- Category: Security / Testing Authenticity / Evidence Hygiene
- Status: Confirmed
- Affected area: Phase 53 脱敏证据与 secret-lint 自证
- Evidence:
  - File: `docs/reviews/phase-53-dynamic-evidence-2026-07-26.md:76-78`
  - Function / Module: 证据整改附录
  - Relevant behavior: 敏感模式列表保留了父提交临时口令的主体片段，同时声明扫描零命中。
  - File: `docs/reviews/evidence/phase-53-dynamic-2026-07-26/logs/logs-sha256-manifest.txt:6-10`
  - Function / Module: 日志归档 manifest
  - Relevant behavior: 同一主体片段再次进入受跟踪文本。
  - File: `docs/reviews/evidence/phase-53-dynamic-2026-07-26/24-tracked-path-check.txt:60-61`
  - Function / Module: secret lint 记录
  - Relevant behavior: 只记录“零命中”，没有命令、扫描文件集合、排除规则或 allowlist。
- Problem: 完整旧值已经从 `18b/21/22` 移除，但其主体结构仍作为扫描规则文字进入
  Git；同时，按字面“扫描暂存内容”会命中规则定义自身，当前记录不足以独立重现
  “零命中”的真实范围。
- Why it matters: 当前环境已销毁，因此没有确认的有效凭据泄露；但证据模板若继续把
  真实凭据片段写进规则说明，会使下一次长期凭据脱敏再次失效，并让审计者无法判断
  零命中究竟来自清洁内容还是未披露的排除项。
- Attack precondition: 能读取仓库历史或证据提交。
- Attack path: 从规则说明恢复临时口令的主体结构；本轮账号/schema 已销毁，故没有
  可用登录路径。
- Minimal fix: 用 `temporary_test_password_literal`、不可逆摘要或
  `[REDACTED_SECRET_PATTERN]` 代替具体片段；归档实际 lint 命令、文件集合、排除规则
  与退出码。
- Mitigation: 若该测试值曾在任何未销毁环境复用则轮换；当前证据说明未复用，可不改写
  历史提交。
- Better long-term fix: 证据 secret lint 使用仓库外临时 denylist，报告只记录规则类别
  和工具版本，不记录真实值。
- Realistic failure scenario: 后续证据复用真实凭据片段作为扫描规则，并在未归档排除
  参数的情况下再次宣称零命中，导致审计者误判敏感信息已完整清除。
- Regression test suggestion: 在临时 checkout 中运行归档命令，并断言当前树不包含
  denylist 的完整值或敏感主体片段；命令与作用域必须随结果一并归档。
- Estimated effort: 10–20 minutes

### Finding: packaged MP4 没有真实媒体探测自动回归

- Severity: Low
- Confidence: High
- Category: Testing
- Status: Confirmed
- Subtype: OverMocked / FalseConfidence
- Affected area: Phase 53 demo 资源回归保护
- Evidence:
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/config/DemoDataInitializerTest.java:116-119`
  - Production file: `platform-boot/src/main/resources/db/demo/sample-video.mp4`
  - Test function: `VideoMediaProbe.inspect` 测试替身
  - What it actually tests vs what it should test: 当前测试验证初始化器如何消费预制
    `VideoMediaInspection`；没有让真实探测器解析打包 MP4。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/business/video/support/VideoMediaAcceptancePolicyTest.java:165-169`
  - Relevant behavior: 策略测试明确不执行媒体探测。
- Problem: 若以后同时替换 MP4、摘要和预制探测事实，31 个聚焦测试可能继续全绿，
  即使新资源已损坏或不可解码。
- Why it matters: 当前资源已有上一轮真实 JCodec、ffprobe、首帧、浏览器拖动和水印
  证据，因此不是当前生产缺陷；它是稳定发布后的回归保护缺口。
- Realistic failure scenario: 后续压缩或替换 demo 视频时产生空轨、损坏首帧或错误编码，
  维护者同步更新 manifest 后 CI 未发现。
- Recommended action: Keep but augment
- Minimal fix: 增加自然退出、带超时和资源上限的测试，让真实探测 worker 解析
  classpath/fat-JAR 中的 `sample-video.mp4`。
- Better long-term fix: 为所有 demo 二进制资源建立类型、摘要和真实解析统一门禁。
- Suggested replacement test: 断言 MP4 为单视频轨 H.264、900 帧、900 秒、首帧可解码，
  并对损坏/空轨 fixture 失败关闭。
- Regression test suggestion: 将该测试纳入默认离线 Maven test/verify，不依赖网络、
  Docker、数据库或 MinIO。
- Estimated effort: 1–2 hours

### Finding: 完整后端启动日志保留本机用户名和绝对工作区路径

- Severity: Low
- Confidence: High
- Category: Security / Release / Evidence Hygiene
- Status: Confirmed
- Affected area: Phase 53 归档日志的环境信息最小化
- Evidence:
  - File: `docs/reviews/evidence/phase-53-dynamic-2026-07-26/logs/envA-run0-migrate-demo-off.log.txt:12`
  - Function / Module: Spring Boot 启动日志
  - Relevant behavior: 11 份后端日志均在第 12 行记录同一本机用户名和绝对工作区路径；
    `frontend-dev.log.txt` 不含该字段。
- Problem: 为保留逐字节同源性，整改把完整本机路径连同业务证据一起归档；这些值不是
  凭据，但不是证明 Phase 53 行为所必需。
- Why it matters: 仓库被复制到审计包、制品或外部协作环境时，会同步暴露本机账号名和
  目录布局，扩大不必要的环境信息留存。
- Realistic failure scenario: 后续证据包进入共享审计系统，外部人员可把本机身份和目录
  结构与其它日志关联。
- Attack precondition: 能读取证据仓库。
- Attack path: 直接查看启动行；没有由此获得认证或远程访问能力。
- Minimal fix: 生成脱敏归档副本，将用户名/绝对根路径替换为
  `[REDACTED_USER]/[REDACTED_PATH]`，manifest 同时保存原始 SHA-256 与脱敏归档
  SHA-256。
- Better long-term fix: 证据收集器默认移除用户目录、临时目录和机器名，只保留相对
  项目路径及运行时版本。
- Regression test suggestion: 对归档文本断言不存在 `[DRIVE]:\Users\<name>\`、
  `/Users/<name>/` 或 `/home/<name>/` 形式的绝对用户路径。
- Estimated effort: 15–30 minutes

## 6. Security Concerns

- Coverage: High
- Inspected evidence: 三个目标脱敏文件、12 个日志、manifest、tracked-path 记录和
  动态报告；只做本地定向字符串核验。
- Exclusions / limits: 未运行漏洞扫描器、凭据尝试、网络请求或任何 cyber 动作。

完整临时口令、完整 `X-Amz-Credential` 和完整 `X-Amz-Signature` 旧值已从当前
Phase 53 证据树移除。剩余两个 Security Low 是凭据派生片段和本机路径最小化，不存在
已确认的有效访问能力。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: `b9abc6c..34e4d51` 生产/测试 blob 比较和当前离线
  `clean test`。
- Exclusions / limits: 未重启服务、数据库、MinIO 或浏览器。

本轮无运行逻辑变化；独立离线回归为 195/195，未发现稳定性 finding。

## 8. Performance Concerns

- Coverage: High
- Inspected evidence: 22 文件变更分类和运行路径零变化检查。
- Exclusions / limits: 未运行压力或容量测试。

归档文件只影响 Git 仓库大小，不进入应用运行时路径；未发现 Phase 53 新性能风险。

## 9. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 当前 Surefire 结果、聚焦测试设计、动态证据哈希链和 lint 记录。
- Exclusions / limits: 用户动态栈未由 Codex 重建。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 12 个归档日志同源性 | High | 无；四方哈希一致 | Keep |
| 生产/测试离线回归 | High | 195/195，当前源码未变 | Keep |
| secret-lint 零命中记录 | Low | 命令/作用域/排除项缺失且规则自引用 | Rewrite evidence |
| packaged MP4 真实可解码性 | Medium | 当前有动态证据，缺自动回归 | Keep but augment |

### Valuable Tests

- `DemoDataInitializerTest` 21/21；
- `VideoMediaAcceptancePolicyTest` 10/10；
- 本轮独立 platform-file 18/18、platform-boot 177/177；
- 提交 blob、manifest 与上一轮独立 inventory 的 12/12 哈希交叉验证。

### Suspicious Tests

- secret-lint 只有结论文本，没有可重放命令和作用域；
- 聚焦媒体测试仍使用预制探测结果。

### Missing Tests

- 真实 packaged MP4 媒体探测默认门禁；
- 可在 fresh checkout 直接执行的证据脱敏脚本。

## 10. Release Concerns

- Coverage: High
- Inspected evidence: Git 提交链、全部归档路径、哈希、阶段状态和下一阶段报告。
- Exclusions / limits: 未 merge/push、部署、切流或执行项目级回滚。

Phase 53 证据已可移植，阶段可 PASS。Phase 44 仍有其正式报告列出的两个 Major，
Phase 0 也仍退回，因此项目保持 `CHANGES_REQUESTED`。

## 11. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `.gitattributes` 路径规则及实际 `git check-attr` 结果。
- Exclusions / limits: 无应用配置变更。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| SchemaValidation | 0 | — | — |
| UnsafeDefault | 0 | — | — |
| EnvironmentSeparation | 0 | — | — |
| SecretConfig | 0 | — | — |
| FeatureFlag | 0 | — | — |
| ConfigDocs | 0 | `.gitattributes` 已与实际行为一致 | Keep |

目标文件的 `text` 与 `diff` 属性均为 unset，提交 blob 不受 EOL 归一化影响。

## 12. Data Integrity Analysis

- Coverage: High
- Inspected evidence: 12 个文件大小/SHA-256、Git blob、manifest 双栏和上一轮 raw
  inventory。
- Exclusions / limits: 未写入真实数据库或对象存储。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 0 | — | — |
| Idempotency | 0 | — | — |
| ConcurrencyConsistency | 0 | — | — |
| MigrationSafety | 0 | — | — |
| InvariantValidation | 0 | — | — |
| BackupRestore | 0 | — | — |
| Reconciliation | 0 | — | — |

证据不变量“提交 blob = manifest 归档栏 = manifest 原始栏 = 上一轮独立 inventory”
对 12/12 文件成立。

## 13. Concurrency Analysis

- Coverage: Medium
- Inspected evidence: 当前增量无共享状态/锁/事务代码，复核既有对象发布边界未被改写。
- Exclusions / limits: 按用户安全边界未执行并发、latch 或外部对象写交错。

本轮没有并发代码变化。既有 `stat(MISSING) → put` 无对象 CAS 的边界继续要求专用
bucket 和外部写串行，并留最终全量审计；不计为本证据增量新 finding。

## 14. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Least-Privilege / Data Minimization | 1 | Low | 日志内本机身份与绝对路径 |
| Honest, Reproducible Gates | 1 | Low | secret-lint 命令与范围未归档 |
| Automate Invariant Checks | 1 | Low | packaged MP4 真实探测 |

### Principles Respected

- **Fail-fast evidence:** 12 个日志缺失问题没有用本机残留冒充提交证据；
- **Deterministic integrity:** manifest、Git blob 和上一轮独立清单四方同源；
- **Separation of duties:** 候选自测、用户动态证据、独立 Phase PASS 和项目 GO 分开；
- **Minimal change:** 纯证据整改没有改动已经闭环的生产逻辑。

## 15. Recommended Fix Order

### Fix Immediately

无 Phase 53 阻断项。

### Fix Before Stable Release

1. 把凭据派生主体片段替换为类别名/摘要，并归档真实 secret-lint 命令和作用域。
2. 脱敏 11 份日志的本机用户名和绝对路径，保留原始/脱敏双哈希。
3. 增加真实 packaged MP4 探测自动门禁。

### Schedule Later

把证据收集、路径检查、哈希、脱敏和报告引用验证收敛为统一生成工具。

### Ignore for Now

不要求为上述 Low 重建已销毁的 Docker/MySQL/MinIO/浏览器环境。

## 16. Quick Wins

| Action | Effort | Benefit |
|--------|--------|---------|
| 用类别名替换凭据派生片段 | 5 min | 去除剩余敏感结构 |
| 归档 secret-lint 命令/范围/退出码 | 10 min | 让零命中结论可复现 |
| 定点脱敏本机用户根路径 | 15–30 min | 降低证据环境信息暴露 |

## 17. Long-term Refactor Plan

本轮不需要结构性重构。稳定发布前建立单一证据工具，输入原始日志，输出脱敏副本、
原始/脱敏 SHA-256、tracked-path 清单和可重放 secret-lint 记录；任何工具化变更必须
保持候选证据与独立复核记录不可混淆。

---

**Final verdict: PASS（0 Critical / 0 High / 0 Medium / 3 Low，均非阻断）。**

Phase 53 关闭并放行 Phase 44 进入既有退回项整改；全项目仍由 Phase 0、44 保持
`CHANGES_REQUESTED`，不得宣称稳定发布或项目 GO，最终全量审计仍是必需门禁。
