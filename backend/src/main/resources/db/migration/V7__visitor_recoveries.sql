-- One-time recovery link for explicitly separated legacy visitor data.
CREATE TABLE visitor_recoveries (
  account_user_id CHAR(36) PRIMARY KEY,
  visitor_user_id CHAR(36) NOT NULL UNIQUE,
  FOREIGN KEY (account_user_id) REFERENCES users(id),
  FOREIGN KEY (visitor_user_id) REFERENCES users(id)
);
