package br.edu.ufape.sguPraeService.fachada;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.RelatorioFinanceiroResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.EstudanteRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.PagamentoCPFRequest;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.exceptions.EstudanteSemAuxilioAtivoException;
import br.edu.ufape.sguPraeService.exceptions.UnavailableVagaException;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.models.enums.ModalidadeAgendamento;
import br.edu.ufape.sguPraeService.servicos.interfaces.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FachadaCompleteTest {

    private ProfissionalService profissionalService;
    private TipoAtendimentoService tipoAtendimentoService;
    private CronogramaService cronogramaService;
    private VagaService vagaService;
    private EnderecoService enderecoService;
    private DadosBancariosService dadosBancariosService;
    private EstudanteService estudanteService;
    private ModelMapper modelMapper;
    private AuthenticatedUserProvider authenticatedUserProvider;
    private AgendamentoService agendamentoService;
    private CancelamentoService cancelamentoService;
    private TipoBeneficioService tipoBeneficioService;
    private BeneficioService beneficioService;
    private PagamentoService pagamentoService;
    private ArmazenamentoService armazenamentoService;
    private NotificacaoPublisher notificacaoPublisher;
    private KeycloakService keycloakService;
    private UsuarioService usuarioService;
    private TipoEtniaService tipoEtniaService;
    private GestorService gestorService;
    private CursoService cursoService;
    private NotificacaoSseServiceInterface notificacaoSseService;
    private NotificacaoRedisServiceInterface notificacaoRedisService;

    private Fachada fachada;

    @BeforeEach
    void setUp() {
        profissionalService = mock(ProfissionalService.class);
        tipoAtendimentoService = mock(TipoAtendimentoService.class);
        cronogramaService = mock(CronogramaService.class);
        vagaService = mock(VagaService.class);
        enderecoService = mock(EnderecoService.class);
        dadosBancariosService = mock(DadosBancariosService.class);
        estudanteService = mock(EstudanteService.class);
        modelMapper = new ModelMapper();
        authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
        agendamentoService = mock(AgendamentoService.class);
        cancelamentoService = mock(CancelamentoService.class);
        tipoBeneficioService = mock(TipoBeneficioService.class);
        beneficioService = mock(BeneficioService.class);
        pagamentoService = mock(PagamentoService.class);
        armazenamentoService = mock(ArmazenamentoService.class);
        notificacaoPublisher = mock(NotificacaoPublisher.class);
        keycloakService = mock(KeycloakService.class);
        usuarioService = mock(UsuarioService.class);
        tipoEtniaService = mock(TipoEtniaService.class);
        gestorService = mock(GestorService.class);
        cursoService = mock(CursoService.class);
        notificacaoSseService = mock(NotificacaoSseServiceInterface.class);
        notificacaoRedisService = mock(NotificacaoRedisServiceInterface.class);

        fachada = new Fachada(
                profissionalService, tipoAtendimentoService, cronogramaService, vagaService,
                enderecoService, dadosBancariosService, estudanteService, modelMapper,
                authenticatedUserProvider, agendamentoService, cancelamentoService,
                tipoBeneficioService, beneficioService, pagamentoService, armazenamentoService,
                notificacaoPublisher, keycloakService, usuarioService, tipoEtniaService,
                gestorService, cursoService, notificacaoSseService, notificacaoRedisService
        );
    }

    @Test
    @DisplayName("Fachada.cadastrarEstudante: Deve vincular Curso e TipoEtnia, e realizar rollback completo (Keycloak + arquivos) se o banco falhar")
    void deveCadastrarEstudanteEFazerRollbackEmCasoDeErro() throws Exception {
        EstudanteRequest dto = new EstudanteRequest();
        dto.setNome("João Silva");
        dto.setEmail("joao@ufape.edu.br");
        dto.setSenha("Senha@123");
        dto.setCpf("12345678909");
        dto.setTipoEtniaId(1L);
        dto.setCursoId(2L);
        dto.setMatricula("2026001");
        dto.setRendaPercapta(new BigDecimal("800.00"));

        TipoEtnia etnia = new TipoEtnia(1L, "Parda");
        Curso curso = new Curso(2L, "BCC", 8, true);
        UUID kcId = UUID.randomUUID();
        Documento docSalvo = new Documento(1L, "doc.pdf", "/app/storage/doc.pdf");

        when(tipoEtniaService.buscarTipoEtnia(1L)).thenReturn(etnia);
        when(cursoService.buscar(2L)).thenReturn(curso);
        when(keycloakService.createUser("joao@ufape.edu.br", "Senha@123", "ESTUDANTE")).thenReturn(kcId);
        when(armazenamentoService.salvarArquivo(any())).thenReturn(List.of(docSalvo));

        // 1. Cenário de Sucesso
        when(estudanteService.salvarEstudante(any(Estudante.class))).thenAnswer(inv -> inv.getArgument(0));
        MultipartFile file = new MockMultipartFile("arquivos", "doc.pdf", "application/pdf", "%PDF".getBytes());

        Estudante salvo = (Estudante) fachada.cadastrarEstudante(dto, List.of(file));
        assertEquals(kcId, salvo.getId());
        assertEquals(curso, salvo.getCurso());
        assertEquals(etnia, salvo.getTipoEtnia());
        assertEquals(1, salvo.getDocumentos().size());

        // 2. Cenário de Falha no Banco -> Deve apagar os arquivos físicos recém-gravados E deletar usuário no Keycloak
        when(estudanteService.salvarEstudante(any(Estudante.class))).thenThrow(new RuntimeException("Falha no banco"));
        assertThrows(RuntimeException.class, () -> fachada.cadastrarEstudante(dto, List.of(file)));

        verify(armazenamentoService, times(1)).deletarArquivosFisicos(List.of(docSalvo));
        verify(keycloakService, times(1)).deleteUser(kcId.toString());
    }

    @Test
    @DisplayName("Fachada.salvarPagamentoPorCpf: Deve buscar aluno por CPF, validar múltiplos benefícios e salvar pagamento")
    void deveSalvarPagamentoPorCpfValidandoRegrasDeNegocio() {
        Estudante est = new Estudante();
        est.setId(UUID.randomUUID());
        est.setCpf("12345678909");

        PagamentoCPFRequest req = new PagamentoCPFRequest();
        req.setCpf("123.456.789-09");
        req.setValor(new BigDecimal("400.00"));
        req.setData(LocalDate.now());
        req.setMesReferencia(5);
        req.setAnoReferencia(2026);
        req.setNumeroLote("01");

        when(estudanteService.listarEstudantes(any(), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(est)));

        // 1. Estudante sem benefícios ativos -> EstudanteSemAuxilioAtivoException
        when(beneficioService.listarPorEstudante(est.getId())).thenReturn(List.of());
        assertThrows(EstudanteSemAuxilioAtivoException.class, () -> fachada.salvarPagamentoPorCpf(req));

        // 2. Estudante com 2 benefícios ativos mas sem informar tipoBeneficioId -> IllegalArgumentException
        Beneficio b1 = new Beneficio();
        b1.setId(1L);
        TipoBeneficio tb1 = new TipoBeneficio();
        tb1.setId(10L);
        b1.setTipoBeneficio(tb1);

        Beneficio b2 = new Beneficio();
        b2.setId(2L);
        TipoBeneficio tb2 = new TipoBeneficio();
        tb2.setId(20L);
        b2.setTipoBeneficio(tb2);

        when(beneficioService.listarPorEstudante(est.getId())).thenReturn(List.of(b1, b2));
        assertThrows(IllegalArgumentException.class, () -> fachada.salvarPagamentoPorCpf(req));

        // 3. Informando tipoBeneficioId=20L -> Seleciona b2 e salva com sucesso
        req.setTipoBeneficioId(20L);
        when(pagamentoService.salvarIndividual(any(Pagamento.class))).thenAnswer(inv -> inv.getArgument(0));

        Pagamento pagamentoSalvo = fachada.salvarPagamentoPorCpf(req);
        assertEquals(b2, pagamentoSalvo.getBeneficio());
        assertEquals(new BigDecimal("400.00"), pagamentoSalvo.getValor());
    }

    @Test
    @DisplayName("Fachada.agendarVaga: Deve converter ObjectOptimisticLockingFailureException em UnavailableVagaException (409)")
    void deveTratarConcorrenciaNoAgendamentoDeVaga() {
        AgendamentoRequest req = new AgendamentoRequest();
        req.setVagaId(1L);
        req.setModalidade(ModalidadeAgendamento.PRESENCIAL);

        Vaga vaga = new Vaga();
        vaga.setId(1L);
        vaga.setDisponivel(true);

        when(vagaService.buscar(1L)).thenReturn(vaga);
        when(authenticatedUserProvider.getUserId()).thenReturn(UUID.randomUUID());
        when(estudanteService.buscarEstudante(any())).thenReturn(new Estudante());
        when(vagaService.salvar(vaga)).thenThrow(new ObjectOptimisticLockingFailureException(Vaga.class, 1L));

        assertThrows(UnavailableVagaException.class, () -> fachada.agendarVaga(req));
    }

    @Test
    @DisplayName("Fachada.gerarRelatorioFinanceiro e logout: Deve consolidar relatório financeiro e limpar SSE via token no logout")
    void deveGerarRelatorioFinanceiroELimparSseNoLogout() {
        UUID u1 = UUID.randomUUID();
        when(pagamentoService.obterIdsEstudantesComPagamento(any())).thenReturn(List.of(u1));
        when(pagamentoService.obterValorTotalPagamentosAtivos(any())).thenReturn(new BigDecimal("1200.00"));
        when(pagamentoService.obterValorTotalPorTipoBeneficio(any()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, "Moradia", new BigDecimal("1200.00")}));
        when(beneficioService.obterQuantidadeBeneficiadosPorCurso(List.of(u1)))
                .thenReturn(List.of(Map.of("cursoId", 1L, "cursoNome", "BCC", "quantidadeBeneficiados", 1L)));

        RelatorioFinanceiroResponse relatorio = fachada.gerarRelatorioFinanceiro(null, 1L);
        assertEquals(1200.0, relatorio.getTotalGeral());
        assertEquals(1, relatorio.getQuantidadePessoasAtendidas());
        assertEquals(1, relatorio.getQuantidadeCursosDistintos());

        // Logout extraindo 'sub' do accessToken quando SecurityContext está vazio
        when(authenticatedUserProvider.getUserId()).thenThrow(new IllegalStateException("Sem contexto"));
        UUID subId = UUID.randomUUID();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + subId + "\"}").getBytes(StandardCharsets.UTF_8));
        String jwt = "header." + payload + ".sig";

        fachada.logout(jwt, "refresh-token");
        verify(notificacaoSseService).removerTodosEmittersDoUsuario(subId);
        verify(keycloakService).logout(jwt, "refresh-token");
    }
}
