package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.report.MonthlyReportService;
import com.raspel.cardtracker.domain.settings.AppSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class ScheduledTasks {
    private final MonthlyReportService reportService;
    private final AppSettingsService appSettingsService;

    public ScheduledTasks(MonthlyReportService reportService, AppSettingsService appSettingsService) {
        this.reportService = reportService;
        this.appSettingsService = appSettingsService;
    }

    @Scheduled(cron = "0 0 8 1 * *")
    public void generateMonthlyReport() {
        try {
            LocalDate lastMonth = LocalDate.now().minusMonths(1);
            String companyName = appSettingsService.getCompanyName();
            if (companyName == null) companyName = "Firma";
            String companySlug = companyName.replaceAll("[^a-zA-Z0-9ğüşıöçĞÜŞİÖÇ]", "_");
            String fileName = String.format("%s_Aylik_Rapor_%s.pdf",
                    companySlug,
                    lastMonth.format(DateTimeFormatter.ofPattern("yyyy_MM")));

            // Docker ortamında REPORTS_DIR env değişkeni kullanılır; yoksa /app/reports'a fallback yapılır
            String reportDirStr = System.getenv("REPORTS_DIR");
            if (reportDirStr == null || reportDirStr.isBlank()) {
                reportDirStr = "/app/reports";
            }
            Path reportDir = Paths.get(reportDirStr);
            if (!Files.exists(reportDir)) {
                Files.createDirectories(reportDir);
            }

            Path targetPath = reportDir.resolve(fileName);
            try (InputStream is = reportService.generateMonthlyReport(
                    lastMonth.getYear(), lastMonth.getMonthValue())) {
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Aylık rapor oluşturuldu: {}", targetPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Aylık rapor oluşturulamadı", e);
        }
    }
}
