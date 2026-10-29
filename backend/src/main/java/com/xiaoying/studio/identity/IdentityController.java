package com.xiaoying.studio.identity;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.common.SafeLog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IdentityController {
  private static final Logger log = LoggerFactory.getLogger(IdentityController.class);
  private final IdentityService service;
  private final SessionProperties config;
  private final Clock clock;
  private final AccountService accounts;
  private final String legacyCookieName;
  private final Duration migrationMarkerTtl;

  public IdentityController(
      IdentityService service,
      SessionProperties config,
      Clock clock,
      AccountService accounts,
      @Value("${studio.session.legacy-cookie-name:studio_session}") String legacyCookieName,
      @Value("${studio.session.migration-marker-ttl-seconds:34560000}") long markerTtlSeconds) {
    this.service = service;
    this.config = config;
    this.clock = clock;
    this.accounts = accounts;
    if (!legacyCookieName.matches("[A-Za-z0-9_]+"))
      throw new IllegalArgumentException("Invalid legacy session cookie name");
    this.legacyCookieName = legacyCookieName;
    if (markerTtlSeconds <= 0) throw new IllegalArgumentException("Invalid migration marker TTL");
    Duration markerTtl = Duration.ofSeconds(markerTtlSeconds);
    this.migrationMarkerTtl = markerTtl.compareTo(config.ttl()) < 0 ? config.ttl() : markerTtl;
  }

  @GetMapping("/me")
  public ResponseEntity<IdentityView> me(HttpServletRequest request) {
    String scoped = token(request);
    boolean legacy = mayUseLegacy(request, scoped);
    String current = legacy ? token(request, legacyCookieName) : scoped;
    User user = service.resolve(current);
    var response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
    if (legacy) {
      response.header(
          HttpHeaders.SET_COOKIE,
          cookie(new IdentityService.SessionResult(current, user, false)).toString());
      migrateVisitorBackup(request, response, user);
    }
    markMigration(response);
    return response.body(view(user));
  }

  @PostMapping("/session")
  public ResponseEntity<IdentityView> session(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin != null && !config.allowedOrigins().contains(origin)) {
      throw new ApiException(ErrorKey.ORIGIN_DENIED);
    }
    String scoped = token(request);
    boolean legacy = mayUseLegacy(request, scoped);
    var result = service.createOrReuse(legacy ? token(request, legacyCookieName) : scoped);
    SafeLog.write(
        () ->
            log.atInfo()
                .addKeyValue(
                    "event",
                    result.created() ? "identity.session.created" : "identity.session.reused")
                .log("Visitor session ready"));
    var cookie =
        ResponseCookie.from(config.cookieName(), result.token())
            .httpOnly(true)
            .secure(config.secure())
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.between(clock.instant(), result.user().expiresAt()))
            .build();
    var response =
        ResponseEntity.status(result.created() ? 201 : 200)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .cacheControl(CacheControl.noStore());
    if (legacy && !result.created()) migrateVisitorBackup(request, response, result.user());
    markMigration(response);
    return response.body(view(result.user()));
  }

  private boolean mayUseLegacy(HttpServletRequest request, String scoped) {
    return scoped == null
        && !config.cookieName().equals(legacyCookieName)
        && token(request, migrationMarkerName()) == null;
  }

  void markMigration(ResponseEntity.HeadersBuilder<?> response) {
    if (config.cookieName().equals(legacyCookieName)) return;
    response.header(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(migrationMarkerName(), "1")
            .httpOnly(true)
            .secure(config.secure())
            .sameSite("Lax")
            .path("/")
            .maxAge(migrationMarkerTtl)
            .build()
            .toString());
  }

  private String migrationMarkerName() {
    return config.cookieName() + "_migrated";
  }

  private void migrateVisitorBackup(
      HttpServletRequest request, ResponseEntity.BodyBuilder response, User current) {
    if (accounts.account(current.id()) == null || token(request, visitorCookieName()) != null)
      return;
    String old = token(request, legacyCookieName + "_visitor");
    if (old == null) return;
    try {
      User visitor = service.resolve(old);
      if (accounts.account(visitor.id()) == null)
        response.header(HttpHeaders.SET_COOKIE, visitorCookie(old, visitor).toString());
    } catch (ApiException error) {
      if (error.status() != HttpStatus.UNAUTHORIZED) throw error;
    }
  }

  String token(HttpServletRequest request) {
    return token(request, config.cookieName());
  }

  String token(HttpServletRequest request, String cookieName) {
    if (request.getCookies() != null) {
      for (var cookie : request.getCookies()) {
        if (cookie.getName().equals(cookieName)) return cookie.getValue();
      }
    }
    return null;
  }

  IdentityView view(User user) {
    var account = accounts.account(user.id());
    if (account == null)
      return new IdentityView(
          user.id(), "访客 " + user.id().substring(0, 8), user.expiresAt(), "GUEST", null, null);
    String email = ContactMasker.email(account.email());
    String phone = ContactMasker.phone(account.phone());
    return new IdentityView(
        user.id(), email != null ? email : phone, user.expiresAt(), "ACCOUNT", email, phone);
  }

  ResponseCookie cookie(IdentityService.SessionResult result) {
    return ResponseCookie.from(config.cookieName(), result.token())
        .httpOnly(true)
        .secure(config.secure())
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.between(clock.instant(), result.user().expiresAt()))
        .build();
  }

  String visitorCookieName() {
    return config.cookieName() + "_visitor";
  }

  ResponseCookie visitorCookie(String token, User visitor) {
    return ResponseCookie.from(visitorCookieName(), token)
        .httpOnly(true)
        .secure(config.secure())
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.between(clock.instant(), visitor.expiresAt()))
        .build();
  }

  ResponseCookie clearVisitorCookie() {
    return ResponseCookie.from(visitorCookieName(), "")
        .httpOnly(true)
        .secure(config.secure())
        .sameSite("Lax")
        .path("/")
        .maxAge(0)
        .build();
  }

  ResponseCookie clearCookie() {
    return ResponseCookie.from(config.cookieName(), "")
        .httpOnly(true)
        .secure(config.secure())
        .sameSite("Lax")
        .path("/")
        .maxAge(0)
        .build();
  }

  public record IdentityView(
      String id, String displayName, Instant expiresAt, String type, String email, String phone) {}
}
