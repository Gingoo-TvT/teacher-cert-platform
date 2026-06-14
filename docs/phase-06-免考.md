# Phase 6 · 免考信息管理（M06）

> 优先级 P0 · 依赖：Phase 5(文件)、Phase 3 · 任务：T-052~T-056 · plan §6.5 / §6.10 / §15.2-A
> 目标：多科免考申请、每科独立佐证、每科二级审核，**免考通过科目联动应考科目、不覆盖过程性结论**。**AT-07 首验。**

## 1. 范围
按学段配置可免科目、多科任选、每科独立佐证材料、依据字典+说明、每科二级审核、应考科目联动。

## 2. 数据库（V12__exemption.sql）
- `exemption_request`：student_id, assessment_year, subject(免考科目), basis(依据), remark, first_review_*, second_review_*, final_status，deleted。一名学生**多科多行**。
- `exemption_material`：exemption_request_id, file_id, file_name。每科**独立**佐证。

## 3. 业务规则
- 可免科目按学段配置（字典 `exemption_subject`，如幼儿园：综合素质（幼儿园）、保教知识与能力）。
- 每科**必须**独立上传佐证材料才能提交。
- 每科独立走状态机 A（通过/退回/不通过，退回/不通过填原因，留痕）。
- **联动**：某科免考复审通过 → 该科从 `ability_test_result` 的应考科目清单移除（Phase 8 消费）。
- **不覆盖**：免考通过 ≠ 过程性考核通过；两者状态相互独立。

## 4. 接口清单
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/exemption/subjects?segment=` | `dict:view` | 该学段可免科目 |
| POST | `/api/exemption` | `exemption:apply` | 申请（多科，每科带佐证） |
| PUT | `/api/exemption/{id}` | `exemption:apply` | 修改（通过前） |
| POST | `/api/exemption/{id}/first-review` | `exemption:firstReview` | 单科初审 |
| POST | `/api/exemption/{id}/second-review` | `exemption:secondReview` | 单科复审 |
| GET | `/api/exemption/{studentId}?year=` | `student:view` | 学生免考清单与状态 |

## 5. 前端
- 学生端：多科选择、每科佐证上传、依据填写、提交（校验每科佐证齐全）。
- 教务员/副院长：按科审核、退回原因、状态展示。

## 6. 验收清单（AT-07）
- [ ] 可选**一科或多科**免考；每科可独立上传佐证。
- [ ] 某科未上传佐证 → 该科不能提交。
- [ ] 每科**独立**二级审核，互不影响（构造：A 科通过、B 科退回、C 科不通过并存）。
- [ ] 免考复审通过的科目 → 从应考科目清单移除（与 Phase 8 联动验证）。
- [ ] 免考结果**不**改变过程性考核结论（独立）。
- [ ] 审核留痕完整。

## 7. 测试用例
- T-EX-1：选 2 科，各传佐证 → 提交成功；其中一科漏传佐证 → 拒。
- T-EX-2：A 科复审通过、B 科复审不通过 → 两科状态独立；应考清单仅移除 A。
- T-EX-3（反例）：免考通过后检查过程性考核状态 → 不变（不被覆盖）。

## 8. DoD
多科免考 + 每科佐证 + 每科二级审核 + 应考联动 + 不覆盖过程性，全部可用；AT-07 自测通过。

## 9. 风险
- "每科独立"是核心：审核、佐证、状态都以**科**为粒度，不能整单一刀切（AT-07 反例覆盖并存的不同结论）。
- 应考科目联动需与 Phase 8 约定接口/事件，避免免考通过后应考清单未刷新。
