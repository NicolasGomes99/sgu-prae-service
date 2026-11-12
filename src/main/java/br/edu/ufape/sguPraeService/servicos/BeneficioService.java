package br.edu.ufape.sguPraeService.servicos;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.exceptions.LimiteBeneficiosExcedidoException;
import br.edu.ufape.sguPraeService.models.Beneficio;
import br.edu.ufape.sguPraeService.models.Estudante;
import br.edu.ufape.sguPraeService.models.enums.MotivoEncerramento;
import com.querydsl.core.types.dsl.BooleanExpression;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import br.edu.ufape.sguPraeService.dados.BeneficioRepository;
import br.edu.ufape.sguPraeService.exceptions.BeneficioNotFoundException;
import lombok.RequiredArgsConstructor;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.BooleanBuilder;
import br.edu.ufape.sguPraeService.models.QBeneficio;

@Service
@RequiredArgsConstructor
public class BeneficioService implements br.edu.ufape.sguPraeService.servicos.interfaces.BeneficioService {

    private final BeneficioRepository beneficioRepository;
    private final ModelMapper modelMapper;
    private final NotificacaoPublisher notificacaoPublisher;

    @Override
    public Page<Beneficio> listar(Predicate predicate, Pageable pageable) {
        QBeneficio qBeneficio = QBeneficio.beneficio;
        BooleanBuilder filtroBase = new BooleanBuilder();
        filtroBase.and(qBeneficio.ativo.isTrue());

        Predicate predicadoFinal = filtroBase.and(predicate);
        return beneficioRepository.findAll(predicadoFinal, pageable);
    }

    @Override
    public Page<Beneficio> listarInativos(Predicate predicate, Pageable pageable) {
        QBeneficio qBeneficio = QBeneficio.beneficio;
        BooleanBuilder filtro = new BooleanBuilder();
        filtro.and(qBeneficio.ativo.isFalse());
        if (predicate != null) {
            filtro.and(predicate);
        }
        return beneficioRepository.findAll(filtro, pageable);
    }

    @Override
    public List<Beneficio> listar() {
        return beneficioRepository.findAll();
    }

    @Override
    public List<Beneficio> listarPorEstudante(UUID estudanteId) {
        return beneficioRepository.findAllByAtivoTrueAndEstudantes_Id(estudanteId);
    }

    @Override
    public Page<Beneficio> listarPorEstudante(UUID estudanteId, Pageable pageable) {
        return beneficioRepository.findAllByAtivoTrueAndEstudantes_Id(estudanteId, pageable);
    }

    @Override
    public Beneficio buscar(Long id) throws BeneficioNotFoundException {
        return beneficioRepository.findById(id).orElseThrow(BeneficioNotFoundException::new);
    }

    @Override
    public Beneficio salvar(Beneficio entity) {
        if (entity.getId() == null) {
            long qtdAtivos = beneficioRepository.countByEstudantesIdAndAtivoTrue(entity.getEstudantes().getId());
            if (qtdAtivos >= 2) {
                throw new LimiteBeneficiosExcedidoException("O estudante já possui o limite máximo de 2 benefícios ativos.");
            }
        }
        return beneficioRepository.save(entity);
    }

    @Override
    public Beneficio editar(Beneficio old, Beneficio entity) throws BeneficioNotFoundException {
        var pagamentosOriginal = old.getPagamentos();
        var termoOriginal = old.getTermo();

        modelMapper.map(entity, old);
        old.setPagamentos(pagamentosOriginal);
        if (entity.getTermo() == null) {
            old.setTermo(termoOriginal);
        }

        return beneficioRepository.save(old);
    }

