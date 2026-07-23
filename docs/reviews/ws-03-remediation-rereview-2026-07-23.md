# Fuck My Shit Mountain Audit Report

**Project:** teacher-cert-platform — WS-3 首批整改增量
**Audit mode:** incremental, security, stability, performance, testing-authenticity, release, configuration, data-integrity
**Date:** 2026-07-23
**Reviewer:** Codex / GPT-5

---

## 1. Executive Summary

本次只复核 `37f6cdb..ca400f1` 的 27 个变更文件，并追踪受影响的既有调用方、演示初始化和评审状态机。结论为 **Request changes / 复核退回**。原 WS-3 报告的三项 Major 中，CI 已补真实 MinIO 与前端 type-check，服务端内容指纹、同 uploader + 同 student 秒传边界以及普通 VO 去指纹也已正确落地；隔离真实 MySQL、Redis、MinIO 的 CI 等价回归通过 266/266，前端 type-check/build 均通过，新增测试不是 mock 或伪绿灯。

但整改仍未完成“实际媒体时长可信”这一核心目标：探测结果取自媒体头时长，未与样本时间线核对。变更还引入或暴露了重复 `complete` 可重复执行整对象下载与进程内解码、秒传绕过当前编码配置、秒传定稿与评审分配并发覆盖状态、V29 部分失败后不可自行重跑等高风险问题。依据独立复核闸门，当前没有放行条件；本报告没有执行畸形媒体构造、并发压测、模糊测试或其它安全敏感操作，相关结论来自本地静态数据流、状态机、依赖字节码和标准构建测试。

### Score Dashboard

```text
Security        ██████░░░░  5.5  B   不可信媒体仍在主 JVM 无资源隔离解析，秒传缓存可绕过当前编码策略
Stability       █████░░░░░  4.5  C   重复定稿会重复整对象探测，且评审状态存在确定性并发覆盖窗口
Performance     █████░░░░░  5.0  B   每次探测复制完整对象到系统临时盘，缺少去重、配额与总时限
Testing         ███████░░░  7.0  A   266 个真实依赖测试可信，但关键时长、编码、并发和 2GB 边界未覆盖
Maintainability ████████░░  7.5  A   探测接口与指纹职责清晰；缓存验证版本和状态迁移责任仍分散
Design          ██████░░░░  5.5  B   媒体事实、动态策略与评审状态没有统一的权威判定/迁移边界
Release         █████░░░░░  4.5  C   V29 不可重入，演示资源与数据库元数据不一致
─────────────────────────────────────
Overall         ██████░░░░  5.6  B   修复方向正确且门禁真实，但仍有 6 项 High 阻断
```

各维度 0.0–10.0，分数越高越好。本分数只评价本次增量及其直接影响面，不代表项目最终全量审计分数。

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|----------|-------|-----------|-----------|
| Critical | 0 | 0 | 0 |
| High | 6 | 6 | 0 |
| Medium | 2 | 2 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **9** | **9** | **0** |

### Change Summary

- Total files changed: 27
- Lines added: 859
- Lines deleted: 123
- Commits in range: 1
- Authors: wenbibuhaoqwq
- Range: `37f6cdb..ca400f1`

### Change Categories

- New features: 8 files（服务端媒体探测、可信指纹、秒传边界）
- Bug fixes: 4 files（VO/API 与上传完成流程）
- Refactoring: 2 files（文件对象读取接口与视频校验支持类型）
- Dependency updates: 2 files（父 POM 与 business 模块 JCodec）
- Configuration: 2 files（CI、动态编码参数）
- Tests: 4 files（Phase 7/14/24 与短视频样本）
- Documentation: 7 files（规格、Phase 7、进度和交接）

### Risk Delta

- New risks introduced or newly exposed: 9
- Existing risks fixed: 3（CI 零状态依赖、跨主体指纹复用、普通 VO 指纹泄露）
- Existing risks made worse: 2（完成接口新增整对象主进程探测；演示视频替换后元数据失配）

### Test Coverage Delta

- New code with direct tests: 7 files
- New code without关键边界测试: 4 files（动态编码变更、重复完成、状态并发、迁移部分失败恢复）
- Deleted tests: 0 files

## 2. Project Map

