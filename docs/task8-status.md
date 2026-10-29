# Task 8：边界测试与容器 CI

日期：2026-10-28。范围为核心行为的边界测试、CI 配置核对和隔离容器验收；最终源码交付整理仍属于 Task 9。

## 完成内容

- 新增真实 MySQL 集成测试：预置结果素材缺失时生成阶段失败、停止后续步骤并清理临时文件；成功和失败任务不能重新领取或退回处理中；旧访客到期后不能访问记录，新访客不会继承旧记录，数据库中的原记录仍属于旧访客；已发布结果文件丢失时返回 404，任务状态保持不变。
- 针对测试中发现的缺口，将预置结果素材不可读取时的错误改为 `RESULT_SOURCE_UNAVAILABLE`，保存清楚的原因；失败仍终止后续步骤，文件写入等其他异常仍按原有错误处理。
- GitHub Actions 在 push 和 pull request 运行前端检查、Java 检查、Compose 健康及基础／素材／任务接口检查。失败分别收集应用和检查环境日志，两个清理步骤各自使用 `always()`，关闭服务时不删除数据卷。
- 为真实查询中断验收提供仅用于隔离环境的 [Compose 覆盖配置](../checks/task8.compose.yml)和内部测试代理。测试代理不暴露主机端口，日志仅记录任务查询方法和路径，不记录 Cookie 或请求内容；默认一键启动和 CI 不使用此代理。

## 本轮验证证据

- Java 容器 `docker compose -f compose.check.yml run --build --rm backend-check`：Spotless、打包、10 项单元测试及 15 项 MySQL 集成测试通过。新增缺失素材测试先发现原错误码过于笼统，修正后通过；原有三阶段失败、队列拒绝、用户隔离测试也通过。
- 前端容器 `docker compose -f compose.check.yml run --build --rm frontend-check`：格式、lint、类型、4 项 Node 测试、30 项组件／跟踪器测试及生产构建通过。
- 独立无代理 Compose 项目从空数据卷启动，前端同源健康检查返回 200；`foundation-check`、`asset-check`、`task-check` 均通过，覆盖分类和封面、会话、图片上传、异步任务、三阶段失败及结果图片读取。此验证会生成测试记录，只在隔离环境执行。
- 浏览器真实连接中断：单独暂停测试代理，Java 后台继续执行；页面保留“处理中”并显示查询超时与“恢复查询”。代理恢复后点击恢复，页面显示后台实际成功状态。只有一个活动任务的测试中，任务成功后代理记录的该任务详情查询在连续 5 秒内保持 146 → 146 次，未继续轮询。
- 使用容器内 YAML 解析器检查流水线语法、push/PR 触发及独立清理步骤。GitHub 托管流水线尚未运行；本地等价检查通过不等同托管 CI 已通过。

## 可复验真实中断

1. 在项目目录用独立项目名启动下面的测试环境，浏览器打开 `http://localhost:3109`。演示配置会将生成阶段延长至 90 秒：

   ```sh
   FRONTEND_PORT=3109 NEXT_PUBLIC_TASK_POLL_INTERVAL_MS=500 NEXT_PUBLIC_TASK_QUERY_TIMEOUT_MS=1500 TASK_ASSET_CHECK_DELAY_MS=1000 TASK_GENERATE_DELAY_MS=90000 TASK_FINALIZE_DELAY_MS=1000 TASK_MAX_DELAY_MS=120000 docker compose -p xiaoying-task8-validation -f compose.yml -f checks/task8.compose.yml up --build -d --wait
   ```

2. 使用示例图片创建任务；处理中时暂停**该隔离项目**的测试代理，观察保留状态和查询异常：

   ```sh
   docker compose -p xiaoying-task8-validation -f compose.yml -f checks/task8.compose.yml pause task-fault-proxy
   ```

3. 恢复代理并在页面点击“恢复查询”：

   ```sh
   docker compose -p xiaoying-task8-validation -f compose.yml -f checks/task8.compose.yml unpause task-fault-proxy
   ```

4. 检查 `docker compose -p xiaoying-task8-validation -f compose.yml -f checks/task8.compose.yml logs task-fault-proxy`；终态之后等待多个轮询周期，同一任务的 GET 数量不应增长。验收完 `docker compose -p xiaoying-task8-validation -f compose.yml -f checks/task8.compose.yml down`，不带 `-v`，保留数据卷。

## 待交付项

- GitHub 托管 CI 尚未实际触发；目录尚未初始化 Git，因此没有里程碑 commit。源码目录或压缩包也可交付，是否创建仓库由最终交付方式决定。
- 仍需按 Task 9 完成全流程视觉和无障碍验收、实际投入记录、README 中真实 AI 工作流案例及交付材料整理。
- 当时的匿名身份限制已于 2026-10-29 部分解决：有效访客可注册账号并重新登录；未注册访客过期后仍无法找回；重启续跑、幂等、自动重试不在题目要求范围内。
