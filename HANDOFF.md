# HANDOFF.md — 交接说明（接手必读）

> 目的：让后续 codex / Claude 在**本机（Windows + Git Bash）** 无障碍接手。
> 顺序：先读本文件 → 再按 `AGENTS.md` §0 读其余文档。当前 WS 链与缺失阶段均已独立复核；整体仍为 CHANGES REQUESTED。
> 当前排期、逐阶段复核矩阵与所有历史计划的合并入口：`docs/CURRENT-EXECUTION-PLAN.md`。

---

## 0. 当前接力快照（2026-07-24）
- 当前分支 `feature/ws03-minio-presign`，基于 WS-13 提交 `acfc3b6`；WS-3 原实现提交为 `338bc91`，第四轮提交为 `ee190f3`，第五轮整改提交见 `git log -1`。`main` 仍为 `e4f8228`，无 remote，禁止 push/擅自 merge。
- WS-3 原有预签名直传能力保持不变；整改包新增服务端逐字节读取最终 MinIO 对象、计算受信 SHA-256 tree 指纹并用 JCodec 校验实际 MP4/H.264/时长/首帧，校验失败关闭。秒传只允许同 uploader、同 student、同一受信对象及已有合格视频记录复用，普通响应不再暴露内部内容指纹。
- 2026-07-23 用户在 Claude 不可用期间明确授权 Codex 独立复核。WS-3 第二轮 `32da735` 的独立报告 `docs/reviews/ws-03-second-remediation-rereview-2026-07-23.md` 仍为 **CHANGES REQUESTED**：时间线、fast-hit、review 行锁和 V29 已闭环，但 `SERVER_CHUNK` 崩溃恢复、并发总磁盘预留、可终止探测时限仍有 3 High，另有 lease 续租/生产配置/资源测试 3 Medium。Phase 53 demo 元数据失配是 U-003 的独立 High，未混入本次报告。
- 第三轮 `df22e5b` 已完成上述整改，但独立重核 `docs/reviews/ws-03-third-remediation-rereview-2026-07-23.md` 仍为 **CHANGES REQUESTED（4 High / 5 Medium）**。High 为：Redis fencing token 在序列过期/恢复后可 ABA；server finalize 输给 assign 后永久 MERGING；direct 接管时 multipart 已丢失会永久 MERGING；持久 `video-probe-temp` 无崩溃孤儿清扫。Medium 为：基础设施故障误判内容失败、强杀未确认退出、磁盘双重计数、MinioClient 未受显式超时控制、fat-JAR worker 缺自动化门禁。
- 第四轮 `ee190f3` 已实现：V31 把 `finalization_token` 固化为数据库永久高水位，Redis 改用随机 owner；server/assign、direct 丢失 multipart 及 server 源分片丢失均可离开 MERGING；探测临时卷增加 owner heartbeat、启动/周期 reaper 和独占锁；强杀后确认 PID、活孤儿阻断准入；容量快照与受管写入原子化并补准入二次检查；MinioClient/AWS S3Client 都有正数总调用/连接/读写超时；fat-JAR worker 纳入 Maven verify。
- 第四轮独立重核 `docs/reviews/ws-03-fourth-remediation-rereview-2026-07-23.md` 为 **CHANGES REQUESTED（1 High / 1 Medium / 2 Low）**。第三轮 4 High / 5 Medium 均可按原口径关闭；新 High 是 JCodec 内容解析 `IOException` 被判基础设施故障，损坏 MP4 可永久卡 MERGING；新 Medium 是 FAILED 对象删除无持久 reconciliation，SERVER 还可由旧 owner 迟到 compose 复活稳定 key；两个 Low 为临时清扫公平性与裸机父 JVM 崩溃后的 worker 监管恢复。
- 第五轮整改已完成：worker 升级为 V4，以源通道 I/O 跟踪区分损坏媒体解析异常与真实基础设施故障；V32 增加 generation 候选台账，SERVER 使用 `/g-{generation}.mp4` 独立对象键，失败/失权对象经持久清理、跨节点租约和 `CLEANED` 墓碑周期复查；生产启动回填/首次对账异步投递到单线程防重入执行器，之后每分钟独立触发，不阻塞 readiness。临时工件清扫改为持久公平游标和 O(scanLimit) 候选内存；worker 以父 PID + 精确启动时刻 watchdog 自行终止失联子进程。
- 第五轮动态证据已执行：V32 从 V31 升级 1/1；T-VID-2L 固定损坏 MP4 在 direct/server 均收敛 `VALIDATION_FAILED` 并可重新初始化；T-VID-2M 覆盖首次删除失败、过期清理租约回收、首次 `CLEANED` 后真实迟到 compose 再删除、ACTIVE/REGISTERED/file_object 保护和遗留 FAILED 回填，共 5/5。claim 安全下限/极值及 direct 已认领后回退、取消、缺失 multipart 的候选退休反例 3/3，worker/清扫/调度聚焦单测 19/19。提交材料为 `docs/reviews/ws-03-fifth-remediation-submission-2026-07-23.md`；这些仍是开发者证据，第五轮独立报告 PASS 前正式状态不变。
- 第五轮最终门禁：本轮专用空库成功执行 33 个迁移至 V32；Surefire 148/148、Failsafe 168/168，合计 316/316，0 failure/error/skip；Phase 7 44/44，V32 迁移 IT 1/1；前端 type-check/build、dev/prod Compose config 与 `git diff --check` 均通过。
- Phase 0、29、35b、36–53 的缺失报告已补齐：14 个阶段 PASS；Phase 0、39、41、42、44、47、53 复核退回。阶段总审计为 `docs/reviews/phase-gap-audit-2026-07-23.md`，逐阶段报告为 `docs/reviews/phase-*-review.md`。
- 第四轮门禁证据：整改者专用全新数据卷 Flyway V1–V31，Surefire 140/140、Failsafe 159/159，合计 299/299；Phase 7 36/36。独立复核核对 XML 后另跑安全白名单 clean verify 17/17、后端 package、前端 type-check/build、dev/prod Compose config、MinIO 依赖树与 fat-JAR worker，均通过。按用户要求未执行畸形媒体、破坏性故障或攻击性并发。
- 后续近端顺序为：提交第五轮整改并交独立复核者仅重核第四轮 1 High / 1 Medium / 2 Low；WS-3 PASS 后按风险修 Phase 42 → 39 → 41 → 47 → Phase 53 的 demo 元数据/旧对象 reconcile → 44 → 0。可播放视频资源虽已替换，但 demo SQL 仍记录 528B/旧摘要，且旧同名 MinIO 对象不会升级替换，故仍单列 Phase 53 High。
- V32 发布必须停写并停止全部第五轮之前的后端与 worker，确认无旧进程后执行 Flyway 至 V32，再启动全部第五轮新实例、核对启动回填/对账与 generation 对象键，最后恢复写流量；禁止 V31/旧稳定 key 协议与第五轮混部，V32 后禁止回滚旧协议二进制。每个后端实例必须独占具有独立配额/文件系统的 probe 卷，禁止共享目录/卷或 `docker compose --scale` 复用当前命名卷。
- 遵守用户安全边界：未执行漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz 或针对现有服务的破坏操作。T-VID 使用固定 44 字节结构夹具、一次受控对象删除失败及本轮专用隔离依赖；任何后续可能属于 cyber 的命令必须明确列出并交由用户亲自决定/执行。
- 2026-07-22 的“缺阶段报告”证据债务已在 2026-07-23 清零；其发现的 CI MinIO/type-check 门禁已在当前整改包补齐并独立重核 PASS。历史结论保留于 `docs/reviews/progress-review-2026-07-22.md`。
- `.claude/audits/`、审计 HTML、`docs/audit-remediation-plan.md`、`docs/prompts/` 是本地未跟踪审计资料，不得纳入功能提交。

