# WS-11 · health liveness/readiness 拆分 + 指标 — 提示词（Opus 4.8）

你是执行 **WS-11** 的 opus。对应 `docs/audit-remediation-plan.md` §5 WS-11（审计 #10）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§5 WS-11**；`git remote -v` 空 → `git switch -c feature/ws11-health-metrics`。

## 任务
- **现状核实**：健康检查现是**自定义 `HealthController`**（`platform-boot/.../controller/HealthController.java`），**非 Spring Boot Actuator**；仓库**未引入** Actuator/Micrometer/Prometheus（均新依赖）。
- `/api/health` 拆 **liveness**（存活）与 **readiness**（探 MySQL/Redis/MinIO 连通 + 关键迁移状态）——可扩现有 `HealthController`，或引 Actuator 用其 `health` liveness/readiness groups（**引 Actuator 须在 `SecurityConfig` 放行/保护 `/actuator/**`**，别裸暴露指标端点）。
- 接 **Actuator/Micrometer/Prometheus**（新依赖），暴露 HTTP 时延/错误率/DB pool/Redis/MinIO/调度结果指标。

## 验收（活体）
- Redis/MinIO **不可达时**：liveness 仍 **UP**、readiness **DOWN**（复现证明）。
- Prometheus 端点有关键指标。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行；`mvn -B -ntp clean verify` 全绿。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-11 条目。
3. **单 commit**（`feature/ws11-health-metrics`）→ **STOP** 交主控复核。**禁止 merge / push**。
