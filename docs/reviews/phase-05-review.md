# Phase 5 复核报告 — 文件 + 过程性材料（T-044~T-051）

| 项 | 值 |
|---|---|
| 阶段 | Phase 5 文件 + 过程性材料 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `aece971`（feat T-044~T-051，单提交） |
| 增量基线 | `3efce2b..HEAD`（约 26 文件；business 15 / boot 6 / frontend 4） |
| 迁移 | 新增 `V11__process_material.sql`；V1–V10 未改动 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | **Blocker × 0 · Major × 0 · Minor × 6（入 backlog）** |

---

## 一、结论

Phase 5 一轮通过。**AT-06 过程性考核「合格」判定正确且服务端硬判**——合格度按字典的四类（教育见习/实习/研习/技能训练）聚合，**缺任一类 或 任一类"不通过" → 不合格**（聚合以字典类别为锚、不以已上传行为锚，正确规避"缺类也算合格"陷阱）；附件上传类型/大小限制走 `sys_param`/字典、超限拒绝；材料"通过"后不可直接替换（须先退回）、锁定禁改；两级审核三态（通过/退回/不通过）状态机服务端强制；process_material **读+写两侧数据范围**正确（写侧 collegeId 取自 student 实体、跨院 403）；附件存储复用 platform-file 的 MinIO（未另造）。**并真收口"可编辑态守卫"backlog**：material 新增守卫，且**回填 `StudentServiceImpl`/`TrainingProfileServiceImpl`**（在审/已通过记录禁写）+ 新增回归反例。独立 `mvn verify` GREEN（18/18），type-check/build 绿。6 个 Minor（口径/命名/契约细节）入 backlog，不阻断。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff 3efce2b..HEAD`（单提交 aece971）。
2. **干净构建 + 独立重跑反例**：`mvn -B -ntp verify`（先 docker 起 MinIO/MySQL/Redis）→ **BUILD SUCCESS**，Failsafe 自动执行 `Phase2SecurityIT 2/2` + `Phase3StudentIT 7/7` + `Phase4TrainingIT 5/5` + `Phase5MaterialIT 4/4` ＝ **18/18**（Phase3/4 增条数为回填守卫的回归反例）；`type-check`/`build` 绿。
3. **读码裁决**：`ProcessMaterialServiceImpl`（AT-06 聚合 / 上传校验 / 替换锁 / ensureEditable）、`DataScopeSqlHandler`(process_material 规则)、写侧 `ensureCanWriteStudent`、`StudentServiceImpl`/`TrainingProfileServiceImpl` 守卫回填、`V11__process_material.sql`、`Phase5MaterialIT`。
4. **三路独立代理**（AT-06·上传·审核 / 数据范围·守卫回填·MinIO / 质量·DoD·迁移·治理）——均 PASS。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 四类合格判定 / 上传 / 审核三态 / 替换锁 与 plan + phase-05 一致 |
| D2 验收清单 | ✅ | AT-06 复跑通过 |
| D3 AT 验收 | ✅ | AT-06 通过；material 读+写数据范围续接 |
| D4 红线合规 | ✅ | 数据范围读+写、文本化 String、留痕、不硬编码（四类/限制走字典/sys_param） |
| D5 代码质量 | ✅ | 分层/事务/Result/BaseEntity；6 个口径/命名 Minor |
| D6 安全 | ✅ | 写侧 collegeId 取自实体、跨院拒绝；预览走限时预签名 URL |
| D7 构建与运行 | ✅ | `mvn verify` 绿、type-check/build 绿；批量下载为请求级返回不起常驻服务；未自起服务 |
| D8 测试 | ✅ | Phase5MaterialIT 4/4 接 failsafe，覆盖 AT-06/上传/替换/数据范围反例 |
| D9 数据库 | ✅ | 仅 V11 新增、V1–V10 未改；幂等、索引/注释、文本字段 VARCHAR |
| D10 回归 | ✅ | Phase2/3/4 共 14 条回归通过（含守卫回填新增反例） |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅ |

---

## 四、做得好

- **AT-06**：`ProcessMaterialServiceImpl.processStatus` 遍历字典四类（非已上传行）做 `allMatch(passed)`；缺类→该类 0 行→不通过→不合格；任一类"不通过"→不合格；四类全通过→合格。反例三场景齐全。
- **上传/替换/锁**：类型(`file.material.allowedTypes`)+大小(`file.maxSize.material`)走 sys_param；通过后 `locked=1`、`ensureEditable` 拒绝替换/删除（须先退回解锁）。
- **审核三态**：通过/退回/不通过；初审=学院教务员、复审=学院负责人；退回/不通过须填意见。
- **数据范围读+写**：process_material 注册进 `DataScopeSqlHandler`（college_id 院 / student_id 本人）；写侧 collegeId 取自 student 实体 + `ensureCanWriteStudent` 跨院 403；@DataScope 覆盖列表/审核/下载。
- **MinIO 复用**：上传/预签名/批量下载走 platform-file 的 `FileService`/`MinioClient`，未另造存储；批量下载为请求级 ZIP 返回（不起常驻服务）。
- **可编辑态守卫 backlog 真收口**：material `MaterialStatus.editable()` + 回填 `StudentStatus`/`TrainingStatus.editable()` 与各 service 的 `ensureEditable`；新增回归 `Phase3StudentIT.studentInReviewCannotBeEditedOrConfirmed`、`Phase4TrainingIT.trainingProfileInReviewCannotBeSavedAgain`。Phase4 b1/b2/b3 与 Phase3 confirm 同类 Minor 一并清除。

---

## 五、Minor（入 backlog，不阻断）

- 类别合格口径 `passedCount>0 && failedCount==0` 比文档默认（"≥1 复审通过即合格"）更严；与 phase-05 §6.1/§10 口径需确认是否需配 sys_param。
- `material:view` 权限点规格有名、种子未建（实际用 `student:view` 覆盖，功能正确）——补种子或改文档对齐。
- id 定向写接口（delete/replace/submit）跨院返回 404（而非 403）——避免存在性泄漏，可接受；如需统一 403 可去 @DataScope 仅靠服务层硬校验。
- `SECOND_REJECTED` 重新提交在 `review.return.target=SECOND_REVIEW` 时可绕过初审（默认 FIRST_REVIEW 安全）——确认是否有意。
- `DEFAULT_MATERIAL_MAX_SIZE` 源码字面量与 sys_param 默认重复（仅兜底）。
- `Phase3StudentIT` 守卫反例只跑 update 路径，未覆盖 confirm 端点（代码已共用 fill 守卫，仅测试覆盖面）。

---

## 六、放行

1. `PROGRESS.md` Phase 5 置 **✅ 已复核**；AT-06 首验通过；可编辑态守卫 backlog 标记已收口（含 Phase3/4 同类 Minor）。
2. 合并 `main`（本地私有、无远程、不 push）；启动 Phase 6。
3. 6 个 Minor 进 backlog。
