# WS-4 前端 UI 优化规范与整改清单

> 状态：D0–D6 首轮整改独立增量复核为 CHANGES_REQUESTED（0 High / 2 Medium / 1 Low）；WS-4 继续复核退回。  
> 边界：本阶段不访问、不修改线上稳定版本 `dfdfb91`，不改变业务流程、接口、权限点或数据口径。  
> 日期：2026-08-05

## 1. D0 结论

当前前端已有可复用的 Naive UI 基座：`PageContainer`、`FilterBar`、`DataPanel`、`DetailPanel`、`EmptyState`、`TableSkeleton`、`StatusTag`，以及主题变量和图表色板。D1 不重做组件体系，重点补齐响应式能力并统一 Overlay。

静态盘点覆盖 74 个 Vue 文件、21 个认证业务页、23 个 Drawer、22 个 Modal。主要问题集中在：

1. 248px 固定侧栏没有手机模式；900px 以下只缩减文字和间距。
2. 多数 `n-grid` 使用固定数字列数，`responsive="screen"` 并不会自动把纯数字列降为一列。
3. 22/23 个 Drawer 固定为 560px；10 个 Modal 固定为 520–760px；32 个 Drawer 双列 Grid 没有手机单列口径。
4. 主从双栏在扣除侧栏和页面 padding 后，实际可用宽度小于断点判断使用的屏幕宽度。
5. 部分异步 Modal 缺少独立 loading 防重，部分系统 Drawer 的关闭入口不一致。

以下判断来自当前源码静态走查，不冒充浏览器动态证据。`docs/ui-audit/d0-before/` 只有在当前候选由外部终端启动，并由 `ui-shots.mjs` 真正生成后才可归档。

## 2. 五断点验收协议

每个改动视图必须覆盖：

| ID | 视口 | 主要用途 |
|---|---:|---|
| `phone` | 375×812 | 手机 |
| `tablet-portrait` | 768×1024 | 竖板 |
| `tablet-landscape` | 1024×768 | 横板/小笔电 |
| `laptop` | 1280×800 | 常用笔记本 |
| `desktop` | 1920×1080 | 桌面 |

每档必须满足：

- 页面级无水平滚动；宽表只在 `DataPanel` 内横向滚动。
- 标题、筛选条、工具栏和关键操作可见、可点、不重叠；空间不足时换行。
- Drawer/Modal 不超过 viewport，手机上的双列表单降为单列。
- loading、空态、错误态、禁用态及成功反馈可辨认。
- Console 无新增 warning/error，页面无未捕获异常。

批量工具：

- 路由和角色矩阵：`frontend/scripts/ui-routes.json`。
- 批量截图和自动检查：`frontend/scripts/ui-shots.mjs`。
- 配置为六个角色固定预期路由；工具把后端实际返回的 `permissions` 与预期逐项比较，缺少或多出入口
  都失败关闭，再按预期矩阵截图。每角色使用独立、仅存于内存的 browser storage state。
- 自动检查页面级横向溢出和 Console warning/error；重叠、按钮可点性、Drawer 打开态仍需在报告截图上人工确认。
- 输出目录必须不存在或为空，避免上次 PASS 截图混入本轮结果。

运行前提：由外部人工终端启动**当前候选**的 demo 环境，所有待测账号已完成初始改密；不要指向线上 `dfdfb91`。密码只从环境变量读取，不写入配置或报告。

```powershell
npm --prefix frontend install
npm --prefix frontend exec -- playwright install chromium
$env:UI_AUDIT_BASE_URL = 'http://127.0.0.1:5173'
$env:UI_AUDIT_PASSWORD = '<六个演示账号共用口令；若不同则设置 ui-routes.json 中对应的 passwordEnv>'
npm --prefix frontend run ui:audit -- --out docs/ui-audit/d0-before
```

只验证矩阵、不访问服务：

```powershell
npm --prefix frontend run ui:audit -- --dry-run
```

## 3. 设计与实现基线

本阶段沿用现有冷白底、青绿色主色和 Naive UI，不换技术栈，不引入第二套样式系统。

