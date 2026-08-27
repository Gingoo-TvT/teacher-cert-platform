# 最终功能完整性复核 R1 整改提交（2026-08-21）

## 1. 提交状态与边界

- 当前状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REREVIEW_PENDING`。
- 本材料只提交正式报告中的 `0C / 3H / 4M / 1L` 八项 finding 整改，不由提交方宣称 finding 已关闭，也不签发功能 PASS。
- 项目继续 `CHANGES_REQUESTED / NO-GO`；本轮未 merge、push、deploy、cutover，也未连接或修改现有生产容器/数据。
- 可选 WS-15 仍不作为功能整改前置；本轮没有为纯优化引入异步审计或额外框架。

## 2. 原复核对象

- 正式报告：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff958-9b0d-72f1-b15b-2487f4fd3e2b\final-functional-completeness-independent-rereview-20260821-aaad823e-local\review.md`
- 报告 SHA-256：`bf976b4151d94ac1fb9e101f95f894cd5d70665adb7f49aee6eed4434556bee7`。
- 原候选：HEAD `aa3509a19151e2caad03014324b9b36e98bc0f05`，fingerprint
  `aaad823edca27d5691c655bf09497575e84955b250baa5de6f2a4a4dadd5d3c8`。
- 原 manifest SHA-256：`510fae01839e0cc6f4908164089dc386bf312cfb2317e43f64bfab81a37b4e96`，
  208 tracked / 194 untracked。
- 正式结论：`CHANGES_REQUESTED（0C / 3H / 4M / 1L）`。

## 3. 最小整改矩阵

| 原 finding | 本地整改实现 | 常驻回归 |
|---|---|---|
| High：视频 A/B 改派 B/C 后可能混入 A 的评分 | 指派事务先锁定视频审核行；目标集合变化且已有评分时拒绝；无评分时原子软删移除任务、恢复或新增目标任务；结算只读取当前未删除且数量恰为 N 的 REVIEWER 集合 | `VideoReviewServiceAssignmentTest`；`Phase7VideoReviewIT` 新增未评分改派、已评分拒绝及旧任务不计票场景 |
| High：完整审核表缺明细，附件 ZIP 只有清单且漏无证书学生 | FULL_REVIEW 改为年度授权学生与材料/免考/测试/视频/证书活动的并集，批量装载初复审、材料、免考、测试、视频和证书字段；附件导出改为流式 ZIP，写入清单及真实材料/视频对象 | `Phase10ExchangeIT` 新增无证书学生、跨学院对照、精确 ZIP 三条目及对象字节断言 |
| High：证书编号年份与签发年份可矛盾 | 签发时要求编号前四位等于签发日期年份，并把读取时证书编号纳入条件更新，防止并发更正后按旧编号签发 | Phase 9 新增同年正例、跨年拒绝与并发编号变化回归 |
| Medium：STANDARD 默认混入不可上报状态且无批次/状态闭环 | 默认仅选择 `ISSUED/EXPORTED/ARCHIVED`；写出前校验 A–Z 26 列；成功流式写出后记录导出批次，`ISSUED` 条件流转为 `EXPORTED`，重导保持原状态并关联审计 | `ExchangeExportGuardTest` 与 Phase 10 状态/批次回归 |
| Medium：UPDATE_EMPTY 无条件覆盖任教学科三元组 | 学科 id/code/name 作为原子组；仅整组为空或策略为 OVERWRITE 时替换，并以最终培养目标、学段、学科再次执行联动校验 | Phase 10 UPDATE_EMPTY/OVERWRITE 与最终联动回归 |
| Medium：正式证书字段未完整复用权威规则 | 签发人限定为启用的 GLOBAL `cert_issuer`；更正复用完整 V-11 和 `TrainingLinkValidator`；前端改为签发人、培养目标、学段和学科联动选择 | Phase 9/48 增加未知签发人、错误编号段及错误联动反例 |
| Medium：参数页可保存阻断业务的值 | 已知参数按业务键校验：评审人数 2–10、学校/省码 5/2 位数字、分数/分差 0–100、文件大小上限为正数 | `SystemManagementServiceImplTest` 参数边界 8/8 |
| Low：过程性合格判定可能显示错误学生 | 管理端改为行级“合格判定”；请求必须携带显式行身份和年度，移除旧抽屉/首行静默回退；弹窗固定显示学号、姓名和年度 | Playwright 多学生与旧抽屉状态场景 |

上述“本地整改实现”是提交方状态，不等于独立复核的 `CLOSED` 裁定。

## 4. 本地门禁

- 后端九模块 `mvn -B -ntp -o test`：**67 suites / 430 tests**，0 failure / 0 error / 0 skip；Checkstyle 0。
- 旧 STANDARD 隐私导出夹具已补齐新必填业务字段；`ExchangeExportGuardTest` **8/8**。
- 视频指派聚焦单测 **10/10**；参数边界 **8/8**；Exchange 附件链接 **1/1**、导出行构建 **4/4**。
- 前端：lint、应用 type-check、测试 type-check、production build 均 PASS；Vitest **16/16**；Playwright 产品冒烟 **7/7**。
- 候选 manifest 工具测试 **6/6**；WS-7 合同 PASS；WS-8/V33 合同 PASS；`git diff --check` PASS。

## 5. 仍需独立执行的增量证据

- 新增/改造的 Phase 7、Phase 9、Phase 10、Phase 48 IT 已通过编译，但本机当前只有生产容器；提交方未连接或修改它们，也未以生产依赖运行 IT。
- 独立复核应在一次性隔离 MySQL/Redis/MinIO 环境重跑上述新增场景，重点核对当前评审集合、无证书 FULL_REVIEW、真实 ZIP 对象字节、STANDARD 批次/状态事务和证书正式字段。
- 前端 7/7 已覆盖 Low finding 的明确学生目标；独立复核可按需复验，不要求把可选 WS-15 或部署/供应链范围并入本次功能增量。

## 6. 新候选身份

- 外置 manifest 目标：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-final-functional-completeness-remediation-r1-candidate-2026-08-21.json`。
- 为避免治理文档写回导致自指纹漂移，本文件不内嵌新 fingerprint；所有仓库字节稳定后生成并立即 verify 的外置 manifest 是本次复核对象的唯一身份依据。
- manifest 生成后不得再修改候选字节；如有任何仓库变化，必须重新 capture 并以新 fingerprint 复核。

