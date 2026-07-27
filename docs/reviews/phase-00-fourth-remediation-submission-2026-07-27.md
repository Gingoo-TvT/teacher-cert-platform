# Phase 0 / U-004 第四轮退回整改提交材料（2026-07-27）

> 身份声明：本文件由**整改者**撰写，只构成第四轮候选材料，不是独立复核结论。
> 第三轮正式结论仍为 **CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 2 Low）**；
> Phase 0、U-004 与全项目在最终 SHA 动态门禁和正式独立复核 PASS 前均不放行。

- 第三轮正式报告：`docs/reviews/phase-00-third-remediation-rereview-2026-07-27.md`
- 第三轮报告归档：`df888c1`
- 第四轮实现候选：`74c1de53137958007f7fe7cf18306dbe00101e45`
- 分支：`codex/phase00-remediation`
- 复核范围：第三轮新增的 1 Medium / 2 Low，以及新最终 SHA 的动态证据闸门
- 实现变更规模：7 个路径，`+2391/-317`

动态门禁必须针对**包含本提交材料的最终 clean `HEAD`**执行，以执行时
`git rev-parse HEAD` 的完整 40 位 SHA 为准；不能复用 `30a76c8` 或 `74c1de5`
之前的 XML、日志、构建产物或 evidence directory。

---

## 1. 第三轮 1 Medium / 2 Low 闭环矩阵

| 第三轮 finding | 第四轮整改 | 纯离线证据 |
|---|---|---|
| Medium：普通 `OSError` / `PermissionError` 可在失败退出后留下表面 PASS | `_run_gate_from_snapshot` 的成功路径只返回内存 `PendingPassEvidence`，源码快照 context 内磁盘无 manifest/checksum；context 清理后才捕获最终 artifact snapshot、secret scan、构造未来 manifest/checksum 的虚拟快照并执行完整 verifier。所有 `Exception` 均进入失败关闭；成功时 checksum-first、PASS manifest-last，再原子 rename。发布后的输出异常不会翻转已发布结果 | `test_self_verification_oserror_never_leaves_staging_pass`、`test_failed_fail_write_never_creates_provisional_pass`、`test_preverified_pass_writes_manifest_last`、`test_atomic_publish_failure_never_exposes_final_directory`、`test_mocked_success_finalizes_after_source_cleanup_and_ignores_print_failure` |
| Low：evidence containment 与实际打开存在 TOCTOU | verifier 只捕获一次 `EvidenceSnapshot`。POSIX 使用 root fd、相对 `openat`、`O_NOFOLLOW/O_DIRECTORY` 和同 fd 前后身份复核；Windows 从卷/UNC share 根到 evidence root 持有完整无 reparse 目录 handle 链，目录 share 不含 DELETE、文件只 share READ。目录/文件都必须按 `inspect → final path → boundary → capability → reader`，且文件在同一 handle `ReadFile` 后复核 attributes/file ID/size/mtime；后续 JSON/XML/hash/identity 校验禁止重开 evidence 路径 | Windows fake callback 覆盖完整祖先链、outside/reparse 时 reader=0、正常顺序、成功/异常逆序 close；完整 verifier 测试屏蔽 `Path.open/read/stat/sha256_file` 的 evidence 路径重开 |
| Low：MinIO 原始响应头未经收窄却声明无秘密 | target identity 升为 schema v4；`Server` 完全不读取，`x-minio-deployment-id` 必须恰为一个 lowercase canonical UUID。Java 只保存 `SHA-256("phase00:minio-instance:v1\0" + nonce + "\0" + uuid)`，原始 header 不进入 attestation、JSON、artifact record 或错误文本。Python exact schema 拒绝旧 `server/deploymentId` 字段；所有 artifact 与候选 manifest 通过 secret scan 后才写 `containsSecrets=false` | Java/Python 固定向量 `7fe3df67cf424bb6bf35a90cd42f348da3d7855a20b0f55f9d3b612cdaea2c1e`；缺失/多值/uppercase/控制字符/敏感串 header、坏 nonce、旧字段与 manifest-only secret 反例 |

## 2. 第四轮 exact suite 合同

正式 Maven 仍须精确出现以下 **7 个 suite / 33 个 testcase**：

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

`Phase00TargetPreflight` 在正式 Maven 前由单独预检命令运行，不计入上述 7 个 suite。
预检失败时正式 Maven 不得启动；旧 XML、缺 suite、改名、跳过、rerun 代替、非零
failure/error 或 parent reactor 零匹配都不能满足合同。

## 3. 整改者已执行的纯离线门禁

以下命令均自然退出，未连接或启动 Docker、MySQL、Redis、MinIO、HTTP 服务、浏览器或网络：

| 门禁 | 结果 |
|---|---|
| `python -B scripts\test_phase00_ci_gate.py -v` | **70/70 PASS** |
| `mvn -B -ntp -o clean test` | **32 suites / 274 tests PASS**，0 failure/error/skip |
| `Phase00TargetGuardInitializerTest` | **8/8 PASS**，方法集合与 exact spec 一致 |
| `mvn -B -ntp -o -DskipTests package` | **9/9 modules BUILD SUCCESS**，Checkstyle 0 |
| `git diff --check` | PASS |
| PASS 状态机专项只读检查 | **0 Critical / 0 High / 0 Medium / 0 Low** |
| Windows handle/evidence snapshot 专项只读检查 | **0 Critical / 0 High / 0 Medium / 0 Low** |
| MinIO schema-v4/secret 边界专项只读检查 | **0 Critical / 0 High / 0 Medium / 0 Low** |

