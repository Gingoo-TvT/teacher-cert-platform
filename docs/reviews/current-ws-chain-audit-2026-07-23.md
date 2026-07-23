# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform 当前 WS 增量链
**Audit mode:** security, configuration, data-integrity, testing-authenticity, release
**Date:** 2026-07-23
**Reviewer:** Codex（Claude 不可用期间，经用户明确授权执行独立复核）

---

## 1. Executive Summary

本次审计冻结 `e4f8228..338bc91`，覆盖 WS-1、WS-2、WS-10、WS-13、WS-3。复核者未采信实现方自报结果，使用两个全新 schema、真实 MySQL/Redis/MinIO 独立重跑专项反例、demo 常驻回归、前端检查、Compose 解析和全量 Maven 门禁。WS-1、WS-2、WS-10、WS-13 无 Blocker/Major，判定 PASS；WS-3 存在 3 个已确认 High，判定 CHANGES REQUESTED，当前提交链不能作为可发布版本合并。

WS-3 的直接上传传输路径本身具备分片约束、CAS 状态、对象键限制、短期预签名、鉴权播放和真实 MinIO 测试，但业务可信边界仍有缺口：服务端没有解析实际媒体内容和真实时长，跨用户秒传把客户端声明的 64 位指纹当作持有证明，同时 CI 没有提供新增集成测试依赖的 MinIO，前端 job 也未执行项目要求的类型检查。265/265 绿证明现有断言可通过，不足以反证这三项风险。

### Score Dashboard

```
Security        ███████░░░  6.5  B   RBAC/凭据边界扎实，但跨用户秒传仍信任客户端指纹
Stability       ███████░░░  6.5  B   并发与上传状态受控，但伪装媒体可进入待评审状态
Testing         ███████░░░  7.0  A   真实依赖 265/265 通过，关键媒体真实性反例缺失
Release         █████░░░░░  5.0  B   本地门禁可复现，仓库 CI 缺 MinIO 与前端 type-check
─────────────────────────────────────
Overall         ██████░░░░  6.3  B
```

各维度 0.0–10.0，分数越高越好。总体分为已评估四个工程维度的平均值；未在本轮选定的性能、可维护性和视觉设计不计分。

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 3 | 3 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **3** | **3** | **0** |

## 2. Project Map

当前链路在 Spring Boot 多模块后端、Vue 3 前端和 Docker Compose 依赖栈上增量实现：WS-1 修改集成测试夹具隔离；WS-2 加固管理员、职工和学生凭据生命周期；WS-10 在 Spring 配置加载早期执行 profile fail-fast；WS-13 在数据库快照与串行锁下实施 RBAC 授权天花板；WS-3 由浏览器直接向 MinIO 执行 S3 multipart，后端负责授权、会话状态、预签名、定稿登记和业务校验。最敏感边界是“客户端声明 → 后端可信业务状态”的转换，以及“本地测试契约 → CI 可复现门禁”的转换。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | High | WS-2/WS-13 生产代码、单元/集成反例；WS-3 鉴权、对象键、秒传路径和 VO 暴露面 | 未做公网部署、TLS 或第三方渗透测试；这些属于后续 WS-5/全量审计 |
| Configuration | High | `RuntimeProfileGuard`、配置文件、dev/prod Compose、启动型单测 | 未验证真实生产域名、证书和 secret store |
| Data integrity | High | 上传会话 CAS、RBAC 锁、凭据 CAS、媒体定稿与秒传数据流、真实 MySQL/MinIO | 未做故障注入式网络分区或 MinIO 节点宕机 |
| Testing authenticity | High | 两个全新 schema、demo 常驻双跑、真实 MySQL/Redis/MinIO、Surefire/Failsafe 报告 | 未启动浏览器做人工 DevTools 网络面板观察；前端请求路径由源码和真实 MinIO IT 交叉验证 |
| Release | High | GitHub Actions、前后端一次性门禁、两个 Compose 的 `config --quiet` | 未触发远端 GitHub Actions，也未构建/发布生产镜像 |

