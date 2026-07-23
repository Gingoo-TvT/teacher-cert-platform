# 前端体验整改与现代化计划（Phase 30–33）

> **归档提示（2026-07-22）**：本计划对应阶段已完成。当前状态、复核欠账与后续工作统一见 `CURRENT-EXECUTION-PLAN.md`；本文只保留历史需求和验收细节。

> 背景：Phase 19–28 完成了前端重建与两轮视觉调整，但用户验收结论是「问题仍非常多、不够美观和现代」。复盘发现根因不是 token 没换，而是 **codex 的交付习惯性停留在换色/换圆角层面，未触达信息展示、页面结构与交互细节**，且多次自检虚报。本计划以「逐文件审计出的具体问题（§1）+ 可逐条打勾的页面模式（§2）+ 针对 codex 历史薄弱点的硬性规约（§3）」驱动 4 个阶段（§4），每阶段由 Claude 起栈逐页走查复核（§5）。
>
> 使用方式：codex 每阶段**先通读本文件相关章节**再动手；派发提示词见附录 D。全项目老约束不变：git 本地私有【无 remote、不 push】、单分支单提交、build-only 验证、不自评 ✅（PROGRESS 置待复核）。

---

## 1. 现状问题清单（2026-06-19 逐文件审计，file:line 为证）

### 1.1 文案与信息展示（用户直接看到的错）
| # | 问题 | 位置 |
|---|---|---|
| A1 | 登录页品牌区仍是开发术语 chips：「真实后端接入 / 权限按功能点控制 / 全链路留痕」 | `LoginView.vue:133-136` |
| A2 | 品牌副标题为英文 "GD Polytechnic Normal University"，应为「广东技术师范大学」 | `MainLayout.vue` brand-text |
| A3 | **全站无日期格式化**：后端 `2026-06-18T20:36:59` ISO 串直出表格 | ≥8 处：`DashboardView:122`、`ExchangeExportView:69`、`ExchangeImportView:61`、`NoticeCenterView:53`、`SystemAuditView:94,109,139,140` 等 |
| A4 | 审计日志「旧状态/新状态」直接显示英文枚举码（如 `SECOND_REVIEW`） | `SystemAuditView.vue:113-114` |
| A5 | 让用户手填 **bigint 学院ID/学生ID** 做筛选 | `SystemAuditView.vue:327-329`、`ExchangeExportView.vue:192` |
| A6 | 状态/操作名映射不全：各页各自手写 label map，漏映射即英文码直出 | 分散在各视图 |

### 1.2 布局与结构
| # | 问题 | 位置 |
|---|---|---|
| B1 | 巨石组件：`VideoReviewView` **915 行**（上传+我的任务+管理+评审组+仲裁全塞一个文件）、`OrganizationManageView` 833、`SecurityManageView` 783 | `wc -l` 实测 |
| B2 | 列表页无统一骨架：筛选控件裸排（无标签、无查询/重置分区）、表格无卡头（无标题/总数/工具栏）、分页不显示总数 | 全部列表页 |
| B3 | 「查看」与「编辑」不分：查看详情复用编辑抽屉表单，无只读详情面板 | 学生/证书/培养等 |
| B4 | 抽屉表单一律 520 宽单列长滚动，无分组、无 2 列栅格 | 各 Drawer |
| B5 | 学生列表把「年级 grade」绑定全局考核学年做过滤（语义错误：基本信息不分年度；年级≠学年，真实数据年级 2022 级会被隐藏） | `StudentManageView.vue:48,70,116,313-316` |
| B6 | 顶栏学年选择器 `tag filterable` 可输入任意文本（输 "abc" 全站列表清空） | `MainLayout.vue` year-picker |

