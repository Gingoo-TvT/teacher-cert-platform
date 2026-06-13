# Phase 1 · 基础数据与字典管理（M01）

> 优先级 P0 · 依赖：Phase 0 · 任务：T-011~T-022 · plan §5.2 / §6.1 / §15.3 / 附录B
> 目标：建成所有字典、行政区划、任教学科标准库、学校/学院/专业及联动配置——这是 M03/M04/M09/M10 的数据底座。**AT-05 的"学科来自标准库、禁自由填写"在本阶段打基础。**

## 1. 范围
字典（17 类）、行政区划三级、任教学科标准库（独立表 + 导入器）、学校/学院/专业、专业多培养目标、培养目标联动配置。

## 2. 数据库（V2/V3/V4/V5 迁移）
- `V2__dict.sql`：`sys_dict_type`、`sys_dict_item`(parent_code, year_version, ext_json, sort, status)、`sys_region`(code,name,parent_code,level)、`teaching_subject`(segment_code,category_node,subject_code,subject_name,is_category,keyword,year_version,status)、`sys_college`、`sys_major`(...,pilot_scope_flag,year_version)、`major_training_goal`、`training_goal_config`。
- `V3__dict_seed.sql`：plan §5.2 全部 17 类字典标准值 + 学校 10588 + 省码 44。
- `V4__region_seed.sql`：广东省县级以上行政区划种子，支撑省→市→区县联动。
- `V5__subject_seed.sql`：幼儿园1/小学23/初中28/高中·中职文化课27 完整项 + 中职专业课示例与类别节点（完整 358 项预留模板导入）。

## 3. 必须逐字一致的字典（验收点）
身份类型(5)、身份证件类型(4)、学历层次(4)、专业培养目标(5)、实习组织方式(2)、实习地点(5)、任教学段(5)、面试组织方式(2)、材料类别(4)、测试结论(4)、视频评分维度(9)——值必须与需求报告**逐字一致**（含括号说明，如"企业（职业技术教育专业）"）。

