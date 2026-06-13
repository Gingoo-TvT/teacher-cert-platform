# 师范生教育教学能力考核与教师职业能力证书管理平台

后端 **Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL + Flyway + MinIO**；前端 **Vue 3 + Vite + TypeScript + Naive UI**。

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

## 进度
Phase 0（工程脚手架）已完成并通过构建/运行验证。后续按 `tasks.md` 推进 Phase 1+。
