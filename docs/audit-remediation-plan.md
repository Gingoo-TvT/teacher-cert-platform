# 审计整改 + 两条新需求 · 执行计划（供 codex / opus 4.8 执行）

> **已合并（2026-07-22）**：WS-1–WS-15 的当前状态、优先级和放行条件已收口到 `CURRENT-EXECUTION-PLAN.md`。本文保留原审计依据、详细做法与验收口径，正文状态不再作为当前账本。

> 依据：`audit-report-teacher-cert-platform-2026-07-05.html`（commit `e4f8228`，overall 5.9/B；0 Critical / 2 High / 6 Medium / 3 Low）
> + 两条新需求：①MinIO 用独立文件服务器 → 全面**预签名**；②**前端全面 UI 优化**（不止列表——含列表多余按钮清理、元素对齐，以及表单/抽屉、布局导航、反馈状态、响应式等全站一致性）。
> + **launch-readiness 仍开放项复查**（审计 11 条之外经代码核实仍开放的：全链路 TLS＝P0-9、RBAC 授权天花板＝P1 自提权、备份运维遗留、以及历次 phase 诚实推迟项）。
> 现状：`main` 已完成 Phase 37–53（P0 全绿、P1 大部分、种子测试/生产分离、各角色 demo 数据）。本计划在此基线上整改。

---

## §0 铁律与环境陷阱（codex / opus 4.8 都必须先读）

- **Git 本地私有**：无 remote、**永不 push**。每个 WS 一条分支（`feature/wsNN-...`）off `main`，`verify` 全绿后**单 commit**，然后**STOP 交人工/主控复核合并**（不要自行 merge、不要 commit 后 checkout main）。开工前后都 `git remote -v` 确认为空。
- **构建门禁**：`mvn -B -ntp clean verify` 必须**全绿**；改前端另跑 `cd frontend && npm run type-check && npm run build`（既有 echarts/naive chunk 告警可忽略）。跑 verify 前先按**精确 PID** 释放 :8080：`PID=$(netstat -ano | grep ":8080" | grep LISTENING | head -1 | awk '{print $NF}'); [ -n "$PID" ] && taskkill //PID $PID //F`。**禁止广杀 java/所有 java 进程**（只杀那个 :8080 PID 或自己起的孤儿 JVM）。
- **迁移编号（多 WS 抢号协调）**：本段原始排期时库头为 **V26**；后续已按磁盘实况推进至 V33。WS-14 首轮正式复核确认共享保留参数在 fresh schema 缺失，因此整改使用下一版 **V34** 种入 `cleanup.backup.retentionDays`；不改任何已应用迁移。测试专用种子仍在 `db/testseed`（`R__` 可重复迁移），demo 数据在 `db/demo`（非 Flyway，由 `DemoDataInitializer` 门禁加载）。
- **verify 门禁必须串行**：多个 WS **排期/开发可并行，但 `mvn verify` 同一时刻只能跑一个**——共享同一 dev 库、:8080 jar 锁与 MinIO/Redis，两个 verify 并发必互相打挂。跑前确认没有别的 verify 在跑（`tasklist //FI "IMAGENAME eq java.exe"` 看是否有 surefire/failsafe JVM）。
- **共享 dev 库**（docker 容器）：`tcp-mysql`（root/root123，db `teacher_cert`）、`tcp-redis`、`tcp-minio`（minioadmin/minioadmin123，bucket `teacher-cert`）。`mvn verify` 有两类已知残留：①把 `test_%` 账号翻成 `must_change_pwd=1`；②IT 的 `readyLogin` 首登改密流转会把种子账号密码从 `ChangeMe123!` 改成 `Changed123!`（手测口令"漂移"的根源，WS-1 有根治 stretch 项）。IT 若报 401 或手测登不上，先复位再跑：`docker exec tcp-mysql mysql -uroot -proot123 teacher_cert -e "UPDATE sys_user SET must_change_pwd=0 WHERE username LIKE 'test_%';"`（密码漂移可参照桌面 `测试账号.txt` 的哈希复位法）。
- **⚠️ 关键陷阱（审计 #2 的直接成因）**：Phase 53 的 demo 数据（`platform.demo.enabled=true` 时装载）若**驻留**共享库，会污染**按全局计数断言**的 IT——审计里 `Phase2SecurityIT`（学院A clerk 学生 1→2）、`Phase7VideoReviewIT`（reviewerA 任务 3→6）就是被 demo 学生/视频任务撑翻。**WS-1 就是修这个**（让 IT 只按自身唯一 fixture 计数）。**WS-1 落地前**：跑 verify 先清 demo 行（demo 走开关随时可重载）；**WS-1 落地后**：demo 数据可**常驻**共享库且 verify 仍绿——这正是目标状态（一边带 demo 手测、一边门禁可信）。CI 用全新 `mysql:8.0` service，本就干净。
- **应用 dev 免 `JWT_SECRET` 启动**（Phase 45 dev 默认）。profile：dev=`migration+testseed`、prod=`migration only`、demo 追加 `platform.demo.enabled`。
- **诚实**：复杂/推迟必须诚实标注、不伪造验收。安全修复带**复现→阻断**活体证据；可靠性/性能带 **IT + 代码边界**证据。每个 WS 写 `DEVLOG.md`（倒序）+ 在 `docs/launch-readiness-plan.md` §11 追加条目。

