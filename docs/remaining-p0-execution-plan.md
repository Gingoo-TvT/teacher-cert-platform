# 剩余上线阻断项 执行计划（Phase 40+）— 供 Opus 4.8 / Sonnet 5 分发执行

> **归档提示（2026-07-22）**：本文是 Phase 40+ 的历史分发计划。已实施内容与尚缺复核的项目已合并到 `CURRENT-EXECUTION-PLAN.md`，正文状态不再单独维护。

> 背景：Phase 37a-part2 / 37b / 37c / 37c-2 / 38a / 39 已完成并合并（见 `launch-readiness-plan.md` §11）。
> 已闭环 P0：文件 IDOR、会话撤销、批次 IDOR、RBAC 授权损坏、TLS、状态流转并发(P0-10 主线)、MinIO 出事务(P0-11)、批量下载 OOM(P0-2)、删父孤儿+删学生停登录(P0-12 关键两项)。
> 本文覆盖**剩余**上线阻断项与紧邻的近-P0 正确性项，按 phase 拆分，标注 Opus/Sonnet 归属、文件、做法、陷阱、验收。

---

## 0. 铁律与环境陷阱（每位执行者开工前必读，违反会破坏验收环境或误判）

**Git**
- 本地私有，**无 remote、禁 push**；每 phase 单分支、单 commit；合并用 `git merge --ff-only main`；**每次合并前后 `git remote -v` 必须为空**。
- 分支名 `feature/phaseNN-<slug>`；合并后 `git branch -d`。

**验证 = 构建级（不得前台起 java/vite 做验证）**
- 后端：`mvn -B -ntp verify`（**83 IT 必须全绿**，0 fail/error/skip）。前端：`cd frontend && npm run type-check && npm run build`（仅允许既有 echarts/naive chunk-size 警告）。
- **jar 文件锁**：`mvn verify` 的 `spring-boot:repackage` 会因运行中的后端持有 `platform-boot/target/teacher-cert-platform.jar` 而报 “Unable to rename … .jar.original” → **verify 前必须先杀 :8080 后端**：
  `PID=$(netstat -ano | grep ":8080" | grep LISTENING | head -1 | awk '{print $NF}'); taskkill //PID $PID //F`（**只杀这个 PID，绝不广杀 java/codex 进程**）。verify 通过后从 fresh jar 重起。
- 编译快检（不触发 repackage、无需杀后端）：`mvn -B -ntp -pl <module> -am -DskipTests compile`。

**验收栈 / 数据库**
- 栈：docker `tcp-mysql`(3306)/`tcp-redis`(6379)/`tcp-minio`(9000)。root 密码 `root123`，库 `teacher_cert`。查询：`docker exec tcp-mysql mysql -uroot -proot123 teacher_cert -N -e "..."`。
- 若 3306/8080 被 `hdp11/hdp12/hdp13`(Hadoop) 抢占：`docker stop hdp11 hdp12 hdp13` 后 `docker compose -f docker-compose.dev.yml up -d --force-recreate mysql`。
- 后端重起（fresh jar，detached+重定向，避免管道 hang）：
  `powershell -NoProfile -Command "Start-Process java -ArgumentList '-Dplatform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef','-jar','C:\Users\wenbibuhaoqwq\Desktop\teacher-cert-platform\platform-boot\target\teacher-cert-platform.jar' -WindowStyle Hidden -RedirectStandardOutput 'C:\Users\wenbibuhaoqwq\Desktop\tcp-backend.log' -RedirectStandardError 'C:\Users\wenbibuhaoqwq\Desktop\tcp-backend.err'"`
  就绪判据：`/api/auth/captcha`=200 且日志出现 “Started PlatformApplication”；**起后 `netstat -ano | grep :8080` 确认 PID 是新进程**（曾发生旧进程占端口、新码没生效的误判）。
- **共享库副作用**：验收栈与 IT 共用 `teacher_cert`。`mvn verify` 跑 83 IT 会改测试账号状态（尤其把 `must_change_pwd` 置 1 → JWT filter 每请求校验 → 活体调用返 403「请先修改初始密码」）。活体前按需复位：
  `UPDATE sys_user SET must_change_pwd=0, failed_login_count=0, locked_until=NULL, status='ENABLED' WHERE username IN ('test_...');`
  活体改动的业务数据（学生状态、角色、批次等）**测毕必须复原**。