浏览器通过预签名 URL 将视频分片直接写入 MinIO；`VideoReviewServiceImpl.complete` 完成对象后调用 `JcodecVideoMediaProbe`，后者把完整对象复制到系统临时文件、计算服务端 SHA-256 tree 指纹并用 JCodec 读取视频信息。校验结果随后写入 `file_object` 与 `video_review`。秒传从 `file_object` 和既有 PASS 评审中复用对象；评审分配独立修改同一 `video_review` 行。V29 扩展摘要字段、可信标记和索引，CI 提供 MySQL、Redis、MinIO 后运行全量 Maven 门禁。

最高风险边界是：MinIO 对象到主 JVM 的不可信媒体解析、`complete` 的幂等认领、秒传缓存与动态参数的一致性、`video_review` 的并发状态所有权，以及 MySQL 非事务 DDL 的失败恢复。

### Coverage Matrix

| Dimension | Coverage | Evidence inspected | Exclusions / limits |
|-----------|----------|--------------------|---------------------|
| Security | High | 媒体探测、服务端指纹、秒传主体约束、VO、JCodec 0.2.5 关键字节码 | 按用户要求未执行畸形样本、模糊测试或安全利用 |
| Stability | High | `complete` 状态机、临时文件生命周期、异常路径、真实 Phase 7 与全量回归 | 未执行并发压测或 2GB 实传 |
| Performance | Medium | 整对象下载、临时盘写入、同步解码、重复调用路径 | 未做容量基准；结论来自确定性 I/O 路径 |
| Testing Authenticity | High | Phase 7/14/24 IT、真实预签名 HTTP、真实 MinIO、Surefire/Failsafe 报告 | 未执行浏览器 E2E；不属于本批 CI 修复范围 |
| Release | High | CI workflow、V29、全新 schema 迁移、演示初始化耦合 | 未模拟生产 DDL 中途失败；按静态 MySQL DDL 语义判定 |
| Configuration | High | `video.allowedCodecs` 种子、读取点、秒传路径、CI endpoint | 未改变运行参数做敏感边界验证 |
| Data Integrity | High | review upsert/assign、会话认领、唯一约束、迁移和对象引用 | 未主动制造并发交错；按事务边界与更新语句证明 |

## 3. Top Risks

1. **High — 媒体时长仍由单一头字段决定：**“实际时长”没有与样本时间线交叉校验，原真实性 Major 未闭环。
2. **High — 重复 complete 重复整对象探测：**同一 MERGING 会话可产生多份全对象临时文件和重复解码。
3. **High — 秒传缓存绕过当前编码配置：**命中旧 PASS 对象时构造 `codec=null` 的“有效”检查结果，不重评当前 `video.allowedCodecs`。
4. **High — 秒传定稿与评审分配竞态：**两个流程普通读写同一 review，可把 REVIEWING 覆盖回 WAIT_REVIEW。
5. **High — V29 不是可恢复迁移：**多个非幂等 DDL 在 MySQL 中分别提交，部分失败后重跑会撞重复列或索引。
6. **High — 演示视频与元数据失配：**样本已从 528B 改为 1,605,702B，但 demo SQL 和升级覆盖逻辑仍保留旧对象/旧大小。
7. **Medium — Phase 7 验收勾选超过自动化证据：**2GB、非允许编码、首帧不可解码和并发探测没有对应测试。
8. **Medium — 多视频轨道只校验第一轨：**允许编码策略没有覆盖全部视频轨道。
9. **Low — CI MinIO 就绪探针存在冷启动抖动窗口：**`/live` 成功后只执行一次建桶命令。

## 4. Detailed Findings

### Finding: 媒体头时长未与样本时间线交叉校验

- Severity: High
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: WS-3 媒体真实性校验
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:137-162`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:1468-1485`
  - Function / Module: `inspectMp4`、`validateMergedVideo`
  - Relevant behavior: 业务直接使用 `DemuxerTrackMeta.getTotalDuration()`；JCodec 0.2.5 的该值来自媒体轨道头时长，当前代码没有与 `stts` 或最后样本时间戳核对。
- Change type: Added
- Risk introduced: 客户端声明时长不再被信任，但信任边界转移到了单一容器元数据字段。
- Problem: 代码能确认容器可解析、存在首帧且首帧可解码，却不能确认声明为目标时长的时间轴确实包含对应长度的媒体样本。规格要求“实际媒体轨道时长”，当前实现只读取一份可独立失配的元数据。
- Why it matters: 短视频可能被错误标记为 `format_check=PASS` 并进入正式评审，直接破坏教学视频时长硬约束。
- Realistic failure scenario: 一个语法有效、首帧可解码，但媒体头时长与样本时间线不一致的文件进入定稿；探测返回目标时长，业务只做目标值容差比较并放行。
- Minimal fix: 从样本时间表或最后一个可读 packet 的 PTS + duration 计算实际时间线，并要求与媒体头时长在小容差内一致；不一致时 fail-closed。
- Better long-term fix: 让独立、受资源限制的媒体探测服务输出包含容器时长、样本时间线、编码、分辨率和验证器版本的签名结果，业务层只消费该权威结果。
- Regression test suggestion: 增加受控夹具，令媒体头时长与样本时间线不一致，断言完成接口返回 `VALIDATION_FAILED`；测试只在隔离环境由项目维护者执行。
- Estimated effort: 1–2 days