> 下方 §1 是 2026-06-14 的历史交接快照，保留用于环境与早期实现追溯；当前状态以上述 §0、`PROGRESS.md` 与 `DEVLOG.md` 顶部为准。

## 1. 历史状态（截至 2026-06-14）
- **Phase 0 与 Phase 1 全部完成，并通过 Claude 阶段复核**（Phase 1 复核 PASS：`docs/reviews/phase-01-review.md`）；**Phase 2 已由 Codex 自测完成并置「待复核」**，等待 Claude 按 `docs/REVIEW-GATE.md` 复核。
- 待确认事项 20 项**已于 2026-06-14 书面确认**（`docs/待确认事项确认单.md`）：原确认的业务取值变更为 `validate.name.mode`→`loose`（迁移 `V6`）；#17/#18 的自动开户与证件号后六位口令已被 2026-07-15 WS-2 安全整改取代为 `false/random`（迁移 `V27`）。
- 仓库：本地 git，**无远程（私有，未开源）**；当前工作分支为 `feature/phase02-T024-authentication`。
- 提交链：
  ```
  08c638f 前端脚手架 + CI [T-008/009/010]   ← Phase 0 完成
  121a489 MinIO文件服务 + 切面 [T-006/007]
  4490c12 DB链路 Flyway/MyBatis-Plus [T-003/004/005]
  698b51f Maven骨架 + common + boot [T-001/002]
  6948682 规划基线
  ```
