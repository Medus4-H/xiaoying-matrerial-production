package com.xiaoying.studio.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@ExtendWith(OutputCaptureExtension.class)
class TaskApiIT {
  private static final Path STORAGE = createStorage();
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TaskMapper mapper;
  @Autowired TaskStateService state;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired com.xiaoying.studio.task.execution.TaskStartupCleanup cleanup;
  private Cookie user;
  private String assetId;

  private static Path createStorage() {
    try {
      return Files.createTempDirectory("studio-task-test-");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry r) {
    r.add("studio.storage-root", STORAGE::toString);
  }

  @AfterAll
  static void cleanupFiles() throws IOException {
    try (var paths = Files.walk(STORAGE)) {
      for (var p : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.deleteIfExists(p);
    }
  }

  @BeforeEach
  void createAsset() throws Exception {
    user = visitor();
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var file = new MockMultipartFile("file", "image.png", "image/png", bytes.toByteArray());
    String body =
        mvc.perform(multipart("/api/assets").file(file).cookie(user))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assetId = json.readTree(body).path("id").asText();
  }

  private Cookie visitor() throws Exception {
    return mvc.perform(post("/api/session"))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getCookie("studio_session");
  }

  private JsonNode create(String failAt, int delay) throws Exception {
    var simulation = new java.util.HashMap<String, Object>();
    simulation.put("assetCheckDelayMs", delay);
    simulation.put("generateDelayMs", delay);
    simulation.put("finalizeDelayMs", delay);
    if (failAt != null) simulation.put("failAt", failAt);
    var request =
        Map.of("templateId", "demo-1", "assetIds", List.of(assetId), "simulation", simulation);
    String body =
        mvc.perform(
                post("/api/tasks")
                    .cookie(user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(request)))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private JsonNode getTask(Cookie visitor, String id) throws Exception {
    String body =
        mvc.perform(get("/api/tasks/{id}", id).cookie(visitor))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private JsonNode finished(String id) throws Exception {
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(12).toNanos();
    while (System.nanoTime() < deadline) {
      var data = getTask(user, id);
      if (List.of("SUCCEEDED", "FAILED").contains(data.path("status").asText())) return data;
      Thread.sleep(30);
    }
    throw new AssertionError("Task did not finish: " + id);
  }

  @Test
  void successIsAsyncHasOrderedStagesResultAndUserIsolation() throws Exception {
    long started = System.nanoTime();
    JsonNode accepted = create(null, 1000);
    assertThat(java.time.Duration.ofNanos(System.nanoTime() - started))
        .isLessThan(java.time.Duration.ofSeconds(2));
    assertThat(accepted.path("status").asText()).isIn("QUEUED", "RUNNING");
    String id = accepted.path("id").asText();
    mvc.perform(get("/api/tasks/active").cookie(user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(id));
    var other = visitor();
    mvc.perform(get("/api/tasks/{id}", id).cookie(other)).andExpect(status().isNotFound());
    mvc.perform(get("/api/tasks").cookie(other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    var completed = finished(id);
    assertThat(completed.path("status").asText()).isEqualTo("SUCCEEDED");
    for (int i = 0; i < 3; i++) {
      assertThat(completed.path("steps").get(i).path("stage").asText())
          .isEqualTo(TaskStage.values()[i].name());
      assertThat(completed.path("steps").get(i).path("status").asText()).isEqualTo("SUCCEEDED");
    }
    String result = completed.path("resultUrl").asText();
    mvc.perform(get(result).cookie(user))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG));
    mvc.perform(get(result).cookie(other)).andExpect(status().isNotFound());
    mvc.perform(get("/api/tasks").cookie(user).param("templateId", "demo-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    assertThat(getTask(user, id).path("status").asText()).isEqualTo("SUCCEEDED");
    mvc.perform(get("/api/tasks/active").cookie(user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void eachStageCanFailAndSkipsSubsequentSteps() throws Exception {
    for (TaskStage stage : TaskStage.values()) {
      var accepted = create(stage.name(), 25);
      var result = finished(accepted.path("id").asText());
      assertThat(result.path("status").asText()).isEqualTo("FAILED");
      assertThat(result.path("failedStage").asText()).isEqualTo(stage.name());
      assertThat(result.path("errorMessage").asText()).contains("模拟失败");
      for (int i = 0; i < 3; i++)
        assertThat(result.path("steps").get(i).path("status").asText())
            .isEqualTo(
                i < stage.ordinal() ? "SUCCEEDED" : i == stage.ordinal() ? "FAILED" : "SKIPPED");
      mvc.perform(get("/api/tasks/{id}/result", accepted.path("id").asText()).cookie(user))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  void failurePersistsInternalReasonAndReturnsLocalizedSafeMessages() throws Exception {
    String id = create("GENERATE", 20).path("id").asText();
    finished(id);
    mvc.perform(get("/api/tasks/{id}", id).cookie(user).header("Accept-Language", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errorCode").value("SIMULATED_FAILURE"))
        .andExpect(jsonPath("$.errorMessageKey").value("task.simulated_generate"))
        .andExpect(jsonPath("$.errorMessage").value("Simulated failure: generation."))
        .andExpect(jsonPath("$.steps[1].errorMessageKey").value("task.simulated_generate"))
        .andExpect(jsonPath("$.steps[1].errorMessage").value("Simulated failure: generation."))
        .andExpect(jsonPath("$.errorDetail").doesNotExist())
        .andExpect(jsonPath("$.steps[1].errorDetail").doesNotExist());
    assertThat(jdbc.queryForObject("SELECT error_detail FROM tasks WHERE id=?", String.class, id))
        .isEqualTo("SIMULATED_GENERATE");
    assertThat(
            jdbc.queryForObject(
                "SELECT error_detail FROM task_steps WHERE task_id=? AND stage='GENERATE'",
                String.class,
                id))
        .isEqualTo("SIMULATED_GENERATE");
    mvc.perform(get("/api/tasks").cookie(user).header("Accept-Language", "en"))
        .andExpect(jsonPath("$.items[0].errorMessage").value("Simulated failure: generation."));
    mvc.perform(get("/api/tasks/{id}", id).cookie(visitor())).andExpect(status().isNotFound());
  }

  @Test
  void missingAssetAfterAcceptanceStoresSpecificInternalReasonAndSafeLocalizedMessage()
      throws Exception {
    String id = create(null, 300).path("id").asText();
    String key =
        jdbc.queryForObject("SELECT storage_key FROM assets WHERE id=?", String.class, assetId);
    Files.delete(STORAGE.resolve(key));
    finished(id);
    mvc.perform(get("/api/tasks/{id}", id).cookie(user).header("Accept-Language", "en"))
        .andExpect(jsonPath("$.errorCode").value("ASSET_CHECK_FAILED"))
        .andExpect(jsonPath("$.errorMessage").value("Asset validation failed: asset not found."))
        .andExpect(jsonPath("$.errorDetail").doesNotExist());
    assertThat(jdbc.queryForObject("SELECT error_detail FROM tasks WHERE id=?", String.class, id))
        .isEqualTo("ASSET_FILE_MISSING");
  }

  @Test
  void legacyTaskMessagesNeverExposeUntrustedDatabaseText() throws Exception {
    String id = create("GENERATE", 20).path("id").asText();
    finished(id);
    jdbc.update(
        "UPDATE tasks SET error_message_key=NULL,error_message=? WHERE id=?",
        "secret@example.test /private/secret",
        id);
    mvc.perform(get("/api/tasks/{id}", id).cookie(user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errorMessage").value("后台处理被中断或发生错误"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret@example.test"))));
    jdbc.update("UPDATE tasks SET error_message=? WHERE id=?", "模拟失败：模拟生成", id);
    mvc.perform(get("/api/tasks/{id}", id).cookie(user))
        .andExpect(jsonPath("$.errorMessage").value("模拟失败：模拟生成"));
  }

  @Test
  void submissionAndWorkerLogsCanBeJoinedByTaskId(CapturedOutput output) throws Exception {
    String id = create(null, 20).path("id").asText();
    finished(id);
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
    while (!output.getAll().contains("\"event\":\"task.succeeded\"")
        && System.nanoTime() < deadline) Thread.sleep(20);
    var events = new java.util.ArrayList<JsonNode>();
    for (String line : output.getAll().split("\\R")) {
      if (!line.startsWith("{")) continue;
      JsonNode event = json.readTree(line);
      if (id.equals(event.path("taskId").asText())) events.add(event);
    }
    assertThat(
            events.stream()
                .anyMatch(
                    event ->
                        event.path("event").asText().equals("task.created")
                            && !event.path("requestId").asText().isBlank()))
        .isTrue();
    assertThat(
            events.stream()
                .filter(event -> event.path("event").asText().equals("task.stage.started"))
                .count())
        .isEqualTo(3);
    assertThat(
            events.stream()
                .anyMatch(event -> event.path("event").asText().equals("task.succeeded")))
        .isTrue();
  }

  @Test
  void tasksAreIndependentAndInputsAreValidated() throws Exception {
    var a = create(null, 200);
    var b = create("GENERATE", 200);
    var doneA = finished(a.path("id").asText());
    var doneB = finished(b.path("id").asText());
    assertThat(doneA.path("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(doneB.path("status").asText()).isEqualTo("FAILED");
    var other = visitor();
    var request = Map.of("templateId", "demo-1", "assetIds", List.of(assetId));
    mvc.perform(
            post("/api/tasks")
                .cookie(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)))
        .andExpect(status().isNotFound());
    var invalid =
        Map.of(
            "templateId",
            "demo-1",
            "assetIds",
            List.of(assetId),
            "simulation",
            Map.of("generateDelayMs", 30001));
    mvc.perform(
            post("/api/tasks")
                .cookie(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(invalid)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/tasks")
                .cookie(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\":\"demo-1\",\"assetIds\":[],\"unknown\":1}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/tasks").cookie(user).param("pageSize", "51"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void restartMarksOldTasksInterruptedWithoutRestartingThem() throws Exception {
    var accepted = create(null, 1000);
    String id = accepted.path("id").asText();
    // Wait for worker to finish; create a separate stale queued record with the same
    // owner/template.
    finished(id);
    TaskRow original = mapper.find(id);
    String stale = java.util.UUID.randomUUID().toString();
    mapper.insert(
        new TaskRow(
            stale,
            original.userId(),
            original.templateId(),
            original.templateTitleSnapshot(),
            original.requirementsSnapshot(),
            "QUEUED",
            null,
            null,
            null,
            null,
            null,
            original.simulationSnapshot(),
            null,
            null,
            null,
            null,
            null,
            null,
            null));
    for (int i = 0; i < 3; i++) mapper.insertStep(stale, TaskStage.values()[i], i + 1);
    cleanup.afterSingletonsInstantiated();
    var result = getTask(user, stale);
    assertThat(result.path("status").asText()).isEqualTo("FAILED");
    assertThat(result.path("errorCode").asText()).isEqualTo("SERVER_RESTARTED");
    assertThat(result.path("failedStage").isNull()).isTrue();
    for (var step : result.path("steps"))
      assertThat(step.path("status").asText()).isEqualTo("SKIPPED");
  }

  @Test
  void missingPresetFailsGenerationAndSkipsFinalization() throws Exception {
    String previous = mapper.preset("demo-1");
    try {
      jdbc.update(
          "UPDATE templates SET preset_result_key=? WHERE id=?",
          "results/missing-task-test.jpg",
          "demo-1");
      String id = create(null, 20).path("id").asText();
      var task = finished(id);
      assertThat(task.path("status").asText()).isEqualTo("FAILED");
      assertThat(task.path("failedStage").asText()).isEqualTo("GENERATE");
      assertThat(task.path("errorCode").asText()).isEqualTo("RESULT_SOURCE_UNAVAILABLE");
      assertThat(task.path("errorMessage").asText()).contains("结果素材");
      assertThat(task.path("steps").get(0).path("status").asText()).isEqualTo("SUCCEEDED");
      assertThat(task.path("steps").get(1).path("status").asText()).isEqualTo("FAILED");
      assertThat(task.path("steps").get(2).path("status").asText()).isEqualTo("SKIPPED");
      mvc.perform(get("/api/tasks/{id}/result", id).cookie(user)).andExpect(status().isNotFound());
      assertThat(Files.exists(STORAGE.resolve("tmp").resolve(id + ".tmp"))).isFalse();
    } finally {
      jdbc.update("UPDATE templates SET preset_result_key=? WHERE id=?", previous, "demo-1");
    }
  }

  @Test
  void terminalTasksCannotBeClaimedOrChangedBackToRunning() throws Exception {
    String successId = create(null, 20).path("id").asText();
    String failedId = create("GENERATE", 20).path("id").asText();
    var success = finished(successId);
    var failed = finished(failedId);
    assertThat(state.claim(successId)).isFalse();
    assertThat(state.claim(failedId)).isFalse();
    assertThatThrownBy(() -> state.start(successId, TaskStage.ASSET_CHECK))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> state.start(failedId, TaskStage.ASSET_CHECK))
        .isInstanceOf(IllegalStateException.class);
    state.fail(successId, TaskStage.GENERATE, "LATE_FAILURE", "late failure");
    assertThat(getTask(user, successId)).isEqualTo(success);
    assertThat(getTask(user, failedId)).isEqualTo(failed);
  }

  @Test
  void expiredSessionCannotAccessOldRecordsAndNewVisitorDoesNotInheritThem() throws Exception {
    String id = create(null, 20).path("id").asText();
    finished(id);
    String owner = mapper.find(id).userId();
    assertThat(
            jdbc.update(
                "UPDATE user_sessions SET expires_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND WHERE user_id=?",
                owner))
        .isEqualTo(1);
    mvc.perform(get("/api/me").cookie(user)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/tasks/{id}", id).cookie(user)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/tasks").cookie(user)).andExpect(status().isUnauthorized());
    var newSession =
        mvc.perform(post("/api/session").cookie(user))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    var newUser = json.readTree(newSession.getContentAsString()).path("id").asText();
    assertThat(newUser).isNotEqualTo(owner);
    Cookie newCookie = newSession.getCookie("studio_session");
    mvc.perform(get("/api/tasks/{id}", id).cookie(newCookie)).andExpect(status().isNotFound());
    mvc.perform(get("/api/tasks").cookie(newCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    assertThat(mapper.find(id).userId()).isEqualTo(owner);
  }

  @Test
  void missingPublishedResultReturnsNotFoundWithoutRewritingTaskState() throws Exception {
    String id = create(null, 20).path("id").asText();
    var completed = finished(id);
    assertThat(completed.path("status").asText()).isEqualTo("SUCCEEDED");
    Files.delete(STORAGE.resolve("results").resolve(id + ".jpg"));
    mvc.perform(get("/api/tasks/{id}/result", id).cookie(user))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESULT_NOT_FOUND"));
    assertThat(getTask(user, id)).isEqualTo(completed);
  }

  @Test
  void staleTabCannotQueryOrCreateTasksAfterCookieChangesToAnotherUser() throws Exception {
    String firstId = create(null, 20).path("id").asText();
    String owner = mapper.find(firstId).userId();
    Cookie other = visitor();
    mvc.perform(get("/api/tasks").cookie(other).header("X-Studio-User", owner))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDENTITY_CHANGED"));
    mvc.perform(get("/api/tasks/active").cookie(other).header("X-Studio-User", owner))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDENTITY_CHANGED"));
    mvc.perform(
            post("/api/tasks")
                .cookie(other)
                .header("X-Studio-User", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        Map.of("templateId", "demo-1", "assetIds", List.of(assetId)))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDENTITY_CHANGED"));
  }
}
