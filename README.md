# 师范生教育教学能力考核与教师职业能力证书管理平台

面向高校师范生培养、教育教学能力考核与教师职业能力证书全流程管理的一体化平台。

后端采用 Java 17、Spring Boot 3.4、MyBatis-Plus、MySQL、Redis、Flyway 与 MinIO；前端采用 Vue 3、TypeScript、Vite 与 Naive UI。

## 当前状态

> 更新时间：2026-08-27
>
> 当前开发状态不是 WS-9。WS-9 已完成并通过独立增量复核；最终功能完整性 R5 也已开发完成并正式通过。
>
> 当前唯一进行中的阶段是 `SECOND-RELEASE-PREPARATION / U-005`：源码增量已通过，隔离动态发布证据仍待闭环，因此项目仍为 `NO-GO`，尚不授权实际部署或切流。

| 范围 | 状态 | 当前结论 |
|---|---|---|
| Phase 0–53 与 F01–F07 | 已完成 | 阶段实现及对应复核记录已归档 |
| WS-5 / WS-6 / WS-7 | 已完成 | HTTPS/会话、前端测试与容器供应链范围均已独立通过 |
| WS-8 | 已完成 | 身份证件号应用层加密、HMAC 查重与 V33 迁移已取得 `INDEPENDENT_FUNCTIONAL_PASS` |
| WS-9 | 已完成 | `ExchangeServiceImpl`、`VideoReviewServiceImpl` 拆分完成，`INDEPENDENT_INCREMENTAL_PASS` |
| WS-11 | 已完成 | liveness、readiness、依赖探测与 Prometheus 指标，`INDEPENDENT_INCREMENTAL_PASS` |
| WS-12 | 已完成 | 前端 bundle 拆分与体积预算，`INDEPENDENT_INCREMENTAL_PASS` |
| WS-14 | 已完成 | 备份生命周期、保留策略与记录归档，`INDEPENDENT_INCREMENTAL_PASS` |
| WS-15 | 可选、暂缓 | 同步审计满足当前要求，不作为功能完整性前置 |
| 最终功能完整性 R5 | **已完成** | `INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/0L）` |
| U-005 第二次部署准备 | **进行中** | R4 源码增量 `PASS`；动态发布证据仍为 `PENDING` |

R5 正式证据记录的本地门禁为后端 **68 suites / 447 tests**、九模块 package 全绿，视频指派聚焦 **10/10**，候选工具 **6/6**，WS-7、WS-8/V33 与 diff check 均通过。本次 README 与仓库快照发布没有重新执行整套动态门禁；完整指纹、报告哈希和证据边界以 [PROGRESS.md](PROGRESS.md) 与 [HANDOFF.md](HANDOFF.md) 为准。

U-005 仍需在同一新冻结候选上完成：clean-store 五镜像装载与 `--pull never`、维护期间公开入口关闭、失败迁移后恢复 `dfdfb91` 五服务、V32→V35 与 Phase 41/00/47、正式 TLS/MinIO gateway、目标身份和恢复点，以及全新 Hosted CI。功能完成、源码增量通过与项目发布 GO 是三个不同结论。

## 核心能力

- 师范生基本信息、培养方案、过程性材料、免考、教育实践与测试结果管理。
- 视频上传、双人评审、复评/仲裁、定稿及异常候选回收。
- 教师职业能力证书生成、签发、作废、重开、更正与标准数据交换。
- 教育部标准 Excel 导入、预校验、回滚、导出和大文件资源预算保护。
- RBAC、学院数据范围、敏感字段脱敏、写操作审计与会话即时失效。
- 身份证件号 AES 应用层加密、HMAC 等值查询/唯一约束及 V33 停机迁移。
- MinIO 分片上传、预签名访问、媒体访问 Cookie 与动态水印。
- liveness/readiness、MySQL/Redis/MinIO 依赖探测、Prometheus 指标和定时任务指标。
- 数据库备份、保留期清理、单飞控制、恢复手册与第二次发布预检。
- 前端响应式 UI、权限路由、单元测试、Playwright E2E、bundle 预算及 CI 门禁。

## 技术栈

| 后端 | 前端与基础设施 |
|---|---|
| Java 17 | Vue 3.5 |
| Spring Boot 3.4.13 | TypeScript 5.6 |
| MyBatis-Plus 3.5.16 | Vite 5.4 |
| Flyway + MySQL 8 | Naive UI 2.40 |
| Redis 7 | Pinia + Vue Router |
| MinIO | Vitest + Playwright |
| FastExcel / Apache POI | Docker Compose + Nginx |
| JUnit 5 / Maven Failsafe | GitHub Actions |

## 仓库结构

| 路径 | 职责 |
|---|---|
| `platform-common` | 统一响应、异常、基础实体、注解与上下文 |
| `platform-security` | JWT、RBAC、数据权限、身份证件号保护与媒体访问授权 |
| `platform-system` | 字典、区划、组织、参数、审计、通知、备份与可观测性 |
| `platform-business` | 学生、培养、材料、免考、视频、测试结果与证书业务 |
| `platform-exchange` | 标准导入导出、预校验、回滚与资源预算 |
| `platform-statistics` | 统计报表与数据范围内聚合 |
| `platform-file` | MinIO 文件服务与分片上传 |
| `platform-boot` | 应用启动、全局配置、接口层、健康检查与 Flyway |
| `frontend` | Vue 管理端、单元测试、E2E 与构建预算 |
| `deploy` | 生产 Compose overlay、回退基线及部署辅助物 |
| `scripts` | 候选清单、发布预检与合同测试脚本 |
| `docs` | 规格、阶段验收、复核报告、证据与运维手册 |

