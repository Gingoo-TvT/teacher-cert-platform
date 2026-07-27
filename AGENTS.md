# AGENTS.md — codex 开发前必读（执行总纲）

> **地位**：本文件是项目最高执行规范。**每次开始任何任务前，必须先读本文件，再读对应阶段文档。**
> 冲突时优先级：**规范类**（流程/代码/Git/测试）以本文件为准；**规格类**（业务/数据/规则）以 `plan.md §15` > `plan.md 正文` > `docs/phase-NN` > `tasks.md` 为准。
> 落地位置：项目初始化（T-001）后，本文件与 `plan.md`/`tasks.md`/`PROGRESS.md`/`DEVLOG.md`/`docs/` 一并置于**仓库根目录**。

---

## 0. 开工前阅读顺序（每个任务都要走一遍）
0. **首次接手先读 `HANDOFF.md`**（当前进度、本机环境与一键命令、从哪开始）——接手时读一次。
1. `AGENTS.md`（本文件：红线 + 流程）
2. `plan.md`（总体方案，重点看 **§15 增补**）
3. `tasks.md`（定位本次的 **T-编号** 与依赖）
4. `docs/README.md`（全局 DoD、测试规范、**AT 追溯矩阵**）
5. `docs/phase-NN-*.md`（本任务所属阶段：数据库/接口/规则/**验收清单**/**测试用例**）
6. `PROGRESS.md`（确认依赖任务已 ✅；把本任务置 🟦 进行中）

**开工门槛**：依赖任务必须已完成；本任务涉及的"验收清单 + 测试用例 + 相关 AT"已读懂。未满足不得开工。

---

## 1. 项目红线（违反即返工，无例外）
- **R1 文本化**：`学校代码/学号/身份证件号码/出生日期/证书编号/有效期限` 全链路 `String`（实体/DTO/VO/Excel 模型），Excel 单元格格式 `@`。禁止 `DATE/BIGINT` 存储、禁止科学计数法/日期序列/前导零丢失。(AT-01)
- **R2 不硬编码**：学校代码、省码、视频阈值、合格线、文件大小、序列作用域等一律读 `sys_param`/字典；禁止魔法值。
- **R3 留痕**：所有写操作（审核/状态流转/作废/重开/导出/敏感访问）经 `@AuditLog` 落 `audit_log`（操作人/时间/意见/前后状态/IP）。(AT-12)
- **R4 逻辑删除**：业务与附件元数据 `deleted` 软删，禁止物理删除；保留历史版本。
- **R5 Flyway**：所有 DDL/种子走版本脚本 `V{n}__{desc}.sql`，幂等可重复执行；**禁止手改库**。
- **R6 数据范围**：所有列表/导出/统计查询必须套 `@DataScope`；学生=本人、学院=授权范围、教务处/签发=全校。漏一处即越权。(AT-13)
- **R7 后端校验**：前端联动仅为体验，**所有校验（证件/出生日期/姓名/专业代码/学段学科/编号/有效期）后端必做且为硬约束**；Phase 10 预校验复用同一套校验器。
- **R8 权限点冻结**：权限点编码以 `plan.md §15.1` 为**唯一来源**，一经定稿不得擅改/分叉。
- **R9 待确认事项**：按 `docs/待确认事项确认单.md` 的默认值实现，代码处标 `// TODO: 待学校确认(确认单#N)`，并集中在参数页可调。
- **R10 不擅改需求**：发现规格冲突/缺口，先记 `DEVLOG.md` 并在 `PROGRESS.md` 标 ⚠️，不自行决断；确需改规格，同步修改 `plan.md`/`docs` 并在 DEVLOG 注明依据。

---

## 2. 技术栈基线（锁定，不擅自升级大版本）
| 后端 | 版本 | 前端 | 版本 |
|---|---|---|---|
| JDK | 17 | Node | ≥18 LTS |
| Spring Boot | 3.4.x | Vue | 3.5.x |
| Maven | 3.9+ | Vite | 5.x |
| MyBatis-Plus | 3.5.x | TypeScript | 5.x |
| MySQL | 8.x | Naive UI | 2.x |
| Redis | 7.x | Pinia / Vue Router | 2.x / 4.x |
| MinIO | 最新稳定 | Axios | 1.x |
| EasyExcel/FastExcel | 3.x | ECharts | 5.x |
| Flyway | 随 Spring Boot 3.4.x（含 flyway-mysql） | 包管理 | npm |
| Knife4j | 4.x | | |
| jjwt | 0.12.x | | |

