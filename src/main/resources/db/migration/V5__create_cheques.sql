CREATE TABLE cheque (
    id BIGSERIAL PRIMARY KEY,
    cheque_number VARCHAR(100) NOT NULL,
    bank VARCHAR(100) NOT NULL,
    maturity_date DATE NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    party VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
