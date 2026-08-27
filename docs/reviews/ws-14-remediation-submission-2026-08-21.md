# WS-14 两项 Medium 最小整改 · 增量复核提交材料

## 结论边界

提交时状态为 `LOCAL_REMEDIATION_READY / INDEPENDENT_INCREMENTAL_REREVIEW_PENDING`；后续正式 R2 报告已裁定
`INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/1L）`。该结论来自用户负责的独立复核，不授权 merge、push、
deploy、cutover 或项目 GO。

## 正式报告绑定

- 首轮候选 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- 首轮 fingerprint：`e6971aae225e1e7e49888f82a34d1587bfd9fd80f131c058410db4db25ac962f`
- 首轮 manifest SHA-256：`08c52be5dc90e598248c6059d8b88fdeb749e3a09323aa2663b43502cfaa9a36`
- 正式报告 SHA-256：`24b3487d0739f6f0027c50eb41584bd2cc89332f53ec73fd88e3a505d139c7b2`
- 正式结论：`CHANGES_REQUESTED（0 Critical / 0 High / 2 Medium / 0 Low）`

首轮 manifest 只绑定整改前字节，不能用于本轮。新整改候选身份由冻结后的仓外 manifest 单独提供。

## Finding 1：版本化桶非当前版本不回收

- 同一稳定 ID `tcp-db-backup-retention` 规则保留 `Expiration(days=N)`，增加
  `NoncurrentVersionExpiration(1)`。
- 幂等匹配同时要求 current days、prefix、enabled 与 `noncurrentDays=1`；current-only 或错误值会更新，完全匹配
  才跳写。
- 不增加独立 delete-marker 规则；这不属于本 finding 的关闭前置。

## Finding 2：fresh schema 缺少共享保留参数

- 新增 `V34__backup_retention_parameter.sql`，种入 editable `int/global`
  `cleanup.backup.retentionDays=30`。
- 棕地重复键只更新元数据与 active 状态，不覆盖学校已有的 45/90 等配置值。
- 复用现有参数更新 API，仅对该键要求 `>0`；45 可保存，0 与负数拒绝。对象生命周期调度和终态记录清理均有
  45 天读取回归。
- `docs/README.md` 与 `Phase00ParameterMatrixIT` 的 fresh-schema 精确合同同步为 33 项。

## 本地证据

- `FileMaintenanceServiceTest`：20/20。
- `SystemManagementServiceImplTest` + `RetentionCleanupServiceTest`：4/4。
- `CleanupScheduleConfigTest`：3/3。
- 九模块 `mvn -B -ntp -o clean test`：**66 suites / 421 tests**，0 failure/error/skip，Checkstyle 0。原 82/567
  混入历史 Phase 41 的 16 suites / 146 tests，已按正式报告勘误。
- 九模块离线 package、WS-7 合同、WS-8/V33 合同、candidate manifest 6/6 与 `git diff --check`：PASS。

## 独立增量复核结果

1. R2 外置 manifest fingerprint `4a1674636fc69fe1305003b0bdedbae8ee9df69ce627455b06073b6e9c69cb80`、
   SHA-256 `e4097df14a5d30f2ed1bed6703b8356623aeaf3362b11b1b2c149b0709f2d630` 已现场验证通过。
2. 正式报告 SHA-256 `8ade2dd744116306553bb06c1cef3e9e7c2e4df1bd6efe650ea6924a8686be61`，确认首轮两项
   Medium CLOSED，裁定 `INDEPENDENT_INCREMENTAL_PASS（0C/0H/0M/1L）`。
3. `Phase00ParameterMatrixIT` / `Phase47CleanupIT` 本轮未在隔离依赖执行；报告明确其不构成功能 finding，保留为
   稳定发布前证据。提交方未连接或修改生产依赖。
