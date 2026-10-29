package com.xiaoying.studio.identity;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
  private final IdentityService identities;
  private final AccountService accounts;

  public RegistrationService(IdentityService identities, AccountService accounts) {
    this.identities = identities;
    this.accounts = accounts;
  }

  @Transactional
  public IdentityService.SessionResult createSeparate(
      User visitor, AccountController.RegisterRequest input) {
    if (accounts.account(visitor.id()) != null) {
      throw new ApiException(ErrorKey.ALREADY_REGISTERED);
    }
    var session = identities.createSession(UUID.randomUUID().toString(), true);
    if (input.channel() != null) {
      accounts.registerSingle(
          session.user(), input.channel(), input.destination(), input.password(), input.code());
    } else {
      accounts.register(
          session.user(),
          input.email(),
          input.phone(),
          input.password(),
          input.emailCode(),
          input.phoneCode());
    }
    return session;
  }
}
