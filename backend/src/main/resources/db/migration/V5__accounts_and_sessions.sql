CREATE TABLE accounts (
  user_id CHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  phone VARCHAR(16) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uq_accounts_email (email),
  UNIQUE KEY uq_accounts_phone (phone),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE user_sessions (
  token_hash CHAR(64) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  INDEX idx_session_user (user_id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

INSERT INTO user_sessions (token_hash, user_id, created_at, expires_at)
SELECT session_token_hash, id, created_at, expires_at FROM users;