**测试账号**（密码统一 `ChangeMe123!`）：test_sys_admin(SYS_ADMIN)、test_academic_admin(ACADEMIC_ADMIN)、test_college_clerk / test_college_auditor(学院A=800000000000000201)、test_review_teacher(_b/_c/_d)、test_student(学生记录 id 9001，college 201)。角色 id：COLLEGE_CLERK …002 / COLLEGE_AUDITOR …003 / REVIEW_TEACHER …004 / SYS_ADMIN …007。

**活体登录 helper（captcha 是明文 SVG `<text>`，base64 解码 + 正则取码）**：见 `Desktop\jhw-*-livetest.py`（本会话已留 rbac/race/batchdl/orphan 四个样例，可复用其 `login()/http()/db()`）。

**迁移**：新迁移 = 磁盘最大 +1。**当前最大 V23 → 下一个 V24**，多 phase 同加迁移时按分发顺序占号，领号前先看 main 最新 max。**唯一键陷阱**：本库多数唯一键**不含 `deleted`**，软删行会与新行撞 `DuplicateKeyException`（见 P0-14 教训）——加唯一约束必须用「生成列 + 活跃态才非空」方案（下 Phase 42 详述），且加索引前先查存量是否已违约。

**MyBatis-Plus 已验证要点**：`TransactionTemplate` 由 Spring Boot 自动装配、可直接构造注入；`update(entity, wrapper)` 用实体非空字段作 SET、wrapper 作 WHERE，`@TableLogic` 自动追加 `deleted=0`、审计字段经 `AuditMetaObjectHandler`(platform-boot) 自动填充；`insert(entity)` 主键由 `@TableId(ASSIGN_ID)` 生雪花。

**文档**：每 phase 完成后（同一 commit 内）——DEVLOG.md 倒序追加 1 条（模板见文件头）；`launch-readiness-plan.md` 对应项由 ⏳ 改 ✅ 并附证据（构建结果 + 活体口径）。安全项写「复现→阻断」；可靠性/架构项以 IT+代码边界为证，**不得虚构活体**。

---

## 1. 分发顺序与依赖
- **Phase 40（SB 升级）单独隔离做**（触及全栈依赖）——建议第一个或最后一个，**不与其它 phase 交叉**；其后其它 phase 基于新基线。
- **Phase 41（运维基建）**、**42（并发收尾）**、**43（完整性/正确性收尾）** 相互独立，可并行分发给不同执行者；**唯一协调点＝迁移编号**（谁先落谁占 V24，后者顺延）。
- **Phase 44（容量/性能）** 可选、最后做。

---

## 2. Phase 40 — P0-8 Spring Boot 3.2.11 EOL 升级 【Opus，高风险，独占】

**为何 P0**：3.2.x 已 EOL；传递的 Spring Security 6.2.x 命中 2025 鉴权绕过族（CVE-2025-41249/41248/41232），正打 `@EnableMethodSecurity`+`@PreAuthorize`——本平台 RBAC 根基。配置无法缓解，必须升级到含修复的受支持分支。

**目标版本**：Spring Boot **3.4.x 最新补丁**（Spring Security 6.4.x，含 CVE 修复）；若 3.4 破坏面过大，回退 **3.3.x 最新受支持补丁**（Security 6.3.x）。二者皆可，以 mvn verify 全绿为准。

**做法（迭代式，Opus 亲自）**：
1. 先查兼容矩阵：`pom.xml:10` parent 版本；`mybatis-plus 3.5.7`(:37)、`knife4j 4.5.0`(:38)、`minio 8.5.12`、`jjwt`、`hutool`、`fastexcel`。SB 3.4 通常需 **MyBatis-Plus ≥ 3.5.9**、**knife4j ≥ 4.5.0/4.6.0**（确认 openapi3-jakarta 对 SB3.4 的支持）。
2. 改 `pom.xml`：bump parent 到目标版本；同步 bump `mybatis-plus.version`、`knife4j.version`（如需）。**一次只动版本号**，先 `mvn -B -ntp -DskipTests compile` 看编译面。
3. `mvn -B -ntp verify` → 逐个修复：常见破坏点＝Spring Security config DSL（`authorizeHttpRequests`/`requestMatchers` 已在 6.x，通常无需改）、MockMvc/`@MockBean`(3.4 起 `@MockBean` 弃用→`@MockitoBean`，仅测试类)、`spring-doc`/knife4j bean、Hibernate/JPA（本项目用 MyBatis 无此项）、`server.servlet` 配置键。
4. **RBAC 回归活体**（关键，因为这正是 CVE 面）：栈起后复跑一条数据范围/授权用例——学生 `GET /system/user`=403、学院A教务员读学院B学生=404、`@pms.has` 正常放行本域——确认 `@PreAuthorize` 语义未因升级改变。
5. 回退预案：若 3.4 依赖僵局（MP/knife4j 不兼容），落 3.3.x 最新补丁并在 DEVLOG 记录原因。

