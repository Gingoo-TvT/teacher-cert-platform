# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform — Phase 0 / U-004 第九轮动态证据增量复核
**Audit mode:** testing-authenticity / release
**Date:** 2026-07-28
**Reviewer:** OpenAI Codex (GPT-5)

---

## 1. Executive Summary

Phase 0 最终候选 `ea760bf2bd6041c8fd185bd6ab887380bf920171` 的五项补充门禁已经闭环。
Linux/POSIX 完整套件由执行者在 `python:3.14`、`--network none`、候选 `git archive`
源码上执行为 **79/79、skipped=0**；两个 POSIX-only ancestor 反例均实际通过。首次定向重跑
因把非包文件写成 unittest 模块路径而产生的 2 个 import error 不属于测试用例失败，修正入口后
定向 2/2 及完整 79/79 均通过。

本地找到最终 evidence bundle `C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r9`。仓库离线 verifier
独立返回 exit 0；另行复算得到 **14/14 checksum 匹配、15 个文件精确闭包、7 suites / 33
testcases / 0 failure / 0 error / 0 skip**。manifest 将 candidate、tree、906-file Git object
snapshot、preflight/formal 双身份、0/0/1/0 freshness、运行时间窗和 secret scan 绑定到同一次
执行，preflight 与 formal 均 exit 0。

成功 bundle 不受复核治理文档的未提交改动污染。第一次运行因 5 个 tracked 文档为 dirty 而按设计
失败；成功运行的 manifest 在 start / after-preflight / end 三处均证明 tracked clean，构建又来自
无 `.git` 的候选 Git-object snapshot。本轮写入 PASS 状态前记录到 5 个治理文件的恢复时间晚于
formal 结束与 bundle 发布，且 gate 源码/spec 仍逐字节等于候选。因此本轮
**0 finding；Phase 0 / U-004 正式 PASS**。这只关闭
逐阶段闸门并允许进入最终全量审计，不构成 merge、push、deploy 或稳定发布 GO。

### Score Dashboard

```text
Testing         ██████████  9.7  A   Linux 79/79；exact 7/33；bundle verifier 与独立 XML/hash 复算全绿
Release         █████████░  9.4  A   候选/source/target/发布闭包成立；Phase PASS 不替代最终全量审计
──────────────────────────────────────────────────────────────────────────────
Overall         ██████████  9.6  A   Phase 0 正式 PASS，0 finding
```

每项为 0.0–10.0，分数越高越好。Linux、Compose 与资源收尾事实来自执行者逐项回传；Codex 未重跑
任何外部资源操作。

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **0** | **0** | **0** |

## 2. Project Map

本轮只审 Phase 0 的最终动态证据链，不重复审计已在上一报告关闭的发布实现：

- 候选源码：`ea760bf` / tree `fd2174f5079adf6bc5a34e2f5a12f8df2c998c09` / 906 files。
- POSIX 合同：`scripts/test_phase00_ci_gate.py` 的 79 个 methods，其中 2 个要求 POSIX openat。
- 真实依赖 producer：preflight → immutable snapshot rematerialization → formal exact 7/33。
- canonical bundle：manifest、`SHA256SUMS`、2 段脱敏日志、7 个 suite XML、Failsafe summary、
  gate spec、preflight/runtime identity，共 15 个文件。
- 独立 consumer：`scripts/phase00_ci_gate.py verify` 对同一已发布 bundle 做离线全量复核。
- 运行环境收尾：一次性 MySQL/Redis/MinIO 栈、Compose 静态解析及专属资源销毁。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | 上一代码复核、最终 SHA/tree、当前 worktree diff/mtime、gate 源码与候选一致性 | 未重复审计无变化的产品代码 |
| Testing Authenticity | High | 用户回传 Linux 79/79；15-file bundle；仓库 verifier；独立 checksum/XML/testcase/time/source 复算 | Linux 原始日志未在本机单独归档；其运行事实按执行者回传记录 |
| Release | High | candidate/spec/source/target/secret scan、私有 bundle 单次发布、Compose 与清理回传 | 按安全边界未执行 Docker、数据库、对象存储、网络或端口操作 |

## 3. Top Risks

本冻结范围没有剩余 Phase 0 阻断风险或可操作 finding。唯一边界是：阶段 PASS 只允许进入最终全量
审计；项目级安全、业务规则、数据一致性、性能和发布 GO 仍须由该全量审计另行裁定。

## 4. Detailed Findings

未发现 Critical、High、Medium、Low 或 Info finding。第八轮 `P00-R3-M1` 与 `P00-R3-L1`
继续保持 CLOSED；本轮只补齐它们在最终 SHA 上的运行证据。

## 5. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: Linux 79/79 回传、candidate test source、r9 manifest/checksums/logs/XML/spec/
  identities、仓库离线 verifier、独立 PowerShell hash/XML 汇总。
- Exclusions / limits: 没有在 Codex 环境重跑 Linux 容器、Docker、MySQL、Redis 或 MinIO；
  Linux 原始 console log 未作为 bundle 第 16 个文件持久化。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|---------------|------|--------|
