package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.exceptions.GlobalAccessDeniedException;
import br.edu.ufape.sguPraeService.models.Cronograma;
import br.edu.ufape.sguPraeService.dados.CronogramaRepository;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CronogramaNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.BooleanBuilder;
import br.edu.ufape.sguPraeService.models.QCronograma;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CronogramaService implements br.edu.ufape.sguPraeService.servicos.interfaces.CronogramaService {
    private final CronogramaRepository repository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    @Override
    public Page<Cronograma> listar(Predicate predicate, Pageable pageable) {
        QCronograma qCronograma = QCronograma.cronograma;
        BooleanBuilder filtroBase = new BooleanBuilder();
        filtroBase.and(qCronograma.ativo.isTrue());

        Predicate predicadoFinal = filtroBase.and(predicate);
        return repository.findAll(predicadoFinal, pageable);
    }

    @Override
    public Page<Cronograma> listarPorTipoAtendimento(Long id, Pageable pageable) {
        return repository.findByAtivoTrueAndTipoAtendimento_Id(id, pageable);
    }

    @Override
    public Page<Cronograma> listarPorProfissional(UUID userId, Pageable pageable) {
        return repository.findAllByAtivoTrueAndProfissional_Id(userId, pageable);
    }

    @Override
    public Cronograma buscar(Long id) throws CronogramaNotFoundException {
        return repository.findById(id).orElseThrow(CronogramaNotFoundException::new);
    }

    @Override
    public Cronograma salvar(Cronograma entity) {
        return repository.save(entity);
    }

    public boolean existeCronogramaPorTipoAtendimento(Long tipoAtendimentoId) {
        return repository.existsByTipoAtendimento_Id(tipoAtendimentoId);
    }

    @Override
    public void deletar(Long id) {
        Cronograma cronograma = buscar(id);
        UUID ownerId = cronograma.getProfissional() != null ? cronograma.getProfissional().getId() : null;
        authenticatedUserProvider.validateOwnershipOrRoles(ownerId, "GESTOR", "ADMINISTRADOR");

        cronograma.setAtivo(false);
        repository.save(cronograma);
    }
}
