ALTER TABLE employee ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE employee DROP CONSTRAINT IF EXISTS fk_employee_user;
ALTER TABLE employee ADD CONSTRAINT fk_employee_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE employee_task ADD COLUMN IF NOT EXISTS atayan_user_id BIGINT;
ALTER TABLE employee_task DROP CONSTRAINT IF EXISTS fk_task_atayan;
ALTER TABLE employee_task ADD CONSTRAINT fk_task_atayan FOREIGN KEY (atayan_user_id) REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE employee_task ADD COLUMN IF NOT EXISTS tamamlayan_user_id BIGINT;
ALTER TABLE employee_task DROP CONSTRAINT IF EXISTS fk_task_tamamlayan;
ALTER TABLE employee_task ADD CONSTRAINT fk_task_tamamlayan FOREIGN KEY (tamamlayan_user_id) REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE employee_task ADD COLUMN IF NOT EXISTS tamamlanma_tarihi TIMESTAMP;
