# WS-11 R2 整改提交说明（开发方材料）

> 本文是整改提交材料，不是独立复核报告，不签发 PASS，也不授权 WS-12、merge、push、deploy、cutover 或项目 GO。

## 1. 基线与结论边界

- 正式报告：`ws11-functional-independent-review-20260821-ea6cf944-local/review.md`
- 报告 SHA-256：`bb3e5d907814f99159ac6434ea0c4ab7f511249dcfd210c1f368ae34393c8e8a`
- 报告绑定 fingerprint：`ea6cf944b0c3229090a3f47477243600d0475aec35a41d9abdbacb3fb1857418`
- 报告裁定：`CHANGES_REQUESTED（0 Critical / 0 High / 3 Medium / 0 Low）`
- 报告 manifest SHA-256：`3ddb5345b3080c080276f19d39851d9c36dea1cbad0697a4f6cd7c8dd34d5efc`
- R2 状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REREVIEW_PENDING`
- R2 最终身份已由外置 `teacher-cert-ws11-remediation-r2-candidate-2026-08-21.json` 冻结；具体 fingerprint 以该文件为准，不得复用首轮 fingerprint。

## 2. Finding 对应整改

| 首轮 Medium | 本轮最小整改 | 回归证据 |
|---|---|---|
| Prometheus 没有可持续的机器抓取身份 | 为 `/actuator/prometheus` 增加独立 stateless HTTP Basic filter chain，凭据只从环境注入；精确绕过业务 JWT filter，不读取 Redis 或业务 DB；匿名及错误凭据仍为 401 | 业务 access TTL 设为 1 秒，TTL 前后机器凭据均为 200；Redis/MinIO down 时仍可抓取并看到 `redis=0` |
| readiness 超过 Compose 5 秒预算 | 增加独立 readiness MinIO 短超时客户端，复用现有 endpoint/凭据/bucket/region；总预算 3 秒，单 worker、零队列，超时可取消；业务 MinIO 10/30/900 秒不变 | 半开 stub 连续三次均在 5 秒内返回 503/DOWN，重入不排队；恢复后返回 200/UP |
| 调度指标不反映真实结果 | 在五类作业的真实完成点记录 counter、timer 与 last-success；视频对账在异步 worker 完成处记账，不在提交处误报成功 | 成功、失败、跳过与异步失败 focused 单测；Prometheus IT 验证三个指标族存在 |

五类固定作业：`database_backup`、`retention_prune`、`minio_incomplete_abort`、`orphan_file_scan`、`video_finalization_reconciliation`。

固定 outcome：`success`、`failure`、`skipped`。指标仅使用固定 `job,outcome` 标签：

- `platform.scheduled.job.executions`
- `platform.scheduled.job.duration`
- `platform.scheduled.job.last.success.timestamp`

## 3. 本地验证

| 门禁 | 结果 |
|---|---|
| 九模块完整 Surefire | 411/411，0 failure/error/skip |
| Phase 14 + WS-11 真实依赖 Failsafe | 18/18，0 failure/error/skip |
| 其中 WS-11 三套 IT | 4/4，0 failure/error/skip |
| 编译与 Checkstyle | BUILD SUCCESS，0 violation |
| Flyway | 34 项验证通过，schema V33 |

真实依赖使用本轮专用 MySQL 8.4、Redis 7、MinIO 容器；未启动常驻应用服务。测试结束后只清理本轮精确命名的临时容器，不触碰用户已有容器。

## 4. 复核请求

请独立复核者仅对新 manifest 绑定字节核验：

1. 机器 Basic 身份在业务 token 过期及 Redis/MinIO down 时仍可抓取，匿名仍拒绝。
2. readiness 半开连续探测均在 Compose 5 秒内返回，恢复后回到 UP，且业务 MinIO 超时未被改短。
3. 五类作业的成功、失败、跳过及异步 worker 结果与指标一致，标签保持低基数。
4. 复算 Surefire 411/411、Phase 14 + WS-11 Failsafe 18/18 与新候选 manifest。

三项 Medium 是否 CLOSED、WS-11 是否 PASS 仅由新的正式独立报告裁定。
