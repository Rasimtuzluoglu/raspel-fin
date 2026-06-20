package com.raspel.cardtracker.domain.cheque;

public enum ChequeStatus {
    PORTFOLIO("Portföyde"),
    CLEARED("Tahsil Edildi"),
    ENDORSED("Ciro Edildi"),
    PAID("Ödendi"),
    CANCELLED("İptal Edildi");

    private final String label;

    ChequeStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
