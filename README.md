# 师范生教育教学能力考核与教师职业能力证书管理平台

面向高校师范生培养与教师职业能力证书管理的一体化平台，覆盖学生基础信息、培养信息、过程性材料、免考、视频评审、测试结果、证书、数据交换、统计和系统治理。

后端采用 Java 17 / Spring Boot 3.4，多模块 Maven 架构；前端采用 Vue 3 / TypeScript / Naive UI。项目同时提供本地开发依赖、生产 Compose、质量门禁和审计证据链。

> 当前状态（2026-08-27）：核心业务 Phase 0–14 共 114/114 项已完成并有阶段复核记录，后续产品与治理阶段已持续推进；但最新发布工作包仍处于 WS-8 R3 整体 Hosted workflow 待闭环状态。项目结论仍是 `CHANGES_REQUESTED / NO-GO`，不得把源码完成、局部测试 PASS 或本仓库 `main` 快照理解为生产发布授权。

## 当前进度

| 范围 | 当前记录 | 边界 |
|---|---|---|
| Phase 0–14 | 114/114；阶段复核与 AT-01～AT-14 复验记录齐全 | 证明一期功能与阶段验收，不自动等于项目发布 GO |
| Phase 15–53 | RBAC、前端重建、体验、可靠性、缓存、备份、demo 等收官后阶段已有对应复核记录 | 详细状态以进度账本和正式报告为准 |
| F01–F07 | 当前账本记录为动态闭环或独立增量 PASS | 仅绑定各自候选与证据范围 |
| WS-5～WS-7 | HTTPS/会话、前端门禁、供应链阶段已取得 scoped/stage PASS | 不授权部署或切流 |
| WS-8 | 身份证件号应用层加密、HMAC 唯一键和 V33 迁移已实现；R3 为 `LOCAL_REMEDIATION_R3_READY / HOSTED_WHOLE_WORKFLOW_PENDING` | 整体 Hosted 绿灯和独立阶段 PASS 前不启动 WS-9 |
| 项目级 | `CHANGES_REQUESTED / NO-GO` | 当前仓库是私有开发快照，不是稳定发布包 |

权威状态入口：

- [HANDOFF.md](HANDOFF.md)：当前接力快照、本机环境和下一步。
- [docs/CURRENT-EXECUTION-PLAN.md](docs/CURRENT-EXECUTION-PLAN.md)：唯一当前执行队列与阶段复核矩阵。
- [PROGRESS.md](PROGRESS.md)：任务、工作包、阶段和 AT 验收账本。
- [DEVLOG.md](DEVLOG.md)：按时间倒序的实现、测试和决策记录。

README 只提供概览；当它与上述状态账本或正式复核报告冲突时，以最新正式证据和当前执行计划为准。

## 主要能力

- 学生基础信息与培养信息：字段文本化、学年版本、专业/培养目标/学段/学科联动和二级审核。
- 过程性材料：四类材料上传、预览、替换、初审、复审、归档和批量下载。
- 免考管理：按科目独立申请、佐证、二级审核及应考科目联动。
- 视频评审：分片直传、媒体校验、双教师独立评分、分差复评、仲裁、退回重传和登录绑定播放。
- 测试与证书：测试结果确认、证书前置校验、18 位编号、签发、作废、重开、导出和归档。
- 数据交换：标准模板、预校验、异常报告、分批导入、回滚、标准/敏感导出和附件清单。
- 统计与工作台：学院进度、材料、免考、视频、证书、异常数据和角色化首页。
- 系统治理：用户、角色、权限、数据范围、字典、参数、通知、审计日志和备份记录。

系统内置 7 类角色：`STUDENT`、`COLLEGE_CLERK`、`COLLEGE_AUDITOR`、`REVIEW_TEACHER`、`ACADEMIC_ADMIN`、`CERT_ISSUER`、`SYS_ADMIN`。菜单、按钮和服务端数据范围均按权限收敛。

## 技术栈

