# DEVLOG.md — 开发日志

> 规范见 `AGENTS.md` §9。**倒序追加**（最新在最上）。每完成一个任务或每个工作会话至少一条。
> 条目模板（复制使用）：
>
> ```
> ## [YYYY-MM-DD] T-xxx 标题
> - 做了什么：
> - 关键决策与理由：
> - 问题与解决：
> - 与规格的偏差/疑问：（如有，须同步改 plan/docs 或记入待确认事项）
> - 测试：（测试名/结果）
> - 下一步：
> ```

---

## [2026-07-04] Phase 41.1 + 41.3（Sonnet 5 执行，Phase 41，待复核合并）— P0-5 生产 DB 非 root 最小权限账号 + `.dockerignore`/Redis 密码/InputStream 泄漏收尾
- 做了什么：①**P0-5**：新增 `deploy/mysql-init/01-app-user.sh`（`docker-entrypoint-initdb.d` 初始化脚本），创建 `teacher_app`@`%` 应用账号，仅授 `SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES,DROP ON teacher_cert.*`（DML+Flyway 所需 DDL），不授 `GRANT OPTION`/`SUPER`/`FILE`/`PROCESS`/`*.*`；`application-prod.yml` 新增 `spring.datasource.username/password: ${DB_USERNAME}/${DB_PASSWORD}`；生产 `docker-compose.yml` mysql 服务不再 publish 3306、挂载新 init 脚本目录，backend 服务把 `SPRING_DATASOURCE_USERNAME: root`/`PASSWORD: ${MYSQL_ROOT_PASSWORD}` 换成 `DB_USERNAME`/`DB_PASSWORD`。②**41.3**：根新增 `.dockerignore`（排除 `.env`/`.git`/`**/target`/`**/*.log`/`node_modules`/`frontend/dist`）；prod redis 加 `--requirepass ${REDIS_PASSWORD}` 且不再 publish 6379，healthcheck 同步改带密码；`application-prod.yml` 新增 `spring.data.redis.password: ${REDIS_PASSWORD}`；修复 `FileServiceImpl.upload` InputStream 未关闭（§7.2 P1）——`try (in) { minioClient.putObject(...) }`（Java 9+ 等价 try-with-resources）。③`.env.example`/`README.md` 补充 `DB_USERNAME`/`DB_PASSWORD`/`REDIS_PASSWORD` 说明，移除不再使用的 `MYSQL_PORT`/`REDIS_PORT`。**dev 栈（`docker-compose.dev.yml`/`application-dev.yml`）按任务要求全程未动，仍用 root。**
- 关键决策与理由：mysql-init 用 `.sh` 而非计划原文提到的字面 `.sql`——MySQL 官方镜像对 `docker-entrypoint-initdb.d/*.sql` 按字面执行、无变量替换机制，若要"密码取环境变量、不硬编码"必须用 shell 脚本读容器 env 再拼 SQL，`.sh` 是唯一能同时满足"env 驱动"与"非硬编码"两个约束的标准做法（官方镜像本就同时支持 `.sh`/`.sql`/`.sql.gz` 三种 initdb.d 文件，非临时变通）。`FileServiceImpl.upload` 内部关闭入参流前，已逐一确认全部 5 处调用方（`FileController.upload`、material `upload`/`replace`、exemption `uploadMaterial`/`replaceMaterial`）均不在 upload 返回后复用该流，且 MinIO putObject 已按声明 size 同步读完整个 stream，故在方法内关闭安全。**MinIO(9000/9001) 端口本次刻意未动**：`presignedGet` 返回给浏览器的 URL 本就直接嵌入 `minio` 容器内部 DNS 名，即便 publish 端口浏览器也无法解析该主机名，对外可达性是需要反代/公网 endpoint 设计的独立架构问题，超出任务书显式界定的范围（任务书只要求 DB+Redis 端口收敛），留后续 phase 处理。
- 问题与解决：无阻塞，实现一次到位。
- 与规格的偏差/疑问：§7.11 原文把 MySQL/Redis/MinIO 端口发布合并一条，本次任务书显式缩小到仅 DB(41.1)+Redis(41.3)，MinIO 端口按上述理由未处理——非遗漏，已在 launch-readiness-plan.md §7.11/§11 明确记录为独立后续项。41.2（真实定时备份，P0-6）按任务书明确排除，未做。
- 测试：`mvn -B -ntp verify` BUILD SUCCESS，**83/83 绿**（0 fail/error/skip），9 个 reactor 模块全 SUCCESS（含 `FileServiceImpl.upload` 改动路径覆盖的 `Phase5MaterialIT`/`Phase6ExemptionIT` 等真实 MinIO 上传集成测试）。mysql-init 脚本的 SQL 逻辑在 dev 库 `tcp-mysql` 容器活体验证：建同名测试账号 → `SHOW GRANTS` 核对与设计完全一致（`SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER ON teacher_cert.*`，无 `GRANT OPTION`/`SUPER`/`FILE`/`*.*`）→ 以该账号实测 `SELECT`/`CREATE TABLE`/`DROP TABLE` 均成功 → `DROP USER` 清理，dev root 数据源全程未动。生产 compose/yml 改动为语法与静态核对，未起 prod 栈活体（需要真实生产网络/密钥，非本地可行）。
- 下一步：本 phase 停在分支 `feature/phase41-ops` 单个 commit，**未合并入 main**，待人工复核。后续：41.2 真实定时备份（P0-6）、Phase 40 Spring Boot 版本升级（P0-8）、P0-10/P0-12 收尾项（唯一约束、DB 外键、`deleteMajor` 守卫、reissue/confirmImport/视频计票并发收尾）、MinIO 对外可达性（反代/公网 endpoint）设计后再收紧端口。

## [2026-07-04] Phase 39 完成（Opus4.8+Sonnet5 执行，Claude 亲自把关关键处）✅ — P0-12 删父孤儿 + 删学生不停登录
- 做了什么：①`StudentServiceImpl.delete` 后置 `userMapper.update(null, eq(studentId).set(status,'DISABLED'))` 停用被删学生的登录账号。②`OrganizationServiceImpl.deleteCollege` 注入 `SysUserMapper`，在「有专业则拒删」外增「有用户（`sys_user.college_id`）则拒删」。
- 关键决策与理由：删学生用「停用账号」而非删 sys_user——JWT filter 每请求校验 `status=ENABLED`（`filter:62`），停用即让旧 token 下次请求 401，且不必跨模块引 platform-security 的 TokenRevocationService（platform-business 不依赖 security）。deleteCollege 用 `sys_user.college_id` 在用校验（SysUserMapper 属 system 同模块），覆盖"有学生却无 major 行"缺口；学生几乎都有账号故此校验有效。`deleteMajor` 守卫未做——引用方 student/training 在 business 模块且 training 用 code/name 快照非 major_id，跨模块+无关联键，需单独设计。
- 与规格的偏差/疑问：无阻塞。DB 外键、MinIO 孤儿清理（P1-9）、deleteMajor 守卫列为 P0-12 收尾。deleteCollege 用户守卫的「有用户无专业」精确场景 demo 无实例（两学院均有专业，被既有专业守卫先拦），由编译+IT+守卫简单性保证。
- 测试：`mvn -B -ntp verify` BUILD SUCCESS，failsafe **83/83 绿**（Phase3StudentIT 7/7 覆盖删学生）。活体（后端 PID 37408）：test_student 旧 token `/auth/me` 删除前 200 → 管理员 `DELETE /student/9001` → 同一 token **401「用户不存在或已停用」**、`sys_user.status=DISABLED`、`student.deleted=1`，复原 ENABLED；`DELETE /college/201`（有专业+用户）被拒、学院仍在。
- 下一步：batch B/C 剩余 P0（mysql-root P0-5、假备份 P0-6、Spring Boot 升级 P0-8）或 P0-12/P0-10 收尾（唯一约束、DB 外键、deleteMajor 守卫）。

## [2026-07-04] Phase 37c-2 完成（Opus4.8+Sonnet5 执行，Claude 亲自把关关键处）✅ — P0-11 收尾：material/exemption 单文件上传移出事务
- 做了什么：material `upload`/`replace`、exemption `uploadMaterial`/`replaceMaterial` 4 个方法去方法级 `@Transactional`，使其调用的 `fileService.upload`（MinIO putObject + file_object insert）不再处于环绕事务内、不占用 DB 连接。
- 关键决策与理由：4 方法均"校验(读)→fileService.upload→1 次业务写(insert/updateById)"，单业务写 autocommit 即原子，无需事务；且仅被控制器调用（grep 确认无 service 自调用），去 `@Transactional` 无副作用。未拆 `fileService.upload`（保持共享服务契约），接受与视频一致的罕见孤儿（插入失败遗留无引用 file_object + MinIO 对象）。
- 与规格的偏差/疑问：无阻塞。`FileServiceImpl.upload` InputStream 未关闭（§7.2 P1）为独立资源泄漏项，未纳入本次。
- 测试：`mvn -B -ntp verify` BUILD SUCCESS，failsafe **83/83 绿**（Phase5MaterialIT 4/4、Phase6ExemptionIT 4/4 覆盖上传/替换，真实 MinIO）。新起后端 PID 36472 就绪。
- 下一步：batch B/C 剩余 P0（无 FK/孤儿 P0-12、mysql-root P0-5、假备份 P0-6、Spring Boot 升级 P0-8）。

## [2026-07-04] Phase 38a 完成（Opus4.8+Sonnet5 执行，Claude 亲自把关关键处）✅ — P0-2 批量下载流式 zip（整包进堆 OOM）
- 做了什么：`ProcessMaterialServiceImpl.batchDownload` 由"`ByteArrayOutputStream` 攒完整 zip → 返回 `byte[]`"改为流式：`BatchDownloadFile` 记录改带 `ContentWriter`（`writeTo(OutputStream) throws IOException` 函数接口），zip 直写响应输出流，逐文件从 MinIO 读→写；`ProcessMaterialController` 改 `file.content().writeTo(response.getOutputStream())`。新增单次 `MAX_BATCH_DOWNLOAD_FILES=2000` 上限。
- 关键决策与理由：查询 + 上限校验放在返回 `BatchDownloadFile` 之前（当前请求线程执行，`@DataScope`/`UserContext` 生效，异常在写响应头前抛出 → 干净错误）；流式 writer 由控制器在请求线程上同步 `writeTo`，故 MinIO 读循环内的 `fileObjectMapper.selectById` 仍有 UserContext/连接可用，无异步 ThreadLocal 问题。去空判改动（保留空批 → manifest-only zip 旧行为，避免破坏 IT）。仅改材料批量下载（P0-2）；证书导出 `XSSFWorkbook`/无界（§7.3 P1）另行收尾。
- 问题与解决：`minioClient.getObject` 抛受检 `Exception`，在 writer 内 try-with-resources 的 catch(Exception) 转 `BizException`；zip 结构性 `IOException` 经 `ContentWriter throws IOException` 上抛。先按 PID 精杀 :8080 释放 jar 锁再 `verify`。
- 与规格的偏差/疑问：无阻塞。流式下中途 MinIO 读失败会截断已提交响应（罕见），换取消除 OOM，可接受；常见非法请求（超限）在流前干净报错。
- 测试：`mvn -B -ntp verify` BUILD SUCCESS，failsafe **83/83 绿**，其中 `Phase5MaterialIT`（`:173` 上传材料入 MinIO → 批量下载 → `ZipInputStream` 解析断言 manifest）真实 MinIO 覆盖文件流式全链路；栈起活体（后端 PID 26732）：test_college_auditor `POST /material/batch-download` → HTTP200 `application/zip`、219B 合法 zip（PK 头）含 `manifest.csv`、CRC 通过。
- 下一步：batch B/C 剩余 P0（无 FK/孤儿 P0-12、mysql-root P0-5、假备份 P0-6、Spring Boot 升级 P0-8）或各 P0 收尾项。

## [2026-07-04] Phase 37c 完成（Opus4.8+Sonnet5 执行，Claude 亲自把关关键处）✅ — P0-11 MinIO I/O 移出事务（连接池耗尽=总瘫）
- 做了什么：`VideoReviewServiceImpl.uploadChunk/merge` 去方法级 `@Transactional`；MinIO `putObject`（分片）/`composeObject`+小分片流式回退（合并）在事务外执行；元数据落库改用注入的 `TransactionTemplate` 短事务（分片：chunk 增改 + `refreshSessionProgress`；合并：`registerComposedFile` + `upsertReviewAfterValidation` + 会话状态；`detail()` 移到提交后）。
- 关键决策与理由：根因是 MinIO 网络往返期间事务未提交 → 持有 Hikari 连接（默认 10）→ 截止日并发大上传耗尽连接池致全站 DB 阻塞。选"上传移出事务 + 元数据短事务"（plan §7.2 修法），而非把整段设 REQUIRES_NEW。`TransactionTemplate` 用 Spring Boot 自动装配 bean 注入，规避同类自调用 `@Transactional` 失效问题。已确认 `registerComposedFile/upsertReviewAfterValidation/validateMergedVideo` 均纯 DB/CPU 无 MinIO，故 merge 可整段抽取。
- 收尾：`FileServiceImpl.upload` 处于 material/exemption 各自 `@Transactional` 内的单文件上传（同反模式、量小）同法收尾。
- 问题与解决：先按 PID 精杀 :8080 后端释放 jar 锁再 `verify`。
- 与规格的偏差/疑问：无阻塞。孤儿风险与改前一致（MinIO 非事务性，未劣化）；已登记 §11 收尾项。
- 测试：`mvn -B -ntp verify` BUILD SUCCESS，failsafe **83/83 绿**，其中 `Phase7VideoReviewIT` **11/11** 经 RANDOM_PORT TestRestTemplate 走真实 HTTP + 真实 MinIO/MySQL 跑通 init→chunk→merge→评审全链路（栈级活体）；连接不再跨 MinIO 往返被持有属架构级保证（调用已移出事务边界），新起后端 PID 16192 启动成功亦确认 `TransactionTemplate` 运行期装配。
- 下一步：P0-11/P0-10 收尾批，或其余 batch B/C P0（Spring Boot 升级 P0-8、批量下载 OOM P0-2、无 FK/孤儿 P0-12、mysql-root P0-5、假备份 P0-6）。

## [2026-07-04] Phase 37b 完成（Opus4.8+Sonnet5 执行，Claude 亲自把关关键处）✅ — P0-10 状态流转并发竞态（复现→阻断）
- 做了什么：18 处审核状态流转由"读状态→Java 判断→`updateById`"（无守卫、后写覆盖先写）改为 **DB 原子条件更新** `update(entity, new LambdaUpdateWrapper().eq(id).eq(status, oldStatus))` + 校验受影响行数，0 行抛"操作冲突"(`code=1000`)。覆盖 6 服务：Student/TrainingProfile/ProcessMaterial(submit/firstReview/secondReview)、Exemption(同上，状态字段 `finalStatus`)、Certificate(issue/markExported/archive/void)、VideoReview(arbitrate/confirm)。
- 关键决策与理由：本期主线选"条件 UPDATE"而非"BaseEntity `@Version`"——前者外科式、免迁移、直达 §8.2 验收标准（6 并发仅 1 成功）且单线程语义不变；`@Version` 系统性兜底 + 唯一约束列为收尾批。`update(entity, wrapper)` 以实体非空字段作 SET、wrapper 作 WHERE，与 `updateById` 行为一致仅多一道状态守卫；`@TableLogic` 自动追加 `deleted=0`、审计字段经 `AuditMetaObjectHandler` 自动填充。
- 收尾（下一批，异于状态守卫的处理）：证书 `reissue`（并 §7.4「REISSUED 从不落库」+ 唯一约束）/`correct`（内容编辑）、导入 `confirmImport` 双确认（需 IMPORTING 过渡态或导入幂等/唯一码）、视频 `submitScore/settle/thirdReview/merge/returnReview`（计票/幂等）、唯一约束 `(student_id,assessment_year)` 等、`@Version` 系统兜底。
- 问题与解决：①先按 PID 精杀 :8080 后端释放 jar 文件锁再 `verify`。②IT 又把 `test_college_clerk.must_change_pwd` 置 1 → 手工复位 0。③活体脚本审计表名笔误 `sys_audit_log` → 实为 `audit_log`（V1），已更正。
- 与规格的偏差/疑问：无阻塞。P0-10 收尾项已登记 §11。
- 测试：`mvn -B -ntp verify` BUILD SUCCESS，failsafe **83/83 绿**。活体（栈 PID 19024 新起、fresh jar）：test_college_clerk 对学生 `990000000000000002` 6 线程 barrier 同时初审 PASS → **仅 1× code=0、5× code=1000「操作冲突」、审计 firstReview 增量=1、终态 `SECOND_REVIEW`**（旧码为 6× 成功/审计 6 条/终态非确定）；测毕复原 FIRST_REVIEW。
- 下一步：P0-10 收尾批，或其余 batch B/C P0（Spring Boot 升级 P0-8、MinIO 出事务 P0-11、批量下载 OOM P0-2、无 FK/孤儿 P0-12、mysql-root P0-5、假备份 P0-6）。

## [2026-07-04] Phase 37a-part2 完成（Opus4.8+Sonnet5 执行，Claude 亲自返工关键处）✅ — batch A 安全急修剩余项（复现→阻断）
- 做了什么：
  - **P0-7 导入批次 IDOR**：`ExchangeServiceImpl` 新增 `ensureBatchAccessible(batch, perm)`（全校/系统放行，否则仅本人 operator 创建的批次），植入 `errorReport/confirmImport/rollback`；`batches()` 列表按 allSchool-else-本人 operator 过滤。批次以 `operatorId` 归属（无 college_id），用操作人校验规避 scopeJson 子串匹配。
  - **P0-14 RBAC 授权"静默损坏/500"**：三关联表（SysUserRole/SysRolePermission/SysUserDataScope）加物理 `@Delete`（deleteByUserId/deleteByRoleId），`SecurityAdminServiceImpl.replaceUserRoles/assignRolePermissions/assignUserDataScope` 改 `deleteByX` + `mapper.insert(entity)`（id=ASSIGN_ID 雪花、审计字段 AuditMetaObjectHandler 自动填充）；assignRolePermissions 用 LinkedHashMap 按权限去重（末次范围为准，防唯一冲突）。撤销我此前给 ON DUPLICATE 补的 FK 列，upsert 还原为原始体。
  - **P0-15 角色弹窗改写数据范围**：`RolePermissionDrawer.vue` 记录后端真实 per-permission `scopeType`，保存用 `scopeFor()`（保原范围→权限 canonical→SCHOOL），弃 3 桶猜测 `defaultScopeFor`。
  - **P0-9 TLS**：`nginx.conf` 加安全头 + `/api` 超时 600s + gzip + `/assets` 缓存 + 443/HSTS/301 模板。
- 关键决策与理由：P0-14 初拟"ON DUPLICATE 补 `role_id=VALUES`"被活体证伪——唯一键不含 `deleted`（`V7:63/99/116`），改 role_id 撞软删旧行→`DuplicateKeyException`→HTTP200 code=500、DB 不变。改"物理先删后插"彻底规避 id 复用 + `parentId*1000` 溢出 + 唯一冲突。`upsert` 保留（`StudentServiceImpl:348` + 12 IT 仍用，已还原为原始 ON DUPLICATE 体）。
- 问题与解决：①旧后端(PID 13632)持 jar 文件锁致 `spring-boot:repackage` 改名失败→按 PID 精杀后重跑 verify 通过。②验收栈与 IT 共用 `teacher_cert` 库，verify 跑 83 IT 把 `test_sys_admin.must_change_pwd` 置 1（JWT filter 每请求校验→assign 返 403"请先修改初始密码"）→手工 `UPDATE` 复位 0。
- 与规格的偏差/疑问：无阻塞。测试库须与运行库物理隔离（并入 §11 说明 + §7.11/P0-5）。
- 测试：`mvn -B -ntp verify` BUILD SUCCESS，failsafe **83/83 绿**（0 失败/错误/跳过）。活体（栈 PID 25932 新起 02:15:50、jar 02:12:03）：test_sys_admin 对 test_review_teacher_c 分配 `[CLERK,AUDITOR,RT]`→code=0/DB 三角色；改分配 `[CLERK,RT]`→**code=0/DB 精确 {CLERK,RT}**（旧码此步 code=500/DB 仍三角色），junction 新雪花 id `2073…858/859`；复原 `[RT]` 时 hard-delete 清除历史 CLERK/AUDITOR 脏行，DB 剩单行 role 004。
- 下一步：batch B/C 剩余 P0（Spring Boot 升级 P0-8、并发 `@Version` P0-10、MinIO 出事务 P0-11、无 FK/孤儿 P0-12、批量下载 OOM P0-2、mysql-root P0-5、假备份 P0-6、P0 无测试 P0-13）。

## [2026-07-03] Phase 35b 完成（Claude 亲自）✅ — 统计报表重做 + 卡顿治理 + 闪烁修复（用户二次反馈）
- 背景：用户反馈 ①统计报表仍丑(x 轴名截断只是"能显示"非好方案) ②几乎所有页 sys_admin 点击卡顿(不止材料/免考) ③材料/免考进入仍有"刷新的页面"闪烁。
- 诊断：①统计图为纵向柱+旋转长标签(治标)；②dev 模式 vite 按需转换模块(Phase 34 拆分后模块数增多→首次导航卡顿)+`hasPerm` O(n) 数组扫描(sys_admin 权限集大)+主包含 naive-ui 未拆；③材料/免考 onMounted `await loadOptions()`→`await loadRecords()` 串行→骨架屏二次闪烁。
- 做了什么（均 Claude 亲自，关键/架构级）：
  1. 统计报表 `StatsReportView` 图表**改横向条形**：维度名放 Y 轴完整可读(不再旋转/截断)，`chartHeight` 随条数(≤20)增长；`ChartBox` 增 `isCategoryAxis` 检测→横条圆角 `[0,4,4,0]`。
  2. `vite.config.ts`：`server.warmup.clientFiles` 预热 布局+全部 components+全部 views(治 dev 首次导航卡顿)；`build.rollupOptions.manualChunks` 拆 echarts/naive/vue(prod 主包瘦身+缓存)。构建实测 naive(1.3MB)/echarts(1MB) 已独立成 chunk。
  3. `stores/user.ts`：`hasPerm` 由 `perms.value.includes`(O(n)) 改 `permSet=computed(new Set)` 的 `.has`(O(1))——sys_admin 每页大量 perm 判定的渲染开销降低。
  4. 材料/免考 `onMounted` 改 `Promise.all([...])` 并行，消除骨架屏二次闪烁。
- 验证：type-check 无错；build ✓ 8.70s(naive/echarts 独立 chunk)；vite 重启加载 warmup 配置、:5173 200、后端 UP。视觉/流畅度交用户目验。
- 备注：echarts 为 `import * as`(全量~1MB)，全站仅用 bar，可选做 tree-shake 进一步瘦身(prod)，本轮未做(风险/收益权衡,dev 无关)。合并 e688f9a(branch feature/phase35b-perf-stats)。

## [2026-07-03] Phase 35 复核通过（Claude · 亲自返工关键件）✅ — 前端验收缺陷 F1–F5
- 做了什么：复核 `feature/phase35-acceptance-fix`（343c491，35 文件）。逐项核 F1–F5 与关键共享件：DataPanel 自动 scroll-x(删 27 硬编码,残留 0)、StudentSelect 远程搜索(去全量 listStudents 预载)、videoDuration 探测+两上传面板+拖拽区、ChartBox 长类目、StatCard 单位内联+greeting 去尾+去重复标签。type-check+build 两轮绿；活体 `/student?keyword=` 张1/DEMO10/李1、端点零 403。
- 结论：**PASS**。codex 本轮质量高（关键件均自行完成且设计正确）。
- **Claude 亲自返工（F4，不退 codex）**：ChartBox 加大标签 `bottom` 但容器仍 320/340px→长类目绘图区被压扁。抽出 `chartLayout` computed 集中 rotate/labelWidth/bottom/top，新增 `resolvedHeight=max(base, top+184+bottom)` 让容器随标签高度同步增高（ResizeObserver 已在→自动 chart.resize）。type-check+build 复跑绿。
- 放行：PROGRESS Phase 35 置 ✅；合并 `main`（本地私有、无远程、不 push）；重启 vite 供用户三宽度目验。

## [2026-07-03] Phase 35 待复核小结（前端验收缺陷修复 F1-F5）
- 做了什么：从 `main` 切出 `feature/phase35-acceptance-fix`，执行 `docs/frontend-fix-plan.md` F1-F5。纯前端展示/交互修复；未改后端、契约、迁移或 stores 逻辑；未新增依赖；未 push。
- 关键决策与理由：共享件按本轮分工先做初版，供 Claude 后续把关/返工：`DataPanel` 自动表宽、`StudentSelect` 远程搜索、视频时长 metadata 探测、`ChartBox` 长类目轴策略、`StatCard` 单位内联。重复铺开项用页面小补丁完成，避免再做批量编码改写。
- 问题与解决：曾有一次机械删除 `scroll-x` 的 PowerShell 写回污染视图编码；已只恢复本轮污染的视图文件并用 `apply_patch` 重新落改动，后续未再使用批量写回。
- 与规格的偏差/疑问：按 build-only 要求未启动常驻服务；三宽度目验、活体角色走查与 Claude 共享件定稿留待复核。

### F1 列表页 / DataPanel
- 页面：证书签发/证书管理/导入导出/材料/免考/统计/学生/系统域/测试/培养/视频组与视频管理等 27 处移除视图硬编码 `:scroll-x`；`DataPanel` 内按列 `width || minWidth || 120` 自动求和作为唯一 `scroll-x`。
- 证据：
```
$ rg -n "scroll-x" frontend/src/views frontend/src/components -g '*.vue'
frontend/src/components\DataPanel.vue:127:      :scroll-x="effectiveScrollX"
```

### F2 学生远程搜索 / 材料·免考·视频·证书·培养·测试·学生本人
- 页面：`MaterialManageView`、`ExemptionManageView`、`video/components/ManagePanel` 移除首屏学生全量预载；`MaterialUploadDrawer`、`ExemptionDrawer`、`UploadVideoDrawer` 改用 `StudentSelect`。同步把证书生成、培养抽屉、测试结果粘贴导入、学生本人页的无参学生加载改为远程/按 ID 查询。
- 证据：
```
$ rg -n "listStudents\(\)|listStudents\(" frontend/src/views frontend/src/components -g '*.vue' -g '*.ts'
frontend/src/components\StudentSelect.vue:61:    const res = await listStudents({ keyword })
frontend/src/views\student\StudentManageView.vue:151:    const res = await listStudents({
frontend/src/views\test-result\TestResultManageView.vue:207:      const res = await listStudents({ keyword: studentNo })
```

### F3 上传入口 / 视频·材料·免考·导入
- 页面：`UploadPanel`、`UploadVideoDrawer` 使用 `detectVideoDurationSeconds` 自动识别时长，失败才显示 `n-input-number`；材料上传、免考申请/替换、导入中心、测试结果导入、学科库导入均换为拖拽上传区。
- 证据：
```
$ rg -n "n-upload-dragger|detectVideoDurationSeconds|durationDetected|durationDetectFailed" frontend/src/views frontend/src/utils -g '*.vue' -g '*.ts'
frontend/src/utils\videoDuration.ts:1:export function detectVideoDurationSeconds(file: File): Promise<number> {
frontend/src/views\video\components\UploadVideoDrawer.vue:6:import { detectVideoDurationSeconds, formatVideoDuration } from '@/utils/videoDuration'
frontend/src/views\video\components\UploadVideoDrawer.vue:29:const durationDetected = ref(false)
frontend/src/views\video\components\UploadVideoDrawer.vue:30:const durationDetectFailed = ref(false)
frontend/src/views\video\components\UploadVideoDrawer.vue:61:    uploadForm.durationSeconds = await detectVideoDurationSeconds(file)
frontend/src/views\video\components\UploadVideoDrawer.vue:157:          <n-upload-dragger>
frontend/src/views\test-result\TestResultManageView.vue:310:          <n-upload-dragger>
frontend/src/views\system\SubjectManageView.vue:322:                <n-upload-dragger>
frontend/src/views\exchange\ExchangeImportView.vue:284:          <n-upload-dragger class="compact-upload">
frontend/src/views\video\components\UploadPanel.vue:9:import { detectVideoDurationSeconds, formatVideoDuration } from '@/utils/videoDuration'
frontend/src/views\video\components\UploadPanel.vue:34:const durationDetected = ref(false)
frontend/src/views\video\components\UploadPanel.vue:35:const durationDetectFailed = ref(false)
frontend/src/views\video\components\UploadPanel.vue:98:    uploadForm.durationSeconds = await detectVideoDurationSeconds(file)
frontend/src/views\video\components\UploadPanel.vue:261:            <n-upload-dragger>
frontend/src/views\exemption\components\ExemptionReplaceModal.vue:58:        <n-upload-dragger>
frontend/src/views\exemption\components\ExemptionDrawer.vue:169:              <n-upload-dragger>
frontend/src/views\material\components\MaterialUploadDrawer.vue:120:              <n-upload-dragger>
```

