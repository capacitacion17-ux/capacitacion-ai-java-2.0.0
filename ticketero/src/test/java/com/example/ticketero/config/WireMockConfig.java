package com.example.ticketero.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock configuration for mocking Telegram API.
 */
@TestConfiguration
public class WireMockConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WireMockServer wireMockServer() {
        WireMockServer server = new WireMockServer(8089);
        
        // Mock successful Telegram sendMessage
        server.stubFor(post(urlPathMatching("/bot.*/sendMessage"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "ok": true,
                        "result": {
                            "message_id": 12345,
                            "chat": {"id": 123456789},
                            "text": "Test message"
                        }
                    }
                    """)));
        
        return server;
    }

    public static void resetMocks(WireMockServer server) {
        server.resetAll();
        
        // Re-configure default stub
        server.stubFor(post(urlPathMatching("/bot.*/sendMessage"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true,\"result\":{\"message_id\":12345}}")));
    }

    public static void simulateTelegramFailure(WireMockServer server) {
        server.stubFor(post(urlPathMatching("/bot.*/sendMessage"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"ok\":false,\"error_code\":500}")));
    }
}