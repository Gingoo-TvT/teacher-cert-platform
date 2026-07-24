# Phase 39 第二轮整改候选提交材料

- 日期：2026-07-24
- 分支：`feature/phase39-college-child-lock`
- Phase 42 PASS 基线：`66fd2a9`
- 第一轮 Phase 39 候选：`34aeec7`
- 第一轮复核结论提交：`1a69c70`
- 第二轮代码候选：`73406ed`
- 第一轮正式报告：`docs/reviews/phase-39-remediation-rereview-2026-07-24.md`
- 第一轮正式结论：**CHANGES_REQUESTED（1 High / 1 Low）**
- 候选状态：**第二轮整改候选完成，待独立增量复核**
- 本材料性质：**第二轮整改候选与开发者自测证据，不是独立 PASS**

## 1. 整改范围

本轮只关闭第一轮报告新增的两项：

1. High：历史跨学院 UPDATE ref 可由 rollback 绕过学院父锁，把 `student`、`training_profile` 或 `certificate` 恢复到已逻辑删除的学院。
2. Low：原并发 IT 在生产 Hook 的 `beforeLock` 处发出 contender 信号，该位置早于 Mapper/JDBC 查询，不能确定证明竞争事务已经进入真实锁查询。

第一轮已确认成立的在线父锁主路径保持不变。无 DDL/Flyway、权限点、角色矩阵、对外 API、前端生产逻辑或业务状态集合变化。

## 2. High 修正：历史恢复目标先锁父级

### 2.1 固定锁序

`ExchangeServiceImpl.rollback` 在锁定 batch 和完整 ref 集后、取得任何业务子行锁之前：

1. 遍历 `student`、`training_profile`、`certificate` 的全部 UPDATE ref；
2. 从 `before_json.collegeId` 解析恢复目标；
3. 对目标学院 ID 去重并按数值升序排列；
4. 依次执行 `deleted=0 FOR UPDATE`；
5. 完成全部有效父锁后才进入业务子行锁与逆序补偿。

固定顺序为：

```text
batch → refs → college IDs ascending → business child
```

Long-as-string JSON、JSON 数字都按十进制精确解析。缺失、非法、非正数、溢出、父学院不存在或已逻辑删除均按对应 ref 记录明确冲突，并在取得业务子行锁前跳过该 ref，禁止把活跃记录恢复到无效父级。

### 2.2 冲突隔离

`CollegeParentGuard.lockStatusForUpdate(..., ROLLBACK_RESTORE)` 在调用方事务内返回可空状态：

- 有效学院：持有父行锁直到 rollback 事务提交；
- 不存在或已删除：返回空并生成单条冲突；
- 不通过业务异常中断整个 rollback，也不把事务标记为 rollback-only；
- 其他有效 ref 仍可继续补偿，最终诚实收敛为 `ROLLED_BACK` 或 `PARTIAL_ROLLBACK`。

### 2.3 删除守卫补全

部分回滚可能因学生快照冲突而只恢复培养信息或证书。为保证等待中的学院删除在取得父锁后能看到并拒绝这些直接子记录，`deleteCollege` 在原专业、用户、学生计数后增加：

- 活跃 `training_profile` 直接计数；
- 活跃 `certificate` 直接计数。

任一计数非零都拒绝删除。因此即使三类 ref 只有一类成功恢复，也不会留下指向已删除学院的活跃记录。

## 3. Low 修正：推进到实际 SQL 执行边界

`Phase39CollegeIntegrityIT` 的 contender 观察由 `CollegeParentLockHook.beforeLock` 移到测试专用 MyBatis `StatementHandler.query` 拦截器：

- 只匹配规范化后的固定 status-only 学院锁 SQL；
- 同时匹配本场景目标 `collegeId`；
- 用 CAS 单次消费，避免同一事务或其他测试 SQL 重复触发；
- 只有胜方已经取得真实父行锁并被冻结后才 arm；
- query-entered 信号在 `invocation.proceed()` 前发出，随后真实查询进入 InnoDB 行锁等待。

测试仍保留 `Future.isDone=false` 作为辅助断言；核心证据是 query-entered 正向屏障、释放胜方后的服务结果、父子终态和 `orphan=0`。

## 4. 动态反例

