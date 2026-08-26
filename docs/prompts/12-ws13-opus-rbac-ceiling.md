# WS-13 · RBAC 授权天花板——防自提权守卫 — 提示词（Opus 4.8）

你是执行 **WS-13** 的 opus。对应 `docs/audit-remediation-plan.md` §4 WS-13（launch-readiness P1 遗留，审计未单列、经代码核实仍无守卫）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§4 WS-13**；`git remote -v` 空 → `git switch -c feature/ws13-rbac-ceiling`。
- **边界先与用户确认**再落地（下面"做法"是最小安全语义草案，避免误伤既有正常授权流）。

## 现状（设计级授权缺口）
`platform-boot/.../controller/SystemSecurityController.java`（管理写方法**按符号定位**：`resetPassword:~79`、`assignUserRoles:~88`、`assignRolePermissions:~146`、`create/update/deleteUser`、`create/update/deleteRole`；类体 37-162）全部只有 `@PreAuthorize` + `@AuditLog`、**无范围/层级约束**。**⚠️ 真正的赋权逻辑在 `platform-security/.../service/SecurityAdminServiceImpl.java`——守卫加在此服务层**（controller 只挂注解）：`assignRolePermissions`（`:~235-256`）仅校验各 permissionId *存在*、`assignUserRoles→replaceUserRoles`（`:~156/286-300`）仅校验各 roleId *存在*、`resetPassword`（`:~142-152`）仅校验目标存在——无任何"⊆ 授权者 / 层级"检查（grep `天花板|自提权|subset` 全库为空 = 守卫确实缺失）。`SystemManagementServiceImpl`（只管 param/audit/backup）与 `DataScopeServiceImpl`（行范围解析）**不做赋权，勿找错**。
- **两个不同的权、两条向量**：**授权向量**（持 `system:role:manage`）可给自己/任何人授满 SYS_ADMIN 等价权；**改密/停用向量**（持 `system:user:manage`，走 `PUT /user/{id}/reset-pwd`）可重置更高权用户（含 admin）密码。复现账号须**按向量配对的权**（或都给）。
- 当前种子仅 SYS_ADMIN 持这些权（`V8` SYSTEM 范围授予、`V20` 重授时学院级角色 COLLEGE_CLERK/AUDITOR/ACADEMIC_ADMIN **均无** `system:*:manage`）→ **潜伏**，一旦给学院级自定义角色发了即刻可利用。

## 做法（最小安全语义，先与用户确认边界）
1. 授出的**权限/角色集必须 ⊆ 授权者自身有效权限集**（不可越己授予）。
2. 不可编辑/改密/停用**权限严格高于自己**的用户。
3. （可选）角色定义管理限 **SYSTEM 范围**持有者。

## 验收（复现→阻断，新 IT 须自带正反两路）
- **⚠️ 现有 IT 没有任何"经 HTTP 驱动 assign/reset 写"的用例**：全库对 `SystemSecurityController` 的 HTTP 调用只有**读**（`Phase2SecurityIT` list user、`Phase24AcceptanceIT` permission tree；`Phase2SecurityIT.ensureCollegeScopedUserManager` 那个 COLLEGE 范围 `system:user:manage` 账号是**走 mapper 建、只 list 不写**）→ "Phase2SecurityIT 全绿"只证守卫没弄坏读，**不是**正路见证。
- 故**新 WS-13 IT 必须自带两路**：① **正路**：SYS_ADMIN 合法 assign/reset **成功**（守卫不误伤）；② **负路**：低权账号（授权向量给 `system:role:manage`、改密向量给 `system:user:manage`，或都给）尝试给自己授 SYS_ADMIN / 重置 admin → **业务拒绝**。
- 全套 `mvn -B -ntp clean verify` **全绿**（现 119 IT 基线 + **本 WS 新增的正/负路 IT** ⇒ 总数 >119；守卫不误伤既有读路径）。
- **实现注意**（落地须定清，非阻塞）：`@pms`/`PermissionService`（`UserContext.getPermissions()`）只有权限**码**、无 per-perm scopeType → 要"范围感知天花板"须另加载 `sys_role_permission.scope_type`；"严格高于自己"的定义须放行**同权**与 SYS_ADMIN-on-SYS_ADMIN，别锁死正常管理。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行；`mvn -B -ntp clean verify` 全绿（含新复现→阻断 IT）。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-13 条目（带复现→阻断证据）。
3. **单 commit**（`feature/ws13-rbac-ceiling`）→ **STOP** 交主控复核。**禁止 merge / push**。