### Finding: MERGING 重试可重复执行全对象下载与主进程解码

- Severity: High
- Confidence: High
- Category: Stability
- Status: Confirmed
- Affected area: 视频直传完成与媒体探测资源边界
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:350-427`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:49-76`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:81-166`
  - Function / Module: `complete`、`inspect`、`downloadAndFingerprint`
  - Relevant behavior: 只有 UPLOADING→MERGING 的首个请求需要认领；后续读到 MERGING 的请求仍继续 `findObject`、复制完整对象和 JCodec 解码。每次调用各自创建系统临时文件。
- Change type: Modified
- Risk introduced: 新增服务端真实性探测后，幂等重试从轻量状态查询变成最多 2GB 的重复磁盘、网络和 CPU 工作。
- Problem: `claimed=false` 没有让重复请求返回“处理中”，也没有探测租约、单飞去重、队列容量或全局配额。探测在 HTTP 工作线程和主 JVM 内同步执行。
- Why it matters: 正常双击、网络重试或网关重复投递即可造成 N 倍对象下载、N 份临时文件和 N 次解码；磁盘、线程或 JVM 资源耗尽会影响全站。
- Realistic failure scenario: 首个完成请求已将会话置为 MERGING 并开始读取大对象，第二个重试请求也看到 MERGING；两个请求同时复制和解析同一对象。
- Minimal fix: 只允许成功认领者执行 complete/probe；其它 MERGING 请求立即返回稳定的“处理中”结果。为探测增加并发上限、磁盘配额、读取/总时限和专用临时目录。
- Better long-term fix: 把媒体探测移至容量受限的异步工作队列和独立受限进程，使用 durable lease + 幂等任务键，并记录可观测的探测状态。
- Regression test suggestion: 使用可计数的安全探测替身做确定性并发单测，断言同一 uploadId 只调用一次探测器；真实大文件容量测试由维护者在受控环境执行。
- Estimated effort: 2–4 days

### Finding: 秒传复用没有按当前编码策略重新判定

- Severity: High
- Confidence: High
- Category: Configuration
- Status: Confirmed
- Affected area: `video.allowedCodecs` 动态配置与 FAST_HIT
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:209-217`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:1447-1485`
  - Function / Module: `init` fast-hit 分支、`validateMergedVideo`
  - Relevant behavior: fast-hit 用旧 PASS 时长构造 `VideoMediaInspection.valid(..., codec=null, frameCount=1)`；后续校验只看 `valid` 和时长，不核对当前允许编码。
- Change type: Added
- Risk introduced: 已验证对象成为无验证版本的永久缓存，动态参数变更不能使旧结论失效。
- Problem: `video.allowedCodecs` 是可编辑运行参数，但可信记录没有持久化 codec、策略版本或验证时间，秒传也不重新探测。
- Why it matters: 管理员收紧编码策略后，同主体仍可通过旧指纹直接进入 WAIT_REVIEW，造成配置页面显示的规则与实际行为不一致。
- Realistic failure scenario: 文件在旧编码策略下 PASS；管理员更新允许编码集合；相同 uploader/student 再次命中旧对象，系统跳过探测并按旧结果放行。
- Minimal fix: 在可信记录中持久化 codec 与验证策略版本；fast-hit 仅在版本和当前配置匹配时复用，否则重新探测。命中前同时 HEAD 对象并核对 key/size。
- Better long-term fix: 将媒体判定建模为不可变 `media_validation` 记录，包含对象版本、摘要算法、规则快照、探测器版本与结果；所有复用统一走缓存有效性判断。
- Regression test suggestion: 在普通业务测试中更新 `video.allowedCodecs` 后再次请求 fast-hit，断言旧验证结果失效；另覆盖对象不存在时不命中。
- Estimated effort: 1 day

### Finding: 上传定稿与评审分配没有共享原子状态迁移

- Severity: High
- Confidence: High
- Category: Data Integrity
- Status: Confirmed
- Affected area: `video_review` 状态机
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:576-608`
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java:1268-1300`
  - Function / Module: `assign`、`upsertReviewAfterValidation`
  - Relevant behavior: 两条事务路径都普通读取 review，再用整实体 `updateById`；没有 `FOR UPDATE`、乐观版本或带旧状态条件的 CAS。
- Change type: Modified
- Risk introduced: fast-hit 让 init 请求也能直接更新既有 review，与评审分配形成新的并发写路径。
- Problem: 一个事务可基于旧 WAIT_REVIEW 快照完成验证，另一个事务可插入任务并改为 REVIEWING；先读后写的旧实体随后可能把状态覆盖回 WAIT_REVIEW。
- Why it matters: 最终可出现“已有评审任务但 review 不是 REVIEWING”的不变量破坏，评委收到任务却无法提交评分；并发创建也可能向一个请求暴露通用唯一冲突。
- Realistic failure scenario: 定稿事务读到 WAIT_REVIEW；分配事务创建任务并提交 REVIEWING；定稿事务用旧实体更新 fileId 和状态，将其覆盖回 WAIT_REVIEW。
- Minimal fix: `assign`、普通定稿、fast-hit 和退回重传对同一 review 使用统一行锁或带版本/旧状态条件的 CAS；更新失败时重读并按状态机决定返回。
- Better long-term fix: 把 review 状态迁移集中到单一领域服务，以明确事件和允许的 from→to 条件更新；禁止其它路径整实体覆盖状态。
- Regression test suggestion: 用两个受控事务和 latch 编写确定性并发测试，断言最终状态与任务集合一致，且重复 init 返回幂等结果。
- Estimated effort: 1–2 days

### Finding: V29 在部分 DDL 成功后无法安全重跑

- Severity: High
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: Flyway V29 升级恢复
- Evidence:
  - File: `platform-boot/src/main/resources/db/migration/V29__trusted_video_media_validation.sql:1-17`
  - Function / Module: `V29__trusted_video_media_validation.sql`
  - Relevant behavior: 三个独立 `ALTER TABLE` 没有存在性保护；MySQL DDL 按语句提交。全新 schema 只证明一次性成功，不证明中途失败后的可恢复性。
- Change type: Added
- Risk introduced: 生产升级在锁等待、连接中断或后续 DDL 失败时可能留下部分结构，Flyway 重试从第一句开始会撞重复列/索引。
- Problem: 迁移违反项目 R5“幂等可重复执行”要求，也没有拆分成可单独确认的版本或提供失败恢复手册。
- Why it matters: 发布可卡在半升级状态，需要人工判断并改库，增加停机和数据修复风险。
- Realistic failure scenario: `file_object` 与 `video_upload_session` 的 ALTER 已提交，第三个 ALTER 失败；修复环境后再次 migrate，第一条 ALTER 因列或索引已存在而再次失败。
- Minimal fix: 在未发布前重写 V29 为可重入的条件 DDL，或拆成独立版本并以信息模式检查存在性；增加部分落地后的恢复测试。
- Better long-term fix: 建立 MySQL 迁移失败演练与 expand/contract 规范，CI 同时验证空库安装和上一发布版本升级、故障恢复。
- Regression test suggestion: 从 V28 schema 开始，准备“V29 前半结构已存在”的安全测试 schema，运行迁移并断言收敛到唯一目标结构。
- Estimated effort: 0.5–1 day

### Finding: 新演示视频与数据库元数据及升级对象不一致

- Severity: High
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: demo profile / Phase 53 演示数据
- Evidence:
  - File: `platform-boot/src/main/resources/db/demo/sample-video.mp4`
  - File: `platform-boot/src/main/resources/db/demo/demo-data.sql:51-53`
  - File: `platform-boot/src/main/resources/db/demo/demo-data.sql:184-186`
  - File: `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/config/DemoDataInitializer.java:66-95`
  - Function / Module: 演示资源、`uploadIfMissing`
  - Relevant behavior: 新资源为 1,605,702B；SQL 仍写 528B、旧摘要和 528 uploaded_bytes；已存在相同 key 时初始化器直接跳过，不替换旧 528B 对象。
- Change type: Modified
- Risk introduced: 为测试真实性替换二进制资源时，没有同步其所有持久化元数据和升级幂等逻辑。
- Problem: 新装 demo 的 DB 元数据与 MinIO 对象不一致；从旧 demo 升级时对象仍可能保持不可播放的旧占位文件。
- Why it matters: Phase 53 演示播放仍不可靠，完成/播放/对象长度核对可能得到相互矛盾的结果，且当前提交的文档容易让后续人员误以为样本替换已经闭环。
- Realistic failure scenario: 旧 demo 环境升级后启动；初始化器看到 object key 已存在并跳过，数据库 upsert 继续写旧 528B 元数据，前端仍读取旧对象或显示错误大小。
- Minimal fix: 用生成资源的真实 size、服务端指纹和 uploaded_bytes 更新 demo SQL；初始化时校验对象 size/摘要，旧占位不匹配则受控替换。
- Better long-term fix: 从资源自动生成 demo metadata manifest，初始化器以 manifest 做可重入 reconcile，避免二进制与手写 SQL 双重事实源。
- Regression test suggestion: 覆盖空 MinIO 首装和预置旧 528B 对象的升级，两种情况下都断言对象可播放且 DB size/hash 与对象一致。
- Estimated effort: 0.5–1 day

### Finding: Phase 7 验收勾选超过现有自动化证据

- Severity: Medium
- Confidence: High
- Category: Testing
- Status: Confirmed
- Affected area: Phase 7 验收清单与 IT
- Evidence:
  - File: `docs/phase-07-视频评审.md:49-50`
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase7VideoReviewIT.java:316-345`
  - File: `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/Phase7VideoReviewIT.java:1165-1172`
  - Function / Module: Phase 7 acceptance、`largePresignedMultipartUploadCompletesWithExpectedObjectMetadataAndBytes`
  - Relevant behavior: 清单已勾选 2GB、超大小、非允许编码和不可解码首帧；现有“大文件”测试实际是 5MiB + 4096B，后两类没有专项反例。
