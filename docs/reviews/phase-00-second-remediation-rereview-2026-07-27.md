# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 0 / U-004 second remediation
**Audit mode:** incremental / testing-authenticity / release / configuration / documentation / supply-chain / security
**Date:** 2026-07-27
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结首轮复核归档基线
`8e3217da5f39f55f8146fa6d4e638adf8406914f`、第二轮产品/测试/门禁提交
`3bfe83d9eaa4486bb39cc89fc3f05a975319e3af`，以及提交材料 HEAD
`c426480e718333ba864c2753c0a6c5d28b971221`。完整增量为 2 个提交、30 个路径、
`+2903/-145`。

正式结论：**CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 3 Low）**。

第二轮候选有实质进展，首轮 3 个 Medium 的原失败条件全部关闭：

- 通用全局摘要秒传合同已按 R10 正式退役；普通同字节上传真实执行两次，并明确形成两个对象、
  两行元数据。Phase 7 的业务内受信 fast-hit 合同不受影响。
- exact gate 已锁定 6 个 suite / 25 个 testcase，删除旧报告并逐类名、方法名、计数、
  fail/error/skip、XML 新鲜度与 Failsafe summary 校验，关闭“目标 suite 可静默消失”。
- npm、真实认证、最小 Checkstyle/ESLint、Phase 0 复核退回和 WS-6 剩余范围已在活动权威文档
  对齐。

首轮 prod 静态文档面与 DataScope 测试层级 Low 也已关闭：prod 通过安全链前置 404 拒绝
OpenAPI/UI/webjar 路径；DataScope 新测试贯穿真实 Aspect、Context、MyBatis Mapper proxy、
生产 DataPermission/Pagination 插件与 count/data SQL。

但本轮新增的证据门禁仍不能证明“这批动态结果属于这个最终提交和这个隔离目标”：

1. gate 只在 Maven 前读取一次 HEAD，结束时只检查 clean worktree；clean HEAD 在运行中漂移时，
   manifest 仍会声明开始时的候选 SHA。
2. MySQL/Redis/MinIO target marker 只做文本格式检查，没有和实际 datasource/Redis/MinIO
   连接变量或运行时服务身份绑定。手工执行时可把另一个目标误标成隔离栈。

另有 3 个 Low：证据 verifier 的来源字段和 Windows 路径边界不完整；Phase 清单把尚未运行的
应用上下文启动合并标为 `[x]`；所谓“全部参数”矩阵只覆盖当前 fresh-schema 32 项中的 24 项。

独立允许范围内的离线门禁全部通过：gate 自测 **21/21**、聚焦回归 **18/18**、全量 Surefire
**266/266**、9 模块 package、Checkstyle、前端 lint/type-check/build 与 `git diff --check`。
这些结果证明当前源码的离线质量，但不替代尚未执行的真实依赖 exact **6/25**。

因此应先修复证据门禁，再由用户在获授权的一次性隔离 MySQL/Redis/MinIO 栈执行最终 HEAD 的
动态门禁并交回证据；当前不宜先跑旧 gate，以免得到仍需重做的证据。Phase 0 和全项目继续
保持 **CHANGES_REQUESTED**，不授权 merge、push、部署、发布或最终全量审计。

遵照用户安全边界，本轮没有启动或连接 Docker、MySQL、Redis、MinIO、HTTP 服务、浏览器或
网络，没有执行扫描、fuzz、故障注入、凭据、权限、攻击性或破坏性动作。

### Score Dashboard

```text
Incremental     ███████░░░  7.0  A   首轮阻断关闭，证据实现新增两项阻断
Testing         ███████░░░  7.0  A   离线 266/266；真实依赖 6/25 尚未执行
Release         ██████░░░░  6.0  B   SHA 与 target provenance 尚不可靠
Configuration   ████████░░  8.0  A   prod docs 已收口；动态目标仍待实证
Documentation   ███████░░░  7.0  A   活动口径统一，清单/参数范围仍有两处过度
Supply Chain    ████████░░  7.5  A   可重复离线构建；WS-7 既有债不在本轮扩张
Security        ████████░░  7.5  A   产品安全面改善；verifier 路径边界需收紧
─────────────────────────────────────
Overall         ███████░░░  7.1  A
```

