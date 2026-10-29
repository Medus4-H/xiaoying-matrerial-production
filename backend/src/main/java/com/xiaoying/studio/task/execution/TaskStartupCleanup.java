package com.xiaoying.studio.task.execution;

import com.xiaoying.studio.common.ErrorCatalog;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.common.SafeLog;
import com.xiaoying.studio.task.TaskMapper;
import com.xiaoying.studio.task.TaskStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public class TaskStartupCleanup implements SmartInitializingSingleton {
  private static final Logger log = LoggerFactory.getLogger(TaskStartupCleanup.class);
  private final TaskMapper mapper;
  private final TaskStateService state;

  public TaskStartupCleanup(TaskMapper mapper, TaskStateService state) {
    this.mapper = mapper;
    this.state = state;
  }

  @Override
  public void afterSingletonsInstantiated() {
    var unfinished = mapper.unfinished();
    if (!unfinished.isEmpty())
      SafeLog.write(() -> log.atWarn()
          .addKeyValue("event", "task.interrupted_on_startup")
          .addKeyValue("count", unfinished.size())
          .log("Unfinished tasks interrupted after restart"));
    for (String id : unfinished)
      state.fail(
          id,
          null,
          ErrorCatalog.instance().definition(ErrorKey.SERVER_RESTARTED).code(),
          ErrorCatalog.instance().message(ErrorKey.SERVER_RESTARTED, ErrorCatalog.DEFAULT_LOCALE),
          ErrorCatalog.instance().definition(ErrorKey.SERVER_RESTARTED).key(),
          ErrorKey.SERVER_RESTARTED.name());
  }
}
