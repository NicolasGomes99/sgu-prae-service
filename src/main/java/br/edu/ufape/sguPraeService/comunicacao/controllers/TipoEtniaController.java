package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.tipoEtnia.TipoEtniaRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoEtnia.TipoEtniaResponse;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.TipoEtniaNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.TipoEtnia;
import com.querydsl.core.types.Predicate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.binding.QuerydslPredicate;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tipoEtnia")
@RequiredArgsConstructor
@Tag(name = "16. Tipos de Etnia")
public class TipoEtniaController {

    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Buscar tipo de etnia por ID",
            description = "Consulta um registro de autodeclaração étnico-racial pelo seu ID."
    )
    @GetMapping("/{id}")
    public ResponseEntity<TipoEtniaResponse> buscarTipoEtnia(
            @Parameter(description = "ID do tipo de etnia", required = true) @PathVariable Long id) throws TipoEtniaNotFoundException {
        TipoEtnia tipoEtnia = fachada.buscarTipoEtnia(id);
        if (tipoEtnia == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(new TipoEtniaResponse(tipoEtnia, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Listar tipos de etnia (Rota Pública)",
            description = "Lista as opções de autodeclaração étnico-racial cadastradas. Rota pública (`permitAll`) utilizada no formulário de cadastro de usuários."
    )
    @GetMapping
    public Page<TipoEtniaResponse> listarTiposEtnia(
            @Parameter(hidden = true) @QuerydslPredicate(root = TipoEtnia.class) Predicate predicate,
            @ParameterObject @PageableDefault(value = 10)
            @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return fachada.listarTiposEtnia(predicate, pageable)
                .map(tipoEtnia -> new TipoEtniaResponse(tipoEtnia, modelMapper));
    }

    @Operation(
            summary = "Cadastrar novo tipo de etnia",
            description = "Cria uma nova opção de autodeclaração étnico-racial (impede duplicidade case-insensitive retornando `409 Conflict`). Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<TipoEtniaResponse> criarTipoEtnia(@Valid @RequestBody TipoEtniaRequest tipoEtniaRequest) {
        TipoEtnia tipoEtnia = tipoEtniaRequest.convertToEntity(tipoEtniaRequest, modelMapper);
        TipoEtnia novoTipoEtnia = fachada.salvarTipoEtnia(tipoEtnia);
        return new ResponseEntity<>(new TipoEtniaResponse(novoTipoEtnia, modelMapper), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Atualizar tipo de etnia por ID",
            description = "Atualiza a descrição de um tipo de etnia existente. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<TipoEtniaResponse> atualizarTipoEtnia(
            @Parameter(description = "ID do tipo de etnia", required = true) @PathVariable Long id,
            @Valid @RequestBody TipoEtniaRequest tipoEtniaRequest) throws TipoEtniaNotFoundException {
        TipoEtnia tipoEtnia = tipoEtniaRequest.convertToEntity(tipoEtniaRequest, modelMapper);
        TipoEtnia tipoEtniaAtualizado = fachada.atualizarTipoEtnia(id, tipoEtnia);
        if (tipoEtniaAtualizado == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(new TipoEtniaResponse(tipoEtniaAtualizado, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Deletar tipo de etnia por ID",
            description = "Remove um tipo de etnia pelo seu ID. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTipoEtnia(
            @Parameter(description = "ID do tipo de etnia", required = true) @PathVariable Long id) throws TipoEtniaNotFoundException {
        fachada.deletarTipoEtnia(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
