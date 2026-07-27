# Phase 0 / U-004 第五轮动态门禁失败归档（2026-07-27）

> 本文件归档用户/独立执行者交回的第五轮动态门禁结果。Codex 仅只读核对
> evidence 的 manifest、checksum 与脱敏预检日志；本归档不是阶段独立 PASS。
> Phase 0、U-004 与全项目继续 **CHANGES_REQUESTED**。

## 1. 结论

- 候选：`65094098642f0891d9d4685851e6ba0b4d79245f`
- 门禁：**FAIL**
- 正式 exact suite：**0/7 suites，0/33 testcases**
- 失败阶段：正式 Maven 前的 `Phase00TargetPreflight`
- 性质：候选代码的确定性缺陷，不是环境抖动；同一 SHA 不得重跑绕过

第五轮已越过第四轮的 Redis INFO 分支：MySQL `server_uuid`、Redis `run_id`、
MinIO bucket 空状态及身份对象字节校验均已实际到达。随后 MinIO S3 `GetObject`
响应缺少候选强制要求的 deployment identity header，预检失败并按设计阻止
formal `clean verify`；因此没有第五轮 fresh-schema 应用上下文、正式 XML 或
runtime target identity。

## 2. 根因与最小修复边界

候选在 `readMinioIdentity` 中读取 S3 `GetObject` 响应头
`x-minio-deployment-id`，并要求它恰为一个小写 canonical UUID，再派生
`instanceFingerprintSha256`。执行者针对仓库 CI 锁定的
MinIO `RELEASE.2025-04-22T22-12-26Z` 验证了多种数据面响应；deployment ID
只在管理面可见，不是 S3 响应头合同。预检因此稳定抛出：

```text
java.lang.IllegalStateException:
MinIO deployment identity header 必须是单个 canonical UUID
  at Phase00TargetPreflight.deriveMinioInstanceFingerprint(...)
  at Phase00TargetPreflight.readMinioIdentity(...)
```

第六轮只做以下最小修复：

1. 删除 S3 deployment header 与 `instanceFingerprintSha256` 全链路依赖；
2. 不接入 Admin API，不新增管理凭据；
3. 保留规范化 endpoint、bucket、唯一身份对象 key、对象 SHA-256、
   candidate、runContext、nonce 与 issuedAt 的逐项校验；
4. preflight 仍要求 bucket 恰有该一个对象/版本且无 delete marker，正式每个
   context refresh 前重读并与首次 attestation 精确一致；
5. target evidence 结构升为 schema v5，身份模式明确为
   `provisioned-object-challenge-v2`。

该合同证明“当前配置目标持有本次一次性挑战对象”，不声明已经取得或证明唯一
MinIO 进程/集群 UUID。把同源字段再次哈希不会增加独立信任，因此不新增替代
fingerprint、size 或 versionId。

## 3. FAIL evidence 核对

用户保留的原目录：

```text
C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r5
```

Codex 只读核对结果：

- `manifest.json`: `schemaVersion=1`、`status=FAIL`
- `candidateSha == expectedCandidateSha == 65094098642f0891d9d4685851e6ba0b4d79245f`
- `preflight.exitCode=1`、`preflight.formalSuiteContract=false`
- `run.executed=false`、`suites=[]`
- `targets.preflight=null`、`targets.runtime=null`
- preflight：1 test / 1 error，reactor `BUILD FAILURE`
- source snapshot：896 files
- tree：`a80f333ffd024636510c0a2f36d77250df53b2b8`
- snapshot manifest：
  `41e31a8df5b704cced3891d2d1755d4f4dfe4d08dc79c7b468d9661714de7179`
- start / after-preflight / end HEAD 均等于候选 SHA，tracked worktree 全程 clean
- Windows 实际使用绝对
  `C:\Users\wenbibuhaoqwq\tools\apache-maven-3.9.9\bin\mvn.cmd`

归档 `SHA256SUMS` 与实时哈希一致：

```text
348206a254d31c8851f84c65bdd789e7f911b93df4e76dd8e9f474b5c00a1ae3  logs/maven-target-preflight.log
d4a0b3bf83e7ba40952a361218aed8f66f0389b05ca0bf77bcc8c8c6871f07de  manifest.json
48f328cf1a2ca182974474b2ce1ea35ed7b544604a015def9c0df4ac4ba944ed  spec/phase00_ci_gate_spec.json
```

执行者对同一目录运行离线 verify 同样 FAIL/exit 1，没有“run 失败但 verify
放行”。归档没有保留原始 MinIO header；“S3 响应无该 header”来自执行者的
单独动态核验，不从 evidence 包作过度推断。

## 4. 执行环境与清理

以下事实来自执行者回传，本次 Codex 未连接或操作这些资源：

- 全新一次性栈 `tcp-phase00-r5`：MySQL 8.0.46、Redis 7.4.9、
  MinIO `RELEASE.2025-04-22T22-12-26Z`
- 门禁前满足 MySQL 0 表、Redis `DBSIZE=0`、MinIO 恰有一个本次
  schema-v2 身份对象且无其它 version/delete marker
- gate run FAIL、同目录离线 verify FAIL、Compose `config --quiet` PASS
- 本轮未要求或执行浏览器复验
- 执行后只销毁本轮 3 个容器、3 个数据卷与网络；相关监听与本轮资源为零，
  共享 `tcp-*` 保持 Exited 且未触碰

## 5. 后续闸门

1. 不对 `6509409` 重跑或复用 r5 evidence/identity/数据卷；
2. 完成上述 schema-v5 最小修复及纯离线回归；
3. 固化包含代码、测试与材料的新最终 clean SHA；
4. 真实依赖 preflight、formal exact 7/33、Compose 与资源清理由用户/
   获授权复核环境执行；
5. 新 evidence 与正式独立复核均 PASS 前，不进入最终全量审计，不
   merge、push、部署、切流或宣称发布 GO。
