# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 41 second-remediation dynamic evidence
**Audit mode:** incremental + security / stability / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-25
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮只复核第二轮静态报告之后新增的动态证据，不重开已经完成的代码审查。冻结代码点为
`b5ed7f5d2b0cb18fd6d8a83e12805c1e6deb7fef`，提交材料点与当前 HEAD 为
`ef6b550052329dc97b56289a3a4364fdb569ab9c`。两项既定真实门禁均已取得并归档：

1. `scripts/test-phase41-backup-restore-real.sh`：真实 MySQL 8.4、真实 MySQL CLI 恢复，
   Surefire **149/149**、`Phase41BackupIT` **1/1**、9 模块 `BUILD SUCCESS`，最终输出
   `[phase41-backup-restore-real] PASS`。
2. `scripts/test-phase41-mysql-init-real.sh`：MySQL 8.4 的 sourced `0644` 与 executable
   `0755` 两路均通过真实账号认证、精确 schema 授权和清理断言，最终输出
   `[phase41-mysql-init-real] PASS`。

上一轮 **1 High / 2 Medium / 1 Low** 的代码整改及其两个动态闭环条件因此全部满足。本轮确认
**1 个新的非阻断 Low**：提交材料中的门禁 A JDBC 示例关闭 TLS，却遗漏
`allowPublicKeyRetrieval=true`，导致 MySQL 8.4 新账号在冷认证缓存下不能按文档原样首次连接。
执行者用 Unix socket 完成一次安全认证、预热快速认证缓存后成功运行。该问题不推翻备份/恢复门禁，
且规范生产 Compose 与测试默认 URL 均已包含 `allowPublicKeyRetrieval=true`；它属于复跑说明和
测试真实性缺口，留待后续修正文档/runner，并进入最终全量审计。

正式结论：**PASS（0 Critical / 0 High / 0 Medium / 1 Low）**。Phase 41 可置
**✅ 独立复核 PASS**，并放行 Phase 47 进入其既有退回项整改。此 PASS 不代表项目发布就绪；
全项目仍受 Phase 0、44、47、53 阻断。

### Score Dashboard

```text
Security   █████████░  9.2  A   初始化账号/授权真实回环通过；凭据未见泄漏
Stability  █████████░  9.1  A   真实 MySQL 8.4 CLI 大语句恢复与回滚通过
Testing    ████████░░  8.6  A   两项门禁通过；提交 SHA 与收尾清单未嵌入日志
Release    █████████░  8.7  A   Phase 41 门禁闭合；仍非生产灾备/全项目 GO
──────────────────────────────
Overall    █████████░  8.9  A
```

Each dimension is scored 0.0–10.0. Higher is better. Overall is a release-risk
judgment, not a simple arithmetic average.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|---|---:|---:|---:|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **1** | **1** | **0** |

### Evidence Gate Statistics

| Gate | Result | Independent evidence |
|---|---|---|
| Real MySQL 8.4 backup/restore CLI | **PASS** | 完整日志、Failsafe XML 1/1、149/149 Surefire、候选特征与源码 blob |
| Real MySQL 8.4 account/privilege loop | **PASS** | sourced/executable/总 PASS、脚本的真实版本/认证/授权/清理断言 |

## 2. Project Map

- `platform-system`：37 张受管表的逻辑快照、SQL 编码、事务恢复产物。
- `platform-boot`：`Phase41BackupIT` 的生成列、大 JSON、双回放、失败回滚和真实 CLI 门禁。
- `deploy/mysql-init`：生产应用账号、凭据处理与最小 schema 权限。
- `scripts`：两项需用户显式授权执行的真实 MySQL 8.4 门禁。
- `docs/reviews/evidence/phase41-real-gates-2026-07-25`：本轮日志与 Surefire/Failsafe XML。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|---|---|---|---|
| Incremental | High | 静态报告之后新增的两份日志、XML、当前源码/脚本 blob 与 Git 时间线 | 未重开无关阶段 |
| Security | High | 真实账号认证、精确权限、无全局权限/GRANT OPTION、凭据痕迹扫描 | 未执行账号或凭据操作 |
| Stability | High | MySQL 8.4、13 MiB JSON、真实 CLI、整体回滚、模块结果 | 未自行运行数据库/MinIO |
| Testing Authenticity | High | 日志全文、XML 汇总、脚本成功路径、候选特有行为、文件哈希 | 日志未嵌入 Git SHA 或收尾清单 |
| Release | High | 两项既定门禁、清理声明、当前治理状态 | 未部署、切流、PITR 或 RPO/RTO 演练 |
| Configuration | High | JDBC URL、MySQL 8.4、packet、生产 Compose | 未读取仓库外生产配置 |
| Data Integrity | High | 37 表指纹、5 个生成列、双回放、故障后精确复原 | 未读取真实业务数据 |
| Concurrency | Medium | 恢复单事务和唯一 COMMIT 的动态回归 | 未执行压力或并发恢复 |

