# 师范生教育教学能力考核与教师职业能力证书管理平台

后端 **Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL + Flyway + MinIO**；前端 **Vue 3 + Vite + TypeScript + Naive UI**。

> 🚀 **接手 / 继续开发先读 [HANDOFF.md](HANDOFF.md)**（现状·环境·一键命令·从哪开始）。
> 📌 开工必读 **[AGENTS.md](AGENTS.md)**；总体方案 [plan.md](plan.md)（含 §15 增补）；任务清单 [tasks.md](tasks.md)；阶段设计与验收 [docs/](docs/)；进度 [PROGRESS.md](PROGRESS.md)；日志 [DEVLOG.md](DEVLOG.md)；待学校确认项 [docs/待确认事项确认单.md](docs/待确认事项确认单.md)。

## 环境要求
JDK 17、Maven 3.9+、Node ≥18、Docker Desktop。

## 快速开始（本地开发）
```bash
# 1) 启动依赖（MySQL/Redis/MinIO）
docker compose -f docker-compose.dev.yml up -d

# 2) 后端（Flyway 自动建表 + 种子参数）
mvn -DskipTests package
java -jar platform-boot/target/teacher-cert-platform.jar
#   健康检查  http://localhost:8080/api/health
#   接口文档  http://localhost:8080/doc.html

# 3) 前端
cd frontend && npm install && npm run dev
#   http://localhost:5173
```

## 模块结构（详见 plan.md §2.4）
| 模块 | 职责 |
|---|---|
| platform-common | 统一响应/异常/基础实体/注解/上下文 |
| platform-security | 认证授权、JWT、RBAC、数据权限切面 |
| platform-system | 字典/区划/学科库/组织/参数/审计/通知 |
| platform-business | 学生/培养/材料/免考/视频/测试/证书 |
| platform-exchange | 教育部标准导入导出与预校验 |
| platform-statistics | 统计报表 |
| platform-file | MinIO 文件服务、分片上传 |
| platform-boot | 启动、全局配置、Swagger、跨域、全局异常 |

## 默认配置（开发）
- MySQL：`root` / `root123`，库 `teacher_cert`
- MinIO：`minioadmin` / `minioadmin123`，bucket `teacher-cert`
- 可配置参数见 `sys_param` 表与 `docs/README.md` §6

## 生产部署（Docker Compose）
本仓库提供生产部署物：后端多阶段 `Dockerfile`、前端 `frontend/Dockerfile` + nginx 反代、生产 `docker-compose.yml`。生产 compose 与 `docker-compose.dev.yml` 分离，开发依赖契约不变。

```bash
# 1) 准备环境变量
cp .env.example .env
# 修改 JWT_SECRET、ADMIN_INITIAL_PASSWORD_HASH、STAFF_INITIAL_PASSWORD、数据库/MinIO 密码和端口；
# 必须设置 DB_USERNAME/DB_PASSWORD（非 root 应用账号，见 deploy/mysql-init/01-app-user.sh）与
# REDIS_PASSWORD（Redis 生产鉴权），不可留空/沿用示例值

# 2) 构建并启动生产服务（后台）
docker compose up -d --build

# 3) 查看健康
docker compose ps
# 前端 http://localhost
# 后端健康 http://localhost:8080/api/health
```

后端启动时由 Flyway 自动迁移并写入种子数据（字典、角色权限、参数等）。

**仅 dev/测试（`db/testseed`，生产不加载）**的初始测试账号：`test_academic_admin`、`test_college_clerk`、`test_college_auditor`、`test_review_teacher`、`test_cert_issuer`、`test_student`，初始密码 `ChangeMe123!`，首次登录需修改。**这些是本地/联调便利账号，切勿用于生产。**

**生产凭据（WS-2 凭据硬化，均为必配、缺失即 fail-fast 拒绝启动）**：
- `ADMIN_INITIAL_PASSWORD_HASH`：`admin` 超管的 **bcrypt 口令哈希**（不落明文）。仅当 admin 仍使用 V8 公开种子口令时执行一次性覆盖、撤销旧 token 并置 `must_change_pwd=1`；首登改密后重启不会再覆写。prod 未配、哈希非法或仍对应公开口令均拒绝启动（`AdminAccountInitializer`）。
- `STAFF_INITIAL_PASSWORD`：管理员新建/重置 STAFF 账号的初始口令。生产必须显式注入（dev 默认 `ChangeMe123!`）；未配则拒绝启动（`SecurityAdminServiceImpl`）。该部署密钥不用于学生账号。
- 学生导入**默认不再自动开户**（`student.autoCreateAccount=false`），且不再由证件号派生口令。显式开启但保持 `student.defaultPwd=random` 时，仅由学生业务流程创建**停用账号**；校级管理员重置后生成单账号随机临时口令并只在受控响应中展示一次，再由学生首次改密。只有显式配置满足强度要求的受控口令才会导入即启用。通用用户管理仅创建 `STAFF`（不得绑定 `studentId`），既有学生账号的类型、用户名、学院和学生绑定不可在系统用户页改绑；用户管理写操作仅限校级 scope，`COLLEGE` scope 仅可按范围读取列表，同院/跨院重置均返回 403。
- WS-2 后 JWT 同时绑定毫秒级签发时间 `iatMs`、当前口令哈希的不可逆 `credentialVersion` 与 Redis 持久 `sessionGeneration`；登出以 Lua 原子推进会话代次，改密/重置后旧 access/refresh token 立即失效，缺少或不匹配任一新 claim 的存量 token 均会被拒绝。发布 WS-2 时须一次性替换或重启**全部**后端实例并要求用户重新登录，不能在滚动窗口保留会签发旧格式 token 的旧实例。
- 交互式生成 bcrypt 哈希：`htpasswd -nBC 12 admin | sed 's/^admin://' | tr -d '\n'`（不会把明文口令放进历史或进程参数）。写入 Compose `.env` 时必须用**单引号**包住完整 `$2...` 哈希，避免 `$` 被插值；详见 `.env.example`。

关键参数位于 `sys_param` 表，可在系统管理页热更新；证书编号 `cert.*`、视频 `video.*`、文件大小 `file.*` 等参数修改后按既有服务实时读取。M14 外部接口仅预留 SPI 与开关，`.env.example` 中 `PLATFORM_INTEGRATION_*_ENABLED=false` 为默认值，关闭时不影响一期功能。

## 非功能与验收
AT-01~AT-14 首验与 Phase14 复验矩阵见 [docs/AT验收复验矩阵.md](docs/AT验收复验矩阵.md)。主流程端到端由 `Phase14E2EIT` 纳入 `mvn verify`，覆盖导入、确认、培养、材料、免考、视频复评、测试结果、证书生成/签发、标准导出文本化与归档。

兼容性记录：导出 Excel 通过 POI 机检断言 A-Z 26 列、H 列为“身份证件号码”、全列文本格式 `@`，证件号/前导零学号/证书编号/有效期按字符串读回；Phase14 文档归档 Excel 与 WPS 双端手动核对要求，浏览器回归目标为 Chrome、Edge、Firefox 最新稳定版。

## 进度
Phase 0~13 已复核通过；Phase 14 为最后收口阶段，详见 `PROGRESS.md`。
