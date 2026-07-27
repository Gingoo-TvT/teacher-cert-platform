# Phase 0 / U-004 第七轮正式独立增量复核

- 日期：2026-07-27
- 模式：incremental / testing-authenticity / release
- 上一正式材料 HEAD：`30a76c8a52d053f39c1b974287434c1390e150ee`
- 本轮最终 HEAD：`9b97741a3da921e3bce648e0c98fdb2892ec72e2`
- 第七轮实现：`b8cd171dab3acf81939a35822c83e751944e3a78`
- 结论：**CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 1 Low）**

## 1. Executive Summary

第七轮不是“测试未跑”。本轮只读核验了
`C:\Users\wenbibuhaoqwq\phase00-ci-evidence-r7`，仓库 verifier 对当前最终 HEAD
退出码为 0；证据绑定 start / after-preflight / end 三处同一 SHA，source tree 与
`HEAD^{tree}` 一致，preflight 与 formal 均 exit 0，精确 **7/7 suites、33/33
testcases、0 failure/error/skip**。runtime freshness 也已真实写成 JSON integer
`0/0/1/0`，因此第六轮 `Long→String` 失败已关闭。

第三轮的 MinIO header / 无秘密证明 Low 已关闭。但另外两项原 finding 尚未完全关闭：

1. `P00-R3-M1` 仍为 Medium：artifact 的内存快照验证完成后，句柄已经释放；随后才把
   checksum / PASS manifest 写回磁盘并 rename。窗口内改变 artifact 可令函数返回
   `verified=true`、磁盘 manifest 为 PASS，而 checksum 已不匹配。
2. `P00-R3-L1` 仍为 Low：Windows 已建立完整祖先 handle 链；POSIX 却直接对多段
   evidence root 路径调用 `os.open(..., O_NOFOLLOW)`，只能阻止末段 symlink，不能锚定
   更早的祖先路径分量。

因此当前真实 33/33 证明应用与目标身份链在本次运行成立，但不能证明 evidence gate 的
并发最终化与跨平台读取边界已满足上一轮整改要求。Phase 0 / U-004 继续
**CHANGES_REQUESTED**；本轮没有新增 finding，也不要求浏览器重跑。

### Score Dashboard

| Dimension | Score | Confidence | 说明 |
|---|---:|---|---|
| Incremental | 8.2/10 | High | 第四至第六轮功能失败链均已真实关闭 |
| Testing Authenticity | 7.0/10 | High | 33/33 真实 PASS，但 PASS bundle 仍有最终化竞态 |
| Release | 6.8/10 | High | 1 Medium / 1 Low 及 Compose/清理材料未闭环 |
| **Overall** | **7.3/10** | **High** | 可继续最小整改，不可判阶段 PASS |

### Finding Statistics

| Severity | Count | Confirmed | Suspected |
|---|---:|---:|---:|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 1 | 1 | 0 |
| Low | 1 | 1 | 0 |
| Info | 0 | 0 | 0 |
| **Total** | **2** | **2** | **0** |

## 2. Project Map

本轮只复核 Phase 0 证据门禁及与其直接相连的测试身份链：

- `scripts/phase00_ci_gate.py`：候选快照、真实门禁、证据捕获、PASS 最终化、离线 verifier。
- `scripts/test_phase00_ci_gate.py`：门禁反例与平台文件系统边界测试。
- `platform-boot/.../Phase00TargetPreflight.java`：真实 MySQL/Redis/MinIO 预检。
- `platform-boot/.../Phase00ScaffoldIT.java`：正式 Spring Context 与 runtime identity。
- `platform-boot/.../Phase00TargetGuardInitializerTest.java`：目标身份和 evidence JSON 回归。
- `scripts/phase00_ci_gate_spec.json`：精确 7 suites / 33 testcases 合同。

前端、业务流程、Flyway、权限点和产品 API 不在本轮变更范围，未重复审计。

### Coverage Matrix

| 范围 | 方法 | 结果 |
|---|---|---|
| `30a76c8..9b97741` | Git diff / 提交链 / `diff --check` | 已覆盖，PASS |
| 第三轮 1M/2L | 原失败条件逐项静态复核 | M1 open；L1 partial；L2 closed |
| 第四至第六轮失败链 | 代码、材料、r7 真实 evidence | Redis / MinIO / Long JSON 均 closed |
| r7 真实依赖门禁 | manifest、checksums、XML、identity、仓库 verifier | 7/7、33/33，PASS |
| 门禁离线测试 | Python 71/71；Java Surefire 274/274 | PASS |
| 后端打包 | Maven offline package | 9/9 modules PASS |
| Compose / 专属资源清理 | 当前证据包与材料检索 | 未归档；不计代码 finding |
| 浏览器 | 本轮材料未要求 | 不执行、非 blocker |

