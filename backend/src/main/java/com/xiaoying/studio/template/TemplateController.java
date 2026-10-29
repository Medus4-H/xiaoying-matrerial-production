package com.xiaoying.studio.template;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TemplateController {
  private static final Map<String, MediaType> COVER_TYPES =
      Map.of(
          "jpg", MediaType.IMAGE_JPEG,
          "png", MediaType.IMAGE_PNG,
          "svg", MediaType.valueOf("image/svg+xml"));

  private final TemplateService service;
  private final ResourceLoader resources;
  private final TemplateMediaProperties config;

  public TemplateController(
      TemplateService service, ResourceLoader resources, TemplateMediaProperties config) {
    this.service = service;
    this.resources = resources;
    this.config = config;
  }

  @GetMapping("/categories")
  public Map<String, ?> categories() {
    return Map.of("items", service.categories());
  }

  @GetMapping("/templates")
  public Map<String, ?> list(@RequestParam(required = false) String categoryId) {
    return Map.of("items", service.list(categoryId));
  }

  @GetMapping("/templates/{id}")
  public TemplateService.TemplateDetail get(@PathVariable String id) {
    return service.get(id);
  }

  @GetMapping("/templates/{id}/cover")
  public ResponseEntity<Resource> cover(@PathVariable String id) {
    String key = service.require(id).coverKey();
    if (!key.matches("covers/[A-Za-z0-9_-]+\\.(jpg|png|svg)")) {
      throw new ApiException(ErrorKey.COVER_NOT_FOUND);
    }
    Resource resource = resources.getResource(config.root() + key);
    if (!resource.exists()) {
      throw new ApiException(ErrorKey.COVER_NOT_FOUND);
    }
    String extension = key.substring(key.lastIndexOf('.') + 1);
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .contentType(COVER_TYPES.get(extension))
        .body(resource);
  }
}
