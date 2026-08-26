# 最终审计第五整改候选真实门禁证据（2026-07-29）

## 证据身份

- 执行者：Claude 隔离复核环境；Codex 未重跑真实依赖。
- Git HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- tracked diff SHA-256：
  `c4473e7638973f1a0d8df3e240f2e2f607c56002aa4cf3a1a88b373ea63294ab`
- 执行时共有 113 个工作树状态项：81 个 tracked 改动路径、32 个 untracked 状态分组；
  tracked diff 身份在执行前后不变。
- 执行口径：Phase 2 与 Phase 41 均在同一个一次性栈、同一份工作树执行。此历史证据只记录
  tracked diff，没有哈希参与编译的 untracked 源码，因此不构成完整候选的严格字节绑定。

## 门禁结果

1. `Phase2SecurityIT`
   - 命令选择器：`-Dit.test=Phase2SecurityIT`
   - 结果：4/4 PASS，0 failure/error/skip，21.032 秒，Maven BUILD SUCCESS，exit 0。
   - 新增反例 `resetPasswordAuditFailureRollsBackMysqlUpdate` 命中一次测试局部
     `AuditLogService.record(...)` 故障；HTTP、事务代理、`sys_user` 全行回滚及旧口令重新登录
     均使用隔离真实 MySQL。
2. `Phase41BackupIT`
   - 命令：`bash scripts/test-phase41-backup-restore-real.sh`
   - 结果：1/1 PASS，0 failure/error/skip，20.134 秒，Maven BUILD SUCCESS，exit 0。
   - 覆盖真实 MySQL 8.4 CLI 回放、37 表全字段指纹、原子回滚、单语句超限失败关闭，以及
     scratch schema 和专用桶前缀清理。
3. 两次 Maven 执行各自带出的 Surefire 均为 349/349，0 failure/error/skip。

`failsafe-summary.xml` 来自顺序执行后的最后一次 Phase 41 命令，因此只记录 `completed=1`；
两份目标 `TEST-*.xml` 分别证明 Phase 2 的 4 条和 Phase 41 的 1 条，不应把 summary 解读为两次
命令的合并计数。

## 隔离与清理边界

- 一次性 MySQL 8.4.11 / Redis 7.4 / MinIO / runner 共享单一网络命名空间，不向主机发布端口，
  不挂卷；runner 使用 JDK 17、Maven 3.9.16、MySQL client 8.4.11，并以 `--init` 回收子进程。
- 执行者报告两个 `teacher_cert_p41_*` scratch schema 已清除、专用桶前缀为空，随后销毁全部
  一次性容器、镜像和卷；凭据文件已删除。
- 执行者报告线上 `teacher-cert-*-prod` 五个容器全程未触碰。以上环境与清理事实属于执行者
  提供的证据，不是 Codex 本机重跑结论。

## 归档范围与安全检查

- 只归档 `run3-*`，排除绑定旧 diff 的 `run2-*`、`gate-*` 和临时 Trigger 测试源。
- 可提交的规范日志为 `logs/run3-phase2.log.txt` 与 `logs/run3-phase41.log.txt`；它们分别是
  执行者原始 `.log` 的逐字节副本，并由 `.gitattributes` 标记为 `-text -diff`，避免 checkout
  换行转换。原始日志及 Failsafe XML/TXT 的 SHA-256 见 `SHA256SUMS`。
- 本机保留的同名 `.log` 仅是被全局 `*.log` 忽略的历史来源，不属于规范证据清单；普通提交应
  携带 `.log.txt`，不得依赖 force-add 或本机 ignored 文件。
- 本地归档前检查未发现 Bearer token、JWT fixture secret、数据库/MinIO 密码、私钥、带凭据
  JDBC URL 或 JSON access/refresh token。正则命中的 `pwd` 均为日志字段
  `must_change_pwd=1`；日志中的已知初始口令属于公开测试夹具，不是生产凭据。

## 判定边界

本目录只证明上述动态命令在指定 tracked diff 上的执行结果。后续重跑须在门禁前于仓库外生成
`scripts/candidate_source_manifest.py` 清单，并在两项门禁后复算一致，以同时绑定 tracked diff
和全部非忽略 untracked 文件。它不构成
`docs/REVIEW-GATE.md` 所要求的独立增量复核 PASS，也不授权 merge、push、部署或切流；项目正式
状态继续以最新独立复核报告和 `docs/CURRENT-EXECUTION-PLAN.md` 为准。
