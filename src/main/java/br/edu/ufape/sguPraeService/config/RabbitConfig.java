package br.edu.ufape.sguPraeService.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // Exchange de Autenticação (Legado / Compatibilidade)
    public static final String AUTH_EXCHANGE = "auth-role-exchange";

    // Infraestrutura de Notificações (Exchange, Fila SSE e Dead Letter Queue)
    public static final String NOTIFICACAO_EXCHANGE = "sgu.notificacoes.exchange";
    public static final String QUEUE_SSE = "sgu.notificacoes.auth.sse.queue";
    public static final String DLQ_SSE = "sgu.notificacoes.auth.sse.dlq";
    public static final String DLX_SSE = "sgu.notificacoes.dlx";
    public static final String DLQ_ROUTING_KEY = "notificacao.dlq";

    @Bean
    public TopicExchange authRoleExchange() {
        return new TopicExchange(AUTH_EXCHANGE);
    }

    @Bean
    public TopicExchange notificacaoExchange() {
        return new TopicExchange(NOTIFICACAO_EXCHANGE);
    }

    @Bean
    public DirectExchange notificacaoDeadLetterExchange() {
        return new DirectExchange(DLX_SSE);
    }

    @Bean
    public Queue notificacaoDeadLetterQueue() {
        return new Queue(DLQ_SSE, true);
    }

    @Bean
    public Binding notificacaoDeadLetterBinding() {
        return BindingBuilder.bind(notificacaoDeadLetterQueue())
                .to(notificacaoDeadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue sseQueue() {
        return QueueBuilder.durable(QUEUE_SSE)
                .withArgument("x-dead-letter-exchange", DLX_SSE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding bindingSse(Queue sseQueue, TopicExchange notificacaoExchange) {
        return BindingBuilder.bind(sseQueue)
                .to(notificacaoExchange)
                .with("notificacao.*");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