- Change type: Modified
- Risk introduced: 文档将部分协议级或相邻反例提升为完整验收证据，掩盖了新探测器的重要分支缺口。
- Problem: 25/25 的测试计数真实，但不能证明清单上所有媒体与容量边界。绿灯的真实性和绿灯的覆盖范围需要分开描述。
- Why it matters: 后续重构 JCodec、配置或大文件路径时，关键退回项可能再次回归而 CI 仍保持全绿。
- Realistic failure scenario: 编码白名单或首帧检查被破坏，现有伪容器/短视频/指纹测试仍全部通过，阶段文档却继续显示已覆盖。
- Minimal fix: 暂时把未覆盖清单项标为待验证，或补安全、可维护的受控媒体夹具；把 2GB 容量验证明确归入受控环境，不以 5MiB 测试替代。
- Better long-term fix: 为每条 Phase 验收项维护测试 ID、命令、环境与证据链接，区分自动化、协议模拟、容量测试和人工验收。
- Regression test suggestion: 增加允许/不允许编码、首帧损坏、缓存策略版本、重复 complete 和迁移恢复测试；2GB 仅由维护者在隔离容量环境执行。
- Estimated effort: 1–2 days

### Finding: 只校验第一条视频轨道

- Severity: Medium
- Confidence: High
- Category: Security
- Status: Confirmed
- Affected area: 多轨 MP4 编码策略
- Evidence:
  - File: `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/JcodecVideoMediaProbe.java:131-156`
  - Function / Module: `inspectMp4`
  - Relevant behavior: 获取 `videoTracks` 后固定选择 `videoTracks.get(0)`，其余视频轨不参与编码或可解码检查。
