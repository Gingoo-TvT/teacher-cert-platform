# Phase 11 复核报告 — 统计查询与报表（T-092~T-100）

| 项 | 值 |
|---|---|
| 阶段 | Phase 11 统计报表（P1） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `37b60ef`（feat T-092~T-100，单提交；新增 platform-statistics 模块） |
| 增量基线 | `24de94c..HEAD`（约 21 文件） |
| 迁移 | 无（`stats:view` 已在 V8 预种）；V1–V17 未改动 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | **Blocker × 0 · Major × 0 · Minor × 5（入 backlog）** |

---

## 一、结论

Phase 11 一轮通过。**数据范围生命线在服务层强制**：`resolveScope("stats:view")` → allSchool/COLLEGE/SELF；**8 类统计全部经 `scopedStudents` 收敛**（其余业务表按 `in(studentId, 范围内学生)` 关联、空集合 fail-closed 返回空、`query.collegeId` 越范围→空），导入导出日志统计按 `batchVisible`(operator/scopeJson) 收敛——学院用户**绝不会看到他院数据**（IT 反例证学院A不含学院B）。**口径与明细对账一致**：材料完成率（四类全 PASSED）按类别计数 == DB 实际 PASSED 计数、证书各状态计数 == 证书清单分组（IT 双对账）；**口径复用各阶段既有**（MaterialStatus/CertificateStatus/VideoReviewStatus/finalStatus + IdCard/BirthDate/MajorCode/TrainingLink 校验器），分母"应交人数"=范围内当年在册学生且文档化（DENOMINATOR_RULE 随报告返回并前端展示）。异常统计可定位到学生/字段；导出**复用 Phase 10 `ExchangeExcelHelper`**（单元格 `@` 文本）。新模块 platform-statistics 正确注册接入（依赖 exchange 取 Excel 助手、无环）；`stats:view` V8 预种无需新迁移。独立 `mvn verify` GREEN（51/51），前端 type-check/build 绿（echarts ^5.5.1）。5 个 Minor（口径细节/性能/SELF 死分支）入 backlog。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff 24de94c..37b60ef`（单提交；无迁移；V1–V17 与 AGENTS/REVIEW-GATE/HANDOFF/tasks 未改）。
2. **干净构建 + 重跑反例**：`mvn -B -ntp verify` → **BUILD SUCCESS 51/51**（Phase2~10 共 46 + `Phase11StatsIT 5`）；前端 `npm install`(echarts) + `type-check`(vue-tsc 干净) + `build` ✓。
3. **读码裁决**：`StatsServiceImpl`(747 行：resolveScope/scopedStudents/8 类统计/对账/导出)、`StatsController`、`platform-statistics/pom.xml`、前端 `stats.ts`/`StatsReportView.vue`、`V8`(stats:view 种子)、`Phase11StatsIT`(424 行)。
4. **一路独立代理**（数据范围完整性/口径复用/模块接入/前端/治理）——PASS，逐条确认 8 类统计均 fail-closed 收敛、无跨院泄漏。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 8 类统计 + ECharts + 导出 + 钻取 与 plan + phase-11 一致 |
| D2 验收清单 | ✅ | 8 类可查可导出、对账一致、数据范围生效、异常可定位 |
| D3 AT 验收 | ✅ | 无新首验；AT-13 数据范围在统计读侧续接 |
| D4 红线合规 | ✅ | 统计读侧一律按范围过滤、文本化、导出留痕、口径不硬编码（走字典/既有状态） |
| D5 代码质量 | ✅ | 分层/Result/LambdaQueryWrapper 参数绑定无注入；性能项为 Minor |
| D6 安全 | ✅ | 范围 fail-closed（空集合/越范围→空）、`stats:view` 鉴权、导出 @AuditLog |
| D7 构建与运行 | ✅ | `mvn verify` 51/51、type-check/build 绿；未自起常驻服务 |
| D8 测试 | ✅ | Phase11StatsIT 5/5：材料对账、证书对账、跨院排除、异常定位、导出文本 |
| D9 数据库 | ✅ | 无新迁移（stats:view 预种）；V1–V17 未改 |
| D10 回归 | ✅ | Phase2~10 共 46 条回归通过 |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅；DEVLOG 有小结 |

---

## 四、做得好

- **数据范围 fail-closed**：`scopedStudents` 非全校且 collegeIds 空 → 返回空；`query.collegeId` 越范围 → 空；8 类统计均建立在范围内学生集上，关联表 `in(studentId,…)` 收敛；batchVisible 按 operator/scopeJson。IT `collegeStatsExcludeOtherCollegeData` 直证学院A不含学院B。
- **对账一致**：`materialCompletionStatsReconcileWithDetailsAndGroupedRows`（各类别 PASSED 计数 == DB）+ `certificateStatsReconcileWithCertificateListGrouping`（各状态 == DB 分组）双对账。
- **口径复用既有**：材料合格沿用 Phase 5 四类 PASSED、证书状态 Phase 9、视频 Phase 7、免考 Phase 6、异常复用 Phase 3/4 校验器；分母规则 DENOMINATOR_RULE 文档化并返回。
- **导出复用 + 文本化**：走 `ExchangeExcelHelper.writeTableWorkbook`（`@` 文本格式），IT 断言列样式 `@`。
- 新模块 platform-statistics 注册接入 boot、无环；前端 ECharts 图表+表格+导出，类型齐全无 any。

---

## 五、Minor（入 backlog，不阻断）

- `scopedStudents` 对 SELF 范围（学生）会因"非全校且 collegeIds 空"先返回空 → 学生看不到本人统计；当前无角色被授 `stats:view` SELF，**fail-closed 不泄漏**、暂不可达；如未来开放学生统计需短路 studentId 分支。
- 材料"某类合格"口径（≥1 条 PASSED）较 Phase 5（`passedCount>0 && failedCount==0`）略松，仅当同类别同时存在 PASSED+FAILED 记录时分歧；建议与 Phase 5 对齐（排除含 FAILED 的类别）。
- `batchVisible` 用 `scopeJson::contains(collegeId字符串)` 子串匹配，理论可过宽匹配（仅影响批次日志元数据、非 PII）；建议解析 JSON 精确比较。
- "钻取到明细"为静态明细表 + 过滤（非图表点击联动）；DoD 宽松满足，可后续增强为交互式钻取。
- 性能：统计全量加载范围内集合到内存、明细 `.limit(200)` 截断无提示、`collegeNames()` 在明细循环内重复查询；当前数据量可接受，建议后续分页/流式 + 提升 collegeNames 缓存。

---

## 六、放行

1. `PROGRESS.md` Phase 11 置 **✅ 已复核**；无新 AT 首验（AT-13 统计读侧续接）。
2. 合并 `main`（本地私有、无远程、不 push）；启动 Phase 12（通知）。
3. 5 个 Minor 进 backlog。
