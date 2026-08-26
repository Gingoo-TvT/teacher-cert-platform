# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform / WS-4 D1–D6 统一候选  
**Audit mode:** incremental + testing-authenticity  
**Date:** 2026-08-05  
**Reviewer:** Codex（独立复核）

---

## 1. Executive Summary

本轮对 WS-4 / D1–D6 统一未提交候选进行了独立增量复核。正式被复核对象是写入本报告前再次验证通过的完整工作树指纹 `65a07cd566da1e5fb9a32243dee6d1a1db1c9517504b9a181b10cf01c461b136`，基于 HEAD `aa3509a19151e2caad03014324b9b36e98bc0f05`；候选包含 153 个 tracked 变更文件与 106 个 non-ignored untracked 文件。正式证据包 `target/ui-audit-ws4-final-65a07cd5` 的 43/43 个校验项均独立复算匹配，manifest、三次 verify、36/36 D6 报告和 85/85 统一增量报告在证据真实性与候选绑定层面成立。

结论仍为 **CHANGES_REQUESTED（0 Critical / 0 High / 5 Medium / 0 Low）**。当前候选保留了四类真实产品状态缺口：材料/统计请求乱序、培养/免考联动选项陈旧提交、视频首次失败伪零值，以及冷启动时首次改密状态在 `loadMe()` 后未重新进入路由门禁。第五个 Medium 是测试真实性缺口：本轮同指纹 75 个“响应式”检查只验证标题和页面级横向溢出，D1–D5 的独有 Drawer、Grid、Overlay、导航和完整错误状态反例仍绑定旧指纹；它们不能证明最终候选。

本轮独立执行了本地、自然退出的 lint、type-check、production build、两个前端合同测试、manifest 工具 6/6、`git diff --check`、候选 manifest 复算与证据包 SHA-256 闭包检查，均 PASS。按用户安全边界，未执行浏览器、网络、Docker、数据库、Redis、MinIO、服务启动、漏洞扫描或任何可能属于 cyber 的动作。因此整改方的 36/36 与 85/85 可作为真实的本地 API 拦截证据，但不是本轮独立动态执行结果，也不构成真实后端或生产证明。

### Score Dashboard

```
Security        ████████░░  8.0  A   后端仍强制首次改密；前端冷启动路由状态有缺口，安全覆盖为中等
Stability       ██████░░░░  5.8  B   四类页面状态一致性问题可由正常快速切换或失败路径触发
Performance     ████████░░  8.0  A   本轮未发现新增性能退化，但仅做增量源码与构建检查
Testing         ██████░░░░  5.5  B   已有负向门禁有价值，但最终候选未覆盖多个已修改关键状态面
Maintainability ███████░░░  6.8  B   局部 query-key 模式清晰，但未一致铺到全部同类组件
Design          ██████░░░░  6.2  B   latest-request/freshness 不变量在页面与 Drawer 边界执行不一致
Release         █████░░░░░  5.4  B   指纹和校验和闭合良好，但 5 个 Medium 阻断 WS-4 批量放行
─────────────────────────────────────
Overall         ███████░░░  6.5  B
```

Each dimension scored 0.0–10.0. **Higher = better (10 = clean, 0 = shit mountain).** Scores are judgment-based, not formula-based. See `rubrics/scoring.md` for anchor descriptions.

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 5 | 5 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **5** | **5** | **0** |

## 2. Project Map

本轮只审 WS-4 相关增量及其直接边界。路由入口由 `frontend/src/router/index.ts` 管理；会话事实由 `frontend/src/stores/user.ts` 持有；页面分别管理筛选条件、列表或报表、请求状态与写入口；Drawer/Modal 承担依赖联动与提交；`DataPanel`、`FilterBar`、`ReviewDialog` 等共享组件提供统一状态外观。证据链由 `scripts/candidate_source_manifest.py`、当前证据包中的两个 Playwright 脚本、JSON 报告和 `SHA256SUMS` 组成。

