package com.xiaoying.studio.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

/** Immutable, versioned source of public error codes and safe translations. */
public final class ErrorCatalog {
  public static final String DEFAULT_LOCALE = "zh-CN";
  private static final Logger log = LoggerFactory.getLogger(ErrorCatalog.class);
  private static final ErrorCatalog INSTANCE = load();
  private final Catalog catalog;

  private ErrorCatalog(Catalog catalog) {
    this.catalog = catalog;
    validate();
  }

  public static ErrorCatalog instance() {
    return INSTANCE;
  }

  private static ErrorCatalog load() {
    try (var primary = optionalResource("error-catalog.json");
        var fallback = optionalResource("error-catalog-fallback.json")) {
      return loadWithFallback(primary, fallback);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load error catalog", e);
    }
  }

  private static InputStream optionalResource(String path) {
    var resource = new ClassPathResource(path);
    try {
      return resource.exists() ? resource.getInputStream() : null;
    } catch (IOException ignored) {
      return null;
    }
  }

  static ErrorCatalog loadWithFallback(InputStream primary, InputStream fallback) {
    var mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    if (primary != null) {
      try {
        return new ErrorCatalog(mapper.readValue(primary, Catalog.class));
      } catch (IOException | RuntimeException ignored) {
        // Do not log content from an invalid catalog.
      }
    }
    log.warn("Primary error catalog unavailable; using the packaged fallback");
    if (fallback == null) throw new IllegalStateException("No valid error catalog available");
    try {
      return new ErrorCatalog(mapper.readValue(fallback, Catalog.class));
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("No valid error catalog available", e);
    }
  }

  private void validate() {
    if (catalog.version() < 1 || catalog.entries() == null) {
      throw new IllegalStateException("Invalid error catalog");
    }
    Set<String> expected =
        Arrays.stream(ErrorKey.values()).map(Enum::name).collect(Collectors.toSet());
    if (!catalog.entries().keySet().equals(expected)) {
      throw new IllegalStateException("Error catalog and ErrorKey differ");
    }
    var keys = new java.util.HashSet<String>();
    for (var entry : catalog.entries().values()) {
      if (entry.code() == null
          || !entry.code().matches("[A-Z][A-Z0-9_]*")
          || HttpStatus.resolve(entry.status()) == null
          || entry.key() == null
          || !entry.key().matches("[a-z][a-z0-9_.]*")
          || !keys.add(entry.key())
          || entry.messages() == null
          || entry.messages().get(DEFAULT_LOCALE) == null
          || entry.messages().get(DEFAULT_LOCALE).isBlank()
          || entry.messages().get("en") == null
          || entry.messages().get("en").isBlank()
          || entry.args() == null
          || !entry.args().isEmpty()
          || entry.messages().values().stream().anyMatch(s -> s.contains("{") || s.contains("}"))) {
        throw new IllegalStateException("Invalid error catalog entry");
      }
    }
  }

  public Definition definition(ErrorKey key) {
    return catalog.entries().get(key.name());
  }

  public String message(ErrorKey key, String language) {
    var messages = definition(key).messages();
    return messages.getOrDefault(locale(language), messages.get(DEFAULT_LOCALE));
  }

  public ErrorKey publicKey(String code, String messageKey) {
    if (code == null || messageKey == null) return null;
    for (ErrorKey key : ErrorKey.values()) {
      Definition item = definition(key);
      if (item.code().equals(code) && item.key().equals(messageKey)) return key;
    }
    return null;
  }

  public boolean isSafeLegacyMessage(String message) {
    if (message == null) return false;
    for (ErrorKey key : ErrorKey.values()) {
      String known = message(key, DEFAULT_LOCALE);
      if (known.equals(message)) return true;
      if ((message(ErrorKey.ASSET_CHECK_FAILED, DEFAULT_LOCALE) + "：" + known).equals(message))
        return true;
    }
    return false;
  }

  public Bundle bundle(String language) {
    String selected = locale(language);
    Map<String, String> values = new LinkedHashMap<>();
    catalog.entries().values().forEach(e -> values.put(e.key(), e.messages().get(selected)));
    return new Bundle(catalog.version(), selected, Collections.unmodifiableMap(values));
  }

  public String locale(String header) {
    if (header == null || header.isBlank()) return DEFAULT_LOCALE;
    try {
      for (var range : Locale.LanguageRange.parse(header)) {
        if (range.getWeight() <= 0) continue;
        String name = range.getRange();
        if (name.equals("en") || name.startsWith("en-")) return "en";
        if (name.equals("zh") || name.startsWith("zh-")) return DEFAULT_LOCALE;
      }
    } catch (IllegalArgumentException ignored) {
      // Malformed language preference falls back to the supported default.
    }
    return DEFAULT_LOCALE;
  }

  public record Catalog(int version, Map<String, Definition> entries) {}

  public record Definition(
      String code, int status, String key, Map<String, String> messages, List<String> args) {}

  public record Bundle(int version, String locale, Map<String, String> items) {}
}
