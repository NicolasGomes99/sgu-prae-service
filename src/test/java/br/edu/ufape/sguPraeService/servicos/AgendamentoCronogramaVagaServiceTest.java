package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.dados.AgendamentoRepository;
import br.edu.ufape.sguPraeService.dados.CancelamentoAgendamentoRepository;
import br.edu.ufape.sguPraeService.dados.CronogramaRepository;
import br.edu.ufape.sguPraeService.dados.VagaRepository;
import br.edu.ufape.sguPraeService.exceptions.GlobalAccessDeniedException;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.models.enums.ModalidadeAgendamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgendamentoCronogramaVagaServiceTest {

    private AgendamentoRepository agendamentoRepository;
    private CancelamentoAgendamentoRepository cancelamentoRepository;
    private CronogramaRepository cronogramaRepository;
    private VagaRepository vagaRepository;
    private AuthenticatedUserProvider authenticatedUserProvider;
    private NotificacaoPublisher notificacaoPublisher;
    private ModelMapper modelMapper;

    private AgendamentoService agendamentoService;
    private CancelamentoService cancelamentoService;
    private CronogramaService cronogramaService;
    private VagaService vagaService;

    private Estudante estudante;
    private Profissional profissional;
    private Cronograma cronograma;
    private Vaga vaga;

    @BeforeEach
    void setUp() {
        agendamentoRepository = mock(AgendamentoRepository.class);
        cancelamentoRepository = mock(CancelamentoAgendamentoRepository.class);
        cronogramaRepository = mock(CronogramaRepository.class);
        vagaRepository = mock(VagaRepository.class);
        authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
        notificacaoPublisher = mock(NotificacaoPublisher.class);
        modelMapper = new ModelMapper();

        agendamentoService = new AgendamentoService(agendamentoRepository, modelMapper, authenticatedUserProvider, notificacaoPublisher);
        cancelamentoService = new CancelamentoService(cancelamentoRepository, authenticatedUserProvider, notificacaoPublisher);
        cronogramaService = new CronogramaService(cronogramaRepository, authenticatedUserProvider);
        vagaService = new VagaService(vagaRepository, modelMapper, authenticatedUserProvider);

        estudante = new Estudante();
        estudante.setId(UUID.randomUUID());
        estudante.setNome("Carlos Aluno");

        profissional = new Profissional();
        profissional.setId(UUID.randomUUID());
        profissional.setNome("Dra. Ana Psicóloga");
        profissional.setEspecialidade("Psicologia");

        cronograma = new Cronograma();
        cronograma.setId(10L);
        cronograma.setData(LocalDate.now().plusDays(2));
        cronograma.setProfissional(profissional);

        vaga = new Vaga();
        vaga.setId(20L);
        vaga.setDisponivel(true);
        vaga.setHoraInicio(LocalTime.of(14, 0));
        vaga.setHoraFim(LocalTime.of(14, 45));
        vaga.setCronograma(cronograma);
    }

    @Test
    @DisplayName("AgendamentoService: Deve bloquear novo agendamento dentro do intervalo mínimo de 24 horas")
    void deveBloquearAgendamentoEmMenosDe24Horas() {
        Agendamento recente = new Agendamento();
        recente.setDataCriacao(LocalDateTime.now().minusHours(5));

        when(agendamentoRepository.findTopByEstudante_IdAndDataCriacaoIsNotNullOrderByDataCriacaoDesc(estudante.getId()))
                .thenReturn(Optional.of(recente));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agendamentoService.agendar(vaga, estudante, ModalidadeAgendamento.PRESENCIAL));

        assertTrue(ex.getMessage().contains("24 horas"));
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("AgendamentoService: Deve bloquear segundo agendamento ativo para a mesma data do cronograma")
    void deveBloquearAgendamentoDuplicadoNaMesmaData() {
        when(agendamentoRepository.findTopByEstudante_IdAndDataCriacaoIsNotNullOrderByDataCriacaoDesc(estudante.getId()))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.existsByEstudante_IdAndDataAndAtivoTrue(estudante.getId(), cronograma.getData()))
                .thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agendamentoService.agendar(vaga, estudante, ModalidadeAgendamento.REMOTO));

        assertTrue(ex.getMessage().contains("possui um agendamento ativo para a data"));
    }

    @Test
    @DisplayName("AgendamentoService: Deve agendar com sucesso e notificar o profissional responsável")
    void deveAgendarENotificarProfissional() {
        when(agendamentoRepository.findTopByEstudante_IdAndDataCriacaoIsNotNullOrderByDataCriacaoDesc(estudante.getId()))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.existsByEstudante_IdAndDataAndAtivoTrue(estudante.getId(), cronograma.getData()))
                .thenReturn(false);
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        Agendamento salvo = agendamentoService.agendar(vaga, estudante, ModalidadeAgendamento.PRESENCIAL);

        assertEquals(estudante, salvo.getEstudante());
        assertEquals(ModalidadeAgendamento.PRESENCIAL, salvo.getModalidade());
        verify(notificacaoPublisher, times(1)).publicar(argThat(evt ->
                evt.destinatarioId().equals(profissional.getId()) && evt.tipo().equals("AGENDAMENTO")
        ));
    }

    @Test
    @DisplayName("AgendamentoService: Deve validar titularidade (Anti-IDOR) na busca e regra de 2h na alteração de modalidade")
    void deveValidarIdorERegraDe2HorasNaAlteracaoDeModalidade() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(50L);
        agendamento.setEstudante(estudante);
        agendamento.setVaga(vaga);
        agendamento.setData(LocalDate.now().plusDays(1));
        agendamento.setModalidade(ModalidadeAgendamento.PRESENCIAL);

        when(agendamentoRepository.findById(50L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Terceiro não relacionado tentando buscar agendamento -> GlobalAccessDeniedException
        UUID intrusoId = UUID.randomUUID();
        when(authenticatedUserProvider.getUserId()).thenReturn(intrusoId);
        when(authenticatedUserProvider.hasAnyRole("GESTOR", "ADMINISTRADOR")).thenReturn(false);
        assertThrows(GlobalAccessDeniedException.class, () -> agendamentoService.buscar(50L));

        // 2. Profissional tentando alterar modalidade (exclusivo do estudante titular) -> GlobalAccessDeniedException
        when(authenticatedUserProvider.getUserId()).thenReturn(profissional.getId());
        assertThrows(GlobalAccessDeniedException.class,
                () -> agendamentoService.alterarModalidade(50L, ModalidadeAgendamento.REMOTO));

        // 3. Estudante titular alterando com mais de 2h de antecedência -> Sucesso + 2 notificações (aluno e profissional)
        when(authenticatedUserProvider.getUserId()).thenReturn(estudante.getId());
        Agendamento atualizado = agendamentoService.alterarModalidade(50L, ModalidadeAgendamento.REMOTO);
        assertEquals(ModalidadeAgendamento.REMOTO, atualizado.getModalidade());
        verify(notificacaoPublisher, times(2)).publicar(any(NotificacaoEvent.class));

        // 4. Estudante titular tentando alterar faltando menos de 2h -> IllegalArgumentException
        agendamento.setData(LocalDate.now());
        vaga.setHoraInicio(LocalTime.now().plusMinutes(30));
        assertThrows(IllegalArgumentException.class,
                () -> agendamentoService.alterarModalidade(50L, ModalidadeAgendamento.PRESENCIAL));
    }

    @Test
    @DisplayName("CancelamentoService: Deve registrar cancelamento, notificar estudante e profissional e bloquear acesso de terceiros")
    void deveSalvarCancelamentoEBloquearIdorNaBusca() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(50L);
        agendamento.setEstudante(estudante);
        agendamento.setVaga(vaga);
        agendamento.setData(LocalDate.now().plusDays(2));

        CancelamentoAgendamento cancelamento = new CancelamentoAgendamento();
        cancelamento.setId(77L);
        cancelamento.setMotivo("Imprevisto médico");
        cancelamento.setAgendamento(agendamento);

        when(cancelamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cancelamentoRepository.findById(77L)).thenReturn(Optional.of(cancelamento));

        CancelamentoAgendamento salvo = cancelamentoService.salvar(cancelamento);
        assertNotNull(salvo.getDataCancelamento());
        verify(notificacaoPublisher, times(2)).publicar(any(NotificacaoEvent.class));

        // Intruso tentando visualizar motivo de cancelamento alheio -> 403 GlobalAccessDeniedException
        when(authenticatedUserProvider.getUserId()).thenReturn(UUID.randomUUID());
        when(authenticatedUserProvider.hasAnyRole("GESTOR", "ADMINISTRADOR")).thenReturn(false);
        assertThrows(GlobalAccessDeniedException.class, () -> cancelamentoService.buscar(77L));
    }

    @Test
    @DisplayName("VagaService e CronogramaService: Deve gerar vagas corretamente e validar dono do cronograma/vaga")
    void deveGerarVagasEValidarProprietario() {
        List<LocalTime> horarios = List.of(LocalTime.of(8, 0), LocalTime.of(9, 0));
        List<Vaga> geradas = vagaService.gerarVagas(horarios, LocalTime.of(0, 45));

        assertEquals(2, geradas.size());
        assertEquals(LocalTime.of(8, 0), geradas.get(0).getHoraInicio());
        assertEquals(LocalTime.of(8, 45), geradas.get(0).getHoraFim());
        assertEquals(LocalTime.of(9, 45), geradas.get(1).getHoraFim());

        when(cronogramaRepository.findById(10L)).thenReturn(Optional.of(cronograma));
        doThrow(new GlobalAccessDeniedException("Sem permissão"))
                .when(authenticatedUserProvider).validateOwnershipOrRoles(profissional.getId(), "GESTOR", "ADMINISTRADOR");

        assertThrows(GlobalAccessDeniedException.class, () -> cronogramaService.deletar(10L));
    }
}
