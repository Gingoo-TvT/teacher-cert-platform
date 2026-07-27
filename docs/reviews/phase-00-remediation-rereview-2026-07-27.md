# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 0 / U-004 remediation
**Audit mode:** incremental / testing-authenticity / release / configuration / documentation / supply-chain / security
**Date:** 2026-07-27
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结已归档的 Phase 44 PASS 基线
`dd04f21387f76adc7fc5d1443903ada0bec4d4c1` 与其直接子提交、Phase 0 / U-004
整改候选 `715b5f1c67a9943c5914eda08e485a1463c0524d`。候选为单提交、24 个路径、
`+2917/-188`；本轮逐文件核对验收基线、lint/CI 接线、两组新增测试、Knife4j/springdoc
配置、候选证据与治理状态。

正式结论：**CHANGES_REQUESTED（0 Critical / 0 High / 3 Medium / 4 Low）**。

- Checkstyle 已真实绑定 Maven `validate`，ESLint 已进入前端 CI；本轮独立离线重放均通过。
- `/v3/api-docs` 的 `NoSuchMethodError` 已由关闭不兼容 Knife4j 增强层修复；新增 IT 的
  `/doc.html`、统一异常、审计与预签名 TTL 主体证据真实。
- `DataScopeSqlHandlerTest` 9 个分支测试真实执行且失败关闭分支有效；本轮独立 Surefire
  为 **257/257**。
- 但 T-FILE-1 所称“同 MD5 二次上传不重复存储”没有执行第二次上传；生产通用上传也不会
  自动命中该查询，因此 6/6 中存在一条实质性假闭环。
- CI 只运行宽泛 `mvn verify`，没有锁定 Phase 0 的 6 个 IT、9 个 DataScope 用例或 XML
  后验计数；目标 suite 被删、改名或漏发现时，现有数百个其它测试仍可使 CI 绿。
- U-004 原本要消除的权威基线冲突尚未闭环：全局 DoD、任务表、统一计划、根 README 与
  review gate 仍分别保留“lint 未落地”“pnpm/mock/Prettier”“Phase 0 无需复核/已复核”等旧口径。

独立允许范围内的门禁全部通过：`git diff --check`、Maven 9 模块 Checkstyle、
Surefire **257/257**、9 模块 package、ESLint、TypeScript type-check、Vite production
build。整改者 Failsafe XML 可交叉支持 Phase00 **6/6**，但该 XML 不改变测试内容本身的
假闭环，也没有作为不可变原始制品归档。

本报告只判定 Phase 0 整改候选，**不授权 merge、push、部署、发布或开始最终全量审计**。
遵照用户安全边界，本轮没有启动或连接 Docker、MySQL、Redis、MinIO、HTTP 服务、浏览器或
网络，没有执行扫描、fuzz、故障注入、凭据、权限或攻击性操作。

### Score Dashboard

```text
Incremental     ██████░░░░  6.0  B   关键实现有进展，但三项阻断仍在
Testing         █████░░░░░  5.0  B   一条假闭环，必需 suite 未锁定
Release         █████░░░░░  5.5  B   CI 可绿但阶段证据可静默消失
Configuration   ███████░░░  7.0  A   dev 文档端点修复；prod UI 壳仍公开
Documentation   ████░░░░░░  4.5  C   多个权威入口互相冲突
Supply Chain    ████████░░  7.5  A   lock 完整；发布 provenance 留 WS-7
Security        ████████░░  7.5  A   无新增高风险面；prod docs 收口不完整
─────────────────────────────────────
Overall         ██████░░░░  6.1  B
```

Each dimension is scored 0.0–10.0. **Higher = better.** Overall is the
arithmetic mean of the seven dimensions, rounded to one decimal place.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 3 | 3 | 0 |
| Low | 4 | 4 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **7** | **7** | **0** |

## 2. Project Map

- 根 `pom.xml`：九模块 Maven reactor、Failsafe 与本轮新增 Checkstyle `validate` 门禁。
- `.github/workflows/ci.yml`：MySQL/Redis/MinIO 依赖、后端 `mvn verify`、前端
  `npm ci → lint → type-check → build`。
- `platform-boot`：Spring Boot 入口、HTTP/security 配置、Phase 0 真实依赖 IT 与 DataScope
  单测。
- `platform-file`：MinIO 对象写入和 `file_object` 元数据；T-FILE-1 秒传合同的实际落点。
- `frontend`：Vue 3/Vite/TypeScript 应用与本轮新增 ESLint 9 flat config。
- `docs/phase-00-脚手架.md`：Phase 0 当前高优先级验收清单与测试用例。
- `docs/README.md`、`tasks.md`、`docs/CURRENT-EXECUTION-PLAN.md`、
  `docs/REVIEW-GATE.md`、根 `README.md`：全局 DoD、任务、活动计划和阶段状态入口。
- `docs/reviews/evidence/phase00-remediation-2026-07-27/`：整改者门禁摘录，不是独立原始日志包。