风险最集中的边界是“当前表单查询 → 最后一次成功响应 → 当前可写对象”之间的绑定，以及父页面数据 freshness 是否被传到子 Drawer。后端 API、数据库状态机、真实对象存储和生产部署不在本轮动态范围；本轮也没有把源码检查冒充这些运行证明。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | Medium | 路由守卫、用户 store、首次改密前端链及服务端现有强制门源码 | 未执行登录或真实 HTTP；未重开 WS-5 安全范围 |
| Stability | High | 80 个 frontend/src 变更文件中的 WS-4 状态面，重点逐行核对列表、Drawer、视频、统计和材料流程 | 未执行真实浏览器；动态触发以源码控制流和归档报告交叉证明 |
| Performance | Low | production build 与相关请求控制流 | 未做性能、负载或资源测试；未据此给出性能清洁结论 |
| Testing | High | 当前 36/36、85/85 脚本/报告，旧 D1–D5 报告、manifest 与测试断言 | 本轮未亲自启动浏览器；旧证据仅作历史覆盖参考 |
| Maintainability | Medium | 共享状态组件、各页面 query-key/request-sequence 模式及直接调用方 | 未审非 WS-4 后端维护性 |
| Design | Medium | 页面状态所有权、父子组件 freshness 传递、fail-closed 写入口 | 未做全仓架构审计 |
| Release | High | manifest 外部 verify、43/43 哈希闭包、证据时间线、当前 Git 状态 | 未 merge、push、deploy；未验证生产或真实后端 |

## 3. Top Risks

1. **Medium — 材料与统计结果没有绑定最后一次查询。** 迟到响应可以覆盖新年度/新统计类型，并让旧材料行重新进入可写状态或让导出与屏幕内容不一致。
2. **Medium — 培养与免考 Drawer 不知道依赖选项是否属于当前选择。** 旧目标/旧学段选项在 pending、失败或乱序时仍可进入保存流程。
3. **Medium — 视频首次加载失败仍展示伪零值和伪占位。** 错误提示旁同时显示“待评分 0”“已提交 0”、五项 0 统计或“请选择左侧任务”。
4. **Medium — 冷启动 `loadMe()` 后没有重检首次改密状态。** 受保护页面可以先挂载；服务端会拒绝业务 API，但前端门禁和产品流程不成立。
5. **Medium — 最终候选门禁没有覆盖 D1–D5 的独有行为。** 75 个响应式检查无法替代 Drawer/Grid/Overlay/导航/错误状态反例，且旧证据指纹不同。

## 4. Detailed Findings

### Finding: 材料与统计页缺少 last-request/query-key 绑定

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 过程性材料、统计报表
- Evidence:
  - File: `frontend/src/views/material/MaterialManageView.vue:80,172-191,240-265`
  - File: `frontend/src/views/stats/StatsReportView.vue:106-129`
  - Function / Module: `loadRecords()`, `loadReport()`, `handleExport()`
  - Relevant behavior: 两个加载函数都直接提交响应，没有 request sequence 或 loaded query-key；材料 `dataFresh` 只检查全局成功/错误/loading，统计导出使用当前表单而不是已展示报表的查询快照。
- Problem: 同一页面快速切换年度、分页、筛选或统计类型会形成并行请求。较旧请求最后返回时，会覆盖较新结果。材料页随后把旧行视为 fresh；统计页会显示 A 查询结果，却以 B 查询参数导出。
- Why it matters: 用户可能对错误年度的材料执行提交、审核、替换或删除，或下载与屏幕汇总不一致的报表。后端权限校验不能纠正用户选中了错误但仍合法的业务对象。
- Realistic failure scenario: 用户先查询年度 A，立即切到年度 B；B 先返回并显示，A 后返回覆盖。材料页 `loadError=''`、`loading=false`、`hasLoadedSuccessfully=true`，按钮重新启用；统计页标题/表格来自 A，而导出请求读取当前 B。
- Minimal fix: 为两个页面增加页面内 request sequence 和序列化 query-key；仅最后请求可提交结果；写入口和导出必须要求 `loadedQueryKey === currentQueryKey`。材料的 `submit/remove/openReview/saveReview` 也应做函数级 freshness 守卫。
- Better long-term fix: 复用当前候选已在学生、证书和视频页面使用的局部 query-key helper；无需引入全局请求框架。
- Regression test suggestion: 延迟 A、启动 B，先返回 B 再返回 A；断言最终只显示 B，材料强制点击写入口时写 API 为 0，统计导出参数与已展示 query-key 完全一致。
- Estimated effort: 2–4 hours

