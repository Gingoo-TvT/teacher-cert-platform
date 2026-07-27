# Phase 0 / U-004 第四轮动态门禁失败归档（2026-07-27）

> 本文件归档用户/独立执行者交回的第四轮动态门禁结果。Codex 仅核对了已保留
> evidence 的 manifest、checksum 与预检日志，不把整改者自测或本归档改写成
> 阶段独立 PASS。Phase 0、U-004 与全项目继续 **CHANGES_REQUESTED**。

## 1. 结论

- 候选：`191c395bdf036f4b4e76a727d66dd24abeba58ea`
- 门禁：**FAIL**
- 正式 exact suite：**0/7 suites，0/33 testcases**
- 失败阶段：正式 Maven 前的 `Phase00TargetPreflight`
- 性质：候选代码的确定性缺陷，不是环境抖动，不能通过重跑同一 SHA 绕过

预检失败后 gate 按设计停止，正式 `clean verify` 未启动；因此第四轮候选没有形成
exact gate 合同内的 fresh-schema 应用上下文、正式 7/33 XML 或 runtime target
identity 动态证据。后续单独完成的浏览器启动只能作为 supporting evidence，不能替代
formal gate。

## 2. 根因

`Phase00TargetPreflight.readRedisIdentity` 把 Lettuce
`commands.info("server")` 返回的 Redis INFO server 原始载荷交给
`parseRedisInfo`。Redis INFO 是 CRLF 分隔的多行文本，但候选实现先调用
`requiredText`；后者进入 `cleanText` 后会拒绝任意 `\r` 或 `\n`。只有通过该
单值校验后，代码才会执行下一行的 `split("\\r?\\n")`。

因此任何正常真实 Redis 的多行 INFO 响应都会稳定触发：

```text
java.lang.IllegalStateException: Redis INFO server 含控制字符
  at Phase00TargetPreflight.cleanText(...)
  at Phase00TargetPreflight.requiredText(...)
  at Phase00TargetPreflight.parseRedisInfo(...)
  at Phase00TargetPreflight.readRedisIdentity(...)
```

修复边界是把“整段多行载荷”与“拆分后的单行键值”分开：

1. 整段 INFO 只允许非空且不得包含 NUL；
2. 只按 LF/CRLF 拆行；
3. 拆出的键和值继续逐项执行单行 `cleanText`；
4. 用真实 Redis 风格 CRLF、LF、NUL、裸 CR/行内控制字符补确定性反例。

## 3. FAIL evidence 核对

用户保留的原目录：

```text
C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r4
```

Codex 只读核对结果：

- `manifest.json`: `status=FAIL`、`schemaVersion=1`
- `candidateSha == expectedCandidateSha == 191c395bdf036f4b4e76a727d66dd24abeba58ea`
- `preflight.exitCode=1`
- `preflight.sourceSnapshot.fileCount=894`
- `preflight.sourceSnapshot.treeSha=4fd8d8df5b9e1b74dfdd127c4f70be236ee149f5`
- `preflight.sourceSnapshot.manifestSha256=d4b00c5356396f3fb34320073d9f7bff4a946c1f5b2424cd7cee22f1cbc7ea16`
- `preflight.formalSuiteContract=false`
- `run.executed=false`
- `suites=[]`
- `targets.preflight=null`、`targets.runtime=null`
- errors 精确记录预检退出 1 与
  `platform-boot/target/phase00-target-preflight.json` 不存在
- 预检日志确认 `Phase00TargetPreflight` 1 个测试发生 1 个 error，
  reactor 最终 `BUILD FAILURE`

归档 `SHA256SUMS` 为：

```text
994b65f34a1934b7a42d9c937ce0cea0c46e340062da9e1f7e7482e183962c48  logs/maven-target-preflight.log
f18e0911cd29dc29eb12eb46a1303ef91946d5b33d2d52e5b9ac31e05c3e2634  manifest.json
48f328cf1a2ca182974474b2ce1ea35ed7b544604a015def9c0df4ac4ba944ed  spec/phase00_ci_gate_spec.json
```

执行者对同一目录运行离线 verify 也得到 FAIL/exit 1，与 run 结论一致；没有出现
“run 失败但 verify 放行”的状态机漏洞。

## 4. 执行环境与命令偏离

以下环境事实来自执行者回传，本次 Codex 未连接或重建这些资源：

- 一次性 Compose 项目 `tcp-phase00`，使用 MySQL 8.0.46、Redis 7.4.9、
  MinIO `RELEASE.2025-04-22T22-12-26Z`
- 门禁前满足 MySQL 0 表、Redis `DBSIZE=0`、MinIO 恰有 1 个本次
  schema-v2 identity 对象且无其它 version/delete marker
- 身份对象与 MySQL UUID、Redis run_id 均来自该隔离栈
- Compose 静态展开通过
- 非生产 `/doc.html` 真实渲染并从 UI 调用健康接口返回统一 `Result`
- 后端已停止，8080/5173 无遗留监听；本次容器、网络与三个数据卷已销毁，
  共享 `tcp-*` 开发栈未触碰

Compose 与浏览器结果均为执行者回传的独立 supporting evidence；它们不能补足第四轮
exact gate 的 0/33，且仍需由新最终 SHA 的复核口径决定是否重跑。

第四轮材料的 Windows 命令还有一处可移植性缺口：Python 以
`shell=False` 启动子进程时，默认无扩展名 `mvn` 不能解析本机的 `mvn.cmd`。
执行者首次运行在任何预检/正式测试前以
`required tool versions are unavailable: ['maven']` 失败；随后只增加：

```powershell
--maven 'C:\Users\wenbibuhaoqwq\tools\apache-maven-3.9.9\bin\mvn.cmd'
```

才进入上述真实预检。第一次失败不是产品动态信号，但证明提交命令和 gate 默认值都应
在下一候选中补齐 Windows 契约。

## 5. 后续闸门

1. 不对 `191c395` 重跑动态门禁；
2. 修复 Redis INFO 多行解析并补纯离线回归；
3. 修复 Windows Maven 默认解析，同时在提交材料中显式给出绝对
   `mvn.cmd`；
4. 固化包含代码、测试和材料的新最终 clean SHA；
5. 仅由用户/获授权复核环境为新 SHA 重建全新隔离栈并重跑完整门禁；
6. 新 evidence 与正式独立复核均 PASS 前，不进入最终全量审计，不
   merge、push、部署、切流或宣称发布 GO。