前端无变更，本轮没有把第三轮的前端结果冒充第四轮新证据。
`Phase00ScaffoldIT`、`Phase00ParameterMatrixIT`、真实应用上下文及 exact 7/33
**尚未由 Codex 执行**，不得从上表推导动态 PASS。

## 4. 仅由用户/获授权复核环境执行的动态门禁

> **安全边界：本节所有命令都由用户执行。**
>
> 它们会涉及 Docker、MySQL、Redis、MinIO、网络、HTTP 服务或浏览器；Codex 本轮没有执行。
> 只能指向全新、一次性、与共享开发/生产环境隔离的栈。目标身份、地址或所有权不确定时立即停止，
> 不要尝试扫描、猜测凭据、探测未知服务或进行任何攻击性/破坏性操作。

### 4.1 用户准备并核对最终候选

用户先执行：

```powershell
git status --short
git rev-parse HEAD
```

要求：

1. `HEAD` 是包含本材料的最终候选；记下完整 40 位 SHA。
2. tracked worktree clean；不得存在未跟踪的 Maven/源码/测试构建输入。
3. 使用全新 MySQL 8、Redis 7、MinIO 一次性隔离栈。
4. 门禁前状态必须恰为：
   - MySQL 目标 schema 已创建但 **0 张表**；
   - Redis 目标 DB `DBSIZE=0`；
   - MinIO 目标 bucket 恰有 **1 个**本次身份对象，无其它对象、version 或 delete marker。
5. 用户读取真实 MySQL `@@GLOBAL.server_uuid` 与 Redis `INFO server` 的 `run_id`。

用户在隔离 MinIO bucket 写入本次唯一身份对象：

```text
.phase00-target/identity-<本次唯一值>.json
```

对象原始字节必须是 schemaVersion 2 的 exact JSON；`candidateSha` 为最终 `HEAD`，
nonce 为 64 位小写 hex，`identityIssuedAt` 为刚生成的 canonical UTC：

```json
{"schemaVersion":2,"candidateSha":"<40位最终HEAD>","runContext":"<本次非秘密唯一标识>","identityNonce":"<64位小写hex>","identityIssuedAt":"<YYYY-MM-DDTHH:mm:ssZ>"}
```

用户计算该对象原始字节 SHA-256，并设置实际连接凭据及以下无凭据绑定变量：

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

同时由用户设置本次隔离栈的 `SPRING_DATASOURCE_*`、`SPRING_DATA_REDIS_*`、
`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET` 与
`JWT_SECRET`。`MINIO_PUBLIC_ENDPOINT` 必须与 `MINIO_ENDPOINT` 的
scheme/host/port 完全同靶。仓库 `.github/workflows/ci.yml` 是身份对象生成与上传的权威示例。

### 4.2 用户执行 exact gate

证据目录必须是新的、不存在的绝对路径：

```powershell
python -B scripts\phase00_ci_gate.py run `
  --evidence-dir 'C:\absolute\new\phase00-ci-evidence' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA
```

该命令会先做目标身份/空状态预检，再从同一候选重新物化源码并执行正式 7/33。
命令自然退出后，用户再离线重验同一证据：

```powershell
python -B scripts\phase00_ci_gate.py verify `
  --evidence-dir 'C:\absolute\new\phase00-ci-evidence' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA
```

用户还需执行本候选的 Compose 静态展开门禁：

```powershell
docker compose -f docker-compose.dev.yml config --quiet
```

### 4.3 用户执行必要浏览器核验

若正式复核要求非生产 `/doc.html` 的真实渲染与示例接口结果，以下命令也**只由用户在外部
PowerShell 执行**。先沿用本节隔离连接环境并设置 `SPRING_PROFILES_ACTIVE=dev`，完成后必须停止：

```powershell
mvn -B -ntp -o -DskipTests package
.\scripts\dev-serve.ps1 backend http://127.0.0.1:8080/api/health `
  'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe' `
  -jar platform-boot\target\teacher-cert-platform.jar
# 用户在浏览器打开 http://127.0.0.1:8080/doc.html，并调用一个无副作用示例接口
.\scripts\dev-stop.ps1 backend
```

Codex/exec 不得调用 `dev-serve.*`；用户完成后必须确认 8080/5173 无遗留监听。

### 4.4 用户清理并交回

用户交回：

- 完整 evidence directory（PASS 或 FAIL manifest、两段脱敏 Maven 日志、XML、身份原字节、
  artifact/source hash 与 `SHA256SUMS`）；
- 最终 `git rev-parse HEAD`、credential-free 隔离目标地址/版本/身份；
- 运行前 0/0/1/0 状态证据；
- exact 7/33、离线 verify、Compose config 与必要浏览器结果；
- 用户销毁本次容器、网络和数据卷后的结果。

失败时也保留完整 FAIL evidence，不只截取末尾日志。用户清理只针对本次明确创建的一次性资源，
不得删除共享开发/生产容器、schema、bucket、网络或数据卷。

## 5. 状态边界

1. 第三轮 1 Medium / 2 Low 已形成第四轮代码/纯离线整改候选，但没有新的正式独立 PASS。
2. 动态 7/33、fresh-schema 应用上下文、Compose 与浏览器证据未执行；
   `docs/phase-00-脚手架.md` 对应项目继续为 `[~]`。
3. 用户动态门禁必须针对包含本材料的最终 clean SHA；任何早期 SHA 结果均不能代替。
4. 当前不得进入最终全量审计，不得 merge、push、部署、切流或宣称稳定发布 GO。
