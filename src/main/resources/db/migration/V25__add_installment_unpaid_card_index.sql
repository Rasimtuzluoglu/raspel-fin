CREATE INDEX IF NOT EXISTS idx_installment_unpaid_card ON installment_entry(is_paid, expense_id);
