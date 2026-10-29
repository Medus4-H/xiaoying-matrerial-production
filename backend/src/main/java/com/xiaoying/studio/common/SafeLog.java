package com.xiaoying.studio.common;

/** Keep observability failures from changing request or task outcomes. */
public final class SafeLog {
  private SafeLog() {}

  public static void write(Runnable operation) {
    try {
      operation.run();
    } catch (RuntimeException ignored) {
      // Logging is best-effort; never recurse into a failing logging subsystem.
    }
  }
}
