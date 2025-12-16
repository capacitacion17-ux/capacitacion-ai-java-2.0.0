package com.example.ticketero.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TelegramConfig {

    @Value("${telegram.bot-token}")
    private String botToken;

    @Value("${telegram.api-url:https://api.telegram.org/bot}")
    private String apiUrl;

    @Value("${telegram.chat-id}")
    private String chatId;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getBotToken() {
        return botToken;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getChatId() {
        return chatId;
    }

    public String getFullApiUrl() {
        return apiUrl + botToken;
    }
}
