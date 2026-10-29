"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { TaskTrackerProvider } from "@/features/tasks/TaskTrackerProvider";
import { IdentityProvider } from "@/components/identity/IdentityProvider";

export function AppProviders({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: false, refetchOnWindowFocus: false },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <IdentityProvider>
        <TaskTrackerProvider>{children}</TaskTrackerProvider>
      </IdentityProvider>
    </QueryClientProvider>
  );
}
