-- V17: Change department.id and referencing columns to BIGINT to resolve Hibernate validation issues
ALTER TABLE card DROP CONSTRAINT IF EXISTS fk_card_department;
ALTER TABLE department_budget DROP CONSTRAINT IF EXISTS fk_department_budget_department;

ALTER TABLE department ALTER COLUMN id TYPE BIGINT;
ALTER TABLE card ALTER COLUMN department_id TYPE BIGINT;
ALTER TABLE department_budget ALTER COLUMN department_id TYPE BIGINT;

ALTER TABLE card ADD CONSTRAINT fk_card_department FOREIGN KEY (department_id) REFERENCES department (id);
ALTER TABLE department_budget ADD CONSTRAINT fk_department_budget_department FOREIGN KEY (department_id) REFERENCES department (id);
