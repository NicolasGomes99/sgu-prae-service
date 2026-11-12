package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.dados.AgendamentoRepository;
import br.edu.ufape.sguPraeService.exceptions.GlobalAccessDeniedException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.AgendamentoNotFoundException;
import br.edu.ufape.sguPraeService.models.*;
import br.edu.ufape.sguPraeService.models.enums.ModalidadeAgendamento;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AgendamentoService implements br.edu.ufape.sguPraeService.servicos.interfaces.AgendamentoService {
    private final AgendamentoRepository repository;
    private final ModelMapper modelMapper;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final NotificacaoPublisher notificacaoPublisher;

    @Override
    public Agendamento salvar(Agendamento entity) {
        return repository.save(entity);
    }

    @Override
    public Agendamento agendar(Vaga vaga, Estudante estudante, ModalidadeAgendamento modalidade) {
        Optional<Agendamento> ultimoAgendamento =
                repository.findTopByEstudante_IdAndDataCriacaoIsNotNullOrderByDataCriacaoDesc(estudante.getId());

        if (ultimoAgendamento.isPresent()) {
            LocalDateTime dataUltimaCriacao = ultimoAgendamento.get().getDataCriacao();
            if (ChronoUnit.HOURS.between(dataUltimaCriacao, LocalDateTime.now()) < 24) {
                LocalDateTime liberacao = dataUltimaCriacao.plusHours(24);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
                throw new IllegalArgumentException(
                        "Você pode realizar um novo agendamento a cada 24 horas. " +
                                "Seu próximo agendamento estará liberado em: " + liberacao.format(formatter)
                );
            }
        }

        boolean jaPossuiAgendamentoNaData = repository.existsByEstudante_IdAndDataAndAtivoTrue(
                estudante.getId(), vaga.getCronograma().getData()
        );

        if (jaPossuiAgendamentoNaData) {
            throw new IllegalArgumentException("Você possui um agendamento ativo para a data deste cronograma.");
        }

        Agendamento agendamento = new Agendamento();
        agendamento.setData(vaga.getCronograma().getData());
        agendamento.setVaga(vaga);
        agendamento.setEstudante(estudante);
        agendamento.setModalidade(modalidade);
        Agendamento salvo = repository.save(agendamento);

        // Notificar o Profissional do novo agendamento
        UUID idProfissional = salvo.getVaga().getCronograma().getProfissional().getId();
        String dataFormatada = salvo.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String msgProfissional = String.format("Um novo atendimento foi agendado para o dia %s às %s na modalidade %s.",
                dataFormatada, salvo.getVaga().getHoraInicio(), modalidade);
        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idProfissional, "Novo Agendamento", msgProfissional, "AGENDAMENTO"));

        return salvo;
    }

    @Override
    public Agendamento buscar(Long id) throws AgendamentoNotFoundException {
        Agendamento agendamento = repository.findById(id).orElseThrow(AgendamentoNotFoundException::new);
        UUID currentUserId = authenticatedUserProvider.getUserId();
        if (!authenticatedUserProvider.hasAnyRole("GESTOR", "ADMINISTRADOR")
                && !Objects.equals(agendamento.getEstudante().getId(), currentUserId)
                && !Objects.equals(agendamento.getVaga().getCronograma().getProfissional().getId(), currentUserId)) {
            throw new GlobalAccessDeniedException("Você não tem permissão para acessar este recurso");
        }
        return agendamento;
    }

    @Override
    public Agendamento editar(Long Id, Agendamento entity) throws AgendamentoNotFoundException {
        Agendamento velhoAgendamento = buscar(Id);
        modelMapper.map(entity, velhoAgendamento);
        return salvar(velhoAgendamento);
    }

    @Override
    public void deletar(Long id) {
        Agendamento agendamento = buscar(id);
        agendamento.setAtivo(false);
        salvar(agendamento);
    }

    @Override
    public Page<Agendamento> listarAgendamentosPorEstudante(Estudante estudante, Pageable pageable) {
        return repository.findAllByEstudante_IdAndAtivoTrue(estudante.getId(), pageable);
    }

    @Override
    public Page<Agendamento> listarAgendamentosEstudanteAtual(Pageable pageable) {
        return repository.findAllByEstudante_IdAndAtivoTrue(authenticatedUserProvider.getUserId(), pageable);
    }

    @Override
    public Page<Agendamento> listarPorProfissional(Profissional profissional, Pageable pageable) {
        return repository.findAllByProfissionalId(profissional.getId(), pageable);
    }

    @Override
    public Page<Agendamento> listarPorProfissionalAtual(Pageable pageable) {
        return repository.findAllByProfissionalId(authenticatedUserProvider.getUserId(), pageable);
    }

    @Override
    public Agendamento alterarModalidade(Long id, ModalidadeAgendamento novaModalidade) throws AgendamentoNotFoundException {
        Agendamento agendamento = buscar(id);
        if (!Objects.equals(agendamento.getEstudante().getId(), authenticatedUserProvider.getUserId())) {
            throw new GlobalAccessDeniedException("Apenas o estudante titular pode alterar a modalidade deste agendamento.");
        }

        LocalDateTime dataHoraAgendamento = LocalDateTime.of(agendamento.getData(), agendamento.getVaga().getHoraInicio());
        if (LocalDateTime.now().plusHours(2).isAfter(dataHoraAgendamento)) {
            throw new IllegalArgumentException("A modalidade só pode ser alterada com até 2 horas de antecedência do horário agendado.");
        }

        agendamento.setModalidade(novaModalidade);
        Agendamento salvo = salvar(agendamento);

        // Notificar Estudante e Profissional sobre a alteração
        UUID idAluno = salvo.getEstudante().getId();
        UUID idProfissional = salvo.getVaga().getCronograma().getProfissional().getId();

        String dataFormatada = salvo.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String msg = String.format("A modalidade do agendamento do dia %s foi alterada para %s.", dataFormatada, novaModalidade);

        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idAluno, "Agendamento Alterado", msg, "AGENDAMENTO"));
        notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idProfissional, "Agendamento Alterado", msg, "AGENDAMENTO"));

        return salvo;
    }
}
