# HANDOFF.md — 交接说明（codex 接手必读）

> 目的：让 codex 在**本机（Windows + Git Bash）** 无障碍接手，从 **Phase 1** 继续开发。
> 顺序：先读本文件 → 再按 `AGENTS.md` §0 读其余文档 → 开始 `tasks.md` 的 **T-011**。

---

## 1. 当前状态（截至 2026-06-13）
- **Phase 0 全部完成（T-001~T-010），并通过构建 + 运行验证**；Phase 1~14 未开始。
- 仓库：本地 git，分支 `main`，**无远程（私有，未开源）**，working tree 干净。
- 提交链：
  ```
  08c638f 前端脚手架 + CI [T-008/009/010]   ← Phase 0 完成
  121a489 MinIO文件服务 + 切面 [T-006/007]
  4490c12 DB链路 Flyway/MyBatis-Plus [T-003/004/005]
  698b51f Maven骨架 + common + boot [T-001/002]
  6948682 规划基线
  ```
- 已验证（详见 `DEVLOG.md`）：后端 `mvn package` 9 模块 SUCCESS；运行后 Flyway 迁移 v1、4 张表、`sys_param` 17 行、`/api/health`=UP、`/doc.html`=200；MinIO 上传→预签名→下载 内容一致；前端 `vite build` 通过。

## 2. 环境与工具链（本机特性，务必注意）
| 项 | 值 |
|---|---|
| 仓库根 | `C:\Users\wenbibuhaoqwq\Desktop\teacher-cert-platform`（Bash: `/c/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform`） |
| Shell | **Git Bash（POSIX sh）**，不是 PowerShell |
| JDK17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`（路径含空格，命令需引号） |
| Maven 3.9.9 | `C:\Users\wenbibuhaoqwq\tools\apache-maven-3.9.9`（手动装，winget 无 `Apache.Maven`） |
| 其它 | git 2.54、Docker Desktop、Node v24.15 + npm（**pnpm 未装，用 npm**） |

- **坑①（PATH）**：`mvn`/`java`/`git` 已写入用户级 PATH，但旧进程继承旧环境 → **新开终端**才直接可用；脚本里用全路径最稳（见 §3）。
- **坑②（GBK 控制台）**：中文 Windows 控制台默认 GBK，java/mvn 的**中文告警会显示乱码**（如 `δ֪`），**不影响编译**（源码已 UTF-8，父 POM 强制）；HTTP/DB 中文正常。
- **坑③（Docker）**：依赖容器需 Docker Desktop 引擎运行；容器名 `tcp-mysql`/`tcp-redis`/`tcp-minio`。

## 3. 一键操作命令（本机已验证，Git Bash 复制即用）
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"
MVN="/c/Users/wenbibuhaoqwq/tools/apache-maven-3.9.9/bin/mvn"
REPO="C:/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform"

# 启动依赖（MySQL/Redis/MinIO）
docker compose -f "$REPO/docker-compose.dev.yml" up -d

# 后端：构建 + 运行（Flyway 自动迁移）
"$MVN" -f "$REPO/pom.xml" -B -ntp -DskipTests package
"$JAVA_HOME/bin/java" -jar "$REPO/platform-boot/target/teacher-cert-platform.jar"
#   健康 curl http://localhost:8080/api/health   文档 http://localhost:8080/doc.html

# 前端
npm --prefix "$REPO/frontend" install
npm --prefix "$REPO/frontend" run dev          # http://localhost:5173
npm --prefix "$REPO/frontend" run build

# DB 查看 / 停依赖
docker exec tcp-mysql mysql -uroot -proot123 teacher_cert -e "SHOW TABLES;"
docker compose -f "$REPO/docker-compose.dev.yml" down      # 加 -v 连数据卷删除

# git（本地，勿加远程/勿 push，保持私有）
git -C "$REPO" add -A && git -C "$REPO" commit -m "feat(T-0xx): ..."
```

