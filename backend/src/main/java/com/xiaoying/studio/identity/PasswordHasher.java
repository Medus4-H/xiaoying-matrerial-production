package com.xiaoying.studio.identity;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {
  private final AccountProperties properties;
  private final SecureRandom random = new SecureRandom();

  public PasswordHasher(AccountProperties properties) {
    this.properties = properties;
  }

  public String hash(String password) {
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    int iterations = properties.passwordIterations();
    return "pbkdf2-sha256$"
        + iterations
        + "$"
        + Base64.getEncoder().encodeToString(salt)
        + "$"
        + Base64.getEncoder().encodeToString(derive(password, salt, iterations));
  }

  public boolean matches(String password, String encoded) {
    try {
      String[] parts = encoded.split("\\$");
      if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
      int iterations = Integer.parseInt(parts[1]);
      if (iterations < 100000 || iterations > 2000000) return false;
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      if (salt.length != 16 || expected.length != 32) return false;
      return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private byte[] derive(String password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Password hashing unavailable", e);
    } finally {
      spec.clearPassword();
    }
  }
}
