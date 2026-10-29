package com.xiaoying.studio.identity;

import java.time.Instant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountMapper {
  @Select(
      "SELECT user_id,email,phone,password_hash,created_at FROM accounts WHERE user_id=#{userId}")
  Account findByUserId(String userId);

  @Select(
      "SELECT user_id,email,phone,password_hash,created_at FROM accounts WHERE email=#{identifier} OR phone=#{identifier}")
  Account findByIdentifier(String identifier);

  @Insert(
      "INSERT INTO accounts(user_id,email,phone,password_hash,created_at) VALUES(#{userId},#{email,jdbcType=VARCHAR},#{phone,jdbcType=VARCHAR},#{passwordHash},#{createdAt})")
  void insert(Account account);

  @Update("UPDATE accounts SET email=#{destination} WHERE user_id=#{userId} AND email IS NULL")
  int bindEmail(@Param("userId") String userId, @Param("destination") String destination);

  @Update("UPDATE accounts SET phone=#{destination} WHERE user_id=#{userId} AND phone IS NULL")
  int bindPhone(@Param("userId") String userId, @Param("destination") String destination);

  @Select(
      "SELECT EXISTS(SELECT 1 FROM assets WHERE user_id=#{userId}) OR EXISTS(SELECT 1 FROM tasks WHERE user_id=#{userId})")
  boolean hasGuestData(String userId);

  @Select(
      "SELECT visitor_user_id FROM visitor_recoveries WHERE account_user_id=#{userId} FOR UPDATE")
  String lockVisitorRecovery(String userId);

  @Delete("DELETE FROM visitor_recoveries WHERE account_user_id=#{userId}")
  int deleteVisitorRecovery(String userId);

  record Account(
      String userId, String email, String phone, String passwordHash, Instant createdAt) {}
}
