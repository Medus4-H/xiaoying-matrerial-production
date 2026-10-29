package com.xiaoying.studio.task;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.identity.RequestIdentity;
import com.xiaoying.studio.task.execution.TaskFiles;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
  private final RequestIdentity identity;
  private final TaskSubmissionService submissions;
  private final TaskQueryService queries;
  private final TaskFiles files;

  public TaskController(
      RequestIdentity identity,
      TaskSubmissionService submissions,
      TaskQueryService queries,
      TaskFiles files) {
    this.identity = identity;
    this.submissions = submissions;
    this.queries = queries;
    this.files = files;
  }

  @PostMapping
  public ResponseEntity<TaskQueryService.TaskSnapshot> submit(
      @RequestBody TaskSubmissionService.CreateTaskRequest input, HttpServletRequest request)
      throws IOException {
    identity.checkOrigin(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .cacheControl(CacheControl.noStore())
        .body(submissions.submit(identity.resolve(request), input));
  }

  @GetMapping
  public ResponseEntity<TaskQueryService.TaskPage> history(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String templateId,
      HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.history(identity.resolve(request), page, pageSize, templateId));
  }

  @GetMapping("/active")
  public ResponseEntity<Map<String, ?>> active(HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("items", queries.active(identity.resolve(request))));
  }

  @GetMapping("/{id}")
  public ResponseEntity<TaskQueryService.TaskSnapshot> get(
      @PathVariable String id, HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.get(identity.resolve(request), id));
  }

  @GetMapping("/{id}/result")
  public ResponseEntity<Resource> result(@PathVariable String id, HttpServletRequest request)
      throws IOException {
    var task = queries.requireOwned(identity.resolve(request), id);
    if (!"SUCCEEDED".equals(task.status()) || task.resultKey() == null)
      throw new ApiException(ErrorKey.RESULT_NOT_FOUND);
    Resource resource;
    try {
      resource = files.read(task.resultKey());
    } catch (java.nio.file.NoSuchFileException e) {
      throw new ApiException(ErrorKey.RESULT_FILE_MISSING);
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.IMAGE_JPEG)
        .body(resource);
  }
}
