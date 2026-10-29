# 素材来源

## 模板封面

`backend/src/main/resources/demo/covers/template-1..6.jpg` 为演示用真实照片，取自 Pexels，按 Pexels License 使用（可免费用于商业与非商业用途，无需署名；不得原样转售，不得暗示模特或作者为本站背书）。未复用 vergeX 品牌或私有素材。

| 文件 | 模板 | 内容 | 原始地址 |
| --- | --- | --- | --- |
| template-1.jpg | demo-1 旅行明信片 | 亚得里亚海边的古镇与钟楼 | https://www.pexels.com/photo/seaside-old-town-7588140/ |
| template-2.jpg | demo-2 双人纪念 | 树荫下的两人合影 | https://www.pexels.com/photo/26574876/ |
| template-3.jpg | demo-3 宠物日记 | 室内仰视的金棕色长毛犬 | https://www.pexels.com/photo/31255875/ |
| template-4.jpg | demo-4 肖像画册 | 米色背景前的女性肖像 | https://www.pexels.com/photo/7506928/ |
| template-5.jpg | demo-5 旅途故事 | 公路上背着行囊的旅人 | https://www.pexels.com/photo/9088756/ |
| template-6.jpg | demo-6 生活拼贴 | 木桌上咖啡、杂志与毛衣的日常场景 | https://www.pexels.com/photo/6802981/ |

处理方式：通过 CDN 参数 `w=1000&h=1250&fit=crop` 统一裁成 4:5，与列表卡片 `aspect-ratio: 4/5` 一致；未做其它后期。封面只用于公开模板展示，不改变上传仍只接受 JPEG/PNG 的约定。正式对外使用时请自行复核各图授权状态。

## 预置结果图

`results/template-*.jpg` 复用上述对应封面，随包提供给模拟生成流程；许可与来源与对应封面相同。V4 迁移修正示例结果路径，V2、V3 保持不变。当前后台会将预置图整理为任务结果，并提供仅所属用户可访问的结果接口；结果不会根据上传图片进行真实 AI 处理。

Maven Wrapper 启动脚本来源：[Apache Maven Wrapper 3.3.4](https://github.com/apache/maven-wrapper/tree/maven-wrapper-3.3.4)，保留原始 Apache 2.0 许可证声明。

`demo-assets/uploads/example.jpg` 为 `template-1.jpg` 的副本，用于本地上传验证，适用同一来源及许可；不是用户上传数据。
