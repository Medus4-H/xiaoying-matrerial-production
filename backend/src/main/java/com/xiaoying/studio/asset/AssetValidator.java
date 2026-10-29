package com.xiaoying.studio.asset;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AssetValidator {
  private final AssetProperties limits;

  public AssetValidator(AssetProperties limits) {
    this.limits = limits;
  }

  public ValidImage validate(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) throw bad(ErrorKey.ASSET_EMPTY);
    if (file.getSize() > limits.maxBytes()) {
      throw new ApiException(ErrorKey.ASSET_TOO_LARGE);
    }
    String declared = file.getContentType();
    if (!"image/jpeg".equals(declared) && !"image/png".equals(declared)) {
      throw bad(ErrorKey.ASSET_TYPE);
    }
    byte[] bytes = file.getBytes();
    if (bytes.length == 0 || bytes.length > limits.maxBytes())
      throw bad(ErrorKey.ASSET_SIZE_INVALID);
    try (ImageInputStream stream =
        ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (stream == null) throw bad(ErrorKey.ASSET_UNREADABLE);
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw bad(ErrorKey.ASSET_CONTENT);
      ImageReader reader = readers.next();
      try {
        reader.setInput(stream, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        String actual =
            switch (format) {
              case "jpeg", "jpg" -> "image/jpeg";
              case "png" -> "image/png";
              default -> "";
            };
        if (!declared.equals(actual)) throw bad(ErrorKey.ASSET_MIME_MISMATCH);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width <= 0
            || height <= 0
            || width > limits.maxDimension()
            || height > limits.maxDimension()
            || (long) width * height > limits.maxPixels()) throw bad(ErrorKey.ASSET_DIMENSIONS);
        if (reader.read(0) == null) throw bad(ErrorKey.ASSET_DECODE);
        return new ValidImage(bytes, actual, width, height);
      } catch (IOException | IllegalArgumentException e) {
        throw bad(ErrorKey.ASSET_CORRUPT);
      } finally {
        reader.dispose();
      }
    }
  }

  private ApiException bad(ErrorKey key) {
    return new ApiException(key);
  }

  public record ValidImage(byte[] bytes, String mimeType, int width, int height) {}
}
