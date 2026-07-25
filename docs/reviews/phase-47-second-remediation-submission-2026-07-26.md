# Phase 47 第二轮整改候选提交（2026-07-26）

## 结论与冻结范围

- 第一轮正式独立结论保持 **CHANGES_REQUESTED（1 Medium / 1 Low）**，依据
  `docs/reviews/phase-47-remediation-rereview-2026-07-26.md`。
- 本文只提交第二轮整改候选，不是独立复核报告，不将 Phase 47 改判 PASS，也不放行
  Phase 53、merge、push、部署、切流或项目发布。
- 当前分支：`codex/phase47-lifecycle-fail-closed`。
- 第一轮代码基线：`aa6f81c`。
- 第一轮正式复核报告归档提交：`81397c3`。
- 第二轮生产/测试代码冻结点：`3bddf6c`
  （`fix(phase47): 对齐生命周期缺省合同与安全分类`）。
- 生产/测试最小增量：
  `aa6f81c..3bddf6c -- FileMaintenanceService.java FileMaintenanceServiceTest.java`。
- 本轮没有 DDL/Flyway、配置、调度、API、权限点、前端或依赖版本变化。

## PASS 后勘误与闭环（2026-07-26）

本文以下内容保留为第二轮候选提交时快照。后续正式独立报告
`docs/reviews/phase-47-second-remediation-rereview-2026-07-26.md` 判定
**PASS（0 Critical / 0 High / 0 Medium / 1 Low 非阻断）**，并指出本文关于 raw
参数数组的证明边界过强：当时候选只确认现有日志事件未携带已列敏感值，没有要求数组
恰含单一生产枚举，也未用额外敏感 raw String 做负向自证。

该 Low 已在 PASS 后由提交 `191a3ad` 闭环：参数数组收紧为单一生产分类枚举，格式化
消息与 raw 值逐项检查敏感哨兵，补真实生产枚举 + 额外敏感 String 的负向自证，以及
unknown code + HTTP 503 回退反例。模块 **18/18**（生命周期 **15/15**）、9 模块
package、diff check 与最终独立只读复核均 PASS。正式报告原 1 Low 计数保持为复核时
快照，不追溯改写。

## 1 Medium / 1 Low 的候选关闭链

| 第一轮 finding | 第二轮整改候选 | 候选证据 |
|---|---|---|
| **Medium：** MinIO SDK 8.5.12 在服务端返回 `NoSuchLifecycleConfiguration` 时于 SDK 内部归一为 `null`；`aa6f81c` 却把 null 当畸形读取，导致新桶永远无法创建首条托管规则。 | `currentRules` 只把高层 `getBucketLifecycle(...) == null` 解释为明确 ABSENT 并返回空规则；不再捕获或放行任何 `ErrorResponseException`。实际抛出的异常、非空配置中的 null/empty rules 继续传播至外层并在整桶 setter 前返回 false。 | 正例直接 `thenReturn(null)`，断言 `true`、桶/规则内容正确且 setter 精确一次；异常形式的 `NoSuchLifecycleConfiguration` 反例断言 `false + never(set)`，防止未来再次在错误抽象层放宽。 |
| **Medium 的测试真实性缺口：** 原正例 mock 了 SDK 不会向业务层抛出的异常，另一条 null 负例反而拒绝真实合同。 | 删除矛盾 null 负例，用真实高层 sentinel 建正例；保留 403、500、其它 404、空 ErrorResponse、I/O、XML、InvalidResponse 与畸形非空配置的 fail-closed 反例。 | `FileMaintenanceServiceTest` 当前 13/13；模块总计 16/16。 |
| **Low：** AccessDenied、NoSuchBucket 与服务端错误全部只显示 `ErrorResponseException`，运维不可区分。 | 本地纯函数只映射固定六类 `ACCESS_DENIED / NO_SUCH_BUCKET / SERVER_ERROR / TRANSPORT / PARSE / INVALID_RESPONSE`；失败 WARN 只传枚举，不传异常对象、原始 code/message、URL、bucket/path 或 trace。 | Logback `ListAppender` 分别验证六类输出；每条失败反例检查格式化消息不含敏感哨兵、`throwableProxy == null`、参数数组不含 `Throwable`。 |

## 关键状态矩阵

