# Phase 0 · 工程脚手架与基础设施

> 优先级 P0 · 依赖：无 · 任务：T-001~T-010 · plan §2 / §5.1 / §15.3
> 目标：搭出可启动的前后端骨架与公共设施，后续所有阶段在此之上开发。本阶段不含业务功能，但**审计/数据权限/文件/统一响应**等横切设施必须一次到位。
>
> **验收基线修订（2026-07-27，U-004 Phase 0 退回整改）**：本文件原稿写于项目启动时，其中三处约定已被后续正式演进
> 取代，为可追溯起见在此集中记录替代依据（详见 DEVLOG 同日条目，按 AGENTS.md R10 留痕）：
> 1. **pnpm → npm**：仓库自 T-008 前端脚手架落地起即使用 npm（`frontend/package-lock.json` 为 npm 锁文件，
>    `AGENTS.md §2` 技术栈基线亦锁定 npm），本文件原「pnpm dev」为规划期笔误，未曾成立过。
> 2. **mock 登录 → 真实认证**：Phase 2（T-024）交付真实 JWT 认证后，mock 登录不再存在；Phase 0 验收中「mock 登录
>    走通封装层」的意图（前端请求封装层可用）由现行「真实登录 → Result 解包 → 路由跳转」链路覆盖，
>    由 `Phase2SecurityIT` 与 `Phase14E2EIT` 回归。
> 3. **lint 门禁**：原稿写 Spotless/Checkstyle/ESLint 三件套但从未落地。现按「违反即真实缺陷」的最小口径正式落地：
>    后端 maven-checkstyle-plugin 绑定 validate 阶段（规则 `build/checkstyle/checkstyle.xml`，`mvn verify` 自动执行）；
>    前端 ESLint 9（`frontend/eslint.config.js`，`npm run lint`）；两者均已进入 `.github/workflows/ci.yml`。
>    格式化统一（Spotless 及 vue3 recommended 升级）移交 WS-6 可持续质量门禁，不在 Phase 0 范围内冒充闭环。

## 1. 目标与范围
- Maven 多模块后端骨架 + Spring Boot 启动。
- Vue3+TS+Naive UI 前端骨架 + 路由/状态/请求封装。
- MySQL/Redis/MinIO 本地依赖（docker-compose）。
- 横切设施：统一响应、全局异常、Flyway、MyBatis-Plus 配置、`@AuditLog`/`@DataScope` 切面、文件服务。

## 2. 交付物
- 可运行 jar（`platform-boot`），`/doc.html` 可访问。
- 前端 `npm run dev` 可启动并走通封装层（原稿 pnpm 为笔误，见文首修订记录 1）。
- `docker-compose.dev.yml` 起三依赖。
- Flyway `V1__base.sql`（`audit_log`/`file_object`/`sys_param`）。

## 3. 模块结构（plan §2.4）
```
platform-parent/  (pom，dependencyManagement 锁版本)
├─ platform-common     Result/异常/枚举/常量/BaseEntity/注解
├─ platform-security   认证授权/JWT/RBAC/DataScope（Phase 2 充实）
├─ platform-system     字典/区划/学科/组织/参数/用户/审计/通知（后续充实）
├─ platform-business   学生/培养/材料/免考/视频/测试/证书（后续充实）
├─ platform-exchange   导入导出与预校验（Phase 10）
├─ platform-statistics 统计（Phase 11）
├─ platform-file       MinIO 文件服务
└─ platform-boot       启动/全局配置/Swagger/CORS/全局异常
```

## 4. 数据库（V1__base.sql）
- `sys_param`(id, param_key UNIQUE, param_value, param_type, param_group, description, editable, 审计列) — 种子见 `README.md` §6。
- `file_object`(id, original_name, stored_name, bucket, object_key, size, content_type, md5, biz_type, uploader_id, upload_time, deleted)。
- `audit_log`(id, biz_type, biz_id, target, operator_id, operate_time, comment, old_status, new_status, operation, ip)。
- 索引：`audit_log(biz_type,biz_id)`、`audit_log(operate_time)`、`file_object(md5)`。

## 5. 公共组件规范
- `Result<T>{code,msg,data}`；`PageResult<T>{total,records}`；`ResultCode` 统一码表。
- `BaseEntity`：id(雪花/自增)、created_by/at、updated_by/at、deleted；MyBatis-Plus 自动填充 + 逻辑删除。
- Jackson：`Long`→`String`、日期统一 `yyyy-MM-dd HH:mm:ss`、**业务文本日期字段保持原文本不参与日期序列化**。
- `@AuditLog(bizType, operation)`：AOP 在方法成功后写 `audit_log`，前后状态由返回值/入参解析（提供 `AuditContext` 供业务填充 old/new 状态）。
- `@DataScope`：注入 SQL 范围（`mybatis` 拦截器或动态条件），支持 `SELF/COLLEGE/MAJOR/ALL/SYSTEM`；Phase 2 接入真实用户范围。