- 布局：先用现有 `n-layout`、`n-grid`、`n-flex`、`n-space` 和共享容器解决问题；页面自己写媒体查询只用于局部特殊结构。
- 间距：共享表面以 4/8/12/16/24 为核心节奏；历史 `--space-5/7` 继续兼容旧视图，不做全仓机械替换。
- 字体：12/13/14/16/20px 形成辅助、说明、正文、分区标题、页标题五级；小字号辅助文本使用 `#6b7280`，不再以低对比浅灰承载正文信息。
- 响应式：手机 1 列；平板按内容 1–2 列；桌面 2–5 列。断点必须用 Naive UI 响应式列描述或共享样式明确表达，禁止只写固定 `:cols="N"`。
- Overlay：窄/中/宽固定为 420/560/720px，全部带 viewport 上限；不创建表单 Schema 或“大一统 Drawer”。
- 表格：继续使用 `DataPanel` 推导 `scroll-x`；不让列宽把整个页面撑开。
- 操作：高频主操作最多一个 primary；同一区域不放两个语义相同的刷新/新增入口。
- 表单：保持 `label-placement="top"`；手机单列；异步提交必须有 loading 防重；校验信息紧邻字段。
- 状态：空态必须解释下一步，错误态可重试，加载态不造成布局跳变；状态文案和色调由同一元数据源维护。
- 动效：只用于 Drawer/Modal、折叠和状态切换，不添加装饰性长动画。

## 4. 问题清单

| ID | 优先级 | 分类 | 范围 | 现象与整改方向 | 动态状态 |
|---|---|---|---|---|---|
| UI-001 | P0 | 布局/响应式 | `MainLayout` | 手机仍占用 248px 侧栏；改为移动端菜单抽屉或等价收起模式 | D1 本地动态关闭：720px 以下改遮罩 Drawer，30/30 五断点检查与导航选路通过 |
| UI-002 | P0 | 响应式 | 22 个 Drawer、10 个固定宽 Modal | 375px 视口越界风险；统一 viewport 限宽，双列转单列 | D3 本地动态关闭：剩余 Overlay 静态统一 token/单列口径，8 个代表 Overlay 手机/桌面 16/16 通过 |
| UI-003 | P1 | 响应式 | Dashboard、证书、测试、材料、免考、统计、系统页等 | 固定数字 Grid 在窄屏继续保持 2–5 列；改为明确响应式列 | D2 七个高频页面本地动态关闭：五断点 55/55、实际列数 37/37；其余系统复合页仍归 UI-004 后续批次 |
| UI-004 | P1 | 布局 | 字典、区划、学科、组织专业 | 主从栏断点未扣侧栏；以内容容器宽度为准收敛为单列 | D6 本地动态关闭：四页五断点 20/20，内容容器断点与错误/陈旧/真实空态通过 |
| UI-005 | P1 | 布局/间距 | 导入页、视频步骤 | Steps 说明和上传区在手机过重；收敛步骤文案与纵向布局 | D6 本地动态关闭：两页五断点 10/10，导入互斥/陈旧结果和视频手机 Drawer 功能检查通过 |
| UI-006 | P1 | 状态缺失 | 复评、指派、退回、替换佐证、评审组等 Modal | 异步提交缺独立 loading 防重；统一 action/footer 状态 | D3 关闭 Overlay 写动作；D5 本地动态关闭非 Overlay 首载错误、陈旧错误、真实空态与依赖选项门禁，浏览器状态机 24/24 |
| UI-007 | P2 | 对齐/一致性 | 系统域 Drawer、多个 Modal | closable、footer/action 位置不统一；按共享模式收口 | 静态确认 |
| UI-008 | P2 | 导航 | `MainLayout` | `default-expanded-keys` 只管初始状态，跨菜单跳转可能不展开当前组 | D4 本地动态关闭：受控 expanded keys 保留手动状态，跨组与同组程序化跳转均重开当前组 |
| UI-009 | P2 | 导航/文案 | `/student/self` | 菜单限制学生，路由只按权限；非学生直达时页面语义不成立 | 待产品口径确认 |
| UI-010 | P2 | 文案/层级 | 全部认证页 | 顶栏路由标题与 `PageContainer` H1 重复，占用垂直空间 | D1 本地动态关闭：移除顶栏重复标题，保留内容区 H1/说明 |
| UI-011 | P2 | 维护性 | `CertificateIssueDrawer`、`CertificateVoidDrawer` | 文件名叫 Drawer，实际为 Modal；后续按真实组件分类 | 静态确认 |
| UI-012 | P2 | 状态一致性 | `StatusTag` | 标签和色调分成两张映射，新增状态可能只补一侧 | D1 静态关闭：统一由 `STATUS_META` 派生文案与色调；动态回归待截图 |

