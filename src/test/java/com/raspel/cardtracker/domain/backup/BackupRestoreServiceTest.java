package com.raspel.cardtracker.domain.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BackupRestoreServiceTest {

    @Test
    void extractDbName_shouldExtractFromUrl() {
        BackupRestoreService service = new BackupRestoreService();
        ReflectionTestUtils.setField(service, "dbUrl", "jdbc:postgresql://localhost:5432/mydb");

        String result = invokeExtractDbName(service, "jdbc:postgresql://localhost:5432/mydb");

        assertThat(result).isEqualTo("mydb");
    }

    @Test
    void extractDbName_shouldHandleQueryParams() {
        BackupRestoreService service = new BackupRestoreService();

        String result = invokeExtractDbName(service, "jdbc:postgresql://localhost:5432/mydb?sslmode=disable");

        assertThat(result).isEqualTo("mydb");
    }

    @Test
    void extractHost_shouldExtractFromUrl() {
        BackupRestoreService service = new BackupRestoreService();

        String result = invokeExtractHost(service, "jdbc:postgresql://db.example.com:5432/mydb");

        assertThat(result).isEqualTo("db.example.com");
    }

    @Test
    void extractHost_shouldHandleNoPort() {
        BackupRestoreService service = new BackupRestoreService();

        String result = invokeExtractHost(service, "jdbc:postgresql://localhost/mydb");

        assertThat(result).isEqualTo("localhost");
    }

    @Test
    void extractPort_shouldExtractFromUrl() {
        BackupRestoreService service = new BackupRestoreService();

        int result = invokeExtractPort(service, "jdbc:postgresql://localhost:9876/mydb");

        assertThat(result).isEqualTo(9876);
    }

    @Test
    void extractPort_shouldDefaultTo5432() {
        BackupRestoreService service = new BackupRestoreService();

        int result = invokeExtractPort(service, "jdbc:postgresql://localhost/mydb");

        assertThat(result).isEqualTo(5432);
    }

    private String invokeExtractDbName(BackupRestoreService service, String url) {
        return (String) ReflectionTestUtils.invokeMethod(service, "extractDbName", url);
    }

    private String invokeExtractHost(BackupRestoreService service, String url) {
        return (String) ReflectionTestUtils.invokeMethod(service, "extractHost", url);
    }

    private int invokeExtractPort(BackupRestoreService service, String url) {
        return (int) ReflectionTestUtils.invokeMethod(service, "extractPort", url);
    }
}
