# Phase 0 / U-004 第六轮动态门禁失败归档（2026-07-27）

> 本文件归档用户/独立执行者交回的第六轮动态门禁结果。Codex 仅只读核对
> evidence 的 manifest、checksum、脱敏日志和 XML；本归档不是阶段独立 PASS。
> Phase 0、U-004 与全项目继续 **CHANGES_REQUESTED**。

## 1. 结论

- 候选：`16256002c62c26eae48b8d1c39d2b7a669343980`
- 门禁：**FAIL（exit 1）**
- preflight：**PASS（exit 0）**
- 正式 Maven：**BUILD SUCCESS（exit 0）**
- 正式 exact suite：**7/7 suites、33/33 testcases**
- 测试结果：**0 failure / 0 error / 0 skip**
- 失败阶段：正式测试结束后的 runtime target identity evidence 校验
- 性质：候选证据写出路径的确定性缺陷，不是环境抖动；同一 SHA 不得重跑绕过

第六轮首次完整执行并通过：

```text
Phase00ScaffoldIT                       6
Phase00ParameterMatrixIT                1
DataScopeSqlHandlerTest                 9
DataScopeMapperChainTest                5
FileServiceUploadContractTest           2
ApiDocumentationSecurityProfileTest     2
Phase00TargetGuardInitializerTest       8
                                      ──
                                      33
```

manifest 唯一错误为：

```text
runtime target identity validation failed:
runtime target identity freshness.mysqlTableCountBefore must be 0, observed='0'
```

因此 7/33 是有效的正式测试进展，但 gate 总结仍必须是 FAIL。

## 2. 根因与第七轮最小修复边界

`Phase00ScaffoldIT` 注入应用 `ObjectMapper`，并用它序列化 runtime identity。
生产 `JacksonConfig` 为前端精度合同把 `Long`/`long` 注册为
`ToStringSerializer`。四个 freshness 字段在 `TargetAttestation` 中是 `long`，
装箱进入 `Map<String,Object>` 后成为 `Long`，故 runtime JSON 被写成字符串
`"0"` / `"1"`。

preflight 使用裸 `new ObjectMapper()`，同一字段保持 JSON integer，所以第六轮
preflight 能通过，而 runtime evidence 在 Python 的严格 integer schema 校验处失败。
Python 不应放宽接受数字字符串。

第七轮只做以下最小修复：

1. runtime identity 写出使用不继承应用 `Long→String` 配置的裸 `ObjectMapper`；
2. 应用 mapper 继续用于身份对象读取，不改业务 JSON 合同；
3. 不改 evidence schema、Python verifier、exact suite 或真实依赖流程；
4. 在既有第 8 个 target-guard testcase 中复用实际写出 helper，序列化后断言
   四个 freshness token 均为 JSON integer，保持 7/33 不变。

## 3. FAIL evidence 核对

用户保留的原目录：

```text
C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r6
```

Codex 只读核对结果：

- `manifest.json`: `schemaVersion=1`、`status=FAIL`
- `candidateSha == expectedCandidateSha == 16256002c62c26eae48b8d1c39d2b7a669343980`
- preflight `exitCode=0`，正式 Maven `exitCode=0`
- manifest 精确记录 7 suites，observed/expected 均为 33
- 7 份正式 XML 与 manifest 一致，全部 0 failure/error/skip
- 两份 Maven 日志均包含 `BUILD SUCCESS`
- `SHA256SUMS` 的 13 个 artifact 逐项重算全部一致
- preflight identity 已归档，大小 1105 B，SHA-256：
  `502809826561b456c57cb71d24d7fea176deedd78411a856a819f53bc5a1058b`
- runtime identity 未归档，manifest 中 runtime evidence/target 为 null，符合
  runtime 校验失败后不归档的 fail-closed 顺序
- source snapshot：898 files，tree
  `b80e3898839a77301588ae585f051babbea2c97f`
- snapshot manifest：
  `a53a1966587ae2a2b137b2a9673c04bce411022709aac72b92af62a1b810f083`
- start / after-preflight / end HEAD 与 tracked-clean 证明均绑定候选 SHA

主要归档哈希：

```text
e36344d6de69bee93aebcb934e6ff2f6a8024337b76de16d5c3a831623c0c243  logs/maven-clean-verify.log
f746e984e257c1c58ad90ccf1336d50ee8e92f7e3948c7f70ac6dd5d6f494adb  logs/maven-target-preflight.log
e3ca67f8a5a1aa8500738c31e982ac767f8b5c4d351caedfefb0f5782f28fe1c  manifest.json
c4a5cd3c9140f81e51e5f3456ff8f3948b0b522bf043dfffa27644d2120120be  spec/phase00_ci_gate_spec.json
```

同目录离线 verify FAIL、Compose `config --quiet` PASS，以及 runtime 文件曾在
工作构建树生成后进入校验，来自执行者交回；evidence 目录本身不能独立复现这些
外部动作或检查未归档 runtime 文件。

## 4. 执行环境与清理

以下事实来自执行者回传，本次 Codex 未连接或操作这些资源：

- 全新一次性栈：MySQL 8.0.46、Redis 7.4.9、
  MinIO `RELEASE.2025-04-22T22-12-26Z`
- 门禁前满足 MySQL 0 表、Redis `DBSIZE=0`、MinIO 恰有一个本次
  schema-v2 身份对象
- gate run FAIL、同目录离线 verify FAIL、Compose `config --quiet` PASS
- 本轮未要求或执行浏览器复验
- 执行后只销毁本轮 3 个容器、3 个数据卷与网络；共享 `tcp-*` 未触碰

## 5. 后续闸门

1. 不对 `1625600` 重跑或复用 r6 evidence/identity/数据卷；
2. 完成上述裸 mapper 最小修复及序列化后 JSON integer 回归；
3. 固化包含代码、测试与第七轮材料的新最终 clean SHA；
4. 真实依赖 preflight、formal exact 7/33、evidence finalization、Compose 与
   资源清理由用户/获授权复核环境执行；
5. 新 evidence 与正式独立复核均 PASS 前，不进入最终全量审计，不 merge、
   push、部署、切流或宣称发布 GO。
