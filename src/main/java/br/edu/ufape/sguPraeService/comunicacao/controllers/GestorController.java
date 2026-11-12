package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.gestor.GestorResponse;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.GestorNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.UsuarioNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Gestor;
import com.querydsl.core.types.Predicate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/gestor")
@Tag(name = "05. Gestores")
public class GestorController {

    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Buscar gestor por ID (UUID)",
            description = """
                    Retorna os dados cadastrais de um gestor da PRAE pelo seu `UUID`.
                    - **Proteção Anti-IDOR**: O gestor autenticado só pode consultar seu próprio registro, exceto usuários com role `ADMINISTRADOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gestor encontrado."),
            @ApiResponse(responseCode = "403", description = "Acesso negado."),
            @ApiResponse(responseCode = "404", description = "Gestor não encontrado.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<GestorResponse> buscarGestor(
            @Parameter(description = "UUID do gestor", required = true) @PathVariable UUID id) throws GestorNotFoundException, UsuarioNotFoundException {
        Gestor response = fachada.buscarGestor(id);
        return new ResponseEntity<>(new GestorResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Listar gestores ativos (Paginado)",
            description = "Lista todos os gestores ativos com suporte a filtros QueryDSL e paginação. Exclusivo para `ADMINISTRADOR`."
    )
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @GetMapping
    public Page<GestorResponse> listarGestores(
            @Parameter(hidden = true) @QuerydslPredicate(root = Gestor.class) Predicate predicate,
            @ParameterObject @PageableDefault(value = 10)
            @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {

        return fachada.listarGestores(predicate, pageable)
                .map(gestor -> new GestorResponse(gestor, modelMapper));
    }
}
