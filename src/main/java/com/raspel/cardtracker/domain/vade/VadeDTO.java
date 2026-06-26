package com.raspel.cardtracker.domain.vade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VadeDTO {
    private BigDecimal tutar;
    private LocalDate vadeTarihi;
}
