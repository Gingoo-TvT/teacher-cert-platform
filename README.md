# 师范生教育教学能力考核与教师职业能力证书管理平台

后端 **Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL + Flyway + MinIO**；前端 **Vue 3 + Vite + TypeScript + Naive UI**。

> 🚀 **接手 / 继续开发先读 [HANDOFF.md](HANDOFF.md)**（现状·环境·一键命令·从哪开始）。
> 📌 开工必读 **[AGENTS.md](AGENTS.md)**；总体方案 [plan.md](plan.md)（含 §15 增补）；任务清单 [tasks.md](tasks.md)；阶段设计与验收 [docs/](docs/)；进度 [PROGRESS.md](PROGRESS.md)；日志 [DEVLOG.md](DEVLOG.md)；待学校确认项 [docs/待确认事项确认单.md](docs/待确认事项确认单.md)。

## 环境要求
JDK 17、Maven 3.9+、Node ≥18、Docker Desktop。

## 快速开始（本地开发）
```bash
# 1) 启动依赖（MySQL/Redis/MinIO）
docker compose -f docker-compose.dev.yml up -d

# 2) 后端（Flyway 自动建表 + 种子参数）
mvn -DskipTests package
SPRING_PROFILES_ACTIVE=dev java -jar platform-boot/target/teacher-cert-platform.jar
#   健康检查  http://localhost:8080/api/health
#   接口文档  http://localhost:8080/doc.html

# 3) 前端
cd frontend && npm install && npm run dev
#   http://localhost:5173
```

## 模块结构（详见 plan.md §2.4）
| 模块 | 职责 |
|---|---|
| platform-common | 统一响应/异常/基础实体/注解/上下文 |
| platform-security | 认证授权、JWT、RBAC、数据权限切面 |
| platform-system | 字典/区划/学科库/组织/参数/审计/通知 |
| platform-business | 学生/培养/材料/免考/视频/测试/证书 |
| platform-exchange | 教育部标准导入导出与预校验 |
| platform-statistics | 统计报表 |
| platform-file | MinIO 文件服务、分片上传 |
| platform-boot | 启动、全局配置、Swagger、跨域、全局异常 |

## 默认配置（开发）
- 后端不再默认激活 profile；本地启动必须显式设置 `SPRING_PROFILES_ACTIVE=dev`。PowerShell 可先执行 `$env:SPRING_PROFILES_ACTIVE='dev'`。
- MySQL：`root` / `root123`，库 `teacher_cert`
- MinIO：`minioadmin` / `minioadmin123`，bucket `teacher-cert`
- 可配置参数见 `sys_param` 表与 `docs/README.md` §6

## 生产部署（Docker Compose）
本仓库提供生产部署物：后端多阶段 `Dockerfile`、前端 `frontend/Dockerfile` + nginx 反代、生产 `docker-compose.yml`。生产 compose 与 `docker-compose.dev.yml` 分离，开发依赖契约不变。

```bash
# 1) 准备环境变量
cp .env.example .env
# 修改 JWT_SECRET、ADMIN_INITIAL_PASSWORD_HASH、STAFF_INITIAL_PASSWORD、数据库/MinIO 密码和入口端口；
# 必须设置 DB_USERNAME/DB_PASSWORD（非 root 应用账号，见 deploy/mysql-init/01-app-user.sh）与
# REDIS_PASSWORD（Redis 生产鉴权），不可留空/沿用示例值。
# APP_IMAGE_TAG 必须改为 sha-<本次候选完整 Git SHA>，禁止 latest 或可移动标签。
# 同时把 TLS_SERVER_NAME 改为正式域名，并让 TLS_CERTIFICATE_DIR 指向证书目录；
# 目录内必须包含学校签发或 Let's Encrypt 提供的 fullchain.pem 与 privkey.pem。部署前在宿主核对专用
# 证书读取组的名称、数字 GID 和成员，并把 TLS_CERTIFICATE_GID 占位符替换为该 GID；它与容器 nginx
# 的 101:101 无关。私钥不得提交仓库或设为全局可读。
# MINIO_PUBLIC_ENDPOINT 必须是浏览器可达的单一 HTTPS origin；JWT access/refresh TTL 默认 900/604800 秒。

# 2) 构建并启动生产服务（后台）
# 首次部署可使用下列命令；从 root runtime、V32 或 Phase 44 之前的版本升级时不得直接滚动替换。
# 尤其 V32→V33 会切换身份证件号持久化协议，必须先执行 docs/phase-14-非功能部署验收.md
# “WS-8 / V33 身份证件号存储协议停机切换”合同；禁止把通用 up 当作升级步骤。
docker compose up -d --build

# 3) 查看健康
docker compose ps
# 前端 https://<TLS_SERVER_NAME>:<FRONTEND_HTTPS_PORT>（HTTP 业务请求会 301 到同一 HTTPS 端口）
# 经唯一业务入口检查后端健康：https://<TLS_SERVER_NAME>:<FRONTEND_HTTPS_PORT>/api/health
```

