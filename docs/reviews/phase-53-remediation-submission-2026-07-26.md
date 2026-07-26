# Phase 53 退回整改候选提交（2026-07-26）

## 1. 状态与冻结范围

- 正式依据：`docs/reviews/phase-53-review.md` 的结论仍为 **CHANGES REQUESTED**；`docs/reviews/ws-03-remediation-rereview-2026-07-23.md` 另记录真实视频资源与旧 demo 元数据/旧同名对象不一致的补充 High。
- 当前状态：**整改候选完成，待用户动态证据与独立增量复核**。本文不是独立 PASS，不放行 Phase 44、merge、push、部署、切流或项目发布。
- 分支：`codex/phase53-demo-reconcile`
- 治理基线：`4996811`
- 代码候选：`b9abc6c`
- 建议复核增量：`4996811..b9abc6c`
- 继承资源：真实 MP4 自 `ca400f1` 起已在基线，未包含于上述六文件代码增量；复核时另冻结 `sample-video.mp4` blob `31205d6cc42d9c850713e806b744446326cc807a`。
- 材料冻结要求：独立复核者还必须记录并冻结本文所在治理提交及本文件 blob，且自行重跑安全的一次性构建/测试；不得采信未提交工作树中的材料。
- 代码范围：
  - `platform-boot/src/main/java/cn/edu/gpnu/platform/boot/config/DemoDataInitializer.java`
  - `platform-boot/src/main/resources/db/demo/demo-data.sql`
  - `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/service/impl/VideoReviewServiceImpl.java`
  - `platform-business/src/main/java/cn/edu/gpnu/platform/business/video/support/VideoMediaAcceptancePolicy.java`
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/boot/config/DemoDataInitializerTest.java`
  - `platform-boot/src/test/java/cn/edu/gpnu/platform/business/video/support/VideoMediaAcceptancePolicyTest.java`

## 2. 原退回项与候选关闭链

### 2.1 可播放视频与可信媒体事实

仓库样例已经是含真实 H.264 帧的受控 MP4，不再使用 528B 空轨占位。初始化器在任何对象写入前按内置清单有界读取并核对原始 SHA-256；上传或复用后完整读回对象，再由服务端媒体探测器核对受信 tree 指纹、时长、帧数、编码、探测器版本和策略哈希，最后复用生产视频验收策略。任一事实不一致均终止 demo 初始化，不写实体 SQL。

| 资源 | 字节数 | 原始 SHA-256 / MD5 | 可信媒体事实 |
|---|---:|---|---|
| `sample-video.mp4` | 1,605,702 | SHA-256 `0a15c2e382438f3fbbd1e9b5302514ad35da07b33b44f7eb61fbb5c8b31d611d` | tree `ee0f34bb9c2cdaa567cb1195b957f28d56c6279f972b9b3afe1c4b235ab21f86`；900 秒；900 帧；H264；`JCODEC_PROCESS_V4`；策略 `450225d274f6959ed2fe8fb299e0cbb91f508604a2aece234524e8246c82ddc9` |
| `sample-material.pdf` | 659 | SHA-256 `53aece50aefaf8236338f0ddada8469e54d1422d26da0a975a2f81c873ba9268`；MD5 `04ef7b13998c0fecd52bbf07af84bd05` | `application/pdf` |
| `sample-image.png` | 99 | SHA-256 `c589a04293ea064c50a46e8fec832e6920b28ea3cf3718aed18b929f9fb631ee`；MD5 `2fba59bf7077925f03e1d9431b792bed` | `image/png` |

### 2.2 旧对象与数据库元数据的一致性切换

- 四类样例使用原始 SHA-256 派生的内容版本 key：
  - `teaching-video/demo/<video-sha256>.mp4`
  - `process-material/demo/<pdf-sha256>.pdf`
  - `process-material/demo/<png-sha256>.png`
  - `exemption-material/demo/<pdf-sha256>.pdf`
- 版本 key 不存在时才上传；检查时已存在则必须同时满足精确字节数、Content-Type 和完整读回 SHA-256。预存的相同版本 key 如被污染，初始化失败关闭且不覆盖。
- 旧固定 key（包括旧 528B 视频对象）刻意保留，本候选不执行删除。所有新版本对象通过探测与最终复核后，单个数据库事务才把 `file_object`、`process_material`、`exemption_material`、`video_review`、`video_upload_session.object_key` 及可信媒体元数据统一切到新 key。
- 如果实体 SQL 失败，数据库事务回滚，旧数据库引用仍指向被保留的旧对象；最多遗留尚无数据库引用的新版本对象，不会出现“新对象 + 旧元数据”的半切换。
- 内容版本写入与幂等 SQL 不再依赖 MySQL session `GET_LOCK`，避免连接池容量和会话锁释放边界。
- 已知并发边界：`stat(MISSING) → put` 之间没有对象存储 CAS；若外部写入者恰在该极窄窗口写入异物，当前 put 仍可能覆盖。demo 初始化必须使用专用隔离 bucket 并与外部写入串行，动态门禁同时覆盖预存污染拒绝。

### 2.3 SQL 与启动失败关闭

- demo SQL 的 bucket、四类 object key、字节数、Content-Type、MD5、视频 tree 指纹、900 秒、H264、策略/探测版本全部由已验证 manifest 渲染。
- 22 个必需 token 缺一即拒绝执行，渲染后仍有 token 残留同样拒绝。
- SQL 已移除旧 528B、905/890/910 秒、旧固定 key 和全部假摘要；既有行通过 `ON DUPLICATE KEY UPDATE` 收敛，而不是只覆盖首次插入。
- `video_upload_session` 同步收敛 `upload_mode`、`object_key`、`uploaded_bytes`、`duration_seconds` 等 V28 字段。
- demo 默认关闭且仅允许 DEV/TEST；显式启用后，资源、对象、探测或 SQL 任一步失败都会终止启动，只输出安全的本地异常类别。

## 3. 自动化与一次性门禁

| 门禁 | 结果 |
|---|---|
| `DemoDataInitializerTest` | 21/21 PASS |
| `VideoMediaAcceptancePolicyTest` | 10/10 PASS |
| Phase 53 聚焦测试 | 31/31 PASS |
| 全量离线 Maven test | `platform-file` 18/18 + `platform-boot` 177/177 = **195/195 PASS** |
| 后端离线 package | 9/9 modules BUILD SUCCESS |
| fat JAR 内容 | 初始化器、demo SQL、MP4/PDF/PNG 均已入包 |
| `git diff --check` | PASS |

正反例覆盖：四对象缺失上传/一致跳过、旧 528B 固定 key 保留并升级、检查时已预存的新版本 key 同长度错内容/错类型污染拒绝且不覆盖、stat/get/put/写后复核故障、探测失败/异常、六类可信媒体事实偏差、大小/时长业务策略、SQL token/旧值/各表版本 key、SQL 失败不触碰旧固定对象。

## 4. 本次会话只读交叉复查（非正式阶段报告）

- 生产代码第二轮只读复查：**PASS（0 Critical / 0 High / 0 Medium / 0 Low）**。
- SQL/渲染复查：可本地补强的非视频 key/旧摘要断言已关闭；当前剩余 **0 Critical / 0 High / 0 Medium / 1 Low**。
- 唯一 Low：单元测试为隔离 runner 故 mock 了真实 `seedEntities`，没有在本机执行真实 MySQL SQL、事务回滚和旧库数据收敛。该项是运行期证据缺口，不是已确认的静态代码错误，必须由下一节动态门禁关闭。
- 上述精确计数来自本次会话的只读交叉复查，尚未形成仓库内正式独立报告；只证明代码候选与离线证据，不替代 `docs/REVIEW-GATE.md` 要求的正式独立增量复核。

## 5. 用户专属动态门禁（可能涉及 cyber，Codex 不执行）

> **以下动作可能涉及 Docker、真实 MinIO/MySQL、服务启动、登录鉴权、故障注入或对象清理，只能由用户在专用隔离环境决定并亲自执行。不要针对共享开发库、共享 bucket 或现有服务执行。**

1. 在一次性隔离 schema 与隔离 bucket 中构造升级前快照：旧固定视频 key 为 528B，数据库仍为旧固定 key、旧摘要和旧时长。
2. 用候选 `b9abc6c` 的构建产物启用 DEV/TEST demo，保存完整启动日志、对象清单和数据库查询结果。
3. 核对四个内容版本对象的 key、字节数、Content-Type、原始 SHA-256；视频另核对 tree 指纹、900 秒、900 帧、H264、策略哈希和探测版本。
4. 另起一次隔离运行，在初始化前向某个版本 key 放置同长度错内容或错误 Content-Type，确认候选拒绝启动、不覆盖该对象且不执行实体 SQL；随后丢弃该专用隔离环境，不要在共享 bucket 制造污染。
5. 核对 `file_object`、`process_material`、`exemption_material`、`video_review`、`video_upload_session` 在同一次成功初始化后全部引用版本 key；尤其确认 `video_upload_session.object_key`、`uploaded_bytes` 和 `duration_seconds`。
6. 核对旧固定对象仍存在但已无数据库引用；本阶段不要删除这些旧对象。
7. 在隔离环境注入一次实体 SQL 失败，确认数据库引用完整回滚到旧值，旧固定对象未被覆盖；仅允许出现无引用的新版本对象。该故障注入不得用于共享环境。
8. 清除故障后成功初始化并再重启一次，确认四对象全部一致跳过、各表行数稳定、无重复数据。
9. 使用授权评审教师在浏览器核对首帧可见、约 900 秒时长、播放/拖动、动态水印和限时鉴权地址；再用无权或跨范围账号确认不能取得播放地址。
10. 归档证据后，仅清理本次隔离 schema、bucket、容器/卷和临时文件；清理属于破坏性动作，必须先逐项确认目标，禁止触碰共享资源。

## 6. 独立增量复核请求

独立复核者应冻结 `4996811..b9abc6c`，重核：

1. 原 Phase 53 可播放视频 Major；
2. WS-3 补充 High 的 528B/旧摘要/旧对象升级不一致；
3. 内容版本 key、预存污染失败关闭、`stat→put` 并发边界和 SQL 原子引用切换；
4. 生产媒体验收策略复用是否无语义回归；
5. 31/31、195/195、9 模块 package 与用户动态证据的候选同源性；
6. 旧对象保留、隔离环境清理和未覆盖项是否如实披露。

只有新的正式独立报告判定 PASS，才能把 Phase 53 置为“✅ 已复核”并进入 Phase 44。

## 7. 安全与发布边界

- Codex 本轮未运行 Docker、网络、真实 MinIO/MySQL、常驻服务、HTTP 登录、浏览器播放、权限变更、故障注入、对象删除、漏洞扫描、攻击性探测、凭据尝试、恶意载荷、fuzz、压力或其它可能属于 cyber 的动作。
- 未改 DDL/Flyway、权限点、业务状态集合、外部 API、前端或生产部署配置。
- 未 merge、push、部署或切流；`main` 仍为 `e4f8228`。
- Phase 53 与全项目继续 **CHANGES_REQUESTED**，直到用户动态门禁和正式独立增量复核均闭环。
