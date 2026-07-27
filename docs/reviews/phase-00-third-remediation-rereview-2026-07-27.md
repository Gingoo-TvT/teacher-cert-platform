# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 0 / U-004 third remediation
**Audit mode:** incremental / testing-authenticity / release / configuration / documentation / supply-chain / security
**Date:** 2026-07-27
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结第二轮报告归档基线
`5d3aa88abc7e596a5b98c402a0c4aff2894bbc3a`、第三轮产品/测试/门禁提交
`cc5786cc1c1ed1a243152bf306eca6998bbc0eee`，以及提交材料 HEAD
`30a76c8a52d053f39c1b974287434c1390e150ee`。完整增量为 2 个提交、19 个路径、
`+7869/-340`；其中实现提交为 14 个路径、`+7669/-323`。

正式结论：**CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 2 Low）**。

第二轮的 2 Medium / 3 Low 均已按原失败条件关闭：

- Maven 不再消费可切换的 live worktree；preflight 与 formal 分别从 expected commit 的
  Git tree/blob 物化，期间复核全部 path/mode/blob，并绑定 start/after-preflight/end HEAD。
- marker 已和 canonical datasource、Redis、MinIO 配置精确比较；独立 preflight、Spring
  initializer 与运行期 suite 又用 MySQL UUID、Redis run_id、MinIO identity/deployment ID
  及首次 `0/0/1/0` 空状态交叉绑定。
- Windows 路径静态边界、strict JSON、source/spec/artifact hash 和 exact artifact 集合的原缺口
  已实质补齐。
- Phase 清单已拆分 package `[x]` 与 fresh-schema context `[~]`。
- 参数基线已扩为 migration / README / IT 三方一致的 32/32 exact active set。

但 exact evidence gate 新增一个阻断性失败关闭问题：`self_verify_or_downgrade` 先把
`status=PASS` 的 manifest 和校验和写盘，再执行自校验，却只捕获 `GateError`。如果自校验阶段
遇到普通 `OSError` / `PermissionError`（例如 Windows 文件锁或并发删除），命令会异常失败，
磁盘仍可能保留并由 Actions `if: always()` 上传一个表面 PASS 的 evidence 包。独立内存级反例
稳定得到 `OSError PASS ['PASS']`。

另有两个 Low：证据路径 containment 仍是“检查后再打开”，同用户并发 reparse/symlink
替换存在 TOCTOU；MinIO `Server` / `x-minio-deployment-id` 响应头未经 redactor 原样进入身份
JSON，但 manifest 无条件声明 `containsSecrets=false`。

独立允许范围内的门禁全部通过：gate 离线反例 **60/60**、全量 Surefire
**32 suites / 274 tests**、9 模块 clean package、Checkstyle 0、前端
lint/type-check/build 与 `git diff --check`。这些结果证明候选离线质量，不证明真实依赖
exact **7 suites / 33 testcases** 已执行。

因此不要先运行当前候选的真实 7/33：先修复 1 Medium，并同轮处理 2 Low，再由用户在获授权的
一次性隔离 MySQL/Redis/MinIO 栈针对新的最终 clean SHA 执行动态门禁、Compose config 与必要
浏览器核验。Phase 0、U-004 与全项目继续保持 **CHANGES_REQUESTED**；不授权 merge、push、
部署、发布或最终全量审计。

遵照用户安全边界，本轮没有启动或连接 Docker、MySQL、Redis、MinIO、HTTP 服务、浏览器或
网络，没有执行扫描、fuzz、故障注入、凭据/权限、攻击性或破坏性动作。

### Score Dashboard

```text
Incremental     ████████░░  8.0  A   第二轮五项关闭；新增 evidence 失败状态问题
Testing         ████████░░  7.5  A   离线 334 项全绿；真实依赖 7/33 尚未执行
Release         ███████░░░  6.5  B   manifest 可在自校验异常后错误保留 PASS
Configuration   █████████░  8.5  A   marker/config/runtime identity 链代码级成立
Documentation   █████████░  8.5  A   [x]/[~]、32/32 与候选状态已对齐
Supply Chain    ████████░░  7.5  A   Git-object 构建显著改善；WS-7 仍待全量审计
Security        ████████░░  7.5  A   静态边界改善；TOCTOU 与 header 归档仍需收口
─────────────────────────────────────
Overall         ████████░░  7.7  A
```

