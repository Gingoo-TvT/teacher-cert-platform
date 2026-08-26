# WS-12 · 前端 bundle 拆分（echarts/naive 懒加载 + budget） — 提示词（codex）

你是执行 **WS-12** 的 codex。对应 `docs/audit-remediation-plan.md` §5 WS-12（审计 #11）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§5 WS-12**；`git remote -v` 空 → `git switch -c feature/ws12-bundle-split`。
- **顺序红线**：**放 WS-4 D1 之后**（D1 的 `themeOverrides`/共享组件收口会影响 naive 引入面，避免两头改）。
- 与 WS-4 D4 留的**路由级分割点**对齐（Dashboard/统计页）。

## 任务
- **现状核实**：`frontend/vite.config.ts:36-38` 的 `manualChunks` **已把 echarts/naive 拆成独立 chunk**——但 chunk 拆分 ≠ 懒加载（路由一引用即拉进首屏）。真正要做的是下面三项：
- 统计/图表页 **route-level 懒加载**（配合 WS-4 D4 留的路由分割点）。
- ECharts 改**按需注册**：`frontend/src/components/ChartBox.vue`（`:2`）现 `import * as echarts from 'echarts'` = **全量引入** → 改 `echarts/core` + `use([...])` 只注册用到的图表/组件；naive **按需**。
- 设 **bundle budget**（超预算 **CI 失败**，与 WS-6a 的 budget 位对齐）。

## 验收
- naive/echarts **不进**登录与普通管理**首屏关键路径**（产物分析证明）。
- CI **超预算失败**。
- `npm run type-check` 干净 + `npm run build` 成功。

## 收尾（硬门槛）
1. 前端 `type-check` + `build` 绿 + 产物体积对比。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-12 条目。
3. **单 commit**（`feature/ws12-bundle-split`）→ **STOP** 交主控复核。**禁止 merge / push**。
