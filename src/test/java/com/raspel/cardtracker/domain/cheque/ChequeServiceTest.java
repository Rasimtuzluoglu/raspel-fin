package com.raspel.cardtracker.domain.cheque;

import com.raspel.cardtracker.domain.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChequeServiceTest {

    @Mock private ChequeRepository chequeRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ChequeService chequeService;

    @Test
    void save_shouldThrowWhenChequeIsNull() {
        assertThatThrownBy(() -> chequeService.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Çek bilgisi zorunludur");
    }

    @Test
    void save_shouldThrowWhenAmountIsZero() {
        Cheque cheque = new Cheque();
        cheque.setAmount(BigDecimal.ZERO);
        cheque.setMaturityDate(LocalDate.now().plusDays(30));

        assertThatThrownBy(() -> chequeService.save(cheque))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Çek tutarı sıfırdan büyük olmalıdır");
    }

    @Test
    void save_shouldThrowWhenAmountIsNegative() {
        Cheque cheque = new Cheque();
        cheque.setAmount(new BigDecimal("-100.00"));
        cheque.setMaturityDate(LocalDate.now().plusDays(30));

        assertThatThrownBy(() -> chequeService.save(cheque))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Çek tutarı sıfırdan büyük olmalıdır");
    }

    @Test
    void save_shouldThrowWhenMaturityDateIsNull() {
        Cheque cheque = new Cheque();
        cheque.setAmount(new BigDecimal("500.00"));
        cheque.setMaturityDate(null);

        assertThatThrownBy(() -> chequeService.save(cheque))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vade tarihi zorunludur");
    }

    @Test
    void save_shouldCreateNewChequeWithAuditLog() {
        Cheque cheque = new Cheque();
        cheque.setAmount(new BigDecimal("1000.00"));
        cheque.setMaturityDate(LocalDate.now().plusDays(30));
        cheque.setChequeNumber("CHK-001");

        when(chequeRepository.save(any(Cheque.class))).thenAnswer(inv -> {
            Cheque c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        Cheque saved = chequeService.save(cheque);

        assertThat(saved.getId()).isEqualTo(1L);
        verify(chequeRepository).save(cheque);
        verify(auditLogService).log(any(), eq("Çek"), any(), contains("Yeni çek oluşturuldu"));
    }

    @Test
    void save_shouldUpdateExistingChequeWithAuditLog() {
        Cheque cheque = new Cheque();
        cheque.setId(5L);
        cheque.setAmount(new BigDecimal("2000.00"));
        cheque.setMaturityDate(LocalDate.now().plusMonths(1));
        cheque.setChequeNumber("CHK-002");

        when(chequeRepository.save(any(Cheque.class))).thenReturn(cheque);

        chequeService.save(cheque);

        verify(auditLogService).log(any(), eq("Çek"), any(), contains("Çek güncellendi"));
    }

    @Test
    void delete_shouldDeleteWithAuditLog() {
        Cheque cheque = new Cheque();
        cheque.setId(1L);
        cheque.setChequeNumber("CHK-DEL");

        when(chequeRepository.findById(1L)).thenReturn(Optional.of(cheque));

        chequeService.delete(1L);

        verify(chequeRepository).deleteById(1L);
        verify(auditLogService).log(any(), eq("Çek"), eq(1L), contains("Çek silindi"));
    }

    @Test
    void findAll_shouldReturnAllChequesOrderedByMaturityDate() {
        Cheque c1 = new Cheque();
        Cheque c2 = new Cheque();
        when(chequeRepository.findAllByOrderByMaturityDateAsc()).thenReturn(List.of(c1, c2));

        List<Cheque> result = chequeService.findAll();

        assertThat(result).hasSize(2);
        verify(chequeRepository).findAllByOrderByMaturityDateAsc();
    }

    @Test
    void findById_shouldReturnEmpty_whenNotFound() {
        when(chequeRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Cheque> result = chequeService.findById(99L);

        assertThat(result).isEmpty();
    }
}