### F4 统计报表 / ChartBox
- 页面：`ChartBox` 长类目时改 45 度旋转、增大 `labelWidth/grid.bottom` 并用 `overflow:'break'`；`StatsReportView` 统计维度/状态补 tooltip，扩展值列放宽，数量列 `tabular-nums`。
- 证据：
```
$ rg -n "overflow: rotate|rotate =|labelWidth|bottom =|value-list|tabular-nums" frontend/src/components/ChartBox.vue frontend/src/views/stats/StatsReportView.vue
frontend/src/components/ChartBox.vue:54:  const rotate = hasLongLabels || hasCrowdedLabels ? 45 : 0
frontend/src/components/ChartBox.vue:55:  const labelWidth = rotate ? Math.min(180, Math.max(112, maxLabelLength * 8)) : 120
frontend/src/components/ChartBox.vue:56:  const bottom = rotate ? Math.min(156, Math.max(92, maxLabelLength * 6 + 54)) : Math.max(48, Math.min(76, maxLabelLength * 4 + 32))
frontend/src/components/ChartBox.vue:119:        overflow: rotate ? 'break' : 'truncate',
frontend/src/views/stats/StatsReportView.vue:88:  { title: '数量', key: 'count', width: 104, render: (row) => h('span', { class: 'numeric tabular-nums' }, String(row.count || 0)) },
frontend/src/views/stats/StatsReportView.vue:162:    { class: 'value-list' },
```

### F5 Dashboard / StatCard
- 页面：`DashboardView` 问候描述只保留角色与日期；首页指标按 label 去重；`StatCard` 新增内联 `unit`，Dashboard 与统计页不再把单位放进 `sub` 独立行。
- 证据：
```
$ rg -n "greetingDescription" frontend/src/views/DashboardView.vue
78:const greetingDescription = computed(() => `${roleText.value} · ${dateText()}`)
267:  <PageContainer :title="greetingTitle" :description="greetingDescription">

$ rg -n "unit: item\.unit|stat-unit|unit\?:|:unit" frontend/src/views/DashboardView.vue frontend/src/views/stats/StatsReportView.vue frontend/src/components/StatCard.vue
frontend/src/components/StatCard.vue:7:  unit?: string | null
frontend/src/components/StatCard.vue:26:      <span v-if="unit" class="stat-unit">{{ unit }}</span>
frontend/src/components/StatCard.vue:144:.stat-unit {
frontend/src/views/stats/StatsReportView.vue:239:        <StatCard :label="metric.label" :value="metric.value" :unit="metric.unit" />
frontend/src/views/DashboardView.vue:90:    unit: item.unit || null,
frontend/src/views/DashboardView.vue:274:        <StatCard :label="item.label" :value="item.value" :unit="item.unit" :sub="item.sub" :tone="item.tone" :icon="item.icon" />

$ rg -n "profile\.value\.description|sub: item\.unit|:sub=\"metric\.unit\"" frontend/src/views/DashboardView.vue frontend/src/views/stats/StatsReportView.vue frontend/src/components/StatCard.vue
<empty>
```

### 纯前端范围与 Build-Only
- 纯前端范围：
```
$ git diff --name-only | rg "^(platform-|pom\.xml|docker-compose|.*db/migration|.*Controller|.*Service|.*Mapper|.*\.java)"
<empty>
```
- build-only：
```
$ npm --prefix frontend run type-check
> teacher-cert-platform-frontend@1.0.0 type-check
> vue-tsc --noEmit

$ npm --prefix frontend run build
> teacher-cert-platform-frontend@1.0.0 build
> vite build
✓ 4967 modules transformed.
✓ built in 7.79s
(!) Some chunks are larger than 500 kB after minification.
```
- 下一步：单分支单提交后等待 Claude 按 Phase35 gate ①-⑥复核，未自行置复核通过。

## [2026-07-03] Phase 34 完成（Claude 亲自执行）✅ — 组件拆分（落地 W6 行数红线）
- 做了什么：应用户要求由 Claude 亲自执行 Phase 34（不交 codex）。并行派 10 个 worker 子代理，每个只拆 1 个 >400 视图为子组件（抽屉/自助面板/域面板）并放到该视图自己的 `components/` 子目录（文件互不相交→无冲突），严格「逐行搬移、行为不变、不跑 build」；Claude 集中 `vue-tsc`+`vite build` 一次通过（0 错），再对 Org 做二次域拆分（642→65，抽 MajorsPanel/ConfigsPanel），再次 type-check+build 绿。活体 SYS_ADMIN 登录 10 个被拆页接口全 200 零 403。
- 成果：Org 1006→65 / Security 798→398 / Cert 690→314 / Material 697→442 / Dict 610→362 / ManagePanel 601→308 / Training 596→347 / Exemption 620→396 / Audit 523→399 / Student 456→340；新增 33 子组件。剩 5 个 401–487（MyTaskPanel/MajorsPanel/Dashboard/Material/Subject）判为合理内聚，不强拆（400 是启发式非硬指标，强拆内聚组件反损维护性）。
- 行为保全：抽屉→子组件 defineExpose(open)+emit(saved)，父 ref 调用+@saved 重载；共享 saving 拆独立（同刻仅一抽屉）不可观测；年度 watch 忠实拆分；Org 二拆给 n-tab-pane 加 display-directive=show 保持常挂载与原单体一致；权限/v-model/校验/文案/样式原样。子代理逐项自述+Claude 核对。
- 放行：合并 `main`（本地私有、无远程、不 push）；纯前端零依赖变更；请用户目验被拆页抽屉/表单交互。详见 docs/reviews/phase-34-review.md。

## [2026-07-03] Phase 33 复核通过（Claude · frontend-quality-plan §5）✅ — 末阶段：工作台/视频工作台/学生端卡片化
- 做了什么：复核 `feature/phase33-workbench-student` 单提交 `3a95a8e`（10 文件）。核显式 gate ①-⑥：VideoReviewView 915→49、MyTaskPanel 评分工作台(维度/总分/结论/意见)、材料四卡、视频步骤条(含 RETURNED)、证书卡(isStudentMode)、Dashboard v2(问候/快捷入口/真实 total)；W3 空；`vue-tsc`+`vite build ✓ 7.93s`。活体走查 STUDENT(本人证书/材料/档案 200)、REVIEW_TEACHER(/video/tasks/my 200)、AUDITOR(/video/reviews、/reviewer-groups 200)——全 200 零 403。
- 结论：**功能 PASS**（一轮·0 修补）。显式 Phase 33 gate 全达成。
- 系统性遗留（非本阶段独有，如实记录我的口径不一致）：W6「>400 行拆分」全项目 13 文件未达标，含 **Phase 32 我已放行的 OrganizationManageView 833→1006、SecurityManageView 798**；Phase 33 的 ManagePanel 601/Material 697/Cert 690/MyTaskPanel 487/Dashboard 445 与之同类。单独退回 Phase 33 不一致 → 判功能 PASS，W6 行数转**可选 Phase 34「组件拆分」**（纯内部重构、零用户可见、build-only）由用户定夺。
- 放行：PROGRESS Phase 33 置 ✅；合并 `main`（本地私有、无远程、不 push）；Phase 30–33 四阶段完成，栈在跑供用户最终目验。归一化 3 个测试账号密码为 ChangeMe123!。

## [2026-07-03] Phase 33 待复核小结（工作台 v2 + 视频评审工作台 + 学生端友好化）
- 做了什么：从最新 `main` 切出 `feature/phase33-workbench-student`，执行 `docs/frontend-quality-plan.md` §4 Phase 33。纯前端展示层改造；未改后端、契约、迁移或 stores 逻辑；未新增依赖；未 push。
- 关键决策与理由：视频页按 W6 拆为 4 个子组件，主文件只保留权限分区与 tab 组合；评分流程从弹窗改成左右工作台，减少评审教师反复开关弹层。学生端只在 `selfMode` 下替换为卡片/步骤样式，管理与审核角色保留原列表流程。
- 问题与解决：`VideoTask` 本身不含学号/姓名，评分工作台用既有 `getVideoReview` 按任务记录补展示信息；若单条详情读取失败，仍显示任务编号并可评分/播放。工作台快捷入口按角色生成，并用现有权限过滤，避免无入口角色点到空壳。
- 与规格的偏差/疑问：按 build-only 要求未启动常驻前端/后端服务；6 角色真实账号逐页走查留待 Claude 复核。

### DashboardView — 工作台 v2
- 结构件：页头改为“问候语 + 姓名 + 角色 + 日期”；指标卡继续使用统计/通知真实返回值并带 icon；新增角色快捷入口卡；最近通知由表格改为列表；图表继续走 `ChartBox`。
- 肉眼变化：进入首页先看到面向当前角色的问候和 4-6 个直达卡，通知以未读圆点、标题、摘要、时间呈现。
- 自检证据：`rg -n 'ChartBox|quick-entry|最近通知|greetingTitle' frontend/src/views/DashboardView.vue` 命中问候、快捷入口、通知列表和 `ChartBox`。

### VideoReviewView + components — 视频拆分与评分工作台
- 结构件：新增 `UploadPanel.vue`、`MyTaskPanel.vue`、`ManagePanel.vue`、`GroupPanel.vue`；主 `VideoReviewView.vue` 缩至 49 行。
- 评分工作台：`MyTaskPanel` 左侧任务列表显示学号/状态/提交时间，右侧评分区包含维度评分表、总分大数字、结论 radio、意见和提交按钮，不再使用提交评分弹窗。
- 学生视频：`UploadPanel` 在 `selfMode` 下显示上传→评审中→已确认/已退回步骤条，退回/校验失败展示意见并提供重新上传。
- 肉眼变化：评审教师进入“我的评审”即看到左右分栏工作台；学生进入“我的视频”看到步骤条和当前视频卡。

### MaterialManageView — 学生材料四卡
- 结构件：学生 `selfMode` 改四类材料卡片网格；每类卡含 icon、状态标签、文件名/大小、上传/预览/提交按钮和退回意见展示。
- 保留项：审核/管理角色仍使用原 `FilterBar + DataPanel + ReviewDialog` 流程，上传抽屉和预览弹窗复用既有逻辑。
- 肉眼变化：学生不再看到整张材料表，而是四类材料按卡片并排显示，缺失项直接显示“尚未上传材料”。

### CertificateManageView — 学生证书卡
- 结构件：学生 `selfMode` 标题改“我的证书”，证书记录改青绿描边渐变卡；证书编号 mono 大字展示，有效期、任教学科、签发日期和状态徽标同卡呈现。
- 保留项：非学生角色继续使用证书统计、筛选、DataPanel 和生命周期操作。
- 肉眼变化：学生进入证书页看到证书卡而不是管理表格，证书号和有效期成为首屏重点信息。

### Cross Role Matrix — Phase33 静态走查口径
- STUDENT：工作台显示本人信息/材料/免考/教学视频/我的证书/通知入口；材料、视频、证书三页进入 selfMode 卡片或步骤视图。
- COLLEGE_CLERK：工作台显示学生初审、培养信息、材料初审、免考初审、导出中心；视频页若无视频权限不出现空壳分区。
- COLLEGE_AUDITOR：工作台显示材料复审、免考复审、视频指派、测试确认、学院统计；视频页显示评审管理与评审组。
- REVIEW_TEACHER：工作台显示评分工作台、待评分视频、评审记录、通知中心；视频页显示“我的评审”左右评分工作台。
- ACADEMIC_ADMIN：工作台显示证书生成、签发、导入预校验、导出中心、统计、通知；证书页保持管理表格和生命周期操作。
- SYS_ADMIN：工作台显示账号管理、参数审计备份、组织与专业、数据字典、任教学科库；本阶段未新增系统域入口逻辑。

### 自检证据（W2）
附录 A 黑名单输出为空：
```
$pattern = '(权限点|权限码|[a-z]+:[a-z]+:?[a-zA-Z]*[''"]?\s*(显隐|控制)|后端|接口|API|迁移|Flyway|RBAC|SPI|V-0[0-9])'
$out = rg -n --glob '*.vue' $pattern frontend/src/views frontend/src/layouts frontend/src/components | rg -v '//|/\*|import|hasPerm|perms:|@/api'
if ($out) { $out } else { '<empty>' }
<empty>
```

纯前端范围输出为空：
```
$ git diff --name-only | rg '^(platform-|pom\.xml|docker-compose|.*db/migration|.*Controller|.*Service|.*Mapper|.*\.java)'
<empty>
```

视频主文件行数：
```
$ (Get-Content -Encoding UTF8 frontend/src/views/video/VideoReviewView.vue).Count
49
```

视频组件命中：
```
$ rg -n 'UploadPanel|MyTaskPanel|ManagePanel|GroupPanel' frontend/src/views/video frontend/src/views/video/components
frontend/src/views/video\VideoReviewView.vue:5:import GroupPanel from '@/views/video/components/GroupPanel.vue'
frontend/src/views/video\VideoReviewView.vue:6:import ManagePanel from '@/views/video/components/ManagePanel.vue'
frontend/src/views/video\VideoReviewView.vue:7:import MyTaskPanel from '@/views/video/components/MyTaskPanel.vue'
frontend/src/views/video\VideoReviewView.vue:8:import UploadPanel from '@/views/video/components/UploadPanel.vue'
frontend/src/views/video\VideoReviewView.vue:29:        <UploadPanel v-if="selfMode" :can-play="canPlay" />
frontend/src/views/video\VideoReviewView.vue:30:        <ManagePanel
frontend/src/views/video\VideoReviewView.vue:41:        <MyTaskPanel />
frontend/src/views/video\VideoReviewView.vue:45:        <GroupPanel />
```

Phase33 结构命中：
```
$ rg -n 'review-workbench|任务列表|评分区|提交评分|video-step|n-steps|重新上传' frontend/src/views/video frontend/src/views/video/components
frontend/src/views/video/components\UploadPanel.vue:180:    <n-card :bordered="false" class="page-section video-step-card">
frontend/src/views/video/components\UploadPanel.vue:188:      <n-steps :current="stepCurrent" class="video-step">
frontend/src/views/video/components\UploadPanel.vue:222:              {{ currentReview ? '重新上传' : '上传视频' }}
frontend/src/views/video/components\UploadPanel.vue:233:            {{ reviewMessage || '视频已退回，请按意见重新上传。' }}
frontend/src/views/video/components\MyTaskPanel.vue:186:  <section class="review-workbench page-section">
frontend/src/views/video/components\MyTaskPanel.vue:190:          <span>任务列表</span>
frontend/src/views/video/components\MyTaskPanel.vue:230:          <span>评分区</span>
frontend/src/views/video/components\MyTaskPanel.vue:297:              <n-button v-else type="primary" :loading="saving" @click="submitScore">提交评分</n-button>
```

学生材料/证书卡片命中：
```
$ rg -n 'selfMode|material-card-grid|尚未上传材料' frontend/src/views/material/MaterialManageView.vue
frontend/src/views/material\MaterialManageView.vue:93:const selfMode = computed(() => canUpload.value && !canFirstReview.value && !canSecondReview.value)
frontend/src/views/material\MaterialManageView.vue:419:    <template v-if="selfMode">
frontend/src/views/material\MaterialManageView.vue:431:      <n-grid :cols="4" :x-gap="12" :y-gap="12" responsive="screen" class="page-section material-card-grid">
frontend/src/views/material\MaterialManageView.vue:447:              <span>{{ card.record?.fileName || '尚未上传材料' }}</span>

$ rg -n 'isStudentMode|certificate-card|证书编号待生成|有效期至|我的证书' frontend/src/views/certificate/CertificateManageView.vue
frontend/src/views/certificate\CertificateManageView.vue:69:const isStudentMode = computed(() => userStore.roles.includes('STUDENT') && canView.value)
frontend/src/views/certificate\CertificateManageView.vue:70:const pageTitle = computed(() => isStudentMode.value ? '我的证书' : '证书管理')
frontend/src/views/certificate\CertificateManageView.vue:409:    <template v-if="isStudentMode">
frontend/src/views/certificate\CertificateManageView.vue:414:            <n-card :bordered="false" class="certificate-card">
frontend/src/views/certificate\CertificateManageView.vue:421:              <div class="certificate-card__number mono tabular-nums">{{ item.certNo || '证书编号待生成' }}</div>
frontend/src/views/certificate\CertificateManageView.vue:425:                  <span>有效期至</span>
```

W4 时间列检查：
```
$ rg -n "key: '(createdAt|updatedAt|operateTime|startedAt|finishedAt|.*ReviewTime|submitTime)'" frontend/src/views
frontend/src/views\exchange\ExchangeExportView.vue:75:  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\exchange\ExchangeImportView.vue:64:  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\system\SystemAuditView.vue:104:  { title: '更新时间', key: 'updatedAt', minWidth: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.updatedAt)) },
frontend/src/views\system\SystemAuditView.vue:119:  { title: '时间', key: 'operateTime', minWidth: 168, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\system\SystemAuditView.vue:149:  { title: '开始', key: 'startedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.startedAt)) },
frontend/src/views\system\SystemAuditView.vue:150:  { title: '完成', key: 'finishedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.finishedAt)) },
```

Build-only 验证：
```
$ npm --prefix frontend run type-check
> teacher-cert-platform-frontend@1.0.0 type-check
> vue-tsc --noEmit

$ npm --prefix frontend run build
> teacher-cert-platform-frontend@1.0.0 build
> vite build
✓ 4874 modules transformed.
✓ built in 7.79s
(!) Some chunks are larger than 500 kB after minification.
```
- 下一步：单分支单提交后等待 Claude 按 Phase33 gate ①-⑥复核。

## [2026-07-03] Phase 32 复核通过（Claude · frontend-quality-plan §5）✅ — 系统域 + 表单/详情/审核体验
- 做了什么：复核 `feature/phase32-form-detail-review` 单提交 `357b6be`（14 文件）。核系统域 6 页两栏化 + P4/P5/P6 接入矩阵；具体验 Org/Security 抽屉 `n-grid :cols="2"`、审计 old/new→statusLabel+StatusTag、学生ID help、权限树改可读名；W3 黑名单系统域重改后复跑仍空；`vue-tsc`+`vite build ✓ 7.74s`。活体 SYS_ADMIN 走查 10 个系统域接口（dict/region/subject/college/user/role/permission-tree/param/audit/backup）全 HTTP200 code=0、零 403，权限树含可读 `name:系统用户管理`。
- 结论：**PASS**（一轮·0 修补）。系统域主从两栏、全站抽屉 P4 双列分组固定底、查看/编辑分离(P5)、审核统一(P6)、审计中文、权限树可读名(W3 正向改进)。DataPanel 仅增展示型 props(rowProps/maxHeight/defaultExpandAll)。
- Minor（非阻断）：证书页无独立「查看」动作（生命周期动作页），P5 不适用；codex 将 P4 用于证书抽屉、P5 仅用于学生/培养——工程正确，DEVLOG 顶部「偏差:无」措辞略欠精确但 Cross-Page 分节准确。
- 放行：PROGRESS Phase 32 置 ✅；合并 `main`（本地私有、无远程、不 push）；栈在跑供目验；下一步 Phase 33（含 W6 拆视频巨石组件，复核 wc -l 抽查）。

## [2026-07-03] Phase 32 待复核小结（系统域 + 表单/详情/审核体验）
- 做了什么：从最新 `main` 切出 `feature/phase32-form-detail-review`，执行 `docs/frontend-quality-plan.md` §4 Phase 32。纯前端展示层改造；未改后端、契约、迁移或 stores 逻辑；未新增依赖；未 push。
- 关键决策与理由：复用 Phase 30/31 的 `FilterBar/DataPanel/DetailPanel/ReviewDialog/StatusTag/formatDateTime/statusLabel`，系统域优先做可见结构件收敛；对 `DataPanel` 仅增加展示型 `rowProps/maxHeight/defaultExpandAll` 透传，支持主从选中行和权限树展开，不改变业务状态。
- 问题与解决：账号权限页原权限树会展示内部功能编码，本轮改为用户可读的功能名称/类型/路径，角色授权树仍用既有 id/code 计算授权。参数审计备份已在 Phase30 部分接入，本轮补学生数字 help 和 P4 抽屉。
- 与规格的偏差/疑问：无。按 build-only 要求未启动常驻前端/后端服务；6 角色起栈走查留待 Claude 复核。

### DictManageView — 数据字典
- 结构件：类型与字典项改为左窄列表卡 + 右详情/明细卡；类型和字典项筛选均接 `FilterBar`，表格接 `DataPanel`，选中类型用 `DetailPanel` 展示。
- P4：字典类型、字典项抽屉均 560 宽，`n-grid cols=2`，分“基本信息/补充说明/扩展信息”，长文本跨 2 列，footer 保留取消/保存与 saving loading。
- 肉眼变化：进入页面即可看到左侧字典类型卡头和总数徽标，右侧先显示当前类型详情，再显示该类型字典项列表。

### RegionManageView — 行政区划
- 结构件：下级区划列表改 `DataPanel`，右侧查询区改 `FilterBar + DetailPanel`；选中行高亮，省级/上级动作放入表格卡头。
- 特有项：保留级联选择和 6 位代码反查；路径节点继续用标签展示。
- 肉眼变化：区划页从“左裸表 + 右表单/描述”变成“左列表卡 + 右查询详情卡”。

### SubjectManageView — 任教学科库
- 结构件：学段/年度/分类/关键词筛选接 `FilterBar`；任教学科表接 `DataPanel`；右侧选择器预览和当前学科接 `DetailPanel`；导入错误表也接 `DataPanel`。
- 特有项：选择动作仍由按钮触发，避免点击行就写入最近学科记录。
- 肉眼变化：学科页右侧能直接看到当前学段、学科编码、分类、年度和类型，不再只显示单列描述。

### OrganizationManageView — 组织与专业
- 结构件：学院/专业两栏均接 `FilterBar + DataPanel + DetailPanel`；联动配置 tab 改为配置列表 + 详情两栏。
- P4：学院、专业、专业培养目标、培养目标联动配置抽屉均 560 宽，按基本信息/学科信息/状态设置/任教学段/实习地点等分组。
- 肉眼变化：左侧选学院、右侧看专业列表和专业详情，配置 tab 选中后能看到默认/允许学段与实习地点摘要。

### SecurityManageView — 账号权限
- 结构件：用户、角色、权限三 tab 均接 `DataPanel`；用户/角色筛选接 `FilterBar`；权限树通过 `DataPanel defaultExpandAll` 展开。
- 特有项：用户角色列继续用 `StatusTag` 组；功能权限表不再把内部编码作为主列展示；角色授权树显示功能名称。
- P4：用户、角色、角色授权、数据范围抽屉均 560 宽，分组双列，footer 保留取消/保存与 saving loading。
- 肉眼变化：账号页三个 tab 都有统一卡头、总数徽标和空态；新增/编辑用户表单分为基本信息、联系信息、账号设置。

### SystemAuditView — 参数审计备份
- 结构件：沿用参数/审计/备份三个 `FilterBar + DataPanel` 分区。
- 审计中文化：审计 old/new 状态继续 `statusLabel + StatusTag`，操作名走 `operationLabel`；参数更新时间、审计时间、备份开始/完成时间均 `formatDateTime`。
- 特有项：学院筛选为学院下拉；学生筛选占位为「学生ID（数字）」并增加“请填写数字编号，用于精确定位学生记录。”提示；参数/备份抽屉改 560 宽双列分组。
- 肉眼变化：审计筛选不再要求学院数字输入，学生数字条件有明确提示，状态列显示中文标签。

### Cross Page — P4/P5/P6
- P4 抽屉：学生详情宽度统一；培养编辑、材料上传、免考申请、证书生成/更正补 560 宽、分组标题与双列栅格；系统域所有表单抽屉旧宽度清空。
- P5 详情：学生、培养管理页查看继续使用 `DetailPanel` 只读抽屉，查看与编辑分离。
- P6 审核：学生、培养、材料、免考初审/复审入口继续统一 `ReviewDialog`，对象摘要 + 结论 + 意见流程一致。

### 自检证据（W2）
变更文件范围：
```
$ git diff --name-only
frontend/src/components/DataPanel.vue
frontend/src/views/certificate/CertificateManageView.vue
frontend/src/views/exemption/ExemptionManageView.vue
frontend/src/views/material/MaterialManageView.vue
frontend/src/views/student/StudentManageView.vue
frontend/src/views/system/DictManageView.vue
frontend/src/views/system/OrganizationManageView.vue
frontend/src/views/system/RegionManageView.vue
frontend/src/views/system/SecurityManageView.vue
frontend/src/views/system/SubjectManageView.vue
frontend/src/views/system/SystemAuditView.vue
frontend/src/views/training/TrainingManageView.vue
```

附录 A 黑名单输出为空：
```
$ rg -n --glob '*.vue' '(权限点|权限码|[a-z]+:[a-z]+:?[a-zA-Z]*[''"]?\s*(显隐|控制)|后端|接口|API|迁移|Flyway|RBAC|SPI|V-0[0-9])' frontend/src/views frontend/src/layouts frontend/src/components | rg -v '//|/\*|import|hasPerm|perms:|@/api'
<empty>
```

W4 时间列检查：
```
$ rg -n "key: '(createdAt|updatedAt|operateTime|startedAt|finishedAt|.*ReviewTime|submitTime)'" frontend/src/views
frontend/src/views\DashboardView.vue:129:  { title: '时间', key: 'createdAt', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.createdAt)) }
frontend/src/views\exchange\ExchangeImportView.vue:64:  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\video\VideoReviewView.vue:227:  { title: '提交时间', key: 'submitTime', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.submitTime)) },
frontend/src/views\exchange\ExchangeExportView.vue:75:  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\system\SystemAuditView.vue:104:  { title: '更新时间', key: 'updatedAt', minWidth: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.updatedAt)) },
frontend/src/views\system\SystemAuditView.vue:119:  { title: '时间', key: 'operateTime', minWidth: 168, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\system\SystemAuditView.vue:149:  { title: '开始', key: 'startedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.startedAt)) },
frontend/src/views\system\SystemAuditView.vue:150:  { title: '完成', key: 'finishedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.finishedAt)) },
```

纯前端范围输出为空：
```
$ git diff --name-only | rg "^(platform-|pom\.xml|docker-compose|.*db/migration|.*Controller|.*Service|.*Mapper|.*\.java)"
<empty>
```

系统页裸表格输出为空：
```
$ rg -n 'n-data-table' frontend/src/views/system
<empty>
```

旧抽屉宽度输出为空：
```
$ rg -n 'n-drawer[^\n]*:width="(420|460|480|520|580|620|720)"' frontend/src/views
<empty>
```

P5/P6 命中：
```
$ rg -n 'ReviewDialog|DetailPanel' frontend/src/views/student/StudentManageView.vue frontend/src/views/training/TrainingManageView.vue frontend/src/views/material/MaterialManageView.vue frontend/src/views/exemption/ExemptionManageView.vue frontend/src/views/certificate/CertificateManageView.vue
frontend/src/views/material/MaterialManageView.vue:16:import ReviewDialog from '@/components/ReviewDialog.vue'
frontend/src/views/material/MaterialManageView.vue:475:    <ReviewDialog
frontend/src/views/exemption/ExemptionManageView.vue:16:import ReviewDialog from '@/components/ReviewDialog.vue'
frontend/src/views/exemption/ExemptionManageView.vue:546:    <ReviewDialog
frontend/src/views/student/StudentManageView.vue:15:import DetailPanel from '@/components/DetailPanel.vue'
frontend/src/views/student/StudentManageView.vue:18:import ReviewDialog from '@/components/ReviewDialog.vue'
frontend/src/views/student/StudentManageView.vue:426:        <DetailPanel v-if="selectedStudent" :items="detailItems" :columns="2" />
frontend/src/views/student/StudentManageView.vue:430:    <ReviewDialog
frontend/src/views/training/TrainingManageView.vue:12:import DetailPanel from '@/components/DetailPanel.vue'
frontend/src/views/training/TrainingManageView.vue:15:import ReviewDialog from '@/components/ReviewDialog.vue'
frontend/src/views/training/TrainingManageView.vue:566:        <DetailPanel v-if="selectedProfile" :items="detailItems" :columns="2" />
frontend/src/views/training/TrainingManageView.vue:570:    <ReviewDialog
```

Build-only 验证：
```
$ npm --prefix frontend run type-check
> teacher-cert-platform-frontend@1.0.0 type-check
> vue-tsc --noEmit

$ npm --prefix frontend run build
> teacher-cert-platform-frontend@1.0.0 build
> vite build
✓ 4864 modules transformed.
✓ built in 7.43s
(!) Some chunks are larger than 500 kB after minification.
```
- 下一步：单分支单提交后等待 Claude 按 Phase 32 gate ①-⑥逐页复核。

## [2026-07-03] Phase 31 复核通过（Claude · frontend-quality-plan §5）✅ — 业务域列表页铺开
- 做了什么：复核 `feature/phase31-list-rollout` 单提交 `652c0f1`（14 文件）。逐页核结构件接入矩阵；W4 grep（日期列全走 formatter、无裸状态码）；W3 附录 A 黑名单复跑空；导出学院下拉/通知 n-list/材料免考文件列逐项验；`vue-tsc`+`vite build` 读输出全绿。**首次活体走查**：captcha 解码登录 3 角色（SYS_ADMIN/ACADEMIC_ADMIN/COLLEGE_CLERK），6 个列表接口全 HTTP200 code=0、零 403、数据范围正确（教务员 student=9<12、college=1<2）。
- 结论：**PASS**（一轮·0 修补）。12 页结构件全接入、格式化/状态中文全覆盖、各页特有项落实；DEVLOG 逐页（W9）+ 证据（W2）+ 诚实披露 2 处规格差异。
- Minor（非阻断记录备查）：① 批次状态用页面本地 map 非全局 statusLabel（用户可见仍中文，决策合理）；② 视频「分差列红高亮」现无契约字段未实现（非回归，已披露）。
- 放行：PROGRESS Phase 31 置 ✅；合并 `main`（本地私有、无远程、不 push）；栈在跑供用户目验；下一步 Phase 32。为活体走查把 3 个测试账号密码归一化为 ChangeMe123!。

