import { TemplateDetails } from "@/components/templates/TemplateDetails";

export default async function TemplatePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <TemplateDetails id={id} />;
}