## 5. 逐视图整改矩阵

| 视图 | 类型 | D1–D6 目标 |
|---|---|---|
| Login | 认证表单 | 检查 375px 高度、验证码行、初始改密 Modal；保留品牌识别 |
| Dashboard | 工作台 | 4/3/2 列指标改响应式；快捷入口换行；验证菜单跳转展开 |
| DictManage | 主从复合页 | 内容宽度断点、左列表/右详情单列化；确认重复刷新/新增入口 |
| RegionManage | 主从复合页 | 树/列表与详情在窄屏顺序化；详情区不撑宽 |
| SubjectManage | 主从复合页 | 1040px 最小宽结构改为容器响应式；导入入口保持唯一 |
| OrganizationManage | 双页签复合页 | 专业/配置面板在中等宽度提前单列；工具条换行 |
| SecurityManage | 三页签复合页 | 作为权限密集页回归；统一 Drawer/Modal；确认全局与面板刷新重复 |
| SystemAudit | 三页签复合页 | 指标 Grid 响应式；备份 Drawer；确认顶部与面板刷新重复 |
| StudentManage | 列表样板 | D1 列表样板：筛选、远程分页、行操作、空态、详情/编辑、审核 Modal 全闭环 |
| StudentSelf | 详情表单 | 双列转单列；确认非学生直接访问口径 |
| TrainingManage | 复杂 Drawer 样板 | D1 Drawer 样板：13 字段、4 分区、联动、校验、只读/编辑、固定 footer |
| MaterialManage | 列表/上传 | 4 列指标、上传 Drawer、预览 Modal；确认空态上传按钮重复 |
| ExemptionManage | 流程列表 | 4 列指标、申请/审核 Overlay；确认空态申请按钮重复 |
| VideoReview | 多角色工作台 | 页签和步骤在手机纵向化；播放器沿用 `min(...,94vw)`；确认两组空态按钮重复 |
| TestResultManage | 列表/导入 | 4 列指标和 760px 导入 Modal 响应式；导入动作防重 |
| CertificateManage | 复合列表 | 5 列指标降列；作废/补发 Modal；确认空态生成按钮重复 |
| CertificateIssue | 签发列表 | 3 列指标、批量工具条和签发 Modal 在窄屏换行 |
| ExchangeImport | 四步工作台 | Steps、拖拽上传、三类结果表适配；不改变导入流程 |
| ExchangeExport | 导出工作台 | 筛选/导出操作换行；大表仅容器内滚动 |
| StatsReport | 指标/图表/明细 | 4 列指标降列；图表最小高度和明细表滚动 |
| NoticeCenter | 列表/详情 | 3 列指标、筛选与分页；详情 Drawer 限宽；确认两处刷新入口 |
| NotFound | 状态页 | 五断点居中、返回入口可见 |

## 6. D1 样板

- 列表管理样板：`StudentManageView.vue`。覆盖共享容器、筛选、分页表格、行操作、空态 CTA、编辑/详情 Drawer 和审核 Modal。
- 复杂 Drawer 样板：`TrainingManageView.vue` + `TrainingDrawer.vue`。覆盖新增/编辑/只读、自助模式、13 个字段、4 个分区、多级联动、校验和异步保存。
- Modal 样板：沿用 `ReviewDialog.vue` 的 viewport 限宽、footer 和 loading 模式。

D1 只完成这两个代表视图和必要的共享能力；确认截图与交互口径后，D2–D6 再铺开，避免一次性巨型 UI diff。

本轮静态落地：

