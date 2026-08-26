# WS-4a · 前端全面 UI 优化·走查 + 设计基线 + 样板（D0/D1/D4） — 提示词（Opus 4.8）

你是执行 **WS-4 设计基线/规范/样板**的 opus（需求② 前端全面 UI 优化，**全站不止列表**）。对应 `docs/audit-remediation-plan.md` §3 WS-4。你出 **D0（走查+规范）→ D1（设计基线+共享组件收口）→ D4（布局/导航/门面）**，codex 按你的样板铺开 D2/D3/D5/D6（`06-ws04b-codex-ui-rollout.md`），最后你做**全站终审走查**。

## 硬约束（用户指定，红线）
- **UI 必须用 Naive UI 实现**：全部优化在 Naive UI 体系内（`n-*` 组件 + `n-config-provider` 的 `themeOverrides`）。
- **禁止引入第二 UI 库**（Element/Ant/Vuetify/Tailwind 组件库等）。
- 自定义 CSS **仅限 design-token 层**（间距/布局容器）微调；视觉样式（色/圆角/字号/密度）一律走 **Naive UI 主题变量**，**不手写覆盖 `n-*` 内部类名**（避免升级即碎）。缺组件用 Naive UI 原语组合，不造轮子。

## 设计工具链（主控已装配，必须使用）
- **frontend-design skill**（user 级插件 `frontend-design@claude-plugins-official`，你的会话自动加载）：D0 定设计方向、D1 出 token 体系、D4 做门面时**必须先调用该 skill**，按其流程走「brainstorm → 紧凑 token 方案（色 4–6 个命名 hex / 字体 2+ 角色 / 布局概念 / signature 元素）→ 对照 brief 自我批判再落地」，明确规避其点名的三种"AI 默认脸"（暖米底+衬线+赤陶色 / 近黑底+单荧光强调 / 报纸细线风）。产出的 token 落到 `theme/tokens.ts` + `theme/naive.ts` themeOverrides（红线不变：Naive UI 体系内实现）。
- **naive-ui-skills**（`~/.claude/skills/naive-ui-skills/`）：全组件 API + `naive-ui-design-{color,layout,typography,border}`/`naive-ui-theming` 设计规范——themeOverrides 键名与组件用法**以此为准**，不凭记忆写。
- **OpenDesign**（桌面端 `D:\Open Design\`，v0.14.1，用户已装）：**设计参考源（只读）**——
  - `D:\Open Design\resources\open-design\craft\*.md`：`anti-ai-slop.md`（D0/D1 通读）、`color.md`、`laws-of-ux.md`、`accessibility-baseline.md`、`state-coverage.md`、`form-validation.md`、`animation-discipline.md`——D1 定基线逐条对照，D0 走查清单的判定依据直接引用它。
  - `design-systems/`（ant/apple/airbnb…成熟体系 token 结构对标，本项目对标 `ant` 最近）、`skills/`（`color-expert`/`design-brief`/`creative-director` 方法论）。
  - GUI 出图/视觉评审归**主控**在 OpenDesign 应用里做；你把 D1 的 token 方案 + 样板截图整理成可贴给主控评审的一页（附进 `docs/ui-optimization-spec.md`），**不要**试图自动化驱动该桌面应用。
- **Playwright MCP**（user 级已装、已 Connected，chromium 已预热）：你的浏览器验证执行器，用法见下节。

## 浏览器验证·跨断点自检协议（硬门槛，防"布局在移动端悄悄崩掉"）
- **断点矩阵（5 档，每个走查/改动视图都过一遍）**：`375×812`（手机）/ `768×1024`（竖板）/ `1024×768`（横板小笔电）/ `1280×800`（笔记本，D6 原目标）/ `1920×1080`（桌面）。
- **逐断点核查项**：① 无水平滚动（`document.scrollingElement.scrollWidth ≤ clientWidth+1`）；② 无元素重叠/关键操作按钮被截断不可点；③ 工具条/筛选条正确**换行**而非溢出；④ 表格窄屏走**容器内横向滚动**而非撑破页面；⑤ Drawer 宽度自适应 ≤ viewport；⑥ console 无新增 error/warning。
- **你交互走查用 Playwright MCP**：`browser_navigate` → 登录（demo 账号）→ `browser_resize` 逐断点 → `browser_take_screenshot` + `browser_console_messages`。
- **D0 必须一次性交付批量自检工具 `frontend/scripts/ui-shots.mjs`**（供你和 codex 的 D2–D6 复用，也是 codex 唯一可用的机制——它没有你的 MCP）：
  - playwright（加 frontend devDependency，与 WS-6 复用，装时记录版本）；各角色 **API 登录**拿 token/cookie（captcha 为 SVG 文本，解析正则照抄 `Phase2SecurityIT.captchaCode`）→ 存 storageState per 角色；
  - 读一份**路由×角色清单**（D0 走查时顺手整理成 `frontend/scripts/ui-routes.json`）→ 按断点矩阵逐页截图 + 执行上面 ①⑥ 两项自动检测 → 截图落 `docs/ui-audit/<子相>/<视图>-<断点>.png`、检测结果落 `report.json`（fail 即列出视图×断点）；
  - 用法一行：`node frontend/scripts/ui-shots.mjs --out docs/ui-audit/d0-before`。改前/改后各跑一遍即完成勾销证据。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§3 WS-4**；`git remote -v` 空。
- **每个子相单独分支 + commit**（避免一个巨型 UI diff 无法复核）：D0→`feature/ws04-d0-ui-audit`、D1→`feature/ws04-d1-design-baseline`、D4→`feature/ws04-d4-layout`。
- 现状盘点：`frontend/src` 约 70 个 `.vue`（15 页面级 View + 20+ Drawer/Modal + 13 共享组件 `DataPanel/FilterBar/PageContainer/DetailPanel/StatCard/StatusTag/EmptyState/TableSkeleton/ChartBox/ReviewDialog/...` + `MainLayout` + `theme/`）。共享组件已相当集中——**先改基线与共享组件，再铺开各视图**，事半功倍。theme 层在 `frontend/src/theme/{naive.ts,global.css,tokens.ts}`。
- **走查前置/勿误报**：① demo 样例视频当前是"合法可下载占位、非可播 MP4"——本机若有 ffmpeg 先重生成 1–2s 真实 H.264 短片替换（见 WS-14），否则走查视频以"可下载"为准、播放器打不开**不是** UI bug；② `test_cert_issuer` 无有效角色（`CERT_ISSUER` 已 V20 退役），该账号近空菜单属**预期**，看证书功能用 `admin`/`test_academic_admin`。

## 任务
### D0 全站 UI 走查 + 问题清单
- 用 demo 数据启动（`--platform.demo.enabled=true`），以**各角色**逐页走查——**每页按五断点矩阵实渲染**（Playwright MCP resize + 截图，见协议节），不是只看桌面宽度。
- 产出问题清单，按 **多余按钮 / 对齐 / 间距 / 状态缺失 / 布局 / 响应式(标注坏在哪个断点) / 文案** 分类，标注视图与截图。
- **"多余按钮"判定清单在此阶段交主控/用户确认**后再铺开——避免误删唯一入口。
- **产物：`docs/ui-optimization-spec.md`**（走查结论 + 下述规范 + 逐视图整改清单，供 codex 逐项执行）**+ `frontend/scripts/ui-shots.mjs` + `ui-routes.json`**（跨断点自检工具，协议节所述，codex 铺开全靠它）**+ `docs/ui-audit/d0-before/` 基线截图**。

### D1 设计基线（design tokens + 共享组件收口）
- `theme/global.css` + Naive UI `themeOverrides` 统一 **spacing 刻度(4/8/12/16/24)、圆角、字号层级、主色/语义色**；消灭视图内散落的魔法 margin/padding。
- 收口共享组件 API：`PageContainer`（页头：标题/说明/主操作位）、`FilterBar`（筛选布局/基线对齐/展开收起）、`DataPanel`（表格密度/操作列宽对齐/分页条位置）、`StatCard/StatusTag/EmptyState/TableSkeleton`（尺寸与语义色统一）。视图层只消费规范化组件。
- 挑 **2 个代表视图**（1 列表管理页 + 1 含 Drawer 的复杂页）改成**样板并截图**——这是 codex 铺开的模板。

### D4 布局/导航/门面
- `MainLayout`（侧栏分组与图标一致、面包屑/页头层级、内容区最大宽与留白）、`DashboardView`（卡片栅格对齐、图表容器统一 `ChartBox`）、`LoginView`（视觉打磨）、`NotFound`/错误页统一。涉及信息架构判断归你。
- 动 Dashboard 时**顺手为 WS-12 留好路由级分割点**（echarts 懒加载）。

## 验收（每子相独立）
- `npm run type-check` 干净 + `npm run build` 成功；**改前/改后截图对比**；**跨断点自检硬门槛**——改动视图跑 `ui-shots.mjs` 改前/改后，五断点全过（无水平滚动/无重叠/console 无新错；任一断点崩=不过）；关键动作入口不丢（对照权限矩阵抽 3 角色活体点查）。
- D2–D6 由 codex 完成后，**你做一次全站终审走查**对照 D0 清单收尾——终审含 `ui-shots.mjs` 全站全断点复扫，`report.json` 零 fail。

## 收尾（每个子相都要）
1. 前端 `type-check` + `build` 绿。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加对应子相条目（带截图对比）。
3. **每子相单 commit**（分支如上）→ **STOP** 交主控复核。**禁止 merge / push**。
