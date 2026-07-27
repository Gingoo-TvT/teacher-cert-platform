# Phase 0 退回整改提交材料（2026-07-27）

> 身份声明：本文件由**整改者**撰写，只构成**候选证据**，不是独立复核结论。Phase 0 与全项目的正式状态以后续独立
> 复核报告为准（`docs/REVIEW-GATE.md`）。

- 依据：`docs/reviews/phase-00-review.md`（CHANGES REQUESTED，3 个退回项）与阶段总审计
  `docs/reviews/phase-gap-audit-2026-07-23.md` 的 PG-M1
- 分支：`codex/phase00-remediation`（自 `codex/phase44-remediation` 的 `dd04f21` 起）
- 范围：验收文档修订 + lint 门禁（根 pom / checkstyle 规则 / 前端 ESLint / CI）+ 2 个新增测试类 +
  1 处配置修复（knife4j 增强关闭）+ lint 落地时清除的 5 处后端未使用 import 与 3 处前端缺陷；
  无 Flyway 迁移、无 API 路径/返回结构变更、无权限点变更

---

## 1. 退回项 1：验收基线对齐

`docs/phase-00-脚手架.md` 文首新增「验收基线修订（2026-07-27）」，按 AGENTS.md R10 在 DEVLOG 同日条目留痕：

| 原口径 | 现口径 | 依据 |
|---|---|---|
| 前端 `pnpm dev` | `npm run dev` | 规划期笔误：仓库自 T-008 起即 npm（`frontend/package-lock.json` 为 npm 锁文件；`AGENTS.md §2` 基线锁 npm），pnpm 从未成立过 |
| mock 登录走通封装层 | 真实登录 → Result 解包 → 路由跳转 | Phase 2（T-024）交付真实 JWT 认证后 mock 不存在；原意图（前端请求封装层可用）由现行链路覆盖，`Phase2SecurityIT`/`Phase14E2EIT` 回归 |
| lint：Spotless/Checkstyle/ESLint | 最小客观规则集（见退回项 2） | 三件套从未落地；本轮以「违反即缺陷」口径正式落地两端 lint 并进 CI；格式化统一（Spotless、vue3 recommended）移交 WS-6，不冒充闭环 |

§8 验收清单十项全部改为 `[x]` 并逐项标注**可重复证据锚点**（测试类/门禁命令），其中两项带 † 标注口径修订。

## 2. 退回项 2：lint 门禁落实并进 CI

**后端**：`maven-checkstyle-plugin` 3.6.0 + checkstyle 10.21.2，绑定根 pom `validate` 阶段——任何 `mvn verify/package`
自动执行，违规即红灯。规则集 `build/checkstyle/checkstyle.xml` 只收录客观缺陷类规则（禁 Tab、禁通配符/未使用/冗余
import、禁内部包、禁 System.out/printStackTrace（AGENTS.md §3.1）、EqualsHashCode 成对、字符串 `==`、空语句），
刻意不含格式化规则——存量不满足，强推会造成大面积无语义 diff 干扰在途审计；取舍理由写在规则文件头注释。

**前端**：ESLint 9 flat config（`frontend/eslint.config.js`）：vue3 `flat/essential` + `@vue/eslint-config-typescript`
recommended；`no-unused-vars` 为 error（`_` 前缀豁免）、`no-debugger` error、`no-explicit-any` 降 off（存量渐进类型化，
收紧移交 WS-6）。`npm run lint`（`--max-warnings 0`）。

**CI**：`.github/workflows/ci.yml` 后端 job 经 `mvn verify` 内嵌执行 checkstyle；前端 job 在 type-check 前新增
`npm run lint`。

**门禁不是摆设的直接证据**——落地当日即捕获并清除 8 处真实问题：
- 后端 5 处未使用 import（`ReviewNotificationHelper`、`ExchangeServiceImpl`、`StatsServiceImpl`×2、
  `DataScopeSqlHandler`、`SystemAuditController`）；
- 前端 `DataPanel.vue` 的 `rowProps` 函数遮蔽同名 prop（vue/no-dupe-keys，真实命名冲突）、`SystemAuditView.vue`
  未使用参数、`TrainingManageView.vue` 死函数。

## 3. 退回项 3：可重复运行证据

**`Phase00ScaffoldIT`（6 用例，真实 MySQL/Redis/MinIO）**，逐项锚定 `docs/phase-00-脚手架.md` §8/§9：

| 用例 | 覆盖验收项 |
|---|---|
| `docHtmlAndOpenApiAreServedWithoutAuthentication` | §8-2：/doc.html 200 + `/v3/api-docs` 含真实业务路径 |
| `bizExceptionReturnsHttp200WithBusinessCode` | §8-3 前半 / T-RESP-1：BizException → HTTP 200 + code≠0 |
| `validationFailureReturnsFieldLevelError` | §8-3 后半：@Valid 失败 → `username: 用户名不能为空` 字段级错误 |
| `uncaughtExceptionReturnsUnifiedResultWithoutStackTraceLeak` | T-RESP-1（P1-10 修订口径）：未映射路径 404 统一体；无专用 handler 的异常落兜底 → HTTP 500 + 统一 Result，响应体无异常类名/堆栈帧 |
| `uploadWritesAuditRowAndPresignedUrlExpiresAfterTtl` | §8-6 + §8-7 / T-AUDIT-1 / T-FILE-2：上传返回 fileId → `audit_log` 操作人/时间/IP 非空 → 3 秒 TTL 预签名正向下载逐字节一致 → **过期后同一 URL 被 MinIO 403 且响应含 expired** |
| `sameMd5LookupHitsExistingObjectWithoutSecondCopy` | T-FILE-1：同 MD5 命中既有对象、不产生第二行记录 |

