# 验收清单

此清单记录截至 Task 8 的历史验证证据；最新一轮源码与容器复核见 [2026-10-29 验收复核](./acceptance-review-2026-10-29.md)。已通过不等于 GitHub 托管流水线或最终交付均已验证。

| 验收项目 | 当前证据 | 最终验收待办 |
| --- | --- | --- |
| 模板及素材 | 分类、六张封面、详情和 1/2/3 张上传在独立容器中验证 | 再核对小屏及键盘操作 |
| 上传与参数 | 前后端校验、真实 multipart、失败替换、访客隔离有自动测试 | 验证完整页面的异常提示 |
| 后台执行 | 单线程多任务、三阶段顺序、指定失败、队列拒绝及结果素材缺失有测试 | 按 README 操作再验证成功与失败演示 |
| 查询与恢复 | 多任务、超时、查询异常区分、刷新恢复及终态停止有自动测试；隔离代理模拟连接中断并验证恢复 | 最终版本复核实际网络变化及多个不同任务 |
| 历史和结果 | 浏览器验证历史、详情、成功图片预览及 Escape 关闭 | 复核分页、结果文件缺失提示及小屏布局 |
| 身份隔离 | 不同访客的素材、任务和结果隔离；过期身份不可读取，新访客不继承旧记录 | 按身份 TODO 如实说明无法找回旧访客资料 |
| 容器与 CI | 全新隔离 Compose 项目健康，基础／素材／任务接口检查通过；本地前后端完整检查通过 | 如使用 GitHub 仓库，实际检查托管 push/PR 结果 |
| 交付材料 | README、配置示例、接口和各阶段记录已存在 | 补实际投入、真实 AI 工作流案例、清理源码包并核对交付方式 |

## 标准容器检查

在项目目录执行，测试环境会创建临时演示访客、素材和任务，不应针对生产数据执行：

```sh
docker compose -f compose.check.yml run --build --rm frontend-check
docker compose -f compose.check.yml run --build --rm backend-check
docker compose up --build -d --wait
docker compose --profile tools run --rm foundation-check
docker compose --profile tools run --rm asset-check
docker compose --profile tools run --rm task-check
```

默认服务可能包含已有资料；要做从零验证时，使用另一个 Compose 项目名和空闲端口，不删除现有数据卷。真实连接中断的隔离复验步骤见 [Task 8 记录](./task8-status.md)。关闭环境时使用 `docker compose down`，不要附带 `-v`。
