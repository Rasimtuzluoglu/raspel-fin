package com.raspel.cardtracker.domain.audit;

public enum AuditAction {
    CREATE("Oluşturma"),
    UPDATE("Güncelleme"),
    DELETE("Silme"),
    LOGIN("Giriş"),
    LOGOUT("Çıkış");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
