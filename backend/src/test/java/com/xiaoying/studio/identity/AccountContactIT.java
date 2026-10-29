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
class AccountContactIT {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private Cookie guest() throws Exception {
    return mvc.perform(post("/api/session"))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getCookie("studio_session");
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }

  private String email() {
    return UUID.randomUUID() + "@example.com";
  }

  private String phone() {
    return "1"
        + String.format(
            "%014d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000000000L));
  }

  private String currentId(Cookie cookie) throws Exception {
    return json.readTree(
            mvc.perform(get("/api/me").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("id")
        .asText();
  }

  private void register(Cookie cookie, String channel, String destination) throws Exception {
    var response =
        mvc.perform(
                post("/api/auth/register")
                    .cookie(cookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "channel",
                                channel,
                                "destination",
                                destination,
                                "password",
                                "password123",
                                "code",
                                "123456",
                                "importGuest",
                                true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(currentId(cookie)))
            .andReturn()
            .getResponse();
    assertThat(response.getContentAsString()).doesNotContain(destination);
  }

  private Cookie login(String identifier) throws Exception {
    var response =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("identifier", identifier, "password", "password123"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(response.getContentAsString()).doesNotContain(identifier);
    return response.getCookie("studio_session");
  }

  private org.springframework.test.web.servlet.ResultActions bind(
      Cookie cookie, String channel, String destination, String code) throws Exception {
    return mvc.perform(
        post("/api/auth/bind")
            .cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("channel", channel, "destination", destination, "code", code))));
  }

  @Test
  void emailOnlyRegistrationBindsPhoneAndBothLoginsKeepTheSameUserAndAssets() throws Exception {
    Cookie cookie = guest();
    String original = currentId(cookie);
    String address = email();
    String mobile = phone();
    String asset = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets(id,user_id,storage_key,original_name,mime_type,size_bytes,width,height,created_at) VALUES(?,?,?,?,?,?,?,?,NOW(3))",
        asset,
        original,
        "test-" + asset,
        "test.png",
        "image/png",
        1,
        1,
        1);
    register(cookie, "email", address);
    mvc.perform(get("/api/me").cookie(cookie))
        .andExpect(jsonPath("$.email").value(address.substring(0, 1) + "***@example.com"))
        .andExpect(jsonPath("$.displayName").value(address.substring(0, 1) + "***@example.com"))
        .andExpect(jsonPath("$.phone").doesNotExist());
    bind(cookie, "phone", mobile, "123456")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.phone").value(mobile.substring(0, 3) + "****" + mobile.substring(11)));
    assertThat(currentId(login(address))).isEqualTo(original);
    assertThat(currentId(login(mobile))).isEqualTo(original);
    assertThat(jdbc.queryForObject("SELECT user_id FROM assets WHERE id=?", String.class, asset))
        .isEqualTo(original);
  }

  @Test
  void phoneOnlyRegistrationBindsEmailAndBothLoginsKeepTheSameUser() throws Exception {
    Cookie cookie = guest();
    String original = currentId(cookie);
    String address = email();
    String mobile = phone();
    register(cookie, "phone", mobile);
    mvc.perform(get("/api/me").cookie(cookie))
        .andExpect(
            jsonPath("$.displayName").value(mobile.substring(0, 3) + "****" + mobile.substring(11)))
        .andExpect(jsonPath("$.email").doesNotExist());
    bind(cookie, "email", address, "123456").andExpect(status().isOk());
    assertThat(currentId(login(address))).isEqualTo(original);
    assertThat(currentId(login(mobile))).isEqualTo(original);
  }

  @Test
  void shortContactsAreMaskedWithoutExposingMostOfTheirCharacters() throws Exception {
    Cookie phoneUser = guest();
    String shortPhone = phone().substring(0, 10);
    register(phoneUser, "phone", shortPhone);
    mvc.perform(get("/api/me").cookie(phoneUser))
        .andExpect(jsonPath("$.phone").value("****" + shortPhone.substring(8)))
        .andExpect(jsonPath("$.displayName").value("****" + shortPhone.substring(8)));

    Cookie emailUser = guest();
    String shortEmail = "x@" + UUID.randomUUID() + ".test";
    register(emailUser, "email", shortEmail);
    mvc.perform(get("/api/me").cookie(emailUser))
        .andExpect(jsonPath("$.email").value("***" + shortEmail.substring(1)))
        .andExpect(jsonPath("$.displayName").value("***" + shortEmail.substring(1)));
    assertThat(currentId(login(shortEmail))).isEqualTo(currentId(emailUser));
    assertThat(currentId(login(shortPhone))).isEqualTo(currentId(phoneUser));
  }

  @Test
  void occupiedContactIsNotMergedOrOverwrittenAndAccountCannotReplaceItsOwnContact()
      throws Exception {
    Cookie first = guest();
    Cookie second = guest();
    String firstId = currentId(first);
    String secondId = currentId(second);
    String address = email();
    String occupied = phone();
    register(first, "email", address);
    register(second, "phone", occupied);
    bind(first, "phone", occupied, "123456")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"));
    bind(first, "email", email(), "123456").andExpect(status().isConflict());
    assertThat(currentId(login(address))).isEqualTo(firstId);
    assertThat(currentId(login(occupied))).isEqualTo(secondId);
  }

  @Test
  void bindingRequiresAuthenticatedAccountAndCorrectDemoCode() throws Exception {
    Cookie cookie = guest();
    bind(cookie, "phone", phone(), "123456").andExpect(status().isUnauthorized());
    register(cookie, "email", email());
    String mobile = phone();
    bind(cookie, "phone", mobile, "wrong").andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/auth/bind")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("channel", "phone", "destination", mobile))))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/me").cookie(cookie)).andExpect(jsonPath("$.phone").doesNotExist());
  }

