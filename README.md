# 小影科技笔试 · 素材制作网站

当前完成架构、访客、账号注册登录与身份弹窗、模板浏览和筛选、上传与任务提交页面，以及后台三阶段处理、个人历史和结果接口。数据与任务状态由 Java/MyBatis/MySQL 管理。页面已支持任务自动跟踪、个人历史、结果预览和阶段耗时；容器验收及尚未验证的事项见 [阶段 D/E 记录](docs/logging-phase-de-status.md)和[最新验收复核](docs/acceptance-review-2026-10-29.md)和 [Task 9 记录](docs/task9-status.md)。

最新代码验收基线为 `25233dc5551b7b6e24d0423db9350829fcdb8943`；其后的交付整理提交仅更新说明。创作台浏览器验收范围见 [创作台复验](docs/workspace-preview-plan.md)，多站会话隔离与逐项复核见 [会话隔离记录](docs/session-isolation-plan.md)。

用户手动上传示例后，已在独立站复验多任务、处理中刷新恢复、预置结果预览与部分小屏及键盘操作；本次未直接观察文件选择、上传过程、替换和移除，详见 [Task 9 记录](docs/task9-status.md)。刷新会清空当前上传位，不会删除已受理的任务记录。

## 一键启动

安装 Docker 与 Compose 插件后，在项目目录执行：

```sh
docker compose up --build -d --wait
```

默认访问 http://localhost:3000。无需宿主 Node/Java/MySQL、宿主绝对路径或付费资源。首次需要联网下载镜像与依赖。Flyway 自动执行 V1 表结构、V2 六模板种子、V3 封面和 V4 结果示例配置、V5 账号和会话迁移、V6 联系方式可选迁移、V7 访客恢复表及 V8 任务错误原因字段；用户由访问时创建。示例封面为 Pexels 授权照片，随 Java 镜像打包。

## 统一环境配置

需要更换配置时将 `.env.example` 复制为 `.env`，再使用同一启动命令。不复制也可使用本地演示默认值。

| 变量 | 作用 |
| --- | --- |
| COMPOSE_PROJECT_NAME | 项目、网络和数据卷隔离，不依赖源码目录名称 |
| FRONTEND_PORT / FRONTEND_BIND_ADDRESS | 主机端口及绑定地址，默认 3000 / 0.0.0.0；诊断覆盖文件强制仅绑定 127.0.0.1 |
| PUBLIC_ORIGIN | 允许的站点来源；未设置时按 FRONTEND_PORT 生成本地来源，远程/HTTPS 必须填写实际来源，无末尾斜杠；多来源逗号分隔 |
| MYSQL_IMAGE/DATABASE/USER/PASSWORD/ROOT_PASSWORD | MySQL 版本和初始化库/凭据 |
| AUTH_DEMO_CODE / AUTH_PASSWORD_ITERATIONS | 演示验证码（默认 123456）/ 密码 PBKDF2 迭代次数（默认 210000）；验证码仅 demo 生效 |
| SESSION_COOKIE_NAME / SESSION_LEGACY_COOKIE_NAME | Compose 中的 Cookie 名称前缀（默认 `studio_session`）及迁移时读取的旧完整名称（默认与前缀一致）；Compose 生成的当前名称为前缀加 `_` 和 `FRONTEND_PORT` |
| SESSION_MIGRATION_MARKER_TTL_SECONDS | 防止旧身份再次回退的站点迁移标记有效期，默认 400 天；不得短于旧会话有效期，实际值至少为当前会话 TTL |
| SESSION_TTL / SESSION_SECURE | Cookie 有效期（7d/2h）及 HTTPS 标志；HTTPS 部署设置 Secure=true |
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
| LOG_CONSOLE_FORMAT / LOG_LEVEL / LOG_SLOW_REQUEST_MS | 结构化日志格式、业务日志级别及慢请求阈值 |
| LOG_MAX_SIZE / LOG_MAX_FILES | Docker json-file 单文件大小和文件数，默认 10m / 3 |
| METRICS_JVM_ENABLED | 是否采样 JVM 堆和 GC 指标，默认 false；线程池和排队采样始终启用 |
| DIAGNOSTICS_ENABLED / DIAGNOSTICS_SECRET | 诊断开关与独立凭据，默认关闭；凭据不写入示例或源码 |
| DIAGNOSTICS_CAPACITY / DIAGNOSTICS_MAX_EVENT_BYTES / DIAGNOSTICS_SESSION_TTL_SECONDS | 内存事件容量、单条字节限制、会话有效秒数；默认 500 / 2048 / 900 |