Each dimension is scored 0.0–10.0. **Higher = better.** Overall is the
arithmetic mean of the seven dimensions, rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 2 | 2 | 0 |
| Low | 3 | 3 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **5** | **5** | **0** |

## 2. Project Map

- `.github/workflows/ci.yml`：真实 MySQL/Redis/MinIO CI 服务与 Phase 0 exact gate 接线。
- `scripts/phase00_ci_gate.py`：候选、工作区、suite、证据、target marker 与 manifest 门禁。
- `scripts/phase00_ci_gate_spec.json`：6 suite / 25 testcase 的机器可读精确集合。
- `scripts/test_phase00_ci_gate.py`：gate 的 21 个纯离线正反例。
- `platform-file`：普通上传、direct/Phase 7 文件合同及同字节上传回归。
- `platform-security` / `platform-boot`：prod docs 安全链、Phase 0 真实依赖 IT、
  参数矩阵和 DataScope Mapper 组合测试。
- `docs/phase-00-脚手架.md`、`tasks.md`、`docs/README.md`、`plan.md`：R10 修订后的
  Phase 0 规格和参数基线。
- `docs/CURRENT-EXECUTION-PLAN.md`、`PROGRESS.md`、`HANDOFF.md`、`DEVLOG.md`：
  活动状态、后续门禁与交接入口。

本轮没有 Flyway 新版本、权限编码、业务状态机、API 返回结构或前端用户流程变化。主要风险从
首轮的产品/测试假闭环，转移到了动态证据 provenance、验收文字精度和参数注册表完整性。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `8e3217d..c426480` 两提交、30 个路径、提交链、调用者与权威文档 | 未重开其它 Phase 的业务正确性 |
| Testing Authenticity | High | 6/25 spec、gate/parser/verifier、21 个 gate UT、5 个新增/修改测试与生产调用链 | 未执行 Failsafe、真实 MySQL/Redis/MinIO 或浏览器 |
| Release | High | workflow、候选 SHA/clean 逻辑、manifest、hash、target marker、手工门禁说明 | 无 remote；未触发 Actions、merge、push、deploy、rollback |
| Configuration | Medium | dev/prod docs 配置、安全链、datasource/Redis/MinIO env 与 marker 声明 | 未解析 Compose，未启动 profile 或发送 HTTP 请求 |
| Documentation | High | Phase 0、全局 DoD、tasks、plan、README、review gate、统一计划与提交材料 | 历史报告只用于 finding closure |
| Supply Chain | Medium | Maven/npm 离线可重复构建、workflow 引用和既有 WS-7 边界 | 未联网查 CVE、生成 SBOM、签名或 attestation |
| Security | Medium | prod docs、摘要复用边界、secret redaction/verifier/path 逻辑 | 未做扫描、fuzz、凭据、权限或攻击性验证 |

## 3. Incremental Change Summary

### Change Summary

- Baseline: `8e3217da5f39f55f8146fa6d4e638adf8406914f`
- Product/test/gate commit: `3bfe83d9eaa4486bb39cc89fc3f05a975319e3af`
- Reviewed material HEAD: `c426480e718333ba864c2753c0a6c5d28b971221`
- Total files changed: 30
- Lines added: 2903
- Lines deleted: 145
- Commits in range: 2
- Authors: `[REDACTED_USER]`

### Change Categories

以下按主要职责归类，不把同一路径重复计数：

- Production behavior / configuration: 9 files
- Tests and exact-gate implementation: 8 files
- CI / build configuration: 2 files
- Specifications / active governance / submission evidence: 11 files
- New Flyway migrations: 0
- Dependency major-version changes: 0

### Previous Finding Closure Matrix

