# Phase 10 · 教育部标准数据交换中心（M10）

> 优先级 P0 · 依赖：Phase 1~9（字典/校验器/业务表全部就绪）· 任务：T-080~T-091 · plan §7 / §15.6 / §15.7
> 目标：标准模板下载、两步导入（预校验→确认→可回滚）、预校验中心 V-01~V-13、异常报告、标准/完整/汇总/异常/附件清单导出，全程文本化。**AT-01、AT-02、AT-14 首验。**

## 1. 范围
26 列模型与文本格式、模板下载（内置下拉+补 H 表头）、预校验（不入库）、确认导入（4 策略+批次+回滚）、异常报告、5 类导出、导出审计。

## 2. 数据库（V17__exchange.sql）
`import_export_batch`、`import_error_detail`、`import_record_ref`（plan §5.3/§15.3）。

## 3. 26 列模型与文本化（AT-01/AT-02）
- A–Z `@ExcelProperty` 模型，**全部 String**；列顺序与名称固定（plan §7）。
- 自定义写出处理器：单元格格式 `@`（文本），学校代码/学号/证件号/出生日期/证书编号/有效期限禁科学计数/日期序列/前导零丢失。
- **H 列表头写"身份证件号码"**（修复模板空表头）。

## 4. 模板下载（§15.7）
- POI `DataValidationHelper` 内置字典与学科库下拉（隐藏 sheet 承载选项），全列预设 `@`；支持按年度/学院/专业生成。

## 5. 预校验中心 V-01~V-13（§7.3，逐条）
| 编号 | 校验 | 复用校验器 |
|---|---|---|
| V-01 | 字段完整性（必填/条件必填按身份类型） | — |
| V-02 | 文本格式 | 写出处理器 |
| V-03 | 学校代码/名称对应 | 字典 |
| V-04 | 姓名格式 | `NameValidator` |
| V-05 | 证件号码 | `IdCardValidator` |
| V-06 | 出生日期一致 | `BirthDateValidator` |
| V-07 | 身份类型字典 | 字典 |
| V-08 | 专业代码 0401/0451/0453 | `MajorCodeValidator` |
| V-09 | 实习地点（企业=职教/海外=汉语国际教育） | 联动校验 |
| V-10 | 任教学科来自学段库、中职专业课具体学科 | 学科库 |
| V-11 | 证书编号 18 位各段合法 | 编号校验 |
| V-12 | 有效期限规则 | `ValidityCalculator` |
| V-13 | 重复（同证件号/同证书号） | 唯一约束 |

## 6. 导入两步 + 回滚（§15.7）
1. **预校验（不入库）**：上传→全读为 String→逐行 V-01~V-13→产出 {总数/成功预览/失败明细}，可下载异常报告。
2. **确认导入**：策略（新增/覆盖/跳过重复/仅更新空字段）→事务入库→写 `import_export_batch` + `import_record_ref`。
3. **回滚**：按 batch_id 反向（INSERT→逻辑删除，UPDATE→还原 before_json），已被后续修改的跳过并提示冲突。

## 7. 异常报告（AT-14）
异常数据表：批次号、行号、学号/姓名、字段、错误值、错误原因、建议处理方式。

## 8. 导出（§7.3 / §15.6）
- 5 类：标准上报表（A–Z 固定）、完整审核表、证书获得者汇总表、异常数据表、附件清单表（列定义见 §15.6）。
- 范围筛选：全校/学院/专业/班级/培养目标/学段/审核状态/证书状态；保留筛选条件。
- 附件/视频批量打包；导出审计（操作人/时间/范围/文件）；敏感导出 `exchange:export:sensitive`。

## 9. 接口清单
| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/api/exchange/template?year=&college=&major=` | `exchange:template` |
| POST | `/api/exchange/prevalidate` | `exchange:prevalidate` |
| GET | `/api/exchange/prevalidate/{batch}/error-report` | `exchange:prevalidate` |
| POST | `/api/exchange/import?strategy=` | `exchange:import` |
| POST | `/api/exchange/import/{batch}/rollback` | `exchange:import` |
| GET | `/api/exchange/batches` | `exchange:import` |
| POST | `/api/exchange/export/{type}` | `exchange:export:standard/full` |
| POST | `/api/exchange/export/attachments` | `exchange:export:standard` |

## 10. 验收清单（AT-01/02/14）
- [x] 导出标准表含 A–Z **26 列**，名称顺序与模板一致，**H 列表头="身份证件号码"**（AT-02）。
- [x] 学校代码/学号/证件号/出生日期/证书编号/有效期限文本导出；重开 Excel **无科学计数、无日期序列、无前导零丢失**（AT-01）。
- [x] 模板下拉项与系统字典一致；全列文本格式。
- [x] V-01~V-13 **逐条**可触发，定位行号/字段/原因（13 条各一反例）。
- [x] 异常数据不静默入库；异常报告含行号/字段/错误值/原因/建议（AT-14）。
- [x] 4 种导入策略行为正确；批次可查；回滚后恢复到导入前。
- [x] 5 类导出列与 §15.6 一致；导出留审计；敏感导出鉴权。

## 11. 测试用例
- T-EXP-1：导出后用 Excel + WPS 打开，证件号 `44010620001231XXXX` 完整显示（非 `4.4E+17`）（AT-01）。
- T-EXP-2：导出表头逐列比对模板（A–Z）；H 列="身份证件号码"（AT-02）。
- T-IMP-1（13 反例）：构造每条 V 规则各一错误行 → 预校验全部命中并定位。
- T-IMP-2：策略=跳过重复，含 1 重复证件号 → 跳过且批次记 fail。
- T-IMP-3：导入后回滚 → 数据恢复；被后续修改的记录回滚时提示冲突。
- T-IMP-4：前导零学号 `00123` 导入→导出保持 `00123`。

## 12. DoD
模板/预校验/导入/回滚/异常报告/5 类导出全部可用；AT-01/02/14 自测（含 13 条 V 反例与 WPS/Excel 兼容）通过。

## 13. 风险
- 文本化是 AT-01 生命线：模型 String + 写出 `@` + 读取按 String，三处缺一即失败。
- POI 下拉 + EasyExcel/FastExcel 数据写入需协调（同一 workbook 处理），注意大数据量内存（SXSSF/流式）。
- 回滚的"已被后续修改"判定要可靠（比对 before_json 或版本号），避免误覆盖他人更新。
