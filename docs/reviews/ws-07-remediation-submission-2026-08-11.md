# WS-7 首轮退回最小整改提交

> 上一整改 fingerprint
> `95e9876379589b8ce4a80432e3a11848028f6abcd46e52241f6a052c034ba444` 及其 manifest 已转为历史；其报告纠正版为
> `C:\Users\wenbibuhaoqwq\Documents\脚本\ws07-remediation-independent-rereview-20260811-95e98763\review.md`
> （SHA-256 `f9d7d6233d6a9ef0d7e99ff84b4049d49ca2f285028002c57957d9ff528e3c49`；旧 `8ecdad33...c6261`
> 字节已 superseded）。上一轮三个 Low 仅记为 `3/3 CLOSED / PARTIAL_REMEDIATION_VERIFIED`，不构成任何 PASS。
>
> 最新继续复核报告：
> `C:\Users\wenbibuhaoqwq\Documents\脚本\ws07-continuation-independent-review-20260811-5753c930\review.md`
> （SHA-256 `3ed3182f44b41b8d940d63daed2086866c547335e0359193fe61e51bdb7c1969`）。
>
> 当前正式状态：`CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 1 Low）`
>
> 本轮开发提交状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_REREVIEW_PENDING`
>
> 分支：`feature/ws07-supply-chain`
>
> HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
>
> 本轮整改候选 manifest：
> `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws7-continuation-remediation-r2-candidate-2026-08-11.json`
> （完整 fingerprint 以该外部文件为准，capture 后不回写仓库文件。）

本文件是开发者整改提交，不是独立 PASS、项目发布 GO，也不授权 merge、push、deploy 或 cutover。

## 1. 正式退回基线

- 首个候选 fingerprint：
  `b8055c66ff1472955a0ad2556175ed0cbc747d0c48434820d290fa7fbbda223b`。
- 正式报告：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\ws07-stage-independent-review-20260811-b8055c66\review.md`，
  SHA-256 `8f211ce9021f310a23a7476f57ae340bac89285a9bb288c08acdb494702383d8`。
- 正式结论：`CHANGES_REQUESTED / INDEPENDENT_REVIEW_PENDING（0 Critical / 0 High / 1 Medium / 3 Low）`。
- 报告确认双镜像 build、默认 UID/GID、目录可写、Compose 规范模型、非 root `nginx -t`、静态合同与
  workflow 解析均通过；本轮不重跑未受影响的双镜像本地 build。

## 2. 三个 Low 的最小整改

1. 镜像身份步骤先独立取得 backend/frontend image ID，再分别校验
   `^sha256:[0-9a-f]{64}$`，最后才写 `image-identities.txt`。`printf` 不再包裹 `docker image inspect`
   命令替换；inspect 失败会直接终止步骤。
2. `scripts/test_ws7_image_identity_failure.sh` 用自然退出的 stub 让 `docker run` 成功、
   `docker image inspect` 返回 17，断言步骤非零退出且身份文件未创建；CI 的 WS-7 合同步骤执行该反例。
3. CURRENT plan、HANDOFF、PROGRESS 与相关现行入口统一记录当前分支、已冻结 b805 manifest、完整
   fingerprint 和正式退回结论；WS-8 保持未启动。
4. `.env.example` 不再把容器 nginx 主 GID 101 当作宿主示例，改为必须替换的宿主专用证书读取组占位符。
   README/Phase 14 要求部署前核对宿主组名、数字 GID 和成员；Compose 配置门禁显式覆盖测试 GID `21001`。

## 3. 开发者增量门禁

| 门禁 | 结果 |
|---|---|
| `python scripts/test_ws7_supply_chain_contract.py` | PASS |
| `bash scripts/test_ws7_image_identity_failure.sh` | PASS；inspect exit 17，未生成身份文件 |
| 两份 workflow 使用现有 `js-yaml` 解析 | PASS；CI 4 jobs，Phase 41 1 job |
| `TLS_CERTIFICATE_GID=21001` 覆盖下 production Compose `config --quiet` | PASS |
| `git diff --check` | PASS |

以上命令均自然退出；未启动常驻服务，未连接线上，未运行依赖/镜像扫描或攻击性检查。

## 4. 最新继续复核正式结论

- 正式 verdict：`CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 1 Low）`。
- 上一轮三个 Low：仅记为 `CLOSED（3/3）/ PARTIAL_REMEDIATION_VERIFIED`；该 finding 生命周期不构成 PASS。
- Medium M01：同一冻结候选的 GitHub Hosted CI 双 SPDX、镜像身份、校验和与 artifact 仍未产生。
- Medium M02：95e98763 manifest 对复核时工作树 verify 失败，observed `5753c930...bf91` 且没有新 manifest。
- Low L01：活动 WS-7 材料在存在开放 Medium 时仍使用冲突 PASS 标签。
- 项目：`CHANGES_REQUESTED / NO-GO`；WS-7 未通过，WS-8 不启动。

## 5. 本轮最小整改提交

1. 活动 WS-7 段统一为 `3/3 Low CLOSED / PARTIAL_REMEDIATION_VERIFIED` 与正式 `CHANGES_REQUESTED`，移除冲突
   PASS 标签；上一报告改引纠正版 SHA，并明确旧 SHA 已 superseded。
2. WS-7 静态合同增加状态互斥反例：活动段存在 `CHANGES_REQUESTED` 时拒绝冲突 PASS 标签；历史 WS-5、
   WS-6、F02、F07 的既有通过记录不受影响。
3. 所有治理字节定稿后，仓库外冻结
   `teacher-cert-ws7-continuation-remediation-r2-candidate-2026-08-11.json`；完整 fingerprint 只从该外部 manifest
   读取，不在 capture 后回写受覆盖文件。

以上只构成 `LOCAL_REMEDIATION_READY / INDEPENDENT_REREVIEW_PENDING`，不得自行关闭正式 2 Medium / 1 Low。

## 6. Hosted CI Medium 保持开放

Phase 14 的正式门禁保持为 GitHub Hosted CI，不接受本地或“等价隔离 runner”替代。当前仓库没有 remote，
本机没有 `gh`，也没有本候选的 Hosted run；因此双 SPDX JSON、`image-identities.txt`、`SHA256SUMS` 与上传
artifact 尚未真实生成，本项继续 OPEN。

关闭本项必须在用户授权后，以明确 allowlist 物化同一冻结候选、配置用户指定 remote、通过 PR 触发 Hosted CI，
并从同一 run 下载 artifact：复算 `SHA256SUMS`、解析两份 SPDX、核对双 image ID、source revision、不可变标签、
run ID/attempt、PR head/base SHA 与实际 `GITHUB_SHA`。不需要 merge、deploy、cutover、扫描、签名或 registry push。

在身份/措辞增量重核和 Hosted 证据全部完成并取得阶段 PASS 前，WS-7 保持 `CHANGES_REQUESTED`，项目继续
`CHANGES_REQUESTED / NO-GO`，WS-8 不启动。
