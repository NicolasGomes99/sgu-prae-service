package br.edu.ufape.sguPraeService.dados;

import br.edu.ufape.sguPraeService.models.Beneficio;
import br.edu.ufape.sguPraeService.models.Estudante;
import br.edu.ufape.sguPraeService.models.enums.MotivoEncerramento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import br.edu.ufape.sguPraeService.models.QBeneficio;
import com.querydsl.core.types.dsl.StringPath;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.lang.NonNull;

public interface BeneficioRepository extends JpaRepository<Beneficio, Long>,
        QuerydslPredicateExecutor<Beneficio>,
        QuerydslBinderCustomizer<QBeneficio> {

  List<Beneficio> findByAtivoTrue();

  List<Beneficio> findAllByAtivoTrueAndStatusTrue();

  long countByEstudantesIdAndAtivoTrue(Long estudanteId);

  Page<Beneficio> findByMotivoEncerramentoNotNull(Pageable pageable);

  Page<Beneficio> findByMotivoEncerramento(MotivoEncerramento motivo, Pageable pageable);

  Page<Beneficio> findByTipoBeneficioId(@Param("tipoId") Long tipoId, Pageable pageable);

  List<Beneficio> findByPagamentos_Id(Long pagamentoId);

  List<Beneficio> findAllByAtivoTrueAndEstudantes_Id(Long estudanteId);

  Page<Beneficio> findAllByAtivoTrueAndEstudantes_Id(Long estudanteId, Pageable pageable);

  @Query("SELECT b.estudantes FROM Beneficio b WHERE b.ativo = true")
  Page<Estudante> findAllEstudantesByAtivoTrue(Pageable pageable);

  @Query("SELECT b.estudantes FROM Beneficio b WHERE b.ativo = true AND b.id = :beneficioId")
  List<Estudante> findEstudantesByBeneficioId(@Param("beneficioId") Long beneficioId);

  @Query("SELECT b.estudantes FROM Beneficio b WHERE b.ativo = true AND b.id = :beneficioId")
  Page<Estudante> findEstudantesByBeneficioId(@Param("beneficioId") Long beneficioId, Pageable pageable);

  @Query("SELECT COUNT(DISTINCT b.estudantes.id) FROM Beneficio b WHERE b.ativo = true")
  Long countDistinctEstudantesAtivos();

  @Query("SELECT DISTINCT b.estudantes.userId FROM Beneficio b WHERE b.ativo = true")
  List<java.util.UUID> findDistinctEstudanteUserIdsWithBeneficioAtivo();

  @Override
  default void customize(QuerydslBindings bindings, @NonNull QBeneficio root) {
    bindings.bind(String.class).first((StringPath path, String value) -> path.containsIgnoreCase(value));


    bindings.excluding(root.termo, root.pagamentos, root.estudantes.documentos);

    // Bindings de relacionamentos
    bindings.bind(root.estudantes.id).first((path, value) -> path.eq(value));
    bindings.bind(root.tipoBeneficio.id).first((path, value) -> path.eq(value));
    bindings.bind(root.motivoEncerramento).first((path, value) -> path.eq(value));

    //Permitir explicitamente o filtro pelo status ativo/inativo
    bindings.bind(root.ativo).first((path, value) -> path.eq(value));


    bindings.bind(root.inicioBeneficio).all((path, value) -> {
      if (value.isEmpty()) return Optional.empty();
      List<YearMonth> datasInicio = new ArrayList<>(value);
      Collections.sort(datasInicio);
      if (datasInicio.size() == 1) {
        return Optional.of(path.eq(datasInicio.getFirst()));
      } else {
        return Optional.of(path.between(datasInicio.getFirst(), datasInicio.getLast()));
      }
    });

    bindings.bind(root.fimBeneficio).all((path, value) -> {
      if (value.isEmpty()) return Optional.empty();
      List<YearMonth> datasFim = new ArrayList<>(value);
      Collections.sort(datasFim);
      if (datasFim.size() == 1) {
        return Optional.of(path.eq(datasFim.getFirst()));
      } else {
        return Optional.of(path.between(datasFim.getFirst(), datasFim.getLast()));
      }
    });
  }
}