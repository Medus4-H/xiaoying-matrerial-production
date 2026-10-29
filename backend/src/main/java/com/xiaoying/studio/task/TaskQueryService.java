package com.xiaoying.studio.task;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorCatalog;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.identity.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class TaskQueryService {
  private final TaskMapper mapper;

  public TaskQueryService(TaskMapper mapper) {
    this.mapper = mapper;
  }

  public TaskRow requireOwned(User user, String id) {
    TaskRow row = id == null ? null : mapper.findOwned(id, user.id());
    if (row == null) throw new ApiException(ErrorKey.TASK_NOT_FOUND);
    return row;
  }

  public TaskSnapshot get(User user, String id) {
    return snapshot(requireOwned(user, id));
  }

  public List<TaskSnapshot> active(User user) {
    return mapper.active(user.id()).stream().map(this::snapshot).toList();
  }

  public TaskPage history(User user, int page, int pageSize, String templateId) {
    if (page < 1 || pageSize < 1 || pageSize > 50) throw new ApiException(ErrorKey.INVALID_PAGE);
    String filter = templateId == null || templateId.isBlank() ? null : templateId;
    long offset = ((long) page - 1) * pageSize;
    return new TaskPage(
        mapper.history(user.id(), filter, pageSize, offset).stream().map(this::snapshot).toList(),
        page,
        pageSize,
        mapper.count(user.id(), filter));
  }

  private TaskSnapshot snapshot(TaskRow row) {
    String language = language();
    String result =
        "SUCCEEDED".equals(row.status()) && row.resultKey() != null
            ? "/api/tasks/" + row.id() + "/result"
            : null;
    return new TaskSnapshot(
        row.id(),
        row.templateId(),
        row.templateTitleSnapshot(),
        row.status(),
        row.currentStage(),
        row.failedStage(),
        row.errorCode(),
        publicMessage(row.errorCode(), row.errorMessageKey(), row.errorMessage(), language),
        publicKey(row.errorCode(), row.errorMessageKey()),
        result,
        row.createdAt(),
        row.startedAt(),
        row.finishedAt(),
        row.updatedAt(),
        mapper.steps(row.id()).stream().map(step -> localized(step, language)).toList());
  }

  private String language() {
    var attributes = RequestContextHolder.getRequestAttributes();
    return attributes instanceof ServletRequestAttributes request
        ? request.getRequest().getHeader("Accept-Language")
        : null;
  }

  private String publicKey(String code, String key) {
    return ErrorCatalog.instance().publicKey(code, key) == null ? null : key;
  }

  private String publicMessage(String code, String key, String legacy, String language) {
    if (code == null) return null;
    var catalog = ErrorCatalog.instance();
    ErrorKey resolved = catalog.publicKey(code, key);
    if (resolved != null) return catalog.message(resolved, language);
    if (catalog.isSafeLegacyMessage(legacy)) return legacy;
    return catalog.message(ErrorKey.EXECUTION_ERROR, language);
  }

  private StepRow localized(StepRow step, String language) {
    return new StepRow(
        step.stage(),
        step.sequence(),
        step.status(),
        step.startedAt(),
        step.finishedAt(),
        step.errorCode(),
        publicMessage(step.errorCode(), step.errorMessageKey(), step.errorMessage(), language),
        publicKey(step.errorCode(), step.errorMessageKey()),
        null,
        null);
  }

  public record TaskSnapshot(
      String id,
      String templateId,
      String templateTitle,
      String status,
      String currentStage,
      String failedStage,
      String errorCode,
      String errorMessage,
      String errorMessageKey,
      String resultUrl,
      java.time.Instant createdAt,
      java.time.Instant startedAt,
      java.time.Instant finishedAt,
      java.time.Instant updatedAt,
      List<StepRow> steps) {}

  public record TaskPage(List<TaskSnapshot> items, int page, int pageSize, long total) {}
}
