# 最终功能完整性复核 R3 整改提交（2026-08-21）

## 1. 提交状态与边界

- 当前状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REREVIEW_PENDING`。
- 本材料只提交最新正式报告中的 `0C / 0H / 2M / 0L` 两项 finding 整改；提交方不宣称 finding 已关闭，也不签发功能 PASS。
- 项目继续 `CHANGES_REQUESTED / NO-GO`；本轮未 merge、push、deploy、cutover，也未连接或修改现有 MySQL/Redis/MinIO、生产容器或数据。
- 可选 WS-15 仍不作为功能整改前置；本轮只补业务并发版本与历史在途评审恢复，没有引入额外框架。

## 2. 原复核对象

- 正式报告：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff958-9b0d-72f1-b15b-2487f4fd3e2b\final-functional-completeness-r2-independent-rereview-20260821-6baa7079-local\review.md`
- 报告 SHA-256：`41c7d71ac5a2e4498618a02a300da907df2acb4dbc948a02e63f87058a844b3f`。
- 原候选：HEAD `aa3509a19151e2caad03014324b9b36e98bc0f05`，fingerprint
  `6baa7079dcea8c6609c89a0f63d018e1fb1ca18efc4d1bd09450936cb81dfcd3`。
- 原 manifest SHA-256：`b7d77e7f70b1d72f38bbd541272cc715a5ab6af31c7cc18406be9219f0b2645d`，
  216 tracked / 200 untracked。
- 正式结论：`CHANGES_REQUESTED（0C / 0H / 2M / 0L）`。

## 3. 最小整改矩阵

| 原 finding | 本地整改实现 | 常驻回归 |
|---|---|---|
| Medium：陈旧全量证书更正表单覆盖已成功修改 | 详情返回服务端聚合快照的 `correctionRevision`；前端打开抽屉时保存并提交。`correct()` 取得行锁后先重算比较，缺失/陈旧 token 在序列占用、证书更新和审计前失败 | `CertificateServiceCorrectionTest` 覆盖旧表单恢复先前学科和缺 token；Phase 9 真实请求源码覆盖 A 成功、B 旧 revision 被拒绝 |
| Medium：V35 历史单评审已提交记录永久卡住 | V35 对单任务记录冻结为迁移时合法人数，非法参数回退 2，保留任务/分数/提交态；指派目标必须包含全部已提交教师，可补派或替换未提交教师；已提交教师后续停用不重验资格，新加入及未提交教师仍按当前资格校验 | `VideoReviewServiceAssignmentTest` 覆盖保留/替换/补派/停用历史教师；Phase 7 覆盖补派 B 后结算；V35 IT 覆盖 V34→V35 回填与历史任务保留 |

上述“本地整改实现”是提交方状态，不等于独立复核的 `CLOSED` 裁定。

## 4. 本地门禁

- 后端九模块 `mvn -B -ntp -o test`：**68 suites / 445 tests**，0 failure / 0 error / 0 skip；Checkstyle 0。
- 后端九模块 `mvn -B -ntp -o -DskipTests package`：PASS；启动模块 82 个测试源编译成功。
- 聚焦：证书更正 **5/5**；视频指派 **8/8**。
- 前端：lint、type-check、tests type-check、build PASS；Vitest **16/16**；Playwright **7/7**。
- 候选 manifest 工具测试、WS-7 合同、WS-8/V33 合同与 `git diff --check`：PASS。

## 5. 仍需独立执行的增量证据

- Phase 7、Phase 9 与 V35 的新增真实 MySQL 场景已通过编译，但提交方未连接现有依赖环境，也未启动容器执行 IT。
- 独立复核可在一次性隔离 MySQL/Redis/MinIO 环境重跑：陈旧证书全量表单、V34 单评审已提交记录升级、已提交教师停用后补派，以及补派完成后的最终结算，并保存机器可读报告。
- 本轮只处理正常授权业务路径；不要求把可选 WS-15、部署或供应链范围并入本次功能增量。

## 6. 新候选身份

- 外置 manifest：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff96a-c185-7730-852e-f3007f3b2bb7\teacher-cert-final-functional-completeness-remediation-r3-candidate-2026-08-21.json`。该目录位于仓库外且是当前任务的受控证据目录；如需复制到 `Documents\脚本`，必须保持文件字节与 SHA-256 不变。
- 为避免治理文档写回导致自指纹漂移，本文件不内嵌新 fingerprint；所有仓库字节稳定后生成并立即 verify 的外置 manifest 是本次复核对象的唯一身份依据。
- manifest 生成后不得再修改候选字节；如有任何仓库变化，必须重新 capture 并以新 fingerprint 复核。
