# DEVLOG.md — 开发日志

> 规范见 `AGENTS.md` §9。**倒序追加**（最新在最上）。每完成一个任务或每个工作会话至少一条。
> 条目模板（复制使用）：
>
> ```
> ## [YYYY-MM-DD] T-xxx 标题
> - 做了什么：
> - 关键决策与理由：
> - 问题与解决：
> - 与规格的偏差/疑问：（如有，须同步改 plan/docs 或记入待确认事项）
> - 测试：（测试名/结果）
> - 下一步：
> ```

---

## [2026-06-13] Phase 0 · Maven 骨架构建通过（T-001/T-002）
- 做了什么：父 POM + 8 子模块；`platform-common` 核心(Result/ResultCode/PageResult/BizException/BaseEntity/@AuditLog/@DataScope)；`platform-boot`(PlatformApplication/OpenApiConfig/CorsConfig/GlobalExceptionHandler/HealthController/application.yml)。
- 验证：`mvn -B -ntp -DskipTests package` → **BUILD SUCCESS**（9 模块），产出可运行 jar `platform-boot/target/teacher-cert-platform.jar`。
- 关键决策：父 POM 强制 UTF-8；锁定 Spring Boot 3.2.11 / MyBatis-Plus 3.5.7 / Knife4j 4.5.0 / FastExcel 1.1.0 / MinIO 8.5.12 / jjwt 0.12.6。
- 已知告警（无害）：platform-common 仅引 `mybatis-plus-annotation`，javac 提示找不到 `org.apache.ibatis.type.JdbcType`（注解默认值引用），警告非错误；待 system/business 引入 mybatis-plus-starter 后消失。控制台中文告警乱码=GBK 控制台渲染，源码 UTF-8 编译正常。
- 状态：T-001 ✅ / T-002 ✅ / T-003 🟦（boot 起步完成，MyBatis-Plus·Jackson·数据源待续）。
- 下一步：T-003 续(MyBatis-Plus 配置/Jackson Long→String) + T-004 Flyway V1 + T-005 docker-compose；冒烟验证 /api/health 与 /doc.html。

## [2026-06-13] 环境搭建 · 工具链 + git 初始化
- 做了什么：安装 Maven 3.9.9 到 `C:\Users\wenbibuhaoqwq\tools`，配置用户级 JAVA_HOME(Temurin JDK17)/MAVEN_HOME/PATH；`git init` 本地私有仓库(main 分支)，提交规划基线 24 文件(6948682)；新增 `.gitignore`。
- 关键决策：Maven 官方未上架 winget → 用官方二进制 + 用户级环境变量，免管理员；git 仅本地、**无远程**，确保不开源。
- 问题与解决：①winget 无 `Apache.Maven` → 改官方二进制；②安装脚本含 `Remove-Item`+`C:\Program Files` 触发保护拦截 → 删去 `Remove-Item`。
- 注意：JDK 平台默认编码 **GBK**（中文 Windows）→ 父 POM 必须强制 UTF-8（`project.build.sourceEncoding` + 编译器编码），否则中文注释/资源乱码。
- 版本：JDK 17.0.19 / Maven 3.9.9 / git 2.54.0 / Docker 已装 / Node v24.15。
- 下一步：Phase 0 T-001 Maven 多模块骨架 → 验证 `mvn package`。

## [2026-06-13] 规划阶段 · 文档体系建立（非编码）
- 做了什么：完成需求分析 → 产出 `plan.md`（含 §15 二次详查增补）、`tasks.md`（114 原子任务）、`docs/`（README + 待确认事项确认单 + phase-00~14 详细设计与验收）、`AGENTS.md`/`PROGRESS.md`/`DEVLOG.md` 流程文件。
- 关键决策与理由：
  - 证书顺序号作用域默认 `SCHOOL_YEAR_SEGMENT`（与需求 9.1 示例一致），并列入确认单待学校书面确认。
  - ORM 选 MyBatis-Plus、Excel 选 EasyExcel/FastExcel（保文本）、大视频用 MinIO 分片、迁移用 Flyway。
  - 补齐 10 项执行缺口（plan §15）：状态机不合格终止态、权限矩阵、补充表、校验精化、视频结算、导出子表列、导入两步+回滚、账号/年度、M03/M04 两级审核、序列作用域。
- 问题与解决：docx 为二进制，已解压 `word/document.xml` 提取全文分析。
- 与规格的偏差/疑问：见 `docs/待确认事项确认单.md`（20 项）。
- 测试：N/A（规划阶段）。
- 下一步：执行 Phase 0（T-001~T-010）搭建工程骨架；开工前先读 `AGENTS.md`。