- Change type: Added
- Risk introduced: 新编码白名单仅约束第一视频轨，而不是文件的全部视频内容。
- Problem: 多视频轨文件可能同时包含允许与不允许的编码，当前 PASS 语义没有定义是拒绝多轨、检查默认轨，还是检查所有可播放轨。
- Why it matters: 业务规则 `video.allowedCodecs` 的实际覆盖面不明确；播放器选择不同轨道时，服务端验证和用户实际播放内容可能不一致。
- Realistic failure scenario: 第一轨满足 H264 和首帧条件，第二轨不符合当前编码策略；探测器仍按第一轨返回 PASS。
- Minimal fix: 明确产品规则；若只允许单视频轨则直接拒绝多轨，否则检查所有可播放视频轨并持久化选轨规则。
- Better long-term fix: 让探测结果返回完整轨道清单，并在业务策略层显式决定允许的轨道组合。
- Regression test suggestion: 增加受控双视频轨样本，断言实现与文档规定的单轨/全轨策略一致。
- Estimated effort: 0.5–1 day

### Finding: CI 在 liveness 后单次建桶存在启动抖动窗口

- Severity: Low
- Confidence: High
- Category: Release
- Status: Confirmed
- Affected area: GitHub Actions MinIO 启动
- Evidence:
  - File: `.github/workflows/ci.yml:47-64`
  - Function / Module: `启动并等待 MinIO`
  - Relevant behavior: 轮询 `/minio/health/live` 后只执行一次 `mc mb --ignore-existing`。
