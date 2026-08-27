# Phase 9 · 证书编号与证书管理（M09）

> 优先级 P0 · 依赖：Phase 3/4/5/6/7/8（全部前置）· 任务：T-072~T-079 · plan §6.6 / §6.7 / §6.8 / §九 / §15.2-C / §15.10
> 目标：18 位证书编号生成、有效期计算、前置条件校验、证书状态机、作废/重开、关键字段锁定。**AT-09、AT-10、AT-11 首验。**

## 1. 范围
编号生成（含序列作用域+分布式锁+查重）、有效期计算、前置条件聚合校验、状态机 C、签发、作废/重开、编号导入/更正、字段锁定。

## 2. 数据库（V15__certificate.sql）
- `certificate`：student_id, assessment_year, cert_no(18位,唯一), training_goal, teaching_segment, teaching_subject, education_level, issuer, issue_date(文本), valid_until(文本), status, void_reason, reissue_origin_cert_no, locked。
- `cert_sequence`：scope_key, current_seq。
- 唯一索引 `certificate(cert_no)`。

## 3. 编号生成（§6.6 / §15.10）
```
certNo = year(4) + schoolCode(5) + levelCode(1) + provinceCode(2) + segmentCode(1) + seq(5)
levelCode   : 博士研究生1 硕士研究生2 本科3 专科4   （map key = education_level 字典值原文）
segmentCode : 幼儿园1 小学2 初级中学3 高级中学4 中等职业学校5   （map key = teaching_segment 字典值原文）
schoolCode  : sys_param cert.school.code (10588)
provinceCode: sys_param cert.province.code (44)
seq         : 5 位零填充；作用域 sys_param cert.seq.scope（默认 SCHOOL_YEAR_SEGMENT）
```
- 序列计数器存 `cert_sequence` 行，与证书写入**同一事务**内 `SELECT ... FOR UPDATE` 自增（事务回滚则序号回退，保证**无空号**）；Redis 锁仅作可选互斥，**禁止用 Redis INCR 当计数器**（回滚会留空号）。
- 更正证书号时先 `SELECT ... FOR UPDATE` 读取最新证书聚合并重跑整体校验，再在同一事务把最终合法编号占入 `cert_sequence`；后续更新或审计失败时两者一起回滚。
- 证书详情与生成/签发/导出/归档/作废/重开/更正等写接口响应都基于数据库实际持久化的聚合快照生成 `correctionRevision`；前端打开更正表单时保存该值并在提交时原样携带。服务端取得行锁后先比较当前 revision，缺失或陈旧请求在序列占用、数据更新和审计前失败并提示刷新。
- 生成即查重（`cert_no` 唯一）。
- 示例可复现：2026 本科 广东 高中 第1 → `202610588344400001`；中职 第1 → `202610588344500001`。

## 4. 有效期（§6.7）
```
issueMonth ≤ 6 → valid_until = (issueYear+3)/6/30
else           → valid_until = (issueYear+3)/12/31
```
文本存储；允许有权限者更正。示例：2022 上半年→2025/6/30；下半年→2025/12/31。

## 5. 前置条件（§6.8，聚合校验）
全部满足才允许生成：基本信息复审通过 + 培养信息复审通过 + 过程性考核"合格"（四类全通过）+ 测试/免考结论有效 + 视频评审通过（受 `video.required` 控制）+ 学院复审 + 教务处最终确认。缺项时返回**缺失清单**。

## 6. 状态机 C（§15.2-C）
待生成→(生成)已生成→(签发)已签发→(导出)已导出→(归档)已归档；旁支 已生成/已签发→(作废,填原因)已作废→(重开)已重开→新证书(待生成)。
- 已生成后锁定：姓名/证件号/任教学段/任教学科/培养目标/证书编号/有效期限。
- 作废/重开记录原因/操作人/时间/关联原证书号。

