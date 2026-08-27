# WS-8 第四轮整改提交说明（2026-08-13）

## 1. 正式输入与裁定边界

- R3 fingerprint：`4b39e52c34419024ed60f86502a71b89cb16ec2d9f5958807d392b2fc9e11d3a`
- R3 manifest SHA-256：`617bd51d08dcbb31f114c0ae6cc21c86a29ec02dd745d9083d441b37ceedc4ba`
- R3 carrier / PR merge SHA：`081b5d1170c10219325e6f7e2941be7bc3d8a743` /
  `34276c2123326b8cfe649e46e90a79eb45542e73`
- R3 Hosted run：`31664938044 / attempt 1`，4/4 jobs success，六 suite 46/46、0 failure/error/skip。
- 独立复核来源：用户负责的 Codex 任务 `019ff958-9b0d-72f1-b15b-2487f4fd3e2b` 最终报告；该结论在本轮
  整改开始时尚未另行落盘，不能用 R3 包内的旧 `09ee0c39` 报告替代。
- 正式结论：`CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 0 Low）`。
- 提交者状态：`LOCAL_REMEDIATION_R4_READY / HOSTED_WHOLE_WORKFLOW_PENDING`。

R3 的候选身份、Hosted 整体绿灯、六套件、V33 静态合同、双镜像/SBOM 与校验和证据均通过独立复算；唯一
Medium 是 WS-7 历史合同的非 GO 语义可假绿。本材料不自行签发 `INDEPENDENT_STAGE_PASS`，不放行 WS-9，也不
授权产品主线 merge、deploy 或 cutover。项目继续 `CHANGES_REQUESTED / NO-GO`。

## 2. 唯一 finding 与最小整改

R3 validator 只禁止未被“不”前缀修饰的“构成/等于项目 GO”，并只对 HANDOFF 做证据词替换反例。因此：

1. 把 `不授权`、`不代表` 或 `不替代` 翻成正向后，部分归档仍可通过；
2. 即使保留原非 GO 句，再追加一条正向授权或“构成项目 GO”，validator 也可假绿。

R4 的功能性整改只修改 `scripts/test_ws7_supply_chain_contract.py`；另同步当前治理入口与本提交说明：

1. 为 HANDOFF、PROGRESS、CURRENT plan、README、audit plan、launch plan、Phase 14 七个有界 WS-7 归档段分别
   固定完整非 GO 原句及对应的冲突正向句；比较前只去除 Markdown 换行空白，不绑定人工折行位置。
2. 正常验证同时要求非 GO 原句存在、对应正向句不存在，并继续拒绝任意未否定的“构成/等于项目 GO”。
3. 七个归档段逐一执行三类反例：负向句替换为正向句、保留负向句并追加对应正向句、保留负向句并追加通用
   “构成项目 GO”；任一情形都必须失败。
4. identity/run/verdict/artifact/WS-8 等证据词的篡改反例覆盖段内全部同名出现，避免只替换首个实例造成测试误判。
5. 历史 WS-7 stage submission 精确锁定 `0C/0H/1M/3L`，remediation submission 同时锁定上一轮
   `0C/0H/1M/3L` 与当轮 `0C/0H/2M/1L`；两者都拒绝新增结构化 `INDEPENDENT_*PASS` verdict。

没有产品代码、数据库迁移、workflow、profile、权限、业务规则或运行配置变化。

## 3. 本地自然退出门禁

- `python -B scripts/test_ws7_supply_chain_contract.py`：PASS；七段正常合同及替换/追加反例均在脚本内执行。
- `python -B scripts/test_ws8_v33_release_contract.py`：PASS。
- `git diff --check`：PASS。

未启动常驻服务、容器或后台进程，也未执行网络、攻击性或 cyber 检查。

## 4. 新候选与 Hosted 验收合同

- R4 manifest 计划固定为：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-whole-workflow-remediation-r4-candidate-2026-08-13.json`。
  fingerprint、tracked/untracked 数量与文件哈希以最终仓库外 manifest 为准，避免候选自引用。
- 只有取得明确授权后，才把该完整候选物化为新 carrier / Draft PR；不覆盖 R3 分支，不 merge。
- 同一候选必须取得整条 Hosted workflow success：Phase 41 runner、backend、frontend、最终镜像/SBOM 4/4；
  WS-7 合同与 WS-8/V33 静态合同都必须实际执行成功；六 suite 必须 fresh 生成且 0 failure/error/skip；最终
  artifact 必须闭合双 SPDX、镜像身份与 `SHA256SUMS`。
- Hosted 证据完成后，只提交用户负责的独立增量重核。只有正式 `INDEPENDENT_STAGE_PASS` 才放行 WS-9；项目级
  GO 仍须后续全量审计与发布流程另行裁定。
