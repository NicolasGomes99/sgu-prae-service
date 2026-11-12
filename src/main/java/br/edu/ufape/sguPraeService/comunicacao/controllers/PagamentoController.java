package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.*;
import br.edu.ufape.sguPraeService.exceptions.BeneficioNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.PagamentoNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Pagamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.querydsl.core.types.Predicate;
import org.springframework.data.querydsl.binding.QuerydslPredicate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pagamento")
@Tag(name = "07. Pagamentos e Folha Mensal")
public class PagamentoController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Listar pagamentos ativos (Paginado)",
            description = """
                    Lista os pagamentos registrados no sistema com suporte a paginação e filtros seguros via whitelist QueryDSL (`id`, `numeroLote`, `mesReferencia`, `anoReferencia`, `data`, `valor`, `beneficio.id`, `beneficio.tipoBeneficio.id`, `beneficio.estudantes.id`, `beneficio.estudantes.curso.id`).
                    - **Perfis permitidos**: `GESTOR` e `ADMINISTRADOR`.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping
    public Page<PagamentoResponse> listar(
            @Parameter(hidden = true) @QuerydslPredicate(root = Pagamento.class) Predicate predicate,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarPagamentos(predicate, pageable)
                .map(fachada::mapToPagamentoResponse);
    }

    @Operation(
            summary = "Listar histórico de pagamentos de um benefício",
            description = """
                    Retorna todos os pagamentos vinculados a um benefício específico.
                    - **Proteção Anti-IDOR**: Estudantes só podem consultar pagamentos de seus próprios benefícios; `GESTOR` e `ADMINISTRADOR` possuem acesso integral.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @GetMapping("/beneficio/{beneficioId}")
    public List<PagamentoResponse> listarPorBeneficioId(
            @Parameter(description = "ID do benefício", required = true) @PathVariable Long beneficioId) throws BeneficioNotFoundException {
        return fachada.listarPagamentosPorBeneficioId(beneficioId)
                .stream()
                .map(fachada::mapToPagamentoResponse)
                .toList();
    }

    @Operation(
            summary = "Buscar pagamento por ID",
            description = """
                    Consulta os detalhes de um lançamento de pagamento pelo seu ID.
                    - **Proteção Anti-IDOR**: Valida propriedade caso o requisitante seja `ESTUDANTE`.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @GetMapping("/{id}")
    public ResponseEntity<PagamentoResponse> buscar(
            @Parameter(description = "ID do pagamento", required = true) @PathVariable Long id) throws PagamentoNotFoundException {
        Pagamento pagamento = fachada.buscarPagamento(id);
        return ResponseEntity.ok(fachada.mapToPagamentoResponse(pagamento));
    }

    @Operation(
            summary = "Registrar lote de pagamentos",
            description = """
                    Lança um lote de pagamentos para um ou mais benefícios ativos.
                    - Atualiza o status do benefício e dispara **notificações individuais automáticas** (SSE + Redis + E-mail) para cada estudante contemplado no lote.
                    - **Perfis permitidos**: `GESTOR` e `ADMINISTRADOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lote de pagamentos registrado e notificações disparadas."),
            @ApiResponse(responseCode = "400", description = "Lista vazia ou dados de pagamento fora da vigência do benefício."),
            @ApiResponse(responseCode = "404", description = "Benefício informado não encontrado.")
    })
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<List<PagamentoResponse>> salvar(@Valid @RequestBody List<PagamentoRequest> entities)
            throws BeneficioNotFoundException {
        if (entities.isEmpty()) return ResponseEntity.badRequest().build();

        List<Pagamento> pagamentos = entities.stream()
                .map(e -> e.convertToEntity(e, modelMapper))
                .toList();

        List<Pagamento> salvos = fachada.salvarPagamentos(pagamentos);

        List<PagamentoResponse> response = salvos.stream()
                .map(fachada::mapToPagamentoResponse)
                .toList();

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Registrar pagamento individual via CPF do estudante",
            description = "Localiza o benefício ativo do estudante a partir do `cpf` informado e registra o pagamento correspondente. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PostMapping("/cpf")
    public ResponseEntity<PagamentoResponse> criarPagamentoPorCpf(@Valid @RequestBody PagamentoCPFRequest request) {
        Pagamento pagamento = fachada.salvarPagamentoPorCpf(request);
        return new ResponseEntity<>(new PagamentoResponse(pagamento, modelMapper), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Atualizar parcialmente um pagamento",
            description = "Permite editar valor, data, mês/ano de referência ou número de lote de um pagamento existente. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    public ResponseEntity<PagamentoResponse> editar(
            @Parameter(description = "ID do pagamento", required = true) @PathVariable Long id,
            @RequestBody PagamentoPatchRequest dto)
            throws PagamentoNotFoundException {
        Pagamento atualizado = fachada.editarPagamento(id, dto);
        return ResponseEntity.ok(fachada.mapToPagamentoResponse(atualizado));
    }

    @Operation(
            summary = "Remover pagamento permanentemente",
            description = "Remove o registro de pagamento pelo ID. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do pagamento", required = true) @PathVariable Long id) throws PagamentoNotFoundException {
        fachada.deletarPagamento(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Estornar / desativar pagamento",
            description = """
                    Desativa logicamente um pagamento (`ativo = false`).
                    - **Regra de Negócio**: Caso este seja o único pagamento ativo do benefício, o status de pagamento do benefício é revertido automaticamente para pendente (`status = false`).
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("desativar/{id}")
    public ResponseEntity<Void> desativar(
            @Parameter(description = "ID do pagamento", required = true) @PathVariable Long id) throws PagamentoNotFoundException {
        fachada.desativarPagamento(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar pagamentos por faixa de valor",
            description = "Filtra pagamentos cujo valor esteja dentro do intervalo `[min, max]`. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/valor/{min}/{max}")
    public Page<PagamentoResponse> listarPorValor(
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable,
            @Parameter(description = "Valor mínimo", required = true) @PathVariable BigDecimal min,
            @Parameter(description = "Valor máximo", required = true) @PathVariable BigDecimal max) {
        return fachada.listarPagamentosPorValor(min, max, pageable)
                .map(fachada::mapToPagamentoResponse);
    }

    @Operation(
            summary = "Listar pagamentos recebidos por um estudante",
            description = """
                    Retorna de forma paginada todos os pagamentos ativos destinados a um estudante específico.
                    - **Proteção Anti-IDOR**: Estudantes só podem consultar seus próprios pagamentos; `GESTOR` e `ADMINISTRADOR` podem consultar de qualquer estudante.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @GetMapping("/estudante/{estudanteId}")
    public ResponseEntity<Page<PagamentoResponse>> listarPagamentosPorEstudante(
            @Parameter(description = "UUID do estudante", required = true) @PathVariable UUID estudanteId,
            @Parameter(hidden = true) @QuerydslPredicate(root = Pagamento.class) Predicate predicate,
            @ParameterObject Pageable pageable) {

        Page<PagamentoResponse> pagamentosPaginados = fachada.listarPagamentosPorEstudante(estudanteId, predicate, pageable);
        return ResponseEntity.ok(pagamentosPaginados);
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/folha")
    @Operation(
            summary = "Consultar Folha de Pagamento Consolidada",
            description = """
                    Gera a folha de pagamento consolidada agrupada por estudante, filtrando obrigatoriamente por `ano` (`2000..2100`) e `mes` (`1..12`), e opcionalmente por `numeroLote`.
                    - **Perfis permitidos**: `GESTOR` e `ADMINISTRADOR`.
                    """
    )
    public ResponseEntity<FolhaPagamentoResponse> consultarFolha(
            @Parameter(description = "Ano de referência (ex: 2026)", required = true) @RequestParam Integer ano,
            @Parameter(description = "Mês de referência (1 a 12)", required = true) @RequestParam Integer mes,
            @Parameter(description = "Filtro opcional pelo número do lote") @RequestParam(required = false) String numeroLote) {

        return ResponseEntity.ok(fachada.gerarFolhaPagamento(ano, mes, numeroLote));
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Operation(
            summary = "Obter valor total em reais de pagamentos ativos",
            description = "Retorna o somatório financeiro (`BigDecimal`) de todos os pagamentos ativos que atendem aos filtros QueryDSL informados. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @GetMapping("/total")
    public ResponseEntity<BigDecimal> getValorTotalPagamentos(
            @Parameter(hidden = true) @QuerydslPredicate(root = Pagamento.class) Predicate predicate) {
        BigDecimal total = fachada.obterValorTotalPagamentosAtivos(predicate);
        return ResponseEntity.ok(total);
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Operation(
            summary = "Obter valor total pago agrupado por Tipo de Benefício",
            description = "Retorna uma lista de objetos contendo `tipoBeneficioId`, `tipoBeneficioDescricao` e `valorTotal` acumulado para cada modalidade de auxílio/bolsa. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @GetMapping("/total/tipo/beneficio")
    public ResponseEntity<List<Map<String, Object>>> getValorTotalPorTipoBeneficio(
            @Parameter(hidden = true) @QuerydslPredicate(root = Pagamento.class) Predicate predicate) {
        List<Object[]> resultado = fachada.obterValorTotalPorTipoBeneficio(predicate);
        List<Map<String, Object>> resposta = new java.util.ArrayList<>();
        for (Object[] row : resultado) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("tipoBeneficioId", row[0]);
            map.put("tipoBeneficioDescricao", row[1]);
            map.put("valorTotal", row[2]);
            resposta.add(map);
        }
        return ResponseEntity.ok(resposta);
    }
}