本轮增量没有 Flyway、业务状态机、权限编码、API 请求/响应结构或生产数据库写路径变化；
风险集中在测试真实性、CI 阶段门禁、文档权威链与文档端点环境隔离。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `dd04f21..715b5f1` 全 24 路径、父子链、提交与工作区 blob | 未重开其它 Phase 的产品正确性 |
| Testing Authenticity | High | 两组新增测试源码、生产调用链、XML、计数、时间链、独立 257/257 | 未连接真实 MySQL/Redis/MinIO |
| Release | High | CI workflow、Maven lifecycle、目标 suite 发现规则、阶段 gate | 未运行 GitHub Actions、merge、push、deploy 或 rollback |
| Configuration | Medium | dev/prod application 配置、security allowlist、锁定 UI JAR | 未启动 prod profile 或发送 HTTP 请求 |
| Documentation | High | Phase 0、全局 DoD、tasks、统一计划、README、review gate、提交材料 | 历史阶段报告只按引用抽查 |
| Supply Chain | Medium | Maven/npm 固定版本、package-lock provenance/integrity、workflow 镜像/Action 引用 | 未联网查 CVE、下载、生成 SBOM 或签名 |
| Security | Medium | 公开文档路径、prod 开关、secret diff 与依赖变化 | 未执行扫描、攻击、认证、凭据或权限测试 |

## 3. Incremental Change Summary

### Change Summary

- Baseline: `dd04f21387f76adc7fc5d1443903ada0bec4d4c1`
- Candidate: `715b5f1c67a9943c5914eda08e485a1463c0524d`
- Total files changed: 24
- Lines added: 2917
- Lines deleted: 188
- Commits in range: 1
- Authors: `[REDACTED_USER]`

### Change Categories

以下为每个路径的主分类，不重复计数：

- New features: 0 files
- Bug fixes: 1 file
- Refactoring / lint cleanup: 8 files
- Dependency updates / lockfile: 2 files
- Configuration / CI: 4 files
- Tests: 2 files
- Documentation / evidence: 7 files

### Previous Finding Closure Matrix

| 上轮退回项 | 本轮结果 | 独立结论 |
|------------|----------|----------|
| Phase 0 验收基线过时、十项无法判定 | Phase 文档已修订并勾选，但全局 DoD/tasks/统一计划/README/review gate 仍冲突 | **部分关闭，Medium 阻断** |
| 后端/前端 lint 缺失且未进 CI | Checkstyle + ESLint 已接入并独立通过 | **实现关闭** |
| `/doc.html`、异常、审计、DataScope、预签名缺可重复证据 | 新增 6 IT + 9 UT；多数证据有效 | **主体关闭，但 T-FILE-1 假闭环且 CI 未锁定目标 suite** |

### Risk Delta

- Existing risks fixed: 4（两端 lint、api-docs 回归、统一异常/审计/TTL 证据、DataScope 分支单测）
- Existing risks not fully closed: 2（权威基线、可重复阶段证据）
- New false-confidence risk introduced: 1（T-FILE-1 测试名与真实行为不一致）
- New Critical / High: 0
- Phase gate: **CHANGES_REQUESTED**

### Test Coverage Delta

- New test files: 2
- New test cases: 15（Failsafe 6 + Surefire 9）
- Deleted tests: 0
- Independent offline Surefire: **257/257**, 0 failure/error/skip
- Candidate real-dependency XML: Phase00 **6/6**, 0 failure/error/skip
- Independent lint/build: Checkstyle 9 模块、ESLint、type-check、Vite build、Maven package 均 PASS
- Remaining gap: 1 个新增 IT 没有执行其声称的第二次上传；CI 没有 Phase 0 精确 suite/用例/计数门禁

### Approval Recommendation

**Request changes.**

先关闭 3 个 Medium，再提交下一轮增量复核。Low 可以同轮修正；若延后，必须在候选材料和
统一计划中明确保留，不得将 10/10、生产文档关闭或证据同源性写成已完全证明。

## 4. Top Risks

1. **Medium — T-FILE-1 是假闭环：** 测试只上传一次，无法证明第二次上传不会产生第二对象和记录。
2. **Medium — CI 未锁定 Phase 0 必需 suite：** 目标测试被删、改名或漏发现时，宽泛全量测试仍可绿。
3. **Medium — 权威验收基线仍冲突：** U-004 的原始治理根因没有从全局入口消除。
4. **Low — 生产 `/doc.html` 静态 UI 仍公开：** 动态 OpenAPI 已关，但“无试调界面”配置说明不成立。
5. **Low — 候选证据不是可验证原始制品：** 只有人工摘录，无完整日志/XML/manifest/candidate marker。
6. **Low — 10/10 的部分证据锚点过度：** exact Compose、全部参数和前端登录跳转没有本轮精确证据。
7. **Low — T-DS-1 权威口径与测试层级漂移：** 文档写 `=`/ALL，测试实际为 `IN`/SCHOOL 且只测 handler。

