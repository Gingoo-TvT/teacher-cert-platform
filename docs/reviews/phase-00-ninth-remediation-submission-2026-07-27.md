# Phase 0 / U-004 第九轮整改提交材料（2026-07-27）

> 本文件是整改者提交材料，不是独立复核结论。最后正式结论仍为第八轮
> **CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 0 Low）**。

- 第八轮正式报告：`docs/reviews/phase-00-eighth-remediation-rereview-2026-07-27.md`
- 第八轮最终 HEAD：`1c9d41696574ff2b4d50a5908f5998c3ed55b437`
- 第九轮实现：`f3fa054`
- 分支：`codex/phase00-remediation`

## 1. 仅整改 P00-R3-M1

第八轮的 withheld marker、final-path capture 与
`.manifest.pass-ready → manifest.json` 文件级 rename 已全部删除。

当前成功路径只有：

1. 从已验证 snapshot bytes 在同文件系统随机私有 sibling 中重建完整 bundle；
2. 私有 bundle 已包含 canonical `manifest.json` 与 `SHA256SUMS`；
3. 对私有 bundle 做 exact payload match 与完整离线 verifier；
4. 原 collection staging 清理成功；
5. 以一次 `private directory → final directory` 原子 rename 作为最后且唯一的成功转换。

rename 成功后不再执行 capture、cleanup、marker 或其它可能改变成功结论的步骤。

## 2. 明确威胁边界

`producer-private` 是所有权前提，不是 ACL 或不可变存储声明：同一 security principal 不得在
私有 bundle 验证至 rename 之间写入该随机 sibling，final 路径在转换期间也必须持续不存在。
目录 rename 原子发布已验证 namespace，但不会冻结 descendants。

任何消费者都必须执行完整离线 verifier / `SHA256SUMS` 校验，禁止只相信
`manifest.status`。发布后同账号主动篡改、断电持久化、远程文件系统特殊语义不在本轮保证内；
本轮未引入跨平台锁、ACL、`renameat2` 或不可变存储。

该边界逐字落实第八轮正式报告给出的最小修法，避免继续叠加“校验后再校验”状态。

## 3. 精确反例与离线回归

- canonical marker rename 入口 mutate/delete：旧实现均先红，得到 `verified=True`；
  新实现不再存在 marker rename，最终 exact 文件集与 checksum 均通过；
- 私有物化 checksum 后 mutation、manifest 后 delete：继续 fail-closed；
- 目录 rename 抛错：`verified=False` 且 final 不出现；
- 发布后 capture 钩子：不再触发，随后独立 checksum 校验通过。

| 门禁 | 结果 |
|---|---|
| `python -B scripts\test_phase00_ci_gate.py`（Windows） | **79 methods：77 PASS / 2 POSIX-only skipped** |
| `mvn -B -ntp -o clean test` | **32 suites / 274 tests**，0 failure/error/skip |
| `mvn -B -ntp -o -DskipTests package` | **9/9 modules BUILD SUCCESS**，Checkstyle 0 |
| 三路独立只读复审 | 正式收窄威胁边界内 **0 finding** |
| `git diff --check` | PASS |

Windows 不能证明两个 POSIX-only 用例实际执行，故不包装为 Linux PASS。

## 4. 由用户或获授权复核环境执行

### 4.1 POSIX 纯离线门禁

```bash
python3 -B scripts/test_phase00_ci_gate.py -v
```

期望 79 methods 全部执行且 `skipped=0`。

### 4.2 会连接或操作真实依赖——仅用户执行

> 以下步骤会连接或操作 Docker、MySQL、Redis、MinIO；Codex 不执行。

在全新一次性隔离栈、全新数据卷与全新 identity object 上，对最终 clean SHA 重跑：

1. Phase 0 exact gate：7 suites / 33 testcases；
2. 对同一 evidence 运行离线 verifier；
3. `docker compose -f docker-compose.dev.yml config --quiet`；
4. 仅销毁本轮专属容器、网络与数据卷并回传清理结果。

门禁前 freshness 仍须为 `0/0/1/0`，不得复用 r7/r8 资源或 evidence，不得触碰共享
`tcp-*` 资源。浏览器不要求。

## 5. 放行边界

1. 当前正式状态仍是 CHANGES_REQUESTED；
2. 第九轮自测不能自行关闭第八轮 1 Medium；
3. Linux 79/79、真实 exact gate、离线 verify、Compose 与专属资源清理均须绑定新最终 SHA；
4. 正式独立增量复核 PASS 后，Phase 0 才能进入最终全量审计；
5. 当前不得 merge、push、deploy、切流或宣称稳定发布 GO。
