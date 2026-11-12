package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.BeneficioRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.FolhaPagamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.PagamentoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.PagamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.dados.BeneficioRepository;
import br.edu.ufape.sguPraeService.dados.PagamentoRepository;
import br.edu.ufape.sguPraeService.exceptions.LimiteBeneficiosExcedidoException;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.models.enums.MotivoEncerramento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BeneficioAndPagamentoServiceTest {

    private BeneficioRepository beneficioRepository;
    private PagamentoRepository pagamentoRepository;
    private NotificacaoPublisher notificacaoPublisher;
    private ModelMapper modelMapper;
    private BeneficioService beneficioService;
    private PagamentoService pagamentoService;

    @BeforeEach
    void setUp() {
        beneficioRepository = mock(BeneficioRepository.class);
        pagamentoRepository = mock(PagamentoRepository.class);
        notificacaoPublisher = mock(NotificacaoPublisher.class);
        modelMapper = new ModelMapper();
        beneficioService = new BeneficioService(beneficioRepository, modelMapper, notificacaoPublisher);
        pagamentoService = new PagamentoService(pagamentoRepository, modelMapper, beneficioRepository);
    }

    private Estudante criarEstudantePadrao() {
        Estudante e = new Estudante();
        e.setId(UUID.randomUUID());
        e.setNome("Maria Estudante");
        e.setCpf("12345678909");
        e.setEmail("maria@ufape.edu.br");

        Curso c = new Curso();
        c.setId(1L);
        c.setNome("Ciência da Computação");
        e.setCurso(c);

        DadosBancarios db = new DadosBancarios();
        db.setId(10L);
        db.setBanco("001");
        db.setAgencia("1234");
        db.setConta("56789-0");
        db.setTipoConta("CORRENTE");
        e.setDadosBancarios(db);
        return e;
    }

    private TipoBeneficio criarTipoBeneficio(Long id, String descricao) {
        TipoBeneficio tb = new TipoBeneficio();
        tb.setId(id);
        tb.setTipo("AUXILIO");
        tb.setDescricao(descricao);
        tb.setValorBeneficio(new BigDecimal("400.00"));
        return tb;
    }

    @Test
    @DisplayName("BeneficioService: Deve bloquear concessão do 3º benefício ativo para o mesmo estudante")
    void deveBloquearTerceiroBeneficioAtivoParaMesmoEstudante() {
        Estudante estudante = criarEstudantePadrao();
        Beneficio novoBeneficio = new Beneficio();
        novoBeneficio.setEstudantes(estudante);
        novoBeneficio.setTipoBeneficio(criarTipoBeneficio(1L, "Auxílio Moradia"));

        when(beneficioRepository.countByEstudantesIdAndAtivoTrue(estudante.getId())).thenReturn(2L);

        assertThrows(LimiteBeneficiosExcedidoException.class, () -> beneficioService.salvar(novoBeneficio));
        verify(beneficioRepository, never()).save(any());
    }

    @Test
    @DisplayName("BeneficioService: Deve prorrogar benefício ativo, recusar inativo e disparar notificação")
    void deveProrrogarBeneficioAtivoERecusarInativo() throws Exception {
        Estudante estudante = criarEstudantePadrao();
        Beneficio beneficio = new Beneficio();
        beneficio.setId(100L);
        beneficio.setAtivo(true);
        beneficio.setEstudantes(estudante);
        beneficio.setTipoBeneficio(criarTipoBeneficio(1L, "Auxílio Alimentação"));
        beneficio.setFimBeneficio(YearMonth.now().plusMonths(1));

        when(beneficioRepository.findById(100L)).thenReturn(Optional.of(beneficio));
        when(beneficioRepository.save(any(Beneficio.class))).thenAnswer(inv -> inv.getArgument(0));

        YearMonth novoPrazo = YearMonth.now().plusMonths(6);
        Beneficio prorrogado = beneficioService.prorrogar(100L, novoPrazo, "Prorrogado por desempenho acadêmico");

        assertEquals(novoPrazo, prorrogado.getFimBeneficio());
        assertEquals("Prorrogado por desempenho acadêmico", prorrogado.getObservacaoProrrogacao());
        verify(notificacaoPublisher, times(1)).publicar(any(NotificacaoEvent.class));

        // Tentar prorrogar benefício já encerrado/inativo deve lançar IllegalArgumentException
        beneficio.setAtivo(false);
        assertThrows(IllegalArgumentException.class,
                () -> beneficioService.prorrogar(100L, novoPrazo.plusMonths(2), "Tentativa inválida"));
    }

    @Test
    @DisplayName("BeneficioService: Deve cancelar benefício e processar encerramento automático de vencidos")
    void deveCancelarBeneficioEProcessarVencidos() {
        Estudante estudante = criarEstudantePadrao();
        Beneficio vencido = new Beneficio();
        vencido.setId(1L);
        vencido.setAtivo(true);
        vencido.setStatus(true);
        vencido.setEstudantes(estudante);
        vencido.setTipoBeneficio(criarTipoBeneficio(1L, "Auxílio Transporte"));
        vencido.setFimBeneficio(YearMonth.now().minusMonths(1)); // Vencido no mês passado

        Beneficio vigente = new Beneficio();
        vigente.setId(2L);
        vigente.setAtivo(true);
        vigente.setStatus(true);
        vigente.setEstudantes(estudante);
        vigente.setTipoBeneficio(criarTipoBeneficio(2L, "Auxílio Creche"));
        vigente.setFimBeneficio(YearMonth.now().plusMonths(3)); // Ainda vigente

        when(beneficioRepository.findAllByAtivoTrueAndStatusTrue()).thenReturn(List.of(vencido, vigente));

        beneficioService.processarBeneficiosVencidos();

        assertFalse(vencido.isAtivo());
        assertFalse(vencido.isStatus());
        assertEquals(MotivoEncerramento.CONCLUSAO_BENEFICIO, vencido.getMotivoEncerramento());
        assertTrue(vigente.isAtivo(), "Benefício vigente não deve ser encerrado");
        verify(beneficioRepository, times(1)).save(vencido);
        verify(notificacaoPublisher, times(1)).publicar(any(NotificacaoEvent.class));
    }

    @Test
    @DisplayName("PagamentoService: Deve gerar Folha de Pagamento agrupando múltiplos benefícios por estudante")
    void deveGerarFolhaPagamentoAgrupadaPorEstudante() {
        Estudante estudante = criarEstudantePadrao();

        Beneficio b1 = new Beneficio();
        b1.setId(10L);
        b1.setEstudantes(estudante);
        b1.setTipoBeneficio(criarTipoBeneficio(1L, "Auxílio Moradia"));

        Beneficio b2 = new Beneficio();
        b2.setId(11L);
        b2.setEstudantes(estudante);
        b2.setTipoBeneficio(criarTipoBeneficio(2L, "Auxílio Alimentação"));

        Pagamento p1 = new Pagamento(1L, new BigDecimal("400.00"), LocalDate.now(), 5, 2026, "LOTE-01", true, b1);
        Pagamento p2 = new Pagamento(2L, new BigDecimal("250.00"), LocalDate.now(), 5, 2026, "LOTE-01", true, b2);

        when(pagamentoRepository.findByAnoReferenciaAndMesReferenciaAndNumeroLote(2026, 5, "LOTE-01"))
                .thenReturn(List.of(p1, p2));

        FolhaPagamentoResponse folha = pagamentoService.gerarFolhaPagamento(2026, 5, "LOTE-01");

        assertEquals(1, folha.getQuantidadeEstudantes());
        assertEquals(2, folha.getQuantidadeTotalPagamentos());
        assertEquals(new BigDecimal("650.00"), folha.getValorTotalGeral());
        assertEquals("001", folha.getItens().getFirst().getBanco());
        assertTrue(folha.getItens().getFirst().getDescricoesBeneficios().contains("Auxílio Moradia"));
        assertTrue(folha.getItens().getFirst().getDescricoesBeneficios().contains("Auxílio Alimentação"));
    }

    @Test
    @DisplayName("Regressão Crítica: PagamentoResponse NÃO deve esvaziar a coleção de pagamentos da entidade Beneficio (orphanRemoval)")
    void pagamentoResponseNaoDeveApagarPagamentosDaEntidadeBeneficio() {
        Estudante estudante = criarEstudantePadrao();
        Beneficio beneficio = new Beneficio();
        beneficio.setId(50L);
        beneficio.setEstudantes(estudante);
        beneficio.setTipoBeneficio(criarTipoBeneficio(1L, "Auxílio Moradia"));

        Pagamento p1 = new Pagamento(1L, new BigDecimal("400.00"), LocalDate.now(), 4, 2026, "01", true, beneficio);
        Pagamento p2 = new Pagamento(2L, new BigDecimal("400.00"), LocalDate.now(), 5, 2026, "02", true, beneficio);
        beneficio.setPagamentos(new ArrayList<>(List.of(p1, p2)));

        PagamentoResponse response = new PagamentoResponse(p1, modelMapper);

        assertEquals(1L, response.getId());
        assertNotNull(response.getBeneficio());
        assertEquals(2, beneficio.getPagamentos().size(),
                "A lista de pagamentos da entidade JPA jamais deve sofrer .clear() ao montar o DTO!");
    }

    @Test
    @DisplayName("DTOs BeneficioRequest e PagamentoRequest: Devem converter para entidade sem alterar configuração global do ModelMapper")
    void deveConverterDtosSemMutarSingletonModelMapper() {
        var matchingStrategyOriginal = modelMapper.getConfiguration().getMatchingStrategy();

        BeneficioRequest beneficioReq = new BeneficioRequest();
        beneficioReq.setEstudanteId(UUID.randomUUID());
        beneficioReq.setTipoBeneficioId(3L);
        beneficioReq.setInicioBeneficio(YearMonth.of(2026, 1));
        beneficioReq.setFimBeneficio(YearMonth.of(2026, 12));
        beneficioReq.setValorPagamento(new BigDecimal("500.00"));

        Beneficio b = beneficioReq.convertToEntity(beneficioReq, modelMapper);
        assertEquals(new BigDecimal("500.00"), b.getValorPagamento());
        assertEquals(YearMonth.of(2026, 1), b.getInicioBeneficio());

        PagamentoRequest pagReq = new PagamentoRequest(
                99L, new BigDecimal("500.00"), LocalDate.of(2026, 5, 10), 5, 2026, "LOTE-99"
        );
        Pagamento p = pagReq.convertToEntity(pagReq, modelMapper);
        assertEquals(99L, p.getBeneficio().getId());
        assertEquals("LOTE-99", p.getNumeroLote());

        assertEquals(matchingStrategyOriginal, modelMapper.getConfiguration().getMatchingStrategy());
    }
}
