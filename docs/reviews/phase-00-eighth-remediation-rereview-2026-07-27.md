# Phase 0 / U-004 第八轮正式独立增量复核

- 日期：2026-07-27
- 模式：incremental / testing-authenticity / release
- 上一正式 HEAD：`9b97741a3da921e3bce648e0c98fdb2892ec72e2`
- 第八轮实现：`781071536eea7ae1e92886173619d1b8d0ea62f7`
- 本轮最终 HEAD：`1c9d41696574ff2b4d50a5908f5998c3ed55b437`
- 本轮最终 tree：`8e629804c088c515ebe772eb914d3db00476f37a`
- 结论：**CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 0 Low）**

## 1. Executive Summary

第八轮对上一轮两项 finding 做了实质整改。`P00-R3-L1` 已在代码与回归合同层面关闭：
POSIX evidence root 现在从 `/` 起逐段执行 no-follow `stat`、相对父 fd 的
`openat(O_DIRECTORY|O_NOFOLLOW)`、`fstat` identity 对齐，并持有全部祖先 fd 到递归读取和
末次逐边复核完成。两个 Linux-only 反例也精确覆盖非末段 symlink 与 stat/open 间替换；当前
Windows 只能确认它们被正确 skip，不能冒充 Linux 运行 PASS。

`P00-R3-M1` 则只部分关闭。候选已从已验证 snapshot bytes 重建随机私有 bundle，并在私有路径和
发布后的 final 路径分别 capture/match；但 final capture 在
`scripts/phase00_ci_gate.py:4674-4679` 返回并释放句柄后，直到
`4683-4691` 才把隐藏 marker 原子改名为 canonical `manifest.json`。纯离线协调反例在这次
marker rename 入口改变 artifact，稳定得到：

```json
{"mutationApplied":true,"verified":true,"error":null,"canonicalPassVisible":true,"withheldMarkerVisible":false,"offlineMismatch":"SHA256SUMS mismatch for artifact.txt"}
```

因此函数仍可能成功返回并暴露 canonical PASS，而同一 bundle 已不能通过自己的 checksum。
这仍是原 M1 的“验证集合与发布集合不一致”，不是新增问题。正式状态继续
**CHANGES_REQUESTED**。本轮没有找到绑定 `1c9d416` 的 r8 evidence、Linux 77/77、
Compose config 或专属资源清理材料；但在 M1 修复前无需先消耗真实依赖环境。

### Score Dashboard

```text
Incremental          ████████░░  8.3  A   L1 已关闭，M1 的大部分旧窗口已消除，但最终 marker 线性化仍不完整
Testing Authenticity ███████░░░  7.0  A   75/2 与 274/274 真实通过，现有发布反例漏掉 canonical-marker 窗口
Release              ███████░░░  6.7  B   仍有 1 Medium，且最终 SHA 的 Linux/真实 gate/Compose/清理证据未生成
──────────────────────────────────────────────────────────────────────────────
Overall              ███████░░░  7.3  A   可做一次更小的 M1 收口，当前不可判 Phase PASS
```

分数越高越好；本报告只评分所选增量、测试真实性与发布维度。

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|---|---:|---:|---:|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 1 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **1** | **1** | **0** |

## 2. Project Map

本轮只覆盖第七轮正式 HEAD 至第八轮最终 HEAD 的 Phase 0 证据门禁增量：

- `scripts/phase00_ci_gate.py`：POSIX evidence capture、PASS 私有重建与发布状态机。
- `scripts/test_phase00_ci_gate.py`：M1 发布窗口与 L1 POSIX 祖先替换反例。
- `docs/reviews/phase-00-eighth-remediation-submission-2026-07-27.md`：整改者声明与待执行门禁。
- `HANDOFF.md`、`PROGRESS.md`、`DEVLOG.md`、`docs/CURRENT-EXECUTION-PLAN.md`、
  `docs/phase-00-脚手架.md`：阶段治理状态。

产品业务、API、Flyway、依赖、前端和 exact 7 suites / 33 testcases 合同没有变化，未重复审计。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|---|---|---|---|
| Incremental | High | `9b97741..1c9d416` 全部 10 个文件、3 个提交、逐函数 diff、提交链与 diff check | 未重复审计无变化的产品代码 |
| Testing Authenticity | High | Python 77 methods、M1 现有 5 项专项测试、独立 marker-window 反例、Surefire XML 32/274 | POSIX 两例在 Windows 跳过；无 Linux runner |
| Release | Medium | PASS 发布状态机、最终 HEAD/tree、材料与本机 r8 evidence 检索、9 模块离线 package | 未执行 Docker/真实依赖/Compose；不存在可离线核验的 r8 bundle |

