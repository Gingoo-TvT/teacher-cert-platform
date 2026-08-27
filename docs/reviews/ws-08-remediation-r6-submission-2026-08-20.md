# WS-8 R6 Phase 10 业务数据整改提交（2026-08-20）

> 性质：整改者提交材料，不是独立复核报告，不自行签发阶段 PASS。

## 1. 正式退回输入与身份绑定

- R5 manifest：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-whole-workflow-remediation-r5-candidate-2026-08-14.json`
- R5 fingerprint：`c649a065b47eea98cb20aef037e9078e8e59fe2c0c1b8aa0d9cc999de1e4d601`
- R5 manifest SHA-256：`b1571be874e1e9deadee2cb26d6c9b58f1579937ebbcb28219ff843164a1d3ee`
- base：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- carrier / tree：`6f6c678a9da3b9a22da97eae53ca1e8f0df759f3` /
  `9dad85a2360732970fcf420c70e2fe438fe59879`
- Draft PR #4 synthetic merge：`115e8ea1545200586a7f0487c23abdb35cd99c51`
- Hosted run：`31808005960 / attempt 1`，4/4 jobs success；六套历史测试 46/46。
- R5 Hosted 证据包：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\ws08-r5-hosted-evidence-20260814-c649a065-run31808005960`
  - 根 `SHA256SUMS` 自身 SHA-256：`4ad9e7c4f8c4e48cbf59e95b4a6b7e8ec16e034544555dc067e6e071dd654869`，
    20/20 exact closure；
  - 嵌套 `supply-chain-artifact/SHA256SUMS` 自身 SHA-256：
    `cb974c4711d7e5e4beda96a2767407dcabe65662792e627802f530bedc3ffc35`，3/3 exact closure。

正式功能独立复核报告：
`C:\Users\wenbibuhaoqwq\Documents\脚本\ws08-r5-functional-independent-rereview-20260820-c649a065-run31808005960\review.md`
（SHA-256 `d849887762ae8ee7cc5eaa9b21010c7216ba5a49e555722534560eae3f4dc493`）。报告判定：

- R5 scoped contract remediation：PASS；
- R5 Hosted evidence：PASS；
- WS-8 full stage：`CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 1 Low）`；
- WS-9：BLOCKED；项目：NO-GO。

## 2. 正式 findings

| Finding | 严重度 | 根因 | R6 关闭方式 |
|---|---|---|---|
| WS8-R5-DATA-M2 | Medium | UPDATE 回滚使用非空字段更新策略，before 值为 NULL 时没有写回，却仍报告成功 | 三类实体使用显式字段 UPDATE 还原完整 before 快照；影响行数必须恰为 1，否则该 ref 记冲突 |
| WS8-R5-FUNC-M1 | Medium | V-13 在 `IdCardValidator` 规范化前统计文件内重复，x/X、a/A 等价号码可能漏报 | 计数与逐行判断均直接复用同一个 `IdCardValidator` 规范值 |
| WS8-R5-STAT-L1 | Low | `failCount` 使用错误明细条数，单行多错误被重复计数 | 独立累计失败行数；错误明细继续逐条保存，结果与批次表均写行数 |

本轮三项同批整改；没有忽略、降级或延期任何正式 finding。

## 3. R6 最小实现与回归

### 3.1 V-13 规范化重复

- `ExchangeServiceImpl` 新增小型 canonical helper，只调用现有 `IdCardValidator.validate()`。
- 全文件计数与逐行 V-13 检查使用同一规范值；单行本身无效时不参与重复计数，由既有 V-05 报错。
- `Phase10ExchangeIT.prevalidateCountsCanonicalIdCardDuplicates` 覆盖居民证末位 x/X 与港澳通行证首字母 a/A；
  四行均命中 V-13，成功预览为空。

### 3.2 失败行与错误明细分离

