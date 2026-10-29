package com.xiaoying.studio.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TemplateSeedIT {
  @Autowired private JdbcTemplate jdbc;

  @Test
  void everySeededTemplateHasAPackagedCoverAndResult() {
    List<String> keys =
        jdbc.query(
            "SELECT cover_key, preset_result_key FROM templates ORDER BY id",
            (row, index) -> row.getString(1) + "|" + row.getString(2));
    assertEquals(6, keys.size());
    for (String pair : keys) {
      for (String key : pair.split("\\|")) {
        assertTrue(new ClassPathResource("demo/" + key).exists(), "Missing asset: " + key);
      }
    }
  }
}
