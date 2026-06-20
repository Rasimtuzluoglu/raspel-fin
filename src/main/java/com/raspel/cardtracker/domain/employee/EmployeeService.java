package com.raspel.cardtracker.domain.employee;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeTaskRepository employeeTaskRepository;
    private final AuditLogService auditLogService;

    // --- EMPLOYEE METHODS ---

    public List<Employee> findAllEmployees() {
        return employeeRepository.findAll();
    }

    public List<Employee> findAllActiveEmployees() {
        return employeeRepository.findAllByActiveTrue();
    }

    public Optional<Employee> findEmployeeById(Long id) {
        return employeeRepository.findById(id);
    }

    public Employee saveEmployee(Employee employee) {
        boolean isNew = employee.getId() == null;
        Employee saved = employeeRepository.save(employee);
        auditLogService.log(
                isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                "Eleman",
                saved.getId(),
                (isNew ? "Yeni eleman kaydedildi: " : "Eleman güncellendi: ") + saved.getFullName()
        );
        return saved;
    }

    public void deleteEmployee(Long id) {
        employeeRepository.findById(id).ifPresent(emp -> {
            auditLogService.log(AuditAction.DELETE, "Eleman", id, "Eleman silindi: " + emp.getFullName());
        });
        employeeRepository.deleteById(id);
    }

    // --- TASK METHODS ---

    public List<EmployeeTask> findAllTasks() {
        return employeeTaskRepository.findAllWithEmployee();
    }

    public Optional<EmployeeTask> findTaskById(Long id) {
        return employeeTaskRepository.findById(id);
    }

    public EmployeeTask saveTask(EmployeeTask task) {
        boolean isNew = task.getId() == null;
        EmployeeTask saved = employeeTaskRepository.save(task);
        auditLogService.log(
                isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                "Eleman Görevi",
                saved.getId(),
                (isNew ? "Yeni eleman görevi oluşturuldu: " : "Eleman görevi güncellendi: ") + saved.getTitle()
        );
        return saved;
    }

    public void deleteTask(Long id) {
        employeeTaskRepository.findById(id).ifPresent(task -> {
            auditLogService.log(AuditAction.DELETE, "Eleman Görevi", id, "Eleman görevi silindi: " + task.getTitle());
        });
        employeeTaskRepository.deleteById(id);
    }
}