---

## §1 分工总览（记得分工：复杂/关键 → opus 4.8；繁琐/重复/配置 → codex）

| WS | 主题 | 归属 | 优先级 |
|----|------|------|--------|
| WS-1 | 恢复 `mvn verify` 绿（IT 计数隔离） | **Opus 4.8** | P0 |
| WS-2 | 凭据硬化（prod admin bootstrap + STAFF/学生初始口令） | **Opus 4.8** | P0 |
| WS-3 | **MinIO 预签名**（独立文件服务器直传/直下）＝需求① | **Opus 4.8**（架构/后端）+ **codex**（配置/前端接线） | P1 |
| WS-4 | **前端全面 UI 优化**（设计基线 + 列表按钮/对齐 + 表单/抽屉 + 布局导航 + 反馈状态 + 响应式）＝需求② | **Opus 4.8**（设计基线/规范/样板）+ **codex**（跨视图铺开） | P1 |
| WS-5 | 传输与会话安全（**TLS/HSTS 全链路**＝launch-readiness P0-9 + CSP + refresh token HttpOnly） | **codex**（TLS/CSP 配置）+ **Opus 4.8**（HttpOnly 改造） | **P1**(TLS)/P2(令牌) |
| WS-6 | 前端测试/lint/a11y 门禁 | **codex**（CI+Vitest 脚手架）+ **Opus 4.8**（Playwright E2E） | P2 |
| WS-7 | 容器/CI 供应链硬化（非 root、digest pin、SBOM） | **codex** | P2 |
| WS-8 | 身份证号应用层加密 + HMAC 唯一键（迁移） | **Opus 4.8** | P2 |
| WS-9 | 拆分 `ExchangeServiceImpl` / `VideoReviewServiceImpl` | **Opus 4.8** | P2 |
| WS-13 | RBAC 授权天花板（防自提权守卫）＝launch-readiness P1 遗留 | **Opus 4.8** | P2 |
| WS-10 | 删默认 `active=dev` + profile fail-fast 守卫 | **codex** | P3 |
| WS-11 | health liveness/readiness 拆分 + 指标 | **Opus 4.8** | P3 |
| WS-12 | 前端 bundle 拆分（echarts/naive 懒加载 + budget） | **codex** | P3 |
| WS-14 | 运维收尾（备份桶生命周期 + `backup_record` 归档 + demo 可播视频） | **codex** | P3 |
| WS-15 | （可选）审计写异步重试——Phase 44b 推迟项，44f 根因修复后已解锁 | **Opus 4.8** | P3 |

**建议顺序**：WS-1 → WS-2 →（并行起 WS-3、WS-4；学校证书/域名一到即插入 WS-5 的 TLS 子相——它是 WS-3 公网 https 端点与 WS-5 Secure cookie 的前置）→ WS-5(令牌)/6/7/13 → WS-8/9 → WS-10/11/12/14/15。WS-1 必须最先（否则后续所有 WS 的 verify 都被 demo 污染误判，且 WS-1 落地后 demo 才能常驻）。**开发可并行，verify 门禁串行**（§0）；各自 verify 后单 commit + STOP，由主控复核合并。

---

## §2 P0 — 恢复门禁可信 + 关闭账号接管窗口

### WS-1 [Opus 4.8] 恢复 `mvn verify` 绿：IT 计数按自身 fixture 隔离（审计 #2 High）
- **成因**：`Phase2SecurityIT`（`:141-145` 断言 clerk 数据范围只 1 条）、`Phase7VideoReviewIT`（`:590-608` 断言 reviewerA total=3、第二页 1 条）用**全局计数/总数**断言，被驻留的 Phase 53 demo 学生（学院A）/视频任务撑成 2、6。
- **做法（不得弱化被测的 RBAC/数据范围语义）**：把这两个（以及全库其余按全局 count/total 断言的 IT）改为**只统计本测试用唯一前缀创建的 fixture**——例如断言"结果里属于本测试标记的记录数=N"且"不含学院B/他人"，而非"total==N"。给本测试造的数据加唯一业务前缀，`@AfterEach` 清理自身 fixture。**核心：既要对驻留 demo/他测数据健壮，又要保住原断言真正校验的越权/范围隔离含义**。
- **验收**：①在**有 demo 数据驻留**的 dev 库、②在**全新库**上各连续跑两次 `mvn -B -ntp verify`，均绿且 Phase2/Phase7 计数稳定；CI（全新 mysql service）保持绿。列出所有改动的 IT 与改法理由（证明是"按自身范围断言"而非"放宽阈值"）。**落地收益**：demo 数据从此可常驻共享库（手测与门禁共存，见 §0）。
- **Stretch（可选顺带根治，时间允许再做）**：IT 的 `readyLogin` 首登改密流转把共享种子账号密码改成 `Changed123!`+翻 `must_change_pwd`（§0 所述"口令漂移"礼仪的根源）——改为该流转**用运行期自建一次性账号**演练首登改密，或套件收尾恢复种子口令哈希；做完则手测账号口令永不漂移。
- **长期（审计的 long-term 建议，另行立项不阻塞本 WS）**：verify 改用 Testcontainers/每次独立 schema，一劳永逸摆脱共享库状态耦合。
- **文件**：`platform-boot/src/test/.../Phase2SecurityIT.java`、`Phase7VideoReviewIT.java`，及审计外自查到的同类。无迁移。

