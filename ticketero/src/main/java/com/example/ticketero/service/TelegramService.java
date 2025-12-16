package com.example.ticketero.service;

import com.example.ticketero.config.TelegramConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private final RestTemplate restTemplate;
    private final TelegramConfig telegramConfig;

    public String enviarMensaje(String telefono, String texto) {
        try {
            String url = telegramConfig.getFullApiUrl() + "/sendMessage";

            // Usar el chat_id configurado para TODOS los mensajes (testing)
            String chatIdReal = telegramConfig.getChatId();
            
            // Solo sanitizar el teléfono (input del usuario)
            // El texto del mensaje es generado por el sistema, no necesita sanitización
            String telefonoSanitizado = sanitizeHtml(telefono);

            // Agregar identificador del cliente al inicio del mensaje
            String mensajeConIdentificador = String.format(
                "📱 <b>Cliente:</b> %s\n━━━━━━━━━━━━━━━━━━━━\n%s",
                telefonoSanitizado,
                texto  // No sanitizar - contiene HTML intencional (<b>, etc.)
            );

            Map<String, Object> request = new HashMap<>();
            request.put("chat_id", chatIdReal);
            request.put("text", mensajeConIdentificador);
            request.put("parse_mode", "HTML");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            log.info("Enviando mensaje a Telegram. Teléfono simulado: {}, ChatId real: {}", 
                telefono, chatIdReal);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null && response.containsKey("result")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                String messageId = String.valueOf(result.get("message_id"));
                log.info("Mensaje enviado exitosamente. MessageId: {}", messageId);
                return messageId;
            }

            log.warn("Respuesta inesperada de Telegram: {}", response);
            return null;

        } catch (Exception e) {
            log.error("Error al enviar mensaje a Telegram: {}", e.getMessage(), e);
            throw new RuntimeException("Error al enviar mensaje a Telegram", e);
        }
    }

    public String obtenerTextoMensaje(String plantilla, String numeroTicket) {
        return switch (plantilla) {
            case "totem_confirmacion" ->
                String.format("✅ <b>Ticket Creado</b>%n%n" +
                    "Tu número de turno es: <b>%s</b>%n%n" +
                    "Te notificaremos cuando estés próximo a ser atendido.", numeroTicket);
            case "totem_proximo_turno" ->
                String.format("⏰ <b>Próximo Turno</b>%n%n" +
                    "Tu turno <b>%s</b> será llamado pronto.%n%n" +
                    "Por favor, estate atento.", numeroTicket);
            case "totem_es_tu_turno" ->
                String.format("🔔 <b>¡Es tu turno!</b>%n%n" +
                    "Turno <b>%s</b>, por favor acércate al módulo de atención.", numeroTicket);
            default ->
                String.format("📩 Mensaje para turno: %s", numeroTicket);
        };
    }

    /**
     * Sanitiza HTML para prevenir XSS (CWE-79)
     * Escapa caracteres peligrosos que podrían inyectar código
     */
    private String sanitizeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
}
