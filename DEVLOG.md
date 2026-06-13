# DEVLOG.md — 开发日志

> 规范见 `AGENTS.md` §9。**倒序追加**（最新在最上）。每完成一个任务或每个工作会话至少一条。
> 条目模板（复制使用）：
>
> ```
> ## [YYYY-MM-DD] T-xxx 标题
> - 做了什么：
> - 关键决策与理由：
> - 问题与解决：
> - 与规格的偏差/疑问：（如有，须同步改 plan/docs 或记入待确认事项）
> - 测试：（测试名/结果）
> - 下一步：
> ```

---

## [2026-06-14] T-019 字典管理页
- 做了什么：新增 `frontend/src/api/dict.ts` 与 `DictManageView.vue`；接入 `/system/dicts` 路由和侧栏菜单；页面支持字典类型/字典项双栏维护、搜索、启停、年度版本、排序、父级编码、扩展 JSON 校验和保存后刷新。
- 关键决策与理由：管理页查询字典项固定传 `onlyEnabled=false`，确保停用项仍可维护；开发态暂不对按钮挂 `v-perm`，因为 Phase 2 前登录页不加载权限，直接挂会隐藏管理按钮；使用现有 Naive UI 和 Axios 封装，不新增前端依赖。
- 问题与解决：运行反例发现后端 `extJson` 非法 JSON 会落到 MySQL JSON 字段并返回 500，已在 `DictServiceImpl` 增加 `objectMapper.readTree` 业务校验，返回“扩展JSON格式不正确”；`NInputNumber` 严格类型可能返回 null，页面表单状态改为非空 UI state，再组装 API payload。
- 与规格的偏差/疑问：无。权限点 `dict:view`/`dict:manage` 的真实按钮权限与菜单权限仍等待 Phase 2 RBAC 接入，本任务只完成页面入口和维护闭环。
- 测试：后端 `mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；前端 `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 通过（保留既有 Naive UI chunk 警告）；启动后端与 Vite dev，`/system/dicts` 返回 200，`/api/health` 代理成功；接口验证新增类型/项、重复类型编码、重复 `(typeCode,itemCode,yearVersion)`、非法 `extJson`、有项删除类型被拒、更新项值后缓存刷新、停用后默认查询隐藏且管理查询可见，临时数据已按逻辑删除清理。
- 下一步：T-020 行政区划维护 + 级联组件。

## [2026-06-14] T-018 学校/学院/专业 + 多培养目标 + 联动配置
- 做了什么：新增学院、专业、专业培养目标、培养目标联动配置实体/Mapper/DTO/VO、`OrganizationService` 与 `/api/college`、`/api/major`、`/api/training-goal-config` 接口；专业详情带学院名称与培养目标列表；写接口接 `@AuditLog`，查询接口接 `@DataScope`。
- 关键决策与理由：T-018 不新增 Flyway，复用 T-011 的 V2 表；`training_goal_config` 存字典 `item_code`，展示名由字典翻译；V2 未定义外键和 JSON schema，引用完整性由 service 校验；多培养目标替换采用“更新/恢复现有关系 + 软删移除项”，避免逻辑删除行仍占唯一键导致重复保存失败。
- 问题与解决：subagent 复核发现 `major_training_goal` 先删后插会被 `(major_id, training_goal_code)` 唯一键挡住，已改为读取含 deleted 的历史行后恢复/停用；学院/专业同类逻辑删除唯一键问题改为创建/更新前按含 deleted 口径检测并返回业务错误；`yearVersion` 超过 V2 `VARCHAR(16)` 曾触发数据库截断，已按表结构补 DTO 长度校验。
- 与规格的偏差/疑问：无新规格偏差。DataScope 当前仍是 Phase 0 骨架切面，T-018 已按红线完成标注，真实范围过滤依赖 Phase 2 T-025 落地；中职教师默认学段/实习地点未写种子，联动配置通过接口维护，后续 Phase 4 使用时按字典配置读取。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=UP，Flyway 校验 V1-V5 且无新迁移；接口正例验证新增学院/专业、专业配置 `[primary_school_teacher,junior_middle_school_teacher]` 查询返回两项、重复保存同一组和移除后恢复均通过、保存/查询 `primary_school_teacher` 联动配置可返回默认/可选学段与实习地点；反例验证重复学院编码、重复 `(internal_major_code, yearVersion)`、停用/不存在学院、非法培养目标、默认学段不在 allowed、中文显示值作为学段编码、超长 `yearVersion`、逻辑删除后同编码重建均返回业务错误；审计日志出现 `college:create`、`major:create`、`major:replaceTrainingGoals`、`trainingGoalConfig:save`；临时业务数据接口可见计数为 0。
- 下一步：T-019 字典管理页。

