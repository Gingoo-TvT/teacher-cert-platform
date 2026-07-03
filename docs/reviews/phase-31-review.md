# Phase 31 复核报告 — 业务域列表页铺开（frontend-quality-plan §4-Phase31）

| 项 | 值 |
|---|---|
| 阶段 | Phase 31（12 个业务列表页统一套用 P1/P2/P3/P7/P8 + 附录 B 格式化，前端） |
| 复核人 | Claude（按 `docs/frontend-quality-plan.md` §5 + REVIEW-GATE；Opus 4.8） |
| 复核日期 | 2026-07-03 |
| 被复核提交 | `652c0f1`（feat phase31，单提交） |
| 增量基线 | `main..feature/phase31-list-rollout`（14 文件；**后端/迁移/api/router/stores 零改动**——纯视图层） |
| **判定** | **✅ PASS（一轮，0 修补）** |
| 计数 | Blocker 0 · Major 0 · Minor 2（均非阻断，记录备查） |

---

## 一、结论
Phase 31 一轮通过，**质量延续 Phase 30 的高水位**。12 页全部套用 Phase 30 结构件（FilterBar/DataPanel/EmptyState/DetailPanel/ReviewDialog）与 `format/statusLabels`，各页特有项（导出学院下拉、通知列表化、材料/免考文件列合并、证书日期格式化…）逐一落实。DEVLOG 完全符合 W9（12 页逐节 + 每页结构件/特有项/肉眼变化/自检证据）与 W2（贴 grep/diff 证据），并诚实标注 2 处规格与现状差异。**首次做了活体 3 角色 API 走查**：登录成功、全部列表接口 200、数据范围正确、零 403。

## 二、gate（§4-Phase31 + §5）核对
| 项 | 结论 | 证据 |
|---|---|---|
| 12 页结构件接入 | ✅ | 矩阵：12 页均 FilterBar+DataPanel；EmptyState 经 DataPanel 内建 slot 统一提供（故页面级计数=0 属预期）；DetailPanel/ReviewDialog 在学生/培养等接入 |
| W4 时间格式化 | ✅ | `grep date-key 未走 formatter` → 空；所有 `*Time/*At/*Date` 列走 `formatDateTime`/`formatDate` |
| W4 状态中文 | ✅ | `grep 裸 row.status render` → 空；状态列走 `StatusTag+statusLabel`（导入/导出批次用页面本地 `batchStatusLabel/strategyLabel` 中文兜底，见 Minor-1） |
| W3 术语黑名单 | ✅ | 附录 A 原命令复跑输出为空 |
| 导出页 学院下拉 | ✅ | `listColleges()`→`collegeOptions`→`n-select placeholder="全部学院"`，仍提交 `collegeId`（不改契约） |
| 通知页 列表化 | ✅ | `n-data-table`→`n-list`：未读圆点/加粗、行点击标记已读+抽屉看全文、`全部已读`保留、未读/已读 tab |
| 材料/免考 文件列 | ✅ | 文件名+`formatFileSize`合并列、佐证文件徽标、预览按钮加 `EyeOutline` |
| W9 DEVLOG 逐页 | ✅ | 12 页逐节 + 结构件/特有项/肉眼变化/自检证据；6 角色矩阵 |
| W2 证据 | ✅ | 贴 `git diff --name-only`、W4 时间列 grep 命中行、黑名单空输出 |
| 范围红线 | ✅ | diff 无 `platform-*`/`migration`/`src/api`/`src/router`/`src/stores`——纯显示层 |
| 构建 | ✅ | `vue-tsc` 无错 + `vite build ✓ built in 8.05s`（仅既有 chunk-size warning） |
| **活体 3 角色 API 走查** | ✅ | 见下表 |

### 活体走查（后端 :8080 detached + captcha 解码登录）
| 角色 | 登录 | student | training | material | cert | 批次 | 学院下拉 | 数据范围 |
|---|---|---|---|---|---|---|---|---|
| SYS_ADMIN | OK | 200/12 | 200/5 | 200/0 | 200/0 | 200/0 | 200/2 | 全校 ✓ |
| ACADEMIC_ADMIN | OK | 200/12 | 200/5 | 200/0 | 200/0 | 200/0 | 200/2 | 全校 ✓ |
| COLLEGE_CLERK | OK | 200/**9** | 200/5 | 200/0 | 200/0 | 200/0 | 200/**1** | 仅本院 ✓ |

全部 HTTP200 code=0，**零 403 / 零 5xx**；教务员学生数 9<12、学院下拉 1<2，证明数据范围过滤未因本轮改造受损。

## 三、Minor（非阻断，记录备查）
- **Minor-1 批次状态用页面本地映射而非全局 `statusLabel`**：导入/导出页 `batchStatusLabel/strategyLabel` 为局部 map，与全局 `STATUS_LABELS` 有重叠项。用户可见结果仍是中文（PREVALIDATED→预校验通过等），未违反用户视角目标；codex 的理由是避免 `FAILED` 在批次语境显示为学生语境的「不合格」——决策合理。可在 Phase 32 顺手并入全局字典（加 `FAILED_BATCH` 语境键）或保留，二者皆可。
- **Minor-2 视频「分差列红色高亮」未实现**：计划 §4-Phase31 写「分差列红色高亮保留」，codex 核查后现状**无该契约字段**，本轮不新增字段故未加。属计划项与现状不符（非回归、未删任何东西），已在 DEVLOG 诚实披露。若需要该列需后端补字段，可挪至后续。

## 四、放行
1. PROGRESS Phase 31 置 **✅ 已复核**；合并 `main`（本地私有、无远程、不 push）。
2. 栈已在跑（Phase 31 代码），供用户逐页目验：学生/培养/材料/免考/视频列表/测试/证书/签发/导入/导出/统计/通知——卡头总数、带标签筛选、时间中文、状态标签、通知列表化、导出学院下拉。
3. 生成 Phase 32 派发词（附录 D）。

> 备注：为活体走查把 `test_sys_admin/test_academic_admin/test_college_clerk` 三个账号密码归一化为 `ChangeMe123!`（mustChangePwd=0）。其余账号不变。
