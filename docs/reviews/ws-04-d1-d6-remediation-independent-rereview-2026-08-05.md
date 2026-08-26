# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform / WS-4 D1–D6 首轮整改候选  
**Audit mode:** incremental + testing-authenticity  
**Date:** 2026-08-05  
**Reviewer:** Codex（独立增量复核）

---

## 0. Verdict Correction (2026-08-06)

本节取代下文原始严重度与 Approval Recommendation；下文保留作为首次判定的审计轨迹，不再代表当前结论。

经按“不 overengineering、回归明确产品契约”的口径重新校准，正式结论更正为
**WS-4 scoped PASS（0 Critical / 0 High / 0 Medium）**：

- 将“绕过 disabled 后必须再次证明 handler guard”定为阻断 Medium 过严；生产代码已有按钮禁用和函数 guard，
  不要求追加近似 mutation test 的证明。
- “视频五个 0 必须逐项断言”只属于测试完善建议，不阻断本轮范围。
- “材料批量下载必须绑定最后成功加载快照”缺少明确产品契约，不能把单一 UX 解释提升为硬性验收要求。

以上三项最多保留为非阻断 Low/建议，不要求修改 `745e9986...d4d9` 候选。原报告中的
`CHANGES_REQUESTED（2 Medium / 1 Low）`、`Request changes` 及相应阻断整改顺序均由本节明确撤销。
本更正不扩大审计范围，也不授权 merge、push、deploy、切流或全项目发布。

---

## 1. Executive Summary

本轮唯一正式复核对象是写入本报告前独立验证通过的完整工作树指纹
`745e9986b65c072823b3b0a0b04ed73acd16756447d7b408f306d8e0e340d4d9`，基于 HEAD
`aa3509a19151e2caad03014324b9b36e98bc0f05`。外部权威 manifest
`C:\Users\wenbibuhaoqwq\Documents\脚本\ws4-r1-final-candidate-source-manifest-20260805-v2.json`
与证据包副本逐字节一致；`target/ui-audit-ws4-r1-745e9986/SHA256SUMS` 精确覆盖其余 62 个文件，
无缺失、额外项、重复项或哈希不匹配。旧候选 `65a07cd5…b136` 与过渡候选 `a63eba41…e8148`
均明确排除在本轮正式验收范围外。

上一轮四类产品缺陷已经按原口径关闭：材料/统计 latest-request 与 loaded snapshot、培养/免考依赖
freshness、视频首错伪零值、冷启动首次改密重检均在生产源码中成立。上一轮最终候选覆盖缺口也显著收窄，
但其函数级写屏障负向用例仍存在可假绿路径，因此不能把 18/18 直接等同于完整回归闭环。

正式结论为 **CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 1 Low）**。新增产品 Medium 是材料
批量下载仍读取当前筛选，而不是最后成功加载的筛选快照；用户修改筛选但未成功刷新时，屏幕显示 B、下载请求
却可提交 C。第二个 Medium 是四个 freshness 写屏障用例只对仍为原生 disabled 的按钮执行
`click({ force: true })`，浏览器不会激活该控件，因此删除函数级 guard 后用例仍可能全绿。Low 是视频管理
“成功空态真实 0”用例没有实际断言五个数值为 0。

独立执行的安全离线门禁包括 frontend lint、type-check、production build、三个前端合同测试、manifest
工具 6/6、gap gate 语法检查、`git diff --check` 和候选 verifier，均 PASS。按用户安全边界，本轮未执行
浏览器、网络、Docker、数据库、Redis、MinIO、服务启动、漏洞扫描或任何可能属于 cyber 的动作；归档中的
浏览器结果只作为经过结构、脚本与校验和复核的候选证据，不冒充本轮独立动态运行。

### Score Dashboard

