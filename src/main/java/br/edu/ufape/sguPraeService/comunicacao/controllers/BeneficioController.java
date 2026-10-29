package br.edu.ufape.sguPraeService.comunicacao.controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.*;
import br.edu.ufape.sguPraeService.models.Pagamento;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
public class BeneficioController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @GetMapping
    public Page<BeneficioResponse> listar(
            @QuerydslPredicate(root = Beneficio.class) Predicate predicate,
            @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarBeneficios(predicate, pageable)
                .map(fachada::mapToBeneficioResponse);
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/inativos")
    public ResponseEntity<Page<BeneficioResponse>> listarInativos(
            @QuerydslPredicate(root = Beneficio.class) Predicate predicate,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false, name = "curso.id") Long cursoId,
            @PageableDefault(sort = "id") Pageable pageable) {

        Page<BeneficioResponse> page = fachada.listarBeneficiosInativosComFiltrosExternos(predicate, nome, cpf, cursoId, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/estudante/{estudanteId}")
    public Page<BeneficioResponse> listarPorEstudanteId(@PageableDefault(sort = "id") Pageable pageable, @PathVariable Long estudanteId)
            throws EstudanteNotFoundException {
        return fachada.listarBeneficiosPorEstudanteId(estudanteId, pageable)
                .map(fachada::mapToBeneficioResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BeneficioResponse> buscar(@PathVariable Long id) throws BeneficioNotFoundException {
        Beneficio beneficio = fachada.buscarBeneficios(id);
        return ResponseEntity.ok(fachada.mapToBeneficioResponse(beneficio));
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/{id}/termo")
    public ResponseEntity<DocumentoResponse> buscarTermo(@PathVariable Long id)
            throws BeneficioNotFoundException, IOException {
        Beneficio beneficio = fachada.buscarBeneficios(id);
        DocumentoResponse termo = fachada.converterDocumentosParaBase64(List.of(beneficio.getTermo())).getFirst();
        return ResponseEntity.ok(termo);
    }

    @PreAuthorize("hasRole('GESTOR')")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<BeneficioResponse> salvar(@Valid @ModelAttribute BeneficioRequest entity)
            throws TipoBeneficioNotFoundException {
        Beneficio beneficio = entity.convertToEntity(entity, modelMapper);
        beneficio = fachada.salvarBeneficios(entity.getEstudanteId(), beneficio, entity.getTermo(), entity.getTipoBeneficioId());
        return new ResponseEntity<>(fachada.mapToBeneficioResponse(beneficio), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('GESTOR')")
    @PatchMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<BeneficioResponse> editar(@PathVariable Long id, @Valid @ModelAttribute BeneficioRequest entity)
            throws BeneficioNotFoundException, TipoBeneficioNotFoundException {
        Beneficio beneficio = entity.convertToEntity(entity, modelMapper);
        beneficio = fachada.editarBeneficios(id, entity.getEstudanteId(), beneficio, entity.getTermo(), entity.getTipoBeneficioId());
        return ResponseEntity.ok(fachada.mapToBeneficioResponse(beneficio));
    }

    @PreAuthorize("hasRole('GESTOR')")
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarBeneficio(
            @PathVariable Long id,
            @Valid @RequestBody BeneficioCancelamentoRequest request)
            throws BeneficioNotFoundException {

        fachada.cancelarBeneficio(id, request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('GESTOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws BeneficioNotFoundException {
        fachada.deletarBeneficio(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/pagos")
    public ResponseEntity<Page<BeneficioResponse>> listarPagosPorMes(
            @PageableDefault(sort = "id") Pageable pageable // Adicionado Pageable
    ) throws BeneficioNotFoundException {
        Page<Beneficio> pageBeneficios = fachada.listarPagosPorMes(pageable);

        return ResponseEntity.ok(
                pageBeneficios.map(fachada::mapToBeneficioResponse)
        );
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/tipo/{id}")
    public ResponseEntity<Page<BeneficioResponse>> listarPorTipo(@PageableDefault(sort = "id") Pageable pageable, @PathVariable Long id)
            throws BeneficioNotFoundException {
        return ResponseEntity.ok(
                fachada.listarBeneficiosPorTipo(id, pageable)
                        .map(fachada::mapToBeneficioResponse)
        );
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/pendentes")
    public ResponseEntity<Page<BeneficioResponse>> listarAuxiliosPendentesMesAtual(
            @QuerydslPredicate(root = Beneficio.class) Predicate predicate,
            @PageableDefault(sort = "id") Pageable pageable
    ) {
        Page<BeneficioResponse> beneficios = fachada.listarBeneficiosPendentesMesAtual(predicate, pageable);
        return ResponseEntity.ok(beneficios);
    }

//    @PreAuthorize("hasRole('GESTOR')") Rota Publica
@GetMapping("/relatorio/financeiro")
public ResponseEntity<RelatorioFinanceiroResponse> gerarRelatorioFinanceiro(
        @QuerydslPredicate(root = Pagamento.class) Predicate predicate,
        @RequestParam(required = false, name = "curso.id") Long cursoId) {

    RelatorioFinanceiroResponse relatorio = fachada.gerarRelatorioFinanceiro(predicate, cursoId);
    return ResponseEntity.ok(relatorio);
}

    @GetMapping("/quantidade/beneficiados")
    public ResponseEntity<Long> getQuantidadeEstudantesBeneficiados() {
        Long quantidade = fachada.contarEstudantesBeneficiados();
        return ResponseEntity.ok(quantidade);
    }

    @GetMapping("/quantidade/cursos")
    public ResponseEntity<Long> getQuantidadeCursosDistintosComBeneficio() {
        Long quantidade = fachada.contarCursosDistintosComBeneficioAtivo();
        return ResponseEntity.ok(quantidade);
    }

    @GetMapping("/quantidade/beneficiados/por/curso")
    public ResponseEntity<List<Map<String, Object>>> getQuantidadeBeneficiadosPorCurso(
            @QuerydslPredicate(root = Pagamento.class) Predicate predicate) {
        List<Map<String, Object>> resposta = fachada.obterQuantidadeBeneficiadosPorCurso((List<UUID>) predicate);
        return ResponseEntity.ok(resposta);
    }

    @PreAuthorize("hasRole('GESTOR')")
    @PatchMapping("/{id}/prorrogar")
    public ResponseEntity<BeneficioResponse> prorrogarBeneficio(
            @PathVariable Long id,
            @Valid @RequestBody BeneficioProrrogacaoRequest request) throws BeneficioNotFoundException {

        BeneficioResponse beneficioProrrogado = fachada.prorrogarBeneficio(id, request);
        return ResponseEntity.ok(beneficioProrrogado);
    }
}
