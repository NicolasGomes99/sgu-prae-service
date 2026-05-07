package br.edu.ufape.sguPraeService.comunicacao.mensageria;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificacaoEvent(
        UUID destinatarioId,    // ID do usuário Keycloak que vai receber (Pode ser null se for para um grupo)
        String perfilDestino,   // Ex: "ALUNO", "PROFISSIONAL", "GESTOR" (Útil para envios em massa)
        String titulo,          // Ex: "Novo Agendamento"
        String mensagem,        // Ex: "Você tem um novo atendimento marcado para 10/10/2026 às 14:00"
        String tipo,            // Ex: "AGENDAMENTO", "BENEFICIO", "SISTEMA"
        LocalDateTime dataHoraGeracao
) {
    // Construtor estático para facilitar a criação de notificações para um usuário específico
    public static NotificacaoEvent paraUsuario(UUID destinatarioId, String titulo, String mensagem, String tipo) {
        return new NotificacaoEvent(destinatarioId, null, titulo, mensagem, tipo, LocalDateTime.now());
    }

    // Construtor estático para notificações em grupo (ex: todos os gestores)
    public static NotificacaoEvent paraGrupo(String perfilDestino, String titulo, String mensagem, String tipo) {
        return new NotificacaoEvent(null, perfilDestino, titulo, mensagem, tipo, LocalDateTime.now());
    }
}