### WS-2 [Opus 4.8] 凭据硬化（审计 #1 High + #4 Medium；= 之前推迟的 P1-8）
- **①prod bootstrap admin 不再是"公开固定口令可登录"**：生产迁移不创建可直接登录的固定口令 admin；改为**要求 `ADMIN_INITIAL_PASSWORD_HASH`/一次性 bootstrap secret，缺失则 prod 启动失败**（fail-fast，配合已有 JWT fail-fast 风格）。`admin` 仅在测试/dev（`db/testseed`）保留已知口令。
- **②STAFF 创建/重置口令**：`SecurityAdminServiceImpl` 的 `initial-password`（`:65-66,99-110,138-148`）在 prod **禁止默认值、强制显式注入**；或改为**一次性随机初始口令**，由管理员受控发放（不落日志）。
- **③学生自动开户**：`StudentServiceImpl.initialPassword`（`:388-397` 取证件号后六位）改为**默认关闭 `student.autoCreateAccount`**，或**随机一次性激活凭证**（不再从 PII 派生）；`must_change_pwd=1` 保留。
- **④文档**：`README.md:62` 停止公开可用于生产的初始口令；测试口令与生产 bootstrap 明确分离。
- **验收（活体）**：prod profile 未配 bootstrap secret → 启动失败；新建/重置 STAFF 后**不能**用公开默认口令登录；新导入学生**不能**用证件号后六位登录；测试/dev 账号仍可登录（IT 全绿）。带复现→阻断证据。
- **依赖**：需与 WS-10（删默认 dev profile）协同——两者共同保证"误直启 jar 不会落到带默认口令的 dev"。

---

## §3 P1 — 两条新需求

### WS-3 [Opus 4.8 架构/后端 + codex 配置/前端] MinIO 预签名（独立文件服务器）＝需求①
> 目标：MinIO 作为**独立文件服务器**，浏览器**直接**与 MinIO 传输字节（上传/下载/视频播放**不经应用服务器**），应用只签发**短时效预签名 URL**并做最终定稿/落库/鉴权。这完成之前推迟的"P1-2 阶段2 直传"。

- **[Opus 4.8] C1 预签名上传（分片直传）**：`init`（后端用 AWS S3 `S3Presigner` 指向 MinIO，签发 **multipart UploadPart 预签名 URL 列表**）→ 浏览器并发直传 MinIO → `complete`（回传 `[partNumber, ETag]`，后端 `CompleteMultipartUpload` 定稿 + 落 `file_object`/`video_*` + 触发既有校验）。改造 `platform-file` 的 `FileService`/`VideoReviewServiceImpl` 上传路径（替换现有"字节过应用"的分片/合并逻辑，保留 Phase 49 的合并语义作为回退/兜底）。预签名**短时效 + 限型限长 + 限 bucket/key 前缀**。
- **[Opus 4.8] C2 预签名下载/预览/播放**：材料预览、附件下载、证书文件、视频播放改为**后端签发预签名 GET → 浏览器直取 MinIO**（替换现在经应用代理/`presignedGet` 之外仍过应用的路径）。保持**鉴权与审计边界**：签发前校验数据范围/权限（尤其敏感文件），预签名 TTL 短。
- **[codex] C3 独立文件服务器的"内外双端点"配置**：应用访问 MinIO 用**内部 endpoint**（`minio.endpoint`），但预签名 URL 必须嵌入**浏览器可达的公网/反代 endpoint**（新增 `minio.public-endpoint`），签名要用 public host 生成。补 `application-{dev,prod}.yml`、`.env.example`、`docker-compose.yml`、`nginx`（如经反代）、`MinioProperties`。CORS：MinIO bucket 需允许前端源直传（PUT/GET + 必要头；dev 放行 `http://localhost:5173`）。**⚠️ mixed-content**：站点一旦上 HTTPS（WS-5 TLS），`public-endpoint` 必须同为 **https**（http 预签名 URL 会被浏览器拦截直传/直下）——与 WS-5 TLS 落地顺序协调，预签名走同一反代/证书最省事。
- **[codex] C4 前端直传/直下接线**：`frontend` 上传组件改走 init→预签名 PUT、下载/预览/播放改用预签名 GET。**并入 Phase 49 诚实推迟的两项**：分片**并发上传 4–6** + `crypto.subtle` **Worker 哈希**（指纹/秒传/断点续传语义沿用：init 前指纹查重、进度=已完成分片集）。
- **验收**：活体走一遍——学生传材料、评审看/播视频、导出下载：字节**不经应用**（抓包/日志证明请求打到 MinIO public endpoint）；预签名过期后 URL 失效；无敏感权限用户签不出敏感文件 URL。`Phase5/6/7/10` 相关 IT 绿（上传/合并/播放/导出契约不破）。可能需迁移（如加直传会话/`file_object` 状态列——编号按 §0 规则取当时磁盘 max+1）。
- **诚实提示**：这是**跨前后端 + 配置 + 安全**的大改，务必分小步（先下载直下、再上传直传），每步 verify + 活体。MinIO CORS/公网可达是常见坑，C3 先打通。

