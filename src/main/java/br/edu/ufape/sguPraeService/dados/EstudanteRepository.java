package br.edu.ufape.sguPraeService.dados;

import br.edu.ufape.sguPraeService.models.Estudante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.edu.ufape.sguPraeService.models.QEstudante;
import com.querydsl.core.types.dsl.StringPath;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.lang.NonNull;


public interface EstudanteRepository extends JpaRepository<Estudante, Long>,
        QuerydslPredicateExecutor<Estudante>,
        QuerydslBinderCustomizer<QEstudante> {

    Optional<Estudante> findByUserId(UUID userId);

    Page<Estudante> findByUserIdIn(List<UUID> userIds, Pageable pageable);

    Page<Estudante> findAllByAtivoTrue(Pageable pageable);

    Optional<Estudante> findByDadosBancarios_Id(Long dadosBancariosId);

    @Override
    default void customize(QuerydslBindings bindings, @NonNull QEstudante root) {
        bindings.bind(String.class).first((StringPath path, String value) -> path.containsIgnoreCase(value));
        bindings.excluding(root.ativo);
        bindings.bind(root.endereco.id).first((path, value) -> path.eq(value));
        bindings.bind(root.dadosBancarios.id).first((path, value) -> path.eq(value));
        // Adicionar outras customizações conforme necessário
    }
}