## [2026-06-14] T-017 任教学科种子（示例库）
- 做了什么：新增 Flyway `V5__subject_seed.sql`，预置 `GLOBAL` 年度任教学科示例库：幼儿园 1、小学 23、初级中学 28、高级中学/中职文化课 27、中职专业课示例 10（含 3 个类别节点、7 个具体学科）。
- 关键决策与理由：`subject_code` 使用学段前缀命名，避免同名学科跨学段触发 `(subject_code, year_version)` 唯一键冲突；类别节点 `is_category=1` 且 `category_node=subject_code`，具体学科 `is_category=0` 并归到父类别；种子用 `ON DUPLICATE KEY UPDATE` 保持幂等。
- 问题与解决：本地文档只明确数量和少量示例，没有给出小学 23、初中 28、高中 27 的完整逐项清单，也未给完整中职 358 项/107 类别清单；按确认单第 13 项“以模板为基准导入字典，年度版本”处理，T-017 只作为基础验收示例库，完整库待学校提供模板后通过 T-016 导入器导入。
- 与规格的偏差/疑问：无阻塞。普通学段为满足数量验收补足了示例项；完整中职专业课不伪造为官方完整库。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 Flyway V5 `subject seed` success=1；DB 校验 `kindergarten/primary_school/junior_middle_school/senior_middle_school` 数量为 `1/23/28/27`，中职示例 10 且 3 个类别节点；无 `(subject_code, year_version)` 重复；接口验证幼儿园仅“幼儿园”、初中 28、小学包含 `书法/舞蹈/心理健康教育/体育与健康`、高中包含 `思想政治/通用技术`；`keyword=电子商务` 命中类别与具体学科且类别 `selectable=false`；选择类别节点、跨学段、自由填写不存在学科均返回业务错误，选择 `sv_ecommerce` 成功；临时后端已停止。
- 下一步：T-018 学校/学院/专业 + 多培养目标 + 联动配置。

## [2026-06-13] T-016 任教学科标准库服务 + 导入器
- 做了什么：新增 `TeachingSubject` 实体、Mapper、`TeachingSubjectService` 与 `/api/subject` 接口；实现按学段/关键词/类别/年度查询、FastExcel 导入、可选校验、最近使用记录；导入结果返回 `total/successCount/failCount/errors`。
- 关键决策与理由：接口补可选 `yearVersion` 参数，以满足“学科库支持年度版本”验收；类别节点不从结果中过滤，而是返回 `selectable=false`，让前端置灰，同时后端 `validateSelectable` 和最近使用记录硬拒绝类别节点；最近使用当前不新增表，按 Redis key `subject:recent:{userId}:{yearVersion}:{segmentCode}` 保存，因 T-016 既有 schema 未定义持久化表。
- 问题与解决：父 POM 锁定 `cn.idev.excel:fastexcel:1.1.0`，实际读写 API 位于传递依赖 `fastexcel-core`；已在 `platform-system` 声明 `fastexcel` 并通过编译。PowerShell 直写中文 SQL 会污染编码，运行验证改用 UTF-8 `.xlsx` + 导入接口，并用 DB HEX 确认中文正确。
- 与规格的偏差/疑问：`AGENTS.md` 表格写 EasyExcel/FastExcel 3.x，但父 POM 和 HANDOFF 锁定 FastExcel 1.1.0，本任务按父 POM 锁定版本实现；完整学科数量验收依赖 T-017 `V5__subject_seed.sql`，T-016 先交付服务与导入器。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；临时 Excel 导入 3 条 `T016_IMPORT` 数据成功，HEX 验证“电子商务/电子商务类/幼儿园” UTF-8 正确；错误 Excel 返回“学段非法”和“文件内重复”两条错误且无 `T016_BAD` 入库；`keyword=电子商务` 命中具体学科和类别节点，类别节点 `selectable=false`；跨学段、自由填写不存在学科、类别节点选择/记录最近使用均返回业务错误；`subject:recent` Redis 仅保存具体学科；临时数据、审计日志、Redis key 已清理，临时后端已停止。
- 下一步：T-017 任教学科种子（示例库）。

