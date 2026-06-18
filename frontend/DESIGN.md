# DESIGN.md — 师范生教育教学能力考核与教师职业能力证书管理平台

> 给编码代理的设计系统规范。目标：在不改业务逻辑/接口的前提下，把 UI 提升到"政务·教育级专业后台"质感——克制、可信、信息密度高但清晰。技术栈：Vue 3 + Naive UI（沿用），通过全局主题 token + 组件层落地，不引入新 UI 框架。

## 1. 设计基调（Design Principles）
- **克制专业（Restrained / Institutional）**：政务教育系统，少装饰、强秩序；色彩低饱和、留白稳定，信息优先。
- **清晰层级（Hierarchy）**：每屏一个主标题、一组操作；用字号/字重/留白而非分隔线堆叠制造层级。
- **垂直韵律（Vertical Rhythm）**：统一的间距阶梯与基线节奏，区块间距 = 内容密度的函数；表单/卡片/区段遵循同一 8px 网格。
- **数据密度可读（Dense but Legible）**：后台表格信息密集，但行高/对齐/留白保证扫读；ID 类用等宽。
- **一致与可达（Consistent / Accessible）**：状态色、按钮层级、空/载态全局一致；对比度 ≥ WCAG AA，焦点可见。

## 2. 颜色 Token（低饱和政务蓝 + 中性灰阶 + 语义色）
- 品牌主色 `--brand: #1d4ed8`（深一档、更institutional 的蓝）；`--brand-hover:#1e40af`；`--brand-soft:#eff4ff`（浅底/选中）。
- 中性灰阶（slate）：`--text:#0f172a` / `--text-secondary:#475569` / `--text-muted:#94a3b8` / `--border:#e5e7eb` / `--border-strong:#d1d5db` / `--page-bg:#f6f8fb` / `--surface:#ffffff`。
- 语义色：success `#16a34a` / warning `#d97706` / error `#dc2626` / info `#2563eb`（配各自 soft 浅底）。
- 角色色（沿用、统一到此处 token）：student/clerk/auditor/review_teacher/academic_admin/sys_admin。
- 数据可视化分类色板（≤8 类，低饱和）：`#2563eb #16a34a #d97706 #7c3aed #0891b2 #dc2626 #64748b #ca8a04`。

## 3. 字体与排版（Type Scale）
- 字体栈：`-apple-system, "Segoe UI", "Microsoft YaHei", "PingFang SC", Roboto, sans-serif`；ID/编号/金额用 `--mono: "JetBrains Mono", ui-monospace, Consolas, monospace`。
- 模块化字阶（行高）：`12/18`(辅助) · `13/20`(正文小) · `14/22`(正文) · `16/24`(小标题) · `20/28`(页标题) · `24/32`(大标题/指标卡数值用 28–30 半粗)。
- 字重：常规 400、强调 500、标题 600；避免 700+ 大面积使用。
- 字距：中文 0；全大写英文标签 +0.02em。

## 4. 间距与栅格（Spacing / Grid）
- 基准 4px；阶梯 `4 8 12 16 20 24 32 40`。
- 页面内边距 24；卡片内边距 20；表单项纵向间距 16；行内控件间距 8–12；区段间距 24–32。
- 圆角阶梯：控件/按钮 6px，卡片/抽屉 8px，标签/徽标 4px（统一，勿混用大圆角）。
- 阴影：克制——卡片 `0 1px 2px rgba(16,24,40,.04), 0 1px 3px rgba(16,24,40,.06)`；浮层/抽屉略深；hover 仅轻微抬升。

## 5. 布局（Layout）
- 侧栏 240（折叠 64）白底、1px 右边线；分组标题灰、字号 12、字距宽；选中项 `--brand-soft` 底 + 左 3px 主色条。
- 顶栏 56–60：左页标题，右 学年选择器 / 通知 / 用户。
- 内容区 `--page-bg` 浅灰、内边距 24；页内首块用 `PageContainer`（标题 20 + 描述 13 muted + 右操作槽）。
- 列表页结构：PageContainer →（筛选条 inline，控件等高 32–34）→ 卡片包表格 → 分页右对齐。
- 内容最大宽度不强制全屏拉伸：超宽屏表格区设 `max-width` 或合理留白，避免行过长难扫读。

## 6. 组件规范（Components）
- **表格**：表头 `#f8fafc` 底、字重 500、sticky；行高 44–48；hover 行 `--brand-soft` 浅；斑马纹可选关；数值/ID 右对齐+mono；操作列用文字按钮（主操作实心、次操作 quaternary），≤3 个，多则收 `...`。
- **按钮层级**：主操作 1 个 primary 实心；次操作 default/secondary；危险操作 error；轻操作 quaternary 文字。同一行不堆多个实心。
- **StatusTag**：沿用自动配色，扩展到全部状态枚举，统一 soft 底 + 同色字；草稿/退回/不合格/已锁定等语义对齐 §2。
- **表单**：label-placement top；必填 *；分组用小标题非粗边框；只读/锁定态浅灰底 + 明确"已锁定"提示；联动字段禁手填（下拉/级联）。
- **指标卡 StatCard**：label 13 muted + value 28–30 半粗 + 可选环比/单位；左色条或图标弱化。
- **图表 ChartBox**：见 §7。
- **空/载/错态**：空态 `n-empty` 给引导文案；加载 skeleton/spinner；错误 inline 提示不阻断。
- **菜单**：父项子项被权限全过滤则隐藏父项（已修）；当前组默认展开。

## 7. 数据可视化（Charts）— 修复轴显示问题
- 统一封装 `ChartBox`：自适应宽高 + resize 监听 + 统一 grid/坐标轴/配色（取 §2 分类色板）。
- **xAxis（类目）**：`axisLabel.interval: 0`（强制全显，勿自动跳标）+ 过长标签 `width` + `overflow: 'truncate'` + `hideOverlap`，必要时 `rotate: 30` 并给足 `grid.bottom`；`axisTick.alignWithLabel: true`。
- **yAxis（数值=计数）**：`minInterval: 1`（整数刻度，杜绝 0.5/1.5）+ 起点 0 + `splitLine` 浅灰；数值轴名置顶 `nameGap` 合理。
- `grid` 四边留白随标签长度自适应；`tooltip.trigger:'axis'`；柱宽上限 `barMaxWidth: 28–36`；移动窄屏减少类目或滚动。

## 8. 动效与可达性
- 过渡 150–200ms ease；hover/active 轻微；避免大位移动画。
- 焦点环 2px 主色；可点元素 ≥32px 命中区；正文对比度 ≥ 4.5:1，次要文本 ≥ 3:1。

## 9. 落地方式（给代理）
- 全部经 `theme/global.css` 的 CSS 变量 + Naive `themeOverrides`(common/Button/DataTable/Menu/Tag/Card/Input) 收口；组件层只引用 token，不写散落硬编码色值/魔法间距。
- 不改路由/接口/权限/状态机；纯视觉与排版层提升；保持 type-check/build 绿。
