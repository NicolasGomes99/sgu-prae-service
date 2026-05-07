package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.dados.EstudanteRepository;
import br.edu.ufape.sguPraeService.exceptions.ExceptionUtil;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.EstudanteNotFoundException;
import br.edu.ufape.sguPraeService.models.Estudante;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import br.edu.ufape.sguPraeService.models.QEstudante;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EstudanteService implements br.edu.ufape.sguPraeService.servicos.interfaces.EstudanteService {
    private final EstudanteRepository estudanteRepository;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public Estudante salvarEstudante(Estudante estudante) {
        try {
            return estudanteRepository.save(estudante);
        }catch (DataIntegrityViolationException e){
            throw ExceptionUtil.handleDataIntegrityViolationException(e);
        }

    }

    @Override
    public Estudante buscarEstudante(Long id) throws EstudanteNotFoundException {
        return estudanteRepository.findById(id)
                .orElseThrow(EstudanteNotFoundException::new);
    }

    @Override
    public Estudante buscarPorUserId(UUID userId) throws EstudanteNotFoundException {
        return (Estudante) estudanteRepository.findByUserId(userId)
                .orElseThrow(EstudanteNotFoundException::new);
    }

    @Override
    public Page<Estudante> buscarPorUserIds(List<UUID> userIds, Pageable pageable) {
        return estudanteRepository.findByUserIdIn(userIds, pageable);
    }

    @Override
    public Page<Estudante> listarEstudantes(Predicate predicate, Pageable pageable) {
        QEstudante qEstudante = QEstudante.estudante;
        BooleanBuilder filtroBase = new BooleanBuilder();
        filtroBase.and(qEstudante.ativo.isTrue());

        Predicate predicadoFinal = filtroBase.and(predicate);

        return estudanteRepository.findAll(predicadoFinal, pageable);
    }

    @Override
    public Estudante atualizarEstudante(Estudante estudante, Estudante existente) throws EstudanteNotFoundException {
        try {
            modelMapper.map(estudante, existente);
            return estudanteRepository.save(existente);
        }catch (DataIntegrityViolationException e){
            throw ExceptionUtil.handleDataIntegrityViolationException(e);
        }

    }

    @Override
    public void deletarEstudante(Long id) throws EstudanteNotFoundException {
        Estudante estudante = estudanteRepository.findById(id).orElseThrow(EstudanteNotFoundException::new);
        estudante.setAtivo(false);
        estudanteRepository.save(estudante);
    }

    public Estudante buscarPorDadosBancariosId(Long id) {
        return estudanteRepository.findByDadosBancarios_Id(id).orElse(null);
    }
}
