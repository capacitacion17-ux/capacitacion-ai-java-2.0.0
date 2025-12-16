package com.example.ticketero.model.enums;

/**
 * Types of automatic recovery events in the system.
 * Used for auditing and monitoring recovery operations.
 */
public enum RecoveryType {
    /**
     * Recovery triggered by detecting a dead worker (no heartbeat).
     */
    DEAD_WORKER,

    /**
     * Recovery triggered by operation timeout.
     */
    TIMEOUT,

    /**
     * Recovery triggered manually by operations team.
     */
    MANUAL
}
