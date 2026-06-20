package com.raspel.cardtracker.domain.card;

public enum CardType {
    CREDIT_CARD("Kredi Kartı"),
    DEBIT_CARD("Banka Kartı / Ön Ödemeli");

    private final String displayName;

    CardType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