## 5. Detailed Findings

### Finding: T-FILE-1 没有执行“同 MD5 二次上传”

- Severity: Medium
- Confidence: High
- Category: Testing / Data Integrity / Backend API
- Status: Confirmed
- Affected area: Phase 0 文件秒传合同与 `Phase00ScaffoldIT`
- Evidence:
  - File: `docs/phase-00-脚手架.md:57,95`
  - Function / Module: `FileService` 秒传能力与 T-FILE-1
  - Relevant behavior: 权威用例要求同一 MD5 二次上传时命中既有对象且不重复存储。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase00ScaffoldIT.java:261-278`
  - Function / Module: `sameMd5LookupHitsExistingObjectWithoutSecondCopy`
  - Relevant behavior: 只调用一次 `fileService.upload`，随后调用 `getByMd5` 并断言数据库本来就只有一行。
  - File: `platform-file/src/main/java/cn/edu/gpnu/platform/file/service/impl/FileServiceImpl.java:60-90,336-351`
  - Function / Module: `upload` / `getByMd5`
  - Relevant behavior: `upload` 每次生成随机 objectKey、写 MinIO、插入一行；查询方法与上传方法没有闭环。
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/controller/FileController.java:36-40`
  - Function / Module: `/api/file/upload`
  - Relevant behavior: 通用上传入口固定把 `md5` 传为 null。
- Problem: 新测试证明的只是“写入一行后可按摘要查到这一行”，不是“第二次上传被去重”。测试即使在
  第二次真实上传会创建新对象、新记录时仍然通过；生产通用上传路径也没有调用摘要查询。
- Why it matters: Phase 0 被标为 10/10 时会把尚未实现或尚未正式退役的秒传合同当成已回归保护，
  后续容量、幂等与用户体验决策都会建立在错误前提上。
- Realistic failure scenario: 客户端连续上传同一文件两次；两次请求都产生不同 UUID 对象和两行
  `file_object`，但 CI 中的 `sameMd5...` 仍保持绿色。
- Minimal fix: 先按 R10 决定合同。若保留秒传，增加受 `bizType`、主体和授权边界约束的摘要预检/
  上传协议，实现第二次请求复用同一 fileId/objectKey；若不保留，正式修改 Phase 0 与 tasks，
  删除“秒传不重复存储”的验收声明，不用查询测试冒充上传去重。
- Better long-term fix: 使用服务端计算的强摘要、业务域隔离、唯一约束/幂等键与并发仲裁，避免
  仅信任客户端 MD5，也避免跨用户复用泄露对象存在性。
- Regression test suggestion: 在隔离 MinIO/数据库中顺序和并发各执行两次同内容上传，断言响应
  fileId/objectKey、数据库行数、对象数与授权边界；若合同退役，则测试明确断言允许重复并更新规格。
- Estimated effort: 1–2 days

### Finding: CI 没有锁定 Phase 0 必需 suite 与用例计数

- Severity: Medium
- Confidence: High
- Category: Release / Testing Authenticity
- Status: Confirmed
- Affected area: GitHub Actions 后端门禁与 Phase 0 阶段证据
- Evidence:
  - File: `.github/workflows/ci.yml:84-88`
  - Function / Module: backend `mvn verify`
  - Relevant behavior: CI 只执行宽泛 reactor verify，没有 Phase 0 后验 gate。
  - File: `pom.xml:95-110`
  - Function / Module: Maven Failsafe
  - Relevant behavior: 只包含 `**/*IT.java`；未要求 Phase00 suite、精确 testcase 或计数。
  - File: `docs/reviews/evidence/phase00-remediation-2026-07-27/gates-2026-07-27.txt:24,44,74`
  - Function / Module: 整改者门禁摘录
  - Relevant behavior: 6/6、9/9 与总数是人工摘录，workflow 不解析该文件或原始 XML。
  - Trigger condition: `mvn -B -ntp -o -pl platform-common test`
  - Relevant behavior: 独立实证 `No tests to run` 后仍为 `BUILD SUCCESS`。
- Problem: 项目已有 257 个 UT 与 212 个 IT；删除、改名或漏发现本轮两个目标 suite，不会让宽泛
  `mvn verify` 变成零测试构建，因此 CI 仍可能通过。阶段验收依赖的 6/6 和 9/9 没有成为机器门禁。
- Why it matters: “新增测试文件存在”不等于“阶段合同持续被执行”。一旦重构或插件发现规则变化，
  Phase 0 可静默失去关键证据而没有红灯。
- Realistic failure scenario: `Phase00ScaffoldIT` 被误命名为不匹配 `*IT`，其它 206 个 IT 全绿；
  CI 成功，阶段报告仍引用旧的 6/6 摘录。
- Minimal fix: 在 `clean verify` 后解析本轮新生成的 Surefire/Failsafe XML，硬校验 suite 名、
  tests=6/9、failure/error/skip=0 及精确 testcase 集合；删除旧报告后再跑，并把 gate 脚本本身接入 CI。
