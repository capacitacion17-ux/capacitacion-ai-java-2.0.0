package com.example.ticketero.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para gestión de colas de tickets
 * 
 * Arquitectura:
 * - 1 Exchange (ticketero-exchange) tipo Direct
 * - 4 Queues (una por tipo de cola: Caja, Personal, Empresas, Gerencia)
 * - Bindings con routing keys
 * - JSON Message Converter
 */
@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    // ============================================================
    // EXCHANGE
    // ============================================================

    /**
     * Exchange principal tipo Direct
     * Los mensajes se enrutan por routing key
     */
    @Bean
    public DirectExchange ticketeroExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    // ============================================================
    // QUEUES - Una por tipo de cola
    // ============================================================

    /**
     * Cola para tickets de Caja
     */
    @Bean
    public Queue cajaQueue() {
        return new Queue("caja-queue", true); // durable = true
    }

    /**
     * Cola para tickets de Banca Personal
     */
    @Bean
    public Queue personalQueue() {
        return new Queue("personal-queue", true);
    }

    /**
     * Cola para tickets de Banca Empresas
     */
    @Bean
    public Queue empresasQueue() {
        return new Queue("empresas-queue", true);
    }

    /**
     * Cola para tickets de Gerencia
     */
    @Bean
    public Queue gerenciaQueue() {
        return new Queue("gerencia-queue", true);
    }

    // ============================================================
    // BINDINGS - Conectan Exchange con Queues
    // ============================================================

    /**
     * Binding: Exchange → caja-queue
     */
    @Bean
    public Binding cajaBinding(Queue cajaQueue, DirectExchange ticketeroExchange) {
        return BindingBuilder
            .bind(cajaQueue)
            .to(ticketeroExchange)
            .with("caja-queue");
    }

    /**
     * Binding: Exchange → personal-queue
     */
    @Bean
    public Binding personalBinding(Queue personalQueue, DirectExchange ticketeroExchange) {
        return BindingBuilder
            .bind(personalQueue)
            .to(ticketeroExchange)
            .with("personal-queue");
    }

    /**
     * Binding: Exchange → empresas-queue
     */
    @Bean
    public Binding empresasBinding(Queue empresasQueue, DirectExchange ticketeroExchange) {
        return BindingBuilder
            .bind(empresasQueue)
            .to(ticketeroExchange)
            .with("empresas-queue");
    }

    /**
     * Binding: Exchange → gerencia-queue
     */
    @Bean
    public Binding gerenciaBinding(Queue gerenciaQueue, DirectExchange ticketeroExchange) {
        return BindingBuilder
            .bind(gerenciaQueue)
            .to(ticketeroExchange)
            .with("gerencia-queue");
    }

    // ============================================================
    // MESSAGE CONVERTER
    // ============================================================

    /**
     * Converter para serializar/deserializar mensajes como JSON
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate configurado con JSON converter
     * Usado para publicar mensajes
     */
    @Bean
    public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        MessageConverter jsonMessageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}
