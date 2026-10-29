package com.xiaoying.studio.storage;

import com.xiaoying.studio.config.StudioProperties;
import java.io.IOException;
import java.nio.file.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage {
  private final Path root;

  public LocalFileStorage(StudioProperties properties) {
    this.root = properties.storageRoot().toAbsolutePath().normalize();
  }

  public String save(String id, String mimeType, byte[] bytes) throws IOException {
    if (!id.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid asset id");
    String key = "uploads/" + id + (mimeType.equals("image/png") ? ".png" : ".jpg");
    Path target = resolve(key);
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
    try {
      Files.write(temporary, bytes);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      return key;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public Resource read(String key) throws IOException {
    Path path = resolve(key);
    if (!Files.isRegularFile(path) || !Files.isReadable(path))
      throw new NoSuchFileException("asset");
    return new FileSystemResource(path);
  }

  public void delete(String key) throws IOException {
    Files.deleteIfExists(resolve(key));
  }

  private Path resolve(String key) {
    if (key == null || !key.matches("uploads/[0-9a-f-]{36}\\.(png|jpg)")) {
      throw new IllegalArgumentException("Invalid storage key");
    }
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
    return path;
  }
}