## 3. Incremental Change Summary

### Change Summary

本轮范围含第四至第七轮整改链。有效代码变化集中在门禁最终化、跨平台 evidence
捕获、MinIO object challenge、Redis INFO 解析和 runtime identity 数字写出；文档提交
负责保存每次候选与失败时间线。

### Previous Finding Closure Matrix

| 原 finding | 本轮状态 | 依据 |
|---|---|---|
| `P00-R3-M1` PASS 最终化失败可留下表面 PASS | **OPEN / Medium** | 普通异常路径已修，但验证后至发布前 artifact 竞态仍可产生不一致 PASS |
| `P00-R3-L1` evidence check-open TOCTOU | **PARTIALLY CLOSED / Low** | Windows 与 root 内部项已锚定；POSIX root 祖先链未锚定 |
| `P00-R3-L2` MinIO header 绕过无秘密证明 | **CLOSED** | 原始 header 已移除，schema v5 object challenge 与 artifact secret scan 生效 |

### Risk Delta

- 新增 finding：**0**
- 关闭：**1 Low**
- 仍开放：**1 Medium / 1 Low**
- 动态门禁：从第六轮 finalization FAIL 进展为当前 HEAD 完整 PASS。

### Test Coverage Delta

- 第七轮 runtime identity 使用实际 helper 写出并验证 JSON integer，已进入目标守卫 8/8。
- Python 现有 71 个测试覆盖普通异常降级、manifest-last、Windows handle 链和单快照消费。
- 仍缺两个原问题直接反例：PASS 持久化窗口 mutation/delete；POSIX root 非末段祖先替换。

### Approval Recommendation

**Request changes。** 只修本报告 1 Medium / 1 Low；下一候选不扩展产品范围。修复后对
最终 clean SHA 重新生成一次完整 evidence，并补 Compose `config --quiet` 与本轮专属资源
销毁结果，即可做证据型增量复核。Phase PASS 不等于 merge、部署或稳定发布 GO。

## 4. Top Risks

1. **Medium — 已验证快照与最终磁盘 PASS bundle 可能不是同一字节集合。**
2. **Low — POSIX evidence root 的非末段祖先仍可在打开前被替换。**
3. **Gate gap — 当前 r7 证据包没有 Compose config 与隔离资源销毁记录。**

## 5. Detailed Findings

### Finding: PASS 内存验证后仍可发布已改变的 artifact

- ID: P00-R3-M1
- Severity: Medium
- Confidence: High
- Category: Release / Testing Authenticity / Provenance
- Status: Confirmed, previous finding remains open
- Affected area: `scripts/phase00_ci_gate.py` evidence finalization
- Evidence:
  - `verify_prepared_pass` 在 `4388` 捕获一次 artifact snapshot，并在 `4401-4405`
    验证内存虚拟 bundle。
  - `persist_preverified_pass` 在 `4426-4428` 只写 checksum 与 manifest，没有重新捕获或
    锁定磁盘 artifact。
  - `run_gate` 在 `4648` 才 rename ready 目录；两步之间没有保持 artifact capability。
  - 纯离线协调反例在验证后、持久化前把 `artifact.txt` 从 `before` 改为 `after`，稳定得到：
    `verified=true`、`error=null`、`diskStatus=PASS`，随后 verifier 报
    `SHA256SUMS mismatch for artifact.txt`。
- Problem: 被验证的是旧内存快照，最终发布的是可在该窗口变化的磁盘目录；代码注释“不得再
  mutate”没有机制保证。
- Why it matters: gate 可成功退出并留下结构上为 PASS、内容却无法通过自身 checksum 的证据包；
  `if: always()` 上传或只看 manifest 的复核会被误导。
- Realistic failure scenario: 同用户并行进程、索引/同步工具或误并发清理在快照完成后改变或删除
  artifact，门禁仍写出 PASS 并原子发布。
- Minimal fix: 由一个发布流程从已验证 snapshot bytes 构造新的私有 bundle，持久化后对该确切
  磁盘 bundle 再捕获/校验，并在任何异常时保证 final 路径不存在可接受 PASS；发布流程内不要把
  可变 artifact 路径交还给其它步骤。
- Better long-term fix: 不建议另建框架；把 capture、persist、disk reverify、rename 收敛成一个
  小型原子发布单元即可。
- Regression test suggestion: 分别在 capture 后/manifest 前、manifest 后/rename 前
  mutate 与 delete artifact；断言命令非成功，final/ready 均不存在可通过 verifier 的 PASS。
- Estimated effort: 2–4 hours

### Finding: POSIX evidence root 的祖先路径未逐段锚定