Each dimension is scored 0.0–10.0. **Higher = better.** Overall is the
arithmetic mean of the seven dimensions, rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 1 | 0 |
| Low | 2 | 2 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **3** | **3** | **0** |

## 2. Project Map

- `scripts/phase00_ci_gate.py`：Git-object 候选快照、目标预检、Maven exact gate、
  evidence 生成与离线 verifier。
- `scripts/phase00_ci_gate_spec.json`：7 suite / 33 testcase、target evidence schema v3。
- `scripts/test_phase00_ci_gate.py`：60 个纯离线正反例。
- `Phase00TargetPreflight`：在正式 Spring/Flyway 前读取真实服务身份与首次空状态。
- `Phase00TargetGuardInitializer` + test `spring.factories`：每个正式 Spring context refresh
  前验证 resolved configuration 并重验服务身份。
- `Phase00ScaffoldIT` / `Phase00ParameterMatrixIT`：真实依赖验收、运行期身份与 32 项参数矩阵。
- `.github/workflows/ci.yml`：CI 服务身份采集、MinIO provisioning identity 与 exact gate 接线。
- `docs/phase-00-脚手架.md`、`docs/README.md`：验收状态与 32 项参数权威说明。
- `docs/CURRENT-EXECUTION-PLAN.md`、`PROGRESS.md`、`HANDOFF.md`、`DEVLOG.md`：
  当前正式状态与后续顺序。

本轮没有生产业务代码、Flyway 版本、权限编码、API 返回结构或前端用户流程变化；风险集中在
测试/发布证据门禁本身。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `5d3aa88..30a76c8` 两提交、19 路径、第二轮 finding 逐项关闭 | 未重开其它 Phase 业务正确性 |
| Testing Authenticity | High | 60 个 gate UT、7/33 spec 与方法集合、274 个 Surefire、preflight/initializer/runtime chain | 未执行 Failsafe 或真实依赖 |
| Release | High | immutable snapshot、HEAD window、manifest/self-verifier、workflow 上传条件 | 未触发 Actions、merge、push、deploy |
| Configuration | High | marker/env parser、Spring resolved properties、Flyway alternate route、目标身份 | 未连接任何运行目标 |
| Documentation | High | Phase 0、README、submission、统一计划、进度、交接 | 历史报告只用于 finding closure |
| Supply Chain | Medium | Git object provenance、离线 Maven/npm、workflow 引用 | 未联网查 CVE、SBOM、签名或 attest |
| Security | Medium | path/reparse、strict JSON、secret redactor、identity evidence | 未做 race harness、扫描、fuzz 或攻击性验证 |

## 3. Incremental Change Summary

### Change Summary

- Baseline: `5d3aa88abc7e596a5b98c402a0c4aff2894bbc3a`
- Product/test/gate commit: `cc5786cc1c1ed1a243152bf306eca6998bbc0eee`
- Reviewed material HEAD: `30a76c8a52d053f39c1b974287434c1390e150ee`
- Total files changed: 19
- Lines added: 7869
- Lines deleted: 340
- Commits in range: 2
- Remote: none

### Previous Finding Closure Matrix

| 第二轮 finding | 第三轮变化 | 独立结论 |
|---------------|------------|----------|
| P00-R2-M1：运行结束未重新绑定候选 HEAD | Git object 双快照、三点 HEAD、tree/manifest/spec attestation | **关闭** |
| P00-R2-M2：marker 未绑定真实连接目标 | marker/config exact compare + preflight/initializer/runtime 身份链 | **关闭** |
| P00-R2-L1：Windows/source/spec 边界不完整 | 静态路径、strict JSON、candidate/live/archive spec、artifact provenance | **原失败条件关闭；新增 TOCTOU Low** |
| P00-R2-L2：应用 context 提前 `[x]` | package `[x]` 与 fresh context `[~]` 拆分 | **关闭** |
| P00-R2-L3：参数矩阵 24/32 | migration / README / IT 32/32 exact active set | **关闭** |

### Risk Delta

- Previous findings fully closed: 5
- New blocking findings: 1 Medium
- New non-blocking findings: 2 Low
- New Critical / High: 0
- Product-code regression found: 0
- Phase gate: **CHANGES_REQUESTED**