## 4. 接口清单
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/dict/{typeCode}/items` | `dict:view` | 按类型取启用项（缓存） |
| POST/PUT/DELETE | `/api/dict/type`、`/api/dict/item` | `dict:manage` | 字典维护 |
| GET | `/api/region/children?parent=` | `dict:view` | 下级区划 |
| GET | `/api/region/path?code=` | `dict:view` | 区划全路径+完整文本 |
| GET | `/api/subject?segment=&keyword=&category=` | `dict:view` | 任教学科（含 is_category 不可选过滤） |
| POST | `/api/subject/import` | `subject:import` | 学科库 Excel 导入（年度版本） |
| GET/POST/PUT | `/api/college`、`/api/major` | `college:manage`/`major:manage` | 组织与专业 |
| GET/PUT | `/api/major/{id}/training-goals` | `major:manage` | 专业-多培养目标 |
| GET/PUT | `/api/training-goal-config` | `major:manage` | 培养目标联动配置 |

## 5. 核心逻辑
- **字典缓存**：`GET items` 走 Redis，`@CacheEvict` 在维护时失效；`@Dict` 注解在出参做 code→value 翻译。
- **区划完整文本**：`path(code)` 拼 `省+市+区县`，供生源地（K 列）导出。
- **任教学科查询**：按 `segment_code` 过滤；`keyword` 模糊命中 `subject_name`/`keyword`；返回时**类别节点 `is_category=1` 标记为不可选**（前端置灰）。
- **培养目标联动配置** `training_goal_config`：每个培养目标存 `default_segment`、`allowed_segments_json`、`default_internship_location`、`allowed_internship_locations_json`——供 Phase 4 联动与 V-09/V-10 校验。

## 6. 前端
- 字典管理页（类型/项、年度版本、启停）。
- 行政区划维护 + 可复用 `RegionCascader`。
- 任教学科库页（维护/导入）+ `SubjectSelect` 组件（按学段过滤、关键词、分类、最近使用、**类别禁选**）。
- 学校/学院/专业维护页（多培养目标、联动配置编辑、试点标识、年度版本）。

## 7. 验收清单
- [ ] 17 类字典标准值与需求报告逐字一致（抽查身份类型 5 项、实习地点 5 项含括号、测试结论 4 项）。
- [ ] 字典取值走缓存；维护后缓存即时刷新（改一项→再查为新值）。
- [x] 行政区划省→市→区县三级联动；区县反查返回完整文本（如"广东省广州市天河区"）。
- [x] 任教学科按学段返回正确数量（幼儿园1/小学23/初中28/高中·中职文化课27）。
- [x] 幼儿园学段仅返回"幼儿园"一项。
- [x] **中职专业课类别节点不可被选择**（接口标记 + 前端置灰）（AT-05 基础）。
- [x] 学科库支持关键词搜索与年度版本；导入新版本后可按年度启用。
- [x] 一个专业可配置多个培养目标；保存后可查询。
- [x] `training_goal_config` 可维护，培养目标可查出默认/可选 学段与实习地点。

### T-011 数据库验收记录
- [x] `V2__dict.sql` 已创建 8 张表：`sys_dict_type`、`sys_dict_item`、`sys_region`、`teaching_subject`、`sys_college`、`sys_major`、`major_training_goal`、`training_goal_config`。
- [x] 表结构覆盖 `parent_code/year_version/ext_json/sort/status`、三级区划、任教学科类别节点、专业试点标识、多培养目标与培养目标联动配置字段。
- [x] 关键唯一索引与查询索引已创建；`year_version` 默认 `GLOBAL` 且非空，避免 MySQL 唯一键被 `NULL` 绕过。
- [x] Flyway 启动迁移通过，`flyway_schema_history` 显示 V1/V2 `success=1`。
- [x] 反例验证通过：重复 `sys_dict_type.type_code`、重复 `(type_code,item_code,year_version)` 均被唯一约束拒绝，临时验证数据无残留。

### T-012 字典接口验收记录
- [x] 字典类型/字典项新增、修改、删除接口已实现，Controller 只做入参/返回，业务校验在 `DictService`。
- [x] 写操作已标注 `@AuditLog(bizType = "dict", ...)`，查询接口已标注 `@DataScope`。
- [x] `/api/dict/{typeCode}/items` 默认只返回启用项，并通过 Redis key `dict:items:{typeCode}` 缓存。
- [x] 维护字典项后即时删除对应 Redis 缓存，再查返回更新值并重新写缓存。
- [x] 反例验证通过：重复 `(type_code,item_code,year_version)` 返回业务错误；有子项时删除字典类型、修改类型编码均返回业务错误。

### T-013 字典标准值验收记录
- [x] `V3__dict_seed.sql` 已创建 17 个字典类型，并预置学校 `10588/广东技术师范大学` 与省码 `44/广东`。
- [x] 明确标准值逐字核对通过：身份类型 5、身份证件类型 4、学历层次 4、专业培养目标 5、实习组织方式 2、实习地点 5、任教学段 5、面试组织方式 2、性别 2、材料类别 4、测试结论 4、视频评分维度 9。
- [x] 实习地点含全角括号标准值：`企业（职业技术教育专业）`、`海外（汉语国际教育专业）`；视频评分维度含 `课件/板书`。
- [x] `exemption_subject`、`exemption_basis`、`cert_issuer` 按确认单第 12 项和 plan“学校维护”口径仅建类型、初始置空。
- [x] Flyway 启动迁移通过，`flyway_schema_history` 显示 V3 `success=1`；运行侧按 type_code/item_code 数量校验通过。

### T-014 行政区划接口验收记录
- [x] `RegionService` 已实现 `children(parent)`、`path(code)`、`fullName(code)`、`validateTriplet(province, city, county)`。
- [x] `/api/region/children?parent=` 支持 parent 为空查省级、传父级查下级；叶子区县返回空列表。
- [x] `/api/region/path?code=` 返回 root→leaf 节点与完整文本；临时验证 `440106` 返回 3 级路径，UTF-8 HEX 对应“广东省广州市天河区”。
- [x] 查询接口已标注 `@DataScope(alias = "sys_region")`；区划代码全链路 `String`，不做数值转换。
- [x] 反例验证通过：不存在代码、非 6 位代码、不存在 parent 均返回业务错误。

### T-015 行政区划种子验收记录
- [x] `V4__region_seed.sql` 已预置广东省县级以上行政区划：省级 1 条、地级市 21 条、县级区划 122 条，代码均以字符串写入。
- [x] 广东省→广州市→天河区正式种子链路可联动：`children?parent=440000` 返回 21 个市，`children?parent=440100` 返回 11 个区且包含 `440106/天河区`。
- [x] `path?code=440106` 返回 `广东省广州市天河区`，UTF-8 HEX 为 `E5B9BFE4B89CE79C81E5B9BFE5B79EE5B882E5A4A9E6B2B3E58CBA`。
- [x] 东莞市、中山市按县级以上行政区划口径作为无区县级子节点的地级市处理；`children?parent=441900` 返回空数组。
- [x] Flyway 启动迁移通过，`flyway_schema_history` 显示 V4 `region seed` 且 `success=1`。
- [x] 反例验证通过：`path?code=449999`、`path?code=44010601`、`children?parent=999999` 均返回业务错误。

### T-016 任教学科服务与导入器验收记录
- [x] 新增 `TeachingSubjectService`、`TeachingSubjectController`、实体/Mapper/VO/导入结果 DTO，提供 `/api/subject` 查询、`/api/subject/import` 导入、`/api/subject/validate` 可选校验、`/api/subject/recent` 最近使用接口。
- [x] 查询支持 `segment`、`keyword`、`category`、`yearVersion`；返回 `selectable=false` 标记类别节点不可选，供前端置灰。
- [x] 导入器使用 FastExcel，所有单元按字符串读取；校验必填、学段字典合法性、状态值、同文件 `subject_code + year_version` 重复；有错误时整批不入库。
- [x] 写操作 `import/recent` 已标注 `@AuditLog`；查询/校验/最近使用查询已标注 `@DataScope(alias = "teaching_subject")`。
- [x] 最近使用按 Redis `subject:recent:{userId}:{yearVersion}:{segmentCode}` 保存，记录前复用可选校验，查询时回表过滤停用/删除/类别节点。
- [x] 运行验证通过：临时 Excel 导入 3 条 `T016_IMPORT` 数据成功；错误 Excel 返回学段非法与文件内重复两条错误且无 `T016_BAD` 入库；`keyword=电子商务` 命中具体学科和类别节点，其中类别节点 `selectable=false`；跨学段、自由填写不存在学科、类别节点选择/记录最近使用均返回业务错误；临时数据和缓存已清理。
- [x] 数量型验收（幼儿园1/小学23/初中28/高中·中职文化课27）依赖 T-017 `V5__subject_seed.sql` 正式种子，T-016 已完成服务与导入能力。

### T-017 任教学科种子验收记录
- [x] `V5__subject_seed.sql` 已创建 `GLOBAL` 年度示例库：幼儿园 1、小学 23、初级中学 28、高级中学/中职文化课 27、中职专业课示例 10。
- [x] 中职专业课示例包含 3 个类别节点、7 个具体学科；类别节点 `is_category=1` 且 `category_node=subject_code`，具体学科归属父类别。
- [x] `subject_code` 使用学段前缀命名，避免不同学段同名学科触发 `(subject_code, year_version)` 唯一键冲突。
- [x] `flyway_schema_history` 显示 V5 `subject seed` 且 `success=1`；DB 校验无 `(subject_code, year_version)` 重复。
- [x] `/api/subject?segment=kindergarten` 仅返回 `幼儿园`；`segment=junior_middle_school` 返回 28 项；`segment=primary_school` 返回 23 项且包含 `书法/舞蹈/心理健康教育/体育与健康`；`segment=senior_middle_school` 返回 27 项且包含 `思想政治/通用技术`。
- [x] `keyword=电子商务` 命中 `sv_ecommerce` 具体学科和 `sv_cat_finance_commerce` 类别节点，其中类别节点 `selectable=false`；`validate` 选择类别节点、跨学段学科、自由填写不存在学科均返回业务错误，选择 `sv_ecommerce` 成功。
- [x] 完整中职专业课 358 项/107 类别节点本地未给完整清单，按确认单第 13 项保留为学校模板导入，不在种子中伪造完整库。

### T-018 学校/学院/专业与联动配置验收记录
- [x] 新增学院、专业、专业培养目标、培养目标联动配置实体/Mapper/DTO/VO/Service/Controller；Controller 仅使用 DTO/VO，不直接暴露 Entity。
- [x] `/api/college`、`/api/major` 支持列表、新增、修改、删除；专业详情返回学院名称和已配置培养目标；`yearVersion` 空值默认 `GLOBAL`，入参长度按 V2 表结构校验。
- [x] `/api/major/{id}/training-goals` 支持整体替换多培养目标；重复保存同一组、移除后再恢复同一培养目标均通过，不触发逻辑删除唯一键冲突。
- [x] `/api/training-goal-config` 可维护并返回培养目标默认/可选任教学段、默认/可选实习地点，返回值存储和展示均使用字典 `item_code`。
- [x] 写操作已标注 `@AuditLog`；查询接口已标注 `@DataScope`；V2 无外键/JSON schema 约束，引用完整性由服务层校验。
- [x] 反例验证通过：重复学院编码、重复 `(internal_major_code, yearVersion)`、停用/不存在学院下新增专业、非法培养目标编码、默认学段不在 allowed、使用中文显示值作为学段编码、超长 `yearVersion` 均返回业务错误。
- [x] 逻辑删除唯一键口径已验证：删除后的学院/专业同编码重建返回业务错误，不落数据库重复键 500；临时业务数据接口可见计数为 0，仅保留软删历史。

## 8. 测试用例
- T-DICT-1：维护字典项后再查 → 返回更新值（缓存刷新）。✅ T-012 已验证：`initial-value` → `updated-value`，缓存 `1 → 0 → 1`。
- T-REGION-1：`path("440106")` → 返回"广东省广州市天河区"。✅ T-015 已用正式广东种子复验。
- T-SUBJ-1：`segment=初级中学` → 返回 28 项。✅ T-017 已验证。
- T-SUBJ-2（反例）：尝试把某"类别节点"作为任教学科保存 → 后端拒绝。✅ T-017 已验证。
- T-SUBJ-3：`keyword=电子商务` 命中具体学科，类别父节点返回但 `selectable=false` 不可选。✅ T-017 已验证。
- T-GOAL-1：专业A 配置[小学教师,初中教师] → 查询返回两项。✅ T-018 已验证，重复保存同一组和移除后恢复同一培养目标均通过。

## 9. DoD
字典/区划/学科/组织/联动配置全部可维护、可联动、可缓存、可按年度版本；接口在 Swagger 可调；导入器对示例库可用。

## 10. 风险
- 任教学科完整库（尤其中职 358 项/107 类别）**不写死代码**，以模板导入；本阶段只做示例种子 + 导入器，完整库待学校提供（确认单第 13 项）。
- 字典值含中文括号/全角符号，种子脚本注意编码与转义，避免与导出标准值不一致（影响 AT-02）。
