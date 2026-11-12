package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.models.Vaga;
import br.edu.ufape.sguPraeService.dados.VagaRepository;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.VagaNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class VagaService implements br.edu.ufape.sguPraeService.servicos.interfaces.VagaService {
    private final VagaRepository repository;
    private final ModelMapper modelMapper;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    @Override
    public List<Vaga> listar() {
        return repository.findAll();
    }

    @Override
    public Vaga buscar(Long id) throws VagaNotFoundException {
        return repository.findById(id).orElseThrow(VagaNotFoundException::new);
    }

    @Override
    public Vaga salvar(Vaga entity) {
        return repository.save(entity);
    }

    @Override
    public Vaga editar(Long id, Vaga entity) throws VagaNotFoundException {
        Vaga vaga = buscar(id);
        validarPermissaoVaga(vaga);
        modelMapper.map(entity, vaga);
        return repository.save(vaga);
    }

    @Override
    public List<Vaga> gerarVagas(List<LocalTime> horarios, LocalTime tempoAtendimento) {
        return horarios.stream()
                .map(horario -> {
                    Vaga vaga = new Vaga();
                    vaga.setHoraInicio(horario);
                    vaga.setHoraFim(horario.plusMinutes(tempoAtendimento.toSecondOfDay() / 60));
                    return vaga;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void deletar(Long id) {
        Vaga vaga = buscar(id);
        validarPermissaoVaga(vaga);
        repository.deleteById(id);
    }

    private void validarPermissaoVaga(Vaga vaga) {
        UUID ownerId = (vaga.getCronograma() != null && vaga.getCronograma().getProfissional() != null)
                ? vaga.getCronograma().getProfissional().getId()
                : null;
        authenticatedUserProvider.validateOwnershipOrRoles(ownerId, "GESTOR", "ADMINISTRADOR");
    }
}
