# 素材制作网站 Implementation Plan

> 状态说明：Task 1–8 的过程描述记录当时的实施情况；源码仓库现已建立并有功能分支与提交。最终复核和未完成项以 [Task 9 记录](./task9-status.md)为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. 如用户另行选择分代理执行，再使用 superpowers:subagent-driven-development。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在三天预算内交付可容器运行、核心行为有测试的素材制作网站。

**Architecture:** Next.js 同源代理 Java；Java 使用 MyBatis/MySQL 持久化身份、模板、素材和任务，用有界线程池顺序执行三个阶段。本地卷保存文件，前端唯一任务跟踪器轮询并隔离查询错误。

**Tech Stack:** Next.js 16 / React 19 / TypeScript 5 / Query 5 / Tailwind 4；Java 21 / Boot 3.5.x / MyBatis Starter 3.0.x / MySQL 8.4 / Flyway；Docker Compose / GitHub Actions。

**Spec:** [设计与验收规格](./2026-10-28-design.md)。执行者必须同时阅读，不凭此文件省略规格中的边界。

## Global Constraints

- 根目录固定 `./`；下文路径相对该目录，所有新增项目文件都在其中。
- 三天按 24 个有效工时估算，包含验证和交付；按各阶段实施记录区分已落地与未实现业务，不预先勾选未验证任务。
- Java 掌管业务/后台执行/持久化；MySQL 记录真实状态；GET 不推动任务。
- 三阶段固定 ASSET_CHECK → GENERATE → FINALIZE；整体 QUEUED/RUNNING/SUCCEEDED/FAILED。
- 单实例、默认 2 线程和 20 个等待队列（可配置）、AbortPolicy；无 MQ/Redis、无独立 admin、无真实 AI。
- JPEG/PNG、单图 5242880 字节、最长边 8192、最大 1600 万像素；模拟每阶段 0—30000 ms，默认 1000/6000/1000。
- 详情左制作右历史，当前模板历史与全部记录页分开；全局 active 恢复不能被分页/模板限制。
- 不在宿主机执行项目安装/构建/测试/应用脚本；通过容器进行。阅读和编辑文档源码不受此限制。
- 状态、字段、API 以规格第 5—10 节为准；精确依赖版本初始化时核查并锁定，不使用浮动 latest。
- 不自动提交/推送用户未授权的远端操作；本地可审查里程碑提交应保留测试证据，未初始化 Git 时先按用户工作区情况处理，不擅自覆盖已有仓库。

## Review Focus

**身份恢复更新（2026-10-29）：** 已增加邮箱或手机号单独注册、可选绑定另一种联系方式和密码登录；访客记录默认与新账号分离，仅主动选择导入时沿用访客 ID；账号会话到期后可重新登录恢复记录，账号切换隔离。验证码仅为演示，不提供真实验证、密码重置或账号合并；此前已失去凭据的匿名访客无法找回。实施方案见 [账号身份计划](./account-identity-plan.md) 和更新后的[注册与绑定设计](./account-contact-design.md)。

当前容易误判的边界，必须在后续实现及验收时分别核对：

- 六张结果示例是随项目提供的预置素材；Task 4 已实现模拟复制、结果整理与授权读取，并非真实 AI 生成。
- 匿名访客身份不同不代表个人数据已隔离。Task 3 的素材归属隔离已实现并验证；Task 4 已另行实现并验证任务、历史和结果的接口隔离，Task 7 仍需验证前端切换身份时的状态隔离。
- Task 2 的长时间打开页面后的身份过期提示已补齐；到期后提示旧访客素材无法恢复，由用户主动建立新身份。刷新后若浏览器已清除过期 Cookie，首次访问仍会建立新访客。

1. 提交事务已完成但线程池拒绝：返回可追踪的失败任务；Task 4 已通过队列拒绝集成测试验证，不让记录永久等待。
2. 非第一页仍有处理中任务：刷新后全部恢复；Task 7 验证，不只扫描历史第一页。
3. 旧身份/旧上传请求晚到：不覆盖当前身份/槽位；Task 5、6、7 验证。
4. 终态后聚焦/重挂载：不再自动跟踪；Task 7 验证，主动刷新仍可用。
5. 文件已写但 SQL 失败/反之：正常成功路径必须有完整文件，写库失败不能报告成功；Task 3、4 验证并说明孤立文件限制。

## 文件职责图