### Test Coverage Delta

- Exact dynamic gate declaration: 7 suites / 33 testcases
- Gate unit tests: 60/60 independently replayed
- Full independent offline Surefire: 32 suites / 274 tests
- Backend package: 9/9 modules; Checkstyle 0
- Frontend: lint / type-check / production build PASS
- `git diff --check`: PASS
- Dynamic evidence on reviewed final HEAD: **not executed**
- Docker Compose config: **not executed**
- Browser validation: **not executed**

### Approval Recommendation

**Request changes.**

先修 P00-R3-M1，并同轮收口 P00-R3-L1/L2。修复后的纯离线反例必须证明：任何自校验异常、
进程非成功返回或证据竞态都不能在磁盘留下 PASS；身份 artifact 也不得无条件声称无秘密。
之后才由用户在全新隔离依赖栈针对新的最终 SHA 执行一次 exact 7/33，避免当前候选先跑后又重做。

## 4. Top Risks

1. **Medium — 自校验异常后保留 PASS manifest：** 命令失败与磁盘 evidence 状态相互矛盾。
2. **Low — containment check/open 竞态：** 静态 reparse 被拒绝，但检查后替换仍可先触碰边界外路径。
3. **Low — 身份 header 未脱敏：** 原样归档远端响应元数据，却无条件声明 evidence 不含秘密。
4. **Required gate gap — exact 7/33 未执行：** 即使修复代码 finding，也不能据离线结果判 Phase PASS。

## 5. Detailed Findings

### Finding: 自校验的非 GateError 会留下表面 PASS 的 evidence

- ID: P00-R3-M1
- Severity: Medium
- Confidence: High
- Category: Release / Testing Authenticity / Provenance
- Status: Confirmed
- Affected area: `scripts/phase00_ci_gate.py` evidence finalization
- Evidence:
  - File: `scripts/phase00_ci_gate.py:3287-3313`
  - Function / Module: `self_verify_or_downgrade`
  - Relevant behavior: 先 `persist_manifest(... status=PASS)`，随后只捕获 `GateError`。
  - File: `scripts/phase00_ci_gate.py:251-256,4220-4266,4280-4383,4773`
  - Relevant behavior: verifier 的文件打开、hash、stat、`read_bytes` 可直接抛普通 `OSError` /
    `PermissionError`。
  - File: `scripts/phase00_ci_gate.py:5041-5048`
  - Relevant behavior: 顶层同样只把 `GateError` 转换为受控 FAIL。
  - Safe independent proof: 将 `verify_evidence` 替换为抛 `OSError("locked")` 的内存 stub，
    `self_verify_or_downgrade` 输出 `OSError PASS ['PASS']`；没有访问服务或外部文件。
- Problem: 函数注释承诺“never leave a false PASS manifest behind”，但异常类型边界使该承诺不成立。
  进程非零退出时，磁盘可留下最后一次成功写入的 PASS manifest 和 `SHA256SUMS`。
- Why it matters: workflow 对 evidence 使用 `if: always()` 上传；手工复核也可能只交 evidence
  directory。命令失败但 manifest PASS 会让自动聚合器或只看摘要的人误判门禁状态。
- Realistic failure scenario: Windows 杀毒/索引器短暂锁住 XML，或另一个进程在自校验阶段删除文件；
  hash/read 抛 `PermissionError` / `FileNotFoundError`，gate 失败退出，已写的 PASS 仍被上传。
- Minimal fix: PASS 只能在全部自校验成功后原子发布。先写 `PENDING`/临时 manifest，在 staging
  目录完成校验；任何 `Exception` 都尽力原子落 FAIL，成功后再以 `os.replace` 发布最终 PASS。
- Better long-term fix: evidence 目录采用一次性 staging → fully verified → read-only finalized
  状态机；manifest 增加 `finalized=true`，上传步骤只接受 verifier 成功后的 finalized 包。
- Regression test suggestion: 分别注入 `OSError`、`PermissionError`、校验中删除 artifact 与最终
  manifest 原子替换失败；断言命令非成功时磁盘绝不存在 `status=PASS`。
- Estimated effort: 2–4 hours

### Finding: evidence containment 仍存在检查后打开的 reparse TOCTOU

