package com.xiaoying.studio.asset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.xiaoying.studio.identity.User;
import com.xiaoying.studio.storage.LocalFileStorage;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AssetServiceTest {
  @Test
  void deletesStoredFileWhenDatabaseInsertFails() throws Exception {
    var mapper = mock(AssetMapper.class);
    var validator = mock(AssetValidator.class);
    var storage = mock(LocalFileStorage.class);
    var file = new MockMultipartFile("file", "x.png", "image/png", new byte[] {1});
    when(validator.validate(file))
        .thenReturn(new AssetValidator.ValidImage(new byte[] {1}, "image/png", 1, 1));
    when(storage.save(any(), eq("image/png"), any())).thenReturn("uploads/test.png");
    doThrow(new IllegalStateException("database failure")).when(mapper).insert(any());
    var service = new AssetService(mapper, validator, storage, Clock.systemUTC());
    var user = new User("u", "hash", Instant.EPOCH, Instant.MAX);
    assertThrows(IllegalStateException.class, () -> service.upload(user, file));
    verify(storage).delete("uploads/test.png");
  }

  @Test
  void taskInputsRequireOwnedDistinctAssetsMatchingTemplate() throws Exception {
    var mapper = mock(AssetMapper.class);
    var storage = mock(LocalFileStorage.class);
    var service = new AssetService(mapper, mock(AssetValidator.class), storage, Clock.systemUTC());
    var user = new User("owner", "hash", Instant.EPOCH, Instant.MAX);
    String first = "11111111-1111-1111-1111-111111111111";
    String second = "22222222-2222-2222-2222-222222222222";
    var a =
        new Asset(first, "owner", "uploads/a.jpg", "a.jpg", "image/jpeg", 100, 1, 1, Instant.EPOCH);
    var b =
        new Asset(second, "owner", "uploads/b.png", "b.png", "image/png", 200, 1, 1, Instant.EPOCH);
    when(mapper.findOwned(first, "owner")).thenReturn(a);
    when(mapper.findOwned(second, "owner")).thenReturn(b);
    when(storage.read(any())).thenReturn(mock(org.springframework.core.io.Resource.class));
    var requirements =
        new TemplateRequirements(2, java.util.Set.of("image/jpeg", "image/png"), 250);
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.List.of(a, b),
        service.requireOwnedAssets(user, java.util.List.of(first, second), requirements));
    assertThrows(
        com.xiaoying.studio.common.ApiException.class,
        () -> service.requireOwnedAssets(user, java.util.List.of(first, first), requirements));
    assertThrows(
        com.xiaoying.studio.common.ApiException.class,
        () -> service.requireOwnedAssets(user, java.util.List.of(first), requirements));
    assertThrows(
        com.xiaoying.studio.common.ApiException.class,
        () ->
            service.requireOwnedAssets(
                user,
                java.util.List.of(first, second),
                new TemplateRequirements(2, java.util.Set.of("image/jpeg"), 250)));
    assertThrows(
        com.xiaoying.studio.common.ApiException.class,
        () ->
            service.requireOwnedAssets(
                user,
                java.util.List.of(first, second),
                new TemplateRequirements(2, java.util.Set.of("image/jpeg", "image/png"), 150)));
    when(mapper.findOwned(second, "owner")).thenReturn(null);
    assertThrows(
        com.xiaoying.studio.common.ApiException.class,
        () -> service.requireOwnedAssets(user, java.util.List.of(first, second), requirements));
  }
}