- `global.css` 与 `themeOverrides` 保持冷白底 + 青绿色主色，不换肤、不引入第二套 UI；核心间距、字号、圆角、语义色和 420/560/720px Overlay 三档已经统一。
- `PageContainer` 去掉无动作时的空占位；`FilterBar` 消除双重下边距并在加载中锁定重置/展开；`DataPanel` 统一移动端操作区与分页横向兜底，提供加载、空态和可重试错误态；`TableSkeleton`、`EmptyState`、`ReviewDialog` 补最小可访问语义和加载关闭边界。
- `StudentManage` 在 768px 以下采用紧凑远程分页和单列详情，编辑/详情 Drawer 都受 viewport 限制；`StudentDrawer` 以自身宽度从双列降为单列。
- `TrainingDrawer` 扩为 720px 宽样板，按容器 560px 断点在单列/双列间切换，手机 footer 两个动作等宽，既有 13 字段、4 分区、联动、校验和保存流程未改。
- 本地 `lint`、`type-check`、`build`、截图脚本语法检查、`ui:audit --dry-run` 与 `git diff --check` 均通过。

本轮本地运行态补证：

- 经用户明确允许，使用专属一次性 Docker 项目启动当前候选，未连接或修改线上稳定版本 `dfdfb91`。
- 学生、学院教务员、教务处管理员三角色 × 学生/培养两个样板 × phone、tablet portrait、tablet landscape、laptop、desktop 五断点共 **30/30 PASS**；无页面级横向溢出、Console warning/error、未捕获异常或权限矩阵漂移。
- 首轮自动检查虽为 30/30，但人工截图发现 375px 固定侧栏仍占 248px；修复为 720px 以下遮罩导航 Drawer，并把“移动端仍有固定侧栏”加入截图门禁后复验通过。同步移除顶栏重复 H1、避免教务管理员三枚行操作换行，并把 FilterBar 单列断点收至 720px。
- 移动导航打开/选路、Student Drawer 和 Training Drawer 的手机/桌面打开态及空保存校验共 **5/5 PASS**。动态反例同时发现两个 Drawer 把表单校验 rejection 泄为页面未处理异常，现已在字段反馈后正常返回并复验关闭。

对照设计资源后的自我约束：保留产品已有识别，不加渐变、装饰性图形或第二强调色；使用 Naive UI 原生 Grid/Drawer/Modal 的键盘与焦点能力，不重复造 ARIA；只抽取已出现的共享状态和尺寸，不建立大一统表单/Drawer 架构。D1 当前只能记本地动态门禁通过，不能替代独立复核或扩大为 WS-4 PASS。

### 6.1 D2 高频指标与列表页响应式铺开

- 范围：`DashboardView`、`MaterialManageView`（含 `MaterialSelfPanel`）、`ExemptionManageView`、
  `TestResultManageView`、`CertificateManageView`（含 `CertificateSelfPanel`）、
  `CertificateIssueView`、`StatsReportView`。
- 根因：这些页面的 `n-grid` 使用固定数字 `:cols`；即使声明 `responsive="screen"`，列数仍不会随
  内容区缩窄而变化，而且屏幕宽度不能反映侧栏和页面 padding 扣除后的真实可用宽度。
- 最小整改：改用 `responsive="self"` 和显式字符串断点，375px 单列、平板 2–3 列、宽屏恢复原目标
  列数，并补 `y-gap`。表格继续只由 `DataPanel` 容器横向滚动，不改业务列、接口或权限。
- 边界：固定宽 Modal/Drawer、审核 loading、防重复提交与主从复合页不纳入 D2，分别留给后续批次。
- 本地门禁：lint、type-check、build 与 `git diff --check` 均通过；学生与教务处管理员在 7 个目标页、
  phone/tablet portrait/tablet landscape/laptop/desktop 五断点共 **55/55 PASS**，页面级横向溢出、
  Console warning/error、未捕获异常和权限矩阵漂移均为 0；另按计算后的 `grid-template-columns`
  断言手机/平板/桌面实际列数，**37/37 PASS**。人工抽查材料手机、学生证书平板、管理员证书横板与
  统计桌面截图，未见卡片裁切、重叠或页面级溢出。
- 状态：`D2_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`。该状态不扩大为 WS-4
  独立 PASS；固定宽 Overlay 与异步动作仍按 D3 边界处理。

