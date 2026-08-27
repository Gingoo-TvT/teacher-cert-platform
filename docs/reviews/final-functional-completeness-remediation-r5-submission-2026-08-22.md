# 最终功能完整性复核 R5 整改提交（2026-08-22）

## 1. 提交状态与边界

- 当前状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REREVIEW_PENDING`。
- 本材料只提交 R4 正式报告中 `0C / 0H / 1M / 0L` 的唯一迁移 finding 整改；提交方不宣称 finding 已关闭，也不签发功能 PASS。
- 项目继续 `CHANGES_REQUESTED / NO-GO`；本轮未 merge、push、deploy、cutover，未连接或修改共享 `*-prod` MySQL/Redis/MinIO、容器或数据。
- 本轮只修正常升级后的新评审创建，不改生产 Java、前端、接口或框架。

## 2. 原复核对象

- 正式报告：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff958-9b0d-72f1-b15b-2487f4fd3e2b\final-functional-completeness-r4-independent-rereview-20260822-29b03079-local\review.md`
- 报告 SHA-256：`37dc0a95a0b52a6a083e961e2c2c09b52541c601bd968316a2d147293d44727b`。
- 原候选：HEAD `aa3509a19151e2caad03014324b9b36e98bc0f05`，fingerprint
  `29b030794b6d693c3486b6320e5f0f150a51a76d4f2cc30dabd567cac5e9f784`。
- 原 manifest SHA-256：`fcfe2c578d29bfa22b1ed0dce737f5b1ed9008a0646bebe140b6cd8f1af44c0e`，
  219 tracked / 202 untracked。
- 正式结论：`CHANGES_REQUESTED（0C / 0H / 1M / 0L）`。报告同时正式确认 R3 两项 Medium 已 CLOSED。

## 3. 唯一 Medium 的最小整改

正常业务失败条件是：旧版本允许当前 `video.reviewerCount` 为 1 或 11；V35 虽能冻结历史快照，但若不归一全局参数，升级后所有新评审都会被既有 2..10 业务校验拒绝。

R5 只调整 V35 的执行顺序与回归：

1. 先按旧参数和有效普通任务数回填全部历史 `video_review.reviewer_count`。
2. 历史快照完成后，再把当前参数 `<2` 归一为 2、`>10` 归一为 10；2..10 保持不变。
3. 参数归一不再回写历史快照，既有任务、分数和提交态均不修改。
4. 迁移 IT 覆盖旧参数 1/11；其中“历史无任务快照仍为 11、当前参数已经为 10”精确证明冻结发生在归一之前。
5. Phase 7 IT 验证合法上限 10 可正常创建新评审；默认 2 与中间值 3 的创建路径已有同一用例覆盖。

以上是提交方实现事实，不等于独立复核的 `CLOSED` 裁定。

## 4. 本地门禁

- 后端九模块 `mvn -B -ntp -o test`：**68 suites / 447 tests**，0 failure / 0 error / 0 skip；Checkstyle 0。
- `VideoReviewServiceAssignmentTest`：**10/10**，0 failure / 0 error / 0 skip。
- `mvn -B -ntp -o -pl platform-boot -am -DskipTests test-compile`：PASS；启动模块 82 个测试源编译成功。
- 后端九模块 `mvn -B -ntp -o -DskipTests package`：PASS。
- 候选 manifest 工具：**6/6**；WS-7 合同、WS-8/V33 合同与 `git diff --check`：PASS。
- 本轮没有前端字节变更，因此未把旧候选的前端运行结果冒充为 R5 新证据。

## 5. 仍需独立执行的增量证据

- V35 与 Phase 7 新增真实依赖场景已通过编译；当前机器只运行共享 `*-prod` 依赖，提交方未连接或写入，也未启动另一套容器。
- 独立复核应在一次性隔离 MySQL/Redis/MinIO 环境执行：V34 分别预置参数 1/11 与对应历史记录，升级 V35 后核对历史快照、任务、成绩和提交态不变，当前参数分别为 2/10，随后正常创建新评审。
- R4 正式报告要求稳定发布前保存 Phase 7、Phase 9 与 V35 的 Failsafe XML；本次独立增量只需裁定上述唯一 Medium，不能把未执行的真实依赖场景写成 PASS。
- 部署、供应链、可选 WS-15 与项目 GO 仍是独立边界。

## 6. 新候选身份

- 外置 manifest：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff96a-c185-7730-852e-f3007f3b2bb7\teacher-cert-final-functional-completeness-remediation-r5-candidate-2026-08-22.json`。
- 为避免治理文档写回导致自指纹漂移，本文件不内嵌新 fingerprint；所有仓库字节稳定后生成并立即 verify 的外置 manifest 是本次复核对象的唯一身份依据。
- manifest 生成后不得再修改候选字节；如有任何仓库变化，必须重新 capture 并以新 fingerprint 复核。
