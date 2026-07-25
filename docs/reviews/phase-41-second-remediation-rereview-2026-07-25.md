# Fuck My Shit Mountain Audit Report

> **后续状态：** 本报告是动态门禁执行前的静态时点快照；两项证据已由
> `phase-41-second-remediation-dynamic-evidence-rereview-2026-07-25.md`
> 独立核验，Phase 41 最新正式结论为 **PASS**。以下原始结论保留作审计追溯。

**Project:** teacher-cert-platform · Phase 41 second remediation
**Audit mode:** incremental + security / stability / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-25
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结第二轮整改代码 `17b11aa64512f57d27fc832e83342b189925272a..b5ed7f5d2b0cb18fd6d8a83e12805c1e6deb7fef`，并核对提交材料至 `ef6b550052329dc97b56289a3a4364fdb569ab9c`。代码增量共 10 个文件、`+1038/-45`。

独立静态复核没有确认新的代码、配置或测试设计 finding。上一轮 **1 High / 2 Medium / 1 Low** 均已形成针对性代码闭环：

| 上一轮 finding | 第二轮独立静态结论 | 正式闭环条件 |
|---|---|---|
| High：5 个生成列被显式回放 | `SELECT` 与 `INSERT` 共用 metadata 驱动的可写列集合，未知生成属性失败关闭；5 张表均有非空 fixture | 当前候选的真实 Phase41BackupIT/CLI 门禁通过 |
| Medium：大行缺 packet 契约 | Base64、32 MiB 单语句预算、64 MiB 客户端/服务端契约及超限失败关闭链一致 | 真实 MySQL 8.4 CLI 回放通过 |
| Medium：失败点过早，未证明已写入前缀回滚 | 目标先制造 drift；故障位于全部正常 INSERT 后、唯一 COMMIT 前；trace 覆盖 SQLState、未提交与显式回滚 | 当前候选的真实 Phase41BackupIT 通过 |
| Low：新 workflow 不能作为合并前入口 | workflow、旧材料勘误和第二轮材料均明确“进入默认分支后才能触发”，当前只承诺本地显式授权路径 | 静态关闭 |

但本轮仍不能给出 PASS。仓库现有 `Phase41BackupIT` XML 生成于候选源码之前，且来自 MySQL 8.0；候选材料也明确声明未运行 Phase41BackupIT、Failsafe、真实 MySQL 8.4 CLI 恢复或账号/授权回环。仓库中未找到以下两条当前候选的 PASS 日志：

1. `[phase41-backup-restore-real] PASS`
2. `[phase41-mysql-init-real] PASS`

这两项分别验证上一轮恢复链缺陷和 PG-M2 的 MySQL 8.4 动态边界，均属于既定阶段闸门，不能由静态阅读、普通单元测试或旧 XML 替代。遵守用户安全限制，本轮没有执行会创建/删除 scratch schema、MinIO 对象、数据库账号、容器或匿名卷的脚本。

正式结论：**CHANGES_REQUESTED（0 个新增代码 finding；2 个必需动态证据闸门未满足）**。Phase 41 继续复核退回，Phase 47 不放行。用户提供两份同源 PASS 日志后，只需进行动态证据增量复核，无需重开已通过的静态审查。

### Score Dashboard