| 高层读取结果 | 预期分类/行为 | 写入 |
|---|---|---|
| `cfg == null` | SDK 8.5.12 已确认的 ABSENT；创建托管规则 | 精确一次 |
| 非空配置、有效非空 rules | PRESENT；保留外部规则，只替换稳定托管 ID | 按需一次或幂等跳过 |
| 非空配置但 rules 为 null/empty | `INVALID_RESPONSE`，fail-closed | 从不 |
| AccessDenied | `ACCESS_DENIED`，fail-closed | 从不 |
| NoSuchBucket | `NO_SUCH_BUCKET`，fail-closed | 从不 |
| InternalError/HTTP 5xx | `SERVER_ERROR`，fail-closed | 从不 |
| IOException/传输失败 | `TRANSPORT`，fail-closed | 从不 |
| XML 解析失败 | `PARSE`，fail-closed | 从不 |
| InvalidResponse、空 ErrorResponse、异常形式 NoSuchLifecycleConfiguration 或其它未知失败 | `INVALID_RESPONSE`，fail-closed | 从不 |

## 已执行的非 cyber 安全门禁

| 门禁 | 结果 |
|---|---|
| `mvn -B -ntp -pl platform-file -am test` | **BUILD SUCCESS，16/16**；其中 `FileMaintenanceServiceTest` **13/13** |
| `mvn -B -ntp -DskipTests package` | 后端 **9/9 modules BUILD SUCCESS** |
| `git diff --check` | PASS |
| 内部独立只读差异审查 | **PASS（0 High / 0 Medium / 0 Low）**；只作候选预审，不替代正式复核 |

第一次模块测试已完成生产编译，但新日志断言助手对空附加禁词数组使用 AssertJ
`doesNotContain`，产生 6 个测试辅助错误；改为逐项断言后同一门禁 16/16 全绿。
该中间失败来自新增测试辅助代码，不是生产控制流失败，未被隐藏为“首次即绿”。

## 证据边界与运维限制

- 未启动后端/前端常驻服务，未运行 Failsafe、`clean verify`、Docker、数据库、真实
  MinIO、真实桶生命周期读取或写入、权限变更、网络请求/故障注入、漏洞扫描、
  攻击性探测、凭据尝试、恶意载荷、fuzz、压力或任何可能属于 cyber 的动作。
- SDK 合同依据是第一轮正式报告归档的本地 MinIO 8.5.12 JAR 字节码证据；第二轮
  Mockito 测试验证服务层在该已锁定高层合同下的控制流，不冒充真实 MinIO 动态测试。
  第一轮报告已明确真实 MinIO 动态验证不是确认本 finding 的必要门禁。
- 当前合同只适用于仓库锁定的 MinIO 8.5.12 与标准 `MinioClient` Bean；升级 SDK 或
  引入适配层时必须重新核验 null/异常合同。
- 生命周期 API 仍是无 CAS 的 get→merge→整桶 set。第二轮继续保证“读取失败不写”；
  成功读取后的外部并发变更窗口是既有架构边界，应用确保任务与人工/外部生命周期
  修改必须运维串行，并留最终全量审计复查。
- 若独立复核者另行要求真实 MinIO 权限错误、网络故障或生命周期写入验证，该操作可能
  涉及对象存储配置、权限、网络与持久状态；复核者必须先把精确命令、目标、预期写入
  和回滚影响明确交给用户亲自决定/执行，Codex 不执行。

## 正式独立增量复核请求

1. 冻结第二轮生产/测试代码点 `3bddf6c`，以 `aa6f81c` 为第一轮代码基线，只核对
   两个源码文件中 1 Medium / 1 Low 的关闭链与必要回归；不要把 Phase 44/53 或最终
   全量审计问题混入本增量 finding。
2. 独立确认 MinIO SDK 8.5.12 的高层 null sentinel 与当前实现一致，并核对
   `null→create once`、异常/畸形响应→never set、外部规则保留/托管替换/幂等跳写。
3. 独立确认六类日志只来自本地白名单，格式化消息、参数数组和 throwable proxy 均不
   泄露原始 message/code/URL/path/trace/异常对象。
4. 第一轮正式结论继续为 **CHANGES_REQUESTED（1 Medium / 1 Low）**。只有新的正式
   独立报告 PASS 后，才能关闭 Phase 47 并放行 Phase 53；本材料与内部只读审查均不
   构成阶段 PASS。