- Change type: Added
- Risk introduced: 进程存活与认证/桶操作完全就绪之间若存在短窗口，CI 会偶发失败。
- Problem: 当前认证建桶本身是有效功能探针，但没有重试；固定版本降低了概率，没有消除冷启动竞态。
- Why it matters: 偶发红灯会降低门禁可信度并浪费重跑时间，但不会改变产品数据。
- Realistic failure scenario: liveness 已返回成功，认证子系统尚未接受建桶请求；单次 `mc mb` 失败并终止 job。
- Minimal fix: 改用 readiness 探针，并对认证建桶命令做有限次数、带退避的重试。
- Better long-term fix: 封装统一 CI dependency-ready 脚本，对健康、认证和最小读写探针分别给出超时与日志。
- Regression test suggestion: 在 CI 中保留固定版本，验证 readiness + 建桶重试最终成功且超时会输出容器日志。
- Estimated effort: 30–60 minutes

## 5. Security Concerns

- Coverage: High
- Inspected evidence: 服务端媒体探测、JCodec 关键元数据路径、指纹算法、秒传主体绑定、VO 与测试夹具。
- Exclusions / limits: 遵照用户要求，没有执行畸形媒体构造、漏洞利用、模糊测试或攻击性并发操作。

原跨账户/跨学生指纹重放已被同 uploader、同 student、服务端可信摘要和既有 PASS 记录共同限制，普通 `VideoReviewVO` 也不再暴露指纹。剩余安全边界主要是媒体事实只由单一头字段代表、只检查第一视频轨，以及不可信媒体仍在主 JVM 内同步解析；前两项已列为 Finding 1/8，资源隔离问题列为 Finding 2。

## 6. Stability Concerns

- Coverage: High
- Inspected evidence: `complete` 认领与恢复路径、临时文件清理、异常捕获、review 更新、真实 Phase 7 和全量门禁。
- Exclusions / limits: 未执行 2GB 实传、并发压测或进程崩溃测试。

最大稳定性风险不是单次顺序调用，而是同一 MERGING 会话的重复调用没有 single-flight。进程正常结束时临时文件会在 finally 删除，但异常终止或系统删除失败仍可能在系统临时目录留下完整视频；建议与 Finding 2 一并迁入专用受限临时卷并做启动清扫。review 状态竞态见 Finding 4。

## 7. Performance Concerns

- Coverage: Medium
- Inspected evidence: MinIO InputStream、64KiB 读取循环、完整临时文件写入、JCodec 首帧解码、重复完成路径。
- Exclusions / limits: 未进行吞吐、磁盘水位、堆内存或真实 2GB 基准。

单次探测是 O(file size) 网络与磁盘 I/O，且客户端直传节省的后端带宽会在定稿时完整发生一次；这可以是有意的真实性成本，但必须通过队列、配额和单飞控制有界化。当前 N 个重复请求导致 N 倍成本，因此 Finding 2 是性能与稳定性共同阻断。

## 8. Release Concerns

- Coverage: High
- Inspected evidence: GitHub Actions、Maven/Node 门禁、V29、全新 schema、demo SQL、demo 初始化器、资源大小。
- Exclusions / limits: 未在生产副本模拟 DDL 中途失败；未运行浏览器人工演示。

CI 零状态依赖已实质修复并由独立等价环境通过。发布仍被 V29 的失败恢复性和 demo 资源一致性阻断，分别见 Finding 5/6。CI readiness 抖动是 Low，不单独阻断。

## 9. Configuration Safety Analysis

- Coverage: High
- Inspected evidence: `video.allowedCodecs` V29 种子、`allowedCodecs()`、fast-hit 构造与业务校验。
- Exclusions / limits: 未在运行环境动态改参做敏感验证；结论来自两条确定性代码路径。

### Configuration Summary

