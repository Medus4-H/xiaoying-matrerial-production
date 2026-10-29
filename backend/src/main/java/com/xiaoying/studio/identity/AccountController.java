package com.xiaoying.studio.identity;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.common.SafeLog;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AccountController {
  private static final Logger log = LoggerFactory.getLogger(AccountController.class);
  private final AccountService accounts;
  private final IdentityService identities;
  private final IdentityController identityController;
  private final RequestIdentity requestIdentity;
  private final RegistrationService registrations;

  public AccountController(
      AccountService accounts,
      IdentityService identities,
      IdentityController identityController,
      RequestIdentity requestIdentity,
      RegistrationService registrations) {
    this.accounts = accounts;
    this.identities = identities;
    this.identityController = identityController;
    this.requestIdentity = requestIdentity;
    this.registrations = registrations;
  }

  @PostMapping("/demo-code")
  public ResponseEntity<DemoCodeView> demoCode(
      @RequestBody CodeRequest input, HttpServletRequest request) {
    requestIdentity.checkOrigin(request);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new DemoCodeView(accounts.demoCode(input.channel(), input.destination())));
  }

  @PostMapping("/register")
  public ResponseEntity<IdentityController.IdentityView> register(
      @RequestBody RegisterRequest input, HttpServletRequest request) {
    requestIdentity.checkOrigin(request);
    User visitor = requestIdentity.resolve(request);
    if (input.channel() != null
        && (input.email() != null
            || input.phone() != null
            || input.emailCode() != null
            || input.phoneCode() != null))
      throw new ApiException(ErrorKey.ACCOUNT_REGISTER_CHANNEL);
    if (Boolean.TRUE.equals(input.importGuest())) {
      User user =
          input.channel() != null
              ? accounts.registerSingle(
                  visitor, input.channel(), input.destination(), input.password(), input.code())
              : accounts.register(
                  visitor,
                  input.email(),
                  input.phone(),
                  input.password(),
                  input.emailCode(),
                  input.phoneCode());
      SafeLog.write(
          () ->
              log.atInfo()
                  .addKeyValue("event", "auth.register.succeeded")
                  .log("Account registered"));
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .body(identityController.view(user));
    }
    String token = identityController.token(request);
    String oldBackup = identityController.token(request, identityController.visitorCookieName());
    User backup = resolveVisitor(oldBackup);
    if (backup != null
        && !backup.id().equals(visitor.id())
        && accounts.hasGuestData(visitor.id())) {
      throw new ApiException(ErrorKey.GUEST_BACKUP_REGISTER);
    }
    var session = registrations.createSeparate(visitor, input);
    SafeLog.write(
        () ->
            log.atInfo().addKeyValue("event", "auth.register.succeeded").log("Account registered"));
    var response =
        ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, identityController.cookie(session).toString());
    if (backup == null || backup.id().equals(visitor.id())) {
      response.header(
          HttpHeaders.SET_COOKIE, identityController.visitorCookie(token, visitor).toString());
    }
    return response
        .cacheControl(CacheControl.noStore())
        .body(identityController.view(session.user()));
  }

  private User resolveVisitor(String token) {
    if (token == null) return null;
    try {
      User visitor = identities.resolve(token);
      return accounts.account(visitor.id()) == null ? visitor : null;
    } catch (ApiException e) {
      if (e.status() != HttpStatus.UNAUTHORIZED) throw e;
      return null;
    }
  }

  @PostMapping("/bind")
  public ResponseEntity<IdentityController.IdentityView> bind(
      @RequestBody BindRequest input, HttpServletRequest request) {
    requestIdentity.checkOrigin(request);
    User user =
        accounts.bind(
            requestIdentity.resolve(request), input.channel(), input.destination(), input.code());
    SafeLog.write(
        () ->
            log.atInfo().addKeyValue("event", "auth.bind.succeeded").log("Account contact bound"));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(identityController.view(user));
  }

  @PostMapping("/login")
  public ResponseEntity<IdentityController.IdentityView> login(
      @RequestBody LoginRequest input, HttpServletRequest request) {
    requestIdentity.checkOrigin(request);
    String currentToken = identityController.token(request);
    User visitor = resolveVisitor(currentToken);
    User backup =
        resolveVisitor(identityController.token(request, identityController.visitorCookieName()));
    if (visitor != null
        && backup != null
        && !backup.id().equals(visitor.id())
        && accounts.hasGuestData(visitor.id())) {
      throw new ApiException(ErrorKey.GUEST_BACKUP_LOGIN);
    }
    var session = accounts.login(input.identifier(), input.password());
    SafeLog.write(
        () ->
            log.atInfo()
                .addKeyValue("event", "auth.login.succeeded")
                .log("Account login succeeded"));
    var response =
        ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, identityController.cookie(session).toString());
    if (visitor != null && (backup == null || backup.id().equals(visitor.id()))) {
      response.header(
          HttpHeaders.SET_COOKIE,
          identityController.visitorCookie(currentToken, visitor).toString());
    }
    return response
        .cacheControl(CacheControl.noStore())
        .body(identityController.view(session.user()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    requestIdentity.checkOrigin(request);
    User user = requestIdentity.resolve(request);
    if (accounts.account(user.id()) == null) throw new ApiException(ErrorKey.GUEST_LOGOUT);
    identities.endSession(identityController.token(request));
    String visitorToken = identityController.token(request, identityController.visitorCookieName());
    User visitor = resolveVisitor(visitorToken);
    var response = ResponseEntity.noContent();
    if (visitor != null) {
      response.header(
          HttpHeaders.SET_COOKIE,
          identityController
              .cookie(new IdentityService.SessionResult(visitorToken, visitor, false))
              .toString());
    } else {
      var recovered = accounts.restoreSeparatedVisitor(user.id());
      response.header(
          HttpHeaders.SET_COOKIE,
          recovered == null
              ? identityController.clearCookie().toString()
              : identityController.cookie(recovered).toString());
    }
    return response
        .header(HttpHeaders.SET_COOKIE, identityController.clearVisitorCookie().toString())
        .cacheControl(CacheControl.noStore())
        .build();
  }

  public record CodeRequest(String channel, String destination) {}

  public record DemoCodeView(String demoCode) {}

  public record RegisterRequest(
      String channel,
      String destination,
      String password,
      String code,
      String email,
      String phone,
      String emailCode,
      String phoneCode,
      Boolean importGuest) {}

  public record BindRequest(String channel, String destination, String password, String code) {}

  public record LoginRequest(String identifier, String password) {}
}
