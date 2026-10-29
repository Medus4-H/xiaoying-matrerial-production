-- Architecture only: no seed templates or user records.
CREATE TABLE users (
 id CHAR(36) PRIMARY KEY, session_token_hash CHAR(64) NOT NULL UNIQUE,
 created_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL
);
CREATE TABLE templates (
 id VARCHAR(64) PRIMARY KEY, title VARCHAR(128) NOT NULL,
 category_id VARCHAR(64) NOT NULL, category_name VARCHAR(64) NOT NULL,
 description TEXT NOT NULL, cover_key VARCHAR(512) NOT NULL,
 required_image_count INT NOT NULL, accepted_mime_types JSON NOT NULL,
 max_file_size_bytes BIGINT NOT NULL, preset_result_key VARCHAR(512) NOT NULL,
 sort_order INT NOT NULL DEFAULT 0
);
CREATE TABLE assets (
 id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL,
 storage_key VARCHAR(512) NOT NULL, original_name VARCHAR(255) NOT NULL,
 mime_type VARCHAR(64) NOT NULL, size_bytes BIGINT NOT NULL,
 width INT NOT NULL, height INT NOT NULL, created_at DATETIME(3) NOT NULL,
 INDEX idx_assets_user(user_id), FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE tasks (
 id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, template_id VARCHAR(64) NOT NULL,
 template_title_snapshot VARCHAR(128) NOT NULL, requirements_snapshot JSON NOT NULL,
 status VARCHAR(24) NOT NULL, current_stage VARCHAR(24), failed_stage VARCHAR(24),
 error_code VARCHAR(64), error_message VARCHAR(1024), result_key VARCHAR(512),
 simulation_snapshot JSON NOT NULL, created_at DATETIME(3) NOT NULL,
 started_at DATETIME(3), finished_at DATETIME(3), updated_at DATETIME(3) NOT NULL,
 INDEX idx_tasks_history(user_id,created_at,id), INDEX idx_tasks_active(user_id,status),
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(template_id) REFERENCES templates(id)
);
CREATE TABLE task_assets (
 task_id CHAR(36) NOT NULL, asset_id CHAR(36) NOT NULL, slot_index INT NOT NULL,
 PRIMARY KEY(task_id,slot_index), UNIQUE KEY uq_task_asset(task_id,asset_id),
 FOREIGN KEY(task_id) REFERENCES tasks(id), FOREIGN KEY(asset_id) REFERENCES assets(id)
);
CREATE TABLE task_steps (
 task_id CHAR(36) NOT NULL, stage VARCHAR(24) NOT NULL, sequence INT NOT NULL,
 status VARCHAR(24) NOT NULL, started_at DATETIME(3), finished_at DATETIME(3),
 error_code VARCHAR(64), error_message VARCHAR(1024), PRIMARY KEY(task_id,stage),
 UNIQUE KEY uq_task_sequence(task_id,sequence), FOREIGN KEY(task_id) REFERENCES tasks(id)
);
