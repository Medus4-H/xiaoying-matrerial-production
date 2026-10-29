package com.xiaoying.studio.identity;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestIdentity {
  private final IdentityService identities;
  private final SessionProperties properties;

  public RequestIdentity(IdentityService identities, SessionProperties properties) {
    this.identities = identities;
    this.properties = properties;
  }

  public User resolve(HttpServletRequest request) {
    String token = null;
    if (request.getCookies() != null) {
      for (var cookie : request.getCookies()) {
        if (cookie.getName().equals(properties.cookieName())) {
          token = cookie.getValue();
          break;
        }
      }
    }
    User user = identities.resolve(token);
    String expectedUser = request.getHeader("X-Studio-User");
    if (expectedUser != null && !expectedUser.equals(user.id())) {
      throw new ApiException(ErrorKey.IDENTITY_CHANGED);
    }
    return user;
  }

  public void checkOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin != null && !properties.allowedOrigins().contains(origin)) {
      throw new ApiException(ErrorKey.ORIGIN_DENIED);
    }
  }
}