  @Test
  void missingAndExpiredSessionsCannotBindContacts() throws Exception {
    var payload = body(Map.of("channel", "email", "destination", email(), "code", "123456"));
    mvc.perform(post("/api/auth/bind").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isUnauthorized());
    Cookie cookie = guest();
    String userId = currentId(cookie);
    register(cookie, "phone", phone());
    jdbc.update(
        "UPDATE user_sessions SET expires_at=DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE user_id=?",
        userId);
    mvc.perform(
            post("/api/auth/bind")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void newRegistrationSeparatesGuestDataAndLogoutRestoresSameBrowserGuest() throws Exception {
    Cookie visitor = guest();
    String visitorId = currentId(visitor);
    String assetId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets(id,user_id,storage_key,original_name,mime_type,size_bytes,width,height,created_at) VALUES(?,?,?,?,?,?,?,?,NOW(3))",
        assetId,
        visitorId,
        "test-" + assetId,
        "test.png",
        "image/png",
        1,
        1,
        1);
    var response =
        mvc.perform(
                post("/api/auth/register")
                    .cookie(visitor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "channel",
                                "email",
                                "destination",
                                email(),
                                "password",
                                "password123",
                                "code",
                                "123456"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    String accountId = json.readTree(response.getContentAsString()).path("id").asText();
    assertThat(accountId).isNotEqualTo(visitorId);
    Cookie accountCookie = response.getCookie("studio_session");
    Cookie backupCookie = response.getCookie("studio_session_visitor");
    assertThat(backupCookie).isNotNull();
    mvc.perform(get("/api/assets/{id}/content", assetId).cookie(accountCookie))
        .andExpect(status().isNotFound());
    var logout =
        mvc.perform(post("/api/auth/logout").cookie(accountCookie, backupCookie))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    assertThat(currentId(logout.getCookie("studio_session"))).isEqualTo(visitorId);
    assertThat(jdbc.queryForObject("SELECT user_id FROM assets WHERE id=?", String.class, assetId))
        .isEqualTo(visitorId);
  }

  @Test
  void previouslySeparatedVisitorCanBeRestoredOnceAtLogout() throws Exception {
    Cookie account = guest();
    register(account, "email", email());
    String accountId = currentId(account);
    Cookie oldVisitor = guest();
    String visitorId = currentId(oldVisitor);
    jdbc.update(
        "INSERT INTO visitor_recoveries(account_user_id,visitor_user_id) VALUES(?,?)",
        accountId,
        visitorId);
    var logout =
        mvc.perform(post("/api/auth/logout").cookie(account))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    assertThat(currentId(logout.getCookie("studio_session"))).isEqualTo(visitorId);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM visitor_recoveries WHERE account_user_id=?",
                Integer.class,
                accountId))
        .isZero();
  }

  @Test
  void guestWithDataCanSignInToExistingAccountWithoutImportAndRestoreAfterLogout()
      throws Exception {
    Cookie account = guest();
    String address = email();
    register(account, "email", address);
    String accountId = currentId(account);
    Cookie visitor = guest();
    String visitorId = currentId(visitor);
    String assetId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets(id,user_id,storage_key,original_name,mime_type,size_bytes,width,height,created_at) VALUES(?,?,?,?,?,?,?,?,NOW(3))",
        assetId,
        visitorId,
        "test-" + assetId,
        "test.png",
        "image/png",
        1,
        1,
        1);
    var login =
        mvc.perform(
                post("/api/auth/login")
                    .cookie(visitor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("identifier", address, "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(accountId))
            .andReturn()
            .getResponse();
    Cookie accountCookie = login.getCookie("studio_session");
    Cookie backupCookie = login.getCookie("studio_session_visitor");
    assertThat(backupCookie).isNotNull();
    mvc.perform(get("/api/assets/{id}/content", assetId).cookie(accountCookie))
        .andExpect(status().isNotFound());
    var logout =
        mvc.perform(post("/api/auth/logout").cookie(accountCookie, backupCookie))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    assertThat(currentId(logout.getCookie("studio_session"))).isEqualTo(visitorId);
  }

  @Test
  void stalePageCannotRegisterOrBindUsingAnotherUsersCookie() throws Exception {
    Cookie first = guest();
    String expected = currentId(first);
    Cookie other = guest();
    String registration =
        body(
            Map.of(
                "channel",
                "phone",
                "destination",
                phone(),
                "password",
                "password123",
                "code",
                "123456"));
    mvc.perform(
            post("/api/auth/register")
                .cookie(other)
                .header("X-Studio-User", expected)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDENTITY_CHANGED"));
    register(other, "phone", phone());
    String binding = body(Map.of("channel", "email", "destination", email(), "code", "123456"));
    mvc.perform(
            post("/api/auth/bind")
                .cookie(other)
                .header("X-Studio-User", expected)
                .contentType(MediaType.APPLICATION_JSON)
                .content(binding))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDENTITY_CHANGED"));
  }
}
