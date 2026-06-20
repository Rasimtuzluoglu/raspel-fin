CREATE INDEX IF NOT EXISTS idx_expense_category ON expense(category);
CREATE INDEX IF NOT EXISTS idx_expense_created_by ON expense(created_by);
CREATE INDEX IF NOT EXISTS idx_card_bank ON card(bank);
CREATE INDEX IF NOT EXISTS idx_card_holder_name ON card(holder_name);
CREATE INDEX IF NOT EXISTS idx_employee_email ON employee(email);
CREATE INDEX IF NOT EXISTS idx_contact_email ON contact(email);
CREATE INDEX IF NOT EXISTS idx_installment_due_ym_paid ON installment_entry(due_year, due_month, is_paid);
