# Phase 35 · 前端验收缺陷修复计划（codex 实现 + Claude 关键返工）

> 背景：Phase 30–34 完成结构/视觉/拆分后，用户实测提出 5 类缺陷。本轮**分工原则**：
> - **codex 承担繁琐/重复/机械的铺开**（逐页删改、逐表调宽、文案调整、拖拽上传替换等）。
> - **Claude 承担关键共享件逻辑**，且**复核时发现的问题由 Claude 亲自返工**（不再退回 codex 反复）。
> 每条缺陷下已标注 `[codex]` / `[Claude 把关/返工]`。老约束不变：纯前端、build-only、git 本地私有无 remote 不 push、单分支单提交、不自评 ✅、DEVLOG 按页分节+证据。
> 分支：`feature/phase35-acceptance-fix`。

---

## F1 · 列表横滚/错位/显示不全（最普遍）
**症状**：大部分列表显示不全、表头表体错位、只差一点点也出横向滚动条。
**根因**：27 处**硬编码 `:scroll-x`**（760–1750，见下）远大于内容区实际宽度 → 恒定横滚；且部分列只有 `width`/缺 `minWidth`，与 `scroll-x` 不匹配 → 表头/表体错位。
```
硬编码 scroll-x 位置（全部要处理）：
CertificateIssueView:226(1320) CertificateManageView:286(1750) ExchangeExportView:271(1040)
ExchangeImportView:308/321/335 ExemptionManageView:317(1520) MaterialManageView:371(1440)
StatsReportView:264/275 StudentManageView:294(1440) ConfigsPanel:157 MajorsPanel:382(1280)
DictManageView:297 RegionManageView:181 SecurityManageView:324/353/375 SubjectManageView:295/336
SystemAuditView:290/335/365 TestResultManageView:287(1460) TrainingManageView:293(1550)
GroupPanel:182 ManagePanel:258(1600)
```
**[Claude 把关/返工] 关键系统性修复**：改 `components/DataPanel.vue` —— 当未显式传 `scrollX` 时，**自动按列求和算最小表宽**（`sum(col.width || col.minWidth || 120)`）作为 `:scroll-x`。这样：内容和 < 容器 → 表格铺满不滚；> 容器 → 才横滚（真宽表）。这是消除"无谓横滚 + 错位"的根本手段，Claude 复核时若 codex 未做或做错，直接由 Claude 实现。
**[codex] 重复铺开**：
- 删除上列 27 处**硬编码 `:scroll-x` 属性**（改由 DataPanel 自动推导；对少数确实超宽且需要固定滚宽的，可保留但按真实列宽和设置，不得虚高）。
- 逐表确保**每列都有 `width`（固定：操作/状态/年度/序号等）或 `minWidth`（文本列）**；文本列统一 `ellipsis: { tooltip: true }`；操作列 `fixed:'right'` 保留。
- 非 DataPanel 的裸 `n-data-table`（如各 modal 内的小表）同法处理。
**验收**：1280/1440/1920 三种窗口宽度下，除天然超宽表（审计/权限矩阵/导入 26 列）外**不应出现横滚**；出现横滚的表**表头与表体列对齐**、内容不被截断（截断项有 tooltip）。

## F2 · 过程性材料/免考管理点击卡顿
**症状**：进入这两页会卡顿一下。
**根因**：`onMounted` 即**全量拉取学生**——`MaterialManageView:186`、`ExemptionManageView:163` 调 `listStudents()`（无分页/无关键词，返回全量），用于填充"学生"下拉；学生多时渲染大 select 造成掉帧。
**[Claude 把关/返工] 关键共享件**：新建 `components/StudentSelect.vue` —— 远程搜索下拉（输入关键词才 `listStudents({keyword})`、防抖、限 20 条、回显已选）。这是复用于多页的关键交互件，Claude 亲自定稿。
**[codex] 重复铺开**：
- 材料、免考（及其抽屉子组件 `MaterialUploadDrawer`/`ExemptionDrawer`）里的"学生"选择改用 `StudentSelect`；移除 `onMounted` 的全量 `listStudents`（selfMode 下本就只用当前学生，直接用 `currentUser.studentId`，无需拉列表）。
- 排查其它 `onMounted` 里的全量 `listStudents()`（如 video `ManagePanel`/`UploadVideoDrawer` 的 students 预载）一并改为 `StudentSelect` 远程搜索或抽屉打开时才加载。
**验收**：材料/免考首屏进入无明显掉帧；学生下拉输入关键词能远程搜。

