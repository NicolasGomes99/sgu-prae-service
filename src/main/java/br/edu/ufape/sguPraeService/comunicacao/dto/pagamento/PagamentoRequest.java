package br.edu.ufape.sguPraeService.comunicacao.dto.pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.edu.ufape.sguPraeService.models.Beneficio;
import org.modelmapper.ModelMapper;

import br.edu.ufape.sguPraeService.comunicacao.annotations.PagamentoValido;
import br.edu.ufape.sguPraeService.models.Pagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@PagamentoValido
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class PagamentoRequest {
    @NotNull(message = "Valor é obrigatório")
    @Positive(message="Valor inválido")
    private Long beneficioId;
    @NotNull(message = "Valor é obrigatório")
    @Positive(message="Valor inválido")
    private BigDecimal valor;
    @NotNull(message = "A data é obrigatória")
    private LocalDate data;
    @NotNull(message = "O mês de referência é obrigatório")
    private Integer mesReferencia;
    @NotNull(message = "O ano de referência é obrigatório")
    private Integer anoReferencia;
    @NotNull(message = "O número do lote é obrigatório")
    private String numeroLote;

    public Pagamento convertToEntity(PagamentoRequest pagamentoRequest, ModelMapper modelMapper) {
        Pagamento entity = new Pagamento();
        entity.setValor(pagamentoRequest.getValor());
        entity.setData(pagamentoRequest.getData());
        entity.setMesReferencia(pagamentoRequest.getMesReferencia());
        entity.setAnoReferencia(pagamentoRequest.getAnoReferencia());
        entity.setNumeroLote(pagamentoRequest.getNumeroLote());
        Beneficio beneficio = new Beneficio();
        beneficio.setId(beneficioId);
        entity.setBeneficio(beneficio);
        return entity;
    }
}
