package com.raspel.cardtracker.domain.employee;

import com.raspel.cardtracker.domain.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeTaskRepository employeeTaskRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EmployeeService employeeService;

    // --- Employee Tests ---

    @Test
    void saveEmployee_shouldCreateNewEmployeeWithAuditLog() {
        Employee employee = new Employee();
        employee.setFirstName("Ahmet");
        employee.setLastName("Yılmaz");

        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        Employee saved = employeeService.saveEmployee(employee);

        assertThat(saved.getId()).isEqualTo(1L);
        verify(auditLogService).log(any(), eq("Eleman"), any(), contains("Yeni eleman kaydedildi"));
    }

    @Test
    void saveEmployee_shouldUpdateExistingEmployeeWithAuditLog() {
        Employee employee = new Employee();
        employee.setId(5L);
        employee.setFirstName("Mehmet");
        employee.setLastName("Demir");

        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        employeeService.saveEmployee(employee);

        verify(auditLogService).log(any(), eq("Eleman"), any(), contains("Eleman güncellendi"));
    }

    @Test
    void deleteEmployee_shouldDeleteWithAuditLog() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setFirstName("Silinecek");
        employee.setLastName("Kişi");

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        employeeService.deleteEmployee(1L);

        verify(employeeRepository).deleteById(1L);
        verify(auditLogService).log(any(), eq("Eleman"), eq(1L), contains("Eleman silindi"));
    }

    @Test
    void findAllEmployees_shouldReturnAllEmployees() {
        when(employeeRepository.findAll()).thenReturn(List.of(new Employee(), new Employee()));

        List<Employee> result = employeeService.findAllEmployees();

        assertThat(result).hasSize(2);
    }

    @Test
    void findAllActiveEmployees_shouldDelegateToRepository() {
        when(employeeRepository.findAllByActiveTrue()).thenReturn(List.of(new Employee()));

        List<Employee> result = employeeService.findAllActiveEmployees();

        assertThat(result).hasSize(1);
        verify(employeeRepository).findAllByActiveTrue();
    }

    @Test
    void findEmployeeById_shouldReturnEmptyWhenNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Employee> result = employeeService.findEmployeeById(99L);

        assertThat(result).isEmpty();
    }

    // --- Task Tests ---

    @Test
    void saveTask_shouldCreateNewTaskWithAuditLog() {
        EmployeeTask task = new EmployeeTask();
        task.setTitle("Görev 1");

        when(employeeTaskRepository.save(any(EmployeeTask.class))).thenAnswer(inv -> {
            EmployeeTask t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        EmployeeTask saved = employeeService.saveTask(task);

        assertThat(saved.getId()).isEqualTo(10L);
        verify(auditLogService).log(any(), eq("Eleman Görevi"), any(), contains("Yeni eleman görevi oluşturuldu"));
    }

    @Test
    void saveTask_shouldUpdateExistingTaskWithAuditLog() {
        EmployeeTask task = new EmployeeTask();
        task.setId(20L);
        task.setTitle("Güncellenmiş Görev");

        when(employeeTaskRepository.save(any(EmployeeTask.class))).thenReturn(task);

        employeeService.saveTask(task);

        verify(auditLogService).log(any(), eq("Eleman Görevi"), any(), contains("Eleman görevi güncellendi"));
    }

    @Test
    void deleteTask_shouldDeleteWithAuditLog() {
        EmployeeTask task = new EmployeeTask();
        task.setId(5L);
        task.setTitle("Silinecek Görev");

        when(employeeTaskRepository.findById(5L)).thenReturn(Optional.of(task));

        employeeService.deleteTask(5L);

        verify(employeeTaskRepository).deleteById(5L);
        verify(auditLogService).log(any(), eq("Eleman Görevi"), eq(5L), contains("Eleman görevi silindi"));
    }

    @Test
    void findAllTasks_shouldDelegateToRepository() {
        when(employeeTaskRepository.findAllWithEmployee()).thenReturn(List.of(new EmployeeTask()));

        List<EmployeeTask> result = employeeService.findAllTasks();

        assertThat(result).hasSize(1);
        verify(employeeTaskRepository).findAllWithEmployee();
    }
}