## 3. Change Summary

- Total files changed: **10**
- Lines added: **1063**
- Lines deleted: **70**
- Commits in range: **3**
- Authors: **wenbibuhaoqwq**

### Change Categories

| Category | Files | Notes |
|---|---:|---|
| Bug fix | 1 | `scripts/phase00_ci_gate.py` |
| Tests | 1 | `scripts/test_phase00_ci_gate.py` |
| Documentation / governance / evidence | 8 | 提交材料、上一轮报告与现行状态 |
| Dependency / schema / product configuration | 0 | 无变化 |

### Previous Finding Closure Matrix

| 原 finding | 第八轮状态 | 依据 |
|---|---|---|
| `P00-R3-M1` PASS 最终化可发布不同 artifact 集合 | **OPEN / Medium** | 私有重建与双 capture 已生效；final capture 后至 canonical marker rename 仍可改变 artifact |
| `P00-R3-L1` POSIX root 非末段祖先未锚定 | **CLOSED（代码/合同）** | `/` 起逐段 openat、identity 对齐、祖先 fd 保留和两项 Linux-only 精确反例 |

### Risk Delta

- 新增 finding：**0**
- 关闭：**1 Low**
- 仍开放：**1 Medium**
- 原 M1 已显著缩窄，但未满足原失败条件的关闭标准。

### Test Coverage Delta

- Python 从 71 methods 增至 77 methods；本机结果 **75 PASS / 2 POSIX-only skipped**。
- 新增的 checksum 后 mutation、manifest 后 delete、目录 rename 内 mutation、final capture error
  均有价值并通过。
- 仍缺 canonical marker rename 入口的 mutate/delete 反例。
- Java Surefire **32 suites / 274 tests**，0 failure/error/skip。

### Approval Recommendation

**Request changes。** 只继续修 `P00-R3-M1`，不扩展 Phase 0、产品代码或动态环境范围。
`P00-R3-L1` 不再进入下一轮代码整改；Linux 77/77 作为最终 SHA 的门禁保留。

## 4. Top Risks

1. **Medium — final capture 与 canonical marker rename 之间仍可暴露 checksum 不一致的 PASS。**
2. **Gate gap — 最终 HEAD 尚无 Linux 77/77、r8 exact 7/33、Compose 与专属资源清理材料。**

## 5. Detailed Findings

### Finding: canonical PASS marker 暴露前仍可改变已复核 artifact

- ID: P00-R3-M1
- Severity: Medium
- Confidence: High
- Category: Release / Testing
- Status: Confirmed, previous finding remains open
- Affected area: Phase 0 evidence PASS publication
- Change type: Modified
- Evidence:
  - File: `scripts/phase00_ci_gate.py:4674-4691`
  - Function / Module: `publish_preverified_pass`
  - Relevant behavior: final-path snapshot 在 4674–4679 完成比较后即释放读取句柄；4683–4691
    才执行 `.manifest.pass-ready → manifest.json`。
  - File: `scripts/test_phase00_ci_gate.py:2816-2833`
  - Function / Module: `assert_publish_mutation_downgrades`
  - Relevant behavior: 现有 `inside-rename` 注入发生在 `publish_dir → final` 的目录 rename，
    随后的 final capture 会捕获它；没有覆盖成功 final capture 后的 canonical-marker rename。
  - Test that demonstrates the issue: 纯离线 patch 只在 source 为
    `.manifest.pass-ready`、destination 为 `final/manifest.json` 时先改写
    `final/artifact.txt`，随后调用真实 rename；函数返回 `verified=true`，而离线 checksum
    立即失败。
- Problem: 最后一次“已验证字节集合”与“canonical PASS 可见”不是同一个受保护的线性化操作。
- Why it matters: 自动上传或只读 manifest 状态的消费者可先接收 PASS，之后完整 verifier 才发现
  证据包内部不一致。
- Realistic failure scenario: 同账号的并发归档/同步/清理进程在 final 目录可见后、marker 暴露前
  改动 artifact；门禁仍成功退出。
- User-visible impact: Phase 0 可被错误标记为证据完整，后续全量审计必须返工或拒绝该 bundle。
- Minimal fix: 不再增加“校验后再校验”的循环。把含 canonical manifest 的完整私有 bundle
  做一次落盘全量 verifier 后，直接以目录原子 rename 作为最终成功转换，并明确证据生产者只保证
  发布瞬间的一致性；若项目坚持防御恶意同账号并发写，则必须先定义并实现跨平台排他/不可变存储边界，
  不能仅靠 marker 次序声称闭环。