    @Override
    public void deletar(Long id) throws BeneficioNotFoundException {
        Beneficio beneficio = buscar(id);
        beneficio.setAtivo(false);
        beneficioRepository.save(beneficio);

        UUID idAluno = beneficio.getEstudantes().getId();
        String msg = String.format("Seu benefício %s foi cancelado pelo sistema.", beneficio.getTipoBeneficio().getDescricao());
        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idAluno, "Benefício Cancelado", msg, "BENEFICIO"));
    }

    @Override
    public List<Beneficio> buscarPorPagamento(Long pagamentoId) {
        return beneficioRepository.findByPagamentos_Id(pagamentoId);
    }

    @Override
    public Page<Beneficio> listarPagosPorMes(Pageable pageable) {
        QBeneficio qBeneficio = QBeneficio.beneficio;
        LocalDate agora = LocalDate.now();

        LocalDate inicioMes = agora.withDayOfMonth(1);
        LocalDate fimMes = agora.withDayOfMonth(agora.lengthOfMonth());

        BooleanExpression isAtivo = qBeneficio.ativo.isTrue();
        BooleanExpression temPagamentoNesteMes = qBeneficio.pagamentos.any().data.between(inicioMes, fimMes);
        BooleanExpression jaPago = isAtivo.and(temPagamentoNesteMes);

        return beneficioRepository.findAll(jaPago, pageable);
    }

    @Override
    public Page<Beneficio> listarPorTipo(Long tipoId, Pageable pageable) {
        return beneficioRepository.findByTipoBeneficioId(tipoId, pageable);
    }

    @Override
    public Page<Beneficio> listarBeneficiosPendentesMesAtual(Predicate predicate, Pageable pageable) {
        QBeneficio qBeneficio = QBeneficio.beneficio;
        LocalDate agora = LocalDate.now();

        LocalDate inicioMes = agora.withDayOfMonth(1);
        LocalDate fimMes = agora.withDayOfMonth(agora.lengthOfMonth());

        BooleanExpression isAtivo = qBeneficio.ativo.isTrue();
        BooleanExpression temPagamentoNesteMes = qBeneficio.pagamentos.any().data.between(inicioMes, fimMes);
        BooleanExpression ehPendente = isAtivo.and(temPagamentoNesteMes.not());

        BooleanBuilder filtroFinal = new BooleanBuilder();
        filtroFinal.and(ehPendente);

        if (predicate != null) {
            filtroFinal.and(predicate);
        }

        return beneficioRepository.findAll(filtroFinal, pageable);
    }

    @Override
    public Page<Estudante> listarEstudantesComBeneficioAtivo(Pageable pageable) {
        return beneficioRepository.findAllEstudantesByAtivoTrue(pageable);
    }

    @Override
    public List<Estudante> listarEstudantesPorAuxilio(Long id) {
        return beneficioRepository.findEstudantesByBeneficioId(id);
    }

    @Override
    public Page<Estudante> listarEstudantesPorAuxilio(Long id, Pageable pageable) {
        return beneficioRepository.findEstudantesByBeneficioId(id, pageable);
    }

    @Override
    public Long contarEstudantesBeneficiados() {
        return beneficioRepository.countDistinctEstudantesAtivos();
    }

    @Override
    public List<UUID> obterUserIdsEstudantesComBeneficioAtivo() {
        return beneficioRepository.findDistinctEstudanteIdsWithBeneficioAtivo();
    }

    @Override
    public Long contarCursosDistintosComBeneficioAtivo() {
        List<Beneficio> beneficiosAtivos = beneficioRepository.findAllByAtivoTrueAndStatusTrue();

        return beneficiosAtivos.stream()
                .map(Beneficio::getEstudantes)
                .filter(java.util.Objects::nonNull)
                .map(Estudante::getCurso)
                .filter(java.util.Objects::nonNull)
                .map(br.edu.ufape.sguPraeService.models.Curso::getId)
                .distinct()
                .count();
    }

    @Override
    public List<Map<String, Object>> obterQuantidadeBeneficiadosPorCurso(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<Estudante> estudantes = beneficioRepository.findAllByAtivoTrueAndStatusTrue().stream()
                .map(Beneficio::getEstudantes)
                .filter(e -> e != null && userIds.contains(e.getId()) && e.getCurso() != null)
                .distinct()
                .toList();

        return estudantes.stream()
                .collect(Collectors.groupingBy(
                        Estudante::getCurso,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("cursoId", entry.getKey().getId());
                    map.put("cursoNome", entry.getKey().getNome());
                    map.put("quantidadeBeneficiados", entry.getValue());
                    return map;
                }).collect(Collectors.toList());
    }

    @Override
    public Beneficio prorrogar(Long id, YearMonth novoPrazo, String observacoes) throws BeneficioNotFoundException {
        Beneficio beneficio = buscar(id);

        if (!beneficio.isAtivo()) {
            throw new IllegalArgumentException("Não é possível prorrogar um benefício que já foi encerrado/inativado.");
        }

        beneficio.setFimBeneficio(novoPrazo);
        beneficio.setObservacaoProrrogacao(observacoes);

        Beneficio beneficioAtualizado = beneficioRepository.save(beneficio);

        UUID idAluno = beneficioAtualizado.getEstudantes().getId();
        String msg = String.format("Seu benefício %s foi prorrogado até %s.",
                beneficioAtualizado.getTipoBeneficio().getDescricao(), novoPrazo);
        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idAluno, "Benefício Prorrogado", msg, "BENEFICIO"));

        return beneficioAtualizado;
    }

    @Override
    public Beneficio cancelar(Long id, MotivoEncerramento motivoEncerramento, String parecerTermino) throws BeneficioNotFoundException {
        Beneficio beneficio = buscar(id);

        beneficio.setMotivoEncerramento(motivoEncerramento);
        beneficio.setParecerTermino(parecerTermino);
        beneficio.setFimBeneficio(java.time.YearMonth.now());
        beneficio.setAtivo(false);

        Beneficio salvo = beneficioRepository.save(beneficio);

        UUID idAluno = salvo.getEstudantes().getId();
        String msg = String.format("Seu benefício %s foi cancelado. Motivo: %s",
                salvo.getTipoBeneficio().getDescricao(), motivoEncerramento);
        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idAluno, "Benefício Cancelado", msg, "BENEFICIO"));

        return salvo;
    }

    @Override
    @Transactional
    public void processarBeneficiosVencidos() {
        List<Beneficio> beneficiosAtivos = beneficioRepository.findAllByAtivoTrueAndStatusTrue();
        YearMonth mesAtual = YearMonth.now();

        for (Beneficio beneficio : beneficiosAtivos) {
            if (beneficio.getFimBeneficio() != null && beneficio.getFimBeneficio().isBefore(mesAtual)) {

                beneficio.setAtivo(false);
                beneficio.setStatus(false);
                beneficio.setMotivoEncerramento(MotivoEncerramento.CONCLUSAO_BENEFICIO);
                beneficioRepository.save(beneficio);

                UUID idAluno = beneficio.getEstudantes().getId();
                String nomeBeneficio = beneficio.getTipoBeneficio().getDescricao();
                String msg = String.format("O prazo de validade do seu benefício %s chegou ao fim. O benefício foi encerrado no sistema.", nomeBeneficio);

                notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idAluno, "Benefício Encerrado", msg, "BENEFICIO"));
            }
        }
    }
}
