package br.edu.ufape.sguPraeService.comunicacao.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.*;
import br.edu.ufape.sguPraeService.models.Pagamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.exceptions.BeneficioNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.TipoBeneficioNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.EstudanteNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Beneficio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.querydsl.core.types.Predicate;
import org.springframework.data.querydsl.binding.QuerydslPredicate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/beneficio")
@Tag(name = "06. Benefícios (Bolsas e Auxílios)")
public class BeneficioController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Listar benefícios ativos (Paginado)",
            description = """
                    Lista todos os benefícios ativos com suporte a paginação e filtros QueryDSL (`tipoBeneficio.id`, `estudantes.id`, `estudantes.curso.id`, `status`, `inicioBeneficio`, `fimBeneficio`).
                    - **Perfis permitidos**: `GESTOR` e `ADMINISTRADOR`.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping
    public Page<BeneficioResponse> listar(
            @Parameter(hidden = true) @QuerydslPredicate(root = Beneficio.class) Predicate predicate,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarBeneficios(predicate, pageable)
                .map(fachada::mapToBeneficioResponse);
    }

    @Operation(
            summary = "Listar benefícios inativos/encerrados com filtros",
            description = "Consulta o histórico de benefícios inativos/cancelados filtrando por `nome` do estudante, `cpf`, `curso.id` ou motivo de encerramento. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/inativos")
    public ResponseEntity<Page<BeneficioResponse>> listarInativos(
            @Parameter(hidden = true) @QuerydslPredicate(root = Beneficio.class) Predicate predicate,
            @Parameter(description = "Filtro opcional por nome do estudante") @RequestParam(required = false) String nome,
            @Parameter(description = "Filtro opcional por CPF do estudante") @RequestParam(required = false) String cpf,
            @Parameter(description = "Filtro opcional pelo ID do curso") @RequestParam(required = false, name = "curso.id") Long cursoId,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {

        Page<BeneficioResponse> page = fachada.listarBeneficiosInativosComFiltrosExternos(predicate, nome, cpf, cursoId, pageable);
        return ResponseEntity.ok(page);
    }

    @Operation(
            summary = "Listar benefícios ativos de um estudante específico",
            description = """
                    Retorna a página de benefícios ativos vinculados a um estudante.
                    - **Proteção Anti-IDOR**: Um `ESTUDANTE` só pode consultar seus próprios benefícios; `GESTOR` e `ADMINISTRADOR` podem consultar de qualquer estudante.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @GetMapping("/estudante/{estudanteId}")
    public Page<BeneficioResponse> listarPorEstudanteId(
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable,
            @Parameter(description = "UUID do estudante", required = true) @PathVariable UUID estudanteId)
            throws EstudanteNotFoundException {
        return fachada.listarBeneficiosPorEstudanteId(estudanteId, pageable)
                .map(fachada::mapToBeneficioResponse);
    }

    @Operation(
            summary = "Buscar benefício por ID",
            description = """
                    Retorna os detalhes de um benefício específico.
                    - **Proteção Anti-IDOR**: Valida automaticamente se o benefício pertence ao estudante autenticado ou se o requisitante possui role `GESTOR`/`ADMINISTRADOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Benefício encontrado."),
            @ApiResponse(responseCode = "403", description = "Acesso negado (benefício pertence a outro estudante)."),
            @ApiResponse(responseCode = "404", description = "Benefício não encontrado.")
    })
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @GetMapping("/{id}")
    public ResponseEntity<BeneficioResponse> buscar(
            @Parameter(description = "ID do benefício", required = true) @PathVariable Long id) throws BeneficioNotFoundException {
        Beneficio beneficio = fachada.buscarBeneficioComValidacaoAcesso(id);
        return ResponseEntity.ok(fachada.mapToBeneficioResponse(beneficio));
    }

    @Operation(
            summary = "Baixar Termo de Compromisso do benefício em Base64",
            description = """
                    Retorna o documento do Termo de Compromisso vinculado ao benefício convertido em `Base64` (`DocumentoResponse`).
                    - **Proteção Anti-IDOR**: Restrito ao estudante titular do benefício ou à gestão (`GESTOR` / `ADMINISTRADOR`).
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @GetMapping("/{id}/termo")
    public ResponseEntity<DocumentoResponse> buscarTermo(
            @Parameter(description = "ID do benefício", required = true) @PathVariable Long id)
            throws BeneficioNotFoundException, IOException {
        Beneficio beneficio = fachada.buscarBeneficioComValidacaoAcesso(id);
        DocumentoResponse termo = fachada.converterDocumentosParaBase64(List.of(beneficio.getTermo())).getFirst();
        return ResponseEntity.ok(termo);
    }

    @Operation(
            summary = "Conceder novo benefício a um estudante (Multipart)",
            description = """
                    Cadastra uma nova concessão de bolsa/auxílio para um estudante com upload do Termo de Compromisso (`PDF`/`PNG`/`JPG`).
                    - **Regras de Negócio**:
                      1. Cada estudante pode possuir no máximo **2 benefícios ativos** simultaneamente.
                      2. Não é permitido conceder dois benefícios ativos do **mesmo tipo** ao mesmo estudante.
                      3. `fimBeneficio` deve ser posterior a `inicioBeneficio`.
                    - **Rollback Físico**: Caso a validação de negócio falhe, o arquivo enviado é removido imediatamente do disco.
                    - **Notificação**: Dispara evento automático de concessão de benefício para o estudante (SSE + Redis + E-mail).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Benefício concedido com sucesso."),
            @ApiResponse(responseCode = "400", description = "Limite de 2 benefícios excedido, tipo duplicado, datas inválidas ou arquivo inválido."),
            @ApiResponse(responseCode = "404", description = "Estudante ou Tipo de Benefício não encontrado.")
    })
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<BeneficioResponse> salvar(@Valid @ModelAttribute BeneficioRequest entity)
            throws TipoBeneficioNotFoundException {
        Beneficio beneficio = entity.convertToEntity(entity, modelMapper);
        beneficio = fachada.salvarBeneficios(entity.getEstudanteId(), beneficio, entity.getTermo(), entity.getTipoBeneficioId());
        return new ResponseEntity<>(fachada.mapToBeneficioResponse(beneficio), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Editar benefício existente (Multipart)",
            description = "Atualiza os dados ou substitui o Termo de Compromisso de um benefício ativo. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<BeneficioResponse> editar(
            @Parameter(description = "ID do benefício", required = true) @PathVariable Long id,
            @Valid @ModelAttribute BeneficioRequest entity)
            throws BeneficioNotFoundException, TipoBeneficioNotFoundException {
        Beneficio beneficio = entity.convertToEntity(entity, modelMapper);
        beneficio = fachada.editarBeneficios(id, entity.getEstudanteId(), beneficio, entity.getTermo(), entity.getTipoBeneficioId());
        return ResponseEntity.ok(fachada.mapToBeneficioResponse(beneficio));
    }

    @Operation(
            summary = "Cancelar / encerrar vigência de um benefício",
            description = "Encerra um benefício informando o `motivoEncerramento` e o `parecerTermino`, inativando o benefício e disparando notificação ao estudante. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarBeneficio(
            @Parameter(description = "ID do benefício", required = true) @PathVariable Long id,
            @Valid @RequestBody BeneficioCancelamentoRequest request)
            throws BeneficioNotFoundException {

        fachada.cancelarBeneficio(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Deletar (desativar) benefício por ID",
            description = "Realiza a exclusão lógica de um benefício. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do benefício", required = true) @PathVariable Long id) throws BeneficioNotFoundException {
        fachada.deletarBeneficio(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar benefícios que já possuem pagamentos registrados",
            description = "Retorna de forma paginada os benefícios ativos que possuem pagamentos vinculados. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/pagos")
    public ResponseEntity<Page<BeneficioResponse>> listarPagosPorMes(
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable
    ) throws BeneficioNotFoundException {
        Page<Beneficio> pageBeneficios = fachada.listarPagosPorMes(pageable);

        return ResponseEntity.ok(
                pageBeneficios.map(fachada::mapToBeneficioResponse)
        );
    }

    @Operation(
            summary = "Listar benefícios por Tipo de Benefício",
            description = "Lista todos os benefícios vinculados a uma modalidade específica (`tipoBeneficioId`). Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/tipo/{id}")
    public ResponseEntity<Page<BeneficioResponse>> listarPorTipo(
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable,
            @Parameter(description = "ID do tipo de benefício", required = true) @PathVariable Long id)
            throws BeneficioNotFoundException {
        return ResponseEntity.ok(
                fachada.listarBeneficiosPorTipo(id, pageable)
                        .map(fachada::mapToBeneficioResponse)
        );
    }

    @Operation(
            summary = "Listar auxílios/bolsas pendentes de pagamento no mês atual",
            description = "Retorna todos os benefícios ativos dentro da vigência que **ainda não receberam pagamento** no mês/ano corrente. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/pendentes")
    public ResponseEntity<Page<BeneficioResponse>> listarAuxiliosPendentesMesAtual(
            @Parameter(hidden = true) @QuerydslPredicate(root = Beneficio.class) Predicate predicate,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable
    ) {
        Page<BeneficioResponse> beneficios = fachada.listarBeneficiosPendentesMesAtual(predicate, pageable);
        return ResponseEntity.ok(beneficios);
    }

    @Operation(
            summary = "Gerar Relatório Financeiro Consolidado (Portal da Transparência)",
            description = """
                    Rota pública de transparência ativa que retorna os indicadores consolidados de execução orçamentária da assistência estudantil (sem expor dados pessoais):
                    - Valor total geral pago, quantidade de estudantes atendidos, quantidade de modalidades e cursos distintos, valores agrupados por tipo de benefício e quantidade de beneficiados por curso.
                    - Suporta filtro opcional por `curso.id` e filtros de período/referência via QueryDSL (`anoReferencia`, `mesReferencia`, `numeroLote`).
                    """
    )
    @GetMapping("/relatorio/financeiro")
    public ResponseEntity<RelatorioFinanceiroResponse> gerarRelatorioFinanceiro(
            @Parameter(hidden = true) @QuerydslPredicate(root = Pagamento.class) Predicate predicate,
            @Parameter(description = "Filtro opcional pelo ID do curso") @RequestParam(required = false, name = "curso.id") Long cursoId) {

        RelatorioFinanceiroResponse relatorio = fachada.gerarRelatorioFinanceiro(predicate, cursoId);
        return ResponseEntity.ok(relatorio);
    }

    @Operation(
            summary = "Contar total de estudantes distintos com benefício ativo (Público)",
            description = "Indicador público que retorna a quantidade total de estudantes atualmente assistidos pela PRAE."
    )
    @GetMapping("/quantidade/beneficiados")
    public ResponseEntity<Long> getQuantidadeEstudantesBeneficiados() {
        Long quantidade = fachada.contarEstudantesBeneficiados();
        return ResponseEntity.ok(quantidade);
    }

    @Operation(
            summary = "Contar total de cursos distintos com estudantes beneficiados (Público)",
            description = "Indicador público que retorna quantos cursos de graduação possuem estudantes com benefícios ativos."
    )
    @GetMapping("/quantidade/cursos")
    public ResponseEntity<Long> getQuantidadeCursosDistintosComBeneficio() {
        Long quantidade = fachada.contarCursosDistintosComBeneficioAtivo();
        return ResponseEntity.ok(quantidade);
    }

    @Operation(
            summary = "Obter distribuição de estudantes beneficiados por curso (Público)",
            description = "Retorna a lista de cursos com o respectivo quantitativo de estudantes assistidos, permitindo filtro por período de pagamento."
    )
    @GetMapping("/quantidade/beneficiados/por/curso")
    public ResponseEntity<List<Map<String, Object>>> getQuantidadeBeneficiadosPorCurso(
            @Parameter(hidden = true) @QuerydslPredicate(root = Pagamento.class) Predicate predicate) {
        List<Map<String, Object>> resposta = fachada.obterQuantidadeBeneficiadosPorCursoComFiltro(predicate);
        return ResponseEntity.ok(resposta);
    }

    @Operation(
            summary = "Prorrogar vigência de um benefício ativo",
            description = "Estende a data `fimBeneficio` de um auxílio/bolsa ativo (exigindo que a nova data seja posterior à atual) e registra a `observacaoProrrogacao`. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("/{id}/prorrogar")
    public ResponseEntity<BeneficioResponse> prorrogarBeneficio(
            @Parameter(description = "ID do benefício", required = true) @PathVariable Long id,
            @Valid @RequestBody BeneficioProrrogacaoRequest request) throws BeneficioNotFoundException {

        BeneficioResponse beneficioProrrogado = fachada.prorrogarBeneficio(id, request);
        return ResponseEntity.ok(beneficioProrrogado);
    }
}
