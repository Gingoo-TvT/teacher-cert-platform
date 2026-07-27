# Phase 0 / U-004 第八轮整改提交材料（2026-07-27）

> 本文件是整改者提交材料，不是独立复核结论。第七轮正式结论仍为
> **CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 1 Low）**；
> 第八轮完整门禁和正式独立复核均通过前，Phase 0 与全项目不放行。

- 第七轮正式报告：`docs/reviews/phase-00-seventh-remediation-rereview-2026-07-27.md`
- 第七轮最终材料：`9b97741a3da921e3bce648e0c98fdb2892ec72e2`
- 第八轮实现：`7810715`
- 分支：`codex/phase00-remediation`

## 1. 严格限于两项 finding 的整改

### P00-R3-M1

- 已验证 artifact、checksum、manifest 的确切字节被重建到同文件系统的随机私有目录；
- 私有完整 bundle 先做 payload 对齐和完整离线 verifier；
- 发布前把 canonical `manifest.json` 改为保留 marker，发布到 final 后再次逐字节核对；
- 最后一次原子 rename 才令 `manifest.json` 可见，此后不再执行会把成功改判为失败的校验或清理；
- 任一早期异常最多留下没有 canonical PASS manifest 的 withheld bundle，不能通过 verifier。

反例覆盖 checksum 后 mutation、manifest 后 delete、目录 rename 内 mutation，以及 final-path
capture 抛错；均不能得到“命令成功 + checksum 不一致的 canonical PASS”。

### P00-R3-L1

POSIX evidence root 不再把多段路径一次性交给 `open(O_NOFOLLOW)`。实现从 `/` 开始，
对每个路径段执行 `stat(..., follow_symlinks=False)`、`openat(O_DIRECTORY|O_NOFOLLOW)`、
`fstat` dev/ino 对齐，并持有全部祖先 fd 到递归捕获及末次逐边复核结束。

Linux 专属反例覆盖非末段 symlink 和 stat/open 间替换；两者都必须在任何 artifact reader
被调用前失败。

本轮未修改产品代码、schema、依赖、配置、exact suite 或 33 个 testcase 合同，也未引入
MinIO Admin API、新凭据或新基础设施。

## 2. 整改者离线结果

| 门禁 | 结果 |
|---|---|
| `python -B scripts\test_phase00_ci_gate.py`（Windows） | **75 passed / 2 POSIX-only skipped**，共 77 methods |
| `mvn -B -ntp -o clean test` | **32 suites / 274 tests**，0 failure/error/skip |
| `mvn -B -ntp -o -DskipTests package` | **9/9 modules BUILD SUCCESS**，Checkstyle 0 |
| 两路 finding 专项 + 全 diff 独立只读复审 | **0 finding** |
| `git diff --check` | PASS |

Windows 不能证明 POSIX 两例实际执行；该限制不包装成 PASS。

## 3. 由用户或获授权复核环境执行

### 3.1 POSIX 纯离线门禁

在 Linux/POSIX checkout 对最终 clean SHA 执行：

```bash
python3 -B scripts/test_phase00_ci_gate.py -v
```

期望 77 methods 全部执行且 `skipped=0`。

### 3.2 会连接或操作真实依赖的命令——仅用户执行

> 以下步骤会连接或操作 Docker、MySQL、Redis、MinIO。Codex 不执行。

按既有 Phase 0 动态材料新建一次性隔离栈、全新数据卷与全新 identity object；不得复用 r7
资源或 evidence。门禁前仍须满足 MySQL/Redis/MinIO freshness `0/0/1/0`，并重新生成
candidate、runContext、nonce、issuedAt、对象名/hash、MySQL UUID 与 Redis run_id。

```powershell
git status --short
$candidateSha = (git rev-parse HEAD).Trim()
$env:PHASE00_EXPECTED_CANDIDATE_SHA = $candidateSha
$mavenExe = (Get-Command mvn.cmd -ErrorAction Stop).Source

python -B scripts\phase00_ci_gate.py run `
  --maven $mavenExe `
  --evidence-dir 'C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r8' `
  --expected-candidate-sha $candidateSha

python -B scripts\phase00_ci_gate.py verify `
  --evidence-dir 'C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r8' `
  --expected-candidate-sha $candidateSha

docker compose -f docker-compose.dev.yml config --quiet
```

无论 PASS/FAIL 都保留完整 evidence；随后只销毁本轮专属容器、网络和数据卷并回传清理结果，
不得触碰共享 `tcp-*` 资源。浏览器不要求。

## 4. 放行边界

1. 第七轮真实 7/33 PASS 仍是有效历史证据，但不能替代第八轮最终 SHA；
2. 第八轮 exact 集合仍是 7 suites / 33 testcases；
3. POSIX 77/77、完整真实 gate、离线 verify、Compose 与专属资源清理均应归档；
4. 正式独立增量复核 PASS 后，Phase 0 才能进入最终全量审计；
5. 当前不得 merge、push、部署、切流或宣称稳定发布 GO。