### Finding: 培养与免考 Drawer 未绑定依赖选项 freshness

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 培养信息编辑、免考申请
- Evidence:
  - File: `frontend/src/views/training/components/TrainingDrawer.vue:99-150,176-195`
  - File: `frontend/src/views/exemption/ExemptionManageView.vue:249-262,452-462`
  - File: `frontend/src/views/exemption/components/ExemptionDrawer.vue:74-112,164-186`
  - Function / Module: `reloadTrainingOptions()`, `save()`, `loadSubjects()`, `saveApply()`
  - Relevant behavior: 培养目标联动没有 sequence/query-key/loading/error/freshness；免考父页有 sequence，却没有把 subject loaded-key/loading/error 传给 Drawer，Drawer 切学段后仍可使用旧 subjects 并保存。
- Problem: 依赖列表与当前培养目标或学段没有形成可验证的一致快照。培养的旧响应可覆盖新目标允许的学段/地点；免考 Drawer 在新科目请求 pending 或失败时仍开放“添加科目”和“保存”。
- Why it matters: 前端会构造与当前业务选择不匹配的 payload。服务端硬校验可能拒绝它，但用户会在填写并上传佐证后才收到失败，且当前 UI 所显示的可选值本身不可信。
- Realistic failure scenario: 用户快速从目标 A 切到 B，B 响应先回、A 后回，A 的 allowed values 覆盖 B；或在免考 Drawer 把学段从小学切到中学后，趁新请求未完成选择旧小学科目并保存。
- Minimal fix: 培养联动绑定目标/学段 query-key；免考将 subjects loading/error/loaded-segment 传入 Drawer。当前依赖未成功时禁用相关控件，并在 `save()`/`saveApply()` 内再次 fail closed。
- Better long-term fix: 将“依赖值 + loaded key + loading/error”封装成小型只读状态对象在父子组件间传递，不需要新状态机或后端改造。
- Regression test suggestion: 对培养延迟 A 后快速切 B，断言 A 不得覆盖；对免考在新学段 pending/失败时强制点击保存，断言 apply/upload API 均为 0，恢复成功后才开放。
- Estimated effort: 3–5 hours

### Finding: 视频首载失败仍呈现伪零值和伪空占位

- Severity: Medium
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 评审教师任务、视频管理统计
- Evidence:
  - File: `frontend/src/views/video/components/MyTaskPanel.vue:239-268,311-328`
  - File: `frontend/src/views/video/components/ManagePanel.vue:307-315`
  - File: `target/ui-audit-ws4-final-65a07cd5/ws4-final-ui-check.mjs:464-491,546-620`
  - Function / Module: 首次加载错误渲染、`videoTaskFeedbackAndDoubleSubmit()`, `videoManageOrderingAndStaleGuard()`
  - Relevant behavior: 任务页错误时仍渲染“待评分 0”“已提交 0”和“请选择左侧任务”；管理页五个统计卡始终渲染。门禁只排除了“暂无评审任务”，且管理页从成功态开始。
- Problem: D5 明确要求首次失败只显示错误/重试，不得把未知数据表示成 0 或真实空态；当前两个视频面板仍违反该状态语义。
- Why it matters: 用户会把依赖故障误判为当前确实无任务、无视频或无待办，影响评审与运维判断。
- Realistic failure scenario: `/video/tasks/my` 或 `/video/reviews` 首次返回 503；页面显示错误提示，同时仍显示 0 统计或要求选择一个实际未加载的任务。
- Minimal fix: 统计和右侧占位仅在对应列表至少成功加载一次后渲染；刷新失败时可以保留上次成功统计并明确标注陈旧。
- Better long-term fix: 继续沿用 `hasLoadedSuccessfully + stale` 的现有局部模式，不扩建全局 UI 状态系统。
- Regression test suggestion: 两个接口分别首次 503，断言错误/重试可见且所有统计标签与任务占位不可见；成功空响应后才允许显示真实 0/空态。
- Estimated effort: 1–2 hours

### Finding: 冷启动身份恢复后未重新执行首次改密路由门禁

