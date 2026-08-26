# 最终全量审计第六整改候选独立增量复核（2026-07-29）

**Project:** teacher-cert-platform  
**Audit mode:** incremental / testing-authenticity  
**Date:** 2026-07-29  
**Reviewer:** Codex GPT-5

> 本报告是最终全量审计结束后的第六整改候选独立增量复核，不重新执行或改写最终全量审计。
>
> 本轮结论：**CHANGES_REQUESTED / NO-GO**。上轮 README 计数 Low 已关闭，完整候选清单工具达到
> 静态可用状态；但新指纹尚未贯穿两个真实 exact gate，上轮候选绑定 Medium 仍为
> `DYNAMIC_EVIDENCE_PENDING`。另新增 **1 Medium**：两份关键 `.log` 被仓库忽略，普通提交无法
> 带入，当前证据包在 fresh clone 中不自足。

---

## 1. Executive Summary

- 最终全量审计基线仍为 `dfdfb9199215d856b70abb653cb65b6f9ed46282`，其正式结论保持
  **CHANGES_REQUESTED / NO-GO（0 Critical / 7 High / 13 Medium / 4 Low）**。
- 本轮冻结时分支为 `codex/final-audit-high-remediation`，HEAD 为
  `aa3509a19151e2caad03014324b9b36e98bc0f05`；工作树有 81 个 tracked 改动路径和
  95 个 non-ignored untracked 文件。该计数发生在写入本报告与本轮治理记录之前。
- 第六整改没有继续改生产逻辑。新增的
  `scripts/candidate_source_manifest.py` 会双读 HEAD、固定参数的 tracked binary diff、
  untracked 路径集合及其原始文件字节，任一漂移即失败；manifest 强制写到仓库外。
- 独立执行 6 个纯离线正反例全部通过；当前快照 capture/verify 得到相同观察指纹
  `0e8a6bf33c57599aabd6b4ea9bb656afa8c0597ac116210b71968efcad3dee20`，其中明确包含
  untracked 的 `DatabaseBackupProperties.java`（1173 bytes，
  SHA-256 `404c5ff46c201195466b6be2bc83939233f11d2f26b7cb063baf20a875e9dec0`）。
  该值只是复核前快照，写入本报告后会变化，不能冒充最终动态门禁身份。
- 历史证据 README 已准确改为“81 tracked + 32 untracked 状态分组 = 113 个状态项”，并明确
  旧包没有绑定 untracked 源码；上轮 Low 按原失败条件关闭。
- 旧 Phase 2 **4/4** 与 Phase 41 **1/1** 证据仍通过 7/7 哈希和 XML/log 验真，但它们早于
  新 manifest 工具，证据目录也没有候选 manifest 或新的 capture/verify 输出。因此不能倒推
  新完整候选已被两个真实门禁测试。
- 新发现两份被 `SHA256SUMS` 和 README 引用的关键运行日志命中 `.gitignore:7` 的 `*.log`，
  当前存在但未被 Git 跟踪；普通提交后证据包会缺文件，7/7 校验无法在 fresh clone 重放。
- Codex 未执行 Docker、数据库、Redis、MinIO、网络、HTTP、服务、浏览器、扫描、fuzz、凭据或
  权限操作。需要真实依赖的两个 exact gate 继续由用户在专用隔离环境执行。

### Score Dashboard

| Dimension | Score / 10 | 结论 |
|---|---:|---|
| Candidate identity design | 9.0 | HEAD、tracked diff 与 non-ignored untracked 文件均被绑定 |
| Drift detection | 8.8 | 双读失败关闭成立；同安全主体恶意竞态不在本阶段模型内 |
| Testing authenticity | 6.8 | 工具 6/6，但尚无新指纹下的真实 exact gate |
| Evidence portability | 5.8 | 两份关键 `.log` 被忽略，fresh clone 会缺件 |
| Documentation accuracy | 9.2 | 81/32/113 与旧证据边界已如实修正 |
| Regression safety | 9.0 | 本轮无生产逻辑变化，离线工具门禁与 diff check 通过 |
| **Scoped overall** | **8.1** | **候选工具可用，动态绑定与归档可移植性未闭环** |

### Finding Statistics

本表统计当前本轮范围内仍需处理的问题，不重复计算最终全量审计其它 OPEN/PARTIAL 项。

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

- Commits in range: 0；候选仍是 `aa3509a` 后的未提交工作树。
- New implementation files:
  - `scripts/candidate_source_manifest.py`
  - `scripts/test_candidate_source_manifest.py`
- Documentation changes: 历史证据 README、当前统一计划、进度与开发日志。
- Production Java/Vue behavior changed in this remediation: 0。
- Review-freeze size: 81 tracked changed paths、95 non-ignored untracked files。

