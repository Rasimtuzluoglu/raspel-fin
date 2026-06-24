package com.raspel.cardtracker.domain.vade;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Vade hesaplama REST controller.
 * CSV dosyası yüklenerek ağırlıklı ortalama vade hesaplanır.
 */
@RestController
@RequestMapping("/api/vade")
@RequiredArgsConstructor
public class VadeController {

    private final VadeHesaplamaService vadeService;

    /**
     * CSV dosyası yükleyerek ağırlıklı ortalama vade hesaplar.
     * Dönüş: ortalama gün, ortalama tarih, işlenen satır sayısı.
     */
    @PostMapping("/hesapla")
    public ResponseEntity<?> hesapla(@RequestParam("dosya") MultipartFile dosya) {
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
            return ResponseEntity.badRequest().body(java.util.Map.of("hata", e.getMessage()));
        }
    }
}
