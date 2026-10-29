import { appConfig } from "@/config/app";
import { request, userScopeHeaders } from "./client";
import type { CreateTaskRequest, TaskSnapshot } from "./types";

export function createTask(
  input: CreateTaskRequest,
  signal: AbortSignal,
  userId: string,
): Promise<TaskSnapshot> {
  return request<TaskSnapshot>(appConfig.api.tasks, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...userScopeHeaders(userId),
    },
    body: JSON.stringify(input),
    signal,
  });
}

export function getTask(
  id: string,
  signal: AbortSignal,
  userId: string,
): Promise<TaskSnapshot> {
  return request<TaskSnapshot>(
    `${appConfig.api.tasks}/${encodeURIComponent(id)}`,
    { signal, headers: userScopeHeaders(userId) },
  );
}

export async function getActiveTasks(
  signal: AbortSignal,
  userId: string,
): Promise<TaskSnapshot[]> {
  const result = await request<{ items: TaskSnapshot[] }>(
    `${appConfig.api.tasks}/active`,
    { signal, headers: userScopeHeaders(userId) },
  );
  return result.items;
}

export function getTaskHistory(
  page: number,
  pageSize: number,
  templateId: string | undefined,
  signal: AbortSignal,
  userId: string,
): Promise<import("./types").TaskPage> {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  });
  if (templateId) params.set("templateId", templateId);
  return request(`${appConfig.api.tasks}?${params}`, {
    signal,
    headers: userScopeHeaders(userId),
  });
}
