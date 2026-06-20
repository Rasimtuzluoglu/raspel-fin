-- V16: Add missing indexes for performance optimization
CREATE INDEX IF NOT EXISTS idx_cheque_maturity_date ON cheque(maturity_date);
CREATE INDEX IF NOT EXISTS idx_cheque_contact_id ON cheque(contact_id);
CREATE INDEX IF NOT EXISTS idx_cheque_status ON cheque(status);
CREATE INDEX IF NOT EXISTS idx_expense_contact_id ON expense(contact_id);
CREATE INDEX IF NOT EXISTS idx_card_department_id ON card(department_id);
CREATE INDEX IF NOT EXISTS idx_department_budget_department_id ON department_budget(department_id);
CREATE INDEX IF NOT EXISTS idx_installment_entry_is_paid ON installment_entry(is_paid);
