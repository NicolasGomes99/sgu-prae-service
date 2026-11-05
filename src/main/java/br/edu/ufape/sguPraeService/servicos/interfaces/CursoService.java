package br.edu.ufape.sguPraeService.servicos.interfaces;


import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CursoNotFoundException;
import br.edu.ufape.sguPraeService.models.Curso;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CursoService {
    Curso salvar(Curso curso);

    Curso buscar(Long id) throws CursoNotFoundException;

    Page<Curso> listar(Predicate predicate, Pageable pageable);

    Curso editar(Long id, Curso novoCurso) throws CursoNotFoundException;

    void deletar(Long id) throws CursoNotFoundException;
}