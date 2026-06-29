package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.note.Note;
import com.raspel.cardtracker.domain.note.NoteService;
import com.raspel.cardtracker.domain.report.MonthlyReportService;
import com.raspel.cardtracker.domain.settings.AppSettingsService;
import com.raspel.cardtracker.domain.user.AppUser;
import com.raspel.cardtracker.domain.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class ScheduledTasks {
    private final MonthlyReportService reportService;
    private final AppSettingsService appSettingsService;
    private final NoteService noteService;
    private final UserService userService;
    @Autowired(required = false)
    private TelegramBotService telegramBotService;

    public ScheduledTasks(MonthlyReportService reportService, AppSettingsService appSettingsService,
                          NoteService noteService, UserService userService) {
        this.reportService = reportService;
        this.appSettingsService = appSettingsService;
        this.noteService = noteService;
        this.userService = userService;
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

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void checkAndNotifyTelegram() {
        if (telegramBotService != null) {
            try {
                telegramBotService.checkAndNotifyLimits();
            } catch (Exception e) {
                log.error("Telegram limit bildirimi hatası", e);
            }
            try {
                checkNoteReminders();
            } catch (Exception e) {
                log.error("Not hatırlatma bildirimi hatası", e);
            }
        }
    }

    private void checkNoteReminders() {
        List<AppUser> connected = userService.findAllActive().stream()
                .filter(u -> u.getTelegramChatId() != null).toList();
        if (connected.isEmpty()) return;

        for (AppUser u : connected) {
            List<Note> dueNotes = noteService.getDueReminders(u.getUsername());
            if (dueNotes.isEmpty()) continue;
            for (Note note : dueNotes) {
                String msg = "<b>⏰ Not Hatırlatması</b>\n\n" +
                        "<b>" + (note.getTitle() != null ? note.getTitle() : "Not") + "</b>\n" +
                        (note.getContent() != null && !note.getContent().trim().isEmpty() ? note.getContent().trim() : "");
                try {
                    telegramBotService.notifyUser(u.getTelegramChatId(), msg);
                } catch (Exception ignored) {}
                noteService.markReminded(note.getId());
            }
        }
    }

    @Async
    public void triggerTelegramCheckAsync() {
        if (telegramBotService != null) {
            try {
                telegramBotService.checkAndNotifyLimits();
            } catch (Exception e) {
                log.error("Telegram async bildirim hatası", e);
            }
        }
    }
}