- 已验证（详见 `DEVLOG.md`）：后端 `mvn package` 9 模块 SUCCESS；运行后 Flyway 迁移 v1、4 张表、`sys_param` 17 行、`/api/health`=UP、`/doc.html`=200；MinIO 上传→预签名→下载 内容一致；前端 `vite build` 通过。

### Phase 2 待复核状态（2026-06-14 22:33 CST）
- 当前工作分支：`feature/phase02-T024-authentication`。
- T-023 已提交；T-024~T-029 代码已完成并通过 Codex 自测，`PROGRESS.md` Phase 2 状态为「待复核」（不得由 Codex 自行置 ✅）。
- Codex 本轮未启动任何常驻 8080/5173 服务；`Get-NetTCPConnection -LocalPort 8080,5173` 无监听。
- Flyway 当前版本：`V8` 已成功应用，`flyway_schema_history.version=8` 的 checksum 为 `-193563120`。
- 为保证 AT-13 可重复验证，`V8__rbac_seed.sql` 已新增 Phase 2 专用测试学院：
  - `PHASE2_COLLEGE_A` → `id=800000000000000201`
  - `PHASE2_COLLEGE_B` → `id=800000000000000202`
  - `test_student/test_college_clerk/test_college_auditor/test_review_teacher` 已绑定学院 A。
- 当前后端已具备：
  - 认证：`/api/auth/captcha`、`/api/auth/login`、`/api/auth/refresh`、`/api/auth/change-pwd`、`/api/auth/me`
  - JWT + BCrypt + 首次改密拦截 + 登录失败锁定参数化
  - `@PreAuthorize` 权限校验
  - `DataScopeContext` / `DataScopeAspect` / `DataScopeService` 真正落地
  - 系统管理接口：`/api/system/user|role|permission/tree`
  - 探针接口：`/api/phase2/probe/*` 用于 401/403/AT-13 反例验证
- 当前前端已具备：
  - `frontend/src/views/LoginView.vue` 真实验证码登录 + 首次强制改密弹窗
  - `stores/user.ts` / `router/index.ts` / `layouts/MainLayout.vue` 已接入真实 `me`、refresh、权限守卫与动态菜单
  - `frontend/src/views/system/SecurityManageView.vue` 覆盖用户/角色/权限/数据范围页
- 当前自测证据：
  - `mvn -B -ntp -pl platform-boot -am -Dtest=Phase2SecurityIT -Dsurefire.failIfNoSpecifiedTests=false test` 通过（2 tests，随机端口，自然退出）
  - `mvn -B -ntp package` 通过
  - `npm --prefix frontend run type-check` 通过
  - `npm --prefix frontend run build` 通过（仅 Vite large chunk warning）
  - DB 核验 7 角色、52 权限、104 角色权限映射；关键矩阵正反例通过；测试账号锁定状态已清理
- 下一步：
  1. 提交当前 Phase 2 待复核版本（若尚未提交）。
  2. 交 Claude 按 `docs/REVIEW-GATE.md` 复核 AT-13、§15.1 矩阵和越权反例。
  3. 若 Claude 退回，只修 Blocker/Major 并重交；PASS 后才可置 Phase 2 为 ✅ 并进入 Phase 3。

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
- **坑③（Docker）**：依赖容器需 Docker Desktop 引擎运行；常规容器名 `tcp-mysql`/`tcp-redis`/`tcp-minio`。用户已明确弃用 bigdata 环境：`hdp11/hdp12/hdp13` 容器于 2026-07-22 删除，`bigdata:3.3.0` 镜像及 9 个 `bigdata_*` 数据卷于 2026-07-23 删除；复核仍应使用独立 Compose 和独立 schema，避免污染共享 dev 数据。

## 3. 一键操作命令（本机已验证，Git Bash 复制即用）
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"
MVN="/c/Users/wenbibuhaoqwq/tools/apache-maven-3.9.9/bin/mvn"
REPO="C:/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform"

# 启动依赖（MySQL/Redis/MinIO）
docker compose -f "$REPO/docker-compose.dev.yml" up -d

# 后端：构建；运行期服务必须在 Codex 外部终端启动
"$MVN" -f "$REPO/pom.xml" -B -ntp -DskipTests package
export JWT_SECRET="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
export SPRING_PROFILES_ACTIVE="dev"
cd "$REPO"
# 下列 dev-serve 命令只能在外部 Git Bash 窗口执行，禁止由 Codex/exec 直接调用：
bash scripts/dev-serve.sh backend http://127.0.0.1:8080/api/health "$JAVA_HOME/bin/java" -jar "$REPO/platform-boot/target/teacher-cert-platform.jar"
#   健康 curl http://localhost:8080/api/health   文档 http://localhost:8080/doc.html
bash scripts/dev-stop.sh backend

