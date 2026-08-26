# 最终审计 R7 正式冻结包独立增量复核（2026-07-29）

**Project:** teacher-cert-platform  
**Audit mode:** incremental / testing-authenticity  
**Date:** 2026-07-29  
**Reviewer:** Codex GPT-5

> 本报告只复核最终全量审计结束后的 R7 证据整改，不重新执行或改写最终全量审计。
>
> 本轮唯一正式冻结对象为
> `final-audit-r7-freeze-raw/final-audit-r7.bundle`。原
> `final-audit-r7-freeze/final-audit-r7.bundle` 只作为失败反例，明确排除在全部正式验收结果、
> 计数和结论之外。
>
> 本轮范围结论：**PASS（0 Critical / 0 High / 0 Medium / 0 Low）**。

---

## 1. Executive Summary

- 正式 bundle SHA-256 为
  `3c0bbce56f3c6df3f85a9f15b514366b9b259ade6a6478fc27ed75e0942a121d`，唯一 ref
  `refs/tags/final-audit-r7-freeze-raw` 指向 commit
  `a66eda76e4d6257a16311d478cb6939c7066ce32`，tree 为
  `321a30931b354dc3757f5ab438626801f3cc3fc2`，parent/base 为
  `aa3509a19151e2caad03014324b9b36e98bc0f05`。
- `git bundle verify`、完整历史检查、`git fsck --full --strict` 与 pack 校验均通过；正式冻结包
  没有缺失对象。
- 从该 bundle fresh 重建 dirty candidate 后，重新 capture 的 manifest 与执行证据中的 manifest
  逐字节一致：均为 18,979 bytes，SHA-256 均为
  `b409d5fe8dbba8069af041fd7e479a7c1068d9a0bdb3c6d96f6a914cb105dd74`。候选指纹为
  `0f9f2fef22ad88e2e6e58640b5a2d6c289fdf48f807ee68feabcd216c208bf6d`，
  HEAD `aa3509a...`、82 个 tracked 改动、99 个 untracked 文件及 tracked diff
  `26aa9d7221b283e8a8c26803368e1037be1b64fc70d35194f619c6f67972802b`
  全部一致。
- 外部 `final-audit-r7` 目录只作为该唯一冻结对象的运行证据包，不是第二个冻结候选。其
  `SHA256SUMS` 11/11 复算一致，完整时间链为：
  capture → Phase 2 → verify → Phase 41 → verify → 证据归档 → raw bundle 冻结。
- Phase 2 selector 精确为 `Phase2SecurityIT`，4/4；Phase 41 selector 精确为
  `Phase41BackupIT`，1/1 且正式脚本输出 PASS；两组均为 0 failure / 0 error / 0 skip。
- 第六轮遗留的“完整候选未绑定真实门禁”与“关键日志不可移植”两个 Medium 均按原失败条件
  `CLOSED`，本轮未发现新问题。
- 该 R7 增量 PASS 不等于项目稳定发布 GO。最终全量审计基线的其它 OPEN/PARTIAL 项和
  **CHANGES_REQUESTED / NO-GO** 状态不被本报告改写，也不授权 merge、push、deploy 或切流。

### Score Dashboard

| Dimension | Score / 10 | 结论 |
|---|---:|---|
| Frozen candidate identity | 9.8 | bundle、commit、tree、parent 与完整对象闭包成立 |
| Manifest round-trip | 9.8 | fresh capture 与门禁 manifest 逐字节一致 |
| Testing authenticity | 9.5 | selector、XML、日志、前后 verify 与时间链闭合 |
| Evidence portability | 9.5 | `.log.txt`、XML/TXT、manifest 与工具输出均由 11 项清单覆盖 |
| Scope discipline | 10.0 | 旧 bundle 仅作失败反例，未混入正式结果 |
| **Scoped overall** | **9.7** | **R7 证据整改可独立 PASS** |

### Finding Statistics

| Severity | Open | New |
|---|---:|---:|
| Critical | 0 | 0 |
| High | 0 | 0 |
| Medium | 0 | 0 |
| Low | 0 | 0 |
| Info | 0 | 0 |

## 2. Incremental Change Summary

### Change Summary

- 本轮候选冻结与证据归档没有新增生产 Java/Vue 行为。
- 正式 raw bundle 把 82 个 tracked 改动和 99 个原始字节 untracked 文件固化为单一 Git
  对象图。