Java 环境配置统一位于 application.yml；前端名称和协议路径位于 src/config/app.ts。固定协议、加密算法、枚举和容器内部端口不机械变为配置。模板内容由种子/数据库管理，不写死在前端。图片卷使用命名卷，STORAGE_ROOT 是容器路径，不是宿主路径。

公开封面目前固定打包在 Java classpath；如后续支持更换目录，需要同步放开 Compose 的 TEMPLATE_MEDIA_ROOT。MySQL 已存在卷不会因修改初始化凭据自动改变用户，应做数据库迁移，或使用另一个项目名建立独立演示环境，不能删除旧卷冒充迁移。

浏览器 API/图片地址都是相对 /api，不返回容器主机名或本机磁盘路径。初次默认密码仅用于本地演示，部署到公网前必须更换。根 .env 不提交。

## 结构与版本

frontend：Next.js 16.4.3、React 19.2.4、TypeScript 5.9.3、Query 5.90.16；组件测试使用 Vitest 3.2.4、Testing Library、MSW 和 jsdom；package-lock.json 固定依赖，npm ci 构建。
backend：Java 21、Boot 3.5.16、MyBatis Starter 3.0.5、Flyway；MySQL 8.4.7；Maven 3.9.9 容器构建。镜像尚未固定 digest。

- frontend/src：页面、访客入口、模板组件、API 客户端。
- backend/src：按 identity/template/asset/task/storage/common/config 分包。
- backend/src/main/resources/demo/covers：真实照片封面（JPEG，1000×1250），不是上传素材；上传阶段仍只接受 JPEG/PNG。
- checks/foundation.mjs、checks/assets.mjs、checks/tasks.mjs：容器接口合同检查；checks/persistence.mjs：独立环境重建前后的数据持久化检查。
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

创作台右侧展示模板示例或选中任务；有历史任务时可点击“查看模板示例”与“返回任务”切换。任务状态查询失败时，预览区可直接点击“恢复查询”；这与后台任务执行失败是不同状态。

## 制作与失败演示

建议使用单独的 Compose 项目与端口演示，避免影响已有资料。以下命令启用 demo 的页面模拟选项，并将生成阶段设为 15 秒，便于观察；变更前端构建变量需要重建镜像：

**浏览器会话隔离：**Cookie 本身不按端口隔离；本项目的 Compose 配置将 `FRONTEND_PORT` 加入会话 Cookie 名称，例如 3000 和 3109 站点分别使用 `studio_session_3000` 和 `studio_session_3109`，访客备份和诊断会话也使用各站点的名称。首次升级时，如果新名称和迁移标记均不存在，后端只在当前数据库验证旧 Cookie 有效后迁移会话及可验证的访客备份；不会删除旧 Cookie，也不会用旧 Cookie 覆盖已存在的新会话。迁移标记避免退出或失效后重新回退旧身份，其有效期须覆盖旧会话有效期；浏览器清除 Cookie 仍会影响恢复。已被覆盖、清除或过期的匿名访客会话不能恢复，注册账号可重新登录。修改端口或 Cookie 前缀后，请将 `SESSION_LEGACY_COOKIE_NAME` 设置为迁移前的完整 Cookie 名称，并保持各站点前缀和端口配置一致；在迁移前备份数据和保留浏览器会话。自建反向代理、非 Compose 部署或多个实例共用相同前缀与端口时，需要自行配置不同的名称；端口命名不是访问权限隔离的替代品。

