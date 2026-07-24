# Phase 39 PG-H1 整改候选提交说明

- 日期：2026-07-24
- 分支：`feature/phase39-college-child-lock`
- 基线：`66fd2a9`（Phase 42 PASS 治理提交）
- 原独立报告：`docs/reviews/phase-39-review.md`
- 原 finding：PG-H1 / High / 学院删除与专业、用户、学生创建存在 TOCTOU 竞态
- 候选状态：**整改完成，待独立增量复核**
- 全项目状态：**CHANGES REQUESTED**

## 1. 原问题

项目按既定决策不使用数据库外键，学院与专业、用户、学生之间的引用完整性只能由应用层维护。原实现中：

1. `deleteCollege` 先分别统计专业和用户，再逻辑删除学院；
2. `createMajor`、`createUser`、`createStudent` 只做普通父记录检查或业务校验；
3. 删除事务与子写事务没有锁定同一父行；
4. `student.autoCreateAccount=false` 时，学生不会产生 `sys_user`，仅统计用户不能覆盖真实学生；
5. 标准导入中心直接写 `student`，属于可能绕过学生服务的在线旁路。

因此，删除方完成计数后，另一事务仍可用先前读到的学院写入子记录，最终留下活跃子记录指向已逻辑删除学院。

## 2. 串行化协议

新增集中式 `CollegeParentGuard`，规定所有目标学院子记录新增或迁移事务与学院删除事务锁定同一 `sys_college` 行：

```sql
SELECT status
  FROM sys_college
 WHERE id = ?
   AND deleted = 0
 FOR UPDATE
```

协议要点：

- 查询只投影 `status`，不装载整行；
- `Propagation.MANDATORY` 强制父锁属于调用方写事务，锁持续到子写或删除提交；
- `deleteCollege` 的第一条数据库读取就是父行锁定读；
- 专业要求父学院存在且启用；
- 用户、学生沿用原业务语义：父学院必须存在，但允许处于停用状态；
- 锁顺序统一为 `RBAC 授权锁 → 学院 ID 升序父锁 → 子记录写入`；
- 学生批量先完成所有学院的数据范围校验，再去重、按学院 ID 升序预锁，避免反序批次死锁及越权存在性泄露。

## 3. 生产改动

### 3.1 学院删除

`OrganizationServiceImpl.deleteCollege`：

- 不再先普通读取学院；
- 第一条数据库读取调用 `CollegeParentGuard.lockExisting(..., DELETE)`；
- 父锁内依次统计活跃专业、用户和学生；
- 任一计数非零即拒绝删除；
- 三类计数均为零时才逻辑删除学院。

`SysCollegeMapper` 新增：

- status-only 父行锁定读；
- 按 `college_id` 直接统计活跃 `student`，不依赖自动开户。

### 3.2 专业、用户、学生

- 专业新增/迁移：锁定并校验目标学院启用后再写；
- STAFF 用户新增/迁移：用户管理的 RBAC 全局锁已经是第一条数据库操作，随后锁定目标学院，再写用户及角色关系；
- 学生单条新增/迁移：先取 RBAC 全局锁，经数据范围校验后锁定目标学院，再写学生/可选账号；
- 学生批量新增：一次取得 RBAC 锁，先校验全部学院范围，再按学院 ID 去重升序预锁，随后逐条写入。

### 3.3 标准导入旁路

`ExchangeServiceImpl.importOneInNewTransaction` 的每行 `REQUIRES_NEW` 事务继续先锁导入 batch；在解析并授权目标学院后、任何 `student` 直接写入前，再锁定目标学院父行。

这样标准导入不能绕过 Phase 39 的学院父子完整性边界；原 Phase 42 的 batch-first 并发协议保持不变。

## 4. 确定性交错反例

新增 `Phase39CollegeIntegrityIT`，使用真实 MySQL 8、真实 Spring 事务和真实 `SELECT ... FOR UPDATE`。测试 Hook 只在锁查询前后编排事件，生产实现为空操作。

共 6 个独立测试：

| 子记录 | 子写先取得父锁 | 删除先取得父锁 |
|---|---|---|
| 专业 | 删除等待子写提交，随后因专业计数拒绝；父/子有效 | 专业创建等待删除提交，随后因学院不存在失败 |
| STAFF 用户 | 删除等待用户提交，随后因用户计数拒绝；父/子有效 | 用户创建等待删除提交，随后因学院不存在失败 |
| 无账号学生 | 删除等待学生提交，随后因直接学生计数拒绝；无 `sys_user` | 学生创建等待删除提交，随后因学院不存在失败；无 `sys_user` |

