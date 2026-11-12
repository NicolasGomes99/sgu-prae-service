package br.edu.ufape.sguPraeService.comunicacao.dto.usuario;

import br.edu.ufape.sguPraeService.comunicacao.annotations.NumeroValido;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class UsuarioPatchRequest {
    @Size(min = 1, max = 100, message = "O nome deve ter entre 1 e 100 caracteres")
    private String nome;

    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    private String nomeSocial;

    @NumeroValido
    private String telefone;
}