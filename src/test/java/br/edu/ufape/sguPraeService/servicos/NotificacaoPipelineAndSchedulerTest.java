package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoConsumer;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.dados.AgendamentoRepository;
import br.edu.ufape.sguPraeService.dados.BeneficioRepository;
import br.edu.ufape.sguPraeService.dados.UsuarioRepository;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.servicos.interfaces.KeycloakService;
import br.edu.ufape.sguPraeService.servicos.interfaces.NotificacaoRedisServiceInterface;
import br.edu.ufape.sguPraeService.servicos.interfaces.NotificacaoSseServiceInterface;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificacaoPipelineAndSchedulerTest {

    private NotificacaoSseServiceInterface sseServiceMock;
    private NotificacaoRedisServiceInterface redisServiceMock;
    private UsuarioRepository usuarioRepository;
    private KeycloakService keycloakService;
    private EmailService emailServiceMock;
    private NotificacaoConsumer consumer;

    @BeforeEach
    void setUp() {
        sseServiceMock = mock(NotificacaoSseServiceInterface.class);
        redisServiceMock = mock(NotificacaoRedisServiceInterface.class);
        usuarioRepository = mock(UsuarioRepository.class);
        keycloakService = mock(KeycloakService.class);
        emailServiceMock = mock(EmailService.class);

        consumer = new NotificacaoConsumer(
                sseServiceMock, redisServiceMock, usuarioRepository, keycloakService, emailServiceMock
        );
    }

    @Test
    @DisplayName("NotificacaoConsumer: Deve processar notificação individual salvando no Redis, emitindo SSE e enviando e-mail")
    void deveProcessarNotificacaoIndividual() {
        UUID userId = UUID.randomUUID();
        Estudante est = new Estudante();
        est.setId(userId);
        est.setAtivo(true);
        est.setEmail("aluno@ufape.edu.br");

        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(est));

        NotificacaoEvent evento = NotificacaoEvent.paraUsuario(userId, "Aviso", "Mensagem", "SISTEMA");
        consumer.receberNotificacao(evento);

        verify(redisServiceMock).guardarNotificacaoOffline(userId, evento);
        verify(sseServiceMock).emitirSinalDeNovaNotificacao(userId);
        verify(emailServiceMock).enviarEmailEmMassa(List.of("aluno@ufape.edu.br"), "Aviso", "Mensagem");
    }

    @Test
    @DisplayName("NotificacaoConsumer: Deve distribuir broadcast para TODOS, por subclasse (ESTUDANTE/ALUNO) e por role Keycloak (ADMINISTRADOR)")
    void deveDistribuirBroadcastPorPerfilERole() {
        Estudante e1 = new Estudante();
        e1.setId(UUID.randomUUID());
        e1.setAtivo(true);
        e1.setEmail("e1@ufape.edu.br");

        Gestor g1 = new Gestor();
        g1.setId(UUID.randomUUID());
        g1.setAtivo(true);
        g1.setEmail("g1@ufape.edu.br");

        when(usuarioRepository.findAllByAtivoTrue()).thenReturn(List.of(e1, g1));
        when(usuarioRepository.findByTipoUsuarioAtivo(Estudante.class)).thenReturn(List.of(e1));
        when(keycloakService.obterUsuariosPorRole("ADMINISTRADOR")).thenReturn(List.of(g1.getId()));
        when(usuarioRepository.findAllById(List.of(g1.getId()))).thenReturn(List.of(g1));

        // 1. Broadcast TODOS
        consumer.receberNotificacao(NotificacaoEvent.paraPerfil("TODOS", "Geral", "Texto", "AVISO"));
        verify(redisServiceMock).guardarNotificacaoOffline(eq(e1.getId()), any());
        verify(redisServiceMock).guardarNotificacaoOffline(eq(g1.getId()), any());
        verify(emailServiceMock).enviarEmailEmMassa(eq(List.of("e1@ufape.edu.br", "g1@ufape.edu.br")), eq("Geral"), eq("Texto"));

        // 2. Broadcast ALUNO (mapeado para Estudante.class)
        clearInvocations(redisServiceMock, sseServiceMock, emailServiceMock);
        consumer.receberNotificacao(NotificacaoEvent.paraPerfil("ALUNO", "Edital", "Novo edital", "EDITAL"));
        verify(redisServiceMock, times(1)).guardarNotificacaoOffline(eq(e1.getId()), any());
        verify(emailServiceMock).enviarEmailEmMassa(eq(List.of("e1@ufape.edu.br")), eq("Edital"), eq("Novo edital"));

        // 3. Broadcast ADMINISTRADOR (via Keycloak)
        clearInvocations(redisServiceMock, sseServiceMock, emailServiceMock);
        consumer.receberNotificacao(NotificacaoEvent.paraPerfil("ADMINISTRADOR", "Alerta", "Manutenção", "SISTEMA"));
        verify(redisServiceMock, times(1)).guardarNotificacaoOffline(eq(g1.getId()), any());
    }

    @Test
    @DisplayName("NotificacaoRedisService: Deve ordenar notificações da mais recente para a mais antiga e paginar corretamente")
    @SuppressWarnings("unchecked")
    void deveOrdenarEPaginarNotificacoesNoRedis() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        NotificacaoRedisService service = new NotificacaoRedisService(redisTemplate);
        UUID userId = UUID.randomUUID();

        NotificacaoEvent antiga = new NotificacaoEvent(
                UUID.randomUUID(), userId, null, "Antiga", "Msg 1", "INFO", LocalDateTime.now().minusHours(3)
        );
        NotificacaoEvent recente = new NotificacaoEvent(
                UUID.randomUUID(), userId, null, "Recente", "Msg 2", "INFO", LocalDateTime.now()
        );

        when(hashOps.values("notificacoes:nao-lidas:" + userId)).thenReturn(List.of(antiga, recente));

        Page<NotificacaoEvent> pagina = service.buscarNotificacoesNaoLidas(userId, 0, 1);

        assertEquals(2, pagina.getTotalElements());
        assertEquals(1, pagina.getContent().size());
        assertEquals("Recente", pagina.getContent().getFirst().titulo(), "A notificação mais recente deve vir primeiro");

        service.marcarUnicaComoLida(userId, recente.id());
        verify(hashOps).delete("notificacoes:nao-lidas:" + userId, recente.id().toString());

        service.marcarTodasComoLidas(userId);
        verify(redisTemplate).delete("notificacoes:nao-lidas:" + userId);
    }

    @Test
    @DisplayName("NotificacaoSseService e EmailService: Deve gerenciar ciclo de vida SSE e enviar e-mail BCC")
    void deveGerenciarSseEEnviarEmailBcc() {
        NotificacaoSseService sseService = new NotificacaoSseService();
        UUID userId = UUID.randomUUID();

        SseEmitter emitter = sseService.subscrever(userId);
        assertNotNull(emitter);
        assertDoesNotThrow(() -> sseService.emitirSinalDeNovaNotificacao(userId));
        assertDoesNotThrow(() -> sseService.removerTodosEmittersDoUsuario(userId));

        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        EmailService emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "remetente", "nao-responda@ufape.edu.br");

        emailService.enviarEmailEmMassa(List.of("a@ufape.edu.br", "b@ufape.edu.br"), "Assunto", "<b>Olá</b>");
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("NotificationScheduler: Deve confirmar agendamentos na janela de 2h e enviar lembrete mensal de benefícios")
    void deveExecutarRotinasDoNotificationScheduler() {
        AgendamentoRepository agendamentoRepo = mock(AgendamentoRepository.class);
        BeneficioRepository beneficioRepo = mock(BeneficioRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NotificacaoPublisher publisher = new NotificacaoPublisher(rabbitTemplate);

        NotificationScheduler scheduler = new NotificationScheduler(agendamentoRepo, beneficioRepo, publisher);

        Estudante est = new Estudante();
        est.setId(UUID.randomUUID());

        Vaga vaga = new Vaga();
        vaga.setHoraInicio(LocalTime.now().plusMinutes(45)); // Dentro da janela de 2h

        Agendamento agendamento = new Agendamento();
        agendamento.setData(LocalDate.now());
        agendamento.setVaga(vaga);
        agendamento.setEstudante(est);
        agendamento.setConfirmacaoEnviada(false);

        when(agendamentoRepo.findByConfirmacaoEnviadaFalseAndAtivoTrue()).thenReturn(List.of(agendamento));

        scheduler.confirmarAgendamentosPendentes();

        assertTrue(agendamento.isConfirmacaoEnviada());
        verify(agendamentoRepo).save(agendamento);
        verify(rabbitTemplate, times(1)).convertAndSend(eq("sgu.notificacoes.exchange"), eq("notificacao.prae"), any(NotificacaoEvent.class));

        // Lembrete mensal de benefícios
        Beneficio ben = new Beneficio();
        ben.setEstudantes(est);
        TipoBeneficio tb = new TipoBeneficio();
        tb.setDescricao("Auxílio Moradia");
        ben.setTipoBeneficio(tb);
        ben.setFimBeneficio(YearMonth.now().plusMonths(2));

        when(beneficioRepo.findAllByAtivoTrueAndStatusTrue()).thenReturn(List.of(ben));

        scheduler.notificarTempoRestanteBeneficios();
        verify(rabbitTemplate, times(2)).convertAndSend(eq("sgu.notificacoes.exchange"), eq("notificacao.prae"), any(NotificacaoEvent.class));
    }
}