---

## 3. 代码规范
### 3.1 后端
- 分层：`controller`(仅参数/返回，不写业务) → `service`/`service.impl`(业务+事务) → `mapper`(MyBatis-Plus) → `entity`/`dto`(入参)/`vo`(出参)；**禁止 entity 直接出入参**。
- 命名：类 PascalCase、方法/字段 camelCase、常量 UPPER_SNAKE、包全小写；REST 路径 kebab/复数名词。
- 统一返回 `Result<T>`/`PageResult<T>`；异常抛 `BizException`，由 `GlobalExceptionHandler` 兜底，**不吞异常、不裸 try-catch 打印**。
- 参数校验用 `@Valid` + JSR-380；业务校验在 service。
- 事务：跨表写操作 `@Transactional(rollbackFor=Exception.class)`；证书序列自增与证书写入**同一事务**。
- 日志 SLF4J，**禁止 System.out**；关键分支与异常 WARN/ERROR。
- 业务注释用中文，说明"为什么"；禁止魔法值（枚举/常量/`sys_param`）。
- 用 Lombok 精简样板；DTO/VO 显式字段。

### 3.2 前端
- 组合式 API + `<script setup lang="ts">`；类型齐全，禁止 `any` 滥用。
- 组件 PascalCase 文件名；页面在 `views/`，可复用组件在 `components/`。
- API 统一在 `api/` 层封装，经 Axios 实例；不在组件里裸调 URL。
- 状态用 Pinia；权限按钮用 `v-perm`，菜单按 `me` 权限动态渲染。
- UI 一律 Naive UI 组件；统一错误提示与 loading；表单校验与后端规则一致（但以后端为准）。

---

## 4. 数据库规范
- 命名：表/列 snake_case，表加业务前缀（`sys_`/业务名）；每表带 `id, created_by/at, updated_by/at, deleted` 与 `COMMENT`。
- 文本字段（R1）一律 `VARCHAR`；金额/分数等按需，但本项目成绩/编号按文本。
- 索引：唯一键（学号、证书编号、`(video_review_id,reviewer_id)` 等）与高频查询列建索引。
- Flyway：版本号严格递增、不复用、不修改已发布脚本；种子用 `INSERT ... ON DUPLICATE KEY UPDATE` 或先判存在，保证可重复执行。
- 迁移脚本与对应 Phase 文档声明的 `V{n}` 编号一致（见各 phase 文档）。

---

## 5. Git 与提交规范
- 分支：`main`(可发布) ← `develop` ← `feature/phaseNN-Txxx-简述`。一个任务一分支一 PR。
- 提交信息（Conventional Commits）：`type(scope): 主题`，type ∈ `feat/fix/refactor/test/docs/chore/perf`，scope 用模块或 T 号。例：`feat(T-073): 证书18位编号生成与序列锁`。
- 小步提交，能编译能测；**不提交密钥/大文件/IDE 配置**。
- PR 描述模板：
  ```
  ## 任务 T-xxx
  ## 改动概述
  ## 覆盖的验收清单（docs/phase-NN）
  - [x] ...
  ## 覆盖/复验的 AT
  ## 自测结果（测试名/截图/日志）
  ## 遗留与 // TODO
  ```

---

## 6. 测试规范
- 详见 `docs/README.md` §2/§3。要点：核心规则（校验/状态/编号/导入导出）必须有**单元测试 + 反例**；主接口集成测试必须使用隔离的真实 MySQL/Redis/MinIO。当前存量 IT 由复核环境提供独立 Compose/schema；新增或改造测试优先使用 Testcontainers，迁移完成前不得虚报测试自带隔离。
- 覆盖率：核心模块行覆盖 ≥80%。
- CI（T-010）必过：lint + 编译 + 测试。**红灯不合并。**