**验收**：`mvn verify` 83/83 绿；前端不受影响（无需改）；`flyway_schema_history` 不变（本 phase 无迁移）；RBAC 活体 3 条通过；`git grep 3.2.11` 无残留。
**陷阱**：`platform-boot/src/main/resources/application*.yml` 里 SB 配置键在 3.4 可能重命名（启动即 warn/fail，看日志）；knife4j 与 springdoc 版本错配会启动期 NoSuchMethod。

---

## 3. Phase 41 — 运维基建（P0-5 / P0-6 / 相关 P1）【Sonnet 主体，Opus 复核 grants 与 backup 设计】

> 多为 prod 配置/脚本，dev 栈（用 root）不便运行时验证；验收以「配置正确 + 文档齐 + 可解释」为准，勿虚构活体。

### 41.1 P0-5 生产 DB 改非 root 最小权限用户 【Sonnet 写、Opus 定 grant 集】
- **现状**：`application-dev.yml` datasource `username: root`（dev 可留 root）；prod 亦须非 root。
- **做法**：
  1. 新增 `docker-entrypoint-initdb.d` 初始化脚本（如 `deploy/mysql-init/01-app-user.sql`，prod compose 挂载到容器 `/docker-entrypoint-initdb.d/`）：创建 `teacher_app`@`%`（密码取环境变量占位，勿硬编码），`GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES,DROP ON teacher_cert.* TO teacher_app`（DDL 权限供 Flyway 迁移；**不授** `GRANT OPTION/SUPER/FILE/PROCESS`，**不给** `*.*`）。Opus 复核 grant 集是否够 Flyway 且最小。
  2. `application-prod.yml`：datasource `username: ${DB_USERNAME}` `password: ${DB_PASSWORD}`（env 驱动，非 root/非明文）。
  3. prod compose：MySQL 服务不再把 3306 publish 到主机（仅内部网络）；后端服务注入 `DB_USERNAME/DB_PASSWORD` env。
- **验收**：脚本 SQL 语法正确（可在 dev 库 `docker exec` 试建该用户并 `SHOW GRANTS` 核对，再 `DROP USER` 清理，不动 root/dev 配置）；application-prod.yml 不含 root；README/部署文档补 env 说明。

### 41.2 P0-6 真备份（替换假/空备份）【Opus 设计 + Sonnet 落实现】
- **先读**：`SystemManagementService.triggerBackup`/`backups`（platform-system 实现类）与 `SystemAuditController:70/78`（`/api/system/backup`、`/backup/trigger`）——确认当前是否只写了一条 `backup_record` 记录而无真实转储。
- **做法（择一，Opus 定）**：(A) `triggerBackup` 调用 `mysqldump`（容器内/宿主）产出 `.sql.gz` 到备份卷或 MinIO 桶，记录真实文件路径/大小/校验；(B) 若不便执行外部进程，至少产出**逻辑导出**（关键表 → 文件/MinIO）并落实文件。**必须**让 `backup_record.status/文件位置` 反映真实产物，不再是空操作。加保留策略/失败态。
- **验收**：触发一次备份 → 产物文件真实存在且非空、记录指向它；栈起活体：`POST /api/system/backup/trigger`（test_sys_admin，需 `system:backup`）→ 返回记录 → 核验产物存在。**这是可活体验证的**，请做「复现（旧＝无产物）→阻断（新＝有产物）」。