```text
Security   █████████░  8.8  A   静态凭据/授权边界成立；MySQL 8.4 回环待证
Stability  ████████░░  7.8  A   生成列、回滚和 packet 代码链成立；真实恢复待证
Testing    █████░░░░░  5.0  B   测试设计完整，但关键 Failsafe XML 早于候选
Release    ████░░░░░░  4.5  C   两项强制动态门禁尚无 PASS 证据
──────────────────────────────
Overall    ██████░░░░  6.4  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Overall is a release-risk judgment, not a simple arithmetic average.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **0** | **0** | **0** |

### Evidence Gate Statistics

| Gate | State | Blocking reason |
|---|---|---|
| Real MySQL 8.4 backup/restore CLI | Pending | 没有当前候选的 `[phase41-backup-restore-real] PASS` 完整日志 |
| Real MySQL 8.4 account/privilege loop | Pending | 没有当前候选的 `[phase41-mysql-init-real] PASS` 完整日志 |

## 2. Project Map

- `platform-system`：`DatabaseBackupService` 负责 37 张受管表的快照、SQL 编码、gzip、摘要和 MinIO 产物。
- `platform-boot`：`Phase41BackupIT` 负责生成列、大 JSON、双回放、故障回滚及可选真实 CLI 验证。
- `platform-exchange`：`import_export_batch.preview_json` 是大单行 JSON 的真实生产来源。
- `deploy/mysql-init`：生产应用账号、库名校验、凭据文件及精确权限初始化。
- `scripts`：两个需用户显式授权的真实 MySQL 8.4 动态门禁。
- Compose / `application-prod.yml`：32 MiB 导出语句预算和 64 MiB MySQL packet 配置链。
- `docs/备份与恢复手册.md`：同版本 Flyway 后的恢复流程、运维账号和证据边界。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `17b11aa..b5ed7f5` 10 个代码/配置/测试文件及提交材料至 `ef6b550` | 未重开无关阶段债务 |
| Security | High | 初始化标识符、凭据文件、账号权限、环境隔离、清理边界 | 未创建账号、认证尝试、漏洞扫描或攻击性输入 |
| Stability | High | 生成列、编码预算、事务边界、CLI 超时和恢复顺序 | 未运行 MySQL/MinIO 动态恢复 |
| Testing Authenticity | High | fixture、断言、故障 trace、runner、XML/源码/提交时间线 | 当前候选无 Failsafe 或真实 CLI XML/日志 |
| Release | High | 分支、提交、workflow、手册、一次性构建、Compose 解析 | 无 remote；未部署、切流或演练 |
| Configuration | High | `.env.example`、dev/prod Compose、应用配置、产物 metadata | 未读取仓库外生产配置 |
| Data Integrity | High | 37 表、5 个生成列、13 MiB JSON、全字段指纹、回滚 drift | 未读取或修改真实业务数据 |
| Concurrency | Medium | 单事务、唯一 COMMIT、失败前成功 INSERT trace | 未执行并发、压力或故障注入 |

## 3. Incremental Change Summary

`DatabaseBackupService` 现在通过 JDBC metadata 只选择 `IS_GENERATEDCOLUMN=NO` 的列，并让导出查询与恢复 INSERT 使用同一列序。文本和二进制值改用无换行 Base64；JSON 经 UTF-8 解码后显式 CAST。每行 SQL 在编码分配前按 UTF-8/Base64/SQL 包装开销预算，配置只能在 1 KiB–32 MiB 内收紧，产物头与 MinIO metadata 声明恢复端至少 64 MiB packet。

`Phase41BackupIT` 给 `student`、`certificate`、`file_object`、`video_upload_session`、`process_material` 五张生成列表加入合法非空数据；13 MiB JSON 必须形成大于默认客户端 16 MiB、且不超过 32 MiB 的语句；1 MiB 收紧配置必须让备份失败关闭。回滚反例先制造目标 drift，再在全部正常 INSERT 后、唯一 COMMIT 前注入缺表错误，并检查 SQLState `42S02`、成功 INSERT 数、COMMIT 未执行、显式 ROLLBACK 以及 37 表回到失败前状态。

真实 CLI runner 只允许 Linux/WSL、回环依赖、预先存在的专用桶、独占对象前缀和唯一 scratch schema；客户端必须是 MySQL 8.4，并通过 0600 option file、64 MiB packet、无 `--force` 的 stdin 回放。MySQL 官方示例明确支持 `mysql --no-login-paths --defaults-file=...`，候选将所有 option-file-handling 选项置于普通客户端选项之前，未确认参数顺序问题。[MySQL option-file handling](https://dev.mysql.com/doc/refman/8.4/en/option-file-options.html)，[MySQL `--no-login-paths` example](https://dev.mysql.com/blog-archive/new-client-option-no-login-paths-turn-off-login-path-file-processing/)

## 4. Top Risks

1. **Release evidence risk：** 当前候选从未通过真实 MySQL 8.4 CLI 的大语句恢复，静态 packet 契约仍可能与具体客户端/服务端组合存在运行差异。
2. **Security/configuration evidence risk：** PG-M2 仅有静态与 stub 证据，真实 MySQL 8.4 sourced/executable 初始化、特殊口令认证和精确授权尚未回环。
3. **Known capability boundary：** 约 24 MiB 以上的原始 UTF-8 单行在 Base64 后会超过 32 MiB 预算并让备份 FAILED。上一轮整改口径明确允许此类备份端 fail-closed，因此不计本轮 finding；写入侧尚未同界，保留给最终全量审计评估是否需要业务上限或分片格式。

## 5. Detailed Findings

本次最小增量内没有确认新的 Critical / High / Medium / Low / Info finding。

“动态日志不存在”是已确认的发布证据阻断，而不是把未知运行结果伪装成代码缺陷。其处置条件明确且二元：两项用户控制的真实门禁均返回 PASS，并提供与 `b5ed7f5`/`ef6b550` 同源的完整日志；否则 Phase 41 保持 CHANGES_REQUESTED。

## 6. Security Analysis

- Coverage: High
- Inspected evidence: 初始化 SQL 标识符约束、应用口令转义、root 凭据 0600 option file、sourced/executable 双路径、精确 schema 权限和 label/临时目录清理边界。
- Exclusions / limits: 未执行账号创建、认证、权限变更、网络探测、fuzz、攻击性输入或任何可能触发 cyber 安全限制的操作。

代码级 PG-M2 仍可接受：未发现新 SQL 插值逃逸、凭据进入 argv/`MYSQL_PWD`、扩大到全局权限或清理越界。真实 MySQL 8.4 认证/授权是独立动态门禁，不能由静态审查替代。

## 7. Stability Analysis

- Coverage: High
- Inspected evidence: snapshot 连接、37 表顺序、生成列过滤、Base64 字节预算、客户端/服务端 packet、单事务与失败路径。
- Exclusions / limits: 未运行数据库、对象存储、真实 CLI 或故障注入。

生成列未知状态会失败关闭，避免静默生成不可恢复产物。32 MiB 语句上限低于 64 MiB 客户端/服务端 packet，给协议开销留下充足余量；真实兼容性仍须动态门禁确认。超过受支持行预算时任务明确置 FAILED，不再产生表面成功但不可回放的产物。

## 8. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `.env.example`、`application-prod.yml`、dev/prod Compose、CI shell syntax gate、恢复手册与 workflow 触发说明。
- Exclusions / limits: 未读取或修改生产 secret、仓库外配置或 GitHub 设置。

导出端 32 MiB、恢复客户端 64 MiB、目标 MySQL 64 MiB 的配置链一致。workflow 已诚实声明只有文件进入默认分支后才能手工触发；当前候选的合并前路径只有用户显式授权的本地隔离脚本。

## 9. Data Integrity Analysis

- Coverage: High
- Inspected evidence: V1–V32 schema、37 张 base table、5 个 STORED generated column、终态 `backup_record` 策略、全字段指纹和回滚 drift。
- Exclusions / limits: 未访问真实业务数据。

可写列列表由 metadata 生成，目标库生成列在恢复后重算并进入 `SELECT *` 全字段指纹。恢复脚本仍采用反序 DELETE、正序 INSERT、单一事务和唯一 COMMIT；测试 trace 证明故障注入位于所有正常 INSERT 之后。动态执行前，这些属于高置信代码证据，不属于已完成的运行证明。

## 10. Concurrency Analysis

- Coverage: Medium
- Inspected evidence: 单一 REPEATABLE_READ 快照连接、非终态备份记录排除、恢复事务和故障 trace。
- Exclusions / limits: 未执行并发恢复、压力、锁等待或故障注入。

本增量没有改变在线写入与备份快照的基本并发模型。生产导出仍以一个 REPEATABLE_READ 事务覆盖全部表；恢复脚本只有一个 START TRANSACTION 和一个末尾 COMMIT。未确认新增竞态。

## 11. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: `Phase41BackupIT` 当前源码、真实 runner、现有 Surefire/Failsafe XML、源码和提交时间线。
- Exclusions / limits: 未执行会创建/删除 schema、对象、账号、容器或卷的测试。

### Confidence Assessment

| Test area | Static design confidence | Current-candidate runtime confidence | Action |
|---|---:|---:|---|
| 5 张生成列表 | High | None | Run gate A |
| 13 MiB JSON / 32 MiB budget | High | None | Run gate A |
| 已成功 INSERT 后的整体回滚 | High | None | Run gate A |
| MySQL 8.4 CLI stdin/packet | High | None | Run gate A |
| 初始化脚本静态/stub | High | High | Keep |
| MySQL 8.4 账号与精确授权 | High | None | Run gate B |

本轮独立执行：

```text
mvn -B -ntp test                         PASS（149/149）
mvn -B -ntp -DskipTests package         PASS（9 modules）
npm --prefix frontend run type-check    PASS
npm --prefix frontend run build         PASS（仅既有 >900 kB chunk warning）
docker compose -f docker-compose.dev.yml
  config --quiet                        PASS
