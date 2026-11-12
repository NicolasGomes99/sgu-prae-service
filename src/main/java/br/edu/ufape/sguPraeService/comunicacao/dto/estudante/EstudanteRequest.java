package br.edu.ufape.sguPraeService.comunicacao.dto.estudante;

import br.edu.ufape.sguPraeService.comunicacao.annotations.NumeroValido;
import br.edu.ufape.sguPraeService.comunicacao.annotations.TipoDeficienciaObrigatorio;
import br.edu.ufape.sguPraeService.comunicacao.dto.endereco.EnderecoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.UsuarioRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TipoDeficienciaObrigatorio
public class EstudanteRequest extends UsuarioRequest {

    @NotBlank(message = "A matrícula é obrigatória")
    private String matricula;

    @NotNull(message = "O ID do curso é obrigatório")
    private Long cursoId;

    @NotNull(message = "Defina o tipo de Etnia.")
    private Long tipoEtniaId;

    @PositiveOrZero(message = "Renda deve ser um valor positivo ou zero.")
    private BigDecimal rendaPercapta;

    @NotBlank(message = "O contato da família é obrigatório.")
    @NumeroValido
    private String contatoFamilia;

    private boolean deficiente;

    @Size(max = 100, message = "O campo 'deficiencia' deve ter no máximo 100 caracteres.")
    private String tipoDeficiencia;

    @Valid
    @NotNull(message = "Endereço é obrigatório.")
    private EnderecoRequest endereco;
}