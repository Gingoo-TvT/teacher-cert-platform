# WP-A 复核报告 — RBAC 基座重定义（收官后重构 Phase 15）

| 项 | 值 |
|---|---|
| 阶段 | WP-A / Phase 15（RBAC 角色权限矩阵重定义，后端为主） |
| 复核人 | Claude（按 `docs/REVIEW-GATE.md` 独立复核） |
| 复核日期 | 2026-06-18 |
| 被复核提交 | `6227cdd`（refactor，单提交） |
| 增量基线 | `main..feature/wp-a-rbac`：V20 迁移 + 9 IT + PROGRESS/DEVLOG（**无业务/main java 改动**） |
| 迁移 | 新增 `V20__rbac_regrant.sql`；V1–V19 未改 ✓ |
| **判定** | **✅ PASS（一轮通过）** |
| 计数 | Blocker 0 · Major 0 · Minor 1（入 backlog） |

---

## 一、结论
WP-A 一轮通过。RBAC 重定义**纯由 V20 运行期授权完成、不改任何业务/安全代码**（SYS_ADMIN 取得全表权限后既有 perm 校验自然放行，无需新机制）。**clean-room 验证**（重置 dev schema → Flyway 全新应用 V1–V20 → 跑全量 IT）：`mvn -B -ntp verify` **BUILD SUCCESS、Failsafe 75/75**（61 回归 + `Phase14E2EIT` 14，全在新模型下绿），日志「Successfully applied 20 migrations, now at v20」证 V20 全新可用且与既有迁移共存。

## 二、复核方法
1. **取增量**：`git diff main..6227cdd`——仅 V20 + 9 个 IT + 进度/日志；`grep src/main/java` 确认**无业务代码改动**。
2. **读 V20 逐角色对矩阵**：revoke(CLERK/AUDITOR/SYS_ADMIN/CERT_ISSUER 旧授权)→按 §1 矩阵 re-grant；ON DUPLICATE KEY 幂等；CERT_ISSUER 角色软删 + `test_cert_issuer` 停用。
3. **读 9 个 IT diff**：核对角色迁移正确且**保持用例原意**。
4. **clean-room 构建**：`docker exec ... DROP/CREATE teacher_cert` → `mvn -B -ntp verify`（消除此前我手工起后端误用中间态 V20 的污染，确保 V20 以提交版校验和全新落库）。

## 三、V20 授权矩阵核对
| 角色 | 结论 | 要点 |
|---|---|---|
| SYS_ADMIN | ✅ | 动态 `INSERT…SELECT` 关联 `sys_permission` 全表（status=1）、SYSTEM scope → 超级管理员 |
| ACADEMIC_ADMIN | ✅ | 不在 revoke 名单（保留原授权）+ 新增 `cert:issue`（SCHOOL）→ 证书签发并入 |
| COLLEGE_AUDITOR（学院负责人）| ✅ | `*:secondReview` + `student:import/edit`、`training:edit`、`video:assign/arbitrate/confirm`、`test:edit/import`、`exchange:import/prevalidate` + 只读项 |
| COLLEGE_CLERK（学院教务员）| ✅ | `*:view` + `info/material/exemption:firstReview`（**保留初审**）+ `student:export`/`exchange:export:*`/`material:batchDownload`；**无** secondReview/edit/import/assign/test/manage |
| REVIEW_TEACHER | ✅ | 未改（video:score/play 保留） |
| CERT_ISSUER | ✅ | 角色软删 + 角色权限撤销 + `test_cert_issuer` 停用 |
| 幂等 / 冻结 | ✅ | revoke→regrant + ON DUPLICATE KEY 可重跑；V1–V19 未改；clean-room 全新应用通过 |

## 四、IT 适配核对（保持原意）
- **Phase2SecurityIT（契约用例）**：钉死新矩阵——CLERK contains firstReview/view/export 且 doesNotContain secondReview/edit/import/assign/test；AUDITOR contains 复审+动作权 且 doesNotContain firstReview；ACADEMIC contains cert:issue；SYS_ADMIN 跨域全权。✓
- **跨院写越权（Phase3/4/8/10）**：写动作迁至 AUDITOR 后，越权用例改用 AUDITOR 执行——仍校验**数据范围**（403 因跨院，而非缺权），用例原意保留。✓
- **video:assign（Phase7/12/14）**：clerk→auditor 全量替换。✓
- **cert:issue（Phase9/14）**：`test_cert_issuer`→`test_academic_admin`；删除对已停用账号的 `resetUser`。✓

## 五、维度结论
| 维度 | 结论 | 说明 |
|---|---|---|
| D1 需求符合 | ✅ | 与用户 4 项决策 + §1 矩阵一致（教务员保留初审、删 CERT_ISSUER、签发并入、SYS_ADMIN 全权） |
| D4 红线合规 | ✅ | 数据范围语义未改（仅改动作权归属）；越权硬校验经 IT 复核仍生效 |
| D5/D6 质量/安全 | ✅ | 无代码改动；SYS_ADMIN 全权经种子达成、非旁路 |
| D7 构建 | ✅ | clean-room `mvn verify` 75/75；build-only 未起常驻 |
| D9 数据库 | ✅ | 仅 V20 新增、幂等、全新应用通过；V1–V19 冻结 |
| D10 回归 | ✅ | Phase2~14 共 75 条在新模型下全绿 |
| D11 文档/进度 | ✅ | PROGRESS 置「待复核」未自 ✅；治理未改 |

## 六、Minor（入 backlog）
- Phase8 `testResultReadAndWriteDataScopeAreEnforced` 保留了 `clerk` 登录变量但写动作已改 auditor，`clerk` 可能成为未使用局部量（仅告警不影响编译/语义）；可在 WP-B 顺手清理。

## 七、放行
1. PROGRESS WP-A 置 **✅ 已复核**。
2. 合并 `main`（本地私有、无远程、不 push）。
3. 放行 Phase 16（WP-B 测试结果只确认，迁移 V21）。
