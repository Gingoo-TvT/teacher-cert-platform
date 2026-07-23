# Phase 42 独立复核报告

- 复核日期：2026-07-23
- 复核者：Codex（用户授权独立复核）
- 结论：**CHANGES REQUESTED**
- 提交：`3e65a02`、`d6f5ee7`、`fd26c41`、`b9a3685`
- 范围：唯一约束、导入原子认领、视频并发结算/合并、证书重开

## 独立证据

- V24 在 fresh schema 成功；证书重开、视频计票/合并和双 confirm 反例纳入 265/265。
- 新增对抗审查覆盖 confirm 与 rollback 同时执行，确认现有状态机允许晚提交数据留在已回滚批次。

## D1–D11

| 维度 | 结论 | 说明 |
|---|---|---|
| D1–D4 规格/验收/AT/红线 | 不通过 | confirm/rollback 的批次终态与业务数据可以不一致。其余唯一键、视频和重开不变量通过。 |
| D5–D6 质量/安全 | 不通过 | `IMPORTING` 被当作可回滚态，但没有停止/租约/对账协议。 |
| D7–D10 构建/测试/数据库/回归 | 部分通过 | 全量绿；缺 confirm-vs-rollback 并发测试。 |
| D11 文档 | 不通过 | 现有计划把“收尾守卫”描述为闭环，实际上 update 0 行被忽略。 |

## Major

`ExchangeServiceImpl.confirmImport` 每行 `REQUIRES_NEW` 提交；`rollback` 在 `IMPORTING` 时只回滚一次性读取到的 refs，confirm 可在之后继续提交，最终条件更新命中 0 行也不报错。必须重新设计停止/回滚协议并补屏障型 IT。详见 PG-H3。