后端启动时由 Flyway 自动迁移并写入种子数据（字典、角色权限、参数等）。
前端容器只读挂载证书目录，证书文件名固定为 `fullchain.pem` / `privkey.pem`。HTTP 仅保留容器内部
`/healthz` 健康检查，其余路径统一跳转 HTTPS；HTTPS 响应携带一年期 HSTS。浏览器直连的
`MINIO_PUBLIC_ENDPOINT` 也必须使用 `https`，并由独立文件服务器或其外层网关提供有效证书，避免
HTTPS 页面上的预签名上传、下载和播放被 mixed content 拦截。

生产 Compose 的公网发布面仅为前端 `FRONTEND_PORT` / `FRONTEND_HTTPS_PORT`。backend 不发布宿主
`8080`；MinIO API 与 console 仅绑定宿主 `127.0.0.1`。`MINIO_PUBLIC_ENDPOINT` 对应的同机 TLS 网关可把
受信任 HTTPS 流量转发到回环 `MINIO_API_PORT`，console 只允许本机或既有受控运维通道访问，不得直接对外发布。

### WS-7 非 root 镜像部署与升级

后端与前端镜像默认分别以 `10001:10001`、`101:101` 运行。宿主公网入口仍为 80/443（或显式配置的
`FRONTEND_PORT` / `FRONTEND_HTTPS_PORT`），只把它们映射到前端容器内部非特权端口 8080/8443；backend
继续不发布宿主 8080。

`TLS_CERTIFICATE_DIR` 内必须是实体证书文件，或其符号链接目标也完整位于同一挂载目录内，不能只挂
Let's Encrypt 的 `live/<domain>` 而漏掉 `archive` 目标。宿主应使用专用证书组：目录建议 `0750`，
`fullchain.pem` / `privkey.pem` 建议 `0640 root:<TLS_CERTIFICATE_GID 对应组>`；Compose 通过补充组把最小
读取权限授给前端 UID 101。部署前必须确认该宿主 GID 对应的组名和成员均符合预期；不得照抄容器主组
`101:101`，也不得复用含无关成员的宿主组。不得把私钥改成 world-readable 来规避权限问题。启动或 reload 前可执行自然退出的
只读预检：

```bash
docker compose run --rm --no-deps --entrypoint sh frontend -c \
  'test -r /etc/nginx/certs/fullchain.pem && test -r /etc/nginx/certs/privkey.pem'
```

旧 root 镜像创建或写入过的 `video-probe-temp` 命名卷会遮蔽新镜像层内的目录所有权。升级必须在授权维护窗
停止全部旧 backend 与媒体 worker，保留原卷并一次性调整既有目录和工件；禁止删卷，也禁止在旧进程仍运行时改
属主：

```bash
docker compose stop backend
# 确认所有旧媒体 worker 已退出后执行：
docker compose run --rm --no-deps --user 0 --entrypoint chown backend \
  -R 10001:10001 /var/lib/teacher-cert/video-probe
docker compose run --rm --no-deps --entrypoint test backend \
  -w /var/lib/teacher-cert/video-probe
```

上述检查通过后才启动新镜像，并按既有健康检查与媒体探测流程复验。新部署的镜像层已预建同一目录和权限；
这一维护步骤只用于保留并迁移既有命名卷。

生产 HTML 响应携带 CSP：脚本、字体、表单、Worker 与业务内容均以同源为基线；现有 PDF 预览保留同源
`object/frame`，验证码允许 `data:`，本地视频时长探测允许 `blob:`，Naive UI 动态样式只在 `style-src`
保留内联样式兼容。`MINIO_PUBLIC_ENDPOINT` 仅作为精确 `connect-src` 用于浏览器预签名直传，不进入脚本、
frame、图片或媒体来源，也不允许通配外源。

