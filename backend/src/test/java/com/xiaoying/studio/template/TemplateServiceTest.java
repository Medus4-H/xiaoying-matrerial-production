package com.xiaoying.studio.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateServiceTest {
  @Test
  void missingTemplateIsNotFound() {
    var service = new TemplateService(mock(TemplateMapper.class), new ObjectMapper());
    var error = assertThrows(ApiException.class, () -> service.get("missing"));
    assertEquals(404, error.status().value());
  }

  @Test
  void readsImageRequirementsFromDatabase() {
    var mapper = mock(TemplateMapper.class);
    when(mapper.find("demo"))
        .thenReturn(
            new TemplateRow(
                "demo",
                "Title",
                "creative",
                "创意",
                "Desc",
                "covers/demo.svg",
                2,
                "[\"image/jpeg\",\"image/png\"]",
                5242880L));
    var detail = new TemplateService(mapper, new ObjectMapper()).get("demo");
    assertEquals(2, detail.requiredImageCount());
    assertEquals(List.of("image/jpeg", "image/png"), detail.acceptedMimeTypes());
    assertEquals("/api/templates/demo/cover", detail.coverUrl());
  }
}
