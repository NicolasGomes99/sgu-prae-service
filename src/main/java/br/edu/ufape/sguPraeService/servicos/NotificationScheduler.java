package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoPublisher;
import br.edu.ufape.sguPraeService.dados.AgendamentoRepository;
import br.edu.ufape.sguPraeService.dados.BeneficioRepository;
import br.edu.ufape.sguPraeService.models.Agendamento;
import br.edu.ufape.sguPraeService.models.Beneficio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final AgendamentoRepository agendamentoRepository;
    private final BeneficioRepository beneficioRepository;
    private final NotificacaoPublisher notificacaoPublisher;

    /**
     * 1. CONFIRMAÇÃO DE AGENDAMENTO (Roda a cada 5 minutos)
     * Notifica o aluno quando a janela de edição de 2 horas se fecha.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void confirmarAgendamentosPendentes() {
        // Busca todos os agendamentos que ainda não enviaram a notificação de confirmação final
        List<Agendamento> pendentes = agendamentoRepository.findByConfirmacaoEnviadaFalseAndAtivoTrue();
        LocalDateTime momentoLimite = LocalDateTime.now().plusHours(2);

        for (Agendamento agendamento : pendentes) {
            LocalDateTime dataHoraAtendimento = LocalDateTime.of(
                    agendamento.getData(),
                    agendamento.getVaga().getHoraInicio()
            );

            // Verifica se já entramos na janela de 2 horas (onde não pode mais alterar a modalidade)
            // e se o atendimento ainda está no futuro.
            if (momentoLimite.isAfter(dataHoraAtendimento) && LocalDateTime.now().isBefore(dataHoraAtendimento)) {

                // Marca como notificado para não mandar de novo daqui a 5 minutos
                agendamento.setConfirmacaoEnviada(true);
                agendamentoRepository.save(agendamento);

                UUID idAluno = agendamento.getEstudante().getUserId();
                String msg = String.format("Seu agendamento para hoje às %s está 100%% confirmado. O prazo para alterações foi encerrado.",
                        agendamento.getVaga().getHoraInicio());

                notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idAluno, "Agendamento Confirmado", msg, "AGENDAMENTO"));
            }
        }
    }

    /**
     * 2. LEMBRETE MENSAL DE BENEFÍCIO (Roda todo dia 1º do mês às 08:00 da manhã)
     * Notifica o tempo restante do benefício ativo.
     */
    @Scheduled(cron = "0 0 8 1 * *")
    public void notificarTempoRestanteBeneficios() {
        log.info("Iniciando rotina mensal de lembrete de benefícios...");

        List<Beneficio> beneficiosAtivos = beneficioRepository.findAllByAtivoTrueAndStatusTrue();
        YearMonth mesAtual = YearMonth.now();

        for (Beneficio beneficio : beneficiosAtivos) {
            if (beneficio.getFimBeneficio() != null && beneficio.getFimBeneficio().isAfter(mesAtual)) {

                long mesesRestantes = ChronoUnit.MONTHS.between(mesAtual, beneficio.getFimBeneficio());

                if (mesesRestantes > 0) {
                    UUID idAluno = beneficio.getEstudantes().getUserId();
                    String nomeBeneficio = beneficio.getTipoBeneficio().getDescricao();

                    String msg = String.format("Atenção: Restam %d mês(es) para o término do seu benefício %s (Válido até %s).",
                            mesesRestantes, nomeBeneficio, beneficio.getFimBeneficio().format(DateTimeFormatter.ofPattern("MM/yyyy")));

                    notificacaoPublisher.publicar(NotificacaoEvent.paraUsuario(idAluno, "Lembrete Mensal", msg, "BENEFICIO"));
                }
            }
        }
    }
}