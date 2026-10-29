package com.xiaoying.studio.task;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TaskMapper {
  String FIELDS =
      "id,user_id,template_id,template_title_snapshot,requirements_snapshot,status,current_stage,failed_stage,error_code,error_message,result_key,simulation_snapshot,created_at,started_at,finished_at,updated_at,error_message_key,error_message_args,error_detail";

  @Insert(
      """
      INSERT INTO tasks(id,user_id,template_id,template_title_snapshot,requirements_snapshot,status,simulation_snapshot,created_at,updated_at)
      VALUES(#{id},#{userId},#{templateId},#{templateTitleSnapshot},#{requirementsSnapshot},'QUEUED',#{simulationSnapshot},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
      """)
  void insert(TaskRow task);

  @Insert(
      "INSERT INTO task_assets(task_id,asset_id,slot_index) VALUES(#{taskId},#{assetId},#{slot})")
  void insertAsset(
      @Param("taskId") String taskId, @Param("assetId") String assetId, @Param("slot") int slot);

  @Insert(
      "INSERT INTO task_steps(task_id,stage,sequence,status) VALUES(#{id},#{stage},#{sequence},'PENDING')")
  void insertStep(
      @Param("id") String id, @Param("stage") TaskStage stage, @Param("sequence") int sequence);

  @Select("SELECT " + FIELDS + " FROM tasks WHERE id=#{id}")
  TaskRow find(String id);

  @Select("SELECT " + FIELDS + " FROM tasks WHERE id=#{id} AND user_id=#{userId}")
  TaskRow findOwned(@Param("id") String id, @Param("userId") String userId);

  @Select(
      "SELECT stage,sequence,status,started_at,finished_at,error_code,error_message,error_message_key,error_message_args,error_detail FROM task_steps WHERE task_id=#{id} ORDER BY sequence")
  List<StepRow> steps(String id);

  @Select("SELECT asset_id FROM task_assets WHERE task_id=#{id} ORDER BY slot_index")
  List<String> assets(String id);

  @Select("SELECT preset_result_key FROM templates WHERE id=#{id}")
  String preset(String id);

  @Select(
      "SELECT "
          + FIELDS
          + " FROM tasks WHERE user_id=#{userId} AND status IN ('QUEUED','RUNNING') ORDER BY created_at DESC,id DESC")
  List<TaskRow> active(String userId);

  @Select({
    "<script>SELECT ",
    FIELDS,
    " FROM tasks WHERE user_id=#{userId}",
    "<if test='templateId != null'> AND template_id=#{templateId}</if>",
    " ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}</script>"
  })
  List<TaskRow> history(
      @Param("userId") String userId,
      @Param("templateId") String templateId,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Select({
    "<script>SELECT COUNT(*) FROM tasks WHERE user_id=#{userId}",
    "<if test='templateId != null'> AND template_id=#{templateId}</if></script>"
  })
  long count(@Param("userId") String userId, @Param("templateId") String templateId);

  @Update(
      "UPDATE tasks SET status='RUNNING',started_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='QUEUED'")
  int claim(String id);

  @Update(
      "UPDATE tasks SET current_stage=#{stage},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='RUNNING'")
  int updateStage(@Param("id") String id, @Param("stage") TaskStage stage);

  @Update(
      "UPDATE task_steps SET status='RUNNING',started_at=UTC_TIMESTAMP(3) WHERE task_id=#{id} AND stage=#{stage} AND status='PENDING'")
  int startStep(@Param("id") String id, @Param("stage") TaskStage stage);

  @Update(
      "UPDATE task_steps SET status='SUCCEEDED',finished_at=UTC_TIMESTAMP(3) WHERE task_id=#{id} AND stage=#{stage} AND status='RUNNING' AND EXISTS (SELECT 1 FROM tasks t WHERE t.id=#{id} AND t.status='RUNNING' AND t.current_stage=#{stage})")
  int completeStep(@Param("id") String id, @Param("stage") TaskStage stage);

  @Update(
      "UPDATE tasks SET status='SUCCEEDED',result_key=#{key},finished_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status='RUNNING' AND current_stage='FINALIZE'")
  int succeed(@Param("id") String id, @Param("key") String key);

  @Update(
      "UPDATE tasks SET status='FAILED',failed_stage=#{stage},error_code=#{code},error_message=#{message},error_message_key=#{messageKey},error_message_args=NULL,error_detail=#{detail},finished_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND status IN ('QUEUED','RUNNING')")
  int fail(
      @Param("id") String id,
      @Param("stage") TaskStage stage,
      @Param("code") String code,
      @Param("message") String message,
      @Param("messageKey") String messageKey,
      @Param("detail") String detail);

  @Update(
      "UPDATE task_steps SET status='FAILED',error_code=#{code},error_message=#{message},error_message_key=#{messageKey},error_message_args=NULL,error_detail=#{detail},finished_at=UTC_TIMESTAMP(3) WHERE task_id=#{id} AND status='RUNNING'")
  void failRunning(
      @Param("id") String id,
      @Param("code") String code,
      @Param("message") String message,
      @Param("messageKey") String messageKey,
      @Param("detail") String detail);

  @Update(
      "UPDATE task_steps SET status='SKIPPED',finished_at=UTC_TIMESTAMP(3) WHERE task_id=#{id} AND status='PENDING'")
  void skipPending(String id);

  @Select("SELECT id FROM tasks WHERE status IN ('QUEUED','RUNNING')")
  List<String> unfinished();
}
