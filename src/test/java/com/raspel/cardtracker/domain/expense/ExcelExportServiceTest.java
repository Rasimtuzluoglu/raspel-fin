package com.raspel.cardtracker.domain.expense;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExportServiceTest {

    private final ExcelExportService service = new ExcelExportService();

    @Test
    void exportInstallments_shouldReturnNonEmptyStream() {
        ByteArrayInputStream result = service.exportInstallments(List.of());

        assertThat(result).isNotNull();
        assertThat(result.available()).isGreaterThan(0);
    }

    @Test
    void createSampleTemplate_shouldReturnNonEmptyStream() {
        ByteArrayInputStream result = service.createSampleTemplate();

        assertThat(result).isNotNull();
        assertThat(result.available()).isGreaterThan(0);
    }

    @Test
    void exportCheques_shouldReturnNonEmptyStream() {
        ByteArrayInputStream result = service.exportCheques(List.of());

        assertThat(result).isNotNull();
        assertThat(result.available()).isGreaterThan(0);
    }
}
