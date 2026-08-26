# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform / FINAL-F01-DYN 证据增量  
**Audit mode:** incremental + testing-authenticity  
**Date:** 2026-08-06  
**Reviewer:** Codex（独立增量复核）

---

## 1. Executive Summary

本轮聚焦最终审计 F-01“ERROR 导出绕过学院数据范围”。生产实现已经按原失败条件收口：ERROR 导出强制指定 import 批次，非校级用户只能访问本人创建的批次，校级用户可访问任一指定批次，查询仅取该 `batch_id` 的错误行，敏感错误值继续按专用权限决定脱敏或明文。常驻 `Phase10ExchangeIT` 也使用真实 HTTP、Spring Security、MySQL 和 Excel 结果覆盖学院 A/B 双 owner、双向越权、校级正例、缺失 batch、证件号/出生日期投影及 exact-one-row。

归档执行者证据自身完整：外部证据目录 9/9 SHA-256、Failsafe 17/17、MySQL 8.4、Phase 10 15/15、Phase 48 2/2 和三模块 Surefire 汇总 349/349 均可独立复算。可是该包没有归档容器内 `/workspace` 的候选 verify、可重建 tracked diff 的 patch/bundle、目标资源标识和清理原始记录；当前工作树也已从成功门禁的 `48a6c264…ba5929` 漂移为 `7409c46f…0647a`。因此正式结论为 **CHANGES_REQUESTED / EVIDENCE_PENDING（0 个新增代码 finding，1 个 Medium 证据完整性 finding）**。不要求继续修改 F-01 产品代码；补齐同一冻结候选的独立动态证据后即可重判。

### Score Dashboard

