# WS-11 health readiness + metrics 功能复核提交

> 本文是整改者提交材料，不是独立复核报告，也不自行签发 WS-11 PASS。

## 1. 范围

- `/api/health` 保持既有 liveness 兼容语义；新增 `/api/health/liveness`。
- `/api/health/readiness` 检查 MySQL、Redis、MinIO 与 Flyway 迁移状态；任一关键依赖不可用时返回 HTTP 503 和 `DOWN`。
- 引入 Actuator/Micrometer/Prometheus；`/actuator/prometheus` 要求登录，包含 HTTP 请求、Hikari 连接池与 `platform_readiness_component` 指标。
- 生产 Compose 的 backend healthcheck 改用 readiness。

## 2. 功能证据

| 门禁 | 结果 |
|---|---|
| `PlatformReadinessProbeTest` + `HealthControllerTest` | 6/6，0 failure/error/skip |
| `Ws11HealthMetricsIT` | 2/2：真实依赖 readiness UP；匿名 Prometheus 401、登录后指标完整 |
| `Ws11HealthDependencyDownIT` | 1/1：Redis/MinIO 不可达时 liveness 200/UP、readiness 503/DOWN |
| 空库九模块 clean verify | Surefire 406/406；Phase 14 + WS-11 Failsafe 17/17；Checkstyle 0；BUILD SUCCESS |
| Flyway | 34 项迁移资源验证通过，schema 最终 V33 |
| 文本门禁 | `git diff --check` PASS |

## 3. 保持不变的边界

- 无业务 API、DTO/VO、权限点、事务、数据库 schema 或 Flyway 变化。
- 未改 MySQL/Redis/MinIO 的业务访问逻辑；健康探针只读取可用性状态。
- 未 merge、push、deploy、cutover，也不构成项目发布 GO。

## 4. 请求复核

请独立确认以下功能边界：

1. 依赖中断不会把 liveness 误报为 DOWN；readiness 会准确返回 503/DOWN。
2. 依赖恢复后 readiness 可回到 UP，四项组件状态与 Prometheus gauge 一致。
3. Prometheus 未登录不可访问，登录后包含 HTTP、连接池与依赖状态指标。
4. Compose 使用 readiness，不再以静态 liveness 作为业务接流依据。

独立 PASS 前，WS-11 保持 `LOCAL_FUNCTIONAL_READY / INDEPENDENT_REVIEW_PENDING`，不领取 WS-12。