## 3. Dynamic Evidence Summary

### Gate A — real backup/restore

- 归档完整日志 SHA-256：
  `B53B7200A31362F0471E9C8D08133210DA0B589BA169CF0F4DAA6457FA1E4345`；
  仓库根目录副本逐字节一致。
- Failsafe XML SHA-256：
  `917AEC004C0D48C3850311462A4061DFAA83FA2FF10D0F50463DD9FC6AC368F0`。
- 源/恢复数据库日志均识别为 MySQL 8.4；XML 运行环境为 Linux amd64、JDK 17.0.19。
- Surefire 为 `platform-file` 3/3 + `platform-boot` 146/146，共 **149/149**。
- 唯一 Failsafe 用例
  `fullArtifactReplacesFlywaySeedsAndRestoresExactSnapshotAtomically` 为
  **1/1、0 failure、0 error、0 skipped**，耗时 27.579 秒。
- 用例强制经过 5 个生成列 fixture、13 MiB JSON、超过 16 MiB 的真实 SQL、
  32 MiB 预算、真实 MySQL 8.4 CLI、双回放和全部正常 INSERT 后的失败回滚断言。
- 测试内 MinIO 对象与双 scratch schema 清理失败会令用例失败；用例已绿。运行后的
  `SHOW DATABASES` 和桶为空复查由执行者提供，未另行写入归档。

### Gate B — real account/privilege loop

- 归档日志 SHA-256：
  `FAD14E350704CB6E701835A189D7EA8B77F71C1F4C3286EE238A55578902A23F`；
  仓库根目录副本逐字节一致。
- 日志依次为 sourced PASS、executable PASS 和总 PASS，符合脚本完整成功路径。
- 脚本只在实际服务端版本为 `8.4.*`、特殊口令真实认证、`DATABASE()` /
  `CURRENT_USER()` 精确匹配、schema 权限精确且无额外全局权限/`GRANT OPTION` 后输出
  单路 PASS。
- 每路 PASS 前必须成功执行 label 所有权检查和 `docker rm --force --volumes`；
  执行者另行确认隔离 dind 最终为 0 容器、0 卷。三行日志没有保存详细 Docker 输出或
  镜像补丁版本，因此 MySQL `8.4.10` 与最终零清单属于执行者补充证据。

### Provenance and hygiene

- 当前相关生产源码、测试和两个 runner 相对 `ef6b550` 无差异；当前 HEAD 仍为
  `ef6b550`。两份运行证据晚于代码点 `b5ed7f5` 与材料点 `ef6b550`。
- Gate A 的大行失败关闭、独占对象前缀与 TCP_SQL_V2 行为属于该候选特征；Gate B
  的 runner 和初始化脚本 blob 与 HEAD 一致。
- 日志没有嵌入 `git rev-parse HEAD`，Gate A 也不是 clean build，故提交同源性是
  高置信交叉印证，不是日志自身的密码学证明。
- 对归档目录全部 37 个文件的凭据形态扫描未发现明文口令、access key、私钥、Bearer
  token 或带 userinfo 的 JDBC URL；4 个命中均为重复的 `must_change_pwd` 布尔日志，
  不是凭据。
- 本次独立复核未启动 Docker、数据库、Redis、MinIO 或应用，未执行账号、网络、schema、
  对象、容器、镜像、卷、fuzz、攻击性输入或任何 cyber 类操作。

## 4. Top Risks

1. **Known backup capability boundary：** 约 24 MiB 以上的原始 UTF-8 单行在 Base64 SQL
   后可能超过 32 MiB 预算并使备份 fail-closed；写入侧尚未同界。
2. **Production recovery boundary：** 本轮证明应用逻辑快照，不替代物理全备、binlog
   连续归档/PITR、MinIO mirror、生产切换、RPO/RTO、超大库或慢存储演练。
3. **Evidence reproducibility：** 日志未自带提交 SHA、客户端/服务端完整版本和收尾清单；
   Gate A 示例 URL 还缺冷认证缓存所需的 TLS/RSA 契约。
4. **Compatibility observation：** Flyway 在 MySQL 8.4 上成功迁移，但运行时提示当前版本
   最高已测试至 MySQL 8.1，保留给依赖升级与最终全量审计。

## 5. Detailed Findings

### Finding: Gate A runbook omits cold-cache JDBC authentication prerequisite