## [2026-07-03] Phase 31 待复核小结（业务域列表页铺开）
- 做了什么：从最新 `main` 切出 `feature/phase31-list-rollout`，执行 `docs/frontend-quality-plan.md` §4 Phase 31。12 个业务列表页逐页套用 P1/P2/P3/P7/P8 与附录 B 格式化；纯前端展示层改造，未改后端、契约、迁移或 stores 逻辑；未新增依赖。
- 关键决策与理由：复用 Phase 30 的 `FilterBar/DataPanel/EmptyState/DetailPanel/ReviewDialog/StatCard/StatusTag` 与 `format/statusLabels`，优先收敛列表页结构和状态/时间/文件格式化；通知页按阶段要求从表格改为列表交互，导出页只读取学院列表并继续提交既有 `collegeId`。
- 问题与解决：批次状态 `FAILED` 在导入/导出语境应显示“失败”，未改全局状态字典，改为页面本地批次状态文案兜底，避免影响学生/材料等“不合格”语境。视频页当前没有分差展示列，本轮不新增契约字段，只保留评分逻辑和既有显示。
- 与规格的偏差/疑问：无。按 build-only 要求未启动常驻前端/后端服务，6 角色无 403/空壳留待 Claude 起栈复核；本轮记录静态权限/页面覆盖口径。未 push。

### StudentManageView — 学生列表
- 结构件：沿用 Phase 30 的 `FilterBar + DataPanel + DetailPanel + ReviewDialog`，状态列继续 `StatusTag + statusLabel`，操作列继续收敛。
- 特有项：空态动作从“新增学生”改为“去导入”，跳转导入中心；保留顶部新增入口。
- 肉眼变化：无数据时主表空态直接引导批量导入，而不是让用户单条新增。
- 自检证据：`frontend/src/views/student/StudentManageView.vue` 出现在本轮 diff；见下方 `git diff --name-only` 与 build-only 证据。

### TrainingManageView — 专业培养信息
- 结构件：新增 `FilterBar + DataPanel + DetailPanel + ReviewDialog`，主表卡头带总数，筛选项带业务标签。
- 特有项：学段、培养目标、实习地点、学历层次、面试方式等联动字段在列表/详情中用字典中文展示；状态列走 `statusLabel`。
- 肉眼变化：培养详情从旧描述表变为双列只读详情面板，审核弹窗与学生页一致。
- 自检证据：`frontend/src/views/training/TrainingManageView.vue` 出现在本轮 diff；`type-check` 已验证 `DetailPanel`/`ReviewDialog` 参数类型。

### MaterialManageView — 过程性材料
- 结构件：新增 `FilterBar + DataPanel + StatCard + ReviewDialog`，主操作进入 DataPanel 卡头。
- 特有项：材料类别中文兜底；文件列合并“文件名 + formatFileSize”；预览按钮增加 `EyeOutline` 图标；状态列走 `statusLabel`。
- 肉眼变化：文件信息不再拆成两列，用户能在同一列看到文件名和 KB/MB 大小。
- 自检证据：`frontend/src/views/material/MaterialManageView.vue` 出现在本轮 diff；`formatFileSize` 由 type-check/build 验证引用。

### ExemptionManageView — 免考管理
- 结构件：新增 `FilterBar + DataPanel + StatCard + ReviewDialog`，主表卡化。
- 特有项：学段/科目/依据用后端 label 与字典中文兜底；佐证列显示文件徽标、首个文件名与大小；预览按钮加 icon。
- 肉眼变化：佐证列从“n 份”变为带徽标的文件摘要，更容易识别附件状态。
- 自检证据：`frontend/src/views/exemption/ExemptionManageView.vue` 出现在本轮 diff；`formatFileSize` 与状态映射通过 type-check。

### VideoReviewView — 视频评审列表 tab
- 结构件：评审管理、我的评审、评审组三个列表均接 `DataPanel`；评审管理筛选接 `FilterBar`；统计块改 `StatCard`。
- 特有项：`RETURNED` 通过 `statusLabel` 显示“已退回”；我的评审提交时间走 `formatDateTime`；评审组状态中文映射。分差列本页现状无契约字段，本轮未新增字段。
- 肉眼变化：视频三个 tab 都有统一卡头、总数徽标、空态和刷新入口。
- 自检证据：W4 时间列 grep 中包含 `VideoReviewView.vue:227` 的 `formatDateTime(row.submitTime)`。

### TestResultManageView — 测试结果
- 结构件：新增 `FilterBar + DataPanel`，导入动作进入 DataPanel 卡头。
- 特有项：成绩列使用 `mono numeric tabular-nums` 右对齐；结论和确认状态用 `StatusTag + statusLabel`。
- 肉眼变化：成绩文本在表格中按数字列对齐，但仍保持字符串显示，不做数值化。
- 自检证据：`frontend/src/views/test-result/TestResultManageView.vue` 出现在本轮 diff；type-check 通过。

### CertificateManageView — 证书管理
- 结构件：新增 `FilterBar + DataPanel`，生成证书动作进入 DataPanel 卡头。
- 特有项：证书号保留 mono；签发日期、有效期用 `formatDate`；生命周期状态走 `statusLabel`。
- 肉眼变化：证书日期从原始文本收敛为 `YYYY-MM-DD`，生命周期状态统一中文标签。
- 自检证据：`frontend/src/views/certificate/CertificateManageView.vue` 出现在本轮 diff；build 通过。

### CertificateIssueView — 证书签发队列
- 结构件：新增 `FilterBar + DataPanel`，操作列改 `renderTableActions`。
- 特有项：证书号保留 mono；签发日期、有效期用 `formatDate`；状态映射中文。
- 肉眼变化：签发队列从裸表格变为带卡头和空态的队列表。
- 自检证据：`frontend/src/views/certificate/CertificateIssueView.vue` 出现在本轮 diff；build 通过。

### ExchangeImportView — 导入中心
- 结构件：上传控制区接 `FilterBar`；成功预览、异常明细、批次记录三张表接 `DataPanel`。
- 特有项：批次时间走 `formatDateTime`；批次类型、策略、状态中文化；异常表卡化。
- 肉眼变化：异常明细 tab 有卡头、总数徽标和空态，不再是裸错误表。
- 自检证据：W4 时间列 grep 中包含 `ExchangeImportView.vue:64` 的 `formatDateTime(row.operateTime)`。

### ExchangeExportView — 导出中心
- 结构件：导出筛选区接 `FilterBar`，更多筛选折叠；导出批次接 `DataPanel`。
- 特有项：学院筛选由“学院ID”输入改为 `listColleges` 下拉，仍传 `query.collegeId`；导出批次时间格式化，导出项/状态中文化。
- 肉眼变化：用户按学院名称筛选导出，不需要手填学院 ID。
- 自检证据：`rg` 黑名单未检出“学院ID”；W4 时间列 grep 中包含 `ExchangeExportView.vue:75`。

### StatsReportView — 统计报表
- 结构件：筛选区接 `FilterBar`；统计汇总表和钻取明细表接 `DataPanel`。
- 特有项：图表继续使用 `ChartBox`，保持 Phase 30 附录 C 色板/tooltip/grid 默认；表格卡化。
- 肉眼变化：统计页图表下方的数据表也有卡头、总数徽标、刷新/空态。
- 自检证据：`frontend/src/views/stats/StatsReportView.vue` 出现在本轮 diff；build 通过。

### NoticeCenterView — 通知中心
- 结构件：筛选区接 `FilterBar`；主内容从 `n-data-table` 改为 `n-list`；空态接 `EmptyState`。
- 特有项：未读圆点、未读标题加粗、时间灰色；点击通知行会标记已读并打开抽屉查看全文；“全部已读”保留。
- 肉眼变化：通知中心变为消息列表形态，用户可直接点行阅读正文。
- 自检证据：`frontend/src/views/notice/NoticeCenterView.vue` 出现在本轮 diff；build 通过。

### 6 角色静态走查矩阵（build-only 口径）
| 角色 | 列表入口覆盖 | 空壳/403 静态口径 |
|---|---|---|
| STUDENT | 学生自助、材料/免考/视频上传、通知 | 本轮未新增 API 调用；导入/导出/统计等仍由路由权限守卫 |
| COLLEGE_CLERK | 学生/培养/材料/免考初审相关列表 | 辅助学生/学院下拉沿用既有权限判断；无 stores/契约变更 |
| COLLEGE_AUDITOR | 学生/培养/材料/免考复审、视频管理 | 视频候选/评审组加载条件沿用既有 `video:*` 权限 |
| REVIEW_TEACHER | 视频“我的评审”、通知 | 视频任务列表只在 `canScore` 下加载；无管理列表强制加载 |
| ACADEMIC_ADMIN | 证书、签发、导入导出、统计 | 导出学院下拉读取既有学院列表；导出仍提交 `collegeId` |
| SYS_ADMIN | 系统外壳可访问授权业务列表 | 本轮未改路由/权限矩阵；无后端范围改动 |

### 自检证据（W2）
变更文件范围：
```
$ git diff --name-only
frontend/src/views/certificate/CertificateIssueView.vue
frontend/src/views/certificate/CertificateManageView.vue
frontend/src/views/exchange/ExchangeExportView.vue
frontend/src/views/exchange/ExchangeImportView.vue
frontend/src/views/exemption/ExemptionManageView.vue
frontend/src/views/material/MaterialManageView.vue
frontend/src/views/notice/NoticeCenterView.vue
frontend/src/views/stats/StatsReportView.vue
frontend/src/views/student/StudentManageView.vue
frontend/src/views/test-result/TestResultManageView.vue
frontend/src/views/training/TrainingManageView.vue
frontend/src/views/video/VideoReviewView.vue
```

附录 A 黑名单输出为空：
```
$ rg -n --glob '*.vue' '(权限点|权限码|[a-z]+:[a-z]+:?[a-zA-Z]*[''"]?\s*(显隐|控制)|后端|接口|API|迁移|Flyway|RBAC|SPI|V-0[0-9])' frontend/src/views frontend/src/layouts frontend/src/components | rg -v '//|/\*|import|hasPerm|perms:|@/api'
<empty>
```

W4 时间列检查：
```
$ rg -n "key: '(createdAt|updatedAt|operateTime|startedAt|finishedAt|.*ReviewTime|submitTime)'" frontend/src/views
frontend/src/views\video\VideoReviewView.vue:227:  { title: '提交时间', key: 'submitTime', minWidth: 160, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.submitTime)) },
frontend/src/views\exchange\ExchangeImportView.vue:64:  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\DashboardView.vue:129:  { title: '时间', key: 'createdAt', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.createdAt)) }
frontend/src/views\exchange\ExchangeExportView.vue:75:  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\system\SystemAuditView.vue:104:  { title: '更新时间', key: 'updatedAt', minWidth: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.updatedAt)) },
frontend/src/views\system\SystemAuditView.vue:119:  { title: '时间', key: 'operateTime', minWidth: 168, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\system\SystemAuditView.vue:149:  { title: '开始', key: 'startedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.startedAt)) },
frontend/src/views\system\SystemAuditView.vue:150:  { title: '完成', key: 'finishedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.finishedAt)) },
```

纯前端范围输出为空：
```
$ git diff --name-only | rg "^(platform-|pom\.xml|docker-compose|.*db/migration|.*Controller|.*Service|.*Mapper|.*\.java)"
<empty>
```

Build-only 验证：
```
$ npm --prefix frontend run type-check
> teacher-cert-platform-frontend@1.0.0 type-check
> vue-tsc --noEmit

$ npm --prefix frontend run build
> teacher-cert-platform-frontend@1.0.0 build
> vite build
✓ 4864 modules transformed.
✓ built in 8.31s
(!) Some chunks are larger than 500 kB after minification.
```
- 下一步：单分支单提交后等待 Claude 按 Phase 31 gate 逐页复核与 6 角色起栈走查。

## [2026-07-02] Phase 30 复核通过（Claude · frontend-quality-plan §5）✅ — 体验基建 + 全局观感
- 做了什么：复核 `feature/phase30-ux-foundation` 单提交 `325c88d`（35 文件）。逐项核 gate ①-⑥：读 6 组件/tokens/format/statusLabels/ChartBox v2/外壳/示范页 diff；复跑附录 A 黑名单 grep（输出为空）；`npm install`+`vue-tsc`+`vite build` 读输出全绿。
- 结论：**PASS**（一轮+1 处复核修补）。色板与验证值逐字节一致、单系列柱规则/图例规则/柱顶圆角符合附录 C；Dashboard 指标改后端 total+unread-count（W7 闭环）、类目去 `\n`；学生列表 grade 解绑学年（B5 闭环）；审计学院下拉提前完成（原 P32 项）。DEVLOG 首次完全符合 W9/W10（按页分节+肉眼变化+诚实报告 grep.exe 故障）。
- 复核修补（Minor，随分支合入）：ReviewDialog 增「退回/不通过必填意见」校验+表单 error 反馈（P6 规格缺口），修补后 type-check/build 复跑绿。
- 放行：PROGRESS Phase 30 置 ✅；合并 `main`（本地私有、无远程、不 push）；重启验收栈供目验；下一步 Phase 31。

## [2026-07-02] Phase 30 待复核小结（体验基建 + 全局观感）
- 做了什么：从 `main` 切出 `feature/phase30-ux-foundation`，执行 `docs/frontend-quality-plan.md` §4 Phase 30（T-190~T-195）。纯前端展示与体验结构调整；未改后端、契约、权限、状态机或迁移；新依赖仅 `@vicons/ionicons5`。
- 关键决策与理由：先落 `FilterBar/DataPanel/EmptyState/TableSkeleton/DetailPanel/ReviewDialog` 与 `format/statusLabels`，再在学生列表、参数审计备份两个示范页接入，避免继续在页面内复制筛选、表格卡头、空态、详情和审核结构。ChartBox 直接按附录 C 固定色板与单系列柱规则，避免页面裸配置漂移。
- 问题与解决：本机 MSYS/Git `grep.exe` 运行时报 Win32 error 5，无法直接执行附录 A 原始管道；本轮用 `git grep`/`rg` 跑同正则并贴空输出，复核环境可重跑附录原命令。为满足 W4，顺手把通知、导入、导出 3 个既有时间列改为 `formatDateTime`。
- 与规格的偏差/疑问：无。未启动常驻前端/后端服务；未 push。

### 通用组件与外壳 — T-190/T-191/T-192
- 新增结构件：`FilterBar.vue`（内联 label、查询/重置、更多筛选）、`DataPanel.vue`（卡头标题+总数徽标+刷新/主操作+分页总数）、`EmptyState.vue`、`TableSkeleton.vue`、`DetailPanel.vue`、`ReviewDialog.vue`。
- 工具与映射：`formatDateTime/formatDate/formatFileSize`；`STATUS_LABELS/OPERATION_LABELS/statusLabel/operationLabel`；`.tabular-nums`。
- 外壳肉眼变化：侧栏菜单从单字色块变为 ionicons 图标；顶栏通知为铃铛 icon+角标；学年选择器只能选固定年份；浏览器标题随路由变更；新增青绿“师”字 favicon。

### LoginView — T-193
- 结构/文案：登录页 chips 改为「全流程线上办理 / 审核进度透明 / 证书全程可溯」，品牌副标题保持中文校名。
- 肉眼变化：登录页不再出现开发建设术语，左侧价值标签面向办理流程。

### StudentManageView — 示范页
- 新增结构件：`FilterBar`、`DataPanel`、`EmptyState`、`DetailPanel`、`ReviewDialog`。
- 业务展示：状态列走 `StatusTag + statusLabel`；表格卡头显示记录数；查看详情为只读 2 列说明面板；审核弹窗统一对象摘要→结论→意见。
- T-195：年级筛选独立为空，新增/编辑表单年级不再默认取全局学年。
- 肉眼变化：学生列表出现带标签筛选区、白色表格卡头与总数徽标、空态引导和独立详情面板。

### SystemAuditView — 示范页
- 新增结构件：参数、审计、备份三个分区分别接 `FilterBar + DataPanel`，分页显示总数并保留刷新/主操作。
- 展示修正：参数更新时间、审计时间、备份开始/完成时间全部 `formatDateTime`；审计操作走 `operationLabel`；旧/新状态走 `statusLabel + StatusTag`。
- 筛选修正：审计学院筛选由文本输入改学院下拉；学生筛选占位改「学生ID（数字）」并放入更多筛选。
- 肉眼变化：参数/审计/备份不再是裸表格，审计状态从 `SECOND_REVIEW` 这类码变为中文标签。

### DashboardView — T-194/T-195
- 图表：类目标签去掉 `\n` 拼接；图表依赖 `ChartBox` 默认 tooltip/grid/色板/单系列规则。
- 数据正确性：指标不再使用 `records.length`；通知数量来自 `PageResult.total`，未读数量来自 `/notice/unread-count`，统计汇总来自统计接口 rows。
- 肉眼变化：工作台指标卡有图标圆底，通知时间显示为 `YYYY-MM-DD HH:mm`，柱图为青绿色单系列并有 tooltip。

### StatsReportView — T-194
- 图表：类目标签去掉 `\n` 拼接，继续通过 `ChartBox` 渲染。
- 指标卡：接入 icon 版本 `StatCard`。
- 肉眼变化：统计页顶部指标卡出现图标圆底，图表不再使用换行类目标签。

### NoticeCenterView / ExchangeImportView / ExchangeExportView — W4 时间列
- 展示修正：3 个页面的 `createdAt/operateTime` 表格列改为 `formatDateTime`。
- 肉眼变化：通知、导入批次、导出批次的时间从 ISO 串变为 `YYYY-MM-DD HH:mm`。

### DictManageView / OrganizationManageView / SecurityManageView / VideoReviewView — W3 文案清理
- 文案：移除用户可见「后端」「权限点」等术语，改为“系统校验”“功能权限”等用户视角文本。
- 肉眼变化：系统管理和视频页不再出现开发/内部术语。

### 6 角色静态走查矩阵（build-only）
| 角色 | 学生列表 | 参数审计备份 | 工作台/统计 |
|---|---|---|---|
| STUDENT | 可按本人范围进入学生信息相关入口；年级筛选不受全局学年强制过滤 | 无系统治理分区 | 工作台可见本人进度与通知 |
| COLLEGE_CLERK | 可见学院范围学生列表与初审入口 | 仅加载有权审计分区时不触发参数/备份分区 | 工作台可见学院初审关注项 |
| COLLEGE_AUDITOR | 可见学院范围学生列表与复审入口 | 仅加载有权审计分区时不触发参数/备份分区 | 工作台可见复审/视频关注项 |
| REVIEW_TEACHER | 无学生列表管理入口 | 无系统治理分区 | 工作台可见视频评审关注项 |
| ACADEMIC_ADMIN | 可见校级学生列表 | 可见参数与审计分区，不加载备份分区 | 工作台与统计可见校级口径 |
| SYS_ADMIN | 按现有权限可见系统治理分区 | 可见参数/审计/备份分区 | 工作台可见系统治理关注项 |

### 自检证据（W2）
组件/工具存在：
```
$ rg --files frontend/src/components frontend/src/utils frontend/src/constants frontend/public | rg "(FilterBar|DataPanel|EmptyState|TableSkeleton|DetailPanel|ReviewDialog|format\\.ts|statusLabels\\.ts|favicon\\.svg)"
frontend/public\favicon.svg
frontend/src/utils\format.ts
frontend/src/constants\statusLabels.ts
frontend/src/components\FilterBar.vue
frontend/src/components\EmptyState.vue
frontend/src/components\DetailPanel.vue
frontend/src/components\DataPanel.vue
frontend/src/components\ReviewDialog.vue
frontend/src/components\TableSkeleton.vue
```

附录 A 黑名单同正则输出为空：
```
$ git grep -n -E '(权限点|权限码|[a-z]+:[a-z]+:?[a-zA-Z]*['"'"'\\"]?\\s*(显隐|控制)|后端|接口|API|迁移|Flyway|RBAC|SPI|V-0[0-9])' -- frontend/src/views frontend/src/layouts frontend/src/components '*.vue' | rg -v '//|/\\*|import|hasPerm|perms:|@/api'
<empty>
```

W4 时间列检查：
```
$ rg -n "key: '(createdAt|updatedAt|operateTime|startedAt|finishedAt|.*ReviewTime)'" frontend/src/views
frontend/src/views\DashboardView.vue:129:  { title: '时间', key: 'createdAt', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.createdAt)) }
frontend/src/views\exchange\ExchangeImportView.vue:62:  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\exchange\ExchangeExportView.vue:70:  { title: '时间', key: 'operateTime', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\notice\NoticeCenterView.vue:54:  { title: '时间', key: 'createdAt', width: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.createdAt)) },
frontend/src/views\system\SystemAuditView.vue:104:  { title: '更新时间', key: 'updatedAt', minWidth: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.updatedAt)) },
frontend/src/views\system\SystemAuditView.vue:119:  { title: '时间', key: 'operateTime', minWidth: 168, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
frontend/src/views\system\SystemAuditView.vue:149:  { title: '开始', key: 'startedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.startedAt)) },
frontend/src/views\system\SystemAuditView.vue:150:  { title: '完成', key: 'finishedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.finishedAt)) },
```

Dashboard 禁用 `records.length`：
```
$ rg -n "records\\.length" frontend/src/views/DashboardView.vue
<empty>
```

Dashboard/统计页类目换行拼接：
```
$ rg -n "\\n" frontend/src/views/DashboardView.vue frontend/src/views/stats/StatsReportView.vue
<empty>
```

纯前端范围：
```
$ git diff --name-only | rg "^(platform-|pom\\.xml|docker-compose|.*db/migration|.*Controller|.*Service|.*Mapper|.*\\.java)"
<empty>
```

新增依赖范围：
```
$ git diff -- frontend/package.json
+    "@vicons/ionicons5": "^0.13.0",
```

Build-only 验证：
```
$ npm --prefix frontend run type-check
> teacher-cert-platform-frontend@1.0.0 type-check
> vue-tsc --noEmit

$ npm --prefix frontend run build
> teacher-cert-platform-frontend@1.0.0 build
> vite build
✓ built in 8.14s
(!) Some chunks are larger than 500 kB after minification.
```
- 下一步：提交本分支单提交后等待 Claude 按 Phase 30 gate ①-⑥ 复核。

## [2026-06-19] Phase 28 复核通过（Claude · REVIEW-GATE）✅ — UI 视觉重做·明亮圆润青绿 SaaS
- 做了什么：复核 `feature/phase28-ui-bright-rounded` 单提交 `004ae10`（18 前端文件）。读 theme/naive.ts、global.css、tokens.ts + 确认新方向落地、**无 api/router/stores/后端/迁移改动**；前端 build-only gate。
- 结论：**PASS**（一轮，客观门槛）。`type-check` 无错 + `built in 6.18s`。核对：① 新方向落地——naive.ts primaryColor #0d9488(青绿)、borderRadius 控件 10px/小 8px；global.css --brand #0d9488、--brand-soft #f0fdfa、--page-bg #f6f8f7(暖)、--radius-card 14px/--radius-tag 999px(pill)、阴影更柔更大；tokens.ts 图表色板首色 #14b8a6。② 导航青绿 pill、登录青绿分栏、卡片/表格圆角化、图表圆角柱。③ 与 Phase 26 蓝/6px 对比：主色蓝→青绿、圆角翻倍、留白加宽，肉眼明显不同。④ diff 无 src/api/router/stores/platform-*/migration——纯视觉。主观观感交用户运行栈终验。
- 放行：PROGRESS Phase 28 置 ✅；合并 `main`（本地私有、无远程、不 push）。合并后重启 vite 供用户硬刷新视觉终验。

## [2026-06-19] Phase 28 待复核小结（UI 视觉重做·明亮圆润青绿 SaaS）
- 做了什么：从最新 `main` 切出 `feature/phase28-ui-bright-rounded`，按新版 `frontend/DESIGN.md` 完成 T-175~T-181。纯前端视觉/排版层重做，未改路由、接口、权限、状态机、业务逻辑、后端代码或 Flyway 迁移。
- T-175/T-176：`frontend/src/theme/global.css` 改为青绿 `#0d9488`、暖中性、`--page-bg:#f6f8f7`、控件 10px、卡片/表格 14px、宽松间距和柔和阴影；`theme/naive.ts` 重写 common/Button/DataTable/Menu/Tag/Card/Input/Form/Select/Pagination/Empty；`MainLayout` 改 248 侧栏、青绿圆角 pill 选中态（无左色条）、顶栏 20px 标题、青绿通知角标和头像、内容区 28px 留白。
- T-177/T-178：全局 DataTable 卡片化、浅青表头、约 50px 行高、青绿 hover、分页右对齐；筛选条等高和宽松间距统一。StatusTag 维持全枚举 soft 但改 pill；StatCard 从左色条改青绿色点 + 32px 大数值；卡片、表单只读态、空态、弹层圆角/阴影统一。
- T-179/T-180：登录页重做为明亮青绿品牌分栏 + 圆角白卡表单，保留 captcha/login/change-password 流程；`ChartBox` 保留 Phase25 轴修复并新增明快图表色板、柱图顶圆角、`barMaxWidth`、圆角 tooltip 与浅色轴线。
- 关键决策与理由：本轮不新增 UI 框架，继续通过 `theme/global.css` + `theme/naive.ts` + `theme/tokens.ts` 收口视觉，页面层只做必要局部间距/圆角修正，确保与 Phase 26 的旧蓝小圆角肉眼明显不同且行为中性。
- 问题与解决：`ChartBox` 需要在不覆盖页面自定义 tooltip/series 的前提下注入圆角柱和 tooltip 样式，采用 normalize 合并；局部系统页仍有 12px 间距和 6/8px 圆角，已改为 token。
- 与规格的偏差/疑问：无。按用户硬约束执行 build-only；未启动前端/后端常驻服务；未自行置 ✅。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）。硬编码色自查：页面/组件/布局层无 `#`/`rgba` 色值，色值集中在 `frontend/src/theme/*`。
- 下一步：`PROGRESS.md` 已置 **Phase 28 待复核**，等待 Claude 复核；本地私有无 remote、不 push。

