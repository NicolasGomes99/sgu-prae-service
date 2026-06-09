package br.edu.ufape.sguPraeService.dados;

import br.edu.ufape.sguPraeService.models.Agendamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import br.edu.ufape.sguPraeService.models.QAgendamento;
import com.querydsl.core.types.dsl.StringPath;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.lang.NonNull;


import java.time.LocalDate;
import java.util.*;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long>,
        QuerydslPredicateExecutor<Agendamento>,
        QuerydslBinderCustomizer<QAgendamento> {

    boolean existsByEstudante_UserIdAndDataAndAtivoTrue(java.util.UUID userId, LocalDate data);

    List<Agendamento> findByConfirmacaoEnviadaFalseAndAtivoTrue();

    Optional<Agendamento> findTopByEstudante_UserIdAndDataCriacaoIsNotNullOrderByDataCriacaoDesc(UUID userId);

    Page<Agendamento> findAllByEstudante_UserIdAndAtivoTrue(UUID userId, Pageable pageable);

    @Query("SELECT a FROM Agendamento a " +
            "JOIN a.vaga v " +
            "JOIN v.cronograma c " +
            "JOIN c.profissional p " +
            "WHERE p.userId = :userId and  a.ativo = true")
    Page<Agendamento> findAllByProfissionalUserId(@Param("userId") UUID userId, Pageable pageable);

    @Override
    default void customize(QuerydslBindings bindings, @NonNull QAgendamento root) {
        // Habilita "contains" para todas as strings
        bindings.bind(String.class).first((StringPath path, String value) -> path.containsIgnoreCase(value));

        // Excluir atributos que não fazem sentido filtrar
        bindings.excluding(root.ativo);

        // --- Binding para data (suporta data exata e periodo) ---
        bindings.bind(root.data).all((path, value) -> {
            if (value.isEmpty()) {
                return Optional.empty();
            }
            // Converte a coleção para uma lista
            List<LocalDate> datas = new ArrayList<>(value);
            Collections.sort(datas);
            if (datas.size() == 1) {
                // 1. Filtro de data exata
                return Optional.of(path.eq(datas.getFirst()));
            } else {
                // 2. Filtro de período
                return Optional.of(path.between(datas.getFirst(), datas.getLast()));
            }
        });

        bindings.bind(root.modalidade).first((path, value) -> path.eq(value));
        bindings.bind(root.vaga.id).first((path, value) -> path.eq(value));
        bindings.bind(root.estudante.id).first((path, value) -> path.eq(value));
        bindings.bind(root.vaga.cronograma.id).first((path, value) -> path.eq(value));
    }
}