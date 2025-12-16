package com.example.ticketero.model.entity;

import com.example.ticketero.model.enums.AdvisorStatus;
import com.example.ticketero.model.enums.QueueType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entidad que representa un asesor que atiende tickets
 */
@Entity
@Table(name = "advisor")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Advisor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "module_number", nullable = false)
    private Integer moduleNumber;

    /**
     * JSON array de tipos de cola que puede atender: ["CAJA", "PERSONAL"]
     */
    @Column(name = "queue_types", nullable = false, length = 200)
    private String queueTypesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdvisorStatus status;

    @Column(name = "avg_service_time_minutes")
    @Builder.Default
    private Integer avgServiceTimeMinutes = 5;

    @Column(name = "total_tickets_served")
    @Builder.Default
    private Integer totalTicketsServed = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    /**
     * Heartbeat para detección de workers muertos
     * Se actualiza cada 5 segundos mientras el worker está activo
     */
    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    /**
     * Contador de veces que este asesor fue auto-recuperado
     * Útil para detectar problemas recurrentes
     */
    @Column(name = "recovery_count")
    @Builder.Default
    private Integer recoveryCount = 0;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = LocalDateTime.now();
        this.lastHeartbeat = LocalDateTime.now();
        if (this.status == null) {
            this.status = AdvisorStatus.AVAILABLE;
        }
    }

    /**
     * Helper para obtener tipos de cola soportados
     */
    @Transient
    public List<QueueType> getQueueTypes() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String[] types = mapper.readValue(queueTypesJson, String[].class);
            return Arrays.stream(types)
                .map(QueueType::valueOf)
                .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Helper para setear tipos de cola soportados
     */
    @Transient
    public void setQueueTypes(List<QueueType> types) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.queueTypesJson = mapper.writeValueAsString(
                types.stream().map(Enum::name).toArray()
            );
        } catch (Exception e) {
            this.queueTypesJson = "[]";
        }
    }
}