## 6. 文件服务（platform-file）
- `FileService`：`upload(stream, meta)`→fileId、`presignedGet(fileId, ttl)`、`delete(fileId)`、`existsByMd5(md5)`（秒传）。
- 普通上传与分片上传分离（分片在 Phase 7）。MinIO bucket 按 `biz_type` 隔离或统一 bucket + 前缀。

## 7. 前端骨架
- Axios 拦截器：注入 token、统一 `Result` 解包、错误 toast、401 刷新/跳登录、loading。
- 布局：侧栏菜单（动态，Phase 2 接权限）、顶栏（用户/通知/登出）、面包屑、多标签页。
- `v-perm` 指令占位（Phase 2 接权限点）。

## 8. 验收清单

> 2026-07-27 复检口径：每项标注当前的可重复证据锚点。带 † 的两项按文首修订记录调整了原口径。

- [x] `mvn -pl platform-boot -am package` 产出可运行 jar，启动无报错。——每次 `mvn verify` 的 9 模块 package 与
      全部 Spring Boot IT（RANDOM_PORT 启动真实上下文）覆盖；fat JAR 内容另有 WS-3 门禁核验。
- [x] `/doc.html` 打开，示例接口可调通，返回统一 `Result`。——`Phase00ScaffoldIT.docHtmlAndOpenApiAreServedWithoutAuthentication`
      （/doc.html 200 + OpenAPI 文档含真实业务路径）；「接口可调通且返回统一 Result」由同类其余用例与全部业务 IT 覆盖。
- [x] 抛 `BizException` → HTTP 200 + `{code≠0,msg}`；`@Valid` 失败 → 字段级错误。——
      `Phase00ScaffoldIT.bizExceptionReturnsHttp200WithBusinessCode` / `validationFailureReturnsFieldLevelError`。
      未捕获异常合同按 P1-10 修订为 HTTP 500 + 统一 Result（监控可见），由
      `uncaughtExceptionReturnsUnifiedResultWithoutStackTraceLeak` 断言统一体且不泄漏堆栈。
- [x] `docker compose -f docker-compose.dev.yml up` 后 mysql/redis/minio 健康，后端连通。——compose 文件即真实
      IT 依赖栈（CI 用等价 services 起同三件），任意一次 verify 全绿即为连通证据；dev/prod compose 另有 `config` 解析门禁。
- [x] Flyway 自动建 `sys_param`/`file_object`/`audit_log`，`sys_param` 含 `docs/README.md` §6 全部默认参数。——
      每次空库 verify 的 Flyway V1 起步迁移 + `Phase13SystemAuditIT`/`Phase44CachingIT` 对 `sys_param` 种子的读写断言。
- [x] 上传任意文件返回 fileId；预签名 URL 限时可访问，过期返回 403/失效。——
      `Phase00ScaffoldIT.uploadWritesAuditRowAndPresignedUrlExpiresAfterTtl`（3 秒 TTL 正向下载逐字节一致 + 过期后同一 URL 403）。
- [x] 标注 `@AuditLog` 的样例方法成功后写入 `audit_log`（操作人/时间/IP 不为空）。——同上用例对 `file/upload`
      审计行的三字段非空断言；更丰富的审计链路由 `Phase13SystemAuditIT` 覆盖。
- [x] `@DataScope` 单测：不同范围生成的 SQL 条件正确。——`DataScopeSqlHandlerTest` 9 例（COLLEGE IN/SELF/ASSIGNED/
      全校无条件/空集合与 NONE fail-closed/未注册表跳过/别名匹配）。
- [x] † 前端 `npm run dev` 启动；真实登录经封装层返回并触发路由跳转（原「pnpm + mock 登录」口径见文首修订记录 1/2）。——
      `npm run build`/`type-check` 每次 CI 执行；登录→跳转链路由 `Phase2SecurityIT` 与浏览器验收（Phase 35 系列）覆盖。
- [x] † lint：后端 checkstyle（validate 阶段强制）、前端 ESLint 全绿（原「Spotless/Checkstyle/ESLint」口径见文首修订记录 3）。——
      `mvn verify` 内嵌执行；`npm run lint` 进 CI；落地当日即捕获并清除 5 处真实未使用 import 与 3 处前端缺陷。

## 9. 测试用例
- T-AUDIT-1：调用带 `@AuditLog` 的方法 → 断言 `audit_log` 新增一行且字段完整。
- T-DS-1（反例）：COLLEGE 范围用户查询 → SQL 含 `college_id = ?`；ALL 范围 → 无范围条件。
- T-FILE-1：同 MD5 二次上传 → 命中秒传不重复存储。
- T-FILE-2（边界）：预签名 URL 过期后访问 → 拒绝。
- T-RESP-1：未捕获异常 → 全局处理器返回统一错误码，不泄漏堆栈。

## 10. 风险与注意
- 文本日期字段（出生日期/有效期限）**绝不能**走默认日期序列化——在 Phase 0 就约定它们是 `String`，避免后期返工（影响 AT-01）。
- `@DataScope` 与 MyBatis-Plus 分页插件的拦截器顺序需测试，避免范围条件丢失。
- MinIO bucket、Redis key 前缀、雪花 workerId 等通过配置注入，避免多环境冲突。
