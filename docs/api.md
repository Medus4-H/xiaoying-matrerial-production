# 已实现的接口

| 路由 | 结果 |
| --- | --- |
| GET /api/me | 200 {id,displayName,expiresAt,type,email,phone}；无有效会话 401 |
| POST /api/session | 新建 201、复用 200；Set-Cookie，JSON 同 me |
| GET /api/categories | {items:[{id,name}]} |
| GET /api/templates?categoryId=... | {items:TemplateDetail[]}；省略筛选返回全部，未知分类空列表 |
| GET /api/templates/{id} | TemplateDetail，缺失 404 |
| GET /api/templates/{id}/cover | 本地封面图片（JPEG），缺失 404 |
| POST /api/assets | Multipart `file`；身份有效时返回 201、素材标识、预览地址、实际类型、字节数和宽高 |
| GET /api/assets/{id}/content | 仅素材所属用户可读取；无身份 401，不存在或无权限 404 |

TemplateDetail：id/title/categoryId/categoryName/description/coverUrl/requiredImageCount/acceptedMimeTypes/maxFileSizeBytes。列表目前返回同一完整 DTO，字段兼容设计的 Summary。coverUrl 为相对地址。

错误：{code,message,fieldErrors:null,requestId}；不包含 stack trace、磁盘路径或数据库异常。身份响应 no-store。不存在模板 TEMPLATE_NOT_FOUND、缺身份 SESSION_REQUIRED、非法来源 ORIGIN_DENIED。框架的 404/405 保留原状态码。

上传默认接受 JPEG/PNG、单张不超过 5 MiB，实际解码内容必须与声明类型一致；图片宽高和总像素也有限制。常见错误为 INVALID_ASSET（400）、ASSET_TOO_LARGE（413）、ORIGIN_DENIED（403）。文件大小和图片尺寸限制由环境配置提供。文件内容响应为 no-store，并提供 nosniff。服务端不会向客户端返回文件系统路径。任务提交会再次检查素材数量、重复项、类型、大小及归属。


## 账号接口

所有写接口校验 Origin。`type` 为 `GUEST` 或 `ACCOUNT`，游客的 `email` 和 `phone` 为 null；所有身份响应禁缓存。注册必须携带有效访客 Cookie；默认创建独立账号并保留原浏览器访客会话，只有显式传 `importGuest: true` 才沿用原访客 ID 和记录。

| 路由 | 请求与结果 |
| --- | --- |
| POST /api/auth/demo-code | `{channel: "email" 或 "phone", destination}`；demo 模式返回 `{demoCode}`，仅模拟发送 |
| POST /api/auth/register | `{channel: "email" 或 "phone", destination, password, code, importGuest?: boolean}`；使用单一联系方式注册，默认不导入访客记录，成功返回账号身份。为兼容旧客户端，仍接受旧的 `{email, phone, password, emailCode, phoneCode}` |
| POST /api/auth/bind | 登录账号提交 `{channel, destination, code}` 绑定尚未设置且未被占用的联系方式，返回更新后的账号身份；无需再次输入密码，不可用于覆盖或合并账号（兼容旧客户端传入的 `password` 字段，服务端不使用） |
| POST /api/auth/login | `{identifier, password}`；identifier 可为邮箱或手机号，成功返回身份并设置新 Cookie |
| POST /api/auth/logout | 当前账号会话失效并清除 Cookie，返回 204；账号数据仍保留 |

重复注册返回 409/ACCOUNT_EXISTS；若浏览器已有不同的待恢复访客且当前访客也有资料，返回 409/GUEST_BACKUP_EXISTS，避免覆盖原访客会话；登录凭据错误返回 401/LOGIN_FAILED；重复绑定当前账号已有联系方式返回 409/CONTACT_ALREADY_BOUND；绑定其他账号占用的联系方式返回 409/ACCOUNT_EXISTS；非 demo 的模拟验证码、注册和绑定返回 403/DEMO_DISABLED。手机号仅按输入格式去除首尾空格并逐字符串判重，不做国家地区归一化；模拟码无法验证真实联系方式，没有密码重置或账户合并功能。账号记录存于 MySQL，素材存于文件卷，两者都需要保留才能恢复完整资料。

## 任务接口

所有接口使用当前用户 Cookie；写请求验证 Origin。任务、历史与图片仅限所属用户，不存在或无权限返回 404。提交后立即返回 HTTP 202 和任务快照，不等待制作结束；排队已满也返回 202，任务状态为 FAILED，errorCode 为 DISPATCH_REJECTED。查询只读取数据库，不驱动处理。

| 路由 | 请求与结果 |
| --- | --- |
| POST /api/tasks | JSON 含 `templateId`、按模板数量提供的 `assetIds`；可选 `simulation`，返回任务快照 |
| GET /api/tasks/{id} | 返回个人任务快照 |
| GET /api/tasks/active | 返回 `{items: TaskSnapshot[]}`，包含当前访客全部未结束任务 |
| GET /api/tasks?page=1&pageSize=20&templateId=... | 返回 `{items, page, pageSize, total}`；模板筛选可省略，page 从 1 开始，pageSize 为 1–50 |
| GET /api/tasks/{id}/result | 成功任务的 JPEG 结果；无权限、未成功或文件不存在返回 404 |

TaskSnapshot 包含 `id`、`templateId`、`templateTitle`、`status`、`currentStage`、`failedStage`、`errorCode`、`errorMessage`、`resultUrl`、`createdAt`、`startedAt`、`finishedAt`、`updatedAt`、`steps`。步骤包含 `stage`、`sequence`、`status`、`startedAt`、`finishedAt`、`errorCode`、`errorMessage`。未发生的时间、错误和结果字段可为 null；`resultUrl` 是相对地址。整体状态为 QUEUED、RUNNING、SUCCEEDED、FAILED；阶段依次为 ASSET_CHECK、GENERATE、FINALIZE；步骤状态为 PENDING、RUNNING、SUCCEEDED、FAILED、SKIPPED。

在 demo 配置下，将下例中的模板和素材 ID 替换为实际值，可指定某一步失败并设置各阶段耗时；`failAt` 可以省略或取 ASSET_CHECK、GENERATE、FINALIZE，耗时单位毫秒，不得超过 `TASK_MAX_DELAY_MS`。`simulation` 可以整体省略，使用配置默认值；prod 环境提供该字段会返回 400/SIMULATION_DISABLED。

```json
{
  "templateId": "实际模板ID",
  "assetIds": ["已上传素材ID"],
  "simulation": {
    "failAt": "GENERATE",
    "assetCheckDelayMs": 1000,
    "generateDelayMs": 6000,
    "finalizeDelayMs": 1000
  }
}
```

模板可能要求多张图片，应以模板接口的 `requiredImageCount` 为准。失败任务查询本身仍返回 200；请求异常与任务执行失败是不同情况。六张随包结果示例复用对应封面，生成过程复制预置结果图片，并不根据输入进行真实图片处理。详细容器演示见 [Task 4 记录](./task4-status.md)。


个人素材、任务查询和提交会携带 `X-Studio-User`，后端仍以 Cookie 身份为准；若请求中的页面身份与 Cookie 不同，返回 409/IDENTITY_CHANGED，阻止旧页面使用切换后的会话。该头只用于检测身份变更，不作为身份凭据；无该头的客户端仍按 Cookie 的实际用户隔离。
