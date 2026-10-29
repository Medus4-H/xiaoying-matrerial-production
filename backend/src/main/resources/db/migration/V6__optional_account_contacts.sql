-- Keep existing accounts and unique indexes; a new account may start with one contact.
ALTER TABLE accounts
  MODIFY COLUMN email VARCHAR(254) NULL,
  MODIFY COLUMN phone VARCHAR(16) NULL;
