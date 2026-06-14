# Phase 1 复核报告

> 规范见 `../REVIEW-GATE.md`。模板见 `REVIEW-TEMPLATE.md`。

- 阶段：Phase 1 · 基础数据与字典（T-011~T-022）
- 复核人：Claude（独立复核，不轻信自报）
- 日期：2026-06-14
- 复核范围：`git diff 7b8458b..cc4468a --stat` = 72 文件 / +6471 / -40；迁移 V2–V5；`platform-system` 实体/Mapper/DTO/VO/Service/Impl；`platform-boot` 6 个控制器（Dict/Region/TeachingSubject/College/Major/TrainingGoalConfig）；前端 4 API + 2 组件（RegionCascader/SubjectSelect）+ 4 页面。
- **判定：✅ PASS（CHANGES NOT REQUIRED）** — 无 Blocker、无 Major；7 项 Minor 入 backlog（不阻断放行）。

## 一、维度结论（D1~D11）
| 维度 | 结论 | 证据 / 说明 |
|---|---|---|
| D1 需求符合性 | ✅ | 子代理按 **UTF-8 代码点**核对 `V3__dict_seed.sql` vs `plan.md §5.2`：12 类"逐字一致"字典全部逐字符匹配（含全角括号 `企业（职业技术教育专业）` U+FF08/09、`课件/板书` 半角 `/`、计数）；`education_level.certLevelCode`(1/2/3/4) 与 `teaching_segment.certSegmentCode`(1/2/3/4/5) 映射符 `plan §6.6` 证书编号规则；区划完整文本拼接正确。 |
| D2 验收清单 | ✅ | `docs/phase-01 §7` 全部由 Claude 运行复跑通过：计数 幼1/小23/初28/高27、类别节点禁选、缓存刷新、区划反查、多培养目标、联动配置。 |
| D3 AT 验收 | ✅ | **AT-05 基础**（标准库 + 类别节点禁选 + 后端硬拒自由填 + 跨学段拒）运行验证通过；完整"培养目标→学段→学科"联动表单在 Phase 4 首验。 |
| D4 红线合规 | ✅ | R1 文本化（code/parentCode/subjectCode/year_version 全 String + VARCHAR）；R2 不硬编码（学段/培养目标/实习地点合法性运行时查字典）；R3 留痕（**全部写接口 @AuditLog**，逐个枚举无遗漏）；R5 Flyway（V2→V5 递增不复用、V1 未改、种子 ON DUPLICATE 幂等）；R6 @DataScope（所有查询接口已挂，真过滤 Phase 2）；R7 后端硬校验（service 层 requireSelectable/跨学段/extJson）。**Minor**：审计前后态字段未填（见问题 1）。 |
| D5 代码质量 | ✅ | 控制器瘦（DTO 入/VO 出、无 mapper 直调、无业务逻辑）；统一 `Result<T>`；`BizException`+`GlobalExceptionHandler`（不吞异常、无 System.out/printStackTrace）；多表写 `@Transactional(rollbackFor=Exception.class)`；逻辑删除 + 唯一键交互两种策略均正确。**Minor**：N+1、dict 计数口径、无 dup-key 专用 handler（问题 3/4/5）。 |
| D6 安全 | ✅（阶段适配） | Phase 1 无敏感 PII、无鉴权面（认证在 Phase 2）；无密钥入库、无明文越权路径。深度安全（鉴权/脱敏/越权/水印）随 Phase 2/3/7 闸门复核。 |
| D7 构建与运行 | ✅ | 独立**干净重建** `mvn clean package` 9 模块 BUILD SUCCESS；前端 `vue-tsc --noEmit` + `vite build` 通过（仅既有 Naive UI chunk 警告）；应用启动 Flyway 校验 5 迁移、schema v5、`/api/health`=UP、零启动异常。 |
| D8 测试（含反例） | ✅ | Claude 独立运行期 **15 条反例/关键用例全过**（见 §三）。 |
| D9 数据库 | ✅ | V2 DDL 表/列 COMMENT 齐全；唯一键齐全（`(type_code,item_code,year_version)`/region code/`(subject_code,year_version)`/college code/`(internal_major_code,year_version)`/`(major_id,training_goal_code)`/training_goal_config code）；`year_version NOT NULL DEFAULT 'GLOBAL'` 防 NULL 绕过唯一键；V3/V4/V5 种子幂等。 |
| D10 回归 | ✅ | `/api/health` UP；Phase 0 链路（dict/region/file/health）未破坏；Flyway `Successfully validated 5 migrations`，无脏迁移。 |
| D11 文档/进度 | ✅ | codex 正确将 Phase 1 置「待复核」**未自置 ✅**（符合闸门）；DEVLOG 有阶段小结 + 每任务条目；Swagger `/doc.html` 可调。本报告产出后由 Claude 置 ✅。 |

