# Phase 0 / U-004 第九轮正式独立增量复核

- 日期：2026-07-27
- 模式：incremental / testing-authenticity / release
- 上一正式 HEAD：`1c9d41696574ff2b4d50a5908f5998c3ed55b437`
- 第九轮实现：`f3fa054bcd3f5f2217a8787a3cb1b9c08185c505`
- 本轮最终 HEAD：`ea760bf2bd6041c8fd185bd6ab887380bf920171`
- 本轮最终 tree：`fd2174f5079adf6bc5a34e2f5a12f8df2c998c09`
- 增量结论：**PASS；`P00-R3-M1` CLOSED，0 个新 finding**
- Phase 0 阶段结论：**CHANGES_REQUESTED / EVIDENCE_PENDING**，尚缺最终 SHA 的动态门禁

## 1. Executive Summary

第九轮逐字落实了第八轮正式报告给出的最小修法。完整 canonical bundle（artifact、
`SHA256SUMS`、`manifest.json`）先从已验证 snapshot bytes 重建到同文件系统的随机私有 sibling，
再在该私有目录内执行 exact payload match 与完整 `_verify_evidence_snapshot`。旧的 withheld
marker、final-path capture 和文件级 marker rename 已全部删除；原 collection staging 的清理和
final 路径不存在检查均发生在发布前，最后且唯一的成功转换是一次
`private directory → final directory` rename。rename 成功后代码不再 capture、cleanup、写 marker
或执行其它可能改变成功结论的操作。

威胁边界也已诚实收窄：`producer-private` 是所有权/单写者前提，不是 ACL、锁或不可变存储保证。
同一 security principal 在私有验证至 rename 间主动写入、发布后篡改、断电持久化和特殊远程文件
系统语义不属于本轮保证；消费者仍必须验证 exact file set、manifest 与 `SHA256SUMS`，不能只相信
`manifest.status`。这与第八轮正式报告建议的线性化点一致，没有引入不必要的平台机制。

因此，上一轮唯一开放的 `P00-R3-M1` 在该明确边界内 **CLOSED**；`P00-R3-L1` 保持代码/合同关闭，
本轮没有新增 Critical / High / Medium / Low / Info finding。

但代码关闭不等于 Phase 0 正式 PASS。本机未找到绑定 `ea760bf` / `fd2174f5…` 的 r9 evidence；
Windows 也不能执行两个 POSIX-only 用例。最终 SHA 的 Linux 79/79、全新隔离栈 exact 7/33、
同一 bundle 离线 verifier、Compose config 与专属资源清理证据均待补。因此本报告接受第九轮
整改增量，但 Phase 0 继续 **CHANGES_REQUESTED / EVIDENCE_PENDING**，不得进入最终全量审计。

### Score Dashboard

```text
Incremental          ██████████  9.6  A   原 M1 状态机已删除，单次目录 rename 成为唯一发布线性化点
Testing Authenticity █████████░  8.7  A   77/2、定向反例与 274/274 可信；Linux/真实依赖仍待执行
Release              ████████░░  7.5  A   0 代码 finding，但最终 SHA 的正式动态证据尚未形成
──────────────────────────────────────────────────────────────────────────────
Overall              █████████░  8.6  A   接受整改增量；阶段仍由动态证据闸门阻断
```

分数越高越好；本报告只评分所选增量、测试真实性与发布维度。

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|---|---:|---:|---:|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **0** | **0** | **0** |

## 2. Project Map

本轮只覆盖 `1c9d416..ea760bf` 的 Phase 0 证据发布增量：

- `scripts/phase00_ci_gate.py`：完整私有 PASS bundle 物化、私有复核与单次目录发布。
- `scripts/test_phase00_ci_gate.py`：旧 marker 入口消失、rename 失败、私有 mutation/delete 与
  post-publish 不重开等合同。
- `docs/reviews/phase-00-ninth-remediation-submission-2026-07-27.md`：整改者声明、威胁边界与
  待执行门禁。
- `HANDOFF.md`、`PROGRESS.md`、`DEVLOG.md`、`docs/CURRENT-EXECUTION-PLAN.md`、
  `docs/phase-00-脚手架.md`：阶段治理状态。

