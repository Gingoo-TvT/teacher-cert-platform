# Phase 3 · 基本信息管理（M03）

> 优先级 P0 · 依赖：Phase 1、Phase 2 · 任务：T-030~T-038 · plan §5.3 / §6.1 / §6.2 / §15.2-A / §15.4 / §15.8 / §15.9
> 目标：采集与维护学生基础身份信息，落地**证件号码校验、出生日期一致性、姓名格式、敏感脱敏、两级审核、字段锁定、学生账号开通**。**AT-03 首验；AT-01 部分（文本化采集）。**

## 1. 范围
学生 CRUD + 批量录入、本人确认/补充、四类证件校验、出生日期一致、姓名校验、生源地三级、脱敏、两级审核（状态机 A）、关键字段锁定、导入即开通学生账号。

## 2. 数据库（V7__student.sql）
`student`：student_no, name, gender, id_card_type, id_card_no, birth_date(文本), identity_type, source_province/city/county, source_full, college_id, grade, class_name, status, locked, 审计列。
- **文本化**：`id_card_no`/`student_no`/`birth_date` 均 VARCHAR。
- 索引：`student(student_no)` 唯一、`student(id_card_no)`、`student(college_id)`。

## 3. 校验器（plan §15.4，精确实现）
| 校验器 | 规则 | 失败提示 | 验收 |
|---|---|---|---|
| `IdCardValidator` | 居民身份证 `^\d{17}[\dXx]$`；港澳台居住证 `^\d{17}[\dXx]$`（末位校验码可X）；港澳通行证 `^[A-Za-z]\d{8}$`；台胞证 `^\d{8}$`。`validate.idcard.checksum=true` 时身份证加 MOD11-2 | "证件类型与号码不匹配" | V-05/AT-03 |
| `BirthDateValidator` | 身份证/港澳台居住证：取号码第 7–14 位 YYYYMMDD，与 `birth_date` 归一(zero-pad)比对 | "出生日期与证件号码不一致" | V-06/AT-03 |
| `NameValidator` | `strict`=`^[一-龥·]{2,}$`；`loose`=禁空格/数字/A-Za-z/除"·"外ASCII标点 | "姓名格式异常" | V-04 |

## 4. 接口清单
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/student` | `student:view`(数据范围) | 列表（脱敏） |
| GET | `/api/student/{id}` | `student:view` | 详情 |
| POST/PUT | `/api/student` | `student:edit` | 教务员增改 |
| POST | `/api/student/confirm` | `student:confirm` | 学生本人确认/补充 |
| POST | `/api/student/{id}/submit` | `student:edit`/`confirm` | 提交进入待初审 |
| POST | `/api/student/{id}/first-review` | `info:firstReview` | 初审 通过/退回/不通过 |
| POST | `/api/student/{id}/second-review` | `info:secondReview` | 复审 通过/退回/不通过 |
| GET | `/api/student/{id}/id-card?plain=1` | `export:sensitive` | 明文证件号（鉴权+留痕） |

> 批量导入走 Phase 10 标准导入中心；本阶段提供单条/简单批量录入即可。

## 5. 状态机（§15.2-A）
草稿→(提交)待初审→(初审通过)待复审→(复审通过)复审通过/合格；退回/不通过分支见 §15.2-A。复审退回回到 `review.return.target`（默认待初审）。**不合格为终止态**，教务处可特批重置（留痕）。

## 6. 脱敏与锁定
- `@Desensitize`：证件号默认前6后4（`110101********1234`）；明文需 `export:sensitive` 且写 `audit_log`。
- 锁定：证书生成后或（按需）复审通过后，关键字段（姓名/证件类型/证件号/出生日期/身份类型）`locked=1` 禁改；学生端禁编辑。

## 7. 学生账号开通（§15.8）
导入/创建学生且 `student.autoCreateAccount=true` → 建 `sys_user`(username=学号, 初始密码=证件号后6位, 角色 STUDENT, 绑定 student_id, must_change_pwd=1)。

## 8. 前端
- 教务员：学生列表（数据范围+脱敏）、编辑、提交、初审/复审（退回填原因）、状态标签。
- 学生端：本人信息确认/补充、生源地 `RegionCascader`、查看审核意见与状态；锁定字段禁编辑。

## 9. 验收清单
- [ ] 四类证件分别用合法/非法号码 → 合法放行、非法拦截并提示"证件类型与号码不匹配"（AT-03）。
- [ ] 身份证号第 7–14 位与出生日期不一致 → 拦截提示（AT-03）。
- [ ] 港澳通行证/台胞证**不做**出生日期一致校验（按 §15.4）。
- [ ] 姓名含数字/字母/空格/非法符号 → 拦截；含"·"的少数民族姓名在对应模式下放行。
- [ ] 列表与详情默认脱敏（前6后4）；无 `export:sensitive` 取明文被拒并无明文泄漏。
- [ ] 证件号/学号/出生日期落库为文本，回显与录入完全一致（无前导零丢失、无日期序列）（AT-01 部分）。
- [ ] 两级审核状态流转与留痕正确；退回必填原因；保留历史版本。
- [ ] 复审通过/证书生成后关键字段锁定，学生与教务员均禁改（特批除外，留痕）。
- [ ] 开通的学生账号可用学号登录，首次强制改密。

## 10. 测试用例
- T-ID-1：`居民身份证` + 17位 → 拒；+ 末位 `X` → 放行。
- T-ID-2：`港澳居民来往内地通行证` + `H12345678` → 放行；+ `123456789` → 拒。
- T-ID-3：`台胞证` + 8 位数字 → 放行；+ 9 位 → 拒。
- T-BIRTH-1（反例）：身份证 `...19900628...` 但出生日期填 `1990/7/1` → 拒。
- T-NAME-1（反例）：`张三3`/`Zhang San`/`张 三` → 拒；`阿依古丽·买买提` loose 模式放行。
- T-TEXT-1：导入 `00123` 学号、`0440106...` 证件号 → 落库与导出均保留前导零。
- T-LOCK-1：证书生成后改证件号 → 拒。
- T-SM-1：待初审→初审不通过 → 状态=不合格(终止)，再次提交被拒（需特批重置）。

## 11. DoD
四类证件 + 出生日期 + 姓名校验、脱敏、两级审核、锁定、账号开通全部可用；AT-03 与 AT-01（采集侧）自测通过。

## 12. 风险
- 出生日期归一要兼容 `1990/6/28` 与 `1990/06/28` 两种文本；比较用 zero-pad，**存储/导出仍保留原文本**。
- 港澳台居住证号段（810000/820000/830000 开头）出生日期位与身份证一致（第 7–14 位），实现时统一处理。
