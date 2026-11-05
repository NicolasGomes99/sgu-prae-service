package br.edu.ufape.sguPraeService.comunicacao.controllers;


import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoPatchRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoResponse;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CursoNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Curso;
import com.querydsl.core.types.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
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

@RestController@RequiredArgsConstructor
@RequestMapping("/curso")
public class CursoController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;


    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping
    public ResponseEntity<CursoResponse> salvar(@Valid @RequestBody CursoRequest curso) {
        Curso response = fachada.salvarCurso(curso.convertToEntity(curso, modelMapper));
        return new ResponseEntity<>(new CursoResponse(response, modelMapper), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<CursoResponse> editar(@PathVariable Long id, @RequestBody CursoPatchRequest patch) throws CursoNotFoundException {
        Curso atualizado = fachada.editarCurso(id, patch);
        return new ResponseEntity<>(new CursoResponse(atualizado, modelMapper), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CursoResponse> buscar(@PathVariable Long id) throws CursoNotFoundException {
        Curso response = fachada.buscarCurso(id);
        return new ResponseEntity<>(new CursoResponse(response, modelMapper), HttpStatus.OK);
    }

    @GetMapping
    public Page<CursoResponse> listar(@QuerydslPredicate(root = Curso.class) Predicate predicate,
                                      @PageableDefault(value = 2)
                                      @SortDefault(sort = "id", direction = Sort.Direction.ASC)
                                      Pageable pageable) {

        return fachada.listarCursos(predicate, pageable)
                .map(curso -> new CursoResponse(curso, modelMapper));
    }


    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) throws CursoNotFoundException {
        fachada.deletarCurso(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}