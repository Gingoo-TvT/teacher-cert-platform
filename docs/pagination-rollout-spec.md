# P1-1 真分页 Rollout 规格（Phase 44e-contract 定契约 → Sonnet 机械铺开）

> 背景：全站 ~11 服务文件 14 处列表接口为「`selectList` 全表 → `new PageResult<>(records.size(), records)`」的**假分页**（全量查后包壳 + 前端客户端分页），千级学生后列表接口内存放大、变慢（launch-readiness-plan.md §2 / P1-1）。
>
> 本规格由 **Phase 44e-contract**（Opus）落定：① 分页拦截器就绪并**验证顺序正确**；② 定 `page`/`size` 契约与向后兼容默认；③ 端到端转好 **2 个样例**（Student 数据范围列表 + 系统参数非范围列表）并以 IT 证明「分页 × 数据范围」组合正确；④ 本文给出**逐字可抄的 before→after 配方 + 剩余 12 处清单**。Sonnet 按本文铺开剩余项，每处照抄配方、按样例改 IT，`mvn verify` 全绿即可。

---

## 0. 契约（不可偏离，全站一致）

**请求参数**：列表接口统一新增两个查询参数
- `page`：页码，从 **1** 起，默认 **1**。
- `size`：每页条数，默认 **20**，**硬上限 200**。

**归一化**：一律用共享工具 `cn.edu.gpnu.platform.common.api.PageQuery`（Phase 44e 新增，platform-common）：
```java
Page<T> pageParam = PageQuery.of(page, size);   // page/size 为控制器 @RequestParam Integer（可空）
```
钳制规则（`PageQuery.of` 内部，勿在各处另写）：`page` 空或 <1 → 1；`size` 空或 <1 → 20；`size` > 200 → 200。

**响应**：沿用既有 `PageResult<T>{ long total; List<T> records; }`——**不改契约**。`total` 改为 `selectPage` 的**真实总数**（此前是 `records.size()`），`records` 改为**当前页**。前端 `DataPanel` 早已消费后端 `total`。

**向后兼容默认（关键决策，已验证）**：默认 `size=20`。理由：
- 全库既有 IT 的数据集都是**个位数条**（§9.3「所有数据集 1-5 条」），页 1（size=20）即返回全部，**不会被默认页大小静默截断**；
- 唯一会「看到分页效果」的既有断言是「列表 records 条数 / 某条是否在 records 里」。经排查，仅 **`/api/student` 列表**被 IT 断言（`Phase3StudentIT`），已随样例更新；**其余 12 处列表接口目前无任何 IT 断言其 records 集**（转换时若新增断言，按样例风格断言 `total` + 页大小，不要断言「全表条数==records.size()」）。
- **数据范围列表**：默认 size 不影响范围正确性——`selectPage` 的 count 与数据两条 SQL 都经数据权限拦截器过滤（见 §1），页与 total 同为已过滤结果。

> 若某接口的调用方（如导出、下拉、其它服务内部调用）需要**全量**而非分页，**不要**动它的全量方法；只改「列表展示」入口。样例中 `StudentService.listAll(...)`（全量）保持原状，仅 `list(...)` 改真分页。

---

## 1. 分页拦截器状态（已就绪，勿改顺序）

`platform-boot/.../config/MyBatisPlusConfig.java` 的 `mybatisPlusInterceptor` bean **已注册** `PaginationInnerInterceptor`，且顺序**正确**：
```java
interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataScopeSqlHandler())); // ① 数据权限
interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));             // ② 分页
interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());                   // ③ 乐观锁
```
**为何顺序不能反**（Phase 44e 已核 + IT 证）：`MybatisPlusInterceptor` 对一次查询按注册顺序对每个内拦截器依次调 `willDoQuery`→`beforeQuery`。数据权限**在前** → 其 `beforeQuery` 先把 `WHERE 范围` 改写进 SQL；分页**在后** → 其 `willDoQuery` 基于**已被范围改写**的 SQL 生成 `COUNT` 并执行 → **count 与数据两条 SQL 都带范围** → `total` 与该页都是「已过滤」结果。若把分页放前面，count 会基于**未过滤**的原 SQL → **total 错误（把别的学院也算进去）**。**Rollout 不得改动此 bean。**

