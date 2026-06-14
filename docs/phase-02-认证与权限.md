# Phase 2 · 账号、角色与权限（M02）

> 优先级 P0 · 依赖：Phase 0、Phase 1 · 任务：T-023~T-029 · plan §3 / §15.1
> 目标：建成 RBAC + 数据范围 + 登录认证，**AT-13（越权拦截）在本阶段首验**。7 角色与 §15.1 权限矩阵在此落地。

## 1. 范围
登录/JWT/验证码/失败锁定/首次改密；用户/角色/权限/数据范围管理；菜单与按钮权限；`@DataScope` 接入真实用户范围。

## 2. 数据库（V7/V8 迁移）
- `V7__rbac.sql`：`sys_user`(username,password_hash,real_name,work_no,status,user_type,college_id,student_id,...)、`sys_role`、`sys_user_role`、`sys_permission`(code,name,type[menu/button/data],parent_id,path,sort)、`sys_role_permission`、`sys_user_data_scope`(user_id,college_id,major_id)。
- `V8__rbac_seed.sql`：7 角色 + §15.1 全部权限点 + 角色-权限映射 + 超管账号 + 各角色测试账号。

## 3. 角色与权限（plan §15.1 矩阵为唯一依据）
7 角色：`STUDENT/COLLEGE_CLERK/COLLEGE_AUDITOR/REVIEW_TEACHER/ACADEMIC_ADMIN/CERT_ISSUER/SYS_ADMIN`。
数据范围标记：`本/院/校/系/✓`（见 §15.1）。种子必须与矩阵逐格一致。