- 预校验循环只在一行存在错误时把 `invalidRowCount` 加一；该行所有错误仍逐条落 `import_error_detail`。
- 返回结果、批次表和批次列表的 `failCount` 均使用失败行数。
- `Phase10ExchangeIT.prevalidateCountsFailedRowsSeparatelyFromErrorDetails` 覆盖单行至少两条错误，并断言
  `total=1/success=0/fail=1`、错误明细不少于 2、批次表和列表均为 1。

### 3.3 含 NULL 的完整 UPDATE 快照恢复

- student、training_profile、certificate 分别使用专用 `LambdaUpdateWrapper`，显式恢复 before 快照字段；
  `.set(..., null)` 会真实写回 SQL NULL。
- UPDATE 恢复只有 mapper 影响行数恰为 1 才返回成功；0 行或多行均记为该 ref 冲突。
- `Phase10ExchangeIT.rollbackRestoresNullFieldsAndCompleteSnapshotsForUpdatedAggregates` 覆盖三实体各一可空字段
  NULL→覆盖为非空→rollback→NULL，并断言完整实体递归快照一致、`rolledBackCount=3/conflictCount=0`。
- 历史跨学院快照锁序与父引用合同继续由 `Phase39CollegeIntegrityIT` 回归。

没有拆分服务、修改 API/DTO、增加迁移、改变权限或重定义四种导入策略；WS-9 不在本次范围。

## 4. 本地自然退出门禁

- `mvn -B -ntp -pl platform-boot -am -DskipTests compile`：PASS。
- `mvn -B -ntp -pl platform-boot -am -DskipTests test-compile`：PASS。
- 真实隔离 MySQL/Redis/MinIO：`Phase10ExchangeIT` 18/18 + `Phase39CollegeIntegrityIT` 11/11 = **29/29**，
  0 failure/error/skip。
- 同一真实依赖环境的 WS-8 六套：
  - `IdCardProtectionServiceTest` 7/7；
  - `RuntimeProfileGuardTest` 11/11；
  - `Phase3StudentIT` 9/9；
  - `Phase10ExchangeIT` 18/18；
  - `V33IdCardProtectionMigrationIT` 3/3；
  - `Phase41BackupIT` 1/1；
  - 合计 **49/49**，0 failure/error/skip。
- 独立只读代码复查未发现确定性 bug、假绿断言或范围扩张。
- 三只任务专用临时依赖容器已精确删除；既有项目容器未触碰。未启动常驻应用服务，未运行攻击性或 cyber 检查。

以上是整改者本地证据，不替代 fresh Hosted 或独立复核。

## 5. R6 候选与 Hosted 闸门

- R6 manifest 计划固定为：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-functional-remediation-r6-candidate-2026-08-20.json`。
- manifest 必须在本提交和全部治理材料稳定后从当前 worktree 捕获并再次 verify；R5 manifest 对 R6 必须按设计失败。
- R6 字节改变后，不得复用 R5 carrier、Draft PR 的 head 或 run `31808005960`。取得明确授权后，才把 R6 manifest
  原样物化为新 carrier / Draft PR 并运行 fresh Hosted workflow，不覆盖 R5，不 merge。
- 同一 R6 候选必须重新取得：
  1. 4/4 jobs success；
  2. 上述六套 **49/49**；
  3. WS-7 与 V33 合同 PASS；
  4. 双镜像、双 SPDX、镜像与 source revision 身份闭环；
  5. 根与嵌套 `SHA256SUMS` 双向 exact closure，根清单明确列出子清单本身及其成员。

## 6. 请求独立增量复核与授权边界

请求用户独立增量复核三项正式 finding 及必要回归，并独立核验 R6 candidate → carrier → synthetic merge →
fresh Hosted → artifacts 的同候选绑定。本文不自行签发 `INDEPENDENT_STAGE_PASS`。

在三项 finding、R6 Hosted 与独立增量复核全部通过前：

- WS-8 保持 `CHANGES_REQUESTED`；
- WS-9 保持 BLOCKED；
- 项目保持 `CHANGES_REQUESTED / NO-GO`；
- 不授权 merge、push、deploy、cutover 或项目发布 GO。
