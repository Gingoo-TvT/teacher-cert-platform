# Phase 7 复核报告 — 教学能力视频评审（T-057~T-067）

| 项 | 值 |
|---|---|
| 阶段 | Phase 7 教学能力视频评审 |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `6318470`（feat T-057~T-067，单提交） |
| 增量基线 | `6b58173..HEAD`（约 35 文件；business 27 / boot 4 / frontend 4） |
| 迁移 | 新增 `V13__video.sql`；V1–V12 未改动 ✓ |
| **判定（轮次1 · 06-17）** | **❌ 退回（CHANGES REQUESTED）** |
| 计数 | **Blocker × 1 · Major × 1 · Minor × 7（入 backlog）** |

---

## 一、结论

Phase 7 主体质量很高，AT-08 头部全部到位且反例充分：**双盲"提交前互不可见"在接口层硬屏蔽**（`toVO` 过滤非本人任务 + `toTaskVO` 仅 owner/管理可见分 + 全量明细端点 `tasks()` 受 `video:assign/arbitrate/confirm` 把关；REVIEW_TEACHER 仅 `video:score/play`=ASSIGNED 触达不到）——三路代理 + IT 负向断言（`doesNotContain("\"score\":85")`）共同证明，**无泄漏路径**；分片上传 init/chunk/merge/progress + 断点续传 + MD5 秒传、服务端校验（MP4/大小/时长容差）、状态机B 分差结算（≤阈值且结论一致→均分；分差>阈值/结论冲突→需复评）、第三专家**两两最小对**（85/60/81→83）、`sys_param` 即改即生效、读+写+ASSIGNED 数据范围、鉴权播放（无 token→401、预签名限时、水印）均正确；大文件**不进内存**（MinIO 服务端 `composeObject`，回退路径顺序流式不缓冲整文件）；权限点 V8 §15.1 预种齐全、`@AuditLog` 覆盖全部写端点；V13 迁移幂等、V1–V12 与治理文档未改；`mvn verify` 28/28、type-check/build 绿。

**但存在 1 个 Blocker + 1 个 Major，必须修复后放行**——核心结算的完整性可被普通学生操作破坏：

- **B1（Blocker）重传未挂"可编辑态守卫"**：再次上传（含 MD5 秒传）在评审进行中（REVIEWING/NEED_REVIEW，`locked=0`）会**静默重置**视频评审且**不清理已有评审任务行**，导致 ①以**另一支视频的陈旧分数**完成结算（终分被悄悄算错），或 ②评审**永久卡死**无法结算。直接击穿 AT-08"对所提交视频独立评审、唯一终分"的保证。
- **B2（Major）`settleIfReady` 硬编码两评委**：`video.reviewerCount` 可配（assign 接受 3），但结算 `submitted.size()!=2` 直接抛错 → 人数设 3 时评审永久卡死，与 T-060/确认单#11"可配多专家"矛盾。

