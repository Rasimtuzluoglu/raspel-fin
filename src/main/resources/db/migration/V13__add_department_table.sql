-- V13: Department tablosunun oluşturulması ve Card ile DepartmentBudget'a bağlanması

CREATE TABLE department (
    id SERIAL PRIMARY KEY,
    version INT DEFAULT 0,
    name VARCHAR(100) NOT NULL UNIQUE,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Insert unique departments from card and department_budget
INSERT INTO department (name)
SELECT DISTINCT department FROM card WHERE department IS NOT NULL AND department <> ''
ON CONFLICT (name) DO NOTHING;

INSERT INTO department (name)
SELECT DISTINCT department FROM department_budget WHERE department IS NOT NULL AND department <> ''
ON CONFLICT (name) DO NOTHING;

-- Add department_id columns
ALTER TABLE card ADD COLUMN department_id INT;
ALTER TABLE department_budget ADD COLUMN department_id INT;

-- Update department_id based on string names
UPDATE card c
SET department_id = d.id
FROM department d
WHERE c.department = d.name;

UPDATE department_budget db
SET department_id = d.id
FROM department d
WHERE db.department = d.name;

-- Drop old string columns
ALTER TABLE card DROP COLUMN department;
ALTER TABLE department_budget DROP COLUMN department;

-- Add foreign key constraints
ALTER TABLE card ADD CONSTRAINT fk_card_department FOREIGN KEY (department_id) REFERENCES department (id);
ALTER TABLE department_budget ADD CONSTRAINT fk_department_budget_department FOREIGN KEY (department_id) REFERENCES department (id);

-- Make department_id NOT NULL on department_budget (it was NOT NULL before)
ALTER TABLE department_budget ALTER COLUMN department_id SET NOT NULL;