- Severity: Medium
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: 前端路由与首次改密流程
- Evidence:
  - File: `frontend/src/router/index.ts:157-190`
  - File: `frontend/src/stores/user.ts:20-21,38-60`
  - Function / Module: `router.beforeEach()`, `loadMe()`
  - Relevant behavior: 守卫在 `loadMe()` 前检查 `mustChangePwd`，但身份加载把该值更新为 true 后不再检查，继续做权限判断并返回 true。
- Problem: token 存在而本地 `mustChangePwd` 缺失/陈旧为 false 时，冷启动先通过检查；`/auth/me` 返回 true 后目标路由仍可挂载。
- Why it matters: 服务端 `JwtAuthenticationFilter` 仍会阻断除改密外的 API，因此这不是后端授权绕过；但前端强制改密产品门禁失效，用户会进入一个业务请求持续 403 的错误页面，而不是改密流程。
- Realistic failure scenario: 管理员重置口令后，浏览器仍持旧的 localStorage false 与有效 token；刷新受保护页面，`loadMe()` 得到 true，页面挂载并发起随后被后端拒绝的请求。
- Minimal fix: `loadMe()` 成功后立即再次判断 `userStore.mustChangePwd`，命中时转回登录/改密入口，不挂载目标组件。
- Better long-term fix: 将“token、initialized、mustChangePwd、权限”收敛为一次身份恢复后的单一路由判定函数，并保持现有服务端强制门不变。
- Regression test suggestion: 冷启动 token + persisted false，mock `/auth/me.mustChangePwd=true`；断言目标组件不挂载、进入改密路径，且业务 API 不发出。
- Estimated effort: 30–60 minutes

### Finding: 最终同指纹门禁未覆盖 D1–D5 独有行为

- Severity: Medium
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: WS-4 批量验收证据
- Evidence:
  - File: `target/ui-audit-ws4-final-65a07cd5/README.md:16-25`
  - File: `target/ui-audit-ws4-final-65a07cd5/ws4-final-ui-check.mjs:44-60,249-255,316-329,612-638`
  - Function / Module: `responsiveRegression()` 与最终 gate 调度
  - Relevant behavior: 75 项只等待 H1 并检查 document 级横向溢出；10 项只覆盖学生、证书和视频聚焦状态。D1–D5 旧报告分别绑定 `f24d8114…`、`c3aa4933…`、`5d1c0f0d…`、`7b7d48a9…`、`509b85d9…`，均不是当前 `65a07cd5…`。
- Problem: 当前 36/36 D6 与 85/85 增量门禁是真实执行结果，但不能推出 D1–D6 最终候选整体成立。旧阶段以后又修改了共享组件和相同页面；历史 PASS 不能替代最终候选上的独有行为回归。
- Why it matters: 本轮四个产品 Medium 正是现有绿灯没有捕获的反例，证明覆盖缺口具有实际后果而非文档洁癖。
- Realistic failure scenario: 标题存在且页面没有 document overflow，因此 75/75 全绿；与此同时局部 Grid、Drawer footer、状态统计或联动请求已经错误，门禁仍无法失败。
- Minimal fix: 不必重跑所有历史截图矩阵；在新的最终 manifest 下补一个 gap gate，复用 D1–D5 的独有断言，并加入本报告四类反例。至少覆盖 Drawer/导航、Grid 实际列数、代表 Overlay 与防重、冷启动改密、D5 首错/陈旧/真空态以及材料/统计/联动乱序。
- Better long-term fix: 将阶段脚本的独有检查组合成可选择的常驻 Playwright 套件，由一次最终候选运行输出一个报告；不需要新增证据基础设施。
- Regression test suggestion: 在同一候选下按“旧独有断言 + 本轮四类反例”执行，前后 verify 同一 manifest，报告逐 testcase 计数且失败时 exit 1。
- Estimated effort: 0.5–1 day

## 5. Frontend State and Stability Concerns

- Coverage: High
- Inspected evidence: WS-4 生产前端变更、共享状态组件、路由/store、当前/历史门禁脚本与报告；独立 lint、type-check、build 和合同测试。
- Exclusions / limits: 未启动浏览器、真实后端或依赖；未扩展到 WS-5 会话存储、WS-6 完整 a11y 或非 WS-4 后端业务审计。