| Subtype | Count | Affected Keys / Files | Recommended Action |
|---------|-------|-----------------------|-------------------|
| SchemaValidation | 0 | 无新增启动期 schema | 保持 |
| UnsafeDefault | 0 | 默认 H264 合理 | 保持参数化 |
| EnvironmentSeparation | 0 | CI/dev MinIO 契约一致 | 保持 |
| SecretConfig | 0 | 未发现新硬编码生产密钥 | 保持环境注入 |
| FeatureFlag | 1 | `video.allowedCodecs` | 持久化验证版本并让秒传按当前策略失效 |
| ConfigDocs | 1 | Phase 7 编码规则 | 补缓存失效语义和验收测试 |

Finding 3 说明动态配置与缓存验证缺少版本绑定。配置本身可编辑，但行为并非“改后立即对所有新提交生效”。

## 10. Data Integrity Analysis

- Coverage: High
- Inspected evidence: review upsert/assign、上传会话认领、文件对象可信标记、唯一键、V29 DDL。
- Exclusions / limits: 未主动执行并发交错或破坏迁移；按事务与 SQL 更新语义复核。

### Integrity Summary

| Subtype | Count | Invariants at Risk | Recommended Action |
|---------|-------|-------------------|-------------------|
| TransactionBoundary | 1 | review 状态与 task 集合一致 | 共用行锁或 CAS |
| Idempotency | 1 | 同 uploadId 只探测一次 | durable lease / single-flight |
| ConcurrencyConsistency | 1 | REVIEWING 不被旧快照回退 | 统一状态迁移 |
| MigrationSafety | 1 | V29 失败后可重跑 | 条件 DDL / 拆分版本 |
| InvariantValidation | 2 | 媒体时间线、demo size/hash | 交叉校验并 reconcile |
| BackupRestore | 0 | 本批未改变 | 最终审计另核 |
| Reconciliation | 1 | DB READY 与 MinIO 对象存在 | fast-hit 前 HEAD，增加对象对账 |

Finding 2、4、5、6 是本节的阻断项。另一个非阻断缺陷是 `findVerifiedInstantHit` 先选同 uploader/hash 的最新 file，再检查 student PASS；较新的其它学生候选会遮蔽较旧合法候选，造成不必要的重新上传。建议把全部条件合并为一次 `EXISTS/JOIN` 查询。

## 11. Testing Authenticity Analysis

- Coverage: High
- Inspected evidence: Surefire/Failsafe XML、Phase 7/14/24 IT、预签名测试客户端、真实媒体资源、CI workflow、前端 type-check/build。
- Exclusions / limits: 没有浏览器 E2E、2GB 容量测试和用户明确禁止的安全敏感验证。

### Confidence Assessment

| Test Area | Real Confidence | Risk | Action |
|-----------|---------------|------|--------|
| 服务端内容指纹与跨主体秒传 | High | 基础主体边界回归概率低 | Keep |
| 真实 MinIO 预签名上传 | High | endpoint/签名/ETag 回归可捕获 | Keep |
| 伪容器与客户端伪报时长 | High | 能证明不信客户端声明，不能证明样本时间线 | Keep + extend |
| 动态编码配置与 fast-hit | None | 策略变更可被旧 PASS 绕过 | Add |
| complete 幂等与 review 并发 | Low | 状态/资源竞态可逃逸 | Add deterministic tests |
| V29 升级恢复 | Low | 只覆盖空库一次成功 | Add partial-state migration test |
| 2GB 容量边界 | None | 5MiB 测试不能代表磁盘/时限/OOM | Controlled environment only |

### Valuable Tests

- `Phase7VideoReviewIT` 通过真实 HTTP 预签名 URL写入真实 MinIO，并覆盖伪容器、真实短视频伪报客户端时长、64 位指纹不匹配、跨账户重放、同主体正常命中和 VO 去指纹。
- Phase 14/24 已改用真实 H264 样本，不再依赖空轨占位字节。
- 独立新 schema 执行 `mvn -B -ntp verify`：Surefire 121/121、Failsafe 145/145，合计 266/266，0 failure/error/skip；Phase 7 25/25、Phase 14 14/14、Phase 24 3/3。
- `npm --prefix frontend run type-check` 与 `npm --prefix frontend run build` 均成功。

### Suspicious Tests

- 没有发现 mock 掉 MinIO 或直接改库冒充上传成功的新增测试。
- 名为 large 的 Phase 7 测试只验证 5MiB + 4096B 的 multipart 语义，不应作为 2GB 容量证据。

### Missing Tests