docker compose -f docker-compose.yml
  config --quiet                        PASS（仅缺本地生产变量的预期 warning）
git diff --check 17b11aa..ef6b550       PASS
```

现有 Failsafe XML 共 185/185，但 `Phase41BackupIT` XML 时间为 15:13，候选测试源码时间为 17:09，代码提交时间为 18:08；因此旧 XML 不属于本候选。普通 `mvn test` 只覆盖 Surefire，不能冒充 Phase41BackupIT。

## 12. Release Analysis

- Coverage: High
- Inspected evidence: 分支、提交、工作区、构建、前端、Compose、workflow、手册和证据日志搜索。
- Exclusions / limits: 未 merge/push、未部署、未切流、未执行生产或隔离基础设施演练。

当前分支为 `codex/phase41-second-remediation`，代码点 `b5ed7f5`，提交材料点 `ef6b550`；`main` 仍为 `e4f8228`，仓库无 remote。已知未跟踪文件保持原样，本轮未把它们纳入审计产物。

Phase 41 的发布门禁不是“再跑一次普通构建”，而是补齐两个明确的真实环境结果。任一脚本未运行、非零退出、清理失败或日志与冻结提交不同源，均维持 CHANGES_REQUESTED。

## 13. Principles Compliance

| Principle | Assessment | Notes |
|-----------|------------|-------|
| KISS | Good | 保留 SQL 产物，新增约束集中在 metadata 与有界编码 |
| DRY | Good | SELECT/INSERT 共用同一可写列集合 |
| YAGNI | Good | 未把本轮扩大为 PITR、生产切换或分片格式 |
| Fail closed | Good | 未知生成属性、超预算行、CLI/恢复错误均不产生伪成功 |
| Evidence honesty | Good | 候选明确声明未跑动态门禁，旧 XML 未被冒充 |
| Least privilege | Good | 初始化账号权限未扩大，root 凭据不进入自建 argv |

## 14. Recommended Fix Order

1. 用户在获授权的专用 Linux/WSL 隔离环境按提交材料执行 `scripts/test-phase41-backup-restore-real.sh`，保存完整输出、退出码和对应 Git 提交。
2. 用户执行 `scripts/test-phase41-mysql-init-real.sh`，保存完整输出、退出码和对应 Git 提交。
3. 将两份日志交给独立复核者，仅复核日志同源性、PASS 标记、清理结果和必要回归；两项均通过后再将 Phase 41 改判 PASS。
4. 最终全量审计时重新评估“生产写入侧未与约 24 MiB 原始整行备份边界同界”的能力债；本轮不扩大整改范围。

## 15. Quick Wins

- 两份动态日志开头同时记录 `git rev-parse HEAD`、UTC/本地时间、MySQL client/server 8.4 版本；敏感环境值继续脱敏。
- 将完整日志作为审计附件保存，不只截取最后一行 PASS。
- 可选测试增强：真实 CLI 回放前再插入 target-only sentinel，使 mutation 可见；当前 stdin、无 `--force` 和 exit=0 链已经足以作为 packet/解析门禁，故不阻断本轮。

---

**Final verdict:** `CHANGES_REQUESTED`
**Static finding count:** `0 Critical / 0 High / 0 Medium / 0 Low / 0 Info`
**Evidence blockers:** `2`
**Phase state:** Phase 41 继续复核退回
**Release gate:** Phase 47 不放行；等待用户提供两项真实 MySQL 8.4 PASS 日志后进行动态证据增量复核