```text
frontend/
  src/app/{layout.tsx,page.tsx,templates/[id]/page.tsx,history/page.tsx,tasks/[id]/page.tsx}
  src/components/{providers,identity,templates,upload,tasks,ui}/
  src/lib/api/{client.ts,types.ts,identity.ts,templates.ts,assets.ts,tasks.ts}
  src/features/tasks/{query-keys.ts,task-registry.ts,TaskTracker.tsx,use-task.ts}
  src/features/upload/{upload-state.ts,UploadSlot.tsx}
  src/test/{setup.ts,server.ts,handlers.ts}
  Dockerfile / package.json / package-lock.json / next.config.ts / eslint.config.mjs
backend/
  src/main/java/com/xiaoying/studio/{StudioApplication.java,identity,template,asset,task,storage,common,config}/
  src/main/resources/{application.yml,application-demo.yml,application-prod.yml,mapper,db/migration}/
  src/test/java/com/xiaoying/studio/{identity,template,asset,task,storage}/
  pom.xml / mvnw / .mvn/ / Dockerfile
demo-assets/{covers,results,uploads}/
docs/{api.md,acceptance.md,effort.md,asset-sources.md}
.github/workflows/ci.yml
compose.yml / .env.example / README.md / .gitignore / .dockerignore
```

文件按职责创建，不生成空壳组件凑结构。小 DTO 可集中在本领域 dto 文件/目录，不要求每字段一个文件。

## 容器检查命令契约

以下为当前实现的入口；与最初拟定的 tools profile 方案不同，前后端检查使用独立配置文件。

| 用途 | 命令 | 通过条件 |
| --- | --- | --- |
| 应用启动 | `docker compose up --build -d --wait` | 三个默认服务健康 |
| 前端检查 | `docker compose -f compose.check.yml run --build --rm frontend-check` | Prettier、lint、typecheck、test:run、build 均退出 0 |
| 后端检查 | `docker compose -f compose.check.yml run --build --rm backend-check` | Maven verify、格式、单测及 MySQL 集成测试通过 |
| 基础接口检查 | `docker compose --profile tools run --rm foundation-check` | 隔离演示环境中的身份和模板 API 检查通过 |
| 查看日志 | `docker compose logs --no-color backend frontend` | 可定位任务 ID 和失败阶段 |
| 停止并保留数据 | `docker compose down` | 数据卷保留 |

frontend-check 镜像按锁文件安装依赖；backend-check 依赖 compose.check.yml 中的独立 mysql-test，不接演示数据库。生产 Dockerfile 与工具 target 共用依赖层。check 源码来自构建上下文，不挂载宿主 node_modules/target。

完整 verify 用 Failsafe 执行 *IT。mysql-test 使用临时存储，不与实际演示数据共用。依赖安装与测试均在容器中进行。

## Task 1：固定版本、应用入口与容器骨架（1 小时）

**Files:** 根 README.md/.env.example/.gitignore/.dockerignore/compose.yml；frontend/package.json/package-lock.json/next.config.ts/tsconfig.json/eslint.config.mjs/Dockerfile/src/app/layout.tsx/src/app/page.tsx；backend/pom.xml/mvnw/.mvn/**/Dockerfile/src/main/java/com/xiaoying/studio/StudioApplication.java/src/main/resources/application.yml。

**Interfaces:** 产出同源 `/api/**` 代理、默认三个服务、独立 compose.check.yml 检查入口、后端健康端点。前端 package scripts 包含 lint/typecheck/test:run/build/check；后端 verify 为测试和构建入口。

- [x] 核查兼容表并固定组件版本；记录 README 版本表。镜像 tag 固定但 digest 尚未锁定。
- [x] 在容器初始化 Next App Router 与 Java 应用；设置 Java 21、UTF-8、UTC。
- [x] 配置代理目标 BACKEND_INTERNAL_URL、同源 Cookie 转发；Next build 不抓取后端数据、不使用远程字体。
- [x] 配置应用/检查镜像 target、MySQL 健康检查及独立测试库；实现当前检查命令。
- [x] 运行应用启动及前后端检查，验证代理可达；补充前端测试、Maven Wrapper 和格式检查。
- [ ] 精确补齐实际投入；当时尚未初始化 Git，逐阶段里程碑 commit 未创建；现已有源码仓库和提交。

## Task 2：迁移、匿名身份、模板（2 小时）

