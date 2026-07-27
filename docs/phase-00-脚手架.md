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
> 4. **通用 MD5 秒传退役**：规划期 T-FILE-1 把内部摘要字段误写成“同 MD5 二次上传自动去重”。
>    当前通用上传不接收或信任客户端摘要，也没有跨请求唯一约束/并发仲裁；直接按全局 MD5 复用还会暴露
>    其它主体对象是否存在。因此 Phase 0 正式退役通用自动秒传合同及公开摘要命中 API；同内容普通上传
>    保留独立 fileId、objectKey 与元数据行。视频秒传继续严格按 Phase 7 的服务端验真指纹、同 uploader/student、当前策略与对象
>    HEAD 合同执行；本修订不削弱该安全能力。
> 5. **证据范围拆分**：Phase 0 的依赖栈验收以 CI 等价 MySQL/Redis/MinIO 的真实连接 +
>    `docker-compose.dev.yml` 静态解析为本阶段门禁；exact dev Compose 启动属于部署形态复验。
>    前端本阶段只锁定构建、类型、lint 与 Axios/路由静态契约；当前浏览器真实登录 E2E 由 WS-6/最终全量
>    审计承接。下列 `[x]` 不再引用这两项未重跑材料，也不把它们写成当前候选已证明。
> 6. **候选与隔离目标证据**：Phase 0 exact gate 固定 5 个 Surefire + 2 个 Failsafe suite（33 cases），
>    在正式测试前先执行不启动 Spring 的只读身份预检；预检还强制 MySQL 目标 schema 为 0 张表、
>    Redis 目标 DB 的 `DBSIZE=0`，且 MinIO 目标 bucket 除唯一的一次性身份对象外没有其它对象。
>    Redis `INFO server` 按真实协议接受 CRLF/LF 多行载荷：整段只允许非空且禁止 NUL，拆行后的
>    行、key、value 继续执行单行控制字符校验；非注释畸形行与重复 key 一律 fail-closed。
>    gate 在 Windows 的无显式覆盖默认解析 `mvn.cmd`（优先非空 `MVN`），但正式提交命令仍须
>    传入本机绝对 `mvn.cmd`，避免 `shell=False` 对裸 `mvn` 的平台差异。
>    gate 拒绝 datasource/Redis/MinIO 的点号、连字符、下划线、大小写及 camel-case relaxed-binding
>    别名和 JNDI/Hikari/Redis URL、sentinel、cluster，以及外部 Flyway/SQL-init 覆盖等
>    改靶或候选外 SQL 来源；正式 JVM 只接受候选内 dev profile 的四项 Flyway 精确合同
>    （enabled/locations/baseline/encoding），并在首个
>    context refresh/Flyway 前复核空状态，并在每个 context refresh 前用 Spring
>    `ConfigurableEnvironment` 的实际解析值再绑定目标；预签名下载使用的
>    `MINIO_PUBLIC_ENDPOINT` 也必须与 SDK `MINIO_ENDPOINT` 的 scheme/host/port 同靶。MySQL UUID、
>    Redis run_id/db 和 MinIO 一次性对象挑战的 schema-v5 运行期身份必须与预检逐字段一致。
>    MinIO 绑定 canonical endpoint/bucket、唯一 schema-v2 身份对象、对象 SHA、candidate/runContext、
>    nonce、issuedAt 及 0/0/1/0 状态；不依赖 S3 响应中不存在的 deployment header，不接 Admin API，
>    也不生成同源字段的冗余实例指纹。该合同只证明当前配置目标持有本次一次性挑战对象，不宣称唯一
>    MinIO 管理实例 UUID。预检与正式 Maven
>    都只从 `git ls-tree` 固定的 path/mode/blob 清单及 `git cat-file`
>    原始对象物化出的源码快照运行；每个 blob 重算 Git 对象 hash，Linux 同时复核 Git executable bit，
>    既不读取可切换的操作工作树，也不受 `.git/info/attributes` 或 global/system attributes 的 export
>    规则影响。预检结束后先逐文件复核快照并归档预检身份，再删除整个构建树、从同一候选 Git 对象重新
>    物化正式构建树；正式 Maven 结束后再次逐文件复核，任何 preflight 对源码的改写都不能污染正式结果。
>    manifest 与离线 verifier 同时绑定 candidate/tree/blob manifest/file count、
>    start/after-preflight/end HEAD、三段快照证明、marker 与连接配置、gate spec/来源 hash、Windows
>    containment/reparse 边界及双身份原字节；verifier 直接从 expected candidate 的 Git blob 读取
>    gate spec，并要求当前工作树与归档 spec 逐字节相同，未提交的弱化 spec 不能改变证据合同。manifest
>    使用有大小上限、拒绝重复键的严格 JSON，顶层、
>    suite/support/artifact 及其闭合路径集合均须 exact。离线 verifier 在 POSIX 以
>    `openat + O_NOFOLLOW`、在 Windows 以从卷/共享根到 evidence root 的完整无 reparse handle 链
>    捕获一次不可变 evidence snapshot，manifest/hash/JSON/XML/identity/spec 后续只消费该快照。
>    全 artifact 与 manifest 通过 secret scan 后才允许写 `containsSecrets=false`。源码快照清理完成
>    后才捕获最终 artifact snapshot，并在内存虚拟快照中完成完整 PASS 验证；磁盘随后先写校验和、
>    最后原子写入已验证 PASS manifest，最终目录 rename 是最后一个证据状态转换。任一不一致均失败
>    且不得宣称 PASS。preflight/runtime target identity 使用独立于业务 Jackson 配置的证据 mapper，
>    四个 freshness 计数必须写成 JSON integer，禁止继承面向前端的 `Long→String`。

