# Phase 13 复核报告 — 系统管理与审计（T-104~T-108）

| 项 | 值 |
|---|---|
| 阶段 | Phase 13 系统管理与审计（P0，最后一个 P0） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `8401a7e`（feat T-104~T-108，单提交） |
| 增量基线 | `e62e9de..HEAD`（约 29 文件） |
| 迁移 | 新增 `V19__system_audit.sql`（backup_record）；V1–V18 未改动 ✓（system:*/audit:* V8 预种） |
| **判定（轮次1 · 06-17）** | **❌ 退回（CHANGES REQUESTED）** |
| 计数（轮次1） | **Blocker × 0 · Major × 1 · Minor × 4（入 backlog）** |
| **最终判定（轮次2 · 06-17）** | **✅ PASS — B1 已修，AT-12 审核全留痕达成（mvn verify 61/61）** |

---

## 〇、复核轮次 2（2026-06-17）：B1 已修复 → ✅ PASS

codex 在原分支单提交 `3c65d4f` 修复，增量 7 个业务 service + `AuditLogServiceImpl` + IT，未动迁移(V1–V19)/治理/前端。

- **B1（Major）已闭环**：在所有主要审核/状态流转 op 显式 `auditLogService.record(bizType,bizId,target,operation,old,new,comment)`（捕获 oldStatus → updateById → record，附加非破坏）：**student first/second、training first/second、exemption first/second、material first（second 已有）、cert void/reissue（correct 已有）、video settle/thirdReview/arbitrate/confirm、exchange rollback**；各加 `xxxTarget()`（id/年度/学生/…）可定位；`AuditLogServiceImpl.record` 改 **best-effort**（try/catch→log.warn，审计落库失败不中断主业务、非事务边界不污染）。
- **反例 `majorReviewFlowsWriteRichAuditAndCanBeQueriedByStudent`**：student/training/exemption 复审退回各产 audit old=SECOND_REVIEW/new=SECOND_REJECTED + bizId/comment/operator/IP/target、cert 作废 old=ISSUED/new=VOIDED + 原因；并 `/api/audit/log?studentId=…` 可查到上述复审记录（**按学生可查**）。AT-12 §7①②达成。
- **独立验证**：`mvn -B -ntp verify` GREEN **61/61**（Phase13 6/6，新增富审计反例 + 回归 Phase2~12 共 55 全绿）；前端未改动（沿用上轮绿）；V1–V19 与治理未动；remote 空、工作树干净；单提交 `3c65d4f`。
- 结论：AT-12 审核全留痕（前后状态/意见/对象 + 按学生查）达成、附加非破坏未回归 → **PASS**，合并 `main` 放行 Phase 14（收口）。

---

## 一、结论（轮次1 退回时的记录，保留备查）

Phase 13 大部分到位且无需返工：**参数改即生效**（`updateParam` 校验类型/范围/已知枚举、仅改可编辑 param_value、不动种子 key；反例 `video.diffThreshold` 12→8 后分差 10 即进 NEED_REVIEW）、**审计不可删**（`rejectAuditDelete` 普通管理员 DELETE → 403 且删除尝试本身留痕）、**审计查询数据范围**（`auditCollegeScope(resolve("audit:view"))` 学院只见本院、反例证不含他院）、**脱敏鉴权**（无 `export:sensitive` 取明文证件号 → 403）、**登录留痕**（AuthService 登录写 auth/login/SUCCESS）、`AuditLogAspect` 抽取 `AuditIp` 为非破坏重构、V19 backup_record + `triggerBackup` + 《备份与恢复手册》产出。独立 `mvn verify` 60/60（含回归 Phase2~12 共 55 全绿——附加改动非破坏），前端 type-check/build 绿。

**但 AT-12「审核全留痕」未达成（1 个 Major，须修复后放行）**：`@AuditLog` 基础切面**仅记 bizType/operation/operator/IP/time，无 bizId/target/前后状态(old/new)/意见(comment)**（切面注释亦承认"前后状态/对象由业务后续补充"）；而**完整审计（含 bizId/target/old/new/comment）仅在 `material.secondReview` + `cert.correct`（+login/delete-rejected）补充**。故主要审核流程——**学生基本信息/专业培养/免考 的 初审·复审·确认、过程性材料 初审、证书 作废·重开、视频 复审·确认·仲裁、导入回滚**——的审计行**无法定位具体业务记录（无 bizId）、无状态变化、无审核意见**。这同时击穿 AT-12 §7①（"均留…意见…前后状态"）与 §7②（"审计可按**学生**/批次/前后状态查询"——多数审核行无 bizId/studentId 关联，按学生查不到其复审记录）。AT-12 仅 material 复审退回单路达标，"全留痕"名不副实。

