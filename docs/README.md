# 小影科技笔试开发文档

项目根目录固定为 `./`。后续源码、配置、测试、素材和交付材料均位于该目录内，不把路径末尾的 `..` 解释为上一级 `./`。

已完成架构、基础身份/模板、素材上传 API 和任务后台 API。见[基础阶段记录](./foundation-status.md)、[Task 3 记录](./task3-status.md)、[Task 4 记录](./task4-status.md)、[Task 5 记录](./task5-status.md)、[Task 6 记录](./task6-status.md)、[Task 7 记录](./task7-status.md)、[Task 8 记录](./task8-status.md)、[账号身份更新](./account-identity-status.md)、[注册与绑定设计](./account-contact-design.md)和[账号隔离修复记录](./account-isolation-status.md)。任务跟踪、历史和结果界面已接入，最终交付验收待完成。

## 阅读顺序

1. [设计与验收规格](./2026-10-28-design.md)：需求边界、技术选型、架构、表结构、接口、状态机、页面、异常与验收标准。
2. [分步开发计划](./2026-10-28-development-plan.md)：实施文件、接口依赖、检查步骤、工时、交付顺序和进度清单。
3. [验收清单](./acceptance.md)：当前验证证据和最终交付仍需复核的内容。

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
