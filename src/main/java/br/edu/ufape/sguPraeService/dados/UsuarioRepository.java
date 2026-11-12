package br.edu.ufape.sguPraeService.dados;

import br.edu.ufape.sguPraeService.models.QUsuario;
import br.edu.ufape.sguPraeService.models.Usuario;
import com.querydsl.core.types.dsl.StringPath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, QuerydslPredicateExecutor<Usuario>, QuerydslBinderCustomizer<QUsuario> {
    List<Usuario> findByIdIn(List<UUID> kcIds);

    List<Usuario> findAllByAtivoTrue();

    @Query("SELECT u FROM Usuario u WHERE TYPE(u) = :tipo AND u.ativo = true")
    List<Usuario> findByTipoUsuarioAtivo(@Param("tipo") Class<? extends Usuario> tipo);

    @Override
    default void customize(QuerydslBindings bindings, @NonNull QUsuario root) {
        bindings.bind(String.class).first((StringPath path, String value) -> path.containsIgnoreCase(value));
    }
}