### 6.2 D3 Overlay 限宽与异步动作闭环

- 范围：登录初始改密、学生/培养样板、材料、免考、测试结果、证书、通知、视频评审，以及字典、
  组织专业、账号权限、参数审计备份等系统域 Drawer/Modal；不改接口、权限、路由或业务状态机。
- 最小整改：统一复用 `--overlay-narrow/medium/wide` 与 `--overlay-drawer-max/modal-max`，表单使用
  `responsive="self"` 在手机改单列；写动作在首次异步校验前设置局部 busy，并在 `finally` 复位，
  等待中锁定表单、X、遮罩和 Esc。材料/视频上传的“取消并清理服务端会话”仍保留为显式可用动作。
- 数据一致性：角色权限加载增加 `loaded` 门禁，加载失败不能把空列表保存回角色；连续查看测试科目或
  有效性时按请求代次只接受最后一次选择；自定义 Student/Subject/Region 控件显式合并父表单 busy，
  避免 Boolean `disabled=false` 覆盖等待态。
- 本地门禁：lint、type-check、build 与 `git diff --check` 均通过；公共登录、学生和系统管理员覆盖
  D3 相关页面五断点 **110/110 PASS**；学生/培养、材料/免考、测试导入、证书生成、通知详情 8 个
  代表 Overlay 的手机/桌面打开态 **16/16 PASS**。另以浏览器路由拦截执行测试导入和材料上传两个
  异步反例 **2/2 PASS**：同步双击只产生 1 个请求，等待中动作呈 loading、表单禁用、Esc/X 锁定，完成或失败
  后状态正确恢复；拦截请求未转发后端，因此没有测试写入。
- 状态：`D3_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`。代表性动态反例与全量
  静态核对共同支撑本地关闭，不把 2 个样例夸大为每个 Overlay 都逐项执行；WS-4 仍待批量独立复核。

### 6.3 D4 布局、导航与公共门面收口

- 范围：`MainLayout`、`DashboardView`、`LoginView`、路由守卫与 403/404 页；不改业务接口、权限点、数据口径或状态机。
- 最小整改：菜单展开状态受控且保留手动选择，路由变化时确保当前组可见；增加轻量面包屑、内容宽度上限与
  移动端首帧布局。Dashboard 仅渲染角色有权查看的统计/通知；不具备统计权限时改为“可办理事项”。无权路由进入
  显式 403，公共 404 与登录页复用 Naive UI 语义组件。
- 功能闭环：首次改密成功后，按后端“立即撤销旧令牌”合同清理前端会话，刷新验证码并要求使用新密码重登；
  Nginx 以 `$http_host` 保留非 80/443 入口端口，避免同源 POST 被误判为 CORS。
- 本地门禁：lint、type-check、build 与 `git diff --check` 均通过；公共登录/404 与三角色 Dashboard 五断点
  **25/25 PASS**，页面溢出、Console error、未捕获异常均为 0；桌面/移动导航、受控展开、学生单列 Dashboard、
  403 反馈与首改密重登录 **10/10 PASS**。
- 状态：`D4_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`。本地动态证据不扩大为 WS-4 独立 PASS。

### 6.4 D5 非 Overlay 反馈、真实空态与最小键盘操作

- 范围：学生/培养列表与本人信息、材料、免考、测试结果、证书管理/签发/本人证书、视频上传、通知中心、
  Dashboard、统计报表，以及主菜单分组、视频任务和认证关键字段；不改变接口、权限或业务状态机。
- 状态语义：首次请求失败只显示错误与重试，不显示“0 条/0 项/尚未上传”等伪空态；已有成功数据后刷新失败
  则保留旧数据并显示陈旧警告，同时禁用依赖最新状态的新增、编辑、审核、导出、上传或提交。只有成功响应且
  数据确为空时才显示真实空态和对应 CTA。
- 依赖选项：学生/培养、材料和免考的列表与字典/学科选项独立加载；列表可读不等于写流程可用，选项未知或
  加载失败时明确告警并阻断相关写入口，重试成功后恢复。
