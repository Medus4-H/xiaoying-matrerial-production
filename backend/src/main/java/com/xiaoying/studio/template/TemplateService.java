package com.xiaoying.studio.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.common.ErrorKey;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {
  private final TemplateMapper mapper;
  private final ObjectMapper json;

  public TemplateService(TemplateMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  public List<TemplateDetail> list(String category) {
    return mapper.list(category).stream().map(this::view).toList();
  }

  public List<TemplateMapper.Category> categories() {
    return mapper.categories();
  }

  public TemplateRow require(String id) {
    var row = mapper.find(id);
    if (row == null) {
      throw new ApiException(ErrorKey.TEMPLATE_NOT_FOUND);
    }
    return row;
  }

  public TemplateDetail get(String id) {
    return view(require(id));
  }

  private TemplateDetail view(TemplateRow row) {
    try {
      return new TemplateDetail(
          row.id(),
          row.title(),
          row.categoryId(),
          row.categoryName(),
          row.description(),
          "/api/templates/" + row.id() + "/cover",
          row.requiredImageCount(),
          json.readValue(row.acceptedMimeTypes(), new TypeReference<List<String>>() {}),
          row.maxFileSizeBytes());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid template MIME configuration", e);
    }
  }

  public record TemplateDetail(
      String id,
      String title,
      String categoryId,
      String categoryName,
      String description,
      String coverUrl,
      int requiredImageCount,
      List<String> acceptedMimeTypes,
      long maxFileSizeBytes) {}
}