按 `REVIEW-GATE §5`（核心 AT 正确性 / 数据完整性）判 **退回**。退回聚焦 B1/B2；其余 AT-08 头部、双盲、结算公式、数据范围、鉴权播放**无需返工**。修复后只复核增量 + 回归。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff 6b58173..6318470`（单提交；V1–V12 与 AGENTS/REVIEW-GATE/HANDOFF/tasks 未改）。
2. **干净构建 + 独立重跑反例**：`mvn -B -ntp verify`（先 docker 起 MinIO/MySQL/Redis）→ **BUILD SUCCESS**，Failsafe 自动跑 `Phase2 2 + Phase3 7 + Phase4 5 + Phase5 4 + Phase6 4 + Phase7VideoReviewIT 6` ＝ **28/28**；前端 `type-check`(vue-tsc) 干净、`build` ✓。
3. **读码裁决**：`VideoReviewServiceImpl`(1062 行，全读)、`VideoReviewController`、`VideoReviewStatus`/`VideoUploadStatus`、`DataScopeSqlHandler`(diff)、`V13__video.sql`、`V8__rbac_seed.sql`、`Phase7VideoReviewIT`。
4. **三路独立代理**（AT-08 业务/状态机 · 数据范围/双盲/鉴权/权限 · 质量/DoD/迁移/前端/OOM/治理）：数据范围代理 **PASS**；业务代理 **CONCERNS**（独立判 B1 为 Blocker）；质量代理 **PASS**（独立判 B2 为 Major）。裁决采纳。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ⚠️ | 上传/校验/双盲/结算/复评/鉴权播放 与 plan + phase-07 一致；重传守卫缺失（B1） |
| D2 验收清单 | ⚠️ | AT-08 头部复跑通过；重传完整性未达"唯一终分"保证（B1） |
| D3 AT 验收 | ⚠️ | AT-08 双盲/结算/复评/鉴权 通过；B1 破坏结算完整性 |
| D4 红线合规 | ✅ | 数据范围读+写硬校验、文本化 String、留痕、不硬编码（阈值/人数/容差走 sys_param、9 维走字典） |
| D5 代码质量 | ⚠️ | 分层/事务/Result/BaseEntity 规范；死代码与 of() 容错（Minor）；B2 配置与实现不一致 |
| D6 安全 | ✅ | 双盲接口层屏蔽、写侧 collegeId 取自实体、跨学生/跨院 403、鉴权播放 401/限时预签名 |
| D7 构建与运行 | ✅ | `mvn verify` 28/28、type-check/build 绿；大文件不进内存；未自起常驻服务 |
| D8 测试 | ⚠️ | 6 反例覆盖头部充分；B1 重传/collegeArbitrate/confirm/越权播放403 未覆盖 |
| D9 数据库 | ✅ | 仅 V13 新增、V1–V12 未改；幂等、索引/唯一键/注释齐、文本字段 VARCHAR、JSON 维度分 |
| D10 回归 | ✅ | Phase2~6 共 22 条回归通过 |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅；DEVLOG 有 Phase 7 小结 |

---

## 四、做得好（无需返工）

- **双盲互不可见（生命线）接口层硬屏蔽**：`VideoReviewServiceImpl.toVO`（:815-820）按 `managementView || 本人` 过滤任务行，`toTaskVO`（:832-851）仅 owner/管理可见分；全量明细 `tasks()`（:419-428 / 控制器 :132）受 `video:assign/arbitrate/confirm` 把关；REVIEW_TEACHER 仅 `video:score/play`=ASSIGNED，触达不到 reveal-all 路径。IT 负向断言成立。
- **状态机B 结算 + 第三专家两两最小对**：`settleIfReady`（≤阈值且 `sameConclusion`→`round((s1+s2)/2)`；否则 NEED_REVIEW）、`bestPair`（85/60/81→取 85&81→83）正确；IT 覆盖均分/分差超阈/结论冲突/第三专家。
- **数据范围读+写+ASSIGNED**：三表注册进 `DataScopeSqlHandler`；`assignedExpression` 按 `reviewer_id=本人` 过滤 `video_review_task`；`video_review` 无 userColumn 故 ASSIGNED→DENY（评审员无法用 list 枚举全部，安全）；写侧 collegeId 取自 student 实体、跨学生/跨院 403。
- **鉴权播放/水印/大文件**：`/play` 限 `video:play`、越权 403、无 token 401、预签名限时（`video.presign.expirySeconds`）、水印含用户名+工号+时间戳；`merge` 用 MinIO `composeObject`，回退顺序流式不缓冲整文件（无 2GB OOM）。
- 分片 init/chunk/merge/进度 + 断点续传 + MD5 秒传；校验 MP4/大小/时长容差；`@AuditLog` 全覆盖；阈值/人数/容差走 `sys_param`、9 维走字典（确认单#13 待校方细则，留 TODO）；V13 幂等、V1–V12 冻结。

---

## 五、问题清单（须修）

| 编号 | 级别 | 维度 | 问题 / 证据 | 期望 |
|---|---|---|---|---|
| **B1** | **Blocker** | 数据完整性/可编辑态守卫 | `VideoReviewServiceImpl.upsertReviewAfterValidation`(:466-488) 仅以 `locked==1`(:472) 拦重传；`existingReview`(:955-960) 无状态过滤。REVIEWING/NEED_REVIEW（locked=0）下学生再次上传/秒传 → 覆盖 `videoFileId`、status 重置 WAIT_REVIEW，但**从不清理 `video_review_task`**。后果：①重指派后 `settleIfReady`(:615-625) 统计到**陈旧分**（A 在旧视频打的分）+ 新分 → 用错分结算新视频；②NEED_REVIEW 重置后两任务仍 `submitted=1`、无再结算触发 → **永久卡死**。秒传 `getByMd5` 全局无属主校验，重置可瞬时触发。 | 把"可编辑态守卫"扩展到视频：一旦该 (student,year) 评审**已有任务**或 status 越过 WAIT_REVIEW，`initUpload(秒传)`/`merge` 拒绝重传；或合法重传时**原子清空任务 + 重置 locked=0**。补反例：REVIEWING/NEED_REVIEW 重传被拒（或正确重置且不串分）。 |
| **B2** | Major | 配置一致性 | `settleIfReady`(:623) 读 `expected=video.reviewerCount` 后又 `if(submitted.size()!=2) throw`。人数设 3：`assign` 放行 3 人、第 3 人 `submitScore` 抛错回滚、评审卡死。 | 泛化 N 评委结算（如对所有初评做两两最小对 + 全体结论一致判定），或在 `assign`/配置处显式校验 `reviewerCount==2`。 |

---

## 六、Minor（入 backlog，可随 B1/B2 一并清）

- `arbitrate`(:367-381) 忽略 `VideoArbitrateRequest.conclusion`（DTO `@NotBlank` 却被 `conclusionByScore` 覆盖）——按分推导或删字段对齐契约。
- 死代码：`VideoReviewStatus.locked()`（未调用）、`VideoUploadStatus.FAST_HIT`（未赋值）、`ensureCanWriteStudent` 的 `ASSIGNED + "video:score"` 分支（无调用方，潜在风险：未走 `assignedToCurrentUser`）——删除或接入守卫（locked() 正好可用于 B1）。
- `VideoReviewStatus.of()`→WAIT_UPLOAD / `VideoUploadStatus.of()`→UPLOADING 未知值容错为**许可态**（脏数据可重启上传）——未知值抛错/映射为终态。
- 格式/时长为**声明可信**（content-type+扩展名 + 客户端 durationSeconds），无 ffprobe/magic-number——建议合并后探测魔数+时长；当前文档化此限制。
- `list()` 全量 `selectList`、total=size 非真分页（复发）。
- 前端 `VideoReviewView.quickHash` 对整文件 `arrayBuffer()`（2GB 进浏览器内存）且为 32 位非 MD5，与后端 `file_md5` 秒传口径不一致（IT 用真 MD5 掩盖）——改增量分片哈希、产出真 MD5。
- 测试覆盖：collegeArbitrate、confirm(REVIEW_COMPLETED→CONFIRMED)、重传守卫、跨范围播放 403、reviewerCount=3 实际结算、bestPair 平局 均未覆盖。

---

## 七、退回处理

1. 阶段在 `PROGRESS.md` 置 **复核退回**；**不合并 main、不置 ✅**；AT-08 维持 `[~]`。
2. codex 在原分支 `feature/phase07-T057-video-review` 仅修 **B1 / B2**（Minor 可一并捎带），并补反例：REVIEWING/NEED_REVIEW **重传被拒或正确重置不串分**、（如支持）N 评委结算；确保 `mvn verify` 仍绿；DEVLOG 记修复。
3. 重交后复核方**只复核增量 + 回归**，重点复跑：重传守卫（不串分/不卡死）、reviewerCount 一致性、双盲与数据范围未回归。直至 PASS。