| 层次 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.4.13、MyBatis-Plus 3.5.16、Spring Security、JWT、Flyway |
| 数据与基础设施 | MySQL 8、Redis 7、MinIO、Docker Compose |
| 文件与交换 | FastExcel、MinIO SDK、AWS S3 SDK、JCodec |
| 前端 | Vue 3.5、TypeScript 5.6、Vite 5.4、Naive UI 2.40、Pinia、Vue Router、Axios、ECharts |
| 测试与质量 | JUnit 5、Mockito、Maven Surefire/Failsafe、Checkstyle、Vitest、Playwright、axe-core、ESLint |

## 仓库结构

| 路径 | 职责 |
|---|---|
| `platform-common` | 统一响应、异常、基础实体、注解和公共上下文 |
| `platform-security` | 认证、JWT、RBAC、会话撤销、数据权限和身份证件号保护 |
| `platform-system` | 字典、区划、组织、参数、用户角色、审计、通知和备份 |
| `platform-business` | 学生、培养、材料、免考、视频、测试结果和证书业务 |
| `platform-exchange` | 标准导入导出、预校验、异常报告和批次回滚 |
| `platform-statistics` | 统计查询与报表 |
| `platform-file` | MinIO 文件服务、上传 intent、分片直传和对象元数据 |
| `platform-boot` | 应用启动、控制器、配置、Flyway、健康检查和集成测试 |
| `frontend` | Vue 3 管理端、单元测试、产品冒烟和 UI 审计工具 |
| `docs` | 规格、阶段验收、执行计划、复核报告和运行手册 |
| `scripts` | 开发启停、候选清单、CI 合同及专项验证脚本 |

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18 LTS 或更高版本
- Docker Desktop / Docker Engine + Compose v2
- Windows 推荐 PowerShell 7 或 Git Bash；Linux/macOS 可使用 Bash

## 本地开发

### 1. 启动依赖

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps
```

开发 Compose 提供 MySQL、Redis 和 MinIO。它使用本地开发固定凭据，仅用于开发/测试，不得复制到生产环境。

### 2. 构建并启动后端

```bash
mvn -B -ntp -DskipTests package
```

PowerShell：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
java -jar .\platform-boot\target\teacher-cert-platform.jar
```

Bash：

```bash
export SPRING_PROFILES_ACTIVE=dev
java -jar platform-boot/target/teacher-cert-platform.jar
```

`dev` profile 提供明确标注为非生产的本地密钥和测试种子；生产 profile 不加载这些默认值，并会对缺失或示例凭据失败关闭。

### 3. 安装并启动前端

```bash
npm --prefix frontend ci
npm --prefix frontend run dev -- --host 127.0.0.1
```

默认入口：

| 服务 | 地址 |
|---|---|
| 前端 | <http://127.0.0.1:5173> |
| 后端健康检查 | <http://127.0.0.1:8080/api/health> |
| 非生产接口文档 | <http://127.0.0.1:8080/doc.html> |
| MinIO API | <http://127.0.0.1:9000> |
| MinIO Console | <http://127.0.0.1:9001> |

dev/test 测试账号包括 `test_academic_admin`、`test_college_clerk`、`test_college_auditor`、`test_review_teacher`、`test_cert_issuer`、`test_student`，初始密码为 `ChangeMe123!`，首次登录需要修改。生产环境不会加载这组测试种子。

停止本地依赖时使用：

```bash
docker compose -f docker-compose.dev.yml down
```

不要随意附加 `-v`，否则会删除本地开发数据卷。

## 构建与测试

后端编译与静态门禁：

```bash
mvn -B -ntp -DskipTests package
```

前端门禁：

```bash
npm --prefix frontend run lint
npm --prefix frontend run type-check
npm --prefix frontend run type-check:tests
npm --prefix frontend run test:unit
npm --prefix frontend run build
```

完整后端验证：

```bash
mvn -B -ntp clean verify
```

完整 `verify` 会执行 `*IT` 集成测试，需要隔离的真实 MySQL、Redis、MinIO 和正确的测试环境标识；不能用共享开发库的偶然绿灯替代正式证据。前端 Playwright 产品冒烟另执行：

```bash
npm --prefix frontend run test:e2e
```

