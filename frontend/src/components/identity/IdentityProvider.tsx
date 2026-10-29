"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, useEffect, useRef, useState } from "react";
import { appConfig } from "@/config/app";
import {
  ApiError,
  identityChangedEvent,
  request,
  userScopeHeaders,
} from "@/lib/api/client";
import { getExpiryRefreshDelay, getExpiryState } from "@/lib/session-expiry";

export interface Identity {
  id: string;
  displayName: string;
  expiresAt: string;
  type?: "GUEST" | "ACCOUNT";
  email?: string | null;
  phone?: string | null;
}

type IdentityStatus = "loading" | "ready" | "expired" | "error";
interface IdentityContextValue {
  user: Identity | null;
  status: IdentityStatus;
  error: Error | null;
  retry: () => void;
  renew: () => void;
  renewing: boolean;
  renewalError: boolean;
  epoch: number;
  transitioning: boolean;
  checking: boolean;
  login: (identifier: string, password: string) => Promise<void>;
  register: (input: RegistrationRequest) => Promise<void>;
  bind: (input: ContactRequest) => Promise<void>;
  logout: () => Promise<void>;
}

export interface ContactRequest {
  channel: "email" | "phone";
  destination: string;
  code: string;
}

export interface RegistrationRequest extends ContactRequest {
  password: string;
  importGuest: boolean;
}

const IdentityContext = createContext<IdentityContextValue | null>(null);

function isUncertainIdentityError(error: unknown): boolean {
  return (
    !(error instanceof ApiError) ||
    error.status === 0 ||
    error.status >= 500 ||
    error.status === 408
  );
}

function broadcastIdentityChange() {
  try {
    localStorage.setItem(
      appConfig.identityChangeKey,
      `${Date.now()}-${Math.random()}`,
    );
  } catch {
    /* Focus revalidation still handles unavailable storage. */
  }
}

