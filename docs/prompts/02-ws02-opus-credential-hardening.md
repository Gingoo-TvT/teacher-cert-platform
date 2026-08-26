# WS-2 · 凭据硬化（prod admin bootstrap + STAFF/学生初始口令） — 提示词（Opus 4.8）

你是执行 **WS-2** 的 opus。**P0**，关闭账号接管窗口。对应 `docs/audit-remediation-plan.md` §2 / 审计 #1 High + #4 Medium（= 之前推迟的 P1-8）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§2 WS-2**。
- `git remote -v` 确认为空 → `git switch -c feature/ws02-credential-hardening`。
- **依赖协同**：与 WS-10（删默认 dev profile）共同保证"误直启 jar 不会落到带默认口令的 dev"。本 WS 先做，WS-10 后补守卫。
- **风格对齐**：新增的强制 secret 走 **prod fail-fast**（缺失即拒启），与已有 `JWT_SECRET` fail-fast 一致。
- **⚠️ 本 WS 可能有迁移（§0 编号规则；见任务①两条路，走迁移才占号）**：库头当前 **V26** → 若走迁移则**取磁盘 max+1**（⚠️ **WS-2(若走迁移)/3/8 都新增迁移、别都假定自己是 V27**；开工 `ls platform-boot/src/main/resources/db/migration | sort -V | tail -1` 复核，主控合并时按先后顺延 V27/V28/V29）。**关键：`admin` 由已应用的 `V8__rbac_seed.sql:246` 创建**（`@rbac_seed_password_hash` = bcrypt(`ChangeMe123!`)、`ENABLED`、`must_change_pwd=1`）、**`V26` 未删它**（Phase 52 视 admin 为生产标准保留）→ 生产现存"公开固定口令超管"即审计 #1。§0 禁止改已应用迁移，故**不能编辑 V8**——用**该新迁移（编号见上）**在生产路径中和 admin 登录（缺 bootstrap secret 时保持不可登录），已知口令 admin **下沉到 `db/testseed/R__testseed.sql`** 仅 dev/test 重建（照 test_* 账号既有处理）。`@rbac_seed_password_hash` 是 admin 与**所有 test_* 共享**的种子哈希——中和只针对 admin 行，**勿动该变量本身**（否则连带废掉 test_* 与 IT）。
- **⚠️ IT 影响先摸底**：**全库 0 个 IT 以 `admin` 登录**（都用 `test_sys_admin`(3008)）→ admin 硬化本身**不动** 119 IT；真正碰 IT 的是**任务 2/3**——`Phase2/Phase13` 间接用 STAFF 创建默认口令、`Phase3/Phase14` 用学生自动开户。故 prod fail-fast **必须保留 dev/test 默认值**（照 JWT dev 默认模式），否则 IT 挂。

## 任务（四处）
1. **prod bootstrap admin 不再是"公开固定口令可登录"**：`admin` 在 `V8:246` 已建、`V26` 未删（**不可编辑已应用迁移**）——**两条路任选**：(a) **新增迁移**（生产路径、编号见上 max+1）把 admin 中和为不可登录；或 (b) **纯 app-bootstrap**：prod 启动时按 `ADMIN_INITIAL_PASSWORD_HASH` 复位 admin、缺则停用/拒启（**免迁移 → 本 WS 不占迁移号，仅 WS-3/8 迁移**）。二者都配 **app 侧 prod fail-fast**（缺 secret 即拒启，风格同 `JWT_SECRET`）；已知口令 admin **只在 `db/testseed/R__testseed.sql`（dev/test）重建**。中和仅针对 admin 行、勿动共享的 `@rbac_seed_password_hash`。
2. **STAFF 创建/重置口令**：`platform-security/.../security/service/SecurityAdminServiceImpl.java`（**按符号定位、勿信行号**：`@Value("${platform.security.initial-password:ChangeMe123!}")` 现 `:65-66`，用于 create `encode(initialPassword)` `:~109`、reset `:~146`）在 **prod 禁止默认值、强制显式注入**；或改为**一次性随机初始口令**，由管理员受控发放（**不落日志**）。**dev/test 默认 `ChangeMe123!` 必须保留**（`Phase2/Phase13` 依赖），仅 prod 缺值 fail-fast。
3. **学生自动开户**：`platform-business/.../student/service/impl/StudentServiceImpl.java`（**按符号**：`initialPassword(idCardNo)` 现 `:398-407` 取证件号后六位；开户开关 `student.autoCreateAccount` 默认 `true` 于 `:367`）改为**默认关闭** `student.autoCreateAccount`，或**随机一次性激活凭证**（不再从 PII 派生）；`must_change_pwd=1` 保留。**⚠️ 直接受影响的 IT（已核实、唯一一处）**：`Phase3StudentIT.createdStudentAccountCanLoginWithInitialPassword`（`:263`，`:270` 用 `permitNo.substring(len-6)` = **证件号后六位登录自动开户账号**）——本 WS 正是要废掉"PII 派生口令 + 默认开户"，故该测试**语义要改、不是保绿**：改成验证新的安全行为（账号以随机一次性凭证/停用态创建、管理员受控发放后可登录，**不再用证件号后六位**）；若保留"可自动开户"分支，须在该测试 `@TestPropertySource` 显式 `student.autoCreateAccount=true` 并断言新凭证机制。**别默默把它改绿或删掉**——这是被测安全语义本身的变更，须诚实体现。
4. **文档**：`README.md`（`:62` 一带现只列 `test_*` 测试账号 + `ChangeMe123!`，**属 dev/test 可保留**）——补"生产 admin 走 `ADMIN_INITIAL_PASSWORD_HASH` bootstrap、不发布可用于生产的初始口令"，把**测试口令与生产 bootstrap 明确分离**成两段。

## 验收（活体，复现→阻断）
- prod profile **未配** bootstrap secret → **启动失败**（贴失败日志）。
- 新建/重置 STAFF 后**不能**用公开默认口令登录（复现被拒）。
- 新导入学生**不能**用证件号后六位登录（复现被拒）。
- 测试/dev 账号仍可登录（含 dev 重建的 `admin`），**`mvn verify` 119 IT 全绿**——admin 硬化零 IT 依赖，重点复看 `Phase2/Phase3/Phase13/Phase14`（STAFF 创建、学生开户在 dev 默认下仍工作）。**验收在 demo 驻留库上照跑**（WS-1 已落地，demo 可常驻）。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ 确认 verify 门禁空闲。
2. `mvn -B -ntp clean verify` 全绿。
3. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-2 条目（带复现→阻断证据）。
4. **单 commit**（`feature/ws02-credential-hardening`）→ **STOP** 交主控复核。**禁止 merge / push**。
