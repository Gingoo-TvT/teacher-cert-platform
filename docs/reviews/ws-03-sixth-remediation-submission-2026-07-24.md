# WS-3 第六轮整改提交材料（2026-07-24）

**范围：** `88d3136..HEAD`，仅处理第五轮独立报告新增的 2 个 Medium。
**基线报告：** `ws-03-fifth-remediation-rereview-2026-07-24.md`。
**提交方结论：** 第六轮整改候选/开发者自测完成，请求独立增量复核。
**正式状态：** 第五轮正式结论仍为 **CHANGES REQUESTED（2 Medium）**。本材料不是独立 PASS。

第四轮 1 High / 1 Medium / 2 Low 已由第五轮独立复核确认关闭。

## 1. 整改摘要

| 第五轮 finding | 第六轮候选实现 | 确定性证据 |
|---|---|---|
| Medium：reconciliation 工作虽投递到专用 executor，但 cron trigger 与同步备份共享 Boot 默认单线程 scheduler | 生产同时显式提供普通 `taskScheduler` 和 `videoFinalizationReconciliationTaskScheduler`；reconciliation 的 `@Scheduled.scheduler` 只绑定后者 | 真实 Spring scheduling 上下文中阻塞普通 scheduler 上的同步备份，对账仍至少连续触发并提交两次 |
| Medium：V32 `video_finalization_object_candidate` 未进入应用逻辑全量备份 | candidate 加入稳定有序的备份 allowlist，精确表数从 36 增至 37；备份产物 section 与当前 schema 表集合做精确契约 | scratch schema 回放三个 generation、三种状态的 candidate 全字段，再调用真实 reconciler 继续清理 |

## 2. 独立 trigger scheduler

- `VideoFinalizationReconciliationTaskSchedulerConfig` 只在 `prod` profile 生效。
- 命名为 `taskScheduler` 的普通调度器继续承载未指定 scheduler 的备份与清理任务。
- `videoFinalizationReconciliationTaskScheduler` 只负责视频对账 cron trigger；实际对象工作仍投递到既有独立 worker executor。
- 两个调度器均为单线程、daemon、取消即移除且关闭不等待长任务，异常处理会记录并允许后续周期继续。
- 显式保留两个调度器是必要的：只注册视频专用 scheduler 会让 Spring Boot 默认调度器自动配置回退，普通未限定任务可能反而复用专用线程。

T-VID-2N 使用真实 `AnnotationConfigApplicationContext`、`@EnableScheduling` 和每秒 cron。同步全量备份在普通 scheduler 上由进程内 latch 确定性阻塞；在 latch 未释放期间，记录型 worker 收到至少两次来自视频专用 thread prefix 的提交。测试在 `finally` 释放 latch、关闭上下文并自然退出，不访问真实备份存储。

## 3. Candidate 台账备份与恢复续跑

- `DatabaseBackupService.BACKUP_TABLES` 现包含 `video_finalization_object_candidate`，逻辑全量备份精确覆盖 37 个表，仍明确排除由 Flyway 重建的 `flyway_schema_history`。
- 既有 Phase 41 备份 IT 改为断言精确表数 37，且产物含 candidate section。
- T-VID-2O 使用唯一且有前缀守卫的 scratch schema，只加载生产迁移至 V32。
- 测试写入三个不同 generation：
  - `CLEANUP_PENDING`，保留已有 attempt/error/retry；
  - claim 已过期的 `CLEANING`；
  - 已有 `cleaned_at` 的 `CLEANED` 墓碑。
- 下载真实 gzip 逻辑备份，断言产物 section 集合与 `information_schema.tables` 精确相等；抽取 candidate INSERT，清空并回放该 section，再对恢复前后所有列做递归相等比较。
- 恢复后调用真实 `VideoFinalizationObjectReconciler`，三行均可继续进入 `CLEANED`，claim 被释放、attempt 分别递增且 `cleaned_at` 保留/生成。
- 对象删除由 `@Primary` 内存对象存储替身记录，不触碰真实视频对象；备份 gzip 产物和 scratch schema 在测试后清理。

## 4. 已执行证据

- 生产调度配置与隔离测试：**3/3**。
- candidate 逻辑备份、scratch restore 与真实对账续跑：**1/1**。
- 本轮专用空库成功执行 **33 个 Flyway 迁移至 V32**。
- 最终后端 `clean verify`：Surefire **149/149**、Failsafe **169/169**，合计 **318/318**，0 failure/error/skip。
- Phase 7：**44/44**；V32 迁移 IT 保持通过。
- 前端 type-check/build、dev/prod Compose config 与 `git diff --check` 均通过；前端仅保留既有大 chunk 警告。

第一次全量运行的唯一失败来自本轮临时 MinIO 未带正式 dev 编排已有的 CORS 白名单。补齐隔离环境配置并定向复验后，重新执行完整 `clean verify` 得到上述最终结果；应用代码与恶意 Origin 拒绝断言未放宽。

全部验证均为会自然退出的一次性命令。未由 Codex 启动常驻后端或前端；本轮独立 Docker 测试项目及其临时卷已在验证后删除。

## 5. 范围边界

本轮没有关闭：

- Phase 41 整份 gzip 使用普通 `INSERT`，直接回放到含 Flyway 种子的恢复库会发生重复键冲突；T-VID-2O 只证明 candidate section 的逐字段恢复和对账续跑。
- Phase 53 demo 对象/元数据升级不一致。
- Phase 7 真实 2GB 上传、非允许编码与不可解码首帧的广覆盖证据债。
- Phase 0、39、41、42、44、47、53 的各自正式退回项。

即使 WS-3 后续取得独立 PASS，全项目仍受上述 U-003/U-004 等退回项阻断，不能宣称发布就绪。

## 6. 安全边界

遵照用户要求，本轮未执行任何 cyber 指令，也未执行漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz、进程杀伤或攻击性并发。调度故障仅由测试进程内 latch 模拟；candidate 对象删除仅使用内存替身。没有需要用户代为执行的 cyber 命令。

## 7. 独立增量复核请求

请冻结 `88d3136..HEAD`，重点核对：

1. 两个 `TaskScheduler` 在生产 profile 的 bean 选择是否确定，普通未限定任务是否始终落在 `taskScheduler`。
2. blocked-backup 测试是否真实运行 Spring scheduling，且在备份未释放时证明至少两个对账 trigger 被提交。
3. 37 表 allowlist 是否与当前 schema 表集合一致，candidate 是否确实进入 gzip 产物。
4. scratch restore 是否保留 generation、state、retry、claim、error 与 tombstone 全字段，并能由真实 reconciler 安全续跑。
5. 测试是否避免真实视频对象删除、不会遗留 scratch schema/临时 Docker 资源。
6. 治理文档是否保持第五轮 **CHANGES REQUESTED（2 Medium）**，没有越权写成 PASS。

独立复核者应产出新的第六轮报告；只有该报告 PASS 后，WS-3/U-002 才能更新为已复核。
