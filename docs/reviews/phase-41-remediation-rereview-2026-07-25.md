# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform · Phase 41 PG-H2 / PG-M2 remediation
**Audit mode:** incremental + security / stability / testing-authenticity / release / configuration / data-integrity / concurrency
**Date:** 2026-07-25
**Reviewer:** OpenAI Codex（用户授权独立复核）

---

## 1. Executive Summary

本轮冻结整改代码 `ca11ecc191723aa38b1e3627d4101dda1c5844ee..c69ea73858fa39158e7c9494c3b587992434a03b`，并核对提交材料至 `274a9393ebe8996df90d335509619d1bbcb05478`。代码增量共 9 个文件、`+1116/-139`，目标是关闭原报告的 PG-H2（逻辑备份不可按手册恢复）与 PG-M2（MySQL 初始化 SQL 插值）。

PG-M2 的代码级整改成立：应用数据库名/用户名已收窄为安全标识符子集，应用口令在固定 SQL mode 下转义，root 凭据不进入 argv 或 `MYSQL_PWD`，sourced/executable 两条路径的临时凭据文件和最小授权也未发现新增缺陷。但仓库没有真实 MySQL 8.4 回环的 `[phase41-mysql-init-real] PASS` 证据，因此只能判定 **PG-M2 code-level closed、dynamic gate pending**。

PG-H2 仍未关闭。导出器使用 `SELECT *` 并把结果集的每一列写入显式 `INSERT` 列表；当前 V24/V28 schema 有 5 个 `GENERATED ALWAYS ... STORED` 列。MySQL 8.4 对生成列的显式写入只允许 `DEFAULT`，而产物会写入计算值或 `NULL`，因此只要 `student`、`certificate`、`file_object`、`video_upload_session` 或 `process_material` 中存在普通生产行，恢复就会失败。现有 `Phase41BackupIT` 没有给这 5 张表插入 fixture，37/37 表指纹仍可在这些表为空时假绿。

另确认两个 Medium：文本统一编码为十六进制后，单行 JSON 会膨胀为约两倍长度的单条 SQL，手册中的默认 `mysql` 客户端管道没有包大小契约；以及失败回滚用例在目标库与备份同态时破坏第一张表 `sys_region` 的第一个 INSERT，只证明 DELETE 可回滚，没有证明“若干 INSERT 已成功后再失败”仍完整回滚。手工 GitHub workflow 还存在 1 Low：文件尚不在默认分支时，`workflow_dispatch` 无法作为合并前执行入口。

正式结论为 **CHANGES_REQUESTED（1 High / 2 Medium / 1 Low）**。Phase 41 保持复核退回，Phase 47 不放行。由于代码缺陷已经阻断，本轮没有理由先执行会创建数据库账号并删除任务专属容器/匿名卷的 MySQL 8.4 动态门禁；应在下一版代码候选通过静态与安全一次性门禁后再由用户执行。

### Score Dashboard

