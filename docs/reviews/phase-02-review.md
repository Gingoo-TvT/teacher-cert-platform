# Phase 2 复核报告 — 账号·角色·权限（T-023~T-029）

| 项 | 值 |
|---|---|
| 阶段 | Phase 2 账号·角色·权限 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-15 |
| 被复核提交 | `f529983`（T-024~T-029）、`d07876f`（T-023） |
| 增量基线 | `bd6fd8c..HEAD`（86 文件，+5047/-107） |
| 迁移 | 新增 `V7__rbac.sql`、`V8__rbac_seed.sql`；V1–V6 未改动 ✓ |
| **判定** | **❌ 退回（CHANGES REQUESTED）** |
| 计数 | **Blocker × 1 · Major × 4 · Minor × 7** |

---

## 一、结论

Phase 2 的**权限矩阵（§15.1）实现零误差、认证安全链路干净、学院/专业的真实数据范围过滤正确**——这部分质量很高，且 `Phase2SecurityIT` 反例**经复核方强制执行 2/2 通过**（401/403/锁定/过期/refresh 在真实 DB+种子上成立）。

但 **AT-13（数据范围，项目生命线）未真实落地**：`@DataScope` 注解驱动的**通用数据权限机制实为空操作**（只写线程上下文、不注入任何 SQL）。这违反 Phase 2 既定交付目标"把 @DataScope/DataScopeAspect 骨架变为真实过滤、所有列表/导出/统计查询必须生效"，按 `REVIEW-GATE §5`（红线·数据范围）判 **退回**。另发现反例测试**根本没接进构建/CI**（D8）。

> 退回聚焦 1 Blocker + 4 Major；§15.1 与认证安全无需返工。修复后只复核增量 + 回归。

---

## 二、复核方法（独立验证，不轻信自报）

1. **取增量**：`git diff bd6fd8c..HEAD`（86 文件）。
2. **干净重建**：`mvn -B -ntp clean package` → **BUILD SUCCESS**；前端 `frontend/dist` 已产出（vite build 绿）。
3. **独立重跑反例**：先发现 `package`/`verify` **0 条测试执行**（见 M3）；遂强制 `mvn -pl platform-boot -am test -Dtest=Phase2SecurityIT -Dsurefire.failIfNoSpecifiedTests=false` → **Tests run: 2, Failures: 0**。日志实证命中：匿名→401、无权限→403、首登强制改密、5 次错密→账号锁定、token 过期→401、refresh；Flyway 校验 8 个迁移、schema=v8。**但其数据范围用例运行于 `Phase2ProbeService` 合成数据 + 真实 org 数据**（见 §四 B1/M4）。
4. **读码裁决**：亲核 `DataScopeAspect`/`DataScope` 注解/`MyBatisPlusConfig`/`Phase2ProbeService`/`OrganizationServiceImpl`/`DataScopeServiceImpl` + V7/V8 + §15.1。
5. **三路独立代理**分维度复核（安全 / 授权·数据范围 / 代码质量·DoD·治理文档完整性）。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合性 | ⚠️ | §15.1 矩阵逐格一致（7 角色/52 权限点/104 映射，编码逐字符相符）；数据范围机制偏离"真实过滤"目标 |
| D2 验收清单 | ⚠️ | 认证/RBAC 项可复跑通过；AT-13 数据范围项不达"真实数据生效"标准 |
| D3 AT 验收 | ❌ | **AT-13**：§15.1✓、403✓、学院/专业跨院隔离✓（真实 org 数据）；**通用机制空操作 + 学生隔离仅合成数据** → 不通过 |
| D4 红线合规 | ❌ | "数据范围"红线未真实落地（B1）；其余红线（文本化 String、留痕 @AuditLog、逻辑删除、不硬编码、权限点）合规 |
| D5 代码质量 | ✅ | 分层规范、统一 Result、事务、BaseEntity；少量 Minor |
| D6 安全 | ⚠️ | BCrypt/JWT 外置/验证码/锁定/首登改密/401·403 均正确（IT 实证）；CORS 通配+credentials（M1）待修 |
| D7 构建与运行 | ✅ | `mvn package` 绿、前端 build 绿、应用可起（Flyway v8 校验通过）；codex 未自起常驻服务 |
| D8 测试 | ❌ | 反例存在且强制运行 2/2 通过，**但未接入构建/CI**（无 failsafe、`*IT` 被 surefire 排除，`package`/`verify` 0 执行）→ M3；且数据范围覆盖基于合成数据 |
| D9 数据库 | ✅ | V7/V8 仅新增、版本递增不复用、V1–V6 未改；种子 `ON DUPLICATE KEY` 幂等；表/索引/注释齐 |
| D10 回归 | ✅ | 既有 health/dict/region/subject/org 链路未破坏；mvn 全模块通过 |
| D11 文档/进度 | ⚠️ | PROGRESS 正确置「待复核」(未自置✅)；DEVLOG 有小结；codex 改了治理文档（m1，内容可接受） |

