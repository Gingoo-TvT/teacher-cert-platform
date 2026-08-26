# WS-6 multipart 大小写第二轮整改提交（2026-08-11）

> 后续正式复核：fingerprint `f5d63378...8607` 已取得
> `INDEPENDENT_INCREMENTAL_PASS（0 Critical / 0 High / 0 Medium / 0 Low）`。正式报告：
> `C:\Users\wenbibuhaoqwq\Documents\脚本\ws06-remediation-r2-independent-evidence-20260811\independent-review-report.md`
>（SHA-256 `EC6FF4F9...AC053`）。以下正文保留开发侧提交快照。
>
> 状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_REREVIEW_PENDING`
>
> 本文是开发侧最小整改提交，不是独立复核 PASS，也不授权 merge、push、deploy、cutover 或项目发布。

## 1. 正式退回基线

- 候选 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- 候选 fingerprint：`cf3776b6e9b3bbd9ba2ca7633fce45b000ca30b72ebe7c66d13886a60c2b0fb0`
- 正式报告：`C:\Users\wenbibuhaoqwq\Documents\脚本\ws06-remediation-independent-evidence-20260811\independent-review-report.md`
- 报告 SHA-256：`4E7F90BA7B9390BE71ADAFB3384201509A3E38348BFDCC022895927F5C8310F5`
- 结论：`CHANGES_REQUESTED（0 High / 1 Medium / 0 Low）`
- 已关闭：401 新 Authorization、tests/config 静态门禁两个 Low。
- 待关闭：multipart 参数值大小写 Medium，正式状态为 `PARTIAL / OPEN`。

## 2. 本轮唯一整改

1. 保留 part header 原始文本；只对 header 名和 Content-Disposition 参数键做大小写不敏感定位。
2. 捕获后的参数值逐字精确比较：`name === "file"`、`filename === "students.xlsx"`；MIME 仍按媒体类型语义比较。
3. 在现有导入 E2E 内用 Chromium 原生 FormData 实际发送 `name="FILE"`，捕获浏览器生成的动态 boundary、
   header 与原始 body，交给正例同一个解析器并要求明确拒绝；正常 UI 上传继续校验 fixture 原字节。

未新增依赖、parser、测试框架或生产代码；未运行 Docker、真实后端、数据库或攻击性检查。

## 3. 最小增量门禁

| 门禁 | 结果 |
|---|---:|
| 扩面 ESLint | PASS |
| tests/config `tsc --noEmit -p tsconfig.tests.json` | PASS |
| Playwright Chromium（含真实 `FILE` 反例） | 5/5 PASS |
| 测试端口 `127.0.0.1:18106` | 0 listener |
| `git diff --check` | PASS |

正式报告已关闭的两个 Low 不重跑、不重开；`cf3776b6...b0fb0` 的其余全绿门禁事实保持为历史候选证据。

## 4. 候选身份与复核边界

第二轮候选身份冻结在仓库外：

`C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws6-remediation-r2-candidate-2026-08-11.json`

增量复核只需验证新 manifest 前后一致、剩余 multipart Medium 的精确参数值和 `FILE` 反例，以及上述最小门禁。
无需 Docker、真实后端/数据库、全栈回归、全站 WCAG、全仓格式化或生产功能重构。正式 PASS 前 WS-6 不关闭，
也不进入 WS-7。
