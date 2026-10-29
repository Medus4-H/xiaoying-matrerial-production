package com.xiaoying.studio.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AccountApiIT {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private Cookie visitor() throws Exception {
    return mvc.perform(post("/api/session"))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getCookie("studio_session");
  }

  private String id(Cookie cookie) throws Exception {
    return json.readTree(
            mvc.perform(get("/api/me").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("id")
        .asText();
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }

  @Test
  void registrationPreservesVisitorRecordsAndLoginRestoresThemAfterExpiry() throws Exception {
    Cookie guest = visitor();
    String userId = id(guest);
    String asset = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets(id,user_id,storage_key,original_name,mime_type,size_bytes,width,height,created_at) VALUES(?,?,?,?,?,?,?,?,NOW(3))",
        asset,
        userId,
        "test-" + asset,
        "test.png",
        "image/png",
        1,
        1,
        1);
    String email = UUID.randomUUID() + "@example.com";
    String phone =
        "1"
            + String.format(
                "%014d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000000000L));
    mvc.perform(
            post("/api/auth/register")
                .cookie(guest)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "email",
                            email,
                            "phone",
                            phone,
                            "password",
                            "example-password",
                            "emailCode",
                            "123456",
                            "phoneCode",
                            "123456",
                            "importGuest",
                            true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId))
        .andExpect(jsonPath("$.type").value("ACCOUNT"));
    assertThat(jdbc.queryForObject("SELECT user_id FROM assets WHERE id=?", String.class, asset))
        .isEqualTo(userId);
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM accounts WHERE user_id=?", String.class, userId))
        .doesNotContain("example-password");
    jdbc.update(
        "UPDATE user_sessions SET expires_at=DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE user_id=?",
        userId);
    mvc.perform(get("/api/me").cookie(guest)).andExpect(status().isUnauthorized());
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("identifier", phone, "password", "example-password"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userId))
            .andReturn();
    Cookie loggedIn = result.getResponse().getCookie("studio_session");
    assertThat(id(loggedIn)).isEqualTo(userId);
    mvc.perform(get("/api/assets/{id}/content", asset).cookie(visitor()))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("identifier", email, "password", "wrong-password"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void failedLoginDoesNotAbandonVisitorData() throws Exception {
    Cookie guest = visitor();
    String userId = id(guest);
    String asset = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets(id,user_id,storage_key,original_name,mime_type,size_bytes,width,height,created_at) VALUES(?,?,?,?,?,?,?,?,NOW(3))",
        asset,
        userId,
        "test-" + asset,
        "test.png",
        "image/png",
        1,
        1,
        1);
    mvc.perform(
            post("/api/auth/login")
                .cookie(guest)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(Map.of("identifier", "nobody@example.com", "password", "wrong-password"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
  }

  @Test
  void switchingBetweenRegisteredAccountsPreservesAndSeparatesHistory() throws Exception {
    Cookie first = visitor();
    Cookie second = visitor();
    String id1 = id(first);
    String id2 = id(second);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String email1 = suffix + "a@example.com";
    String email2 = suffix + "b@example.com";
    String phone1 =
        "1"
            + String.format(
                "%014d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000000000L));
    String phone2 = "2" + phone1.substring(1);
    for (var entry :
        java.util.List.of(
            Map.of("email", email1, "phone", phone1, "cookie", first),
            Map.of("email", email2, "phone", phone2, "cookie", second))) {
      mvc.perform(
              post("/api/auth/register")
                  .cookie((Cookie) entry.get("cookie"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      body(
                          Map.of(
                              "email",
                              entry.get("email"),
                              "phone",
                              entry.get("phone"),
                              "password",
                              "password123",
                              "emailCode",
                              "123456",
                              "phoneCode",
                              "123456",
                              "importGuest",
                              true))))
          .andExpect(status().isOk());
    }
    String task1 = UUID.randomUUID().toString();
    String task2 = UUID.randomUUID().toString();
    for (String[] task : java.util.List.of(new String[] {task1, id1}, new String[] {task2, id2})) {
      jdbc.update(
          "INSERT INTO tasks(id,user_id,template_id,template_title_snapshot,requirements_snapshot,status,simulation_snapshot,created_at,updated_at) VALUES(?,?,?,?,'{}','FAILED','{}',NOW(3),NOW(3))",
          task[0],
          task[1],
          "demo-1",
          "test");
    }
    Cookie switched =
        mvc.perform(
                post("/api/auth/login")
                    .cookie(first)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("identifier", email2, "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id2))
            .andReturn()
            .getResponse()
            .getCookie("studio_session");
    mvc.perform(get("/api/tasks/{id}", task1).cookie(switched)).andExpect(status().isNotFound());
    mvc.perform(get("/api/tasks/{id}", task2).cookie(switched)).andExpect(status().isOk());
    Cookie back =
        mvc.perform(
                post("/api/auth/login")
                    .cookie(switched)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("identifier", phone1, "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id1))
            .andReturn()
            .getResponse()
            .getCookie("studio_session");
    mvc.perform(get("/api/tasks/{id}", task1).cookie(back)).andExpect(status().isOk());
    mvc.perform(get("/api/tasks/{id}", task2).cookie(back)).andExpect(status().isNotFound());
  }

  @Test
  void registrationRejectsBadCodesAndDuplicateContactWithoutReassigningRecords() throws Exception {
    Cookie first = visitor();
    Cookie other = visitor();
    String firstId = id(first);
    String otherId = id(other);
    String email = UUID.randomUUID() + "@example.com";
    String phone =
        "1"
            + String.format(
                "%014d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000000000L));
    var valid =
        Map.of(
            "email",
            email,
            "phone",
            phone,
            "password",
            "password123",
            "emailCode",
            "123456",
            "phoneCode",
            "123456",
            "importGuest",
            true);
    mvc.perform(
            post("/api/auth/demo-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("channel", "email", "destination", email))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demoCode").value("123456"));
    mvc.perform(
            post("/api/auth/register")
                .cookie(first)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "email",
                            email,
                            "phone",
                            phone,
                            "password",
                            "password123",
                            "emailCode",
                            "wrong",
                            "phoneCode",
                            "123456",
                            "importGuest",
                            true))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/auth/register")
                .cookie(first)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(valid)))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/auth/register")
                .cookie(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(valid)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"));
    assertThat(id(first)).isEqualTo(firstId);
    assertThat(id(other)).isEqualTo(otherId);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE user_id=?", Integer.class, otherId))
        .isZero();
  }
}
