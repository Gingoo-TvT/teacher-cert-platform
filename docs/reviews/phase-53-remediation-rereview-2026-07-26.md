# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 53 remediation
**Audit mode:** incremental + security / stability / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-26
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮以 `4996811a028016280d63437c6336af3678cd5128` 为基线，冻结整改代码
`b9abc6c2614e98cd52e271bb5882383e03b25c09`，并核对提交材料
`724e07d3cb58876fd475291c5e9714321e8c9c87` 与动态证据提交
`8a772fe2fb25bbb63c317bdec8ebf06eb2d19408`。代码增量为 1 个提交、6 个文件、
`+1356/-121`；代码点之后只有文档和证据变化，六个生产/测试文件与当前 HEAD
逐路径比较无漂移。

正式结论：**CHANGES REQUESTED（0 Critical / 0 High / 1 Medium / 2 Low）**。

- 原 Phase 53 Major 已按行为口径关闭：仓库内 1,605,702B MP4 是真实 H.264
  媒体；独立制品核验确认 fat JAR 内资源大小和 SHA-256 与 manifest 一致，用户侧
  ffprobe、首帧、拖动至中段和动态水印证据相互吻合。
- WS-3 补充 High 已按行为口径关闭：内容版本 object key、旧固定 key 保留、
  已存在对象完整校验、可信媒体探测、五类数据库引用收敛和单事务 SQL 切换成立；
  用户动态证据表明旧 528B 环境升级、二启幂等、两类污染拒绝、SQL 故障回滚和
  恢复链均符合预期。
- 独立安全离线门禁全绿：`mvn -o -B -ntp -pl platform-boot -am clean test`
  **195/195**，其中初始化器 **21/21**、共享媒体策略 **10/10**；
  后端 **9/9 modules package PASS**；前端 type-check/build PASS；fat JAR
  资源与关键类存在；diff check PASS。
- 新 Medium 不在生产行为，而在正式证据包：动态报告用 12 个 `logs/*.log`
  证明启动成功、污染失败关闭、SQL 故障与恢复，但 `.gitignore` 的 `*.log`
  使整目录未进入 `8a772fe`。当前机器上的原始日志与报告一致，本轮也记录了
  SHA-256；然而 fresh clone 和最终全量审计拿不到被引用的原始证据。
- 两个 Low 分别是：单元测试把真实媒体探测全部 stub 掉，无法自动保护
  packaged MP4 的可解码性；已跟踪证据保存了已失效的临时口令和完整、已过期的
  localhost 预签名 URL，虽资源已销毁且当前无有效访问风险，仍违反证据脱敏纪律。

Phase 53 继续维持复核退回；最小重交范围仅为证据归档与脱敏，不要求重写已闭环的
生产代码。Phase 44 暂不放行；全项目仍由 Phase 0、44、53 保持
**CHANGES_REQUESTED**。本报告不是 merge、push、部署、切流或项目发布 GO。

本轮没有启动常驻服务、Docker、数据库、MinIO 或浏览器，没有执行网络、权限变更、
故障注入、扫描、恶意载荷、fuzz、压力或任何可能属于 cyber 的动作。

### Score Dashboard

