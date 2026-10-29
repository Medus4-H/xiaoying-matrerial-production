package com.xiaoying.studio.task.execution;

import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.config.StudioProperties;
import com.xiaoying.studio.template.TemplateMediaProperties;
import java.io.IOException;
import java.nio.file.*;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;

@Component
public class TaskFiles {
  private final Path root;
  private final TemplateMediaProperties media;
  private final ResourceLoader loader;

  public TaskFiles(StudioProperties config, TemplateMediaProperties media, ResourceLoader loader) {
    this.root = config.storageRoot().toAbsolutePath().normalize();
    this.media = media;
    this.loader = loader;
  }

  public void generate(String id, String presetKey) throws IOException {
    checkId(id);
    if (presetKey == null || !presetKey.matches("results/[A-Za-z0-9_-]+\\.jpg"))
      throw new IOException("Invalid preset image");
    Resource preset = loader.getResource(media.root() + presetKey);
    if (!preset.exists() || !preset.isReadable())
      throw new TaskExecutionException(ErrorKey.RESULT_SOURCE_UNAVAILABLE);
    Path tmp = temporary(id);
    Files.createDirectories(tmp.getParent());
    try (var in = preset.getInputStream()) {
      Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public String publish(String id) throws IOException {
    checkId(id);
    Path target = root.resolve("results").resolve(id + ".jpg");
    Files.createDirectories(target.getParent());
    Files.move(temporary(id), target, StandardCopyOption.ATOMIC_MOVE);
    return "results/" + id + ".jpg";
  }

  public Resource read(String key) throws IOException {
    if (key == null || !key.matches("results/[0-9a-f-]{36}\\.jpg"))
      throw new NoSuchFileException("result");
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path) || !Files.isReadable(path))
      throw new NoSuchFileException("result");
    return new FileSystemResource(path);
  }

  public void cleanup(String id) throws IOException {
    checkId(id);
    Files.deleteIfExists(temporary(id));
  }

  private Path temporary(String id) {
    return root.resolve("tmp").resolve(id + ".tmp");
  }

  private void checkId(String id) {
    if (id == null || !id.matches("[0-9a-f-]{36}"))
      throw new IllegalArgumentException("Invalid task id");
  }
}
