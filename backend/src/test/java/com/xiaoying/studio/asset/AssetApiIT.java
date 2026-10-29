package com.xiaoying.studio.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AssetApiIT {
  private static final Path STORAGE = createStorage();
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  private Cookie visitor;

  private static Path createStorage() {
    try {
      return Files.createTempDirectory("studio-asset-test-");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add("studio.storage-root", STORAGE::toString);
  }

  @AfterAll
  static void cleanup() throws IOException {
    try (var paths = Files.walk(STORAGE)) {
      for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.deleteIfExists(path);
    }
  }

  @BeforeEach
  void session() throws Exception {
    visitor = newVisitor();
  }

  private Cookie newVisitor() throws Exception {
    return mvc.perform(post("/api/session"))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getCookie("studio_session");
  }

  private byte[] image(String format) throws IOException {
    var bytes = new ByteArrayOutputStream();
    assertThat(ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), format, bytes))
        .isTrue();
    return bytes.toByteArray();
  }

  @Test
  void pngAndJpegAreStoredAndOnlyOwnerCanRead() throws Exception {
    for (String format : new String[] {"png", "jpeg"}) {
      byte[] source = image(format);
      var file = new MockMultipartFile("file", "../photo." + format, "image/" + format, source);
      String body =
          mvc.perform(multipart("/api/assets").file(file).cookie(visitor))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();
      JsonNode asset = json.readTree(body);
      assertThat(asset.path("width").asInt()).isEqualTo(2);
      assertThat(asset.path("height").asInt()).isEqualTo(3);
      assertThat(asset.path("sizeBytes").asLong()).isEqualTo(source.length);
      assertThat(body).doesNotContain(STORAGE.toString()).doesNotContain("storageKey");
      String url = asset.path("previewUrl").asText();
      byte[] stored =
          mvc.perform(get(url).cookie(visitor))
              .andExpect(status().isOk())
              .andExpect(header().string("X-Content-Type-Options", "nosniff"))
              .andExpect(header().string("Cache-Control", "no-store"))
              .andExpect(content().contentTypeCompatibleWith("image/" + format))
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      assertThat(stored).isEqualTo(source);
      mvc.perform(get(url).cookie(newVisitor())).andExpect(status().isNotFound());
      mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void rejectsInvalidContentTypesEmptyAndOversizedImages() throws Exception {
    var corrupt = new MockMultipartFile("file", "bad.png", "image/png", new byte[] {1, 2, 3});
    mvc.perform(multipart("/api/assets").file(corrupt).cookie(visitor))
        .andExpect(status().isBadRequest());
    var mismatch = new MockMultipartFile("file", "bad.jpg", "image/jpeg", image("png"));
    mvc.perform(multipart("/api/assets").file(mismatch).cookie(visitor))
        .andExpect(status().isBadRequest());
    var unsupported = new MockMultipartFile("file", "bad.gif", "image/gif", new byte[] {1});
    mvc.perform(multipart("/api/assets").file(unsupported).cookie(visitor))
        .andExpect(status().isBadRequest());
    var empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
    mvc.perform(multipart("/api/assets").file(empty).cookie(visitor))
        .andExpect(status().isBadRequest());
    var largeDimensions = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(8193, 1, BufferedImage.TYPE_INT_RGB), "png", largeDimensions);
    var wide =
        new MockMultipartFile("file", "wide.png", "image/png", largeDimensions.toByteArray());
    mvc.perform(multipart("/api/assets").file(wide).cookie(visitor))
        .andExpect(status().isBadRequest());
    var tooManyPixels = new ByteArrayOutputStream();
    ImageIO.write(
        new BufferedImage(4001, 4000, BufferedImage.TYPE_BYTE_GRAY), "png", tooManyPixels);
    var dense =
        new MockMultipartFile("file", "dense.png", "image/png", tooManyPixels.toByteArray());
    mvc.perform(multipart("/api/assets").file(dense).cookie(visitor))
        .andExpect(status().isBadRequest());
    var huge = new MockMultipartFile("file", "huge.png", "image/png", new byte[5242881]);
    mvc.perform(multipart("/api/assets").file(huge).cookie(visitor))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void writesRequireIdentityAndAllowedOrigin() throws Exception {
    var file = new MockMultipartFile("file", "ok.png", "image/png", image("png"));
    mvc.perform(multipart("/api/assets").file(file)).andExpect(status().isUnauthorized());
    mvc.perform(
            multipart("/api/assets")
                .file(file)
                .cookie(visitor)
                .header("Origin", "https://untrusted.invalid"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/assets/../../secret/content").cookie(visitor))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void missingStoredFileIsReportedAsNotFound() throws Exception {
    var file = new MockMultipartFile("file", "ok.png", "image/png", image("png"));
    String body =
        mvc.perform(multipart("/api/assets").file(file).cookie(visitor))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode asset = json.readTree(body);
    String key =
        jdbc.queryForObject(
            "SELECT storage_key FROM assets WHERE id=?", String.class, asset.path("id").asText());
    Files.delete(STORAGE.resolve(key));
    mvc.perform(get(asset.path("previewUrl").asText()).cookie(visitor))
        .andExpect(status().isNotFound());
  }
}
