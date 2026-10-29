package com.xiaoying.studio.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.asset.AssetService;
import com.xiaoying.studio.asset.TemplateRequirements;
import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorCatalog;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.common.SafeLog;
import com.xiaoying.studio.config.StudioProperties;
import com.xiaoying.studio.identity.User;
import com.xiaoying.studio.task.execution.TaskRunner;
import com.xiaoying.studio.template.TemplateService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskSubmissionService {
  private static final Logger log = LoggerFactory.getLogger(TaskSubmissionService.class);
  private final TaskMapper mapper;
  private final TemplateService templates;
  private final AssetService assets;
  private final TaskQueryService queries;
  private final TaskStateService state;
  private final TaskRunner runner;
  private final ThreadPoolTaskExecutor executor;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final TaskProperties defaults;
  private final StudioProperties config;

  public TaskSubmissionService(
      TaskMapper mapper,
      TemplateService templates,
      AssetService assets,
      TaskQueryService queries,
      TaskStateService state,
      TaskRunner runner,
      ThreadPoolTaskExecutor executor,
      TransactionTemplate tx,
      ObjectMapper json,
      TaskProperties defaults,
      StudioProperties config) {
    this.mapper = mapper;
    this.templates = templates;
    this.assets = assets;
    this.queries = queries;
    this.state = state;
    this.runner = runner;
    this.executor = executor;
    this.tx = tx;
    this.json = json;
    this.defaults = defaults;
    this.config = config;
  }

  public TaskQueryService.TaskSnapshot submit(User user, CreateTaskRequest input)
      throws IOException {
    if (input == null
        || input.templateId() == null
        || input.templateId().isBlank()
        || input.assetIds() == null) throw new ApiException(ErrorKey.INVALID_TASK);
    var simulation = simulation(input.simulation());
    var template = templates.get(input.templateId());
    var requirements =
        new TemplateRequirements(
            template.requiredImageCount(),
            Set.copyOf(template.acceptedMimeTypes()),
            template.maxFileSizeBytes());
    assets.requireOwnedAssets(user, input.assetIds(), requirements);
    String preset = mapper.preset(template.id());
    if (preset == null || !preset.matches("results/[A-Za-z0-9_-]+\\.jpg"))
      throw new ApiException(ErrorKey.INVALID_TEMPLATE);
    var snapshot =
        new TaskConfiguration.Requirements(
            template.requiredImageCount(),
            template.acceptedMimeTypes(),
            template.maxFileSizeBytes(),
            preset);
    String id = UUID.randomUUID().toString();
    TaskRow row =
        new TaskRow(
            id,
            user.id(),
            template.id(),
            template.title(),
            encode(snapshot),
            "QUEUED",
            null,
            null,
            null,
            null,
            null,
            encode(simulation),
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    tx.executeWithoutResult(
        status -> {
          mapper.insert(row);
          for (int i = 0; i < input.assetIds().size(); i++)
            mapper.insertAsset(id, input.assetIds().get(i), i);
          TaskStage[] stages = TaskStage.values();
          for (int i = 0; i < stages.length; i++) mapper.insertStep(id, stages[i], i + 1);
        });
    SafeLog.write(
        () ->
            log.atInfo()
                .addKeyValue("event", "task.created")
                .addKeyValue("taskId", id)
                .log("Task record created"));
    try {
      long queuedAt = System.nanoTime();
      executor.execute(() -> runner.run(id, queuedAt));
      SafeLog.write(
          () ->
              log.atInfo()
                  .addKeyValue("event", "task.dispatched")
                  .addKeyValue("taskId", id)
                  .addKeyValue("queueDepth", executor.getQueueSize())
                  .addKeyValue("activeThreads", executor.getActiveCount())
                  .log("Task dispatched"));
    } catch (java.util.concurrent.RejectedExecutionException e) {
      SafeLog.write(
          () ->
              log.atWarn()
                  .addKeyValue("event", "task.dispatch.rejected")
                  .addKeyValue("taskId", id)
                  .addKeyValue("queueDepth", executor.getQueueSize())
                  .log("Task dispatch rejected"));
      state.fail(
          id,
          null,
          ErrorCatalog.instance().definition(ErrorKey.DISPATCH_REJECTED).code(),
          ErrorCatalog.instance().message(ErrorKey.DISPATCH_REJECTED, ErrorCatalog.DEFAULT_LOCALE),
          ErrorCatalog.instance().definition(ErrorKey.DISPATCH_REJECTED).key(),
          ErrorKey.DISPATCH_REJECTED.name());
    }
    return queries.get(user, id);
  }

  private TaskConfiguration.Simulation simulation(TaskConfiguration.SimulationInput input) {
    if (input != null && !config.simulationEnabled())
      throw new ApiException(ErrorKey.SIMULATION_DISABLED);
    long a = delay(input == null ? null : input.assetCheckDelayMs(), defaults.assetCheckDelayMs());
    long b = delay(input == null ? null : input.generateDelayMs(), defaults.generateDelayMs());
    long c = delay(input == null ? null : input.finalizeDelayMs(), defaults.finalizeDelayMs());
    return new TaskConfiguration.Simulation(input == null ? null : input.failAt(), a, b, c);
  }

  private long delay(Long input, long fallback) {
    long value = input == null ? fallback : input;
    if (value < 0 || value > defaults.maxDelayMs())
      throw new ApiException(ErrorKey.INVALID_SIMULATION);
    return value;
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize task snapshot", e);
    }
  }

  public record CreateTaskRequest(
      String templateId, List<String> assetIds, TaskConfiguration.SimulationInput simulation) {}
}
