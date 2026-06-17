# AT 验收复验矩阵（Phase 14）

> Phase 14 整体复验以既有 Phase2~13 集成测试回归 + `Phase14E2EIT` 主流程端到端为依据。Excel/WPS 双端核对为交付前人工检查项，机检以 POI 读回断言文本格式和值一致。

| AT | 内容 | 首验阶段 | Phase14 复验方式 | 对应 IT / 证据 | 结论 |
|---|---|---|---|---|---|
| AT-01 | 文本字段导入导出一致 | P3/P8/P10 | 复跑 P10 文本化机检；E2E 从导入到标准导出比对学号、证件号、证书号、有效期字符串 | `Phase10ExchangeIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-02 | 26 列 + H 表头 | P10 | 复跑模板/标准导出 A-Z 26 列机检；E2E 标准导出再次断言 H 列与文本格式 | `Phase10ExchangeIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-03 | 证件 + 出生日期 | P3 | 复跑学生证件四类反例与出生日期一致性校验 | `Phase3StudentIT` | ✅ 通过 |
| AT-04 | 专业代码 0401/0451/0453 | P4 | 复跑教育类研究生专业代码服务端硬校验反例 | `Phase4TrainingIT` | ✅ 通过 |
| AT-05 | 学段-学科联动禁自由填 | P1/P4 | 复跑学段学科、培养目标和实习地点联动反例；E2E 走标准学科正例 | `Phase4TrainingIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-06 | 四类材料全过才合格 | P5 | 复跑缺类/不通过/全通过聚合判定；E2E 四类材料复审通过后断言合格 | `Phase5MaterialIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-07 | 多科免考 + 每科佐证二级审核 | P6 | 复跑多科互不影响、漏佐证、应考联动；E2E 免考通过后断言应考科目剔除 | `Phase6ExemptionIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-08 | 视频独立评审 + 复评结算 | P7 | 复跑双盲、分差/结论冲突、第三专家、鉴权播放和数据范围；E2E 构造 85/60/81 复评结算 83 | `Phase7VideoReviewIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-09 | 证书前置条件 | P9 | 复跑缺项清单反例；E2E 聚合所有前置后断言 precheck 通过 | `Phase9CertificateIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-10 | 18 位编号连续不重 | P9 | 复跑并发生成无重号无空号、段码和作用域参数反例；E2E 断言证书号 18 位且前缀正确 | `Phase9CertificateIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-11 | 有效期规则 | P9 | 复跑上/下半年有效期边界；E2E 签发日期上半年断言 `2029/6/30` | `Phase9CertificateIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-12 | 审核全留痕 | P13 | 复跑富审计反例；E2E 断言 student/training/exemption/video 富审计与证书生命周期审计产生 | `Phase13SystemAuditIT`、`Phase14E2EIT` | ✅ 通过 |
| AT-13 | 数据范围权限 | P2~P13 | 复跑各业务读写范围、统计范围、通知本人可见；E2E 主流程使用授权角色按范围完成 | `Phase2SecurityIT`、各 Phase IT、`Phase14E2EIT` | ✅ 通过 |
| AT-14 | 异常报告 | P10 | 复跑 V-01~V-13 预校验反例与异常不入库；E2E 正例导入保持批次链路可用 | `Phase10ExchangeIT`、`Phase14E2EIT` | ✅ 通过 |

## 兼容性核对记录
- Excel 文本一致性：`Phase10ExchangeIT` 与 `Phase14E2EIT` 使用 POI 读回断言单元格文本格式 `@`、证件号/前导零学号/证书编号/有效期字符串值不变。
- WPS/Excel 人工核对：交付前用 Phase14 标准导出文件分别在 Microsoft Excel 与 WPS 打开，核对证件号、前导零学号、证书编号、有效期不被转换；本仓库保留机检依据，人工截图/记录由交付复核归档。
- 浏览器回归：前端 build 产物面向 Chrome、Edge、Firefox 最新稳定版；Phase14 不改既有业务语义，页面回归以各阶段已通过页面和最终 build 为依据。