```text
Security        █████████░  8.5  A   原跨学院路径在代码和真实反例设计上关闭；未独立重跑使结论仅限候选切片
Stability       ████████░░  8.2  A   exact-batch 与 fail-closed 分支明确，当前离线 349/349；动态同源性仍待补
Performance     █████████░  8.8  A   ERROR 查询由全表收窄到单批次；未做负载或大批次性能复核
Testing         ███████░░░  7.0  A   HTTP/MySQL/Excel 反例真实，但容器内源码与外部 manifest 没有正式绑定
Maintainability ████████░░  8.3  A   修复复用统一 batch owner guard，未引入平行授权框架
Design          █████████░  8.5  A   权限事实由 exact owner/all-school 决定，避免 scopeJson 模糊解析
Release         ██████░░░░  5.8  B   证据包不可从仓库重建，且缺栈内 verify、target marker 与清理原始证明
─────────────────────────────────────
Overall         ████████░░  7.9  A
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 1 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **1** | **1** | **0** |

## 2. Project Map

F-01 路径为 `ExchangeController.export` → `ExchangeServiceImpl.export(ERROR)` → `requireBatch` → `ensureBatchAccessible` → `ImportErrorDetail(batch_id)` → `ExchangeExcelHelper`。认证和权限入口在 Controller，真正的数据归属守卫在 service；`import_export_batch.operator_id` 是非校级访问的单一 owner，`DataScopeService` 仅决定是否为 all-school。敏感值在写入 Excel 行前经 `projectErrorValue` 投影。

动态反例位于 `Phase10ExchangeIT`：测试通过真实登录取得三类 token，直接在真实 MySQL 建立两个不同 owner 的 import/error 批次，再通过随机端口 HTTP 下载 Excel 并解析工作簿。`Phase48CertImportGuardIT` 是首轮非法 `FOR UPDATE LIMIT 1` 修复后的证书终态回归控制。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | High | Controller、service owner guard、错误值投影、双 owner HTTP 用例、归档 XML/日志 | 未由本轮独立者启动真实栈 |
| Stability | Medium | 缺 batch/type/owner 失败关闭、Phase 48 回归、当前全仓 Surefire | 未重跑 Failsafe 和外部依赖故障路径 |
| Performance | Medium | ERROR 查询 SQL 构造及 exact batch 结果边界 | 未做大错误批次或内存/时延测量 |
| Testing | High | 测试源码、49 个归档 Surefire class summary、2 个 Failsafe XML、9/9 checksum、当前聚焦 13/13 | 缺容器内候选 verify，归档包不含完整 target provenance |
| Maintainability | Medium | 调用链、共享 guard、DTO 变更与直接调用方 | 未审计 F-01 以外的整个 ExchangeServiceImpl |
| Design | Medium | owner/all-school 单一授权事实、exact batch 边界 | 未重新评估所有 exchange scope 语义 |
| Release | High | manifest schema/tool、capture/verify 日志、证据 closure、当前候选漂移 | 无旧候选 patch/bundle、栈内 verify、资源清理原始清单 |

## 3. Top Risks

1. **Medium — 成功动态门禁没有把容器内源码绑定到 `48a6c264…`。** 外部 manifest 与 XML/日志各自可信，但缺少连接二者的栈内 verify 和可重建源码载体，不能按正式闸门签发 scoped PASS。

## 4. Detailed Findings

### Finding: 动态成功日志缺少容器内候选与可重建源码绑定

- Severity: Medium
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: FINAL-F01-DYN evidence provenance
- Evidence:
  - File: `C:\Users\wenbibuhaoqwq\Desktop\teacher-cert-evidence\phase10-phase48-2026-07-29\candidate-source-manifest.json:1`
  - File: `C:\Users\wenbibuhaoqwq\Desktop\teacher-cert-evidence\phase10-phase48-2026-07-29\logs\step1-capture.log.txt:1`
  - File: `C:\Users\wenbibuhaoqwq\Desktop\teacher-cert-evidence\phase10-phase48-2026-07-29\logs\step3-verify-after-gates.log.txt:1`
  - File: `C:\Users\wenbibuhaoqwq\Desktop\teacher-cert-evidence\phase10-phase48-2026-07-29\logs\phase10-phase48-it.log.txt:1430`
  - Function / Module: `candidate_source_manifest.py` / external Failsafe harness
  - Relevant behavior: 归档 manifest 绑定宿主工作树 `48a6c264…`，测试日志显示实际 Maven `user.dir=/workspace/platform-boot`；包内没有 `/workspace` 门禁前后 verify、tracked patch/bundle 或从该 manifest 物化源码的记录。
- Change type: Evidence-only gap
- Problem: 证据包能证明某个 `/workspace` 在 MySQL 8.4 上得到 17/17，也能证明宿主候选在两个时点等于 `48a6c264…`，但缺少强证据证明两者是同一源码。manifest 对 tracked 变化只保存 binary diff 汇总哈希，不保存 diff 原文；当前工作树已漂移，旧候选不能从包内重建。
- Why it matters: F-01 原 finding 是跨学院 PII 泄漏 High。正式关闭必须把实际执行的反例、源码候选和目标环境连成同一证据链，不能用执行者口头说明补齐缺环。
- Risk introduced: 复核者可能把另一个容器工作区的绿灯错误归属给 `48a6c264…`，或在后续工作树漂移后仍把旧结果当作当前候选证明。
- Realistic failure scenario: 宿主 capture 后，容器使用较早复制或缓存的源码运行并通过；门禁后仅验证宿主未变。由于没有栈内 verify，最终报告仍会得到“同候选 PASS”，但被审代码未实际执行。
- User-visible impact: 若错误放行，学院用户仍可能在未被实际测试的构建中下载其他学院的原始错误值。
- Minimal fix: 在下一个冻结候选上由独立隔离环境执行：宿主 capture → `/workspace` 门禁前 verify → Phase 10 15/15 + Phase 48 2/2 → `/workspace` 与宿主门禁后 verify；归档命令、manifest、XML、原始日志、容器/镜像/schema 目标标识、SHA256 和清理零清单。
- Better long-term fix: 正式门禁直接从受校验的 git bundle 或只读源码快照物化工作区，并把 snapshot/hash、target marker 与完整证据目录纳入一个可离线 verifier 的 manifest。
- Regression test suggestion: 证据 harness 在测试前后各改变一个 tracked/untracked 字节，必须失败且不得发布 PASS；未归档栈内 verify 或存在额外文件时 verifier 也必须失败。
- Estimated effort: 2–4 hours（由获授权的独立隔离环境执行）

## 5. Security Concerns

- Coverage: High
- Inspected evidence: `ExchangeController.export`、`ExchangeServiceImpl.exportErrors/ensureBatchAccessible/projectErrorValue`、Phase 10 双 owner 用例与归档 XML。
- Exclusions / limits: 未执行独立真实 HTTP/MySQL 门禁；结论不覆盖其它导出类型、TLS、refresh token 或媒体访问。

原 F-01 代码级失败条件已关闭，没有发现新的静态跨学院旁路。Controller 仍要求 full export 权限，service 再以 all-school 或 exact operator owner 做对象级授权；`scopeJson` 和客户端 `collegeId` 不参与最终授权。非敏感角色的身份证件号/出生日期错误值按字段投影脱敏，校级敏感权限正例保留明文。

## 6. Stability Concerns

- Coverage: Medium
- Inspected evidence: 缺 batch、非 import batch、跨 owner、无错误行分支；当前全仓 Surefire 349/349。
- Exclusions / limits: 未由本轮独立者运行 Failsafe、真实数据库故障或并发导入门禁。

ERROR 导出在任何数据查询前验证 batch、类型和 owner，失败关闭路径明确。首轮候选暴露的 MySQL `FOR UPDATE LIMIT 1` 已在第二轮门禁转绿，当前可执行源码只剩解释性注释命中；但这些运行事实仍受候选绑定 finding 限制。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: `ImportErrorDetail` 查询限定单个 batch 并使用有序结果，Excel 行数等于该批次错误数。
- Exclusions / limits: 未执行容量、堆、数据库计划或锁等待测量。

本增量把原全表导出缩小为 exact batch，没有引入新的跨批次扫描。单个超大错误批次的导出预算属于更宽的容量合同，本轮没有足够证据单列 finding。

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: `Phase10ExchangeIT`、`ExchangeExportGuardTest`、Phase 10/48 XML、完整 Maven 日志、9/9 SHA256、当前聚焦 13/13 与 manifest 工具 6/6。
- Exclusions / limits: 未亲自运行归档候选的真实栈；浏览器、Docker、网络和线上环境均未访问。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| Phase 10 双 owner ERROR 导出 | High | 测试行为真实，但执行源码同源性未正式绑定 | Keep；在冻结候选重跑 |
| ExchangeExportGuardTest | Medium | Mockito 单独不能证明 SQL/HTTP/Excel；已由 IT 补强 | Keep as fast regression |
| Phase 48 终态证书守卫 | High | 与 F-01 间接相关，负责锁查询修复回归 | Keep |
| candidate manifest 6/6 | High | 工具行为可靠，但本次包缺栈内调用产物 | Keep；接入正式 harness |

### Valuable Tests

- `errorExportIsExactBatchOwnerOnlyAcrossCollegesAndMasksSensitiveValues` 使用真实三角色登录、两 owner 批次、双向 403、校级访问、敏感投影和解析后的 Excel 单行断言，能捕获原 High。
- `Phase48CertImportGuardIT` 在修复五处锁查询后重跑，防止为了让 Phase 10 转绿而放松终态证书守卫。
- 当前独立离线聚焦 13/13 与全仓 349/349 证明后续工作树没有破坏 fast regression；它们不替代旧候选动态同源证明。

### Suspicious Tests

- Mockito `ExchangeExportGuardTest` 含 SQL wrapper/调用次数断言，若没有真实 IT 会形成实现细节型假信心；当前仅保留为快速回归，不作为 F-01 关闭依据。

### Missing Tests

- 不缺产品反例；缺的是正式 harness 对“实际执行源码 = manifest 候选”的负向门禁。

## 9. Maintainability Concerns

- Coverage: Medium
- Inspected evidence: ERROR 导出调用链、DTO batchId、共享 batch access guard 及直接测试调用方。
- Exclusions / limits: 未对超过千行的 `ExchangeServiceImpl` 做全文件 maintainability 审计。

F-01 复用既有 `requireBatch` 与 `ensureBatchAccessible`，没有建立第二套学院解析或授权规则。该局部修复可维护；大 service 的结构债不由本次增量新增，故不重复报为 finding。

## 10. Design / Principles Concerns

- Coverage: Medium
- Inspected evidence: fail-fast、least privilege、single source of truth 与测试边界。
- Exclusions / limits: 未审计整个 exchange 架构或所有数据范围切面。

局部设计遵守 fail-fast 和 least privilege：客户端必须给 exact batch，服务端以持久 owner/all-school 决定访问，结果查询再次绑定 batch。没有使用自由 `scopeJson` 或客户端学院提示作为安全事实。

## 11. Release Concerns

- Coverage: High
- Inspected evidence: 外部证据 closure、manifest schema/工具哈希、capture/verify 日志、XML、Maven 日志、当前候选 fingerprint。
- Exclusions / limits: 没有旧候选 bundle、镜像/container ID、完整命令稿或清理零清单。

本节唯一阻断是 Detailed Finding 所列证据 provenance。它不要求产品改动，但在补齐前不得把 F-01 从 `EXECUTOR_GATE_PASS / INDEPENDENT_REVIEW_PENDING` 改成独立 PASS。

## 12. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Reproducible evidence boundary | 1 | Medium | FINAL-F01-DYN external harness |

### Principles Respected

- Fail-Fast：缺 batch、错误 batch 类型和跨 owner 在查询错误明细前拒绝。
- Principle of Least Privilege：非校级只能读取本人批次，敏感值另需专用权限。
- Single Source of Truth：持久 `operator_id` 与 all-school scope 是授权事实，客户端 hint 不参与裁决。
- Test Behavior, Not Only Implementation：真实 HTTP/MySQL/Excel 用例补强了 Mockito 快速回归。

## 13. Change Summary

- Base Git reference: `aa3509a19151e2caad03014324b9b36e98bc0f05`
- Reviewed historical candidate: `48a6c264d80824082d9fde1b5c1aaa5f26a6845605bf71cdbbbef88069ba5929`
- Current observed candidate: `7409c46fb7ae94e4ee1bf4975418e567c5f1d77e4a37c2f2efe83f403790647a`
- Historical full-candidate manifest: 82 tracked changed files、101 non-ignored untracked files
- Current full-candidate manifest: 154 tracked changed files、113 non-ignored untracked files
- F-01 direct changed-file slice: 4 tracked files，379 additions / 67 deletions（文件内含同批其它审计整改）
- Commits in range: 0；全部为 `aa3509a` 上的 dirty-worktree 增量
- Authors: working-tree evidence 未提供可验证 author 元数据

### Change Categories

- Bug fixes: 3 production files
- Tests: 1 integration-test file，另复验 1 个既有 Phase 48 IT
- Configuration: 0
- Migration/schema: 0
- Dependencies: 0
- Documentation: 本报告与治理状态同步

### Risk Delta

- Existing product risks fixed in inspected code: 1 High 原失败条件
- New product risks introduced: 0
- Evidence/release risks confirmed: 1 Medium
- Existing risks made worse: 0

### Test Coverage Delta

- New production behavior with direct unit + integration coverage: 3 files / 1 端到端行为切片
- New production behavior without tests: 0（本范围）
- Deleted tests: 0

## 14. Approval Recommendation

**Request changes — evidence only.** 不再修改 F-01 产品代码；在同一新冻结候选上补齐独立栈内候选 verify、原始动态门禁和可移植证据闭包后重交。当前仍为 `EXECUTOR_GATE_PASS / INDEPENDENT_REVIEW_PENDING`，项目级 `CHANGES_REQUESTED / NO-GO` 不变。

## 15. Recommended Fix Order

### Fix Immediately

1. 冻结统一最终候选并生成仓库外 manifest 与可重建源码载体。
2. 由获授权独立环境在栈内门禁前后验证同一 manifest，执行 Phase 10 15/15 + Phase 48 2/2。
3. 归档 target marker、完整命令、XML/日志/SHA256 与资源清理零清单，再进行轻量增量复核。

### Fix Before Stable Release

- 将正式证据 verifier 纳入 P0 发布门禁；F-01 未独立 PASS 前不得 merge、deploy 或切流。

### Schedule Later

- 如后续确认单批次错误量很大，再单列 ERROR 导出流式/分页容量评估；不阻断当前原 High 的代码收口。

### Ignore for Now

- 不为 F-01 引入新的全局 scope 框架、事件总线或 service 重写。

## 16. Quick Wins

- 在现有 harness 命令中增加 `/workspace` 前后两次 `candidate_source_manifest.py verify`，并把输出纳入 SHA256SUMS。
- 归档 `docker compose ps --format json`、MySQL schema UUID/端点标记和 teardown 后零清单，但不记录秘密值。
- 对 tracked diff 同时保存受哈希保护的 patch/bundle，避免只有汇总哈希而无法重建历史候选。