**Files:** backend 的 db/migration/V1__schema.sql、V2__seed_templates.sql；identity/{IdentityController,IdentityService,CurrentUserResolver,UserMapper}.java；template/{TemplateController,TemplateService,TemplateMapper}.java；common/{ApiException,ApiExceptionHandler}.java；config/{WebConfig,StorageConfig}.java；对应 mapper XML；demo-assets/covers/**、results/**；测试 IdentityServiceTest.java、IdentityApiIT.java、TemplateApiIT.java。

**Interfaces:** `IdentityService.resolve(String token): CurrentUser`、`createOrReuse(String token): SessionResult`；`TemplateService.list(String categoryId): List<TemplateSummary>`、`get(String id): TemplateDetail`；提供规格中的 me/session/categories/templates/cover API，字段严格一致。

- [x] 编写身份、模板测试；用隔离容器验证会话创建/复用、来源校验、分类及 404。
- [x] 在容器运行基础新增测试，确认接口实现缺失时失败；素材集成测试首次运行曾受仓库网络中断，不能将该次网络故障记作有效红灯。
- [x] 实现六表、索引和种子，三个分类六个模板包含 1/2/3 张要求；V4 提供本地结果示例。
- [x] 实现身份 Cookie、有效期和 Origin 写请求保护；实现统一错误和模板查询。身份建立只在 POST，不在业务 GET 隐式建用户。
- [x] 运行 backend-check，验证新建测试库及再次执行迁移；数据库集成测试检查封面和结果素材。
- [x] 更新 docs/api.md 和素材来源；实际投入未精确计量、Git 里程碑未创建，保留为交付待办。

## Task 3：素材上传和授权读取（1.5 小时）

**Files:** asset/{AssetController,AssetService,AssetValidator,AssetMapper}.java；storage/{FileStorage,LocalFileStorage}.java；asset/mapper XML；测试 AssetValidatorTest.java、AssetApiIT.java、LocalFileStorageTest.java；demo-assets/uploads/**。

**Interfaces:** 当前实现为 `AssetService.upload(User, MultipartFile): AssetResponse`、`requireOwnedAssets(User,List<String>,TemplateRequirements): List<Asset>`，以及 `LocalFileStorage.save/read/delete`。素材存储只接受服务端生成的键；结果发布能力留到 Task 4 再设计和实现。

- [x] 编写合法 JPEG/PNG、过大、类型与内容不一致、损坏、尺寸和总像素上限、无权读取及路径检查等测试。
- [x] 在容器中确认素材 API、身份慢响应和真实超大文件反馈的回归测试先失败，再实现或修正。
- [x] 实现临时写入/原子发布、内容校验、素材记录和授权读取；写库失败尽力清理文件，设置 MIME、no-store 和 nosniff 响应头。
- [x] 上传回传服务端 ID 和相对预览地址，经 Next 代理实际读取图片；提供内部的素材数量、类型、大小、重复项和归属校验方法。
- [x] 在隔离 MySQL 和真实代理下执行检查，验证成功/拒绝请求及素材隔离。当前目录无 Git 仓库，里程碑 commit 作为交付待办。

## Task 4：接单、三阶段后台执行、历史与模拟（3.5 小时）

**实现与验证：** 具体文件和验证证据见 [Task 4 记录](./task4-status.md)；使用 MyBatis 注解映射、短事务和可配置有界线程池，默认并发 2、队列 20。

- [x] 实现提交校验、事务创建任务与步骤，提交后异步派发；队列拒绝保存失败结果并返回可查询快照。
- [x] 实现条件领取、三阶段顺序执行、各阶段失败中断、预置结果整理与授权读取。
- [x] 实现按访客隔离的单任务、历史分页、模板筛选和全量 active 查询；查询不推进任务。
- [x] 支持 demo 参数快照、prod 拒绝覆盖、启动时将旧未终结任务标记中断、可配置线程池及关闭宽限期。
- [x] 容器测试覆盖步骤顺序、失败中断、重复领取、异步受理、参数校验、队列拒绝、成功终态、访客隔离和启动收尾逻辑。
- [x] 使用单线程隔离容器通过代理验证两个任务、三阶段失败、历史、结果和访客隔离。
- [x] 在独立 Compose 项目内强制终止并重启后端，验证运行中任务标记中断、后续步骤跳过且不续跑。
- [ ] 创建里程碑 commit（当时交付目录尚未初始化 Git）。

**第一天出口：** 后端闭环可验证；不能进入只做漂亮前端而任务执行仍是假状态的路径。

## Task 5：前端基础、模板列表和身份（2 小时）

**实现与验证：** 复用现有 API 客户端、Query Provider 和模板组件，新增统一 IdentityProvider 与访客弹窗；实际文件、验证和取舍见 [Task 5 记录](./task5-status.md)。

- [x] 增加 MSW 组件测试：401 后共享一次身份创建、模板筛选及空状态、加载/请求失败/详情 404，以及旧身份响应不能覆盖新身份。
- [x] 实现统一身份上下文、过期提示和主动建立新身份；切换时取消并移除旧个人数据查询，提供身份世代供后续任务使用。
- [x] 完成暗色变量、导航、访客入口、身份弹窗，以及来自 Java 的列表和分类；不添加虚假账号、会员或积分。
- [x] 运行 frontend-check；在隔离浏览器经代理加载模板和图片，检查分类筛选、键盘进入卡片、弹窗 Escape 关闭及焦点归还。
- [ ] 补充准确实际投入并创建里程碑 commit（投入未精确计时；当时目录尚未初始化 Git）。

## Task 6：左侧制作、上传槽位和提交（3 小时）

**实现与验证：** 使用独立上传槽位、服务端素材 ID、任务提交接口及可配置演示选项；实际范围和验证证据见 [Task 6 记录](./task6-status.md)。

- [x] 测试图片数量/类型/大小、上传中禁止提交、替换失败保留旧图、移除及请求乱序、受理后解除提交状态。
- [x] 按槽位的请求版本与 AbortController 处理取消，释放临时预览地址；会话变化和卸载不接受旧结果。
- [x] 按模板要求显示上传位，支持选择、预览、替换、移除、上传中和失败提示；小屏堆叠。
- [x] 实现提交防重复、受理反馈和未知结果提示，不自动重提；通过构建配置显示演示失败选项，后端仍独立校验。
- [x] 容器前端检查通过；浏览器验证 1/2/3 张模板上传提交、连续提交及指定阶段失败；受理快照为临时展示，Task 7 接入跟踪。
- [ ] 精确补充实际投入并创建里程碑 commit（投入未精确计时；当时目录尚未初始化 Git）。

## Task 7：唯一跟踪器、右侧历史、全部记录和结果（3 小时）

**实现与验证：** 使用共享任务跟踪器和独立任务定时器，页面共享服务端快照；实际范围与证据见 [Task 7 记录](./task7-status.md)。

- [x] 可控响应和虚拟计时器测试覆盖多个任务、查询异常与任务失败区分、手动恢复、终态和无任务时停止轮询。
- [x] 验证全量 active 恢复超过一页的任务、旧身份请求取消、旧状态不能覆盖终态及卸载清理；验证后台标签页暂停和回到前台恢复。
- [x] 实现统一跟踪器、可配置轮询间隔和查询超时、错误暂停与人工恢复；每个任务读取服务端状态。
- [x] 实现当前模板历史、手动刷新、个人历史分页、任务详情及三阶段记录、结果缩略图和预览；已有任务快照优先。
- [x] 新任务受理后立即注册并显示，终态刷新历史；历史合并保持页面大小并避免状态回退。
- [x] 运行前端全量检查；浏览器观察多任务、处理中刷新恢复、成功/失败及结果预览；查询异常和终态停止通过组件及虚拟计时器测试。
- [x] Task 8 使用独立测试代理验证真实连接中断、恢复查询和唯一活动任务终态后停止查询；见 Task 8 记录。
- [ ] 补充实际投入并创建里程碑 commit（投入未精确计时；当时目录尚未初始化 Git）。

**第二天出口：** 页面能完成全流程，关键前端异步测试可验证。若超时，削减动画和装饰，不能用全局 loading/error 替代任务独立状态。

## Task 8：边界补齐与容器 CI（3 小时）

**实现与验证：** Java 边界集成测试、CI 配置和独立容器验收详见 [Task 8 记录](./task8-status.md)。

- [x] 覆盖三阶段失败、队列拒绝、会话隔离与失效、终态不可回退、结果源和发布结果文件缺失；针对结果源缺失补充可理解的失败原因。
- [x] 本地独立 mysql-test 与后端 verify、前端锁文件安装及 lint/类型/测试/构建通过；流水线语法和 push/PR 条件已检查。
- [x] 无代理独立 Compose 项目完成健康、分类/封面、访客、上传、任务及结果的接口冒烟。
- [x] 配置失败日志和独立的 always 清理步骤，不通过 down -v 删除数据卷。
- [x] 通过隔离测试代理和浏览器验证连接中断、恢复查询及唯一活动任务终态后停止自动查询。
- [x] 本轮容器检查已通过并记录证据。
- [x] GitHub 托管 CI 已在目标仓库对 `280acbb` 的 push 中通过；详见 [Task 9 记录](./task9-status.md)。

## Task 9：从零验收、文档与交付（3 小时）

**当前进度：** 已完成独立空卷启动、数据卷接口复验、部分小屏与键盘检查；用户手动上传后复验了多任务、处理中刷新恢复及结果预览。目标仓库的 GitHub push 检查已通过。上传过程、替换移除等本次未直接观察，真实总工时仍待补充。详见 [Task 9 记录](./task9-status.md)。前面各 Task 关于尚未初始化 Git 或未创建提交的文字是当时的历史记录，当前仓库和功能分支已存在。

**Files:** README.md、docs/api.md、docs/acceptance.md、docs/effort.md、docs/asset-sources.md、.env.example；只做验收暴露问题的必要修复。

**Interfaces:** 一个可读取源码目录/仓库 commit 或源码 zip；README 中的命令必须与实际入口一致，不填占位版本/假凭据。

- [x] 使用独立 Compose project 和干净卷从零启动，不删除已有正常数据；完整人工流程仍见下方待办。
- [x] 检查示例素材和依赖入口；页面资源使用相对路径，未发现在线素材或字体引用。空访客在隔离容器重建后保持身份；任务、素材及结果数据卷重建后的接口复验通过，完整浏览器复验仍待完成。
- [x] 检查 375px 下列表、双素材详情及空历史的横向溢出，以及部分键盘焦点和身份弹窗 Escape；不代表全站完整无障碍验收。
- [x] 补充 README 中成功、指定阶段失败、多任务和刷新恢复的演示步骤；AI 工作流和实际修正案例已记录。
- [x] 更新已实现/未实现/已验证/未验证、部署回滚及数据保护说明；已知总工时未准确记录，见 [投入记录](./effort.md)。
- [x] 检查 Git 跟踪文件不含已知依赖目录、构建产物、环境凭据或数据库卷；应用验收基线 commit 为 `97d4685`，后续仅文档提交单独注明。
- [x] 在容器中重新执行前后端完整检查和错误目录检查；通过当前对话提供源码分支。
- [x] 用户手动上传后，在独立浏览器复验连续提交、成功和失败展示、处理中刷新恢复、结果预览 Escape、带任务的模板示例切换及部分小屏布局。
- [ ] 本次未直接观察文件选择、上传进度和失败、替换及移除；带任务和素材的数据卷重建后浏览器访问仍待复验。此前自动文件选择被安全审核拒绝。
- [x] GitHub 托管 push 检查通过，记录运行链接与验收 commit；pull request 触发尚未单独验证。
- [ ] 根据可靠记录补充项目真实累计工时；目前无法准确重建。

## 缓冲与调整（2 小时）

合计：Task 1—4 为 8 小时，Task 5—7 为 8 小时，Task 8—9 为 6 小时，另留 2 小时处理集成与容器问题。容器验证从 Task 1 开始，不把最终缓冲当首次联调。

可削减：动画、装饰、开发面板视觉、额外模板。不可削减：真实上传、Java 三阶段、失败停止、身份隔离、查询异常区分、多任务与刷新恢复、核心测试、必要交付说明。不增加独立 admin 来扩大工作量。

## 验收映射与进度

| 要求 | 实施任务 | 当前状态 |
| --- | --- | --- |
| R-01 | 1、2、4 | 前后端技术栈与任务闭环已实现；最终完整验收待完成 |
| R-02 | 2、5 | 模板接口、种子、筛选、详情和浏览器基础交互已验证 |
| R-03 | 3、6 | 详情、上传界面、前后端校验和提交防重复已实现并验证 |
| R-04 | 4、6 | 后端异步受理和执行及前端提交入口已实现并验证 |
| R-05 | 2、4、7 | 后端三阶段、状态查询与前端展示已实现并测试 |
| R-06 | 4、6 | 后端模拟失败和耗时配置、可配置前端演示入口已实现并验证 |
| R-07 | 6、7、8 | 上传、提交、任务状态及查询异常区分已实现；隔离代理真实连接中断和恢复已验证 |
| R-08 | 7、8 | 多任务跟踪、清理和终态停止已测试；代理日志验证唯一活动任务结束后无周期查询 |
| R-09 | 4、7 | 历史、刷新恢复和结果预览已实现并通过基础浏览器验证 |
| R-10 | 2、3、4、5 | 账号注册、登录与会话恢复已实现；已有账号资料隔离，匿名访客失效后仍不可找回 |
| R-11 | 5、6、7、9 | 列表、制作、历史及预览界面已有；完整视觉与无障碍验收待完成 |
| R-12 | 2—8 | 前后端测试、本地检查和 GitHub 托管 push 检查通过；PR 触发尚未单独验证 |
| R-13 | 9 | 基础说明已有；实际投入和最终交付待完成 |
| R-14 | 1、8、9 | Compose 已验证接口和主要页面流程；从零完整交付验收待完成 |

## 执行记录要求

每任务登记日期、开始/结束、开发/验证/交付整理用时、变更文件、验证命令/结果、未验证项和偏差。计划总预算不能替代实际累计。文档整理投入尚未精确计时，不填写虚构数值。

用户后续已授权最小架构骨架实施；本轮不扩展到业务填充、创建远端仓库或公网部署。当前入口与原计划命令差异见 architecture-status.md。

## 基础阶段进度更新

基础阶段已落地身份与模板 API、V2 种子和容器 API 验证；当时仅部分完成 Task 5/6 的访客入口与模板浏览。基础阶段证据见 foundation-status.md，当前状态以 Task 4、5 进度更新为准。

## Task 1、2 基础收尾

两项的实现基础和当前验证已完成：补齐 Maven Wrapper、前端测试入口、自动格式检查、独立 MySQL 集成测试和 V4 结果示例。V4 复用封面照片；当时尚未实现制作流程，现已由 Task 4 补齐模拟处理。当时目录没有 Git 仓库、里程碑 commit 未创建；目前源码仓库及提交已存在；镜像未锁 digest，实际投入未精确记录。Task 3、4 进度见下文，Task 5—8 已完成主要功能和本地验证；Task 9 尚有浏览器复验范围，目标仓库的托管 push CI 已通过。

## Task 3 与身份过期提示

已完成素材上传 API、服务端文件校验、文件卷持久化、素材授权读取、按模板约束校验素材的内部方法，以及长时间打开页面后的身份过期提示。实际接口和验证证据见 [Task 3 记录](./task3-status.md)。上传、替换、移除的页面交互已由 Task 6 完成；任务记录隔离、模拟结果及结果 API 已由 Task 4 完成；状态与历史展示属于 Task 7。

## Task 4 进度更新

后台任务 API、三阶段执行、模拟结果、历史及访客隔离已实现并经过容器验证。详细范围、测试及限制见 [Task 4 记录](./task4-status.md)。前端上传由 Task 6 接入，轮询、历史和结果展示由 Task 7 接入；该阶段的匿名身份限制后来已由账号注册与登录部分缓解；未注册访客到期后仍无法找回。

## Task 5 进度更新

前端身份上下文、访客弹窗、模板浏览和筛选已完善，并通过组件与浏览器基础验收；详见 [Task 5 记录](./task5-status.md)。上传和提交已由 Task 6 接入，跟踪和历史由 Task 7 接入；后续已补充账号注册和登录，未注册访客到期仍无法恢复。

## Task 6 进度更新

模板详情页的上传槽位和任务提交已实现并验证；详见 [Task 6 记录](./task6-status.md)。Task 7 已接入自动跟踪、历史和结果展示。

## Task 7 进度更新

任务跟踪、个人历史、详情及结果预览已实现并通过组件测试和基础浏览器验收。Task 7 阶段证据见 [Task 7 记录](./task7-status.md)，真实连接中断与恢复已在 [Task 8 记录](./task8-status.md)补充验证；Task 9 仍待完成。

## Task 8 进度更新

边界集成测试、CI 结构及本地容器验收已完成；真实连接中断恢复和终态停止查询已有隔离浏览器与代理日志证据，详见 [Task 8 记录](./task8-status.md)。目标仓库的 GitHub 托管 push 检查已通过；交付剩余范围见 [Task 9 记录](./task9-status.md)。
