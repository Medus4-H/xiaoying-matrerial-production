package com.xiaoying.studio.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ErrorCatalogTest {
  @Test
  void everyDeclaredErrorHasDistinctKeysAndBothSafeLanguages() {
    var catalog = ErrorCatalog.instance();
    var chinese = catalog.bundle("zh-CN");
    var english = catalog.bundle("en-US");
    assertThat(chinese.items()).hasSize(ErrorKey.values().length);
    assertThat(english.items().keySet()).isEqualTo(chinese.items().keySet());
    for (ErrorKey key : ErrorKey.values()) {
      assertThat(catalog.definition(key).code()).isNotBlank();
      assertThat(catalog.message(key, "zh-CN")).isNotBlank();
      assertThat(catalog.message(key, "en")).isNotBlank();
    }
  }

  @Test
  void unsupportedOrMalformedLanguageFallsBackAndQualityIsRespected() {
    var catalog = ErrorCatalog.instance();
    assertThat(catalog.locale("fr-FR")).isEqualTo("zh-CN");
    assertThat(catalog.locale("not a language; q=oops")).isEqualTo("zh-CN");
    assertThat(catalog.locale("zh;q=0.2,en;q=0.9")).isEqualTo("en");
    assertThat(catalog.locale("en;q=0")).isEqualTo("zh-CN");
  }

  @Test
  void missingOrMalformedPrimaryCatalogUsesThePackagedFallback() throws Exception {
    for (boolean missing : new boolean[] {true, false}) {
      try (var fallback = new ClassPathResource("error-catalog-fallback.json").getInputStream()) {
        var primary =
            missing ? null : new ByteArrayInputStream("{broken".getBytes(StandardCharsets.UTF_8));
        var catalog = ErrorCatalog.loadWithFallback(primary, fallback);
        assertThat(catalog.definition(ErrorKey.LOGIN_FAILED).code()).isEqualTo("LOGIN_FAILED");
        assertThat(catalog.message(ErrorKey.LOGIN_FAILED, "en"))
            .isEqualTo("Incorrect account or password");
      }
    }
  }

  @Test
  void missingBothCatalogsFailsSafelyWithoutInventingErrorCodes() {
    assertThatThrownBy(() -> ErrorCatalog.loadWithFallback(null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No valid error catalog available");
  }
}
