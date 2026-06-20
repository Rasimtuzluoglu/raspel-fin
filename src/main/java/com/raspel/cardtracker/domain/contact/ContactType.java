package com.raspel.cardtracker.domain.contact;

public enum ContactType {
    CUSTOMER("Müşteri"),
    SUPPLIER("Tedarikçi"),
    BOTH("Müşteri & Tedarikçi");

    private final String label;

    ContactType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
