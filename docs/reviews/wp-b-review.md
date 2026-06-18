# WP-B 复核报告 — 测试结果「只确认、不录入」（Phase 16）

| 项 | 值 |
|---|---|
| 阶段 | WP-B / Phase 16（测试结果只确认，后端为主 + 前端最小） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-18 |
| 被复核提交 | `f4ce3a8`（refactor，单提交） |
| 增量基线 | `main..feature/wp-b-test-confirm`（后端控制器/Service + V21 + 3 IT + 前端最小 + 文档） |
| 迁移 | 新增 `V21__test_confirm_only.sql`；V1–V20 未改 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | Blocker 0 · Major 0 · Minor 0 |

---

## 一、结论
WP-B 一轮通过。测试结果手工录入/编辑入口（`POST /api/test`、`PUT /api/test` 及其 `save` 服务方法、`test:edit` 授权）**整体下线**，仅保留「导入（`/api/test/import`、`/import-file`）+ 确认（`/{id}/confirm`）+ 查询/有效性」。**导入路径内部落库逻辑未动**，三条既有契约（成绩文本前导零、免考通过剔除应考科目、确认后锁定拒改、写侧数据范围）全部经导入路径复验通过。**clean-room**（重置 dev schema → Flyway 全新应用 V1–V21）`mvn -B -ntp verify` **BUILD SUCCESS、Failsafe 76/76**；前端 `vue-tsc --noEmit` 无错、`vite build` 成功。

## 二、复核方法
1. **取增量**：`git diff main..f4ce3a8`——后端 `AbilityTestResultController/Service/ServiceImpl`、`V21`、`Phase8/Phase14/Phase2` IT、前端 `testResult.ts`/`TestResultManageView`/`MainLayout`/`router`、文档。
2. **读后端**：确认仅删除手工 `save(AbilityTestSaveRequest)` 公有入口（原委托 `save(request,"test:edit")`），`importRows/importFile` 复用的内部落库未删；剩余端点 `@PreAuthorize` 去除 `test:edit`、改 `test:import`/`test:confirm`。
3. **读 V21**：幂等撤销全角色 `test:edit` 授权（保留权限点定义），再保 `test:import/test:confirm` 归 AUDITOR/ACADEMIC_ADMIN。
4. **读 IT**：手工建结果改导入路径；新增「手工端点已下线」反例；契约断言保留。
5. **clean-room 构建**：`DROP/CREATE teacher_cert` → `mvn verify`（全新应用 V1–V21）；前端 `type-check` + `build`。

## 三、关键核对
| 项 | 结论 | 证据 |
|---|---|---|
| 手工录入下线 | ✅ | 控制器删 `POST/PUT /api/test` + `save`；Service 接口/impl 删 `save`；新反例 `manualCreateAndUpdateEndpointsAreOffline` 证 POST/PUT 非 0 且不落库 |
| 导入路径完好 | ✅ | `importRows/importFile` 未改，内部落库保留；Phase8/14 改导入后成绩/确认/有效性全绿 |
| 成绩文本（前导零） | ✅ | `000000000000123456789` / `00000000000085` 经导入往返一致 |
| 确认后锁定拒改 | ✅ | 确认后再导入同生改值 → code=1000「已锁定」 |
| 免考联动/数据范围 | ✅ | 免考通过剔除应考科目；跨学院导入写 403 |
| 权限收口 | ✅ | V21 撤 `test:edit`；Phase2 钉死 auditor/academic 有 `test:import+test:confirm`、无 `test:edit` |
| 前端最小 | ✅ | 移除 `save/update` API 与录入入口；`vue-tsc` 无错、`built in 6.14s`（仅既有 chunk-size 告警） |
| 迁移/冻结 | ✅ | 仅 V21 新增、幂等、全新应用 v21；V1–V20 未改 |

## 四、维度结论
D1 需求 ✅（只确认不录入达成）· D4 红线 ✅（数据范围/锁定语义不变）· D5/6 质量安全 ✅（删冗余入口、权限收口）· D7 构建 ✅（clean-room 76/76 + 前端绿）· D8 测试 ✅（新增下线反例 + 契约经导入复验）· D9 数据库 ✅（V21 幂等、V1–V20 冻结）· D10 回归 ✅（Phase2~14 全绿）· D11 文档进度 ✅（待复核未自 ✅）。

## 五、放行
1. PROGRESS WP-B 置 **✅ 已复核**。
2. 合并 `main`（本地私有、无远程、不 push）。
3. 放行 Phase 17（WP-C 视频退回可重传，迁移 V22）。