## 7. 接口清单
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/cert/precheck/{studentId}?year=` | `cert:generate` | 前置条件检查（返回缺失项） |
| POST | `/api/cert/generate` | `cert:generate` | 生成编号（前置+查重+锁序列） |
| POST | `/api/cert/{id}/issue` | `cert:issue` | 签发（签发人/日期/算有效期） |
| POST | `/api/cert/{id}/void` | `cert:void` | 作废（填原因） |
| POST | `/api/cert/{id}/reissue` | `cert:reissue` | 重开（关联原证书） |
| PUT | `/api/cert/{id}/correct` | `cert:correct` | 必传 `correctionRevision`；锁定最新聚合并比对后更正，编号同步占用序列（留痕） |
| GET | `/api/cert` | `cert:view`(范围) | 证书清单 |

## 8. 前端
- 教务处证书管理：前置校验（缺项明示并阻断）、生成、清单、作废/重开、更正；更正抽屉保存详情返回的 revision，陈旧时提示刷新。
- 签发人页：签发确认、有效期回显、导出清单入口。

## 9. 验收清单（AT-09/10/11）
- [x] 任一前置不满足 → 生成被拒，返回明确缺失清单（AT-09）。
- [x] 18 位编号各段正确；示例 `202610588344400001`/`...500001` 可复现（AT-10）。
- [x] 同年同校同学段顺序号连续递增不重复（作用域 `SCHOOL_YEAR_SEGMENT`）；并发生成不产生重号。
- [x] 切换 `cert.seq.scope=SCHOOL_YEAR` 后行为随之变化（参数化生效）。
- [x] 有效期：上半年签发→+3年6/30、下半年→+3年12/31（AT-11）。
- [x] 生成后关键字段锁定，任何角色直接改被拒（更正走 `cert:correct` 并留痕）。
- [x] 更正 `00001→00002` 后下一次自动生成得到 `00003`；更正事务失败时序列不前移。
- [x] 两名管理员从同一详情快照打开全量更正表单；首笔成功后，第二笔旧 revision 被拒绝且不得恢复首笔已改字段。
- [x] 作废/重开记录原因/操作人/时间/原证书号；状态流转符合 §15.2-C。

## 10. 测试用例
- T-CERT-1（反例）：过程性考核未"合格" → 生成被拒，缺失清单含"过程性考核"。
- T-CERT-2：本科/广东/高中 2026 第1 → `202610588344400001`；同年中职第1 → `...500001`。
- T-CERT-3（并发）：并发生成 100 张 → 序号 1..100 连续不重。
- T-CERT-4：2022-03 签发 → 有效期 2025/6/30；2022-09 → 2025/12/31。
- T-CERT-5：作废后重开 → 新证书关联原证书号，原证书状态=已作废。
- T-CERT-6（反例）：锁定后改任教学科 → 拒。
- T-CERT-7：同作用域 `00001` 更正为 `00002` 后生成下一张 → `00003`；在序列推进后制造更正失败 → 证书与序列均回滚，下一张仍为 `00002`。
- T-CERT-8（并发）：两个部分更正从同一旧快照发起 → 行锁后基于最新聚合重新组装和校验，不得合成一个从未整体校验过的字段组合。
- T-CERT-9（陈旧全量表单反例）：A、B 同时取得同一 `correctionRevision`；A 更正学科成功，B 用旧全量表单只改有效期 → B 在任何序列/数据/审计写入前被拒绝，最终学科保持 A 的结果。缺失 revision 同样失败关闭。
- T-CERT-10（写响应一致性）：每个证书写响应的 `correctionRevision` 必须等于紧接着 GET 详情从数据库重算的值；生成响应 token 可不经额外 GET 立即用于合法更正。

## 11. DoD
编号/有效期/前置/状态机/作废重开/锁定全部可用；AT-09/10/11 自测（含并发与反例）通过。

## 12. 风险
- 序列并发安全是硬要求（AT-10），必须用分布式锁或行锁 + 事务，**严禁**应用层 `max(seq)+1` 裸读写。
- 作用域默认 `SCHOOL_YEAR_SEGMENT`（确认单第 1 项）；上线前以学校书面结论为准。