产品业务、API、Flyway、依赖、前端和 exact 7 suites / 33 testcases 合同没有变化，未重复审计。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|---|---|---|---|
| Incremental | High | `1c9d416..ea760bf` 全部 10 个文件、3 个提交、逐函数 diff、提交链、tree 与 diff check | 未重复审计无变化的产品代码 |
| Testing Authenticity | High | Python 79 methods、发布专项 8 项、Surefire XML 32/274、9 模块 package | 两项 POSIX 用例在 Windows 跳过；未执行真实依赖 |
| Release | Medium | 私有发布状态机、威胁边界、HEAD/tree、治理材料、本机 r9 evidence 检索 | 无 r9 bundle，不能运行其离线 verifier；未执行 Compose/资源操作 |

## 3. Change Summary

- Total files changed: **10**
- Lines added: **543**
- Lines deleted: **118**
- Commits in range: **3**
- Authors: **wenbibuhaoqwq**

### Change Categories

| Category | Files | Notes |
|---|---:|---|
| Bug fix | 1 | `scripts/phase00_ci_gate.py` |
| Tests | 1 | `scripts/test_phase00_ci_gate.py` |
| Documentation / governance / previous review | 8 | 第八轮报告、第九轮材料与现行状态 |
| Dependency / schema / product configuration | 0 | 无变化 |

### Previous Finding Closure Matrix

| 原 finding | 第九轮状态 | 依据 |
|---|---|---|
| `P00-R3-M1` PASS 最终化可发布不同 artifact 集合 | **CLOSED** | 完整 canonical bundle 私有全量 verifier 后，仅以一次目录 rename 发布；rename 后无 final-path 操作 |
| `P00-R3-L1` POSIX root 非末段祖先未锚定 | **保持 CLOSED（代码/合同）** | 本轮未改祖先 fd 实现；最终 SHA 的 Linux 运行证据仍是阶段门禁 |

### Risk Delta

- 新增 finding：**0**
- 关闭：**1 Medium**
- 仍开放代码 finding：**0**
- 阶段 gate gap：最终 SHA 的 Linux 与真实依赖证据未形成。

### Test Coverage Delta

- Python 从 77 methods 增至 79 methods；稳定完整结果为 **77 PASS / 2 POSIX-only skipped**。
- checksum 后 mutation、manifest 后 delete 继续真实注入并 fail-closed。
- 目录 rename 失败真实命中发布调用，final 不出现。
- post-publish capture hook 未触发，证明成功后不再重开 final。
- 两个旧 marker mutate/delete 用例现在验证旧调用点已经消失，并重新校验最终 checksum；它们不是
  “mutation 实际发生”的新实现反例，不据此夸大测试覆盖。
- Java Surefire **32 suites / 274 tests**，0 failure/error/skip。

### Approval Recommendation

**Accept the remediation increment；keep Phase 0 at CHANGES_REQUESTED / EVIDENCE_PENDING。**
不要继续修改发布状态机；直接在最终 clean SHA 上补齐剩余动态门禁，再做一次只核对证据绑定的正式复核。

## 4. Top Risks

1. **Gate gap — `ea760bf` 尚无 Linux 79/79、真实 exact 7/33、离线 verifier、Compose 与资源清理证据。**
2. **Platform evidence limit — Windows 的 77/2 不能替代两个 POSIX ancestor 用例实际执行。**

以上均为发布/验收证据缺口，不是本冻结 diff 的代码 finding。

## 5. Detailed Findings

本冻结范围未发现可操作的 Critical、High、Medium、Low 或 Info finding。上一轮
`P00-R3-M1` 已按原失败条件关闭；剩余事项只属于 Phase 0 正式门禁。

## 6. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 79 项 Python gate test、8 项发布专项、32 份 Surefire XML、提交 diff。
- Exclusions / limits: Windows 不能执行两个 POSIX-only 用例；没有最终 SHA 的真实依赖 evidence。

Python 首次完整执行的产品断言全部通过，但 teardown 删除
`.phase00-gate-test-…/rematerialization-source` 时收到一次 Windows `WinError 32`，因此该次整体
exit 1。残留目录随后立即可安全删除；同一用例定向重跑 1/1 PASS，第二次完整执行
79 methods 为 **77 PASS / 2 skipped**，且没有临时目录残留。该用例与第九轮发布代码无关，
当前证据更符合 Git 子进程/文件系统的瞬时占用，不作为产品 finding；首跑事实仍保留在证据摘要中。

