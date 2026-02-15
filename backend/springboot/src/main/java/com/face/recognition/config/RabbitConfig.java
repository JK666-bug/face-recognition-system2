package com.face.recognition.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String FACE_RECOGNITION_QUEUE = "face_recognition_queue";

    @Bean
    public Queue faceRecognitionQueue() {
        // durable: true, exclusive: false, autoDelete: false
        return new Queue(FACE_RECOGNITION_QUEUE, true);
    }

    @Bean
    public org.springframework.amqp.support.converter.MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
    }
}