```sh
FRONTEND_PORT=3109 NEXT_PUBLIC_SHOW_SIMULATION_CONTROLS=true TASK_GENERATE_DELAY_MS=15000 \
  docker compose -p xiaoying-demo up --build -d --wait
```

1. 打开 `http://localhost:3109`，选择“旅行明信片”，按要求上传项目附带的 `demo-assets/uploads/example.jpg` 或自己的 JPEG/PNG 图片。选择“正常执行”并点击“开始制作”；在制作记录中查看阶段，完成后预览结果。
2. 展开“模拟设置”，依次选择“素材检查”“模拟生成”或“结果整理”，分别提交任务，可观察指定阶段失败及后续步骤跳过。选择“正常执行”可恢复成功演示。
3. 在任务处理中继续提交另一任务；刷新页面或前往“我的记录”，检查每个任务独立更新与历史恢复。若查询暂时失败，保留上次状态并可点击“恢复查询”。
4. 三阶段默认耗时由 `TASK_ASSET_CHECK_DELAY_MS`、`TASK_GENERATE_DELAY_MS` 和 `TASK_FINALIZE_DELAY_MS` 配置，单位毫秒；修改后使用同一项目名重新启动。重启后端会将未完成任务标记失败，调整前应先等待任务结束。也可使用 [任务 API](docs/api.md) 中的 `simulation` 请求覆盖各阶段耗时。

结束时可执行 `docker compose -p xiaoying-demo down`；不加 `-v`，数据库与素材卷会保留。模拟选项仅在 demo 模式使用，不需要真实 AI 服务。

## 错误文案与语言

错误码与中英文安全文案集中在 `backend/src/main/resources/error-catalog.json`，Java 类型常量与目录在加载时校验；前端控制流常量由 `error-catalog-check` 核对。保留既有对外错误码与状态，错误响应增加 `messageKey`，前端保留 `requestId` 并优先读取 Java 的语言包。语言包不可用时回退安全响应文案；网络不可用时显示通用提示。详见 [错误码盘点与契约](docs/error-catalog-inventory.md)。

错误文案默认中文，设置 `NEXT_PUBLIC_ERROR_LOCALE=en` 后重建前端镜像可使用英文错误提示；这不是全站语言切换。后端按 `Accept-Language` 支持 `zh-CN`、`en`，不支持的语言回退中文。`GET /api/i18n/errors?locale=en` 返回公开语言包。新失败任务保存文案键并按查询语言展示；旧任务按兼容规则读取。

## 日志与任务错误记录

后端将结构化日志输出到容器控制台。每个后端接收的请求生成 `X-Request-Id`；错误响应中的 `requestId` 与响应头一致。任务创建日志同时包含请求编号和任务编号，可用于关联后台阶段日志。HTTP 5xx 的页面提示会显示请求编号；网络连接未建立或前端代理失败时可能没有后端编号。成功轮询默认不逐次记录，慢请求阈值由 `LOG_SLOW_REQUEST_MS` 控制。

```sh
docker compose logs --no-log-prefix --no-color backend \
  | jq -R 'fromjson? | select(.requestId == "<请求编号>")'
docker compose logs --no-log-prefix --no-color backend \
  | jq -R 'fromjson? | select(.taskId == "<任务编号>")'
```

没有 `jq` 时可直接查看 `docker compose logs --no-color backend`；启动横幅等非 JSON 内容不属于结构化事件。`LOG_CONSOLE_FORMAT` 默认 `ecs`，`LOG_LEVEL` 控制应用业务事件日志级别，`LOG_MAX_SIZE` 与 `LOG_MAX_FILES` 控制后端容器的 `json-file` 日志轮转。旧日志可能因轮转或容器重建不可用，日志不是持久审计系统。关闭日志会影响编号检索。

业务事件不主动记录联系方式、密码、验证码、Cookie、请求体、原始文件名或路径；日志仍包含可关联的任务和素材 ID，应限制访问。对框架中会打印非法路径的警告做了定向抑制，并保留不带原始路径的可疑请求事件。测试覆盖已知日志路径；自行调高第三方框架或 MyBatis 日志级别可能暴露参数，需要另行审查。

