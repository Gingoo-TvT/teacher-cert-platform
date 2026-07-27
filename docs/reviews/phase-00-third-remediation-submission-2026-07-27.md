# Phase 0 / U-004 第三轮退回整改提交材料（2026-07-27）

> 身份声明：本文件由**整改者**撰写，只构成第三轮候选材料，不是独立复核结论。
> 第二轮正式结论仍为 **CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 3 Low）**；
> Phase 0、U-004 与全项目在最终 SHA 动态门禁和独立复核 PASS 前均不放行。

- 第二轮正式报告：`docs/reviews/phase-00-second-remediation-rereview-2026-07-27.md`
- 第二轮报告归档基线：`5d3aa88abc7e596a5b98c402a0c4aff2894bbc3a`
- 第三轮产品/测试/门禁候选：`cc5786cc1c1ed1a243152bf306eca6998bbc0eee`
- 分支：`codex/phase00-remediation`
- 复核范围：第二轮新增的 2 Medium / 3 Low，以及最终 SHA 的动态证据闸门
- 变更规模：14 个路径，`+7669/-323`

动态门禁必须针对包含本材料的最终 clean `HEAD` 执行，不能复用 `cc5786c` 之前的 XML、日志或
evidence directory。

---

## 1. 第二轮 2 Medium / 3 Low 闭环矩阵

| 第二轮 finding | 第三轮整改 | 候选证据 |
|---|---|---|
| Medium：Maven 运行中切换 clean commit，证据仍可能归属开始 SHA | gate 以 `git ls-tree` 的 path/mode/blob 清单和 `git cat-file` 原始对象物化不可变候选；预检结束后复核全部输入、归档预检身份，删除构建树并从同一候选重物化正式构建树；正式 Maven 后再次复核全部输入。工作树 HEAD 在 start / after-preflight / end 三点均须等于 expected SHA。离线 verifier 直接读取 expected candidate 的 gate-spec Git blob，逐字节要求当前 spec 与归档 spec 相同，不能由未提交的弱化 spec 驱动证据解释 | `scripts/phase00_ci_gate.py`；`test_clean_candidate_head_drift_across_maven_window_fails`、`test_snapshot_mutation_is_rejected_and_rematerialization_restores_git`、`test_candidate_blob_reader_ignores_weakened_live_worktree_spec`、`test_verifier_rejects_live_spec_that_differs_from_candidate_blob` |
| Medium：target marker 未绑定实际 MySQL/Redis/MinIO | 三个 marker 改为可解析的 credential-free canonical URI，并与 datasource、Redis host/port/db、MinIO SDK endpoint/bucket **结构化精确比较**；拒绝 relaxed-binding 别名、JNDI/Hikari alternate route、Redis URL/sentinel/cluster、外部 Flyway/SQL-init 等改靶通道。正式测试前的独立预检读取 MySQL `server_uuid`、Redis `run_id`、MinIO 一次性对象 SHA/deployment ID，并证明初始状态恰为 MySQL 0 表、Redis DBSIZE 0、MinIO 仅 1 个身份对象且无额外 version/delete marker；首个及后续 Spring context refresh 前再次绑定实际解析目标与服务身份 | `Phase00TargetPreflight`、`Phase00TargetGuardInitializer`、`Phase00TargetGuardInitializerTest` 8/8；target evidence schema v3；`spring.factories` 在 context refresh 前接入 initializer |
| Low：Windows containment 与 source/spec hash 不完整 | 所有不可信相对路径拒绝盘符、UNC、反斜杠、ADS、保留名、尾点/尾空格、大小写碰撞、symlink/reparse 与越界；manifest/spec 使用有大小上限、拒绝重复键的严格 JSON及 exact schema；artifact 路径集合、source hash、artifact hash 与 `SHA256SUMS` 必须闭合；candidate/live/archive spec 三者逐字节相等 | 纯离线路径、reparse、严格 JSON、重复 ID/path、额外 artifact、candidate blob 反例 |
| Low：应用上下文未执行却提前 `[x]` | 将“可执行 jar package”和“fresh-schema Spring context 启动”拆为两项：package 保持 `[x]`，真实上下文、Flyway、HTTP、审计与 MinIO 合同保持 `[~]`，等待第三轮动态门禁 | `docs/phase-00-脚手架.md` §8 |
| Low：参数矩阵只覆盖 24/32 | `Phase00ParameterMatrixIT` 扩为当前 V1–V32 fresh-schema **32 项全集**，逐项核对 exact key/value/type，并以数据库全量 active key 集合拒绝未知项或遗漏项 | `Phase00ParameterMatrixIT.allDocumentedDefaultsHaveExpectedValueAndType`；`docs/README.md` §6 |

