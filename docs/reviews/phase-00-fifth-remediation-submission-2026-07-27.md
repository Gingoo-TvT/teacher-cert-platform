# Phase 0 / U-004 第五轮退回整改提交材料（2026-07-27）

> 身份声明：本文件由**整改者**撰写，只构成第五轮候选材料，不是独立复核结论。
> 最后一份带严重度计数的正式报告仍是第三轮
> **CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 2 Low）**；
> 第四轮最终材料的动态 gate 又在 preflight 正式 FAIL。新最终 SHA 的完整动态证据与
> 正式独立复核 PASS 前，Phase 0、U-004 与全项目均不放行。

- 第三轮正式报告：`docs/reviews/phase-00-third-remediation-rereview-2026-07-27.md`
- 第四轮提交材料：`docs/reviews/phase-00-fourth-remediation-submission-2026-07-27.md`
- 第四轮动态 FAIL 归档：`docs/reviews/phase-00-fourth-remediation-dynamic-failure-2026-07-27.md`
- 第四轮最终材料：`191c395bdf036f4b4e76a727d66dd24abeba58ea`
- 第四轮 FAIL 归档：`613ac50`
- 第五轮实现：`6a5577d`
- 分支：`codex/phase00-remediation`

动态门禁必须针对**包含本提交材料的最终 clean `HEAD`**执行，以执行时
`git rev-parse HEAD` 的完整 40 位 SHA 为准；不能复用 `191c395` 的身份对象、
日志、XML、构建目录、evidence directory 或隔离数据卷。

## 1. 第四轮动态结论

第四轮不是“动态门禁尚未执行”，而是：

- 用户在全新一次性 MySQL 8.0.46 / Redis 7.4.9 / MinIO 隔离栈执行；
- candidate、expected candidate、spec 与 Git-object source snapshot 绑定成立；
- `Phase00TargetPreflight` 运行 1 个测试、产生 1 个 error；
- manifest 为 `FAIL`，`preflight.exitCode=1`、formal `run.executed=false`；
- 正式 exact suite 为 **0/7 suites、0/33 testcases**；
- 同一 evidence 的离线 verify 也 FAIL，没有“run 失败但 verify 放行”；
- Compose 与浏览器 supporting evidence 通过，但不能替代 formal 0/33；
- 本次隔离容器、网络和数据卷已销毁，共享 `tcp-*` 未触碰。

直接根因是 `parseRedisInfo` 对 `commands.info("server")` 的 CRLF 多行响应先调用
只允许单行值的 `requiredText → cleanText`，在后续 `split("\\r?\\n")` 之前必然
抛出“Redis INFO server 含控制字符”。这是候选代码缺陷，不是环境抖动；同一 SHA
不得重跑绕过。

## 2. 第五轮整改

### 2.1 Redis INFO 多行协议边界

`Phase00TargetPreflight.parseRedisInfo` 现将协议载荷与单值字段分开：

1. 整段 payload 必须非 null、非 blank，且不得包含 NUL；
2. 仅按真实 Redis INFO 的 LF/CRLF 分行；
3. 每个拆分后的行、key、value 继续通过单行 `cleanText`，裸 CR 或行内
   CR/LF/NUL 失败关闭；
4. 空行和 `#` 注释可跳过；
5. 其它行必须包含非空 key 的首个 `:` 分隔符；
6. 同一 payload 的重复 key 失败关闭，禁止后值静默覆盖前值；
7. 后续既有 `redis_version`、`run_id` 格式及 expected run ID 校验保持不变。

回归直接使用真实 Redis 风格的 CRLF/LF 文本，并覆盖 NUL、裸 CR、空 payload、
无分隔符行和重复 key。断言并入 `Phase00TargetGuardInitializerTest` 既有第 8 个
测试方法，没有增加或改名 formal testcase；exact 7/33 合同不漂移。

### 2.2 Windows Maven 可执行文件

第四轮材料在 Windows 的裸命令先于任何预检以
`required tool versions are unavailable: ['maven']` 失败。原因是 Python
以 `shell=False` 调用子进程，不能依赖 `cmd.exe` 为裸 `mvn` 补 `.cmd`。

第五轮 gate 的默认解析规则为：

1. 非空 `MVN` 环境变量优先；
2. Windows 使用 `shutil.which("mvn.cmd")` 的解析结果；
3. 找不到时回退显式 `mvn.cmd`，随后工具版本预检仍会 fail-closed；
4. POSIX 保持 `mvn`；
5. preflight 与 formal 继续共用同一 `args.maven`，manifest 中记录实际命令。

纯离线测试覆盖 override bypass、Windows resolve、Windows fallback、POSIX 与
parser wiring。为消除执行材料歧义，本轮 Windows 正式命令仍要求显式传入本机
绝对 `mvn.cmd`。

## 3. exact suite 合同

正式 Maven 必须精确出现以下 **7 个 suite / 33 个 testcase**：

| runner | suite | testcase |
|---|---|---:|
| Failsafe | `Phase00ScaffoldIT` | 6 |
| Failsafe | `Phase00ParameterMatrixIT` | 1 |
| Surefire | `DataScopeSqlHandlerTest` | 9 |
| Surefire | `DataScopeMapperChainTest` | 5 |
| Surefire | `FileServiceUploadContractTest` | 2 |
| Surefire | `ApiDocumentationSecurityProfileTest` | 2 |
| Surefire | `Phase00TargetGuardInitializerTest` | 8 |
|  | **合计** | **33** |

