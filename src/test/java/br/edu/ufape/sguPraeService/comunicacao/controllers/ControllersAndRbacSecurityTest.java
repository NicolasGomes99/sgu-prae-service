package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.auth.TokenResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.BeneficioResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.RelatorioFinanceiroResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.PublicacaoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.CredorResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.EstudanteResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.RelatorioEstudanteAssistidoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.FolhaPagamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.PagamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalResponse;
import br.edu.ufape.sguPraeService.config.SwaggerConfig;
import br.edu.ufape.sguPraeService.config.WebConfig;
import br.edu.ufape.sguPraeService.config.filters.TokenResponseRewriteFilter;
import br.edu.ufape.sguPraeService.exceptions.GlobalAccessDeniedException;
import br.edu.ufape.sguPraeService.exceptions.GlobalExceptionHandler;
import br.edu.ufape.sguPraeService.exceptions.UnavailableVagaException;
import br.edu.ufape.sguPraeService.exceptions.auth.KeycloakAuthenticationException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.EstudanteNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.models.enums.ModalidadeAgendamento;
import br.edu.ufape.sguPraeService.models.enums.NaturezaBeneficio;
import br.edu.ufape.sguPraeService.servicos.VagaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        AuthController.class,
        UsuarioController.class,
        EstudanteController.class,
        ProfissionalController.class,
        GestorController.class,
        BeneficioController.class,
        PagamentoController.class,
        DadosBancariosController.class,
        TipoBeneficioController.class,
        AgendamentoController.class,
        CronogramaController.class,
        VagaController.class,
        TipoAtendimentoController.class,
        NotificacaoController.class,
        CursoController.class,
        TipoEtniaController.class
})
@Import({
        WebConfig.class,
        SwaggerConfig.class,
        TokenResponseRewriteFilter.class,
        GlobalExceptionHandler.class,
        ModelMapper.class
})
@EnableMethodSecurity
class ControllersAndRbacSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Fachada fachada;

    @MockitoBean
    private VagaService vagaService;

    @MockitoBean
    private AuthenticatedUserProvider authenticatedUserProvider;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Segurança WebConfig: Requisições anônimas para rotas protegidas devem retornar 401 Unauthorized")
    void deveBloquearRequisicoesAnonimasEmRotasProtegidas() throws Exception {
        mockMvc.perform(get("/pagamento/folha").param("ano", "2026").param("mes", "5"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/notificacoes/stream"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/notificacoes/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfilDestino\":\"TODOS\",\"titulo\":\"Aviso\",\"mensagem\":\"Msg\",\"tipo\":\"SISTEMA\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rotas Públicas e AuthController completo: /login, /refresh, /logout, /reset-password, /roles")
    @WithMockUser(roles = "ESTUDANTE")
    void deveCobrirTodosEndpointsDoAuthControllerERotasPublicas() throws Exception {
        TokenResponse tokenResp = new TokenResponse();
        tokenResp.setAccess_token("header.eyJleHAiOjE4OTM0NTYwMDB9.sig");
        tokenResp.setRefresh_token("refresh-xyz");
        when(fachada.login("aluno@ufape.edu.br", "SenhaForte123")).thenReturn(tokenResp);
        when(fachada.refresh(any())).thenReturn(tokenResp);
        when(fachada.getUserRoles()).thenReturn(List.of("ESTUDANTE"));
        when(jwtDecoder.decode("token-123")).thenReturn(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token-123")
                        .header("alg", "RS256")
                        .subject(UUID.randomUUID().toString())
                        .claim("resource_access", java.util.Map.of("sgu-prae", java.util.Map.of("roles", List.of("ESTUDANTE"))))
                        .build()
        );

        // 1. POST /login
        mockMvc.perform(post("/login")
                        .param("email", "aluno@ufape.edu.br")
                        .param("senha", "SenhaForte123"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));

        // 2. POST /refresh
        mockMvc.perform(post("/refresh").param("refresh_token", "refresh-xyz"))
                .andExpect(status().isOk());

        // 3. POST /logout
        mockMvc.perform(post("/logout")
                        .header("Authorization", "Bearer token-123")
                        .param("refresh_token", "refresh-xyz"))
                .andExpect(status().isNoContent());

        // 4. POST /reset-password
        mockMvc.perform(post("/reset-password").param("email", "aluno@ufape.edu.br"))
                .andExpect(status().isNoContent());

        // 5. GET /roles
        mockMvc.perform(get("/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("ESTUDANTE"));
    }

    @Test
    @WithMockUser(roles = "ESTUDANTE")
    @DisplayName("Segurança RBAC: ESTUDANTE tentando acessar folha de pagamento, broadcast ou criar curso deve receber 403 Forbidden")
    void deveBloquearEstudanteEmRotasDeGestaoCom403() throws Exception {
        mockMvc.perform(get("/pagamento/folha").param("ano", "2026").param("mes", "5"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/notificacoes/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfilDestino\":\"TODOS\",\"titulo\":\"T\",\"mensagem\":\"M\",\"tipo\":\"AVISO\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/curso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Novo Curso\",\"numeroPeriodos\":8}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/estudantes/credores"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ADMINISTRADOR", "GESTOR", "PROFISSIONAL", "ESTUDANTE"})
    @DisplayName("Cobertura Completa de UsuarioController, EstudanteController, ProfissionalController e GestorController")
    void deveCobrirTodosEndpointsDeUsuariosEstudantesProfissionaisEGestores() throws Exception {
        UUID userId = UUID.randomUUID();
        Usuario usuario = new Estudante();
        usuario.setId(userId);
        usuario.setNome("Usuário Teste");
        usuario.setEmail("user@ufape.edu.br");
        usuario.setCpf("52998224725");

        when(fachada.cadastrarEstudante(any(), any())).thenReturn(usuario);
        when(fachada.cadastrarProfissional(any())).thenReturn(usuario);
        when(fachada.cadastrarGestor(any())).thenReturn(usuario);
        when(fachada.buscarUsuario(userId)).thenReturn(usuario);
        when(fachada.listarUsuarios(any(), any())).thenReturn(new PageImpl<>(List.of(usuario)));
        when(fachada.buscarUsuarioAtual()).thenReturn(usuario);
        when(fachada.editarUsuario(any())).thenReturn(usuario);

        // --- UsuarioController (9 endpoints) ---
        String estudanteJson = """
                {
                  "nome": "Aluno Novo",
                  "email": "aluno@ufape.edu.br",
                  "cpf": "52998224725",
                  "senha": "Password123!",
                  "telefone": "(87) 99999-8888",
                  "dataNascimento": "2000-01-15",
                  "tipoEtniaId": 1,
                  "matricula": "2026001",
                  "cursoId": 1,
                  "rendaPercapta": 500.00,
                  "contatoFamilia": "(87) 98888-7777",
                  "deficiente": false,
                  "endereco": {
                    "rua": "Rua Principal",
                    "cep": "55290-000",
                    "bairro": "Centro",
                    "cidade": "Garanhuns",
                    "estado": "PE",
                    "numero": "10"
                  }
                }
                """;
        MockMultipartFile dadosPart = new MockMultipartFile("dados", "", "application/json", estudanteJson.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/usuario/estudante").file(dadosPart)).andExpect(status().isCreated());

        String profJson = """
                {
                  "nome": "Prof Novo",
                  "email": "prof@ufape.edu.br",
                  "cpf": "52998224725",
                  "senha": "Password123!",
                  "telefone": "(87) 99999-8888",
                  "dataNascimento": "1985-05-10",
                  "siape": "1234567",
                  "especialidade": "Psicologia"
                }
                """;
        mockMvc.perform(post("/usuario/profissional").contentType(MediaType.APPLICATION_JSON).content(profJson)).andExpect(status().isCreated());

        String gestorJson = """
                {
                  "nome": "Gestor Novo",
                  "email": "gestor@ufape.edu.br",
                  "cpf": "52998224725",
                  "senha": "Password123!",
                  "telefone": "(87) 99999-8888",
                  "dataNascimento": "1980-03-20",
                  "siape": "7654321"
                }
                """;
        mockMvc.perform(post("/usuario/gestor").contentType(MediaType.APPLICATION_JSON).content(gestorJson)).andExpect(status().isCreated());

        mockMvc.perform(get("/usuario/{id}", userId)).andExpect(status().isOk());
        mockMvc.perform(get("/usuario")).andExpect(status().isOk());
        mockMvc.perform(get("/usuario/current")).andExpect(status().isOk());
        mockMvc.perform(patch("/usuario").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Nome Editado\",\"telefone\":\"(87) 99999-8888\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/usuario")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/usuario/{id}", userId)).andExpect(status().isNoContent());

        // --- EstudanteController (14 endpoints) ---
        EstudanteResponse estResp = new EstudanteResponse();
        estResp.setId(userId);
        estResp.setNome("Estudante Teste");
        when(fachada.listarEstudantesComFiltrosExternos(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(estResp)));
        when(fachada.listarEstudantesPorCurso(eq(1L), any())).thenReturn(new PageImpl<>(List.of(estResp)));
        when(fachada.buscarEstudante(userId)).thenReturn(estResp);
        when(fachada.atualizarEstudante(any())).thenReturn(estResp);
        when(fachada.listarCredoresComBeneficiosAtivos(any())).thenReturn(new PageImpl<>(List.of(new CredorResponse())));
        when(fachada.listarCredoresPorBeneficio(eq(1L), any())).thenReturn(new PageImpl<>(List.of(new CredorResponse())));
        when(fachada.listarCredoresParaPublicacao(any())).thenReturn(new PageImpl<>(List.of(estResp)));
        when(fachada.listarCredoresPorCurso(eq(1L), any())).thenReturn(new PageImpl<>(List.of(new CredorResponse())));
        when(fachada.gerarRelatorioEstudanteAssistido(userId)).thenReturn(new RelatorioEstudanteAssistidoResponse());
        when(fachada.buscarEstudanteAtual()).thenReturn(estResp);
        when(fachada.buscarEstudantePorUserId(userId)).thenReturn(estResp);
        when(fachada.buscarDocumentosPorEstudante(userId)).thenReturn(List.of(new DocumentoResponse("doc.pdf", "YmFzZTY0")));

        mockMvc.perform(get("/estudantes")).andExpect(status().isOk());
        mockMvc.perform(get("/estudantes/curso/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/estudantes/{id}", userId)).andExpect(status().isOk());
        mockMvc.perform(patch("/estudantes").contentType(MediaType.APPLICATION_JSON).content("{\"rendaPercapta\":600.00}")).andExpect(status().isOk());
        mockMvc.perform(delete("/estudantes/{id}/", userId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/estudantes/credores")).andExpect(status().isOk());
        mockMvc.perform(get("/estudantes/credores/beneficio/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/estudantes/credores/publicacao")).andExpect(status().isOk());
        mockMvc.perform(get("/estudantes/credores/curso/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/estudantes/{id}/relatorio", userId)).andExpect(status().isOk());
        mockMvc.perform(get("/estudantes/current")).andExpect(status().isOk());
        mockMvc.perform(get("/estudantes/userid/{userId}", userId)).andExpect(status().isOk());
        MockMultipartFile docFile = new MockMultipartFile("arquivos", "doc.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mockMvc.perform(multipart("/estudantes/documentos").file(docFile)).andExpect(status().isNoContent());
        mockMvc.perform(get("/estudantes/{id}/documentos", userId)).andExpect(status().isOk());

        // --- ProfissionalController (5 endpoints) ---
        ProfissionalResponse profResp = new ProfissionalResponse();
        profResp.setId(userId);
        profResp.setSiape("123456");
        when(fachada.listarProfissionais()).thenReturn(List.of(profResp));
        when(fachada.buscarProfissional(userId)).thenReturn(profResp);
        when(fachada.buscarProfissionalAtual()).thenReturn(profResp);
        when(fachada.editarProfissional(any())).thenReturn(profResp);

        mockMvc.perform(get("/profissional")).andExpect(status().isOk());
        mockMvc.perform(get("/profissional/{id}", userId)).andExpect(status().isOk());
        mockMvc.perform(get("/profissional/current")).andExpect(status().isOk());
        mockMvc.perform(patch("/profissional").contentType(MediaType.APPLICATION_JSON).content("{\"especialidade\":\"Pedagogia\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/profissional/{id}", userId)).andExpect(status().isNoContent());

        // --- GestorController (2 endpoints) ---
        Gestor gestor = new Gestor();
        gestor.setId(userId);
        gestor.setNome("Gestor");
        gestor.setSiape("999999");
        when(fachada.buscarGestor(userId)).thenReturn(gestor);
        when(fachada.listarGestores(any(), any())).thenReturn(new PageImpl<>(List.of(gestor)));

        mockMvc.perform(get("/gestor/{id}", userId)).andExpect(status().isOk());
        mockMvc.perform(get("/gestor")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMINISTRADOR", "GESTOR", "ESTUDANTE"})
    @DisplayName("Cobertura Completa de BeneficioController, PagamentoController, DadosBancariosController e TipoBeneficioController")
    void deveCobrirTodosEndpointsFinanceirosEBeneficios() throws Exception {
        UUID estId = UUID.randomUUID();
        Beneficio beneficio = new Beneficio();
        beneficio.setId(1L);
        Documento termoDoc = new Documento(1L, "termo.pdf", "/tmp/termo.pdf");
        beneficio.setTermo(termoDoc);

        BeneficioResponse benResp = new BeneficioResponse();
        benResp.setId(1L);

        when(fachada.listarBeneficios(any(), any())).thenReturn(new PageImpl<>(List.of(beneficio)));
        when(fachada.mapToBeneficioResponse(any())).thenReturn(benResp);
        when(fachada.listarBeneficiosInativosComFiltrosExternos(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(benResp)));
        when(fachada.listarBeneficiosPorEstudanteId(eq(estId), any())).thenReturn(new PageImpl<>(List.of(beneficio)));
        when(fachada.buscarBeneficioComValidacaoAcesso(1L)).thenReturn(beneficio);
        when(fachada.converterDocumentosParaBase64(any())).thenReturn(List.of(new DocumentoResponse("termo.pdf", "YmFzZTY0")));
        when(fachada.salvarBeneficios(any(), any(), any(), any())).thenReturn(beneficio);
        when(fachada.editarBeneficios(anyLong(), any(), any(), any(), any())).thenReturn(beneficio);
        when(fachada.listarPagosPorMes(any())).thenReturn(new PageImpl<>(List.of(beneficio)));
        when(fachada.listarBeneficiosPorTipo(eq(1L), any())).thenReturn(new PageImpl<>(List.of(beneficio)));
        when(fachada.listarBeneficiosPendentesMesAtual(any(), any())).thenReturn(new PageImpl<>(List.of(benResp)));
        when(fachada.gerarRelatorioFinanceiro(any(), any())).thenReturn(RelatorioFinanceiroResponse.builder().totalGeral(1000.0).build());
        when(fachada.contarEstudantesBeneficiados()).thenReturn(15L);
        when(fachada.contarCursosDistintosComBeneficioAtivo()).thenReturn(4L);
        when(fachada.obterQuantidadeBeneficiadosPorCursoComFiltro(any())).thenReturn(List.of(Map.of("cursoNome", "BCC", "quantidadeBeneficiados", 10L)));
        when(fachada.prorrogarBeneficio(eq(1L), any())).thenReturn(benResp);

        // --- BeneficioController (17 endpoints) ---
        mockMvc.perform(get("/beneficio")).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/inativos")).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/estudante/{estudanteId}", estId)).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/{id}/termo", 1L)).andExpect(status().isOk());

        MockMultipartFile termoFile = new MockMultipartFile("termo", "termo.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mockMvc.perform(multipart("/beneficio")
                        .file(termoFile)
                        .param("estudanteId", estId.toString())
                        .param("tipoBeneficioId", "1")
                        .param("inicioBeneficio", "2026-01")
                        .param("fimBeneficio", "2026-12")
                        .param("horasBeneficio", "20")
                        .param("valorPagamento", "400.00"))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/beneficio/{id}", 1L)
                        .file(termoFile)
                        .with(req -> { req.setMethod("PATCH"); return req; })
                        .param("estudanteId", estId.toString())
                        .param("tipoBeneficioId", "1")
                        .param("inicioBeneficio", "2026-01")
                        .param("fimBeneficio", "2026-12")
                        .param("horasBeneficio", "20")
                        .param("valorPagamento", "400.00"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/beneficio/{id}/cancelar", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivoEncerramento\":\"CONCLUSAO_CURSO\",\"parecerTermino\":\"Concluiu\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/beneficio/{id}", 1L)).andExpect(status().isNoContent());
        mockMvc.perform(get("/beneficio/pagos")).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/tipo/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/pendentes")).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/relatorio/financeiro")).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/quantidade/beneficiados")).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/quantidade/cursos")).andExpect(status().isOk());
        mockMvc.perform(get("/beneficio/quantidade/beneficiados/por/curso")).andExpect(status().isOk());
        mockMvc.perform(patch("/beneficio/{id}/prorrogar", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novoPrazo\":\"2027-06\",\"observacoes\":\"Prorrogado\"}"))
                .andExpect(status().isOk());

        // --- PagamentoController (13 endpoints) ---
        Pagamento pagamento = new Pagamento(1L, new BigDecimal("400.00"), LocalDate.now(), 5, 2026, "LOTE-01", true, beneficio);
        PagamentoResponse pagResp = new PagamentoResponse();
        pagResp.setId(1L);
        when(fachada.listarPagamentos(any(), any())).thenReturn(new PageImpl<>(List.of(pagamento)));
        when(fachada.mapToPagamentoResponse(any())).thenReturn(pagResp);
        when(fachada.listarPagamentosPorBeneficioId(1L)).thenReturn(List.of(pagamento));
        when(fachada.buscarPagamento(1L)).thenReturn(pagamento);
        when(fachada.salvarPagamentos(any())).thenReturn(List.of(pagamento));
        when(fachada.salvarPagamentoPorCpf(any())).thenReturn(pagamento);
        when(fachada.editarPagamento(eq(1L), any())).thenReturn(pagamento);
        when(fachada.listarPagamentosPorValor(any(), any(), any())).thenReturn(new PageImpl<>(List.of(pagamento)));
        when(fachada.listarPagamentosPorEstudante(eq(estId), any(), any())).thenReturn(new PageImpl<>(List.of(pagResp)));
        when(fachada.gerarFolhaPagamento(2026, 5, null)).thenReturn(new FolhaPagamentoResponse());
        when(fachada.obterValorTotalPagamentosAtivos(any())).thenReturn(new BigDecimal("400.00"));
        when(fachada.obterValorTotalPorTipoBeneficio(any())).thenReturn(List.<Object[]>of(new Object[]{1L, "Auxílio", new BigDecimal("400.00")}));

        mockMvc.perform(get("/pagamento")).andExpect(status().isOk());
        mockMvc.perform(get("/pagamento/beneficio/{beneficioId}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/pagamento/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(post("/pagamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"beneficioId\":1,\"valor\":400.00,\"data\":\"2026-05-10\",\"mesReferencia\":5,\"anoReferencia\":2026,\"numeroLote\":\"L1\"}]"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/pagamento/cpf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"52998224725\",\"valor\":400.00,\"numeroLote\":\"L1\",\"data\":\"2026-05-10\",\"mesReferencia\":5,\"anoReferencia\":2026}"))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/pagamento/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroLote\":\"L2\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/pagamento/{id}", 1L)).andExpect(status().isNoContent());
        mockMvc.perform(patch("/pagamento/desativar/{id}", 1L)).andExpect(status().isNoContent());
        mockMvc.perform(get("/pagamento/valor/{min}/{max}", 100, 500)).andExpect(status().isOk());
        mockMvc.perform(get("/pagamento/estudante/{estudanteId}", estId)).andExpect(status().isOk());
        mockMvc.perform(get("/pagamento/folha").param("ano", "2026").param("mes", "5")).andExpect(status().isOk());
        mockMvc.perform(get("/pagamento/total")).andExpect(status().isOk());
        mockMvc.perform(get("/pagamento/total/tipo/beneficio")).andExpect(status().isOk());

        // --- DadosBancariosController (5 endpoints) ---
        DadosBancarios db = new DadosBancarios(1L, "Titular", "123456", "0001", "BB", "CORRENTE");
        when(fachada.buscarDadosBancarios(1L)).thenReturn(db);
        when(fachada.listarDadosBancarios(any())).thenReturn(new PageImpl<>(List.of(db)));
        when(fachada.salvarDadosBancarios(eq(estId), any())).thenReturn(db);
        when(fachada.atualizarDadosBancarios(eq(1L), any())).thenReturn(db);

        String dbJson = "{\"nomeTitular\":\"Titular\",\"conta\":\"123456\",\"agencia\":\"0001\",\"banco\":\"BB\",\"tipoConta\":\"CORRENTE\"}";
        mockMvc.perform(get("/dadosBancarios/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/dadosBancarios")).andExpect(status().isOk());
        mockMvc.perform(post("/dadosBancarios/{idEstudante}", estId).contentType(MediaType.APPLICATION_JSON).content(dbJson)).andExpect(status().isCreated());
        mockMvc.perform(patch("/dadosBancarios/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content(dbJson)).andExpect(status().isOk());
        mockMvc.perform(delete("/dadosBancarios/{id}", 1L)).andExpect(status().isNoContent());

        // --- TipoBeneficioController (8 endpoints) ---
        TipoBeneficio tb = new TipoBeneficio(1L, "Auxílio Moradia", NaturezaBeneficio.AUXILIO, "Desc", new BigDecimal("400.00"), true);
        when(fachada.listarTipoBeneficios(any(), any())).thenReturn(new PageImpl<>(List.of(tb)));
        when(fachada.listarTipoBeneficiosInativos(any(), any())).thenReturn(new PageImpl<>(List.of(tb)));
        when(fachada.buscarTipoBeneficio(1L)).thenReturn(tb);
        when(fachada.salvarTipoBeneficio(any())).thenReturn(tb);
        when(fachada.editarTipoBeneficio(eq(1L), any())).thenReturn(tb);
        when(fachada.contarTiposBeneficio()).thenReturn(3L);

        String tbJson = "{\"tipo\":\"Auxílio Moradia\",\"naturezaBeneficio\":\"AUXILIO\",\"descricao\":\"Desc\",\"valorBeneficio\":400.00}";
        mockMvc.perform(get("/tipo-beneficio")).andExpect(status().isOk());
        mockMvc.perform(get("/tipo-beneficio/inativos")).andExpect(status().isOk());
        mockMvc.perform(get("/tipo-beneficio/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(post("/tipo-beneficio").contentType(MediaType.APPLICATION_JSON).content(tbJson)).andExpect(status().isCreated());
        mockMvc.perform(patch("/tipo-beneficio/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content(tbJson)).andExpect(status().isOk());
        mockMvc.perform(delete("/tipo-beneficio/{id}", 1L)).andExpect(status().isNoContent());
        mockMvc.perform(patch("/tipo-beneficio/desativar/{id}", 1L)).andExpect(status().isNoContent());
        mockMvc.perform(get("/tipo-beneficio/quantidade")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMINISTRADOR", "GESTOR", "PROFISSIONAL", "ESTUDANTE"})
    @DisplayName("Cobertura Completa de Agendamento, Cronograma, Vaga, TipoAtendimento, Notificacao, Curso e TipoEtnia")
    void deveCobrirTodosEndpointsDeAgendamentosNotificacoesECadastrosBasicos() throws Exception {
        UUID userId = UUID.randomUUID();

        // --- AgendamentoController (11 endpoints) ---
        TipoAtendimento ta = new TipoAtendimento();
        ta.setId(1L);
        ta.setNome("Psicologia");
        ta.setTempoAtendimento(LocalTime.of(0, 30));
        ta.setHorarios(List.of(LocalTime.of(8, 0)));

        Cronograma cronograma = new Cronograma();
        cronograma.setId(1L);
        cronograma.setData(LocalDate.of(2026, 6, 15));
        cronograma.setTipoAtendimento(ta);

        Vaga vaga = new Vaga();
        vaga.setId(1L);
        vaga.setHoraInicio(LocalTime.of(8, 0));
        vaga.setHoraFim(LocalTime.of(8, 30));
        vaga.setDisponivel(true);
        vaga.setCronograma(cronograma);

        AgendamentoResponse agResp = new AgendamentoResponse();
        agResp.setId(1L);
        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setData(LocalDate.now());
        agendamento.setModalidade(ModalidadeAgendamento.PRESENCIAL);
        agendamento.setVaga(vaga);
        CancelamentoAgendamento cancelamento = new CancelamentoAgendamento();
        cancelamento.setId(1L);
        cancelamento.setMotivo("Motivo");
        cancelamento.setDataCancelamento(LocalDateTime.now());
        cancelamento.setAgendamento(agendamento);

        when(fachada.agendarVaga(any())).thenReturn(agResp);
        when(fachada.buscarAgendamento(1L)).thenReturn(agResp);
        when(fachada.listarAgendamentoPorEstudanteAtual(any())).thenReturn(new PageImpl<>(List.of(agResp)));
        when(fachada.listarAgendamentosPorEstudante(eq(userId), any())).thenReturn(new PageImpl<>(List.of(agResp)));
        when(fachada.listarAgendamentoPorProfissionalAtual(any())).thenReturn(new PageImpl<>(List.of(agResp)));
        when(fachada.listarAgendamentosPorProfissional(eq(userId), any())).thenReturn(new PageImpl<>(List.of(agResp)));
        when(fachada.cancelarAgendamento(eq(1L), any())).thenReturn(cancelamento);
        when(fachada.buscarCancelamento(1L)).thenReturn(cancelamento);
        when(fachada.listarCancelamentosPorEstudanteAtual(any())).thenReturn(new PageImpl<>(List.of(cancelamento)));
        when(fachada.listarCancelamentosPorProfissionalAtual(any())).thenReturn(new PageImpl<>(List.of(cancelamento)));
        when(fachada.alterarModalidadeAgendamento(eq(1L), any())).thenReturn(agendamento);

        mockMvc.perform(post("/agendamento").contentType(MediaType.APPLICATION_JSON).content("{\"vagaId\":1,\"modalidade\":\"PRESENCIAL\"}")).andExpect(status().isCreated());
        mockMvc.perform(get("/agendamento/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/agendamento/estudante")).andExpect(status().isOk());
        mockMvc.perform(get("/agendamento/estudante/{estudanteId}", userId)).andExpect(status().isOk());
        mockMvc.perform(get("/agendamento/profissional")).andExpect(status().isOk());
        mockMvc.perform(get("/agendamento/profissional/{profissionalId}", userId)).andExpect(status().isOk());
        mockMvc.perform(post("/agendamento/{id}/cancelar", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Imprevisto\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/agendamento/cancelamento/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/agendamento/cancelamento/estudante")).andExpect(status().isOk());
        mockMvc.perform(get("/agendamento/cancelamento/profissional")).andExpect(status().isOk());
        mockMvc.perform(patch("/agendamento/{id}/modalidade", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"modalidade\":\"REMOTO\"}")).andExpect(status().isOk());

        // --- CronogramaController (7 endpoints) ---
        when(fachada.listarCronogramas(any(), any())).thenReturn(new PageImpl<>(List.of(cronograma)));
        when(fachada.buscarCronograma(1L)).thenReturn(cronograma);
        when(fachada.listarCronogramasPorTipoAtendimento(eq(1L), any())).thenReturn(new PageImpl<>(List.of(cronograma)));
        when(fachada.salvarCronograma(any(), eq(1L))).thenReturn(cronograma);
        when(fachada.listarCronogramasPorProfissional(any())).thenReturn(new PageImpl<>(List.of(cronograma)));
        when(fachada.editarCronograma(eq(1L), any(), eq(1L))).thenReturn(cronograma);

        mockMvc.perform(get("/cronograma")).andExpect(status().isOk());
        mockMvc.perform(get("/cronograma/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/cronograma/tipo-atendimento/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(post("/cronograma").contentType(MediaType.APPLICATION_JSON).content("{\"datas\":[\"15/06/2026\"],\"tipoAtendimentoId\":1}")).andExpect(status().isCreated());
        mockMvc.perform(get("/cronograma/profissional")).andExpect(status().isOk());
        mockMvc.perform(patch("/cronograma/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"16/06/2026\",\"tipoAtendimentoId\":1}")).andExpect(status().isOk());
        mockMvc.perform(delete("/cronograma/{id}", 1L)).andExpect(status().isNoContent());

        // --- VagaController (4 endpoints) ---
        when(vagaService.listar()).thenReturn(List.of(vaga));
        when(vagaService.buscar(1L)).thenReturn(vaga);
        when(vagaService.editar(eq(1L), any())).thenReturn(vaga);

        mockMvc.perform(get("/vaga")).andExpect(status().isOk());
        mockMvc.perform(get("/vaga/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(patch("/vaga/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"horaInicio\":\"09:00\",\"horaFim\":\"09:30\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/vaga/{id}", 1L)).andExpect(status().isNoContent());

        // --- TipoAtendimentoController (6 endpoints) ---
        when(fachada.listarTipoAtendimentos(any(), any())).thenReturn(new PageImpl<>(List.of(ta)));
        when(fachada.buscarTipoAtendimento(1L)).thenReturn(ta);
        when(fachada.salvarTipoAtendimento(any())).thenReturn(ta);
        when(fachada.editarTipoAtendimento(eq(1L), any())).thenReturn(ta);
        when(fachada.deletarHorarioTipoAtendimento(1L, 0)).thenReturn(ta);

        String taJson = "{\"nome\":\"Psicologia\",\"tempoAtendimento\":\"00:30\",\"horarios\":[\"08:00\"]}";
        mockMvc.perform(get("/tipo-atendimento")).andExpect(status().isOk());
        mockMvc.perform(get("/tipo-atendimento/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(post("/tipo-atendimento").contentType(MediaType.APPLICATION_JSON).content(taJson)).andExpect(status().isCreated());
        mockMvc.perform(patch("/tipo-atendimento/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content(taJson)).andExpect(status().isOk());
        mockMvc.perform(delete("/tipo-atendimento/{id}/horarios/{index}", 1L, 0)).andExpect(status().isOk());
        mockMvc.perform(delete("/tipo-atendimento/{id}", 1L)).andExpect(status().isNoContent());

        // --- NotificacaoController (5 endpoints / 8 rotas) ---
        UUID notifId = UUID.randomUUID();
        when(authenticatedUserProvider.getUserId()).thenReturn(userId);
        when(fachada.subscreverNotificacoes(userId)).thenReturn(new SseEmitter(0L));
        when(fachada.buscarNotificacoesNaoLidas(eq(userId), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/notificacoes/stream")).andExpect(status().isOk()).andExpect(header().string("X-Accel-Buffering", "no"));
        mockMvc.perform(get("/notificacoes")).andExpect(status().isOk());
        mockMvc.perform(get("/notificacoes/historico")).andExpect(status().isOk());
        mockMvc.perform(patch("/notificacoes/{id}/lida", notifId)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/notificacoes/{id}/lida", notifId)).andExpect(status().isNoContent());
        mockMvc.perform(patch("/notificacoes/limpar-todas")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/notificacoes/limpar-todas")).andExpect(status().isNoContent());
        mockMvc.perform(post("/notificacoes/broadcast").contentType(MediaType.APPLICATION_JSON).content("{\"perfilDestino\":\"ESTUDANTE\",\"titulo\":\"Edital\",\"mensagem\":\"Aberto\",\"tipo\":\"EDITAL\"}")).andExpect(status().isAccepted());

        // --- CursoController (5 endpoints) ---
        Curso curso = new Curso(1L, "Ciência da Computação", 8, true);
        when(fachada.salvarCurso(any())).thenReturn(curso);
        when(fachada.editarCurso(eq(1L), any())).thenReturn(curso);
        when(fachada.buscarCurso(1L)).thenReturn(curso);
        when(fachada.listarCursos(any(), any())).thenReturn(new PageImpl<>(List.of(curso)));

        mockMvc.perform(post("/curso").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Ciência da Computação\",\"numeroPeriodos\":8}")).andExpect(status().isCreated());
        mockMvc.perform(patch("/curso/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"BCC\",\"numeroPeriodos\":8}")).andExpect(status().isOk());
        mockMvc.perform(get("/curso/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/curso")).andExpect(status().isOk());
        mockMvc.perform(delete("/curso/{id}", 1L)).andExpect(status().isNoContent());

        // --- TipoEtniaController (5 endpoints) ---
        TipoEtnia etnia = new TipoEtnia(1L, "Parda");
        when(fachada.buscarTipoEtnia(1L)).thenReturn(etnia);
        when(fachada.listarTiposEtnia(any(), any())).thenReturn(new PageImpl<>(List.of(etnia)));
        when(fachada.salvarTipoEtnia(any())).thenReturn(etnia);
        when(fachada.atualizarTipoEtnia(eq(1L), any())).thenReturn(etnia);

        mockMvc.perform(get("/tipoEtnia/{id}", 1L)).andExpect(status().isOk());
        mockMvc.perform(get("/tipoEtnia")).andExpect(status().isOk());
        mockMvc.perform(post("/tipoEtnia").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"Parda\"}")).andExpect(status().isCreated());
        mockMvc.perform(patch("/tipoEtnia/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"Preta\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/tipoEtnia/{id}", 1L)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ESTUDANTE")
    @DisplayName("GlobalExceptionHandler: Deve mapear corretamente exceções para 400, 401, 403, 404 e 409")
    void deveMapearExcecoesNoGlobalExceptionHandler() throws Exception {
        doThrow(new KeycloakAuthenticationException("Credenciais inválidas."))
                .when(fachada).login("errado@ufape.edu.br", "123");

        mockMvc.perform(post("/login")
                        .param("email", "errado@ufape.edu.br")
                        .param("senha", "123"))
                .andExpect(status().isUnauthorized());

        UUID outroId = UUID.randomUUID();
        when(fachada.buscarEstudante(outroId)).thenThrow(new GlobalAccessDeniedException("Você não tem permissão"));

        mockMvc.perform(get("/estudantes/{id}", outroId))
                .andExpect(status().isForbidden());

        UUID inexistenteId = UUID.randomUUID();
        when(fachada.buscarEstudante(inexistenteId)).thenThrow(new EstudanteNotFoundException());

        mockMvc.perform(get("/estudantes/{id}", inexistenteId))
                .andExpect(status().isNotFound());

        when(fachada.agendarVaga(any())).thenThrow(new UnavailableVagaException());

        mockMvc.perform(post("/agendamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vagaId\":1,\"modalidade\":\"PRESENCIAL\"}"))
                .andExpect(status().isConflict());
    }
}
