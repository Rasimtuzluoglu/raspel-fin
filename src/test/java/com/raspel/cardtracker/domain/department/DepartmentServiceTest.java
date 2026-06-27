package com.raspel.cardtracker.domain.department;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock private DepartmentRepository departmentRepository;
    @Mock private CardRepository cardRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void save_shouldThrowWhenDepartmentIsNull() {
        assertThatThrownBy(() -> departmentService.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zorunludur");
    }

    @Test
    void save_shouldCreateNewDepartment() {
        Department dept = new Department();
        dept.setName("Yeni Departman");

        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        Department saved = departmentService.save(dept);

        assertThat(saved.getId()).isEqualTo(1L);
        verify(departmentRepository).save(dept);
        verify(auditLogService).log(any(), eq("Departman"), any(), contains("oluşturuldu"));
    }

    @Test
    void save_shouldUpdateExistingDepartment() {
        Department dept = new Department();
        dept.setId(5L);
        dept.setName("Güncellenen Departman");

        when(departmentRepository.save(any(Department.class))).thenReturn(dept);

        departmentService.save(dept);

        verify(auditLogService).log(any(), eq("Departman"), any(), contains("güncellendi"));
    }

    @Test
    void findAllActive_shouldReturnOnlyActiveDepartments() {
        Department active = new Department();
        active.setIsActive(true);
        Department inactive = new Department();
        inactive.setIsActive(false);

        when(departmentRepository.findAll()).thenReturn(List.of(active, inactive));

        List<Department> result = departmentService.findAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
    }

    @Test
    void findAllActive_shouldFilterNullIsActive() {
        Department active = new Department();
        active.setIsActive(true);
        Department nullActive = new Department();
        nullActive.setIsActive(null);

        when(departmentRepository.findAll()).thenReturn(List.of(active, nullActive));

        List<Department> result = departmentService.findAllActive();

        assertThat(result).hasSize(1);
    }

    @Test
    void findAll_shouldReturnAllDepartments() {
        when(departmentRepository.findAll()).thenReturn(List.of(new Department(), new Department()));

        List<Department> result = departmentService.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void findById_shouldReturnDepartmentWhenFound() {
        Department dept = new Department();
        dept.setId(1L);
        dept.setName("Test");

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(dept));

        Optional<Department> result = departmentService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test");
    }

    @Test
    void findById_shouldReturnEmptyWhenNotFound() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Department> result = departmentService.findById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void findByName_shouldDelegateToRepository() {
        Department dept = new Department();
        dept.setName("Muhasebe");

        when(departmentRepository.findByNameIgnoreCase("Muhasebe")).thenReturn(Optional.of(dept));

        Optional<Department> result = departmentService.findByName("Muhasebe");

        assertThat(result).isPresent();
        verify(departmentRepository).findByNameIgnoreCase("Muhasebe");
    }

    @Test
    void delete_shouldDetachCardsAndSetInactive() {
        Department dept = new Department();
        dept.setId(1L);
        dept.setName("Silinecek");
        dept.setIsActive(true);

        Card card = new Card();
        card.setDepartment(dept);

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(dept));
        when(cardRepository.findByDepartment(dept)).thenReturn(List.of(card));

        departmentService.delete(1L);

        assertThat(dept.getIsActive()).isFalse();
        assertThat(card.getDepartment()).isNull();
        verify(cardRepository).save(card);
        verify(auditLogService).log(any(), eq("Departman"), eq(1L), contains("Departman silindi"));
    }
}