- 两项真实 exact gate 在同一完整候选 manifest 下执行，并在每项之后复算原 manifest。
- 两份规范运行日志采用 `.log.txt`，与 XML/TXT、工具输出和 manifest 一起进入闭合校验清单。

### Risk Delta

- Candidate-binding Medium: `CLOSED`。
- Evidence-portability Medium: `CLOSED`。
- New production regression: 0。
- New evidence finding: 0。

### Approval Recommendation

**Approve this R7 increment only.** 不扩大为项目级发布授权。

## 3. Project Map and Review Boundary

| 对象 | 角色 | 正式验收处理 |
|---|---|---|
| `final-audit-r7-freeze-raw/final-audit-r7.bundle` | 唯一正式冻结源码对象 | **纳入** |
| `final-audit-r7/` | manifest-bound 运行证据包 | **纳入为旁证，不是冻结候选** |
| `final-audit-r7-freeze-raw/FREEZE.txt` | 人读摘要 | 辅助核对，不替代 bundle |
| `final-audit-r7-freeze-raw/candidate-ignore-rules.txt` | 重建辅助 | 非正式候选；无该规则重捕获仍得到同一 manifest |
| `final-audit-r7-freeze/final-audit-r7.bundle` | 原始失败反例 | **明确排除** |

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|---|---|---|---|
| Bundle identity | High | SHA-256、ref、commit/tree/parent、verify/fsck/pack | 不接受旧 bundle |
| Candidate binding | High | fresh mixed-reset、capture、external verify、逐字节 manifest 比较 | 文件模式不在 manifest 模型内 |
| Dynamic evidence | High | 三次工具输出、两份完整日志、两份目标 XML/TXT、summary | Codex 未重跑真实依赖 |
| Offline regression | High | manifest 6/6、Maven Surefire 50 suites / 349 tests | 不替代真实 Failsafe |
| Security/cyber | Not assessed | 仅做本地文件、Git 与自然退出离线检查 | 未执行扫描、攻击、网络或服务操作 |

## 4. Previous Finding Closure Matrix

| 上轮问题 | Severity | 本轮状态 | 关闭依据 |
|---|---:|---|---|
| 完整候选指纹尚未贯穿两个真实 exact gate | Medium | `CLOSED` | 同一 manifest 在门禁前 capture、两门禁后 verify；raw bundle 重建 manifest 逐字节相同 |
| 两份关键日志无法由可移植冻结对象取得 | Medium | `CLOSED` | `.log.txt` 已进入 raw bundle 的 99 个原始字节文件，外部证据 11/11 哈希闭合 |

## 5. Findings and Non-blocking Limits

本轮没有 Critical、High、Medium 或 Low finding。

### Top Risks

本 R7 范围没有未关闭风险。项目级最高风险仍来自最终全量审计的其它 OPEN/PARTIAL 项，但它们
不在本次增量复核范围内，也未被本轮 PASS 降级或关闭。

### Detailed Findings

无新增 finding。第六轮遗留的两个 Medium 已在上节 Closure Matrix 中按原失败条件关闭。

非阻断边界：

- manifest 不绑定文件模式，raw freeze 对新增文件统一使用 `100644`。当前相关脚本均通过解释器
  调用，没有确认依赖 executable bit 的正式门禁路径。
- Windows 深路径环境宜启用 `core.longpaths=true` 或使用短临时目录；这是 checkout 环境限制，
  不是 bundle 对象缺失。
- 本地未签名证据不防御同一安全主体在运行中替换并恢复源码、再伪造整套日志和摘要。该假设超出
  本阶段既定威胁模型；本轮不引入 ACL、签名服务或不可变存储。

## 6. Testing Authenticity

### Evidence chain

| 时间（UTC+8） | 事件 | 结果 |
|---|---|---|
| 19:55:47 | candidate manifest capture | PASS，fingerprint `0f9f2fef...bf6d` |
| 19:57:23 | `Phase2SecurityIT` 完成 | BUILD SUCCESS，4/4 |
| 19:58:04 | 同一 manifest verify | PASS |
| 19:59:02 | Phase 41 正式脚本完成 | BUILD SUCCESS，1/1，脚本 PASS |
| 19:59:23 | 同一 manifest verify | PASS |
| 19:59:41 | 证据包与 `SHA256SUMS` 完成 | 11/11 |
| 20:32:25 | raw freeze commit / bundle | `a66eda76...` |