按 `REVIEW-GATE §5`（核心 AT 首验未达成）判 **退回**。退回聚焦 B1（审计补全）；其余 Phase 13 功能无需返工。修复后只复核增量 + 回归。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff e62e9de..8401a7e`（单提交；V1–V18 与治理文档未改）。
2. **干净构建 + 重跑反例 + 全回归**：`mvn -B -ntp verify` → **BUILD SUCCESS 60/60**（Phase2~12 共 55 回归 + `Phase13SystemAuditIT 5`）；前端 type-check/build 绿。
3. **读码裁决（100% 增量）**：`AuditLogAspect`（确认仅记 bizType/operation/operator/IP）、`AuditLogServiceImpl`（record 重载）、`SystemManagementServiceImpl`（参数/审计查询/不可删/备份）、`AuditQueryMapper`、3 个被改既有文件 diff（aspect 重构 / `ProcessMaterialServiceImpl.secondReview` old/new / `AuthService` 登录留痕，均附加非破坏）、`V19`、`Phase13SystemAuditIT`、`grep auditLogService.record(` 全量调用点。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ⚠️ | 参数/审计查询/脱敏/备份 达标；审核留痕完整性（前后状态/对象）不全（B1） |
| D2 验收清单 | ⚠️ | 参数改即生效/不可删/脱敏/数据范围 过；"均留前后状态/意见"+"按学生查"未全达（B1） |
| D3 AT 验收 | ❌ | **AT-12 仅 material 复审退回单路达标**，主要审核流程留痕不完整 |
| D4 红线合规 | ✅ | 审计查询数据范围、脱敏鉴权、不硬编码（参数走 sys_param） |
| D5 代码质量 | ✅ | 分层/校验/Result；既有改动附加非破坏 |
| D6 安全 | ✅ | 审计不可删 + 删除留痕、明文导出鉴权、审计查询按范围 |
| D7 构建与运行 | ✅ | `mvn verify` 60/60、type-check/build 绿；未自起常驻服务 |
| D8 测试 | ⚠️ | 5 反例覆盖参数/不可删/脱敏/范围 + material 复审 old/new；其余审核路径 old/new/bizId 未覆盖（因未实现） |
| D9 数据库 | ✅ | 仅 V19 新增、V1–V18 未改；backup_record 文本字段/索引 |
| D10 回归 | ✅ | Phase2~12 共 55 条回归全绿（附加改动非破坏） |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅；产出备份手册 |

---

## 四、做得好（无需返工）

- **参数改即生效**：`updateParam` 校验（类型/范围/已知枚举 cert.seq.scope·video.arbitrate.mode·review.return.target·validate.name.mode·cert.*.code 数字）、仅改可编辑 param_value、不动种子 key；反例证 `video.diffThreshold`=8 后业务即时生效。
- **审计不可删 + 删除留痕**：普通管理员 DELETE → 403，且 `deleteRejected` 审计行记录该尝试；日志不物理删。
- **审计查询数据范围**：`audit:view` 学院只见本院（经业务记录/operator 学院解析）、SCHOOL/SYSTEM 全量；反例证不含他院。
- **脱敏鉴权 + 登录留痕**：无 `export:sensitive` 取明文证件号 403；登录写 auth/login/SUCCESS。
- **非破坏**：`AuditLogAspect` 抽 `AuditIp` 重构、`ProcessMaterialServiceImpl`/`AuthService` 仅附加 record 调用；55 条回归全绿。
- V19 backup_record + 触发占位 + 《备份与恢复手册》（MySQL 全备+binlog 时间点恢复、MinIO 版本化、逻辑删除保留）。

---

## 五、问题清单（须修）

| 编号 | 级别 | 维度 | 问题 / 证据 | 期望 |
|---|---|---|---|---|
| **B1** | Major | AT-12 审核全留痕 | `AuditLogAspect`(:36-41) 仅记 bizType/operation/operator/IP，无 bizId/target/old-new/comment；完整审计仅 `ProcessMaterialServiceImpl.secondReview`(:213) + `CertificateServiceImpl.correct`(Phase9)。主要审核流程（student/training/exemption 初审·复审·确认、material 初审、cert 作废·重开、video 复审·确认·仲裁、import 回滚）审计行无 bizId/前后状态/意见 → 无法定位记录、无法按学生查其复审、无状态变化。AT-12 §7①②未全达成。 | 在上述审核 op 显式 `auditLogService.record(bizType,bizId,target,operation,old,new,comment)`（pattern 已有，附加非破坏），或增强 `@AuditLog` 切面经返回值/SpEL 携带 bizId/target/old-new；补 IT：至少 student/training/exemption 复审退回 + cert 作废 的 old/new+bizId+意见 反例；`mvn verify` 回归全绿。 |

---

## 六、Minor（入 backlog，可随 B1 一并清）

- `triggerBackup` 直接置 status=COMPLETED（无真实备份执行，仅记录+指向手册）——一期演练记录可接受，建议注明"记录性占位"。
- 审计/备份 `list` 全量 `selectList` 包 `PageResult`（非真分页，复发）；审计量大时建议分页 + 时间范围必填。
- `AuditQueryMapper` 用原生 XML/注解多表 join 解析学院——确认参数绑定无注入（已用 `#{}`，但 join 面较大，建议加注释/索引）。
- `@AuditLog` 未记 comment/old-new 是设计性留白（注释已说明），但应在本阶段统一补齐审核 op，避免散落遗漏（与 B1 同源）。

---

## 七、退回处理

1. 阶段在 `PROGRESS.md` 置 **复核退回**；**不合并 main、不置 ✅**；AT-12 维持 `[~]`。
2. codex 在原分支 `feature/phase13-T104-system-audit` 修 **B1**（补全主要审核 op 的完整审计：bizId/target/old/new/comment），补多路反例；确保 `mvn verify` 回归全绿、附加非破坏；DEVLOG 记修复与覆盖清单。
3. 重交后复核方**只复核增量 + 回归**，重点复跑：student/training/exemption 复审 + cert 作废 等审计含 old/new/bizId/意见、按学生可查、Phase2~12 未回归。直至 PASS。
