# Phase 41 第二轮退回整改候选提交（2026-07-25）

> **PASS 后续勘误（2026-07-25）：** 后续独立动态复核已将 Phase 41 判定为
> **PASS（0 High / 0 Medium / 1 Low 非阻断）**。该 Low 所指出的 Gate A 冷认证
> 缓存前置现已补入下方 JDBC 示例，真实 runner 也会在 Maven 启动前要求“受验证
> TLS / 允许回环公钥获取 / 受控服务器 RSA 公钥文件”三选一。本文其余
> `CHANGES_REQUESTED` 表述保留为提交当时的历史快照。

## 结论与冻结范围

- 原正式结论保持 **CHANGES_REQUESTED（1 High / 2 Medium / 1 Low）**，依据
  `docs/reviews/phase-41-remediation-rereview-2026-07-25.md`。
- 本文只提交第二轮整改候选，不是独立复核报告，不将 Phase 41 改判 PASS，
  不放行 Phase 47。
- 当前分支：`codex/phase41-second-remediation`。
- 第二轮代码冻结点：`b5ed7f5`（`fix(phase41): 关闭生成列与大行恢复缺口`）。
- 本轮最小代码范围：`17b11aa..b5ed7f5`；从原 Phase 41 退回基线计算的完整
  整改范围：`ca11ecc..b5ed7f5`。

## 1 High / 2 Medium / 1 Low 的候选关闭链

| 原 finding | 第二轮候选修复 | 候选证据 |
|---|---|---|
| **High：显式回放 5 个生成列** | 从 JDBC metadata 读取唯一写入列集合，只接受 `IS_GENERATEDCOLUMN=NO`；无法确认元数据时失败关闭。导出 `SELECT` 与恢复 `INSERT` 共用该集合，生成列交由目标 MySQL 重算。 | `Phase41BackupIT` 给 `student`、`certificate`、`file_object`、`video_upload_session`、`process_material` 五张表各造合法非空行；产物不得出现生成列，恢复后仍把目标重算值纳入 37 表全字段指纹。 |
| **Medium：hex 大行缺 packet 契约** | 文本/二进制改为无换行 Base64；文本以 `CONVERT(FROM_BASE64(...) USING utf8mb4)` 恢复，JSON 再显式 CAST。导出器先计算 UTF-8/Base64/包装字节预算，再允许分配和编码；默认单语句上限 32 MiB，只允许向下收紧。产物头、MinIO metadata、Compose 和手册统一要求客户端/服务端至少 64 MiB packet。 | 13 MiB JSON 夹具要求真实 INSERT 大于默认客户端 16 MiB且不超过 32 MiB；另将 cap 收紧至 1 MiB，断言任务 `FAILED`、无 URI/校验和且错误信息不泄漏数据。真实 MySQL 8.4 CLI 动态门禁尚待用户执行。 |
| **Medium：失败注入过早且目标同态** | 先对目标 37 表制造与产物不同的 drift；回放全部正常 INSERT 后，在唯一 `COMMIT` 前注入缺表语句。执行跟踪必须同时证明缺表 SQLState `42S02`、全部正常 INSERT 已成功、`COMMIT=false`、显式 `ROLLBACK=true`，再核对 37 表精确回到尝试前 drift。 | 新反例已进入 `Phase41BackupIT`；其真实 MySQL 动态执行尚待用户门禁。 |
| **Low：特性分支无法触发新 workflow** | 旧提交材料已勘误。合并前只提供用户在获授权 Linux/WSL 隔离环境运行的本地脚本；GitHub `workflow_dispatch` 仅在 workflow 文件进入默认分支后作为后续入口，不再冒充合并前证据。 | `docs/reviews/phase-41-remediation-submission-2026-07-25.md` 顶部勘误；本文件下方给出两项用户专属命令及影响。 |

实现过程中另做三路只读静态交叉复核，补齐并关闭了流式 ResultSet、编码前预算、
回滚跟踪、回环 authority、JVM `-D` 覆盖、复用对象前缀及预检目标与最终注入目标
不一致等候选缺口；最终内部静态结果为 **0 High / 0 Medium / 0 Low**。这只是
整改者内部证据，不替代真实动态门禁与独立增量复核。

## 已由 Codex 执行的非 cyber 安全门禁

| 门禁 | 结果 |
|---|---|
| `mvn -B -ntp test` | **BUILD SUCCESS**；Surefire 149/149，0 failure/error/skip；不会运行 `*IT` |
| `mvn -B -ntp -DskipTests package` | 9 模块 **BUILD SUCCESS** |
| 前端 | `type-check`、生产 `build` 均 PASS；仅既有大 chunk warning |
| Compose | dev/prod `config --quiet` PASS；仅解析配置，未启动容器 |
| 脚本静态检查 | 三个 Phase 41 脚本 `bash -n` PASS |
| 初始化静态契约 | `scripts/test-phase41-mysql-init.sh` PASS；只使用 stub/临时文件，不连接数据库或 Docker |
| 工作树 | `git diff --check` PASS |