## F3 · 上传无明显入口 + 手填视频时长（不合理）
**症状**：上传文件/视频"没有能上传的地方"；且要**自己输入视频时长（秒）**。
**根因**：视频上传抽屉 `UploadPanel:236`、`UploadVideoDrawer:134` 用 `n-input-number` 让用户**手填 durationSeconds**；上传控件是默认 `n-upload` 小按钮（`UploadPanel:237`/`UploadVideoDrawer:135`/材料 `MaterialUploadDrawer` 等），不直观。
**[Claude 把关/返工] 关键逻辑**：**视频时长自动探测**——选择视频文件后用 `HTMLVideoElement`（`URL.createObjectURL(file)` + `loadedmetadata` 读 `video.duration`）自动得到秒数，写入 `uploadForm.durationSeconds` 并**只读展示**（"时长：15:00 (900s) · 自动识别"），删除手填 `n-input-number`。失败兜底允许手填。此逻辑同时改 `UploadPanel.vue` 与 `UploadVideoDrawer.vue`，Claude 亲自实现（涉及浏览器 API 细节 + 两处一致）。
**[codex] 重复铺开**：
- 所有文件上传处（视频、材料 `MaterialUploadDrawer`、免考 `ExemptionDrawer`/`ExemptionReplaceModal`）把 `n-upload` 换成 **`n-upload` + `n-upload-dragger` 拖拽区**（明显的"点击或拖拽文件到此处上传"），保留 `:max="1"`/`accept`/`:default-upload="false"`。
- 确认**学生端有清晰上传入口**：材料 selfMode 四卡的"上传/替换"、视频 `UploadPanel` 的"上传视频/重新上传"按钮文案与可见性正确（canUploadNow 态）。
**验收**：选视频文件后时长自动出现且不可手改（识别失败才可填）；材料/免考/视频上传区为明显拖拽框；学生登录能找到并完成上传。

## F4 · 统计报表图表横坐标显示不全
**症状**：统计报表列表仍丑；图表横坐标名称显示不全。
**根因**：`components/ChartBox.vue:117` 对类目轴 `overflow:'truncate'` + `labelWidth` 86/104 → 长名（学院名/维度名）被截断。
**[Claude 把关/返工] 关键共享件**：改 `ChartBox.vue` 类目轴策略——长类目时**加大旋转角（45°）+ 足够 `grid.bottom` + 提高/取消截断宽**使名称完整显示；类目数多或名称过长时保留 `hideOverlap` 但优先"完整可读"，并保留 axis tooltip 兜底。Claude 亲自定稿（共享组件、影响全站图）。
**[codex] 重复铺开**：`StatsReportView.vue` 统计表格美化复查——列宽/对齐/`mono tabular-nums`、卡头、空态与 F1 一致；确保图表下方数据表完整可读（长名不截断）。
**验收**：统计报表各维度图 x 轴名称完整可读（或旋转后可读）；表格对齐美观。

## F5 · 系统管理员首页丑（StatCard/问候文案）
**症状**：批次数卡片"数字 0 下面还有个批"（单位"批"单独占行）、标签像重复；问候语尾巴"· 关注账号、参数审计、备份治理和基础数据维护。"多余。
**根因**：`DashboardView:78` `greetingDescription` 拼了 `profile.value.description`；`DashboardView:89` `sub: item.unit` 把单位（"批"）当副行 → 显示成 `批次数 / 0 / 批`。
**[codex] 直接改**（简单）：
- `greetingDescription` 去掉 `· ${profile.value.description}`，只留 `角色 · 日期`（如"系统管理员 · 2026-07-03"）。
- StatCard 单位不再作独立副行：**把单位并入数值**（值渲染为 `0 批` / `12 人` 等，单位小字紧跟），或直接不显示单位——二选一，保证不再"0 下面一个批"。`statCards` 里 `sub: item.unit` 相应调整（`sub` 保留给真正的说明文案，不塞单位）。
- 检查 sys_admin 首页指标是否有**重复标签**（"批次数"上面又有"批次数"）——若 metrics 有重复项或分组标题冗余，去重/去标题。
**[Claude 把关]**：若单位内联需要改 `StatCard.vue`（值+单位小字），由 Claude 定稿组件；codex 负责各处传参。
**验收**：sys_admin 首页问候只有"角色 · 日期"；指标卡无孤立单位行、无重复标签，观感干净。

---

## 复核方式（本轮特别）
1. codex 提交后，Claude：`type-check`+`build`（读输出）→ 起栈（后端 detached + vite）→ **1280/1440/1920 三宽度**逐页目验列表对齐/无谓横滚（F1）、材料免考流畅度（F2）、上传拖拽区+时长自动（F3）、统计图 x 轴（F4）、sys_admin 首页（F5）→ 活体 6 角色关键页零 403。
2. **发现的问题由 Claude 亲自返工修复并记录**（F1 的 DataPanel 自动 scroll-x、F3 的时长探测、F4 的 ChartBox、必要的 StudentSelect/StatCard 定稿），**不退回 codex**。
3. 全绿后合并 main，写 `docs/reviews/phase-35-review.md`（含三宽度目验结论 + Claude 返工清单）。

## 附：派发提示词（给 codex）
```
从最新 main 切 feature/phase35-acceptance-fix。先通读 docs/frontend-fix-plan.md。
按 F1–F5 的 [codex] 项实现（F1 删27处硬编码scroll-x+补列宽；F2 学生下拉改远程搜索去全量预载；
F3 上传换 n-upload-dragger 拖拽区+确认学生上传入口；F4 统计表美化复查；F5 首页问候去尾巴+StatCard单位不占独立行+去重复标签）。
标注 [Claude 把关/返工] 的关键共享件（DataPanel 自动 scroll-x、StudentSelect 远程组件、
视频时长自动探测、ChartBox 长类目、StatCard 单位内联）可先做初版；Claude 复核时会亲自定稿/返工，不要求你反复打磨。
纯前端不改后端/契约/迁移/stores；build-only type-check+build 绿；git 本地私有无 remote 不 push；
单分支单提交；不自评 ✅，PROGRESS 置「Phase 35 待复核」；DEVLOG 按页分节 + 每条附证据(grep/wc/命令输出)。
```