## [2026-06-18] Phase 26 复核通过（Claude · REVIEW-GATE）✅ — UI 设计提升·按 DESIGN.md
- 做了什么：复核 `feature/phase26-ui-uplift` 单提交 `4350304`（33 前端文件）。读 theme/naive.ts、tokens.ts、utils/tableActions.ts + 抽查视图 diff 行为中性 + 确认 **无 api/router/stores/后端/迁移改动**；前端 build-only gate。
- 结论：**PASS**（一轮，客观门槛）。`type-check` 无错 + `built in 7.15s`。核对：① 主题 token 收口——`theme/naive.ts` GlobalThemeOverrides(primaryColor #1d4ed8/radii 6·4px/Button·DataTable·Menu·Tag·Card·Input)、`tokens.ts` 8 色低饱和图表色板+轴色、`global.css`(+322) CSS 变量；硬编码色收敛至 theme/*。② 操作列 `renderTableActions` ≤3 内联/>3 收「更多」popover；StatCard/StatusTag/PageContainer/登录分栏/外壳导航按 DESIGN.md 重排。③ 抽查 StudentManageView 等 diff 仅列宽/类名/操作列 helper，handler/请求/权限判断未变；diff 名单无 src/api、src/router、src/stores、platform-*、migration。主观视觉质量交用户在运行栈验收。
- 放行：PROGRESS Phase 26 置 ✅；合并 `main`（本地私有、无远程、不 push）。运行 vite 合并后热更新供用户视觉验收。**收官后重构(WP-A~D + Phase19~24) + 验收修复(Phase25/27) + UI 提升(Phase26) 全部完成。**

## [2026-06-18] Phase 26 待复核小结（UI 设计提升·按 DESIGN.md）
- 做了什么：从最新 `main` 切出 `feature/phase26-ui-uplift`，按 `frontend/DESIGN.md` 完成 T-166~T-171。纯前端视觉/排版层提升，未改路由、接口、权限、状态机、后端代码或 Flyway 迁移。
- T-166：新增 `frontend/src/theme/naive.ts`，将 Naive `themeOverrides` 从 `App.vue` 移出并覆盖 common/Button/DataTable/Menu/Tag/Card/Input/Form/Select/Pagination/Empty；`global.css` 扩展低饱和政务蓝、灰阶、语义色、角色色、图表色板、字阶、间距、圆角、阴影 token；`ChartBox` 改用 `theme/tokens.ts`。
- T-167/T-168：`MainLayout` 调整侧栏 240/64、白底右边线、分组选中 soft 底 + 左 3px 色条、顶栏 60、内容留白 24；`PageContainer` 统一页头节奏。表格全局表头浅底 sticky、行 hover、分页右对齐；`.mono/.numeric` 表格内右对齐；新增 `renderTableActions`，多于 3 个动作收进“更多”，主行动作实心、次动作文字。
- T-169/T-170：`StatusTag` 扩展英文/中文状态枚举并统一 soft 方角标签；`StatCard` 改为语义 `tone`；表单只读/禁用态、卡片、空态、加载容器统一；登录页品牌分栏按 token 重做对比、层级和移动端布局，保留真实 captcha/login/change-pwd 流程。
- 关键决策与理由：色值集中在 `frontend/src/theme/*`，组件和页面只引用 token 或语义 `tone`，避免后续视觉漂移；操作列使用轻量工具收纳多动作，不引入新 UI 框架，不改变任何业务回调。
- 问题与解决：`StatCard` 原有 `color="#..."` 用法导致页面散落色值，本轮改为 `tone` 并逐页替换；Naive overrides 先用库内 `.d.ts` 核对变量名，`vue-tsc` 校验通过。
- 与规格的偏差/疑问：无。按用户硬约束执行 build-only；未启动前端/后端常驻服务；未做运行期截图验收，留给 Claude 复核。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）。硬编码色自查：页面/组件/布局层无 `#`/`rgba` 色值，色值集中在 `frontend/src/theme/*` token/overrides。
- 下一步：`PROGRESS.md` 已置 **Phase 26 待复核**，等待 Claude 复核，未自行置 ✅；本地私有无 remote、不 push。

## [2026-06-18] Phase 27 复核通过（Claude · REVIEW-GATE）✅ — 负责人评审教师列表端点（闭环 Phase 25 Major）
- 做了什么：复核 `feature/phase27-reviewer-list` 单提交 `716b7c9`（控制器/Service/Impl/VO + 前端 video.ts/VideoReviewView + Phase7 IT）。读端点/impl/IT/前端切换 + 确认 `selectEnabledByRoleAndCollege` 既存(SysUserMapper:42)、无新迁移；clean-room：重置 schema → `mvn verify`(全新 V1–V23) + 前端 type-check/build。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS、83/83**(Phase7 10→11)；Flyway v23(无新迁移)；前端绿。核对：① `GET /api/video/reviewer-candidates` `@pms.has('video:assign')`；impl `reviewerCandidateCollegeIds` 解析 video:assign 数据范围(NONE→403/allSchool→全校/COLLEGE→本院)，列 ENABLED+REVIEW_TEACHER，按 id dedup；VO 仅 id/realName/workNo 无敏感。② 前端按人选择器 `canAssign ? listReviewerCandidates() : null`，负责人(video:assign)现可加载候选，移除对 `/system/user`(system:user:manage) 的依赖。③ IT：负责人得本院 reviewerA/B、排除跨院 REVIEWER_D 与负责人自身；REVIEW_TEACHER 无 video:assign → 403；按人指派 2 人→评分 84/80→结算 82 PASS。
- 放行：PROGRESS Phase 27 置 ✅，Phase 25 Major 闭环；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 26(UI 提升)。

## [2026-06-18] Phase 27 待复核小结（负责人评审教师列表端点）
- 做了什么：从最新 `main` 切出 `feature/phase27-reviewer-list`，完成 T-172/T-173/T-174。新增 `GET /api/video/reviewer-candidates`，`@PreAuthorize("@pms.has('video:assign')")`，返回候选评审教师 `id/realName/workNo`；前端 `VideoReviewView` 的“按人指派”选择器改用该端点，不再调用 `/system/user`。
- 关键决策与理由：端点不新增权限点和迁移，复用 `video:assign`，避免放宽 `system:user:manage` 给学院负责人；候选范围由服务端 `dataScopeService.resolve("video:assign")` 解析，COLLEGE 只查授权学院，SCHOOL 查全校，学院来源不信任请求参数。候选过滤复用评审组成员校验口径：用户必须 ENABLED 且具 `REVIEW_TEACHER` 角色。
- 问题与解决：Phase 25 为消 403 把 `listUsers` 改为有 `system:user:manage` 才拉，导致负责人虽有 `video:assign` 但按人选择器为空。本轮补专用业务端点后，负责人/教务处按人指派候选恢复，按组指派和评审组 CRUD 不变。
- IT 覆盖：`Phase7VideoReviewIT` 新增 `reviewerCandidatesAreScopedAndSupportDirectAssignSettlement`，断言学院负责人候选仅含本院 `REVIEW_TEACHER`，不含跨院评审教师与非评审教师；评审教师访问候选端点返回 403；随后按端点返回的两名评审教师完成按人指派、两人评分、自动结算 PASS。
- 与规格的偏差/疑问：无。V1-V23 冻结，未新增 Flyway 迁移、权限点或 RBAC 种子；本地私有仓库无 remote、不 push。
- 测试：定向 `Phase7VideoReviewIT` **11/11** 通过；全量 `mvn -B -ntp verify` **83/83** 通过；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）。
- 下一步：`PROGRESS.md` 已置 **Phase 27 待复核**，等待 Claude 复核，未自行置 ✅。

## [2026-06-18] Phase 25 复核通过（Claude · REVIEW-GATE）✅ — 验收修复·403 与图表轴（含 1 Major backlog）
- 做了什么：复核 `feature/phase25-acceptance-fix` 单提交 `4b42ed5`（17 文件，纯前端）。读 SystemAuditView/ChartBox/VideoReviewView 核心 + 确认后端/迁移零改动；前端 build-only gate。
- 结论：**PASS**（一轮）。`type-check` 无错 + `built in 6.36s`。核对：① 组合页 403 修复——SystemAuditView 三段(param/audit/backup)各 `if(!canXxx)return` 守卫 + onMounted 仅 push 有权分区 + StatCard/tab/按钮 v-if + 无分区 n-empty；同模式扫到 Security/Organization/Video/Exchange/Certificate/Stats/Dashboard，「辅助下拉」亦按真实权限守卫，角色进组合页不再打无权 API。② ChartBox 统一轴(DESIGN.md §7)：xAxis interval:0+truncate+hideOverlap+alignWithLabel、yAxis minInterval:1+起点0，全图表生效。后端权限点/RBAC/迁移未动。
- **Major（入 backlog→Phase 27）**：VideoReviewView「按人指派」的评审教师候选源自 `listUsers`(`system:user:manage`)；本轮为消 403 改为有该权限才拉 → **学院负责人(video:assign 无 system:user:manage) 按人选择器为空**。属长期既存缺口（此前为 403、IT 直传 reviewerIds 绕过 UI），非本轮回归；按组指派可用。需后端新增 `video:assign` 门控的「列本院 REVIEW_TEACHER」端点，前端按人选择器改用之（Phase 27）。
- 放行：PROGRESS Phase 25 置 ✅；合并 `main`（本地私有、无远程、不 push）。运行中 vite 合并后热更新可回归 403 已消。下一步 Phase 26(UI) + Phase 27(评审教师列表端点)。

## [2026-06-18] Phase 25 待复核小结（验收修复·403 与图表轴）
- 做了什么：从本地最新 `main` 切出 `feature/phase25-acceptance-fix`，完成 T-163/T-164/T-165。纯前端修复，未改后端、接口、权限点、RBAC 种子或迁移。组合页不再只靠菜单 `hasAnyPerm`，而是在页面内部按分区真实权限点条件加载 API，并同步 tab/区块/按钮显隐与空态。
- T-163：`SystemAuditView` 参数/审计/备份分别按 `system:param:manage`、`audit:view`、`system:backup` 加载；`SecurityManageView` 用户/角色/权限树分别按 `system:user:manage`、`system:role:manage`、`system:perm:manage` 加载，组织范围下拉只在用户管理分区加载。扩展同类入口：`OrganizationManageView` 按 `college:manage`/`major:manage`，`VideoReviewView` 按 `video:upload/score/assign/arbitrate/confirm/play`，导入/导出按 exchange 子权限，证书/签发队列按 `cert:view/generate/issue`，统计与工作台按 `stats:view` 条件加载。
- T-164：`ChartBox` 统一 `frontend/DESIGN.md` §7 轴规则：xAxis `axisLabel.interval=0`、`width`、`overflow:'truncate'`、`hideOverlap`、`axisTick.alignWithLabel=true`；yAxis `min=0`、`minInterval=1`、浅色 splitLine；按类目标签长度自适应 rotate 与 grid bottom，并增加 `ResizeObserver` 配合 window resize。`StatsReportView` 与 `DashboardView` 去除页面内分散轴配置，仅保留业务数据和颜色。
- T-165 全角色自检矩阵（静态权限/API 口径）：STUDENT 可进本人/材料/免考/视频/通知，学生自助页不再因辅助学生列表触发无权 API；COLLEGE_CLERK 进参数审计备份只加载 `audit:view` 审计分区，不拉参数/备份；COLLEGE_AUDITOR 进视频页加载评审管理/评审组/任务所需分区，不拉无权系统用户列表；REVIEW_TEACHER 进视频页只加载我的评审任务，不拉视频管理列表/评审组/学生列表；ACADEMIC_ADMIN 进参数审计备份加载参数+审计，不拉备份；SYS_ADMIN 系统页全分区可见。`CERT_ISSUER` 已在 WP-A 停用，签发能力按 `cert:issue` 并入教务处管理员入口。
- 关键决策与理由：修复点放在前端页面分区加载层，而非后端放宽权限，保持 §15.1 权限矩阵和 Phase 24 RBAC 边界不变；对“辅助下拉”调用也按对应后端权限守卫，避免菜单允许进入但页面初始化打到更窄 API。
- 问题与解决：`ChartBox` 初版轴归一化复用 xAxis 类型导致 yAxis TS `mainType` 冲突，改为 unknown 轴输入并在输出处收窄；splitLine 局部变量消除 unknown 属性访问。视频页评审教师来源原调用 `system:user:manage` 接口，普通视频负责人无该系统权限时会 403，本轮改为有系统用户管理权限才拉列表，页面不再自动触发无权 API。
- 与规格的偏差/疑问：无。按用户硬约束仅前端修复；未做运行期 HTTP 角色矩阵，因为本轮要求 build-only 且不得前台起常驻服务，角色矩阵记录为静态权限/API 调用自检，待 Claude 复核运行期零 403。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size warning）。未启动前端/后端常驻服务。
- 下一步：`PROGRESS.md` 已置 Phase 25 待复核，等待 Claude 复核；本地私有无 remote、不 push。

## [2026-06-18] Phase 24 复核通过（Claude · REVIEW-GATE）✅ — 收官后重构整体收官
- 做了什么：复核 `feature/phase24-acceptance` 单提交 `ef8da92`（V23 + Phase24AcceptanceIT 938 行 + Phase4/Phase9 夹具对齐 + 进度日志）。读 V23、3 个验收用例、夹具 diff；clean-room：重置 schema → `mvn verify`（全新 V1–V23）+ 前端 type-check/build。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS、82/82**（Phase24AcceptanceIT 3 + 全回归 79）；Flyway v23；前端 `vue-tsc`+build 绿。核对：① V23 幂等修正 `training_goal_config` 中职(secondary_vocational_school_teacher) default/allowed 实习地点=`other`，符附录 A「中职→其他」；Phase4/Phase9 夹具由 enterprise_vocational_education→other 同步对齐（Phase4 仍断言类别节点不可选，非弱化）。② Phase24AcceptanceIT：fieldDict 用例断言 gender/id_card_type 等字典逐字 + `/training/options` 中职 containsExactly(other)/高中 contains(中小学)；rbacBoundaries 用例 7 角色登录 + 教务员 assign/test:import 被拒(403)；mainFlow 用例全链 + `assertStandardExport` 逐列 1–25 == 录入 + 26 列 + H=身份证件号码 + 全列 @。③ V1–V22 冻结、仅 V23 新增。
- 放行：PROGRESS Phase 24 置 ✅；合并 `main`（本地私有、无远程、不 push）。**🎉 收官后重构整体完成：WP-A/B/C/D（后端 RBAC/测试只确认/视频退回/评审分组）+ Phase 19~23（前端以前瞻版为底重建）+ Phase 24（收口验收）全部 ✅，12 项诉求 + UI 取长补短全部落地。** 可选 T-162（中职专业课全量学科种子）待学校确认单列。

## [2026-06-18] Phase 24 待复核小结（字段规范收口 + 整体验收）
- 做了什么：从 `main` 切出 `feature/phase24-acceptance`，完成字段规范终校与收口验收。新增 `V23__field_acceptance.sql`，按 `docs/refactor-ui-rbac-plan.md` 附录 A 将中职培养目标联动修正为默认/允许实习地点 `other`（中职→其他）；其余身份证件类型、身份类型、学历层次、培养目标、实习组织方式、实习地点、任教学段、面试组织方式、性别等字典枚举核对一致。V1-V22 冻结未改。
- 收口 IT：新增 `Phase24AcceptanceIT`，覆盖字段枚举与 `/api/training/options` 联动、新 RBAC 全角色边界、主流程 E2E 与标准导出逐字段==录入。角色边界断言 SYS_ADMIN 全权、ACADEMIC_ADMIN 含 `cert:issue`、COLLEGE_AUDITOR 可复审/视频指派/测试导入确认、COLLEGE_CLERK 仅查看+初审且 secondReview/assign/test 写拒绝、REVIEW_TEACHER 可评分、STUDENT 本人可见/确认。
- 主流程断言：标准导入→学生确认与初复审→培养初复审→四类材料合格→免考通过并剔除应考科目→视频 85/60 触发复评并以第三专家收口→测试成绩 `00000000000085` 导入确认→证书前置/生成/签发/导出/归档→标准导出。导出断言 26 列、H=`身份证件号码`、全列文本格式 `@`，并逐字段核对学号、姓名、证件号、出生日期、任教学科、证书号、有效期等与录入/生成值一致。
- 回归调整：因 V23 将中职培养目标联动收敛为 `other`，同步更新 `Phase4TrainingIT` 和 `Phase9CertificateIT` 中职测试夹具，保持与附录 A 逐字一致。
- 测试：定向 `mvn -B -ntp -pl platform-boot -am '-Dtest=Phase4TrainingIT,Phase9CertificateIT,Phase24AcceptanceIT' '-Dsurefire.failIfNoSpecifiedTests=false' test` 15/15 通过；全量 `mvn -B -ntp verify` **82/82** 通过。
- 前端：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 24 待复核**，等待 Claude 复核，未自行置 ✅；本地私有无远程、不 push。

## [2026-06-18] Phase 23 复核通过（Claude · REVIEW-GATE）✅ — 系统管理/通知/工作台/全局学年
- 做了什么：复核 `feature/phase23-system-notice-dashboard-year` 单提交 `eba635b`（前端 19 文件：新增 stores/year.ts + MainLayout + Dashboard + Notice + 系统管理 2 页 + 9 列表接学年 + 进度日志）。读 year.ts/MainLayout/Dashboard/NoticeCenter + 抽查列表年store 接入；前端 build-only gate。
- 结论：**PASS**（一轮）。`type-check` 无错 + `built in 5.97s`。核对：① WP-E 全局学年——`useYearStore`(assessmentYear persist localStorage + yearOptions 当前±2 + setYear)；MainLayout 顶栏 year-picker；9 列表(student/training/material/exemption/video/test/cert/exchange/stats)均 ref(yearStore.assessmentYear) + `watch(()=>yearStore.assessmentYear)→reload`，切换即全局生效。② 通知红点——MainLayout 菜单项 noticeCenter `unreadCount>0` 渲 menu-dot + 头部角标；NoticeCenter 列表项 readFlag===0 渲 notice-dot + StatusTag。③ 工作台——按 userStore.roles 6 角色(SYS_ADMIN/ACADEMIC_ADMIN/COLLEGE_AUDITOR/COLLEGE_CLERK/REVIEW_TEACHER/STUDENT)分别 StatCard+ChartBox+最近通知。④ 系统管理(Security/SystemAudit)接真实 API；评审组入口留视频域(已说明)。⑤ 仅新增前端内部 year store；后端/迁移 V1–V22 冻结。
- 放行：PROGRESS Phase 23 置 ✅；合并 `main`（本地私有、无远程、不 push）。**前端重建 Phase 19–23 全完成**；下一步 Phase 24(字段规范收口 + 整体验收)。

## [2026-06-18] Phase 23 待复核小结（前端重建·系统管理/通知/工作台/全局学年）
- 做了什么：从 `main` 切出 `feature/phase23-system-notice-dashboard-year`，用 Phase 19 设计系统重建系统管理、通知中心和角色感知工作台，并新增全局考核学年 Pinia store + 顶栏选择器。全包只改前端与进度日志，未改后端、迁移 V1-V22、API 契约或后端 IT。
- 系统管理：`SecurityManageView` 接真实 `security` API，账号/角色/权限矩阵/数据范围动作按 `system:user:manage`、`system:role:manage`、`system:perm:manage` 显隐；`SystemAuditView` 接真实 `systemAudit` API，参数、审计、备份按 `system:param:manage`、`audit:view`、`system:backup` 显隐，SYS_ADMIN 具备全功能入口。
- 通知中心：`NoticeCenterView` 支持列表、全部已读、已读/未读和类型过滤；未读红点覆盖顶栏角标、菜单“通知中心”项、列表未读行/标题，布局和页面均轮询未读数。
- 工作台：`DashboardView` 按 `roles/perms` 区分学生、学院教务员、学院负责人、评审教师、教务处管理员、系统管理员视角，展示对应 StatCard、ChartBox 图表、快捷入口和最近通知，继续调用真实统计与通知 API。
- 全局学年：新增 `useYearStore`，顶栏统一选择“考核学年”；已接入学生、培养、材料、免考、视频、测试、证书、导入模板/导出、统计页面，作为默认筛选或模板年度，切换后同步筛选并按页刷新。评审组管理入口继续保留在视频域，理由是建组、成员校验和按组指派都属于 `video:assign` 评审工作流。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 23 待复核**，等待 Claude 复核，未自行置 ✅。

## [2026-06-18] Phase 22 复核通过（Claude · REVIEW-GATE）✅ — 前端重建·测试/证书/导入导出/统计
- 做了什么：复核 `feature/phase22-test-cert-exchange-stats` 单提交 `f14d5cf`（前端 6 页面 + 进度日志）。读 TestResult/CertificateManage/CertificateIssue/ExchangeImport/Stats 核心 + 确认 api/stores/后端/迁移零改动；前端 build-only gate。
- 结论：**PASS**（一轮）。`type-check` 无错 + `built in 6.08s`。核对：① 测试只确认(WP-B)——imports 仅 confirm/importFile/importTests，**无 save/update**；perms 仅 test:import/test:confirm，**无 test:edit**；页内文案明示「不提供手工新建或编辑入口」。② 证书签发并入(item-12)——CertificateManage `canIssue=hasPerm('cert:issue')` + 页内 issueCertificate；CertificateIssue 仅按 cert:issue 门控，**无 CERT_ISSUER 角色判断**（grep 无命中），对教务处管理员开放；precheck 四项 + generate/correct/void/reissue 各按 perm。③ 导入——prevalidate + strategy(INSERT_ONLY 等) + confirmImport + rollback + 错误计数表。④ 统计——StatCard + ChartBox/ECharts。⑤ `src/api/*`/`stores/user` 未改；后端/迁移 V1–V22 冻结。
- 放行：PROGRESS Phase 22 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 23(前端·系统管理/通知/工作台/全局学年)。

## [2026-06-18] Phase 22 待复核小结（前端重建·测试/证书/导入导出/统计）
- 做了什么：从 `main` 切出 `feature/phase22-test-cert-exchange-stats`，用 Phase 19 设计系统重建测试结果、证书管理、证书签发、导入中心、导出中心、统计报表页面。所有页面继续接生产真实 `src/api/*` 与 `stores/user`，未改后端、迁移、API 契约或后端 IT。
- 测试结果：`TestResultManageView` 落实 WP-B 只确认，移除手工新建/编辑入口，仅保留 `/test/import`、`/test/import-file` 导入、`/test/{id}/confirm` 确认、列表查询、有效性提示与应考科目口径展示；成绩用 `.mono` 文本只读展示，前导零不做数值化。
- 证书：`CertificateManageView` 整合同页生命周期：前置校验、生成 18 位编号、更正、作废、重开、签发、标记导出、归档；签发动作按 `cert:issue` 显隐并并入教务处管理员入口，不再依赖独立 `CERT_ISSUER` 角色语义。`CertificateIssueView` 保留为签发队列辅助页，沿用同一权限点。
- 导入导出：`ExchangeImportView` 改为四步向导（模板→上传预校验→V-01~V-13 错误表→策略确认），支持 `INSERT_ONLY/OVERWRITE/SKIP_DUPLICATE/UPDATE_EMPTY`、批次列表、异常报告下载与回滚；`ExchangeExportView` 支持标准/完整/证书汇总/异常表和附件视频打包导出。
- 统计报表：`StatsReportView` 使用 `StatCard` 与 `ChartBox` 重建 8 类统计，保留筛选、指标、柱状图、统计表、钻取明细和 Excel 导出，继续调用真实 `/api/stats/{type}` 与 `/api/stats/{type}/export`。
- 权限与范围：前端动作显隐按 `test:*`、`cert:*`、`exchange:*`、`stats:view` 权限点判断；数据范围、状态机与文本化 Excel 语义全部由后端既有契约承载，本包不改。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 22 待复核**，等待 Claude 复核，未自行置 ✅。

## [2026-06-18] Phase 21 复核通过（Claude · REVIEW-GATE）✅ — 前端重建·材料/免考/视频
- 做了什么：复核 `feature/phase21-material-exemption-video` 单提交 `8653846`（前端 3 域页面 + 进度日志）。读 MaterialManage/ExemptionManage/VideoReview 核心 + 确认 api/stores/后端/迁移零改动；前端 build-only gate。
- 结论：**PASS**（一轮）。`type-check` 无错 + `built in 6.07s`。核对：① 材料内联预览——`previewable` 按 content-type/扩展名判 pdf/image/png，`<object>/<iframe>` 内联、否则 `n-result`「打开文件」下载兜底；初/复审按 material:firstReview/secondReview，批量下载按 material:batchDownload。② 免考——`form.rows` 多科逐行(subject+basis+佐证文件，必填校验)→ `applyExemption({items})`；二级审核按 exemption:firstReview/secondReview；examSubjects 展示应考剔除。③ 视频——RETURNED 态行内按钮显「重新上传」(WP-C)；`saveAssign` 按 `assignForm.mode` group/person 二选一分别 `assignVideoReviewGroup`/`assignVideoReview` + 校验(WP-D)；评审组 CRUD；thirdReview/arbitrate/confirm/returnVideoReview/playVideoReview 全按 video:* perm 显隐。④ 沿用 WP-C/WP-D 已加的真实 API；`src/api/*`/`stores/user` 未改；后端/迁移 V1–V22 冻结。
- 放行：PROGRESS Phase 21 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 22(前端·测试/证书/导入导出/统计)。

## [2026-06-18] Phase 21 待复核小结（前端重建·材料/免考/视频）
- 做了什么：从 `main` 切出 `feature/phase21-material-exemption-video`，用 Phase 19 设计系统重建过程性材料、免考、视频评审三域页面。所有页面继续接生产真实 `src/api/*` 与 `stores/user`，未改后端、迁移、API 契约或后端 IT。
- 过程性材料：`MaterialManageView` 改为 `PageContainer` + 指标卡 + 表格；支持四类材料上传/替换/提交、初审/复审、批量下载和四类合格判定。预览改为弹窗内联 `object/iframe`，复用 `/material/preview/{id}` 返回的预签名 URL；PDF/JPG/PNG 内嵌预览，非可预览文件退化为打开链接。
- 免考：`ExemptionManageView` 支持多科申请、每科佐证上传/替换/删除/预览、二级审核；应考口径弹窗调用 `/exemption/exam-subjects/{studentId}` 展示复审通过后剔除结果，多科记录在 UI 上按行独立操作，互不覆盖。
- 视频评审：`VideoReviewView` 保留上传初始化、分片上传、秒传与进度；`RETURNED/VALIDATION_FAILED/WAIT_UPLOAD` 状态显示上传/重传入口，其他状态按后端守卫不展示重传。负责人侧新增按人/按组二选一指派，接 `assignVideoReview`/`assignVideoReviewGroup`；新增评审组 CRUD 和成员维护标签页，接 `/video/reviewer-groups`。
- 视频播放与状态机动作：鉴权播放弹窗保留动态水印；我的评审支持 9 维评分；负责人侧支持第三专家复评、学院仲裁、确认与退回。退回意见按现有 `returnVideoReview(id, comment)` 提交；后端 `VideoReviewVO` 未暴露独立退回意见字段，本包不扩展契约，RETURNED 态展示状态提示并提供重新上传入口。
- RBAC 与范围：动作显隐均按权限点判断（`material:*`、`exemption:*`、`video:*`），不改写后端数据范围或状态机语义。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 21 待复核**，等待 Claude 复核材料内联预览、免考应考口径、视频退回重传与分组指派 UI，未自行置 ✅。

## [2026-06-18] Phase 20 复核通过（Claude · REVIEW-GATE）✅ — 前端重建·基础数据/学生/培养
- 做了什么：复核 `feature/phase20-base-student-training` 单提交 `6341cb0`（前端 7 页面 + 进度日志）。读 StudentManage/StudentSelf/TrainingManage/OrganizationManage 核心 + 确认 `src/api/`、`src/stores/`、后端/迁移零改动；前端 build-only gate。
- 结论：**PASS**（一轮）。`type-check` 无错 + `built in 5.49s`。核对：① 字段下拉——gender/idCardType/identityType 等用 `listDictItems(typeCode,true)` 字典；training 用 dict + `/training/options`。② **联动 server-driven**——`reloadTrainingOptions(goal, segment)` 后端返回 allowedSegments/Locations，前端 watch goal/segment、违规自动重置 subject/location；与文档「中职→其他、高中→中小学」由后端单源生效。③ **教务员不能新增专业** ——`canManageMajor = hasPerm('major:manage')` + 按钮 `v-if="canManageMajor"`，与 WP-A V20 矩阵一致（COLLEGE_CLERK 无该权限自动隐藏，同样 v-if 应用于新增学院/培养目标配置）。④ StudentSelf 锁定守卫——`locked = student.locked===1`，全字段 `:disabled="locked"`、提交/保存按钮 disabled。⑤ 动作按 perm 显隐——`canEdit/canFirstReview/canSecondReview` 各按 student:edit/info:firstReview/info:secondReview。⑥ `src/api/*`、`stores/user` 未改，后端/迁移 V1–V22 冻结。
- 放行：PROGRESS Phase 20 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 21(前端·材料/免考/视频)。

## [2026-06-18] Phase 20 待复核小结（前端重建·基础数据/学生/培养）
- 做了什么：从 `main` 切出 `feature/phase20-base-student-training`，以前瞻版外壳与 Phase 19 设计系统为底，重建基础数据四页（数据字典、行政区划、任教学科库、组织与专业）、学生基本信息、学生本人信息、专业培养信息。所有页面继续接生产真实 `src/api/*` 与 `stores/user`，未引入 mock，未改后端、迁移、API 契约或后端 IT。
- 基础数据：字典、区划、学科库、组织专业均改为 `PageContainer` 主从/详情布局；写按钮按 `dict:manage`、`subject:import`、`college:manage`、`major:manage` 显隐。学院教务员无 manage 权时只能只读浏览，不显示新增学院/新增专业/配置维护入口。
- 学生信息：`StudentManageView` 支持关键词、状态、学院和学年/年级/班级筛选，详情抽屉、编辑抽屉和初/复审弹窗按 `student:edit`、`info:firstReview`、`info:secondReview` 显隐；性别、身份证件类型、身份类型、生源地使用字典和 `RegionCascader`。`StudentSelfView` 增加锁定态守卫，`locked=1` 时只读并提示证书生成后需受控更正。
- 专业培养：`TrainingManageView` 支持关键词、状态、考核年度、学院、学段筛选；详情/编辑/提交/初审/复审按 `training:edit`、`training:confirm`、`info:firstReview`、`info:secondReview` 显隐；培养目标调用 `/training/options` 限制可选实习地点和任教学段，任教学科复用 `SubjectSelect`，不提供自由文本入口。
- 枚举/联动来源：性别、证件类型、身份类型、学历层次、培养目标、实习组织方式、实习地点、任教学段、面试组织方式、测试结论均从字典读取；培养目标→实习地点/学段与学段→学科联动使用既有后端配置和学科库校验。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 20 待复核**，等待 Claude 复核基础数据/学生/培养页面、字段枚举联动与新 RBAC 显隐，未自行置 ✅。

## [2026-06-18] Phase 19 复核通过（Claude · REVIEW-GATE）✅ — 前端重建·基座与设计系统
- 做了什么：复核 `feature/phase19-frontend-shell` 单提交 `9aa54cc`（前端 11 文件：4 设计组件 + theme + App/main + MainLayout 重建 + LoginView 真实鉴权）。读 MainLayout/LoginView 全文 + 确认 api/stores 未改、后端/迁移未改；前端 build-only gate。
- 结论：**PASS**（一轮）。`npm run type-check` 无 TS 错 + `vite build` `✓ built`（仅既有 chunk-size 警告）。核对：① 菜单 `canShowLeaf=hasAnyPerm(perms)` 按权限过滤；**空壳父菜单已修**——`visibleChildren.length===0 → null → filter` 隐藏父级；菜单 perms 同步 WP-B（testResultManage 去 test:edit）。② 登录走真实 `getCaptcha/login/changePassword`（@/api/auth），首登 mustChangePwd 弹改密→loadMe→redirect，无 mock 角色快入。③ `src/api/*`、`api/request.ts`、`stores/user`(hasPerm/hasAnyPerm) 原样保留，真实集成不回退。④ PageContainer/StatusTag/ChartBox/StatCard/.mono/Naive 主题(主色/圆角/zhCN) 迁入；既有业务页在新外壳下可用（逐页重建留 Phase 20–23）。后端零改动、迁移 V1–V22 冻结。
- 放行：PROGRESS Phase 19 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 20(前端·基础数据/学生/培养)。

## [2026-06-18] Phase 19 待复核小结（前端重建·基座与设计系统）
- 做了什么：从 `main` 切出 `feature/phase19-frontend-shell`，以前瞻版为视觉底重建生产前端外壳与登录页；新增 `frontend/src/theme/global.css`，迁入 `PageContainer`、`StatusTag`、`ChartBox`、`StatCard` 四个设计系统组件，统一主色、圆角、角色色板、`.mono` 等基础样式，并在 `App.vue` 接入 Naive UI 中文 locale、主题覆盖、loading/notification provider。
- 真实集成保留：未引入前瞻版 mock store/role switcher，生产版 `src/api/*`、`api/request.ts`、`stores/user.ts`、`directives/perm.ts` 与 token refresh/Result 解包/Blob 下载能力保持不变；路由守卫继续按 `meta.perms + userStore.hasAnyPerm` 判断。
- 登录与鉴权：`LoginView` 改为前瞻版品牌分栏视觉，但仍调用真实 `/auth/captcha`、`/auth/login`、首登 `/auth/change-pwd`；登录成功和首登改密后的跳转语义不变。
- 菜单与外壳：`MainLayout` 改为前瞻版侧栏/顶栏视觉，按生产权限点过滤菜单；当父菜单所有子项被过滤时直接隐藏父级，修复“基础数据/系统管理”空壳父菜单；保留通知未读角标、初始密码提示和退出登录。
- 与规格的偏差/疑问：无。未改后端、迁移、后端 IT、API 契约或业务页语义；业务页完整重建留 Phase 20~23。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（Vite 仅提示既有 chunk-size warning）。
- 下一步：`PROGRESS.md` 已置 **Phase 19 待复核**，等待 Claude 复核登录/鉴权/菜单按权限显隐和空壳父菜单修复，未自行置 ✅。

## [2026-06-18] Phase 18 / WP-D 复核通过（Claude · REVIEW-GATE）✅ — 评审指定与分组
- 做了什么：复核 `feature/wp-d-reviewer-group` 单提交 `4999233`。读 V22、ReviewerGroupServiceImpl(+265)/Controller、VideoReviewServiceImpl assign 扩展、DataScopeSqlHandler、Phase7 IT(+84)；clean-room：重置 schema → `mvn verify`（全新 V1–V22）+ 前端 type-check/build。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS、79/79**（Phase7 9→10）；Flyway v22；前端 `vue-tsc`+build 绿。核对：① V22 reviewer_group/member 幂等 DDL + 唯一约束（同院重名、同组重复成员）+ 索引。② 组 CRUD 全程 `ensureCanManageCollege`，create 的 collegeId 取负责人 scope（非请求），addMember 校验本院启用 REVIEW_TEACHER；list 按 video:assign scope 显式过滤；delete/removeMember 用 `name#id`/`reviewer_user_id=id` 避免唯一冲突。③ assign `reviewerIds` XOR `groupId`，组解析校验同院+ENABLED，人数=video.reviewerCount，`requireReviewerForReview` 硬校验各评审 同院+ENABLED+REVIEW_TEACHER（连带硬化按人路径）。④ Controller 全 `video:assign` + 写操作 `@AuditLog`；reviewer_group 入 TABLE_RULES。⑤ IT 覆盖 按组88/按人81/人数不足/跨院成员·组·评审 全拒，向后兼容。
- 放行：PROGRESS WP-D 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 19(前端重建·基座与设计系统)——进入前端重建主体。

## [2026-06-18] Phase 18 / WP-D 待复核小结（评审指定与分组）
- 做了什么：从 `main` 切出 `feature/wp-d-reviewer-group`，完成评审组模型与按组/按人指派增强。新增 `V22__reviewer_group.sql`，创建 `reviewer_group`、`reviewer_group_member`，唯一约束防同组重复成员；未新增 `video:return`/`video:group` 等权限点，评审组管理和指派继续复用 `video:assign`。
- 后端实现：新增评审组实体、Mapper、DTO/VO、`ReviewerGroupService` 与 `/api/video/reviewer-groups` CRUD/成员增删接口；写操作接 `@AuditLog`。`VideoAssignRequest` 支持 `reviewerIds` 或 `groupId` 二选一，`VideoReviewServiceImpl.assign` 按组解析成员后沿用既有任务创建、通知和结算流程。
- 数据范围与硬校验：评审组 CRUD 与成员维护均通过 `DataScopeService.resolve("video:assign")` 做服务层 fail-closed；组所属学院取当前负责人学院，不信任请求；成员必须是本院启用 `REVIEW_TEACHER`；按人和按组指派均校验目标视频学院、评审教师学院与 `video.reviewerCount`。
- IT 覆盖：`Phase7VideoReviewIT` 新增 WP-D 反例/正例，覆盖负责人建组（2 名本院评审）→按组指派→评分→结算、按人 `reviewerIds` 兼容、跨院成员/跨院评审人/跨院视频指派拒绝、组成员数与 `video.reviewerCount` 不符报错；同步调整 `Phase12NotificationIT` 的视频分配通知用例为真实评审教师账号。
- 前端最小：`frontend/src/api/video.ts` 增加评审组类型与 CRUD/成员/按组指派 API 封装；完整评审组管理与分组指派 UI 留 Phase 21。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase7VideoReviewIT,Phase12NotificationIT" verify` 通过，**14/14**；全量 `mvn -B -ntp verify` 通过，Failsafe **79/79**；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 18 / WP-D 待复核**，等待 Claude 复核 V22、评审组范围校验、assign 扩展与全回归，未自行置 ✅。

## [2026-06-18] Phase 17 / WP-C 复核通过（Claude · REVIEW-GATE）✅ — 视频退回可重传
- 做了什么：复核 `feature/wp-c-video-return` 单提交 `597cad9`。读 VideoReviewStatus/Controller/ServiceImpl(+164)/IT(+106)/前端最小；clean-room：重置 schema → `mvn verify`（全新 V1–V21）+ 前端 type-check/build。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS、78/78**（Phase7 7→9）；Flyway v21（未新增迁移，退回复用 `video:confirm`/`video:arbitrate`）；前端 `vue-tsc` 无错 + build 绿。核对：① `returnReview` 置 RETURNED、清终分/仲裁/确认、解锁、富审计 `return` old/new/意见、通知学生、`ensureCanWriteReview` 数据范围；CONFIRMED 禁退回、意见必填、仅 WAIT_REVIEW/REVIEWING/NEED_REVIEW/REVIEW_COMPLETED 可退回。② 守卫 `ensureReuploadable` 仅 RETURNED 跳过 taskCount 放行，REVIEWING/NEED_REVIEW/CONFIRMED 仍拒（Phase7 既有反例不变）。③ 退回重传清旧任务(`reviewer_id=id` 避免重指派唯一冲突)/会话/分片/终分→WAIT_REVIEW + 通知。④ IT 覆盖 退回→重传→重新指派→88/84 结算 86 + 审计 + CONFIRMED 双禁。
- 放行：PROGRESS WP-C 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 18(WP-D 评审指定+分组，迁移 V22)。

## [2026-06-18] Phase 17 / WP-C 待复核小结（视频退回可重传）
- 做了什么：从 `main` 切出 `feature/wp-c-video-return`，完成视频评审退回可重传。新增 `RETURNED` 视频评审状态与 `POST /api/video/reviews/{id}/return`，退回意见必填，复用 `video:confirm`/`video:arbitrate` 权限与服务层数据范围校验，未新增 `video:return` 权限点，因此**未新增 V22**，V1~V21 保持冻结。
- 状态机与守卫：`CONFIRMED` 明确禁止退回；允许 `WAIT_REVIEW/REVIEWING/NEED_REVIEW/REVIEW_COMPLETED` 退回到 `RETURNED`。`ensureReuploadable` 仅对 `RETURNED` 放行已有旧任务下的重传，`REVIEWING/NEED_REVIEW/REVIEW_COMPLETED/CONFIRMED` 仍拒绝，Phase 7 原重传守卫反例保持。
- 重传重置：`RETURNED` 重传时软删旧 `video_review_task` 与旧上传会话/分片，清空终分、结论、仲裁人、仲裁模式、确认人、确认时间并解锁；新视频校验通过后回 `WAIT_REVIEW`，可重新指派评审并重新结算，旧分数不参与新一轮评审。
- 通知与审计：退回后通知学生；退回后重传并重新进入待评审时通知学院负责人。退回显式调用 `auditLogService.record("video", bizId, target, "return", old, "RETURNED", comment)`，记录 old/new/意见/操作人/IP/target；审计失败仍沿用既有 best-effort。
- IT 覆盖：`Phase7VideoReviewIT` 新增 `returnedVideoCanBeReuploadedReassignedAndSettledWithAudit`，贯通退回→学生重传→重新指派→评分→结算，并断言退回审计 old/new/意见；新增 `confirmedVideoCannotBeReturnedOrReuploaded`，覆盖 `CONFIRMED` 不可退回且不可重传；原 `REVIEWING/NEED_REVIEW` 重传拒绝反例继续保留。
- 前端最小变更：视频评审状态筛选增加 `RETURNED/已退回`，`video.ts` 预留 `returnVideoReview(id, comment)` API；不做完整退回/重传 UI（留 Phase 21）。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase7VideoReviewIT" verify` 通过，Phase7 **9/9**；全量 `mvn -B -ntp verify` 通过，Failsafe **78/78**；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 17 / WP-C 待复核**，等待 Claude 复核退回态、守卫边界、审计通知与全回归，未自行置 ✅。

## [2026-06-18] Phase 16 / WP-B 复核通过（Claude · REVIEW-GATE）✅ — 测试结果只确认
- 做了什么：复核 `feature/wp-b-test-confirm` 单提交 `f4ce3a8`。读 V21、控制器/Service（确认仅删手工 `save`/`update`，导入内部落库与 import/confirm/查询/有效性均在）、3 个 IT diff；clean-room：重置 dev schema → `mvn verify` 全新应用 V1–V21 + 前端 type-check/build。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS、Failsafe 76/76**（Phase8 由 4→5，新增 `manualCreateAndUpdateEndpointsAreOffline` 反例证 `POST/PUT /api/test` 已下线且不落库）；Flyway「now at v21」证 V21 全新可用；前端 `vue-tsc` 无错 + `built in 6.14s`。契约不变性已验证均经导入路径：前导零成绩文本、免考通过剔除应考科目、确认后锁定拒改（改走 import 仍报「已锁定」）、跨学院写数据范围 403。Phase2 契约钉死 auditor/academic `test:import+test:confirm` 且无 `test:edit`。V21 幂等撤 `test:edit` 保留权限点定义、V1–V20 冻结。
- 放行：PROGRESS WP-B 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 17(WP-C 视频退回可重传，迁移 V22)。

## [2026-06-18] Phase 16 / WP-B 待复核小结（测试结果只确认）
- 做了什么：从 `main` 切出 `feature/wp-b-test-confirm`，完成测试结果“只导入 + 确认”后端收口与前端最小适配。后端下线手工 `POST /api/test` 新建与 `PUT /api/test` 修改入口，保留 `GET /api/test`、`GET /api/test/{studentId}`、`GET /api/test/{studentId}/validity`、`POST /api/test/import`、`POST /api/test/import-file`、`POST /api/test/{id}/confirm`；导入复用的内部落库逻辑未删除。
- V21 撤权清单：新增 `V21__test_confirm_only.sql`，幂等撤销所有角色的 `test:edit` 授权，保留 `sys_permission` 中的 `test:edit` 权限点定义用于历史审计/外键兼容；确保 `COLLEGE_AUDITOR`、`ACADEMIC_ADMIN` 继续拥有 `test:import` 与 `test:confirm`。
- IT 调整：`Phase8TestResultIT` 中原手工 save/update 建结果的用例均改为 `/api/test/import` 导入路径后查询/确认，保留前导零成绩、免考剔除应考科目、结论有效性、锁定拒改、读写数据范围断言；新增手工 POST/PUT 下线反例与锁定后重导入拒绝反例。`Phase14E2EIT` 第 7 阶段改为导入成绩后确认，主流程证书前置仍贯通；`Phase2SecurityIT` 同步 WP-B 后 `test:edit` 不授权、`test:import/test:confirm` 保留的矩阵断言。
- 前端最小变更：移除 `frontend/src/api/testResult.ts` 的 `saveAbilityTest/updateAbilityTest`，移除 `TestResultManageView` 的录入/编辑抽屉与 `test:edit` 入口；菜单与路由不再要求 `test:edit`，保留导入、查询、确认与有效性展示能力，完整只确认 UI 留到 Phase 22 前端重建。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase2SecurityIT,Phase8TestResultIT,Phase9CertificateIT,Phase14E2EIT" verify` 通过，Failsafe 28/28；全量 `mvn -B -ntp verify` 通过，Failsafe **76/76**；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **Phase 16 / WP-B 待复核**，等待 Claude 复核 T-121~T-125、V21 撤权与导入+确认契约，未自行置 ✅。

## [2026-06-18] WP-A 复核通过（Claude · REVIEW-GATE）✅ — RBAC 基座
- 做了什么：复核 `feature/wp-a-rbac` 单提交 `6227cdd`（V20 + 9 IT + PROGRESS/DEVLOG，**无业务/main java 改动**）。读 V20 逐角色核对 §1 矩阵；读 9 个 IT diff 核对角色迁移；**clean-room**：重置 dev schema → `mvn -B -ntp verify` 让 Flyway 全新应用 V1–V20 再跑全量 IT。
- 结论：**PASS**（一轮）。`mvn verify` **BUILD SUCCESS，Failsafe 75/75**（61 回归 + Phase14E2EIT 14，全在新 RBAC 模型下绿）；Flyway「Successfully applied 20 migrations, now at v20」证 V20 全新可用且幂等。V20 校验：CLERK=只读+初审、AUDITOR=复审+录入/导入/video:assign/arbitrate、ACADEMIC_ADMIN+=cert:issue、SYS_ADMIN 全权、CERT_ISSUER 软删+账号停用；revoke→regrant 幂等、V1–V19 未改。IT 校验：clerk 失去的 assign/import/edit/secondReview 正确迁移 auditor/academic，跨院写越权用例改用 auditor 仍考数据范围（非缺权），Phase2 契约用例钉死新矩阵。
- 放行：PROGRESS WP-A 置 ✅；合并 `main`（本地私有、无远程、不 push）。下一步 Phase 16(WP-B)。

## [2026-06-18] WP-A 待复核小结（RBAC 基座重定义）
- 做了什么：从 `main` 切出 `feature/wp-a-rbac`，新增 `V20__rbac_regrant.sql`，仅重定义运行期 RBAC 授权矩阵与受影响 IT 登录角色；未改业务代码、数据范围语义、前端 UI 或 V1~V19 既有迁移。
- V20 授权变更：`SYS_ADMIN` 通过动态 `INSERT ... SELECT` 关联当前 `sys_permission` 全表权限；`ACADEMIC_ADMIN` 保留原授权并新增 `cert:issue`；`COLLEGE_AUDITOR` 承接学院侧 `student/training/exchange/test` 录入导入预校验、各业务复审、`video:assign/arbitrate/confirm` 等动作权；`COLLEGE_CLERK` 收敛为 `*:view`、`student:export`、`exchange:export:standard/full`、`material:batchDownload`、`student/training/material/exemption` 相关初审、`notice:view/stats:view/audit:view/dict:view`，移除 edit/import/prevalidate/test/video assign/secondReview/manage 类动作。
- CERT_ISSUER 处理：按 WP-A 要求删除 `CERT_ISSUER` 角色与角色权限/用户角色关联，并停用历史测试账号 `test_cert_issuer`；证书签发统一由 `test_academic_admin` 所属 `ACADEMIC_ADMIN` 执行。
- IT 调整：`Phase2SecurityIT` 改为断言 clerk/auditor/academic/sysadmin 新矩阵；`Phase3StudentIT`、`Phase4TrainingIT` 写侧范围反例改用 `test_college_auditor`；`Phase7VideoReviewIT`、`Phase12NotificationIT`、`Phase14E2EIT` 的 `video:assign` 改用 auditor；`Phase8TestResultIT` 的 test 录入/导入改用 auditor；`Phase9CertificateIT`、`Phase14E2EIT` 的 `cert:issue` 改用 academic admin；`Phase10ExchangeIT` 导入/预校验/确认导入改用 auditor，clerk 保留读/导出。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase2SecurityIT,Phase7VideoReviewIT,Phase8TestResultIT,Phase9CertificateIT,Phase10ExchangeIT,Phase12NotificationIT,Phase14E2EIT" verify` 通过，Failsafe 44/44；全量 `mvn -B -ntp verify` 通过，Failsafe **75/75**；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅既有 Vite chunk-size 警告）。
- 下一步：`PROGRESS.md` 已置 **WP-A 待复核**，等待 Claude 复核 V20 授权矩阵、CERT_ISSUER 删除策略与全回归结果，未自行置 ✅。

## [2026-06-18] T-115 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：复核 `feature/t115-e2e-split` 单提交 `96980e6`（仅 `Phase14E2EIT.java` + PROGRESS/DEVLOG）。增量基线 `main..HEAD`：无业务代码/迁移/yml/pom/前端改动。首跑因本地 MySQL/Redis/MinIO 未起导致全 IT 上下文加载失败（`Communications link failure`，环境问题非代码问题）；起 `docker-compose.dev.yml`（mysql/redis/minio）后重跑。
- 结论：**PASS**（一轮）。`mvn -B -ntp verify` **BUILD SUCCESS，Failsafe 75/75**（`Phase14E2EIT` 14 个有序阶段子用例全绿 + Phase2~13 回归 61 全绿）。读码确认 `@TestMethodOrder`+`@TestInstance(PER_CLASS)` 共享流程字段、`@BeforeAll/@AfterAll` 整链前后清理、每阶段断言其后置条件；原断言**零删减**全部迁移到位（标准导出 26 列 / H=身份证件号码 / 全列 `@` / 导出逐字段==录入、成绩 `00000000000085`、免考剔除应考科目、视频第三专家终分 83、student/training/exemption secondReview + video confirm + cert 生命周期审计）。
- 放行：PROGRESS T-115 置 ✅；合并 `main`（本地私有、无远程、不 push）。测试可维护性加固达成，主流程覆盖与断言不变。

## [2026-06-18] T-115 待复核小结（Phase14E2EIT 拆分）
- 做了什么：在 `feature/t115-e2e-split` 仅重构 `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase14E2EIT.java`，把原单一 `mainFlowFromImportToArchiveAndStandardExportIsConsistent` 拆为 14 个 `@Order` 有序子用例：导入、学生复审、培养复审、四类材料、免考、视频复评、测试结果、证书前置、生成、签发、导出、归档、标准导出终断言、审计留痕。
- 关键决策与理由：采用 `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` + `@TestInstance(PER_CLASS)` 共享流程字段，`@BeforeAll/@AfterAll` 只做整条链路前后清理；每个子用例重新登录取 token，避免测试运行超过 30 秒后 JWT 失效。未改任何业务代码、迁移、配置、依赖或前端。
- 断言核对：原有断言全部保留并迁移到对应阶段，包括标准导出 A-Z 26 列、H=`身份证件号码`、全列文本格式 `@`、导出 `studentNo/name/idCard/birthDate/teachingSubject/certNo/validUntil` 逐字段等于录入、成绩 `00000000000085`、免考剔除应考科目、视频第三专家终分 83、student/training/exemption/video/cert 审计断言。
- 测试：`mvn -B -ntp -pl platform-boot -am -DskipTests test-compile` 通过；定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase14E2EIT" verify` 通过，`Phase14E2EIT` 14/14 绿；全量 `mvn -B -ntp verify` 通过，Failsafe 共 75/75（Phase14E2EIT 14 + Phase2~13 回归 61）。
- 下一步：T-115 已在 `PROGRESS.md` 置「待复核」，等待 Claude 复核测试拆分质量与全回归结果，未自行置 ✅。

## [2026-06-18] Phase 14 复核通过（Claude · REVIEW-GATE）✅ — 全项目收官
- 做了什么：独立复核 Phase 14 收口增量（单提交 `bccee73`）。`mvn -B -ntp verify` GREEN **62/62**（`Phase14E2EIT 1` + 全回归 Phase2~13 共 61）、前端 type-check/build 绿；全读 851 行 E2E + SPI/开关/空实现 + Dockerfile/compose/nginx + AT复验矩阵；无既有业务代码改动、无新迁移、V1–V19/治理冻结，按比例未另派代理。
- 结论：**PASS**（一轮）。E2E 主流程总闸真实贯通（导入→学生/培养复审→四类材料合格→免考通过且应考剔除→视频 2 评审+需复评+第三专家 83+确认→测试前导零成绩保留+确认锁定→证书前置→18 位生成→签发有效期 2029/6/30→导出→归档），**标准导出 26 列+H+文本 `@`+逐字段==录入**，并断言全流程审计留痕——一条用例联动复验 AT-01/02/05/06/07/08/09/10/11/12/13 + 状态机A/C；AT-01~14 整体复验归档（矩阵复验列全 ✅ + IT 依据）；M14 6 SPI + 开关默认关 + 空实现，一期零影响（61 回归证）；部署物齐备（多阶段后端 Dockerfile/前端 nginx/生产 compose 五服务健康检查+Flyway 种子/README/备份手册）。
- 放行：PROGRESS Phase 14 置 ✅、AT-01~14 复验列全 ✅；合并 `main`（本地私有，无远程，不 push）。**🎉 全项目 14 阶段（Phase 0~14）全部 ✅ 已复核，114/114 任务，AT-01~14 首验+复验全通过——项目收官。** 3 Minor 入 backlog（镜像未在 verify 内构建需交付前手动冒烟、/api/health 端点确认、E2E 单大用例可后续拆分）。

## [2026-06-17] Phase 14 待复核小结（T-109~T-114）
- 做了什么：从 `main` 切出 `feature/phase14-T109-acceptance`，完成最后收口阶段。新增 M14 外部接口空 SPI 与配置开关（统一身份、教务、学籍、电子签章、电子证照、上级平台），默认关闭且不接入既有业务；新增后端多阶段 `Dockerfile`、前端 `frontend/Dockerfile`/`nginx.conf`、生产 `docker-compose.yml`、`.env.example` 与 README 部署说明。
- 关键决策与理由：本阶段未新增 V20 迁移，V1~V19 保持冻结；Docker/compose 作为 build-only 交付物，不在自动验证中构建或启动生产应用容器，避免联网/卡死；M14 只预留接口与开关，空实现返回 disabled，不改变一期运行期行为。
- 验收归档：新增 `docs/AT验收复验矩阵.md`，逐条记录 AT-01~AT-14 的首验阶段、Phase14 复验方式、对应 IT 与结论；`PROGRESS.md` AT 跟踪的 `复验(Phase14)` 列已逐条标 ✅；`docs/phase-14-非功能部署验收.md` 验收清单已按收口结果勾选。
- E2E：新增 `Phase14E2EIT` 纳入 `mvn verify`，贯通导入→学生/培养复审→四类材料→免考→视频分差复评→测试确认→证书前置/生成/签发/导出/归档→标准导出，断言状态、AT-01/02 文本导出一致、AT-07 应考剔除、AT-08 85/60/81 终分 83、AT-09/10/11 证书前置/编号/有效期与 AT-12 留痕。
- 兼容性与非功能：Excel 文本一致性由 POI 机检覆盖，Excel/WPS 双端人工核对要求写入复验矩阵；主流浏览器回归目标记录为 Chrome/Edge/Firefox 最新稳定版；生产 compose 与 dev compose 分离，MySQL/Redis/MinIO/backend/frontend 均配置数据卷与健康检查。
- 测试：定向 `mvn -B -ntp -pl platform-boot -am "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=Phase14E2EIT" verify` 通过（1/1）；`mvn -B -ntp verify` 通过，Failsafe 共 **62/62**（Phase14E2EIT 1 + Phase2~13 回归 61）；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅 Vite chunk size 警告）；生产 compose 执行 `docker compose -f docker-compose.yml config --quiet` 通过，临时 dev 依赖已停止。
- 下一步：Phase 14 已置「待复核」，等待 Claude 按 `docs/REVIEW-GATE.md` 做最终复核；保持本地私有，不加远程、不 push。

## [2026-06-17] Phase 13 复核通过（Claude · REVIEW-GATE，2轮 PASS）✅
- 做了什么：复核 B1 修复增量（单提交 `3c65d4f`，7 业务 service + AuditLogServiceImpl + IT）。`mvn -B -ntp verify` GREEN **61/61**（Phase13 6/6 新增富审计反例 + 回归 Phase2~12 共 55）；读 7 service diff + AuditLogServiceImpl + 新 IT；未动迁移(V1–V19)/治理/前端。
- 结论：**PASS**。B1 闭环——主要审核/状态流转 op 均显式 `auditLogService.record(bizType,bizId,target,old,new,comment)`（捕获 oldStatus→updateById→record，附加非破坏）：student/training/exemption first+second、material first、cert void/reissue、video settle/thirdReview/arbitrate/confirm、exchange rollback；各加 `xxxTarget()` 可定位；`AuditLogServiceImpl.record` 改 best-effort（try/catch→log.warn，审计失败不中断主业务、非事务边界不污染）。反例 `majorReviewFlowsWriteRichAuditAndCanBeQueriedByStudent`：student/training/exemption 复审退回 old=SECOND_REVIEW/new=SECOND_REJECTED + bizId/comment/operator/IP/target、cert 作废 old=ISSUED/new=VOIDED，且 `/api/audit/log?studentId=` 可查到——AT-12 §7①②达成。55 回归全绿、附加非破坏。
- 放行：PROGRESS Phase 13 置 ✅、**AT-12 首验通过**（全部 P0 完成）；合并 `main`（本地私有，无远程，不 push）；启动 Phase 14 收口。Minor×4 入 backlog。

## [2026-06-17] Phase 13 复核退回修复（B1 · AT-12 审核全留痕）
- 做了什么：在原分支 `feature/phase13-T104-system-audit` 修复 `docs/reviews/phase-13-review.md` 退回项 B1。补全主要审核/状态流转 op 的显式富审计：学生基本信息 firstReview/secondReview、专业培养 firstReview/secondReview、免考 firstReview/secondReview、过程性材料 firstReview（secondReview 已有）、证书 void/reissue、视频自动结算/thirdReview/arbitrate/confirm、导入回滚 rollback。
- 关键决策与理由：沿用既有 `auditLogService.record(bizType,bizId,target,operation,old,new,comment)` 模式，在状态更新后附加记录，不改状态机/数据范围/返回语义；`AuditLogServiceImpl.record` 改为 best-effort，审计落库失败仅告警，避免通知/审计类辅助能力反向中断主流程。
- 问题与解决：证书 `recordAudit` 原 target 只有证书号，已扩展为 `id/assessmentYear/studentId/certNo`，保证作废/重开/更正都能定位业务记录；证书重开不改既有实体状态，仅审计记录 `VOIDED -> REISSUED` 的业务事件，避免触碰已通过状态机。
- 测试：新增 `Phase13SystemAuditIT.majorReviewFlowsWriteRichAuditAndCanBeQueriedByStudent`，覆盖 student/training/exemption 复审退回 old=SECOND_REVIEW/new=SECOND_REJECTED + bizId/comment/operator/IP、cert 作废 old=ISSUED/new=VOIDED + 原因，并验证 `/api/audit/log?studentId=...` 可查到上述复审记录。`mvn -B -ntp verify` GREEN 61/61（含 Phase2~12 回归）、`npm --prefix frontend run type-check` 通过、`npm --prefix frontend run build` 通过。
- 下一步：Phase 13 已在 `PROGRESS.md` 重新置「待复核」，AT-12 跟踪记录 B1 修复覆盖面，交 Claude 复核增量与回归，未自行置 ✅。

## [2026-06-17] Phase 13 复核退回（Claude · REVIEW-GATE）❌
- 做了什么：独立复核 Phase 13 增量（单提交 `8401a7e`）。`mvn -B -ntp verify` GREEN 60/60（Phase13 5/5 + 回归 Phase2~12 共 55）、前端绿；全读 AuditLogAspect/AuditLogServiceImpl/SystemManagementServiceImpl/AuditQueryMapper/V19/IT + 3 个既有改动 diff + `grep auditLogService.record(`。
- 结论：**退回**。无需返工项：参数改即生效（updateParam 校验+仅改可编辑值，反例 video.diffThreshold 12→8 即生效）、审计不可删（普通管理员 403 + 删除尝试留痕）、审计查询数据范围（audit:view 学院只见本院）、脱敏鉴权（明文证件号 403）、登录留痕、V19 backup_record+手册、AuditLogAspect 抽 AuditIp 非破坏重构、55 回归全绿。**但 B1（Major）AT-12 审核全留痕未达成**：`@AuditLog` 切面仅记 bizType/operation/operator/IP/time，**无 bizId/target/前后状态/意见**；完整审计仅 `material.secondReview` + `cert.correct`（+login/delete-rejected）。主要审核流程（student/training/exemption 初审·复审·确认、material 初审、cert 作废·重开、video 复审·确认·仲裁、import 回滚）审计行无法定位记录、无状态变化/意见，且按学生查不到其复审 → 击穿 AT-12 §7①（均留前后状态/意见）+ §7②（按学生查）。
- 退回处理：PROGRESS Phase 13 置 **复核退回**、不并 main、不置 ✅、AT-12 维持 `[~]`；codex 原分支在主要审核 op 显式 `auditLogService.record(bizType,bizId,target,operation,old,new,comment)`（pattern 已有、附加非破坏）或增强切面携带，补 student/training/exemption 复审退回 + cert 作废 的 old/new+bizId+意见 反例，重交只复核增量+回归。Minor×4 入 backlog。

## [2026-06-17] Phase 13 待复核小结（T-104~T-108）
- 做了什么：从 `main` 切出 `feature/phase13-T104-system-audit`，完成系统参数、审计日志、脱敏鉴权治理与备份记录能力。新增 `V19__system_audit.sql` 仅创建 `backup_record`，未修改 V1 `sys_param`/`audit_log` schema 或既有种子；新增 `/api/system/param`、`/api/audit/log`、`/api/system/backup` 接口与前端“参数审计备份”页面。
- 关键决策与理由：系统参数只允许更新可编辑参数的 `paramValue/description`，并按参数类型、分组和已知关键参数枚举校验；既有 `ParamService` 每次读库，更新后天然热生效。备份入口只写 `backup_record` 演练记录并链接 `docs/备份与恢复手册.md`，实际 MySQL dump/binlog/MinIO mirror 由运维执行，避免 Web 应用持有运维级权限。
- AT-12 留痕：复用 `@AuditLog` 基础切面并把 IP 解析抽到 `AuditIp`；登录成功补充 `auth/login` 审计；对材料复审补显式 rich audit，记录 `oldStatus/newStatus/comment/operator/ip/target`；审计删除拒绝自身显式留痕。审计查询支持业务类型、学生、批次、学院、时间、操作人、关键词等维度；学院范围通过操作人学院或关联业务记录学院解析，SCHOOL/SYSTEM 可查全量。
- 安全治理：普通管理员删除审计日志一律 403 且删除尝试自身留痕；无敏感导出权限访问学生明文证件号被拒；既有脱敏/水印/鉴权语义保持不变，Phase2~12 回归覆盖未破坏。产出《备份与恢复手册》，记录 MySQL 全备+binlog 时间点恢复、MinIO 版本化/镜像恢复、逻辑删除恢复和演练记录口径。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，定向 `Phase13SystemAuditIT` 5/5 通过；`mvn -B -ntp verify` 通过，Failsafe 共 60 tests，覆盖登录成功留痕、复审退回 rich audit、审计不可删且拒绝动作留痕、`video.diffThreshold=8` 即时触发需复评、敏感明文无权限拒绝、学院审计范围不含他院，并回归 Phase2~12；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 13 已在 `PROGRESS.md` 与 `docs/phase-13-系统管理与审计.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 首验 AT-12 与参数/审计/脱敏/备份治理，未自行置 ✅。

## [2026-06-17] Phase 12 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 12 增量（单提交 `0c82431`）。`mvn -B -ntp verify` GREEN **55/55**（`Phase12NotificationIT 4/4` + **回归 Phase2~11 共 51**）、前端 type-check/build 绿；全读 V18 + NotificationServiceImpl + NoticeController + ReviewNotificationHelper + **6 个触发点 service diff（逐一确认附加非破坏）** + IT；P1 轻量 + 全回归绿，按比例未另派代理。
- 结论：**PASS**（一轮）。四类触发（提交→学院教务员/负责人、退回→学生+抄送、视频分配→评审教师、导出→发起人）接入正确；**严格附加非破坏**（6 service 仅注入 helper + updateById 后 send，唯一改动为等价局部变量；51 条既有回归全绿）；**send 失败不影响主业务**（helper 每法 + service 每通道 双层 try/catch、非事务边界）；**通知本人可见**（list/unreadCount 按 currentUserId、markRead 以 id+user_id→他人 403、read-all 仅本人）；NotificationService 置 platform-system 无环、通道抽象可扩展；V18 文本字段+索引、notice:view V8 预种、V1–V17/治理未改、PROGRESS 未自 ✅。
- 放行：PROGRESS Phase 12 置 ✅（无新 AT 首验）；合并 `main`（本地私有，无远程，不 push）；启动 Phase 13（AT-12 审核全留痕首验）。4 Minor 入 backlog（send 失败未直接造例、通知在业务事务内同步发送可改 afterCommit、轮询非 SSE、list 非真分页）。

## [2026-06-17] Phase 12 待复核小结（T-101~T-103）
- 做了什么：从 `main` 切出 `feature/phase12-T101-notification`，完成 `V18__notification.sql`、`notification` 实体/Mapper/VO、`NotificationService`、`NotifyChannel` 抽象、站内信落库通道、通知中心接口、前端通知中心、顶栏未读角标与轮询。
- 触发点清单：已在 Phase3/4/5/6 状态流转处接入提交待初审/待复审提醒、退回/不通过提醒；Phase7 视频评审分配提醒评审教师；Phase10 导出完成提醒发起人。接收人通过学生关联账号、学院教务员/负责人角色、评审任务 reviewerId 与当前用户解析。
- 关键决策与理由：通知能力放在 `platform-system`，业务模块只单向依赖系统服务，避免 notification 反向依赖 business；`NotificationService.send` 内部捕获通道异常，通知落库/通道失败不回滚也不中断审核、分配、导出等主业务。
- 数据范围与前端结果：通知列表、未读计数、单条已读、全部已读均按当前登录 `user_id` 过滤；用户 A 不能读取或标记用户 B 通知。前端新增 `/api/notice` API、通知中心页面、菜单入口与未读角标，ID 仍按 string 处理。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，定向 `Phase12NotificationIT` 4/4 通过；`mvn -B -ntp verify` 通过，Failsafe 共 55 tests，覆盖材料提交待初审、退回、视频分配、导出完成、未读计数/标记已读、本人可见反例并回归 Phase2~11；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 12 已在 `PROGRESS.md` 与 `docs/phase-12-通知.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核触发点、本人可见与非破坏回归，未自行置 ✅。

## [2026-06-17] Phase 11 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 11 增量（单提交 `37b60ef`，新模块 platform-statistics）。`mvn -B -ntp verify` GREEN **51/51**（`Phase11StatsIT 5/5` + 回归 Phase2~10 共 46）、前端 `npm install`(echarts) + type-check/build 绿；读 747 行 `StatsServiceImpl` + 控制器 + IT + 前端；一路独立代理 PASS（逐条确认 8 类统计 fail-closed 收敛、无跨院泄漏）。
- 结论：**PASS**（一轮）。数据范围服务层强制（`resolveScope("stats:view")` + `scopedStudents`，空集/越范围→空、batch 按 operator/scopeJson），学院只见本院（IT 反例 学院A不含B）；材料完成率（四类全 PASSED）按类别 + 证书各状态 **与 DB 分组对账一致**（IT 双对账）；口径复用 Phase5/6/7/9 既有状态与 Phase3/4 校验器、分母 DENOMINATOR_RULE 文档化；异常可定位学生/字段；导出复用 `ExchangeExcelHelper` 文本 `@`；`stats:view` V8 预种无新迁移；新模块注册接入 boot 无环；V1–V17/治理未改、PROGRESS 未自 ✅。
- 放行：PROGRESS Phase 11 置 ✅（AT-13 统计读侧续接，无新首验）；合并 `main`（本地私有，无远程，不 push）；启动 Phase 12。5 Minor 入 backlog（SELF 范围死分支、材料类合格口径较 Phase5 略松、scopeJson 子串匹配、钻取静态、统计全量入内存/明细 200 截断）。

## [2026-06-17] Phase 11 待复核小结（T-092~T-100）
- 做了什么：从 `main` 切出 `feature/phase11-T092-statistics`，新增 `platform-statistics` 模块并接入 boot，完成 8 类统计服务、`/api/stats/{type}` 查询、`/api/stats/{type}/export` 导出、统计报表页（Vue3 + Naive UI + ECharts）、路由菜单和前端 API。
- 关键决策与理由：本阶段未新增 V18 迁移，`stats:view` 已在 V8 预种并按 COLLEGE/SCHOOL 授权；聚合查询跨多张业务表且 `import_export_batch` 无 college_id，统计读侧采用服务层 `DataScopeService.resolve("stats:view")` 明确收敛范围，而不是为多表聚合另造拦截规则。
- 统计口径：分母按 docs §6 默认“当前考核年度在册学生”；由于 `student` 表无年度字段，默认读取 `current_assessment_year` 参数并按当前数据范围内学生集合计，`assessment_year` 过滤应用于材料、免考、视频、测试、证书等带年度字段的业务表。
- 业务结果：学院提交、材料完成率、免考、视频评审、证书生成、任教学段/学科交叉、异常数据、导入导出日志均可查可导出；Excel 导出复用 `ExchangeExcelHelper.writeTableWorkbook`，列格式保持文本 `@`；异常统计复用既有学生/培养/证书校验器定位学生和字段。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，定向 `Phase11StatsIT` 5/5 通过；`mvn -B -ntp verify` 通过，Failsafe 共 51 tests，覆盖材料完成率对账、证书状态对账、学院A不含学院B、异常定位、Excel文本格式导出并回归 Phase2~10；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 11 已在 `PROGRESS.md` 与 `docs/phase-11-统计报表.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核统计口径、对账一致性、数据范围与导出文本格式，未自行置 ✅。

## [2026-06-17] Phase 10 复核通过（Claude · REVIEW-GATE，2轮 PASS）✅
- 做了什么：复核 B1/B2 修复增量（单提交 `e868997`，service +101 / IT +58）。`mvn -B -ntp verify` GREEN **46/46**（Phase10 6/6，新增 B1/B2 反例 + 回归 Phase2~9 共 40）；读 B1/B2 diff 与两条新反例逐条核；未动迁移(V1–V17)/治理/前端。
- 结论：**PASS**。B1：`ensureCanUpdateExisting` 对已存在 student/training/certificate 先校验其当前 collegeId ∈ 调用者 `exchange:import` 写范围、再断言现有学院==目标，`applyStudent` 不再跨学院改写 collegeId → 学院 A 凭学号命中他院 B 学生被 `ensureCanImportCollege(B)` 拒（反例证 B 学生 collegeId/姓名/证件号 与证书学院 不变）。B2：`confirmImport` 去 `@Transactional`，每行 `TransactionTemplate(REQUIRES_NEW)` 独立事务 + `catch(Exception)`，坏行自身回滚、已成功行各自提交，`dbText` 防异常明细二次截断（反例证 83 字超长学号坏行 → code 0、成功1/失败1，OK 行入库、坏行不存在）。
- 放行：PROGRESS Phase 10 置 ✅、AT-01/02/14 首验通过；合并 `main`（本地私有，无远程，不 push）；启动 Phase 11。Minor×7 维持 backlog。

## [2026-06-17] Phase 10 复核退回修复（B1/B2）
- 做了什么：修复 `docs/reviews/phase-10-review.md` 退回项 B1/B2。B1：确认导入更新现有 student/training/certificate 前校验其当前 `collegeId` 是否在调用者 `exchange:import` 写范围内，现有学生不再按导入行跨学院改写 `collegeId`，避免学院 A 凭学号覆盖/迁移学院 B 记录。B2：每行 `importOne` 改为 `TransactionTemplate + PROPAGATION_REQUIRES_NEW` 独立事务，行循环捕获含 `DataAccessException` 在内的异常并记录失败，坏行不回滚已成功行。
- 关键决策与理由：确认导入外层不再包大事务，批次状态和错误明细按逐行结果落库；错误明细写库前按字段长度裁剪，避免“坏行本身超长”导致记录失败原因时再次触发 DB 截断并把接口打成 500。
- 问题与解决：最初 B2 反例用超长学号触发 DB 截断，行内异常已被捕获，但 `import_error_detail.student_no` 再次写入超长值导致 500；已用 `dbText` 保护异常明细字段长度，保留失败定位能力。
- 与规格的偏差/疑问：未改已通过的 AT-01/AT-02/AT-14、读侧数据范围、回滚冲突逻辑和 V17 迁移。
- 测试：`mvn -B -ntp -DskipTests test-compile` 通过；定向 `Phase10ExchangeIT` 6/6 通过；`mvn -B -ntp verify` 通过，Failsafe 共 46 tests（含新增跨学院覆盖拒绝、坏行不回滚已成功行反例）；`npm --prefix frontend run type-check`、`npm --prefix frontend run build` 通过。
- 下一步：Phase 10 已在 `PROGRESS.md` 重新置「待复核」，交 Claude 复核 B1/B2 增量与回归，未自行置 ✅。

## [2026-06-17] Phase 10 复核退回（Claude · REVIEW-GATE）❌
- 做了什么：独立复核 Phase 10 增量（单提交 `c074a48`，新模块 platform-exchange）。`mvn -B -ntp verify` GREEN 44/44、前端 type-check/build 绿；全读 1449 行 `ExchangeServiceImpl` + `ExchangeExcelHelper` + 26 列模型 + 控制器 + V17 + 588 行 IT；两路独立代理（质量 PASS / AT 代理 CONCERNS）。
- 结论：**退回**。AT-01 文本化（模型 String+写 `@`+读字符串）、AT-02 26 列 A–Z+H="身份证件号码"、AT-14 V-01~V-13 复用既有校验器且不入库、INSERT_ONLY/SKIP、回滚冲突判定、导出读侧数据范围+敏感脱敏 全过且反例齐；V17 仅新增、V1–V16 冻结、新模块接入正确、POI 受管。**但 B1（Major）导入更新写侧漏校验现有记录归属**：`importOne` `studentByNo` 无范围命中他院学生、`ensureCanImportCollege` 只校验目标学院、`applyStudent` 无条件改 collegeId → OVERWRITE/UPDATE_EMPTY 下学院 A 凭学号覆盖/迁移他院 B 学生（与 Phase 3 同类跨学院写）；**B2（Major）导入逐行事务粒度**：裸 `DataAccessException` 逃出 per-row catch 回滚整批 + studentMapper dup-catch 致 `UnexpectedRollbackException` 风险。
- 退回处理：PROGRESS Phase 10 置 **复核退回**、不并 main、不置 ✅、AT-01/02/14 维持 `[~]`；codex 原分支修 B1（更新前校验现有记录 collegeId∈写范围、collegeId 不跨范围改）/B2（per-row REQUIRES_NEW 或 catch DataAccessException 隔离坏行）+ 补反例（跨院更新被拒、坏行不污染整批、OVERWRITE/UPDATE_EMPTY 往返），重交只复核增量+回归。Minor×7 入 backlog。

## [2026-06-17] Phase 10 待复核小结（T-080~T-091）
- 做了什么：从 `main` 切出 `feature/phase10-T080-exchange`，完成 `V17__exchange.sql`、`platform-exchange` 模块、导入导出批次/异常/回滚追溯表、26 列标准 Excel 模型、模板下载、预校验中心、异常报告、确认导入/回滚、标准/完整/证书汇总/异常/附件清单导出；前端新增导入中心、导出中心、API、路由与菜单。
- 关键决策与理由：Phase10 文档中的 `V16__exchange.sql` 已过时，磁盘 max 为 V16，按 AGENTS 迁移规则使用 `V17__exchange.sql`；26 列模型全 `String`，写出列格式统一 `@`，读取按字符串取值；预校验复用既有 `NameValidator`、证件/出生日期、专业代码、培养联动、证书段码与有效期规则，避免另造口径。
- 问题与解决：导入回滚初版用完整 JSON 比对，自动审计时间字段会让刚导入且未修改的关联记录误判为冲突；已改为规范化快照比较，忽略 `createdAt/updatedAt/createdBy/updatedBy/deleted`，业务字段后续改动仍会冲突。学院导入跨院反例改为预校验合法、确认导入阶段由写侧数据范围拒绝，覆盖真实边界。
- 业务结果：V-01~V-13 预校验不入库并可下载异常报告；确认导入支持新增/覆盖/跳过重复/仅更新空字段，写批次与 record_ref；回滚支持 INSERT 逻辑删除、UPDATE 还原 before_json、已后续修改跳过并提示；导出按数据范围过滤，敏感汇总导出需要 `exchange:export:sensitive` 才返回明文，否则脱敏。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase10 共 44 tests；`Phase10ExchangeIT` 覆盖 AT-02 26列+H表头、AT-01 文本单元格/证件号/前导零学号导入导出保持、AT-14 V-01~V-13 十三条反例、异常不入库、跳过重复、导入回滚与冲突提示、导出/导入数据范围和敏感导出鉴权；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 10 已在 `PROGRESS.md` 与 `docs/phase-10-导入导出与预校验.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核 AT-01/AT-02/AT-14，未自行置 ✅。

## [2026-06-17] Phase 9 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 9 增量（单提交 `6be5f79`）。`mvn -B -ntp verify` GREEN **40/40**（`Phase9CertificateIT 7/7` + 回归 Phase2~8 共 33）、前端 `type-check`/`build` 绿；全读 595 行 `CertificateServiceImpl` + `CertSequenceMapper`(FOR UPDATE) + 状态机 + V15/V16 + 636 行 IT；两路独立代理，代理 1 给出**并发安全明确结论**。
- 结论：**PASS**（一轮）。**AT-10 18 位编号生命线安全**：`cert_sequence` 行锁(`SELECT…FOR UPDATE`)+同 `@Transactional` 自增，行锁持有至 generate 提交故并发严格串行；序号在所有可失败门之后、唯一一次 insert 之前消费，insert 冲突则整事务回滚序号回退（无空号）；`cert_no` 唯一键兜底；无 Redis INCR/max+1；50×10 线程并发反例断言 certNo 不重且序号连续 1..50。AT-09 前置聚合复用各阶段结论→缺失清单→拒；AT-11 有效期上/下半年（边界==6归上半年）。状态机 C 各流转有守卫、关键字段结构性锁定(仅 cert:correct 可改+留痕)、作废→重开关联原号(原证留 VOIDED 合验收)。读+写数据范围(读 SELF/COLLEGE/SCHOOL、写仅校级、collegeId 取自实体)。V15+V16 幂等、V1–V14 冻结、ID 命名空间不冲突；V16 把"学生看本人证书"作新迁移补授(遵守不改已发布脚本)。
- 放行：PROGRESS Phase 9 置 ✅、AT-09/10/11 首验通过；合并 `main`（本地私有，无远程，不 push）；启动 Phase 10。9 Minor 入 backlog（export/archive 用 cert:view 授权写语义异味、REISSUED 死枚举、correct 双留痕、reissue→generate 权限耦合、学院复审被 PASSED 吸收、V15 重复 seed cert.* 死号、并发例 50 vs 规格 100、list 非真分页、若干覆盖面）。

## [2026-06-17] Phase 9 待复核小结（T-072~T-079）
- 做了什么：从 `main` 切出 `feature/phase09-T072-certificate`，完成证书域后端与前端。新增 `V15__certificate.sql`（`certificate`、`cert_sequence`、证书状态字典、`cert.seq.scope`/学校码/省码参数）与 `V16__certificate_student_view.sql`（学生 `cert:view` SELF 授权补充）；新增证书实体/Mapper/Service/Controller、证书管理页、证书签发页、API、路由与菜单。
- 关键决策与理由：18 位编号按 `year + cert.school.code + education_level.ext_json.certLevelCode + cert.province.code + teaching_segment.ext_json.certSegmentCode + seq(5)` 生成；序列使用 `cert_sequence` 行、`INSERT ... ON DUPLICATE KEY UPDATE` 初始化、同事务 `SELECT ... FOR UPDATE` 自增并写证，避免 Redis INCR 或 `max(seq)+1` 空号/重号；V15 已本地应用后发现学生查看授权缺口，按 Flyway 不改已发布脚本原则用 V16 补种。
- 业务结果：前置聚合复用 Phase3/4/5/7/8 既有结论与 `AbilityTestResultService.validity()`，缺项返回缺失清单；有效期按签发上下半年文本计算；状态机 C 覆盖生成、签发、导出、归档、作废、重开与 `cert:correct` 更正留痕；`certificate` 接入 `DataScopeSqlHandler`，生成/签发/作废/重开/更正服务层防御为校级范围。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase9 共 40 tests；`Phase9CertificateIT` 7/7 覆盖 AT-09 缺过程性拒绝、AT-10 编号示例/作用域切换/50并发连续不重无空号、AT-11 有效期、锁定后直接重生成拒绝且更正留痕、作废重开、证书读+写数据范围；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 9 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核 AT-09/AT-10/AT-11，未自行置 ✅。

## [2026-06-17] Phase 8 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 8 增量（单提交 `6fca8cb`）。`mvn -B -ntp verify` GREEN **33/33**（`Phase8TestResultIT 4/4` + 回归 Phase2~7 共 29）、前端 `type-check`/`build` 绿；读 V14 + service + 控制器 + 枚举 + IT + 清理两文件；两路独立代理（业务/数据范围/导入 · 质量/迁移/前端/清理）均 PASS。
- 结论：**PASS**（一轮）。免考联动（复用 Phase6 exam-subjects，PASSED 科目剔除应考）、成绩文本 AT-01（`FastExcel useScientificFormat(false)`，21 位前导零往返一致）、结论有效性契约（合格/免考有效，为 Phase9 预留）、确认锁定守卫、免考≠过程性（独立）、读+写数据范围（写侧 collegeId 取自实体、跨院 403、学生无写权）全过。三项跨阶段顺手清理 C1（`ExemptionStatus.of()` fail-closed）/C2（`StudentServiceImpl` 死分支删除）/C3（`material:view` 补种授权）经代理逐条确认落地且回归无破坏。V1–V13/治理未改，PROGRESS 未自 ✅。
- 放行：PROGRESS Phase 8 置 ✅、AT-01 采集侧续接 P8；合并 `main`（本地私有，无远程，不 push）；启动 Phase 9。7 Minor 入 backlog（exam_subjects 快照陈旧、importFile 无行数上限、CSV 公式注入留 Phase10 导出侧、审批留痕工作流前向、material:view 暂无消费方、list 非真分页、validity/importFile 覆盖面）。

## [2026-06-17] Phase 8 待复核小结（T-068~T-071）
- 做了什么：从 `main` 切出 `feature/phase08-T068-test-result`，完成 `V14__test.sql`、`ability_test_result` 实体/DTO/VO/Mapper/Service/Controller、测试结果录入/修改/轻量导入、免考联动应考科目、确认锁定、有效性查询；前端新增测试结果 API、管理页、路由与菜单。
- 关键决策与理由：`score` 全链路 `String/VARCHAR`，只做外层空白规整、不做数值/日期转换；应考科目直接复用 Phase 6 `ExemptionService.examSubjects(studentId, year, segment)`，只按 `finalStatus=PASSED` 剔除免考科目；`ability_test_result` 接入 `DataScopeSqlHandler`，写侧 collegeId 固定取 student 实体并按 `test:edit/import/confirm` 硬校验。
- 跨阶段复检顺手项已清理：`ExemptionStatus.of()` 未知值改 fail-closed 抛错；删除 `StudentServiceImpl.fill` 中 `enforceWriteScope=false && !existing` 的未校验 collegeId 死分支；V14 幂等补种 `material:view` 权限并授予学生/学院/教务处查看范围。
- 与规格的偏差/疑问：全量 26 列预校验/批次/回滚仍按 Phase 10 实现；本阶段提供 JSON 批量导入与 xls/xlsx/csv 轻量文件导入，字段保持文本。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase8 共 33 tests；`Phase8TestResultIT` 4/4 覆盖 B 科免考通过剔除、成绩文本前导零/长串读写一致、待确认有效性为否、确认锁定拒改、免考结论不覆盖过程性、院/本人读范围和跨院写 403；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 8 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核，未自行置 ✅。

## [2026-06-17] Phase 7 复核通过（Claude · REVIEW-GATE，2轮 PASS）✅
- 做了什么：复核 B1/B2 修复增量（单提交 `75b7fa7`，4 文件）。`mvn -B -ntp verify` GREEN **29/29**（Phase7 7/7，新增重传拒绝 + 3 评委结算反例 + 回归 Phase2~6 共 22）；读 B1/B2 diff 与新反例逐条核。
- 结论：**PASS**。B1 重传守卫三入口（`initUpload` 秒传前 / `merge` / `upsertReviewAfterValidation`）：REVIEWING/NEED_REVIEW/已结算/已有任务 拒绝重传，反例证 status/videoFileId/taskCount 不变（不串分、不卡死）；B2 `settleIfReady` 泛化 N 评委（`allPairDiffWithin`+`sameConclusion`），N=2 不变、N=3 结算 82；`of()` 改 fail-closed、`locked()` 不再死代码。未动迁移/治理/已通过逻辑，前端未改。
- 放行：PROGRESS Phase 7 置 ✅、AT-08 首验通过；合并 `main`（本地私有，无远程，不 push）；启动 Phase 8。Minor 维持 backlog；跨阶段复检顺手项并入 Phase 8 派发。

## [2026-06-17] Phase 7 复核退回修复（B1/B2）
- 做了什么：修复 `docs/reviews/phase-07-review.md` 退回项 B1/B2。B1：在 `initUpload` 秒传分支、断点会话入口、`merge` 与 `upsertReviewAfterValidation` 兜底处统一执行视频重传守卫；同一学生/年度已有评审任务，或状态不属于 `WAIT_UPLOAD/VALIDATING/VALIDATION_FAILED/WAIT_REVIEW且无任务` 时拒绝重传，避免 REVIEWING/NEED_REVIEW 被学生再传重置并串用陈旧任务分。B2：`settleIfReady` 去掉两评委硬编码，按 `video.reviewerCount` 个初评做全体两两分差 ≤ 阈值且结论全一致时均分结算，否则进入 NEED_REVIEW，N=2 行为保持不变。
- 关键决策与理由：选择泛化 N 评委结算而不是限制配置，保持 T-060/确认单#11 的“可配多专家”能力；`VideoReviewStatus` 新增 `reuploadable()`，并把 `VideoReviewStatus.of()`/`VideoUploadStatus.of()` 未知值 fail-open 改为抛错，避免脏状态被当作可上传态。
- 问题与解决：新增重传反例最初使用过长 assessmentYear 触发字段截断，已按 V13 字段约束缩短测试年度；业务断言验证 REVIEWING 下 init 秒传与 merge 均拒绝、任务数与视频文件 ID 不变，NEED_REVIEW 下秒传也拒绝。
- 与规格的偏差/疑问：未改已通过的双盲、第三专家两两最小对、数据范围和鉴权逻辑；Minor 中仅低成本收紧 `of()` 未知值容错，其余继续按复核报告入 backlog。
- 测试：`mvn -B -ntp -pl platform-boot -am "-Dtest=Phase7VideoReviewIT" "-Dsurefire.failIfNoSpecifiedTests=false" test` 通过，`Phase7VideoReviewIT` 7/7；`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase7 共 29 tests（Phase7 7/7，含重传拒绝与 3 评委结算新增反例）；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 7 已在 `PROGRESS.md` 重新置「待复核」，提交后交 Claude 只复核 B1/B2 增量 + 回归。

## [2026-06-17] Phase 7 复核退回（Claude · REVIEW-GATE）❌
- 做了什么：独立复核 Phase 7 增量（单提交 `6318470`）。`mvn -B -ntp verify` GREEN（`Phase7VideoReviewIT 6/6` + 回归 Phase2~6 共 22 = 28/28）、前端 `type-check`/`build` 绿；全读 1062 行 `VideoReviewServiceImpl` + 控制器 + 状态机 + V13 + IT；三路独立代理（数据范围 PASS；业务/质量各独立判出 B1/B2）。
- 结论：**退回**。AT-08 头部全过且生命线稳：双盲"提交前互不可见"接口层硬屏蔽（无泄漏路径）、状态机B 分差/结论冲突→需复评、第三专家两两最小对 85/60/81→83、鉴权播放 401/限时预签名/水印、读+写+ASSIGNED 数据范围、大文件不进内存、权限 V8 预种、V1–V12/治理未改。**但 B1（Blocker）重传未挂可编辑态守卫**：REVIEWING/NEED_REVIEW（locked=0）下学生再传/秒传静默重置评审且不清任务 → 以陈旧分结算或永久卡死，击穿"唯一终分"；**B2（Major）`settleIfReady` 硬编码 2** 与可配 `video.reviewerCount` 矛盾（设 3 卡死）。
- 退回处理：PROGRESS Phase 7 置 **复核退回**、不并 main、不置 ✅、AT-08 维持 `[~]`；codex 原分支修 B1/B2 + 补反例（重传被拒/正确重置不串分、N 评委结算），重交后只复核增量+回归。Minor×7 入 backlog（arbitrate 忽略 conclusion、死代码、of() 容错许可态、格式/时长声明可信、list 非真分页、前端 quickHash 整文件+非 MD5、覆盖面）。

## [2026-06-17] Phase 7 待复核小结（T-057~T-067）
- 做了什么：从 `main` 切出 `feature/phase07-T057-video-review`，完成 `V13__video.sql`、分片上传会话/分片/video_review/video_review_task 表，视频上传 init/chunk/merge/progress、秒传/断点续传、MP4/大小/时长校验、评审任务分配、9 维独立评分、分差结算、第三专家/学院仲裁、结果确认、鉴权播放与动态水印；前端新增视频评审页、API、路由与菜单。
- 关键决策与理由：视频业务放 `platform-business` 并复用 `platform-file` MinIO；大文件合并优先 MinIO `composeObject`，测试小分片 fallback 仍按流拼接避免整文件入内存；合格线/分差阈值/评审人数/时长容差/视频上限/仲裁模式/播放过期时间均走 `sys_param`，V13 幂等补种；`video_review`/`video_review_task`/`video_upload_session` 接入 `DataScopeSqlHandler`，评审教师按 `reviewer_id` 只能看自己任务。
- 问题与解决：Phase 1 已存在 `video_score_dimension` 字典项，V13 只补 9 维 `ext_json` 权重而不重复造字典；提交前互不可见在服务层按当前 reviewer 过滤任务详情，管理/结算视图才返回全量评分；播放接口先做登录、权限和数据范围校验，再发限时预签名 URL 与水印信息。
- 与规格的偏差/疑问：IT 按要求不真传 2GB，使用小文件/小分片验证协议、秒传、续传，用元数据边界覆盖格式/大小/时长规则；9 维权重按 plan §15.5 默认值落地，后续学校正式模板可继续覆盖字典扩展字段。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 Phase2~Phase7 共 28 tests；`Phase7VideoReviewIT` 覆盖分片成功/秒传/断点续传、非 MP4/时长超容差拒绝、教师 A 提交后教师 B 看不到 A 分数意见、分差≤阈值结算、分差>阈值需复评、结论冲突需复评、thirdExpert 85/60/81→83、sys_param 改参生效、鉴权播放反例、院/学生/评审教师数据范围与写侧跨院 403；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 7 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核 AT-08，未自行置 ✅。

## [2026-06-17] Phase 6 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 6 增量（单提交 `df13e97`）。`mvn -B -ntp verify` GREEN（`Phase6ExemptionIT 4/4` + 回归 Phase2 2/2 + Phase3 7/7 + Phase4 5/5 + Phase5 4/4 = 22/22）、前端 `type-check`/`build` 绿；读码核 AT-07 五条 + 读写数据范围 + 可编辑态守卫 + 权限种子；三路独立代理复核均 PASS。
- 结论：**PASS**（一轮）。AT-07：三科 PASS/REJECT/FAIL 并存互不影响、漏佐证按科拒提、仅复审通过移出应考（`includedInExam=0` 唯一写在 `secondReview` PASS）、免考不写过程性表（不覆盖）、读+写数据范围（写侧 collegeId 取自 student 实体、跨学生/跨院 403 且不建行）；可编辑态守卫复用；`exemption:*` 权限点 V8 §15.1 预种无缺口；V1–V11/治理文档未动；PROGRESS 未自 ✅。
- 放行：PROGRESS Phase 6 置 ✅、AT-07 首验通过；合并 `main`（本地私有，无远程，不 push）；启动 Phase 7（视频评审，AT-08）。
- backlog（不阻断，8 Minor）：`ExemptionStatus.of()` 未知值 fail-open、`submit` 跳过 locked 检查、`list` 非真分页、404-vs-403 存在性探测、`ExemptionQuery.collegeId` 边界注释、`readPermission()` 口径、退回重提/初审 FAIL 测试覆盖、前端 segment 接线。

## [2026-06-17] Phase 6 待复核小结（T-052~T-056）
- 做了什么：从 `main` 切出 `feature/phase06-T052-exemption`，完成 `V12__exemption.sql`、免考申请/佐证实体 DTO/VO/Mapper/Service/Controller，多科免考申请、每科独立佐证上传/替换/删除/预览、提交、初审/复审三态、复审通过应考口径移出；前端新增免考管理页、API、菜单与路由。
- 关键决策与理由：免考属 `platform-business`，佐证复用 `platform-file` 的 MinIO `FileService`；读侧把 `exemption_request`/`exemption_material` 加入 `DataScopeSqlHandler`，写侧按 `student` 实体归属用 `DataScopeService.resolve(...)` 硬校验，避免 request 覆盖 collegeId；Phase 8 表未建前，以 `included_in_exam` 与 `/api/exemption/exam-subjects` 作为应考科目口径预留。
- 问题与解决：确认单 #12 说明可免科目/免考依据后续模板导入，本阶段在 `V12` 留 TODO 并放入示例字典值用于联调和反例；最初目标 IT 的 helper 重复提交导致状态机正确拒绝，已改为显式 `submit -> firstReview -> secondReview` 流转。
- 与规格的偏差/疑问：无阻塞。`ability_test_result` 仍属 Phase 8，本阶段不建表、不覆盖过程性材料结论，仅提供应考口径接口供 Phase 8 消费。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 7/7 + `Phase4TrainingIT` 5/5 + `Phase5MaterialIT` 4/4 + `Phase6ExemptionIT` 4/4（合计 22 tests）；覆盖 AT-07 多科 A 通过/B 退回/C 不通过互不影响、漏佐证拒提交、通过科目移出应考、不覆盖过程性结论、可编辑态守卫、读写数据范围；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 6 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核，未自行置 ✅。

## [2026-06-17] Phase 5 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 5 增量。`mvn -B -ntp verify` GREEN（`Phase5MaterialIT 4/4` + 回归 `Phase2 2/2`+`Phase3 7/7`+`Phase4 5/5` = 18/18）、`type-check`/`build` 绿；读码核 AT-06 聚合/上传校验/替换锁/数据范围读写/可编辑态守卫回填。
- 结论：**PASS**（一轮）。AT-06（四类缺一/不通过→不合格、全过→合格）、上传限制、替换锁、两级三态审核、process_material 读+写数据范围、MinIO 复用全过；**可编辑态守卫 backlog 跨 material+student+training 真收口**（带回归反例，清除 Phase3/4 同类 Minor）；V11/治理文档未动。
- 放行：PROGRESS Phase 5 置 ✅、AT-06 首验通过；合并 `main`（本地私有，无远程）；启动 Phase 6。
- backlog（不阻断，6 Minor）：类别合格口径是否配 sys_param、material:view 权限点种子、跨院写返 404 vs 403 等。

## [2026-06-17] Phase 5 待复核小结（T-044~T-051）
- 做了什么：从 `main` 切出 `feature/phase05-T044-process-material`，完成 `V11__process_material.sql`、过程性材料实体/DTO/VO/Mapper/Service/Controller，材料上传/预览/替换/删除、初审/复审、AT-06 四类聚合合格判定、批量下载 ZIP+清单；前端新增过程性材料页面与菜单入口。
- 关键决策与理由：材料业务放 `platform-business` 并复用 `platform-file` 的 MinIO `FileService`/预签名/对象读取；上传大小与 MIME 白名单走 `sys_param`，读侧复用 `DataScopeSqlHandler` 新增 `process_material` 表规则，写侧按 student 归属学院/本人硬校验。
- 问题与解决：用户口径中的四类材料名称与现有 `material_category` 字典/phase 文档不完全一致，本阶段按已落库标准字典四类实现并记录口径；批量下载清单采用 ZIP 内 `manifest.csv`，字段覆盖学号/姓名/类别/文件名/状态/审核人/审核时间。附带收口可编辑态守卫：student/training/material 仅 DRAFT/FIRST_REJECTED/SECOND_REJECTED 可写，在审/已通过/locked 拒绝，并补 Phase3/Phase4 反例。
- 测试：`docker compose -f docker-compose.dev.yml up -d` 后，`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 7/7 + `Phase4TrainingIT` 5/5 + `Phase5MaterialIT` 4/4（合计 18 tests）；覆盖 AT-06 缺一/不通过/全通过、上传类型/大小超限、通过后替换拒绝、material 读写数据范围、批量下载清单；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 5 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核，未自行置 ✅。

## [2026-06-16] Phase 4 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：独立复核 Phase 4 增量。`mvn -B -ntp verify` GREEN（`Phase4TrainingIT 4/4` + `Phase3StudentIT 6/6`(含新 confirm 反例) + `Phase2SecurityIT 2/2` = 12/12）、`type-check`/`build` 绿；读码核 `MajorCodeValidator`/`TrainingLinkValidator`/写侧 `allowedCollegeId`/`training_profile` 数据范围规则/Phase3 confirm backlog 修复。
- 结论：**PASS**（一轮）。AT-04（专业代码 0401/0451/0453）、AT-05（学段-学科联动禁自由填、中职类别节点拒绝）、培养目标-实习地点联动、读+写两侧数据范围、状态机A 全过；Phase 3 confirm backlog 已闭环；V10/治理文档未动。
- 放行：PROGRESS Phase 4 置 ✅、AT-04/AT-05 首验通过；合并 `main`（本地私有，无远程）；启动 Phase 5。
- backlog（不阻断）：training/student「可编辑态守卫」统一化（在审/锁定记录禁写非关键字段）+ TrainingStatus.of 未知值应抛错（3 Minor）。

## [2026-06-16] Phase 4 待复核小结（T-039~T-043）
- 做了什么：从 `main` 切出 `feature/phase04-T039-training-profile`，新增 `V10__training_profile.sql`、专业培养信息实体/DTO/VO/Mapper/Service/Controller、`MajorCodeValidator` 与培养目标/学段/学科/实习地点联动校验，`training_profile` 接入 Phase 2 数据权限规则；前端新增专业培养信息表单与菜单路由；顺带修复 Phase 3 backlog：学生 confirm 不再写请求中的 `collegeId`。
- 关键决策与理由：培养信息属于 `platform-business`，写侧用 `DataScopeService.resolve("training:edit"/"training:confirm")` 做硬校验，全校放行、学院仅授权学院、学生仅本人；读侧继续复用 `DataPermissionInterceptor + DataScopeSqlHandler`，`training_profile` 院范围走 `college_id`，本人走 `student_id`；专业/培养目标/学段/地点/学科均从字典与 Phase 1 标准库/配置表取数。
- 问题与解决：首次 `mvn verify` 中 Phase4 测试姓名含 ASCII `A/B`，触发 Phase 3 姓名校验，已改纯中文测试姓名，不改业务规则；本地 V10 已应用后未再修改迁移脚本，避免 Flyway checksum 变化。Phase3 confirm 自助换学院反例已加入 `Phase3StudentIT`。
- 与规格的偏差/疑问：无阻塞。企业实习按培养目标 `secondary_vocational_school_teacher` 限制，海外实习按校内专业名称“汉语国际教育专业”限制；完整中职专业课库仍沿用 Phase 1 确认单#13 的示例库/模板导入口径。
- 测试：`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 6/6 + `Phase4TrainingIT` 4/4（合计 12 tests）；覆盖 AT-04 非法前缀拒绝/合法前缀放行、普通师范试点专业匹配、AT-05 自由填学科拒绝/类别节点拒绝/联动 options、企业/海外地点限制、多培养目标保存、两级审核、锁定拒改、写侧跨院 403、training_profile 列表学院/学生数据范围；`npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过。
- 下一步：Phase 4 已在 `PROGRESS.md` 置「待复核」，交 Claude 按 `docs/REVIEW-GATE.md` 复核，未自行置 ✅。

## [2026-06-16] Phase 3 复核通过（Claude · REVIEW-GATE，2 轮）✅
- 做了什么：第 2 轮复核 Phase 3 退回复修增量。独立 `mvn -B -ntp verify` GREEN（`Phase3StudentIT 5/5` 含新跨院反例 + `Phase2SecurityIT 2/2` 回归）、`npm run type-check` 与 `build` 绿；读码核 `allowedCollegeId` 写侧范围校验、`StudentConfirmRequest`、新反例。
- 结论：**PASS**。M1/M2（create/batchCreate/update 写侧 collegeId 按数据范围硬校验，跨院 403 且不落库/不开账号）+ M3（前端 type-check）已闭环；AT-03/AT-01/读写两侧数据范围/状态机A/字段锁定/脱敏/留痕全过；治理文档与 V9 未动。
- 放行：`PROGRESS.md` Phase 3 置 ✅、AT-03 首验通过；合并 `main`（本地私有，无远程）；启动 Phase 4。
- backlog（不阻断）：confirm 可改本人 collegeId（学生自助换学院）→ 建议快速补丁：confirm 不写 collegeId。

## [2026-06-16] Phase 3 退回复修：写侧数据范围硬校验
- 做了什么：按 `docs/reviews/phase-03-review.md` 只修 M1/M2/M3；`StudentServiceImpl` 在 create/batchCreate/update 写侧用 `DataScopeService.resolve("student:edit")` 做学院范围硬校验，COLLEGE 账号只能写入授权学院，SCHOOL/SYSTEM/LOGIN_ALL 放行；前端 `StudentManageView` 的 `row-key` 补 `Student` 类型。
- 关键决策与理由：校验放在 service 层，确保 create、batchCreate、update 共用同一规则，且 `ensureStudentAccount` 只会基于已校验的 `collegeId` 开通学生账号；学生本人 confirm 不纳入本次 `student:edit` 写侧校验，避免扩大退回范围。
- 问题与解决：原实现读侧 `@DataScope` 已生效，但写侧 `request.collegeId` 可被院级账号直接覆盖，导致跨学院建/迁学生并开账号；新增 `Phase3StudentIT.collegeClerkCannotWriteStudentOutsideAuthorizedCollege` 验证学院A教务员 create/update 指向学院B 被 403 拒绝，且学生和账号均不落库。
- 与规格的偏差/疑问：Minor 未处理，继续进 backlog。`PROGRESS.md` Phase 3 已重新置「待复核」，未置 ✅。
- 测试：`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过；`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 5/5（合计 7 tests）。
- 下一步：交 Claude 只复核 Phase 3 退回复修增量 + 回归。

## [2026-06-16] Phase 3 待复核小结（T-030~T-038）
- 做了什么：从 `main` 切出 `feature/phase03-T030-student-basic-info`，完成 `V9__student.sql`、首个 `platform-business` 业务模块接入、学生基本信息 CRUD/简单批量录入/本人确认、四类证件校验、出生日期一致性、姓名参数化校验、脱敏、两级审核、字段锁定、学生账号自动开通、student 表接入 Phase 2 数据范围拦截器，以及教务员/学生端前端页面与菜单路由。
- 关键决策与理由：`student_no/id_card_no/birth_date` 全链路按 `String/VARCHAR` 处理，出生日期比较只做归一不改存储文本；姓名校验读取 `sys_param.validate.name.mode`；数据范围复用 `DataPermissionInterceptor + DataScopeSqlHandler`，新增 `student` 表规则，COLLEGE 走 `college_id IN (...)`，SELF 走 `student.id = current.studentId`。
- 问题与解决：完整 `mvn verify` 初次发现 Phase 2 旧探针被 Phase 3 集成测试动态创建的学生账号污染，学院教务员探针从期望 1 条变成多条；已在 Phase2/Phase3 IT 中清理 `P3%/00P3%` 测试造数，保证反例相互隔离，不改业务权限逻辑。
- 与规格的偏差/疑问：批量导入按 Phase 3 文档说明留到 Phase 10 标准导入中心；本阶段提供单条录入与学生本人确认。Phase 3 已在 `PROGRESS.md` 置「待复核」，未置 ✅。
- 测试：`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 + `Phase3StudentIT` 4/4；覆盖 AT-03 四类证件正反例、身份证出生日期不一致、港澳通行证/台胞证跳过日期校验、AT-01 前导零/文本出生日期、脱敏/明文权限、锁定拒改/拒删、简单批量录入、学院A/学生本人数据范围；`npm --prefix frontend run build` 通过（仅 Vite 既有 large chunk warning）。
- 下一步：交 Claude 按 `docs/REVIEW-GATE.md` 复核 Phase 3。

## [2026-06-16] Phase 2 复核通过（Claude · REVIEW-GATE）✅
- 做了什么：第 3 轮复核 Phase 2 退回修复增量。独立 `mvn -B -ntp clean package` 绿 + `mvn -B -ntp verify` 绿（Failsafe 自动跑 `Phase2SecurityIT` **2/2 通过**）；读码核 `DataScopeSqlHandler`/拦截器/CORS/Failsafe/CI；DB 核 §15.1 与数据范围真实表隔离。
- 结论：**PASS**。B1 数据范围经 `DataPermissionInterceptor + ParenthesedExpressionList` 在真实 `sys_user/sys_college/sys_major` 表上行级隔离生效（学院A 只见本院、学生只见本人、教务处全校）；M1 CORS 白名单、M3 反例接 CI、M4 探针真实表、M2/m7 均已修；§15.1 零误差、认证安全无回归。
- 放行：`PROGRESS.md` Phase 2 置 ✅ 已复核、AT-13 首验通过；合并 `main`（本地私有，无远程）；启动 Phase 3。轮次1 的 7 个 Minor 转 backlog。

## [2026-06-16] Phase 2 退回复修：学院数据范围真实表过滤
- 做了什么：在上一轮未提交修复基础上，只修 `Phase2SecurityIT.phase2AuthRbacAndDataScope` 第 113 行失败；`DataScopeSqlHandler` 的 COLLEGE 分支改用 JSQLParser `ParenthesedExpressionList` 构造 `IN (...)` 右值，保留 MyBatis-Plus `DataPermissionInterceptor + DataScopeSqlHandler` 方向。
- 关键决策与理由：按要求先临时打印运行期 `scopeType/collegeIds/expression`。诊断结果为 `scopeType=COLLEGE`、`collegeIds=[800000000000000201]`，并非上下文为空或走 `denyExpression`；实际 SQL 片段为 `sys_user.college_id IN 800000000000000201`，少括号导致 MySQL 语法错，接口落为 0 条。
- 问题与解决：将 `InExpression` 右值从裸 `ExpressionList` 改为 `ParenthesedExpressionList` 后，学院教务员按 `college_id IN (800000000000000201)` 过滤，可见学院 A 本院学生且不含学院 B；SELF/SCHOOL/无 `@DataScope` 分支未改。临时 `DATA_SCOPE_DEBUG` 已移除。
- 与规格的偏差/疑问：无业务规格变更。`PROGRESS.md` Phase 2 重新置「待复核」，未置 ✅。
- 测试：`mvn -B -ntp -pl platform-boot -am "-Dtest=Phase2SecurityIT" "-Dsurefire.failIfNoSpecifiedTests=false" test` 通过（2 tests）；`mvn -B -ntp verify` 通过，Failsafe 自动跑 `Phase2SecurityIT` 2/2 通过（clerk=1、不含学院B；student本人=1；academic全校=2）。
- 下一步：交 Claude 只复核 Phase 2 增量 + 回归。

## [2026-06-14] Phase 2 待复核小结（T-024~T-029）
- 做了什么：在 `feature/phase02-T024-authentication` 上完成 Phase 2 剩余任务：认证/JWT/验证码/refresh/失败锁定/首次改密、RBAC 权限校验、数据范围真过滤、系统用户/角色/权限/数据范围接口、前端真实登录/强制改密/动态菜单与账号权限管理页。修复 `@PreAuthorize` 抛出的 `AccessDeniedException` 被兜底为 500 的问题，补 403 映射；修复过期/无效 JWT 在 filter 中抛出 servlet error 的问题；修复 MyBatis-Plus 默认不写 null 导致账号解锁后 `locked_until` 未清空的问题。
- 关键决策与理由：运行期验证不再从 Codex 启动 8080/5173 常驻服务；Phase 2 反例改用 `@SpringBootTest(webEnvironment=RANDOM_PORT)` 集成测试承载，测试进程自然退出，不继承长期 stdout/stderr 管道。
- 问题与解决：错密锁定测试后发现 `test_sys_admin.locked_until` 残留，已在登录成功、锁定过期、改密、重置密码和测试清理中改用 `LambdaUpdateWrapper#set(..., null)` 显式清空。
- 与规格的偏差/疑问：无业务规格变更。按阶段闸门仅将 Phase 2 置「待复核」，不自行置 ✅；AT-13 记为 Codex 自测通过、待 Claude 复核。
- 测试：`mvn -B -ntp -pl platform-boot -am -Dtest=Phase2SecurityIT -Dsurefire.failIfNoSpecifiedTests=false test` 通过（2 tests）；`mvn -B -ntp package` 通过；`npm --prefix frontend run type-check` 通过；`npm --prefix frontend run build` 通过（仅 Vite 既有 large chunk warning）；DB 核验 V7/V8 success、7 角色、52 权限、104 角色权限映射、关键矩阵正反例通过；`Get-NetTCPConnection -LocalPort 8080,5173` 无监听。
- 下一步：提交 Phase 2 待复核版本，交 Claude 按 `docs/REVIEW-GATE.md` 独立复核 AT-13 与 §15.1 矩阵。

## [2026-06-14] 运行期自测防卡死规则与脚本
- 做了什么：确认 Codex/headless exec 会等待子进程 stdout/stderr 管道 EOF；若直接启动 `java -jar`、`vite dev` 等常驻服务，服务继承管道且不退出，会导致 Codex 一直 working。新增 `scripts/dev-serve.sh` / `scripts/dev-stop.sh` 与 PowerShell 等价脚本 `scripts/dev-serve.ps1` / `scripts/dev-stop.ps1`，统一用后台进程、日志重定向、健康检查、PID 文件管理常驻服务。同步更新 `AGENTS.md §6.1` 硬规则与 `HANDOFF.md` 本机操作说明。
- 关键决策与理由：保留 Bash 脚本以匹配 Git Bash 工作流，同时补 PowerShell fallback；本机当前 Git Bash 启动出现 `Bash/Service/CreateInstance/E_ACCESSDENIED`，仅 Bash 脚本无法覆盖 Windows 受限场景。脚本只管理临时目录中记录的命名服务 PID，不扫描和误杀无关进程。
- 问题与解决：本轮曾遗留后端 `java` PID 27544，经 `jcmd VM.command_line` 确认为本仓库 jar 后停止；未确认归属的 `node` 进程没有监听 5173，未做误杀。
- 修正：后续实测表明由 Codex/exec 直接调用 `dev-serve.ps1` 仍会卡住当前命令链路；因此规则已收紧为 **Codex/exec 内禁止启动任何常驻服务或包装启动脚本**，`dev-serve.*` 仅供外部终端/watchdog/Claude 复核环境使用。
- 与规格的偏差/疑问：无业务规格变更；这是执行流程与本机开发安全规则补丁。
- 测试：`dev-serve.ps1` / `dev-stop.ps1` PowerShell AST 解析通过；`dev-stop.ps1 backend/frontend` 在无服务时正确返回；`Get-NetTCPConnection -LocalPort 8080,5173` 无监听。Git Bash 当前因 `E_ACCESSDENIED` 无法执行 `bash -n`，已记录使用 PowerShell fallback。
- 下一步：继续 Phase 2；Codex 内只允许运行会自然退出的一次性命令，`scripts/dev-serve.*` 仅供外部终端/watchdog/Claude 复核环境使用。

## [2026-06-14] Phase 2 中途交接记录（T-024~T-029 未完成）
- 做了什么：继续推进 Phase 2。已在 working tree 落下认证/JWT/验证码/锁定、RBAC 鉴权、`DataScopeContext` 真过滤、系统用户/角色/权限/数据范围接口、`/api/phase2/probe` 探针接口；`mvn -B -ntp -DskipTests package` 通过。为修复本地 Flyway checksum mismatch，执行了 **仅本地开发库** 的 V8 回退（删除 `id>=800000000000000000` 的 RBAC 种子数据 + `flyway_schema_history.version='8'`），随后按最新 `V8__rbac_seed.sql` 重迁移。
- 关键决策与理由：本地 Phase 1 没保留启用学院数据，原先 `V8` 用 `MIN(sys_college)` 绑定测试账号会得到 `NULL`，导致学院范围反例不稳定。已改 `V8__rbac_seed.sql`，新增 Phase 2 专用测试学院 `PHASE2_COLLEGE_A/B`，并将学生/学院教务员/学院负责人/评审教师测试账号显式绑定到学院 A，确保 AT-13 可重复验证。
- 问题与解决：PowerShell / 工具层多次把 `Start-Process` 显示为 `aborted`，但后台 `java` 进程实际已成功启动。已确认后续续做时必须先查 `Get-Process java`、`netstat :8080`、`backend.out.log`，避免重复启动把验证环境搅乱。
- 与规格的偏差/疑问：当前后端主体实现已接近 T-027，但 **仍未完成整阶段反例验证、前端真实登录与系统安全管理页、docs/phase-02 勾选、分任务提交与 Phase 2 待复核收尾**，因此不能标记任何后续任务完成。
- 测试：`mvn -B -ntp -DskipTests package` SUCCESS；`/api/health`=200；`flyway_schema_history` 当前 V8 checksum=`-193563120`；DB 核验 7 角色、52 权限、104 角色权限映射；测试账号 `test_student/test_college_clerk/test_college_auditor/test_review_teacher` 已绑定 `college_id=800000000000000201`；`test_student` 使用 `ChangeMe123!` + 验证码登录成功，返回 `mustChangePwd=true`。
- 下一步：下个 Codex 先跑完 Phase 2 反例（401/403/首次改密/refresh/错密锁定/学生仅本人/学院 A 查不到学院 B），再补前端 `LoginView`、`stores/user.ts`、`router/index.ts`、`MainLayout.vue` 和 T-029 管理页，最后更新 `PROGRESS/docs/phase-02/DEVLOG` 并按任务循环提交。

## [2026-06-14] T-023 RBAC 表
- 做了什么：新增 `V7__rbac.sql`，创建 `sys_user`、`sys_role`、`sys_user_role`、`sys_permission`、`sys_role_permission`、`sys_user_data_scope`；`PROGRESS.md` Phase 2 置为进行中，T-023 置完成；`docs/phase-02-认证与权限.md` 记录 T-023 验收。
- 关键决策与理由：迁移版本严格按磁盘 max+1 使用 V7，未修改 V1~V6；`sys_user` 增加 `must_change_pwd`、`failed_login_count`、`locked_until` 支撑 T-024 首次改密和登录锁定；`sys_role_permission.scope_type` 保存 `SELF/COLLEGE/SCHOOL/SYSTEM/LOGIN_ALL/ASSIGNED`，用于 T-025 解释 §15.1 的本/院/校/系/✓/分配范围。
- 问题与解决：Phase 文档标题仍写“V5/V6 迁移”，与 HANDOFF/任务要求的 V7/V8 冲突；按 AGENTS §4“迁移版本以磁盘 max+1 为准”和本轮用户要求执行，并在记录中说明。Windows 环境仍存在 `Path/PATH` 重复键，`Start-Process` 初次失败；规整当前进程环境后以后端 detached + `backend.out.log`/`backend.err.log` 方式完成启动验证。
- 与规格的偏差/疑问：无阻塞。V8 需包含各角色测试账号，但账号命名/默认绑定学院专业未在规格中定义；后续 T-026 按确认单默认值实现并继续记录。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health` 返回 UP；日志显示 Flyway `Migrating schema teacher_cert to version "7 - rbac"` 且成功；Docker MySQL 只读核验 `sys_user/sys_role/sys_permission/sys_user_data_scope` 存在，`flyway_schema_history` V1~V7 `success=1`；临时后端进程已停止。
- 下一步：T-024 认证：登录/JWT/验证码/锁定。

## [2026-06-14] 待确认事项落地（学校书面确认）+ 迁移版本顺延
- 做了什么：`docs/待确认事项确认单.md` 20 项按学校 2026-06-14 书面确认回填；新增 `V6__confirmed_params.sql`（**唯一取值变更**：`validate.name.mode` strict→loose，确认单#15 放宽，兼容少数民族/外文姓名）；其余 19 项确认采用既有默认值，无需改参数。同步更新 `docs/README.md §6`、`docs/phase-03`（NameValidator 默认 loose）、`HANDOFF.md`（状态/起点/迁移）。
- 迁移版本顺延：发现 `tasks.md`/phase 文档存在版本漂移——codex 早前插入 `V4__region_seed` 使 subject_seed 顺延 V5，但下游 RBAC 仍标 `V5__rbac.sql`（与 subject_seed 冲突），且 student/training/… 全部 off-by-one。本次统一顺延：RBAC→V7、rbac_seed→V8、student→V9、…、notification→V17（磁盘 V1–V6 + 计划 V7–V17 连续无冲突）。并把 `AGENTS.md §4`/`HANDOFF §4` 改为「**迁移版本以磁盘 max+1 为准，文档编号指示性**」，避免再漂移。
- 关键确认结论：证书序列作用域 `SCHOOL_YEAR_SEGMENT`（按学段，与示例一致，解决需求 9.1 文字/示例冲突）；姓名 `loose`；身份证不加 MOD11-2；复审退回「待初审」；学生导入即开通（用户名=学号/初始密码=证件后6位）；部署 docker-compose 校内。待学校后续提供（不阻塞）：完整中职专业课库、免考依据/可免科目清单（#12/#13，模板导入）；性能指标(#19)仍待提供。
- 测试：重打 jar（确认 V6 已打入 `BOOT-INF/classes/db/migration/`）→ 启动 Flyway「Migrating … to version 6 - confirmed params / Successfully applied 1 migration, now at v6」、`flyway_schema_history` V6 `success=1`、DB `validate.name.mode=loose`。✅
- 下一步：交接 codex 从 Phase 2（`V7__rbac.sql`）开始；Claude 阶段复核。

## [2026-06-14] Phase 1 复核通过（Claude · REVIEW-GATE）
- 做了什么：按 `docs/REVIEW-GATE.md` 独立复核 Phase 1（T-011~T-022），产出 `docs/reviews/phase-01-review.md`，判定 **PASS**（无 Blocker、无 Major）。
- 独立复跑：①干净重建 `mvn clean package` 9 模块 SUCCESS + 前端 `type-check`/`vite build` 通过；②子代理按 UTF-8 代码点核对 V3 字典 vs `plan §5.2` → 12 类逐字一致、17 类型、3 类型（免考依据/科目/签发人）正确置空；③启动应用（Flyway 校验 5 迁移、schema v5、`/api/health`=UP）后运行 **15 条反例/关键用例全过**：字典逐字（全角括号）、区划完整文本、学科计数 1/23/28/27、中职类别节点禁选「任教学科类别节点不可选择」、跨学段/自由填写被拒、缓存刷新 rv1→rv2 无残留、重复学院编码被拒「学院编码已存在」。
- 维度结论：D1~D11 全过。
- Minor backlog（不阻断放行）：审计切面前后态字段未填（**Phase 3 开工前必须接入**，AT-12 生命线）；`toMajorVO` N+1；dict 删除计数口径；无 dup-key 专用异常 handler；RegionCascader `check-strategy=all`（UX）；**V5 学科为示例合成、非官方库（上线前以官方库按模板导入替换，确认单#13）**。
- 放行：`PROGRESS.md` Phase 1 → ✅ 已复核；AT-05 首验（P1 基础）记 ✅；main 快进合并至复核提交（本地私有、无远程/不 push）；准予开始 Phase 2。
- 下一步：Phase 2（T-023 RBAC 表起）由 codex 实现，Claude 阶段复核。

## [2026-06-14] Phase 1 待复核小结
- 做了什么：Phase 1（T-011~T-022）全部完成，覆盖字典/区划/任教学科/组织专业/培养目标联动配置的 Flyway 迁移、标准种子、后端接口、缓存、导入器和前端维护页；`PROGRESS.md` 已将 Phase 1 置为“待复核”，未自行标记 ✅。
- 关键决策与理由：所有字典标准值按 `plan.md §5.2/附录B` 和阶段文档核对；学校、省码、培养目标、学段、实习地点均走字典或参数，不在前端硬编码业务值；T-020 区划页因后端仅定义查询接口，按只读维护/查看页交付并记录边界。
- 问题与解决：中职完整专业课库本地无 358 项/107 类别清单，按确认单第 13 项保留模板导入，不伪造完整库；证书序列作用域冲突仍按确认单默认值 `SCHOOL_YEAR_SEGMENT` 处理，已在 Phase 0 参数中落地；Windows `PATH/Path` 重复导致 `Start-Process` 不稳定，验证时已规整环境并记录处理方式。
- 与规格的偏差/疑问：无新增阻塞；待学校后续提供完整中职专业课模板、免考依据/可免科目、签发人等可维护字典内容。
- 测试：T-011~T-022 均已完成对应 `mvn -B -ntp -DskipTests package`、前端 `type-check/build` 或运行验证；Phase 1 文档验收清单已勾选，T-DICT/T-REGION/T-SUBJ/T-GOAL 用例均有正反例记录；临时后端/前端服务已停止。
- 下一步：交给 Claude 按 `docs/REVIEW-GATE.md` 复核 Phase 1；PASS 后再进入 Phase 2。

## [2026-06-14] T-022 学校/学院/专业维护页
- 做了什么：新增 `frontend/src/api/organization.ts` 与 `OrganizationManageView.vue`；接入 `/system/organizations` 路由和“基础数据/组织与专业”菜单；页面支持学校字典展示、学院维护、专业维护、试点标识、年度版本、专业多培养目标和培养目标联动配置编辑。
- 关键决策与理由：学校、培养目标、任教学段、实习地点候选均从字典接口加载，保存时只提交 `item_code`，保持“不硬编码”和“文本化编码”口径；ID 在前端按 `string` 接收，已复核后端 `JacksonConfig` 将 `Long` 序列化为字符串，避免 JS 大整数精度风险；停用学院可查看但前端禁用新增专业入口，后端仍保留硬约束。
- 问题与解决：重启后 Docker 容器未运行，已重新 `docker compose up -d`；PowerShell 环境同时存在 `PATH/Path`，`Start-Process` 会报重复键，后续通过规整环境变量和 Node 绝对路径完成前端 dev 验证。subagent 提醒停用学院仍可发起新增专业，已增加 `canCreateMajor` 和函数入口拦截。
- 与规格的偏差/疑问：无。T-022 不新增 Flyway 和后端代码，复用 T-018 已完成且带 `@AuditLog`/`@DataScope` 的组织接口。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过；启动后 `/api/health`=UP，`/system/organizations` 返回 200，Vite 代理 `/api/dict/school/items` 与 `/api/college` 正常；API 正例验证新增学院/专业、Long id 字符串返回、专业配置 `[primary_school_teacher,junior_middle_school_teacher]` 查询返回两项、联动配置保存与查询成功；反例验证重复学院、重复专业、停用学院下新增专业、中文显示值作为培养目标/学段、默认学段不在 allowed、超长 `yearVersion` 均返回业务错误；临时数据已清理，临时服务已停止。
- 下一步：提交 T-022；执行 Phase 1 待复核闸门。

## [2026-06-14] T-021 任教学科库页 + 选择组件
- 做了什么：新增 `frontend/src/api/subject.ts`、`SubjectSelect.vue` 和 `SubjectManageView.vue`；接入 `/system/subjects` 路由与“基础数据/任教学科库”菜单；页面支持学段/年度/分类/关键词查询、学科选择校验、最近使用、Excel 导入与错误明细展示。
- 关键决策与理由：学段选项从 `teaching_segment` 字典加载，学科候选从 `/api/subject` 加载，前端不硬编码业务标准值；类别节点继续在候选中展示但禁选，和后端 `selectable=false`、`validateSelectable` 硬约束保持一致；最近使用只在通过后端校验后记录。
- 问题与解决：运行验证时先用错中职学段编码 `secondary_vocational`，实际字典/种子编码为 `secondary_vocational_school`；前端实现本身不写死学段编码，验证脚本改用字典标准编码后通过。页面分类名称解析优先使用类别节点，避免同一 `categoryNode` 下具体学科排在前面时显示成子学科名。
- 与规格的偏差/疑问：无。完整中职专业课仍按 T-017 记录的确认单第 13 项，以学校模板导入补齐；T-021 完成 AT-05 的标准库和类别禁选前端基础，后续 Phase 4 还需在培养信息表单中复用并校验。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过；启动后端与 Vite dev，`/system/subjects` 返回 200；幼儿园 1、小学 23、初中 28、高中/中职文化课 27；`keyword=电子商务` 命中 `sv_ecommerce` 与 `sv_cat_finance_commerce`，类别节点 `selectable=false`；分类 `sv_cat_finance_commerce` 返回 4 项；选择 `sv_ecommerce` 校验与最近使用记录成功；选择类别节点、跨学段、自由填写不存在学科均返回业务错误；非法学段 Excel 导入返回 `failCount=1` 且错误定位第 2 行 `segment_code`。
- 下一步：T-022 学校/学院/专业维护页。

## [2026-06-14] T-020 行政区划维护 + 级联组件
- 做了什么：新增 `frontend/src/api/region.ts`、可复用 `RegionCascader.vue` 和 `RegionManageView.vue`；接入 `/system/regions` 路由与“基础数据/行政区划”菜单；页面支持区划下级浏览、代码反查完整文本、路径标签展示和级联选择回显。
- 关键决策与理由：T-020 不新增 Flyway 和后端接口，复用 T-014/T-015 已有只读区划接口；当前后端没有区划新增/编辑/删除/停用接口，因此页面按只读维护/查看口径交付，避免做没有审计日志支撑的前端伪 CRUD；区划代码全链路保持字符串。
- 问题与解决：东莞/中山等无区县子节点的地市会返回空 children，组件在懒加载到空数组时将当前节点置为可选末级，不伪造第三级区划；Naive UI 大 chunk 警告为既有全量组件库打包警告，本任务未扩大处理范围。
- 与规格的偏差/疑问：`tasks.md` 写“维护页”，但 Phase 1 后端接口清单仅有区划查询接口，无写接口；本任务按只读维护/查看页实现，并记录边界，后续如需区划 CRUD 应单独补后端写接口、权限和 `@AuditLog`。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过；启动后端与 Vite dev，`/system/regions` 返回 200；`path?code=440106` 返回 `广东省广州市天河区`；`children?parent=440000` 返回 21 个市，`children?parent=440100` 返回 11 个区且包含 `440106`，`children?parent=441900` 返回空数组；反例 `path?code=449999`、`path?code=44010601`、`children?parent=999999` 均返回业务错误。
- 下一步：T-021 任教学科库页 + 选择组件。

## [2026-06-14] T-019 字典管理页
- 做了什么：新增 `frontend/src/api/dict.ts` 与 `DictManageView.vue`；接入 `/system/dicts` 路由和侧栏菜单；页面支持字典类型/字典项双栏维护、搜索、启停、年度版本、排序、父级编码、扩展 JSON 校验和保存后刷新。
- 关键决策与理由：管理页查询字典项固定传 `onlyEnabled=false`，确保停用项仍可维护；开发态暂不对按钮挂 `v-perm`，因为 Phase 2 前登录页不加载权限，直接挂会隐藏管理按钮；使用现有 Naive UI 和 Axios 封装，不新增前端依赖。
- 问题与解决：运行反例发现后端 `extJson` 非法 JSON 会落到 MySQL JSON 字段并返回 500，已在 `DictServiceImpl` 增加 `objectMapper.readTree` 业务校验，返回“扩展JSON格式不正确”；`NInputNumber` 严格类型可能返回 null，页面表单状态改为非空 UI state，再组装 API payload。
- 与规格的偏差/疑问：无。权限点 `dict:view`/`dict:manage` 的真实按钮权限与菜单权限仍等待 Phase 2 RBAC 接入，本任务只完成页面入口和维护闭环。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过（保留既有 Naive UI chunk 警告）；启动后端与 Vite dev，`/system/dicts` 返回 200，`/api/health` 代理成功；接口验证新增类型/项、重复类型编码、重复 `(typeCode,itemCode,yearVersion)`、非法 `extJson`、有项删除类型被拒、更新项值后缓存刷新、停用后默认查询隐藏且管理查询可见，临时数据已按逻辑删除清理。
- 下一步：T-020 行政区划维护 + 级联组件。

## [2026-06-14] T-018 学校/学院/专业 + 多培养目标 + 联动配置
- 做了什么：新增学院、专业、专业培养目标、培养目标联动配置实体/Mapper/DTO/VO、`OrganizationService` 与 `/api/college`、`/api/major`、`/api/training-goal-config` 接口；专业详情带学院名称与培养目标列表；写接口接 `@AuditLog`，查询接口接 `@DataScope`。
- 关键决策与理由：T-018 不新增 Flyway，复用 T-011 的 V2 表；`training_goal_config` 存字典 `item_code`，展示名由字典翻译；V2 未定义外键和 JSON schema，引用完整性由 service 校验；多培养目标替换采用“更新/恢复现有关系 + 软删移除项”，避免逻辑删除行仍占唯一键导致重复保存失败。
- 问题与解决：subagent 复核发现 `major_training_goal` 先删后插会被 `(major_id, training_goal_code)` 唯一键挡住，已改为读取含 deleted 的历史行后恢复/停用；学院/专业同类逻辑删除唯一键问题改为创建/更新前按含 deleted 口径检测并返回业务错误；`yearVersion` 超过 V2 `VARCHAR(16)` 曾触发数据库截断，已按表结构补 DTO 长度校验。
- 与规格的偏差/疑问：无新规格偏差。DataScope 当前仍是 Phase 0 骨架切面，T-018 已按红线完成标注，真实范围过滤依赖 Phase 2 T-025 落地；中职教师默认学段/实习地点未写种子，联动配置通过接口维护，后续 Phase 4 使用时按字典配置读取。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=UP，Flyway 校验 V1-V5 且无新迁移；接口正例验证新增学院/专业、专业配置 `[primary_school_teacher,junior_middle_school_teacher]` 查询返回两项、重复保存同一组和移除后恢复均通过、保存/查询 `primary_school_teacher` 联动配置可返回默认/可选学段与实习地点；反例验证重复学院编码、重复 `(internal_major_code, yearVersion)`、停用/不存在学院、非法培养目标、默认学段不在 allowed、中文显示值作为学段编码、超长 `yearVersion`、逻辑删除后同编码重建均返回业务错误；审计日志出现 `college:create`、`major:create`、`major:replaceTrainingGoals`、`trainingGoalConfig:save`；临时业务数据接口可见计数为 0。
- 下一步：T-019 字典管理页。

## [2026-06-14] T-017 任教学科种子（示例库）
- 做了什么：新增 Flyway `V5__subject_seed.sql`，预置 `GLOBAL` 年度任教学科示例库：幼儿园 1、小学 23、初级中学 28、高级中学/中职文化课 27、中职专业课示例 10（含 3 个类别节点、7 个具体学科）。
- 关键决策与理由：`subject_code` 使用学段前缀命名，避免同名学科跨学段触发 `(subject_code, year_version)` 唯一键冲突；类别节点 `is_category=1` 且 `category_node=subject_code`，具体学科 `is_category=0` 并归到父类别；种子用 `ON DUPLICATE KEY UPDATE` 保持幂等。
- 问题与解决：本地文档只明确数量和少量示例，没有给出小学 23、初中 28、高中 27 的完整逐项清单，也未给完整中职 358 项/107 类别清单；按确认单第 13 项“以模板为基准导入字典，年度版本”处理，T-017 只作为基础验收示例库，完整库待学校提供模板后通过 T-016 导入器导入。
- 与规格的偏差/疑问：无阻塞。普通学段为满足数量验收补足了示例项；完整中职专业课不伪造为官方完整库。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 Flyway V5 `subject seed` success=1；DB 校验 `kindergarten/primary_school/junior_middle_school/senior_middle_school` 数量为 `1/23/28/27`，中职示例 10 且 3 个类别节点；无 `(subject_code, year_version)` 重复；接口验证幼儿园仅“幼儿园”、初中 28、小学包含 `书法/舞蹈/心理健康教育/体育与健康`、高中包含 `思想政治/通用技术`；`keyword=电子商务` 命中类别与具体学科且类别 `selectable=false`；选择类别节点、跨学段、自由填写不存在学科均返回业务错误，选择 `sv_ecommerce` 成功；临时后端已停止。
- 下一步：T-018 学校/学院/专业 + 多培养目标 + 联动配置。

## [2026-06-13] T-016 任教学科标准库服务 + 导入器
- 做了什么：新增 `TeachingSubject` 实体、Mapper、`TeachingSubjectService` 与 `/api/subject` 接口；实现按学段/关键词/类别/年度查询、FastExcel 导入、可选校验、最近使用记录；导入结果返回 `total/successCount/failCount/errors`。
- 关键决策与理由：接口补可选 `yearVersion` 参数，以满足“学科库支持年度版本”验收；类别节点不从结果中过滤，而是返回 `selectable=false`，让前端置灰，同时后端 `validateSelectable` 和最近使用记录硬拒绝类别节点；最近使用当前不新增表，按 Redis key `subject:recent:{userId}:{yearVersion}:{segmentCode}` 保存，因 T-016 既有 schema 未定义持久化表。
- 问题与解决：父 POM 锁定 `cn.idev.excel:fastexcel:1.1.0`，实际读写 API 位于传递依赖 `fastexcel-core`；已在 `platform-system` 声明 `fastexcel` 并通过编译。PowerShell 直写中文 SQL 会污染编码，运行验证改用 UTF-8 `.xlsx` + 导入接口，并用 DB HEX 确认中文正确。
- 与规格的偏差/疑问：`AGENTS.md` 表格写 EasyExcel/FastExcel 3.x，但父 POM 和 HANDOFF 锁定 FastExcel 1.1.0，本任务按父 POM 锁定版本实现；完整学科数量验收依赖 T-017 `V5__subject_seed.sql`，T-016 先交付服务与导入器。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；临时 Excel 导入 3 条 `T016_IMPORT` 数据成功，HEX 验证“电子商务/电子商务类/幼儿园” UTF-8 正确；错误 Excel 返回“学段非法”和“文件内重复”两条错误且无 `T016_BAD` 入库；`keyword=电子商务` 命中具体学科和类别节点，类别节点 `selectable=false`；跨学段、自由填写不存在学科、类别节点选择/记录最近使用均返回业务错误；`subject:recent` Redis 仅保存具体学科；临时数据、审计日志、Redis key 已清理，临时后端已停止。
- 下一步：T-017 任教学科种子（示例库）。

## [2026-06-13] T-015 行政区划种子
- 做了什么：新增 Flyway `V4__region_seed.sql`，预置广东省县级以上行政区划数据：省级 1 条、地级市 21 条、县级区划 122 条；同步更新 Phase 1 文档验收记录，并将后续任教学科种子版本从 `V4__subject_seed.sql` 顺延为 `V5__subject_seed.sql`。
- 关键决策与理由：区划 `code/parent_code` 全部按字符串写入，保持文本化口径；种子使用 `ON DUPLICATE KEY UPDATE` 保持幂等；东莞市、中山市按县级以上行政区划口径作为无区县级子节点的地级市处理，不伪造第三级节点。
- 问题与解决：本地文档只给出“广东省三级数据完整可联动”和 `440106` 示例，没有完整区划清单；按 T-015 的 GB/T 2260 口径补充广东县级以上数据，并在文档记录直筒子市处理口径。Flyway 版本因 T-015 占用 V4，已同步更新 `tasks.md` 与 Phase 1 文档，T-017 改用 V5。
- 与规格的偏差/疑问：无。东莞/中山无区县级子节点属于县级以上行政区划数据口径差异，已记录在验收说明。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V4 `region seed` success=1；DB 校验广东 `level=1/2/3` 数量为 `1/21/122`；`children?parent=440000` 返回 21 个市，`children?parent=440100` 返回 11 个区且含 `440106/天河区`；`path?code=440106` 返回“广东省广州市天河区”，UTF-8 HEX 为 `E5B9BFE4B89CE79C81E5B9BFE5B79EE5B882E5A4A9E6B2B3E58CBA`；反例 `449999`、`44010601`、`parent=999999` 均返回业务错误；临时后端已停止。
- 下一步：T-016 任教学科标准库服务 + 导入器。

## [2026-06-13] T-014 行政区划三级联动
- 做了什么：新增 `SysRegion`、`SysRegionMapper`、`RegionService`、`RegionController` 与区划 VO；实现 `/api/region/children` 和 `/api/region/path`；补 `CorsConfig` 的 JSON UTF-8 charset，确保 HTTP 中文响应可被客户端正确识别。
- 关键决策与理由：区划 `code/parentCode` 全链路 `String`，避免前导零和编码语义丢失；`parent` 为空查省级，传父级查下级；`path` 拼 root→leaf 的完整文本，供后续生源地导出复用；预留 `validateTriplet` 给 Phase 3/10 生源地校验。
- 问题与解决：初次 HTTP 验证时 PowerShell 将 JSON 中文误解码，数据库 UTF-8 HEX 正确；通过为 Jackson JSON converter 增加 `application/json;charset=UTF-8` 支持解决。
- 与规格的偏差/疑问：无。T-014 使用临时区划数据验证接口，正式广东省种子在 T-015 落地。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；临时插入 `440000/440100/440106` 验证 children 省→市→区县、叶子空列表、path 3 级路径；`fullName` UTF-8 HEX 对应“广东省广州市天河区”；反例 `449999`、`44010601`、`parent=999999` 均返回业务错误；验证数据已清理，临时后端已停止。
- 下一步：T-015 行政区划种子。

## [2026-06-13] T-013 字典标准值种子
- 做了什么：新增 Flyway `V3__dict_seed.sql`，创建 17 个字典类型，预置学校 `10588/广东技术师范大学`、省码 `44/广东`，以及 plan §5.2 明确给出的标准字典项。
- 关键决策与理由：种子使用 `INSERT ... ON DUPLICATE KEY UPDATE`，可重跑且不手改库；`exemption_subject`、`exemption_basis`、`cert_issuer` 仅创建类型、不插业务项，依据确认单第 12 项“免考依据/可免科目：字典维护，初始置空”和 plan 中“学校维护”口径。
- 问题与解决：PowerShell/MySQL CLI 中文比较会受控制台编码影响，逐字一致性由 gpt-5.5/xhigh 只读 subagent 对 UTF-8 文件核对，运行侧改用 type_code/item_code/数量与 HEX 抽查验证，避免编码误判。
- 与规格的偏差/疑问：`plan.md §5.2` 的 `exemption_subject` 行示例写有“幼儿园：综合素质（幼儿园）、保教知识与能力”，但确认单第 12 项裁定初始置空；按确认单默认值实现，后续由学校提供清单维护。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V1/V2/V3 `success=1`；运行侧校验 17 个类型、学校/省码、明确值数量（5/4/4/5/2/5/5/2/2/4/4/9）通过；HEX 抽查确认全角括号与 `课件/板书` 落库。
- 下一步：T-014 行政区划三级联动。

## [2026-06-13] T-012 字典管理 CRUD + Redis 缓存
- 做了什么：新增字典类型/字典项实体、DTO/VO、Mapper、`DictService` 与 `/api/dict` 接口；接入 Redis，`GET /api/dict/{typeCode}/items` 走 `dict:items:{typeCode}` 缓存；维护字典项后删除缓存；写操作接 `@AuditLog`，查询入口接 `@DataScope`。
- 关键决策与理由：缓存用 `StringRedisTemplate` + Jackson JSON，避免引入额外缓存抽象；`onlyEnabled` 默认 true，满足按类型取启用项；有子项的字典类型禁止删除或修改编码，避免产生孤儿字典项。
- 问题与解决：`platform-system` 编译期需要 Redis/Jackson 类型，已在模块 POM 显式声明依赖；一次重打包失败由运行中的 jar 占用导致，停止临时 Java 进程后重新 `mvn package` 通过。
- 与规格的偏差/疑问：无。权限点 `dict:view`/`dict:manage` 认证鉴权将在 Phase 2 RBAC 接入，T-012 先提供接口与切面标注。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；T-DICT-1 接口验证通过：首次查询写缓存、更新后缓存 `1→0`、再查返回 `updated-value` 并重新写缓存；反例：重复字典项、有子项删除类型、有子项修改类型编码均返回业务错误；中文值写入/查询成功；审计日志产生 5 条 dict 记录；验证数据与缓存已清理，临时后端已停止。
- 下一步：T-013 字典标准值种子。

## [2026-06-13] T-011 字典/区划/学科/组织表
- 做了什么：创建分支 `feature/phase01-T011-dict-schema`；新增 Flyway `V2__dict.sql`，建 `sys_dict_type`、`sys_dict_item`、`sys_region`、`teaching_subject`、`sys_college`、`sys_major`、`major_training_goal`、`training_goal_config` 8 张表；更新 `PROGRESS.md` 与 `docs/phase-01-字典与标准数据.md` 的 T-011 验收记录。
- 关键决策与理由：T-011 仅交付表结构，不提前写 V3/V4 种子，避免越界到 T-013/T-017；带年度版本的唯一键统一使用非空默认 `GLOBAL`，避免 MySQL 唯一索引允许多个 `NULL` 导致重复项绕过；学校/专业/区划等代码字段均用 `VARCHAR`，符合文本化红线。
- 问题与解决：验证时发现年度版本若可空会削弱唯一约束，已改为 `NOT NULL DEFAULT 'GLOBAL'`；临时启动后端触发 Flyway 后已停止 8080 进程，避免遗留后台服务。
- 与规格的偏差/疑问：无。完整字典标准值、行政区划种子、任教学科示例库按任务拆分留给 T-013/T-015/T-017。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V1/V2 `success=1`；information_schema 验证 8 张表与关键索引存在；反例：重复 `sys_dict_type.type_code`、重复 `(type_code,item_code,year_version)` 均触发 `ERROR 1062 Duplicate entry`，临时验证数据计数为 0。
- 下一步：T-012 字典管理 CRUD + Redis 缓存。

## [2026-06-13] Phase 0 完成（T-003~T-010）
- 做了什么：
  - DB 链路：MyBatis-Plus(分页/乐观锁/逻辑删除/自动填充) + MySQL + Flyway V1(sys_param/audit_log/file_object + 17 参数种子) + Jackson Long→String。
  - docker-compose.dev（mysql/redis/minio）。
  - platform-file：MinIO 客户端 + FileService(上传/预签名/删除/秒传) + FileController。
  - 切面：AuditLogAspect(boot, 写 audit_log) + DataScopeAspect(security, 骨架) + UserContext(common)。
  - 前端：Vue3+Vite+TS+Naive UI 脚手架（router 守卫 + Pinia + Axios 封装 + v-perm + 登录/首页/404）。
  - CI：GitHub Actions(后端 mvn + 前端 vite build) + .editorconfig + .gitattributes(LF 规范)。
- 验证（全部通过）：`mvn package` 9 模块 SUCCESS；运行 Flyway migrate v1、4 表、sys_param 17 行、/api/health=UP、/doc.html=200；MinIO bucket 自动建 + 文件 上传→预签名→下载 内容一致；前端 `npm install` + `vite build`(2864 模块) 成功。
- 问题与解决：①Lombok optional 不向子模块传递 → 父 POM 统一声明；②MinioConfig @PostConstruct 调 @Bean 循环依赖 → 改独立 client 初始化 bucket。
- 注意：前端 2 个 npm 漏洞(dev 依赖)，后续 `npm audit`；naive-ui 全量导入主包偏大，后续按需引入。
- 下一步：Phase 1 字典与标准数据（T-011 表 → 种子 → 学科库/区划 → 前端字典页）。

## [2026-06-13] Phase 0 · Maven 骨架构建通过（T-001/T-002）
- 做了什么：父 POM + 8 子模块；`platform-common` 核心(Result/ResultCode/PageResult/BizException/BaseEntity/@AuditLog/@DataScope)；`platform-boot`(PlatformApplication/OpenApiConfig/CorsConfig/GlobalExceptionHandler/HealthController/application.yml)。
- 验证：`mvn -B -ntp -DskipTests package` → **BUILD SUCCESS**（9 模块），产出可运行 jar `platform-boot/target/teacher-cert-platform.jar`。
- 关键决策：父 POM 强制 UTF-8；锁定 Spring Boot 3.2.11 / MyBatis-Plus 3.5.7 / Knife4j 4.5.0 / FastExcel 1.1.0 / MinIO 8.5.12 / jjwt 0.12.6。
- 已知告警（无害）：platform-common 仅引 `mybatis-plus-annotation`，javac 提示找不到 `org.apache.ibatis.type.JdbcType`（注解默认值引用），警告非错误；待 system/business 引入 mybatis-plus-starter 后消失。控制台中文告警乱码=GBK 控制台渲染，源码 UTF-8 编译正常。
- 状态：T-001 ✅ / T-002 ✅ / T-003 🟦（boot 起步完成，MyBatis-Plus·Jackson·数据源待续）。
- 下一步：T-003 续(MyBatis-Plus 配置/Jackson Long→String) + T-004 Flyway V1 + T-005 docker-compose；冒烟验证 /api/health 与 /doc.html。

## [2026-06-13] 环境搭建 · 工具链 + git 初始化
- 做了什么：安装 Maven 3.9.9 到 `C:\Users\wenbibuhaoqwq\tools`，配置用户级 JAVA_HOME(Temurin JDK17)/MAVEN_HOME/PATH；`git init` 本地私有仓库(main 分支)，提交规划基线 24 文件(6948682)；新增 `.gitignore`。
- 关键决策：Maven 官方未上架 winget → 用官方二进制 + 用户级环境变量，免管理员；git 仅本地、**无远程**，确保不开源。
- 问题与解决：①winget 无 `Apache.Maven` → 改官方二进制；②安装脚本含 `Remove-Item`+`C:\Program Files` 触发保护拦截 → 删去 `Remove-Item`。
- 注意：JDK 平台默认编码 **GBK**（中文 Windows）→ 父 POM 必须强制 UTF-8（`project.build.sourceEncoding` + 编译器编码），否则中文注释/资源乱码。
- 版本：JDK 17.0.19 / Maven 3.9.9 / git 2.54.0 / Docker 已装 / Node v24.15。
- 下一步：Phase 0 T-001 Maven 多模块骨架 → 验证 `mvn package`。

## [2026-06-13] 规划阶段 · 文档体系建立（非编码）
- 做了什么：完成需求分析 → 产出 `plan.md`（含 §15 二次详查增补）、`tasks.md`（114 原子任务）、`docs/`（README + 待确认事项确认单 + phase-00~14 详细设计与验收）、`AGENTS.md`/`PROGRESS.md`/`DEVLOG.md` 流程文件。
- 关键决策与理由：
  - 证书顺序号作用域默认 `SCHOOL_YEAR_SEGMENT`（与需求 9.1 示例一致），并列入确认单待学校书面确认。
  - ORM 选 MyBatis-Plus、Excel 选 EasyExcel/FastExcel（保文本）、大视频用 MinIO 分片、迁移用 Flyway。
  - 补齐 10 项执行缺口（plan §15）：状态机不合格终止态、权限矩阵、补充表、校验精化、视频结算、导出子表列、导入两步+回滚、账号/年度、M03/M04 两级审核、序列作用域。
- 问题与解决：docx 为二进制，已解压 `word/document.xml` 提取全文分析。
- 与规格的偏差/疑问：见 `docs/待确认事项确认单.md`（20 项）。
- 测试：N/A（规划阶段）。
- 下一步：执行 Phase 0（T-001~T-010）搭建工程骨架；开工前先读 `AGENTS.md`。
