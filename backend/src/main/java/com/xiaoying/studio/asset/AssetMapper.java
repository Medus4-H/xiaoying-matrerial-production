package com.xiaoying.studio.asset;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssetMapper {
  @Insert(
      """
      INSERT INTO assets(id,user_id,storage_key,original_name,mime_type,size_bytes,width,height,created_at)
      VALUES(#{id},#{userId},#{storageKey},#{originalName},#{mimeType},#{sizeBytes},#{width},#{height},#{createdAt})
      """)
  void insert(Asset asset);

  @Select(
      """
      SELECT id,user_id,storage_key,original_name,mime_type,size_bytes,width,height,created_at
      FROM assets WHERE id=#{id} AND user_id=#{userId}
      """)
  Asset findOwned(@Param("id") String id, @Param("userId") String userId);
}