## 3. Top Risks

1. **WS3-H1 · High**：服务端只校验文件名/content-type/大小和客户端时长，任意字节可伪装为合格 MP4 进入评审。
2. **WS3-H2 · High**：跨用户秒传把未验证的客户端指纹当作持有证明，可重放他人指纹或污染未来秒传映射。
3. **WS3-H3 · High**：CI 不启动 MinIO 且前端不跑 type-check，仓库主门禁无法复现当前本地绿灯。

## 4. Detailed Findings

### Finding: WS3-H1 服务端未验证真实媒体格式与时长

- Severity: High
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: WS-3 视频直传定稿与 Phase 7 视频校验
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:1128-1137,1246-1257,1438-1468`
  - Function / Module: `validateCompletedVideoObject`、`validateUploadMeta`、`validateMergedVideo`
  - Relevant behavior: 定稿只比对桶、键、大小和归一化 content-type；业务校验读取文件元数据与客户端提交的 `durationSeconds`，没有解析对象字节、容器或真实时长。
  - Test evidence: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase7VideoReviewIT.java:1643-1655` 用短字符串和确定性伪字节作为 MP4，注释明确说明服务端不解析真实结构；这些路径在独立集成测试中被接受。
- Problem: 规格要求“合并后校验格式=MP4、大小、时长”，但当前后端校验的是声明和元数据，不是实际媒体。攻击者或损坏客户端可上传非视频、损坏容器或任意时长内容，再声明 `.mp4`、`video/mp4` 和 900 秒，使记录进入 `WAIT_REVIEW`。
- Why it matters: 这会把不可播放或不合规内容写入业务主流程，消耗评审资源并破坏 AT-08 的后端硬约束；文件内容还可能触发后续播放器或转码器的异常路径。
- Realistic failure scenario: 学生将任意二进制文件命名为 `lesson.mp4`，以 `video/mp4` 直传并在 merge 请求提交 900 秒；MinIO 对象大小正确，后端将格式检查置 PASS，学院随后分配教师才发现视频无法播放。
- Minimal fix: 定稿后在受限进程中用可信媒体探测器读取对象流、range 或受控临时文件，校验 MP4 容器、允许的轨道/编码和实际时长；探测失败必须 fail-closed。
- Better long-term fix: 将媒体验证设计为有超时、资源上限、隔离目录和可观测状态的异步扫描流水线，只有可信扫描完成后才能进入 `WAIT_REVIEW`。
- Regression test suggestion: 真实 MinIO IT 上传扩展名/content-type 正确但内容为随机字节的对象，以及真实 15 分钟声明配短视频的对象；两者必须进入 `VALIDATION_FAILED`，不得创建可分配评审任务。
- Estimated effort: 1–3 days

### Finding: WS3-H2 跨用户秒传信任未验证的客户端指纹

