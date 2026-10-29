package com.xiaoying.studio.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "studio.session.cookie-name=studio_session_3000")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class SessionMigrationIT {
  @Autowired MockMvc mvc;
  @Autowired IdentityService identities;
  @Autowired AccountService accounts;
  @Autowired JdbcTemplate jdbc;

  private IdentityService.SessionResult visitor() {
    return identities.createSession(UUID.randomUUID().toString(), true);
  }

  @Test
  void validLegacySessionMigratesWithoutDeletingOriginalAndKeepsOwnedHistory() throws Exception {
    var visitor = visitor();
    String asset = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets(id,user_id,storage_key,original_name,mime_type,size_bytes,width,height,created_at) VALUES(?,?,?,?,?,?,?,?,NOW(3))",
        asset,
        visitor.user().id(),
        "test-" + asset,
        "test.png",
        "image/png",
        1,
        1,
        1);
    var response =
        mvc.perform(get("/api/me").cookie(new Cookie("studio_session", visitor.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(visitor.user().id()))
            .andReturn()
            .getResponse();
    Cookie scoped = response.getCookie("studio_session_3000");
    assertThat(scoped).isNotNull();
    assertThat(scoped.getValue()).isEqualTo(visitor.token());
    assertThat(response.getCookie("studio_session")).isNull();
    mvc.perform(get("/api/tasks").cookie(scoped)).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT user_id FROM assets WHERE id=?", String.class, asset))
        .isEqualTo(visitor.user().id());
  }

  @Test
  void scopedSessionWinsOverAnUnrelatedLegacySession() throws Exception {
    var scoped = visitor();
    var legacy = visitor();
    mvc.perform(
            get("/api/me")
                .cookie(
                    new Cookie("studio_session_3000", scoped.token()),
                    new Cookie("studio_session", legacy.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(scoped.user().id()));
  }

  @Test
  void invalidLegacySessionCreatesOnlyScopedCookie() throws Exception {
    Cookie legacy = new Cookie("studio_session", "invalid-token");
    mvc.perform(get("/api/me").cookie(legacy)).andExpect(status().isUnauthorized());
    var response =
        mvc.perform(post("/api/session").cookie(legacy))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    assertThat(response.getCookie("studio_session_3000")).isNotNull();
    assertThat(response.getCookie("studio_session")).isNull();
  }

  @Test
  void invalidScopedCookieNeverFallsBackToAnUnrelatedValidLegacyCookie() throws Exception {
    var legacy = visitor();
    Cookie scoped = new Cookie("studio_session_3000", "invalid-token");
    Cookie old = new Cookie("studio_session", legacy.token());
    mvc.perform(get("/api/me").cookie(scoped, old)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/session").cookie(scoped, old))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(legacy.user().id())));
  }

  @Test
  void completedMigrationDoesNotResurrectAValidLegacyIdentity() throws Exception {
    var scoped = visitor();
    var legacy = visitor();
    var initial =
        mvc.perform(get("/api/me").cookie(new Cookie("studio_session_3000", scoped.token())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    Cookie marker = initial.getCookie("studio_session_3000_migrated");
    assertThat(marker).isNotNull();
    Cookie old = new Cookie("studio_session", legacy.token());
    mvc.perform(get("/api/me").cookie(marker, old)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/session").cookie(marker, old))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(legacy.user().id())));
  }

  @Test
  void logoutMarksTheSiteEvenWhenClientSkippedIdentityBootstrap() throws Exception {
    var account = visitor();
    accounts.registerSingle(
        account.user(), "email", UUID.randomUUID() + "@example.com", "example-password", "123456");
    var otherLegacy = visitor();
    var response =
        mvc.perform(
                post("/api/auth/logout")
                    .cookie(
                        new Cookie("studio_session_3000", account.token()),
                        new Cookie("studio_session", otherLegacy.token())))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    Cookie marker = response.getCookie("studio_session_3000_migrated");
    assertThat(marker).isNotNull();
    mvc.perform(get("/api/me").cookie(marker, new Cookie("studio_session", otherLegacy.token())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void sessionEndpointCanAlsoPromoteAValidLegacyCookie() throws Exception {
    var legacy = visitor();
    var response =
        mvc.perform(post("/api/session").cookie(new Cookie("studio_session", legacy.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(legacy.user().id()))
            .andReturn()
            .getResponse();
    assertThat(response.getCookie("studio_session_3000").getValue()).isEqualTo(legacy.token());
    assertThat(response.getCookie("studio_session")).isNull();
  }

  @Test
  void accountMigrationPreservesTheLegacyVisitorBackup() throws Exception {
    var account = visitor();
    accounts.registerSingle(
        account.user(), "email", UUID.randomUUID() + "@example.com", "example-password", "123456");
    var guest = visitor();
    var response =
        mvc.perform(
                get("/api/me")
                    .cookie(
                        new Cookie("studio_session", account.token()),
                        new Cookie("studio_session_visitor", guest.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(account.user().id()))
            .andReturn()
            .getResponse();
    Cookie scoped = response.getCookie("studio_session_3000");
    Cookie backup = response.getCookie("studio_session_3000_visitor");
    assertThat(scoped).isNotNull();
    assertThat(backup).isNotNull();
    var logout =
        mvc.perform(post("/api/auth/logout").cookie(scoped, backup))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    mvc.perform(get("/api/me").cookie(logout.getCookie("studio_session_3000")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(guest.user().id()));
  }
}
