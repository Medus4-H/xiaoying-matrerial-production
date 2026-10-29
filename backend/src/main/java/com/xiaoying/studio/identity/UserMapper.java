package com.xiaoying.studio.identity;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
  @Select(
      "SELECT u.id,s.token_hash AS session_token_hash,s.created_at,s.expires_at FROM users u JOIN user_sessions s ON s.user_id=u.id WHERE s.token_hash=#{hash}")
  User findByHash(String hash);

  @Insert(
      "INSERT INTO users(id,session_token_hash,created_at,expires_at) VALUES(#{id},#{sessionTokenHash},#{createdAt},#{expiresAt})")
  void insert(User user);

  @Insert(
      "INSERT INTO user_sessions(token_hash,user_id,created_at,expires_at) VALUES(#{sessionTokenHash},#{id},#{createdAt},#{expiresAt})")
  void insertSession(User user);

  @Delete("DELETE FROM user_sessions WHERE token_hash=#{hash}")
  void deleteSession(String hash);
}