### 41.3 相关 P1（同 phase 顺带，Sonnet）
- **`.dockerignore`**（§7.11）：仓库根加 `.dockerignore` 排除 `.env`、`.git`、`**/target`、`**/*.log`、`node_modules`、`frontend/dist`——防真实 `.env`/密钥被 `COPY . .`（后端 Dockerfile:17）打进镜像层。
- **Redis 加密码 + 不暴露端口**（§7.11）：prod compose redis `--requirepass ${REDIS_PASSWORD}`；`application-prod.yml` redis 加 password；redis/minio 端口不 publish 主机。
- **`FileServiceImpl.upload` InputStream 未关闭**（§7.2 P1）：`platform-file/.../FileServiceImpl.upload` 用 try-with-resources 或 finally 关闭入参流（防高并发流/socket 泄漏）。**注意**：确认 MinIO putObject 不需要流在其后仍开启（putObject 同步读完即可关）。Sonnet，trivial。

---

## 4. Phase 42 — P0-10 并发收尾（迁移 V24）【Opus，设计密集】

> 主线（18 处状态流转原子条件更新）已在 37b 完成。本 phase 补三类**异于状态守卫**的并发缺口 + 并发重复创建的唯一约束。

### 42.1 唯一约束防并发重复创建（迁移，MySQL 生成列方案）【Opus】
- **问题**：`certificate (student_id, assessment_year)` 仅非唯一索引 → 并发生成两张有效证书；`student.id_card_no` 非唯一 → 并发建同证件号两人。**不能**直接加普通唯一键：① 重开(reissue)合法地为同一 student-year 产生多张（VOIDED/REISSUED + 新活跃），② 软删行会撞唯一键。
- **做法（生成列 + 活跃态才非空，NULL 不入唯一约束）**，迁移 **V24**：
  ```sql
  -- 证书：仅"活跃"证书受唯一约束；VOIDED/REISSUED/软删 → NULL（可共存）
  ALTER TABLE certificate ADD COLUMN active_key VARCHAR(48)
    GENERATED ALWAYS AS (
      CASE WHEN deleted = 0 AND status IN ('GENERATED','ISSUED','EXPORTED','ARCHIVED')
           THEN CONCAT(student_id, '-', assessment_year) END) STORED;
  CREATE UNIQUE INDEX uk_cert_active ON certificate (active_key);
  -- 学生证件号：仅未删学生唯一
  ALTER TABLE student ADD COLUMN idcard_key VARCHAR(64)
    GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN id_card_no END) STORED;
  CREATE UNIQUE INDEX uk_student_idcard ON student (idcard_key);
  ```
- **上线前必查存量违约**（否则加索引失败）：
  `SELECT student_id,assessment_year,COUNT(*) FROM certificate WHERE deleted=0 AND status IN (...) GROUP BY 1,2 HAVING COUNT(*)>1;`（student idcard 同理）→ 有则先在迁移前人工/脚本去重，迁移文件顶部注明。
- **陷阱**：`id_card_no` 若为**加密存储**（查 student 实体/写入路径，本项目有 `plainIdCard` 端点，疑似明文或可逆）——若为**随机盐加密**则密文不定、无法唯一，需改存确定性摘要列再唯一；执行者**先确认存储形态**，明文/确定性→可直接生成列，随机→在 plan 里回报改设计。
- **验收**：迁移 `mvn verify` 通过（Flyway 迁移在 IT 启动时执行，全绿即迁移成功）；栈起活体：两并发 `POST /certificate`(generate) 同 student-year → 仅 1 成功、另一撞 `uk_cert_active` 被拒（`DataIntegrityViolation` 需在 service 捕获转 BizException「本年度已有有效证书」，勿裸 500——参考 `CertificateServiceImpl:129` 已有 DuplicateKey 捕获风格）。

### 42.2 confirmImport 双确认重复导入 【Opus】
- **问题**：`ExchangeServiceImpl.confirmImport`（非 @Transactional，末尾才翻状态）→ 两并发都过 `PREVALIDATED` 检查 → 全量导两次。
- **做法**：`ExchangeBatchStatus`（`platform-exchange/.../support/ExchangeBatchStatus.java`）加 `IMPORTING` 过渡态；confirmImport 开头**原子认领**：
  `int claimed = batchMapper.update(null, new LambdaUpdateWrapper<ImportExportBatch>().eq(id).eq(status,'PREVALIDATED').set(status,'IMPORTING')); if(claimed==0) throw BizException("批次不可确认导入或正在导入");`
  （confirmImport 无环绕事务，该 update 立即提交、并发可见）。末尾照常置 IMPORTED/FAILED。`rollback` 的可回滚态集需评估是否纳入 IMPORTING（崩溃残留恢复）；`batches()` 列表 label 补 IMPORTING。