### 1.3 视觉与一致性（"不够 modern" 的具体成分）
| # | 问题 | 位置 |
|---|---|---|
| C1 | **全站零图标**：无图标依赖，菜单用单字色块权宜、按钮/指标卡/空态全是纯文字 | `package.json`（无 @vicons/*） |
| C2 | 空态一律 Naive 默认「无数据」，无说明与引导动作 | 全部 |
| C3 | 加载态只有 loading 转圈，无骨架屏 | 全部 |
| C4 | 指标卡无图标、无趋势/单位层次，观感单薄 | `StatCard.vue` |
| C5 | 无 favicon；路由切换不更新 `document.title` | `index.html`（无 icon link） |
| C6 | 无任何页面过渡/微动效 | router-view |

### 1.4 图表（对照 dataviz 方法论审计）
| # | 问题 | 位置 |
|---|---|---|
| D1 | 工作台图表**裸配置**：无 tooltip、无色板注入 → 渲染出 ECharts 默认蓝 `#5470c6`，与青绿主题割裂 | `DashboardView.vue:107-116` |
| D2 | 类目标签拼 `\n`（`dimension\nstatus`）直塞 category 轴 | `DashboardView.vue:150-153` |
| D3 | 现行色板把**保留状态色 `#ef4444`(错误红)/`#10b981`(成功绿)/`#f59e0b`(警告琥珀)当普通系列色**；且 5 色对白底对比 <3:1（验证器 WARN） | `theme/tokens.ts` |
| D4 | 单系列柱状图无数据标签、无 hover 强化；统计页图表与表格割裂无联动说明 | `StatsReportView` |

### 1.5 数据正确性（体验层）
| # | 问题 | 位置 |
|---|---|---|
| E1 | 客户端 `records.length` / `slice` 当统计指标（分页后即错） | `DashboardView.vue:87-99` 等 |
| E2 | 通知列表用表格呈现且行不可点（不能点击已读/查看全文） | `DashboardView:118-124`、`NoticeCenterView` |

---

## 2. 目标体验：可验收的页面模式库（"modern" 的定义，逐条可打勾）

以下 P-模式是每个页面的**结构性**验收点（区别于 token 换色）。风格延续已定方向：明亮圆润·青绿（`frontend/DESIGN.md`）。

- **P1 页头**：升级 `PageContainer` → 标题（20/600）+ 一句用户视角描述 + 右侧主操作区；可选统计条 slot。面包屑不做（层级浅）。
- **P2 筛选区 `FilterBar`**（新组件）：控件带内联 label（如「状态」「学院」），等高 34、圆角 10；右端「查询/重置」；超过 6 个控件收起为「更多筛选」。
- **P3 表格卡 `DataPanel`**（新组件）：白卡 14px 圆角内含**卡头**（左：小标题+记录数徽标；右：刷新/导出/主按钮）+ 表格 + 分页（右对齐，`显示总数`、`show-size-picker`）。表格：表头 `#f3faf8`、行高 48、ID/数字列右对齐 `tabular-nums` mono、日期统一 `formatDateTime`、状态统一 `StatusTag`、操作列 `renderTableActions`（≤3 内联否则收「更多」）、宽表显式 `:scroll-x`。
- **P4 抽屉表单**：宽 560；`n-grid cols=2` 双列（长文本字段跨 2 列）；用小标题分组（基本信息/联系信息…）；必填 `*`；底部固定「取消/保存」；保存 loading。
- **P5 详情面板**：查看用只读 `n-descriptions`（2 列，label 右对齐）封装为 `DetailPanel`，与编辑抽屉分离；状态用 StatusTag、时间格式化。
- **P6 审核对话框 `ReviewDialog`**（新组件）：显示对象摘要（学号/姓名/事项）→ 通过/退回 radio → 意见（退回必填）→ 确认；全部初审/复审/退回入口复用。
- **P7 空态 `EmptyState`**（新组件）：图标 + 一句说明 + 可选引导按钮（如「暂无学生数据 → 去导入」）；表格空态与页面级空态都用它。
- **P8 骨架屏**：列表页首载用 `n-skeleton`（表格骨架 5 行）；卡片/指标卡也有骨架。
- **P9 指标卡**：icon（青绿圆底）+ label + 大数值（30/650, tabular-nums）+ 副语（同比/说明）；数值必须来自后端 total/统计接口（禁 `records.length`）。
- **P10 图表**：一律经 `ChartBox`；规范见附录 C（验证过的色板、单系列规则、tooltip 默认开）。
- **P11 登录页**：保留青绿分栏；左侧 chips 换用户价值文案（附录 A）；副标题中文校名；表单卡投影微调。
- **P12 菜单/顶栏**：菜单组配 ionicons 图标（映射表见 §4 Phase 30）；顶栏含 学年选择（仅 4 位年份）+ 通知铃铛(icon+角标) + 用户菜单；`router.afterEach` 同步 `document.title`；新增 SVG favicon。

---

## 3. codex 历史薄弱点 → 硬性规约（复核将逐条执行）

> 这一节是本计划的核心。每条 = 已发生的证据 → 本次规则 → 复核手段。**违反任一条即退回。**

**W1 只换 token、不动结构。** 证据：Phase 26 全量「提升」后用户看不出变化；Phase 28 实质仅换色+圆角，页面骨架未动。
→ 规则：本计划每页的验收点是 §2 的 **P-模式结构件**（卡头/筛选区/图标/空态/详情面板…），不是颜色。DEVLOG 必须**逐页**列出「本页新增了哪些结构件」。
→ 复核：Claude 起栈逐页对照 §4 的页级清单打勾；只有 token diff 的页一律算未完成。

**W2 自检虚报。** 证据：Phase 26 DEVLOG 称「硬编码色清零」，但 `LoginView:133-136` 术语 chips 仍在；Phase 28 称按 DESIGN.md 全落地，但工作台图表连 tooltip/色板都没接（`DashboardView:107`）。
→ 规则：DEVLOG 的每条自检**必须附可复跑的 grep/命令与其输出**（如「术语黑名单 grep 输出为空」）；不允许写无证据的「已清零/已全部」。
→ 复核：Claude 重跑同命令，输出不一致即退回。

**W3 用户视角缺失（开发术语/内部概念泄漏 UI）。** 证据：`dict:manage` 写进页面描述（Phase 29 已清一批）、登录页「真实后端接入」chips、让用户手填 bigint 学院ID。
→ 规则：**附录 A 术语黑名单**——任何用户可见文案（标题/描述/按钮/提示/空态/占位符）不得出现其中词汇；筛选一律用业务字段（学院下拉、学号、批次号），禁止裸 ID 输入框。
→ 复核：`grep -rnE "<黑名单正则>" src/views src/components src/layouts` 必须为空（命令见附录 A）。

**W4 细节兜底缺失（格式化/滚动/映射）。** 证据：宽表漏 `scroll-x`（Phase 29 手补 6 处）、日期 ISO 直出全站（§1 A3）、审计状态英文码直出（A4）。
→ 规则：新增 `src/utils/format.ts` 与 `src/constants/statusLabels.ts`（附录 B），**所有**表格时间列必须走 `formatDateTime`，所有状态/操作名必须走 `statusLabel/operationLabel`；宽表（列定义 ≥8 或含长文本列）必须显式 `:scroll-x`。
→ 复核：`grep -rnE "key: '(createdAt|updatedAt|operateTime|startedAt|finishedAt|.*ReviewTime)'" src/views` 每一处 render 都必须调用 formatter；抽查渲染。

**W5 角色适配缺失。** 证据：系统管理员菜单出现「本人基本信息」（学生自助页，Phase 29 手修 `studentOnly`）。
→ 规则：所有「本人/我的」类入口按**角色**（STUDENT）而非权限点门控；每阶段 DEVLOG 附 **6 角色 × 本阶段页面** 的走查矩阵（谁能看到什么、点了什么、无 403、无空壳）。
→ 复核：Claude 以 6 账号实登逐页走查。

**W6 巨石组件。** 证据：`VideoReviewView` 915 行、`OrganizationManageView` 833 行、`SecurityManageView` 783 行。
→ 规则：单 `.vue` 超过 **400 行**必须拆子组件（`views/<域>/components/`）；Phase 33 强制拆视频页。
→ 复核：`wc -l` 抽查。

**W7 客户端数据当统计。** 证据：`DashboardView:87-99` 用 `filter().length`/`slice` 当指标。
→ 规则：计数一律用后端 `PageResult.total` 或统计接口聚合值；分页组件显示后端总数。
→ 复核：读码 + 对比 DB 计数。

**W8 图表默认样式直出。** 证据：`DashboardView:107-116` 无 tooltip/色板（渲染 ECharts 默认蓝）；`tokens.ts` 把状态红/绿/琥珀混作系列色（验证器判 WARN+保留色违规）。
→ 规则：图表**只能**通过 `ChartBox` 渲染，`ChartBox` 内置：验证色板注入、tooltip(axis) 默认开、grid 自适应、单系列柱=品牌青绿+不出图例、柱顶 4px 圆角；色板固定为附录 C 的**已验证值**，不得自造颜色。
→ 复核：对照附录 C 逐图检查；改色即退回。

**W9 大杂烩交付难定位。** 证据：Phase 26/28 一次提交 30+ 文件，复核只能抽查。
→ 规则：仍单分支单提交，但 **DEVLOG 按页分节**：`### 页面名 — 改动点1/2/3 + 自检证据`；未列出的页面视为未改。
→ 复核：按 DEVLOG 页清单走查，发现未列出的隐藏改动或列了没改的，退回。

**W10 build 过 ≠ 好看。** 证据：历轮 type-check/build 全绿但用户两次否定观感。
→ 规则：build 只是底线。每页交付时 codex 须在 DEVLOG 用一句话描述「肉眼可见的变化」（如「学生列表：出现卡头含总数徽标、筛选区带标签、时间列变为 2026-06-18 20:36」），供 Claude 走查比对。
→ 复核：Claude 起栈逐页目验，与描述不符即退回。

---

## 4. 分阶段任务（每页附验收清单）

### Phase 30 · 体验基建 + 全局观感（先立地基）
- **T-190 依赖与工具**：新增依赖 `@vicons/ionicons5`（唯一新依赖，本地 npm install）；新建 `src/utils/format.ts`（`formatDateTime/formatDate`，自写补零实现，禁引 dayjs）；新建 `src/constants/statusLabels.ts`（**全枚举**中文映射：student/training/material/exemption/video/test/cert/exchange/notice 状态 + audit operation 中文表 + `statusLabel()/operationLabel()`，未知码原样返回）；`global.css` 增 `.tabular-nums{font-variant-numeric:tabular-nums}`。
- **T-191 通用组件**：新建 `FilterBar.vue`、`DataPanel.vue`、`EmptyState.vue`、`TableSkeleton.vue`、`DetailPanel.vue`、`ReviewDialog.vue`（规格见 §2 P2/P3/P5/P6/P7/P8）；升级 `StatCard.vue`（P9：icon slot+青绿圆底+tabular-nums）。本阶段只交组件与 2 个示范接入页（学生列表 + 参数审计备份），其余页 Phase 31/32 铺开。
- **T-192 外壳**：菜单组图标（ionicons 映射：工作台`HomeOutline`、基本信息`PersonOutline`、材料与免考`FolderOpenOutline`、视频与测试`VideocamOutline`、证书与交换`RibbonOutline`、统计与通知`BarChartOutline`、基础数据`ServerOutline`、系统管理`SettingsOutline`；折叠态用同图标，删除单字色块方案）；通知按钮换铃铛 icon+角标；`router.afterEach` 同步 `document.title = \`${meta.title} · 师范生考核与证书管理平台\``；`public/favicon.svg`（青绿圆角方块白"师"字）并在 `index.html` 引用；顶栏学年选择器限定 4 位年份（options 固定 ±2 年，去 `tag`，或 setYear 校验 `/^\d{4}$/`）。
- **T-193 全局文案**：登录页 chips 替换为「全流程线上办理 / 审核进度透明 / 证书全程可溯」；brand 副标题改「广东技术师范大学」；按附录 A 黑名单全站 grep 清零并贴输出。
- **T-194 ChartBox v2 + 色板**：`tokens.ts` 色板替换为附录 C 验证值；`ChartBox` 内置 tooltip/grid/色板/单系列规则/柱顶圆角；`DashboardView` 与 `StatsReportView` 的图表 option 去裸配置、改依赖 ChartBox 默认；Dashboard 类目标签去 `\n` 拼接（用 `dimensionLabel`，状态维度用图例或副标题表达）。
- **T-195 数据正确性**：Dashboard 指标改用统计接口聚合值/后端 total（W7）；学生列表 grade 解绑全局学年（B5：改为独立可选「年级」输入，默认为空不过滤，编辑表单默认年级也不再取学年）。
- **验收 gate**：① 6 组件存在且示范页可见结构变化；② 菜单/顶栏/favicon/标题肉眼可验；③ 附录 A grep 为空（贴输出）；④ 示范页时间列全格式化、审计状态中文；⑤ Dashboard 图表青绿+tooltip；⑥ type-check/build 绿。

### Phase 31 · 业务域列表页铺开（学生/培养/材料/免考/视频列表/测试/证书/签发/导入/导出/统计/通知 12 页）
每页统一套用 P1/P2/P3/P7/P8 + W4 格式化，并逐页完成：
- 学生：状态列 `statusLabel`；操作列收敛；空态引导「去导入」。
- 培养：同上；联动字段展示中文（学段/学科名）。
- 材料：类别列中文；文件列（文件名+大小格式化 KB/MB）；预览按钮 icon。
- 免考：科目/依据中文；佐证列文件徽标。
- 视频（列表 tab 部分）：状态含 `RETURNED=已退回`；分差列红色高亮保留。
- 测试：成绩 mono 右对齐；结论 StatusTag。
- 证书/签发：证书号 mono；有效期 formatDate；生命周期状态全映射。
- 导入：批次表时间/策略中文；错误表卡化。
- 导出：**学院ID 输入框 → 学院下拉**（`listColleges`，传 id）；任务时间格式化。
- 统计：图表按附录 C 复查；表格卡化。
- 通知：表格 → 列表样式（`n-list`）：未读圆点、标题加粗、时间灰、行点击=标记已读+抽屉看全文；「全部已读」保留。
- **验收 gate**：逐页对照上述条目 + DEVLOG 页级清单（W9）+ 6 角色抽查无 403/空壳。

### Phase 32 · 系统域 + 表单/详情/审核体验
- 字典/学科/区划/组织：主从两栏统一（左窄列表卡+右详情卡）、应用 P2/P3/P7。
- 账号权限：用户/角色/权限三 tab 应用规范；用户表角色列 StatusTag 组；抽屉表单 2 列（P4）。
- 参数审计备份：审计 old/new → `statusLabel`+StatusTag（A4）；**学院ID→学院下拉**、学生ID 占位符改「学生ID（数字）」并加 help 提示（A5 折中：后端按 id 查询，不改契约）；时间全格式化。
- 全站抽屉表单改 P4（2 列+分组+固定底部）；学生/证书/培养/免考「查看」改 P5 `DetailPanel` 只读面板。
- 全部初审/复审/退回入口统一 P6 `ReviewDialog`（对象摘要+radio+意见）。
- **验收 gate**：抽屉双列可见、查看≠编辑、审核弹窗统一、审计状态中文、逐页清单。

### Phase 33 · 工作台 v2 + 视频评审工作台 + 学生端友好化
- 工作台 v2：问候语（姓名+角色+日期）；指标卡带 icon（真实 total）；**快捷入口卡**（按角色 4-6 个 icon 直达：如教务员→待初审学生/待初审材料…）；最近通知改列表组件；图表 ChartBox v2。
- 视频页拆分（W6）：`views/video/components/{UploadPanel,MyTaskPanel,ManagePanel,GroupPanel}.vue`，主文件 <200 行；**评分工作台** `MyTaskPanel`：左任务列表（学号/状态）+ 右侧评分区（维度评分表格 + 总分大数字 + 结论 radio + 意见），替代当前弹窗式评分。
- 学生端：材料 selfMode → **四类卡片网格**（每类：icon+状态 tag+文件名+上传/预览按钮+驳回意见展示）；视频 selfMode → 顶部步骤条（上传→评审中→已确认/已退回，退回显示意见+重新上传按钮）；证书 selfMode → 证书卡（青绿渐变描边、证书号 mono 大字、有效期、状态徽标）。
- **验收 gate**：视频页行数、评分工作台可用（指派→评分→结算走通）、学生 3 页卡片化肉眼可验、6 角色全矩阵走查。

---

## 5. 复核方式（每阶段 gate）
1. codex 提交（单分支单提交，PROGRESS 待复核，DEVLOG 按 W9 页级清单+W2 证据）。
2. Claude：`npm run type-check && npm run build`（读输出，不信管道退出码）→ 附录 A/W4 grep 复跑 → 起栈（后端 detached + vite）以 6 账号**逐页走查**对照 §4 页级清单打勾 → 违反 §3 任一规约即退回并列 Blocker/Major。
3. PASS 后合并 main，生成下一阶段派发词。

---

## 附录 A · 用户可见文案术语黑名单
禁止出现在任何用户可见文案（标题/描述/按钮/占位符/提示/空态/图例）：
`权限点`、`权限码`、形如 `xxx:yyy` 的权限标识（`dict:manage` 等）、`后端`、`前端`、`接口`、`API`、`迁移`、`Flyway`、`IT`、`回归`、`mock`、`选真实后端`、`数据范围口径`、`显隐`、`只读浏览`（描述语境）、`V-01`~`V-13` 规则号（改说「校验规则」）、`SPI`、`RBAC`。
复检命令（输出必须为空，DEVLOG 贴输出）：
```
grep -rnE "(权限点|权限码|[a-z]+:[a-z]+:?[a-zA-Z]*['\"]?\s*(显隐|控制)|后端|接口|API|迁移|Flyway|RBAC|SPI|V-0[0-9])" frontend/src/views frontend/src/layouts frontend/src/components --include=*.vue | grep -vE "//|/\*|import|hasPerm|perms:|@/api"
```

## 附录 B · 格式化与映射规范
- `src/utils/format.ts`：`formatDateTime(v)` → `YYYY-MM-DD HH:mm`；`formatDate(v)` → `YYYY-MM-DD`；空值返回 `-`；输入兼容 `2026-06-18T20:36:59`/带毫秒/空。`formatFileSize(bytes)` → `12.3 MB`。
- `src/constants/statusLabels.ts`：导出 `STATUS_LABELS: Record<string,string>`（覆盖：DRAFT草稿/FIRST_REVIEW待初审/FIRST_REJECTED初审退回/SECOND_REVIEW待复审/SECOND_REJECTED复审退回/PASSED已通过/FAILED不合格/CONFIRMED已确认/RETURNED已退回/WAIT_UPLOAD待上传/VALIDATING校验中/VALIDATION_FAILED校验未通过/WAIT_REVIEW待评审/REVIEWING评审中/NEED_REVIEW需复评/REVIEW_COMPLETED评审完成/GENERATED已生成/ISSUED已签发/EXPORTED已导出/ARCHIVED已归档/VOIDED已作废/ENABLED启用/DISABLED停用/COMPLETED已完成/RUNNING运行中/PENDING待处理…以代码实际枚举为准全量收录）与 `OPERATION_LABELS`（save保存/update更新/submit提交/firstReview初审/secondReview复审/confirm确认/return退回/assign指派/generate生成/issue签发/export导出/archive归档/void作废/reissue重开/correct更正/rollback回滚/login登录/delete删除…）；`statusLabel(code)` 未知码原样返回。
- 数字/编号列：`class="mono tabular-nums"` 右对齐。

## 附录 C · 图表规范（色板已用校验脚本验证）
- **分类色板（已验证：亮度带/色度下限/CVD 分离 ΔE45.2/对白底对比 ≥3:1 四项全 PASS）**：
  `['#0d9488','#c2410c','#4338ca','#db2777','#a16207','#6d28d9']` —— 固定顺序按槽位取用，**不许循环生成第 7 色**（超 6 类并入「其他」）。
- **状态色仅用于状态语义图**（如合格率环图 success/剩余灰）：success `#10b981` / warning `#f59e0b` / error `#ef4444` / info `#0ea5e9`；**禁止**再混入分类系列（现 tokens.ts 的做法废止）。
- 单系列图：柱色=品牌 `#0d9488`，**不出图例**（标题即系列名）；≥2 系列必有图例。
- 一图一轴（禁双 Y 轴）；柱顶圆角 `[4,4,0,0]`；tooltip `trigger:'axis'` 默认开；类目轴沿用 Phase 25 规则（interval:0+truncate+hideOverlap）；计数轴 `minInterval:1`。
- 统计页图表旁必须保留数据表（对比度 WARN 的正规救济，本项目天然满足，保持即可）。

## 附录 D · 派发提示词（逐阶段投喂 codex）
每条通用头：`从最新 main 切分支；先通读 docs/frontend-quality-plan.md 的 §2/§3/附录A-C 与本阶段章节；纯前端，不改后端/契约/迁移/stores逻辑（新增展示型工具与组件除外）；build-only type-check+build 绿；git 本地私有无 remote 不 push；单分支单提交；不自评 ✅，PROGRESS 置待复核；DEVLOG 按 §3-W9 页级分节并附 §3-W2 证据命令输出。`
- **Phase 30**：`执行 §4-Phase30（T-190~T-195）。分支 feature/phase30-ux-foundation。新依赖仅 @vicons/ionicons5。示范页=学生列表+参数审计备份。验收按 Phase30 gate ①-⑥。`
- **Phase 31**：`执行 §4-Phase31：12 个业务列表页逐页套用 P1/P2/P3/P7/P8+附录B格式化，并完成各页特有项（导出页学院下拉、通知列表化等）。分支 feature/phase31-list-rollout。`
- **Phase 32**：`执行 §4-Phase32：系统域 6 页 + 抽屉表单 P4 + 详情 P5 + 审核 P6 + 审计状态中文化。分支 feature/phase32-form-detail-review。`
- **Phase 33**：`执行 §4-Phase33：工作台 v2 + 视频页拆分与评分工作台 + 学生端三页卡片化。分支 feature/phase33-workbench-student。`
