import type { StepStatus, TaskStage, TaskStatus } from "@/lib/api/types";

export const stageNames: Record<TaskStage, string> = {
  ASSET_CHECK: "素材检查",
  GENERATE: "模拟生成",
  FINALIZE: "结果整理",
};

export const statusNames: Record<TaskStatus, string> = {
  QUEUED: "等待处理",
  RUNNING: "处理中",
  SUCCEEDED: "制作成功",
  FAILED: "制作失败",
};

export const stepNames: Record<StepStatus, string> = {
  PENDING: "等待",
  RUNNING: "处理中",
  SUCCEEDED: "完成",
  FAILED: "失败",
  SKIPPED: "已跳过",
};