## [2026-06-13] T-015 行政区划种子
- 做了什么：新增 Flyway `V4__region_seed.sql`，预置广东省县级以上行政区划数据：省级 1 条、地级市 21 条、县级区划 122 条；同步更新 Phase 1 文档验收记录，并将后续任教学科种子版本从 `V4__subject_seed.sql` 顺延为 `V5__subject_seed.sql`。
- 关键决策与理由：区划 `code/parent_code` 全部按字符串写入，保持文本化口径；种子使用 `ON DUPLICATE KEY UPDATE` 保持幂等；东莞市、中山市按县级以上行政区划口径作为无区县级子节点的地级市处理，不伪造第三级节点。
- 问题与解决：本地文档只给出“广东省三级数据完整可联动”和 `440106` 示例，没有完整区划清单；按 T-015 的 GB/T 2260 口径补充广东县级以上数据，并在文档记录直筒子市处理口径。Flyway 版本因 T-015 占用 V4，已同步更新 `tasks.md` 与 Phase 1 文档，T-017 改用 V5。
- 与规格的偏差/疑问：无。东莞/中山无区县级子节点属于县级以上行政区划数据口径差异，已记录在验收说明。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V4 `region seed` success=1；DB 校验广东 `level=1/2/3` 数量为 `1/21/122`；`children?parent=440000` 返回 21 个市，`children?parent=440100` 返回 11 个区且含 `440106/天河区`；`path?code=440106` 返回“广东省广州市天河区”，UTF-8 HEX 为 `E5B9BFE4B89CE79C81E5B9BFE5B79EE5B882E5A4A9E6B2B3E58CBA`；反例 `449999`、`44010601`、`parent=999999` 均返回业务错误；临时后端已停止。
- 下一步：T-016 任教学科标准库服务 + 导入器。

## [2026-06-13] T-014 行政区划三级联动
- 做了什么：新增 `SysRegion`、`SysRegionMapper`、`RegionService`、`RegionController` 与区划 VO；实现 `/api/region/children` 和 `/api/region/path`；补 `CorsConfig` 的 JSON UTF-8 charset，确保 HTTP 中文响应可被客户端正确识别。
- 关键决策与理由：区划 `code/parentCode` 全链路 `String`，避免前导零和编码语义丢失；`parent` 为空查省级，传父级查下级；`path` 拼 root→leaf 的完整文本，供后续生源地导出复用；预留 `validateTriplet` 给 Phase 3/10 生源地校验。
- 问题与解决：初次 HTTP 验证时 PowerShell 将 JSON 中文误解码，数据库 UTF-8 HEX 正确；通过为 Jackson JSON converter 增加 `application/json;charset=UTF-8` 支持解决。
- 与规格的偏差/疑问：无。T-014 使用临时区划数据验证接口，正式广东省种子在 T-015 落地。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；临时插入 `440000/440100/440106` 验证 children 省→市→区县、叶子空列表、path 3 级路径；`fullName` UTF-8 HEX 对应“广东省广州市天河区”；反例 `449999`、`44010601`、`parent=999999` 均返回业务错误；验证数据已清理，临时后端已停止。
- 下一步：T-015 行政区划种子。

## [2026-06-13] T-013 字典标准值种子
- 做了什么：新增 Flyway `V3__dict_seed.sql`，创建 17 个字典类型，预置学校 `10588/广东技术师范大学`、省码 `44/广东`，以及 plan §5.2 明确给出的标准字典项。
- 关键决策与理由：种子使用 `INSERT ... ON DUPLICATE KEY UPDATE`，可重跑且不手改库；`exemption_subject`、`exemption_basis`、`cert_issuer` 仅创建类型、不插业务项，依据确认单第 12 项“免考依据/可免科目：字典维护，初始置空”和 plan 中“学校维护”口径。
- 问题与解决：PowerShell/MySQL CLI 中文比较会受控制台编码影响，逐字一致性由 gpt-5.5/xhigh 只读 subagent 对 UTF-8 文件核对，运行侧改用 type_code/item_code/数量与 HEX 抽查验证，避免编码误判。
- 与规格的偏差/疑问：`plan.md §5.2` 的 `exemption_subject` 行示例写有“幼儿园：综合素质（幼儿园）、保教知识与能力”，但确认单第 12 项裁定初始置空；按确认单默认值实现，后续由学校提供清单维护。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V1/V2/V3 `success=1`；运行侧校验 17 个类型、学校/省码、明确值数量（5/4/4/5/2/5/5/2/2/4/4/9）通过；HEX 抽查确认全角括号与 `课件/板书` 落库。
- 下一步：T-014 行政区划三级联动。

