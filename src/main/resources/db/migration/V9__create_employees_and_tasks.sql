-- Elemanlar (Employee) tablosu
CREATE TABLE employee (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(50),
    department VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Görevler (EmployeeTask) tablosu
CREATE TABLE employee_task (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    assigned_to BIGINT REFERENCES employee(id) ON DELETE SET NULL,
    due_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'TODO', -- 'TODO', 'IN_PROGRESS', 'COMPLETED'
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', -- 'LOW', 'MEDIUM', 'HIGH'
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_employee_task_assigned_to ON employee_task(assigned_to);
CREATE INDEX idx_employee_task_status ON employee_task(status);
CREATE INDEX idx_employee_task_due_date ON employee_task(due_date);
