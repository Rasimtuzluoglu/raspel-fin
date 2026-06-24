package com.raspel.cardtracker.domain.backup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Veritabanı yedekleme ve geri yükleme servisi.
 * pg_dump ve psql komutları ile çalışır.
 */
@Service
@Slf4j
public class BackupRestoreService {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPass;

    /**
     * pg_dump ile yedek alır, SQL dosyası döner.
     */
    public File createBackup() throws Exception {
        String dbName = extractDbName(dbUrl);
        String host = extractHost(dbUrl);
        int port = extractPort(dbUrl);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File backupDir = new File("/app/backups");
        if (!backupDir.exists()) backupDir.mkdirs();
        File file = new File(backupDir, "manuel_yedek_" + timestamp + ".sql");

        ProcessBuilder pb = new ProcessBuilder(
            "pg_dump",
            "-h", host,
            "-p", String.valueOf(port),
            "-U", dbUser,
            "-d", dbName,
            "--no-owner",
            "--no-acl"
        );
        pb.environment().put("PGPASSWORD", dbPass);
        pb.redirectOutput(file);

        Process p = pb.start();
        int exit = p.waitFor();
        if (exit != 0) {
            String err = new String(p.getErrorStream().readAllBytes());
            log.error("pg_dump hatası: {}", err);
            throw new RuntimeException("Yedek alınamadı: " + err);
        }
        log.info("Yedek alındı: {}", file.getAbsolutePath());
        return file;
    }

    /**
     * SQL dosyasından geri yükleme yapar.
     */
    public void restoreFromFile(InputStream sqlStream) throws Exception {
        String dbName = extractDbName(dbUrl);
        String host = extractHost(dbUrl);
        int port = extractPort(dbUrl);

        ProcessBuilder pb = new ProcessBuilder(
            "psql",
            "-h", host,
            "-p", String.valueOf(port),
            "-U", dbUser,
            "-d", dbName,
            "-v", "ON_ERROR_STOP=1"
        );
        pb.environment().put("PGPASSWORD", dbPass);

        Process p = pb.start();
        try (OutputStream os = p.getOutputStream()) {
            sqlStream.transferTo(os);
        }
        int exit = p.waitFor();
        if (exit != 0) {
            String err = new String(p.getErrorStream().readAllBytes());
            log.error("psql geri yükleme hatası: {}", err);
            throw new RuntimeException("Geri yükleme başarısız: " + err);
        }
        log.info("Geri yükleme tamamlandı.");
    }

    private String extractDbName(String url) {
        String[] parts = url.split("/");
        String last = parts[parts.length - 1];
        if (last.contains("?")) last = last.substring(0, last.indexOf("?"));
        return last;
    }

    private String extractHost(String url) {
        String s = url.replace("jdbc:postgresql://", "");
        if (s.contains("/")) s = s.substring(0, s.indexOf("/"));
        if (s.contains(":")) s = s.substring(0, s.indexOf(":"));
        return s;
    }

    private int extractPort(String url) {
        try {
            String s = url.replace("jdbc:postgresql://", "");
            if (s.contains("/")) s = s.substring(0, s.indexOf("/"));
            if (s.contains(":")) return Integer.parseInt(s.substring(s.indexOf(":") + 1));
        } catch (Exception ignored) {}
        return 5432;
    }
}
