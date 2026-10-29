ALTER TABLE tasks
  ADD COLUMN error_message_key VARCHAR(128) NULL,
  ADD COLUMN error_message_args JSON NULL,
  ADD COLUMN error_detail VARCHAR(64) NULL;

ALTER TABLE task_steps
  ADD COLUMN error_message_key VARCHAR(128) NULL,
  ADD COLUMN error_message_args JSON NULL,
  ADD COLUMN error_detail VARCHAR(64) NULL;
