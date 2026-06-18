# DESIGN.md — 师范生教育教学能力考核与教师职业能力证书管理平台

> 给编码代理的设计系统规范。**风格方向：明亮圆润 · 友好 SaaS（Notion 风）** —— 青绿主色、大圆角、宽松留白、柔和透气、轻松易读。技术栈：Vue 3 + Naive UI（沿用），经全局主题 token（`theme/naive.ts` themeOverrides + `theme/global.css` + `theme/tokens.ts`）+ 组件层落地，不引新 UI 框架、不改业务逻辑/接口/权限。
>
> ⚠️ 本版是对旧「政务克制蓝/小圆角」方向的**明确替换**——目标是产出**肉眼明显不同**的界面：换主色系（蓝→青绿）、圆角翻倍（6→14px）、间距更宽松、卡片更柔和。

## 1. 设计基调（Design Principles）
- **明亮友好（Bright & Friendly）**：浅暖底、白卡、青绿点缀；观感轻松、亲和、不严肃。
- **圆润柔和（Rounded & Soft）**：大圆角（卡片 14px）、柔和阴影、无硬边框堆叠；元素像"圆角卡片"而非"表格线框"。
- **宽松透气（Generous Whitespace）**：留白比信息密度更优先一档；区块/卡片之间给足呼吸感。
- **清晰易读（Legible）**：字号略大、行高舒展、层级靠留白与字重；ID 类用等宽。
- **一致可达（Consistent / Accessible）**：状态色、按钮层级、空/载态全局一致；对比度 ≥ WCAG AA，焦点可见。

## 2. 颜色 Token（青绿主色 + 暖中性 + 友好语义）
- 品牌主色 `--brand:#0d9488`（teal-600）；`--brand-hover:#0f766e`；`--brand-pressed:#115e59`；`--brand-suppl:#14b8a6`。
- 品牌浅底（选中/hover/标识）`--brand-soft:#f0fdfa`（teal-50）、`--brand-soft-strong:#ccfbf1`（teal-100）。
- 暖中性：`--text:#1f2937` / `--text-secondary:#4b5563` / `--text-muted:#9ca3af` / `--border:#eef0ee` / `--border-strong:#e2e6e3` / `--page-bg:#f6f8f7`（带暖意的浅灰绿白）/ `--surface:#ffffff`。
- 语义色（明快友好）：success `#10b981` / warning `#f59e0b` / error `#ef4444` / info `#0ea5e9`，各配 soft 浅底（如 `#ecfdf5`/`#fffbeb`/`#fef2f2`/`#eff6ff`）。
- 角色色保留但统一到 token。
- 图表分类色板（明快、≤8 类）：`#14b8a6 #0ea5e9 #f59e0b #8b5cf6 #ef4444 #10b981 #6366f1 #f97316`。

## 3. 字体与排版（Type Scale）
- 字体栈：`-apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", Inter, Roboto, sans-serif`；ID/编号用 `--mono: "JetBrains Mono", ui-monospace, Consolas, monospace`。
- 字阶（行高，整体偏舒展）：`12/18`(辅助) · `13/20`(正文小) · `14/22`(正文，默认) · `16/26`(小标题) · `20/30`(页标题) · `26/34`(大标题) · 指标卡数值 `30–32` 半粗。
- 字重：常规 400、强调 500、标题 600。
- 字距：中文 0；全大写英文标签 +0.02em；标题可略松。

## 4. 间距·圆角·阴影（宽松圆润是本版关键差异点）
- 基准 4px；阶梯 `4 8 12 16 20 24 28 32 40 48`（比旧版更舍得留白）。
- 页面内边距 **28**；卡片内边距 **24**；表单项纵向间距 **20**；行内控件间距 10–12；区块间距 **28–32**。
- **圆角**：控件/按钮/输入 **10px**；卡片/抽屉/弹窗 **14px**；标签/徽标 **8px**（pill 类用 999px）。统一偏大、圆润。
- **阴影**（柔和有轻微抬升，营造卡片漂浮感）：卡片 `0 1px 3px rgba(17,24,39,.06), 0 6px 16px rgba(17,24,39,.05)`；hover 略增；弹层更深。弱化硬边框，多用阴影+留白分隔。

