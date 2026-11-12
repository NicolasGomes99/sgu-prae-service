package br.edu.ufape.sguPraeService.dados;

import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.BeneficioResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.cancelamentoAgendamento.CancelamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.cronograma.CronogramaResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.dadosBancarios.DadosBancariosResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.endereco.EnderecoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.EstudanteResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.PagamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoatendimento.TipoAtendimentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoBeneficio.TipoBeneficioResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoEtnia.TipoEtniaResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.vaga.VagaResponse;
import br.edu.ufape.sguPraeService.dados.conversores.YearMonthConverter;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.models.enums.ModalidadeAgendamento;
import br.edu.ufape.sguPraeService.models.enums.NaturezaBeneficio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class RepositoryQuerydslAndDtoMappingTest {

    @Test
    @DisplayName("QueryDSL Security & Bindings: Todos os repositórios devem configurar bindings e whitelists sem NullPointerException")
    void deveConfigurarQuerydslBindingsEmTodosOsRepositoriosSemErro() {
        PagamentoRepository pagamentoRepo = mock(PagamentoRepository.class, CALLS_REAL_METHODS);
        QuerydslBindings pagBindings = new QuerydslBindings();
        assertDoesNotThrow(() -> pagamentoRepo.customize(pagBindings, QPagamento.pagamento));
        // Verifica que excludeUnlistedProperties(true) foi ativado no PagamentoRepository
        Boolean excludeUnlisted = (Boolean) ReflectionTestUtils.getField(pagBindings, "excludeUnlistedProperties");
        assertEquals(Boolean.TRUE, excludeUnlisted);

        BeneficioRepository beneficioRepo = mock(BeneficioRepository.class, CALLS_REAL_METHODS);
        QuerydslBindings benBindings = new QuerydslBindings();
        assertDoesNotThrow(() -> beneficioRepo.customize(benBindings, QBeneficio.beneficio));

        AgendamentoRepository agendamentoRepo = mock(AgendamentoRepository.class, CALLS_REAL_METHODS);
        QuerydslBindings agBindings = new QuerydslBindings();
        assertDoesNotThrow(() -> agendamentoRepo.customize(agBindings, QAgendamento.agendamento));

        CronogramaRepository cronogramaRepo = mock(CronogramaRepository.class, CALLS_REAL_METHODS);
        QuerydslBindings croBindings = new QuerydslBindings();
        assertDoesNotThrow(() -> cronogramaRepo.customize(croBindings, QCronograma.cronograma));

        EstudanteRepository estudanteRepo = mock(EstudanteRepository.class, CALLS_REAL_METHODS);
        QuerydslBindings estBindings = new QuerydslBindings();
        assertDoesNotThrow(() -> estudanteRepo.customize(estBindings, QEstudante.estudante));
    }

    @Test
    @DisplayName("YearMonthConverter: Deve converter bidirecionalmente entre YearMonth e LocalDate (dia 1) lidando com nulos")
    void deveConverterYearMonthParaLocalDateEViceVersa() {
        YearMonthConverter converter = new YearMonthConverter();

        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));

        YearMonth ym = YearMonth.of(2026, 5);
        LocalDate dbDate = converter.convertToDatabaseColumn(ym);
        assertEquals(LocalDate.of(2026, 5, 1), dbDate);

        YearMonth reconvertido = converter.convertToEntityAttribute(LocalDate.of(2026, 5, 28));
        assertEquals(ym, reconvertido);
    }

    @Test
    @DisplayName("DTO Mappings: Todos os objetos *Response devem mapear entidades completas com relacionamentos sem erros do ModelMapper")
    void deveMapearTodosOsDtosDeRespostaSemErro() {
        ModelMapper modelMapper = new ModelMapper();

        TipoEtnia etnia = new TipoEtnia(1L, "Parda");
        Curso curso = new Curso(2L, "Ciência da Computação", 8, true);
        Endereco endereco = new Endereco();
        endereco.setId(3L);
        endereco.setRua("Rua A");
        endereco.setCep("55290000");
        endereco.setBairro("Centro");
        endereco.setCidade("Garanhuns");
        endereco.setEstado("PE");
        endereco.setNumero("100");
        endereco.setComplemento("Ap 1");
        DadosBancarios banco = new DadosBancarios(4L, "Aluno", "12345-6", "0001", "Banco do Brasil", "CORRENTE");

        Estudante estudante = new Estudante();
        estudante.setId(UUID.randomUUID());
        estudante.setNome("Estudante Completo");
        estudante.setEmail("estudante@ufape.edu.br");
        estudante.setCpf("12345678901");
        estudante.setTipoEtnia(etnia);
        estudante.setCurso(curso);
        estudante.setEndereco(endereco);
        estudante.setDadosBancarios(banco);
        estudante.setMatricula("2026001");
        estudante.setRendaPercapta(new BigDecimal("600.00"));

        EstudanteResponse estResp = new EstudanteResponse(estudante, modelMapper);
        assertEquals("Estudante Completo", estResp.getNome());
        assertEquals("Ciência da Computação", estResp.getCurso().getNome());
        assertEquals("Garanhuns", estResp.getEndereco().getCidade());
        assertEquals("Banco do Brasil", estResp.getDadosBancarios().getBanco());

        Profissional prof = new Profissional();
        prof.setId(UUID.randomUUID());
        prof.setNome("Dr. Psicólogo");
        prof.setSiape("998877");
        prof.setEspecialidade("Psicologia Clínica");
        ProfissionalResponse profResp = new ProfissionalResponse(prof, modelMapper);
        assertEquals("998877", profResp.getSiape());

        TipoAtendimento ta = new TipoAtendimento();
        ta.setId(5L);
        ta.setNome("Psicologia");
        ta.setTempoAtendimento(LocalTime.of(0, 30));
        ta.setHorarios(List.of(LocalTime.of(8, 0), LocalTime.of(9, 0)));
        TipoAtendimentoResponse taResp = new TipoAtendimentoResponse(ta, modelMapper);
        assertEquals("Psicologia", taResp.getNome());

        Vaga vaga = new Vaga();
        vaga.setId(6L);
        vaga.setHoraInicio(LocalTime.of(8, 0));
        vaga.setHoraFim(LocalTime.of(8, 30));
        vaga.setDisponivel(true);

        Cronograma cronograma = new Cronograma();
        cronograma.setId(7L);
        cronograma.setData(LocalDate.now());
        cronograma.setVagas(List.of(vaga));
        cronograma.setTipoAtendimento(ta);
        cronograma.setProfissional(prof);
        cronograma.setAtivo(true);
        vaga.setCronograma(cronograma);

        VagaResponse vagaResp = new VagaResponse(vaga, modelMapper);
        assertEquals(6L, vagaResp.getId());

        CronogramaResponse croResp = new CronogramaResponse(cronograma, modelMapper);
        assertEquals(7L, croResp.getId());
        assertEquals("Psicologia", croResp.getTipoAtendimento().getNome());

        Agendamento agendamento = new Agendamento();
        agendamento.setId(8L);
        agendamento.setData(LocalDate.now());
        agendamento.setVaga(vaga);
        agendamento.setEstudante(estudante);
        agendamento.setAtivo(true);
        agendamento.setModalidade(ModalidadeAgendamento.PRESENCIAL);
        agendamento.setConfirmacaoEnviada(false);
        agendamento.setDataCriacao(LocalDateTime.now());
        AgendamentoResponse agResp = new AgendamentoResponse(agendamento, modelMapper);
        assertEquals(8L, agResp.getId());
        assertEquals("Estudante Completo", agResp.getEstudante().getNome());

        CancelamentoAgendamento cancelamento = new CancelamentoAgendamento();
        cancelamento.setId(9L);
        cancelamento.setMotivo("Imprevisto");
        cancelamento.setDataCancelamento(LocalDateTime.now());
        cancelamento.setAgendamento(agendamento);
        CancelamentoResponse cancResp = new CancelamentoResponse(cancelamento, modelMapper);
        assertEquals("Imprevisto", cancResp.getMotivo());

        TipoBeneficio tb = new TipoBeneficio(10L, "Auxílio Alimentação", NaturezaBeneficio.AUXILIO, "Mensal", new BigDecimal("350.00"), true);
        TipoBeneficioResponse tbResp = new TipoBeneficioResponse(tb, modelMapper);
        assertEquals("Auxílio Alimentação", tbResp.getTipo());

        Beneficio beneficio = new Beneficio();
        beneficio.setId(11L);
        beneficio.setTipoBeneficio(tb);
        beneficio.setEstudantes(estudante);
        beneficio.setValorPagamento(new BigDecimal("350.00"));
        beneficio.setInicioBeneficio(YearMonth.of(2026, 1));
        beneficio.setFimBeneficio(YearMonth.of(2026, 12));

        Pagamento pagamento = new Pagamento(12L, new BigDecimal("350.00"), LocalDate.now(), 5, 2026, "LOTE-05", true, beneficio);
        beneficio.addPagamento(pagamento);

        BeneficioResponse benResp = new BeneficioResponse(beneficio, modelMapper);
        assertEquals(11L, benResp.getId());
        assertEquals("Auxílio Alimentação", benResp.getTipoBeneficio().getTipo());

        PagamentoResponse pagResp = new PagamentoResponse(pagamento, modelMapper);
        assertEquals(12L, pagResp.getId());
        assertEquals(1, beneficio.getPagamentos().size());

        assertNotNull(new CursoResponse(curso, modelMapper));
        assertNotNull(new TipoEtniaResponse(etnia, modelMapper));
        assertNotNull(new EnderecoResponse(endereco, modelMapper));
        assertNotNull(new DadosBancariosResponse(banco, modelMapper));
    }
}