- 最小可访问性：菜单分组支持 Enter/Space 展开并同步 `aria-expanded`；视频任务行支持键盘选择并暴露选择态；
  登录、首改密、评分和评语字段补明确可访问名称。完整自动化 a11y 门禁及上传拖拽键盘体验留给 WS-6。
- 本地门禁：lint、type-check、build、既有 logout/dashboard 前端合同与 `git diff --check` 通过；在专属一次性
  前端容器中使用 Playwright API 拦截，确定性执行初始错误、重试、陈旧数据保留、写入阻断、真实空态和键盘
  操作 **24/24 PASS**。该证据只证明当前前端构建产物的 UI 状态机，不冒充真实后端、数据库或端到端联调。
- 状态：`D5_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`；聚焦复核发现的本人资料陈旧态、
  本人证书伪空态、首载 0 统计和选项失败写入口四类问题均已按原触发条件关闭。

### 6.5 D6 复合系统页与上传流程产品收口

- 范围：`DictManage`、`RegionManage`、`SubjectManage`、`OrganizationManage`、`ExchangeImport` 和学生
  `UploadPanel`；不改 API、权限点、导入/上传协议或后端状态机。
- 布局：四个系统复合页使用 `responsive="self"` 按真实内容宽度在主从双栏与单列间切换；导入和视频
  Steps 在手机纵向显示，统计卡与上传 Drawer/footer 不越出 viewport。
- 产品状态：字典类型/项、区划列表/路径、学段/分类/学科、学院/专业/培养目标与配置均独立呈现首次错误、
  陈旧结果和真实空态。依赖不可信时保留可读旧数据但关闭编辑、删除、选择、导入或新增配置。
- 异步正确性：latest-request 与查询键绑定覆盖字典项、区划、学科筛选、导入批次和视频年度；最近学科标签
  不能绕过禁用门禁。预校验重试失败后旧结果只读，上传、确认和回滚互斥。
- 本地门禁：lint、type-check、production build 与 `git diff --check` 通过；隔离静态前端以 API 拦截执行
  六页五断点 **30/30**，组织依赖失败/恢复、预校验与确认双提交、预校验失败阻断旧结果、视频手机步骤与
  Drawer 操作 **6/6**，合计 **36/36 PASS**。该证据不冒充真实后端或独立复核。
- 状态：`D6_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`。D0–D6 实现批次已收口，
  WS-4 在批量独立复核前仍为进行中。

### 6.6 D1–D6 集中复核退回整改

- 集中只读复核发现 3 个 Medium：列表迟到响应覆盖新筛选/分页；刷新失败后陈旧数据仍可执行新增、编辑、
  审核、签发等写操作；视频任务与管理页首载失败伪空、陈旧数据可写且评分缺函数级防重。
- 学生、培养、证书管理/签发、测试结果、免考、通知和视频任务/管理 9 页以局部 query-key、request
  sequence 与 freshness guard 修复；D5 遗漏的陈旧态“新增学生”也纳入按钮与函数双层阻断。
- 修后只读复查为 **0 High / 0 Medium**。本地静态门禁 PASS；当前构建复跑 D6 原 **36/36**，受影响与
  复合页面五断点 **75/75**，请求乱序、陈旧写屏障、首错重试及评分同步双触发 **10/10**。
- 独立批量复核结果：**CHANGES_REQUESTED（0 High / 5 Medium / 0 Low）**。已覆盖的学生、证书、视频主列表
  修复成立，但材料/统计、培养/免考 Drawer、视频首错、冷启动首次改密和最终同指纹 gate 仍有缺口；详见
  `reviews/ws-04-d1-d6-batch-independent-review-2026-08-05.md`。
- 本轮最小整改：材料/统计补 request sequence、current/loaded query-key 与导出 loaded snapshot；培养/免考
  Drawer 将级联选项 pending/error/loaded-key 绑定控件和保存；视频首错隐藏伪 0/伪占位；冷启动
  `loadMe()` 后重检 `mustChangePwd`。gap gate 使用精确迟到响应完成等待及收窄的 HTTP 合同。