- ID: P00-R3-L1
- Severity: Low
- Confidence: High
- Category: Security / Supply Chain / Testing Authenticity
- Status: Confirmed, previous finding partially closed
- Affected area: `scripts/phase00_ci_gate.py` POSIX evidence snapshot capture
- Evidence:
  - `_capture_evidence_posix` 在 `1875-1880` 直接执行
    `os.open(root, O_DIRECTORY | O_NOFOLLOW)`。
  - `O_NOFOLLOW` 只保护 `root` 的最后一个路径分量；更早的目录分量没有逐段 `openat` 和持有 fd。
  - Windows 路径已在 `2162-2253` 建立完整祖先 handle 链，POSIX 没有等价实现或测试。
- Problem: root 内部的子目录/文件已经 handle-anchored，但打开 root 本身之前仍依赖可变的多段
  路径解析。
- Why it matters: verifier 明确接受外部交回的 evidence directory；读取边界应在第一次读取前
  成立，不能只依赖末段 no-follow。
- Realistic failure scenario: Linux 上 evidence root 的某个非末段祖先在 `os.open(root)` 前被
  替换成 symlink 或另一个目录，verifier 可能锚定并读取调用者原意之外的目录。
- Minimal fix: 从 `/` 或可信父 fd 开始逐段
  `openat(O_DIRECTORY | O_NOFOLLOW)`，持有祖先 fd 至 root capture 完成并核对 identity。
- Better long-term fix: 与 Windows 保持同一“完整祖先 capability chain”原则即可，无需扩大设计。
- Regression test suggestion: Linux-only 协调测试在 root open 前替换非末段祖先；断言替代目标
  reader 计数为 0 且抛 `GateError`。
- Estimated effort: 2–4 hours

## 6. Testing Authenticity Analysis

### Confidence Assessment

动态证据真实性为 High：

- `manifest.status=PASS`；
- candidate / expected / start / after-preflight / end 均为
  `9b97741a3da921e3bce648e0c98fdb2892ec72e2`；
- source tree 为 `5c05081ffe72ac8df1c9c2b7adc1f0f17e8e9a10`，与当前 `HEAD^{tree}` 一致；
- preflight / formal exit 均为 0；
- 14/14 checksum 项由仓库 verifier 复核；
- 7/7 suites、33/33 testcases、0 failure/error/skip；
- preflight/runtime identity 一致，freshness 为 JSON integer `0/0/1/0`；
- 独立 `verify` 退出码 0。

### Valuable Tests

- `scripts/test_phase00_ci_gate.py`：71/71。
- Java Surefire：32 suites / 274 tests。
- `Phase00TargetGuardInitializerTest`：8/8。
- Maven offline package：9/9 modules，Checkstyle 0。
- 本轮纯离线竞态反例：稳定复现不一致 PASS。

### Missing Tests

- M1 两个发布窗口的 mutate/delete 反例。
- L1 的 Linux 非末段祖先替换反例。
- 最终候选的 Compose config 与专属资源清理归档。

## 7. Release Concerns

- 当前 HEAD 的真实 33/33 可以保留为有效进展，但代码 finding 使 Phase 0 不能 PASS。
- r7 evidence 目录未包含当前 SHA 的 Compose config 输出与专属容器/网络/卷销毁证明。
- 前端和浏览器没有本轮变更；无需为了本次整改重复浏览器验证。
- Phase 0 后仍有用户要求的最终全量审计；本报告不授权 merge、push、部署或切流。

## 8. Recommended Fix Order

1. 修 `P00-R3-M1`，补两个精确发布窗口反例。
2. 同轮补 POSIX 完整祖先 fd 链与一个 Linux 反例。
3. 重跑 Python 目标门禁、Java 目标测试、后端离线 package。
4. 由用户/获授权环境对新最终 clean SHA 运行一次完整 7/33 gate，并只补 Compose config 与专属
   资源清理记录；不要求浏览器。
5. 做一次只针对两项 finding 与新 evidence 的独立增量复核。

## 9. Quick Wins

- 现有测试已具备 patch/coordinator 模式，M1 的两个 mutation/delete 用例可直接加入同一测试类。
- POSIX 修复可复用 Windows 已明确的“祖先链先建立、reader 后执行”合同，不需要新增公共抽象。
- Compose 与清理只需一份短日志，避免把整个 Docker 环境再次写入报告。

## Safety Boundary

本次 Codex 只做源码、Git、现有本地 evidence 的只读核验，以及会自然退出的离线测试/构建。
没有执行 Docker、MySQL、Redis、MinIO、网络、HTTP 服务、浏览器、扫描、fuzz、故障注入、
凭据/权限或其它可能属于 cyber 的动作；没有 stage、commit、merge、push、deploy。