当前候选已经在学生、证书、视频主列表等页面采用 query-key、latest-request 和函数级 freshness guard，这些修复方向正确，聚焦 10 项门禁也能真实证明其覆盖路径。问题是同一种不变量没有覆盖材料、统计和 Drawer 内依赖数据，造成“页面级已修、相邻页面仍漏”的不一致。

本轮没有要求建立全局状态机。最小闭环是把已经存在的局部模式复制到四个缺口，并在父子组件边界传递 `loadedKey/loading/error`。服务端校验与权限仍是最终边界，前端修复负责避免误导、错误对象选择和无效写请求。

## 6. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: 当前 36/36 与 85/85 的脚本、JSON、截图、日志和 SHA-256；旧 D1–D5 报告与候选指纹；本轮本地静态/构建/合同命令。
- Exclusions / limits: 本轮未亲自重跑 Playwright；整改方 API 拦截运行不等于真实后端或生产联调。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|-----------------|------|--------|
| manifest 与证据闭包 | High | 不覆盖产品语义，但候选身份和归档完整性可靠 | Keep |
| D6 36 项 | High（限定 D6/API 拦截） | 不证明真实后端 | Keep |
| 10 项学生/证书/视频状态反例 | High（限定已列路径） | 未覆盖相邻同类页面 | Keep but augment |
| 75 项响应式页面检查 | Low | 只检查 H1 与 document overflow，局部布局/状态可假绿 | Keep but augment |
| 旧 D1–D5 阶段报告 | Medium（历史候选） | 指纹不同，不能直接证明最终候选 | Keep as historical evidence |
| 材料/统计/联动/冷启动改密 | None | 本报告的四类缺陷不会使现有 final gate 失败 | Add targeted tests |

### Valuable Tests

- D6 的组织依赖失败/恢复、导入预校验和确认双提交、陈旧预校验阻断与手机上传 Drawer 检查具有明确负向断言。
- 当前 10 项聚焦状态门禁会制造迟到响应、移除 DOM disabled 后强制触发，并核对写请求数；对已覆盖的学生、证书和视频路径具有真实回归价值。
- manifest 外部 verify、三次相同指纹与 43/43 校验和闭包可可靠证明归档没有被替换或混包。

### Suspicious Tests

- 75 个响应式检查多数使用空列表 mock，只等待标题并检查页面级横向溢出；它们不能证明 Grid 断点、局部容器、Overlay、写入口或错误状态。
- 旧 D1–D5 的细粒度门禁各自有价值，但没有当前源码快照或相同最终指纹，不能在批量结论中当作当前执行。

### Missing Tests

- 材料与统计的 A/B 乱序、loaded query-key 与写/导出绑定。
- 培养目标和免考学段依赖选项的 pending、失败与迟到响应。
- 视频任务和管理页首次失败时所有伪零值/占位隐藏。
- 冷启动 `/auth/me.mustChangePwd=true` 的路由重定向。
- 最终候选上的 D1 Drawer/导航、D2 Grid 实际列数、D3 Overlay/防重、D4 公共门面/首次改密和 D5 完整状态矩阵独有断言。

## 7. Change Summary

- Total files changed: 完整 manifest 为 153 tracked + 106 non-ignored untracked；前端切片为 79 tracked + 9 untracked。
- Lines added: 完整 tracked diff 7,673；前端 tracked diff 3,885。
- Lines deleted: 完整 tracked diff 1,783；前端 tracked diff 1,257。
- Commits in range: 0；这是绑定 HEAD `aa3509a…` 的未提交工作树候选。
- Authors: 脏工作树无法可靠归属单一作者，本报告不作推断。

### Change Categories

- New features / UI behavior: 80 个 `frontend/src` tracked/untracked 文件，包含共享组件、路由、页面与 Drawer；类别互有重叠。
- Bug fixes / state remediation: 9 个集中整改页面及 D6 复合页；本轮发现另有 8 个直接相关文件缺少同等闭环。
- Test/tooling: 4 个 `frontend/scripts` 文件，另审查正式证据包内 2 个 browser gate。
- Dependency/build/configuration: 4 个前端 package/Docker/Nginx 文件；本轮未发现与五项 finding 相关的依赖问题。
- Documentation/governance: 候选含现行 WS-4 规范、计划、进度和日志；本报告只更新治理结论。

