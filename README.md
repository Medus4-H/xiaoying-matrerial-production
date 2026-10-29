# 小影科技笔试 · 素材制作网站

当前完成架构、访客、账号注册登录与身份弹窗、模板浏览和筛选、上传与任务提交页面，以及后台三阶段处理、个人历史和结果接口。数据与任务状态由 Java/MyBatis/MySQL 管理。页面已支持任务自动跟踪、个人历史和结果预览；最终完整交付验收仍待完成。

## 一键启动

安装 Docker 与 Compose 插件后，在项目目录执行：

```sh
docker compose up --build -d --wait
```

默认访问 http://localhost:3000。无需宿主 Node/Java/MySQL、宿主绝对路径或付费资源。首次需要联网下载镜像与依赖。Flyway 自动执行 V1 表结构、V2 六模板种子、V3 封面和 V4 结果示例配置、V5 账号和会话迁移、V6 联系方式可选迁移及 V7 访客恢复表；用户由访问时创建。示例封面为 Pexels 授权照片，随 Java 镜像打包。

## 统一环境配置

需要更换配置时将 `.env.example` 复制为 `.env`，再使用同一启动命令。不复制也可使用本地演示默认值。

| 变量 | 作用 |
| --- | --- |
| COMPOSE_PROJECT_NAME | 项目、网络和数据卷隔离，不依赖源码目录名称 |
| FRONTEND_PORT | 主机公开端口，默认 3000 |
| PUBLIC_ORIGIN | 允许的站点来源；未设置时按 FRONTEND_PORT 生成本地来源，远程/HTTPS 必须填写实际来源，无末尾斜杠；多来源逗号分隔 |
| MYSQL_IMAGE/DATABASE/USER/PASSWORD/ROOT_PASSWORD | MySQL 版本和初始化库/凭据 |
| AUTH_DEMO_CODE / AUTH_PASSWORD_ITERATIONS | 演示验证码（默认 123456）/ 密码 PBKDF2 迭代次数（默认 210000）；验证码仅 demo 生效 |
| SESSION_COOKIE_NAME/TTL/SECURE | Cookie 名、有效期（7d/2h）、HTTPS 标志；HTTPS 部署设置 Secure=true |
| APP_PROFILE | demo/prod；prod 禁止在任务请求中覆盖模拟参数 |
| NEXT_PUBLIC_SHOW_SIMULATION_CONTROLS | 是否显示任务失败模拟选项，默认 false；修改后须重新构建前端，仅应配合 demo 模式 |
| NEXT_PUBLIC_TASK_POLL_INTERVAL_MS / NEXT_PUBLIC_TASK_QUERY_TIMEOUT_MS | 任务轮询间隔和单次查询超时，默认 2000 / 10000 毫秒；修改后须重新构建前端 |
| NEXT_PUBLIC_TASK_HISTORY_PAGE_SIZE | 历史每页数量，范围 1–50，默认 20；修改后须重新构建前端 |
| BACKEND_INTERNAL_URL | 仅容器内部代理地址，变更后需要重新构建前端 |
| ASSET_MAX_BYTES / ASSET_MAX_DIMENSION / ASSET_MAX_PIXELS | 图片大小、最长边及总像素上限 |
| ASSET_MULTIPART_FILE_LIMIT / ASSET_MULTIPART_REQUEST_LIMIT | Spring 的单文件和整次请求限制，须不低于相应的素材限制 |
| TASK_ASSET_CHECK_DELAY_MS / TASK_GENERATE_DELAY_MS / TASK_FINALIZE_DELAY_MS | 三阶段默认耗时，单位毫秒 |
| TASK_MAX_DELAY_MS | 每个阶段允许的最大耗时，单位毫秒 |
| TASK_CONCURRENCY / TASK_QUEUE_CAPACITY | 后台线程数和等待队列容量，默认 2 和 20 |
| TASK_SHUTDOWN_GRACE_SECONDS / TASK_CONTAINER_STOP_GRACE | 执行器关闭等待时间和容器停止宽限期，默认 30 秒和 40 秒 |

Java 环境配置统一位于 application.yml；前端名称和协议路径位于 src/config/app.ts。固定协议、加密算法、枚举和容器内部端口不机械变为配置。模板内容由种子/数据库管理，不写死在前端。图片卷使用命名卷，STORAGE_ROOT 是容器路径，不是宿主路径。

公开封面目前固定打包在 Java classpath；如后续支持更换目录，需要同步放开 Compose 的 TEMPLATE_MEDIA_ROOT。MySQL 已存在卷不会因修改初始化凭据自动改变用户，应做数据库迁移，或使用另一个项目名建立独立演示环境，不能删除旧卷冒充迁移。

