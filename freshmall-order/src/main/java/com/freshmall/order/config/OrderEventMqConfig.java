package com.freshmall.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmall.common.mq.OrderMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderEventMqConfig {

    @Bean
    public DirectExchange orderStockEventExchange() {
        return new DirectExchange(OrderMqConstants.STOCK_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderStockEventDlx() {
        return new DirectExchange(OrderMqConstants.STOCK_EVENT_DLX, true, false);
    }

    @Bean
    public Queue orderStockEventQueue() {
        return QueueBuilder.durable(OrderMqConstants.STOCK_EVENT_QUEUE)
                .withArgument("x-dead-letter-exchange", OrderMqConstants.STOCK_EVENT_DLX)
                .withArgument("x-dead-letter-routing-key", OrderMqConstants.STOCK_EVENT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderStockEventDlq() {
        return QueueBuilder.durable(OrderMqConstants.STOCK_EVENT_DLQ).build();
    }

    @Bean
    public Binding orderStockEventBinding(Queue orderStockEventQueue, DirectExchange orderStockEventExchange) {
        return BindingBuilder.bind(orderStockEventQueue)
                .to(orderStockEventExchange)
                .with(OrderMqConstants.STOCK_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding orderStockEventDlqBinding(Queue orderStockEventDlq, DirectExchange orderStockEventDlx) {
        return BindingBuilder.bind(orderStockEventDlq)
                .to(orderStockEventDlx)
                .with(OrderMqConstants.STOCK_EVENT_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