| 首轮 finding | 第二轮变化 | 独立结论 |
|--------------|------------|----------|
| M1：T-FILE 只上传一次，假闭环全局秒传 | R10 退役通用全局摘要复用；两层测试真实上传两次并要求两个对象/两行 | **关闭** |
| M2：CI 未锁定必需 suite | exact spec + clean verify + 精确 6/25 XML/方法/计数/新鲜度后验 | **原失败条件关闭；新增 provenance M1/M2** |
| M3：活动权威文档互相冲突 | npm、真实认证、lint、Phase 状态、WS-6 范围统一 | **关闭** |
| L1：prod 静态 docs UI 仍公开 | prod 前置 404 filter + dev/prod 九类路径正反例 | **关闭** |
| L2：证据 provenance 不足 | 完整脱敏日志/XML/hash/manifest/verifier 已落地 | **部分关闭；来源/路径 Low，SHA/target 两项 Medium** |
| L3：10/10 锚点过度 | 六项真实依赖/浏览器项改 `[~]`，新增参数矩阵 | **部分关闭；应用启动与参数范围各留 1 Low** |
| L4：DataScope 规格/层级漂移 | 规格对齐并新增真实 Mapper/plugin chain | **关闭** |

### Risk Delta

- Previous findings fully closed: 5
- Previous findings partially closed: 2
- New blocking findings: 2 Medium
- New non-blocking findings: 3 Low
- New Critical / High: 0
- Product-code regression found: 0
- Phase gate: **CHANGES_REQUESTED**

### Test Coverage Delta

- Exact dynamic gate declaration: 6 suites / 25 testcases
- Gate unit tests: 21/21 independently replayed
- Focused independent Surefire: 18/18
- Full independent offline Surefire: 31 suites / 266 tests
- Backend package: 9/9 modules
- Frontend: lint / type-check / production build PASS
- Deleted acceptance tests: 0
- Dynamic evidence on reviewed final HEAD: **not executed**
- Current fresh-schema parameter coverage: 24 documented keys; 8 current migration defaults outside matrix

### Approval Recommendation

**Request changes.**

先修复两个 Medium，并同轮处理三个 Low。之后由用户在全新隔离依赖栈只跑一次最终 SHA 的 exact
6/25 gate，同时补 `docker compose -f docker-compose.dev.yml config --quiet` 的自然退出证据；
必要的开发态浏览器核验继续由用户或获授权复核环境执行。只有新证据通过独立核验后，Phase 0
才可改为 PASS 并进入最终全量审计。

## 4. Top Risks

1. **Medium — 候选 SHA 只在运行前绑定：** clean HEAD 在 Maven 期间漂移时，manifest 可错认候选。
2. **Medium — target marker 只是自报文本：** 手工门禁可把实际 datasource/Redis/MinIO 目标错误标成隔离栈。
3. **Low — verifier 来源/路径边界不完整：** Windows 路径可在最终拒绝前触发 evidence_dir 外读取/哈希。
4. **Low — 应用上下文启动被过早勾选：** package 成立，但最终候选 RANDOM_PORT/上下文门禁未执行。
5. **Low — 参数矩阵不是当前注册表全集：** 24 项矩阵遗漏迁移中另 8 项默认参数。
6. **Required gate — exact 6/25 尚未执行：** 即使代码 finding 清零，也不能在没有最终 SHA 动态证据时 PASS。

## 5. Detailed Findings

### Finding: gate 未在运行结束重新绑定候选 HEAD

- ID: P00-R2-M1
- Severity: Medium
- Confidence: High
- Category: Release / Testing Authenticity / Provenance
- Status: Confirmed
- Affected area: `scripts/phase00_ci_gate.py` 的 candidate attestation
- Evidence:
  - File: `scripts/phase00_ci_gate.py:755-765`
  - Function / Module: `run_gate`
  - Relevant behavior: 只在 Maven 前用 `git_head` 读取并验证 `actual_candidate`。
  - File: `scripts/phase00_ci_gate.py:790-797`
  - Function / Module: post-run source checks
  - Relevant behavior: 结束时只复查 tracked/untracked cleanliness，不再次读取 HEAD。
  - File: `scripts/phase00_ci_gate.py:904-915`
  - Function / Module: manifest creation
  - Relevant behavior: manifest 始终写入运行开始时缓存的 `actual_candidate`。
  - File: `scripts/test_phase00_ci_gate.py:99-452`
  - Relevant behavior: 21 个反例覆盖错误 SHA、dirty、stale/missing XML、hash 等，但没有
    “运行期间 clean checkout/HEAD 漂移”。
- Problem: `git status` clean 只能说明索引和工作树相对**当前** HEAD 无改动，不能证明当前 HEAD
  仍是 Maven 开始前的候选。另一个终端、自动化或 clean checkout 在运行中切换提交后，目标
  XML、源码哈希和构建产物可以来自新 HEAD，而 manifest 仍声明旧 SHA。
