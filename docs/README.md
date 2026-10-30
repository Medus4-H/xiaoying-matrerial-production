# 小影科技笔试开发文档

项目源码、配置、测试、素材和交付材料位于仓库根目录。功能完成程度、运行方法和已知限制以[项目 README](../README.md)及最新验收记录为准；各阶段记录保留当时的事实，不能单独代表当前状态。

## 阅读顺序

1. [项目 README](../README.md)：启动、配置、接口演示、交付边界和 AI 使用说明。
2. [Task 9 验收记录](./task9-status.md)：当前浏览器验收和待验证事项；[本地流水线复验](./local-ci-2026-10-30.md)记录最新容器验证。
3. [接口文档](./api.md)和[素材来源](./asset-sources.md)：接口约定和资源来源。
4. [分步开发计划](./2026-10-28-development-plan.md)与[设计规格](./2026-10-28-design.md)：实施过程和设计依据；[验收清单](./acceptance.md)为历史检查。

## 已确认的约束

- 最多三天；暂按每天八小时、共 24 个有效工时安排，实际投入据实登记。
- Next.js App Router、React、TypeScript；Java、Spring Boot、MyBatis、MySQL。
- Java 掌管业务、后台执行和持久化；前端不模拟服务端任务状态。
- 详情页采用参考图的左侧制作、右侧历史双栏布局。
- 不做类似 vergeX-admin 的独立管理后台；只做开发配置下的轻量模拟控制。
- 采用访客及可登录账号、单实例后端、有界线程池、前端轮询、本地文件卷。
- 项目构建、测试和运行使用容器隔离环境；不要求评估者在宿主机运行项目脚本。
- 设计文档保留历史方案；当前实现和验证以对应阶段记录及根 README 为准。

## 使用规则

开发前同时阅读两份文档。正文题目约束优先于截图中题外功能，用户后续明确调整优先于本计划。实施过程中及时勾选任务，并记录实际投入、偏差和验证证据；不得把计划完成时间当成实际投入。

当前入口与配置以根 README 和基础阶段记录为准，架构记录是上一阶段快照。

素材上传和会话过期提示见 [Task 3 记录](./task3-status.md)；任务受理、后台执行、历史及结果 API 见 [Task 4 记录](./task4-status.md)。前端模板与访客交互见 [Task 5 记录](./task5-status.md)，上传与提交见 [Task 6 记录](./task6-status.md)，任务跟踪及历史见 [Task 7 记录](./task7-status.md)，边界与 CI 验证见 [Task 8 记录](./task8-status.md)。

- 日志与错误国际化：见 [改造计划](./logging-plan.md)、[错误码盘点](./error-catalog-inventory.md)、[阶段 A/B 记录](./logging-phase-ab-status.md)、[阶段 C 记录](./logging-phase-c-status.md)和[阶段 D/E 记录](./logging-phase-de-status.md)。

- 最终验收与投入：见 [Task 9 记录](./task9-status.md)和[投入记录](./effort.md)。
