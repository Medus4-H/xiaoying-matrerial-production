package com.xiaoying.studio.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaoying.studio.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityServiceTest {
  @Test
  void createsHashedTokenAndReusesValidIdentity() {
    var mapper = mock(UserMapper.class);
    var service =
        new IdentityService(
            mapper,
            new SessionProperties("studio_session", Duration.ofDays(7), false, List.of()),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var created = service.createOrReuse(null);
    assertNotEquals(created.token(), created.user().sessionTokenHash());
    assertEquals(64, created.user().sessionTokenHash().length());
    verify(mapper).insert(created.user());

    when(mapper.findByHash(created.user().sessionTokenHash())).thenReturn(created.user());
    var reused = service.createOrReuse(created.token());
    assertEquals(created.user().id(), reused.user().id());
    verify(mapper, times(1)).insert(any());
  }

  @Test
  void rejectsMissingAndExpiredSessions() {
    var mapper = mock(UserMapper.class);
    var service =
        new IdentityService(
            mapper,
            new SessionProperties("s", Duration.ofDays(7), false, List.of()),
            Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));
    assertThrows(ApiException.class, () -> service.resolve(null));
    when(mapper.findByHash(anyString()))
        .thenReturn(new User("u", "hash", Instant.EPOCH, Instant.EPOCH));
    assertThrows(ApiException.class, () -> service.resolve("expired"));
  }
}
