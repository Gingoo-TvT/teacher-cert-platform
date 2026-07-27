# Phase 0 / U-004 第六轮整改提交材料（2026-07-27）

> 身份声明：本文件由整改者撰写，只构成第六轮候选材料，不是独立复核结论。
> Phase 0 / U-004 与全项目继续 **CHANGES_REQUESTED**；新最终 SHA 的真实 exact
> 7/33 和正式独立复核均 PASS 前不放行。

- 第三轮正式报告：`docs/reviews/phase-00-third-remediation-rereview-2026-07-27.md`
- 第五轮提交材料：`docs/reviews/phase-00-fifth-remediation-submission-2026-07-27.md`
- 第五轮动态 FAIL 归档：`docs/reviews/phase-00-fifth-remediation-dynamic-failure-2026-07-27.md`
- 第五轮最终材料：`65094098642f0891d9d4685851e6ba0b4d79245f`
- 第五轮失败归档：`b4bf841`
- 第六轮实现：`b123238`
- 分支：`codex/phase00-remediation`

动态门禁必须针对包含本文件的最终 clean `HEAD`，不能复用第五轮身份对象、日志、
XML、构建目录、evidence directory、容器、网络或数据卷。

## 1. 第五轮动态结论

第五轮用户门禁确认第四轮 Redis INFO 与 Windows `mvn.cmd` 缺陷已关闭，预检成功
校验 MySQL UUID、Redis run_id、MinIO bucket 状态与身份对象。随后候选要求 S3
`GetObject` 响应必须包含唯一 canonical `x-minio-deployment-id`；仓库 CI 使用的同版
MinIO 不提供该 header，因此 preflight FAIL、formal `run.executed=false`、
**0/7 suites、0/33 testcases**。离线 verify 同样 FAIL。

这是确定性候选缺陷，同一 SHA 不重跑。

## 2. 第六轮最小整改

本轮只做一件事：删除 MinIO deployment header、UUID 解析和
`instanceFingerprintSha256` 全链路，target evidence 升为 schema v5，
`minioIdentityMode` 为 `provisioned-object-challenge-v2`。

仍逐字段绑定：

- canonical endpoint 与 bucket；
- 本轮唯一 schema-v2 identity object 的 key 与 SHA-256；
- candidate SHA、runContext、nonce、issuedAt；
- MySQL 0 表、Redis 0 key、MinIO 1 object / 0 delete marker；
- preflight 与每个 Spring context refresh 的重新读取及逐字段一致性。

不接 MinIO Admin API、不新增管理凭据，也不把同一组字段再哈希成“实例指纹”。
该合同只证明当前配置目标持有本轮一次性挑战对象，不宣称证明唯一 MinIO 管理实例
UUID。完整克隆或代理不在最小权限 Phase 0 门禁的证明范围内。

## 3. exact suite 合同

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

第 8 个 target-guard 方法现为
`evidenceSchemaFiveContainsExactFreshnessAndObjectChallengeKeys`。正式 suite 数量和
testcase 总数未变化；`Phase00TargetPreflight` 仍是正式 Maven 之前的独立预检，不计入
7/33。

## 4. 整改者已完成的离线门禁

以下检查均自然退出，未连接或启动 Docker、MySQL、Redis、MinIO、HTTP 服务、浏览器
或网络：

| 门禁 | 结果 |
|---|---|
| `python -B scripts\test_phase00_ci_gate.py -v` | **71/71 PASS** |
| `Phase00TargetGuardInitializerTest` | **8/8 PASS** |
| `mvn -B -ntp -o clean test` | **32 suites / 274 tests PASS**，0 failure/error/skip |
| `mvn -B -ntp -o -DskipTests package` | **9/9 modules BUILD SUCCESS**，Checkstyle 0 |
| Java 与 Python/spec 两路增量静态复核 | **0 finding** |

这些结果不替代真实依赖 preflight 或 exact 7/33。

## 5. 仅由用户/获授权复核环境执行

> **以下命令会连接或操作 Docker、MySQL、Redis、MinIO，属于用户明确要求由自己执行的范围；
> Codex 不执行。**

用户需新建一次性隔离栈与全新 schema-v2 identity object，并重新生成
candidateSha、runContext、nonce、issuedAt、对象名/hash、MySQL UUID 与 Redis run_id。
门禁前仍须满足 0/0/1/0。完整环境变量和对象格式沿用第五轮提交材料 §5.1，但所有值必须来自
本轮新栈与新 SHA。

先固定候选：

```powershell
git status --short
$candidateSha = (git rev-parse HEAD).Trim()
$env:PHASE00_EXPECTED_CANDIDATE_SHA = $candidateSha
$mavenExe = (Get-Command mvn.cmd -ErrorAction Stop).Source
```

使用新的、不存在的 evidence 目录执行：

```powershell
python -B scripts\phase00_ci_gate.py run `
  --maven $mavenExe `
  --evidence-dir 'C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r6' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA

python -B scripts\phase00_ci_gate.py verify `
  --evidence-dir 'C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r6' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA

docker compose -f docker-compose.dev.yml config --quiet
```

若 preflight 再次失败，保留完整 FAIL evidence，不运行或拼凑 formal 7/33。浏览器
supporting evidence 仅在正式复核明确要求时由用户执行。完成后只销毁本轮隔离容器、
网络和数据卷，不触碰共享资源。

## 6. 放行边界

1. 第四、第五轮动态 gate 均为正式 FAIL、0/33；
2. 第六轮当前只有代码、离线测试与静态复核候选；
3. 新 evidence 必须绑定包含本材料的最终 clean SHA；
4. exact 7/33 与正式独立增量复核均 PASS 后，Phase 0 才可放行并进入最终全量审计；
5. 当前不得 merge、push、部署、切流或宣称稳定发布 GO。