- Better long-term fix: 建立统一阶段 gate manifest，按 Phase 声明必需 suite、用例、运行环境、候选
  SHA 与制品哈希，由 CI 生成和验证，而不是在提交材料中手工摘录。
- Regression test suggestion: 对 gate 做离线自测：缺 XML、旧 XML、suite 改名、少一个 testcase、
  重复 testcase、非零 skip 与错误 candidate SHA 都必须失败。
- Estimated effort: 0.5–1 day

### Finding: U-004 的权威验收基线同步仍未闭环

- Severity: Medium
- Confidence: High
- Category: Documentation / Release / Maintainability
- Status: Confirmed
- Affected area: Phase 0 全局 DoD、任务表、执行计划与复核状态
- Evidence:
  - File: `docs/README.md:21,31`
  - Function / Module: 全局 DoD / 测试规范
  - Relevant behavior: 仍称 Checkstyle、ESLint 未落地且不得宣称已执行。
  - File: `tasks.md:48,55-56`
  - Function / Module: T-008 / T-010
  - Relevant behavior: 仍要求 pnpm、mock、Spotless/Checkstyle、ESLint/Prettier 与格式化零差异。
  - File: candidate snapshot `715b5f1:docs/CURRENT-EXECUTION-PLAN.md:134,143`
  - Function / Module: U-001 / WS-6
  - Relevant behavior: 冻结候选中一处把 ESLint 留给 WS-6，一处又把最小 lint 写为尚待引入；
    独立复核后活动计划已同步结论，但不反向改变候选快照。
  - File: `README.md:99`
  - Function / Module: 项目进度
  - Relevant behavior: 无条件声明 Phase 0 已复核通过。
  - File: `docs/REVIEW-GATE.md:75`
  - Function / Module: Phase 0 特例
  - Relevant behavior: 仍写 Phase 0 self-verified、无需补复核报告。
  - File: `docs/phase-00-脚手架.md:6-16,87-90`
  - Function / Module: 本候选新口径
  - Relevant behavior: 已正式改为 npm、真实认证与 Checkstyle/ESLint 最小门禁。
- Problem: Phase 专属文档虽具有更高规格优先级，但开发者开工必读的全局 DoD、活动计划和 review
  gate 仍给出相反指令；U-004 正是因这条权威链漂移被退回，本轮不能只修改最低范围文档后宣称关闭。
- Why it matters: 后续执行者无法从规定入口唯一判断“什么已经落地、WS-6 还剩什么、Phase 0 是否
  已通过”，容易重复实施、跳过复核或错误启动最终审计。
- Realistic failure scenario: 执行者按 `docs/README.md` 和 WS-6 重新引入 ESLint，另一执行者按
  `README.md`/`REVIEW-GATE.md` 跳过本轮退回状态；两条计划同时继续并产生互相覆盖的提交。
- Minimal fix: 同步上述五个入口，统一为“Phase 0 最小 Checkstyle/ESLint 已落地；WS-6 只保留
  格式化、更严格规则、Vitest/Playwright/a11y；Phase 0 当前 CHANGES_REQUESTED，独立 PASS 后才
  放行最终全量审计”。
- Better long-term fix: 只保留一个机器可读的阶段状态/能力清单，由 README、tasks 和阶段索引生成
  展示；历史口径移入显式归档而非继续留在活动入口。
- Regression test suggestion: 增加文档一致性脚本，检查 Phase 状态、包管理器、已落地门禁与
  WS-6 剩余范围在所有权威入口中唯一一致。
- Estimated effort: 1–2 hours

### Finding: 生产环境仍公开 Knife4j 静态 UI

- Severity: Low
- Confidence: High
- Category: Security / Configuration
- Status: Confirmed
- Affected area: production API documentation exposure
- Evidence:
  - File: `platform-boot/src/main/resources/application-prod.yml:5-13`
  - Function / Module: prod springdoc / Knife4j switches
  - Relevant behavior: 注释声明生产不暴露 API 文档与试调界面。
  - File: `platform-security/src/main/java/cn/edu/gpnu/platform/security/config/SecurityConfig.java:60-62`
  - Function / Module: public request matchers
  - Relevant behavior: 所有 profile 都 permitAll `/doc.html`、`/webjars/**` 与文档路径。
  - File: `platform-boot/src/main/resources/application.yml:21-27`
  - Function / Module: Knife4j compatibility workaround
  - Relevant behavior: 明确说明 `doc.html` 是不受 `knife4j.enable` 控制的静态 webjar。
  - Runtime artifact: `knife4j-openapi3-ui-4.5.0.jar`
  - Relevant behavior: 独立只读列包确认包含 `META-INF/resources/doc.html` 和 webjars assets。
- Problem: prod 已关闭动态 OpenAPI handler，但静态 UI 壳和 assets 仍被 Spring 资源映射并公开放行；
  配置注释所称“无试调界面”不成立。
