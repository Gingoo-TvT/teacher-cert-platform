# Phase 41 退回整改候选提交（2026-07-25）

## 结论与冻结范围

- 当前状态：**整改代码候选已冻结；待用户执行安全敏感动态门禁，再交独立增量复核**。
- 正式阶段状态：仍为 **CHANGES REQUESTED**；本文不是 PASS 报告，不放行 Phase 47。
- 原报告：`docs/reviews/phase-41-review.md`。
- 增量基线：`ca11ecc..c69ea73`。
- 代码冻结点：`c69ea73`（`fix(phase41): 保证逻辑备份可完整恢复`）。
- 分支：`codex/phase41-recoverable-backup`。
- 本轮只请求关闭原报告的 PG-H2（逻辑备份不可按手册恢复）与 PG-M2（MySQL 初始化 SQL 插值）；PITR、生产切换等运维能力不冒充本轮闭环。

## PG-H2：可完整替换恢复的逻辑备份

1. `DatabaseBackupService` 生成 `REPLACE_AFTER_FLYWAY` 产物：
   - 保存会话外键设置，单事务反序 `DELETE` 37 张受管表，再正序 `INSERT` 快照，成功后 `COMMIT`；
   - 不使用会隐式提交的 `TRUNCATE`，任一语句失败时可整体回滚；
   - 文本值使用 UTF-8 十六进制表达式，避免引号、反斜杠、换行及 SQL mode 改变回放语义；
   - `backup_record` 只导出终态 `COMPLETED/FAILED`，排除本次及全部并发 `RUNNING/PENDING`；
   - MinIO user metadata 同步保存 `record-id/sha256/restore-mode/table-count/row-count`，灾备索引不只依赖源库内本次备份记录。
2. `Phase41BackupIT` 使用两个唯一前缀 scratch schema：
   - source 与 restore 均只执行生产 Flyway V1–V32，restore 明确带 RBAC/字典/参数种子；
   - 读取真实 MinIO gzip，验证对象大小、SHA-256 与 user metadata；
   - 完整执行产物并对 37/37 表做逐行全字段确定性 SHA-256 指纹；
   - 覆盖逻辑删除、引号/反斜杠/换行文本、父子关系、目标库快照外行删除；
   - 覆盖历史 `COMPLETED/FAILED` 保留、并发 `RUNNING/PENDING` 与本次记录排除；
   - 同一产物连续回放两次结果一致；测试内 JDBC 会话构造中途 INSERT 失败，确认前置 DELETE 与已写入行整体回滚；
   - MinIO 对象和两个 scratch schema 均为任务专属精确目标，清理失败会使测试红灯。

## PG-M2：MySQL 初始化输入与凭据边界

1. `docker-compose.yml` 不再把可配置库名交给官方 `MYSQL_DATABASE` 提前消费，改由 `APP_DATABASE` 进入项目脚本后校验。
2. `01-app-user.sh`：
   - `DB_USERNAME`/`APP_DATABASE` 仅允许 ASCII 字母、数字、下划线并限制 MySQL 长度，拒绝 `root`；
   - 拒绝空应用口令和空 root 口令；
   - 固定会话字符串解析模式并对应用口令做字面量转义；
   - sourced 路径复用官方 `docker_process_sql`；
   - executable fallback 使用权限 `0600` 的一次性 option file，不把 root 口令放入 argv，也不使用已弃用的 `MYSQL_PWD`；正常、mysql 非零及 HUP/INT/TERM 均落到清理。
3. `scripts/test-phase41-mysql-init.sh` 为不连接数据库的静态契约门禁，覆盖特殊字符、非法标识符、两条执行分支、日志/argv 口令泄露和 option file 成功/失败清理。
4. `scripts/test-phase41-mysql-init-real.sh` 与手工 `workflow_dispatch` 工作流已准备：在两个一次性 `mysql:8.4` 容器中分别覆盖官方 0644 sourced、0755 executable 路径，随后用特殊应用口令真实登录，核对数据库、`CURRENT_USER` 及精确两条 `SHOW GRANTS`。该脚本具有专用 label、无端口发布、仅匿名卷、删除前复核 label；非 GitHub CI 必须显式设置授权开关。

## 已执行证据

| 门禁 | 结果 |
|---|---|
| `Phase41BackupIT` 定向恢复 | **1/1 PASS**；真实 MySQL scratch + 真实 MinIO gzip；37/37 指纹、双回放、失败回滚 |
| `mvn -B -ntp clean verify` | **BUILD SUCCESS**；Surefire **149/149** + Failsafe **185/185** = **334/334**；0 failure/error/skip |
| `Phase41BackupIT` 最终 XML | **1/1**，0 failure/error/skip |
| 静态初始化契约 | `bash scripts/test-phase41-mysql-init.sh` **PASS** |
| 前端 | `npm --prefix frontend run type-check`、`npm --prefix frontend run build` **PASS**；仅既有大 chunk warning |
| Compose | dev、prod `config --quiet` **PASS** |
| 工作树 | `git diff --check` **PASS** |
| 资源收尾 | `teacher_cert_p41_%` scratch schema 为 0；dev MySQL/Redis/MinIO 已 stop，未删除数据卷；8080/5173 无监听 |

## 必须由用户或独立复核者执行的安全敏感门禁

Codex **未执行**真实 MySQL 账号认证/授权回环。它会创建账号、校验授权，并删除两个任务专属一次性 MySQL 容器及匿名卷，按用户要求只明确交由用户决定并执行：

- GitHub 手工方式：在分支可用后打开 Actions → **Phase 41 MySQL 8.4 手工安全回环** → **Run workflow**。
- 获授权的 Linux/WSL 隔离 Docker 环境：

  ```bash
  PHASE41_ALLOW_REAL_DOCKER_TEST=1 bash scripts/test-phase41-mysql-init-real.sh
  ```

预期最终输出：`[phase41-mysql-init-real] PASS`。未取得这条真实 MySQL 8.4 证据前，只能表述为“脚本与门禁静态复核通过”，不能表述为真实初始化回环已通过。

## 明确未覆盖的边界

- 没有执行真实 `mysql` 客户端管道恢复；自动证据是对真实 MySQL scratch 使用 JDBC 执行同一 gzip SQL。
- 没有执行生产库切换、生产凭据路径、真实 PITR、binlog 连续归档/回放、MinIO mirror、超大库、慢 MinIO、容量/性能演练。
- `docs/备份与恢复手册.md` 已修正 `--source-data=2` 坐标、隔离恢复实例及连续回放口径；文档修正不等于 T-107/PITR 已动态验收。
- 没有启动后端或前端常驻服务。
- 没有执行漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz、压力测试或任何 cyber 操作。

## 独立复核请求

1. 先取得上述用户执行的 MySQL 8.4 手工门禁结果。
2. 冻结 `ca11ecc..c69ea73`，仅复核 PG-H2/PG-M2 的关闭链及必要回归。
3. 独立确认结果前，Phase 41 与全项目保持 **CHANGES REQUESTED**，Phase 47 不放行。
