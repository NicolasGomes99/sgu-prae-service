package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.TipoBeneficio;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoBeneficio.TipoBeneficioResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoBeneficio.TipoBeneficioRequest;
import br.edu.ufape.sguPraeService.exceptions.TipoBeneficioNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.querydsl.core.types.Predicate;
import org.springframework.data.querydsl.binding.QuerydslPredicate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tipo-beneficio")
@Tag(name = "09. Tipos de Benefício")
public class TipoBeneficioController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Listar tipos de benefício ativos (Paginado)",
            description = "Lista o catálogo de modalidades de bolsas e auxílios estudantis ativos. Requer usuário autenticado."
    )
    @GetMapping
    public Page<TipoBeneficioResponse> listar(
            @Parameter(hidden = true) @QuerydslPredicate(root = TipoBeneficio.class) Predicate predicate,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarTipoBeneficios(predicate, pageable).map(tipoBeneficio -> new TipoBeneficioResponse(tipoBeneficio, modelMapper));
    }

    @Operation(
            summary = "Listar tipos de benefício inativos",
            description = "Lista as modalidades de benefício desativadas. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/inativos")
    public ResponseEntity<Page<TipoBeneficioResponse>> listarInativos(
            @Parameter(hidden = true) @QuerydslPredicate(root = TipoBeneficio.class) Predicate predicate,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {

        Page<TipoBeneficioResponse> page = fachada.listarTipoBeneficiosInativos(predicate, pageable)
                .map(tipoBeneficio -> new TipoBeneficioResponse(tipoBeneficio, modelMapper));

        return ResponseEntity.ok(page);
    }

    @Operation(
            summary = "Buscar tipo de benefício por ID",
            description = "Consulta os detalhes (descrição, natureza da bolsa/auxílio e valor base) de um tipo de benefício."
    )
    @GetMapping("/{id}")
    public ResponseEntity<TipoBeneficioResponse> buscar(
            @Parameter(description = "ID do tipo de benefício", required = true) @PathVariable Long id) throws TipoBeneficioNotFoundException {
        TipoBeneficio response = fachada.buscarTipoBeneficio(id);
        return new ResponseEntity<>(new TipoBeneficioResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Cadastrar novo tipo de benefício",
            description = "Cria uma nova modalidade de bolsa ou auxílio estudantil no catálogo da PRAE. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<TipoBeneficioResponse> salvar(@Valid @RequestBody TipoBeneficioRequest entity) {
        TipoBeneficio response = fachada.salvarTipoBeneficio(entity.convertToEntity(entity, modelMapper));
        return new ResponseEntity<>(new TipoBeneficioResponse(response, modelMapper), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Editar tipo de benefício por ID",
            description = "Atualiza descrição, natureza ou valor padrão de uma modalidade de benefício. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<TipoBeneficioResponse> editar(
            @Parameter(description = "ID do tipo de benefício", required = true) @PathVariable Long id,
            @Valid @RequestBody TipoBeneficioRequest entity) throws TipoBeneficioNotFoundException {
        TipoBeneficio response = fachada.editarTipoBeneficio(id, entity.convertToEntity(entity, modelMapper));
        return new ResponseEntity<>(new TipoBeneficioResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Deletar tipo de benefício por ID",
            description = "Remove um tipo de benefício. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do tipo de benefício", required = true) @PathVariable Long id) throws TipoBeneficioNotFoundException {
        fachada.deletarTipoBeneficio(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(
            summary = "Desativar logicamente um tipo de benefício",
            description = "Marca um tipo de benefício como inativo (`ativo = false`) preservando o histórico de benefícios já concedidos. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("desativar/{id}")
    public ResponseEntity<Void> desativar(
            @Parameter(description = "ID do tipo de benefício", required = true) @PathVariable Long id) throws TipoBeneficioNotFoundException {
        fachada.desativarTipoBeneficio(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(
            summary = "Contar quantidade total de tipos de benefício ativos (Público)",
            description = "Retorna o total de modalidades de bolsas/auxílios ativas no sistema."
    )
    @GetMapping("/quantidade")
    public ResponseEntity<Long> getQuantidadeTiposBeneficio() {
        Long quantidade = fachada.contarTiposBeneficio();
        return ResponseEntity.ok(quantidade);
    }
}
