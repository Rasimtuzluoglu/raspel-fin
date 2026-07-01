package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        status.put("docker", checkDockerFast());
        status.put("container", Map.of("status", "yes", "detail", "Uygulama yan\u0131t veriyor"));
        status.put("database", checkDatabase());

        boolean isAdmin = false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }

        if (isAdmin) {
            status.put("lastErrors", getLastErrors(3));
        } else {
            List<Map<String, String>> restricted = new ArrayList<>();
            Map<String, String> msg = new LinkedHashMap<>();
            msg.put("time", "");
            msg.put("message", "Hata detaylar\u0131 sadece y\u00F6netici g\u00F6rebilir");
            restricted.add(msg);
            status.put("lastErrors", restricted);
            status.put("restricted", true);
        }

        return ResponseEntity.ok(status);
    }

    private Map<String, Object> checkDockerFast() {
        Path socket = Paths.get("/var/run/docker.sock");
        if (Files.exists(socket)) {
            try {
                ProcessBuilder pb = new ProcessBuilder("docker", "version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean finished = p.waitFor(3, TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    return Map.of("status", "yes", "detail", "Docker çalışıyor");
                }
                if (!finished) p.destroyForcibly();
            } catch (Exception ignored) {}
            return Map.of("status", "yes", "detail", "Docker socket mevcut (yanıt alınamadı)");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                return Map.of("status", "yes", "detail", "Docker çalışıyor (socket yok ama CLI mevcut)");
            }
            if (!finished) p.destroyForcibly();
        } catch (Exception ignored) {}
        return Map.of("status", "no", "detail", "Docker kullanılamıyor: docker.sock mount edilmemiş ve docker komutu çalışmadı. Konteynerda çalışıyorsanız docker-compose.yml'a /var/run/docker.sock:/var/run/docker.sock volume ekleyin.");
    }

    private Map<String, Object> checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            return Map.of("status", valid ? "yes" : "no",
                    "detail", valid ? "Ba\u011Flant\u0131 ba\u015Far\u0131l\u0131" : "Ba\u011Flant\u0131 ge\u00E7ersiz");
        } catch (Exception e) {
            return Map.of("status", "no", "detail", "Veritaban\u0131 hatas\u0131: " + e.getMessage());
        }
    }

    private List<Map<String, String>> getLastErrors(int count) {
        List<Map<String, String>> errors = new ArrayList<>();
        Path logFile = Paths.get("/app/logs/cardtracker.log");
        if (!Files.exists(logFile)) {
            logFile = Paths.get(System.getProperty("user.dir"), "logs", "cardtracker.log");
        }
        if (!Files.exists(logFile)) {
            Map<String, String> empty = new LinkedHashMap<>();
            empty.put("time", "-");
            empty.put("message", "Log dosyas\u0131 bulunamad\u0131");
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
                empty.put("message", "Hata bulunamad\u0131");
                errors.add(empty);
            }
        } catch (Exception e) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("time", "-");
            err.put("message", "Log okunamad\u0131: " + e.getMessage());
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

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;
            if (!finished) {
                process.destroyForcibly();
            }
            String out = output.toString();

            boolean isNewer = out.contains("Downloaded newer image") || out.contains("Pulling from");
            boolean isUpToDate = out.contains("Image is up to date");

            return ResponseEntity.ok(Map.of(
                    "status", exitCode == 0 ? "ok" : "error",
                    "updateAvailable", isNewer,
                    "upToDate", isUpToDate,
                    "message", isNewer ? "Yeni g\u00FCncelleme mevcut!" :
                               isUpToDate ? "\u015Eu an yeni g\u00FCncelleme yok" :
                               exitCode == 0 ? "Kontrol tamamland\u0131" : "Docker komutu ba\u015Far\u0131s\u0131z",
                    "detail", out.length() > 300 ? out.substring(out.length() - 300) : out
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "updateAvailable", false,
                    "upToDate", false,
                    "message", "G\u00FCncelleme kontrol edilemedi: " + e.getMessage()
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
                        "message", "G\u00FCncelleme ba\u015Far\u0131yla indirildi. Uygulama yeniden ba\u015Flat\u0131l\u0131yor...",
                        "output", output.toString()
                ));
            } else {
                log.error("Imaj cekme basarisiz. Exit code: {}", exitCode);
                return ResponseEntity.status(500).body(Map.of(
                        "status", "error",
                        "message", "\u0130maj \u00E7ekilemedi (exit code: " + exitCode + "). Docker'\u0131n kurulu ve eri\u015Filebilir oldu\u011Fundan emin olun.",
                        "output", output.toString()
                ));
            }
        } catch (Exception e) {
            log.error("Guncelleme hatasi", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "G\u00FCncelleme yap\u0131lamad\u0131: " + e.getMessage() + ". Docker'\u0131n kurulu ve docker.sock'\u0131n mount edilmi\u015F oldu\u011Fundan emin olun."
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
