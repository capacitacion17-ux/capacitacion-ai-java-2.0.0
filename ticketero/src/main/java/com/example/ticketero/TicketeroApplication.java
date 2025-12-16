package com.example.ticketero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TicketeroApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketeroApplication.class, args);
    }

}