- Why it matters: 当前没有 OpenAPI payload，直接泄露影响有限；但仍扩大生产攻击面、暴露技术指纹，
  并让运维误以为文档表面已完全关闭。
- Realistic failure scenario: 外部访问者在生产请求 `/doc.html` 得到 Knife4j UI 和版本相关资产，
  虽然 `/v3/api-docs` 不可用，但发布验收把此路径误判为不存在。
- Minimal fix: 按 profile/配置条件化安全规则，在 prod 对 `/doc.html`、`/webjars/**`、
  `/swagger-ui/**`、`/v3/api-docs/**` 明确 deny；同步注释。
- Better long-term fix: 将 API 文档 UI 作为非生产构建/profile 的显式可选依赖，并为公开文档面建立
  单一配置属性与启动期一致性校验。
- Regression test suggestion: 自然退出的 prod-profile negative IT，逐一断言上述路径为 403/404，
  dev-profile 则保留 `/doc.html` 与标准 OpenAPI 正向。
- Estimated effort: 2–4 hours

### Finding: 候选门禁归档缺少不可变同源证据

- Severity: Low
- Confidence: High
- Category: Testing Authenticity / Release / Supply Chain
- Status: Confirmed
- Affected area: `phase00-remediation-2026-07-27` evidence bundle
- Evidence:
  - File: `docs/reviews/evidence/phase00-remediation-2026-07-27/gates-2026-07-27.txt:1,11-97`
  - Function / Module: candidate gate evidence
  - Relevant behavior: 文件自称“整改者自测/证据摘录”，内容为选择性 summary。
  - File: `docs/reviews/phase-00-remediation-submission-2026-07-27.md:104-106`
  - Function / Module: evidence reference
  - Relevant behavior: 明确把归档称为“原始输出摘录”，未附原始 XML、完整日志或 manifest。
  - File: `docs/reviews/phase-00-remediation-submission-2026-07-27.md:41-45`
  - Function / Module: lint 捕获计数
  - Relevant behavior: 写“8 处/后端 5 处 import”，但列举的是 5 个 Java 文件、6 个 import occurrence，
    再加 3 个前端问题应为 9 个 occurrence。
- Problem: 归档没有完整命令日志、exit code、候选 SHA、原始 XML、文件哈希、精确 target marker
  或自动生成 manifest；手工摘录无法独立证明这些行来自同一次候选运行。
- Why it matters: 当前本机 target XML 与源码、class、时间链可以交叉增强可信度，但 target 会被后续
  测试覆盖，也不随 Git 克隆；未来只能相信人工转录。
- Realistic failure scenario: 后续一次局部运行覆盖 XML，提交材料仍保留旧总数；审核者无法判断摘录
  对应哪个 SHA、哪个 schema 或是否来自同一命令。
- Minimal fix: gate 删除旧报告后运行，归档完整脱敏日志、目标 XML、summary、candidate SHA、
  工具版本、目标 schema marker、起止时间、exit code 和 SHA-256 manifest。
- Better long-term fix: 由 CI 生成不可变、有 provenance 的 Phase evidence artifact，并把摘要从
  manifest 自动渲染到提交材料。
- Regression test suggestion: 验证 manifest 中每个文件哈希、candidate SHA、suite/testcase 集合、
  时间顺序和 target marker；任一漂移必须失败。
- Estimated effort: 2–4 hours

### Finding: 10/10 清单中的三项证据锚点超出实际证明范围

- Severity: Low
- Confidence: High
- Category: Documentation / Testing Authenticity
- Status: Confirmed
- Affected area: Phase 0 acceptance checklist
- Evidence:
  - File: `docs/phase-00-脚手架.md:77-80`
  - Function / Module: Compose 与 `sys_param` 验收项
  - Relevant behavior: 勾选 exact `docker-compose.dev.yml up` 与全部默认参数。
  - File: `docs/reviews/evidence/phase00-remediation-2026-07-27/gates-2026-07-27.txt:92-94`
  - Function / Module: Compose evidence
  - Relevant behavior: 只归档两份 Compose `config --quiet`，真实 verify 使用另一个等价依赖栈。
  - File: `docs/reviews/phase-00-remediation-submission-2026-07-27.md:108-115`
  - Function / Module: known boundaries
  - Relevant behavior: 候选自己承认没有对 README 参数清单做逐 key 专项断言。
  - File: `docs/phase-00-脚手架.md:87-88`
  - Function / Module: 前端登录与路由验收
  - Relevant behavior: 引用 Phase 2 Java IT 与 Phase 35 浏览器材料，但它们不是本轮前端登录跳转证据。
- Problem: 配置可解析、等价依赖栈可运行、业务测试读取若干参数和历史浏览器验收都是有价值证据，
  但不等于清单逐字要求的 exact Compose、全部 key 与当前前端登录跳转。
- Why it matters: 全部勾选会抹掉“已证明”“替代证明”“只做静态核对”“留最终审计”的边界，
  使下一轮无法知道还缺哪条精确证据。
