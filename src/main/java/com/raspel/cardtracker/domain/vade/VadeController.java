package com.raspel.cardtracker.domain.vade;

import jakarta.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/vade")
@RequiredArgsConstructor
@Slf4j
public class VadeController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String[] ALLOWED_CONTENT_TYPES = {"text/csv", "application/csv", "text/plain", "application/vnd.ms-excel"};

    private final VadeHesaplamaService vadeService;

    @PostMapping("/hesapla")
    @RolesAllowed("ADMIN")
    public ResponseEntity<?> hesapla(@RequestParam("dosya") MultipartFile dosya) {
        if (dosya.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("hata", "Lütfen bir dosya seçin."));
        }
        if (dosya.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(java.util.Map.of("hata", "Dosya boyutu 10 MB'dan büyük olamaz."));
        }
        if (dosya.getOriginalFilename() != null && !dosya.getOriginalFilename().toLowerCase(java.util.Locale.ENGLISH).endsWith(".csv")) {
            return ResponseEntity.badRequest().body(java.util.Map.of("hata", "Sadece CSV dosyaları kabul edilir."));
        }

        try {
            List<VadeDTO> items = vadeService.parseCSV(dosya.getInputStream());
            BigDecimal avgDays = vadeService.calculateWeightedDays(items);
            LocalDate avgDate = vadeService.calculateAverageDate(avgDays);

            return ResponseEntity.ok(java.util.Map.of(
                    "ortalamaGun", avgDays,
                    "ortalamaTarih", avgDate.toString(),
                    "islemSayisi", items.size()
            ));
        } catch (Exception e) {
            log.warn("Vade hesaplama hatası: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("hata", "Dosya işlenemedi, lütfen formatı kontrol edin."));
        }
    }
}