## 1. 目标与范围
- Maven 多模块后端骨架 + Spring Boot 启动。
- Vue3+TS+Naive UI 前端骨架 + 路由/状态/请求封装。
- MySQL/Redis/MinIO 本地依赖（docker-compose）。
- 横切设施：统一响应、全局异常、Flyway、MyBatis-Plus 配置、`@AuditLog`/`@DataScope` 切面、文件服务。

## 2. 交付物
- 可运行 jar（`platform-boot`），非生产 profile 的 `/doc.html` 可访问；生产 profile 关闭文档面。
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
- `@DataScope`：注入 SQL 范围（MyBatis 拦截器或动态条件），现行范围类型为
  `NONE/SELF/COLLEGE/SCHOOL/SYSTEM/LOGIN_ALL/ASSIGNED`；其中 SCHOOL/SYSTEM/LOGIN_ALL 为全校可见，
  NONE 与缺少所需身份/学院集合时失败关闭，Phase 2 接入真实用户范围解析。

## 6. 文件服务（platform-file）
- `FileService`：`upload(stream, meta)`→fileId、`presignedGet(fileId, ttl)`、`delete(fileId)`；
  不公开通用摘要命中 API。
- 通用上传每次创建独立对象/元数据；不得把客户端 MD5 当作跨用户、跨业务复用凭据。需要秒传的业务必须像
  Phase 7 一样显式定义服务端强摘要、主体/业务授权、并发仲裁、对象存在性和生命周期合同。
- 普通上传与分片上传分离（分片在 Phase 7）。MinIO bucket 按 `biz_type` 隔离或统一 bucket + 前缀。

## 7. 前端骨架
- Axios 拦截器：注入 token、统一 `Result` 解包、错误 toast、401 刷新/跳登录、loading。
- 布局：侧栏菜单（动态，Phase 2 接权限）、顶栏（用户/通知/登出）、面包屑、多标签页。
- `v-perm` 指令占位（Phase 2 接权限点）。

## 8. 验收清单

> 2026-07-28 第九轮最终动态证据复核口径：最终 HEAD `ea760bf` 的 Linux/POSIX **79/79**、
> 全新隔离栈 exact **7 suites / 33 testcases**、同 bundle 离线 verifier、Compose 静态解析与
> 专属资源清理五项门禁全部 PASS。证据包 candidate/tree/source/target/checksum/XML 绑定经独立
> 复算成立，`P00-R3-M1` / `P00-R3-L1` 保持 CLOSED，0 finding。Phase 0 / U-004 正式
> **✅ 已复核 PASS**；这只放行最终全量审计，不代表项目发布 GO。

- [x] `mvn -pl platform-boot -am package` 产出 Spring Boot executable jar。——本候选的 9 模块
      package 已通过，fat JAR 内容另有 WS-3 历史门禁；该项只证明打包产物，不把真实依赖应用上下文
      启动混入离线 `[x]`。按 `AGENTS.md §6.1`，Codex 不在 headless exec 直接启动常驻 packaged jar。
- [x] 在 fresh-schema 真实 MySQL/Redis/MinIO 上启动 Spring Boot 应用上下文。——
      `Phase00ScaffoldIT` / `Phase00ParameterMatrixIT` 使用 RANDOM_PORT 启动真实上下文并触发 Flyway；
      第四轮因 Redis INFO 多行解析缺陷、第五轮因 S3 deployment header 假设分别在 preflight FAIL，
      两轮正式均为 0/33；第六轮 exact 7/33 后因 evidence integer 校验 FAIL；第七轮
      `9b97741` 首次 exact 7/33 PASS；第九轮最终 SHA `ea760bf` 在全新 `tcp-phase00-r9` 栈再次
      exact 7/33 PASS，preflight/runtime 双证据均为 0/0/1/0。
- [x] 非生产 `/doc.html` 打开，示例接口可调通，返回统一 `Result`；生产文档面关闭。——
      `Phase00ScaffoldIT.docHtmlAndOpenApiAreServedWithoutAuthentication` 验证 dev `/doc.html` 200 +
      OpenAPI 文档含真实业务路径；`ApiDocumentationSecurityProfileTest` 验证 prod 文档 API/UI/静态资源
      404 且 dev 保持公开；第九轮最终 SHA 的对应 1+2 个 testcase 均在 exact XML 中通过。
