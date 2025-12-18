package com.example.ticketero.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de OpenAPI/Swagger para documentación de la API
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ticketeroOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketero API")
                        .description("Sistema de gestión de turnos con notificaciones Telegram")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("David Espinoza")
                                .email("david.espinoza@ticketero.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Desarrollo"),
                        new Server().url("https://api.ticketero.com").description("Producción")
                ));
    }
}