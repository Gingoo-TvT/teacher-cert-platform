# Phase NN 复核报告（模板）

> 复制为 `phase-NN-review.md` 使用。规范见 `../REVIEW-GATE.md`。

- 阶段：Phase NN · <名称>
- 复核人：Claude
- 日期：YYYY-MM-DD
- 复核范围：`git diff <from-commit>..<to-commit> --stat` 摘要
- **判定：☐ PASS　☐ 退回(CHANGES REQUESTED)**

## 一、维度结论（D1~D11，✅通过 / ⚠️瑕疵 / ❌不通过）
| 维度 | 结论 | 证据 / 说明 |
|---|---|---|
| D1 需求符合性 | | |
| D2 验收清单（复跑 docs/phase-NN） | | |
| D3 AT 验收 | | 列 AT 编号 + 证据 |
| D4 红线合规（AGENTS §1） | | |
| D5 代码质量 | | /code-review 摘要 |
| D6 安全 | | /security-review 摘要 |
| D7 构建与运行 | | mvn / vite / 冒烟 |
| D8 测试（含反例） | | |
| D9 数据库 | | |
| D10 回归 | | 既有阶段冒烟 |
| D11 文档/进度 | | PROGRESS/DEVLOG/Swagger |

## 二、问题清单
| # | 级别 | 问题 | 证据 | 期望 | 定位(文件/类/行) |
|---|---|---|---|---|---|
| 1 | Blocker/Major/Minor | | | | |

## 三、独立复跑的反例 / 关键用例
- 用例：given / when / then → 实际结果
- ...

## 四、结论与放行
- 判定说明：……
- 若 PASS：`PROGRESS.md` Phase NN → ✅ 已复核；AT 跟踪更新：……
- 若退回：待修（Blocker/Major）清单：……；Minor backlog：……

复核人：Claude　·　日期：YYYY-MM-DD