### Change Categories

- Evidence tooling: dirty-tree candidate capture/verify。
- Tests: six pure-offline positive/negative cases。
- Documentation: corrected status-item counts and explicit old-evidence limitation。
- Governance: marked the candidate `DYNAMIC_EVIDENCE_PENDING`。

### Risk Delta

- Previous README Low closed: 1。
- Previous candidate-binding Medium statically ready but dynamically open: 1。
- New evidence-portability Medium: 1。
- New production-code regression: 0 confirmed。

### Test Coverage Delta

- Added 6/6 Python tests for stable capture and post-capture HEAD/tracked/untracked drift rejection。
- Independent current-worktree capture/verify smoke: PASS。
- No new Phase 2 / Phase 41 dynamic execution exists for the new complete-source fingerprint。
- No Maven regression rerun was necessary: this remediation changed only offline evidence tooling and docs;
  the immediately preceding independent review already ran focused 33/33, full Surefire 349/349 and
  9/9 package on the unchanged production/test implementation。

### Approval Recommendation

**Request changes**。停止修改生产逻辑；先完成同一外部 manifest 下的两个 exact gate，并把关键日志
以可跟踪形式归档。之后只做证据身份与包闭合的轻量复核。

## 3. Project Map

| Area | 本轮作用 |
|---|---|
| `scripts/candidate_source_manifest.py` | 构造并复算 dirty-tree 完整候选身份 |
| `scripts/test_candidate_source_manifest.py` | HEAD、tracked、untracked 和输出路径边界回归 |
| `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/` | 历史 Phase 2/41 真实门禁包 |
| `.gitignore` / `.gitattributes` | 决定日志能否进入可移植证据提交 |
| `CURRENT-EXECUTION-PLAN.md` / `PROGRESS.md` / `DEVLOG.md` | 当前正式状态与下一门禁入口 |

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|---|---|---|---|
| Incremental implementation | High | 两个新脚本逐行读码、当前工作树 smoke | 未改生产逻辑，不重复全量业务审计 |
| Testing authenticity | High | 6/6 Python、7/7 历史哈希、两份 XML/log | 未执行真实 MySQL/Redis/MinIO |
| Candidate binding | High | HEAD/diff/untracked 双读、关键配置类 hash | 新 manifest 尚未用于真实 gate |
| Evidence portability | High | ignore 规则、Git tracking、SHA256SUMS 引用 | 未创建 fresh clone，静态 Git 结论已确定 |
| Security/cyber | Not assessed | 仅确认无秘密操作 | 按用户要求不执行扫描、攻击性或外部操作 |

## 4. Previous-Finding Closure Matrix

| 上轮 finding | Severity | 本轮状态 | 独立结论 |
|---|---:|---|---|
| 真实门禁摘要未覆盖 build-affecting untracked 源码 | Medium | `STATIC_READY / DYNAMIC_EVIDENCE_PENDING` | 工具已覆盖关键类，但没有门禁前 capture、两次 gate 后 verify 与归档 manifest |
| README 把 113 个总状态项误写为 113 个 tracked 改动 | Low | `CLOSED` | 现准确写为 81 tracked + 32 untracked 状态分组，并撤回严格绑定表述 |

## 5. Top Risks

1. **Medium — 新完整候选指纹尚未贯穿真实门禁。** 当前只能证明工具可用，不能证明被测试的就是
   当前 81+95 文件候选。
2. **Medium — 两份关键 `.log` 不可由普通 Git 提交携带。** fresh clone 会使
   `SHA256SUMS` 引用缺失文件，并丢失脚本 PASS、构建与清理时序。

## 6. Detailed Findings

### Finding: 新完整候选指纹尚未用于两个真实 exact gate

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity / Evidence Integrity
- Status: Confirmed residual; remediation ready, dynamic evidence pending
- Affected area: 最终审计第六整改候选的 Phase 2 / Phase 41 正式证据
- Evidence:
  - File: `scripts/candidate_source_manifest.py:189-247`
  - Relevant behavior: 工具能够双读并记录完整候选。
  - File: `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/README.md:11-12`
  - Relevant behavior: 历史包明确只绑定旧 tracked diff。
  - File: `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/README.md:52-55`
  - Relevant behavior: 明确要求后续使用 source manifest 重跑。
  - File: `docs/CURRENT-EXECUTION-PLAN.md:88-96`
  - Relevant behavior: 当前状态仍为 `DYNAMIC_EVIDENCE_PENDING`。
- Problem: 工具及 smoke 在两个旧真实门禁之后产生；仓库内没有 manifest、三次 capture/verify 输出或
  新 Phase 2/41 产物。
