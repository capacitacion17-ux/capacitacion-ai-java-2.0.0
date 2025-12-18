package com.example.ticketero.model.dto;

import com.example.ticketero.model.enums.QueueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;

/**
 * Request para crear un nuevo asesor
 */
public record AdvisorCreateRequest(
    @NotBlank(message = "El nombre es obligatorio")
    String name,
    
    @NotNull(message = "El número de módulo es obligatorio")
    @Positive(message = "El número de módulo debe ser positivo")
    Integer moduleNumber,
    
    @NotNull(message = "Las colas especializadas son obligatorias")
    Set<QueueType> specializedQueues
) {}