- ID: P00-R3-L1
- Severity: Low
- Confidence: High
- Category: Security / Supply Chain / Testing Authenticity
- Status: Confirmed
- Affected area: 不可信 evidence directory 的离线 verifier
- Evidence:
  - File: `scripts/phase00_ci_gate.py:1766-1802`
  - Function / Module: `resolve_contained_path`
  - Relevant behavior: 逐段检查 link/reparse 并 `resolve` 后返回路径，但没有持有目录/file handle。
  - File: `scripts/phase00_ci_gate.py:984-1024,4220-4266,4280-4383`
  - Relevant behavior: 调用者稍后再以路径执行 `read_bytes`、hash、stat；检查和实际打开不是同一原子操作。
  - File: `scripts/test_phase00_ci_gate.py`
  - Relevant behavior: 现有 reparse 反例覆盖静态 reparse，没有覆盖检查通过后的父目录/文件替换。
- Problem: 同用户并发进程可以在 containment 检查与后续打开之间替换父目录或文件为
  junction/reparse/symlink。最终可能因 identity/hash 漂移而失败，但 verifier 已先读取边界外路径。
- Why it matters: 离线 verifier 的输入可能是下载或外部交回的 evidence 包；安全边界应在读取前
  成立，而不是依赖读取后的 hash/目录闭合校验。
- Realistic failure scenario: evidence 位于可并发修改目录，验证过程中某一父目录被替换为 junction；
  后续 hash/read 跟随新目标，先触碰 evidence root 外文件再报错。
- Minimal fix: 使用 handle/descriptor anchored、no-follow 的读取方式，并在 handle 上验证最终路径、
  file ID 与类型；至少对整个父链做 before/after identity 复核并拒绝任何变化。
- Better long-term fix: 把下载证据先复制到由 verifier 独占、权限收紧的 staging 目录，再基于
  handle 验证并只读封存。
- Regression test suggestion: 由测试协调器在 containment 返回后、打开前替换父目录；断言 verifier
  在读取替代目标前失败。本轮按用户要求没有执行该竞态 harness。
- Estimated effort: 0.5–1 day

### Finding: 原样归档的 MinIO 响应 header 未纳入无秘密证明

- ID: P00-R3-L2
- Severity: Low
- Confidence: High
- Category: Security / Evidence Hygiene
- Status: Confirmed
- Affected area: preflight/runtime target identity evidence
- Evidence:
  - File: `Phase00TargetPreflight.java:397-409,490-499`
  - Relevant behavior: `Server` 与 `x-minio-deployment-id` 响应头写入身份 JSON。
  - File: `Phase00ScaffoldIT.java:551-555,616-618`
  - Relevant behavior: 运行期再次采集并写入同两项 header。
  - File: `scripts/phase00_ci_gate.py:1272-1275,1433-1439`
  - Relevant behavior: Python 只要求短 clean text / deployment ID 非空，没有限定身份格式或执行
    known-secret 检查。
  - File: `scripts/phase00_ci_gate.py:3591-3601,3832-3842,4085-4090`
  - Relevant behavior: 两份 identity JSON 原样归档且没有 redactions，manifest 无条件声明
    `containsSecrets=false`。
- Problem: 远端或代理响应头是外部输入；宽松文本可包含 credential-like 内容。日志 redactor 的
  保护没有覆盖这两份原样 identity artifact。
- Why it matters: evidence 会被上传并保留 30 天。即使标准 MinIO 返回值通常无秘密，manifest
  也不能在未验证 artifact 内容时绝对声明 `containsSecrets=false`。
- Realistic failure scenario: 错误代理或非标准 MinIO 端点把请求中的 access-key 标识或其它敏感
  元数据反射到 `Server`/deployment header，随后被归档。
- Minimal fix: 将 deployment ID 限定为预期 UUID/hex 格式；`Server` 使用窄 allowlist 或不归档；
  两份 identity artifact 在写入前执行 known-secret/credential-pattern 扫描或安全脱敏。
- Better long-term fix: identity schema 只保留证明服务连续性所需的规范化、非秘密字段；以一个受控
  service-instance fingerprint 替代任意响应 header。
- Regression test suggestion: 注入 credential-like、超长和控制字符 header，断言拒绝或脱敏，
  且 `containsSecrets=false` 只在 artifact 扫描通过后写入。
- Estimated effort: 1–2 hours

