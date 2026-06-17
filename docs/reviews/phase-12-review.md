# Phase 12 复核报告 — 通知提醒（T-101~T-103）

| 项 | 值 |
|---|---|
| 阶段 | Phase 12 通知（P1） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-17 |
| 被复核提交 | `0c82431`（feat T-101~T-103，单提交） |
| 增量基线 | `b7dd377..HEAD`（约 26 文件） |
| 迁移 | 新增 `V18__notification.sql`；V1–V17 未改动 ✓（`notice:view` 已在 V8 预种，无需补权限） |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | **Blocker × 0 · Major × 0 · Minor × 4（入 backlog）** |

---

## 一、结论

Phase 12 一轮通过。**四类触发点接入正确且非破坏**：提交（待初审/待复审）→ 对应学院教务员/负责人、退回/不通过 → 学生（复审退回抄送教务员）、视频分配 → 被分配评审教师、导出完成 → 发起人——均在既有 service 状态流转后调用 `ReviewNotificationHelper`/`NotificationService.send`；**接入严格附加**（6 个 service 仅 +注入 helper + send 调用，唯一非新增改动是把 `setStatus(returnTargetFromSecondRejected(...))` 拆成等价局部变量，语义不变）。**send 失败不影响主业务**：`ReviewNotificationHelper` 每法 try/catch + `NotificationServiceImpl.send` 每通道 try/catch（log.warn），且 send 非 @Transactional 边界、不触发回滚标记。**通知本人可见**：list/unreadCount 按当前 user_id 过滤、`markRead` WHERE id+user_id（他人通知 updated=0 → 403）、`read-all` 仅本人。`NotificationService` 置于 `platform-system`（business/exchange 依赖它，无循环）；通道抽象 `NotifyChannel`（站内信 `InAppNotifyChannel` + 预留 `NoopNotifyChannel`）。V18 表文本字段 + (user_id,read_flag)/(user_id,created_at) 索引；`notice:view` V8 预种。**关键回归证据：`mvn verify` 55/55，既有 Phase2~11 共 51 条全绿——触发点接入未破坏任何既有流程**；前端 type-check/build 绿。4 个 Minor 入 backlog。

---

## 二、复核方法（独立验证）

1. **取增量**：`git diff b7dd377..0c82431`（单提交；V1–V17 与治理文档未改）。
2. **干净构建 + 重跑反例 + 全回归**：`mvn -B -ntp verify` → **BUILD SUCCESS 55/55**（Phase2~11 共 51 回归 + `Phase12NotificationIT 4`）；前端 type-check/build 绿。
3. **读码裁决（100% 增量）**：`V18__notification.sql`、`NotificationServiceImpl`、`NoticeController`、`ReviewNotificationHelper`、**6 个触发点 service diff（逐一确认附加非破坏）**、`Phase12NotificationIT`。
4. 鉴于 P1 轻量 + 全回归绿 + 增量全读，未另派代理（与重/高危阶段不同，按比例处理）。

---

## 三、维度结论（D1–D11）

| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 四类触发 + 通知中心 + 未读角标 + 通道抽象 与 plan + phase-12 一致 |
| D2 验收清单 | ✅ | 四类触发接收人正确、角标计数、通道可扩展 |
| D3 AT 验收 | ✅ | 无新首验 |
| D4 红线合规 | ✅ | 通知本人可见、触发点非破坏、文本化、留痕 |
| D5 代码质量 | ✅ | helper 收口触发、双层 try/catch、分层/Result/BaseEntity |
| D6 安全 | ✅ | 本人可见（list/read 按 user_id）、他人通知 403 |
| D7 构建与运行 | ✅ | `mvn verify` 55/55、type-check/build 绿；未自起常驻服务 |
| D8 测试 | ✅ | Phase12NotificationIT 4/4：提交→教务员、退回→学生、视频分配→教师、导出→发起人+角标、本人可见+403 |
| D9 数据库 | ✅ | 仅 V18 新增、V1–V17 未改；文本字段、索引、软删除 |
| D10 回归 | ✅ | **Phase2~11 共 51 条回归全绿（触发点接入非破坏的硬证据）** |
| D11 文档/进度 | ✅ | 治理文档未改；PROGRESS 置「待复核」未自 ✅ |

---

## 四、做得好

- **触发点接入严格附加、非破坏**：6 个 service 仅注入 helper + 在 updateById 之后 send；唯一改动为等价局部变量重构；51 条既有回归全绿。
- **send 失败不影响主业务**：helper 每法 + service 每通道 双层 try/catch；send 非事务边界，通知落库失败不回滚审核/分配/导出。
- **通知本人可见**：`list`/`unreadCount` 按 `currentUserId()`，`markRead` 以 id+user_id 更新（他人 → updated 0 → 403），`read-all` 仅本人。反例 `noticeCanOnlyBeListedAndReadByOwner` 直证跨用户隔离。
- **架构无环**：`NotificationService` 在 platform-system；`ReviewNotificationHelper` 在 platform-business（依赖 system）；exchange 直接注入 NotificationService。通道抽象可扩展（邮件/短信留 Noop）。
- 接收人解析复用既有（`selectEnabledByRoleAndCollege`/`selectEnabledByStudentId`）；文本字段 codePoint 安全裁剪。

---

## 五、Minor（入 backlog，不阻断）

- "send 失败不影响主业务"未用强制通道异常直接造例（双层 try/catch 结构性保证，且非事务边界）；建议补一条注入失败通道的反例。
- 通知在业务 `@Transactional` 内（updateById 后）同步发送；当前 helper 捕获、非事务边界不致回滚，更稳妥可改 afterCommit/异步发送。
- 未读角标用轮询（规格允许轮询或 SSE）；大并发可后续上 SSE。
- 通知 `list` 全量 `selectList` 包 `PageResult`（非真分页，复发）；通知量大时建议分页。

---

## 六、放行

1. `PROGRESS.md` Phase 12 置 **✅ 已复核**；无新 AT 首验。
2. 合并 `main`（本地私有、无远程、不 push）；启动 Phase 13（系统管理与审计，AT-12 审核全留痕首验）。
3. 4 个 Minor 进 backlog。
