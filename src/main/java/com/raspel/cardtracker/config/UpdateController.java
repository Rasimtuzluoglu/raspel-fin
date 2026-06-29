package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
public class UpdateController {

    private final Environment environment;
    private final UserService userService;
    private final DataSource dataSource;

    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> systemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

        status.put("docker", checkDocker());

        status.put("container", Map.of("status", "yes", "detail", "Uygulama yanit veriyor"));

        status.put("database", checkDatabase());

        status.put("lastErrors", getLastErrors(5));

        return ResponseEntity.ok(status);
    }

    private Map<String, Object> checkDocker() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            int code = finished ? p.exitValue() : -1;
            if (code == 0) {
                return Map.of("status", "yes", "detail", "Docker calisiyor");
            }
            return Map.of("status", "no", "detail", "Docker exit code: " + code);
        } catch (Exception e) {
            return Map.of("status", "no", "detail", "Docker erisilemedi: " + e.getMessage());
        }
    }

    private Map<String, Object> checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(3);
            String url = conn.getMetaData().getURL();
            return Map.of("status", valid ? "yes" : "no",
                    "detail", valid ? "Baglanti basarili" : "Baglanti gecersiz",
                    "url", url != null ? url.replaceAll("//[^@]*@", "//***:***@") : "-");
        } catch (Exception e) {
            return Map.of("status", "no", "detail", "Veritabani hatasi: " + e.getMessage());
        }
    }

    private List<Map<String, String>> getLastErrors(int count) {
        List<Map<String, String>> errors = new ArrayList<>();
        Path logFile = Paths.get(System.getProperty("user.dir"), "logs", "cardtracker.log");
        if (!Files.exists(logFile)) {
            logFile = Paths.get(System.getProperty("user.dir"), "logs", "application.json");
        }
        if (!Files.exists(logFile)) {
            Map<String, String> empty = new LinkedHashMap<>();
            empty.put("time", "-");
            empty.put("message", "Log dosyasi bulunamadi");
            errors.add(empty);
            return errors;
        }
        try {
            List<String> lines = Files.readAllLines(logFile);
            int found = 0;
            for (int i = lines.size() - 1; i >= 0 && found < count; i--) {
                String line = lines.get(i);
                if (line.contains("ERROR") || line.contains("\"level\":\"ERROR\"")) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    String time = extractTime(line);
                    entry.put("time", time != null ? time : "-");
                    String msg = extractMessage(line);
                    if (msg != null && msg.length() > 200) {
                        msg = msg.substring(0, 200) + "...";
                    }
                    entry.put("message", msg != null ? msg : line.substring(0, Math.min(200, line.length())));
                    errors.add(entry);
                    found++;
                }
            }
            if (errors.isEmpty()) {
                Map<String, String> empty = new LinkedHashMap<>();
                empty.put("time", "-");
                empty.put("message", "Hata bulunamadi");
                errors.add(empty);
            }
        } catch (Exception e) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("time", "-");
            err.put("message", "Log okunamadi: " + e.getMessage());
            errors.add(err);
        }
        return errors;
    }

    private String extractTime(String line) {
        try {
            int end = line.indexOf(" ERROR");
            if (end < 0) end = line.indexOf(" WARN");
            if (end < 0) end = line.indexOf(" INFO");
            if (end > 0 && end < 25) return line.substring(0, end).trim();
        } catch (Exception ignored) {}
        return null;
    }

    private String extractMessage(String line) {
        try {
            int idx = line.indexOf(" - ");
            if (idx > 0 && idx + 3 < line.length()) return line.substring(idx + 3).trim();
        } catch (Exception ignored) {}
        return line;
    }

    @PostMapping("/check-update")
    public ResponseEntity<Map<String, Object>> checkUpdate() {
        String image = environment.getProperty("DOCKER_IMAGE", "ghcr.io/rasimtuzluoglu/raspel-fin:latest");
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "pull", image);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;
            String out = output.toString();

            boolean isNewer = out.contains("Downloaded newer image") || out.contains("Pulling from");
            boolean isUpToDate = out.contains("Image is up to date");

            return ResponseEntity.ok(Map.of(
                    "status", exitCode == 0 ? "ok" : "error",
                    "updateAvailable", isNewer,
                    "upToDate", isUpToDate,
                    "message", isNewer ? "Yeni guncelleme mevcut!" : "Suan yeni guncelleme yok",
                    "detail", out.length() > 300 ? out.substring(out.length() - 300) : out
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "updateAvailable", false,
                    "message", "Guncelleme kontrol edilemedi: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerUpdate() {
        String image = environment.getProperty("DOCKER_IMAGE", "ghcr.io/rasimtuzluoglu/raspel-fin:latest");
        log.info("Admin tarafindan guncelleme baslatildi. Imaj: {}", image);

        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "pull", image);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;

            if (exitCode == 0) {
                log.info("Imaj basariyla cekildi. Yeniden baslatiliyor...");
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    System.exit(0);
                }).start();
                return ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "message", "Guncelleme basariyla indirildi. Uygulama yeniden baslatiliyor...",
                        "output", output.toString()
                ));
            } else {
                log.error("Imaj cekme basarisiz. Exit code: {}", exitCode);
                return ResponseEntity.status(500).body(Map.of(
                        "status", "error",
                        "message", "Imaj cekilemedi (exit code: " + exitCode + "). Docker'in kurulu ve erisilebilir oldugundan emin olun.",
                        "output", output.toString()
                ));
            }
        } catch (Exception e) {
            log.error("Guncelleme hatasi", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Guncelleme yapilamadi: " + e.getMessage() + ". Docker'in kurulu ve docker.sock'in mount edilmis oldugundan emin olun."
            ));
        }
    }

    @PostMapping("/active-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getActiveUsers() {
        long count = userService.findAllActive().size();
        return ResponseEntity.ok(Map.of("count", count));
    }
}
