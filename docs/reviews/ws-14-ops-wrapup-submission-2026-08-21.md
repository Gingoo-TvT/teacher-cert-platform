# WS-14 备份生命周期与记录归档 · 提交材料

## 结论边界

当前为 `LOCAL_IMPLEMENTATION_COMPLETE / INDEPENDENT_REVIEW_PENDING`。本材料是提交方自测，不签发独立 PASS，
不授权 merge、deploy、cutover 或项目 GO。

## 改动

- MinIO 实际备份桶/前缀新增稳定 ID `tcp-db-backup-retention` 的到期规则；匹配时跳写，更新时保留其它规则。
- `backup_record` 纳入既有分批物理清理，只删到期 `COMPLETED/FAILED`，保留 `PENDING/RUNNING`，不删除对象。
- 两端共用 `cleanup.backup.retentionDays`，默认 30 天；无 Flyway、无业务 API/权限/规则变化。
- Phase 53 现有 demo 样本已确认可播 H.264 并被初始化/播放链使用，本轮不重复修改二进制。

## 本地证据

- `mvn -B -ntp -o clean test`：64 suites / 416 tests，0 failure/error/skip，Checkstyle 0。
- `FileMaintenanceServiceTest`：19/19。
- `CleanupScheduleConfigTest`：3/3。
- `Phase47CleanupIT` 已扩展旧终态删除、近期终态保留、旧 RUNNING 保留、记录清理不删 MinIO 对象；当前只有
  正在运行的 prod 容器，提交方未连接或改动，留给独立隔离环境执行。

## 建议独立复核

1. 核对生命周期规则创建、幂等跳写、外部规则保留与异常失败返回。
2. 在隔离 MySQL/MinIO 执行 `Phase47CleanupIT`，确认终态/非终态与对象边界。
3. 核对候选 manifest、测试结果及无迁移边界；项目级结论仍保持 `CHANGES_REQUESTED / NO-GO`。