- **验收**：栈起活体：构造一个 PREVALIDATED 批次，两并发 confirmImport → 仅 1 执行导入、另一被拒；DB 无重复导入行。（构造批次较重，可参考 Phase10ExchangeIT 造数方式；若活体成本过高，以新增 IT 覆盖 + 代码边界为证并诚实标注。）

### 42.3 视频计票/幂等并发（settle / merge / submitScore / thirdReview / returnReview）【Opus】
- `VideoReviewServiceImpl.settleIfReady/settle`（:824-856）：两评审并发提交最后一分，REPEATABLE_READ 各自快照只见自己 → 都不结算或都结算。做法：结算处对 review 行 `SELECT ... FOR UPDATE`（或把 REVIEWING→REVIEW_COMPLETED/NEED_REVIEW 用原子条件更新 + 受影响行判定），确保仅一次结算。
- `merge`（:212）幂等守卫（不同于 uploadChunk 有 status 守卫）：重试产生重复 file_object + MinIO 孤儿 + 重指 videoFileId → 对 session 状态加原子认领（UPLOADING→MERGING），仅首个执行合并。
- `thirdReview`/`returnReview`：同 37b 手法（状态原子条件更新 + 行数判定）加守卫。
- **验收**：栈起活体：2 评审并发对同一 REVIEWING 视频提交末分 → 恰一次结算、review 落定态、无卡 REVIEWING；merge 重复调用 → 不产生第二个 file_object。IT 覆盖 Phase7VideoReviewIT 需保持 11/11。

### 42.4 证书 reissue REISSUED 落库（并 §7.4 死枚举）【Opus/Sonnet】
- `CertificateServiceImpl.reissue`(:205) 只 `setLocked(1)` 从不 `setStatus(REISSUED)` → 原证保持 VOIDED → 可被反复重开。做法：原证 `VOIDED → REISSUED` 用原子条件更新（`WHERE status='VOIDED'`，行数=0 抛「已重开/状态变更」），既落库又防并发重复重开。核对 `activeCertificate` 的活跃态集是否把 REISSUED 排除（应排除）。
- **验收**：活体：对同一 VOIDED 证书两并发 reissue → 仅 1 成功、原证 REISSUED、仅 1 张新证；旧证不可再次 reissue。

---

## 5. Phase 43 — 数据完整性 + 领域正确性收尾 【Opus】

### 43.1 P0-12 收尾 【Opus】
- **DB 外键决策**：本库全程软删、生产码无硬删 → FK 级联删除几乎不触发，且软删父行仍在（FK 满足）。**建议不加 FK**，改为：① 保留/补齐应用层删除守卫（已做 deleteCollege 用户校验、delete 学生停登录）；② `deleteMajor` 守卫见下；③ 加**孤儿巡检**（见 43.3 / P1-9）。若坚持加 FK，须先全表补齐无违约数据 + 迁移，风险高——在 plan 回报权衡结论由用户拍板。
- **`deleteMajor` 使用守卫**：引用方 student/training 在 business 模块、且 training 用 code/name 快照（非 major_id），system 模块的 `OrganizationServiceImpl` 无法常规 count。做法（Opus 定）：(A) 在 business 侧提供一个「major 是否在用」的查询服务，system 通过接口/事件调用（引入受控依赖方向）；或 (B) `deleteMajor` 至少校验本模块可见的引用（`major_training_goal` 已删）+ 明确产品约束（禁用而非删除 major）。**倾向 (B) + 把 major 删除改为「停用」**，并在 UI/文档说明。回报结论。

### 43.2 §7.4 证书往返/序列正确性 【Opus，correctness】
- 导出把 `status` 写入"备注"列（:865），导入把该列当学院 id `parseLong`（:1058）→ 重导出文件必坏。修：往返列对齐（导出/导入用同一语义列，别复用）。
- 导入 `cert_no` 不占 `cert_sequence` → 与自动生成永久撞号、`generate` 撞号回滚又不推进 → 「证书编号已存在请重试」永远失败。修：导入证书号纳入序列占用，或生成时跳过已占号。
- 导入 status=ISSUED 但 `issueDate` 从不设 + 无补设路径。修：导入/更正补 issueDate。
- **验收**：IT 覆盖导出→导入往返一致、导入后 generate 不撞号；活体择一关键路径演示。