- Better long-term fix: 无需新框架；优先采用前述单一目录发布线性化点并收窄威胁模型。外部后续篡改由
  SHA256SUMS 与离线 verifier 检出。
- Regression test suggestion: 新增 canonical-marker rename 时 mutate 与 delete 两个 subtest；
  当前实现必须先红。收口后断言不存在
  `verified=true + canonical manifest visible + checksum mismatch`，并保留目录 rename 失败反例。
- Estimated effort: 1–3 hours

## 6. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 77 项 Python gate test、M1 定向 coordinator、32 份 Surefire XML、提交 diff。
- Exclusions / limits: Windows 不能执行两个 POSIX-only 用例；没有最终 SHA 的真实依赖 evidence。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|---|---|---|---|
| 私有 bundle 重建与私有复核 | High | 已验证字节确实被重建并重新核对 | Keep |
| withheld final capture | Medium | 能捕获目录 publish 前/中 mutation，但漏 canonical-marker 窗口 | Keep but augment |
| POSIX ancestor chain | High（静态合同） | Windows 未实际执行 Linux syscalls | Keep；最终 SHA 在 Linux 跑 77/77 |
| Java 离线回归 | High | 与本轮 Python 发布缺陷无直接覆盖关系 | Keep |

### Valuable Tests

- `test_artifact_mutation_after_checksum_cannot_publish_pass`
- `test_artifact_delete_after_manifest_cannot_publish_pass`
- `test_artifact_mutation_inside_final_rename_cannot_report_pass`
- `test_published_capture_error_never_leaves_canonical_pass`
- 两项 POSIX-only 非末段祖先反例

### Suspicious Tests

`inside final rename` 的命名容易让读者以为覆盖最终 marker rename；实际注入的是私有目录到 final
的 rename。测试本身有价值，但不能作为 M1 全关闭证据。

### Missing Tests

- canonical marker rename 入口 mutate artifact。
- canonical marker rename 入口 delete artifact。
- 最终 clean SHA 的 Linux 77/77。

## 7. Release Concerns

- Coverage: Medium
- Inspected evidence: HEAD/tree、提交链、离线 package、治理文档、本机 evidence 路径检索。
- Exclusions / limits: 按用户安全边界未执行 Docker、MySQL、Redis、MinIO、网络、服务、浏览器或
  Compose；本机没有 r8 evidence 可供离线 verifier。

当前无需先跑真实依赖。先关闭 M1并固化新的最终 SHA，再一次性执行：

1. Linux/POSIX Python 77/77，skipped=0；
2. 全新隔离栈 exact 7 suites / 33 testcases；
3. 对同一 r8 bundle 运行离线 verifier；
4. `docker compose -f docker-compose.dev.yml config --quiet`；
5. 回传本轮专属容器、网络、卷的清理结果。

浏览器不要求。Phase PASS 仍不授权 merge、push、部署、切流或稳定发布；之后还需用户要求的最终全量审计。

## 8. Recommended Fix Order

### Fix Immediately

1. 只修 M1 canonical-marker 窗口并补两个精确反例。

### Fix Before Stable Release

2. 在新的最终 clean SHA 上执行 Linux 77/77 与一次完整真实 gate。
3. 同 SHA 归档 Compose config 和专属资源清理结果。
4. 做一次仅核对 M1 与新 evidence 的独立增量复核。

### Schedule Later

无本轮新增长期重构项。

### Ignore for Now

不重复浏览器验证，不扩展产品、schema、依赖或 exact suite 合同。

## 9. Quick Wins

- 复用现有 `assert_publish_mutation_downgrades` fixture，新增 marker rename 的 mutate/delete 两个分支。
- 删除容易误导的“final exact capture 已完全封闭所有并发窗口”声明。
- 采用一个明确的最终发布线性化点，停止继续叠加 capture/marker 状态。

## Safety Boundary

本次只执行 Git/源码/材料读取、纯离线 Python 回归和定向临时夹具、Maven offline test/package、
XML 汇总与 diff check。没有执行 Docker、MySQL、Redis、MinIO、网络、HTTP 服务、浏览器、扫描、
fuzz、凭据/权限或其它可能属于 cyber 的动作；两个因 MSYS Python `TemporaryDirectory` ACL
产生的空临时目录均在核对绝对路径后删除，最终无残留。没有 stage、commit、merge、push 或 deploy。
