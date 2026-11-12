package br.edu.ufape.sguPraeService.comunicacao.dto.pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.modelmapper.ModelMapper;

import br.edu.ufape.sguPraeService.comunicacao.dto.beneficio.BeneficioResponse;
import br.edu.ufape.sguPraeService.models.Pagamento;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class PagamentoResponse {

    private Long id;
    private BigDecimal valor;
    private LocalDate data;
    private Integer mesReferencia;
    private Integer anoReferencia;
    private String numeroLote;
    private BeneficioResponse beneficio;

    public PagamentoResponse(Pagamento pagamento, ModelMapper modelMapper) {
        if (pagamento == null) throw new IllegalArgumentException("Pagamento não pode ser nulo");
        this.id = pagamento.getId();
        this.valor = pagamento.getValor();
        this.data = pagamento.getData();
        this.mesReferencia = pagamento.getMesReferencia();
        this.anoReferencia = pagamento.getAnoReferencia();
        this.numeroLote = pagamento.getNumeroLote();
        if (pagamento.getBeneficio() != null) {
            this.beneficio = new BeneficioResponse(pagamento.getBeneficio(), modelMapper);
        }
    }
}
