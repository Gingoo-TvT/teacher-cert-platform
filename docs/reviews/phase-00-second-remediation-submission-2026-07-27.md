# Phase 0 / U-004 第二轮退回整改提交材料（2026-07-27）

> 身份声明：本文件由**整改者**撰写，只构成第二轮**候选材料**，不是独立复核结论。
> Phase 0、U-004 与全项目继续保持 **CHANGES_REQUESTED**；只有独立复核报告明确 PASS 后，才可放行最终全量审计。

- 首轮正式报告：`docs/reviews/phase-00-remediation-rereview-2026-07-27.md`
- 首轮治理基线：`8e3217da5f39f55f8146fa6d4e638adf8406914f`
- 第二轮产品/测试/门禁候选：`3bfe83d9eaa4486bb39cc89fc3f05a975319e3af`
- 分支：`codex/phase00-remediation`
- 复核范围：首轮新增的 3 Medium / 4 Low；不重写已关闭的 Phase 44 或其它阶段问题
- 变更规模：25 个路径，`+2765/-131`

第二轮动态门禁必须针对提交材料归档后的最终 `HEAD` 执行；该材料提交只增加治理文档，产品、测试和门禁代码冻结在
`3bfe83d9eaa4486bb39cc89fc3f05a975319e3af`。

---

## 1. 首轮 3 Medium / 4 Low 闭环矩阵

| 首轮 finding | 第二轮整改 | 候选证据 |
|---|---|---|
| Medium：T-FILE-1 只上传一次，却宣称“同 MD5 二次上传” | 按 R10 **退役通用文件服务的摘要秒传合同**：删除 `FileService.getByMd5`，删除通用 `upload` 的 `md5` 入参及持久化路径；通用附件上传不再信任客户端摘要，也不暴露全局对象存在性。Phase 7 的受信视频 fast-hit 是独立、受主体和业务域约束的合同，不受本次变更影响 | `FileServiceUploadContractTest` 证明公共接口无摘要查询/上传参数，并真实以相同字节执行两次 MinIO PUT、两次数据库 INSERT，得到不同对象键；`Phase00ScaffoldIT.sameContentUploadsRemainIndependentWithoutGlobalDeduplication` 在真实依赖门禁中执行同字节二次上传并断言两行 READY |
| Medium：CI 只跑宽泛 `mvn verify`，目标 suite 可静默消失 | 新增 exact spec 与 gate；运行前删除目标旧 XML/summary，固定执行 `mvn -B -ntp clean verify`，随后校验 6 个 suite、25 个 testcase、逐方法名、失败/错误/跳过数、XML 新鲜度和 Failsafe summary | `.github/workflows/ci.yml`、`scripts/phase00_ci_gate.py`、`scripts/phase00_ci_gate_spec.json`；gate 自测 21/21 |
| Medium：U-004 权威口径互相冲突 | 同步 `AGENTS.md`、`plan.md`、`tasks.md`、根 `README.md`、`docs/README.md`、`docs/REVIEW-GATE.md` 与 `docs/phase-00-脚手架.md`；统一 npm、真实认证、最小 lint、阶段待复核/独立 PASS 与 prod 文档端点口径 | 权威文档 diff + 离线文档/门禁对抗复核 |
| Low：prod 静态 doc UI 仍可访问 | `SecurityConfig` 在 prod profile 的 JWT 之前对 `/doc.html`、Swagger UI、`/v3/api-docs`（含 yaml/子路径）及 webjars 返回 404；dev 继续开放 | `ApiDocumentationSecurityProfileTest` 2/2：prod 全部 404、dev 全部 200 |
| Low：证据 provenance 不足 | gate 绑定完整候选 SHA，运行前后检查 tracked clean，并拒绝可改变构建的 untracked Maven/源码输入；记录无秘密 target marker、工具版本、起止时间、完整脱敏 Maven 日志、原始测试 XML、源码/工件哈希、manifest 与 `SHA256SUMS`；PASS 证据自校验失败会降级为 FAIL | `scripts/phase00_ci_gate.py` + 21 个纯离线 gate 自测 |
| Low：10/10 验收锚点过度 | `docs/phase-00-脚手架.md` 将六项真实依赖/浏览器项目改为 `[~]`，只保留已由离线门禁证明的项目为 `[x]`；新增 24 项 `sys_param` 精确矩阵 IT | `Phase00ParameterMatrixIT` 1 项；动态执行前不宣称该 IT 已运行 |
| Low：T-DS-1 只测 handler 且规格漂移 | 规格改为现行 `DataScopeType` 合同；新增真实 `@DataScope → Aspect → Context → MyBatis Mapper proxy → DataPermission/Pagination interceptor → count/data SQL` 链路 | `DataScopeMapperChainTest` 5/5，覆盖 COLLEGE/SCHOOL/SELF/NONE/空学院集合；`DataScopeSqlHandlerTest` 保留 9 个原子边界测试 |

## 2. exact suite 合同

第二轮动态门禁只在以下 **6 个 suite / 25 个 testcase** 全部出现且精确匹配时才可 PASS：

