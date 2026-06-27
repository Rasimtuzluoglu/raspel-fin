CREATE TABLE budget (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER DEFAULT 0,
    department_id BIGINT REFERENCES department(id),
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    limit_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(department_id, year, month)
);
CREATE INDEX IF NOT EXISTS idx_budget_dept_year_month ON budget(department_id, year, month);