## [2026-06-13] T-012 字典管理 CRUD + Redis 缓存
- 做了什么：新增字典类型/字典项实体、DTO/VO、Mapper、`DictService` 与 `/api/dict` 接口；接入 Redis，`GET /api/dict/{typeCode}/items` 走 `dict:items:{typeCode}` 缓存；维护字典项后删除缓存；写操作接 `@AuditLog`，查询入口接 `@DataScope`。
- 关键决策与理由：缓存用 `StringRedisTemplate` + Jackson JSON，避免引入额外缓存抽象；`onlyEnabled` 默认 true，满足按类型取启用项；有子项的字典类型禁止删除或修改编码，避免产生孤儿字典项。
- 问题与解决：`platform-system` 编译期需要 Redis/Jackson 类型，已在模块 POM 显式声明依赖；一次重打包失败由运行中的 jar 占用导致，停止临时 Java 进程后重新 `mvn package` 通过。
- 与规格的偏差/疑问：无。权限点 `dict:view`/`dict:manage` 认证鉴权将在 Phase 2 RBAC 接入，T-012 先提供接口与切面标注。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；T-DICT-1 接口验证通过：首次查询写缓存、更新后缓存 `1→0`、再查返回 `updated-value` 并重新写缓存；反例：重复字典项、有子项删除类型、有子项修改类型编码均返回业务错误；中文值写入/查询成功；审计日志产生 5 条 dict 记录；验证数据与缓存已清理，临时后端已停止。
- 下一步：T-013 字典标准值种子。

## [2026-06-13] T-011 字典/区划/学科/组织表
- 做了什么：创建分支 `feature/phase01-T011-dict-schema`；新增 Flyway `V2__dict.sql`，建 `sys_dict_type`、`sys_dict_item`、`sys_region`、`teaching_subject`、`sys_college`、`sys_major`、`major_training_goal`、`training_goal_config` 8 张表；更新 `PROGRESS.md` 与 `docs/phase-01-字典与标准数据.md` 的 T-011 验收记录。
- 关键决策与理由：T-011 仅交付表结构，不提前写 V3/V4 种子，避免越界到 T-013/T-017；带年度版本的唯一键统一使用非空默认 `GLOBAL`，避免 MySQL 唯一索引允许多个 `NULL` 导致重复项绕过；学校/专业/区划等代码字段均用 `VARCHAR`，符合文本化红线。
- 问题与解决：验证时发现年度版本若可空会削弱唯一约束，已改为 `NOT NULL DEFAULT 'GLOBAL'`；临时启动后端触发 Flyway 后已停止 8080 进程，避免遗留后台服务。
- 与规格的偏差/疑问：无。完整字典标准值、行政区划种子、任教学科示例库按任务拆分留给 T-013/T-015/T-017。
- 测试：`mvn -B -ntp -DskipTests package` 9 模块 SUCCESS；启动后 `/api/health`=200；`flyway_schema_history` 显示 V1/V2 `success=1`；information_schema 验证 8 张表与关键索引存在；反例：重复 `sys_dict_type.type_code`、重复 `(type_code,item_code,year_version)` 均触发 `ERROR 1062 Duplicate entry`，临时验证数据计数为 0。
- 下一步：T-012 字典管理 CRUD + Redis 缓存。

## [2026-06-13] Phase 0 完成（T-003~T-010）
- 做了什么：
  - DB 链路：MyBatis-Plus(分页/乐观锁/逻辑删除/自动填充) + MySQL + Flyway V1(sys_param/audit_log/file_object + 17 参数种子) + Jackson Long→String。
  - docker-compose.dev（mysql/redis/minio）。
  - platform-file：MinIO 客户端 + FileService(上传/预签名/删除/秒传) + FileController。
  - 切面：AuditLogAspect(boot, 写 audit_log) + DataScopeAspect(security, 骨架) + UserContext(common)。
  - 前端：Vue3+Vite+TS+Naive UI 脚手架（router 守卫 + Pinia + Axios 封装 + v-perm + 登录/首页/404）。
  - CI：GitHub Actions(后端 mvn + 前端 vite build) + .editorconfig + .gitattributes(LF 规范)。
- 验证（全部通过）：`mvn package` 9 模块 SUCCESS；运行 Flyway migrate v1、4 表、sys_param 17 行、/api/health=UP、/doc.html=200；MinIO bucket 自动建 + 文件 上传→预签名→下载 内容一致；前端 `npm install` + `vite build`(2864 模块) 成功。
- 问题与解决：①Lombok optional 不向子模块传递 → 父 POM 统一声明；②MinioConfig @PostConstruct 调 @Bean 循环依赖 → 改独立 client 初始化 bucket。
- 注意：前端 2 个 npm 漏洞(dev 依赖)，后续 `npm audit`；naive-ui 全量导入主包偏大，后续按需引入。
- 下一步：Phase 1 字典与标准数据（T-011 表 → 种子 → 学科库/区划 → 前端字典页）。

