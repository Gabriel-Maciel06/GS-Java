package br.com.fiap.alertacom.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração da topologia AMQP do RabbitMQ para mensageria assíncrona de alertas críticos.
 * Define a Topic Exchange, a fila durável de alertas críticos, o binding de roteamento
 * e o conversor Jackson2JsonMessageConverter com suporte a LocalDateTime, UTF-8 e emojis.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_ALERTA = "alerta.exchange";
    public static final String QUEUE_ALERTAS_CRITICOS = "alertas.criticos.queue";
    public static final String ROUTING_KEY_CRITICO = "alerta.critico.#";

    @Bean
    public TopicExchange alertaExchange() {
        return new TopicExchange(EXCHANGE_ALERTA, true, false);
    }

    @Bean
    public Queue alertasCriticosQueue() {
        return QueueBuilder.durable(QUEUE_ALERTAS_CRITICOS).build();
    }

    @Bean
    public Binding bindingAlertasCriticos(Queue alertasCriticosQueue, TopicExchange alertaExchange) {
        return BindingBuilder
                .bind(alertasCriticosQueue)
                .to(alertaExchange)
                .with(ROUTING_KEY_CRITICO);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
