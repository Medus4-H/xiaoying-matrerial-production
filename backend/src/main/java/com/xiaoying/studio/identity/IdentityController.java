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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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

  public IdentityController(
      IdentityService service, SessionProperties config, Clock clock, AccountService accounts) {
    this.service = service;
    this.config = config;
    this.clock = clock;
    this.accounts = accounts;
  }

  @GetMapping("/me")
  public ResponseEntity<IdentityView> me(HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(view(service.resolve(token(request))));
  }

  @PostMapping("/session")
  public ResponseEntity<IdentityView> session(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin != null && !config.allowedOrigins().contains(origin)) {
      throw new ApiException(ErrorKey.ORIGIN_DENIED);
    }
    var result = service.createOrReuse(token(request));
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
    return ResponseEntity.status(result.created() ? 201 : 200)
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .cacheControl(CacheControl.noStore())
        .body(view(result.user()));
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
