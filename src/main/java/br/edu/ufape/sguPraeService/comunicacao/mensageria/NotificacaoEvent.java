package br.edu.ufape.sguPraeService.comunicacao.mensageria;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificacaoEvent(
        UUID id,
        UUID destinatarioId,
        String perfilDestino,
        String titulo,
        String mensagem,
        String tipo,
        LocalDateTime dataHoraGeracao
) {

    public static NotificacaoEvent paraUsuario(UUID destinatarioId, String titulo, String mensagem, String tipo) {
        return new NotificacaoEvent(UUID.randomUUID(), destinatarioId, null, titulo, mensagem, tipo, LocalDateTime.now());
    }

    public static NotificacaoEvent paraPerfil(String perfilDestino, String titulo, String mensagem, String tipo) {
        return new NotificacaoEvent(UUID.randomUUID(), null, perfilDestino, titulo, mensagem, tipo, LocalDateTime.now());
    }
}