### 6.1 运行期自测防卡死（硬规则）
- **禁止**以前台或继承当前标准输出/错误管道的方式启动常驻服务（如 `java -jar`、`npm run dev`、`vite dev`）。Headless 调度方会一直等待 stdout/stderr EOF，服务不退出就会导致 Codex/exec 无限空转。
- **Codex/exec 内同样禁止**调用任何会启动常驻服务的包装脚本，即使脚本内部重定向日志也不作为安全依据。`scripts/dev-serve.sh` / `scripts/dev-serve.ps1` 仅供外部人工终端、watchdog 或 Claude 复核环境使用。
- 运行期验证只能三选一：
  1. 运行会自然退出的一次性命令，例如 `mvn -B -ntp -DskipTests package`、`npm --prefix frontend run build`、`npm --prefix frontend run type-check`。
  2. 由外部终端运行 `scripts/dev-serve.sh` / `scripts/dev-serve.ps1` 后，Codex 只执行会自然退出的 HTTP/DB/CLI 检查；完成后必须由外部或 Codex 的 `scripts/dev-stop.*` 停服务。
  3. 对需要长时间常驻应用的反例验证，默认交给 Claude 阶段复核执行，Codex 只记录未本地运行的原因。
- 外部终端启动后端示例（严禁由 Codex/exec 执行）：
  ```bash
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"
  export JWT_SECRET="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  bash scripts/dev-serve.sh backend http://127.0.0.1:8080/api/health "$JAVA_HOME/bin/java" -jar platform-boot/target/teacher-cert-platform.jar
  bash scripts/dev-stop.sh backend
  ```
- 外部终端启动前端示例（严禁由 Codex/exec 执行）：
  ```bash
  bash scripts/dev-serve.sh frontend http://127.0.0.1:5173 npm --prefix frontend run dev -- --host 127.0.0.1
  bash scripts/dev-stop.sh frontend
  ```
- 外部 PowerShell fallback（Git Bash 不可用时，严禁由 Codex/exec 执行）：
  ```powershell
  $env:JWT_SECRET = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
  .\scripts\dev-serve.ps1 backend http://127.0.0.1:8080/api/health 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe' -jar platform-boot\target\teacher-cert-platform.jar
  .\scripts\dev-stop.ps1 backend
  ```

---

## 7. 任务执行标准流程（每个任务严格照此 Loop）
1. **领取**：在 `tasks.md` 选定 T-编号，确认依赖在 `PROGRESS.md` 为 ✅。
2. **读文档**：本文件红线 → 对应 `phase-NN`（验收清单 + 测试用例）。
3. **建分支**：`feature/phaseNN-Txxx-...`。
4. **登记进行中**：`PROGRESS.md` 该任务置 `🟦`，填分支名。
5. **实现**：写迁移(Flyway) → 后端(分层) → 前端 → 接入 `@AuditLog`/`@DataScope`。
6. **自测**：跑该任务的测试用例（含反例）+ 逐条勾选 `phase-NN` 验收清单。
7. **完成**：验收清单全绿后，`PROGRESS.md` 置 `✅`，更新 AT 跟踪。
8. **写日志**：在 `DEVLOG.md` 追加一条（见 §9）。
9. **提交/PR**：按 §5 模板，列出覆盖的验收与 AT。

> 不得跳步：未写迁移不写代码、未过反例不算完成、未写 DEVLOG 不算交付。

> **阶段级闸门**：一个 Phase 的所有任务 `[x]` 后，**codex 不得自行标记阶段完成**——置阶段状态为 **待复核** 并发起 Claude 复核（见 §7.5）。

---

## 7.5 阶段复核闸门（Claude 复核）
> 详细协议见 `docs/REVIEW-GATE.md`。