```text
Security        █████████░  8.8  A   生产增量未引入泄露；证据仓仍有一个失效凭据 Low
Stability       █████████░  9.2  A   版本对象、失败关闭和单事务切换链成立
Performance     ████████░░  8.5  A   有界读取与线性校验成立；未做规模/压力验证
Testing         ████████░░  7.6  B   195/195 全绿；动态原始日志未提交且媒体探测被 stub
Maintainability █████████░  8.8  A   manifest/策略抽取清晰，边界有明确说明
Design          █████████░  8.8  A   内容版本发布模型成立，外部同-key CAS 仍是边界
Release         ███████░░░  7.4  B   行为闭环，但证据包在 fresh clone 中不自足
─────────────────────────────────────
Overall         ████████░░  8.4  A
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
| Low | 2 | 2 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **3** | **3** | **0** |

## 2. Project Map

- `platform-boot`：`DemoDataInitializer` 读取三种 classpath 资源，构造四个对象候选，
  上传/复核内容版本对象，调用真实媒体探测并在单个数据库事务内渲染执行
  `demo-data.sql`。
- `platform-business`：新增 `VideoMediaAcceptancePolicy`，由 demo 初始化与生产
  `VideoReviewServiceImpl` 复用同一大小、格式、时长、帧数、编码和探测结果规则。
- `platform-boot` 测试：`DemoDataInitializerTest` 21 个用例覆盖 manifest、对象污染、
  写后损坏、探测事实、SQL token、失败关闭与幂等；共享策略测试 10 个。
- demo 资源：MP4 1,605,702B、PDF 659B、PNG 99B；fat JAR 中三者 SHA-256 与源码
  manifest 一致。
- 动态证据：三个一次性隔离 schema/bucket 的旧环境升级、污染、SQL 故障/恢复、
  鉴权、Range、浏览器播放和清理记录；32 个文本/截图/旧基线文件已跟踪，12 个启动日志
  仅存在于当前工作区。
- 治理面：候选材料与动态报告均未提前宣称 Phase PASS；本报告给出正式退回结论。

本轮无 DDL/Flyway、对外 API、权限点、业务状态集合、前端生产代码、依赖版本或生产
部署配置变化。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `4996811..b9abc6c` 六文件完整 diff、提交链、blob、`b9abc6c..HEAD` 路径比较 | 未重开其它 Phase |
| Security | High | demo/prod profile 隔离、日志实参、播放证据、凭据归档卫生 | 未执行扫描、登录、攻击请求或权限操作 |
| Stability | High | 资源预校验、对象读取/写后复核、媒体验收、事务边界、失败路径 | 未重跑服务、MinIO/MySQL 或故障注入 |
| Performance | Medium | 有界资源读取、完整 SHA 读取次数、SQL 批量执行 | 未做容量、压力或长时运行 |
| Testing Authenticity | High | 两个测试文件、195 个独立测试、XML、tracked/untracked 证据清单、原始日志哈希 | 用户动态栈未由 Codex 重跑 |
| Release | High | 分支/提交、制品、前后端构建、动态清理记录、证据可移植性 | 未 merge/push、部署、切流或回滚演练 |
| Configuration | High | `RuntimeProfileGuard`、dev/demo/prod 配置 blob 零变化、demo 默认关闭 | 未读取仓库外生产配置 |
| Data Integrity | High | 五类引用、旧 key 保留、事务 SQL、污染/回滚前后快照 | 未独立写入真实数据库/对象存储 |
| Concurrency | Medium | 同候选多实例幂等、`stat→put→revalidate→DB` 顺序、发布协议 | 未执行并发写或锁交错 |
| Maintainability / Design | High | manifest、策略抽取、错误分类、SQL token 渲染 | 全仓 demo/媒体设计留最终审计 |

## 3. Incremental Change Summary

### Change Summary

- Production/test code files changed: 6
- Code lines added: 1,356
- Code lines deleted: 121
- Remediation code commits: 1
- Documentation/evidence commits after code freeze: 2
- Code commit: `b9abc6c`
- Submission commit: `724e07d`
- Evidence commit / HEAD at review start: `8a772fe`
- Author: `wenbibuhaoqwq <wenbibuhaoqwq@localhost>`

### Change Categories

| Category | Files | Summary |
|----------|-------|---------|
| Bug fix | 4 | 内容版本初始化、SQL manifest 化、生产调用点与共享媒体策略 |
| Tests | 2 | 初始化器 21 个用例、共享策略 10 个用例 |
| Inherited demo resource | 0 | MP4 由更早的 `ca400f1` 引入；本轮只冻结并验证，不计入六文件增量 |
| Documentation / evidence | 2 commits | 候选说明与用户动态证据 |
| Dependencies / Flyway / API / frontend | 0 | 无变化 |

### Previous Finding Closure Matrix

| Previous requirement | Remediation evidence | Independent result |
|----------------------|----------------------|--------------------|
| 528B 空壳必须替换为真实可播放 MP4 | 真实 H.264 资源、fat JAR 摘要、ffprobe、首帧/拖动/水印截图 | **Closed** |
| 资源元数据必须来自真实内容 | 原始 SHA-256/大小、tree 指纹、900 秒/900 帧/H264、策略/探测版本 | **Closed** |
| 旧对象升级不得原位覆盖 | SHA 派生版本 key，旧固定 key 保留 | **Closed** |
| 已存在同-key 污染必须拒绝且不覆盖 | size/type/完整读回 SHA 校验；两类动态污染反例 | **Closed** |
| 所有数据库引用必须原子收敛 | 五类引用由同一 manifest 渲染，单个 `TransactionTemplate` 执行 | **Closed** |
| SQL 失败必须保持旧 DB/旧对象组合 | 用户隔离环境后段故障、事务回滚前后快照、恢复重启 | **Closed behaviorally; raw log archive incomplete** |
| 浏览器首帧/时长/拖动/水印/鉴权 | 已跟踪截图、API 矩阵与过期记录 | **Closed behaviorally** |

原 Major 与补充 High 均按生产行为口径关闭；新 Medium 只针对正式证据包的可移植性，
不推翻代码和已观察行为结论。

### Risk Delta

- Previous blocking findings closed: 1 Major + 1 supplemental High
- New blocking findings: 1 Medium（证据归档）
- New non-blocking findings: 2 Low
- Production behavior regressions confirmed: 0
- Existing baseline risks made worse: 0

### Test Coverage Delta

- Independent full offline tests: 195/195
- `DemoDataInitializerTest`: 21/21
- `VideoMediaAcceptancePolicyTest`: 10/10
- `platform-file`: 18/18
- `platform-boot`: 177/177
- Independent backend package: 9/9 modules
- Independent frontend type-check/build: PASS
- Valuable negatives: 同长度错内容、错 Content-Type、读回错摘要、写后损坏、媒体事实
  六类偏差、SQL token 残留、大小/时长/编码不合规
- Dynamic user gates: 10/10 reported and cross-checked against tracked snapshots/screenshots
- Remaining gaps: 12 个被报告引用的启动日志未提交；单元测试没有真实探测 packaged MP4

### Approval Recommendation

**Request changes / keep Phase 53 returned.** 只需提交一个最小证据整改：

1. 把 12 个启动日志先脱敏，再以可跟踪的 `.txt` 或脱敏摘录 + 原始 SHA-256 清单归档；
2. 修正动态报告引用，并增加“所有引用证据必须被 Git 跟踪”的一次性检查；
3. 同次把已跟踪证据中的临时口令和完整预签名查询参数改为
   `[REDACTED_SECRET]`。

重交时只复核上述证据增量、tracked-path 自洽性和一次离线回归，不要求再次执行
Docker、MySQL、MinIO、浏览器或故障注入。

## 4. Top Risks

1. **Medium — dynamic report depends on 12 ignored log files：** 当前机器可核对，fresh
   clone 与最终全量审计不可复核启动/失败关闭的原始记录。
2. **Low — packaged video decodability is not an automated regression gate：**
   31 个聚焦测试全部以 stub 代替真实媒体探测。
3. **Low — tracked evidence contains expired but unredacted credential material：**
   已销毁环境降低了即时风险，但仓库历史仍保存不必要的敏感值。
4. **Carry-forward object race：** `stat(MISSING) → put` 与最终对象复核→DB 提交之间
   没有对象存储 CAS，必须坚持隔离 bucket 和外部精确同-key 写串行。
5. **Carry-forward specification conflict：** Phase 7 文档要求“拿到预签名 URL 后未登录
   也失败”，而现有 MinIO URL 是有效期内的 bearer URL；本候选未改该路径，留最终
   全量审计裁定规格或改鉴权代理。

后两项是候选前已有边界，不计本轮 finding。

## 5. Detailed Findings

### Finding: 动态报告引用的 12 个启动日志未进入证据提交

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity / Release Evidence
- Status: Confirmed
- Affected area: Phase 53 动态门禁的可复现性、fresh-clone 复核和最终全量审计
- Evidence:
  - File: `.gitignore:7`
  - Function/module: repository ignore policy
  - Relevant behavior: 全局 `*.log` 规则忽略动态证据目录内的启动日志。
  - File: `docs/reviews/phase-53-dynamic-evidence-2026-07-26.md:31-50`
  - Function/module: dynamic gate evidence
  - Relevant behavior: 成功初始化、幂等、两类污染拒绝、SQL 故障和恢复均直接引用
    `logs/*.log`。
  - Commit: `8a772fe2fb25bbb63c317bdec8ebf06eb2d19408`
  - Relevant behavior: `git ls-tree -r` 对该证据路径没有任何 `/logs/` 条目；
    当前工作区显示整个目录为 ignored。
  - Evidence file:
    `docs/reviews/evidence/phase53-remediation-rereview-2026-07-26/offline-gates-and-evidence-integrity.txt`
  - Relevant behavior: 记录当前机器 12 个原始日志的名称、大小与 SHA-256，并说明
    关键结果与动态报告一致。
- Problem: 正式报告把被忽略的本地文件当作已归档证据；提交本身无法重建报告引用链。
- Why it matters: 阶段 PASS 后若切换机器、清理工作区或进入最终全量审计，审计者只能
  看到二次摘要，无法复核候选启动、失败关闭和 SQL 故障的原始时序与分类。
- Realistic failure scenario: 当前工作区被删除或在 CI/fresh clone 中检出 `8a772fe`；
  报告的五处 `logs/...` 链接全部失效，原 Major/High 的关键动态关闭只能依赖自报。
- Minimal fix: 对 12 个日志先做定向脱敏，以 `.txt` 归档；至少保留时间线、profile、
  schema/bucket 代号、初始化结果、失败类别和退出结果，并附原始文件 SHA-256；
  更新报告为实际可跟踪路径。
- Better long-term fix: 增加证据清单文件和 CI/本地脚本，解析复核报告中的相对路径并
  对每一项执行 `git ls-files --error-unmatch`，同时做 secret lint。
- Regression test suggestion: 在一个临时 fresh checkout 中运行证据完整性检查，
  断言动态报告全部相对路径存在、受 Git 跟踪且摘要匹配。
- Estimated effort: 20–40 minutes

### Finding: 聚焦测试未对打包后的真实 MP4 执行媒体探测

- Severity: Low
- Confidence: High
- Category: Testing Authenticity / Regression Protection
- Status: Confirmed
- Affected area: `sample-video.mp4` 的可解码性、时长、帧数、编码与首帧回归保护
- Evidence:
  - File:
    `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/config/DemoDataInitializerTest.java:116-119`
  - Function/module: test setup
  - Relevant behavior: `videoMediaProbe.inspect(...)` 总是返回预制
    `trustedInspection()`。
  - File:
    `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/config/DemoDataInitializerTest.java:469-476`
  - Function/module: `trustedInspection`
  - Relevant behavior: 900 秒、H264、900 帧等事实由测试常量直接构造。
  - File:
    `platform-boot/src/test/java/cn/edu/gpnu/platform/business/video/support/VideoMediaAcceptancePolicyTest.java:165-169`
  - Function/module: `StubVideoMediaProbe.inspect`
  - Relevant behavior: 明确抛出“本策略单测不执行媒体探测”。
- Problem: 聚焦测试验证了对象/SQL/策略合同，但没有让真实 worker/探测器读取仓库或
  fat JAR 内的 `sample-video.mp4`。
- Why it matters: 未来若替换 MP4 并同步修改 manifest 与测试常量，31 个测试可能仍
  全绿，即使新资源无法解码或没有真实首帧。
- Realistic failure scenario: 维护者提交损坏 MP4，同时更新大小、SHA 和预制
  `VideoMediaInspection`；对象/SQL 单测均通过，直到人工浏览器门禁才发现不可播放。
- Minimal fix: 增加一个自然退出、有超时和资源上限的测试，调用真实媒体探测器检查
  classpath `sample-video.mp4`，断言 fingerprint、900 秒、900 帧、H264 和首帧有效。
- Better long-term fix: 把该测试放入独立 `media-resource-contract` 门禁，同时验证
  源资源与 fat JAR 内资源摘要一致；变更媒体资源时强制运行。
- Regression test suggestion: 用真实样例先 PASS，再复制并截断少量尾部字节，断言
  探测失败关闭且进程在限定时间内退出。
- Estimated effort: 1–2 hours

### Finding: 已跟踪动态证据保留失效但未脱敏的凭据材料

- Severity: Low
- Confidence: High
- Category: Security / Evidence Hygiene
- Status: Confirmed
- Affected area: Git 证据历史、临时测试账号与预签名 URL
- Evidence:
  - File:
    `docs/reviews/evidence/phase-53-dynamic-2026-07-26/21-browser-verification-summary.txt:4`
  - Function/module: browser verification account record
  - Relevant behavior: 保存已销毁隔离 schema 的临时明文口令。
  - File:
    `docs/reviews/evidence/phase-53-dynamic-2026-07-26/18b-presigned-url-expiry-record.txt:3`
  - Function/module: presigned URL expiry record
  - Relevant behavior: 保存完整 localhost URL，包括 access-key 标识和完整签名查询值。
  - File:
    `docs/reviews/evidence/phase-53-dynamic-2026-07-26/23-cleanup-confirmation.txt`
  - Function/module: cleanup record
  - Relevant behavior: 对应 schema、bucket 和 Redis 隔离数据已销毁，URL 也已自然过期。
- Problem: 证据留存了证明结论不需要的完整秘密值，而不是只保留签发时间、有效期、
  URL 结构和 HTTP 结果。
- Why it matters: 即使本轮值已失效，仓库会把不必要的凭据材料永久复制到历史、镜像、
  缓存和审计导出中；相同模式在下次若误用长期凭据会造成真实泄露。
- Realistic failure scenario: 后续人员照抄证据格式，用可复用账号或仍有效签名生成新
  记录，提交后才发现秘密已进入不可轻易清除的 Git 历史。
- Minimal fix: 当前文件用 `[REDACTED_SECRET]` 替换临时口令、access-key 标识和签名；
  只保留 endpoint/path、`X-Amz-Expires`、时间线和 206/403 结果。如测试值曾在其它
  环境复用，应立即轮换。
- Better long-term fix: 为 `docs/reviews/evidence/` 增加提交前 secret lint 与允许字段
  模板；所有身份材料默认只保存不可逆摘要或掩码。
- Regression test suggestion: 对证据目录执行 secret scanner/正则门禁，至少拒绝
  明文口令、完整 `X-Amz-Credential`、`X-Amz-Signature` 和 bearer token。
- Estimated effort: 10–20 minutes

## 6. Security Concerns

- Coverage: High
- Inspected evidence: demo/prod profile 边界、日志异常处理、播放 API/Range/过期记录、
  tracked 证据秘密值。
- Positive evidence:
  - `RuntimeProfileGuard` 与 `application-{dev,demo,prod}.yml` 在代码增量中未变；
    `prod + demo` 继续被启动前拒绝。
  - 初始化失败只记录固定异常类别，不把底层异常 message/cause、连接信息或 SQL
    注入生产日志。
  - 播放 Controller、权限点和数据范围未因 Phase 53 改动；用户证据覆盖授权正例、
    越权 403、未认证 401、篡改签名 403 和自然过期 403。
- Finding: 已跟踪证据保留失效口令和完整预签名查询参数，计 1 Low。
- Exclusions / limits: 未执行扫描、攻击请求、登录、凭据尝试、权限变更或网络探测。

Phase 7 的“有效预签名 URL 在未登录时也必须失败”与 bearer URL 现实合同冲突，
属于候选前既有规格问题；本轮不计 finding，进入最终全量审计裁定。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: 完整初始化器、SQL、生产媒体策略抽取、资源/对象/媒体/事务失败
  分支、用户隔离环境前后快照。
- Positive evidence:
  - classpath 资源在分配前以“上限 + 1”有界读取并核对大小/SHA；
  - 版本对象缺失才写，存在时完整核对 size/type/SHA；
  - 媒体探测后再次复核全部对象，再进入单事务数据库切换；
  - SQL 失败回滚所有 InnoDB DML，不覆盖/删除旧固定 key；
  - 二次启动同字节、同 SQL，动态证据为新建 0/跳过 4、DB/对象零漂移。
- Existing boundary: 外部精确同-key 写没有对象 CAS；专用隔离 bucket 与运维串行仍是
  正确性前提。
- Exclusion: Codex 未重跑真实对象写、数据库事务故障或服务启动。

## 8. Performance Concerns

- Coverage: Medium
- Inspected evidence: 资源有界读取、对象完整 SHA、初始化次数和 SQL 批量执行方式。
- Result: 本轮没有把请求热路径变为额外全对象读取；完整 SHA/媒体探测只发生在显式
  demo 初始化。三个小资源和 1.6MB 视频的读取成本有界。
- Existing boundary: 每次 demo 启动会读回四个对象并探测视频；对当前固定样例合理，
  若未来扩大 demo 媒体必须重新评估启动时间与内存上限。
- Exclusion: 未执行压力、容量、长时或多实例基准。

## 9. Testing Authenticity Analysis

### Confidence Assessment

| Test claim | Confidence | Independent assessment |
|------------|------------|------------------------|
| 内容版本 key/污染拒绝/SQL token | High | 21 个单测 + 完整源码控制流 |
| 生产媒体策略与 demo 复用 | High | 10 个策略测试 + 生产调用点 diff |
| packaged MP4 摘要与大小正确 | High | fat JAR 条目独立流式 SHA-256 |
| packaged MP4 真实可解码 | Medium | 用户 ffprobe/截图成立，但单元测试只用 stub |
| 旧库升级、幂等、污染、回滚行为 | High for current workspace | tracked 快照 + 当前机器原始日志一致 |
| 动态证据可在 fresh clone 复核 | Low | 12 个被引用日志未进入提交 |
| 鉴权/水印/过期 | Medium-High | 已跟踪 API/截图/过期记录，Codex 未重跑 |

### Valuable Tests

- 同长度错内容与错误 Content-Type 都断言不覆盖；
- 写后损坏、读回摘要不符、探测事实六类偏差均失败关闭；
- SQL 渲染要求 22 个 token 全部替换且拒绝旧 key/旧摘要；
- 生产策略抽取保留大小、类型、时长、帧数、编码和探测有效性边界；
- 独立 `clean test` 195/195，避免复用候选自报的绿色计数。

### Suspicious Tests

没有发现删除断言、吞异常、扩大容错或只验证 mock 调用而不验证结果的问题。确认的
真实性盲点是媒体探测被全量 stub，以及动态报告引用未跟踪日志。

### Missing Tests

- 真实探测 packaged `sample-video.mp4` 的资源合同门禁；
- fresh checkout 下报告引用路径全部 tracked 的证据完整性门禁；
- 证据目录的 secret lint。

## 10. Release Concerns

- Phase gate: **CHANGES REQUESTED**。
- Blocking reason: 1 Medium evidence portability defect。
- Project gate: **CHANGES_REQUESTED**，仍由 Phase 0、44、53 阻断。
- Allowed next action: 仅做 Phase 53 证据归档/脱敏整改并重交；不需要重新执行用户侧
  Docker、MySQL、MinIO、浏览器或故障注入。
- Not yet released: Phase 44。
- Not authorized: merge、push、部署、切流、旧对象清理或项目发布。
- Rollback boundary: 新旧初始化器不得混部；V32 发布协议继续要求停止全部旧节点，
  迁移后只启动同一新协议实例，失败只能前向修复。

## 11. Configuration Safety Analysis

### Configuration Summary

| Item | Change | Assessment |
|------|--------|------------|
| demo enable/profile | none | 默认关闭；prod+demo fail-fast 保持 |
| MinIO endpoint/bucket/credentials | no production change | 动态隔离值只属已销毁测试环境 |
| DB/Flyway | none | 无迁移；demo SQL 是运行期 DML |
| media limits | production policy reused | 未新增独立 demo 魔法阈值 |
| frontend/API | none | 构建通过，运行合同未改 |

没有新增生产默认值、环境变量、依赖、端口、密钥或自动启用范围。

## 12. Data Integrity Analysis

### Integrity Summary

- 四个对象候选先独立验证，数据库提交是唯一可见引用切换点；
- `file_object`、`process_material`、`exemption_material`、`video_review`、
  `video_upload_session` 的 insert/update 路径都由同一 manifest 收敛；
- 旧固定对象不覆盖、不删除，SQL 失败可继续由旧数据库引用；
- 用户侧故障后 DB 前后快照相同，新对象仅成为无引用候选，清障后可恢复；
- 旧 528B/旧摘要/旧时长/旧 key 没有留在渲染 SQL；
- 本轮没有写入或删除任何数据库、对象、容器、镜像或数据卷。

## 13. Concurrency Analysis

- Coverage: Medium
- 同候选多实例写同一内容版本 key、相同字节和相同幂等 SQL，最终状态可收敛。
- `stat(MISSING) → put` 没有 if-none-match/CAS；外部或人工精确同-key 写必须与 demo
  初始化串行。
- 最终对象复核与数据库提交之间也无跨系统原子事务；隔离 bucket 可把外部变更排除。
- 旧 `4996811` 初始化器若与 `b9abc6c` 混部，旧节点可能后提交旧固定 key；现行 V32
  发布协议禁止新旧二进制混部，因此不作为新增 finding。
- 按用户安全边界未执行并发、锁、故障交错或真实对象写。

## 14. Principles Compliance

### Principles Violated

- **Evidence must be portable：** 报告引用 12 个仅存在于当前工作区的 ignored 日志。
- **Secrets are not evidence：** 已失效口令和完整签名查询值不应进入 Git。
- **Automate invariant checks：** 真实 demo MP4 的可解码性仍依赖人工动态证据。

### Principles Respected

- **Fail closed：** 资源、对象、媒体或 SQL 任一不一致都会阻止启动/提交。
- **Publish by immutable content：** 内容版本 key 取代原位覆盖。
- **Commit references atomically：** 五类数据库引用在单事务内切换。
- **Preserve rollback state：** 旧固定 key 保留，失败不破坏旧组合。
- **Reuse production policy：** demo 不另造视频验收规则。
- **Honest gates：** 候选、用户动态证据、独立复核和最终发布 GO 分开记录。

## 15. Recommended Fix Order

### Fix Immediately

1. 脱敏并归档 12 个启动日志或足量摘录，附原始 SHA-256，修正动态报告引用。
2. 加一次证据完整性检查，确保报告引用文件全部存在且被 Git 跟踪。
3. 把两个已跟踪证据文件内的秘密值替换为 `[REDACTED_SECRET]`。

完成上述最小证据整改后，Phase 53 可只做证据增量复核。

### Fix Before Stable Release

1. 增加真实 packaged MP4 探测测试。
2. 为整个 `docs/reviews/evidence/` 增加 secret lint。

### Schedule Later

1. 最终全量审计裁定 Phase 7 有效预签名 URL 的 bearer 合同与“未登录失败”文档冲突。
2. 评估对象存储条件写/CAS 或单写者方案，减少外部同-key 并发边界。
3. 把证据清单、哈希、tracked-path 和清理证明收敛为统一生成工具。

### Ignore for Now

- 不要求为本次证据整改重新启动服务或重新创建数据库/MinIO/浏览器环境。
- 不把 Phase 44、Phase 0 或最终全量审计问题混入 Phase 53 修复范围。

## 16. Quick Wins

| Action | Effort | Benefit |
|--------|--------|---------|
| 将脱敏日志扩展名改为 `.txt` 并提交 | 15–25 min | 关闭唯一 Medium |
| 生成 12 项 SHA-256 manifest | 5 min | 固化当前原始证据同源性 |
| 修正报告引用并跑 `git ls-files --error-unmatch` | 5 min | fresh-clone 可复核 |
| 两处证据替换为 `[REDACTED_SECRET]` | 5–10 min | 关闭证据卫生 Low |
| 增加 packaged MP4 实探测试 | 1–2 h | 关闭长期媒体回归 Low |

## 17. Long-term Refactor Plan

1. **Evidence manifest：** 每次动态门禁自动生成相对路径、大小、SHA-256、脱敏状态和
   产生命令，不允许手写悬空引用。
2. **Resource contract gate：** 对所有 demo 二进制资源做类型、摘要和真实解析验证，
   并比较源码资源与 fat JAR 条目。
3. **Cross-store publish protocol：** 如未来 demo 初始化可被外部并发写，采用条件写、
   隔离 namespace 或单写者租约，把当前运维前提变为机器可验证合同。
4. **Final full audit：** 统一复查 Phase 7 预签名语义、demo/prod profile、旧对象回收、
   多实例发布和证据脱敏，不在本轮提前宣称项目发布就绪。

---

**Final verdict: CHANGES REQUESTED（0 Critical / 0 High / 1 Medium / 2 Low）。**

原 Phase 53 Major 与补充 High 的生产行为已关闭；唯一阻断项是动态证据包没有提交其
引用的 12 个启动日志。完成最小证据归档/脱敏并重交后，只需独立复核该证据增量与
必要离线回归。Phase 44 暂不放行，全项目继续由 Phase 0、44、53 保持
CHANGES_REQUESTED，全部退回项关闭后仍需执行用户要求的最终全量审计。
