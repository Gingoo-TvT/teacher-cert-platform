# WS-3b · MinIO 预签名·内外双端点配置 + 前端直传/直下接线 — 提示词（codex）

你是执行 **WS-3 配置/前端**的 codex（需求① MinIO 独立文件服务器）。对应 `docs/audit-remediation-plan.md` §3 WS-3（C3/C4）。**opus 并行做 C1/C2 后端**（`03-ws03a-opus-minio-presign-backend.md`）——先和 opus 对齐 init/complete 契约与 endpoint 命名；**C3 双端点先打通**（MinIO CORS/公网可达是最常见坑）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§3 WS-3**；`git remote -v` 空 → `git switch -c feature/ws03-minio-config-frontend`。
- 动前端 → 收尾必跑 `cd frontend && npm run type-check && npm run build`。
- **⚠️ 与 WS-4 交叉文件**：`frontend/src/views/video/components/UploadPanel.vue`、`.../UploadVideoDrawer.vue`、`frontend/src/views/material/components/MaterialUploadDrawer.vue` ——**本 WS 先动这三个**，WS-4 D3 最后再碰（已在计划里约定）。

## 任务
### C3 独立文件服务器"内外双端点"配置（先做，打通为要）
- 应用访问 MinIO 用**内部** `minio.endpoint`；预签名 URL 必须嵌入**浏览器可达的公网/反代** endpoint → 新增 `minio.public-endpoint`，**签名用 public host 生成**（配合 opus 的 `S3Presigner`）。
- 补：`platform-boot/src/main/resources/application-{dev,prod}.yml`、`.env.example`、`docker-compose.yml`、`frontend/nginx.conf`（如经反代）、`MinioProperties`。
- **MinIO bucket CORS**：允许前端源直传/直下（PUT/GET + 必要头；dev 放行 `http://localhost:5173`）。
- **⚠️ mixed-content**：站点一旦上 HTTPS（WS-5 T1 TLS），`public-endpoint` 必须同为 **https**（http 预签名会被浏览器拦截）——与 WS-5 落地顺序协调，**建议预签名走同一反代/证书**最省事。

### C4 前端直传/直下接线
- `frontend/src` 上传组件改走 **init → 预签名 PUT**（本 WS 主要工作）；下载/预览/播放**多数后端已返回预签名 GET**（材料预览/免考预览/视频播放走 Phase 37a `presignedGet` 模式）→ 前端**对齐消费即可**，剩余=证书文件下载。
- **并入 Phase 49 诚实推迟的两项**：分片**并发上传 4–6** + `crypto.subtle` **Worker 哈希**（指纹/秒传/断点续传语义沿用：init 前指纹查重、进度=已完成分片集）。
- 涉及前端文件：`frontend/src/api/video.ts`、`api/material.ts`、`api/certificate.ts` 等 + 上述三个上传组件 + 相关预览/播放组件（`material/components/MaterialPreviewModal.vue` 等）。

## 验收（活体）
- 走一遍：学生**分片直传**材料/视频、评审看/播视频、下载**已存单对象**——字节**不经应用**（抓包/日志证明打到 MinIO **public endpoint**）。**生成式导出（Excel/ZIP 打包）合法保持过应用**，不列入"字节不经应用"（与 opus C1/C2 契约一致）。
- 预签名过期失效；`http://localhost:5173` 直传/直下 CORS 通过；（HTTPS 后）public-endpoint 为 https 且不被 mixed-content 拦。
- 前端 `npm run type-check` 干净 + `npm run build` 成功。

## 收尾（硬门槛）
1. 若需跑 IT 验证契约：释放 :8080（精确 PID）+ verify 门禁串行 + `mvn -B -ntp clean verify` 绿。
2. 前端 `type-check` + `build` 绿。
3. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-3 配置/前端条目。
4. **单 commit**（`feature/ws03-minio-config-frontend`）→ **STOP** 交主控复核。**禁止 merge / push**。
