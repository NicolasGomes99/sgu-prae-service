package br.edu.ufape.sguPraeService.servicos;


import br.edu.ufape.sguPraeService.dados.CursoRepository;
import br.edu.ufape.sguPraeService.exceptions.ExceptionUtil;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CursoNotFoundException;
import br.edu.ufape.sguPraeService.models.*;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service @RequiredArgsConstructor
public class CursoService implements br.edu.ufape.sguPraeService.servicos.interfaces.CursoService {
    private final CursoRepository cursoRepository;
    private final ModelMapper modelMapper;

    @Override
    public Curso salvar(Curso curso) {
        try {
            if(cursoRepository.existsByNome(curso.getNome())) {
                Curso cursoDesativado = cursoRepository.findByNomeAndAtivoFalse(curso.getNome());
                if (cursoDesativado != null) {
                    cursoDesativado.setAtivo(true);
                    return cursoRepository.save(cursoDesativado);
                }
            }
            return cursoRepository.save(curso);
        }catch (DataIntegrityViolationException e){
            throw  ExceptionUtil.handleDataIntegrityViolationException(e);
        }

    }

    @Override
    public Curso buscar(Long id) throws CursoNotFoundException {
        return cursoRepository.findById(id).orElseThrow(CursoNotFoundException::new);
    }

    @Override
    public Page<Curso> listar(Predicate predicate, Pageable pageable) {
        QCurso curso = QCurso.curso;
        BooleanBuilder filtroFixo = new BooleanBuilder();
        filtroFixo.and(curso.ativo.isTrue());

        Predicate predicadoFinal = filtroFixo.and(predicate);

        return cursoRepository.findAll(predicadoFinal, pageable);
    }

    @Override
    public Curso editar(Long id, Curso novoCurso) throws CursoNotFoundException{
        try {
            Curso antigoCurso = cursoRepository.findById(id).orElseThrow(CursoNotFoundException::new);
            modelMapper.map(novoCurso, antigoCurso);
            return cursoRepository.save(antigoCurso);
        } catch (DataIntegrityViolationException e){
            throw ExceptionUtil.handleDataIntegrityViolationException(e);
        }

    }

    @Override
    public void deletar(Long id) throws CursoNotFoundException {
        Curso curso = cursoRepository.findById(id).orElseThrow(CursoNotFoundException::new);
        curso.setAtivo(false);
        cursoRepository.save(curso);
    }


}