- 本地门禁：gap **18/18**、D5 **24/24**、D6 **36/36**、D1–D4/D6 增量 **85/85**，lint、type-check、
  build 与三个前端合同均 PASS。状态为 `LOCAL_REMEDIATION_GATE_PASS / INDEPENDENT_REREVIEW_PENDING`；
  不改变 API、权限或业务状态机，也不将本地 API 拦截结果冒充真实后端或 WS-4 独立 PASS。
- 首轮整改独立增量复核：唯一候选 `745e9986...d4d9` 的 manifest、62/62 checksum 与上述四份报告绑定
  成立；上一轮四类产品缺陷按原口径关闭。最新正式结论为
  **CHANGES_REQUESTED（0 High / 2 Medium / 1 Low）**：材料批量下载需绑定 loaded query snapshot；四个
  函数级写屏障反例需消除 disabled 假绿；视频成功空态需精确断言五个 0。报告为
  `reviews/ws-04-d1-d6-remediation-independent-rereview-2026-08-05.md`。

## 7. 用户已确认并应用：重复入口（2026-08-02）

用户已确认采用下述建议；本轮只调整入口位置/文案，不改变权限条件、处理函数或业务流程。

### A. 空列表时同屏出现两个同动作 CTA

建议：空列表时保留空态中央主 CTA，隐藏同屏 DataPanel 表头中的同动作按钮；有数据时恢复表头按钮。

1. 培养信息：“新增培养信息”已按空表/有表切换。
2. 过程性材料：“上传材料”已按空表/有表切换。
3. 免考管理：“免考申请”已按空表/有表切换。
4. 证书管理：“生成证书”已按空表/有表切换。
5. 视频管理：“上传视频”已按空表/有表切换。
6. 评审组：“新增评审组”已按空表/有表切换。

### B. 同页存在两个刷新入口

建议按实际作用域分别处理：

1. 账号权限：顶层动作已改名“刷新全部”；各 DataPanel 的局部刷新保留。
2. 参数审计备份：顶部“刷新参数/刷新审计”已删除；对应 DataPanel 局部刷新与唯一顶部“记录备份演练”保留。
3. 数据字典：PageContainer 顶部“刷新/新增类型”已删除；类型 DataPanel 内入口保留。
4. 通知中心：页面级“刷新”保留；FilterBar submit 文案已改为“查询”。

`StudentManage` 的“新增学生”和“去导入”语义不同，不列为重复按钮。

## 8. D0–D6 当前状态与下一步

- D0：`D0_STATIC_COMPLETE / DUPLICATE_ACTION_POLICY_CONFIRMED / RUNTIME_SCREENSHOTS_PENDING`。
- D1：`D1_LOCAL_RUNTIME_GATE_PASS / INDEPENDENT_REVIEW_PENDING`；五断点 30/30、移动导航与双样板 Drawer 5/5。
- D2：`D2_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`；七页五断点 55/55、Grid 实际列数 37/37。
- D3：`D3_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`；相关页五断点 110/110、代表 Overlay 16/16、拦截写反例 2/2。
- D4：`D4_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`；五断点 25/25、布局/导航/权限/首改密功能 10/10。
- D5：`D5_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`；隔离前端状态机 24/24，覆盖初始错误、重试、陈旧数据、写入阻断、真实空态与最小键盘操作。
- D6：`D6_LOCAL_RUNTIME_GATE_PASS / BATCH_INDEPENDENT_REVIEW_DEFERRED`；六页五断点 30/30、产品流程 6/6，覆盖内容容器布局、依赖恢复、双提交互斥、陈旧结果阻断和手机上传。
- D1 改动前没有生成真实 `d0-before`；后续不得把新工作树截图改名冒充 before。若不重建准确的前置候选，只归档绑定当前候选的 `d1-after` 与 `report.json`。
- 集中整改：首轮独立增量复核已将原 0 High / 5 Medium / 0 Low 收敛为
  **0 High / 2 Medium / 1 Low**，状态为 `INDEPENDENT_REREVIEW_CHANGES_REQUESTED`，不得自行改写为 PASS。
- 下一步：只修最新报告 2 Medium / 1 Low，并在新唯一 manifest 下重交；WS-4 继续为复核退回。
- 本轮不构成 WS-4 独立 PASS，也不授权 merge、push、deploy 或切流；线上稳定版本 `dfdfb91` 未访问、未修改。