- **codex 完工**：本 Phase 全部任务 `[x]` → 阶段状态置 **待复核** → 提交并在 `DEVLOG.md` 写阶段小结 → 通知"Phase N 待 Claude 复核"。**不得自行置 ✅。**
- **Claude 复核**：独立构建/运行/**重跑反例**、对抗性验证、对照规格逐项核对，产出 `docs/reviews/phase-NN-review.md`：
  - **PASS** → 阶段置 **✅ 已复核**、更新「AT 跟踪」、放行下一阶段；
  - **退回** → 列 Blocker/Major，阶段置 **复核退回**；codex 修复后重交，Claude 只复核增量 + 回归，直至 PASS。
- §7 步骤①的"依赖任务已 ✅"指**前序阶段已复核通过**，而非 codex 自测完成即可。

---

## 8. 进度文档 `PROGRESS.md` 规范
- 任务状态：`[ ]`=待开始 · `[~]`=进行中 · `[x]`=完成 · `[!]`=阻塞（必须在 DEVLOG 说明原因）。
- 阶段状态（汇总表）：待开始 → 进行中 → **待复核**(codex 完工) → **✅ 已复核**(Claude 通过) / **复核退回**。
- 更新时机：开工置 `[~]`；任务完成且验收清单全绿置 `[x]`；遇阻置 `[!]`；**阶段全任务 [x] 后置「待复核」，经 Claude 复核(REVIEW-GATE)通过才置 ✅**。
- 同步维护"阶段进度汇总"计数与"AT 跟踪表"。
- **不允许**先置 `[x]` 再补测试；状态必须反映真实进度。

## 9. 开发日志 `DEVLOG.md` 规范
- 粒度：每完成一个任务、或每个工作会话，至少追加一条（倒序，最新在上）。
- 必含字段：日期 / 任务号 / 做了什么 / 关键技术决策与理由 / 遇到的问题与解决 / 与规格的偏差或疑问 / 下一步。
- 决策若偏离 plan/docs，必须写明依据，并同步改对应文档（R10）。

## 10. 阻塞与变更处理
- 规格冲突/歧义：记 DEVLOG + `PROGRESS` 标 `[!]` + 必要时更新 `待确认事项确认单.md`；**不私自拍板**业务规则。
- 跨任务影响的设计变更：先改 `plan.md`/`docs`，再改代码，DEVLOG 留痕。

## 11. 安全红线
- 所有附件/视频访问鉴权 + 限时预签名，禁公开链接；视频播放页动态水印。
- 敏感字段（证件号/出生日期/成绩/师德材料/视频）默认脱敏；明文/敏感导出需 `export:sensitive`/`exchange:export:sensitive` 且留痕。
- 不在代码/仓库写死密钥；配置走环境变量/`.env`（不入库）。
- 依赖定期查漏（OWASP/依赖审计）。

## 12. 任务 DoD 速查（提交前逐条勾）
- [ ] 已读本文件红线 + 对应 phase 文档验收清单
- [ ] Flyway 迁移已写且幂等，版本号与 phase 文档一致
- [ ] 后端分层、统一 Result、参数+业务校验、事务正确
- [ ] 涉及写操作已接 `@AuditLog`；涉及查询已接 `@DataScope`
- [ ] 文本化字段全链路 String + Excel `@`（若涉及）
- [ ] 单元测试 + **反例** 通过；主接口集成测试通过
- [ ] 该 phase 验收清单逐条 `- [x]`；相关 AT 自测记录归档
- [ ] Swagger 更新，非生产 profile 的 `/doc.html` 可调；生产 profile 必须关闭文档 API、UI 与静态资源
- [ ] Codex/exec 未直接启动常驻服务；若外部启动过后端/前端，已通过 `scripts/dev-stop.*` 或外部 watchdog 停止，且未遗留 8080/5173 监听进程
- [ ] `PROGRESS.md` 置 ✅、`DEVLOG.md` 追加、PR 按模板
- [ ] lint/CI 全绿，未破坏既有回归

**阶段级（整个 Phase 完工时额外）：**
- [ ] 全部任务 `[x]`，阶段状态置「待复核」（**codex 不自行置 ✅**）
- [ ] 已提交 + `DEVLOG.md` 阶段小结；发起 Claude 复核（`docs/REVIEW-GATE.md`）
- [ ] 经 Claude 复核 **PASS** 后，阶段方可置 ✅ 并放行下一阶段