---

## 四、问题清单

| 编号 | 级别 | 维度 | 问题 / 证据 | 期望 |
|---|---|---|---|---|
| **B1** | **Blocker** | 数据范围/红线 | **`@DataScope` 通用机制为空操作**：`DataScopeAspect.around()`（`platform-security/.../aspect/DataScopeAspect.java:31-44`）仅 `resolve()`+`DataScopeContext.set()`+`proceed()`，**不拼接任何 WHERE/SQL**；`alias` 仅出现在 `log.debug`（死参数）。`MyBatisPlusConfig.java:22-23` 只注册分页+乐观锁，**无数据权限拦截器**。全仓 `DataScopeContext.get()` 仅 3 处消费（`Phase2ProbeService:21`、`OrganizationServiceImpl:313/329/347`）。故 dict/region/subject/training-goal/**system-user/role/permission** 列表上的 `@DataScope` **全是空操作**，而注解 javadoc（`DataScope.java:10-11`）声称"由切面注入过滤条件"——名实不符；Phase 3 学生/材料列表挂上即漏全院数据。 | 实现真正强制：(A 推荐) 增 MyBatis-Plus 数据权限 `InnerInterceptor`，读 `DataScopeContext`+`@DataScope.alias` 重写 SQL（`AND {alias}.college_id IN (...)`/本人）；或 (B) 放弃注解驱动，删除名不副实的 `@DataScope`、按 `OrganizationServiceImpl` 模式提供可复用过滤助手 + 更正 javadoc + 文档化。"列表/导出/统计自动生效"须成立。 |
| **M1** | Major | 安全 | **CORS 通配 + 携带凭证**：`CorsConfig.java:25-39` `allowedOriginPatterns("*")` 同时 `allowCredentials(true)` 挂 `/api/**`，任意站点可发起带凭证跨域调用（Bearer 降低影响但仍是配置漏洞）。 | 收敛为可配置白名单（`${CORS_ALLOWED_ORIGINS}`），或仅 Bearer 时 `allowCredentials(false)`。 |
| **M2** | Major | 数据范围 | **admin 列表上的 `@DataScope` 是装饰性的**：`SecurityAdminServiceImpl.listUsers/listRoles/...`（`:65-85`）标了 `@DataScope` 却不读上下文、不过滤；今因相关权限仅 SYS_ADMIN(全校)持有而无泄漏，但给"已生效"的假象，一旦授予院级角色即跨院泄漏。 | 随 B1 一并修：要么真过滤，要么去掉误导注解。 |
| **M3** | Major | 测试/CI | **反例未接入构建**：唯一测试 `Phase2SecurityIT.java` 为 `*IT` 后缀，surefire 默认不收、POM 又无 maven-failsafe-plugin，故 `mvn package`/`mvn verify` **0 条执行**（实测）；只有强制 `-Dtest` 才跑。DoD"CI 绿/反例通过"名不副实，回归无防护。 | 配 maven-failsafe-plugin 并绑 `integration-test`/`verify`（或重命名 `*IT`→纳入 surefire），使 `mvn verify` 自动跑反例；接入 CI（T-010）。 |
| **M4** | Major | AT-13 | **AT-13"学生只见本人"仅合成数据演示**：`Phase2ProbeService.listStudents()`（`:20-61`）内存造两条假学生（本院 + `collegeId+900000` 外院）Java 过滤；V1–V8 无 `student` 表。生命线无法以"真实多租户数据被拦截"签收。 | 机制按 B1 做实后，AT-13 隔离须能作用于真实带租户维度的表；学生表本身可待 Phase 3，但机制必须先真实可用；探针移出 main（m5）。 |
| m1 | Minor | 治理 | codex 改了 `docs/REVIEW-GATE.md`/`AGENTS.md`（加"禁 headless 起常驻服务"防卡死规则）。内容正确、未削弱任何维度/严重度/红线/AT/DoD，但闸门文档应归复核方所有。 | 知会即可；今后治理文档由复核方落。 |
| m2 | Minor | 安全 | 初始/重置口令源码字面量 `ChangeMe123!`（`SecurityAdminServiceImpl.java:61`，yml 未定义该属性故字面量即默认）；有 `must_change_pwd=1` 兜底。 | 走 env/`sys_param` 或随机化。 |
| m3 | Minor | 安全 | 文档示例 JWT 密钥 `0123...`（`AGENTS.md`/`HANDOFF.md`）+ 测试 `Phase2SecurityIT.java:31`。**非生产默认**（`application.yml` `${JWT_SECRET:}` 空默认、`JwtService` 拒空/<32字节）。 | 文档换占位符 + `openssl rand -hex 32` 提示。 |
| m4 | Minor | 安全 | refresh 无轮换/吊销、`logout` 空操作（`AuthService.java:68-75`、`AuthController:51`）。 | 加 jti + Redis 黑/白名单或文档化接受风险。 |
| m5 | Minor | 安全 | 探针端点 `/api/phase2/probe/**`（含 `cert:generate` 越权反例）发布在 main 源码（鉴权正确，非漏洞，但多余攻击面）。 | 移至 test 源或 `@Profile("!prod")`。 |
| m6 | Minor | 代码质量 | `listUsers` 返回 `new PageResult<>(records.size(), records)` 实为不分页全量（`SecurityAdminServiceImpl.java:84`）。 | 用户量上来后改 `Page`。 |
| m7 | Minor | 数据范围 | `DataScopeServiceImpl.java:34-38` 无论 scopeType 都强行把 `current.getCollegeId()` 塞进 `collegeIds`；今无泄漏但脆弱。 | 仅 `scopeType==COLLEGE` 时填充。 |

---

## 五、做得好（无需返工）

- **§15.1 权限矩阵零误差**：恰 7 角色、52 权限点（编码逐字符一致、无缺/多/改/错）、104 角色-权限映射逐格匹配，scope 字母正确（本/院/校/系/✓/分配）；超管 `admin`→SYS_ADMIN + 7 个分角色测试账号；`ON DUPLICATE KEY` 幂等。抽查均对（STUDENT 无 `cert:generate`；CERT_ISSUER 有 `cert:issue`(校)无 `cert:generate`；COLLEGE_CLERK 有 `material:firstReview` 无 `material:secondReview`；SYS_ADMIN 无 `student:view`）。
- **认证安全链路干净（IT 实证）**：口令 BCrypt 加盐、`must_change_pwd=1`；JWT 密钥**未硬编码**（`${JWT_SECRET:}` 空默认 + 运行期拒空/<32字节）、签名+过期+逐请求校验+refresh；验证码服务端单次有效；登录失败锁定走 `sys_param`、计数防篡改；首登强制改密**服务端**强制；401/permitAll/`@PreAuthorize` 模型正确；SQL 全参数化无注入。
- **真实数据范围（学院/专业）正确**：`OrganizationServiceImpl` 按 scope `in(collegeId, scope.collegeIds)` / `id=-1` 拒绝，学院A 的学院/专业列表查不到学院B——AT-13 在真实 org 数据上成立。
- 迁移规范、逻辑删除、@AuditLog 留痕、前端经 api 层 + string ID、真实登录(非 dev-token)。

---

## 六、退回处理

1. 阶段在 `PROGRESS.md` 置 **复核退回**；**不合并 main、不置 ✅**。
2. codex 仅修 **B1 / M1 / M2 / M3 / M4**（Minor 进 backlog，可一并捎带）；在 `DEVLOG.md` 记修复。
3. 重交后复核方**只复核增量 + 回归**，重点复跑：通用 `@DataScope` 在真实表上的隔离、`mvn verify` 自动跑反例、CORS 收敛，直至 AT-13 真实落地后判 PASS。
