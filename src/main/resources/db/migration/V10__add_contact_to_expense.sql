ALTER TABLE expense ADD COLUMN contact_id BIGINT;
ALTER TABLE expense ADD CONSTRAINT fk_expense_contact FOREIGN KEY (contact_id) REFERENCES contact(id) ON DELETE SET NULL;
