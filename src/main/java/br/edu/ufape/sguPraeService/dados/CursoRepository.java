package br.edu.ufape.sguPraeService.dados;

import br.edu.ufape.sguPraeService.models.Curso;
import br.edu.ufape.sguPraeService.models.QCurso;
//import br.edu.ufape.sguPraeService.models.Usuario;
import com.querydsl.core.types.dsl.StringPath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.lang.NonNull;

import java.util.List;

public interface CursoRepository extends JpaRepository<Curso, Long>, QuerydslPredicateExecutor<Curso>, QuerydslBinderCustomizer<QCurso> {

    @Override
    default void customize(QuerydslBindings bindings, @NonNull QCurso root) {
        bindings.bind(String.class).first((StringPath path, String value) -> path.containsIgnoreCase(value));
    }

    Page<Curso> findByAtivoTrue(Pageable pageable);

    boolean existsByNome(String nome);
    Curso findByNomeAndAtivoFalse(String nome);
}