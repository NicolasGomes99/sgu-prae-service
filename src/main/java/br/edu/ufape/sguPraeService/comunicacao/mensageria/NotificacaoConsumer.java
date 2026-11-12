package br.edu.ufape.sguPraeService.comunicacao.mensageria;

import br.edu.ufape.sguPraeService.config.RabbitConfig;
import br.edu.ufape.sguPraeService.dados.UsuarioRepository;
import br.edu.ufape.sguPraeService.models.Estudante;
import br.edu.ufape.sguPraeService.models.Gestor;
import br.edu.ufape.sguPraeService.models.Profissional;
import br.edu.ufape.sguPraeService.models.Usuario;
import br.edu.ufape.sguPraeService.servicos.EmailService;
import br.edu.ufape.sguPraeService.servicos.interfaces.KeycloakService;
import br.edu.ufape.sguPraeService.servicos.interfaces.NotificacaoRedisServiceInterface;
import br.edu.ufape.sguPraeService.servicos.interfaces.NotificacaoSseServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoConsumer {

    private final NotificacaoSseServiceInterface sseService;
    private final NotificacaoRedisServiceInterface redisService;
    private final UsuarioRepository usuarioRepository;
    private final KeycloakService keycloakService;
    private final EmailService emailService;

    @RabbitListener(queues = RabbitConfig.QUEUE_SSE)
    public void receberNotificacao(NotificacaoEvent evento) {
        List<String> emailsParaDisparo = new ArrayList<>();

        // 1. Notificação individual
        if (evento.destinatarioId() != null) {
            Usuario usuario = usuarioRepository.findById(evento.destinatarioId()).orElse(null);
            if (usuario != null && Boolean.TRUE.equals(usuario.getAtivo())) {
                processarNotificacao(usuario.getId(), evento);
                if (usuario.getEmail() != null && !usuario.getEmail().isBlank()) {
                    emailsParaDisparo.add(usuario.getEmail());
                }
            }
        }
        // 2. Notificação em Broadcast (Grupos ou Todos)
        else if (evento.perfilDestino() != null) {
            String destino = evento.perfilDestino().trim().toUpperCase();

            if (destino.equals("TODOS")) {
                emailsParaDisparo.addAll(distribuirParaTodos(evento));
            } else if (destino.equals("ADMINISTRADOR")) {
                emailsParaDisparo.addAll(distribuirParaRoleKeycloak(destino, evento));
            } else {
                emailsParaDisparo.addAll(distribuirParaPerfilBanco(destino, evento));
            }
        }

        // 3. Disparo de E-mail Assíncrono (BCC)
        if (!emailsParaDisparo.isEmpty()) {
            emailService.enviarEmailEmMassa(emailsParaDisparo, evento.titulo(), evento.mensagem());
        }
    }

    private List<String> distribuirParaTodos(NotificacaoEvent evento) {
        List<String> emails = new ArrayList<>();
        List<Usuario> todosUsuarios = usuarioRepository.findAllByAtivoTrue();

        for (Usuario usuario : todosUsuarios) {
            processarNotificacao(usuario.getId(), evento);
            if (usuario.getEmail() != null && !usuario.getEmail().isBlank()) {
                emails.add(usuario.getEmail());
            }
        }
        return emails;
    }

    private List<String> distribuirParaRoleKeycloak(String roleName, NotificacaoEvent evento) {
        List<String> emails = new ArrayList<>();
        try {
            List<UUID> usuariosIds = keycloakService.obterUsuariosPorRole(roleName);
            List<Usuario> usuarios = usuarioRepository.findAllById(usuariosIds);
            for (Usuario usuario : usuarios) {
                if (Boolean.TRUE.equals(usuario.getAtivo())) {
                    processarNotificacao(usuario.getId(), evento);
                    if (usuario.getEmail() != null && !usuario.getEmail().isBlank()) {
                        emails.add(usuario.getEmail());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Não foi possível buscar usuários no Keycloak para a role '{}': {}", roleName, e.getMessage());
        }
        return emails;
    }

    private List<String> distribuirParaPerfilBanco(String nomePerfil, NotificacaoEvent evento) {
        List<String> emails = new ArrayList<>();
        Class<? extends Usuario> classeUsuario = mapearNomeParaClasse(nomePerfil);

        if (classeUsuario != null) {
            List<Usuario> destinatarios = usuarioRepository.findByTipoUsuarioAtivo(classeUsuario);
            for (Usuario usuario : destinatarios) {
                processarNotificacao(usuario.getId(), evento);
                if (usuario.getEmail() != null && !usuario.getEmail().isBlank()) {
                    emails.add(usuario.getEmail());
                }
            }
        } else {
            log.info("Perfil '{}' não corresponde a subclasse direta de Usuario no banco; buscando por Role no Keycloak.", nomePerfil);
            emails.addAll(distribuirParaRoleKeycloak(nomePerfil, evento));
        }
        return emails;
    }

    private void processarNotificacao(UUID usuarioId, NotificacaoEvent evento) {
        redisService.guardarNotificacaoOffline(usuarioId, evento);
        sseService.emitirSinalDeNovaNotificacao(usuarioId);
    }

    private Class<? extends Usuario> mapearNomeParaClasse(String nomePerfil) {
        return switch (nomePerfil.toUpperCase()) {
            case "ESTUDANTE", "ALUNO" -> Estudante.class;
            case "PROFISSIONAL", "TECNICO" -> Profissional.class;
            case "GESTOR" -> Gestor.class;
            default -> null;
        };
    }
}
