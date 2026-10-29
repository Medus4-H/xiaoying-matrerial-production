package com.xiaoying.studio.identity;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.common.SafeLog;
import com.xiaoying.studio.identity.AccountMapper.Account;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private static final Logger log = LoggerFactory.getLogger(AccountService.class);
  private final AccountMapper accounts;
  private final IdentityService identities;
  private final AccountProperties properties;
  private final PasswordHasher passwords;
  private final Clock clock;

  public AccountService(
      AccountMapper accounts,
      IdentityService identities,
      AccountProperties properties,
      PasswordHasher passwords,
      Clock clock) {
    this.accounts = accounts;
    this.identities = identities;
    this.properties = properties;
    this.passwords = passwords;
    this.clock = clock;
  }

  public Account account(String userId) {
    return accounts.findByUserId(userId);
  }

  public boolean hasGuestData(String userId) {
    return accounts.hasGuestData(userId);
  }

  @Transactional
  public IdentityService.SessionResult restoreSeparatedVisitor(String accountId) {
    String visitorId = accounts.lockVisitorRecovery(accountId);
    if (visitorId == null) return null;
    if (accounts.findByUserId(visitorId) != null) {
      throw new ApiException(ErrorKey.VISITOR_RECOVERY_INVALID);
    }
    var session = identities.createSession(visitorId, false);
    accounts.deleteVisitorRecovery(accountId);
    return session;
  }

  public String demoCode(String channel, String destination) {
    requireDemo();
    if ("email".equals(channel)) normalizeEmail(destination);
    else if ("phone".equals(channel)) normalizePhone(destination);
    else throw invalid(ErrorKey.ACCOUNT_CHANNEL);
    return properties.demoCode();
  }

  @Transactional
  public User register(
      User user, String email, String phone, String password, String emailCode, String phoneCode) {
    requireDemo();
    email = normalizeEmail(email);
    phone = normalizePhone(phone);
    validatePassword(password);
    if (!properties.demoCode().equals(emailCode) || !properties.demoCode().equals(phoneCode)) {
      throw invalid(ErrorKey.ACCOUNT_CODE);
    }
    if (accounts.findByUserId(user.id()) != null) {
      throw new ApiException(ErrorKey.ALREADY_REGISTERED);
    }
    try {
      accounts.insert(
          new Account(user.id(), email, phone, passwords.hash(password), clock.instant()));
    } catch (DuplicateKeyException e) {
      throw new ApiException(ErrorKey.ACCOUNT_EXISTS);
    }
    return user;
  }

  @Transactional
  public User registerSingle(
      User user, String channel, String destination, String password, String code) {
    requireDemo();
    String normalized = normalizeContact(channel, destination);
    validatePassword(password);
    validateCode(code);
    if (accounts.findByUserId(user.id()) != null) {
      throw new ApiException(ErrorKey.ALREADY_REGISTERED);
    }
    try {
      accounts.insert(
          new Account(
              user.id(),
              "email".equals(channel) ? normalized : null,
              "phone".equals(channel) ? normalized : null,
              passwords.hash(password),
              clock.instant()));
    } catch (DuplicateKeyException e) {
      throw new ApiException(ErrorKey.ACCOUNT_EXISTS);
    }
    return user;
  }

  @Transactional
  public User bind(User user, String channel, String destination, String code) {
    requireDemo();
    Account account = accounts.findByUserId(user.id());
    if (account == null) throw new ApiException(ErrorKey.ACCOUNT_REQUIRED);
    String normalized = normalizeContact(channel, destination);
    validateCode(code);
    try {
      int changed =
          "email".equals(channel)
              ? accounts.bindEmail(user.id(), normalized)
              : accounts.bindPhone(user.id(), normalized);
      if (changed == 0) throw new ApiException(ErrorKey.CONTACT_ALREADY_BOUND);
    } catch (DuplicateKeyException e) {
      throw new ApiException(ErrorKey.CONTACT_TAKEN);
    }
    return user;
  }

  private String normalizeContact(String channel, String destination) {
    if ("email".equals(channel)) return normalizeEmail(destination);
    if ("phone".equals(channel)) return normalizePhone(destination);
    throw invalid(ErrorKey.ACCOUNT_CHANNEL);
  }

  private void validateCode(String code) {
    if (!properties.demoCode().equals(code)) throw invalid(ErrorKey.ACCOUNT_CODE);
  }

  public IdentityService.SessionResult login(String identifier, String password) {
    if (identifier == null
        || identifier.length() > 254
        || password == null
        || password.length() > 128) throw invalid(ErrorKey.ACCOUNT_LOGIN_INPUT);
    String normalized = identifier.trim().toLowerCase(Locale.ROOT);
    Account account = accounts.findByIdentifier(normalized);
    if (account == null || !passwords.matches(password, account.passwordHash())) {
      SafeLog.write(
          () ->
              log.atInfo()
                  .addKeyValue("event", "auth.login.failed")
                  .addKeyValue("detail", account == null ? "UNKNOWN_IDENTIFIER" : "BAD_PASSWORD")
                  .log("Account login failed"));
      throw new ApiException(ErrorKey.LOGIN_FAILED);
    }
    return identities.createSession(account.userId(), false);
  }

  private void requireDemo() {
    if (!properties.demoEnabled()) throw new ApiException(ErrorKey.DEMO_DISABLED);
  }

  private String normalizeEmail(String input) {
    if (input == null) throw invalid(ErrorKey.ACCOUNT_EMAIL);
    String value = input.trim().toLowerCase(Locale.ROOT);
    if (value.length() > 254 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
      throw invalid(ErrorKey.ACCOUNT_EMAIL);
    return value;
  }

  private String normalizePhone(String input) {
    if (input == null) throw invalid(ErrorKey.ACCOUNT_PHONE);
    String value = input.trim();
    if (!value.matches("\\+?[0-9]{6,15}")) throw invalid(ErrorKey.ACCOUNT_PHONE_FORMAT);
    return value;
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8 || password.length() > 128)
      throw invalid(ErrorKey.ACCOUNT_PASSWORD);
  }

  private ApiException invalid(ErrorKey key) {
    return new ApiException(key);
  }
}
