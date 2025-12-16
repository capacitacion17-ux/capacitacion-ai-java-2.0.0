package com.example.ticketero.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Auditoría de eventos de recuperación automática del sistema.
 * Registra cuando se detectan y corrigen inconsistencias (workers muertos, timeouts, etc.)
 */
@Entity
@Table(name = "recovery_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advisor_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Advisor advisor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    @JsonIgnoreProperties({"assignedAdvisor", "events", "hibernateLazyInitializer", "handler"})
    private Ticket ticket;

    @Column(name = "recovery_type", nullable = false, length = 50)
    private String recoveryType; // DEAD_WORKER, TIMEOUT, MANUAL

    @Column(name = "old_advisor_status", length = 20)
    private String oldAdvisorStatus;

    @Column(name = "old_ticket_status", length = 20)
    private String oldTicketStatus;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (this.detectedAt == null) {
            this.detectedAt = LocalDateTime.now();
        }
    }
}
