package br.edu.ufape.sguPraeService.comunicacao.dto.estudante;

import br.edu.ufape.sguPraeService.comunicacao.annotations.NumeroValido;
import br.edu.ufape.sguPraeService.comunicacao.dto.endereco.EnderecoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;


@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EstudanteUpdateRequest {

    @PositiveOrZero(message = "Renda deve ser um valor positivo ou zero.")
    private BigDecimal rendaPercapta;

    private String contatoFamilia;

    private Boolean deficiente;

    @Size(max = 100, message = "O campo 'deficiencia' deve ter no máximo 100 caracteres.")
    private String tipoDeficiencia;

    private Long tipoEtniaId;

    @Valid
    private EnderecoRequest endereco;
}