- Why it matters: 旧 4/4 与 1/1 可以证明功能路径，但不能证明新完整候选字节就是当时编译输入。
- Realistic failure scenario: 当前任一 non-ignored untracked 源码在旧 gate 后变化，旧 HEAD 和
  tracked digest 仍可保持不变；没有运行前后 manifest 时，独立消费者无法区分。
- Minimal fix: 在本轮报告/治理更新结束后冻结工作树；仓库外 capture 一份 manifest；由用户在同一
  隔离环境顺序执行精确 `Phase2SecurityIT` 和 Phase 41 正式脚本，每项后对同一 manifest verify；
  归档 manifest、三次工具输出、完整日志、目标 XML/TXT 与闭合 `SHA256SUMS`。
- Better long-term fix: 从不可变提交或 `git archive <candidate-sha>` 运行门禁。
- Regression test suggestion: evidence verifier 要求 manifest 指纹、两次 selector、XML 计数和最终
  verify 全部一致。
- Estimated effort: 两项既有门禁运行时间，加 10–15 分钟归档

### Finding: 两份关键运行日志被 `.gitignore` 排除

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity / Release Evidence
- Status: Confirmed new finding
- Affected area: fifth-remediation 真实门禁包及第六整改后续归档流程
- Evidence:
  - File: `.gitignore:7`
  - Relevant behavior: 全局 `*.log` 规则忽略所有 `.log`。
  - Files:
    - `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/logs/run3-phase2.log`
    - `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/logs/run3-phase41.log`
  - Relevant behavior: 两文件当前存在且 7/7 哈希匹配，但 `git check-ignore -v` 均命中
    `.gitignore:7`，`git ls-files --error-unmatch` 均失败。
  - File: `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/SHA256SUMS:6-7`
  - Relevant behavior: 校验清单直接引用这两个不会被普通提交携带的文件。
  - File: `docs/reviews/evidence/final-audit-fifth-remediation-real-gates-2026-07-29/README.md:44-45`
  - Relevant behavior: 文档将其表述为已归档原始日志。
- Problem: 本机证据包完整，但常规 stage/commit/fresh clone 不能重建两份日志。
- Why it matters: Phase 2 XML 可证明 4 个 testcase，Phase 41 XML 可证明 1 个 testcase；但完整
  Maven BUILD SUCCESS、脚本最终 PASS、环境检查与清理时序主要在两份日志中。缺失后证据链降级。
- Realistic failure scenario: 工作区清理或在另一机器检出候选后，`SHA256SUMS` 立即因两个路径缺失而
  失败，独立复核只能依赖二次摘要。
- Minimal fix: 后续正式证据将原始日志逐字节复制为 `.log.txt`，更新 README/SHA256SUMS，并确认
  `git ls-files --error-unmatch` 成功；或显式 force-add 两份 `.log`，再核对 staged tree。
- Better long-term fix: 证据闭合脚本同时检查“文件存在、hash 匹配、可被 Git 跟踪”，并在 fresh
  checkout 重放。
- Regression test suggestion: 对 SHA256SUMS 每个路径执行 hash 校验和
  `git ls-files --error-unmatch`；日志用 `-text -diff` 保留原始字节。
- Estimated effort: 10–20 minutes

## 7. Testing Authenticity Analysis

Coverage: High  
Inspected evidence: Python 工具与测试、历史 SHA256SUMS、Phase 2/41 XML/TXT/log、Git ignore/tracking  
Exclusions / limits: 未运行 Docker、真实服务或 Failsafe

### Confidence Assessment

| Test area | Real confidence | Risk | Action |
|---|---|---|---|
| Manifest stable capture | High | 未来重构可能破坏字段或路径集合 | Keep |
| Post-capture tracked/untracked/HEAD drift | High | 变更未被拒绝 | Keep |
| Mid-capture double-read | Medium | 当前主要由读码而非专用竞态测试证明 | Keep；非阻断 |
| Phase 2 historical gate | High for behavior, Low for current candidate identity | 当前指纹未绑定 | Rerun under manifest |
| Phase 41 historical gate | High for behavior, Low for current candidate identity | 当前指纹未绑定 | Rerun under manifest |
| Evidence package portability | Low | ignored logs disappear in fresh clone | Fix archive form |

### Valuable Tests

- `test_capture_binds_tracked_diff_and_exact_untracked_file`
- HEAD、tracked content、untracked content/path set 变化拒绝用例
- manifest 仓库内路径拒绝用例
- 历史 Phase 2 真实 MySQL 回滚 4/4 与 Phase 41 真实恢复 1/1

### Suspicious Tests

