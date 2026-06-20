package com.raspel.cardtracker.domain.cheque;

public enum ChequeType {
    ENTERING("Çek Girişi"),
    EXITING("Çek Çıkışı");

    private final String label;

    ChequeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