数据范围上下文由 `DataScopeAspect`（`@Around`）在**整个控制器方法**期间 set/clear，`selectPage` 在方法内执行 → count/数据两查询都在范围上下文内。**只要列表控制器保留其既有 `@DataScope` 注解，分页自动被范围约束。**

---

## 2. before → after 配方

### 变体 A — Wrapper + @DataScope（数据范围列表，占多数）
适用：controller 上有 `@DataScope(alias=..., ...)`、service 用 `LambdaQueryWrapper` + `selectList`。样例：**Student**。

**Service 接口**：list 方法末尾加 `Integer page, Integer size`。
**Service 实现**（`X` 为实体，`toVO` 为既有映射）：
```java
// before
List<XVO> records = xMapper.selectList(wrapper).stream().map(this::toVO).toList();
return new PageResult<>(records.size(), records);
// after
Page<X> result = xMapper.selectPage(PageQuery.of(page, size), wrapper);
List<XVO> records = result.getRecords().stream().map(this::toVO).toList();
return new PageResult<>(result.getTotal(), records);
```
import：`com.baomidou.mybatisplus.extension.plugins.pagination.Page` + `cn.edu.gpnu.platform.common.api.PageQuery`。
**Controller**：list 方法加
```java
@RequestParam(value = "page", required = false) Integer page,
@RequestParam(value = "size", required = false) Integer size
```
并透传。**保留 `@DataScope` 不动**（范围随分页生效的关键）。

> 若列表还有**客户端专属筛选**（前端 `records.filter(...)` 而非发给后端的参数，如 Student 的「年级/班级」），真分页后客户端只看得到当前页 → **必须把该筛选下推为后端查询参数**（样例 Student 加了 `grade` 参数、`buildListWrapper` 里 `like(grade).or().like(className)`）。转换每个列表前先看它的前端视图有没有这种 `filter`。

### 变体 B — Wrapper 无数据范围（全局/系统列表）
适用：controller 无 `@DataScope`（全局参考数据/系统数据）。样例：**系统参数 params**。
配方同 A 的 service/controller 改法，**无 `@DataScope` 可加**。`total` 天然是「按过滤条件的真实总数」（无行级范围）。

### 变体 B' — Wrapper + 服务内 `userId` 过滤（本人列表）
适用：`NotificationServiceImpl.list`（`wrapper.eq(userId, currentUserId())`）。该过滤**已在 wrapper 里** → 直接套变体 B 配方（`selectPage` 会带上 `eq(userId)`），无需额外处理。

### 变体 C — 服务内 Java 手工范围过滤（须先下推进 wrapper）
适用：`ExchangeServiceImpl.batches`——范围是「非全校只看本人 `operatorId` 的批次」，当前是 `selectList` 后 **Java `.filter(b -> uid.equals(b.getOperatorId()))`**。
真分页前**必须把该 Java 过滤改成 wrapper 条件**，否则 `total`/页错乱：
```java
DataScopeContext.Scope scope = dataScopeService.resolve("exchange:import");
boolean allSchool = scope != null && scope.allSchool();
if (!allSchool) {
    wrapper.eq(ImportExportBatch::getOperatorId, UserContext.getUserId()); // 下推
}
Page<ImportExportBatch> result = batchMapper.selectPage(PageQuery.of(page, size), wrapper);
List<BatchVO> records = result.getRecords().stream().map(this::toBatchVO).toList();
return new PageResult<>(result.getTotal(), records);
```

