# WS-14 · 运维收尾（备份生命周期 + backup_record 归档 + demo 可播视频） — 提示词（codex）

你是执行 **WS-14** 的 codex。对应 `docs/audit-remediation-plan.md` §5 WS-14（Phase 41.2 / 53 遗留打包）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§5 WS-14**；`git remote -v` 空 → `git switch -c feature/ws14-ops-wrapup`。
- **demo 可播视频是 WS-4 D0 走查的前置**——尽早做这一子项。
- **引用已核实（均在）**：`RetentionCleanupService`（`platform-system`）、`FileMaintenanceService.ensureAbortIncompleteMultipartLifecycle`、`Phase47CleanupIT`、`scripts/gen-demo-samples.py`、`db/demo/sample-video.mp4`、`backup_record`（`SystemAuditController`/`Phase13SystemAuditIT` 有引用）。ffmpeg 有无是**运行期探测**（`ffmpeg -version`）。

## 任务
### 备份产物保留
- MinIO `db-backup/` 前缀加**生命周期规则**（保留 N 天，仿 `FileMaintenanceService.ensureAbortIncompleteMultipartLifecycle` 的**幂等确保**模式）。
- `backup_record` 表纳入 `RetentionCleanupService`（照 Phase 47 模式：sys_param 化保留期 + 分批物理删；**只删记录行**，产物由生命周期管）。

### demo 可播视频
- 本机若有 `ffmpeg`（先 `ffmpeg -version` 探测）→ 生成 **1–2s 真实 H.264+AAC** 短片替换 `platform-boot/src/main/resources/db/demo/sample-video.mp4`（更新 `scripts/gen-demo-samples.py` 注释说明来源）。
- **无 ffmpeg 则明确记录跳过**（诚实标注）。

## 验收
- **IT 证** `backup_record` prune 逻辑（照 `Phase47CleanupIT` 模式直接调服务方法）。
- 生命周期规则**幂等**在。
- （若做）demo 视频**浏览器实测可播**。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行；`mvn -B -ntp clean verify` 全绿。
2. `DEVLOG.md`（倒序，标注 demo 视频做/跳过）+ `docs/launch-readiness-plan.md` §11 追加 WS-14 条目。
3. **单 commit**（`feature/ws14-ops-wrapup`）→ **STOP** 交主控复核。**禁止 merge / push**。