- Why it matters: 本轮 exact gate 的核心价值是把 6/25 动态结果绑定到最终候选；该缺口直接使
  “candidate SHA 已完整绑定”的提交声明过强。
- Realistic failure scenario: 手工复核在本地运行 1–3 分钟 Maven；期间另一个任务把 clean
  worktree 切到另一个提交。构建成功、结束仍 clean，证据包写入开始时 SHA，后续复核者无法发现。
- Minimal fix: 在 Maven 完成并收集任何源码/报告前再次执行 `git rev-parse HEAD`；要求
  `endHead == startHead == expectedCandidateSha`，否则 FAIL。manifest 同时记录 start/end SHA。
- Better long-term fix: 在只读临时 worktree/归档源码中构建，并把 Git tree hash、gate spec hash、
  workflow identity 纳入 manifest/attestation。
- Regression test suggestion: stub `git_head` 依次返回两个不同 clean SHA，断言 gate FAIL 且
  FAIL manifest 明确记录 drift；相同 SHA 才允许 PASS。
- Estimated effort: 1–2 hours

### Finding: target marker 未与真实连接配置或服务身份绑定

- ID: P00-R2-M2
- Severity: Medium
- Confidence: High
- Category: Configuration / Release / Testing Authenticity
- Status: Confirmed
- Affected area: 手工真实依赖门禁的隔离目标 provenance
- Evidence:
  - File: `scripts/phase00_ci_gate.py:193-210`
  - Function / Module: `read_target_markers`
  - Relevant behavior: 只检查 marker 是否存在、字符合法、无 credential-like 文本。
  - File: `scripts/phase00_ci_gate.py:1141-1146`
  - Function / Module: `verify_evidence`
  - Relevant behavior: verifier 只对 manifest marker 重复同样的语法检查。
  - File: `.github/workflows/ci.yml:89-107`
  - Relevant behavior: workflow 当前把连接变量和 marker 并列硬编码，值看起来一致；脚本不校验二者。
  - File: `docs/reviews/phase-00-second-remediation-submission-2026-07-27.md:68-103`
  - Relevant behavior: 因仓库无 remote，实际下一步是用户手工设置两组变量并交回证据。
- Problem: marker 是执行者自报的标签，而不是从 datasource、Redis、MinIO 配置或运行目标导出的
  事实。脚本可以连接 A，却把 manifest 标成 B；也不证明 schema fresh、bucket 专用或服务版本真实。
- Why it matters: Phase 0 动态门禁会运行 Flyway、文件写入和审计写入。错误目标不仅污染证据，
  还可能误操作共享开发环境，违反用户明确要求的一次性隔离边界。
- Realistic failure scenario: 复制旧 shell 环境后只更新 marker，`SPRING_DATASOURCE_URL` 仍指向
  长期开发库；6/25 全绿，manifest 却声称运行在 fresh schema。
- Minimal fix: 解析并规范化 marker，与 `SPRING_DATASOURCE_URL`、Redis host/port/database、
  `MINIO_ENDPOINT`/`MINIO_BUCKET` 逐项交叉比较；任何不一致 FAIL。至少在 suite 中记录
  credential-free 的 `DATABASE()`、MySQL version/server UUID、Redis version/run ID/db、
  MinIO endpoint/bucket，并把它们写入 manifest。
- Better long-term fix: gate 自己创建随机 schema/bucket/Redis namespace，运行前证明为空，运行后
  清理，并由 CI OIDC/受控 runner 产生签名 provenance；手工 marker 仅作可读标签。
- Regression test suggestion: 为 URL/schema、Redis db、MinIO bucket 各造 marker/env 不一致反例；
  另覆盖服务身份与声明版本不一致、非空 schema/bucket 和重复 runContext。
- Estimated effort: 0.5–1 day

### Finding: evidence verifier 的来源字段与 Windows 路径边界不完整

