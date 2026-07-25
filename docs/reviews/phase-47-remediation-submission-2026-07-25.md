# Phase 47 退回整改候选提交（2026-07-25）

## 结论与冻结范围

- 原正式结论保持 **CHANGES_REQUESTED（1 Major）**，依据
  `docs/reviews/phase-47-review.md`。
- 本文只提交整改候选，不是独立复核报告，不将 Phase 47 改判 PASS，也不放行
  Phase 53、merge、push、部署、切流或项目发布。
- 当前分支：`codex/phase47-lifecycle-fail-closed`。
- 代码冻结点：`aa6f81c`（`fix(phase47): 生命周期读取失败时禁止覆盖写`）。
- 最小增量：`b2f1f70..aa6f81c`。
- 本轮生产/测试范围仅为：
  `FileMaintenanceService.java` 与 `FileMaintenanceServiceTest.java`。

## 原 1 Major 的候选关闭链

| 原 finding | 整改候选 | 候选证据 |
|---|---|---|
| `currentRules` 捕获任意异常并返回空列表，调用方随后整桶 `setBucketLifecycle`，可能在 500、AccessDenied 或网络/解析错误时覆盖其它既有规则。 | `currentRules` 只捕获 `ErrorResponseException`；只有 `errorResponse.code()` 精确等于 `NoSuchLifecycleConfiguration` 才返回空规则。其它错误原样传播到外层，返回 false，控制流在 `setBucketLifecycle` 前终止。 | AccessDenied、InternalError/500、NoSuchBucket/404、空 ErrorResponse、IOException、XmlParserException 均断言 `false` 且 `never(setBucketLifecycle)`；NoSuchLifecycleConfiguration 断言精确写一次。 |
| 原实现把任意“空/失败”混为无配置，异常 SDK 响应仍可能触发写入。 | `cfg == null`、`rules == null`、`rules.isEmpty()` 均按无效读取失败关闭；仅明确 NoSuchLifecycleConfiguration 可创建。 | 三个无效响应反例均断言 `false` 且不写。 |
| 整桶写入必须继续保留其它生命周期规则。 | 成功读取后保留快照内所有非托管规则，仅删除旧的稳定托管 ID 并追加新规则；同 ID、同 days 时幂等跳写。 | 捕获写入参数，断言规则总数为 2、外部规则对象原样且只出现一次、托管规则唯一并更新 days；匹配规则断言不写。 |
| 原错误日志把读取失败写成设置失败并输出原始异常 message。 | 告警文案改为“规则确保失败”，仅记录本地异常简单类名，不输出服务端可控 message、异常对象或内部 endpoint/path。 | 两路内部只读终审确认日志 Low 已关闭。 |

## 已执行的非 cyber 安全门禁

| 门禁 | 结果 |
|---|---|
| `mvn -B -ntp -pl platform-file -am test` | **BUILD SUCCESS，15/15**；其中 `FileMaintenanceServiceTest` 新增 12/12 |
| `mvn -B -ntp -DskipTests package` | 后端 9 模块 **BUILD SUCCESS** |
| `git diff --check` | PASS |
| 内部只读代码终审 | 0 High / 0 Medium / 0 Low |
| 内部只读测试终审 | 0 High / 0 Medium / 0 Low |

第一次定向 Maven 尝试因 PowerShell 未引用
`-Dsurefire.failIfNoSpecifiedTests=false`，Maven 在任何编译/测试前把部分参数解析为
非法生命周期阶段；引用参数后定向门禁进入测试并通过，最终又以上表不依赖该属性的
模块全量测试形成 15/15 证据。该命令行问题不是代码或测试失败。

## 证据边界与运维限制

- 未启动后端/前端常驻服务，未运行 Failsafe、`clean verify`、Docker、真实 MinIO、
  真实桶生命周期读取或写入、权限变更、网络故障注入、漏洞扫描、攻击性探测、
  凭据尝试、恶意载荷、fuzz、压力或任何可能属于 cyber 的动作。
- 当前 12 条新增测试是纯 Mockito 控制流反例，不冒充真实 MinIO 动态验证；原阶段
  的历史 115/115 也不冒充本候选错误分流证据。
- MinIO SDK 的生命周期设置是整桶全量替换且没有 CAS。候选修复已经关闭
  “读取失败后写入”的 Major；成功读取后的短暂外部并发变更窗口仍属于 API 架构边界。
  运维必须串行化应用确保任务与人工/外部生命周期修改。
- 当前正式报告只要求失败路径分流反例，没有新增真实 MinIO 动态门禁。若独立复核者
  另行要求在真实 MinIO 注入 AccessDenied、500、网络故障或执行生命周期写入验证，
  该操作可能涉及对象存储配置、权限、网络与持久状态；复核者必须先把精确命令、
  目标、预期写入和回滚影响明确交给用户亲自决定/执行，Codex 不执行。

## 独立增量复核请求

1. 冻结 `b2f1f70..aa6f81c`，只按原 1 Major 核对错误分流、失败不写、其它规则保留
   与必要回归；不要把 Phase 44/53 或最终全量审计问题混入本增量 finding。
2. 独立确认 12 条纯 Mockito 反例确实覆盖 403、500、其它 404、网络、解析、空响应
   与明确无配置的分流，并核对模块 15/15、9 模块 package 的提交同源性。
3. 原 Phase 47 正式结论继续为 **CHANGES_REQUESTED**。本材料仅声明整改候选与
   开发者安全门禁完成，不构成独立 PASS，不放行 Phase 53、merge、push、部署、
   切流或项目发布。