- 无伪绿或空选择器；当前问题是新指纹未实际包住真实 gate，而不是 testcase 本身失真。

### Missing Tests

- 新 manifest 下的 Phase 2 exact gate。
- 同一 manifest 下的 Phase 41 正式脚本。
- 证据路径在 Git/fresh clone 中可取得的闭合检查。

## 8. Release and Documentation Analysis

Coverage: High  
Inspected evidence: README、SHA256SUMS、ignore/attribute rules、统一计划/进度/DEVLOG  
Exclusions / limits: 未 stage、commit、push、deploy 或创建 fresh clone

- README 的 81/32/113 口径已修正，历史证据能力边界不再夸大。
- 治理文档没有提前自报 PASS，`DYNAMIC_EVIDENCE_PENDING` 与当前事实一致。
- `.log` 本机存在不等于可归档；下一包必须使用 trackable 名称或明确 force-add。
- 本轮不授权 merge、push、deploy、切流或稳定发布。

## 9. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected areas |
|---|---:|---:|---|
| Evidence must identify the tested candidate | 1 | Medium | 新 manifest 尚未贯穿真实 gate |
| Evidence must be portable and reproducible | 1 | Medium | 两份 ignored `.log` |

### Principles Respected

- **Fail closed:** HEAD、tracked diff、untracked 路径或内容漂移均使 verify 失败。
- **Explicit scope:** manifest 明确只覆盖 HEAD、tracked diff 与 non-ignored untracked 普通文件。
- **No self-inclusion:** manifest 强制写到仓库外。
- **Bounded remediation:** 没有引入签名服务、ACL、不可变存储或新的基础设施。
- **Honest evidence boundary:** README 和治理文档明确旧 gate 不能倒推新指纹。

## 10. Recommended Fix Order

### Fix Immediately

1. 完成本轮报告与治理更新后冻结工作树，在仓库外 capture 最终 manifest。
2. 由用户在同一专用隔离环境执行 Phase 2 exact gate，随后 verify 同一 manifest。
3. 执行 Phase 41 正式脚本，随后再次 verify 同一 manifest。
4. 以 `.log.txt` 或 force-add 方式归档完整日志，确保 manifest、工具输出、XML/TXT 与日志全部进入
   `SHA256SUMS` 且可由 Git 取得。

### Fix Before Stable Release

- 在 staged tree 或 fresh checkout 执行一次证据闭合检查。

### Schedule Later

- 将 manifest capture/verify 和 evidence portability 检查接入统一门禁脚本。

### Ignore for Now

- 不引入远程签名、ACL、不可变对象存储或分布式证据服务。
- 不重复运行与本轮无关的 Phase 3、媒体、浏览器、TLS 或供应链门禁。

## 11. Quick Wins

- 日志后缀改为 `.log.txt` 即可复用 Phase 53 已采用的可跟踪口径。
- 下一轮只核 manifest、三次工具输出、两个 exact selector、XML/TXT/log 和 SHA256SUMS。
- 生产逻辑与上轮两个功能 Medium 无需再次修改。

## 12. Independent Gate Results

| Check | Result |
|---|---|
| `python -B scripts/test_candidate_source_manifest.py -v` | PASS；6/6 |
| Current worktree capture / verify smoke | PASS；观察指纹 `0e8a6bf...dee20` |
| Critical untracked class included | PASS；1173 bytes，SHA-256 `404c5ff4...e9dec0` |
| Historical evidence `SHA256SUMS` | PASS；7/7 local files |
| Historical Phase 2 XML | PASS；4/4，0 failure/error/skip |
| Historical Phase 41 XML | PASS；1/1，0 failure/error/skip |
| New manifest-bound Phase 2 / Phase 41 | **NOT RUN / MISSING** |
| Evidence logs Git portability | FAIL；2/2 `.log` ignored and untracked |
| `git diff --check HEAD` | PASS |
| Docker / DB / Redis / MinIO / network / service / browser | Not executed |

## 13. Final Verdict

本轮独立增量复核结论为 **CHANGES_REQUESTED / NO-GO**：

- 不重跑或改写已经结束的最终全量审计；
- 上轮 README 计数 Low 已 `CLOSED`；
- source manifest 工具达到 `STATIC_READY`，没有发现新的算法级 High/Medium；
- 上轮候选绑定 Medium 仍为 `DYNAMIC_EVIDENCE_PENDING`，不能在两个真实门禁执行前标记关闭；
- 新增 **1 Medium**：两份关键 `.log` 被忽略，证据包无法由普通提交/fresh clone 完整取得；
- 下一轮只需复核完整候选指纹、两个 exact gate 与可移植证据包，不再扩大生产逻辑范围；
- 不授权 merge、push、deploy 或切流。