### Exact suite verification

| Suite | Selector | Tests | Failures | Errors | Skipped |
|---|---|---:|---:|---:|---:|
| `cn.edu.gpnu.platform.boot.Phase2SecurityIT` | `Phase2SecurityIT` | 4 | 0 | 0 | 0 |
| `cn.edu.gpnu.platform.boot.Phase41BackupIT` | `Phase41BackupIT` | 1 | 0 | 0 | 0 |

Phase 2 XML 中四个 testcase 均可在正式冻结源码定位；Phase 41 XML 的单一恢复用例及超限失败关闭
路径也与正式源码一致。`failsafe-summary.xml` 的 `completed=1` 来自顺序执行后的最后一个
Phase 41 命令，未被误解为两次命令的合并计数。

## 7. Independent Gate Results

| Gate | Result |
|---|---|
| Formal bundle SHA-256 | PASS；`3c0bbce5...a121d` |
| `git bundle verify` / full strict fsck / pack | PASS |
| Bundle-only fresh reconstruction | PASS；82 tracked / 99 untracked |
| Fresh manifest vs external manifest | PASS；18,979 bytes，SHA-256 `b409d5fe...dd74`，逐字节相同 |
| External evidence `SHA256SUMS` | PASS；11/11 |
| Manifest tool tests | PASS；6/6 |
| Offline Maven Surefire | PASS；50 suites / 349 tests，0 failure/error/skip |
| `git diff --check` | PASS |
| Docker / DB / Redis / MinIO / network / service / browser | Not executed |

## 8. Explicit Negative Control Exclusion

原失败对象
`final-audit-r7-freeze/final-audit-r7.bundle` 的 SHA-256 为
`6fbd4dc4b85b929f932aefc210f0e3bc01e08b4c0c6678b2f1543f3bb3ab6319`，
其 ref 指向 `8e5f6792f73bd70442655e7c93ee710420b021ac`。

它只用于确认失败条件：对正式 manifest 执行 verifier 时 exit 1，期望指纹
`0f9f2fef...bf6d`，观察指纹为 `f9cac527...f7fb`。该对象及其任何测试或文件均未计入正式
bundle 身份、11/11 证据、4/4、1/1、349/349 或本轮 PASS。

## 9. Engineering Principles

- **Single source identity:** 只接受一个 raw bundle 作为冻结候选。
- **Fail closed:** HEAD、tracked diff、untracked 路径或原始字节变化均使 manifest verify 失败。
- **Independent reproduction:** 从 bundle 反向重建候选，而不是只采信生成者摘要。
- **Evidence proportionality:** 使用 manifest、哈希、XML 和完整日志闭环，不增加本阶段不需要的
  签名或存储基础设施。
- **Honest release boundary:** 增量证据 PASS 不冒充项目级最终发布 GO。

## 10. Recommended Fix Order and Quick Wins

本 R7 增量没有剩余整改项。无需继续修改生产逻辑、证据工具或冻结格式。

### Recommended Fix Order

本 R7 范围无修复顺序；保持冻结即可。项目其它整改应继续按统一计划中的现有优先级单独处理。

### Quick Wins

无需新增实现。最小治理动作仅为保留正式 raw bundle、对应运行证据和本报告，避免再生成含义相近
但身份不清的第二个冻结对象。

后续只应：

1. 保留正式 raw bundle 及其当前证据目录；
2. 在治理文档中记录 R7 scoped PASS；
3. 将最终全量审计其它 OPEN/PARTIAL 项作为独立后续工作，不与本结论混算。

## 11. Final Verdict

**R7 独立增量复核：PASS。**

- 0 Critical / 0 High / 0 Medium / 0 Low；
- 上轮 2 个 Medium 全部关闭；
- 唯一正式冻结对象为
  `final-audit-r7-freeze-raw/final-audit-r7.bundle`；
- 原 `final-audit-r7-freeze` 只保留为失败反例并明确排除；
- 项目最终全量审计的其它风险与 **CHANGES_REQUESTED / NO-GO** 基线不变；
- 不授权 merge、push、deploy、切流或稳定发布。
