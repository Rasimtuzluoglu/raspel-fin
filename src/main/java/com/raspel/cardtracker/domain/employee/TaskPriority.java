package com.raspel.cardtracker.domain.employee;

public enum TaskPriority {
    LOW("Düşük"),
    MEDIUM("Orta"),
    HIGH("Yüksek");

    private final String label;

    TaskPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
