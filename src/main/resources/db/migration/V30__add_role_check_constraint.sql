ALTER TABLE app_user ADD CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'USER'));
