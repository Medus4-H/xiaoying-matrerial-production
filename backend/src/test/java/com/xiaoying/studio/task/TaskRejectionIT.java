package com.xiaoying.studio.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class TaskRejectionIT {
  private static final Path STORAGE = createStorage();
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @MockitoBean(name = "taskExecutor")
  ThreadPoolTaskExecutor executor;

  private static Path createStorage() {
    try {
      return Files.createTempDirectory("studio-task-rejection-");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add("studio.storage-root", STORAGE::toString);
  }

  @AfterAll
  static void cleanup() throws Exception {
    try (var paths = Files.walk(STORAGE)) {
      for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.deleteIfExists(path);
    }
  }

  @Test
  void rejectedQueueStillReturnsPersistedFailedTask() throws Exception {
    Cookie user =
        mvc.perform(post("/api/session"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getCookie("studio_session");
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var file = new MockMultipartFile("file", "ok.png", "image/png", bytes.toByteArray());
    String body =
        mvc.perform(multipart("/api/assets").file(file).cookie(user))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String asset = json.readTree(body).path("id").asText();
    doThrow(new TaskRejectedException("full")).when(executor).execute(any(Runnable.class));
    var request = Map.of("templateId", "demo-1", "assetIds", List.of(asset));
    String accepted =
        mvc.perform(
                post("/api/tasks")
                    .cookie(user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorCode").value("DISPATCH_REJECTED"))
            .andExpect(jsonPath("$.failedStage").doesNotExist())
            .andExpect(jsonPath("$.steps[0].status").value("SKIPPED"))
            .andExpect(jsonPath("$.steps[2].status").value("SKIPPED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(accepted).path("id").asText();
    mvc.perform(get("/api/tasks/{id}", id).cookie(user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errorCode").value("DISPATCH_REJECTED"));
    mvc.perform(get("/api/tasks/active").cookie(user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }
}
