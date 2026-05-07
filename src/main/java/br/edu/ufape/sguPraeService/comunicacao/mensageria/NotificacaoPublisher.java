package br.edu.ufape.sguPraeService.comunicacao.mensageria;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String NOTIFICACAO_EXCHANGE = "sgu.notificacoes.exchange";
    private static final String NOTIFICACAO_ROUTING_KEY = "notificacao.prae";

    public void publicar(NotificacaoEvent evento) {
        try {
            rabbitTemplate.convertAndSend(NOTIFICACAO_EXCHANGE, NOTIFICACAO_ROUTING_KEY, evento);
            log.info("Evento de notificação publicado: [{}] {}", evento.tipo(), evento.titulo());
        } catch (Exception e) {
            log.error("Falha ao publicar evento de notificação no RabbitMQ", e);
        }
    }
}