CI 在后端、前端和 runner 合同全部成功后才构建 `sha-<commit>` 双镜像，并配置生成两份 SPDX、镜像身份和校验和 artifact。SBOM 是组件清单，不等于漏洞扫描、镜像签名或生产发布证明。

## 安全与数据约束

- 学校代码、学号、证件号码、出生日期、证书编号和有效期限全链路按字符串处理；Excel 单元格固定为文本格式。
- 查询和导出必须执行服务端数据范围；敏感导出需要独立权限并写入审计日志。
- 写操作通过结构化审计记录操作人、目标、前后状态、意见、结果和可信来源 IP。
- refresh token 使用 host-only、HttpOnly、SameSite Cookie；access token 仅驻前端内存，登出和改密会撤销旧会话。
- 文件和视频读取走登录绑定的应用鉴权，不向浏览器提供永久公开对象链接。
- V33 将学生、证书及交换链中的证件号码以 AES-GCM 密文存储，并使用独立 HMAC 进行等值查询和唯一约束。
- 生产必须显式配置数据库、Redis、MinIO、JWT、管理员口令、证件加密密钥和 HMAC pepper；示例值会触发失败关闭。

完整红线见 [AGENTS.md](AGENTS.md)，业务规格见 [plan.md](plan.md) §15。

## 生产部署边界

仓库包含后端/前端多阶段镜像、TLS nginx 和生产 `docker-compose.yml`，但当前项目状态仍为 `NO-GO`，不得直接把以下文件的存在理解为可上线：

1. 以 [.env.example](.env.example) 生成仓库外 `.env`，替换全部占位值，并保持密钥不进入版本控制。
2. 用 `docker compose config --quiet` 校验展开结果，但不要在未完成发布门禁时启动或切流。
3. V32 视频定稿协议、Phase 44 字典缓存协议和 V32→V33 身份证件号迁移均要求停写、停旧节点、备份、同版本切换与只读验收，禁止新旧协议滚动混部。
4. 正式操作必须遵循 [Phase 14 部署验收](docs/phase-14-非功能部署验收.md) 和 [备份与恢复手册](docs/备份与恢复手册.md)，并绑定同一候选的运行证据与独立复核结论。

## 文档导航

| 文档 | 用途 |
|---|---|
| [AGENTS.md](AGENTS.md) | 项目最高执行规范、红线、Git、测试和复核流程 |
| [HANDOFF.md](HANDOFF.md) | 当前接力状态、本机命令、已知坑和下一步 |
| [plan.md](plan.md) | 总体业务与技术方案，§15 为规格增补最高优先级 |
| [tasks.md](tasks.md) | Phase 0–14 的 114 个原子任务与依赖 |
| [docs/README.md](docs/README.md) | 阶段文档索引、DoD 和 AT 追溯矩阵 |
| [docs/CURRENT-EXECUTION-PLAN.md](docs/CURRENT-EXECUTION-PLAN.md) | 当前唯一执行队列、复核矩阵与优先级 |
| [PROGRESS.md](PROGRESS.md) | 实现、复核、工作包和 AT 验收状态 |
| [DEVLOG.md](DEVLOG.md) | 实现决策、测试结果、问题与下一步 |
| [docs/AT验收复验矩阵.md](docs/AT验收复验矩阵.md) | AT-01～AT-14 复验记录 |
| [docs/待确认事项确认单.md](docs/待确认事项确认单.md) | 学校确认事项及冻结口径 |
| [docs/phase-14-非功能部署验收.md](docs/phase-14-非功能部署验收.md) | 部署、升级和非功能验收合同 |
| [docs/备份与恢复手册.md](docs/备份与恢复手册.md) | 数据库、对象存储、密钥和恢复流程 |

## 开发约定

开始任务前按 `HANDOFF → AGENTS → plan → tasks → docs/README → 对应 phase → PROGRESS` 的顺序阅读。业务改动必须遵循 Flyway、后端分层、服务端校验、`@AuditLog`、`@DataScope`、单元测试与反例要求；阶段完成后只能进入“待复核”，必须由独立复核 PASS 才能标记为“已复核”。
