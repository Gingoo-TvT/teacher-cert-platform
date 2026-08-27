# WS-9 真实依赖回归整改提交材料（2026-08-20）

> 本材料是整改者提交证据，不是独立复核报告，不自行签发 WS-9 PASS。

## 1. 正式输入

- 正式报告：`C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff958-9b0d-72f1-b15b-2487f4fd3e2b\ws09-functional-independent-review-20260820-6fd8e17b-local\review.md`
- 报告 SHA-256：`0553a3963eccbffaecbc1f55b0db220ac9ef60244f8b2ca9df2aa5a5e64b2ea1`
- 正式结论：`CHANGES_REQUESTED（0 个生产代码缺陷 / 1 个 Medium）`
- 唯一 finding：WS-9 重构后的 Phase 7/10/14 真实依赖回归未形成；因回滚锁逻辑搬移，同批补 Phase 39。

## 2. 最小整改

- 不修改生产代码、API、数据库、事务、锁序、CAS、审计或业务规则。
- 移除 `Phase7VideoReviewIT` 原类级 1 秒/0.2 秒测试租约，只在专门的续租/接管用例内设置 5 秒/1 秒，并在用例结束恢复原配置。
- 普通功能用例使用生产默认 2 分钟/30 秒租约；专门用例仍跨过 5 秒 TTL，验证续租、接管、旧 owner 拒绝与 ABA 边界。

## 3. 同一轮本地证据

- 全新 `clean verify`：BUILD SUCCESS。
- Surefire：400/400，0 failure/error/skip。
- `Phase7VideoReviewIT`：44/44。
- `Phase10ExchangeIT`：18/18。
- `Phase14E2EIT`：14/14。
- `Phase39CollegeIntegrityIT`：11/11。
- 四套 Failsafe 合计：87/87，0 failure/error/skip。
- Checkstyle：0。

XML 与摘要计划归档到：
`C:\Users\wenbibuhaoqwq\.codex\visualizations\2026\08\13\019ff96a-c185-7730-852e-f3007f3b2bb7\ws09-integration-remediation-evidence-20260820-local`。

## 4. 候选与复核边界

治理与测试字节改变后，旧 fingerprint `6fd8e17b...9bc23` 及其 manifest 不得复用。完成治理同步后冻结新 manifest，
再由用户对唯一 Medium 的整改与上述回归证据做独立增量复核。

当前状态为 `LOCAL_INTEGRATION_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REVIEW_PENDING`。独立 PASS 前：

- 不把 WS-9 标为 PASS；
- 不领取 WS-11；
- 不 merge、deploy 或 cutover；
- 项目保持 `CHANGES_REQUESTED / NO-GO`。
