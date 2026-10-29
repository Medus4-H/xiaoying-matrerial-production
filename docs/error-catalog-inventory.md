# 阶段 A：错误码盘点与兼容契约

日期：2026-10-29。当前目录为阶段 B 实现的公开错误文案来源；后台 detail 的采集和持久化属于阶段 C。

| 领域 | 现有公开错误码 | 兼容与安全策略 |
| --- | --- | --- |
| 会话和账号 | `SESSION_REQUIRED`、`IDENTITY_CHANGED`、`LOGIN_FAILED`、`INVALID_ACCOUNT_INPUT`、`ACCOUNT_EXISTS` 等 | 保留原码；登录失败对外不区分账号不存在或密码错误。 |
| 模板与素材 | `TEMPLATE_NOT_FOUND`、`COVER_NOT_FOUND`、`INVALID_ASSET`、`ASSET_TOO_LARGE`、`ASSET_NOT_FOUND` 等 | 相同旧码对应多种提示时使用不同的文案键；无权限与不存在统一响应。 |
| 任务与结果 | `INVALID_TASK`、`INVALID_SIMULATION`、`TASK_NOT_FOUND`、`RESULT_NOT_FOUND` 等 | 保留原码和状态；后台任务仍写入兼容的中文失败文案，历史多语言字段属于阶段 C。 |
| 通用与框架 | `INVALID_REQUEST`、`INTERNAL_ERROR`、`HTTP_<状态>` | 保留原响应结构及状态；不向用户返回内部异常。 |

完整 code、HTTP 状态、公开 `messageKey` 和中英文文案以 `backend/src/main/resources/error-catalog.json` 为准。同一码允许对应不同安全文案键；文案键必须唯一。内部 detail 尚未实现，不能由个人任务接口返回。

Java 的 `ErrorKey` 与目录启动时做一致性检查；前端控制流常量由容器检查和 CI 与目录核对。目录中目前所有参数列表为空，不能插入用户输入或敏感信息。`NEXT_PUBLIC_ERROR_LOCALE` 默认 `zh-CN`，可设 `en`，更改后需要重建前端镜像；这只影响错误文案，不是全站语言切换。

主目录不可读取、损坏或校验失败时使用随镜像提供的备用目录；`error-catalog-check` 检查两者一致，修改主目录时需要同步备用目录。两份目录均不可用时安全停止启动，避免返回不受控的错误码或异常内容。

Java 按 `Accept-Language` 选择中文或英文，非法和不支持的语言回退中文。`GET /api/i18n/errors?locale=en` 返回只读公开语言包。错误响应保留 `code`、`message`、`fieldErrors`、`requestId` 并增加 `messageKey`；当前没有参数插值，故不返回 `messageArgs`。

前端优先按语言包的文案键查找，查找失败使用安全服务端文案；无响应时使用通用提示。阶段 B 保留后端返回的请求编号，但请求编号与日志的统一关联属于阶段 C，不保证每个编号都能检索到日志。