- Severity: Low
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: Phase 41 Gate A 复跑说明与 runner 前置校验
- Evidence:
  - File: `docs/reviews/phase-41-second-remediation-submission-2026-07-25.md:68`
  - Function / Module: `scripts/test-phase41-backup-restore-real.sh:42-65`
  - Relevant behavior: 提交材料的 URL 显式 `useSSL=false`，却没有
    `allowPublicKeyRetrieval=true` 或受控服务器 RSA 公钥。MySQL 8.4 新账号的
    `caching_sha2_password` 快速认证缓存为空时，执行者实际遇到
    `Public Key Retrieval is not allowed`；一次 Unix socket 安全认证预热缓存后才通过。
- Problem: 门禁 A 的可复制命令没有声明冷认证缓存下必需的 TLS 或 RSA 公钥交换契约，
  runner 也只校验回环目标，不会在 Maven 启动前对该配置失败关闭。
- Why it matters: 新审计者按材料原样复跑会在 JDBC 初始化阶段失败；MySQL 重启、改密或
  `FLUSH PRIVILEGES` 后问题可重现，容易被误判为产品恢复缺陷。
- Realistic failure scenario: 在全新 MySQL 8.4 账号上直接复制提交材料的 URL，Connector/J
  禁用 TLS且不允许获取服务器公钥，首次认证失败；人工 socket 登录后缓存被预热，同一 URL
  又暂时成功，造成门禁依赖未记录的环境状态。
- Minimal fix: 将 Gate A 示例与 runner fail-fast 契约改为三选一：受验证 TLS、
  `allowPublicKeyRetrieval=true`，或受控 `serverRSAPublicKeyFile`。
- Better long-term fix: 为真实门禁提供零状态、clean build 的受管 runner，并把生产连接
  升级为可验证 TLS；门禁日志自动记录脱敏连接模式、提交 SHA 和资源零清单。
- Regression test suggestion: 在全新 MySQL 8.4 账号、未预热认证缓存的隔离环境，严格按
  更新后的示例执行 Gate A，并确认无需额外 socket 登录即进入测试。
- Estimated effort: 1–2 hours

该问题不阻断 Phase 41：认证发生在备份/恢复断言之前，预热不改变被测 SQL、CLI packet、
事务或数据指纹。规范生产 Compose `docker-compose.yml:76` 与测试默认 URL
`Phase41BackupIT.java:70-76` 均已有 `allowPublicKeyRetrieval=true`，所以没有确认生产
Compose 的冷启动故障。