当前数据库迁移已到 `V35`：

- `V33__id_card_application_encryption.sql`：身份证件号加密存储协议。
- `V34__backup_retention_parameter.sql`：备份保留参数。
- `V35__video_reviewer_count_snapshot.sql`：视频评审人数快照。

已发布迁移不得修改或复用版本号；所有 DDL 与种子变更继续通过 Flyway 前向演进。

## 本地开发

环境要求：JDK 17、Maven 3.9+、Node.js 18+、Docker Desktop。

```bash
# 1. 启动本地依赖
docker compose -f docker-compose.dev.yml up -d

# 2. 构建后端
mvn -B -ntp -DskipTests package

# 3. 显式使用 dev profile 启动后端
SPRING_PROFILES_ACTIVE=dev java -jar platform-boot/target/teacher-cert-platform.jar

# 4. 安装并启动前端
npm --prefix frontend install
npm --prefix frontend run dev
```

本地入口：

- 前端：`http://localhost:5173`
- Swagger：`http://localhost:8080/doc.html`
- 存活检查：`http://localhost:8080/api/health/liveness`
- 就绪检查：`http://localhost:8080/api/health/readiness`
- Prometheus：`http://localhost:8080/actuator/prometheus`，使用 `PROMETHEUS_SCRAPE_USERNAME/PASSWORD` Basic 认证

应用不再默认激活 profile，本地启动必须显式设置 `SPRING_PROFILES_ACTIVE=dev`。开发环境的示例账号与口令只用于本机测试，生产环境全部凭据必须由环境变量或受控密钥系统提供。

## 常用校验

```bash
# 后端完整构建与测试
mvn -B -ntp verify

# 后端只构建
mvn -B -ntp -DskipTests package

# 前端静态门禁
npm --prefix frontend run lint
npm --prefix frontend run type-check
npm --prefix frontend run test:unit
npm --prefix frontend run build

# 候选源清单工具测试
python scripts/test_candidate_source_manifest.py

# 第二次发布合同测试
python scripts/test_second_release_contract.py
```

真实 MySQL、Redis、MinIO 集成门禁必须使用隔离环境。绿色源码测试、静态合同、历史 Hosted 产物、独立阶段 PASS 与项目 GO 不可互相替代。

## 生产与第二次发布

生产 Compose 只公开前端 HTTP/HTTPS 入口；backend 不发布宿主 8080，MinIO API 与 console 仅绑定回环地址。生产必须配置非 root 数据库账号、Redis 密码、JWT 密钥、管理员/员工初始凭据、TLS 证书、MinIO HTTPS 入口，以及彼此独立的 `IDCARD_ENCRYPTION_KEY` 与 `IDCARD_HMAC_PEPPER`。

第一次稳定部署基线为 `dfdfb91`。第二次发布必须遵循 [第二次部署发布手册](docs/第二次部署发布手册.md)：

- 复用首次部署的 Compose project 与四个既有命名卷。
- 使用 `deploy/compose.cpu-v1.yml` 与 `deploy/compose.existing-volumes.yml`。
- 预载并核对五幅不可变镜像，使用 `--no-build --pull never`。
- V32/V33/V35 等协议迁移禁止新旧 binary 混部，只允许停机前向迁移。
- 不覆盖原 `.env`，不执行 `down -v`，不删除或重建既有数据卷。
- 动态门禁与最终项目级复核通过前，不执行真实部署、迁移或切流。

仓库 `main` 上的最新代码快照只是版本归档与协作基线，不代表生产发布批准。

## 安全与数据规则

- 学校代码、学号、身份证件号码、出生日期、证书编号和有效期限全链路使用字符串，Excel 单元格固定文本格式 `@`。
- 所有写操作留审计；所有列表、导出和统计查询执行数据范围控制。
- 业务与附件元数据使用逻辑删除；数据库结构只通过 Flyway 更新。
- 生产 profile 关闭文档 API/UI/静态资源，并对示例密钥、缺失凭据和非法配置 fail closed。
- 附件与视频访问必须鉴权并使用限时授权；敏感明文导出要求专用权限与审计。
- `.env`、TLS 私钥、身份证件号密钥、数据库备份与真实证据载体不得提交仓库。

## 文档导航

- [HANDOFF.md](HANDOFF.md)：当前接力点、环境与证据边界。
- [AGENTS.md](AGENTS.md)：开发、测试、Git 与安全红线。
- [PROGRESS.md](PROGRESS.md)：最新阶段状态、指纹与正式结论。
- [DEVLOG.md](DEVLOG.md)：倒序开发和治理日志。
- [plan.md](plan.md)：总体业务与技术方案。
- [tasks.md](tasks.md)：任务编号、依赖与拆解。
- [docs/CURRENT-EXECUTION-PLAN.md](docs/CURRENT-EXECUTION-PLAN.md)：当前执行计划。
- [docs/README.md](docs/README.md)：阶段文档、DoD 与 AT 追溯入口。
- [docs/第二次部署发布手册.md](docs/第二次部署发布手册.md)：第二次发布权威操作步骤。
- [docs/备份与恢复手册.md](docs/备份与恢复手册.md)：备份、恢复与密钥边界。

## 当前下一步

冻结治理同步后的新 release-prep 候选，重新生成并验证候选清单，使 clean-store、迁移/回退、正式入口、真实依赖场景和全新 Hosted 证据全部绑定同一份字节；独立复核通过后，才进入项目级发布裁定。