Flyway V8 为任务及步骤增加可空的安全文案键、预留参数和内部原因；普通个人 API 不返回内部原因。新失败记录按请求语言生成安全文案，旧记录只展示已知安全历史文案，无法确认安全的内容回退为通用提示，原数据库值不被重写。当前错误文案不使用插值参数。详情见 [阶段 C 记录](docs/logging-phase-c-status.md)。

## 阶段耗时与本机开发诊断

任务详情使用持久化的阶段起止时间计算耗时；缺失、无效或结束早于开始时显示“耗时不可用”，等待、执行中及跳过的步骤不显示已完成耗时。后台日志中的阶段耗时和排队等待使用单调时钟；队列深度与活跃线程数是当时的线程池快照。设置 `METRICS_JVM_ENABLED=true` 可记录全 JVM 堆和 GC 累计快照：它们受其他任务及 JVM 活动影响，不能作为某个任务的资源消耗。JVM 不提供 GC 计数或耗时时，对应字段不显示。采样失败不会中断任务。

诊断页面默认关闭，没有普通页面入口。开发时需要 Docker Compose 支持 `!override`，用覆盖文件将前端绑定到本机并显式启用（以下命令使用 Bash 或 Zsh）：

```sh
# 从密码管理器输入独立的随机凭据；输入不回显，不应保存到 .env 或命令历史。
read -rs DIAGNOSTICS_SECRET
export DIAGNOSTICS_SECRET
docker compose -f compose.yml -f compose.diagnostics.yml up --build -d --wait
# 访问 http://localhost:3000/dev/logs，输入相同凭据。
```

请使用至少 32 字符、由密码管理器或安全随机工具生成的独立高熵凭据；程序只能检查长度（32–256），**不能验证随机性**。不要将凭据提交到仓库或传入 URL。凭据通过容器环境变量传递，具有容器管理权限的本机用户仍可读取。覆盖文件同时启用 `demo,diagnostics` profile；任一缺失、同时启用 `prod`/`production`、凭据或容量配置无效会拒绝启动。只绑定本机不是认证机制，诊断会话仍需独立登录。启用诊断后，普通用户的登录不会授予日志权限；服务端对每次查询鉴权，建立会话和退出必须来自 `PUBLIC_ORIGIN`。诊断 Cookie 为 HttpOnly、SameSite=Strict，默认 15 分钟过期，退出或服务重启失效；内存会话最多 20 个，每分钟最多 10 次失败尝试（成功登录清空计数），重启会重置限制。需要 HTTPS 时设置 `SESSION_SECURE=true`。

页面按请求编号、任务编号、级别和起始时间筛选本实例的白名单结构化事件；不展示原始日志消息、堆栈和输入。事件有容量及单条大小上限，满时淘汰最旧事件；竞争或记录失败可能丢弃新事件，重启后全部消失，**不能用作审计日志**。JVM 指标和个人业务数据一样需要限制本机及容器访问。退出诊断模式时，以正常 Compose 配置重建服务并清除当前 shell 中凭据：

```sh
docker compose up -d --force-recreate backend frontend
unset DIAGNOSTICS_SECRET
```

上述操作会重启后端；未完成任务会按既有规则标记失败，应先等待任务结束。它保留 MySQL 和素材卷。使用不同项目名或端口时，恢复命令也须使用相同项目名和端口配置；不要执行 `down -v`。

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

前端检查格式、lint、类型、单测和构建；Java verify 检查格式、运行单测及独立 MySQL 集成测试并打包；foundation-check、asset-check 和 task-check 对运行服务执行真实 API 检查，会创建访客、素材或任务记录，仅用于隔离演示环境，勿用于生产；task-check 需要 demo 配置。CI push/PR 配置包含这些入口，已在独立容器执行等价检查。2026-10-29 手动触发时 GitHub 返回 `Actions has been disabled for this user`，没有生成运行记录；仓库级 Actions 已启用，账号层面的禁用原因尚未确定。排查与本地验证见 [阶段 D/E 记录](docs/logging-phase-de-status.md)。真实连接中断和恢复的隔离演示见 [Task 8 记录](docs/task8-status.md)。

