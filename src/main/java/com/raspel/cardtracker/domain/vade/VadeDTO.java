package com.raspel.cardtracker.domain.vade;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CSV'den okunan her bir vade satırını temsil eden DTO.
 */
public class VadeDTO {
    private BigDecimal tutar;
    private LocalDate vadeTarihi;

    public VadeDTO() {}

    public VadeDTO(BigDecimal tutar, LocalDate vadeTarihi) {
        this.tutar = tutar;
        this.vadeTarihi = vadeTarihi;
    }

    public BigDecimal getTutar() { return tutar; }
    public void setTutar(BigDecimal tutar) { this.tutar = tutar; }
    public LocalDate getVadeTarihi() { return vadeTarihi; }
    public void setVadeTarihi(LocalDate vadeTarihi) { this.vadeTarihi = vadeTarihi; }
}