## 2. 第三轮 exact suite 合同

正式 Maven 只在以下 **7 个 suite / 33 个 testcase** 全部出现且方法集合精确匹配时可 PASS：

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

`Phase00TargetPreflight` 在正式 Maven 之前由单独的 `clean verify` 预检命令执行，不计入上述 7 个正式
suite；预检失败时正式 Maven 不得启动。parent reactor 的零匹配、旧 XML、改名、跳过、缺方法或非零
failure/error 均不能替代本合同。

## 3. 整改者已执行的非 cyber 离线门禁

以下动作均会自然退出，未连接或启动 Docker、MySQL、Redis、MinIO、HTTP 服务、浏览器或网络：

| 门禁 | 结果 |
|---|---|
| `python -B scripts\test_phase00_ci_gate.py -v` | **60/60 PASS**；含真实临时 Git repo 的脏工作树 spec 绕过反例 |
| `mvn -B -ntp -o clean test` | **32 suites / 274 tests PASS**，0 failure/error/skip；Java 候选在最终 Python-only 加固后未变化 |
| `Phase00TargetGuardInitializerTest` | **8/8 PASS**，方法集合与 exact spec 一致 |
| `mvn -B -ntp -o -DskipTests clean package` | **9/9 modules BUILD SUCCESS**，Checkstyle 0 |
| `npm --prefix frontend run lint` | PASS，0 warning |
| `npm --prefix frontend run type-check` | PASS |
| `npm --prefix frontend run build` | PASS；仅既有大 chunk 警告 |
| `git diff --check` / staged diff check | PASS |
| 独立只读增量检查 | candidate-spec Git-blob 绑定修复 **0 High / 0 Medium / 0 Low**；不是正式阶段复核 |

一次未清理的 `mvn -DskipTests package` 曾与只读复核进程的 Maven 同时写共享 `target/`，因 jar 时间戳
早于并发生成 class 而失败；该结果属于并发 target 污染。停止并发后，顺序执行的 `clean package`
9/9 成功。此处保留失败事实，不把它计为产品信号，也不以失败前产物充当证据。

`Phase00ScaffoldIT`、`Phase00ParameterMatrixIT`、真实应用上下文和 7/33 exact gate **尚未由 Codex
执行**，不得由上表推导为动态 PASS。

## 4. 仅由用户/获授权复核环境执行的动态门禁

> **以下整节涉及 Docker、数据库、Redis、MinIO、网络或浏览器，Codex 没有执行。**
> 只能指向全新、一次性、与共享开发/生产环境隔离的栈；目标不确定时先停止，不要尝试探测。

### 4.1 用户先准备并核对目标

1. 检出包含本材料的最终候选，确认 `git rev-parse HEAD` 为完整 40 位 SHA、tracked worktree clean，
   且没有未跟踪的 Maven/源码/测试构建输入。
2. 用户创建全新 MySQL 8、Redis 7、MinIO 栈；门禁前必须满足：
   - MySQL 目标 schema 已创建但 **0 张表**；
   - Redis 目标 DB `DBSIZE=0`；
   - MinIO 目标 bucket 恰有 **1 个**本次身份对象，无其它对象、version 或 delete marker。
3. 用户读取真实 MySQL `@@GLOBAL.server_uuid` 和 Redis `INFO server` 的 `run_id`。
4. 用户在 MinIO bucket 写入本次唯一对象
   `.phase00-target/identity-<本次唯一值>.json`。对象必须是以下 exact JSON（schemaVersion 2），
   `candidateSha` 为最终 HEAD，nonce 为 64 位小写 hex，`identityIssuedAt` 为刚生成的 canonical UTC：

```json
{"schemaVersion":2,"candidateSha":"<40位最终HEAD>","runContext":"<本次非秘密唯一标识>","identityNonce":"<64位小写hex>","identityIssuedAt":"<YYYY-MM-DDTHH:mm:ssZ>"}
```

5. 用户计算该对象原始字节的 SHA-256，并设置实际连接凭据及以下绑定变量。marker 不含凭据：

