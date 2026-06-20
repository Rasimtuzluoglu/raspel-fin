CREATE TABLE expense (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES card(id),
    description VARCHAR(255) NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL,
    installments INT NOT NULL DEFAULT 1,
    expense_date DATE NOT NULL,
    category VARCHAR(50),
    created_by VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE installment_entry (
    id BIGSERIAL PRIMARY KEY,
    expense_id BIGINT NOT NULL REFERENCES expense(id) ON DELETE CASCADE,
    due_year INT NOT NULL,
    due_month INT NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    is_paid BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_expense_card_id ON expense(card_id);
CREATE INDEX idx_expense_date ON expense(expense_date);
CREATE INDEX idx_installment_due ON installment_entry(due_year, due_month);
CREATE INDEX idx_installment_expense ON installment_entry(expense_id);
