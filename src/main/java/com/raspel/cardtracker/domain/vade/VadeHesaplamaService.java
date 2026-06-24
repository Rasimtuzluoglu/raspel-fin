package com.raspel.cardtracker.domain.vade;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Vade hesaplama servisi - CSV dosyasından tutar ve vade tarihlerini okuyarak
 * ağırlıklı ortalama vade hesaplar.
 */
@Service
@Slf4j
public class VadeHesaplamaService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Tutar ve vade tarihi sütunlarını içeren CSV'yi parse eder.
     * Beklenen format: tutar, vadeTarihi
     */
    public List<VadeDTO> parseCSV(InputStream inputStream) throws Exception {
        List<VadeDTO> list = new ArrayList<>();
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            String content = sb.toString();
            String[] lines = content.split("\\r?\\n");
            if (lines.length < 2) {
                throw new IllegalArgumentException("CSV dosyası en az 1 başlık + 1 veri satırı içermelidir.");
            }

            // İlk satır başlık, atla
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] cols = line.split(";|,");
                if (cols.length < 2) {
                    log.warn("Satır {} atlandı: yetersiz kolon ({})", i + 1, cols.length);
                    continue;
                }

                try {
                    BigDecimal tutar = new BigDecimal(cols[0].trim().replace(",", ".").replace("₺", "").replace("TL", "").trim());
                    if (tutar.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("Satır {} atlandı: tutar <= 0", i + 1);
                        continue;
                    }

                    LocalDate vadeTarihi;
                    try {
                        vadeTarihi = LocalDate.parse(cols[1].trim(), DATE_FMT);
                    } catch (DateTimeParseException e) {
                        log.warn("Satır {} atlandı: vade tarihi parse edilemedi ({})", i + 1, cols[1].trim());
                        continue;
                    }

                    list.add(new VadeDTO(tutar, vadeTarihi));
                } catch (Exception e) {
                    log.warn("Satır {} atlandı: {}", i + 1, e.getMessage());
                }
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("CSV dosyasından hiçbir geçerli satır okunamadı.");
        }
        return list;
    }

    /**
     * Ağırlıklı ortalama vade gününü hesaplar.
     * Formül: Σ(tutar × günFarkı) / Σ(tutar)
     */
    public BigDecimal calculateWeightedDays(List<VadeDTO> items) {
        LocalDate bugun = LocalDate.now();
        BigDecimal toplamTutar = BigDecimal.ZERO;
        BigDecimal agirlikliToplam = BigDecimal.ZERO;

        for (VadeDTO item : items) {
            long gunFarki = Math.max(1, ChronoUnit.DAYS.between(bugun, item.getVadeTarihi()));
            agirlikliToplam = agirlikliToplam.add(item.getTutar().multiply(BigDecimal.valueOf(gunFarki)));
            toplamTutar = toplamTutar.add(item.getTutar());
        }

        if (toplamTutar.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return agirlikliToplam.divide(toplamTutar, 2, RoundingMode.HALF_UP);
    }

    /**
     * Ortalama vade tarihini döner: bugün + ortalamaGün
     */
    public LocalDate calculateAverageDate(BigDecimal avgDays) {
        return LocalDate.now().plusDays(avgDays.longValue());
    }
}
