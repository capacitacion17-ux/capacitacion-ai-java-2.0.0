package com.example.ticketero.model.enums;

/**
 * Tipos de cola de atención
 */
public enum QueueType {
    CAJA("Caja"),
    PERSONAL("Banca Personal"),
    EMPRESAS("Banca Empresas"),
    GERENCIA("Atención Gerencial");

    private final String displayName;

    QueueType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