## 二、问题清单（全部 Minor，入 backlog，不阻断放行）
| # | 级别 | 问题 | 证据 | 期望 | 定位 |
|---|---|---|---|---|---|
| 1 | Minor→**Phase 3 前置** | 审计切面仅写 `bizType/operation/operatorId/ip`，`comment/oldStatus/newStatus/bizId/target` 留空 | `AuditLogAspect` | Phase 3 状态流转/审核接口落地前必须接入"前后状态/意见"，否则 AT-12 不达 | `platform-boot/.../aspect/AuditLogAspect.java:38-43` |
| 2 | Minor | 审计写失败仅 `log.warn`（best-effort、与业务非同事务） | 同上 | 敏感操作（作废/重开/导出）应评估"审计失败即失败" | `AuditLogAspect.java:37-46` |
| 3 | Minor | `toMajorVO` N+1：逐专业 `selectById(college)` + 每次重载 `training_goal` 字典 | 列表路径 O(rows) 往返 | 列表批量化（一次加载学院 + 字典 map） | `OrganizationServiceImpl.java:434-450` |
| 4 | Minor | `deleteType/updateType` 的 `countItems` 走 MP wrapper（自动 `deleted=0`），与 college/major"含软删"计数口径不一致 | — | 统一口径（按是否允许"类型下仅余软删项时删除"明确语义） | `DictServiceImpl.java` |
| 5 | Minor | `GlobalExceptionHandler` 无 `DuplicateKeyException/DataIntegrityViolationException` 专用处理 | 现 dup-key 全靠 service 预检 | 加专用 handler，兜并发创建竞态（否则落通用 500） | `platform-boot/.../handler/GlobalExceptionHandler.java` |
| 6 | Minor | `RegionCascader` `check-strategy="all"` 使中间省/市节点也可选 | `RegionCascader.vue:177` | 直筒市需要此特性；后端 `validateTriplet` 为硬门，UX 项；如需强制下钻可限叶+无子市 | 前端 UX |
| 7 | Minor→**上线前置** | V5 学科名为**示例合成**（满足计数 23/28/27），非官方权威清单 | `V5__subject_seed.sql` + DEVLOG T-017 已标注 | 正式上报前以学校/教育部官方学科库按模板导入替换（确认单#13）；**勿将示例库当权威库上报** | 运营/数据 |

**架构说明（非问题）**：6 个 `@RestController` 位于 `platform-boot`（`platform-system` 无 web starter，仅 boot 启用 web）。务实、与 Phase 0（FileController 在 boot）一致，可接受；后续若业务模块独立 web 化再统一。

## 三、独立复跑的反例 / 关键用例
**构建**：`mvn -B -ntp -DskipTests clean package` → 9 模块 BUILD SUCCESS；`npm run type-check` + `npm run build` → 通过。
**字典逐字**：子代理 UTF-8 代码点比对 V3 vs plan §5.2 → 12 类逐字一致、17 类型、3 类型（免考依据/科目/签发人）正确置空。
**运行期（应用 + docker 依赖，15/15 PASS）**：
1. `dict/identity_type/items` → 计 5 ✅
2. `dict/internship_location/items` → 计 5 且含全角括号 `企业（职业技术教育专业）`/`海外（汉语国际教育专业）` ✅
3. `dict/ability_test_conclusion/items` → 计 4 且含 `不合格`/`待确认` ✅
4. `region/path?code=440106` → `广东省广州市天河区` ✅
5–8. `subject?segment=` kindergarten/primary/junior/senior → 1/23/28/27 ✅
9. 中职 `subject` 列表 → 3 个类别节点 `selectable=false` ✅
10. `subject/validate` 选类别节点 `sv_cat_finance_commerce` → 拒「任教学科类别节点不可选择」✅
11. `subject/validate` 选具体学科 `sv_ecommerce` → 通过 ✅
12. `subject/validate` 跨学段（primary_school + sv_ecommerce）→ 拒 ✅
13. `subject/validate` 自由填写不存在学科 → 拒 ✅
14. 字典缓存刷新：建临时项 rv1 →（缓存）→ 改 rv2 → 再查得 rv2 且无残留 rv1 ✅
15. 重复学院编码 → 拒「学院编码已存在」✅

## 四、结论与放行
- **判定：✅ PASS。** Phase 1 满足全部硬红线（R1/R2/R3/R5/R6/R7）、D5 架构标准、D7 构建运行、D8 反例；逻辑删除/唯一键处理两种形态均正确；字典逐字一致。
- `PROGRESS.md`：Phase 1 → **✅ 已复核**；AT 跟踪 **AT-05 首验（P1 基础）** 记 ✅（完整联动 P4 复验）。
- **放行**：准予开始 **Phase 2（账号角色权限，T-023~T-029）**；main 快进合并至复核提交（本地私有，无远程/不 push）。
- **Backlog（不阻断）**：问题 1（审计前后态）**指派 Phase 3 开工前必须完成**；问题 7（示例学科库）**上线前必须替换为官方库**；问题 2~6 随相关阶段顺手优化。

复核人：Claude　·　日期：2026-06-14
