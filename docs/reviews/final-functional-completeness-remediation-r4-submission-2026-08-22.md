# 最终功能完整性复核 R4 整改提交（2026-08-22）

## 1. 提交状态与边界

- 当前状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REREVIEW_PENDING`。
- 本材料只提交最新正式报告中的 `0C / 0H / 2M / 0L` 两项相邻业务 finding 整改；提交方不宣称 finding 已关闭，也不签发功能 PASS。
- 项目继续 `CHANGES_REQUESTED / NO-GO`；本轮未 merge、push、deploy、cutover，未连接或修改共享 `*-prod` MySQL/Redis/MinIO、容器或数据。
- 本轮只修正常业务链路，不引入额外框架、可选 WS-15 或攻击性验证。

## 2. 原复核对象

- 正式报告：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff958-9b0d-72f1-b15b-2487f4fd3e2b\final-functional-completeness-r3-independent-rereview-20260821-a43d5a75-local\review.md`
- 报告 SHA-256：`5765c347958227ca5812079d8a5b881b6bf7ae48294a0c6dcf51f3b114335abe`。
- 原候选：HEAD `aa3509a19151e2caad03014324b9b36e98bc0f05`，fingerprint
  `a43d5a758a6d3942102cd994e1a74f0e6f19144846af82bcb535830cef156c80`。
- 原 manifest SHA-256：`cf2ea1f658231948fdb596f23182e124065ce177eb9b75066021fb505172d2be`，
  219 tracked / 201 untracked。
- 正式结论：`CHANGES_REQUESTED（0C / 0H / 2M / 0L）`。报告同时确认 R2 两项原 finding 已按精确场景 CLOSED。

## 3. 最小整改矩阵

| R3 finding | 本地整改实现 | 常驻回归 |
|---|---|---|
| Medium：证书写接口返回的 `correctionRevision` 与持久化记录不一致 | generate、correct、issue、export、archive、void 的成功响应统一按证书 id 重读持久化行后映射 VO；reissue 复用 generate 路径 | `CertificateServiceCorrectionTest` 用纳秒内存时间与截断持久化时间证明响应使用数据库 revision；Phase 9 串联所有写接口并与详情 revision 对照 |
| Medium：V35 将历史 `>10` 人在途评审迁移成不可完成状态 | V35 按历史有效任务数 `>=2` 冻结，不把新建上限 10 反套到旧记录；无任务时才读取旧参数。新建评审仍限制 2..10。`assign()` 完成任务同步后复用现有 `settleIfReady()`，全部提交时立即结算并记录状态审计 | `VideoReviewServiceAssignmentTest` 覆盖 11 人/3 人已提交后补齐 8 人，以及全部已提交时指派即结算；V35 IT 和 Phase 7 IT 覆盖升级、保留成绩及最终完成 |

上述“本地整改实现”是提交方状态，不等于独立复核的 `CLOSED` 裁定。

## 4. 本地门禁

- 证书/视频聚焦单元：**15/15**，0 failure / 0 error / 0 skip。
- 后端九模块 `mvn -B -ntp -o test`：**68 suites / 447 tests**，0 failure / 0 error / 0 skip；Checkstyle 0。
- 后端九模块 `mvn -B -ntp -o -DskipTests package`：PASS；启动模块 82 个测试源编译成功。
- 候选 manifest 工具：**6/6**；WS-7 合同、WS-8/V33 合同与 `git diff --check`：PASS。
- 本轮没有前端字节变更，因此未把旧候选的前端运行结果冒充为 R4 新证据。

## 5. 仍需独立执行的增量证据

- Phase 7、Phase 9 与 V35 新增真实依赖场景已通过编译，但当前机器只运行共享 `*-prod` 依赖，提交方未连接或写入，也未启动另一套容器。
- 独立复核应在一次性隔离 MySQL/Redis/MinIO 环境执行：证书各写响应 revision 与详情一致、V34 形成 11 人/3 人已提交后升级 V35、补齐剩余评审并最终结算；保存机器可读报告并绑定新 manifest。
- 本次独立增量复核只需裁定上述两项 Medium；部署、供应链、可选 WS-15 与项目 GO 仍是独立边界。

## 6. 新候选身份

- 外置 manifest：
  `C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff96a-c185-7730-852e-f3007f3b2bb7\teacher-cert-final-functional-completeness-remediation-r4-candidate-2026-08-22.json`。
- 为避免治理文档写回导致自指纹漂移，本文件不内嵌新 fingerprint；所有仓库字节稳定后生成并立即 verify 的外置 manifest 是本次复核对象的唯一身份依据。
- manifest 生成后不得再修改候选字节；如有任何仓库变化，必须重新 capture 并以新 fingerprint 复核。
