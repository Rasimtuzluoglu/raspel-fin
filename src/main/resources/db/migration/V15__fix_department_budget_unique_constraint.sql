-- V15: Add unique constraint on department_budget table for (department_id, budget_year, budget_month)
ALTER TABLE department_budget
ADD CONSTRAINT uq_dept_id_year_month
UNIQUE(department_id, budget_year, budget_month);