- Realistic failure scenario: `.env`/dev Compose 的实际启动链或某个新增参数 key 回归，当前抽查仍绿；
  清单继续显示 10/10，问题直到最终审计或部署才出现。
- Minimal fix: 对每项标明证据类型和限制；补 exact dev Compose 健康证据、fresh-schema 参数矩阵
  断言与真实前端登录 E2E，或把措辞收窄为本轮实际证明的范围。
- Better long-term fix: 将十项清单变为机器可读验收矩阵，每项绑定 gate ID、环境、artifact 与
  最后成功 candidate SHA。
- Regression test suggestion: 参数矩阵逐 key/value/type 断言；dev Compose exact smoke；
  Vite 页面登录后断言 token、`/me`、目标 route 和错误态。
- Estimated effort: 0.5–1 day

### Finding: T-DS-1 权威口径与测试层级仍有漂移

- Severity: Low
- Confidence: High
- Category: Testing / Documentation
- Status: Confirmed
- Affected area: Phase 0 DataScope acceptance
- Evidence:
  - File: `docs/phase-00-脚手架.md:94`
  - Function / Module: T-DS-1
  - Relevant behavior: 仍写 COLLEGE SQL 含 `college_id = ?`，ALL 无范围条件。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/config/DataScopeSqlHandlerTest.java:14-20,31-52`
  - Function / Module: DataScope handler unit test
  - Relevant behavior: 注释承认现行实现为 `IN (...)`；测试实际使用 SCHOOL，且直接调用 handler。
  - File: `docs/reviews/phase-00-remediation-submission-2026-07-27.md:60-63`
  - Function / Module: remediation explanation
  - Relevant behavior: 差异只写在候选材料和测试注释，没有按 R10 改权威测试用例。
- Problem: 9 个单测能证明 handler 分支表达式，但没有证明 `@DataScope` aspect → context →
  MyBatis interceptor → 真实 mapper SQL 的组合链；权威用例名称也仍使用过时枚举/SQL 形态。
- Why it matters: handler 单体正确时，注解漏接、context 生命周期、插件顺序或 mapper alias 错误仍可能
  逃逸；规格与测试又会对“ALL/SCHOOL、`=`/`IN`”给出不同答案。
- Realistic failure scenario: 某 mapper 漏 `@DataScope` 或 alias 不匹配，handler 单测 9/9 仍绿，
  实际查询不追加学院条件。
- Minimal fix: 按 R10 把 T-DS-1 改为现行 SCHOOL/COLLEGE 集合合同，并增加至少一条真实 mapper SQL
  capture/集成测试，证明注解到最终分页 SQL 的组合链。
- Better long-term fix: 建立按数据范围类型和注册表的参数化集成矩阵，复用生产 interceptor 顺序并
  对空范围统一失败关闭。
- Regression test suggestion: 真实 MyBatis mapper 在 COLLEGE、SCHOOL、SELF、NONE 与空集合下执行，
  捕获最终 SQL/结果集；专门覆盖 alias 匹配和分页 count/data 双 SQL。
- Estimated effort: 0.5–1 day

## 6. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 新测试源码、生产调用链、Surefire/Failsafe XML、Git blob、时间链、独立离线门禁
- Exclusions / limits: 未由 Codex 连接真实依赖、启动 HTTP/浏览器或重跑 Failsafe

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| `Phase00ScaffoldIT` 文档/异常/审计/TTL 5 项 | High | 运行证据是本机 target，不是不可变 artifact | Keep，补 manifest |
| `Phase00ScaffoldIT` T-FILE-1 | Low | 第二次上传实际重复仍会绿 | Rewrite |
| `DataScopeSqlHandlerTest` | Medium | handler 分支真实，但组合链可逃逸 | Keep + add integration |
| Maven Surefire 全量 | High | 257/257 离线独立重跑 | Keep |
| Checkstyle / ESLint | High | 两端门禁真实执行且零 warning | Keep |
| Phase 0 CI gate | Low | 必需 suite 可静默消失 | Add exact gate |

### Valuable Tests

- `/doc.html` + `/v3/api-docs` 正向请求实际捕获并防止 Knife4j/springdoc 二进制回归。
- BizException、validation、404/500 的 HTTP 响应与堆栈不泄漏断言覆盖真实异常链。
- 认证上传后的 audit operator/time/IP 与 3 秒预签名 URL 正向/自然过期 403 有真实依赖价值。
- DataScope 的空 COLLEGE 集合、NONE、SELF、ASSIGNED 与 alias 双向分支有明确失败关闭价值。

### Suspicious Tests

- `sameMd5LookupHitsExistingObjectWithoutSecondCopy` 的名称和说明声称没有第二份 copy，但执行体没有
  第二次上传或第二次 put。
- Phase 文档将历史 Java IT/浏览器材料组合成当前前端登录跳转证据，无法逐步追溯。
- 候选证据为人工摘录，缺少 gate 对 XML 与 testcase 集合的机器验证。

### Missing Tests

- 顺序及并发同内容二次上传的对象/数据库/授权不变量，或明确退役该合同。
- Phase00 6 个和 DataScope 9 个精确 testcase 的 CI 后验门禁。
- prod profile 下文档 UI/API/assets 的 403/404 负向。
- fresh-schema `sys_param` 全量参数矩阵。
- `@DataScope` 注解到真实 mapper/pagination SQL 的组合链。
- 当前前端登录、`/me`、路由跳转和错误状态 E2E。

## 7. Release Concerns

- Coverage: High
- Inspected evidence: Maven lifecycle、CI workflow、测试发现规则、Git 范围、阶段计划与候选证据
- Exclusions / limits: 未执行 GitHub Actions、merge、push、deploy、release 或 rollback

- Checkstyle 与 ESLint 已从“手工可跑”变成 CI 实际步骤，这是本轮明确的发布改进。
- 后端仍需一个 Phase 0 精确后验 gate；宽泛全量绿不等于阶段证据存在。
- `mvn -DskipTests package` 产出可执行 fat JAR，但本轮没有由 Codex 启动它；RANDOM_PORT IT 只作为
  Spring 上下文交叉证据。
- Phase 0 下一轮 PASS 只会放行最终全量审计；稳定发布仍受 WS-6、WS-7、历史 Low 与最终审计约束。
- workflow Actions 和基础镜像没有 digest 固定是既有 WS-7 范围，不重复计为本候选新增 finding。

## 8. Configuration Safety Analysis

- Coverage: Medium
- Inspected evidence: `application.yml`、`application-prod.yml`、security request matchers、UI JAR 内容
- Exclusions / limits: 未启动 dev/prod profile、未访问任何端点、未读取仓库外环境

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| EnvironmentSeparation | 1 | prod docs paths / `SecurityConfig` | prod 明确 deny 静态 UI 与 assets |
| ConfigDocs | 1 | `pom.xml` compatibility comments | 统一为“静态 UI 可用，增强层不兼容” |
| UnsafeDefault | 0 | 本轮新增配置 | 保持 prod 动态 docs 默认关闭 |
| SecretConfig | 0 | candidate diff | 未发现新密钥或明文凭据 |

- `knife4j.enable=false` 能关闭不兼容的增强自动配置，标准 springdoc 文档在 dev 恢复。
- `pom.xml:39-41,69` 仍称 2.8.x 与 Knife4j 4.5.0 “兼容”，应改为只说明依赖解析兼容；
  增强 customizer 的二进制接口并不兼容。
- prod 的 `springdoc.api-docs.enabled=false` 是失败关闭的，但不控制 Knife4j 静态资源。

## 9. Documentation Analysis

- Coverage: High
- Inspected evidence: 全部活动 Phase 0 入口、候选材料、证据摘录和相关配置注释
- Exclusions / limits: 未逐字重审全部 53 个 Phase 历史档案

### Documentation Summary

| Subtype | Count | Affected Docs | Recommended Action |
|---------|-------|---------------|-------------------|
| StaleDocs | 1 Medium | global DoD/tasks/plan/README/review gate | 统一 Phase 0 当前口径与状态 |
| ApiDocs | 1 Low | prod docs 注释、POM 兼容注释 | 区分动态 API、静态 UI、增强层 |
| DecisionRecord | 1 Low | T-DS-1 / T-FILE-1 | 用 R10 明确保留或退役合同 |
| DeveloperDocs | 1 Low | 10/10 evidence anchors | 标注 exact、equivalent、historical、unverified |

- 候选提交材料清楚声明“整改者自测不是独立 PASS”，这一点符合项目 gate。
- 当前统一计划能够区分 Phase 0 PASS、最终全量审计和 release GO；但同一计划内 WS-6 描述仍冲突。
- 历史口径应保存在提交材料/DEVLOG，而不是继续出现在开工必读的活动规范中。

## 10. Supply Chain / Reproducibility Analysis

- Coverage: Medium
- Inspected evidence: `pom.xml` 固定版本、`package.json`、lockfile、installed top-level tree、CI 引用
- Exclusions / limits: 遵照用户要求未联网查 advisory、未扫描依赖、未生成 SBOM/签名/attestation

### Supply Chain Summary

| Subtype | Count | Affected Surface | Recommended Action |
|---------|-------|------------------|-------------------|
| DependencyProvenance | 0 candidate blockers | npm lock / Maven versions | 保持 lock 与精确 plugin 版本 |
| Reproducibility | 1 Low | Phase evidence artifact | 增加 SHA/manifest/原始 XML |
| CIIntegrity | 0 candidate blockers | Actions / service images | 由 WS-7 固定 SHA/digest |
| ArtifactProvenance | 1 Low | Phase 0 evidence | CI 生成带 candidate SHA 的 artifact |
| RegistryHygiene | 0 | candidate additions | 未发现新增 install script |

- Checkstyle plugin/core 版本明确固定；ESLint 顶层依赖由 package-lock v3 固定并带 resolved/integrity。
- 本轮新增 npm lock entries 未发现缺失来源/完整性或新增 install script。
- GitHub Actions 仍以 `@v4`、数据库/缓存服务以 tag 而非 digest 固定；这是已有 WS-7 发布债，
  不是本单提交新增回归。

## 11. Security Concerns

- Coverage: Medium
- Inspected evidence: prod 文档暴露、security allowlist、candidate secret diff、上传摘要边界
- Exclusions / limits: 未执行扫描、fuzz、攻击性请求、凭据、权限、网络或服务操作

- 本轮没有新增权限点、鉴权旁路、敏感字段、外部 URL 或仓库密钥。
- prod 静态 UI 为 Low：动态 spec 已关闭，未确认敏感 API 描述泄露，但公开资产违背环境收口声明。
- 若保留 MD5 秒传，不能用未授权的全局“摘要存在”作为跨用户对象复用信号；必须绑定业务域、主体、
  访问授权，并优先服务端计算更强摘要。
- 预签名 IT 真实证明 TTL 自然过期，不证明所有业务签发入口的授权；后者继续由各业务 Phase 审计。

## 12. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Test behavior, not labels | 1 | Medium | T-FILE-1 |
| No fake green / fail fast | 1 | Medium | CI target suite gate |
| Single source of truth | 1 | Medium | Phase 0 governance docs |
| Environment separation | 1 | Low | prod documentation UI |
| Evidence-driven release | 2 | Low | evidence artifact / checklist anchors |
| Specification consistency | 1 | Low | T-DS-1 |

### Principles Respected

- **Small scoped change:** 产品行为增量很小，大部分为门禁、测试和说明。
- **Fail closed:** DataScope 空集合/NONE、prod 动态 api-docs 与异常响应边界保持失败关闭。
- **Reproducible local gates:** Maven 离线测试/打包和前端 lint/type-check/build 可自然退出并独立重放。
- **Pinned dependency graph:** 新增 npm 工具进入 lockfile，Maven plugin/core 使用明确版本。
- **Honest phase status:** 候选材料没有自行宣称独立 PASS 或发布 GO。

## 13. Recommended Fix Order

### Fix Immediately

1. 明确 T-FILE-1 合同并实现真实二次上传反例，或按 R10 正式退役。
2. 给 CI 增加 Phase00 6/6、DataScope 9/9 的精确 XML/testcase gate。
3. 同步所有权威入口，消除 U-004 原始基线冲突。

### Fix Before Stable Release

1. prod 拒绝 `/doc.html`、webjars、Swagger 与 api-docs，并补 profile 负向。
2. 将完整日志/XML/manifest/candidate marker 变成 CI evidence artifact。
3. 补 fresh-schema 参数矩阵、exact dev Compose 与当前前端登录 E2E。
4. 对齐 T-DS-1 规格并补注解到真实 SQL 的集成链。

### Schedule Later

1. 用机器可读 Phase manifest 统一状态、必需 suite、环境与证据。
2. 按 WS-6 建 Vitest、Playwright、a11y 和渐进严格 lint/format。
3. 按 WS-7 固定 Actions SHA、镜像 digest，并生成 SBOM/provenance。

### Ignore for Now

- 不因为本轮没有联网依赖扫描而重复制造一个候选 blocker；该项由 WS-7 和最终全量审计承接。
- 不把既有前端 >900 kB chunk warning 误判为 Phase 0 整改回归。

## 14. Quick Wins

| Action | Effort | Impact |
|--------|--------|--------|
| 同步 `docs/README`、tasks、统一计划、README、review gate | 1–2 h | 关闭 U-004 权威链冲突 |
| 给 Phase 0 gate 加 XML suite/count/testcase 校验 | 2–4 h | 防止目标测试静默消失 |
| 修正“5 文件/6 imports/9 occurrences”与 Knife4j 兼容注释 | 15–30 min | 消除证据和配置误导 |
| prod security matcher 增加 docs 路径负向 | 2–4 h | 收口生产静态 UI |
| 为每个 checklist 项标注 exact/equivalent/historical | 1 h | 保留证据边界 |

## 15. Long-term Refactor Plan

1. 建立 `phase-gates.json` 一类机器可读 manifest，声明阶段状态、必需 suite/testcase、运行环境、
   candidate SHA、artifact 哈希与证据路径。
2. 让 CI 从 manifest 执行/校验门禁并生成报告摘要；Markdown 只展示，不再成为第二套事实来源。
3. 将文件去重作为明确的 API/数据完整性能力设计：业务域和主体隔离、服务端强摘要、唯一约束、
   并发幂等与对象生命周期对账。
4. 最终全量审计统一复查 prod 文档暴露、真实前端登录、fresh schema 参数矩阵、CI provenance
   与所有阶段残留 Low；Phase 0 本轮不得提前触发该审计。