- [x] 抛 `BizException` → HTTP 200 + `{code≠0,msg}`；`@Valid` 失败 → 字段级错误。——
      `Phase00ScaffoldIT.bizExceptionReturnsHttp200WithBusinessCode` / `validationFailureReturnsFieldLevelError`。
      未捕获异常合同按 P1-10 修订为 HTTP 500 + 统一 Result（监控可见），由
      `uncaughtExceptionReturnsUnifiedResultWithoutStackTraceLeak` 断言统一体且不泄漏堆栈；
      第九轮最终 SHA 的三个对应 testcase 均通过。
- [x] CI 等价 MySQL/Redis/MinIO services 上后端可连通；`docker-compose.dev.yml` 可静态解析。——
      第九轮 preflight/exact 在 MySQL 8.0.46、Redis 7.4.9 与一次性 MinIO 上 PASS；
      `docker compose -f docker-compose.dev.yml config --quiet` exit 0，专属栈已 `down -v` 零残留。
- [x] Flyway 自动建 `sys_param`/`file_object`/`audit_log`，并生成 `docs/README.md` §6 参数基线。——
      `Phase00ParameterMatrixIT.allDocumentedDefaultsHaveExpectedValueAndType` 在 fresh schema 对全部 **32**
      个 active 参数逐项核对 exact key/value/type，并以全表 key 集合比较拒绝未知项或缺失项；
      第九轮最终 SHA 的 fresh-schema 运行 1/1 PASS。
- [x] 上传任意文件返回 fileId；预签名 URL 限时可访问，过期返回 403/失效。——
      `Phase00ScaffoldIT.uploadWritesAuditRowAndPresignedUrlExpiresAfterTtl`（3 秒 TTL 正向下载逐字节一致 +
      过期后同一 URL 403）在第九轮最终 SHA 的 exact gate 中通过。
- [x] 标注 `@AuditLog` 的样例方法成功后写入 `audit_log`（操作人/时间/IP 不为空）。——同上用例对
      `file/upload` 审计行的三字段非空断言在第九轮最终 SHA 中通过；更丰富链路由
      `Phase13SystemAuditIT` 覆盖。
- [x] `@DataScope` 单测：不同范围生成的 SQL 条件正确。——`DataScopeSqlHandlerTest` 9 例（COLLEGE IN/SELF/ASSIGNED/
      全校无条件/空集合与 NONE fail-closed/未注册表跳过/别名匹配）；`DataScopeMapperChainTest` 5 例
      进一步覆盖 `@DataScope` → context → 生产 MyBatis-Plus data-permission/pagination 插件 →
      Mapper 的 count/data SQL 与结果。
- [x] † 前端生产构建、类型检查与 Axios/路由实现静态契约通过（原「pnpm + mock 登录」口径见文首修订记录 1/2）。——
      `npm run lint`/`type-check`/`build` 每次 CI 执行；该项不再引用历史 Phase 35 材料冒充当前浏览器 E2E。
- [x] † lint：后端 checkstyle（validate 阶段强制）、前端 ESLint 全绿（原「Spotless/Checkstyle/ESLint」口径见文首修订记录 3）。——
      `mvn verify` 内嵌执行；`npm run lint` 进 CI；落地当日即捕获并清除 5 处真实未使用 import 与 3 处前端缺陷。

## 9. 测试用例
- T-AUDIT-1：调用带 `@AuditLog` 的方法 → 断言 `audit_log` 新增一行且字段完整。
- T-DS-1（反例）：现行 `COLLEGE` 集合范围 → 最终 SQL 含 `college_id IN (...)`；
  `SCHOOL` → 不追加范围条件；空 `COLLEGE` / `NONE` → fail-closed。除 handler 分支单测外，必须至少有一条
  `@DataScope` → context → MyBatis data-permission interceptor → 真实 mapper/分页 SQL 或结果的组合链。
- T-FILE-1（R10 修订）：通用自动 MD5 秒传合同及摘要命中 API 已退役；顺序执行两次同内容普通上传，
  断言 fileId、objectKey 与元数据行彼此独立，且公开 `FileService` 不存在摘要查询方法。
- T-FILE-2（边界）：预签名 URL 过期后访问 → 拒绝。
- T-RESP-1：未捕获异常 → 全局处理器返回统一错误码，不泄漏堆栈。

## 10. 风险与注意
- 文本日期字段（出生日期/有效期限）**绝不能**走默认日期序列化——在 Phase 0 就约定它们是 `String`，避免后期返工（影响 AT-01）。
- `@DataScope` 与 MyBatis-Plus 分页插件的拦截器顺序需测试，避免范围条件丢失。
- MinIO bucket、Redis key 前缀、雪花 workerId 等通过配置注入，避免多环境冲突。