### WS-4 [Opus 4.8 设计基线/规范/样板 + codex 跨视图铺开] 前端全面 UI 优化＝需求②
> 范围：**全站**，不止列表。现状盘点：`frontend/src` 共 ~59 个 `.vue`（15 个页面级 View + 20+ 个 Drawer/Modal + 13 个共享组件 `DataPanel/FilterBar/PageContainer/DetailPanel/StatCard/StatusTag/EmptyState/TableSkeleton/ChartBox/ReviewDialog/...` + `MainLayout` + `theme/global.css`）。共享组件已相当集中——**先改基线与共享组件，再铺开各视图**，事半功倍。
> **硬约束（用户指定）：UI 必须用 Naive UI 实现。** 全部优化在 Naive UI 组件体系内完成（`n-*` 组件 + `n-config-provider` 的 `themeOverrides` 主题变量）；**禁止引入第二 UI 库**（Element/Ant/Vuetify/Tailwind 组件库等）；自定义 CSS 仅限 design-token 层（间距/布局容器）微调，视觉样式（色/圆角/字号/密度）一律走 Naive UI 主题变量而非手写覆盖 `n-*` 内部类名（避免升级即碎）。缺组件时优先用 Naive UI 原语组合，而非造轮子。
> **设计工具链与浏览器验证（2026-07-12 主控已装配，WS-4 各子相必须使用）**：① **frontend-design skill**（`frontend-design@claude-plugins-official`，user 级插件已装）——opus 在 D0/D1/D4 做设计决策时**必须调用**（brainstorm→token 体系→自我批判流程，防"AI 默认脸"）；② **naive-ui-skills**（`~/.claude/skills/naive-ui-skills/`，含全组件 API + design-color/layout/typography/border/theming）——组件用法与主题变量的权威参考（codex 直接读磁盘路径）；③ **OpenDesign 桌面端**（`D:\Open Design\`，v0.14.1）——设计参考源：`resources/open-design/craft/*.md`（anti-ai-slop/color/laws-of-ux/accessibility-baseline/state-coverage/form-validation/animation-discipline，D1/D5/D6 逐条对照）、`design-systems/`（成熟设计系统 token 对标）、`skills/`（color-expert/design-brief 等方法论）；GUI 出图/评审归主控，agent 只读其资源，**产出仍以 Naive UI themeOverrides 落地、不引入其运行时**；④ **Playwright MCP**（user 级已装并 Connected，chromium 已预热）——**浏览器实渲染验证**。
> **跨断点自检协议（验收硬门槛，防"布局在移动端悄悄崩掉"）**：每个改动视图在 **375×812 / 768×1024 / 1024×768 / 1280×800 / 1920×1080** 五断点实渲染，逐断点核查：无水平滚动（`scrollWidth≤clientWidth`）、无元素重叠/关键按钮被截断、工具条/筛选条正确换行、表格窄屏走容器内横向滚动而非撑破布局、Drawer 宽度自适应 ≤ viewport、console 无新增错误。**D0 一次性交付自检工具 `frontend/scripts/ui-shots.mjs`**（playwright：各角色 API 登录〔captcha SVG 解析照 `Phase2SecurityIT.captchaCode`〕→ 存 storageState → 按路由清单×断点矩阵截图 + 横向滚动检测 + console 收集，产物入 `docs/ui-audit/<子相>/`），D2–D6 改前/改后各跑一遍逐项勾销；playwright devDependency 与该脚本供 WS-6 复用。
> 执行顺序：D0→D1（Opus 定基线）→ D2–D6（codex 按样板铺开，每个子相独立可验收）。每个子相单独分支+commit，避免一个巨型 UI diff 无法复核。

- **[Opus 4.8] D0 全站 UI 走查 + 问题清单**：以各角色（用 demo 数据，`--platform.demo.enabled=true` 启动）逐页走查截图，产出问题清单（按"多余按钮 / 对齐 / 间距 / 状态缺失 / 布局 / 文案"分类，标注视图与截图）。**"多余按钮"判定清单在此阶段交主控/用户确认**后再铺开——避免误删唯一入口。产物：`docs/ui-optimization-spec.md`（走查结论 + 下述规范 + 逐视图整改清单，供 codex 逐项执行）。**走查前置/勿误报**：①demo 样例视频当前是"合法可下载占位、非可播 MP4"（Phase 53 诚实标注，生成环境无 ffmpeg）——本机若有 ffmpeg 先重生成 1–2s 真实 H.264 短片替换 `db/demo/sample-video.mp4`（见 WS-14），否则走查视频以"可下载"为准、播放器打不开**不是** UI bug；②`test_cert_issuer` 无有效角色（`CERT_ISSUER` 已于 V20 退役）——该账号登录后近空菜单属预期，勿当 UI 缺陷记录，看证书功能用 admin/test_academic_admin。
- **[Opus 4.8] D1 设计基线（design tokens + 共享组件收口）**：
  - `theme/global.css` + Naive UI `themeOverrides` 统一 **spacing 刻度（4/8/12/16/24）、圆角、字号层级、主色/语义色**；消灭视图内散落的魔法 margin/padding。
  - 收口共享组件 API：`PageContainer`（统一页头：标题/说明/主操作位）、`FilterBar`（筛选项布局、基线对齐、展开/收起）、`DataPanel`（表格密度、操作列宽/对齐、分页条位置）、`StatCard/StatusTag/EmptyState/TableSkeleton`（尺寸与语义色统一）。视图层只消费规范化后的组件，不各自造样式。
  - 挑 **2 个代表视图**（1 个列表管理页 + 1 个含 Drawer 的复杂页）改成样板并截图。
- **[codex] D2 列表/表格规范铺开**（原始诉求核心）：按样板铺开到全部列表视图——**删多余按钮**（同一动作只留一处；行内操作 vs 顶部批量分区；无权限按钮隐藏而非灰置——配合 `v-perm`）、**操作列统一**（宽度/对齐/按钮尺寸密度一致）、**元素对齐**（工具条基线、列对齐：文本左对齐/数字右对齐/状态居中、筛选条与表格间距）。逐视图核对入口不丢（对照权限矩阵）。
- **[codex] D3 表单/抽屉/弹窗统一**（20+ 个 Drawer/Modal）：标签宽度与放置（统一 label-width/placement）、必填标记与校验提示一致、按钮次序与位置统一（主按钮右置、取消左侧、`:loading` 防重复提交已有——保持）、抽屉宽度分级（窄/中/宽三档）、Modal vs Drawer 使用场景统一。
- **[Opus 4.8] D4 布局/导航/门面**：`MainLayout`（侧栏分组与图标一致性、面包屑/页头层级、内容区最大宽与留白）、`DashboardView`（卡片栅格对齐、图表容器统一 `ChartBox`）、`LoginView`（视觉打磨）、`NotFound`/错误页统一。涉及信息架构判断，归 Opus。
- **[codex] D5 反馈状态一致性**：加载（统一 `TableSkeleton`/按钮 loading）、空态（统一 `EmptyState`，含引导动作）、错误提示（统一 message/notification 用法与文案语气）、危险操作二次确认样式统一（`ReviewDialog`/`n-popconfirm` 择一规范）。
- **[codex] D6 响应式 + 基础可访问性**：窄屏（≤1280/笔记本）下工具条换行、表格横向滚动、抽屉宽度自适应；焦点可见、按钮可键盘触达、对比度过基线——与 WS-6 的 axe 门禁衔接。
- **验收（每个子相独立）**：`npm run type-check` 干净 + `npm run build` 成功；**改前/改后截图对比**（按 D0 问题清单逐项勾销）；**跨断点自检硬门槛**——每个改动视图按上方五断点协议跑 `ui-shots.mjs` 改前/改后，无水平滚动/无重叠/console 无新错（任一断点崩=不过）；关键动作入口不丢失（对照权限矩阵，抽 3 角色活体点查）。D2–D6 完成后由 Opus 做一次**全站终审走查**（含全断点复扫）对照 D0 清单收尾。
- **注**：与审计 #6/#11 协同——铺开后用 WS-6 的 Playwright 冒烟兜住 RBAC 入口；D4 动 Dashboard 时顺手为 WS-12（echarts 懒加载）留好路由级分割点。与 WS-3 并行安全（WS-3 动上传/下载逻辑层，WS-4 动展示层；仅 `UploadPanel/UploadVideoDrawer` 两文件可能交叉——**该两文件由 WS-3 先行，WS-4 D3 最后再碰**）。

---

## §4 P2 — 审计 Medium

### WS-5 [codex TLS/CSP + Opus 4.8 HttpOnly] 传输与会话安全（launch-readiness P0-9 + 审计 #3）
- **[codex] T1 TLS/HSTS 全链路（P1，launch-readiness 开放 P0-9——审计 11 条未单列但经核实仍开放）**：`frontend/nginx.conf` 中 443/HSTS 配置**已有注释模板**（`:6-17`，证书路径标注"运维提供"）——启用它：443 server block（证书走卷挂载 + env 路径）、HTTP→HTTPS 301、HSTS；`docker-compose.yml` 发布 443 + 证书卷；`.env.example`/部署文档写清证书来源（学校签发或 Let's Encrypt）。**依赖学校提供域名/证书**——未到位前先用自签证书在内网把整条路径演练通、文档标注"待正式证书替换"。**与 WS-3 联动**：预签名 `public-endpoint` 必须同 scheme（https），建议 MinIO 公网面走同一反代/证书（见 WS-3 C3 mixed-content 注意）。当前 :80 明文传输登录密码/JWT/身份证 PII，面向数千学生的公网服务上线前必须关掉。
  - ✅ **2026-08-11 DYNAMIC_CLOSED / INDEPENDENT_INCREMENTAL_PASS**：fingerprint `9e33d20f...a83a` 上生产 backend 无宿主端口、MinIO API/console 仅回环；443/18443 经官方模板渲染且非默认跳转真实到达 HTTPS 200；证书运行手册可执行。原 2 Medium / 1 Low 全部关闭，0 open finding。
- **[codex] T2 CSP 基线（审计 #3 快赢）**：`frontend/nginx.conf` 增严格 CSP，并缩短 access token TTL。
  - ✅ **2026-08-11 SCOPED_DYNAMIC_PASS**：`default-src/script-src/base-uri 'self'`，脚本不放行 `unsafe-inline/unsafe-eval`；现有材料/免考 PDF 真实使用同源 `<object>/<iframe>`，因此 `object-src/frame-src` 保持 `'self'`。验证码保留 `img-src data:`，本地视频探测保留 `media-src blob:`，Worker 保留同源，Naive UI 动态样式只在 `style-src` 保留 `'unsafe-inline'`。精确 `MINIO_PUBLIC_ENDPOINT` 只进入 `connect-src`；默认 access TTL 为 900 秒。正式 WS-5 阶段复核已通过 T2，不随 T1 三项整改重开。
- **[Opus 4.8] T3 refresh token HttpOnly（审计 #3 长期）**：refresh token 迁 **HttpOnly+Secure+SameSite cookie 或 BFF session**（Secure 依赖 T1 的 HTTPS），前端只在内存持短期 access token（`stores/user.ts`、`api/request.ts`、后端 refresh 端点）。契约敏感、与登录/刷新/401 重试流耦合，务必配 WS-6 的 E2E 回归。
  - ✅ **2026-08-11 SCOPED_DYNAMIC_PASS**：登录/刷新 JSON 不返回 refresh token，后端写入 host-only、HttpOnly、prod Secure、SameSite=Strict、Path=`/api/auth/refresh` 的 Cookie；刷新只读 Cookie且不接收公开请求体，logout/改密清除同属性 Cookie。前端 access 仅驻 Pinia 内存，保留页面重载恢复、401 单飞刷新和跨标签页失效。正式 WS-5 阶段复核的真实 Phase 2 4/4 与浏览器门禁已通过；不新增 refresh 一次性消费状态、jti 或设备级会话架构。
- **阶段状态/验收**：T1 `DYNAMIC_CLOSED`，T2/T3 `SCOPED_DYNAMIC_PASS`，WS-5 为 `INDEPENDENT_SCOPED_PASS（0 open finding）`。该 scoped PASS 不授权项目发布，下一队列转 WS-6。

### WS-6 [codex 脚手架 + Opus 4.8 E2E] 前端测试门禁（审计 #6）
- **[codex]**：CI 前端 job 保留 lint/type-check/既有合同/build，并新增 **Vitest** 覆盖 user store、Axios 401 单飞刷新、router 权限与 `v-perm`；bundle budget 继续并入 WS-12。
- **[codex]**：新增 **Playwright** 产品冒烟：登录与 401 刷新、首登改密、RBAC/脱敏、导入预校验、视频评分；在登录、改密弹窗和代表业务稳定态接入 axe `critical/serious` 基线。CI 失败阻断。
- **验收**：CI 跑 lint + app/test-config 双 type-check + unit + 既有合同 + build + 5 条 Chromium E2E；失败保留 trace/screenshot artifact。
- **当前状态（2026-08-11）**：第二轮候选 `f5d63378...8607` 已取得 `INDEPENDENT_INCREMENTAL_PASS（0 Critical / 0 High / 0 Medium / 0 Low）`；multipart 大小写 Medium 正式关闭，两个 Low 不重开。WS-6 为 `INDEPENDENT_SCOPED_PASS`。E2E 仍只证明前端产品行为，不冒充真实后端/数据库联调或项目发布 GO。

### WS-7 [codex] 容器/CI 供应链硬化（审计 #8）
- runtime 镜像建**非 root 用户**（后端 `Dockerfile`、前端 `frontend/Dockerfile`）；base image 与 GitHub Actions **pin 到 digest/SHA**；CI **先 `mvn verify`/前端 test 通过再 build 镜像**；生成 **SBOM**（接 Trivy/OSV 可作后续）。
- **验收**：`docker run` 后进程 UID≠0；digest bump 需显式 PR；CI 顺序为"验证→构建"。
- **当前状态（2026-08-12）**：最终 fingerprint `d5ea9863...9e24` 已取得
  `INDEPENDENT_STAGE_PASS（0 Critical / 0 High / 0 Medium / 1 Low）`；Hosted run `31507732334` 的双 SPDX、
  镜像身份、校验和与 artifact 已独立核验闭环。唯一 Low 是临时 evidence remote，已在复核后清除。WS-7 只放行
  WS-8，依赖/镜像扫描、provenance/签名和制品平台仍未扩入本阶段，且不构成项目 GO。

### WS-8 [Opus 4.8] 身份证号加密 + HMAC 唯一键（审计 #5；本分支迁移固定 V33）
- `student` / `certificate` 的 `id_card_no` 应用层加密存储；唯一键从"明文生成列"改为 **`HMAC-SHA256(id_card_no, pepper)` 应用写入列**（V33 + 存量回填），并清理 Exchange 预览、回滚快照和证件号错误值中的可读明文。对外明文仅允许 `plainIdCard` 与上位规格已冻结、受权限和审计保护的 `exchange:export:sensitive`；普通投影/普通导出继续脱敏。加密 key 与 HMAC pepper 均为必配 secret，prod 缺失或仍为示例值时 fail-fast。
- **验收**：相同证件号仍触发唯一约束；普通列表只返回脱敏；无敏感权限拿不到明文；备份抽样不出现可读证件号。**存量迁移风险高**，先在库快照上演练回填；注意与导入/导出（Exchange 明文列）与 V24 生成列的交互。
- **当前状态（2026-08-20）**：R6 fingerprint `7e077df1...f5c74b` 已关闭 R5 的 2 Medium / 1 Low；独立功能增量
  报告确认 0 open finding，真实依赖六套 49/49 与 Phase 39 + Phase 10 29/29 均通过。用户明确以功能完整性作为
  后续开发门槛，Hosted/供应链证据不再阻塞 WS-9，因此 WS-8 记为 `INDEPENDENT_FUNCTIONAL_PASS` 并领取 WS-9。
  该口径只放行继续开发，不授权 merge、deploy、cutover 或项目 GO；项目继续 `CHANGES_REQUESTED / NO-GO`。

### WS-9 [Opus 4.8] 拆分巨型服务类（审计 #7）
- `ExchangeServiceImpl`（~1547 行）抽 `ExchangeImportValidator`/`ExchangeExportRowBuilder`/`ExchangeRollbackService`/字典下拉 helper；`VideoReviewServiceImpl`（~1420 行）抽 `VideoUploadComposer`/`VideoReviewSettlement`/VO 转换。**先抽无状态纯函数 helper**，controller 依赖更窄接口。
- **验收**：拆分前后 `Phase7/Phase10`、导出行字段快照、视频结算 IT 全绿；新 helper 补 focused 单测。**与 WS-3 有交叉**（都动 Video/Exchange 上传导出）——**WS-3 先行**，WS-9 在其之后拆（届时 WS-3 已把直传逻辑挪出，`VideoReviewServiceImpl` 上传段自然瘦身），避免双向冲突。
- **当前状态（2026-08-20）**：七个目标职责等价抽取成立；fingerprint `df1f1950...b2f45` 的正式增量复核确认 Surefire 400/400、Phase 7/10/14/39 87/87，原唯一 Medium `WS9-INT-M1` CLOSED。WS-9 为 `[x] INDEPENDENT_INCREMENTAL_PASS（0 open finding）`，现领取 WS-11。

### WS-13 [Opus 4.8] RBAC 授权天花板——防自提权守卫（launch-readiness P1 遗留，审计未单列、经代码核实仍无守卫）
- **现状**：`SystemSecurityController:51-152` 全部管理写只有 `@PreAuthorize`、无范围/层级约束；`assignRolePermissions`/`assignUserRoles` 接受**任意** permissionId/roleId → 持 `system:role:manage` 者可给自己/任何人授满 SYS_ADMIN 等价权、可重置更高权用户的密码。当前种子里仅 SYS_ADMIN 持这些权 → **潜伏**，但属设计级授权缺口（一旦给学院级自定义角色发了这权即刻可利用）。
- **做法（最小安全语义，边界先与用户确认）**：加"授权天花板"守卫——①授出的权限/角色集必须 ⊆ **授权者自身有效权限集**（不可越己授予）；②不可编辑/改密/停用**权限严格高于自己**的用户；③（可选）角色定义管理限 SYSTEM 范围持有者。
- **验收（复现→阻断）**：构造持 `system:role:manage` 的低权账号→尝试给自己授 SYS_ADMIN / 重置 admin 密码 → 业务拒绝（IT 证明）；既有正常授权流不受影响（`Phase2SecurityIT` 等全绿）。

---

## §5 P3 — 审计 Low / Quick wins

### WS-10 [codex] 删默认 `active=dev` + profile 守卫（审计 #9）
- 删 `application.yml` 的 `spring.profiles.active=dev`；本地/dev 脚本显式传 `SPRING_PROFILES_ACTIVE=dev`；加启动守卫：非 dev 环境若检测到 `db/testseed`/dev 凭证/默认 secret 直接失败。**与 WS-2 协同**。
- **验收**：无 profile 启动失败并提示；dev 脚本仍可启；prod 只加载 `db/migration`。**注意**：会影响"直接 `java -jar` 免配启动"的便利（当前 dev 默认给了 JWT dev 密钥）——需**同步更新**：README 启动说明 + 桌面 `测试账号.txt` 里的重启命令（改为带 `SPRING_PROFILES_ACTIVE=dev`）+ IT 是否依赖默认 profile（`@SpringBootTest` 未显式设 profile，删默认后测试将无 profile——需在测试配置里显式激活 dev，这是本 WS 最大的隐藏坑，先查清再动手）。

### WS-11 [Opus 4.8] health readiness + 指标（审计 #10）
- `/api/health` 拆 liveness（存活）与 readiness（探 MySQL/Redis/MinIO 连通 + 关键迁移状态）；接 Actuator/Micrometer/Prometheus 暴露 HTTP 时延/错误率/DB pool/Redis/MinIO/调度结果指标。
- **验收**：Redis/MinIO 不可达时 liveness 仍 UP、readiness DOWN；Prometheus 有关键指标。
- **当前状态（2026-08-21）**：`[x] INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/0L）`。R2 正式独立增量报告绑定 fingerprint `85606aed...9161f`（报告 SHA-256 `38c9896d...9d50a`），确认首轮 Prometheus 机器身份、readiness 总预算和后台作业指标三项 Medium 全部 CLOSED；Surefire 411/411、Phase 14 + WS-11 Failsafe 18/18。该 PASS 只关闭 WS-11 并放行 WS-12，不构成项目发布 GO。

### WS-12 [codex] 前端 bundle 拆分（审计 #11）
- 统计/图表页 route-level 懒加载；ECharts 改**按需注册**图表/组件；naive 按需；设 bundle budget（超预算 CI 失败）。
- **验收**：naive/echarts 不进登录与普通管理首屏关键路径；CI 超预算失败。**顺序**：放 WS-4 之后（D1 的 `themeOverrides`/组件收口会影响 naive 引入面，避免两头改）。
- **当前状态（2026-08-21）**：整改 fingerprint `1c8b8821...0ae7b` 已取得正式 `INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/0L）`；首轮唯一生产/预算 gzip 等级分叉 Medium CLOSED，放行 WS-14。该 PASS 不等于项目 GO。

### WS-14 [codex] 运维收尾（Phase 41.2 / 53 遗留小项打包）
- **备份产物保留**：MinIO `db-backup/` 前缀加生命周期规则（保留 N 天，仿 `FileMaintenanceService.ensureAbortIncompleteMultipartLifecycle` 的幂等确保模式）；`backup_record` 表纳入 `RetentionCleanupService`（照 Phase 47 模式：sys_param 化保留期 + 分批物理删；只删记录行，产物由生命周期管）。
- **demo 可播视频**：本机若有 `ffmpeg`（先 `ffmpeg -version` 探测），生成 1–2s 真实 H.264+AAC 短片替换 `platform-boot/src/main/resources/db/demo/sample-video.mp4`（更新 `scripts/gen-demo-samples.py` 注释说明来源）；无 ffmpeg 则明确记录跳过。**为 WS-4 D0 走查的前置**（评审角色要能真播放）。
- **验收**：IT 证 `backup_record` prune 逻辑（照 `Phase47CleanupIT` 模式直接调服务方法）；生命周期规则幂等在；（若做）demo 视频浏览器实测可播。
- **当前状态（2026-08-21）**：`[x] INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/1L）`。
  R2 fingerprint `4a167463...9cb80` 的正式报告确认首轮两项 Medium CLOSED：托管规则包含
  `NoncurrentVersionExpiration(1)` 且纳入幂等匹配；V34 提供 editable int 参数种子并只接受正整数，对象和记录侧
  共同读取 45 天。唯一 Low 是测试统计混入历史 XML，正确口径为 Surefire 66 suites / 421 tests、focused 27/27，
  已勘误且不阻断。隔离 Phase00/Phase47 IT 留稳定发布前证据；该 PASS 不等于项目 GO。

### WS-15 [Opus 4.8，可选] 审计写异步重试（Phase 44b 诚实推迟项——44f 根因修复后已解锁）
- **背景**：Phase 44b 曾完整实现 `@Async` 审计落库（请求线程捕获 ThreadLocal→异步写 + dev/test 用 `SyncTaskExecutor` 保 IT 确定性），但消融显示 `@EnableAsync` 后 Phase5/6 **通知路径**间歇 `Lock wait timeout` 而诚实回退。**Phase 44f 已查明并根治了该路径的根因**（`Db.saveBatch` 另开 SqlSession 与外层事务自锁互等）——当时"未查明的干扰源"大概率就是它，重试有了绿灯基础。
- **做法**：重提 44b 原方案；**消融复验**为硬门槛——`@EnableAsync` 开启后连续 **3 次**全量 verify 零锁等待才可合并；仍复现则再次诚实回退（现状同步写可接受，本项纯收益优化）。
- **收益**：每个 `@AuditLog` 写端点少一次同步阻塞落库。
- **当前处置（2026-08-21）**：暂不实施。现有同步审计可接受，本项是纯收益优化，不作为功能完整性复核前置。

---

## §6 与既有推迟项的对应关系（避免重复/遗漏）
**本计划收编的历史推迟/遗留项：**
- **需求① 预签名 = 之前推迟的 P1-2 阶段2**（Phase 49 已做阶段1"服务端 composeObject 快路径"，WS-3 再进一步到"字节不经应用"）；Phase 49 顺带推迟的**分片并发 + Worker 哈希**并入 WS-3 C4。
- **审计 #1/#4 凭据 = 之前推迟的 P1-8**（WS-2 一次做掉 admin bootstrap + STAFF + 学生三处）。
- **WS-5 T1 TLS = launch-readiness P0-9 已 scoped 动态关闭**（fingerprint `9e33d20f...a83a` 独立增量 PASS；正式域名/受信证书仍属于授权发布环境核验，不等于项目 GO）。
- **WS-13 = launch-readiness P1「RBAC 管理写无授权天花板（自提权）」**（审计未单列，经代码核实仍无守卫）。
- **WS-14 = Phase 41.2 备份运维遗留（备份桶生命周期/backup_record 归档）+ Phase 53 demo 视频占位**。
- **WS-15 = Phase 44b 诚实推迟的审计写异步**（Phase 44f 根因修复后解锁，消融复验为硬门槛）。

**明确不做/勿重复立项：**
- **DataScope 授权缓存**：审计未列、之前**刻意推迟**（陈旧授权=越权），本计划**继续不做**，除非另有明确需求。
- **改密/重置不失效旧令牌**（launch-readiness 旧风险行）——**已由 Phase 37a `TokenRevocationService` 修复**（登出/改密/重置后旧 token 立即 401），勿重复立项；WS-5 T3 动 refresh 流时保住该语义即可。
- **RBAC 关系表 PK `id*1000` 溢出**（launch-readiness 旧 P2）——已由 P0-14 改 `ASSIGN_ID` 重建修复。
- 审计"已验证控制"（JWT fail-fast、RBAC/数据范围切面、V24 唯一约束、异常码 4xx/5xx、清理/备份调度、种子测试/生产分离）均为 Phase 37–53 成果，**不要回退**。

---
*生成于 2026-07-05；基线 commit `e4f8228`；执行方式：codex / opus 4.8 按 WS 分工，各自分支 + verify 绿 + 单 commit + STOP 待主控复核合并。*
