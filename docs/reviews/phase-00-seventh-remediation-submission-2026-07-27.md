# Phase 0 / U-004 第七轮整改提交材料（2026-07-27）

> 身份声明：本文件由整改者撰写，只构成第七轮候选材料，不是独立复核结论。
> Phase 0 / U-004 与全项目继续 **CHANGES_REQUESTED**；新最终 SHA 的完整 gate
> 和正式独立复核均 PASS 前不放行。

- 第三轮正式报告：`docs/reviews/phase-00-third-remediation-rereview-2026-07-27.md`
- 第六轮提交材料：`docs/reviews/phase-00-sixth-remediation-submission-2026-07-27.md`
- 第六轮动态 FAIL 归档：`docs/reviews/phase-00-sixth-remediation-dynamic-failure-2026-07-27.md`
- 第六轮最终材料：`16256002c62c26eae48b8d1c39d2b7a669343980`
- 第六轮失败归档：`7f02635`
- 第七轮实现：`b8cd171`
- 分支：`codex/phase00-remediation`

动态门禁必须针对包含本文件的最终 clean `HEAD`，不能复用 r6 identity、evidence、
构建目录、容器、网络或数据卷。

## 1. 第六轮动态结论

第六轮取得了三轮以来的首次完整运行：

- preflight：exit 0；
- 正式 Maven：exit 0 / BUILD SUCCESS；
- exact：7/7 suites、33/33 testcases；
- 测试：0 failure / 0 error / 0 skip。

gate 最终仍为 FAIL。唯一错误是 runtime identity 的
`freshness.mysqlTableCountBefore` 期望 JSON integer `0`，实际收到字符串 `"0"`。
其余 7 份 XML、两份日志、preflight identity、spec 与 13 项 checksum 均已归档并
核对一致。

## 2. 第七轮最小整改

根因是 `Phase00ScaffoldIT` 使用 Spring 应用 `ObjectMapper` 写 runtime evidence，
而生产 `JacksonConfig` 为前端精度合同把 `Long` 序列化为字符串。

本轮只做：

1. 保留应用 mapper 继续读取 HTTP/MinIO JSON；
2. runtime target identity 写出改用裸 `new ObjectMapper()`，不继承业务
   `Long→String`；
3. 既有第 8 个 target-guard testcase 复用同一写出 helper，解析实际字节并逐项
   断言四个 freshness token 是 JSON integer 0/0/1/0。

Python schema、evidence schema、生产配置、依赖、suite 和 testcase 集合均未改。
没有新增抽象层或基础设施。

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

## 4. 整改者已完成的离线门禁

以下检查均自然退出，未连接或启动 Docker、MySQL、Redis、MinIO、HTTP 服务、
浏览器或网络：

| 门禁 | 结果 |
|---|---|
| `python -B scripts\test_phase00_ci_gate.py -v` | **71/71 PASS** |
| `Phase00TargetGuardInitializerTest` | **8/8 PASS** |
| `mvn -B -ntp -o clean test` | **32 suites / 274 tests PASS**，0 failure/error/skip |
| `mvn -B -ntp -o -DskipTests package` | **9/9 modules BUILD SUCCESS**，Checkstyle 0 |
| 第七轮实现增量静态终审 | **0 Critical / 0 High / 0 Medium / 0 Low** |

这些结果不替代第七轮真实依赖 gate。

## 5. 仅由用户/获授权复核环境执行

> **以下命令会连接或操作 Docker、MySQL、Redis、MinIO，全部由用户执行；
> Codex 不执行。**

用户需按第六轮提交材料 §5 新建一次性隔离栈和全新 schema-v2 identity object，
重新生成 candidateSha、runContext、nonce、issuedAt、对象名/hash、MySQL UUID 与
Redis run_id，并在门禁前满足 0/0/1/0。

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
  --evidence-dir 'C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r7' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA

python -B scripts\phase00_ci_gate.py verify `
  --evidence-dir 'C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r7' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA

docker compose -f docker-compose.dev.yml config --quiet
```

无论 PASS/FAIL 都保留完整 evidence。浏览器 supporting evidence 仅在正式复核明确
要求时由用户执行。完成后只销毁本轮隔离容器、网络和数据卷，不触碰共享资源。

## 6. 放行边界

1. 第六轮 exact 7/33 全绿，但总 gate 正式 FAIL；
2. 第七轮当前只有最小代码、离线测试与静态复核候选；
3. 新 evidence 必须绑定包含本材料的最终 clean SHA；
4. 完整 gate 与正式独立增量复核均 PASS 后，Phase 0 才可放行并进入最终全量审计；
5. 当前不得 merge、push、部署、切流或宣称稳定发布 GO。
