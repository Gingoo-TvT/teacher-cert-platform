# WP-C 复核报告 — 视频评审「退回后可重传」（Phase 17）

| 项 | 值 |
|---|---|
| 阶段 | WP-C / Phase 17（视频退回可重传，后端为主 + 前端最小） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-18 |
| 被复核提交 | `597cad9`（feat，单提交） |
| 增量基线 | `main..feature/wp-c-video-return`（video 控制器/Service/状态机/DTO + Phase7 IT + 前端最小 + 文档） |
| 迁移 | **无新增**（退回复用 `video:confirm`/`video:arbitrate`）；V1–V21 未改 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | Blocker 0 · Major 0 · Minor 0 |

---

## 一、结论
WP-C 一轮通过。视频评审新增 `RETURNED` 退回态与 `POST /api/video/reviews/{id}/return` 退回动作（学院负责人触发，复用 `video:confirm`/`video:arbitrate`，**未新增权限/迁移**）；退回后学生可重新上传，重传清理旧评分任务/上传会话/分片/终分并回到 `WAIT_REVIEW` 重新评审；**Phase 7 重传守卫不破**——仅 `RETURNED` 放行，`REVIEWING/NEED_REVIEW/REVIEW_COMPLETED/CONFIRMED` 仍拒。clean-room `mvn -B -ntp verify` **78/78 全绿**（Phase7 7→9），前端 `vue-tsc` + `vite build` 绿。

## 二、复核方法
1. **取增量**：`git diff main..597cad9`——video 控制器/服务/状态机/DTO、`Phase7VideoReviewIT`(+106)、前端 `video.ts`/`VideoReviewView`、文档；确认无迁移、无 Phase7 既有逻辑回改。
2. **读后端**：返还动作、守卫扩展、重传重置三处逐行核对。
3. **读 IT**：退回全链 + CONFIRMED 双禁 反例。
4. **clean-room**：`DROP/CREATE teacher_cert` → `mvn verify`（全新 V1–V21）+ 前端 type-check/build。

## 三、关键核对
| 项 | 结论 | 证据 |
|---|---|---|
| 退回动作 | ✅ | `returnReview` 置 RETURNED、`clearReviewOutcome`（终分/结论/仲裁/确认 清空）、解锁、富审计 `return`(old/new/comment/target)、通知学生 |
| 退回门槛 | ✅ | `CONFIRMED` 抛「已确认视频不可退回」；`returnable()` 仅 WAIT_REVIEW/REVIEWING/NEED_REVIEW/REVIEW_COMPLETED；意见 `@NotBlank` |
| 退回鉴权/范围 | ✅ | `ensureCanReturnReview` 经 `video:confirm`/`video:arbitrate` + `ensureCanWriteReview` 数据范围 |
| 守卫不破 | ✅ | `ensureReuploadable`：`status != RETURNED && taskCount>0` 才按任务拒；`!reuploadable()` 仍拒 REVIEWING/NEED_REVIEW/REVIEW_COMPLETED/CONFIRMED；既有 `reuploadIsRejectedAfter...` 不变仍绿 |
| 重传重置 | ✅ | `resetReturnedReviewForReupload`：归档旧任务(`reviewer_id=id` 避免重指派唯一冲突)、归档旧会话/分片、清终分、解锁→WAIT_REVIEW + 通知 |
| IT 覆盖 | ✅ | 退回→重传(WAIT_REVIEW/taskCount=0/新文件)→重新指派→88/84 结算 86；审计 old=REVIEW_COMPLETED/new=RETURNED/意见/operator/IP/target；CONFIRMED 退回与重传双禁 |
| 前端最小 | ✅ | `returnVideoReview` API + 「已退回」筛选项；`vue-tsc`/`build` 绿（完整退回 UI 在 Phase 21） |
| 迁移/冻结 | ✅ | 无新增迁移；V1–V21 冻结；Flyway v21 |

## 四、维度结论
D1 需求 ✅ · D4 红线 ✅（数据范围/双盲/分差结算未动，退回附加非破坏）· D5/6 质量安全 ✅（复用权限、唯一冲突处理周到）· D7 构建 ✅（78/78 + 前端绿）· D8 测试 ✅（全链 + CONFIRMED 反例 + 守卫回归）· D9 数据库 ✅（无迁移）· D10 回归 ✅（Phase2~14 全绿）· D11 文档进度 ✅（待复核未自 ✅）。

## 五、放行
1. PROGRESS WP-C 置 **✅ 已复核**。
2. 合并 `main`（本地私有、无远程、不 push）。
3. 放行 Phase 18（WP-D 评审指定 + 分组，迁移 V22）。
