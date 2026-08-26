# WS-8 整体 Hosted workflow 第三轮整改提交说明（2026-08-13）

## 1. 正式输入与状态边界

- 分支：`feature/ws08-idcard-encryption`
- 基线 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- R2 fingerprint：`09ee0c395efc5322ce368b6a5fb307a3398f3471c71612c322275e8918a442ba`
- R2 carrier / PR merge SHA：`127d42f7b14b3c5750575a5323973e955515cc6c` /
  `87014fb3c6a8162f6eedb2fcf53e01026291d408`
- Hosted run：`31661893931` / attempt `1`
- 正式报告：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\ws08-hosted-independent-rereview-20260813-09ee0c39-run31661893931\review.md`
  （SHA-256 `CEAF031AB450392BE9CFE01EE440416289E6B2E76B3B801F8198F8DFA2F4D0B4`）
- 正式结论：`CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 1 Low）`
- R3 manifest：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-whole-workflow-remediation-r3-candidate-2026-08-13.json`
- 提交者状态：`LOCAL_REMEDIATION_R3_READY / HOSTED_WHOLE_WORKFLOW_PENDING`

本材料不自行签发 `INDEPENDENT_STAGE_PASS`，不放行 WS-9，也不授权产品主线 merge、deploy 或 cutover。项目继续
`CHANGES_REQUESTED / NO-GO`。

## 2. 已关闭的 profile Medium 与当前 scoped 证据

R2 已验证 `SPRING_PROFILES_ACTIVE: dev` 只作用于 WS-8 selector step。Hosted backend、WS-8 selector 与 artifact
upload 均成功；artifact `9166572200` 精确包含 2 个 Surefire + 4 个 Failsafe XML，合计 **46/46**、
0 failure/error/skip。该结果关闭原 profile Medium，但正式报告仅将其判为 WS-8 scoped PASS。

## 3. 当前 Medium 与最小整改

`scripts/test_ws7_supply_chain_contract.py` 原本把 WS-7 的永久供应链技术合同与 2026-08-11 当时的活动治理快照
耦合，永久要求 HANDOFF/PROGRESS/current plan 仍处于 `feature/ws07-supply-chain`、旧 finding 数和“WS-8 不启动”。
进入 WS-8 后该门禁必然失败，并因 CI `needs` 链跳过 V33 静态合同与最终镜像/SBOM。

R3 只做以下收敛：

1. 保留 Docker digest、非 root、Actions SHA、CI `needs`、双 SBOM、镜像身份与校验和全部技术合同。
2. 活动入口仅核对有界 WS-7 归档：最终 fingerprint、manifest/run、`INDEPENDENT_STAGE_PASS`、双 SPDX、镜像
   身份、校验和/artifact，以及“只放行 WS-8、非项目 GO”。
3. 不再要求全局当前分支/阶段停在 WS-7；边界 marker 不依赖 WS-8 的 `[~]` / `[x]` 或“当前/已通过”措辞。
4. 历史 WS-7 stage/remediation submission 继续锁定当时分支、候选、精确 finding 数、Hosted OPEN、WS-8 阻塞与
   禁止冲突 PASS，不改写 finding 生命周期。
5. 内置篡改反例分别拒绝最终 identity、run/verdict、artifact、双 SPDX、镜像身份、校验和与非 GO 边界漂移。

无产品代码、数据迁移、运行 profile、权限或业务规则变化。

## 4. Low 证据包整改

外部包 `ws08-r2-hosted-evidence-20260813-09ee0c39-run31661893931` 的根 `SHA256SUMS` 已补入：

```text
1a4aa5e440e8aa48beebe34000a91fac6579112b8178a79eb55c09712fb09482  r2-static-review/SHA256SUMS
```

修复后声明集合与实际文件集合双向一致 17/17，逐项 SHA-256 17/17；根清单 SHA-256 为
`9a70e6beab8cd058f7df537a978d29dfe306cefcae29653e102d666cedd7e178`。该 Low 不要求重跑旧 Hosted。

## 5. 本地自然退出门禁

- `python scripts/test_ws7_supply_chain_contract.py`：PASS。
- `python scripts/test_ws8_v33_release_contract.py`：PASS。
- GitHub workflow YAML 解析：PASS。
- `git diff --check`：PASS。

未启动常驻服务、未运行攻击性检查。

## 6. R3 Hosted 验收合同

1. R3 manifest 在 carrier 前后 verify PASS，candidate 与 carrier 的 tracked/untracked 原字节完全一致。
2. 新 Draft PR 的 base/head/merge tree 与 R3 identity 可复算；不覆盖历史分支，不 merge。
3. 整条 workflow conclusion 为 `success`；Phase 41 runner、backend、frontend 与最终镜像/SBOM job 均成功。
4. WS-7 历史合同和 WS-8/V33 静态合同均执行成功，不再 skipped。
5. WS-8 selector 重新生成六份 fresh XML，仍为 tests > 0 且 0 failure/error/skip；artifact summary 绑定
   `GITHUB_SHA` / run ID / attempt，下载 ZIP SHA-256 与 server digest 一致。
6. 最终镜像/SBOM artifact 包含双 SPDX、双 image ID、source revision、`SHA256SUMS`，并通过包内校验。
7. 新证据只提交独立阶段重核；在正式 PASS 前 WS-9 不启动，项目继续 NO-GO。