登录/刷新响应 JSON 只返回短期 access token；refresh token 由后端写入 host-only、HttpOnly、
`SameSite=Strict`、Path=`/api/auth/refresh` 的 Cookie，prod profile 同时带 `Secure`。前端 access token 仅驻
Pinia 内存，不写 `localStorage`；页面重载通过 Cookie 刷新恢复会话，logout 与成功改密会清 Cookie 和本地
会话，并通知其它标签页丢弃迟到的会话响应。旧版本遗留的本地 token 键会在启动时清理。

### WS-8 身份证件号码保护与密钥恢复

生产必须同时配置彼此独立、且不得复用 `JWT_SECRET` 的 `IDCARD_ENCRYPTION_KEY` 与
`IDCARD_HMAC_PEPPER`：前者是 Base64 编码的随机 32 字节 AES 密钥，后者至少为 32 个 UTF-8 字节。
缺失、示例或格式不合法的配置会在生产启动及数据库迁移前失败关闭。两项值上线后必须保持稳定，并在数据库
备份之外使用受控密钥系统备份；WS-8 不包含在线密钥轮换。丢失或更换 AES 密钥会使历史密文无法解密，
更换 HMAC pepper 会使既有证件查重键失配。

已有 V32 数据库升级到 V33 时，禁止新旧 backend/worker 混部，也禁止直接运行上面的通用 Compose 启动命令。
必须先冻结 Student/Certificate/Exchange 写入、排空事务、停止全部旧节点并生成可校验的 V33 前恢复点，再由一台
隔离流量的新 binary 执行 Flyway V33 与 `AFTER_MIGRATE` 回填。完整顺序、只读验收及迁移开始后的旧 binary
回滚禁令见 [Phase 14 的 WS-8/V33 停机切换合同](docs/phase-14-非功能部署验收.md#31-ws-8--v33-身份证件号存储协议停机切换禁止滚动混部)。

学生与证书表、导入预览/错误明细/前后快照中的身份证件号码均以应用层密文持久化，确定性 HMAC 只用于等值
查询和唯一约束。普通列表、详情及导出仍默认脱敏；仅既有授权且留审计的 `export:sensitive` /
`exchange:export:sensitive` 路径可在响应边界解密明文。逻辑备份不携带上述两项密钥，恢复环境必须使用原 AES
密钥与原 HMAC pepper，并按 `docs/备份与恢复手册.md` 完成密文、脱敏及重复证件号拒绝复验。

### TLS 证书续期或替换

以下步骤适用于已运行的生产 Compose，证书续期仍由学校 CA、Let's Encrypt 客户端或既有证书系统完成；私钥与
上一组可回退证书保存在版本控制之外的受保护目录。命令从仓库根目录执行；Compose 自动读取 `.env` 只用于自身插值，
不会把值导出给当前 shell，因此先显式设置与 `.env` 一致的三项非秘密运维变量，不要 `source` 整份密钥配置：

```bash
export TLS_CERTIFICATE_DIR=./deploy/certs
export TLS_SERVER_NAME=cert.example.edu.cn
export FRONTEND_HTTPS_PORT=443
```

续期完成后按顺序执行：

1. 在覆盖正式文件前检查新证书的域名、有效期和私钥。下面两个公钥 SHA-256 输出必须一致；`checkend`
   返回非 0 表示不足 30 天，不得作为新证书上线。

   ```bash
   openssl x509 -in "$TLS_CERTIFICATE_DIR/fullchain.pem.next" -noout -checkhost "$TLS_SERVER_NAME" -checkend 2592000 -serial -dates -fingerprint -sha256
   openssl pkey -in "$TLS_CERTIFICATE_DIR/privkey.pem.next" -check -noout
   openssl x509 -in "$TLS_CERTIFICATE_DIR/fullchain.pem.next" -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256
   openssl pkey -in "$TLS_CERTIFICATE_DIR/privkey.pem.next" -pubout -outform DER | openssl dgst -sha256
   ```

2. 保留上一对证书后，将新文件成对替换为固定名称 `fullchain.pem` / `privkey.pem`。先校验配置，成功后才 reload；
   reload 不可用时才只重建 frontend。

   ```bash
   docker compose exec frontend nginx -t
   docker compose exec frontend nginx -s reload
   # fallback: docker compose up -d --no-deps --force-recreate frontend
   ```

3. 从授权的外部位置读取线上证书，核对 `serial`、SHA-256 fingerprint 和 `notAfter` 与新证书一致，再检查
   HTTPS 健康端点。

   ```bash
   openssl s_client -connect "${TLS_SERVER_NAME}:${FRONTEND_HTTPS_PORT}" -servername "$TLS_SERVER_NAME" </dev/null 2>/dev/null | openssl x509 -noout -serial -enddate -fingerprint -sha256
   curl -fsS "https://${TLS_SERVER_NAME}:${FRONTEND_HTTPS_PORT}/api/health"
   ```

4. 至少每日或每周对正式 `fullchain.pem` 执行 `openssl x509 -noout -checkend 2592000`，把非 0 结果接入现有
   告警并明确负责人，形成 30 天到期提醒。`nginx -t` 失败时不得 reload；reload 后外部核验失败时，恢复上一对
   证书，再依次执行 `nginx -t`、reload 和外部复验。

### CI 镜像身份与 SBOM

GitHub Actions 只在 runner 合同、后端和前端质量门禁全部成功后，才用 `sha-${GITHUB_SHA}` 构建后端与前端
最终镜像，并核验两幅镜像的默认 UID 非 0。工作流随后配置生成 `backend.spdx.json`、
`frontend.spdx.json`、`image-identities.txt` 与 `SHA256SUMS`，作为同一次 CI run 的可下载 artifact。

SBOM 只是组件清单，不等于漏洞扫描结果。本阶段不配置漏洞扫描、镜像签名、provenance、registry push 或生产
发布；在 hosted CI 实际运行并归档前，只能表述为“工作流已配置生成”，不能声称远端产物已经存在。

### Phase 44 字典缓存协议发布

生产 Compose 已透传 `DICT_CACHE_WRITER_LEASE` / `DICT_CACHE_WRITER_RENEW_INTERVAL`，默认 `2m` / `20s`；续租周期必须为正且不超过租约三分之一，所有后端实例必须保持一致。

从旧版本升级时必须停机切换：停止字典管理写请求并排空在途事务 → 停止全部旧后端节点 → 等待旧 pending 的 60 秒 TTL 到期并核对无 `P:*` 残留（必要时仅在全停机状态按具体 typeCode 定向清理字典 payload/version，禁止清空 Redis）→ 一次性启动全部同版本新 binary → 逐实例核对镜像版本、启动日志和 `/api/health` → 只读核对大小写别名与 schema v2 重建 → 恢复字典写流量。禁止新旧 binary 混部；新协议上线后不得回滚旧 binary 承接字典读写，只能保持写入口冻结并前向修复。完整操作边界见 [Phase 14 部署验收](docs/phase-14-非功能部署验收.md)。

### 视频定稿可靠性发布（V32）

V32 为视频定稿增加 `video_finalization_object_candidate` 持久候选台账：每个定稿 generation 使用独立对象键，FAILED/失权对象由生产环境每分钟的 reconciliation 持久重试；已确认删除的 `CLEANED` 墓碑也会周期复查，防止迟到的旧 generation 写入遗留对象。启动回填/对账投递到单线程后台执行器并防重入，不阻塞 `ApplicationReady` 与 readiness。

发布 V32 必须按顺序执行：停止视频定稿写流量 → 停止全部旧后端节点与媒体 worker → 由新版本执行 Flyway V32 → 启动全部新二进制实例 → 健康检查与迁移核验通过后恢复写流量。禁止 V31 或更旧的稳定对象键实现与 V32 混部；V32 落库后禁止回滚到旧协议二进制，只能前向修复。

媒体 worker 额外通过 `VIDEO_PROBE_PARENT_CHECK_INTERVAL`（默认 `PT1S`）核验父 JVM 的 PID 与启动时刻；父 JVM 异常退出后 worker 会自行退出。生产 Compose 已透传该变量。

**仅 dev/测试（`db/testseed`，生产不加载）**的初始测试账号：`test_academic_admin`、`test_college_clerk`、`test_college_auditor`、`test_review_teacher`、`test_cert_issuer`、`test_student`，初始密码 `ChangeMe123!`，首次登录需修改。**这些是本地/联调便利账号，切勿用于生产。**

**生产凭据（WS-2 凭据硬化，均为必配、缺失即 fail-fast 拒绝启动）**：
- `ADMIN_INITIAL_PASSWORD_HASH`：`admin` 超管的 **bcrypt 口令哈希**（不落明文）。仅当 admin 仍使用 V8 公开种子口令时执行一次性覆盖、撤销旧 token 并置 `must_change_pwd=1`；首登改密后重启不会再覆写。prod 未配、哈希非法或仍对应公开口令均拒绝启动（`AdminAccountInitializer`）。
- `STAFF_INITIAL_PASSWORD`：管理员新建/重置 STAFF 账号的初始口令。生产必须显式注入（dev 默认 `ChangeMe123!`）；未配则拒绝启动（`SecurityAdminServiceImpl`）。该部署密钥不用于学生账号。
- 学生导入**默认不再自动开户**（`student.autoCreateAccount=false`），且不再由证件号派生口令。显式开启但保持 `student.defaultPwd=random` 时，仅由学生业务流程创建**停用账号**；校级管理员重置后生成单账号随机临时口令并只在受控响应中展示一次，再由学生首次改密。只有显式配置满足强度要求的受控口令才会导入即启用。通用用户管理仅创建 `STAFF`（不得绑定 `studentId`），既有学生账号的类型、用户名、学院和学生绑定不可在系统用户页改绑；用户管理写操作仅限校级 scope，`COLLEGE` scope 仅可按范围读取列表，同院/跨院重置均返回 403。
- WS-2 后 JWT 同时绑定毫秒级签发时间 `iatMs`、当前口令哈希的不可逆 `credentialVersion` 与 Redis 持久 `sessionGeneration`；登出以 Lua 原子推进会话代次，改密/重置后旧 access/refresh token 立即失效，缺少或不匹配任一新 claim 的存量 token 均会被拒绝。发布 WS-2 时须一次性替换或重启**全部**后端实例并要求用户重新登录，不能在滚动窗口保留会签发旧格式 token 的旧实例。
- 交互式生成 bcrypt 哈希：`htpasswd -nBC 12 admin | sed 's/^admin://' | tr -d '\n'`（不会把明文口令放进历史或进程参数）。写入 Compose `.env` 时必须用**单引号**包住完整 `$2...` 哈希，避免 `$` 被插值；详见 `.env.example`。

关键参数位于 `sys_param` 表，可在系统管理页热更新；证书编号 `cert.*`、视频 `video.*`、文件大小 `file.*` 等参数修改后按既有服务实时读取。M14 外部接口仅预留 SPI 与开关，`.env.example` 中 `PLATFORM_INTEGRATION_*_ENABLED=false` 为默认值，关闭时不影响一期功能。

## 非功能与验收
AT-01~AT-14 首验与 Phase14 复验矩阵见 [docs/AT验收复验矩阵.md](docs/AT验收复验矩阵.md)。主流程端到端由 `Phase14E2EIT` 纳入 `mvn verify`，覆盖导入、确认、培养、材料、免考、视频复评、测试结果、证书生成/签发、标准导出文本化与归档。

兼容性记录：导出 Excel 通过 POI 机检断言 A-Z 26 列、H 列为“身份证件号码”、全列文本格式 `@`，证件号/前导零学号/证书编号/有效期按字符串读回；Phase14 文档归档 Excel 与 WPS 双端手动核对要求，浏览器回归目标为 Chrome、Edge、Firefox 最新稳定版。

## 进度
Phase 1~53、Phase 0 / U-004、F01–F07、WS-5、WS-6 与 WS-7 均已有各自独立 PASS 记录。WS-7 最终
fingerprint `d5ea9863...9e24` 的 Hosted run `31507732334` 已闭环双 SPDX、镜像身份、校验和与 artifact；唯一
Low（临时 evidence remote）已后续清除。该阶段 PASS 只放行 WS-8，不代表 merge、部署、切流或项目发布 GO。
WS-8 R2 Hosted 六-suite已取得 46/46 scoped PASS，但最新阶段结论仍为
`CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 1 Low）`；R3 已最小修复过期 WS-7 当前态合同，等待新候选
整体绿灯 Hosted workflow 与独立阶段 PASS。WS-9 不启动，项目继续 NO-GO。
当前唯一状态入口见
[docs/CURRENT-EXECUTION-PLAN.md](docs/CURRENT-EXECUTION-PLAN.md) 与 [PROGRESS.md](PROGRESS.md)。
