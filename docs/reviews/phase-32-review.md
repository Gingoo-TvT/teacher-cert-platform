# Phase 32 复核报告 — 系统域 + 表单/详情/审核体验（frontend-quality-plan §4-Phase32）

| 项 | 值 |
|---|---|
| 阶段 | Phase 32（系统域 6 页 + 抽屉 P4 + 详情 P5 + 审核 P6 + 审计状态中文，前端） |
| 复核人 | Claude（§5 + REVIEW-GATE；Opus 4.8） |
| 复核日期 | 2026-07-03 |
| 被复核提交 | `357b6be`（feat phase32，单提交） |
| 增量基线 | `main..feature/phase32-form-detail-review`（14 文件；**后端/迁移/api/router/stores 零改动**——纯视图层，仅 DataPanel 增展示型 props） |
| **判定** | **✅ PASS（一轮，0 修补）** |
| 计数 | Blocker 0 · Major 0 · Minor 1（非阻断记录备查） |

---

## 一、结论
Phase 32 一轮通过。系统域 6 页统一为主从两栏（左列表卡 + 右详情卡），全站编辑抽屉收敛为 P4（560 宽、`n-grid cols=2`、分组小标题、固定 footer），查看/编辑分离（P5），初审/复审统一 P6。**权限树由内部功能码改为可读功能名**（`system:user:manage`→显示「系统用户管理」），是 W3 的正向改进。W3 黑名单在系统域大改后复跑仍为空。活体 SYS_ADMIN 走查 10 个系统域接口全 200、零 403。

## 二、gate（§4-Phase32 + §5）核对
| 项 | 结论 | 证据 |
|---|---|---|
| 字典/学科/区划/组织 主从两栏 | ✅ | 4 页均 FilterBar+DataPanel+DetailPanel；DataPanel 增 `rowProps/maxHeight/defaultExpandAll` 透传支持选中行高亮与权限树展开（展示型，不改业务） |
| 区划/学科 无编辑抽屉 → P4 N/A | ✅（合理） | Region/Subject 为浏览/导入页（drawer/edit=0），已应用 P2/P3；无多字段编辑表单故 P4 不适用 |
| 账号权限 三 tab + 用户角色 StatusTag 组 + 抽屉 2 列 | ✅ | 用户/角色/权限 tab 卡化；用户抽屉 `n-grid :cols="2"`（651 行）；权限树显示可读名 |
| 参数审计备份 | ✅ | 审计 old/new→`statusLabel`+StatusTag（123-124）；学院下拉；学生ID 占位「学生ID（数字）」+ help「请填写数字编号…」；参数/备份抽屉 560 双列 |
| 全站抽屉 P4 | ✅ | Org 学院抽屉 `n-grid :cols="2"`（805 行）；Security 用户抽屉同；DEVLOG Cross-Page 列明学生/培养/材料/免考/证书/系统域抽屉均 560+分组+双列 |
| 查看≠编辑（P5） | ✅ | 学生/培养「查看」用 `DetailPanel` 只读抽屉，与编辑分离；证书（前置/签发/更正/作废生命周期动作）、免考（提交/换佐证/审核）无独立「查看」动作故 P5 不适用（见 Minor） |
| 审核统一（P6） | ✅ | 学生/培养/材料/免考初审/复审入口均 `ReviewDialog`（对象摘要+结论+意见），退回必填意见（Phase30 修补）继承 |
| W3 黑名单 | ✅ | 系统域重改后附录 A 原命令复跑仍空；`SecurityManageView` 无 `row.code` 直显 |
| W9/W2 DEVLOG | ✅ | 6 系统页 + Cross-Page 逐节；贴 diff/grep 证据 |
| 范围红线 | ✅ | 无 platform-*/migration/api/router/stores |
| 构建 | ✅ | `vue-tsc` 无错 + `vite build ✓ built in 7.74s` |
| 活体 SYS_ADMIN 走查 | ✅ | dict/region/subject/college/user/role/permission-tree/param/audit/backup 10 接口全 HTTP200 code=0、零 403；权限树返回含可读 `name:系统用户管理` |

## 三、Minor（非阻断，记录备查）
- **证书页无只读「查看」详情**：计划 §4-Phase32 的 P5 清单含「证书」，但证书页是生命周期动作页（前置/签发/更正/作废），无独立「查看」动作可拆；codex 将 P4 应用于证书生成/更正抽屉，P5 仅用于确有查看动作的学生/培养——工程上正确。DEVLOG 顶部「偏差：无」措辞略欠精确（未点名证书 P5 不适用），但 Cross-Page 分节对实际落地范围描述准确。如需证书只读详情卡，可作后续小改（非回归）。

## 四、放行
1. PROGRESS Phase 32 置 **✅ 已复核**；合并 `main`（本地私有、无远程、不 push）。
2. 栈在跑（Phase 32 代码），供用户目验：系统域两栏、抽屉双列、审计中文状态、权限树可读名、查看/编辑分离、审核弹窗统一。
3. 生成 Phase 33 派发词（工作台 v2 + 视频页拆分评分工作台 + 学生端卡片化——本阶段含 W6 强制拆巨石组件，复核将 `wc -l` 抽查）。