MySQL 官方说明 `caching_sha2_password` 的首次完整认证需要安全连接或 RSA 交换，且
服务器重启、`FLUSH PRIVILEGES` 等会清空缓存：
[MySQL 8.4 caching_sha2_password](https://dev.mysql.com/doc/refman/8.4/en/caching-sha2-pluggable-authentication.html)。
Connector/J 官方说明 `allowPublicKeyRetrieval` 默认关闭：
[Connector/J security properties](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)。

#### PASS 后续闭环（2026-07-25）

- Current status: **Closed after review**。正式复核时确认的 `1 Low` 及文末 finding
  count 保留为当时快照，不改写原独立结论。
- `scripts/test-phase41-backup-restore-real.sh` 现会在 Maven 启动前解析 JDBC
  参数，拒绝重复安全键，并强制“`VERIFY_CA/VERIFY_IDENTITY` 受验证 TLS、
  `allowPublicKeyRetrieval=true`、受控可读绝对路径
  `serverRSAPublicKeyFile`”三选一；日志只输出脱敏后的模式名。
- `scripts/test-phase41-backup-restore-contract.sh` 使用临时 `uname/mysql/mvn`
  及网络能力 fail stub，覆盖三类正例、ASCII/大小写/重复键与畸形值反例，并断言
  认证前置失败发生在 `mysql`/Maven 前且凭据哨兵不泄漏；CI 已用无 services、
  只读权限、5 分钟上限的独立 job 接入该纯静态契约。
- Gate A 示例与 `docs/备份与恢复手册.md` 已同步冷缓存认证前置。Codex 未重跑
  真实 MySQL、账号、schema、对象或容器；如需新的冷缓存动态证据，仍只能由用户或
  获授权复核者在专用隔离环境执行，不影响原 Phase 41 PASS。

## 6. Security Analysis

- Coverage: High
- Result: PASS
- Evidence: Gate B 真实覆盖特殊口令、真实账号身份、精确 schema 权限、零额外全局权限和
  零 `GRANT OPTION`；日志/关键 XML 未检出明文凭据形态。
- Boundary: `allowPublicKeyRetrieval=true&useSSL=false` 不是生产 TLS 证明。是否升级为
  `VERIFY_IDENTITY` 与受管证书链留最终全量审计。

## 7. Stability Analysis

- Coverage: High
- Result: PASS
- Evidence: MySQL 8.4 上 13 MiB JSON 生成超过默认客户端 16 MiB 的真实语句，并以
  64 MiB 客户端/服务端 packet 完成 CLI 恢复；生成列、双回放与后段失败整体回滚均通过。
- Boundary: Gate A 使用 `--init`/tini 解决容器 PID 1 僵尸回收，这是可复现 runner
  前置条件，不是生产业务逻辑整改。

## 8. Configuration Safety Analysis

- Coverage: High
- Result: PASS with one Low
- Evidence: 32 MiB 导出预算、64 MiB MySQL/CLI packet 与生产 Compose 一致；
  Gate B 确认 MySQL 8.4 和精确权限。
- Boundary: Gate A 提交材料的 URL 缺少冷缓存认证参数，见唯一 Low。

## 9. Data Integrity Analysis

- Coverage: High
- Result: PASS
- Evidence: 5 张生成列表均有非空数据，恢复后生成值重算；37 表全字段指纹、目标外 drift
  删除、双回放和全部 INSERT 后失败回滚均由同一 Failsafe 用例执行。
- Boundary: 逻辑快照不是在线生产写流量下的 RPO/RTO 或超大库恢复演练。

## 10. Concurrency Analysis

- Coverage: Medium
- Result: No new finding
- Evidence: 动态测试验证恢复脚本单事务、末尾唯一 COMMIT 和错误后显式回滚。
- Boundary: 未执行多恢复者并发、锁等待、压力或故障注入；本增量也未改变并发模型。

## 11. Testing Authenticity Analysis

- Coverage: High
- Result: PASS with evidence limitations

| Test area | Result | Evidence |
|---|---|---|
| 5 张生成列表 | PASS | 同一 Phase41BackupIT 1/1 |
| 13 MiB JSON / 32 MiB budget | PASS | 18,175,967-byte SQL 与 1 MiB fail-closed |
| 已成功 INSERT 后整体回滚 | PASS | 同一完整恢复用例 |
| MySQL 8.4 CLI stdin/packet | PASS | 服务端/客户端 8.4、exit=0、最终 PASS |
| 初始化账号与精确授权 | PASS | sourced/executable/总 PASS |

提交同源性与资源清理采用“日志 + XML + 当前 blob + 时间线 + 候选特征 + 执行者收尾声明”
交叉印证。由于日志未内嵌提交 SHA 和运行后零清单，本报告不把它表述为密码学供应链证明。

## 12. Release Analysis

- Coverage: High
- Result: Phase 41 PASS
- Evidence: 两项上一轮明确要求的真实门禁均满足，0 个阻断 finding。
- Release boundary: 仅放行 Phase 47 的既有整改/复核。不得据此 merge、push、部署、切流，
  也不得宣称全项目 GO。

## 13. Principles Compliance

| Principle | Assessment | Notes |
|---|---|---|
| KISS | Good | 动态证据只关闭既定两个门禁 |
| DRY | Good | SELECT/INSERT 继续共用 metadata 列集合 |
| YAGNI | Good | 未扩大为 PITR 或生产切换 |
| Fail closed | Good | 超预算、生成属性未知、CLI/恢复错误均拒绝伪成功 |
| Evidence honesty | Good | 明确区分日志事实、脚本契约、执行者声明和未覆盖项 |
| Least privilege | Good | 真实授权回环未发现权限扩大 |

## 14. Recommended Fix Order

1. **Phase 47：** 按既有报告整改生命周期读取失败时覆盖其它规则的问题，并重新独立复核。
2. **非阻断 Quick fix：** 修正 Gate A JDBC 示例及 runner 前置校验，加入冷认证缓存复跑说明。
3. **之后：** Phase 53 demo 元数据/旧对象 reconcile → Phase 44 状态诚实化和事务后缓存逐出
   → Phase 0 验收基线。
4. **最终全量审计：** 重新评估 24 MiB 原始单行边界、PITR/RPO/RTO、生产 TLS、
   Flyway/MySQL 8.4 支持声明、口令轮换与应用账号兼持 Flyway DDL 权限。

## 15. Quick Wins

- 后续真实门禁日志开头记录 `git rev-parse HEAD`、脚本/目标 blob、UTC 时间及客户端/服务端
  完整版本，末尾记录退出码和脱敏后的资源零清单。
- Gate A 使用 clean build 或保存 class/source hash，减少“Nothing to compile”带来的同源性限制。
- 将 `--init`/tini 与 MySQL 冷认证要求写入可复现 runner 说明。

---

**Final verdict:** `PASS`
**Finding count:** `0 Critical / 0 High / 0 Medium / 1 Low / 0 Info`
**Evidence blockers:** `0`
**Phase state:** Phase 41 `✅ 独立复核 PASS`
**Release gate:** 放行 Phase 47 进入既有退回项整改；全项目仍非发布就绪
