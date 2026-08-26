# WS-8 正式退回整改候选提交说明（2026-08-13）

## 1. 身份与结论边界

- 分支：`feature/ws08-idcard-encryption`
- HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- 原候选 fingerprint：`f4434bb8de4584544aa993bad967e6179a5a9d409f1f3856b21c2486d3c3f0df`
- 原正式报告：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\ws08-stage-independent-rereview-20260812-f4434bb8\review.md`
  （SHA-256 `17C91C9B055FC7AD08BA3ACF7205644F85D9B920DD400739C42BD6F5D765C713`）
- 原结论：`CHANGES_REQUESTED（0 Critical / 1 High / 2 Medium / 0 Low）`
- 新候选 manifest：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-remediation-candidate-2026-08-13.json`
  （完整 fingerprint、HEAD、tracked diff 与 untracked 哈希以该仓库外文件为准）
- 提交者状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_REREVIEW_PENDING`

本材料只请求对原 1 High / 2 Medium 做增量重核；不签发 WS-8 PASS，不放行 WS-9，也不授权
stage/commit/merge/push/deploy/cutover。项目继续 `CHANGES_REQUESTED / NO-GO`。

## 2. 原 finding → 最小整改映射

### High：V33 持久化协议缺少停机切换与旧 binary 回滚禁令

- `docs/phase-14-非功能部署验收.md` §3.1 固定：
  `FREEZE_WRITES > STOP_ALL_OLD > BACKUP_V32 > MIGRATE_V33 > START_ALL_NEW > VERIFY > OPEN_TRAFFIC`。
- 不可逆边界是 V33-capable 进程**开始迁移**；即使 `AFTER_MIGRATE` 回填失败，也禁止旧 binary 再连接该库。
- README 在通用 Compose `up` 前阻断 V32→V33 误用；Compose 顶部有 `WS8_V33_OFFLINE_UPGRADE` 警告；备份手册
  绑定 V33 前数据库/对象恢复点、校验和及原 key/pepper 版本引用。
- `scripts/test_ws8_v33_release_contract.py` 固定步骤顺序、回滚两分支及三份活动材料接线，并进入 CI runner 合同 job。

### Medium：AES key、HMAC pepper、JWT secret 未强制相互独立

- `RuntimeProfileGuard` 在上下文/Flyway 前读取 trim 后三项值，拒绝 AES=HMAC、HMAC=JWT、AES=JWT；错误只列键名。
- `IdCardProtectionService` 构造器另拒绝 trim 后 AES key 与 HMAC pepper 相同，覆盖直接实例化。
- `RuntimeProfileGuardTest` 覆盖三组两两复用、三项不同正例和真实 SpringApplication 启动前自然退出；
  `IdCardProtectionServiceTest` 覆盖带空白的直接复用反例。

### Medium：WS-8 测试未接 CI，且缺 HMAC 冲突前置反例

- backend job 保持既有 Phase 0 exact gate 不变，随后独立运行 WS-8 selector：
  `IdCardProtectionServiceTest`、`RuntimeProfileGuardTest`、`V33IdCardProtectionMigrationIT`、`Phase3StudentIT`、
  `Phase10ExchangeIT`、`Phase41BackupIT`。
- `scripts/verify_ws8_ci_reports.py` 要求六份精确 suite XML 均存在、每 suite `tests > 0`、0 failure/error/skip，
  并生成绑定 `GITHUB_SHA` 的摘要 artifact；即使 Maven 为上游 reactor 设置 no-match 兼容，也不能由缺失 suite 假绿。
- V33 scratch-schema 新反例用前导空格与规范值绕过 V24 原始明文唯一键，使两条存量值规范化为同一 HMAC；
  callback 必须失败，Mockito spy 证明 `encrypt` 从未被调用，同时 Student/Certificate/Exchange 值均保持未更新。

## 3. 本地自然退出证据

- 后端全 reactor Checkstyle/编译：PASS。
- `IdCardProtectionServiceTest`：7/7，0 failure/error/skip。
- `RuntimeProfileGuardTest`：11/11，0 failure/error/skip。
- 临时隔离 MySQL 8.4 的 `V33IdCardProtectionMigrationIT`：3/3，0 failure/error/skip。
- `scripts/test_ws8_v33_release_contract.py`：PASS。
- `.github/workflows/ci.yml`：js-yaml 解析 PASS。
- 两个 Python 脚本语法检查与 scoped `git diff --check`：PASS。
- 临时 MySQL 仅绑定 `127.0.0.1:33308`；按精确容器 ID 停止并自动删除，端口零残留。现有用户容器未连接、
  未修改、未停止。

旧候选的独立定向 18/18、Surefire 364/364、Failsafe 220/220、聚焦 97/97 仍是正式报告已核验的正向证据；
本次不冒充重新全量执行。Hosted CI 的 6-suite selector/artifact 由独立重核环境执行并留证。

## 4. 请求的最小增量重核

1. 新 manifest 复核前/后 `verify` 均 PASS。
2. 只读核对 Phase 14 的 V33 切换顺序、不可逆边界、README/备份手册/Compose 与静态合同一致。
3. 运行加密服务 + RuntimeProfileGuard 定向单测，确认三组两两复用失败且错误不泄露值。
4. 在隔离 MySQL 运行 V33 migration IT 3/3，确认 collision 用例在任何 `encrypt`/保护性 UPDATE 前失败。
5. 运行 Hosted backend WS-8 selector，核对 6 suites 全部存在、`tests > 0`、0 skip，并下载校验
   `ws8-ci-evidence-<sha>-attempt-<n>` artifact。

不需要 Docker Compose 生产起栈、真实切流、攻击性测试、KMS/在线轮换、双协议或 WS-9 重构。
