# Phase 5 · 文件服务 + 过程性考核材料（M05）

> 优先级 P0 · 依赖：Phase 0(文件)、Phase 2、Phase 3 · 任务：T-044~T-051 · plan §6.4 / §15.2-A / §6.9
> 目标：四类过程性材料的上传/预览/替换/两级审核/批量下载，落地**"四类全部复审通过才合格"**。**AT-06 首验。**

## 1. 范围
受限附件上传（PDF/JPG/JPEG/PNG）、在线预览、替换规则、初/复审（通过/退回/不通过）、四类聚合"合格"、批量下载与清单元数据。

## 2. 数据库（V11__material.sql）
`process_material`：student_id, assessment_year, category(材料类别字典), file_id, file_name, file_path, uploader_id, upload_time, first_review_status/reviewer/time/comment, second_review_status/reviewer/time/comment, status, deleted。
- 四类材料：思想品德及师德素养、教师教育课程学业、教育实习实践、专业能力及技能培训。
- 每类支持**单个或多个**文件（多行）。

## 3. 文件规则
- 类型白名单 PDF/JPG/JPEG/PNG；单附件大小 `file.maxSize.material`（默认 50MB，可配）。
- 在线预览：PDF 与图片经预签名鉴权 URL 预览，不强制下载。
- 替换规则：审核**通过前**可直接替换/删除；**通过后**必须先退回再替换。

## 4. 接口清单
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/material/upload` | `material:upload` | 学生上传（校验类型/大小） |
| DELETE | `/api/material/{id}` | `material:upload` | 通过前删除 |
| PUT | `/api/material/{id}/replace` | `material:upload` | 替换（通过后需先退回） |
| GET | `/api/material/preview/{id}` | `material:view`/`upload` | 预览预签名 |
| POST | `/api/material/{id}/first-review` | `material:firstReview` | 初审 通过/退回/不通过 |
| POST | `/api/material/{id}/second-review` | `material:secondReview` | 复审 通过/退回/不通过 |
| GET | `/api/material/process-status/{studentId}?year=` | `material:view` | 四类聚合状态 |
| POST | `/api/material/batch-download` | `material:batchDownload` | 按学院/专业/班级/学生打包 |

## 5. 核心逻辑
- 每条材料独立走状态机 A（通过/退回/不通过，退回与不通过必填原因，保留历史版本）。
- **聚合"合格"判定**：四类材料**各类至少一条且该类复审通过**，四类齐全 → 过程性考核状态置"合格"；缺任一类或任一类不通过 → 不可"合格"。
- 批量下载：打 zip，附 Excel 清单（文件名/材料状态/审核人/审核时间），受数据范围约束。

## 6. 前端
- 学生端：四类分区上传、预览、替换（通过后入口禁用并提示退回）、查看审核意见。
- 教务员/副院长：审核列表、预览、初/复审、退回原因、批量下载、四类聚合状态可视。

## 7. 验收清单（AT-06）
- [ ] 非白名单类型/超大小附件 → 拒传并提示。
- [ ] PDF 与图片可在线预览，预览 URL 鉴权且限时。
- [ ] 审核通过前可替换；通过后直接替换被拒（须先退回）。
- [ ] 初/复审三种结论可用；退回与不通过必填原因；保留历史版本与留痕。
- [ ] **四类材料全部复审通过 → 过程性考核状态"合格"；缺任一类或任一类不通过 → 不能"合格"**（AT-06）。
- [ ] 批量下载按数据范围打包，Excel 清单含文件名/状态/审核人/审核时间。

## 8. 测试用例
- T-MAT-1（反例）：上传 `.docx` → 拒（类型）；上传 60MB → 拒（大小，默认 50MB）。
- T-MAT-2：四类各一条且均复审通过 → 聚合"合格"。
- T-MAT-3（反例）：仅三类通过、第四类缺失 → 聚合非"合格"（AT-06 关键反例）。
- T-MAT-4（反例）：某类复审"不通过" → 聚合非"合格"。
- T-MAT-5：材料复审通过后尝试替换 → 拒；退回后可替换。
- T-MAT-6：批量下载 zip 内含 Excel 清单且字段完整。

## 9. DoD
四类材料上传/预览/替换/两级审核/聚合合格/批量下载可用；AT-06 自测（含反例）通过。

## 10. 风险
- 聚合"合格"必须以"四类齐全 + 各类复审通过"为准，**不能**只看已上传材料是否通过（缺类也算合格是典型 bug，AT-06 专门反例覆盖）。
- 多文件同类时，聚合判定的口径（该类**所有**文件通过，还是**至少一条**通过）需在确认单外按需明确——默认：该类**存在至少一条复审通过**即该类合格；如学校要求全部通过另配。