浏览器 API/图片地址都是相对 /api，不返回容器主机名或本机磁盘路径。初次默认密码仅用于本地演示，部署到公网前必须更换。根 .env 不提交。

## 结构与版本

frontend：Next.js 16.4.3、React 19.2.4、TypeScript 5.9.3、Query 5.90.16；组件测试使用 Vitest 3.2.4、Testing Library、MSW 和 jsdom；package-lock.json 固定依赖，npm ci 构建。
backend：Java 21、Boot 3.5.16、MyBatis Starter 3.0.5、Flyway；MySQL 8.4.7；Maven 3.9.9 容器构建。镜像尚未固定 digest。

- frontend/src：页面、访客入口、模板组件、API 客户端。
- backend/src：按 identity/template/asset/task/storage/common/config 分包。
- backend/src/main/resources/demo/covers：真实照片封面（JPEG，1000×1250），不是上传素材；上传阶段仍只接受 JPEG/PNG。
- checks/foundation.mjs、checks/assets.mjs、checks/tasks.mjs：容器接口合同检查。
- docs：[设计](docs/2026-10-28-design.md)、[计划](docs/2026-10-28-development-plan.md)、[基础阶段记录](docs/foundation-status.md)、[Task 4 记录](docs/task4-status.md)、[Task 5 记录](docs/task5-status.md)、[Task 6 记录](docs/task6-status.md)、[Task 7 记录](docs/task7-status.md)、[Task 8 记录](docs/task8-status.md)、[账号身份更新](docs/account-identity-status.md)、[注册与绑定设计](docs/account-contact-design.md)、[账号隔离修复记录](docs/account-isolation-status.md)、[验收清单](docs/acceptance.md)、[接口](docs/api.md)。

## 身份

访客 Cookie 为 HttpOnly、SameSite=Lax，随机令牌仅以 SHA-256 哈希入库。账号、素材、任务归属于同一个稳定的用户 ID；账号会话存入独立表，同一账号可拥有多个独立会话。密码使用带随机盐的 PBKDF2-HMAC-SHA256 散列，不以明文保存。写请求对携带 Origin 的请求校验配置白名单；无 Origin 的服务端工具请求可使用。

**演示步骤：**打开页面，点击右上角访客身份 → 注册账号，选择“邮箱注册”或“手机号注册”，填写该联系方式、验证码和至少 8 位密码。点击获取验证码，页面会显示演示码（默认 `123456`）。注册只需一种联系方式。默认创建独立账号，不继承当前访客的素材和历史；仅当这些资料属于本人时，可在注册页主动勾选导入，沿用访客的用户 ID。

注册后可点击账号入口，在“账号信息”中绑定尚未填写的另一种联系方式。已登录后绑定时只需输入新联系方式和对应演示验证码，无需再次输入密码；若该联系方式已被其他账号使用则拒绝，不合并账号。绑定成功后两种联系方式均可用原密码登录。已绑定的联系方式暂不支持修改或解绑。手机号目前仅检查数字和可选的加号，不自动识别国家地区或归一化不同写法；注册、绑定及登录应使用一致写法。

**验证码仅供演示，不会发送邮件或短信，也不能证明邮箱或手机号归属。**
身份接口返回的账号名称、邮箱和手机号均已脱敏：11 至 15 位手机号显示前 3 位和后 4 位，较短号码只显示后 2 位；邮箱显示首字符和域名，只有一个字符的邮箱用户名全部隐藏。注册、绑定及登录仍需填写完整联系方式。脱敏仅减少页面与身份接口的明文展示，数据库仍保存完整联系方式以支持登录，不等同于数据库加密；密码使用散列保存。
登录可以使用邮箱或手机号加密码。点击身份入口选择“切换 / 登录账号”，即可在不同账号间切换；退出后可重新登录。会话过期或更换浏览器后，登录原账号可恢复原有记录。已注册账号之间的素材、任务、历史和结果彼此隔离。登录已有账号不会合并访客资料。当前浏览器中有效的访客会话会在登录或独立注册时暂存，退出账号后可恢复访客身份；访客会话过期或浏览器数据清除后无法恢复。刷新时若浏览器已清除过期 Cookie，页面会先建立新访客，随后可选择登录原账号。

**限制：**已过期或已清除 Cookie 且从未注册的旧访客无法识别或找回；不支持密码重置、真实验证码或账号合并。`demo` 模式的验证码是公开的固定演示码，没有真实身份验证、发送、限流或防撞库能力，不可用于真实用户或公网。非 demo 配置默认禁用模拟验证码、注册和绑定；上线前需要接入真实验证、认证安全防护、HTTPS 及账号恢复流程。

