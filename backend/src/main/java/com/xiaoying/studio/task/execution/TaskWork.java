package com.xiaoying.studio.task.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.asset.AssetService;
import com.xiaoying.studio.asset.TemplateRequirements;
import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.identity.User;
import com.xiaoying.studio.task.*;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TaskWork {
  private final TaskMapper mapper;
  private final AssetService assets;
  private final TaskFiles files;
  private final ObjectMapper json;

  public TaskWork(TaskMapper mapper, AssetService assets, TaskFiles files, ObjectMapper json) {
    this.mapper = mapper;
    this.assets = assets;
    this.files = files;
    this.json = json;
  }

  public String execute(String id, TaskStage stage) throws Exception {
    TaskRow task = mapper.find(id);
    if (task == null) throw new TaskExecutionException(ErrorKey.TASK_MISSING);
    var simulation = json.readValue(task.simulationSnapshot(), TaskConfiguration.Simulation.class);
    var requirements =
        json.readValue(task.requirementsSnapshot(), TaskConfiguration.Requirements.class);
    Thread.sleep(simulation.delay(stage));
    if (stage == simulation.failAt()) throw new TaskExecutionException(simulatedFailure(stage));
    return switch (stage) {
      case ASSET_CHECK -> {
        try {
          var owner = new User(task.userId(), "", Instant.EPOCH, Instant.MAX);
          assets.requireOwnedAssets(
              owner,
              mapper.assets(id),
              new TemplateRequirements(
                  requirements.requiredImageCount(),
                  Set.copyOf(requirements.acceptedMimeTypes()),
                  requirements.maxFileSizeBytes()));
        } catch (ApiException e) {
          throw new TaskExecutionException(assetFailureKey(e.key()), e);
        }
        yield null;
      }
      case GENERATE -> {
        files.generate(id, requirements.presetResultKey());
        yield null;
      }
      case FINALIZE -> files.publish(id);
    };
  }

  public void cleanup(String id) throws Exception {
    files.cleanup(id);
  }

  private ErrorKey assetFailureKey(ErrorKey key) {
    return switch (key) {
      case ASSET_NOT_FOUND -> ErrorKey.ASSET_CHECK_MISSING;
      case ASSET_COUNT -> ErrorKey.ASSET_CHECK_COUNT;
      case ASSET_REQUIREMENTS -> ErrorKey.ASSET_CHECK_REQUIREMENTS;
      default -> ErrorKey.ASSET_CHECK_FAILED;
    };
  }

  private ErrorKey simulatedFailure(TaskStage stage) {
    return switch (stage) {
      case ASSET_CHECK -> ErrorKey.SIMULATED_ASSET_CHECK;
      case GENERATE -> ErrorKey.SIMULATED_GENERATE;
      case FINALIZE -> ErrorKey.SIMULATED_FINALIZE;
    };
  }
}