### Risk Delta

- New risks introduced: 无法仅凭未提交聚合候选准确区分“新引入”与“未完全关闭”；五项均位于本批已修改或宣称验收的范围。
- Existing risks fixed: 已独立确认学生、证书和视频主列表的 latest-request/陈旧写屏障及视频评分同步双触发在源码和聚焦门禁中成立。
- Existing risks made worse: 未发现被明确删除的安全后端边界；前端状态收口不完整导致批量验收声明超出实际覆盖。

### Test Coverage Delta

- New/modified behavior with meaningful final-candidate tests: 学生、证书、视频任务、视频管理四组状态反例，以及 D6 六页产品流程。
- New/modified behavior without sufficient final-candidate tests: `MaterialManageView`、`StatsReportView`、`TrainingDrawer`、`ExemptionManageView`、`ExemptionDrawer`、`MyTaskPanel`、`ManagePanel`、`router/index.ts` 共 8 个直接文件。
- Deleted tests: 未观察到。

### Approval Recommendation

**Request changes.** 五个 Medium 均可用局部状态绑定和小型反例关闭，不需要后端重构、全局状态机、ACL、真实生产访问或扩大到 WS-5/WS-6。整改并在新最终 manifest 下补齐 gap gate 后，再做一次只核这五项和必要回归的独立增量复核。

---

## 8. Principles Compliance

当前实现总体保持 KISS：多数修复采用页面内 sequence/query-key，没有为 UI 状态引入复杂基础设施。候选也遵守 fail-closed 写入口的正确方向，manifest 和证据边界表达诚实。主要问题是一致性不变量没有贯穿所有同类页面及父子组件边界。

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| State ownership / single source of truth | 2 | Medium | 材料/统计查询、培养/免考依赖选项 |
| Fail-safe defaults | 2 | Medium | 视频首错伪零值、Drawer 依赖未成功仍可保存 |
| Boundary contract | 2 | Medium | 父页 freshness 未传 Drawer、`loadMe()` 后路由未重检 |
| Tests verify behavior | 1 | Medium | 最终响应式门禁断言弱于批量验收范围 |

### Principles Respected

- 候选身份、证据闭包和运行边界均明确，不把 API 拦截测试冒充真实后端。
- 已覆盖页面的 latest-request 和函数级 freshness guard 是局部、可读且可测试的实现。
- 异步写操作普遍在首个 `await` 前设置 loading/saving，现有双提交反例具有实际价值。
- 本轮整改无需引入新依赖或系统级架构。

---

## 9. Recommended Fix Order

### Fix Immediately

- 无 Critical/High；不需要紧急生产操作。线上稳定版本继续冻结且不触碰。

### Fix Before Stable Release

1. 先补材料/统计 query-key、培养/免考依赖 freshness 与函数级写守卫。
2. 隐藏视频首次失败时的全部伪零值/伪占位。
3. 在 `loadMe()` 后重新执行首次改密门禁。
4. 在新候选上运行一个只覆盖缺口和 D1–D5 独有断言的合并 browser gate，并前后验证同一 manifest。

### Schedule Later

- UI-009 非学生直达本人页的产品口径继续保持已登记状态；未获产品结论前不把它混入本轮整改。
- 完整 a11y、TLS/session、真实后端 E2E 仍按 WS-5/WS-6 和既有发布计划推进。

### Ignore for Now

- production build 的既有 `naive`/`echarts` 大 chunk warning 属 WS-12 存量，不是本轮退回原因。
- D1 没有真实 `d0-before` 已被如实记录，不要求伪造或重建前置截图。

## 10. Quick Wins

- 在 `router.beforeEach` 的 `loadMe()` 成功分支后补一次 `mustChangePwd` 判断，并加入冷启动合同测试。
- 给视频统计卡和评分区占位增加 `hasLoadedSuccessfully` 条件。
- 复用学生/证书页现有 query-key 模式到材料与统计，不建立新框架。
- 给 Exemption Drawer 传入 `subjectsLoading/subjectsError/loadedSegment`，保存前一行 guard 即可阻断陈旧提交。
- 扩展现有 final gate 的 case 表，不重建另一套证据发布协议。