### 变体 D — 自定义 `@Select` Mapper（审计日志）
适用：`SystemManagementServiceImpl.auditLogs` → `AuditQueryMapper.selectLogs`（手写 `@Select`，含硬编码 `LIMIT 500`，返回 `List<Map<String,Object>>`，范围以 `collegeIds` 入参手工传）。
MyBatis-Plus 支持**自定义 mapper 方法自动分页**：给方法**第一个参数**加 `IPage`，**删掉 SQL 里的 `LIMIT`**：
```java
// AuditQueryMapper：第一参数加 IPage，@Select 去掉 "LIMIT 500"
IPage<Map<String,Object>> selectLogs(IPage<Map<String,Object>> page,
        @Param("bizType") String bizType, /* ...其余入参不变... */);
// service：
IPage<Map<String,Object>> result = auditQueryMapper.selectLogs(PageQuery.of(page, size), bizType, ...);
List<AuditLogVO> records = result.getRecords().stream().map(this::toAuditVO).toList();
return new PageResult<>(result.getTotal(), records);
```
`auditLogs` 里 `collegeIds` 为空集时的空守卫（`return new PageResult<>(0, List.of())`）保留不变。注意其范围是自定义 `collegeIds` 语义（非拦截器），继续按原逻辑传参。

### 前端配方（视图用 `DataPanel` 的）
`DataPanel`（Phase 44e 已扩展，**向后兼容**）新增：`remote`（默认 false）、`page`（默认 1）两个 prop + `update:page`/`update:pageSize` 两个事件。改法（照样例 `StudentManageView` / `SystemAuditView` 参数页）：
1. 加 `const page = ref(1); const size = ref(20)`（同一视图多列表则各一套，如 `paramPage/paramSize`）。
2. `loadXxx()` 里把 `page`/`size` 传给 API；API 的 `cleanParams` 需支持 number（样例已把 `student.ts` 的 `cleanParams` 从「仅 string」扩为「string|number」）。
3. 新增 `search()`（`page.value=1; loadXxx()`）、`onPageChange(p)`（`page.value=p; loadXxx()`）、`onPageSizeChange(s)`（`size.value=s; page.value=1; loadXxx()`）。
4. `FilterBar @submit` / 关键词 `@keyup.enter` / `resetFilters` 由 `loadXxx` 改指向 `search`（筛选变更回到第 1 页）。
5. `DataPanel` 加 `remote :page="page" :page-size="size" @update:page="onPageChange" @update:page-size="onPageSizeChange"`，`:data` 用「当前页 records」、`:total` 用后端 `total`。
6. **删除该视图原有的客户端分页 / 客户端筛选 computed**（如 Student 原 `filteredRecords`），把其筛选下推到后端参数（见变体 A 注意）。

> **前端特例**：`NoticeCenterView`（通知中心）用的是 `n-list` **不是 `DataPanel`**，且用 `notices.length`/`filter(readFlag)` 做本地统计与「通知类型」下拉。转 `NotificationServiceImpl.list` 时，前端要么迁到 `DataPanel` + 后端 `total`，要么保留 n-list 但改为「后端分页 + 后端 unread-count（已有 `/notice/unread-count`）」；本地按 `type` 的下拉筛选需下推为后端参数或接受「仅筛当前页」。此项前端改造量最大，建议单独小心处理。

### IT 配方（每处按样例更新/新增）
- **既有断言列表 records 集的 IT**：改为断言 `total` + 页大小（勿断言「全表条数」）。参考 `Phase3StudentIT.dataScopeUsesRealStudentTable`（补 `at("/data/total")`）。
- **数据范围列表**：新增一条「分页 × 范围」IT，证 `total` 为**已过滤总数**（跨学院造数，越权那条不计入 total）、页大小生效、页间不重叠。**逐字参考 `Phase3StudentIT.paginatedStudentListIsScopedAndPagedForCollegeUser`**。
- **非范围列表**：新增一条分页 IT，证 `records.size()==size < total`、`total` 跨页稳定、页间不重叠、超大 size 被钳到 ≤200。**逐字参考 `Phase13SystemAuditIT.paramListSupportsRealServerSidePagination`**。

