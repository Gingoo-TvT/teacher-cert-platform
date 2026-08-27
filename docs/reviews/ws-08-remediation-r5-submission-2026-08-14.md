# WS-8 R5 历史归档完整闭包整改提交（2026-08-14）

> 性质：整改者提交材料，不是独立复核报告，不自行签发阶段 PASS。

## 1. 正式退回输入与候选边界

- R4 manifest：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-whole-workflow-remediation-r4-candidate-2026-08-13.json`
- R4 fingerprint：`4ef707be07aa9658620be101be95a32c2501f461bd9f4679a900fe7148e08729`
- R4 manifest SHA-256：`2a12a00af7278dcb624d448020535ad27daa8302ef3923e6145f68b8642cf5f5`
- 正式结论：用户于 2026-08-14 交付的独立增量复核结论为
  `CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 0 Low）`。
- 报告边界：截至本轮整改开始，R4 结论没有另行落盘为新 `review.md` / `independent-review-report.md`；不得用
  `09ee0c39` 的旧 R2 报告替代。
- Hosted 边界：R3 run `31664938044` 仅绑定 R3 fingerprint `4b39e52c...11d3a`，不能外推至 R4 或 R5。

R4 合同脚本的正常测试为 PASS，但这只是既定样例通过，不是 finding 关闭，也不放行 WS-9。项目继续
`CHANGES_REQUESTED / NO-GO`。

## 2. 唯一 Medium 与独立复现

R4 只禁止七段各自的固定正向句，以及未否定的“构成/等于项目 GO”。只要原非 GO 句仍在，追加其它普通同义
授权文字就不会触发这些有限检查。

本轮在不修改文件的内存副本中，将下列四类文字分别追加到 HANDOFF、PROGRESS、CURRENT plan、README、
audit plan、launch plan 与 Phase 14 的 WS-7 有界归档：

1. `本结论授权产品主线 merge、deploy 和 cutover。`
2. `项目当前为 GO。`
3. `项目 GO 已放行。`
4. `该阶段 PASS 同时代表项目发布 GO。`

R4 validator 对上述 **28/28** 组合全部接受，复现正式 finding。根因是有限自然语言禁令不可能证明不可变历史段
没有新增裁定。

## 3. R5 最小整改

功能性改动仍只在 `scripts/test_ws7_supply_chain_contract.py`：

1. 对七段已独立复核、后续只应作为历史归档的有界内容，先移除全部空白，再分别计算并锁定剩余非空白内容的
   SHA-256。这样允许纯空白调整，但任何非空白内容的增、删、改都会失败关闭。
2. 保留 R4 的身份、Hosted artifact、WS-8 交接、精确非 GO 原句、固定冲突句、通用项目 GO 与历史 verdict/count
   检查，提供更具体的失败信息；完整摘要是最终闭包，不再依赖这些有限措辞覆盖全部同义表达。
3. 把上面四类普通同义授权追加逐段加入可执行反例，共 **28** 个新增断言；连同既有替换、固定追加、通用项目
   GO、证据 token 与历史 finding 数反例一起执行。

没有修改产品代码、迁移、workflow、profile、权限、接口、业务规则或运行配置，也没有扩入 WS-9。

## 4. 本地自然退出门禁

- `python -B scripts/test_ws7_supply_chain_contract.py`：PASS；七段基线与新增同义追加反例均实际执行。
- `python -B scripts/test_ws8_v33_release_contract.py`：PASS。
- `python -B scripts/test_candidate_source_manifest.py`：6/6 PASS。
- `git diff --check`：PASS。

未启动服务、容器或后台进程，未执行网络、攻击性或 cyber 检查。

## 5. R5 候选与后续闸门

- R5 manifest 计划固定为：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-whole-workflow-remediation-r5-candidate-2026-08-14.json`。
- manifest 必须在全部治理材料完成后从当前 worktree 捕获并再次 verify；R4 manifest 对 R5 应按设计失败。
- 只有取得明确授权后，才把 R5 完整候选物化为新 carrier / Draft PR 并运行新的整体 Hosted workflow；不覆盖
  R3/R4，不 merge。
- 同一 R5 候选必须重新取得 4/4 jobs、WS-8 六 suite、V33/WS-7 静态合同、双镜像/SPDX、镜像身份与
  `SHA256SUMS` artifact 闭环。
- Hosted 证据完成后只提交用户独立增量重核。正式 `INDEPENDENT_STAGE_PASS` 前 WS-9 不启动；项目级 GO 仍由
  后续总审计与发布流程另行裁定。
