package com.example.ticketero.model.entity;

import com.example.ticketero.model.enums.QueueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Configuración por tipo de cola
 */
@Entity
@Table(name = "queue_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_type", unique = true, nullable = false, length = 50)
    private QueueType queueType;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avg_service_time_minutes", nullable = false)
    @Builder.Default
    private Integer avgServiceTimeMinutes = 5;

    @Column(name = "notification_threshold", nullable = false)
    @Builder.Default
    private Integer notificationThreshold = 3;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1;

    @Column(name = "max_queue_size")
    private Integer maxQueueSize;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
