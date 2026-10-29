package com.xiaoying.studio.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ErrorI18nIT {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void serverGeneratedRequestIdMatchesHeaderAndErrorBody() throws Exception {
    var response =
        mvc.perform(get("/api/me").header("X-Request-Id", "forged-id"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse();
    String requestId = response.getHeader("X-Request-Id");
    assertThat(requestId).isNotBlank().isNotEqualTo("forged-id");
    assertThat(json.readTree(response.getContentAsString()).path("requestId").asText())
        .isEqualTo(requestId);
  }

  @Test
  void errorBundleIsAvailableWithoutAuthenticationAndSupportsFallback() throws Exception {
    mvc.perform(get("/api/i18n/errors").param("locale", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locale").value("en"))
        .andExpect(jsonPath("$.items['auth.login_failed']").value("Incorrect account or password"));
    mvc.perform(get("/api/i18n/errors").param("locale", "unsupported"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locale").value("zh-CN"));
  }

  @Test
  void loginFailureKeepsItsCodeWhileSelectingLanguage() throws Exception {
    String input = "{\"identifier\":\"unknown@example.com\",\"password\":\"password123\"}";
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "en")
                .content(input))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
        .andExpect(jsonPath("$.messageKey").value("auth.login_failed"))
        .andExpect(jsonPath("$.message").value("Incorrect account or password"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "fr")
                .content(input))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("账号或密码不正确"));
  }

  @Test
  void existingAndUnknownAccountsHaveTheSamePublicLoginFailure() throws Exception {
    var visitor =
        mvc.perform(post("/api/session")).andReturn().getResponse().getCookie("studio_session");
    String email = UUID.randomUUID() + "@example.com";
    mvc.perform(
            post("/api/auth/register")
                .cookie(visitor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"channel\":\"email\",\"destination\":\""
                        + email
                        + "\",\"password\":\"password123\",\"code\":\"123456\"}"))
        .andExpect(status().isOk());
    for (String identifier : new String[] {email, UUID.randomUUID() + "@example.com"}) {
      mvc.perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"identifier\":\"" + identifier + "\",\"password\":\"wrongpassword\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
          .andExpect(jsonPath("$.messageKey").value("auth.login_failed"))
          .andExpect(jsonPath("$.message").value("账号或密码不正确"))
          .andExpect(jsonPath("$.detail").doesNotExist());
    }
  }

  @Test
  void distinctValidationMessagesPreserveTheExistingErrorCode() throws Exception {
    for (String channel : new String[] {"email", "phone"}) {
      mvc.perform(
              post("/api/auth/demo-code")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"channel\":\"" + channel + "\",\"destination\":\"bad\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_INPUT"))
          .andExpect(
              jsonPath("$.messageKey")
                  .value(channel.equals("email") ? "auth.email_invalid" : "auth.phone_format"));
    }
  }
}
