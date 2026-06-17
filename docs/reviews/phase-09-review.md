# Phase 9 复核报告 — 证书编号与证书管理（T-072~T-079）

| 项 | 值 |
|---|---|
| 阶段 | Phase 9 证书 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `6be5f79`（feat T-072~T-079，单提交） |
| 增量基线 | `f2f8a64..HEAD`（约 27 文件；business 17 / boot 4 / frontend 5） |
| 迁移 | 新增 `V15__certificate.sql` + `V16__certificate_student_view.sql`；V1–V14 未改动 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | **Blocker × 0 · Major × 0 · Minor × 9（入 backlog）** |

---

## 一、结论

Phase 9 一轮通过，三项 AT 首验齐绿。**AT-10 18 位编号生成（上报级生命线）——并发无重号、回滚无空号，经独立判定为安全**：`nextSequence` 用 `cert_sequence` 行 + `SELECT ... FOR UPDATE` 在 `@Transactional` 的 `generate` 内自增，**行锁持有至 generate 提交**故同作用域并发严格串行；序号在**所有可失败前置门（校级权限/前置校验/已有证书查重）之后、唯一一次 insert 之前**消费，insert 触发 `DuplicateKeyException` 则整事务回滚、序号随之回退（无空号）；`cert_no` 唯一键 + 异常兜底；**无 Redis INCR、无 max(seq)+1**。并发反例（50 学生 × 10 线程 → certNo 全不重、序号恰为 1..50 连续）为真实多线程竞争。**AT-09 前置聚合**复用 Phase3/4/5/6/7/8 既有结论（基本/培养复审通过 + 过程性合格 + 测试免考有效 + 教务处确认 + 视频必过），缺项返回明确缺失清单、生成被拒。**AT-11 有效期**上半年→+3/6/30、下半年→+3/12/31（边界 ==6 归上半年正确）。状态机 C 各流转均有当前态守卫、关键字段结构性锁定（仅 `cert:correct` 可改且留痕）、作废→重开新证关联原号。`certificate` **读+写数据范围**到位（读 SELF/COLLEGE/SCHOOL、写仅校级、collegeId 取自 student 实体）。V15+V16 幂等、V1–V14 冻结、ID 命名空间不冲突。独立 `mvn verify` GREEN（40/40），type-check/build 绿。9 个 Minor 入 backlog。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff f2f8a64..6be5f79`（单提交；V1–V14 与 AGENTS/REVIEW-GATE/HANDOFF/tasks 未改）。
2. **干净构建 + 独立重跑反例**：`mvn -B -ntp verify`（docker 起依赖）→ **BUILD SUCCESS**，Failsafe 自动执行 Phase2~8 共 33 + `Phase9CertificateIT 7` ＝ **40/40**；前端 `type-check`/`build` 绿。
3. **读码裁决**：`CertificateServiceImpl`(595 行：generate/nextSequence/doPrecheck/validUntil/状态机/数据范围)、`CertSequenceMapper`(FOR UPDATE)、`CertificateStatus`、`CertificateController`、`DataScopeSqlHandler`(certificate 规则)、`V15`/`V16`、`Phase9CertificateIT`(636 行)。
4. **两路独立代理**（AT-10 并发/AT-09 前置/AT-11/状态机/数据范围 · 质量/DoD/迁移/前端/治理）——均 PASS；代理 1 给出**并发安全的明确结论**。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 编号/有效期/前置/状态机/作废重开/锁定 与 plan + phase-09 一致 |
| D2 验收清单 | ✅ | AT-09/10/11 复跑通过（含并发与反例） |
| D3 AT 验收 | ✅ | AT-09/AT-10/AT-11 首验通过 |
| D4 红线合规 | ✅ | 数据范围读+写、文本化 String、留痕、不硬编码（码表 ext_json/参数 sys_param） |
| D5 代码质量 | ✅ | 分层/事务/Result/BaseEntity；写方法全 `@Transactional`（序号回滚路径受保护） |
| D6 安全 | ✅ | 写仅校级（ensureSchoolWrite allSchool）、collegeId 取自实体、FOR UPDATE SQL 参数绑定无注入 |
| D7 构建与运行 | ✅ | `mvn verify` 40/40、type-check/build 绿；并发反例进程内多线程、未自起常驻服务 |
| D8 测试 | ✅ | Phase9CertificateIT 7/7：并发 1..50 无重无空、段码示例、作用域参数化、前置缺失、有效期、作废重开、读写范围 |
| D9 数据库 | ✅ | 仅 V15+V16 新增、V1–V14 未改；幂等、唯一键、文本 VARCHAR、ID 命名空间不冲突 |
| D10 回归 | ✅ | Phase2~8 共 33 条回归通过 |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅；DEVLOG 有小结 |

---

## 四、做得好

- **AT-10 并发安全（生命线）**：`cert_sequence` 行锁 + 同事务自增 + 回滚无空号 + 唯一键兜底；序号在所有可失败门之后消费；真实 50×10 线程并发反例断言无重号且连续 1..50。
- **18 位段码不硬编码**：层次码/学段码取自 V3 字典 `ext_json`（certLevelCode/certSegmentCode），schoolCode/provinceCode/作用域走 `sys_param`；`202610588344400001`(高中)/`...500001`(中职) 可复现；作用域切 `SCHOOL_YEAR` 行为随之变。
- **AT-09 前置聚合复用**：逐项复用各阶段既有结论（不重算），缺项→明确缺失清单→生成被拒；免考学生（exempted 有效 + CONFIRMED）不被误拒。
- **AT-11 有效期**：边界 ==6 归上半年；多格式日期解析；文本存储。
- **状态机 C + 字段锁定**：各流转当前态守卫；关键字段除 `cert:correct`（留痕）外无端点可改（结构性锁定）；作废→重开新证关联原号、原证留 VOIDED（与验收 T-CERT-5 一致）。
- **数据范围**：certificate 注册进 `DataScopeSqlHandler`；读 SELF/COLLEGE/SCHOOL（学生本人经 V16 授权 cert:view SELF）、写 `ensureSchoolWrite` 仅校级；clerk 生成 403、跨院读不可见。
- **迁移卫生**：V15+V16 幂等；V16 把"学生看本人证书"权限作为**新迁移**补授（V15 已应用，遵守不改已发布脚本）；前端 `row-key` 显式类型（未重蹈隐式 any）。

---

## 五、Minor（入 backlog，不阻断）

- `markExported`/`archive` 用 `ensureSchoolWrite("cert:view")` 授权写操作——语义异味（读权限码守写），当前仅 SCHOOL 级 cert:view 通过、功能安全；建议引入 `cert:export`/`cert:archive` 或复用 `cert:issue`。
- `CertificateStatus.REISSUED` 枚举值从不作为目标态写入（重开后原证留 VOIDED）；与验收一致但为死分支，确认或移除。
- `correct` 双重留痕：控制器 `@AuditLog` + 服务 `recordAudit` 各写一条（服务条带 old/new 状态+原因）；建议二选一或注明意图，避免 AT-12 计数重复。
- `reissue`→`generate` 隐式依赖 `cert:generate`（ACADEMIC_ADMIN 两权皆有、无实锁死）；建议抽出无前置生成核心或注释。
- `doPrecheck` 未列独立"学院复审"项——被各业务 `PASSED`(复审通过)态吸收；建议文档注明 学院复审 == SECOND_REVIEW→PASSED。
- `V15` 重复 seed `cert.*` 三参（V1 已拥有，param_key 冲突走 UPDATE、150… 号不落地）——幂等无害，建议删该块或注明防御性。
- 并发反例用 50（规格 §9/§10 写 100）——所验性质（无重+连续）已充分，建议提到 100。
- `list()` 全量 `selectList`、total=size 非真分页（复发）。
- 覆盖面：序号"insert 失败回滚不跳号"未直接造例、SCHOOL_YEAR 并发未测、export/archive 对 COLLEGE cert:view 的 403 未断言、correct 对 VOIDED 拒 未测。

---

## 六、放行

1. `PROGRESS.md` Phase 9 置 **✅ 已复核**；AT-09/AT-10/AT-11 首验通过。
2. 合并 `main`（本地私有、无远程、不 push）；启动 Phase 10（导入导出与预校验）。
3. 9 个 Minor 进 backlog。