## 6. Testing Authenticity Analysis

### Confidence Assessment

| Evidence | Confidence | Reason |
|----------|------------|--------|
| gate UT 60/60 | High | 本轮独立纯离线重跑，正反例实际执行 |
| Surefire 274/274 | High | `clean test` 后立即从 32 份新鲜 XML 汇总，0 failure/error/skip |
| package / frontend | High | 本轮顺序执行，自然退出并成功 |
| exact 7/33 declaration | High for design | spec 与七个类的 `@Test` 方法集合静态精确一致 |
| target identity chain | High for code design | preflight → initializer → runtime suite 三层交叉绑定 |
| final real-dependency evidence | Not established | reviewed HEAD 没有 preflight/Failsafe/runtime identity evidence |
| evidence finalization | Low until fixed | 非 `GateError` 会让进程失败与 manifest PASS 并存 |

### Valuable Tests

- Git-object snapshot 测试覆盖 clean HEAD 漂移、candidate blob、spec drift、preflight/formal
  重物化与源码变更拒绝。
- target parser 测试覆盖 marker/config 不一致、relaxed-binding aliases、URI 歧义与身份漂移。
- `Phase00TargetGuardInitializerTest` 的 8 个纯单测锁定普通运行无副作用、正式配置匹配、
  alternate route fail-closed 和 `0/0/1/0` freshness schema。
- `Phase00ParameterMatrixIT` 查询完整 active set，而不是按 32 个已知 key 过滤，因此 unknown/missing
  均会失败。

### Missing Tests

- 自校验抛普通 I/O 异常时不得留下 PASS。
- staging/PENDING → finalized PASS 的原子状态转换。
- containment 检查后父链发生 reparse/symlink 替换。
- 身份 header credential-like 输入的拒绝/脱敏。
- 修复后最终 SHA 的真实 preflight + exact 7/33。
- Compose config 与必要的非生产浏览器证据。

Surefire 的 `flakyFailure` / `rerunFailure` 节点当前未显式拒绝；本仓库 POM 未启用 rerun，
故本轮不列当前 finding。建议在后续门禁硬化时补为防御性反例。

## 7. Release Concerns

- 当前有 1 个 Medium，不能先生成真实依赖 PASS 包；否则修门禁后仍需针对新 SHA 重跑。
- reviewed HEAD `30a76c8` 只有离线证据；没有动态 preflight/runtime identity、7/33 XML、
  Compose 或浏览器证据。
- 仓库无 remote，本轮没有可读取的 GitHub Actions 运行；workflow 只作为配置审查对象。
- 没有 merge、push、deploy、rollback、生产变更或稳定发布授权。
- 即使后续 Phase 0 PASS，也只放行最终全量审计，不等于 release GO。

## 8. Configuration Safety Analysis

- marker 与 canonical config 的结构化比较成立；MySQL schema、Redis DB、MinIO bucket/endpoint
  任一漂移都失败。
- `SPRING_APPLICATION_JSON`、config import、JNDI、Hikari alternate route、Redis URL/
  sentinel/cluster、外部 Flyway/SQL-init 与 Maven/Java override channel 被拒绝。
- Spring initializer 在 context refresh 前比较 resolved properties，并在同一 Failsafe JVM
  首次采集 freshness、后续重验身份。
- “是否为一次性隔离宿主”仍需执行者选择和销毁证据；代码能证明目标一致与首次为空，不能从
  服务 UUID 单独证明宿主所有权。

## 9. Documentation Analysis

- package `[x]` 与 fresh-schema context `[~]` 已正确拆分。
- README、IT 与 migration 的 32 项参数基线一致。
- submission、PROGRESS、HANDOFF 与统一计划均明确第三轮只是候选、7/33 未运行。
- 本报告优先于第三轮提交材料的整改者自述；历史第二轮报告保持原结论，不改写时间线。
- 活动文档应更新为第三轮正式 `CHANGES_REQUESTED（1 Medium / 2 Low）`，下一步改为先整改门禁，
  不再要求用户立即运行当前 SHA。

## 10. Supply Chain / Reproducibility Analysis

- 从 expected commit 的 Git objects 构建，且 preflight/formal 使用两次独立物化，显著提升
  candidate provenance。