---

## 3. 剩余待转清单（12 处；样例 2 处已完成）

✅ 已完成（Phase 44e 样例）：`StudentServiceImpl.list`（变体 A）、`SystemManagementServiceImpl.params`（变体 B）。

| # | 文件 / 方法 | 后端端点 | 变体 | 前端视图 | 备注 |
|---|---|---|---|---|---|
| 1 | `SecurityAdminServiceImpl.listUsers` | GET `/api/system/user` | A（sys_user 范围，`@DataScope`） | `SecurityManageView`（用户页） | `summary.enabledUsers` 现由列表本地算 → 真分页后仅代表当页；改用后端 `total` 作「用户总数」，「启用用户」若要全量需另计（可暂移除或标注）。 |
| 2 | `TrainingProfileServiceImpl.list` | GET `/api/training` | A（training_profile，VO） | `TrainingManageView` | `toVOs` 已批量（44a），仅套分页配方。 |
| 3 | `CertificateServiceImpl.list` | GET `/api/cert` | A（certificate，VO） | `CertificateManageView` | — |
| 4 | `ProcessMaterialServiceImpl.list` | GET `/api/material` | A（process_material，VO） | `MaterialManageView` | — |
| 5 | `AbilityTestResultServiceImpl.list` | GET `/api/test-result`（见控制器） | A（ability_test_result，VO） | `TestResultManageView` | — |
| 6 | `ExemptionServiceImpl.list` | GET `/api/exemption` | A（exemption_request，VO） | `ExemptionManageView` | — |
| 7 | `VideoReviewServiceImpl.list` | GET `/api/video/reviews` | A（video_review，VO） | `VideoReviewView`（管理页） | `toVOList` 已批量（44a）。 |
| 8 | `VideoReviewServiceImpl.tasks` | GET `/api/video/...`（tasks） | A（video_review_task，VO） | `VideoReviewView` / `MyTaskPanel` | 我的任务列表，范围表 video_review_task。 |
| 9 | `SystemManagementServiceImpl.backups` | GET `/api/system/backup` | B（无范围） | `SystemAuditView`（备份页） | `summary.completedBackups` 现由本地算 → 同 #1，改后端 total 或标注。 |
| 10 | `NotificationServiceImpl.list` | GET `/api/notice` | B'（`eq(userId)`） | `NoticeCenterView` | **前端特例**（n-list，非 DataPanel），见 §2 前端特例。 |
| 11 | `ExchangeServiceImpl.batches` | GET `/api/exchange/batches` | **C**（operatorId 手工范围→下推 wrapper） | `ExchangeImportView` | 必须先把 Java `.filter(operatorId)` 改成 `wrapper.eq(...)` 再 `selectPage`。 |
| 12 | `SystemManagementServiceImpl.auditLogs` | GET `/api/audit/log` | **D**（自定义 `@Select` + `IPage` 参数、去 `LIMIT 500`） | `SystemAuditView`（审计页） | 改 `AuditQueryMapper.selectLogs` 签名；保留空 collegeIds 守卫；范围仍走 collegeIds 入参。 |

另：`SystemManagementServiceImpl` 内 `new PageResult<>(0, List.of())`（auditLogs 空守卫）**不是**假分页包壳，保留不动。

---

## 4. 门禁（每处转换后）
- `mvn -B -ntp verify` 全绿（先按 §0 铁律精杀 :8080 PID；每处按样例更新/新增 IT）。当前基线含 Phase 44e 两样例后为 **102**。
- 前端 `cd frontend && npm run type-check && npm run build` 绿（仅既有 echarts/naive chunk 警告）。
- **数据范围项必须**保留 `@DataScope`，并（首次或抽查）以「分页 × 范围」IT 证 total 也被过滤——**不得让分页绕过范围**。
