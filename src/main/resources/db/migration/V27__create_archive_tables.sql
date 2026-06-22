CREATE TABLE IF NOT EXISTS expense_archive (
    id BIGINT NOT NULL,
    card_id BIGINT,
    contact_id BIGINT,
    description VARCHAR(255) NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL,
    installments INT NOT NULL DEFAULT 1,
    expense_date DATE NOT NULL,
    category VARCHAR(50),
    tag VARCHAR(50),
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    original_amount NUMERIC(15,2),
    exchange_rate NUMERIC(15,4) NOT NULL DEFAULT 1.0000,
    receipt_path VARCHAR(255),
    receipt_content_type VARCHAR(100),
    created_by VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS installment_entry_archive (
    id BIGINT NOT NULL,
    expense_id BIGINT NOT NULL,
    due_year INT NOT NULL,
    due_month INT NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    is_paid BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
