# Phase 10 · 教育部标准数据交换中心（M10）

> 优先级 P0 · 依赖：Phase 1~9（字典/校验器/业务表全部就绪）· 任务：T-080~T-091 · plan §7 / §15.6 / §15.7
> 目标：标准模板下载、两步导入（预校验→确认→可回滚）、预校验中心 V-01~V-13、异常报告、标准/完整/汇总/异常/附件清单导出，全程文本化。**AT-01、AT-02、AT-14 首验。**
>
> 2026-07-24 Phase 42 第二轮独立增量重核 **PASS**：逐行/错误明细使用 status-only `FOR UPDATE`，关闭整批 `preview_json` 重读的 O(N²) Medium；T-IMP-5C 双向真实 MySQL 交错关闭错误明细屏障测试 Low；rollback 完整行锁与原 PG-H3 batch-first 协议保持。见 `docs/reviews/phase-42-second-remediation-rereview-2026-07-24.md`。开发者门禁 323/323 早于最终测试源码，严格提交级同源性作为非阻断证据限制保留。

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
2. **确认导入**：策略（新增/覆盖/跳过重复/仅更新空字段）→原子认领 `PREVALIDATED → IMPORTING`。每个逐行 `REQUIRES_NEW` 事务及失败明细事务的第一项业务动作都必须对同一 batch 执行 `SELECT ... FOR UPDATE`，并只在数据库持久状态仍为 `IMPORTING` 时写业务数据、`import_record_ref` 或错误明细。
3. **回滚**：按 batch_id 反向（INSERT→逻辑删除，UPDATE→还原 before_json），已被后续修改的跳过并提示冲突。rollback 在一个事务内将 batch `SELECT ... FOR UPDATE` 作为第一条数据库语句：先等待在途行提交并阻断后续行，再锁定当前完整 ref 集；在取得任何 student/training/certificate 业务子行锁之前，必须预解析所有 UPDATE `before_json` 中的目标 `collegeId`，去重并按 ID 升序执行 `deleted=0 FOR UPDATE`。目标学院缺失/已删除时必须 fail closed 或把该 ref 记为明确冲突，禁止恢复到无效父级。之后才锁对应业务记录、逆序补偿，并把补偿结果和 `ROLLED_BACK/PARTIAL_ROLLBACK` 原子提交。固定顺序为 `batch → refs → college IDs 升序 → business child`。
4. **收尾守卫**：confirm 的 `IMPORTING → IMPORTED/FAILED` 条件更新必须恰好命中 1 行；未命中时重读数据库真实状态并返回“导入已停止”，禁止返回本地累计出的伪成功终态。
5. **锁查询固定大小**：逐行导入与失败明细事务的 batch `FOR UPDATE` 只允许投影状态所需的固定大小字段（`status` 或 `id,status`），禁止随每行重复装载整批 `preview_json`；rollback 单次读取所需 batch 元数据不受此限制。
6. **学院父子完整性**：每个逐行 `REQUIRES_NEW` 事务在直接新增或迁移 `student` 前，必须对解析且授权通过的目标学院执行 `deleted=0 FOR UPDATE`；与学院删除共用串行化边界，禁止标准导入成为绕过 Phase 39 父锁的旁路。

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
- [x] confirm 与 rollback 竞争同一 batch 行锁：rollback 返回终态后不得再出现迟到业务写/ref；在途行先取得锁时，rollback 必须等待其提交并补偿完整引用集。
- [x] 标准导入的直接学生写入路径已接入目标学院父行锁，目标学院已逻辑删除时整行事务失败，不产生学生孤儿。
- **Phase 39 复核退回项（未闭环）**：历史 UPDATE ref 的 rollback 在任何业务子行锁前预锁 `before_json` 目标学院；目标学院已删除时禁止恢复，并以明确冲突/失败终态收敛。
- [x] 5 类导出列与 §15.6 一致；导出留审计；敏感导出鉴权。

## 11. 测试用例
- T-EXP-1：导出后用 Excel + WPS 打开，证件号 `44010620001231XXXX` 完整显示（非 `4.4E+17`）（AT-01）。
- T-EXP-2：导出表头逐列比对模板（A–Z）；H 列="身份证件号码"（AT-02）。
- T-IMP-1（13 反例）：构造每条 V 规则各一错误行 → 预校验全部命中并定位。
- T-IMP-2：策略=跳过重复，含 1 重复证件号 → 跳过且批次记 fail。
- T-IMP-3：导入后回滚 → 数据恢复；被后续修改的记录回滚时提示冲突。
- T-IMP-4：前导零学号 `00123` 导入→导出保持 `00123`。
- T-IMP-5A（rollback 先线性化）：两行导入在首行提交后暂停 → rollback 返回并持久化 `ROLLED_BACK` → 释放 confirm；confirm 必须返回“导入已停止/ROLLED_BACK”，两行均不得留下活跃 student/training/certificate，第二行不得产生迟到 ref。
- T-IMP-5B（在途行先线性化）：第一行取得 batch 锁、尚未写业务数据时暂停 → rollback 发起但不得完成 → 释放行事务；rollback 必须看到并补偿该行完整 3 条 refs，终态 `ROLLED_BACK`，三类业务数据均无活跃记录且 refs 保留用于追溯。
- T-IMP-5C（失败明细屏障）：行事务失败后、错误明细竞争 batch 锁前暂停 → rollback 先完成时不得出现迟到 `import_error_detail`；错误明细先取得锁时 rollback 必须等待其提交后再落终态。
- T-IMP-6A（历史父级失效）：构造旧版可达的 student UPDATE ref（before=A、after=B），软删 A 后 rollback → 不得把学生恢复到 A，活跃学生学院孤儿数为 0，终态/冲突信息明确。
- T-IMP-6B（删除/回滚双向交错）：历史 ref rollback 与 `deleteCollege(A)` 分别先取得学院父锁 → 两个方向都不得留下活跃 student/training/certificate 指向已删除 A；锁顺序必须为 `batch → refs → college IDs 升序 → business child`。

## 12. DoD
模板/预校验/导入/回滚/异常报告/5 类导出全部可用；AT-01/02/14 自测（含 13 条 V 反例与 WPS/Excel 兼容）通过。

## 13. 风险
- 文本化是 AT-01 生命线：模型 String + 写出 `@` + 读取按 String，三处缺一即失败。
- POI 下拉 + EasyExcel/FastExcel 数据写入需协调（同一 workbook 处理），注意大数据量内存（SXSSF/流式）。
- 回滚的"已被后续修改"判定要可靠（比对 before_json 或版本号），避免误覆盖他人更新。
- 锁外读取 batch 状态不能形成并发屏障；所有逐行业务写入、ref 与错误明细必须先取得同一 batch 行锁。rollback 需在补偿全程持锁，超大批次应关注锁持有时长，但不得为缩短时长拆成会重新暴露迟到写窗口的多事务协议。
- batch 锁查询不能使用 `SELECT *` 逐行重读 `preview_json`；否则 N 行预览会形成 O(N²) 数据传输/映射并破坏万行级导入时限。性能修复必须收窄投影，不能移除串行化锁。
- 当前 `PARTIAL_ROLLBACK` 表示本次补偿遇到冲突；自动重试是否应跳过已成功补偿的 ref 属于既有语义债，本次 PG-H3 不重新定义，后续如需改变必须先明确规格并补幂等标记/反例。
- 持久 `before_json` 是不受当前在线校验保护的历史输入；任何补偿恢复都必须重新验证父引用。不能因新版本已禁止跨学院导入更新，就假定历史 ref 不含跨学院快照。