## 4. 既定约定（不可擅改，否则破坏一致性）
- 基础包 `cn.edu.gpnu.platform`；groupId `cn.edu.gpnu`；version `1.0.0-SNAPSHOT`。
- 8 模块：业务模块均依赖 `platform-common`；`platform-boot` 聚合 common/system/security/file（business/exchange/statistics 待各自 Phase 接入 boot 依赖）。
- **Lombok 已在父 POM 统一声明**——新模块用 Lombok 无需再加依赖。
- 版本锁定在**父 pom**：Spring Boot 3.2.11 / MyBatis-Plus 3.5.7 / Knife4j 4.5.0 / FastExcel 1.1.0 / MinIO 8.5.12 / jjwt 0.12.6；Flyway 随 Boot = 9.22.x（MySQL 原生支持）。
- DB：库 `teacher_cert`，`root`/`root123`；MinIO `minioadmin`/`minioadmin123`，bucket `teacher-cert`。
- **Flyway 迁移**：`platform-boot/src/main/resources/db/migration/`，V1 已用 → **下一个从 `V2__*.sql` 起**，版本号严格递增、不改已发布脚本、种子幂等。
- 实体继承 `BaseEntity`（id/审计字段/逻辑删除自动）；Mapper 放 `**/mapper`（已 `@MapperScan("cn.edu.gpnu.platform.**.mapper")`）；统一返回 `Result`；写操作 `@AuditLog`；列表/导出/统计查询 `@DataScope`；当前用户取 `UserContext`。
- **文本化字段全链路 String + Excel `@`**（学校代码/学号/证件号/出生日期/证书编号/有效期限）——AT-01 生命线，勿用数值/日期类型。

## 5. 从这里开始 → Phase 1（基础数据与字典，T-011~T-022）
1. 读 `AGENTS.md`（红线+流程）→ `docs/phase-01-字典与标准数据.md`（数据库/接口/规则/**验收清单**/**测试用例**）→ `plan.md` §5.2 / §6.1 / 附录B。
2. 顺序：`V2__dict.sql`（`sys_dict_type`/`sys_dict_item`/`sys_region`/`teaching_subject`/`sys_college`/`sys_major`/`major_training_goal`/`training_goal_config`）→ `V3__dict_seed.sql`（17 类字典标准值，**逐字对齐需求**）→ `V4__subject_seed.sql`（学科库示例）→ 字典/区划/学科库 服务与接口（Redis 缓存）→ 前端字典/区划/学科库/组织页。
3. 每个任务按 `AGENTS.md` §7 循环：建分支 `feature/phase01-Txxx-...` → `PROGRESS.md` 置 `[~]` → 写迁移+代码 → `mvn package` + 运行验证（含反例）→ 勾 `docs/phase-01` 验收清单 → 写 `DEVLOG.md` → 提交。

## 6. 已知坑与规避（别重复踩）
- Lombok `optional` 不向子模块传递 → 已在父 POM 解决。
- `@Configuration` 的 `@PostConstruct` **勿调 `@Bean` 方法**（CGLIB 循环依赖）→ 见 `MinioConfig.ensureBucket` 独立 client 写法。
- Write 工具不一定建父目录 → 新源码包先 `mkdir -p`。
- Bash 执行含空格路径（JAVA_HOME）务必加引号。
- `target/`、`node_modules/`、`dist/`、`.env` 已被 `.gitignore` 排除；`.gitattributes` 统一 LF（CRLF 警告已消除）。

## 7. 待确认事项（不阻塞开发，已设默认值并参数化）
见 `docs/待确认事项确认单.md`（20 项）。重点：**证书序列作用域默认 `SCHOOL_YEAR_SEGMENT`**（`sys_param.cert.seq.scope`），上线前需学校书面确认（与需求 9.1 文字/示例冲突的裁定）。

## 8. 验收基线
- 每阶段对照 `docs/phase-NN` 验收清单 + `docs/README.md` §4 的 **AT-01~AT-14 追溯矩阵**；状态在 `PROGRESS.md` 的「AT 跟踪」表登记；Phase 14 整体复验。
- 全局 DoD 见 `AGENTS.md` §12（提交前逐条勾）。
