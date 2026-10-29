package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.pagamento.*;
import br.edu.ufape.sguPraeService.exceptions.BeneficioNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.PagamentoNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Pagamento;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
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
public class PagamentoController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @GetMapping
    public Page<PagamentoResponse> listar(
            @QuerydslPredicate(root = Pagamento.class) Predicate predicate,
            @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarPagamentos(predicate, pageable)
                .map(fachada::mapToPagamentoResponse);
    }

    @GetMapping("/beneficio/{beneficioId}")
    public List<PagamentoResponse> listarPorBeneficioId(@PathVariable Long beneficioId) throws BeneficioNotFoundException {
        return fachada.listarPagamentosPorBeneficioId(beneficioId)
                .stream()
                .map(fachada::mapToPagamentoResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagamentoResponse> buscar(@PathVariable Long id) throws PagamentoNotFoundException {
        Pagamento pagamento = fachada.buscarPagamento(id);
        return ResponseEntity.ok(fachada.mapToPagamentoResponse(pagamento));
    }

    @PreAuthorize("hasRole('GESTOR')")
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

    @PreAuthorize("hasRole('GESTOR')")
    @PostMapping("/cpf")
    public ResponseEntity<PagamentoResponse> criarPagamentoPorCpf(@Valid @RequestBody PagamentoCPFRequest request) {
        Pagamento pagamento = fachada.salvarPagamentoPorCpf(request);
        return new ResponseEntity<>(new PagamentoResponse(pagamento, modelMapper), HttpStatus.CREATED);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<PagamentoResponse> editar(@PathVariable Long id, @RequestBody PagamentoPatchRequest dto)
            throws PagamentoNotFoundException {
        Pagamento atualizado = fachada.editarPagamento(id, dto);
        return ResponseEntity.ok(fachada.mapToPagamentoResponse(atualizado));
    }

    @PreAuthorize("hasRole('GESTOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws PagamentoNotFoundException {
        fachada.deletarPagamento(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('GESTOR')")
    @PatchMapping("desativar/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) throws PagamentoNotFoundException {
        fachada.desativarPagamento(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/valor/{min}/{max}")
    public Page<PagamentoResponse> listarPorValor(@PageableDefault(sort = "id") Pageable pageable, @PathVariable BigDecimal min, @PathVariable BigDecimal max) {
        return fachada.listarPagamentosPorValor(min, max, pageable)
                .map(fachada::mapToPagamentoResponse);
    }


    @PreAuthorize("hasAnyRole('GESTOR', 'ESTUDANTE')")
    @GetMapping("/estudante/{estudanteId}")
    public ResponseEntity<Page<PagamentoResponse>> listarPagamentosPorEstudante(
            @PathVariable UUID estudanteId,
            @QuerydslPredicate(root = Pagamento.class) Predicate predicate,
            Pageable pageable) {

        Page<PagamentoResponse> pagamentosPaginados = fachada.listarPagamentosPorEstudante(estudanteId, predicate, pageable);
        return ResponseEntity.ok(pagamentosPaginados);
    }

    @GetMapping("/folha")
    @Operation(summary = "Consultar Folha de Pagamento", description = "Gera a folha consolidada por aluno, filtrando por ano e mês (obrigatórios) e lote (opcional).")
    public ResponseEntity<FolhaPagamentoResponse> consultarFolha(
            @RequestParam Integer ano,
            @RequestParam Integer mes,
            @RequestParam(required = false) String numeroLote) {

        return ResponseEntity.ok(fachada.gerarFolhaPagamento(ano, mes, numeroLote));
    }

    @Operation(summary = "Retorna o valor total em reais de pagamentos já realizados de todos os benefícios")
    @GetMapping("/total")
    public ResponseEntity<BigDecimal> getValorTotalPagamentos(
            @QuerydslPredicate(root = Pagamento.class) Predicate predicate) {
        BigDecimal total = fachada.obterValorTotalPagamentosAtivos(predicate);
        return ResponseEntity.ok(total);
    }

    @GetMapping("/total/tipo/beneficio")
    public ResponseEntity<List<Map<String, Object>>> getValorTotalPorTipoBeneficio(
            @QuerydslPredicate(root = Pagamento.class) Predicate predicate) {
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
