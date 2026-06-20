-- V11: Performans için indekslerin eklenmesi
-- Note: These indexes are intentionally redundant as safety nets
-- in case earlier migration indexes were missed or dropped.

CREATE INDEX IF NOT EXISTS idx_expense_date ON expense(expense_date);
CREATE INDEX IF NOT EXISTS idx_expense_card_id ON expense(card_id);
CREATE INDEX IF NOT EXISTS idx_card_department ON card(department);