Codex 未启动后端/前端常驻服务，未运行 `Phase41BackupIT`、Failsafe、`clean verify`、
真实 `mysql` 客户端恢复、真实 MySQL/Redis/MinIO 写入、账号/授权验证、Docker
容器创建删除、漏洞扫描、攻击性探测、恶意载荷、fuzz、压力或任何可能属于 cyber
的动作。

## 必须由用户亲自执行的安全敏感动态门禁

下面两项都可能涉及账号、数据库、对象存储或容器变更，Codex **不会执行**。
必须先确认目标是本机回环、无真实业务数据、可销毁的专用隔离环境。

### A. TCP_SQL_V2 真实 MySQL 8.4 CLI 恢复

影响范围：

- 不启动或删除容器；
- 在指定回环 MySQL 创建并精确删除两个唯一的
  `teacher_cert_p41_source_*` / `teacher_cert_p41_restore_*` scratch schema；
- 在用户预先创建的 `teacher-cert-p41-*` 专用测试桶中，使用每次自动生成且运行前
  为空的独占前缀创建并删除一个测试对象；
- 要求 MySQL 服务端与 `mysql` 客户端均为 8.4.x，服务端
  `max_allowed_packet >= 64 MiB`，账号具有对上述 scratch schema 建删权限。

仅由用户在获授权的 Linux/WSL 隔离环境填入专用测试凭据后执行：

```bash
export PHASE41_ALLOW_BACKUP_RESTORE_TEST=1
export PHASE41_ISOLATED_ENVIRONMENT_ACK=1
export PHASE41_DEDICATED_TARGETS_ACK=1
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:<mysql-port>/<dedicated-db>?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export SPRING_DATASOURCE_USERNAME='<dedicated-test-user>'
export SPRING_DATASOURCE_PASSWORD='<dedicated-test-password>'
export SPRING_DATA_REDIS_HOST='127.0.0.1'
export SPRING_DATA_REDIS_PORT='<redis-port>'
export SPRING_DATA_REDIS_PASSWORD='<dedicated-redis-password-if-any>'
export MINIO_ENDPOINT='http://127.0.0.1:<minio-port>'
export MINIO_ACCESS_KEY='<dedicated-minio-access-key>'
export MINIO_SECRET_KEY='<dedicated-minio-secret-key>'
export MINIO_BUCKET='teacher-cert-p41-<dedicated-suffix>'
bash scripts/test-phase41-backup-restore-real.sh
```

上例的 `allowPublicKeyRetrieval=true&useSSL=false` 只允许用于 runner 已强制校验的
本机回环、专用隔离测试目标，用于避免 MySQL 8.4 新账号在
`caching_sha2_password` 冷认证缓存下依赖未记录的预热状态；它不是生产 TLS 证明。
若不采用该隔离模式，JDBC URL 必须改为 `sslMode=VERIFY_CA/VERIFY_IDENTITY`
（并提供受控信任链），或提供可读绝对路径
`serverRSAPublicKeyFile=/secure/path/mysql-server-rsa-public-key.pem`。

预期末行：`[phase41-backup-restore-real] PASS`。

### B. MySQL 8.4 首次初始化账号/精确授权回环

影响范围：

- 必要时拉取 `mysql:8.4` 镜像；
- 创建两个带项目专用 label 的一次性容器及匿名卷；
- 在容器内分别覆盖官方 entrypoint 的 sourced/executable 路径，创建测试库和测试账号，
  验证真实认证及精确授权；
- 结束时精确删除这两个一次性容器及其匿名卷。

仅由用户在获授权的 Linux/WSL 隔离 Docker 环境执行：

```bash
PHASE41_ALLOW_REAL_DOCKER_TEST=1 bash scripts/test-phase41-mysql-init-real.sh
```

预期末行：`[phase41-mysql-init-real] PASS`。

`.github/workflows/phase41-mysql-init-real.yml` 只有进入默认分支后才可从 GitHub
Actions 手工触发；当前仓库无 remote，不能作为本候选合并前门禁。

## 独立增量复核请求

1. 用户先保存上述 A、B 两项完整 PASS 日志；任一未执行或失败，均不得把 Phase 41
   改判 PASS。
2. 独立复核者冻结 `17b11aa..b5ed7f5`，按原 1 High / 2 Medium / 1 Low 逐项重跑
   真实反例，并检查必要回归与提交同源性。
3. 新的独立报告 PASS 前，Phase 41 与全项目保持 **CHANGES_REQUESTED**，
   Phase 47 不放行。
