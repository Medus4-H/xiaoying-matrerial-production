package com.xiaoying.studio.identity;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
  private final UserMapper mapper;
  private final SessionProperties config;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public IdentityService(UserMapper mapper, SessionProperties config, Clock clock) {
    this.mapper = mapper;
    this.config = config;
    this.clock = clock;
  }

  public User resolve(String token) {
    User user =
        token == null || token.isBlank() || token.length() > 128
            ? null
            : mapper.findByHash(hash(token));
    if (user == null || !user.expiresAt().isAfter(clock.instant())) {
      throw new ApiException(ErrorKey.SESSION_REQUIRED);
    }
    return user;
  }

  @Transactional
  public SessionResult createOrReuse(String token) {
    if (token != null) {
      try {
        return new SessionResult(token, resolve(token), false);
      } catch (ApiException e) {
        if (e.status() != HttpStatus.UNAUTHORIZED) throw e;
      }
    }
    String userId = UUID.randomUUID().toString();
    SessionResult result = createSession(userId, true);
    return result;
  }

  @Transactional
  public SessionResult createSession(String userId, boolean newUser) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var now = clock.instant();
    var user = new User(userId, hash(value), now, now.plus(config.ttl()));
    if (newUser) mapper.insert(user);
    mapper.insertSession(user);
    return new SessionResult(value, user, newUser);
  }

  public void endSession(String token) {
    if (token != null && !token.isBlank() && token.length() <= 128)
      mapper.deleteSession(hash(token));
  }

  private String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public record SessionResult(String token, User user, boolean created) {}
}