## 5. 布局（Layout）
- 侧栏 248（折叠 64）白底、无重边线（用极浅 `--border` 或纯留白分隔）；分组标题 12px muted 宽字距；**选中项 = `--brand-soft` 圆角 pill（10px）底 + 青绿图标/文字**（不用左色条，用圆角块高亮，更友好）；项间距宽松。
- 顶栏 60：左页标题（20px 600），右 学年选择器 / 通知(青绿角标) / 用户头像(青绿)。
- 内容区 `--page-bg` 暖浅底、内边距 28；页内首块 `PageContainer`（标题 20 + 描述 13 muted + 右操作槽），与内容卡片间距 24。
- 列表页：PageContainer →（筛选条：控件圆角 10px、等高 34、间距宽）→ 圆角 14px 卡片包表格 → 分页右对齐。
- 适度最大宽度，超宽屏留白，避免行过长。

## 6. 组件规范（Components）
- **卡片**：白底、圆角 14px、柔和阴影、内边距 24、无硬边框；区块以卡片+留白组织。
- **表格**：放进圆角 14px 卡片内；表头浅青底 `#f3faf8`、字重 500、sticky、首尾留圆角；行高 **48–52**（宽松）；hover 行 `--brand-soft`；数值/ID 右对齐 mono；操作列文字按钮（主操作 primary 实心圆角、次 quaternary），≤3 否则收「更多」popover。
- **按钮**：圆角 10px；主操作 1 个青绿实心；次 default/secondary（浅）；危险 error；轻操作 quaternary。
- **StatusTag**：圆角 pill、soft 底 + 同色字，覆盖全状态枚举，语义对齐 §2。
- **表单**：label top；必填 *；分组用小标题+留白（非粗边框）；只读/锁定态浅底 + 明确「已锁定」；联动字段禁手填（下拉/级联）。
- **指标卡 StatCard**：白卡圆角 14px、label 13 muted + value 30–32 半粗 + 可选环比/单位 + 左侧青绿小图标/色点；卡片间距 16–20。
- **图表 ChartBox**：见 §7。
- **空/载/错态**：空态 `n-empty` 友好引导文案（可配青绿插画感图标）；加载 skeleton；错误 inline 不阻断。
- **菜单**：父项子项被权限全过滤则隐藏父项；当前组默认展开。

## 7. 数据可视化（Charts）
- 统一封装 `ChartBox`：自适应 + resize + 统一 grid/坐标轴/配色（§2 明快色板）。
- **xAxis（类目）**：`axisLabel.interval:0` + 过长 `width`+`overflow:'truncate'`+`hideOverlap`，必要时 `rotate:30` + 足够 `grid.bottom`；`axisTick.alignWithLabel:true`；轴线浅 `#e2e6e3`、标签 `#6b7280`。
- **yAxis（计数）**：`minInterval:1` 整数刻度 + 起点 0 + `splitLine` 浅色。
- 柱图圆角顶 `itemStyle.borderRadius:[6,6,0,0]`、柱宽上限 `barMaxWidth:28–34`；`tooltip.trigger:'axis'`、圆角卡片样式 tooltip。

## 8. 动效与可达性
- 过渡 160–200ms ease；hover 轻微抬升/变色；圆角元素过渡自然。
- 焦点环 2px 青绿；可点元素 ≥34px 命中区；正文对比度 ≥4.5:1，次要 ≥3:1。

## 9. 落地方式（给代理）
- 全部经 `theme/global.css`（CSS 变量）+ `theme/naive.ts`（Naive themeOverrides：common 主色/圆角/`borderRadius:'10px'`、Card/DataTable `borderRadius:'14px'`、Button/Input/Tag/Menu 圆角与配色）+ `theme/tokens.ts`（图表色板）收口；组件层只引用 token，不写散落硬编码色值/魔法间距。
- 不改路由/接口/权限/状态机/迁移；纯视觉与排版层。保持 type-check/build 绿。
- 目标自检：与上一版相比，**主色由蓝变青绿、圆角明显变大、留白明显变宽**——肉眼可辨。
