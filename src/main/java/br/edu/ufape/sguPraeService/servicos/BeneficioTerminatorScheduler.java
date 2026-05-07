package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.servicos.interfaces.BeneficioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BeneficioTerminatorScheduler {

    private final BeneficioService beneficioService;

    /**
     * ENCERRAMENTO NATURAL DE BENEFÍCIOS
     * Roda todo dia 1º do mês à 01:00 da manhã.
     * Expressão Cron: Segundo(0) Minuto(0) Hora(1) DiaDoMes(1) Mês(*) DiaDaSemana(*)
     */
    @Scheduled(cron = "0 0 1 1 * *")
    public void encerrarBeneficiosVencidosMensalmente() {
        log.info("Iniciando rotina de encerramento automático de benefícios vencidos...");

        beneficioService.processarBeneficiosVencidos();
    }
}