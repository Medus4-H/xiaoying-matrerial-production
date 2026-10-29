import { TaskDetail } from "@/components/tasks/TaskDetail";

export default async function TaskPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <TaskDetail id={id} />;
}
