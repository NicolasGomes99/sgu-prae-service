package br.edu.ufape.sguPraeService.servicos.interfaces;

import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.EstudanteNotFoundException;
import br.edu.ufape.sguPraeService.models.Estudante;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.querydsl.core.types.Predicate;

import java.util.List;
import java.util.UUID;

public interface EstudanteService {
    @Transactional
    Estudante salvarEstudante(Estudante estudante);

    Estudante buscarEstudante(Long id) throws EstudanteNotFoundException;

    Estudante buscarPorUserId(UUID userId) throws EstudanteNotFoundException;

    Page<Estudante> buscarPorUserIds(List<UUID> userIds, Pageable pageable);

    Page<Estudante> listarEstudantes(Predicate predicate, Pageable pageable);

    Estudante atualizarEstudante(Estudante estudante, Estudante existente) throws EstudanteNotFoundException;

    void deletarEstudante(Long id) throws EstudanteNotFoundException;

    Estudante buscarPorDadosBancariosId(Long id);


}