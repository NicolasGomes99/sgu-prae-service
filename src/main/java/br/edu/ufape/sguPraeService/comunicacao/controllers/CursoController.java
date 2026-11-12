package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoPatchRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoResponse;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CursoNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Curso;
import com.querydsl.core.types.Predicate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.binding.QuerydslPredicate;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@RestController
@RequiredArgsConstructor
@RequestMapping("/curso")
@Tag(name = "15. Cursos")
public class CursoController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Cadastrar novo curso de graduação",
            description = """
                    Cria um novo curso de graduação. Caso já exista um curso inativo com o mesmo nome, ele é reativado automaticamente com os novos parâmetros.
                    - **Perfis permitidos**: `GESTOR` e `ADMINISTRADOR`.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<CursoResponse> salvar(@Valid @RequestBody CursoRequest curso) {
        Curso response = fachada.salvarCurso(curso.convertToEntity(curso, modelMapper));
        return new ResponseEntity<>(new CursoResponse(response, modelMapper), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Editar curso por ID",
            description = "Atualiza nome ou número de períodos de um curso existente. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<CursoResponse> editar(
            @Parameter(description = "ID do curso", required = true) @PathVariable Long id,
            @RequestBody CursoPatchRequest patch) throws CursoNotFoundException {
        Curso atualizado = fachada.editarCurso(id, patch);
        return new ResponseEntity<>(new CursoResponse(atualizado, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Buscar curso por ID",
            description = "Retorna os dados de um curso específico pelo seu ID."
    )
    @GetMapping("/{id}")
    public ResponseEntity<CursoResponse> buscar(
            @Parameter(description = "ID do curso", required = true) @PathVariable Long id) throws CursoNotFoundException {
        Curso response = fachada.buscarCurso(id);
        return new ResponseEntity<>(new CursoResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Listar cursos ativos (Rota Pública)",
            description = "Lista todos os cursos de graduação ativos com paginação e filtro por nome. Rota pública (`permitAll`) utilizada na tela de auto-cadastro de estudantes."
    )
    @GetMapping
    public Page<CursoResponse> listar(
            @Parameter(hidden = true) @QuerydslPredicate(root = Curso.class) Predicate predicate,
            @ParameterObject @PageableDefault(value = 2)
            @SortDefault(sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return fachada.listarCursos(predicate, pageable)
                .map(curso -> new CursoResponse(curso, modelMapper));
    }

    @Operation(
            summary = "Desativar curso por ID",
            description = "Realiza a exclusão lógica (`ativo = false`) de um curso. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID do curso", required = true) @PathVariable Long id) throws CursoNotFoundException {
        fachada.deletarCurso(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