上传的素材写入后端数据卷，只有所属访客可通过受保护的图片接口读取。服务端同时检查声明类型与图片实际内容、大小及像素，读取不到的素材或其他访客的素材统一返回 404。在模板详情页按要求上传、替换或移除图片，上传完毕后可提交任务；右侧展示当前模板记录，点击“我的记录”可查看全部历史。处理中任务自动更新，查询失败时可手动恢复；刷新页面可从后端恢复未结束任务，成功后可预览图片。未引用的文件暂不自动清理，磁盘使用可能增长。

## 错误文案与语言

错误码与中英文安全文案集中在 `backend/src/main/resources/error-catalog.json`，Java 类型常量与目录在加载时校验；前端控制流常量由 `error-catalog-check` 核对。保留既有对外错误码与状态，错误响应增加 `messageKey`，前端保留 `requestId` 并优先读取 Java 的语言包。语言包不可用时回退安全响应文案；网络不可用时显示通用提示。详见 [错误码盘点与契约](docs/error-catalog-inventory.md)。

错误文案默认中文，设置 `NEXT_PUBLIC_ERROR_LOCALE=en` 后重建前端镜像可使用英文错误提示；这不是全站语言切换。后端按 `Accept-Language` 支持 `zh-CN`、`en`，不支持的语言回退中文。`GET /api/i18n/errors?locale=en` 返回公开语言包。已有后台任务失败文案仍以中文保存；历史任务文案国际化、请求编号与结构化日志关联将在后续阶段完成。

## 验证命令

所有项目命令在容器内执行：

```sh
docker compose -f compose.check.yml run --build --rm frontend-check
docker compose -f compose.check.yml run --rm error-catalog-check
docker compose -f compose.check.yml run --build --rm backend-check
docker compose --profile tools run --rm foundation-check
docker compose --profile tools run --rm asset-check
docker compose --profile tools run --rm task-check
docker compose logs --no-color backend frontend
docker compose down
```

前端检查格式、lint、类型、单测和构建；Java verify 检查格式、运行单测及独立 MySQL 集成测试并打包；foundation-check、asset-check 和 task-check 对运行服务执行真实 API 检查，会创建访客、素材或任务记录，仅用于隔离演示环境，勿用于生产；task-check 需要 demo 配置。CI push/PR 配置包含这些入口，已在独立容器执行等价检查，但尚未在 GitHub 实际触发。真实连接中断和恢复的隔离演示见 [Task 8 记录](docs/task8-status.md)。

## 部署、回滚与边界

修改 .env → build/up → 健康与接口验证。回滚切换前一镜像，保留 MySQL/文件卷；数据库新增迁移不自动倒退，不承诺自动撤销破坏性 DDL。回滚到 V7 前的版本不会处理新的访客恢复记录；回滚到 V6 前的版本无法正确处理只有一种联系方式的新账号；回滚到 V5 前的版本会暂时失去账号登录及新会话识别能力，数据表和资料仍应保留，恢复新版本后才能重新使用账号。down 保留数据，不使用 down -v 清除现有记录。

六张结果示例复用本地模板封面；模拟生成会将预置图片复制为任务结果，并不使用真实 AI 改造上传的图片。后台任务只支持单实例；服务重启时旧的等待中或处理中任务标记为失败，不自动续跑。队列已满时仍返回 202，但该任务会记为失败；查询结果才能确定最终状态。任务创建与线程派发之间存在进程异常窗口，不提供幂等、自动重试或断点续跑。不做独立 admin、真实 AI、MQ/Redis；注册验证码仅供演示。AI 用于拆分、实现及只读审查；封面取自 Pexels，来源与许可见 [素材来源](docs/asset-sources.md)。尚无准确工时计量，不拿计划 24 小时冒充实际投入；完整交付前需补齐投入记录。

前端检查包含 Prettier 格式、lint、类型、单测与构建；Java verify 包含 Spotless 格式、单测和独立 MySQL 集成测试。前端源码可使用 `npm run format`，Java 使用 `mvn -B -s settings.xml spotless:apply`，均应在容器中执行。原计划的检查入口改用 `compose.check.yml`，其中 mysql-test 使用临时存储，不连接演示数据库。

后端提供 Apache Maven Wrapper 3.3.4，固定 Maven 3.9.9；容器检查使用相同版本的 Maven 镜像。Flyway V4 配置结果示例，已执行的 V1—V3 保持不变。
