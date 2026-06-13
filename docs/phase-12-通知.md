# Phase 12 · 通知提醒（M12）

> 优先级 P1 · 依赖：Phase 2，及各业务 Phase 的触发点 · 任务：T-101~T-103 · plan §M12
> 目标：站内信通知，覆盖提交/退回/评审分配/导出完成四类触发，预留邮件/短信通道。

## 1. 数据库（V15__notification.sql）
`notification`：user_id, type, title, content, biz_type, biz_id, read_flag, created_at。

## 2. 触发点
| 触发 | 接收人 | 接入阶段 |
|---|---|---|
| 提交提醒（待初审/待复审产生） | 对应审核人 | Phase 3/4/5/6 |
| 退回提醒 | 学生/教务员 | Phase 3/4/5/6 |
| 评审提醒（视频任务分配） | 评审教师 | Phase 7 |
| 导出完成提醒 | 发起人 | Phase 10 |

## 3. 设计
- `NotificationService.send(userId, type, title, content, bizRef)`；各业务在状态流转处调用（解耦：事件或直接调用）。
- 通道抽象 `NotifyChannel`：站内信（一期）+ 邮件/短信（预留实现）。

## 4. 接口与前端
- `GET /api/notice?read=`、`POST /api/notice/{id}/read`、`POST /api/notice/read-all`（权限 `notice:view`）。
- 前端通知中心 + 顶栏未读角标（轮询或 SSE）。

## 5. 验收清单
- [ ] 四类触发均产生站内信，接收人正确。
- [ ] 未读角标计数正确；标记已读后角标更新。
- [ ] 通道可扩展（邮件/短信留接口，关闭不影响站内信）。

## 6. 测试用例
- T-NOTI-1：学生提交材料 → 对应教务员收到"待初审"通知。
- T-NOTI-2：视频分配 → 被分配教师收到评审提醒。
- T-NOTI-3：导出完成 → 发起人收到通知，角标 +1。

## 7. DoD
四类触发站内信 + 未读角标 + 通道抽象可用。

## 8. 风险
- 触发点散落各业务 Phase，建议用领域事件统一收口，避免遗漏某状态流转不发通知。
