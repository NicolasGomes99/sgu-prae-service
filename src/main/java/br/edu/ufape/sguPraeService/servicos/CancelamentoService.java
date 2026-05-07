package br.edu.ufape.sguPraeService.servicos;



import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.dados.CancelamentoAgendamentoRepository;
import br.edu.ufape.sguPraeService.exceptions.GlobalAccessDeniedException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CancelamentoNotFoundException;
import br.edu.ufape.sguPraeService.models.CancelamentoAgendamento;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class CancelamentoService implements br.edu.ufape.sguPraeService.servicos.interfaces.CancelamentoService {
    private final CancelamentoAgendamentoRepository cancelamentoAgendamentoRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final NotificacaoPublisher notificacaoPublisher;


    @Override
    public CancelamentoAgendamento salvar(CancelamentoAgendamento entity) {
        entity.setDataCancelamento(LocalDateTime.now());
        CancelamentoAgendamento salvo = cancelamentoAgendamentoRepository.save(entity);

        // Notificar Estudante e Profissional do Cancelamento
        UUID idAluno = salvo.getAgendamento().getEstudante().getUserId();
        UUID idProfissional = salvo.getAgendamento().getVaga().getCronograma().getProfissional().getUserId();
        String dataFormatada = salvo.getAgendamento().getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String msg = String.format("O agendamento do dia %s foi cancelado. Motivo: %s", dataFormatada, salvo.getMotivo());

        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idAluno, "Agendamento Cancelado", msg, "AGENDAMENTO"));
        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idProfissional, "Agendamento Cancelado", msg, "AGENDAMENTO"));

        return salvo;
    }

    @Override
    public CancelamentoAgendamento buscar(Long id) {
        CancelamentoAgendamento cancelamento = cancelamentoAgendamentoRepository.findById(id).orElseThrow(CancelamentoNotFoundException::new);
        if(!Objects.equals(cancelamento.getAgendamento().getEstudante().getUserId(), authenticatedUserProvider.getUserId())
                && !Objects.equals(cancelamento.getAgendamento().getVaga().getCronograma().getProfissional().getUserId(), authenticatedUserProvider.getUserId())){
            throw new GlobalAccessDeniedException("Você não tem permissão para acessar este recurso");
        }
        return cancelamentoAgendamentoRepository.findById(id).orElse(null);
    }

    @Override
    public Page<CancelamentoAgendamento> listar(Pageable pageable) {
        return cancelamentoAgendamentoRepository.findAll(pageable);
    }

    @Override
    public Page<CancelamentoAgendamento> ListarCancelamentosPorEstudante(UUID userId, Pageable pageable) {
        return cancelamentoAgendamentoRepository.findAllByAgendamento_Estudante_UserId(userId, pageable);
    }

    @Override
    public Page<CancelamentoAgendamento> ListarCancelamentosPorProfissional(UUID userId, Pageable pageable) {
        return cancelamentoAgendamentoRepository.findAllByProfissionalUserId(userId, pageable);
    }

    @Override
    public Page<CancelamentoAgendamento> ListarPorEstudanteAtual(Pageable pageable) {
        return cancelamentoAgendamentoRepository.findAllByAgendamento_Estudante_UserId(authenticatedUserProvider.getUserId(), pageable);
    }

    @Override
    public Page<CancelamentoAgendamento> ListarPorProfissionalAtual(Pageable pageable) {
        return cancelamentoAgendamentoRepository.findAllByProfissionalUserId(authenticatedUserProvider.getUserId(), pageable);
    }
}
