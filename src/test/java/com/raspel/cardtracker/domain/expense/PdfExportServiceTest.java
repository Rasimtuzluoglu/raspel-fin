package com.raspel.cardtracker.domain.expense;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportServiceTest {

    private final PdfExportService service = new PdfExportService();

    @Test
    void exportInstallments_shouldReturnNonEmptyData() {
        ByteArrayInputStream result = service.exportInstallments(List.of());

        assertThat(result).isNotNull();
        assertThat(result.available()).isGreaterThan(0);
    }

    @Test
    void exportCheques_shouldReturnNonEmptyData() {
        ByteArrayInputStream result = service.exportCheques(List.of());

        assertThat(result).isNotNull();
        assertThat(result.available()).isGreaterThan(0);
    }
}
