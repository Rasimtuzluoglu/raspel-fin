package com.raspel.cardtracker.domain.card;

import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.ExpenseRepository;
import com.raspel.cardtracker.domain.expense.InstallmentEntryRepository;
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
class CardServiceTest {

    @Mock private CardRepository cardRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private InstallmentEntryRepository installmentEntryRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private CardService cardService;

    @Test
    void save_shouldThrowWhenCardIsNull() {
        assertThatThrownBy(() -> cardService.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kart bilgisi zorunludur");
    }

    @Test
    void save_shouldCreateNewCard() {
        Card card = new Card();
        card.setName("Test Kart");
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        Card saved = cardService.save(card);

        assertThat(saved.getId()).isEqualTo(1L);
        verify(cardRepository).save(card);
        verify(auditLogService).log(any(), eq("Kart"), any(), contains("Yeni kart oluşturuldu"));
    }

    @Test
    void save_shouldUpdateExistingCard() {
        Card card = new Card();
        card.setId(5L);
        card.setName("Güncellenen Kart");
        when(cardRepository.save(any(Card.class))).thenReturn(card);

        cardService.save(card);

        verify(auditLogService).log(any(), eq("Kart"), any(), contains("Kart güncellendi"));
    }

    @Test
    void delete_shouldSetCardInactiveAndDetachExpenses() {
        Card card = new Card();
        card.setId(1L);
        card.setName("Kart");
        card.setActive(true);

        Expense expense = new Expense();
        expense.setCard(card);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(expenseRepository.findByCardId(1L)).thenReturn(List.of(expense));
        when(cardRepository.save(any())).thenReturn(card);

        cardService.delete(1L);

        assertThat(card.getActive()).isFalse();
        verify(cardRepository).save(card);
        verify(expenseRepository).deleteAllByCardId(1L);
        verify(auditLogService).log(any(), eq("Kart"), any(), contains("Kart silindi"));
    }

    @Test
    void delete_shouldNotDetachExpensesWhenNoneExist() {
        Card card = new Card();
        card.setId(2L);
        card.setName("Boş Kart");
        card.setActive(true);

        when(cardRepository.findById(2L)).thenReturn(Optional.of(card));
        when(expenseRepository.findByCardId(2L)).thenReturn(List.of());
        when(cardRepository.save(any())).thenReturn(card);

        cardService.delete(2L);

        verify(expenseRepository, never()).saveAll(any());
    }

    @Test
    void activate_shouldSetCardActive() {
        Card card = new Card();
        card.setId(3L);
        card.setActive(false);

        when(cardRepository.findById(3L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any())).thenReturn(card);

        cardService.activate(3L);

        assertThat(card.getActive()).isTrue();
        verify(auditLogService).log(any(), eq("Kart"), any(), contains("aktifleştirildi"));
    }

    @Test
    void hardDelete_shouldSucceedWhenNoExpenses() {
        when(expenseRepository.findByCardId(1L)).thenReturn(List.of());

        cardService.hardDelete(1L);

        verify(cardRepository).deleteById(1L);
    }

    @Test
    void hardDelete_shouldThrowWhenExpensesExist() {
        Expense expense = new Expense();
        expense.setId(10L);
        when(expenseRepository.findByCardId(1L)).thenReturn(List.of(expense));

        assertThatThrownBy(() -> cardService.hardDelete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("harcama");
    }

    @Test
    void findAll_shouldReturnAllCards() {
        Card c1 = new Card();
        Card c2 = new Card();
        when(cardRepository.findAll()).thenReturn(List.of(c1, c2));

        List<Card> result = cardService.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void count_shouldReturnCardCount() {
        when(cardRepository.count()).thenReturn(5L);

        long count = cardService.count();

        assertThat(count).isEqualTo(5L);
    }
}