- 媒体头时长与样本时间线不一致。
- 当前编码参数变化后的 fast-hit。
- 同 uploadId 重复 complete 只探测一次。
- fast-hit/定稿与 assign 的确定性并发状态不变量。
- V29 部分结构已落地后的迁移恢复。
- 多视频轨策略、非允许编码、可解析容器但首帧不可解码。
- demo 旧占位对象升级 reconcile。

## 12. Principles Compliance

本次变更在接口分层、服务端校验、真实依赖测试和敏感摘要最小暴露方面明显改善，但在 fail-fast、单一事实源、显式状态所有权和可重入迁移上仍未达到发布标准。

### Principles Violated

| Principle | Violations | Severity | Affected Areas |
|-----------|------------|----------|----------------|
| Fail-Fast | 2 | High | 重复 complete、缓存策略失效 |
| Single Source of Truth | 2 | High | 媒体时长、demo 资源元数据 |
| Atomic State Transition | 1 | High | `video_review` |
| Idempotency | 2 | High | 媒体探测、V29 |
| Bounded Resource Use | 1 | High | 整对象临时下载与解码 |
| Test-to-Requirement Traceability | 1 | Medium | Phase 7 勾选项 |

### Principles Respected

- 客户端摘要不再作为跨主体持有证明，服务端真实读取对象计算指纹。
- 文件复用同时绑定 uploader、student、可信算法标记和既有 PASS 记录。
- VO 移除内部去重键，最小化敏感实现信息暴露。
- CI 使用固定版本真实 MinIO，并在前端构建前显式执行 type-check。
- 新增媒体与上传测试主要走真实 HTTP、真实对象存储和真实 MySQL，不是伪造绿灯。

---

## 13. Approval Recommendation

**Request changes（复核退回）。**

U-001（CI MinIO + frontend type-check）可单项判定闭环。U-002 中跨主体秒传与 VO 去指纹可判闭环，但“实际媒体时长可信”仍未闭环；Finding 2–5 构成新的 WS-3 发布阻断，Finding 6 归入仍退回的 Phase 53。WS-3 继续保持 CHANGES REQUESTED，不得置 PASS。

## 14. Recommended Fix Order

### Fix Immediately

1. 修正媒体时长为样本时间线与头字段交叉验证（Finding 1）。
2. 让同一 uploadId 的 complete/probe 具备独占认领与有界资源执行（Finding 2）。
3. 统一 review 状态迁移的行锁/CAS（Finding 4）。
4. 使 V29 可从部分落地状态安全恢复（Finding 5）。

### Fix Before Stable Release

1. 为 fast-hit 持久化验证策略版本并按当前编码规则失效，命中前核对对象存在（Finding 3）。
2. 同步 demo size/hash/uploaded_bytes，并替换旧占位对象（Finding 6）。
3. 明确并实现多视频轨策略（Finding 8）。
4. 修正文档勾选或补齐对应测试（Finding 7）。

### Schedule Later

1. 将媒体解析迁出主 JVM，建设有资源上限的异步探测器。
2. 将媒体验证结果建模为带对象版本和规则版本的不可变记录。
3. 将 demo 数据改为 manifest 驱动 reconcile。

### Ignore for Now

CI readiness 单次建桶抖动（Finding 9）可在上述阻断项之后处理。

## 15. Quick Wins

- MERGING 且当前请求未认领时立即返回“处理中”，先消除重复探测。
- fast-hit 命中前增加对象 HEAD，并在验证记录上保存 codec/规则版本。
- 更新 demo SQL 的真实 size、uploaded_bytes 和摘要。
- 把 Phase 7 尚无证据的勾选项暂时恢复为未完成，避免状态失真。
- CI 改用 readiness 并给 `mc mb` 增加有限重试。

## 16. Long-term Refactor Plan

1. **媒体探测工作单元：**动机是隔离不可信解析和大文件资源；采用 durable job、独占 lease、专用临时卷、CPU/内存/磁盘/总时限；风险是上传完成从同步变异步；以状态机、重试、崩溃恢复和容量测试验证。
2. **统一 review 状态机：**动机是消除定稿、秒传、分配、退回重传的并发写分叉；采用单一领域服务和 CAS from→to 更新；风险是改变既有事务边界；以确定性并发 IT 和全量 Phase 7 回归验证。
3. **版本化媒体验证缓存：**动机是让参数和探测器升级可正确失效；记录对象版本、规则快照、算法与探测器版本；风险是历史数据迁移；以旧记录 fallback 为“需重验”并覆盖升级测试。