```text
Security   ████████░░  8.2  A   PG-M2 静态边界成立；真实 MySQL 8.4 回环待证
Stability  █████░░░░░  4.5  C   生成列使常规生产快照不可恢复，超包路径可中断回放
Testing    ████░░░░░░  4.2  C   真实 MySQL/MinIO 有价值，但关键表为空且失败点过早
Release    ████░░░░░░  3.8  C   High/Medium 阻断，动态门禁及合并前 workflow 路径未闭环
──────────────────────────────
Overall    █████░░░░░  5.2  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Overall is a release-risk judgment, not a simple arithmetic average.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 1 | 1 | 0 |
| Medium | 2 | 2 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **4** | **4** | **0** |

## 2. Project Map

- `platform-system`：`DatabaseBackupService` 负责 37 张受管表的快照、gzip、SHA-256、MinIO 上传及恢复 SQL 生成。
- `platform-boot`：`Phase41BackupIT` 使用真实 MySQL scratch schema 与 MinIO 对象验证产物；Flyway V24/V28 定义 5 个生成列。
- `platform-exchange`：`import_export_batch.preview_json` 保存整批成功预览，是当前可达的大文本单行。
- `deploy/mysql-init` / Compose：负责应用账号初始化、库名与凭据传递。
- `scripts` / GitHub Actions：分别提供静态契约和显式授权的 MySQL 8.4 真实回环。
- `docs/备份与恢复手册.md`：规定 Flyway 后、禁止 `--force` 的 `gunzip | mysql` 恢复路径。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Incremental | High | `ca11ecc..c69ea73` 9 个代码/配置/测试文件及提交材料 | 未重开无关基线债务 |
| Security | High | 初始化标识符、口令转义、root 凭据路径、授权、清理目标 | 未执行账号创建、认证尝试、攻击性输入或漏洞扫描 |
| Stability | High | SQL 生成、事务边界、MySQL 生成列、客户端包限制 | 未启动服务或执行故障注入 |
| Testing Authenticity | High | `Phase41BackupIT` fixture、恢复顺序、失败注入、XML 与时间链 | 未重跑真实依赖全量测试 |
| Release | High | 分支/提交、默认分支、workflow 触发条件、手册、一次性构建 | 无 remote；未部署、切流或执行生产演练 |
| Configuration | High | Compose 环境变量链、MySQL 8.4 workflow、恢复 CLI | 未读取仓库外生产参数 |
| Data Integrity | High | 37 表清单、生成列、逻辑删除、preview JSON、指纹比较 | 未读取或修改真实业务数据 |
| Concurrency | Medium | 单事务恢复、失败回滚测试、非终态 backup_record 排除 | 按安全边界未重跑 latch/并发或破坏性交错 |

## 3. Incremental Change Summary

候选把原来的普通追加式 INSERT 产物升级为 `REPLACE_AFTER_FLYWAY`：保存外键设置，单事务反序 DELETE 37 张受管表，再正序 INSERT 快照；排除所有非终态备份记录，并把记录 ID、摘要、模式、表数和行数写入 MinIO metadata。初始化侧新增输入校验、凭据文件、静态脚本和 MySQL 8.4 手工门禁。

这些方向本身正确，但“完整回放所有列”和“所有文本转为单条十六进制字面量”引入了 schema 元数据与客户端协议两类新约束，当前实现和测试均未覆盖。

## 4. Top Risks

1. **High — 5 个生成列被显式写入，常规生产快照无法回放。**
2. **Medium — 大 `preview_json` 经十六进制编码后可超过默认 `mysql` 客户端或目标服务端包上限。**
3. **Medium — 回滚反例只在首个 INSERT 失败，不能证明已写入前缀的整体回滚。**
4. **Low — 仅 `workflow_dispatch` 的新 workflow 不能在尚未进入默认分支时手工触发。**

## 5. Detailed Findings

### Finding: 备份产物显式写入 MySQL 生成列

- Severity: High
- Confidence: High
- Category: Stability / Data Integrity
- Status: Confirmed
- Affected area: Phase 41 逻辑备份与灾难恢复
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:214-244`
  - Function / Module: `dumpTable`
  - Relevant behavior: 对每张表执行 `SELECT *`，再把 `ResultSetMetaData` 的全部列名和值写入显式 INSERT。
  - File: `platform-boot/src/main/resources/db/migration/V24__unique_active_cert_and_student_idcard.sql:27-39`
  - Relevant behavior: `certificate.active_key`、`student.idcard_key` 是 `GENERATED ALWAYS ... STORED`。
  - File: `platform-boot/src/main/resources/db/migration/V28__minio_presigned_multipart.sql:9-43`
  - Relevant behavior: `file_object.active_upload_key`、`video_upload_session.active_slot_key`、`process_material.active_file_key` 是生成列。
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase41BackupIT.java:265-315`
  - Relevant behavior: source fixture 只写 `sys_college`、`sys_major`、`notification`、`backup_record`，没有让上述 5 张表进入非空恢复路径。
  - External contract: [MySQL 8.4 generated-column rules](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html) 规定显式 INSERT/REPLACE/UPDATE 生成列时只允许 `DEFAULT`。
- Problem: 产物对生成列写入数据库读取出的计算值或 `NULL`，而不是省略该列或写 `DEFAULT`。
- Why it matters: 学生、证书、文件、视频上传和过程材料都是核心生产表。任何一张存在行，都可使“已 COMPLETED 且校验和正确”的备份在恢复时失败，原 PG-H2 的可恢复性目标没有达成。
- Realistic failure scenario:
  1. 生产库存在至少一个正常学生。
  2. 备份成功生成包含 `student.idcard_key` 的 INSERT。
  3. 运维按手册先跑 Flyway，再执行产物。
  4. MySQL 在 student INSERT 处拒绝显式生成列值；默认未使用 `--force` 的客户端中止，恢复事务不能提交，灾备库不可用。
- Minimal fix: 从数据库元数据明确识别并排除生成列，使 SELECT、列列表和值列表使用同一组可写列；也可为生成列只输出 `DEFAULT`，但省略列更清晰。不得靠硬编码 5 个列名。
- Better long-term fix: 在备份生成前建立 schema capability manifest，校验 37 张表的可导出列、生成列和不支持类型；发现未知 schema 形态时让备份任务 FAILED，而不是生成不可恢复产物。
- Regression test suggestion:
  - 在 5 张含生成列的表各插入至少一行，完整回放后比较所有列（包括目标库重新计算出的生成列）。
  - 增加元数据驱动断言：产物 INSERT 列表不得包含任何 `IS_GENERATEDCOLUMN=YES` / `EXTRA LIKE '%GENERATED%'` 的列。
  - 保留连续双回放和全 37 表指纹。
- Estimated effort: 4–8 小时

### Finding: 十六进制单行 SQL 缺少 MySQL packet 上限契约

- Severity: Medium
- Confidence: High
- Category: Stability / Release / Data Integrity
- Status: Confirmed
- Affected area: 大字段备份及手册中的真实 mysql 客户端恢复
- Evidence:
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:233-243,278-282`
  - Function / Module: `dumpTable` / `quote`
  - Relevant behavior: 每行生成一条 INSERT；文本的每个 UTF-8 字节被编码为两个十六进制字符。
  - File: `platform-boot/src/main/resources/db/migration/V17__exchange.sql:8-15`
  - Relevant behavior: `import_export_batch.preview_json` 是 JSON 列。
  - File: `platform-exchange/src/main/java/cn/edu/gpnu/platform/exchange/service/impl/ExchangeServiceImpl.java:216-222`
  - Relevant behavior: 预校验成功行集合整体序列化后写入单个 `preview_json`。
  - File: `platform-boot/src/main/resources/application.yml:8-12`
  - Relevant behavior: 框架接受最高 100MB 单文件/120MB 请求；未发现与备份单行相匹配的 JSON 或 SQL statement 上限。
  - File: `docs/备份与恢复手册.md:75-82`
  - Relevant behavior: 恢复命令使用默认 `mysql --binary-mode`，没有声明客户端/服务端 `max_allowed_packet`。
  - External contract: [MySQL 8.4 packet-size rules](https://dev.mysql.com/doc/refman/8.4/en/packet-too-large.html) 说明单条 SQL 是一个通信 packet，客户端与服务端均有上限；文档给出的 mysql 客户端默认值为 16MB、服务端默认值为 64MB。
- Problem: 约 8MB 的 UTF-8 JSON 文本经过 `0x...` 后，单条 INSERT 已接近或超过默认 mysql 客户端 16MB 上限；更大值还可超过目标服务端上限。源库能保存原始行，不代表恢复客户端能发送膨胀后的语句。
- Why it matters: 备份任务仍会标记 COMPLETED；故障只在灾备恢复时暴露。当前真实 scratch IT 使用 JDBC 按行执行小 fixture，并没有经过手册规定的 mysql 客户端管道。
- Realistic failure scenario: 一个较大的导入批次形成超过 8MB 的 `preview_json`；备份生成约 16MB 以上的单条 INSERT；默认 mysql 客户端报 `ER_NET_PACKET_TOO_LARGE` 并关闭连接，恢复失败。
- Minimal fix: 明确且验证源单行上限、备份后 statement 上限、mysql 客户端参数和目标服务端参数；恢复手册必须给出两端一致的受支持值。若继续采用 SQL 格式，备份前应拒绝超过已验证上限的行并把任务置 FAILED。
- Better long-term fix: 使用不把大文本扩为单个 SQL packet 的可流式恢复格式/工具，或将大 JSON 从批次主行拆为有界分片，并为恢复工具实现显式 framing。
- Regression test suggestion: 在任务专属 MySQL 8.4 环境写入安全边界内但源文本大于 8MB 的 `preview_json`，用手册的真实 `gunzip | mysql` 路径回放并核对摘要；同时验证超过声明上限时备份 fail closed。
- Estimated effort: 0.5–1 天

### Finding: 失败回滚测试没有覆盖成功 INSERT 之后的失败

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity / Concurrency
- Status: Confirmed
- Affected area: `Phase41BackupIT` 恢复原子性证据
- Evidence:
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase41BackupIT.java:171-186`
  - Function / Module: `backupCanRestoreCompleteSnapshotAfterFlywayAndRemainAtomic`
  - Relevant behavior: 两次成功回放后目标库已与产物同态；随后把 `INSERT INTO sys_region` 替换为不存在的表，再比较失败前后快照。
  - File: `platform-system/src/main/java/cn/edu/gpnu/platform/system/service/impl/DatabaseBackupService.java:62-65,183-202`
  - Relevant behavior: `sys_region` 是正序回放的第一张表；失败前只执行了反序 DELETE，没有任何快照 INSERT 成功。
- Problem: 测试只证明前置 DELETE 会回滚，不能证明多个 INSERT 成功后出现错误时已写入前缀也会整体回滚。目标库又与产物同态，即使出现某些前缀部分提交，最终快照也可能相同。
- Why it matters: 候选材料把该测试表述为“前置 DELETE 与已写入行整体回滚”，证据强度高于实际覆盖。恢复是关键数据路径，原子性不能仅靠静态阅读和一个过早失败点。
- Realistic failure scenario: 后续恢复执行器或脚本分句逻辑意外在若干表后提交；当前用例仍可能因为失败发生在第一条 INSERT、且目标与产物同态而保持绿色。
- Minimal fix: 失败前先给目标库制造与备份不同的可识别 drift；把失败注入移动到保证非空的后段表，并显式证明至少一个前缀 INSERT 已执行。失败后断言 drift 和 37 张表都精确回到失败前快照。
- Better long-term fix: 参数化多个失败点（首表、中段、末表、COMMIT 前），由执行 trace 记录 DELETE/INSERT 的顺序和成功前缀，避免用最终状态反推控制流。
- Regression test suggestion: 选择 `notification` 或 `backup_record` 作为后段故障点；先改目标 `sys_region`/`sys_college` 为与备份不同的值，确认前段确已写入备份值，再触发失败并验证原 drift 被恢复。
- Estimated effort: 2–4 小时

### Finding: 新 workflow 不能作为合并前手工门禁入口

- Severity: Low
- Confidence: High
- Category: Release / Configuration
- Status: Confirmed
- Affected area: Phase 41 MySQL 8.4 动态门禁说明
- Evidence:
  - File: `.github/workflows/phase41-mysql-init-real.yml:1-19`
  - Relevant behavior: workflow 只有 `workflow_dispatch`。
  - File: `docs/reviews/phase-41-remediation-submission-2026-07-25.md:57-66`
  - Relevant behavior: 把 Actions → Run workflow 列为候选合并前的手工执行方式。
  - Repository state: 该 workflow 不存在于本地 `main`，且仓库没有 remote。
  - External contract: [GitHub Actions manual workflow documentation](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow) 要求可手工触发的 workflow 文件存在于默认分支。
- Problem: 当前候选分支无法通过 GitHub UI 直接出现这一手工入口，提交材料给出的两条执行路径实际只剩本地显式授权脚本。
- Why it matters: 这不会破坏生产代码，但会使门禁交付说明不可执行，并可能导致“以为 GitHub 可跑、实际没有动态证据”的流程假绿。
- Realistic failure scenario: 复核者打开 Actions 找不到该 workflow，或 CLI dispatch 返回 workflow 不存在；候选因此长期停留在待证状态。
- Minimal fix: 把提交材料改为：合并前仅支持经用户授权的本地隔离脚本；workflow 只有在文件先进入默认分支后才能使用。若必须合并前通过 Actions，需先以独立治理提交落一个不会自动运行的 bootstrap workflow。
- Better long-term fix: 将高风险手工门禁统一登记为 release environment 的受保护 workflow，并把默认分支存在性、审批人和证据归档作为固定前置条件。
- Regression test suggestion: 在门禁说明中加入 `gh workflow view` / 默认分支存在性检查；缺失时明确返回“use local authorized gate”，不得宣称可从 Actions 运行。
- Estimated effort: 15–30 分钟

## 6. Security Concerns

- Coverage: High
- Inspected evidence: `01-app-user.sh` 的输入校验、SQL 字面量、sourced/executable 分支、临时 option file、Compose 变量链、精确授权与 label 清理条件。
- Exclusions / limits: 未执行账号创建、认证尝试、漏洞扫描、恶意载荷、fuzz、网络探测或任何可能属于 cyber 的操作。

PG-M2 的代码级修复可以关闭：标识符无法逃逸，应用口令不会改变 heredoc SQL 结构，root 凭据不进入 argv/`MYSQL_PWD`，临时文件按 0600 创建并覆盖成功/失败清理。未发现新安全 finding。真实 MySQL 8.4 sourced/executable 认证和授权仍是动态证据门禁，不能由静态审计替代。

## 7. Stability Concerns

- Coverage: High
- Inspected evidence: 37 表恢复顺序、生成列、SQL 字面量、事务语句、真实客户端手册。
- Exclusions / limits: 未启动 MySQL/Redis/MinIO 或项目服务，未做故障注入。

High 和第一个 Medium 都发生在“备份生成成功、灾难恢复才失败”的晚暴露路径，优先级高于动态账号回环。默认 mysql 客户端未启用 `--force`，因此错误会中止执行；这降低半恢复提交风险，但不会改变备份不可用的发布阻断。

## 8. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `.env.example`、`docker-compose.yml`、CI MySQL 版本、手工 workflow、初始化脚本参数链。
- Exclusions / limits: 未接触生产 secret、外部 CI 配置或实际 GitHub 仓库。

应用数据库名通过同一个 `${MYSQL_DATABASE}` 同时进入初始化脚本和后端 URL，正常显式配置下链路一致。未计入本增量的既有边界包括：完全缺失 `MYSQL_DATABASE` 时初始化脚本与后端 fail-fast 口径不同、root healthcheck argv、首次空卷后不自动轮换账号口令，以及应用账号兼持 Flyway DDL 权限；这些留最终全量审计，不阻断对本增量 PG-M2 的代码级关闭。

## 9. Data Integrity Analysis

- Coverage: High
- Inspected evidence: Flyway V1–V32 schema、37 表清单、生成列、终态备份记录筛选、全字段指纹、preview JSON。
- Exclusions / limits: 未读取、导出或修改真实业务数据。

生成列 finding 直接阻断“受管数据可完整恢复”。修复后必须让 5 张表均有行；只比较空表 schema 或表名数量不能证明行级恢复。`backup_record` 排除 `RUNNING/PENDING`、保留 `COMPLETED/FAILED` 的策略本轮未发现缺陷。

## 10. Concurrency Analysis

- Coverage: Medium
- Inspected evidence: 单一 `START TRANSACTION`/末尾 `COMMIT`、失败时 JDBC rollback、并发非终态备份排除。
- Exclusions / limits: 未重跑 latch、并发恢复、压力或破坏性故障。

生产 SQL 静态上只有一个显式事务和一个终点 COMMIT，未确认真实部分提交缺陷；Finding 3 是关键测试证据不足，故评 Medium 而非数据损坏 High。下一轮应通过后段失败点和目标 drift 证明真实原子性，不需要压力或攻击性并发。

## 11. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: `Phase41BackupIT` 全流程、fixture 覆盖、恢复执行器、Failsafe XML、源/class/XML/提交时间线。
- Exclusions / limits: 遵守用户安全边界，未独立运行会启动依赖、建账号、删除容器/卷或可能被理解为 cyber 的脚本。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| 37 表清单/表数 | High | 清单与 schema 数量一致 | Keep |
| 小字段 scratch 回放 | High | 真实 MySQL 8.0 + MinIO，JDBC 执行 | Keep |
| 生成列表 | None | 5 张关键表均为空 | Add non-empty fixtures |
| 大单行/真实 mysql CLI | None | JDBC 小值不覆盖客户端 packet | Add MySQL 8.4 CLI gate |
| 连续双回放 | High | 目标外行删除与同产物二次回放成立 | Keep |
| 失败整体回滚 | Low | 首个 INSERT 即失败，目标又与产物同态 | Strengthen failure point |
| MySQL 初始化静态契约 | High | 两条路径及凭据文件被静态/桩覆盖 | Keep |
| MySQL 8.4 真实认证/授权 | None | 没有 `[phase41-mysql-init-real] PASS` | Run only after code fixes |

现有 XML 共 45 个文件，Surefire **149/149** + Failsafe **185/185** = **334/334**，0 failure/error/skip；`Phase41BackupIT` **1/1**。报告日志显示数据库是 MySQL 8.0，且 XML 时间晚于最终源码、早于候选提交，支持开发者确实运行过该候选门禁，但不支持生成列、大 packet 或 MySQL 8.4 动态结论。

## 12. Release Concerns

- Coverage: High
- Inspected evidence: 冻结提交、Git 状态、现有测试 XML、后端/前端一次性构建、Compose 解析、手册与 workflow。
- Exclusions / limits: 未执行 clean verify、真实依赖回归、动态账号门禁、部署、切流、PITR 或生产恢复演练。

独立安全一次性门禁：

```text
git diff --check ca11ecc..274a939        PASS
mvn -B -ntp -DskipTests package          BUILD SUCCESS（9 modules）
npm --prefix frontend run type-check     PASS
npm --prefix frontend run build          PASS（仅既有 >900 kB chunk warning）
docker compose -f docker-compose.yml
  config --quiet                          PASS（审查环境缺生产变量，仅有预期 warning）
```

本地没有运行中的容器。Git Bash 因 Windows signal-pipe 权限失败、WSL 因沙箱 `E_ACCESSDENIED`，故未把本轮 shell 语法尝试作为候选代码失败；候选现有静态脚本证据仍可保留，但真实 MySQL 8.4 门禁缺失。High/Medium 已足够阻断发布，不应让用户现在承担动态账号/容器门禁成本。

## 13. Principles Compliance

| Principle | Assessment | Notes |
|-----------|------------|-------|
| KISS | Partial | 单事务 SQL 产物直观，但对生成列和 packet 隐含约束处理不足 |
| DRY | Good | 37 表清单和初始化生产脚本被测试复用 |
| YAGNI | Good | 本轮没有扩成 PITR 或生产切换 |
| Fail closed | Partial | 初始化输入能关闭；不可恢复的 schema/大行仍被标为 COMPLETED |
| Evidence honesty | Weak | 失败回滚和 37/37 描述超出 fixture 实际覆盖 |
| Least privilege | Good | 本增量未扩大既定应用账号权限 |

## 14. Recommended Fix Order

1. **先关闭 High：** 元数据驱动排除所有生成列，并让 5 张表各有真实行完成回放/全字段对账。
2. **再关闭 packet Medium：** 明确受支持的行/statement/客户端/服务端上限；优先采用不会把大文本变成超大单语句的恢复格式。用真实 mysql 8.4 CLI 覆盖安全有界的大 JSON。
3. **强化原子性证据：** 目标先制造 drift，把故障移到后段非空表，证明已有 INSERT 成功后仍回到失败前完整状态。
4. **修正文档/workflow：** 合并前只宣称本地显式授权路径；默认分支存在 workflow 后才能使用 GitHub 手工 dispatch。
5. **重跑门禁：** Phase41BackupIT、全量 `clean verify`、前端、Compose、diff check 全绿后，才由用户执行 MySQL 8.4 账号/授权回环并归档 `[phase41-mysql-init-real] PASS`。
6. **重新提交独立增量复核。** PASS 前 Phase 41 不置 ✅、Phase 47 不放行。

## 15. Quick Wins

- 给 `Phase41BackupIT` 的 5 张生成列表补最小 fixture，可立即把最危险的假绿变成红灯。
- 把失败替换目标从第一张 `sys_region` 移到后段非空表，并在失败前插入目标 drift。
- 在手册中删除当前不可用的“候选分支直接 Run workflow”表述。
- 下一候选提交前先静态扫描 `information_schema.columns.EXTRA`，产物中出现任何生成列名即失败。

---

**Final verdict:** `CHANGES_REQUESTED`
**Phase state:** Phase 41 继续复核退回
**Release gate:** Phase 47 不放行；先修 1 High / 2 Medium / 1 Low，再执行用户控制的 MySQL 8.4 动态门禁