每个场景都断言：

- 服务操作的成功/失败结果与错误域；
- 活跃父记录数；
- 活跃目标子记录数；
- `LEFT JOIN sys_college ... parent.id IS NULL` 的孤儿数为 0；
- 学生场景显式设置并验证 `student.autoCreateAccount=false`；
- 学生场景没有生成 `sys_user`；
- 线程池限时终止、Future 可取消、闩锁在失败路径释放；
- 不使用 `Thread.sleep`。

测试还用反射固定 Mapper SQL 契约：只允许一个 `sys_college FOR UPDATE` 方法，投影必须为 `status`，条件必须含 `deleted=0`，返回类型必须为 `Integer`。

## 5. 门禁结果

### 5.1 聚焦门禁

- 构造器及权限相关单元测试：
  - `CredentialHardeningTest`
  - `SecurityAdminServiceImplTest`
  - `StudentServiceRbacTest`
  - 结果：**28/28**，0 failure/error/skip。
- Phase 39 真实依赖专项：
  - `Phase39CollegeIntegrityIT`
  - 结果：**6/6**，0 failure/error/skip。
- 预提交只读交叉审查：
  - 生产锁协议、MySQL RR 可见性、锁顺序、旁路覆盖、测试真实性及清理路径；
  - 结果：**0 High / 0 Medium / 0 Low**。

### 5.2 最终全量门禁

最终从全新、任务专属且不挂载现有数据卷的 MySQL/Redis/MinIO 临时环境执行：

```text
mvn -B -ntp clean verify
BUILD SUCCESS
Surefire 149/149
Failsafe 180/180
Total 329/329
Failures 0 / Errors 0 / Skipped 0
Phase39CollegeIntegrityIT 6/6
Flyway 33 migrations, versioned schema through V32 plus test repeatable
```

首次全量运行的 329 项中有一个既有 Phase 7 CORS 断言失败：临时 MinIO 启动时漏带项目 `docker-compose.dev.yml` 已定义的 `MINIO_API_CORS_ALLOW_ORIGIN` 白名单，导致 `https://evil.example` 被测试环境错误回显。补齐同一 dev CORS 配置后：

1. 原失败方法隔离重跑 **1/1** 通过；
2. 再从全新 MySQL/Redis/MinIO 状态完整执行，得到上述 **329/329 BUILD SUCCESS**。

这是测试环境配置偏差，不涉及生产源码修改；本报告保留该过程，不隐藏首轮失败。

## 6. 范围与非目标

- 无 DDL/Flyway 变化；
- 无权限点、角色矩阵、状态集合或前端生产逻辑变化；
- 不宣称全库所有含 `college_id` 的表都已取得父锁；本候选关闭的是 PG-H1 明确要求的专业、职工用户、学生及标准导入在线写路径；
- 不自行把 Phase 39 标为 PASS；独立复核通过前，Phase 41 不放行；
- 全项目仍因 Phase 0、39、41、44、47、53 保持 **CHANGES REQUESTED**。

## 7. 环境与安全边界

- 未启动外部常驻后端或前端；
- Maven 测试内嵌随机端口服务均随测试 JVM 自然退出；
- 临时 MySQL/Redis/MinIO 使用内存盘、未挂载现有数据卷，门禁完成后已全部停止并自动移除；
- 结束后无 Java 进程，8080/5173 及临时依赖端口无监听；
- 未执行漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz、压力、故障破坏或任何 cyber 指令。

## 8. 独立增量复核请求

请冻结 `66fd2a9..HEAD`，重点复核：

1. 删除事务第一条数据库读取及 MySQL REPEATABLE READ 可见性；
2. 专业、STAFF 用户、学生单条/批量/迁移和标准导入旁路是否全部使用同一父锁；
3. `RBAC → college → child` 及多学院升序锁顺序；
4. 无自动账号学生是否由直接 `student` 计数保护；
5. 6 个真实 MySQL 双向交错是否能在删除先赢和子写先赢两种线性化顺序下证明 `orphan=0`；
6. 全量 329/329 的提交同源性。

正式 PASS 前，Phase 39 保持“待独立增量复核”，不得推进 Phase 41。
