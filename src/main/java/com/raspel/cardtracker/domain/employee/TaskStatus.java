package com.raspel.cardtracker.domain.employee;

public enum TaskStatus {
    TODO("Yapılacak"),
    IN_PROGRESS("Devam Ediyor"),
    COMPLETED("Tamamlandı");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