### 43.3 §7.5 Rule 11 材料合格永久卡死（near-P0）【Opus，correctness，重要】
- `ProcessMaterialServiceImpl.categoryStatus:363-372` 用**全历史行按当前状态**计数，`passed = passedCount>0 && failedCount==0`；FAILED 是终态且无管理员 override → 一条 FAILED 永久钉住该类别不合格，即便重传新材料通过 → **证书合格永久卡死无恢复**。
- 做法：合格判定只算每类**最新/有效**行（按 student+category 取最新一条或排除被替代行）；或加管理员 override/重开路径。Opus 定语义（与产品规则对齐：过程材料合格口径）。
- **验收**：IT：某类别先 FAILED 再传新材料 PASSED → 该类别应判 passed；活体演示恢复路径。

### 43.4 其它 §7 正确性小项（Sonnet 按 Opus 点名，随手）
- §7.7 fail-open：`StudentStatus/TrainingStatus/MaterialStatus.of` 未知值 `orElse(DRAFT)` → 改 `orElseThrow`（与 `ExemptionStatus.of` 一致）；`BirthDateValidator` 接受 2023-02-30 → 用 `LocalDate.parse` 严格校验。
- §9.2 P1 字典软删盲：`DictServiceImpl.existsTypeCode/existsItem` 用 `*IncludingDeleted`（对齐 Organization 做法）+ 全局 `DataIntegrityViolationException` handler 兜底裸 DuplicateKey → 友好提示。
- §7.2 P1 审计/通知吞异常：`AuditLogServiceImpl.record`/`ReviewNotificationHelper` 在 @Transactional 内失败只 log.warn 不回滚 → 评估是否让审计失败回滚主事务（或转异步 outbox）。Opus 定。

---

## 6. Phase 44（可选，容量/性能）【Sonnet 按 Opus 规格】
- §7.3 N+1 `toVO`（5 处：VideoReview:1011/1034/1060、Training:329、AbilityTestResult:227/233、Exchange rowFromCertificate/fullReviewRows）→ 照 `ExemptionServiceImpl.toVO:384` 批量 `.in()`+Map 范式改。
- §7.3 参考数据/权限缓存（ParamServiceImpl 每 getX 一次 DB、DataScopeServiceImpl.resolve、RegionServiceImpl、OrganizationServiceImpl majors 3N+1）→ 加 Caffeine/Redis 缓存（读多写少，写时逐出）。
- §7.3 证书导出 `selectCertificates:812` 无界 + `XSSFWorkbook` 全 DOM → 改 SXSSF 流式 + 分页（对齐 38a 的流式响应；导出量大时同样直写响应流）。
- §7.3 P1-1 真分页：多列表当前 `new PageResult<>(size, records)` 是全量查后包壳（非真分页）→ 铺开 11 文件 + 前端参数化 `page/size`，MyBatis-Plus `Page<>` 真分页。Sonnet 铺开、Opus 定契约与门禁。
- `AuditLogAspect` 同步阻塞写 → `@Async`。

---

## 7. 每 phase 收尾清单（Definition of Done）
1. 代码改动仅限本 phase 范围；`mvn -B -ntp verify` 83/83 绿；（含前端改动则）`npm run type-check` + `build` 绿。
2. 安全项：栈起活体「复现→阻断」证据（脚本 + 关键输出）；可靠性/正确性项：IT 覆盖 + 代码边界说明（诚实，不虚构活体）。活体改的数据已复原。
3. DEVLOG.md 追加 1 条（做了什么/关键决策/问题与解决/偏差/测试/下一步）；`launch-readiness-plan.md` 对应项 ⏳→✅ 带证据 + 登记「待收尾」。
4. 单 commit（消息含 phase + P 编号 + 验收摘要 + `Co-Authored-By: Claude ...`）；`git merge --ff-only main`；`git remote -v` 空；`git branch -d`。
5. 迁移若占号：确认 main 最新 max 后领 V(N+1)，迁移文件顶部注明存量前置检查（如唯一约束去重）。

---

## 8. 建议分发
- **Opus 4.8**：Phase 40（SB 升级）、42（并发收尾全）、43.1/43.2/43.3（完整性与正确性关键）、41.2 backup 设计。
- **Sonnet 5**：41.1/41.3（运维配置）、43.4（正确性小项）、44（容量/性能，按 Opus 规格）、41.2 backup 实现落地。
- **协调**：迁移编号（V24 起）、Phase 40 与其它不交叉、每 phase 独立分支单 commit。
