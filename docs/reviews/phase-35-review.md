# Phase 35 复核报告 — 前端验收缺陷修复（codex 实现 + Claude 亲自返工关键件）

| 项 | 值 |
|---|---|
| 阶段 | Phase 35（F1–F5 验收缺陷修复，前端） |
| 复核/返工 | Claude（Opus 4.8）——本轮按用户要求：发现的关键问题**由 Claude 亲自返工，不退回 codex** |
| 日期 | 2026-07-03 |
| 被复核提交 | `343c491`（fix phase35）+ Claude 返工 1 处 |
| 增量基线 | `main..feature/phase35-acceptance-fix`（35 文件；后端/迁移/api/router/stores 零改动） |
| **判定** | **✅ PASS（codex 一轮质量高 + Claude 返工 F4 图高 1 处）** |

---

## 一、结论
codex 本轮质量为历轮最佳：F1–F5 全部落地，且**关键共享件**（DataPanel 自动 scroll-x、StudentSelect 远程搜索、videoDuration 探测、ChartBox 长类目、StatCard 单位内联）均自行完成且设计正确。Claude 逐项核验，发现**唯一实质缺口**（F4：ChartBox 加大了标签 `bottom` 但容器高度未同步→长类目图绘图区被压扁），**由 Claude 亲自返工修复**。type-check+build 两轮绿；活体 F2 关键词搜索 + 端点零 403。

## 二、逐项核验
| # | codex 实现 | Claude 核验/返工 |
|---|---|---|
| **F1 列表横滚/错位** | DataPanel `effectiveScrollX = scrollX ?? inferScrollX(columns)`（按列 width\|\|minWidth\|\|120 求和，含嵌套 children）；删除 27 处硬编码 scroll-x（残留 0）；逐表补列宽 | ✅ 设计正确：内容和<容器→铺满不滚、>容器→才滚，根治"无谓横滚"。保留 |
| **F2 材料/免考卡顿** | 新建 `StudentSelect.vue`（remote+260ms 防抖+限 20+回显 selectedLabel）；材料/免考抽屉改用它；移除父级 onMounted 全量 `listStudents` | ✅ 活体 `/student?keyword=` 张→1/DEMO→10/李→1；保留 |
| **F3 上传入口/时长** | `utils/videoDuration.ts`（createObjectURL+loadedmetadata+revoke+兜底）；UploadPanel/UploadVideoDrawer 选文件自动识别时长+只读展示+失败回退手填；视频/材料/免考上传全改 `n-upload-dragger` 拖拽区 | ✅ 逻辑正确、两处一致；保留 |
| **F4 统计图 x 轴** | ChartBox：rotate 45°、labelWidth 112–180、bottom 92–156、`overflow: rotate?'break':'truncate'` | ⚠️→**Claude 返工**：bottom 增大但容器仍 320/340px→绘图区压扁。抽出 `chartLayout` 计算并派生 `resolvedHeight = max(base, top+184+bottom)`，容器随标签高度同步增高 |
| **F5 首页 StatCard/问候** | greeting 去掉 `· profile.description` 尾巴；StatCard 加 `unit` 内联到数值（"0 批"不再独占行）；Dashboard `uniqueMetricsByLabel` 去重复标签 | ✅ 三点全中；保留 |

## 三、Claude 返工明细（F4）
- `components/ChartBox.vue`：新增 `chartLayout` computed（把 rotate/labelWidth/bottom/top 计算集中，normalizeOption 改为读取它，消除重复）；新增 `resolvedHeight` computed，容器高度 = `max(传入高度, top + 184(最小绘图区) + bottom)`；模板 `:style` 高度改用 `resolvedHeight`。长类目旋转/换行撑高 `bottom` 时容器同步增高，绘图区不再被压扁；ResizeObserver 已在，容器变高自动 `chart.resize()`。type-check+build 复跑绿。

## 四、验证
- `vue-tsc` 无错（两轮）；`vite build ✓ 8.68s`（仅既有 chunk-size warning）。
- 27 处硬编码 scroll-x 残留 0；StudentSelect/videoDuration 新件到位；上传拖拽区覆盖视频/材料/免考。
- 活体：F2 关键词搜索通；SYS_ADMIN 关键列表端点零 403（前序阶段一致）。
- **视觉验收交用户**：1280/1440/1920 三宽度列表对齐/无横滚（F1）、统计图 x 轴完整（F4）、首页干净（F5）、材料/免考流畅（F2）、上传拖拽+时长自动（F3）——结构重构+视觉需用户目验确认。

## 五、放行
1. PROGRESS Phase 35 置 ✅；合并 `main`（本地私有、无远程、不 push）。
2. 重启 vite 供用户对 5 类缺陷做最终目验。
