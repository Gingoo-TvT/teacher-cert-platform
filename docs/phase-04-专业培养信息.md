# Phase 4 · 专业培养信息（M04）

> 优先级 P0 · 依赖：Phase 1、Phase 3 · 任务：T-039~T-043 · plan §5.3 / §6.3 / §6.4 / §6.5 / §15.2-A
> 目标：维护专业培养信息并落地**专业代码校验、培养目标→实习地点/学段/学科联动、任教学科标准库约束、两级审核**。**AT-04、AT-05 首验。**

## 1. 范围
二级学科代码/名称、校内专业代码/名称、学历层次、培养目标（多选一）、实习组织方式/地点、任教学段、任教学科、面试组织方式、测试结论；联动与两级审核。

## 2. 数据库（V10__training.sql）
`training_profile`：student_id, assessment_year, second_discipline_code/name, internal_major_code/name, education_level, training_goal, internship_org_mode, internship_location, teaching_segment, teaching_subject_id, interview_org_mode, ability_test_conclusion, status, locked。
- 与学生**按考核年度一对多**；唯一约束 `(student_id, assessment_year)`。

## 3. 校验与联动（精确实现）
| 项 | 规则 | 验收 |
|---|---|---|
| 专业代码 `MajorCodeValidator` | `identity_type=教育类研究生` → `second_discipline_code` 前 4 位 ∈ {0401,0451,0453}，校内专业代码可空；其它师范生 → 校内专业代码/名称须匹配试点范围(`pilot_scope_flag`) | V-08/AT-04 |
| 培养目标联动 | 选定培养目标 → 由 `training_goal_config` 带出 `default_segment`/可选学段、`default_internship_location`/可选实习地点 | AT-05 |
| 实习地点限制 | 企业仅"职业技术教育专业"、海外仅"汉语国际教育专业" | V-09 |
| 任教学段→学科 | 学科必须来自该学段 `teaching_subject` 库；幼儿园仅"幼儿园"；中职专业课仅具体学科（类别不可选） | V-10/AT-05 |
| 培养目标多选一 | 同一专业多培养目标中，学生/教务员必须选定唯一一个 | — |
| 任教学段一致 | 培养信息任教学段须与证书任教学段一致（Phase 9 复用） | — |

## 4. 接口清单
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/training/{studentId}?year=` | `student:view` | 查询 |
| POST/PUT | `/api/training` | `training:edit`/`training:confirm` | 维护/本人选定培养目标 |
| GET | `/api/training/options?goal=&segment=` | `dict:view` | 联动可选项（学段/地点/学科） |
| POST | `/api/training/{id}/submit` | `training:edit`/`confirm` | 提交待初审 |
| POST | `/api/training/{id}/first-review` | `info:firstReview` | 初审 |
| POST | `/api/training/{id}/second-review` | `info:secondReview` | 复审 |

## 5. 前端
- 联动表单：选培养目标 → 实习地点/任教学段候选刷新 → 任教学段变 → 任教学科候选刷新（`SubjectSelect`）。
- 专业代码即时校验提示（教育类研究生前缀）。
- 审核操作与状态标签（状态机 A，同 Phase 3）。

## 6. 验收清单
- [ ] 教育类研究生二级学科代码非 0401/0451/0453 开头 → 提示异常并阻止提交（AT-04）。
- [ ] 教育类研究生校内专业代码可空；其它师范生为空或不在试点范围 → 提示（条件必填）。
- [ ] 选定培养目标后实习地点/任教学段按 `training_goal_config` 联动（默认值+可选范围）。
- [ ] **任教学段变化 → 任教学科候选同步刷新**；任教学科**不可自由输入**，只能从标准库选（AT-05）。
- [ ] 幼儿园学段任教学科只能"幼儿园"；中职专业课类别节点不可选。
- [ ] 实习地点：企业仅职教专业可选、海外仅汉语国际教育可选（V-09）。
- [ ] 同一专业多培养目标时必须选定唯一一个才能提交。
- [ ] 两级审核状态流转与留痕正确。

## 7. 测试用例
- T-MAJOR-1（反例）：身份类型=教育类研究生，二级学科代码 `0301...` → 拒（AT-04）。
- T-MAJOR-2：教育类研究生校内专业代码留空 → 放行。
- T-LINK-1：培养目标=小学教师 → 任教学段候选含小学；切到幼儿园教师 → 学科候选只剩"幼儿园"。
- T-LINK-2（反例）：任教学段=初中，任教学科手填"语文ABC" → 拒（非标准库）。
- T-LOC-1（反例）：非汉语国际教育专业选"海外"实习地点 → 拒。

## 8. DoD
联动表单 + 专业代码校验 + 学科库约束 + 两级审核可用；AT-04、AT-05 自测通过。

## 9. 风险
- 联动既要前端体验也要**后端二次校验**（前端可绕过）；V-08/V-09/V-10 的后端校验是硬约束，Phase 10 预校验复用同一套校验器。
