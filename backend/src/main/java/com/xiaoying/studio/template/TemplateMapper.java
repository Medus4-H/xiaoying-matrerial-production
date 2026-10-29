package com.xiaoying.studio.template;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TemplateMapper {
  String COLUMNS =
      "id,title,category_id,category_name,description,cover_key,required_image_count,accepted_mime_types,max_file_size_bytes";

  @Select(
      "SELECT "
          + COLUMNS
          + " FROM templates WHERE (#{categoryId} IS NULL OR category_id=#{categoryId}) ORDER BY sort_order,id")
  List<TemplateRow> list(@Param("categoryId") String categoryId);

  @Select("SELECT " + COLUMNS + " FROM templates WHERE id=#{id}")
  TemplateRow find(String id);

  @Select(
      "SELECT DISTINCT category_id AS id,category_name AS name FROM templates ORDER BY category_id")
  List<Category> categories();

  record Category(String id, String name) {}
}
