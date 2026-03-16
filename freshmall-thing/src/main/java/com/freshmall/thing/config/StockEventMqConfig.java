package com.freshmall.thing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmall.common.mq.OrderMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class StockEventMqConfig {

    @Value("${stock.event.consume.max-attempts:3}")
    private int maxAttempts;

    @Value("${stock.event.consume.initial-interval-ms:1000}")
    private long initialInterval;

    @Value("${stock.event.consume.multiplier:2.0}")
    private double multiplier;

    @Value("${stock.event.consume.max-interval-ms:10000}")
    private long maxInterval;

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
    public MessageConverter stockEventJacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RetryOperationsInterceptor stockEventRetryInterceptor(RabbitTemplate rabbitTemplate) {
        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
                rabbitTemplate,
                OrderMqConstants.STOCK_EVENT_DLX,
                OrderMqConstants.STOCK_EVENT_DLQ_ROUTING_KEY);

        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialInterval, multiplier, maxInterval)
                .recoverer(recoverer)
                .build();
    }

    @Bean(name = "stockEventListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory stockEventListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter stockEventJacksonMessageConverter,
            RetryOperationsInterceptor stockEventRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setDefaultRequeueRejected(false);
        factory.setMessageConverter(stockEventJacksonMessageConverter);
        factory.setAdviceChain(stockEventRetryInterceptor);
        return factory;
    }
}