export function IdentityProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const [now, setNow] = useState(Date.now);
  const [epoch, setEpoch] = useState(0);
  const [transitioning, setTransitioning] = useState(false);
  const [checking, setChecking] = useState(false);
  const checkingRef = useRef(false);
  const pendingIdentityCheck = useRef(false);
  const switchingRef = useRef(false);
  const [identityUncertain, setIdentityUncertain] = useState(false);
  const identityGeneration = useRef(0);
  const identity = useQuery({
    queryKey: ["identity"],
    staleTime: Infinity,
    queryFn: async ({ signal }) => {
      try {
        return await request<Identity>(appConfig.api.me, { signal });
      } catch (error) {
        if (
          error instanceof ApiError &&
          error.status === 401 &&
          !signal.aborted
        ) {
          return request<Identity>(appConfig.api.session, {
            method: "POST",
            signal,
          });
        }
        throw error;
      }
    },
  });
  const renew = useMutation({
    mutationFn: async () => {
      switchingRef.current = true;
      identityGeneration.current += 1;
      setChecking(false);
      await queryClient.cancelQueries({ queryKey: ["identity"] });
      await queryClient.cancelQueries({
        predicate: (query) =>
          query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
      });
      return request<Identity>(appConfig.api.session, { method: "POST" });
    },
    onSuccess: (visitor) => {
      queryClient.removeQueries({
        predicate: (query) =>
          query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
      });
      queryClient.setQueryData(["identity"], visitor);
      setEpoch((previous) => previous + 1);
      setNow(Date.now());
      broadcastIdentityChange();
    },
    onSettled: () => {
      switchingRef.current = false;
      if (pendingIdentityCheck.current)
        window.dispatchEvent(new Event(identityChangedEvent));
    },
  });

  async function changeIdentity(action: () => Promise<Identity>) {
    switchingRef.current = true;
    identityGeneration.current += 1;
    setChecking(false);
    setTransitioning(true);
    try {
      await queryClient.cancelQueries({ queryKey: ["identity"] });
      await queryClient.cancelQueries({
        predicate: (query) =>
          query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
      });
      const next = await action();
      queryClient.removeQueries({
        predicate: (query) =>
          query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
      });
      queryClient.setQueryData(["identity"], next);
      setEpoch((previous) => previous + 1);
      setNow(Date.now());
      setIdentityUncertain(false);
      broadcastIdentityChange();
    } catch (error) {
      if (isUncertainIdentityError(error)) {
        queryClient.removeQueries({
          predicate: (query) =>
            query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
        });
        setIdentityUncertain(true);
        broadcastIdentityChange();
      }
      throw error;
    } finally {
      switchingRef.current = false;
      if (pendingIdentityCheck.current) {
        setChecking(true);
        window.dispatchEvent(new Event(identityChangedEvent));
      }
      setTransitioning(false);
    }
  }
  const post = <T,>(path: string, body: unknown, userId?: string) =>
    request<T>(path, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(userId ? userScopeHeaders(userId) : {}),
      },
      body: JSON.stringify(body),
    });
  useEffect(() => {
    if (!identity.data) return;
    const refreshClock = () => setNow(Date.now());
    const delay = getExpiryRefreshDelay(
      identity.data.expiresAt,
      now,
      Date.now(),
    );
    const timer =
      delay === null ? null : window.setTimeout(refreshClock, delay);
    window.addEventListener("focus", refreshClock);
    document.addEventListener("visibilitychange", refreshClock);
    return () => {
      if (timer !== null) window.clearTimeout(timer);
      window.removeEventListener("focus", refreshClock);
      document.removeEventListener("visibilitychange", refreshClock);
    };
  }, [identity.data, now]);

  useEffect(() => {
    let disposed = false;
    async function revalidateIdentity() {
      if (disposed || !queryClient.getQueryData<Identity>(["identity"])) return;
      if (switchingRef.current || checkingRef.current) {
        pendingIdentityCheck.current = true;
        return;
      }
      pendingIdentityCheck.current = false;
      checkingRef.current = true;
      setChecking(true);
      const generation = ++identityGeneration.current;
      try {
        await queryClient.cancelQueries({ queryKey: ["identity"] });
        const latest = await request<Identity>(appConfig.api.me);
        if (
          disposed ||
          generation !== identityGeneration.current ||
          pendingIdentityCheck.current
        )
          return;
        const previous = queryClient.getQueryData<Identity>(["identity"]);
        if (!previous || previous.id !== latest.id) {
          await queryClient.cancelQueries({
            predicate: (query) =>
              query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
          });
          if (
            disposed ||
            generation !== identityGeneration.current ||
            pendingIdentityCheck.current
          )
            return;
          queryClient.removeQueries({
            predicate: (query) =>
              query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
          });
          setEpoch((value) => value + 1);
        }
        queryClient.setQueryData(["identity"], latest);
        setNow(Date.now());
        setIdentityUncertain(false);
      } catch {
        if (!disposed && generation === identityGeneration.current) {
          await queryClient.cancelQueries({
            predicate: (query) =>
              query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
          });
          queryClient.removeQueries({
            predicate: (query) =>
              query.queryKey[0] === "assets" || query.queryKey[0] === "tasks",
          });
          setIdentityUncertain(true);
        }
      } finally {
        checkingRef.current = false;
        if (
          !disposed &&
          pendingIdentityCheck.current &&
          !switchingRef.current
        ) {
          void revalidateIdentity();
        } else if (!disposed && generation === identityGeneration.current) {
          setChecking(false);
        }
      }
    }
    const onFocus = () => {
      void revalidateIdentity();
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") void revalidateIdentity();
    };
    const onStorage = (event: StorageEvent) => {
      if (event.key === appConfig.identityChangeKey) void revalidateIdentity();
    };
    window.addEventListener("focus", onFocus);
    window.addEventListener("storage", onStorage);
    window.addEventListener(identityChangedEvent, onFocus);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      disposed = true;
      window.removeEventListener("focus", onFocus);
      window.removeEventListener("storage", onStorage);
      window.removeEventListener(identityChangedEvent, onFocus);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [queryClient]);

  const status: IdentityStatus = identityUncertain
    ? "error"
    : identity.isPending
      ? "loading"
      : identity.isError
        ? "error"
        : getExpiryState(identity.data.expiresAt, now).expired
          ? "expired"
          : "ready";
  const value: IdentityContextValue = {
    user: identityUncertain ? null : (identity.data ?? null),
    status,
    error: identity.error,
    retry: () => {
      identityGeneration.current += 1;
      void identity.refetch().then((result) => {
        if (!result.isError && result.data) {
          setIdentityUncertain(false);
          setEpoch((previous) => previous + 1);
          setNow(Date.now());
        }
      });
    },
    renew: () => renew.mutate(),
    renewing: renew.isPending,
    renewalError: renew.isError,
    epoch,
    transitioning,
    checking,
    login: (identifier, password) =>
      changeIdentity(() =>
        post<Identity>(appConfig.api.login, { identifier, password }),
      ),
    register: (input) =>
      changeIdentity(() =>
        post<Identity>(appConfig.api.register, input, identity.data?.id),
      ),
    bind: async (input) => {
      const generation = identityGeneration.current;
      const userId = identity.data?.id;
      await queryClient.cancelQueries({ queryKey: ["identity"] });
      try {
        const updated = await post<Identity>(appConfig.api.bind, input, userId);
        if (
          generation !== identityGeneration.current ||
          userId !== updated.id ||
          queryClient.getQueryData<Identity>(["identity"])?.id !== userId
        ) {
          throw new Error("当前身份已变化，请重新查看账号信息");
        }
        queryClient.setQueryData(["identity"], updated);
      } catch (error) {
        if (
          generation === identityGeneration.current &&
          isUncertainIdentityError(error)
        ) {
          void queryClient.invalidateQueries({ queryKey: ["identity"] });
        }
        throw error;
      }
    },
    logout: () =>
      changeIdentity(async () => {
        await request<void>(appConfig.api.logout, {
          method: "POST",
          headers: identity.data?.id ? userScopeHeaders(identity.data.id) : {},
        });
        try {
          return await request<Identity>(appConfig.api.session, {
            method: "POST",
          });
        } catch (error) {
          setIdentityUncertain(true);
          throw error;
        }
      }),
  };
  return (
    <IdentityContext.Provider value={value}>
      {children}
    </IdentityContext.Provider>
  );
}

export function useIdentity(): IdentityContextValue {
  const value = useContext(IdentityContext);
  if (!value) throw new Error("useIdentity requires IdentityProvider");
  return value;
}