- Severity: High
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: WS-3 视频 init、文件登记与跨用户去重
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:153-155,206-218,889-927,1277-1283,1854-1863`
  - Function / Module: `initUpload`、`startDirectUpload`、`upsertReviewAfterValidation`、`toVO`
  - Relevant behavior: 客户端提供的 64 位值直接用于 `getByMd5` 跨用户查重；直传登记把该声明写入会话/评审，服务端没有根据对象内容重算或执行持有证明；响应 VO 又返回该指纹。
- Problem: 哈希值在这里同时承担内容标识和授权型“持有证明”，但它既由客户端声明又未与实际字节绑定。知道某个指纹的用户可不上传内容直接绑定既有文件；攻击者也可先用错误内容登记一个受害指纹，污染后续秒传结果。
- Why it matters: 跨账号错误绑定会导致学生提交的评审对象与其实际选择文件不一致，形成持久数据完整性问题，并把去重优化变成跨主体的数据引用通道。
- Realistic failure scenario: 用户 A 的视频指纹经接口响应、协作或日志被用户 B 获知；B 在 init 中提交该值后命中 `FAST_HIT`，无需证明持有文件即可把 A 的对象绑定到自己的评审记录。另一种路径是 B 用伪内容抢先登记 A 的声明指纹，A 后续上传时命中错误对象。
- Minimal fix: 在可信校验完成前关闭跨 uploader/student 秒传，只允许同一学生/同一上传者恢复会话；同时停止在普通业务 VO 暴露内部去重键。
- Better long-term fix: 使用服务端可验证的对象 checksum 或挑战式 possession proof，并把去重键、对象真实性状态、拥有者范围建成明确的数据契约；只有已验证对象可跨主体复用。
- Regression test suggestion: 用“声明指纹与上传字节不匹配”完成一次上传，再由另一学生使用相同声明指纹 init；必须拒绝或重新上传，绝不能绑定首个对象。另补跨学生已知指纹重放反例。
- Estimated effort: 1–2 days

### Finding: WS3-H3 CI 无法复现 MinIO 集成测试与前端类型门禁

- Severity: High
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: `.github/workflows/ci.yml` 与 WS-3 发布门禁
- Evidence:
  - File: `.github/workflows/ci.yml:8-44,46-60`
  - Function / Module: GitHub Actions `backend`、`frontend` jobs
  - Relevant behavior: backend services 只有 MySQL/Redis，但 `mvn verify` 中 Phase 5/7 已执行真实 MinIO 预签名 PUT；frontend 只运行 `npm run build`，未运行项目 DoD 要求的 `npm run type-check`。
  - Runtime evidence: 在独立 Compose 提供 MinIO 时 `mvn clean verify` 265/265 通过，说明本地测试契约真实依赖该服务。
- Problem: 仓库 CI 和当前测试环境契约不一致。PR/主分支上要么在 MinIO 连接处失败，要么只能通过削弱/跳过测试来变绿；同时纯类型错误可绕过前端门禁。
- Why it matters: `REVIEW-GATE` D8 和 `AGENTS.md` DoD 要求 CI 绿。一个无法按仓库配置复现的门禁不能证明改动可发布，也会迫使团队忽略红灯。
- Realistic failure scenario: WS-3 合并后 GitHub Actions 在 Phase 5/7 初始化或预签名上传处连接 `localhost:9000` 失败；开发者因本地 265/265 通过误判为 CI 环境偶发问题并合并后续改动。
- Minimal fix: backend job 增加 MinIO 服务、健康检查、与测试一致的凭据/桶/CORS 初始化；frontend job 在 build 前执行 `npm run type-check`。
- Better long-term fix: 将 CI 与本地复核复用同一份测试依赖 Compose/Testcontainers 契约，并在门禁中输出依赖健康和测试报告摘要，减少环境漂移。
- Regression test suggestion: 在无预置状态的 CI runner 上执行完整 `mvn clean verify` 与 `npm run type-check && npm run build`；不得依赖开发机已有桶、卷或环境变量。
- Estimated effort: 2–6 hours

## 5. Security Concerns

- Coverage: High
- Inspected evidence: WS-2 凭据初始化/重置与 token 撤销；WS-13 RBAC 写侧守卫、数据库重读与串行锁；WS-3 预签名授权、对象键、CORS、秒传和响应字段；对应单元/集成反例。
- Exclusions / limits: 未执行公网渗透、TLS/HSTS/CSP、依赖漏洞联网扫描；这些留给 WS-5、WS-7 和最终全量审计。

WS-2 和 WS-13 在本轮范围内表现可靠：生产初始凭据 fail-fast，弱口令被拒，学生自动开户默认关闭，口令变更/重置使用 CAS 并撤销 token；RBAC 管理操作基于数据库现态计算授权天花板，对目标角色潜在权限、学院/专业范围和受影响成员做后端硬校验。安全阻断集中在 WS3-H1/H2，属于客户端声明跨越可信边界而未被服务端证明。

## 6. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `RuntimeProfileGuard`、`spring.factories` 注册、application 配置、测试配置、dev 脚本、两个 Compose 配置和启动型单测。
- Exclusions / limits: 未接触真实生产 secret store、域名或证书。

WS-10 判定 PASS。应用必须显式选择且只能选择 dev/prod；prod 会拒绝 test seed、demo、已知默认值以及缺失/弱数据库、Redis、MinIO、JWT、STAFF 初始凭据。守卫在配置数据加载后、Bean 创建前执行，测试通过真实 `SpringApplication` 启动路径验证，而非只测辅助方法。dev/prod Compose 均可解析。

## 7. Data Integrity Analysis

- Coverage: High
- Inspected evidence: 上传会话唯一约束/CAS、part 连续性与大小、对象定稿、文件登记；凭据 CAS；RBAC 事务锁与 before/after 快照；真实数据库并发反例。
- Exclusions / limits: 未进行网络分区、进程强杀或存储节点故障注入。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 0 | 本轮未发现新增跨表事务断裂 | 保持现有事务与 CAS |
| Idempotency | 0 | 上传恢复/定稿重复调用已有状态约束 | 保持回归 |
| ConcurrencyConsistency | 0 | RBAC 与凭据并发保护通过专项反例 | 保持锁顺序和数据库重读 |
| MigrationSafety | 0 | V27/V28 在全新 schema 成功 | 后续仍禁止改已发布迁移 |
| InvariantValidation | 2 | 媒体真实性、客户端指纹与实际对象绑定 | 修复 WS3-H1/H2 |
| BackupRestore | 0 | 本轮未评估，不计为发现 | 最终全量审计覆盖 |
| Reconciliation | 0 | 本轮未发现新增缺口 | 媒体扫描可增加孤儿/失败态对账 |

## 8. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 全新 schema `clean verify`、demo 数据常驻双跑、真实 MySQL/Redis/MinIO、专项单元与 IT、前端 type-check/build、测试载荷源码。
- Exclusions / limits: 未用真实浏览器人工观察网络请求；未做 2GB 实体文件和长时压力测试。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|---------------|------|--------|
| WS-1 fixture isolation | High | 共享 demo 数据可能污染计数 | Keep；demo 常驻连续两跑均绿 |
| WS-2 credential hardening | High | 弱口令、并发改密、旧 token 残留 | Keep；单元+真实库反例均绿 |
| WS-10 profile guard | High | 无 profile 或 prod 默认值误启动 | Keep；真实应用启动型测试 |
| WS-13 RBAC ceiling | High | 自提权、范围扩大、并发窗口 | Keep；低权限 IT 与锁/矩阵单测 |
| WS-3 multipart transport | High | CORS、过期 URL、ETag、断点/恢复、越权 | Keep；真实 MinIO PUT 与状态验证 |
| WS-3 media authenticity | Low | 伪 MP4 和伪时长可通过 | Rewrite fixtures + add adversarial media probe tests |
| Repository CI | Low | 本地绿无法在 runner 复现 | Fix CI environment contract |

### Valuable Tests

- `Phase2SecurityIT` 与 `Phase7VideoReviewIT` 在 demo 常驻库连续两轮 27/27，证明 WS-1 没有靠清空共享业务数据或放宽断言取巧。
- WS-2/WS-10/WS-13 的 105 个目标单测覆盖 fail-fast、CAS、token 撤销、授权矩阵和并发序列；相应真实依赖 IT 纳入 50/50 专项回归。
- WS-3 的预签名 URL、CORS、ETag、未授权、恢复、材料/视频上传均直接访问真实 MinIO，不是 mock 网络调用。

### Suspicious Tests

- `Phase7VideoReviewIT.mp4` 和 `filledMp4Payload` 生成的并非可播放 MP4，但成功用例仍通过；它准确暴露了服务端只看声明的事实，却不能作为“真实格式/时长校验已通过”的证据。

### Missing Tests

- 声明为 MP4、实际为随机字节的对象必须失败。
- 声明时长与可信媒体探测时长不一致必须失败。
- 声明指纹与实际对象不一致，以及跨学生已知指纹重放必须失败。
- CI runner 从零状态启动 MinIO 后完整复现 265 条测试。

## 9. Release Concerns

- Coverage: High
- Inspected evidence: `.github/workflows/ci.yml`、dev/prod Compose、Maven/前端一次性门禁、Flyway V1–V28、测试依赖清理与端口核验。
- Exclusions / limits: 未构建并推送生产镜像，未验证真实部署、回滚和监控。

发布阻断为 WS3-H3，同时 WS3-H1/H2 也必须在稳定发布前清零。生产/dev Compose 语法有效，隔离环境测试完成后没有遗留 8080/5173 监听，临时容器、网络和卷已清理。前端构建仍提示 `echarts` 约 1035kB、`naive` 约 1340kB chunk 超过 900kB；该项属于既有 WS-12 优化，不阻断本次安全整改复核。

## 10. Principles Compliance

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Validate at trust boundary | 2 | High | WS-3 媒体真实性、跨用户指纹 |
| Fail-Fast | 1 | High | CI 缺运行测试所需 MinIO，失败只能到执行期暴露 |
| Defense in Depth | 2 | High | 浏览器声明未由服务端内容校验/持有证明复核 |

### Principles Respected

WS-2 和 WS-10 对不安全配置采取启动即拒绝；WS-13 把权限判断放在后端事务内，以数据库现态而不是请求 DTO 为真源；WS-3 的传输状态机使用显式状态、CAS、唯一约束和短期预签名，具备良好的最小权限与并发控制基础。WS-1 的测试修复保留精确断言并只隔离自身 fixture，没有通过放宽期望掩盖问题。

## 11. Recommended Fix Order

### Fix Immediately

1. 关闭跨用户/跨上传者秒传，直到指纹与对象内容存在服务端可验证绑定；停止在普通业务 VO 返回内部去重键。
2. 在视频进入 `WAIT_REVIEW` 前增加可信媒体格式和时长探测，失败必须进入 `VALIDATION_FAILED`。

### Fix Before Stable Release

1. 为 GitHub Actions 增加 MinIO、桶/CORS准备和测试配置，前端增加 type-check。
2. 补齐伪媒体、伪时长、指纹不匹配和跨用户重放反例，随后在全新 schema 重跑专项 + `mvn clean verify`。

### Schedule Later

- 按既有 WS-12 拆分前端大 chunk；按 WS-5/WS-7 完成 TLS/CSP、镜像与供应链硬化；最终全量审计再覆盖故障注入、性能和部署运行面。

### Ignore for Now

- 不因本轮未做 2GB 实体压力测试而单独退回；当前分片/大小/连续性逻辑已有真实 MinIO 覆盖，但应在最终上线验收补运行期容量证据。

## 12. Quick Wins

- 先把 fast-hit 查询限定为相同 uploader/student，并从 `VideoReviewVO` 移除 `fileMd5`，可立即关闭最直接的跨主体复用通道。
- CI 前端 job 将命令改为 `npm run type-check` 后再 `npm run build`。
- 把目前的伪 MP4 成功 fixture 改名为“传输载荷”，另加入最小真实 MP4 样例，避免报告把传输成功误写为媒体校验成功。

## 13. Long-term Refactor Plan

1. **可信对象状态**：为文件对象增加明确的扫描状态、探测结果和服务端 checksum；业务表只能引用已验证对象。风险是状态迁移和历史数据回填，测试需覆盖重复扫描、失败恢复和并发引用。
2. **上传与业务提交解耦**：浏览器直传完成后先进入隔离/待扫描区，扫描成功再原子登记为可用业务文件。风险是异步延迟，测试需覆盖通知、超时、孤儿清理和幂等。
3. **统一测试依赖契约**：让 CI、本地复核和后续 Testcontainers 使用同一套 MySQL/Redis/MinIO 初始化规则。风险是流水线耗时，测试需记录冷启动时间并保证 runner 零状态可重复。