| Linux/POSIX Python 79/79 | Medium-High | 原始日志未落本机，但环境、源码来源、完整输出与两用例逐名回传，候选中恰为 79 methods / 2 POSIX guards | Keep |
| exact 7 suites / 33 cases | High | XML、manifest、spec、source mtime 与 Git blobs 多路互锁 | Keep |
| 同 bundle 离线 verifier | High | Codex 对现存 final bundle 独立执行并得到 exit 0 | Keep |
| candidate/source/worktree binding | High | SHA/tree/spec/file set 与三段 clean/HEAD 绑定，构建树无 `.git` | Keep |
| Compose 与资源收尾 | Medium-High | exit 0 与零残留来自执行者回传；无可持久化运行对象供事后重查 | Keep |

### Valuable Tests

- `test_posix_capture_rejects_nonterminal_ancestor_symlink_before_read`
- `test_posix_capture_rejects_ancestor_replacement_before_read`
- `Phase00TargetPreflight`
- `Phase00ScaffoldIT` 6/6
- `Phase00ParameterMatrixIT` 1/1
- 5 个 exact Surefire suites 合计 26/26
- 对 final bundle 的独立 offline verifier

### Suspicious Tests

无。错误模块路径的定向命令没有加载测试文件，只产生 unittest import error；它没有被计入任何
PASS，也不改变完整套件中两个 POSIX 用例始终为 `ok` 的结果。

### Missing Tests

Phase 0 本轮规定的测试门禁均已满足。Linux 原始 console log 可在最终审计归档时补作证据卫生增强，
但不要求重跑，也不构成 Phase 0 finding。

## 6. Release Concerns

- Coverage: High
- Inspected evidence: final bundle、离线 verifier、SHA256SUMS、XML、manifest source/target/security
  字段、当前 Git HEAD/diff/mtime、执行者 Compose/cleanup 回传。
- Exclusions / limits: 没有操作 Docker daemon、端口、数据库、Redis、MinIO、浏览器、账号或凭据；
  没有独立重现已经销毁的一次性运行环境。

五项阶段门禁结论：

| Gate | Independent conclusion |
|---|---|
| Linux/POSIX Python | PASS，79/79，skipped=0；两项 POSIX-only 均执行 |
| 全新隔离栈 exact gate | PASS，7 suites / 33 testcases，0/0/0 |
| 同 bundle offline verifier | PASS，Codex 独立执行 exit 0 |
| `docker compose ... config --quiet` | PASS，执行者回传 exit 0 |
| 专属资源清理 | PASS，执行者回传容器/网络/卷零残留，共享栈未触碰 |

脏工作树的处理不污染证据：成功 run 的 formal 窗口为
`2026-07-27T16:09:56.538Z..16:10:56.339Z`，bundle 于本地 00:10:57 完成；本报告开始写入前
记录到 5 个治理文档在 00:12:16 才恢复。manifest 中
`trackedWorktreeCleanAtStart/AfterPreflight/AtEnd` 均为 true，
而 gate 的 `require_clean_tracked_worktree` 在任一 tracked 差异存在时会在 Maven 前失败。具体
暂存/恢复命令未归档，本报告只采信上述可复核效果，不猜测操作方式。

Phase 0 / U-004 因此正式 **PASS**。所有逐阶段复核闸门现已关闭，下一项是最终全量审计；在最终
审计另行给出 GO 前，禁止把本报告解释为 merge、push、deploy、切流或稳定发布授权。

## 7. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| None in reviewed scope | 0 | — | — |

### Principles Respected

- Fail-fast：dirty tracked worktree、stale final evidence、候选/目标漂移均在发布前拒绝。
- Reproducibility：Maven 只从候选 Git object tree 物化的 906-file snapshot 执行。
- Atomicity：完整私有 bundle 通过全量 verifier 后，以一次目录 rename 发布。
- Evidence integrity：exact file set、SHA256SUMS、strict JSON、suite/testcase 与 target identity
  由独立 consumer 重新验证。
- Scope discipline：未为同安全主体恶意写者、不可变存储或远程文件系统添加超出既定威胁模型的机制。

---

## 8. Recommended Fix Order

### Fix Immediately

无 Phase 0 修复项。

### Fix Before Stable Release

执行用户要求的最终全量审计，并由其独立裁定项目级发布 GO。

### Schedule Later

最终审计归档时可把 Linux console log 与本报告摘要放到同一长期证据介质；无需为此重跑门禁。

### Ignore for Now

不重复浏览器验证，不扩展 Phase 0 exact suite，不引入 ACL、锁、不可变存储或其它越过既定威胁
模型的设计。

## 9. Quick Wins

- 以 `docs/CURRENT-EXECUTION-PLAN.md` 为唯一入口启动最终全量审计，避免重新打开历史候选计划。
- 最终审计消费 r9 bundle 时继续运行完整 verifier，禁止只读取 `manifest.status`。
