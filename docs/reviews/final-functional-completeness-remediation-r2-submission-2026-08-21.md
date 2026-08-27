# 最终功能完整性复核 R2 整改提交（2026-08-21）

## 1. 提交状态与边界

- 当前状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REREVIEW_PENDING`。
- 本材料只提交最新正式报告中的 `0C / 1H / 5M / 0L` 六项 finding 整改；提交方不宣称 finding 已关闭，也不签发功能 PASS。
- 项目继续 `CHANGES_REQUESTED / NO-GO`；本轮未 merge、push、deploy、cutover，也未连接或修改现有 MySQL/Redis/MinIO、生产容器或数据。
- 可选 WS-15 仍不作为功能整改前置；本轮仅补业务不变量、一个必要列迁移与常驻回归，没有引入额外框架。

## 2. 原复核对象

- 正式报告：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff958-9b0d-72f1-b15b-2487f4fd3e2b\final-functional-completeness-r1-independent-rereview-20260821-9cc342c0-local\review.md`
- 报告 SHA-256：`3cad9705cf66028c21c20a2483f58e126bb8bcbd2e5d91da8c2334a997827c61`。
- 原候选：HEAD `aa3509a19151e2caad03014324b9b36e98bc0f05`，fingerprint
  `9cc342c0e274f868ccb2afa3e5b7e52a88677432cb90728939ae6240485da880`。
- 原 manifest SHA-256：`cdac083dc97484e561f15b4f9431e137730a996e5edd06249c3aad1e89268c43`，
  213 tracked / 196 untracked。
- 正式结论：`CHANGES_REQUESTED（0C / 1H / 5M / 0L）`。

## 3. 最小整改矩阵

| 原 finding | 本地整改实现 | 常驻回归 |
|---|---|---|
| High：证书更正号未推进序列 | `correct()` 锁定读取最新证书；最终合法编号与证书更新、审计在同一事务推进 `cert_sequence`，失败整体回滚 | `CertificateServiceCorrectionTest`；Phase 9 `00001→00002→00003` 与失败回滚场景 |
| Medium：附件 ZIP 漏免考佐证 | 从已授权学生与年度的免考申请批量收敛 `exemption_material`，加入同一清单和流式 ZIP，并再次核对父申请/学生一致性 | `ExchangeAttachmentLinkTest` 覆盖过程材料、免考佐证、视频真实字节及范围外材料排除 |
| Medium：STANDARD 把 N/O/Z 当无条件必填 | 通用循环只校验 `required=true`；非 `education_master` 再条件校验 N/O，Z 保持可空 | `ExchangeExportGuardTest` 覆盖教育类研究生 N/O 空、普通身份缺 N/O、Z 空 |
| Medium：视频时长参数缺范围 | `durationTarget=1..86400` 秒，`durationTolerance=0..3600` 秒；默认 900/60 不变 | `SystemManagementServiceImplTest` 正反边界 |
| Medium：评审人数热变更卡在途任务 | V35 为 `video_review` 增加非空 `reviewer_count`；已有任务按有效普通任务数回填，未指派记录按当前参数回填，新评审创建时冻结；指派和结算只读冻结值 | `VideoReviewServiceAssignmentTest`；Phase 7 旧双人评审热改三人仍结算、新评审按三人；`V35VideoReviewerCountSnapshotMigrationIT` |
| Medium：并发证书更正缺同版本约束 | `CertificateMapper.selectByIdForUpdate()` 串行化更正；每个请求基于最新锁定聚合重新组装和完整校验 | `CertificateServiceCorrectionTest` 最新聚合反例；Phase 9 既有更正/签发交错场景按行锁语义复验 |

上述“本地整改实现”是提交方状态，不等于独立复核的 `CLOSED` 裁定。

## 4. 本地门禁

- 后端九模块 `mvn -B -ntp -o test`：**68 suites / 440 tests**，0 failure / 0 error / 0 skip；Checkstyle 0。
- 后端九模块 `mvn -B -ntp -o -DskipTests package`：PASS；启动模块 82 个测试源编译成功。
- 聚焦：证书更正 **3/3**；视频/参数 **15/15**；附件与 STANDARD **12/12**；导入身份语义与导出行构建 **7/7**。
- 候选 manifest 工具测试 **6/6**；WS-7 合同 PASS；WS-8/V33 合同 PASS；`git diff --check` PASS。
- 本轮没有改前端产品字节，因此不把上一候选的前端结果冒充本轮重跑结果。

## 5. 仍需独立执行的增量证据

- Phase 7、Phase 9 与 V35 的新增真实 MySQL 场景已通过编译，但提交方未连接现有依赖环境，也未启动容器执行 IT。
- 独立复核可在一次性隔离 MySQL/Redis/MinIO 环境重跑：证书更正后下一号及失败回滚、评审人数热变更、V35 回填，以及免考佐证 ZIP 的真实对象字节与范围边界。
- 本轮只处理正常业务操作可触发的完整性问题；不要求把可选 WS-15、部署或供应链范围并入本次功能增量。

## 6. 新候选身份

- 外置 manifest 目标：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-final-functional-completeness-remediation-r2-candidate-2026-08-21.json`。
- 为避免治理文档写回导致自指纹漂移，本文件不内嵌新 fingerprint；所有仓库字节稳定后生成并立即 verify 的外置 manifest 是本次复核对象的唯一身份依据。
- manifest 生成后不得再修改候选字节；如有任何仓库变化，必须重新 capture 并以新 fingerprint 复核。
