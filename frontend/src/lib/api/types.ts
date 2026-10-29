export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
  requestId: string;
  messageKey?: string;
}

export type TaskStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type TaskStage = "ASSET_CHECK" | "GENERATE" | "FINALIZE";
export type StepStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "SKIPPED";

export interface Items<T> {
  items: T[];
}

export interface TemplateDetail {
  id: string;
  title: string;
  categoryId: string;
  categoryName: string;
  description: string;
  coverUrl: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
}

export interface AssetResponse {
  id: string;
  previewUrl: string;
  mimeType: string;
  sizeBytes: number;
  width: number;
  height: number;
}

export interface CreateTaskRequest {
  templateId: string;
  assetIds: string[];
  simulation?: { failAt?: TaskStage };
}

export interface TaskSnapshot {
  id: string;
  templateId: string;
  templateTitle: string;
  status: TaskStatus;
  currentStage: TaskStage | null;
  failedStage: TaskStage | null;
  errorCode: string | null;
  errorMessage: string | null;
  resultUrl: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  updatedAt: string;
  steps: Array<{
    stage: TaskStage;
    sequence: number;
    status: StepStatus;
    startedAt: string | null;
    finishedAt: string | null;
    errorCode: string | null;
    errorMessage: string | null;
  }>;
}

export interface TaskPage {
  items: TaskSnapshot[];
  page: number;
  pageSize: number;
  total: number;
}
