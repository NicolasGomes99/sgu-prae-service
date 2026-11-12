package br.edu.ufape.sguPraeService.comunicacao.dto.usuario;

import br.edu.ufape.sguPraeService.comunicacao.annotations.NumeroValido;
import br.edu.ufape.sguPraeService.models.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.br.CPF;
import org.modelmapper.ModelMapper;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class UsuarioRequest {
    @NotBlank(message = "O nome é obrigatório")
    @Size(min = 1, max = 100, message = "O nome deve ter entre 1 e 100 caracteres")
    private String nome;

    @Size(max = 100, message = "O nome social deve ter no máximo 100 caracteres")
    private String nomeSocial;

    @NotBlank(message = "O CPF é obrigatório")
    @CPF
    private String cpf;

    @NotBlank(message = "O e-mail é obrigatório")
    @Email
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
    private String senha;

    @NumeroValido
    private String telefone;

    public Usuario convertToEntity(UsuarioRequest usuarioRequest, ModelMapper modelMapper) {
        return modelMapper.map(usuarioRequest, Usuario.class);
    }
}