- ID: P00-R2-L1
- Severity: Low
- Confidence: High
- Category: Security / Supply Chain / Testing Authenticity
- Status: Confirmed
- Affected area: 离线证据包 verifier
- Evidence:
  - File: `scripts/phase00_ci_gate.py:98-102`
  - Function / Module: `safe_relative_path`
  - Relevant behavior: 使用 `PurePosixPath`，只拒绝 POSIX absolute 和 `..` part。
  - File: `scripts/phase00_ci_gate.py:993-1023,1046-1081`
  - Relevant behavior: Windows `Path` 随后拼接并打开路径；外部路径会在 exact directory coverage
    最终拒绝前被读取/哈希。
  - File: `scripts/phase00_ci_gate.py:1056-1069,1186-1203`
  - Relevant behavior: `sourceSha256` 可缺省；suite artifact 没有要求 record `sourcePath` 等于
    spec report；spec 自身未归档/哈希。
  - Independent safe proof: `PurePosixPath` 接受 `..\escape`、`C:\outside\file.xml` 与 UNC
    形态；本轮只验证路径解析，没有访问 evidence_dir 外文件。
- Problem: 在 Windows 上，反斜杠、drive 与 UNC 不按 POSIX 规则解释；后续 Windows Path join
  可逃出 evidence_dir。当前 exact file coverage 会阻止这种包最终 PASS，因此不是直接假绿，
  但 verifier 会在拒绝前触碰边界外文件，且来源字段可以被弱化。
- Why it matters: evidence verifier 应安全处理可能损坏或外来的证据包，不能把“验证失败”建立在
  已经读取非目标路径之后。
- Realistic failure scenario: 复核者下载一个损坏或被替换的 evidence 包并在 Windows 上执行
  verifier；manifest 中的反斜杠 traversal 使脚本先打开并哈希 evidence_dir 外的可读文件，
  最后才因目录 coverage 不匹配报错。
- Minimal fix: 明确拒绝反斜杠、drive/UNC、`.`/`..`、空 segment；`resolve(strict=False)` 后要求
  `candidate.is_relative_to(evidence_dir.resolve())`，并拒绝路径链中的 symlink/reparse point。
  同时要求每个 suite/support artifact 的 `sourcePath`、`sourceSha256` 与 spec 精确对应，并归档
  spec SHA-256。
- Better long-term fix: 用固定 schema 的 manifest + 内容寻址 artifact store；在受限目录/进程中
  验证不可信证据包。
- Regression test suggestion: 增加 Windows drive、UNC、反斜杠 traversal、mixed separator、
  symlink/reparse、缺 source hash/path 和 spec hash drift 反例。
- Estimated effort: 2–4 hours

### Finding: Phase 清单把未执行的应用上下文门禁标为完成

- ID: P00-R2-L2
- Severity: Low
- Confidence: High
- Category: Documentation / Testing Authenticity
- Status: Confirmed
- Affected area: `docs/phase-00-脚手架.md` 验收清单
- Evidence:
  - File: `docs/phase-00-脚手架.md:81-87`
  - Relevant behavior: 文档定义 `[x]` 为本候选门禁已执行并通过，却把“package 产出可运行 jar，
    应用上下文可启动”合并标为 `[x]`。
  - File: `docs/reviews/phase-00-second-remediation-submission-2026-07-27.md:53-64`
  - Relevant behavior: 只列离线 Surefire 与 `-DskipTests package`，并明确
    `Phase00ScaffoldIT` / `Phase00ParameterMatrixIT` 未执行。
- Problem: package 证明了 jar 可生成，不证明最终候选在目标配置下能够启动 Spring context。
  `RANDOM_PORT` IT 才覆盖该动态事实。
- Why it matters: 清单用 `[x]` / `[~]` 区分已证和待证；合并陈述会让下一位复核者误以为启动门禁
  已完成。
- Realistic failure scenario: 最终候选出现只在 Spring bean 创建、Flyway 或真实依赖连接阶段暴露的
  启动错误；`-DskipTests package` 仍成功，而 Phase 清单继续显示该项完成。
- Minimal fix: 拆成“jar package” `[x]` 与“目标 profile/真实依赖应用上下文启动” `[~]`，或在
  exact 6/25 动态证据成立前将整项置 `[~]`。
- Better long-term fix: 每个 checklist item 关联 evidence manifest 中的 gate ID、candidate SHA
  与最后成功时间，文档只从机器结果生成状态。