## 4. 接口清单
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/auth/captcha` | 匿名 | 图形验证码 |
| POST | `/api/auth/login` | 匿名 | 账号+密码+验证码 → token |
| POST | `/api/auth/refresh` | 持 refresh | 刷新 token |
| POST | `/api/auth/logout` | 登录 | 登出 |
| POST | `/api/auth/change-pwd` | 登录 | 改密（首次强制） |
| GET | `/api/auth/me` | 登录 | 当前用户+权限点+菜单 |
| CRUD | `/api/system/user` | `system:user:manage` | 用户管理、重置密码、分配角色 |
| CRUD | `/api/system/role` | `system:role:manage` | 角色 + 分配权限 |
| GET | `/api/system/permission/tree` | `system:perm:manage` | 权限树 |
| PUT | `/api/system/user/{id}/data-scope` | `system:user:manage` | 分配学院/专业范围 |

## 5. 核心逻辑
- **登录**：验证码校验 → 用户名查用户 → BCrypt 校验 → 失败计数（`sys_user.failed_login_count/locked_until`，达阈值锁定 N 分钟）→ 签 JWT（含 userId/roles）+ refresh。`status=锁定/停用` 拒登。
- **首次改密**：`must_change_pwd=1` 时除改密接口外其它一律拦截。
- **鉴权**：`@PreAuthorize("@pms.has('cert:generate')")` 基于权限点；`me` 返回权限点集合供前端 `v-perm` 与菜单。
- **数据范围**：`@DataScope` 从当前用户解析范围 → SELF=本人(student_id)、COLLEGE=college_id、MAJOR=授权专业集合、ALL/SYSTEM=不限；注入到业务查询 SQL。

## 6. 前端
- 登录页（验证码、首次强制改密弹窗）。
- 用户管理、角色与权限分配（权限树）、数据范围分配。
- 动态菜单（按 `me` 权限渲染）、`v-perm` 控制按钮。

## 7. 验收清单（AT-13）
- [x] 7 角色与 §15.1 矩阵逐格一致（抽查：学生无 `cert:generate`；签发人有 `cert:issue` 无 `cert:generate`；教务员 `material:firstReview` 有、`material:secondReview` 无）。
- [x] 连续错误密码达阈值 → 账号锁定且提示，锁定期内正确密码也拒登。
- [x] token 过期 → refresh 成功续期；refresh 失效 → 前端清 session 并跳登录。
- [x] 首次登录强制改密，未改密访问其它接口被拦截。
- [x] 无权限点接口返回 403；前端无权限菜单/路由被守卫拦截。
- [x] **数据范围生效**：学院A教务员查询学生列表只见本院数据（构造学院B数据做反例）。
- [x] 学生账号只能查到本人数据（反例：访问他人 id 返回 403/空）。
- [x] 教务处/签发人可见全校；系统管理员可管账号但**业务结论接口对其关闭或仅留痕**。

## 8. 测试用例
- T-AUTH-1：错误验证码 → 拒登，不校验密码。
- T-AUTH-2：连续 5 次错密 → 第 6 次锁定提示；正确密码仍拒。
- T-AUTH-3：token 过期 + 有效 refresh → 自动续期透明重试。
- T-RBAC-1（反例）：学生调用 `POST /api/cert/generate` → 403。
- T-DS-1（反例）：学院A教务员查学院B学生 → 数据为空/403。
- T-DS-2：教务处查询 → 返回全校。

## 9. DoD
登录认证 + 7 角色矩阵 + 数据范围 + 菜单/按钮权限全链路可用；AT-13 自测通过并归档。

> 2026-06-14 Codex 自测已通过，阶段状态置「待复核」；AT-13 等待 Claude 按 `docs/REVIEW-GATE.md` 独立复核后才置 ✅。

## 10. 任务验收记录
### T-023 RBAC 表
- [x] 新增 `V7__rbac.sql`，版本号按磁盘 max+1 从 V7 起，未修改 V1~V6。
- [x] 建表覆盖 `sys_user`、`sys_role`、`sys_user_role`、`sys_permission`、`sys_role_permission`、`sys_user_data_scope`，字段与 `plan.md §5.1` 对齐。
- [x] `sys_user` 预留 `student_id`、`college_id`、`must_change_pwd`、`failed_login_count`、`locked_until`，支撑 T-024 登录锁定与首次改密。
- [x] `sys_role_permission.scope_type` 记录 §15.1 矩阵范围（SELF/COLLEGE/SCHOOL/SYSTEM/LOGIN_ALL/ASSIGNED），支撑 T-025 数据范围解释。
- [x] `mvn -B -ntp -DskipTests package` 通过；启动应用后 Flyway 从 V6 迁移到 V7，`flyway_schema_history` V7 `success=1`。

### T-024 认证：登录/JWT/验证码/锁定
- [x] 新增 Spring Security/JWT/BCrypt/验证码服务，匿名放行 `/api/auth/captcha|login|refresh`，其它接口需 access token。
- [x] `POST /api/auth/login` 返回 access/refresh token、当前用户、角色、权限、`mustChangePwd`；`GET /api/auth/me` 返回当前用户权限集合。
- [x] 连续 5 次错密触发锁定，锁定期内正确密码仍拒登；登录成功、锁定过期、改密、重置密码均显式清空 `locked_until`。
- [x] 首次登录强制改密：除 `/api/auth/change-pwd`、`/api/auth/logout`、`/api/auth/me` 外访问业务接口返回 403。
- [x] `Phase2SecurityIT` 覆盖未登录 401、错密锁定、首次改密、改密后登录、access token 过期 401、refresh 成功和 refresh 类型错误 401。

### T-025 授权：RBAC + 数据范围落地
- [x] `@PreAuthorize("@pms.has('...')")` 基于当前用户权限点校验，缺权限返回 HTTP 403。
- [x] `AccessDeniedException` 在 MVC 内统一映射为 403 `ResultCode.FORBIDDEN`，避免落入兜底 500。
- [x] `DataScopeAspect` 接入 `DataScopeService`，按角色权限范围解析 SELF/COLLEGE/SCHOOL/SYSTEM/LOGIN_ALL/ASSIGNED 并写入 `DataScopeContext`。
- [x] `/api/phase2/probe/*` 探针验证学生仅本人、学院仅本院、教务处全校、系统管理员不具备业务学生查看权限。

### T-026 角色与权限点种子（§15.1 矩阵）
- [x] 新增 `V8__rbac_seed.sql`，幂等写入 7 角色、52 权限点、104 角色权限映射、超管账号和 7 个测试账号。
- [x] 种子新增 `PHASE2_COLLEGE_A/B` 固定测试学院，测试学生与学院角色绑定学院 A，保证 AT-13 反例可重复。
- [x] DB 核验：V7/V8 `success=1`，V8 checksum `-193563120`；抽查 `ACADEMIC_ADMIN cert:generate SCHOOL`、`CERT_ISSUER cert:issue SCHOOL`、`COLLEGE_CLERK material:firstReview COLLEGE`、`STUDENT student:view SELF`。
- [x] 反例核验：`STUDENT cert:generate`、`CERT_ISSUER cert:generate`、`COLLEGE_CLERK material:secondReview`、`SYS_ADMIN student:view` 均无映射。

### T-027 用户/角色/权限/数据范围管理接口
- [x] 新增 `/api/system/user` 用户增删改查、重置密码、分配角色、分配数据范围接口，写接口接权限校验和事务。
- [x] 新增 `/api/system/role` 角色增删改查、角色权限分配接口；`/api/system/permission/tree` 返回权限树。
- [x] 分配用户角色和数据范围采用逻辑禁用旧关系 + upsert 新关系，立即影响 `me` 权限与 `DataScopeService` 解析。

### T-028 登录页
- [x] `frontend/src/views/LoginView.vue` 接入真实验证码、登录接口、首次强制改密弹窗和登录后重定向。
- [x] `frontend/src/stores/user.ts` 存储 access/refresh token、当前用户、角色权限；`frontend/src/api/request.ts` 注入 Bearer token 并在 401 时尝试 refresh，失败后清 session 跳登录。
- [x] `frontend/src/router/index.ts` 基于 `me` 初始化权限并按路由 `meta.perms` 守卫。

### T-029 用户/角色/权限/数据范围管理页
- [x] 新增 `frontend/src/api/security.ts` 与 `frontend/src/views/system/SecurityManageView.vue`，覆盖用户、角色、权限树、角色授权、用户数据范围分配。
- [x] `frontend/src/layouts/MainLayout.vue` 菜单按当前用户权限动态过滤，新增“系统管理 / 账号权限”入口。

### Phase 2 自测证据（2026-06-14）
- [x] `mvn -B -ntp -pl platform-boot -am -Dtest=Phase2SecurityIT -Dsurefire.failIfNoSpecifiedTests=false test`：2 tests passed（随机端口 SpringBootTest，自然退出，不启动常驻 8080 服务）。
- [x] `mvn -B -ntp package`：9 模块 BUILD SUCCESS。
- [x] `npm --prefix frontend run type-check`：通过。
- [x] `npm --prefix frontend run build`：通过，仅保留 Vite 既有 large chunk warning。
- [x] `docker exec tcp-mysql ...` DB 核验：7 roles / 52 permissions / 104 role_permissions；测试账号 `locked_until`、`last_login_at` 已清理为 NULL。
- [x] `Get-NetTCPConnection -LocalPort 8080,5173`：无监听；Codex 未启动常驻后端/前端服务。

## 11. 风险
- `@DataScope` 必须覆盖**所有**业务列表/导出查询，遗漏即越权（贯穿后续每个 Phase 的回归项）。
- 权限点命名一经定稿（§15.1）即冻结，后续 Phase 引用同一套编码，避免分叉。