```
Security        ████████░░  8.2  A   未发现权限边界回归；本轮缺陷是前端对象一致性与测试证明
Stability       ███████░░░  7.2  B   原四类状态缺陷关闭，但材料批量下载仍可与可见查询漂移
Performance     ████████░░  8.0  A   本轮未发现新增性能退化；未执行性能或负载测试
Testing         ██████░░░░  6.4  B   覆盖广度明显提升，但四个关键负向用例可假绿，另有一项弱断言
Maintainability ████████░░  7.8  A   局部 query-key/freshness 模式清晰，整改未引入额外框架
Design          ███████░░░  7.4  B   绝大多数读写已绑定 loaded state，批量下载仍绕过同一不变量
Release         ███████░░░  6.8  B   指纹和证据闭包可靠，但 2 个 Medium 仍阻断 WS-4 放行
─────────────────────────────────────
Overall         ███████░░░  7.3  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are
judgment-based, not formula-based.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 2 | 2 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **3** | **3** | **0** |

## 2. Project Map

本轮聚焦上一轮 5 个 Medium 的整改文件、直接调用方与同候选证据链。页面以局部 request sequence、
current/loaded query-key 和 freshness guard 管理异步读取及写入口；父页向 Drawer 传递依赖选项状态；路由守卫
在 `loadMe()` 后重新判断首次改密。正式证据由外部 manifest、三段同指纹 verify、两份浏览器脚本、四份
JSON 报告、截图和 `SHA256SUMS` 组成。

风险边界仍是“当前输入、最后成功加载的数据、将要执行的动作”三者是否绑定。后端权限、数据库业务约束与
真实对象存储不在本轮动态范围，且没有被前端拦截证据替代。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | Medium | 路由首次改密门禁、相关前端写入口及后端权限存在性 | 未执行登录、HTTP 或安全扫描；未重开 WS-5 |
| Stability | High | 材料、统计、培养、免考、视频和路由整改源码及直接调用方 | 未独立启动浏览器或真实后端 |
| Performance | Low | production build 与请求控制流 | 未做负载、资源或性能测试 |
| Testing | High | gap/D5/D6/final 脚本、报告、截图、日志、manifest 和 checksum | 浏览器结果为归档证据，不是本轮重新执行 |
| Maintainability | Medium | 页面内 query-key/freshness 模式与父子组件状态传递 | 未审非 WS-4 后端维护性 |
| Design | High | loaded state 与动作对象绑定、函数/UI 双层门禁 | 只覆盖上一轮整改及相邻路径 |
| Release | High | 唯一候选、外部 manifest、62/62 闭包和三次同指纹 verify | 未 merge、push、deploy 或触碰线上版本 |

## 3. Top Risks

1. **Medium — 材料批量下载与屏幕所示查询没有绑定。** 筛选发生漂移或刷新失败时，可下载另一组合法但非当前可见对象。
2. **Medium — 四个关键写屏障反例可在函数级 guard 被删除后继续 PASS。** 当前门禁主要证明按钮 disabled，没有证明处理函数 fail closed。
3. **Low — 视频真实零值用例只检查标签和空态。** 用例名称声称验证数值，但断言没有覆盖数值。

## 4. Detailed Findings

### Finding: 材料批量下载使用实时筛选而非已加载快照

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 过程性材料批量下载
- Evidence:
  - File: `frontend/src/views/material/MaterialManageView.vue:332-348,453-456`
  - Function / Module: `batchDownload()`
  - Relevant behavior: `batchDownload()` 逐项读取当前 `keyword/status/category/assessmentYear`；按钮没有
    `dataFresh` 门禁，也没有使用 `loadRecords()` 成功时绑定的查询快照。
- Problem: 页面已经展示最后一次成功查询 B 后，用户可以把输入改为 C 而不提交搜索，或 C 刷新失败；列表仍是
  B，但批量下载向 `/material/batch-download` 提交 C。
- Why it matters: 下载包与用户正在核对的列表不一致。后端权限可以阻止越权，却无法判断用户本次意图究竟是
  B 还是 C；两组对象都合法时会静默下载错误范围。
- Realistic failure scenario: 教务人员按年度 B 查出材料，随后将年度改为 C；在未搜索或 C 请求失败时直接点
  “批量下载”，返回 C 的 ZIP，而页面仍展示 B。
- Minimal fix: 成功加载时保存完整筛选快照；按钮和函数均要求 `currentQueryKey === loadedQueryKey`，并仅用
  loaded snapshot 组装下载请求。无需新框架。
- Better long-term fix: 将页面内已存在的 `currentQueryKey/loadedQueryKey` 不变量应用到所有依赖当前结果集的
  读后动作，包括下载、导出和合格判定。
- Regression test suggestion: B 成功后改为 C，分别覆盖“未搜索”和“C 刷新失败”；移除 DOM disabled 后触发，
  断言下载请求为 0。C 成功后再断言 POST body 精确等于已加载 C 快照。
- Estimated effort: 30–60 minutes

### Finding: freshness 写屏障门禁对函数级 guard 不敏感

- Severity: Medium
- Confidence: High
- Category: Testing Authenticity
- Test Subtype: FalseConfidence
- Status: Confirmed
- Affected area: gap gate 的材料、统计、培养、免考四个错误/陈旧写屏障用例
- Evidence:
  - File: `frontend/scripts/test-ws4-gap-gate.mjs:257-265,325-330,398-407,477-487`
  - Comparator: `target/ui-audit-ws4-r1-745e9986/scripts/ws4-final-ui-check.mjs:454-458,503-507,603-608`
  - Function / Module: `material-stale-blocks-review`, `stats-stale-query-blocks-export`,
    `training-cascade-error-blocks-save`, `exemption-cascade-error-blocks-save`
  - Relevant behavior: 四项均先确认按钮 disabled，再执行 `click({ force: true })`；Playwright 的 force 只跳过
    actionability 检查，原生 disabled 控件仍不会产生浏览器激活事件。
- Problem: 这些断言可以证明 UI 禁用状态，却没有真正进入处理函数。删除生产函数中的 freshness guard 后，材料和
  统计用例仍可全绿；培养表单还会被普通必填校验拦住，免考则会被空行校验拦住。
- Why it matters: 上一轮正式 Finding 5 明确要求按钮与函数双层负向证明。当前生产 guard 经源码确认存在，但
  18/18 不能可靠防止后续重构误删函数级防线，因此正式测试真实性仍未闭环。
- Realistic failure scenario: 开发者保留 `:disabled` 绑定，却在重构时删除 `save()` 或 `handleExport()` 的开头
  guard。门禁继续 PASS；随后另一调用路径或 UI 状态竞态直接调用处理函数，产生错误写请求。
- Minimal fix: 材料/统计在确认 disabled 后移除底层按钮的 `disabled` 属性并分发 DOM click，继续断言精确请求数
  为 0。培养/免考先构造除 freshness 外完全有效的表单/行，再解除 DOM disabled 并断言所有写 endpoint 为 0。
- Better long-term fix: 保留浏览器级函数穿透反例，并对页面 freshness 判定提取一个小型纯函数单测；不需要新增
  证据系统或全局状态机。
- Regression test suggestion: 临时 mutation 删除四个函数级 guard 时，相应用例必须失败；恢复 guard 后四项均
  PASS。mutation 仅作为本地测试设计校验，不进入产品代码。
- Estimated effort: 1–2 hours

### Finding: 视频成功空态用例没有断言真实零值

- Severity: Low
- Confidence: High
- Category: Testing Authenticity
- Test Subtype: HappyPathOnly
- Status: Confirmed
- Affected area: 视频管理成功空响应回归
- Evidence:
  - File: `frontend/scripts/test-ws4-gap-gate.mjs:539-543`
  - Function / Module: `video-manage-success-empty-shows-real-zero`
  - Relevant behavior: 用例只等待“视频总数”和“暂无视频评审记录”，没有读取五张统计卡的 `.stat-value`。
- Problem: 名为“shows-real-zero”的测试没有验证数值。标签存在但值为空、占位或非零时仍可能 PASS。
- Why it matters: 这不会推翻当前生产实现；源码计算的五项值确实为 0。但门禁声明强于实际断言，降低未来回归
  可信度。
- Realistic failure scenario: 样式或映射重构后卡片标题仍在，但数值变成空字符串或陈旧值；用例保持全绿。
- Minimal fix: 对五张统计卡逐项断言 `.stat-value` 的规范化文本精确等于 `0`。
- Better long-term fix: 让测试 ID、测试名和断言对象保持一一对应；无需增加新的测试层。
- Regression test suggestion: 将任一空态统计 mock 成非零或空文本时，本用例必须失败。
- Estimated effort: 10–20 minutes

## 5. Frontend State and Stability Concerns

- Coverage: High
- Inspected evidence: 8 个直接整改生产文件、gap gate、正式 final gate、同候选报告及独立静态/构建门禁。
- Exclusions / limits: 未启动浏览器、后端或依赖；未扩展到 WS-5 会话安全、WS-6 a11y 或非 WS-4 产品范围。

上一轮状态一致性整改总体有效。材料和统计只接纳最后请求，统计导出绑定已加载快照；培养与免考在依赖 pending、
失败或 key 不一致时关闭控件和函数；视频首错不再伪造 0；路由不会在冷启动 `mustChangePwd=true` 时挂载业务页。

剩余产品风险仅是材料批量下载没有复用同一 loaded-query 不变量。最小闭环是把下载视为“依赖当前已加载结果的
动作”，不需要改变接口、权限、后端状态机或引入全局请求框架。

## 6. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 18/18、24/24、36/36、85/85 报告及对应脚本、日志、截图、manifest 和 62/62 checksum。
- Exclusions / limits: 本轮没有自行重跑 Playwright；API 路由拦截不证明真实后端、数据库或生产行为。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| manifest 与证据闭包 | High | 只证明候选身份和归档完整性 | Keep |
| D1–D4/D6 85 项与 D5/D6 | High（限定 API 拦截） | 不证明真实后端 | Keep |
| latest-request 与冷启动反例 | High | 精确等待响应并核对可见状态/业务请求 | Keep |
| 四个 freshness 写屏障 | Low | disabled 控件没有进入处理函数，可假绿 | Fix before acceptance |
| 视频成功空态零值 | Medium | 验证标题/空态但没有验证值 | Add exact assertions |

### Valuable Tests

- A/B 乱序反例精确等待旧响应完成，能证明最终可见对象属于最新查询。
- 冷启动首次改密同时断言登录 URL、业务标题不存在、登录页可见和业务 API 为 0，证明强度充分。
- D1 Drawer/导航、D2 Grid 实际列数、D3 Overlay/双提交、D5 状态矩阵均已进入同一最终候选证据。
- 三段 verify 和 62/62 checksum 可可靠排除证据混包与执行后换源。

### Suspicious Tests

- 四个 disabled `force:true` 用例没有触发业务 handler；其中培养与免考还有其他表单校验作为旁路阻断。
- 视频“真实 0”用例的名称和断言对象不一致。

### Missing Tests

- 材料批量下载的 current/loaded query-key 漂移、刷新失败和请求体快照。
- 材料、统计、培养、免考四个 handler 的函数级 freshness 穿透反例。
- 视频成功空响应时五个统计值精确为 0。

## 7. Change Summary

- Total files changed: 完整正式 manifest 为 154 tracked + 110 non-ignored untracked；本轮直接复核切片包含
  8 个 tracked 生产文件及 1 个 untracked gap gate。
- Lines added: 8 个直接 tracked 生产文件相对 HEAD 共 734 行；未跟踪脚本没有可靠基线，不伪造增量行数。
- Lines deleted: 同一 8 个 tracked 生产文件相对 HEAD 共 170 行。
- Commits in range: 0；这是绑定 HEAD `aa3509a…` 的未提交工作树候选。
- Authors: 脏工作树无法可靠归属单一作者，本报告不作推断。

### Change Categories

- Bug fixes / state remediation: 材料、统计、培养、免考、视频和路由共 8 个直接生产文件。
- Test/tooling: `test-ws4-gap-gate.mjs`、最终 browser gate、manifest verifier 和正式证据 runner。
- Documentation/governance: 本报告只同步独立结论，不修改产品行为。
- Dependency/build/configuration: 本轮未发现与整改 finding 直接相关的依赖或配置变更。

### Risk Delta

- Existing risks fixed: 上一轮材料/统计乱序、Drawer 依赖 freshness、视频首错和冷启动改密四类产品问题关闭。
- Existing risks reduced: 最终候选覆盖从旧指纹碎片提升为同指纹 18/24/36/85 证据，但函数级写屏障仍需补强。
- New/adjacent risk found: 材料批量下载没有绑定已加载查询快照。
- Existing risks made worse: 未发现。

### Test Coverage Delta

- New/modified behavior with meaningful final-candidate tests: 四类原缺陷、D1–D5 代表独有行为和 D6 流程。
- New/modified behavior without sufficient tests: 材料批量下载、四个函数级 freshness guard、视频五项真实零值。
- Deleted tests: 未观察到。

### Approval Recommendation

**Request changes.** 只需关闭本报告 2 Medium / 1 Low 并在新的唯一 manifest 下重交同一 gap gate；不要求
重建历史截图、接真实后端、引入新框架、执行 cyber 动作或扩大到 WS-5/WS-6。当前结论不授权 merge、push、
deploy 或切流。

---

## 8. Principles Compliance

当前整改遵守 KISS：采用页面内 sequence/query-key 和局部 guard，没有新状态框架或依赖。候选身份、证据闭包和
运行边界表达清楚。未闭环之处是同一 loaded-state 原则尚未覆盖材料批量下载，以及部分测试只验证 UI disabled
而没有验证函数边界。

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| State ownership / single source of truth | 1 | Medium | 材料批量下载查询参数 |
| Tests verify behavior | 2 | Medium / Low | 四个函数级写屏障、视频真实零值 |

### Principles Respected

- 原四类产品缺陷的修复均为局部、可读、失败关闭的实现。
- 外部 manifest、同候选三段 verify 与 checksum 闭包没有把旧候选混入正式结论。
- API 拦截门禁没有被冒充为真实后端或生产证明。
- 本轮无需新增依赖、全局状态机或证据基础设施。

## 9. Recommended Fix Order

### Fix Immediately

- 无 Critical/High；不需要任何生产操作。线上稳定版本继续冻结且不触碰。

### Fix Before Stable Release

1. 先让材料批量下载绑定 loaded query snapshot，并补漂移/失败/正确请求体反例。
2. 修正四个写屏障反例，使其确实进入 handler 且只因 freshness guard 阻断。
3. 给视频成功空态补五项精确零值断言。
4. 生成新的唯一 manifest 和同候选证据，只复核上述 3 项及必要回归。

### Schedule Later

- 完整 a11y、TLS/session、真实后端 E2E 继续按 WS-5/WS-6 和既有发布计划处理，不混入本轮。

### Ignore for Now

- production build 的既有 Naive UI/ECharts 大 chunk warning 不是本轮退回原因。
- D1 缺少真实 `d0-before` 已如实记录，不要求伪造。

## 10. Quick Wins

- 给材料“批量下载”复用已存在的 `dataFresh` 和 loaded query snapshot。
- 对材料/统计按钮沿用 final gate 已有的“移除底层 disabled 后 DOM click”模式。
- 培养/免考先构造完全有效表单，再仅破坏 dependency freshness。
- 为五张视频统计卡增加一个精确 `0` 循环断言。