| suite | testcase |
|---|---:|
| `Phase00ScaffoldIT` | 6 |
| `Phase00ParameterMatrixIT` | 1 |
| `DataScopeSqlHandlerTest` | 9 |
| `DataScopeMapperChainTest` | 5 |
| `FileServiceUploadContractTest` | 2 |
| `ApiDocumentationSecurityProfileTest` | 2 |
| **合计** | **25** |

宽泛全量通过、旧 XML、只有 class 计数没有方法集合、或 parent reactor 的零匹配成功，均不能替代本合同。

## 3. 整改者已执行的离线门禁

以下命令均会自然退出，不连接 Docker、MySQL、Redis、MinIO、HTTP 服务、浏览器或网络：

| 门禁 | 结果 |
|---|---|
| `python -B scripts\test_phase00_ci_gate.py` | **21/21 PASS** |
| `mvn -B -ntp -o clean test` | **266/266 PASS**，31 份当前源码 Surefire 报告，0 failure/error/skip |
| `mvn -B -ntp -o -DskipTests package` | **9/9 modules BUILD SUCCESS**，Checkstyle 0 violation |
| `npm --prefix frontend run lint` | PASS，0 warning |
| `npm --prefix frontend run type-check` | PASS |
| `npm --prefix frontend run build` | PASS；仅既有大 chunk 警告 |
| `git diff --check` / 候选路径核对 | PASS |

说明：

1. `clean test` 之前磁盘存在旧 Surefire XML；只有清理后得到的 **266/266** 是当前源码离线结果。
2. `Phase00ScaffoldIT` 与 `Phase00ParameterMatrixIT` 属真实依赖 IT，本轮 Codex **没有执行**，不得从上表推导其 PASS。
3. 两路内部只读对抗检查未发现新的问题；它们只是整改者侧候选质量检查，不是正式独立复核，也不产生分级结论。

## 4. 必须由用户执行的动态门禁

以下步骤会连接或操作 MySQL、Redis、MinIO，可能涉及 Docker/网络，因此 Codex 未执行。请用户在**已授权、全新、一次性、
与共享开发环境隔离**的依赖栈中执行；不要把命令指向共享库或生产资源。

### 4.1 运行前

1. 检出本提交材料归档后的最终候选 `HEAD`，确认 tracked worktree clean，且不存在未跟踪的 `pom.xml`、`.mvn`、
   `mvnw*`、`build/` 或 `platform-*/src/main|test` 输入。
2. 准备全新 MySQL 8 / Redis 7 / MinIO，并设置项目既有的连接环境变量与 `JWT_SECRET`。
3. 设置以下**不含凭据**的目标标识，值必须与实际隔离栈一致：

```powershell
$env:PHASE00_TARGET_SCHEMA = 'teacher_cert'
$env:PHASE00_MYSQL_SERVICE_MARKER = 'mysql:<实际版本>@<实际地址>'
$env:PHASE00_REDIS_SERVICE_MARKER = 'redis:<实际版本>@<实际地址>/<db>'
$env:PHASE00_MINIO_SERVICE_MARKER = 'minio:<实际版本>@<实际地址>/<bucket>'
$env:PHASE00_RUN_CONTEXT = 'manual:<本次唯一运行标识>'
```

### 4.2 执行

证据目录必须是本次新建的空目录；不要复用首轮或其它提交的报告：

```powershell
$env:PHASE00_EXPECTED_CANDIDATE_SHA = git rev-parse HEAD
python -B scripts\phase00_ci_gate.py run `
  --evidence-dir 'C:\absolute\new\phase00-ci-evidence' `
  --expected-candidate-sha $env:PHASE00_EXPECTED_CANDIDATE_SHA
```

也可以在本候选上触发仓库 GitHub Actions；workflow 会以 `GITHUB_SHA`、`run_id`、`run_attempt` 绑定证据。

### 4.3 交回复核者

- 完整证据目录（含 manifest、完整脱敏 Maven log、XML、源码/工件哈希与 `SHA256SUMS`）；
- `git rev-parse HEAD` 的完整 40 位 SHA；
- 隔离栈版本/地址标识及运行前为空、运行后清理结果；
- 若失败，保留 FAIL manifest 和完整失败日志，不只截取最后几行。

## 5. 未覆盖与状态边界

1. Codex 没有启动或连接 Docker、数据库、Redis、MinIO、网络、HTTP 服务或浏览器，也没有执行扫描、fuzz、故障注入、
   凭据、权限或其它可能属于 cyber 的动作。
2. 浏览器级 `/doc.html` 开发态渲染仍需用户/复核环境核对；prod 的 HTTP 安全合同已由内存 MockMvc 测试覆盖。
3. 第二轮候选未获得正式 PASS；`docs/phase-00-脚手架.md` 的六项 `[~]` 只有在上述动态门禁和必要浏览器核验成立后，
   才能由独立复核者按 `docs/REVIEW-GATE.md` 裁定。
4. 当前不得进入最终全量审计，不得 merge、push、部署或宣称稳定发布 GO。
