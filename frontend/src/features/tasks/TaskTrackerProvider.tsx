"use client";

import { useQueryClient } from "@tanstack/react-query";
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
} from "react";
import { useIdentity } from "@/components/identity/IdentityProvider";
import { appConfig } from "@/config/app";
import { getActiveTasks, getTask } from "@/lib/api/tasks";
import { TaskTracker, type TrackerState } from "./task-tracker";

const TrackerContext = createContext<TaskTracker | null>(null);
const emptyState: TrackerState = { tasks: {}, loading: false, loadError: null };
const emptySnapshot = () => emptyState;
const noSubscription = () => () => {};

export function TaskTrackerProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const identity = useIdentity();
  const client = useQueryClient();
  const userId =
    identity.status === "ready" && !identity.transitioning && !identity.checking
      ? identity.user?.id
      : undefined;
  const scope = userId ? `${userId}:${identity.epoch}` : null;
  const tracker = useMemo(() => {
    if (!scope || !userId) return null;
    return new TaskTracker({
      intervalMs: appConfig.taskPollIntervalMs,
      timeoutMs: appConfig.taskQueryTimeoutMs,
      listActive: (signal) => getActiveTasks(signal, userId),
      getTask: (id, signal) => getTask(id, signal, userId),
      onChanged: () => {
        void client.invalidateQueries({
          queryKey: ["tasks", userId, "history"],
        });
      },
    });
  }, [client, scope, userId]);
  useEffect(() => {
    if (!tracker) return;
    tracker.start();
    const updateVisibility = () =>
      tracker.setVisible(document.visibilityState !== "hidden");
    updateVisibility();
    document.addEventListener("visibilitychange", updateVisibility);
    return () => {
      document.removeEventListener("visibilitychange", updateVisibility);
      tracker.dispose();
    };
  }, [tracker]);
  return (
    <TrackerContext.Provider value={tracker}>
      {children}
    </TrackerContext.Provider>
  );
}

export function useTaskTracker() {
  const tracker = useContext(TrackerContext);
  const state = useSyncExternalStore(
    tracker?.subscribe ?? noSubscription,
    tracker?.snapshot ?? emptySnapshot,
    emptySnapshot,
  );
  return { tracker, state };
}