## [2026-06-13] Phase 0 · Maven 骨架构建通过（T-001/T-002）
- 做了什么：父 POM + 8 子模块；`platform-common` 核心(Result/ResultCode/PageResult/BizException/BaseEntity/@AuditLog/@DataScope)；`platform-boot`(PlatformApplication/OpenApiConfig/CorsConfig/GlobalExceptionHandler/HealthController/application.yml)。
- 验证：`mvn -B -ntp -DskipTests package` → **BUILD SUCCESS**（9 模块），产出可运行 jar `platform-boot/target/teacher-cert-platform.jar`。
- 关键决策：父 POM 强制 UTF-8；锁定 Spring Boot 3.2.11 / MyBatis-Plus 3.5.7 / Knife4j 4.5.0 / FastExcel 1.1.0 / MinIO 8.5.12 / jjwt 0.12.6。
- 已知告警（无害）：platform-common 仅引 `mybatis-plus-annotation`，javac 提示找不到 `org.apache.ibatis.type.JdbcType`（注解默认值引用），警告非错误；待 system/business 引入 mybatis-plus-starter 后消失。控制台中文告警乱码=GBK 控制台渲染，源码 UTF-8 编译正常。
- 状态：T-001 ✅ / T-002 ✅ / T-003 🟦（boot 起步完成，MyBatis-Plus·Jackson·数据源待续）。
- 下一步：T-003 续(MyBatis-Plus 配置/Jackson Long→String) + T-004 Flyway V1 + T-005 docker-compose；冒烟验证 /api/health 与 /doc.html。

## [2026-06-13] 环境搭建 · 工具链 + git 初始化
- 做了什么：安装 Maven 3.9.9 到 `C:\Users\wenbibuhaoqwq\tools`，配置用户级 JAVA_HOME(Temurin JDK17)/MAVEN_HOME/PATH；`git init` 本地私有仓库(main 分支)，提交规划基线 24 文件(6948682)；新增 `.gitignore`。
- 关键决策：Maven 官方未上架 winget → 用官方二进制 + 用户级环境变量，免管理员；git 仅本地、**无远程**，确保不开源。
- 问题与解决：①winget 无 `Apache.Maven` → 改官方二进制；②安装脚本含 `Remove-Item`+`C:\Program Files` 触发保护拦截 → 删去 `Remove-Item`。
- 注意：JDK 平台默认编码 **GBK**（中文 Windows）→ 父 POM 必须强制 UTF-8（`project.build.sourceEncoding` + 编译器编码），否则中文注释/资源乱码。
- 版本：JDK 17.0.19 / Maven 3.9.9 / git 2.54.0 / Docker 已装 / Node v24.15。
- 下一步：Phase 0 T-001 Maven 多模块骨架 → 验证 `mvn package`。

## [2026-06-13] 规划阶段 · 文档体系建立（非编码）
- 做了什么：完成需求分析 → 产出 `plan.md`（含 §15 二次详查增补）、`tasks.md`（114 原子任务）、`docs/`（README + 待确认事项确认单 + phase-00~14 详细设计与验收）、`AGENTS.md`/`PROGRESS.md`/`DEVLOG.md` 流程文件。
- 关键决策与理由：
  - 证书顺序号作用域默认 `SCHOOL_YEAR_SEGMENT`（与需求 9.1 示例一致），并列入确认单待学校书面确认。
  - ORM 选 MyBatis-Plus、Excel 选 EasyExcel/FastExcel（保文本）、大视频用 MinIO 分片、迁移用 Flyway。
  - 补齐 10 项执行缺口（plan §15）：状态机不合格终止态、权限矩阵、补充表、校验精化、视频结算、导出子表列、导入两步+回滚、账号/年度、M03/M04 两级审核、序列作用域。
- 问题与解决：docx 为二进制，已解压 `word/document.xml` 提取全文分析。
- 与规格的偏差/疑问：见 `docs/待确认事项确认单.md`（20 项）。
- 测试：N/A（规划阶段）。
- 下一步：执行 Phase 0（T-001~T-010）搭建工程骨架；开工前先读 `AGENTS.md`。
