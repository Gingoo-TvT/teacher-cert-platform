# WS-4b · 前端全面 UI 优化·跨视图铺开（D2/D3/D5/D6） — 提示词（codex）

你是执行 **WS-4 跨视图铺开**的 codex（需求② 前端全面 UI 优化）。对应 `docs/audit-remediation-plan.md` §3 WS-4。**依赖 opus 的 D0/D1 先落地**：以 `docs/ui-optimization-spec.md`（D0 走查清单+规范）和 D1 的 2 个样板视图 + 收口后的共享组件为**唯一模板**逐项执行。

## 硬约束（用户指定，红线）
- **UI 必须用 Naive UI**：`n-*` 组件 + `themeOverrides`；**禁止第二 UI 库**；不手写覆盖 `n-*` 内部类名（走主题变量）；缺组件用 Naive UI 原语组合。
- **不改视觉基线**（色/圆角/字号/密度以 D1 的 `theme/naive.ts` themeOverrides 为准）——你只按样板铺开，不另立风格。

## 工具链与跨断点自检（必须使用；你没有 Claude 侧 MCP/skill，以下全是磁盘路径/脚本）
- **组件与主题权威参考**：`C:\Users\wenbibuhaoqwq\.claude\skills\naive-ui-skills\`（全 `n-*` 组件 API + `naive-ui-design-*`/`naive-ui-theming` 规范）——themeOverrides 键名、组件属性**以此为准**，不凭记忆写。
- **设计 craft 对照（OpenDesign 资源，只读）**：`D:\Open Design\resources\open-design\craft\`——D3 对照 `form-validation.md`；D5 对照 `state-coverage.md`（loading/empty/error/disabled 全态覆盖）+ `anti-ai-slop.md`（文案语气）；D6 对照 `accessibility-baseline.md`；动效克制照 `animation-discipline.md`。
- **跨断点自检工具（opus D0 已交付）**：`frontend/scripts/ui-shots.mjs` + `frontend/scripts/ui-routes.json`——playwright 脚本，自动各角色登录→按 **375×812 / 768×1024 / 1024×768 / 1280×800 / 1920×1080** 五断点截图 + 自动检测水平滚动与 console 错误，产物落 `docs/ui-audit/<子相>/` + `report.json`。
  - **每个子相开工先跑改前基线**：`node frontend/scripts/ui-shots.mjs --out docs/ui-audit/<d2|d3|d5|d6>-before`；收尾跑 `--out docs/ui-audit/<子相>-after`。
  - 若该工具尚不存在 → **STOP 知会主控**（说明 WS-4a D0 未落地，你的依赖未就绪），不要自己另造机制。
- **逐断点核查项（人工复核 report.json + 抽看截图）**：无水平滚动；无元素重叠/按钮被截断；工具条/筛选条换行而非溢出；表格窄屏走容器内横向滚动而非撑破布局；Drawer 宽度自适应 ≤ viewport；console 无新增错误。**D6 之外的子相同样适用**——任何视图改动都不允许在任一断点悄悄崩。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§3 WS-4**，以及 `docs/ui-optimization-spec.md`（opus D0 产物）。
- `git remote -v` 空。**每个子相单独分支 + commit**：D2→`feature/ws04-d2-tables`、D3→`feature/ws04-d3-forms`、D5→`feature/ws04-d5-feedback`、D6→`feature/ws04-d6-responsive`。
- 收尾必跑 `cd frontend && npm run type-check && npm run build`。
- **⚠️ 交叉文件**：`video/components/UploadPanel.vue`、`video/components/UploadVideoDrawer.vue`、`material/components/MaterialUploadDrawer.vue` 由 **WS-3 先动**——**D3 最后再碰这三个**，且先确认 WS-3 已合并/协调好。

## 任务（按 D0 清单逐项，改前/改后截图勾销）
### D2 列表/表格规范铺开（原始诉求核心）
- **删多余按钮**（同一动作只留一处；行内操作 vs 顶部批量分区；**无权限按钮隐藏而非灰置**——配合 `frontend/src/directives/perm.ts` 的 `v-perm`）。
- **操作列统一**（宽度/对齐/按钮尺寸密度一致）。
- **元素对齐**（工具条基线；列对齐：文本左/数字右/状态居中；筛选条与表格间距）。
- 逐视图**核对入口不丢**（对照权限矩阵）。覆盖 `frontend/src/views/**` 全部列表视图（Student/Certificate/Exchange/Exemption/Material/Training/TestResult/Notice/System/... 管理页）。

### D3 表单/抽屉/弹窗统一（20+ 个 Drawer/Modal）
- 标签宽度与放置统一（label-width/placement）；必填标记与校验提示一致；按钮次序统一（主按钮右置、取消左侧、`:loading` 防重复提交保持）；抽屉宽度分级（窄/中/宽三档）；Modal vs Drawer 使用场景统一。

### D5 反馈状态一致性
- 加载（统一 `TableSkeleton`/按钮 loading）、空态（统一 `EmptyState` 含引导动作）、错误提示（统一 message/notification 用法与文案语气）、危险操作二次确认（`ReviewDialog`/`n-popconfirm` 择一规范）。

### D6 响应式 + 基础可访问性
- **断点矩阵下探到 375**（手机）：五断点全过，不只是 ≤1280 笔记本——工具条换行、表格容器内横向滚动、抽屉宽度自适应（窄屏全宽/近全宽档）；焦点可见、按钮可键盘触达、对比度过基线（对照 OpenDesign `accessibility-baseline.md`）——与 **WS-6 的 axe 门禁**衔接。
- D6 收尾跑**全站**全断点 `ui-shots.mjs`，`report.json` 零 fail 作为本子相核心证据。

## 验收（每子相独立）
- `npm run type-check` 干净 + `npm run build` 成功；**改前/改后截图对比**逐项勾销 D0 清单；**跨断点自检硬门槛**——改动视图 `ui-shots.mjs` 改前/改后五断点全过（无水平滚动/无重叠/console 无新错；任一断点崩=不过，"桌面看着好了"不算完成）；关键动作入口不丢（对照权限矩阵抽 3 角色活体点查）。

## 收尾（每个子相都要）
1. 前端 `type-check` + `build` 绿。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加对应子相条目（带截图对比）。
3. **每子相单 commit**（分支如上）→ **STOP** 交主控复核。**禁止 merge / push**。
4. 全部 D2–D6 完成后**知会主控**，由 opus 做全站终审走查收尾。
