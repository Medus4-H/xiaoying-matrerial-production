import { useState } from "react";
import type { TaskSnapshot } from "@/lib/api/types";
import { stageNames, stepNames } from "./task-labels";

export function WorkspacePreview({
  coverUrl,
  task,
  queryError,
  showExample = false,
  onToggleExample,
  onResume,
}: {
  coverUrl: string;
  task?: TaskSnapshot;
  queryError?: string | null;
  showExample?: boolean;
  onToggleExample?: () => void;
  onResume?: () => void;
}) {
  const [failedImage, setFailedImage] = useState<string | null>(null);
  const [label, detail] =
    !task || showExample
      ? ["模板示例", "上传素材并开始制作，即可在这里查看任务状态和结果。"]
      : task.status === "QUEUED"
        ? ["等待处理", "任务已受理，正在等待后台处理。"]
        : task.status === "RUNNING"
          ? [
              "制作中",
              task.currentStage
                ? `当前阶段：${stageNames[task.currentStage]}`
                : "正在准备任务。",
            ]
          : task.status === "FAILED"
            ? [
                "制作失败",
                `${task.failedStage ? `${stageNames[task.failedStage]}：` : ""}${task.errorMessage || "任务处理失败"}`,
              ]
            : ["制作完成", "这里展示本次任务的预置演示结果。"];
  const image =
    !task || showExample
      ? coverUrl
      : task.status === "SUCCEEDED"
        ? task.resultUrl
        : null;
  return (
    <section className="workspace-preview" aria-label="作品预览">
      <div className="workspace-preview-heading">
        <h2>作品预览</h2>
        <span>{label}</span>
        {onToggleExample && (
          <button type="button" onClick={onToggleExample}>
            {showExample ? "返回任务" : "查看模板示例"}
          </button>
        )}
      </div>
      <div className="workspace-preview-canvas">
        {image && failedImage !== image ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={image}
            alt={task && !showExample ? "制作结果" : "模板示例图片"}
            onError={() => setFailedImage(image)}
          />
        ) : image ? (
          <p role="alert">图片暂时无法加载，请稍后重试。</p>
        ) : (
          <div className="workspace-preview-message" role="status">
            <strong>{label}</strong>
            <p>{detail}</p>
          </div>
        )}
      </div>
      {image && <p>{detail}</p>}
      {!showExample && !!task?.steps.length && (
        <ol className="preview-steps" aria-label="处理步骤">
          {task.steps.map((step) => (
            <li key={step.stage}>
              {stageNames[step.stage]}：{stepNames[step.status]}
            </li>
          ))}
        </ol>
      )}
      {queryError && (
        <div className="preview-query-error" role="alert">
          <p>状态查询暂时失败，显示上次记录。</p>
          {onResume && (
            <button type="button" onClick={onResume}>
              恢复查询
            </button>
          )}
        </div>
      )}
      <p className="simulation-note">
        结果为预置演示图片，不会根据上传素材生成。
      </p>
    </section>
  );
}