Maven offline `clean test` BUILD SUCCESS；逐份解析 Surefire XML 得到
**32 suites / 274 tests / 0 failure / 0 error / 0 skip**。随后
`mvn -B -ntp -o -DskipTests package` 为 **9/9 modules BUILD SUCCESS**，Checkstyle 0。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|---|---|---|---|
| 私有 bundle 物化与私有 verifier | High | 对 exact paths/bytes 与完整 manifest 合同做复核 | Keep |
| 最终目录发布 | High | 成功路径一次调用；rename 失败与 post-publish 不重开均有合同 | Keep |
| 旧 marker 回归 | Medium-High | 证明调用点消失；mutation 在新实现中不会实际发生 | 保留准确描述 |
| POSIX ancestor chain | High（静态合同） | Windows 未实际执行 syscalls | 最终 SHA 在 Linux 跑 79/79 |
| Java 离线回归 | High | 与 Python 发布状态机没有直接覆盖关系 | Keep |

### Valuable Tests

- `test_artifact_mutation_after_checksum_cannot_publish_pass`
- `test_artifact_delete_after_manifest_cannot_publish_pass`
- `test_private_pass_directory_rename_failure_never_exposes_pass`
- `test_pass_publication_does_not_reopen_final_directory`
- `test_mocked_success_finalizes_after_source_cleanup_and_ignores_print_failure`
- 两项 POSIX-only ancestor 反例

### Suspicious Tests

两个名称仍含 “before canonical marker”的用例在新实现中不会实际注入 mutation/delete；它们的
有效价值是确认旧 marker rename 已不存在并复验最终 checksum。提交材料若把它们描述为“新实现主动
拦住了 marker mutation”会夸大事实；当前正式报告按结构性回归记录，不构成 finding。

### Missing Tests

- 最终 clean SHA 的 Linux/POSIX 79/79，`skipped=0`。
- 全新隔离真实依赖上的 exact 7/33 与 evidence bundle 离线 verifier。

## 7. Release Concerns

- Coverage: Medium
- Inspected evidence: HEAD/tree、提交链、离线 test/package、治理文档、本机 r9 evidence 检索。
- Exclusions / limits: 按用户安全边界未执行 Docker、MySQL、Redis、MinIO、网络、服务、浏览器或
  Compose；没有 r9 bundle 可供离线 verifier。

本机未找到 `C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r9`，工作区与常见本机路径也未发现绑定
`ea760bf` / `fd2174f5…` 的 r9 bundle。已有 r7 bundle 绑定 `9b97741`，不能替代最终 SHA。

Phase 0 正式 PASS 前仍须：

1. Linux/POSIX Python **79/79**，`skipped=0`；
2. 全新隔离栈 exact **7 suites / 33 testcases**；
3. 对同一 r9 bundle 执行仓库离线 verifier；
4. `docker compose -f docker-compose.dev.yml config --quiet`；
5. 回传本轮专属容器、网络、数据卷的清理结果。

浏览器不要求。Phase PASS 仍不授权 merge、push、部署、切流或稳定发布；其后才进入用户要求的
最终全量审计。

## 8. Recommended Fix Order

### Fix Immediately

无代码整改。冻结 `ea760bf`，不要再改发布状态机。

### Fix Before Stable Release

1. 在 Linux 对最终 SHA 执行 Python 79/79。
2. 由用户/获授权环境生成最终 SHA 绑定的全新 exact 7/33 evidence。
3. 对该 bundle 执行离线 verifier，并补 Compose 与专属资源清理证据。
4. 做一次只核对上述证据绑定的最终 Phase 0 复核。

### Schedule Later

若未来要防御同账号恶意 peer writer、远程文件系统或断电持久化，先另行定义威胁模型；不在本阶段
预先引入 ACL、锁、`renameat2` 或不可变存储。

### Ignore for Now

不重复浏览器验证，不扩展产品、schema、依赖、前端或 exact suite 合同。

## 9. Quick Wins

- Linux 门禁与真实依赖 gate 使用同一最终 SHA，避免再次因材料提交改变 HEAD。
- evidence 生成后先运行仓库离线 verifier，再只归档 manifest、checksums、XML 与清理摘要。
- 保持消费者“完整校验 bundle，禁止只读 status”的文档措辞。

## Safety Boundary

本次只执行 Git/源码/材料读取、纯离线 Python 回归、Maven offline test/package、Surefire XML 汇总、
本机已知 evidence 路径检索与 diff check。没有执行 Docker、MySQL、Redis、MinIO、网络、HTTP
服务、浏览器、扫描、fuzz、凭据/权限或其它可能属于 cyber 的动作。首次 Python teardown 留下的
单个本轮临时目录在核对绝对工作区路径后删除，最终无残留。没有 stage、commit、merge、push 或
deploy。