**`DataScopeSqlHandlerTest`（9 用例，纯单测）**，覆盖 §8-8 / T-DS-1：COLLEGE → `college_id IN (...)`；SELF →
`student_id = ?`；ASSIGNED → `reviewer_id = ?`；全校/缺上下文 → 不追加条件；**空集合与 NONE → fail-closed
`id = -1`（绝不退化为全量可见）**；未注册表跳过；`@DataScope(alias)` 匹配/不匹配两向。T-DS-1 原文「`college_id = ?`」
按现行集合注入合同断言为 `IN`，差异记录在测试类注释。

## 4. 整改过程中发现并修复的真实回归（可重复证据的直接价值证明）

`Phase00ScaffoldIT` 首跑即抓到 `GET /v3/api-docs` **500**：
`NoSuchMethodError: SpringDocConfigProperties.getGroupConfigs()`。

- 根因：knife4j 4.5.0 的增强层 `Knife4jOpenApiCustomizer` 按 springdoc 2.3.0 的 `List getGroupConfigs()` 二进制链接
  （已反编译核实）；Phase 40 升级 SB 3.4 时 springdoc 被覆盖为 2.8.17，该方法自 2.4.0 起返回 `Set`，二进制不兼容。
  Phase 40 以来没有任何测试真正请求过文档端点，问题一直潜伏——这正是本退回项「缺可重复证据」的实证。
- 处置：knife4j 无更新版本（4.5.0 为 Maven Central 最新），关闭 dev 侧 `knife4j.enable` 增强自动装配。
  `/doc.html` UI 是 `knife4j-openapi3-ui` webjar 的静态资源（已核 jar 内 `META-INF/resources/doc.html`），
  不受该开关影响，数据仍由标准 springdoc 端点供给；损失仅为全仓未使用的 `@ApiSupport` 排序/界面定制扩展。
  生产 profile 本就 `knife4j.enable=false` + api-docs disabled（Phase 37a P0-4），不受影响。

## 5. 门禁

环境为本轮专用隔离栈（`tcp-p44-*`，`down -v` 重建全新数据卷，跑前 `teacher_cert` 0 张表）：

| 门禁 | 结果 |
|---|---|
| Flyway（空库→最终版本） | 33 个迁移（32 版本化 + `R__testseed`），最终 **V32** |
| checkstyle（validate 阶段，9 模块） | 全绿（本轮联网获取插件后随 verify 内嵌执行） |
| Surefire（单测） | **257/257**（system 53 + file 18 + boot 186），0 failure/error/skip |
| Failsafe（IT） | **212/212**，0 failure/error/skip |
| 合计 | **469/469** |
| `Phase00ScaffoldIT`（新增） | **6/6** |
| `DataScopeSqlHandlerTest`（新增） | **9/9** |
| 前端 `npm run lint` | 0 告警（`--max-warnings 0`） |
| 前端 `type-check` / `build` | 通过（build 仍有既有大 chunk 警告） |
| dev/prod Compose `config --quiet` | 通过 |
| `git diff --check` | PASS |

注：boot 186 含新增 `DataScopeSqlHandlerTest` 9 例；Failsafe 212 含新增 `Phase00ScaffoldIT` 6 例。

### 安全边界

仅本地构建/测试与只读检查；隔离栈用后 `down -v` 销毁；未启动常驻服务（AGENTS.md §6.1），未做扫描/凭据尝试/
故障注入等任何可能属于 cyber 的操作。checkstyle 插件与 ESLint 依赖经公网 Maven Central/npm registry 下载，
为构建依赖获取，非对外发布。

## 6. 门禁记录（原始输出摘录）

见 `docs/reviews/evidence/phase00-remediation-2026-07-27/gates-2026-07-27.txt`（按既定证据卫生口径脱敏）。

## 7. 未覆盖与已知边界（诚实记录）

1. **格式化统一未做**：Spotless / vue3 strongly-recommended 及 `no-explicit-any` 收紧移交 WS-6；本轮 lint 是
   「客观缺陷最小集」，不是完整代码风格治理。
2. **浏览器活体验收未重跑**：/doc.html 的断言是 HTTP 层（200 + HTML 骨架 + OpenAPI JSON 可用），未做浏览器渲染
   截图；按 AGENTS.md §6.1 headless 不起常驻服务，浏览器级验收由用户或复核环境执行。
3. **`sys_param` 与 §6 参数清单的逐项一致性**未新增专项断言：现有 `Phase13SystemAuditIT`/各业务 IT 已按用途读写
   种子参数，逐 key 全量比对留最终全量审计。
4. `knife4j.enable` 关闭属 dev 行为变化：若将来需要增强扩展，需等 knife4j 发布兼容 springdoc ≥2.4 的版本或
   移除 knife4j 改纯 springdoc + swagger-ui。