```powershell
$env:PHASE00_EXPECTED_CANDIDATE_SHA = git rev-parse HEAD
$env:PHASE00_RUN_CONTEXT = '<本次非秘密唯一标识>'
$env:PHASE00_TARGET_SCHEMA = '<实际schema>'
$env:PHASE00_MYSQL_SERVICE_MARKER = 'mysql://<实际host>:<port>/<schema>'
$env:PHASE00_REDIS_SERVICE_MARKER = 'redis://<实际host>:<port>/<db>'
$env:PHASE00_MINIO_SERVICE_MARKER = 'http://<实际host>:<port>/<bucket>'
$env:PHASE00_EXPECTED_MYSQL_SERVER_UUID = '<实际server_uuid，小写canonical UUID>'
$env:PHASE00_EXPECTED_REDIS_RUN_ID = '<实际run_id，40位小写hex>'
$env:PHASE00_MINIO_IDENTITY_OBJECT = '.phase00-target/identity-<本次唯一值>.json'
$env:PHASE00_EXPECTED_MINIO_IDENTITY_SHA256 = '<身份对象原始字节SHA-256>'
$env:PHASE00_EXPECTED_MINIO_IDENTITY_NONCE = '<对象内64位小写hex nonce>'
$env:PHASE00_EXPECTED_MINIO_IDENTITY_ISSUED_AT = '<对象内canonical UTC>'
```

同时设置 `SPRING_DATASOURCE_*`、`SPRING_DATA_REDIS_*`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、
`MINIO_SECRET_KEY`、`MINIO_BUCKET` 与 `JWT_SECRET`。`MINIO_PUBLIC_ENDPOINT` 必须与
`MINIO_ENDPOINT` 的 scheme/host/port 完全同靶。仓库 CI 的身份对象生成与上传实现见
`.github/workflows/ci.yml`，可作为用户环境适配时的权威示例。

### 4.2 用户执行

证据目录必须是新的空目录，不能复用上一轮：

```powershell
python -B scripts\phase00_ci_gate.py run `
  --evidence-dir 'C:\absolute\new\phase00-ci-evidence' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA
```

该命令会先执行目标身份/空状态预检，再从同一候选重新物化源码并执行正式 7/33。之后用户可离线重验：

```powershell
python -B scripts\phase00_ci_gate.py verify `
  --evidence-dir 'C:\absolute\new\phase00-ci-evidence' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA
```

第二轮正式报告还要求用户补交：

```powershell
docker compose -f docker-compose.dev.yml config --quiet
```

必要的非生产 `/doc.html` 浏览器渲染与示例接口核验也由用户或获授权复核环境执行。若采用仓库
Windows watchdog，以下命令**只能由用户在外部 PowerShell**执行；先沿用本节的隔离连接环境并设置
`SPRING_PROFILES_ACTIVE=dev`，完成后必须停止：

```powershell
mvn -B -ntp -o -DskipTests package
.\scripts\dev-serve.ps1 backend http://127.0.0.1:8080/api/health `
  'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe' `
  -jar platform-boot\target\teacher-cert-platform.jar
# 用户在浏览器打开 http://127.0.0.1:8080/doc.html，并调用一个无副作用示例接口
.\scripts\dev-stop.ps1 backend
```

上述 gate、Compose、服务与浏览器动作均未由 Codex 执行。

### 4.3 用户清理并交回

- 完整 evidence directory（PASS 或 FAIL manifest、两段完整脱敏 Maven 日志、XML、身份原字节、
  artifact/source hash 与 `SHA256SUMS`）；
- 最终 `git rev-parse HEAD`、隔离目标的 credential-free 地址/版本/身份；
- 运行前 0/0/1/0 空状态证据、Compose config 结果及必要浏览器结果；
- 用户销毁本次容器、网络和数据卷后的结果。失败时也保留完整 FAIL evidence，不只截末尾日志。

## 5. 状态边界

1. 第二轮 2 Medium / 3 Low 已形成第三轮整改候选，但没有独立正式 PASS，不能在历史报告上改结论。
2. 动态 7/33、fresh-schema 应用上下文、Compose 与浏览器证据未执行；`docs/phase-00-脚手架.md`
   对应项目继续为 `[~]`。
3. 当前不得进入最终全量审计，不得 merge、push、部署、切流或宣称稳定发布 GO。
