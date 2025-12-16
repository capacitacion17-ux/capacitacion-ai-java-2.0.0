package com.example.ticketero.model.dto;

import com.example.ticketero.model.enums.QueueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request para crear un nuevo ticket
 */
public record TicketCreateRequest(
    @NotBlank(message = "El ID nacional es obligatorio")
    @Pattern(regexp = "^[0-9]{8,12}$", message = "ID nacional inválido")
    String nationalId,
    
    @Pattern(regexp = "^[0-9]{9,15}$", message = "Teléfono inválido")
    String telefono,
    
    @NotBlank(message = "La sucursal es obligatoria")
    String branchOffice,
    
    @NotNull(message = "El tipo de cola es obligatorio")
    QueueType queueType
) {
    /**
     * Constructor compacto para normalizar datos
     */
    public TicketCreateRequest {
        if (telefono != null && telefono.isBlank()) {
            telefono = null;
        }
    }
}