# 前端；dev 服务同样只能在 Codex 外部终端启动
npm --prefix "$REPO/frontend" install
bash "$REPO/scripts/dev-serve.sh" frontend http://127.0.0.1:5173 npm --prefix "$REPO/frontend" run dev -- --host 127.0.0.1
bash "$REPO/scripts/dev-stop.sh" frontend
npm --prefix "$REPO/frontend" run build

# 如 Git Bash 因系统资源/权限异常不可用，可用 PowerShell 等价脚本：
# $env:JWT_SECRET='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
# .\scripts\dev-serve.ps1 backend http://127.0.0.1:8080/api/health 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe' -jar platform-boot\target\teacher-cert-platform.jar
# .\scripts\dev-stop.ps1 backend

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
- 版本锁定在**父 pom**：Spring Boot 3.4.13 / MyBatis-Plus 3.5.16 / Knife4j 4.5.0 + springdoc 2.8.17 / FastExcel 1.1.0 / MinIO 8.5.12 / jjwt 0.12.6；Flyway 由 Boot 3.4.13 管理并显式引入 `flyway-mysql`。前端使用 npm + `package-lock.json`。
- DB：库 `teacher_cert`，`root`/`root123`；MinIO `minioadmin`/`minioadmin123`，bucket `teacher-cert`。
- **Flyway 迁移**：`platform-boot/src/main/resources/db/migration/`，**V1~V8 已用**（V1 base / V2 dict / V3 dict_seed / V4 region_seed / V5 subject_seed / V6 confirmed_params / V7 rbac / V8 rbac_seed）→ **后续从 `V9__student.sql` 起**（Phase 3）；版本号以**磁盘 max+1** 为准、不改已发布脚本、种子幂等。
- 实体继承 `BaseEntity`（id/审计字段/逻辑删除自动）；Mapper 放 `**/mapper`（已 `@MapperScan("cn.edu.gpnu.platform.**.mapper")`）；统一返回 `Result`；写操作 `@AuditLog`；列表/导出/统计查询 `@DataScope`；当前用户取 `UserContext`。
- **文本化字段全链路 String + Excel `@`**（学校代码/学号/证件号/出生日期/证书编号/有效期限）——AT-01 生命线，勿用数值/日期类型。

## 5. 当前整改入口 → WS-3 第五轮整改完成，待独立复核
1. 读 `AGENTS.md` → `docs/REVIEW-GATE.md` → `docs/CURRENT-EXECUTION-PLAN.md` → 原报告 `docs/reviews/ws-03-review-2026-07-23.md` → 首批重核 `docs/reviews/ws-03-remediation-rereview-2026-07-23.md` → 第二轮重核 `docs/reviews/ws-03-second-remediation-rereview-2026-07-23.md` → 第三轮重核 `docs/reviews/ws-03-third-remediation-rereview-2026-07-23.md` → 第四轮重核 `docs/reviews/ws-03-fourth-remediation-rereview-2026-07-23.md`。
2. 已闭环：服务端 `SHA256_TREE_V1` 可信指纹；同 uploader/student + 既有 PASS 的秒传边界；普通 VO 去 `fileMd5`；GitHub Actions 真实 MinIO/桶初始化与前端 type-check。U-001 可视为 PASS。
3. 第二轮已闭环：JCodec 三时长交叉核验与唯一视频轨；当前策略/探测器版本 + MinIO HEAD 的 fast-hit；定稿与 assign 共用 review 行锁；V29 逐项检测恢复。
4. 第三轮已实现：`SERVER_CHUNK /merge` 使用可续租 lease、稳定 object key 和数据库当前 token；磁盘按活跃任务累计总预留准入；JCodec 在受限堆独立 JVM 内运行；S3 超时、官方 Compose/.env/专用临时卷一并落地。这些正常路径已确认有效，但不能视为完整 fencing/recovery。
5. 第四轮已实现且旧 9 项关闭：数据库持久 high-water fencing + Redis 随机 owner；server finalize/assign、direct NoSuchUpload 和 server 源分片丢失收敛；临时卷 owner/reaper/独占锁；worker 死亡确认与活孤儿准入；原子容量公式；MinioClient/AWS S3Client 正数超时；fat-JAR smoke 纳入 verify。发布采用 V31 停机切换且每实例 probe 卷独占。
6. 第五轮已实现：V4 worker 区分 JCodec 内容解析异常与真实源/结果 I/O；V32 generation 候选台账、独立 SERVER 对象键、持久清理/墓碑复查和生产启动回填/每分钟调度；持久公平清扫游标；父 PID + 启动时刻 watchdog。
7. 第五轮当前证据：本轮专用空库 33 个迁移至 V32；Surefire 148/148、Failsafe 168/168，合计 316/316；Phase 7 44/44、V32 迁移 IT 1/1；T-VID-2L/2M + 对象保护/遗留回填 5/5、claim/direct 终态反例 3/3、聚焦单测 19/19、预签名端点/CORS 1/1；前端 type-check/build、dev/prod Compose config 与 `git diff --check` 均通过。未经第五轮独立报告 PASS 不得改变 CHANGES REQUESTED。若需要启动后端/前端做运行期验证，仍必须遵守 `AGENTS.md §6.1`，Codex/headless exec 不启动常驻服务。
8. 安全边界：本轮动态反例只使用固定小型结构夹具和专用隔离依赖，不包含漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz 或对既有服务的破坏。任何后续可能属于 cyber 的命令必须先明确指出并交由用户亲自决定/执行。