`Phase00TargetPreflight` 仍在正式 Maven 前单独执行，不计入 7/33。预检失败必须
阻止 formal；不能以 supporting evidence、旧 XML、跳过、rerun/flaky 节点或
parent reactor 的零匹配结果补足合同。

## 4. 整改者已执行的纯离线门禁

以下命令均自然退出，未连接或启动 Docker、MySQL、Redis、MinIO、HTTP 服务、
浏览器或网络：

| 门禁 | 结果 |
|---|---|
| `python -B scripts\test_phase00_ci_gate.py -v` | **71/71 PASS** |
| `Phase00TargetGuardInitializerTest` | **8/8 PASS**，方法集合与 exact spec 一致 |
| `mvn -B -ntp -o clean test` | **32 suites / 274 tests PASS**，0 failure/error/skip |
| `mvn -B -ntp -o -DskipTests package` | **9/9 modules BUILD SUCCESS**，Checkstyle 0 |
| Java Redis INFO 增量只读终审 | **0 Critical / 0 High / 0 Medium / 0 Low** |
| Python Windows Maven 增量只读终审 | **0 Critical / 0 High / 0 Medium / 0 Low** |
| `git diff --check` | PASS |

有一次全量 `clean test` 与另一只读子任务误启动的 `clean test/package` 并发，
共享 `target/` 被对方删除/重建，platform-boot 因多个已编译依赖类同时消失产生
13 个 `NoClassDefFoundError`。停止所有并发构建后，同一源码顺序执行上表全量测试
与 package 均通过；该次失败是共享构建目录调度污染，不作为产品信号，也没有从记录中
删除或冒充成功。

`Phase00ScaffoldIT`、`Phase00ParameterMatrixIT`、真实 target preflight、fresh-schema
应用上下文及 exact 7/33 **尚未在第五轮最终 SHA 上执行**。

## 5. 仅由用户/获授权复核环境执行的动态门禁

> **安全边界：本节全部命令由用户执行。**
>
> 它们会连接或操作 Docker、MySQL、Redis、MinIO、HTTP 服务或浏览器，Codex 不执行。
> 只能使用本次新建、一次性、与共享开发/生产环境隔离的栈；目标身份、所有权、地址或
> 凭据不确定时立即停止。不得扫描、猜测凭据、探测未知服务或触碰共享资源。

### 5.1 固化新候选与全新目标

用户先执行并记录：

```powershell
git status --short
git rev-parse HEAD
$mavenExe = (Get-Command mvn.cmd -ErrorAction Stop).Source
$mavenExe
```

要求：

1. `HEAD` 包含本材料且 tracked worktree clean；
2. `$mavenExe` 是存在的绝对 `mvn.cmd` 路径；
3. 使用全新的 MySQL/Redis/MinIO 容器、网络、数据卷与 identity 对象；
4. 门禁前仍严格满足 MySQL 0 表、Redis `DBSIZE=0`、MinIO 1 个本次
   schema-v2 identity 对象且无其它 version/delete marker；
5. 所有 expected identity、target marker 与实际连接配置均重新取自本次新栈；
6. 不复用 `tcp-phase00` 第四轮资源或 `phase00-ci-evidence-r4`。

完整身份对象、环境变量和 0/0/1/0 约束沿用第四轮提交材料 §4.1，但
`candidateSha`、`runContext`、nonce、issuedAt、对象名/hash、MySQL UUID 和
Redis run_id 必须全部按本次新栈/新 SHA 重新生成。

### 5.2 执行第五轮 exact gate

证据目录必须是新的、不存在的绝对路径：

```powershell
python -B scripts\phase00_ci_gate.py run `
  --maven $mavenExe `
  --evidence-dir 'C:\absolute\new\phase00-ci-evidence-r5' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA
```

命令自然退出后，再对同一目录执行纯离线验证：

```powershell
python -B scripts\phase00_ci_gate.py verify `
  --evidence-dir 'C:\absolute\new\phase00-ci-evidence-r5' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA
```

用户另行执行本候选的 Compose 静态展开门禁：

```powershell
docker compose -f docker-compose.dev.yml config --quiet
```

若正式复核要求新 SHA 的浏览器 supporting evidence，仍由用户在外部 PowerShell
按第四轮材料 §4.3 启动、核验并停止；Codex/exec 不调用 `dev-serve.*`。完成后必须
确认 8080/5173 无遗留监听。

### 5.3 交回与清理

用户交回：

- 完整 PASS 或 FAIL evidence directory；
- 最终 40 位 SHA 与实际 `mvn.cmd` 路径；
- credential-free 隔离目标版本、identity 与运行前 0/0/1/0 证据；
- preflight、formal exact 7/33、离线 verify 与 Compose 结果；
- 若执行则附新 SHA 的浏览器结果；
- 只销毁本次容器、网络和数据卷后的零残留结果。

失败时也保留完整 FAIL evidence。不得删除共享开发/生产容器、schema、bucket、
网络、数据卷或其它用户资料。

## 6. 状态边界

1. 第四轮动态 gate 正式 FAIL、0/33，不能由 supporting evidence 抵消；
2. 第五轮已形成代码、纯离线测试与静态终审候选，但没有第五轮真实依赖 PASS；
3. 新动态门禁必须针对包含本材料的最终 clean SHA；
4. 只有新 evidence 与正式独立增量复核均 PASS，Phase 0 才能放行；
5. 当前不得进入最终全量审计，不得 merge、push、部署、切流或宣称稳定发布 GO。
