package com.xiaoying.studio.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "studio.logging.slow-request-ms=60000")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingIT {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void logsOnlySafeFieldsAndCorrelatesErrorResponse(CapturedOutput captured) throws Exception {
    String response =
        mvc.perform(
                post("/api/auth/login?note=private-query@example.test")
                    .header("X-Request-Id", "forged-request-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"identifier\":\"private-user@example.test\",\"password\":\"secret-password-123\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(response).path("requestId").asText();
    String logs = captured.getAll();
    assertThat(logs).contains(id, "http.request", "auth.login.failed", "LOGIN_FAILED");
    assertThat(logs)
        .doesNotContain(
            "private-query@example.test",
            "private-user@example.test",
            "secret-password-123",
            "forged-request-id");
  }

  @Test
  void successfulPollingDoesNotWriteRequestLogs(CapturedOutput captured) throws Exception {
    var session =
        mvc.perform(post("/api/session"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getCookie("studio_session");
    int before = occurrences(captured.getAll(), "http.request");
    for (int i = 0; i < 3; i++) {
      mvc.perform(get("/api/tasks/active").cookie(session)).andExpect(status().isOk());
    }
    assertThat(occurrences(captured.getAll(), "http.request")).isEqualTo(before);
  }

  @Test
  void uploadsNeverLogOriginalFilenameOrSessionToken(CapturedOutput captured) throws Exception {
    var session =
        mvc.perform(post("/api/session"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getCookie("studio_session");
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    var file =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "private-file-name-321.png", "image/png", bytes.toByteArray());
    mvc.perform(multipart("/api/assets").file(file).cookie(session))
        .andExpect(status().isCreated());
    assertThat(captured.getAll())
        .contains("asset.upload.stored")
        .doesNotContain("private-file-name-321.png", session.getValue());
  }

  @Test
  void illegalPathsDoNotLeakTheirContentsInFrameworkLogs(CapturedOutput captured) throws Exception {
    mvc.perform(get("/api/assets/../../private-person@example.test/content"))
        .andExpect(status().isNotFound());
    assertThat(captured.getAll()).contains("request.invalid_path")
        .doesNotContain("private-person@example.test");
  }

  private int occurrences(String value, String part) {
    return (value.length() - value.replace(part, "").length()) / part.length();
  }
}