`Phase39CollegeIntegrityIT` 现为 **11/11**：

1. 原 6 个在线双向交错：专业、STAFF 用户、无自动账号学生分别覆盖子写先赢与删除先赢。
2. 历史学院已删除：三类 UPDATE ref 全部记冲突，学生/培养信息/证书都留在有效学院，无孤儿。
3. rollback 先锁历史学院：学生快照冲突，培养信息与证书恢复；等待中的删除被直接子记录拒绝。
4. certificate-only：学生与培养信息均快照冲突，仅证书恢复；删除明确因“学院下存在证书”失败。
5. delete 先锁历史学院：删除提交后 rollback 的三类 ref 全部 fail closed，无孤儿。
6. 多父目标：ref 按 `A2 / A1 / A2` 逆序且重复出现；事件轨迹证明父学院去重后按 ID 升序锁定，且最后一个父锁完成早于首个 `student/training_profile/certificate FOR UPDATE` 查询。

两个只读交叉审查分别核对生产锁协议和最终测试真实性，结论均为 **0 High / 0 Medium / 0 Low**。

## 5. 门禁结果

### 5.1 聚焦门禁

- `Phase39CollegeIntegrityIT`：**11/11**
- `Phase10ExchangeIT`：**13/13**
- failures / errors / skipped：**0 / 0 / 0**
- 前端 `type-check`、`build`：PASS；本轮无前端文件变化。
- `git diff --check`：PASS。

第一次复跑 Phase 39 时，任务专属临时 MySQL 尚未创建 `teacher_cert` 空库，11 个方法均在 Spring 上下文初始化、任何测试逻辑执行前因 `Unknown database` 停止。补建空库后由 Flyway 从零迁移，同一源码立即重跑 11/11。该环境准备失误不作为测试通过，本文保留过程而不隐藏。

### 5.2 最终全量门禁

最终另换一套全新、任务专属、无现有卷挂载的 MySQL 8 / Redis 7 / MinIO 临时依赖，执行：

```text
mvn -B -ntp clean verify
BUILD SUCCESS
Surefire 149/149
Failsafe 185/185
Total 334/334
Failures 0 / Errors 0 / Skipped 0
Phase39CollegeIntegrityIT 11/11
Phase10ExchangeIT 13/13
33 个 Flyway 迁移资源校验通过，版本化 schema 至 V32
```

全量门禁后未再修改生产或测试源码；已验证的 5 个源文件原样固化为提交 `73406ed`。

## 6. 环境与安全边界

- 未由 Codex/exec 启动常驻后端、前端或包装服务；
- Maven 测试中的随机端口嵌入式服务均随测试 JVM 自然退出；
- 专项与全量各使用一套无持久卷的临时依赖，完成后均停止并自动移除；
- 结束后无 Java 进程，8080/5173 与两套临时依赖端口均无监听；
- 未执行漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz、压力、破坏性故障或任何 cyber 指令。

## 7. 状态边界

- 本候选不修改第一轮正式报告；
- 本候选不自行把 Phase 39 标为 PASS；
- 独立增量复核 PASS 前，Phase 41 继续阻塞；
- 全项目仍因 Phase 0、39、41、44、47、53 保持 **CHANGES_REQUESTED**。

## 8. 独立增量复核请求

请分别冻结：

- 第一轮退回后的最小整改增量：`1a69c70..73406ed`
- 完整 Phase 39 整改范围：`66fd2a9..73406ed`

后续仅用于回填状态与本提交材料的治理文档提交不改变代码冻结点 `73406ed`。

重点核对：

1. 三类 UPDATE ref 的恢复目标是否全部在 child 锁前解析、去重和升序预锁；
2. 非法、缺失或已删除父级是否只形成对应 ref 冲突，且不会写入无效 `college_id`；
3. 部分补偿只恢复 training 或 certificate 时，学院删除是否仍被直接计数拒绝；
4. query-entered 探针是否确实位于 `StatementHandler.query` 执行边界，并避免误触发；
5. `A2 / A1 / A2` 多父用例是否证明去重升序和全部父锁先于 child 锁；
6. 334/334、Phase 39 11/11 与提交 `73406ed` 的源码同源性。

只有新的独立报告给出 PASS，才能关闭 Phase 39 并进入 Phase 41。