## 6. 已知坑与规避（别重复踩）
- Lombok `optional` 不向子模块传递 → 已在父 POM 解决。
- `@Configuration` 的 `@PostConstruct` **勿调 `@Bean` 方法**（CGLIB 循环依赖）→ MinIO 桶初始化已拆到 `MinioBucketInitializer`，并复用受管的唯一 `MinioClient`。
- Write 工具不一定建父目录 → 新源码包先 `mkdir -p`。
- Bash 执行含空格路径（JAVA_HOME）务必加引号。
- `target/`、`node_modules/`、`dist/`、`.env` 已被 `.gitignore` 排除；`.gitattributes` 统一 LF（CRLF 警告已消除）。
- **坑④（后端启动看似卡住）**：本机 `Start-Process` / 工具层经常显示 `aborted`，但 `java` 进程其实已经在后台成功启动。**不要重复盲启**；每次启动后先自检：
  - `Get-Process java`
  - `netstat -ano | Select-String ':8080'`
  - `Get-Content -Tail 100 backend.out.log`
- **坑⑤（2分钟无反馈必须自检）**：如果后端启动或接口验证超过 2 分钟没有反馈，必须立刻检查 `java` 进程、`8080` 端口、`backend.out.log/backend.err.log`，不要继续空等。
- **坑⑥（Codex/exec 常驻服务管道卡死）**：禁止直接执行 `java -jar`、`npm run dev`、`vite dev` 这类常驻服务。它们会继承 headless exec 的 stdout/stderr 管道，服务不退出则 EOF 永不到，表现为 Codex 一直 working 且不再发请求。也不要让 Codex/exec 调用包装启动脚本启动常驻服务；`scripts/dev-serve.sh` / `scripts/dev-serve.ps1` 只供外部人工终端、watchdog 或 Claude 复核环境使用。Codex 本轮只能跑会自然退出的一次性命令；若遗留服务导致卡住，可在外部运行 `~/Desktop/codex-watchdog.sh` 兜底。

## 7. 待确认事项（不阻塞开发，已设默认值并参数化）
见 `docs/待确认事项确认单.md`（20 项，**已于 2026-06-14 书面确认**）。原结论中**姓名校验改 `loose` 放宽**（#15，`V6` 落地），**证书序列作用域确认 `SCHOOL_YEAR_SEGMENT`**（按学段，与示例一致）；#17/#18 后由 WS-2 安全整改收敛为默认不开学生账号、禁止 PII 派生口令（`false/random`，`V27` 落地）。待学校后续提供（不阻塞）：完整中职专业课库、免考依据/可免科目清单（#12/#13，模板导入）；性能指标 #19 仍待提供。

## 8. 验收基线 与 阶段复核闸门
- **每个 Phase 完工后 codex 不自行置完成**：置「待复核」，由 **Claude 按 `docs/REVIEW-GATE.md` 复核**通过才算完成（产出 `docs/reviews/phase-NN-review.md`，PASS 才放行下一阶段）。
- 每阶段对照 `docs/phase-NN` 验收清单 + `docs/README.md` §4 的 **AT-01~AT-14 追溯矩阵**；状态在 `PROGRESS.md` 的「AT 跟踪」表登记；Phase 14 整体复验。
- 全局 DoD 见 `AGENTS.md` §12（提交前逐条勾）。
