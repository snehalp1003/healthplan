package com.me.healthplan;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.me.healthplan.rabbitmq.IndexingListener;

@SpringBootApplication
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.me.healthplan*" )
public class HealthplanApplication {
    
    public final static String MESSAGE_QUEUE = "indexing-queue";
    public final static String topicExchange = "spring-boot-exchange";

    public static void main(String[] args) {
        SpringApplication.run(HealthplanApplication.class, args);
    }
    
    @Bean
    Queue queue() {
        return new Queue(MESSAGE_QUEUE, false);
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange(topicExchange);
    }

    @Bean
    Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("snehal.indexing.#");
    }

    @Bean
    SimpleMessageListenerContainer container(ConnectionFactory connectionFactory,
                                             MessageListenerAdapter listenerAdapter) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(MESSAGE_QUEUE);
        container.setMessageListener(listenerAdapter);
        return container;
    }

    @Bean
    MessageListenerAdapter listenerAdapter(IndexingListener receiver) {
        return new MessageListenerAdapter(receiver, "receiveMessage");
    }
}
