package com.face.recognition.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String FACE_RECOGNITION_QUEUE = "face_recognition_queue";

    @Bean
    public Queue faceRecognitionQueue() {
        return new Queue(FACE_RECOGNITION_QUEUE, true);
    }

    @Bean
    public org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory(
            @Value("${spring.rabbitmq.host}") String host,
            @Value("${spring.rabbitmq.port}") int port,
            @Value("${spring.rabbitmq.username}") String username,
            @Value("${spring.rabbitmq.password}") String password) {
        org.springframework.amqp.rabbit.connection.CachingConnectionFactory connectionFactory = new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(
                host, port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);

        // RabbitMQ Java client max body size: 256MB
        // The default is 64MB. A 22MB video grows under Base64 and can exceed it.
        connectionFactory.getRabbitConnectionFactory().setMaxInboundMessageBodySize(256 * 1024 * 1024);

        return connectionFactory;
    }

    @Bean
    public org.springframework.amqp.support.converter.MessageConverter jsonMessageConverter() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // Max string length 100MB. Jackson 2.15+ defaults to 20MB, which Base64 video exceeds.
        mapper.getFactory().setStreamReadConstraints(
                com.fasterxml.jackson.core.StreamReadConstraints.builder()
                        .maxStringLength(100 * 1024 * 1024)
                        .build());
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter(mapper);
    }
}