- Maven `-o` 单测/package 与已安装依赖下的 npm scripts 可重复通过。
- Actions、MySQL/Redis image tag、MinIO/mc image 仍未固定 digest，属于既有 WS-7/
  最终全量审计边界，不作为本轮新增 finding。
- 未联网执行依赖漏洞扫描、SBOM、签名或二进制 attestation。

## 11. Security Concerns

- 正向：目标配置和真实身份双绑定，降低误写共享环境的概率。
- 正向：静态 Windows/reparse、strict JSON、artifact provenance 与 source/spec hash 已明显加强。
- 待修：自校验异常必须失败关闭到磁盘状态，而不只是进程退出码。
- 待修：不可信 evidence 的 containment 应在 handle 层成立。
- 待修：外部响应 header 不能绕过 artifact 的无秘密声明。
- 本轮没有确认新的产品权限、认证、数据范围或敏感业务 High/Critical。

## 12. Principles Compliance

### Principles Violated

- **Fail closed in durable state：** 自校验异常时退出码失败，但持久 manifest 仍可 PASS。
- **Boundary before access：** path containment 与实际文件打开不是同一受保护操作。
- **Evidence before assertion：** 未扫描身份 artifact，却绝对声明 `containsSecrets=false`。

### Principles Respected

- **Build what you attest：** Git-object snapshot、tree/blob manifest 与三点 HEAD 关闭了 live
  worktree 漂移。
- **Bind labels to facts：** marker、resolved configuration 与服务身份已形成三层交叉证明。
- **No candidate-as-PASS：** 动态 7/33、Compose/browser 均保持未执行。
- **Single source of truth：** 参数 32/32、Phase `[x]/[~]` 和统一执行计划口径一致。
- 严格遵守用户的非 cyber、非破坏与不触碰外部服务边界。

## 13. Recommended Fix Order

### Fix Immediately

1. P00-R3-M1：使用 staging/PENDING 和原子发布，任何异常/非成功返回都不得留下 PASS。

### Fix in the Same Remediation

2. P00-R3-L1：将 evidence 读取改为 handle/descriptor anchored no-follow，或独占 staging 后验证。
3. P00-R3-L2：收窄身份 header schema，并让 identity artifact 经过无秘密验证/脱敏。
4. 增加上述失败分支的纯离线反例；建议同时拒绝 Surefire rerun/flaky 节点。

### Run Only After the Fix

5. 用户在获授权的一次性隔离栈针对新的最终 clean SHA 执行 preflight + exact 7/33。
6. 用户补 Compose config、必要浏览器结果与容器/网络/卷销毁证据。
7. 独立复核新代码增量及完整 evidence；通过后才可把 Phase 0 置 PASS。

### Schedule Later

- WS-7：Action 完整 SHA、镜像 digest、SBOM、依赖审计、artifact signing/provenance。
- 从机器可读 manifest 自动生成 Phase checklist，减少手工状态同步。

## 14. Quick Wins

| Item | Effort | Impact |
|------|--------|--------|
| `OSError` / `PermissionError` 后无 PASS 反例 | <1 hour | 立即锁住本轮 Medium 的原失败条件 |
| PENDING → atomic PASS finalization | 2–4 hours | 消除退出码与 evidence 状态矛盾 |
| deployment ID 格式与 Server allowlist | 1–2 hours | 收口 identity artifact 外部输入 |
| rerun/flaky XML 节点拒绝 | <1 hour | 防止未来启用重跑后假绿 |

## 15. Long-term Refactor Plan

1. evidence 生成采用 staging、状态机、原子 finalized manifest 和只读封存。
2. verifier 使用目录 handle/no-follow API，而不是路径字符串的 check-then-open。
3. target identity schema 只保存规范化服务指纹，不保存任意响应 header。
4. 在受控 runner 对 evidence 做签名/attestation，并绑定 workflow、toolchain、commit/tree/spec。
5. 由 evidence manifest 自动更新 Phase 状态；人工文档只解释边界和例外。

---

**Evidence:** `docs/reviews/evidence/phase00-third-remediation-rereview-2026-07-27/`
**Verdict:** `CHANGES_REQUESTED`
**Next gate:** 先整改 1 Medium / 2 Low；不要运行当前 SHA 的真实 7/33。修复后再由用户执行新最终 SHA 的隔离动态门禁。