- Regression test suggestion: 文档一致性测试拒绝引用未执行 gate 的 `[x]`。
- Estimated effort: less than 1 hour

### Finding: 参数矩阵只覆盖当前 fresh-schema 默认参数的 24/32

- ID: P00-R2-L3
- Severity: Low
- Confidence: High
- Category: Configuration / Documentation / Testing
- Status: Confirmed
- Affected area: `sys_param` 默认值基线与 `Phase00ParameterMatrixIT`
- Evidence:
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase00ParameterMatrixIT.java:30-71`
  - Relevant behavior: 测试严格覆盖 `DOCUMENTED_DEFAULTS` 的 24 项，并只查询这些 key。
  - File: `docs/README.md:79-102`
  - Relevant behavior: §6 当前只列同一 24 项，却称为默认参数基线。
  - Files: `V8__rbac_seed.sql:12-14`、`V11__process_material.sql:38`、
    `V12__exemption.sql:61-64`、`V13__video.sql:111-113`
  - Relevant behavior: fresh-schema 还插入 8 项：
    `login.lockThreshold`、`login.lockMinutes`、`captcha.ttlSeconds`、
    `file.material.allowedTypes`、`file.exemption.allowedTypes`、
    `file.maxSize.exemption`、`video.durationTarget`、`video.presign.expirySeconds`。
- Problem: 测试名和验收文字容易被理解为“当前 fresh-schema 全部默认参数”，实际上未断言另 8 项
  存在、默认值或类型；这些参数删除/漂移时矩阵仍绿。
- Why it matters: R2 要求阈值、文件类型/大小、安全和视频参数化。注册表遗漏会造成文档与运行默认值
  分叉。
- Realistic failure scenario: 后续迁移或合并误删 `login.lockThreshold` 或
  `file.exemption.allowedTypes`；24 项矩阵仍全绿，fresh 部署却回退到硬编码/缺省行为。
- Minimal fix: 将 §6 与矩阵扩为当前 32 项，并断言无缺失；若只想锁 Phase 0 核心子集，改名为
  “24 项核心/跨阶段基线子集”，明确其余参数的权威注册表与测试归属。
- Better long-term fix: 从迁移/集中参数注册表生成文档和参数化测试，防止手工三处维护。
- Regression test suggestion: fresh schema 查询全部 active `sys_param` key/value/type，与机器可读
  registry 做 exact set 比较，未知新增项也要求显式分类。
- Estimated effort: 1–2 hours

## 6. Testing Authenticity Analysis

### Confidence Assessment

| Evidence | Confidence | Reason |
|----------|------------|--------|
| gate UT 21/21 | High | 本轮独立离线重跑，正反例确实执行 |
| focused 18/18 | High | 当前源码独立重跑，覆盖两次普通上传、prod docs 与 DataScope 组合链 |
| Surefire 266/266 | High | 当前工作树 31 个新鲜 XML，0 failure/error/skip |
| Maven package / frontend gates | High | 本轮自然退出、离线/本地独立执行 |
| exact 6/25 declaration | High for design | spec/parser 的精确集合真实；但不是动态执行结果 |
| Phase00ScaffoldIT / ParameterMatrixIT | Not established | 最终候选没有真实依赖 Failsafe XML |
| candidate/target provenance | Low until fixed | HEAD drift 与 marker/env 解耦会错误归属 |

### Valuable Tests

- `FileServiceUploadContractTest` 真实调用两次普通上传并验证两个 object key、两次 PUT/INSERT。
- `Phase00ScaffoldIT.sameContentUploadsRemainIndependentWithoutGlobalDeduplication` 的测试设计与
  R10 新合同一致，待真实依赖执行。
- `ApiDocumentationSecurityProfileTest` 使用真实 Spring Security chain 验证 prod 404、dev 200，
  覆盖 API、UI 与静态资源路径。
- `DataScopeMapperChainTest` 不只调用 handler，而是贯穿注解、AOP、ThreadLocal context、
  MyBatis mapper proxy、生产插件顺序以及 count/data SQL。
- gate UT 对缺失、重复、跳过、陈旧、错误 suite、错误 SHA、dirty/untracked、hash 与自校验失败
  都有价值。

### Suspicious Tests

- 没有发现通过删除断言、固定返回或只验证 mock 自身形成的新产品测试假绿。
- `Phase00ParameterMatrixIT` 的 24 项断言本身真实，但名称/文档边界让覆盖范围显得比实际更大。
- gate UT 未模拟运行期间 HEAD 漂移、marker/env mismatch 与 Windows 路径边界。

### Missing Tests

- start/end HEAD drift 的 gate 反例。
- datasource/Redis/MinIO marker 与实际配置/服务身份不一致反例。
- Windows drive、UNC、mixed separator、reparse/symlink 与缺 source/spec hash 的 verifier 反例。
- current `sys_param` 全 32 项 exact registry。
- 最终 HEAD 的真实依赖 6 suites / 25 testcases。
- `docker-compose.dev.yml config --quiet` 的本轮独立证据。
- 非生产 `/doc.html` 浏览器渲染与示例接口；该项涉及浏览器/服务，按用户要求留给用户。

## 7. Release Concerns

- 当前没有 remote，GitHub Actions 不是可立即复用的正式证据来源；提交材料设计的手工路径是实际
  近端门禁，因此 M1/M2 不能以“CI 环境通常稳定”降级。
- `c426480` 没有最终 SHA 的真实依赖证据；动态门禁缺失本身就是 PASS 的硬闸，不计入代码 finding。
- 当前没有 merge、push、deploy、rollback、生产变更或稳定发布授权。
- `actions/checkout@v4`、`actions/setup-java@v4`、`actions/upload-artifact@v4` 未固定完整 commit
  SHA，镜像也未统一 digest；这是既有 WS-7 供应链范围，不是本轮新增 Phase 0 阻断，最终全量审计
  仍须复查。
- 即使 Phase 0 后续 PASS，也只放行最终全量审计，不直接等同 release GO。

## 8. Configuration Safety Analysis

### Configuration Summary

- `application-prod.yml` 关闭 springdoc/Knife4j 动态文档，`SecurityConfig` 在 JWT 前对 prod
  文档路径返回 404；静态与动态契约一致。
- workflow 的 MySQL/Redis/MinIO env 与 marker 当前文本相符，但 gate 未建立结构化约束。
- exact gate 不负责 `docker-compose.dev.yml config`，Phase 清单却把 Compose 静态解析并入真实
  依赖项；后续证据必须单独补充，不能从 Maven 6/25 推导。
- 本轮没有读取、打印或归档秘密值；报告只保留不含凭据的变量名和目标形态。
- 未执行任何环境写入或服务连接。

## 9. Documentation Analysis

### Documentation Summary

- 已对齐：npm、真实认证、最小 lint、R10 文件合同、Phase 0 复核退回、WS-6 剩余范围。
- 已诚实标注：六项真实依赖/浏览器项目等待动态证据，候选不等于 PASS。
- 仍需修正：把“jar package”和“应用上下文启动”拆开；明确参数矩阵是 24 项子集还是当前 32 项
  全集；更新提交材料中“完整 SHA/target provenance”过强表述。
- 历史首轮 CHANGES_REQUESTED 报告应保留不改写；本报告作为后续时间线追加。

## 10. Supply Chain / Reproducibility Analysis

### Supply Chain Summary

- Maven `-o` 单测和 package、npm lint/type-check/build 可在当前缓存下重复通过。
- 没有新增未锁定 npm 依赖或大版本栈升级；package-lock 仍是 npm 的唯一前端锁文件。
- evidence 包已有完整脱敏 Maven log、XML、artifact hash、manifest 与 `SHA256SUMS` 的设计，
  明显优于首轮人工摘录。
- 但 candidate/target/spec/source 边界尚不够形成可靠 provenance；修复 M1/M2/L1 后再生成动态包。
- 未联网执行依赖漏洞扫描、SBOM、签名或证书验证；这些动作可能超出用户安全边界，并属于 WS-7/
  最终发布审计。

## 11. Security Concerns

- 正向改善：通用客户端摘要驱动的跨主体全局复用已退役，降低对象存在性泄露和错误授权复用风险。
- 正向改善：prod 文档 API、UI 与静态资源统一 404，减少生产技术面暴露。
- 正向改善：DataScope 的 NONE/空 COLLEGE 范围继续失败关闭。
- 待修：verifier 必须在读取前证明路径位于 evidence_dir 内；不能依赖“之后 exact coverage 会失败”。
- 本轮没有确认新的产品权限、注入、认证或敏感数据 High/Critical。
- 未执行任何 cyber 类验证；需要真实基础设施或浏览器的步骤均明确交给用户。

## 12. Principles Compliance

### Principles Violated

- **Evidence over assertion：** target marker 仍是自报标签，未绑定实际连接事实。
- **Build what you attest：** 只绑定开始 HEAD，不足以证明构建结束仍是同一候选。
- **Boundary before access：** Windows 路径应在打开/哈希前完成 evidence root containment。
- **Single source of truth：** 参数基线和 fresh-schema 注册表仍有 24/32 的范围分叉。

### Principles Respected

- R10 变更先同步规格，再移除不存在且不安全的通用秒传合同。
- 测试从“名字像反例”升级为真实两次调用和真实 Mapper/plugin chain。
- prod docs 与 DataScope 继续采用失败关闭。
- 候选材料明确区分离线自测、真实依赖动态证据、独立 PASS 与发布 GO。
- 未因 Claude 不可用而把整改者自测直接升级为独立结论。
- 严格遵守用户的非 cyber、安全、隔离和非破坏边界。

## 13. Recommended Fix Order

### Fix Immediately

1. P00-R2-M1：记录并验证 start/end HEAD 完全一致，增加 drift 反例。
2. P00-R2-M2：marker 与实际 env/credential-free 服务身份结构化绑定，增加 mismatch 反例。

### Fix Before Stable Release

3. P00-R2-L1：收紧 Windows 路径 containment，并强制 source/spec hash。
4. P00-R2-L2：拆分 package `[x]` 与 context `[~]`。
5. P00-R2-L3：扩为 32 项 exact registry，或明确 24 项子集边界。
6. 修复提交后，用户在全新一次性隔离栈执行最终 HEAD 的 exact 6/25，交回完整 PASS/FAIL 包、
   目标身份、运行前空状态和运行后清理结果；另交 Compose config 和必要浏览器证据。

### Schedule Later

- WS-7：Action 完整 SHA、镜像 digest、SBOM、依赖审计、artifact signing/provenance。
- 从机器可读参数 registry 与 gate manifest 生成文档状态，减少手工三处维护。

### Ignore for Now

- 前端 Vite 大 chunk warning：既有非阻断性能债，不影响本轮 Phase 0 正确性。
- Phase 7、39、41、44 的已记录长期边界：留最终全量审计，不在本次候选扩项。

## 14. Quick Wins

| Item | Effort | Impact |
|------|--------|--------|
| end HEAD 复核 + manifest start/end 字段 | 1–2 hours | 直接关闭候选归属 Medium |
| Phase 清单拆分 package/context | <1 hour | 恢复 `[x]` / `[~]` 语义 |
| marker/env mismatch 纯单测 | 1–2 hours | 防止手工门禁明显错靶 |
| 拒绝 `\`、drive、UNC 并做 resolved containment | 1–2 hours | 关闭 Windows verifier 边界 |
| 补齐/改名 24 项参数矩阵 | 1–2 hours | 消除参数覆盖过度声明 |

## 15. Long-term Refactor Plan

1. 把 Phase gate 运行在一次性只读 Git worktree 或源码归档内，构建期间禁止工作树切换。
2. gate 自行分配随机 schema/bucket/Redis namespace，记录 credential-free 服务身份并完成自动清理。
3. 以 JSON Schema 固定 manifest，包含 Git commit/tree、gate spec、workflow、toolchain、target identity、
   source/artifact hashes；可选签名和透明日志。
4. 将 `sys_param` 定义集中为机器可读 registry，由 Flyway、文档、管理页校验和 IT 共享。
5. 从 evidence manifest 自动更新 Phase checklist；人工文档只解释边界，不手填运行状态。

---

**Evidence:** `docs/reviews/evidence/phase00-second-remediation-rereview-2026-07-27/`
**Verdict:** `CHANGES_REQUESTED`
**Next gate:** 修复 2 Medium / 3 Low 后，由用户执行最终 SHA 的隔离 exact 6/25 + Compose/browser 必要证据。
