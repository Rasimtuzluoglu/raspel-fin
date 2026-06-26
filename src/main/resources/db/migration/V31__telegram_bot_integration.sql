ALTER TABLE app_user ADD COLUMN IF NOT EXISTS telegram_chat_id BIGINT UNIQUE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS telegram_verification_code VARCHAR(20);
