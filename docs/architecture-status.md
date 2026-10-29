# 架构实施记录

日期：2026-10-28。范围：用户只授权最小架构，不进入业务实现。

## 决策与计划偏差

- Ruling: 保留当前目录，不创建另一个工作树；目录尚无 Git 仓库，且用户明确所有开发在此根目录。
- Ruling: 只实施计划 Task 1 和无数据的六表结构；后续 Task 2—9 的业务保持未开始。
- Ruling: 检查入口使用 compose.check.yml，不预建 tools profile 的测试库；目前无数据库集成测试，不伪造测试覆盖。后续增加集成测试时再接独立 mysql-test。
- Ruling: 依赖锁文件必须由容器生成；不在宿主运行 npm。已在 Node 容器生成 package-lock.json，Dockerfile 使用 npm ci。
- Ruling: 当前只有声明类型、入口和配置，不添加低价值的占位单测。业务行为开发时补充先失败后通过的测试。
- 不实现完整 API client 或全局异常处理逻辑，避免架构阶段塞入未验证业务。

## 验证

初次 Docker 服务未运行，随后用户已启动；继续进行容器验证，结果在执行后更新。

## 实际投入

本轮架构整理、验证和修正尚在进行，未精确计时，不填写估算值冒充实际投入。

### 已获得的证据

- 两份 Compose 配置语法检查通过。
- Node 容器完成 npm lock 生成；frontend-check 的 ESLint、TypeScript、Next 正式构建全部退出 0。
- Java 容器 Maven verify 返回 BUILD SUCCESS；当前没有业务测试，不能称为业务测试通过。
- 独立只读审查未发现重要问题；建议的前后端构建上下文 .dockerignore 已加入。
- Java 镜像站网络失败后重试成功；应用整栈启动已验证。
- 静态检查：POM XML 可解析、锁文件直接依赖一致、迁移文件恰为六张业务表。

### 最终容器验证结果

- `docker compose up --build -d --wait --wait-timeout 300`：退出 0。
- `docker compose ps`：frontend/backend/mysql 全部 healthy。
- 容器内请求 `/`、`/history`、`/templates/demo`、`/tasks/demo`：全部 HTTP 200；仅骨架占位，不代表参数存在性校验或业务完成。
- 前端同源 `/api/system/health`：HTTP 200，`{"status":"UP"}`，证明代理连接 Java。
- MySQL SHOW TABLES：六张业务表及 flyway_schema_history；迁移 version=1、success=1；tasks 条数为 0。
- CI 配置已提供，尚未在 GitHub 托管环境实际触发。
- 容器保留运行，可访问 http://localhost:3000；停止使用 `docker compose down`，不会删除数据卷。
- 检查/构建在容器内执行；当前无业务单测或端到端业务测试，禁止将本阶段描述为完整笔试通过。

### 后续入口

从开发计划 Task 2 的匿名身份和模板种子开始；六表 DDL 已存在，不要覆盖已执行的 V1，后续种子使用 V2。任务状态和错误类型已定，业务接口仍需实现。原计划的工具 profile、Maven Wrapper、Tailwind 和业务测试框架按后续实际需要加入。