## 复验容器重建后的数据

下面的检查会创建测试访客、上传项目示例、提交一个成功任务，并重建所选 Compose 项目的容器。**仅针对独立演示项目执行**；重建会中断该项目尚未完成的任务。先按上文启动 `xiaoying-demo`，再运行：

```sh
docker volume create xiaoying-demo-proof
FRONTEND_PORT=3109 docker compose -p xiaoying-demo --profile tools run --rm --no-deps \
  -v xiaoying-demo-proof:/proof asset-check node /checks/persistence.mjs prepare
FRONTEND_PORT=3109 docker compose -p xiaoying-demo up -d --force-recreate --wait
FRONTEND_PORT=3109 docker compose -p xiaoying-demo --profile tools run --rm --no-deps \
  -v xiaoying-demo-proof:/proof asset-check node /checks/persistence.mjs verify
```

检查对比重建前后图片的 SHA-256、会话、任务状态及历史，并验证其他访客无法访问素材和结果。校验成功后会删除临时状态文件；若中途失败，`xiaoying-demo-proof` 中的临时文件可能包含测试会话，应限制 Docker 访问，勿提交到源码。不要使用 `down -v` 删除现有数据卷。

## 部署、回滚与边界

修改 .env → build/up → 健康与接口验证。回滚切换前一镜像，保留 MySQL/文件卷；数据库新增迁移不自动倒退，不承诺自动撤销破坏性 DDL。回滚到 V8 前的版本无法使用新的任务错误文案键和内部原因，但仍读取原错误字段；在独立测试库验证过阶段 B 镜像可启动并读取 V8 中的旧记录。回滚到 V7 前的版本不会处理新的访客恢复记录；回滚到 V6 前的版本无法正确处理只有一种联系方式的新账号；回滚到 V5 前的版本会暂时失去账号登录及新会话识别能力，数据表和资料仍应保留，恢复新版本后才能重新使用账号。down 保留数据，不使用 down -v 清除现有记录。

六张结果示例复用本地模板封面；模拟生成会将预置图片复制为任务结果，并不使用真实 AI 改造上传的图片。后台任务只支持单实例；服务重启时旧的等待中或处理中任务标记为失败，不自动续跑。队列已满时仍返回 202，但该任务会记为失败；查询结果才能确定最终状态。任务创建与线程派发之间存在进程异常窗口，不提供幂等、自动重试或断点续跑。不做独立 admin、真实 AI、MQ/Redis；注册验证码仅供演示。封面取自 Pexels，来源与许可见 [素材来源](docs/asset-sources.md)。实际总投入缺少完整计时，无法可靠填写；不能把三天预算当作实际工时，交付者需按自己的记录补充。

可核实的阶段操作时段及范围记录在 [实际投入记录](docs/effort.md)；其中墙钟时间包含构建、测试和等待，不等于人的净工时，也无法合计为完整项目投入。

使用了 AI 编程工具：依据原题、截图、现有代码与分阶段计划拆解任务，提供相关文件作为上下文，生成或修改代码，再通过容器中的格式、类型、单元、数据库和接口检查核实。比如诊断阶段先验证权限与日志白名单，审查发现不支持的 GC 计数被当成零以及错误响应未使用统一目录，补充测试后修正并重新验证。AI 不代替最终人工确认需求、数据及交付材料。

前端检查包含 Prettier 格式、lint、类型、单测与构建；Java verify 包含 Spotless 格式、单测和独立 MySQL 集成测试。前端源码可使用 `npm run format`，Java 使用 `mvn -B -s settings.xml spotless:apply`，均应在容器中执行。原计划的检查入口改用 `compose.check.yml`，其中 mysql-test 使用临时存储，不连接演示数据库。

后端提供 Apache Maven Wrapper 3.3.4，固定 Maven 3.9.9；容器检查使用相同版本的 Maven 镜像。Flyway V4 配置结果示例，已执行的 V1—V3 保持不变。
