package br.edu.ufape.sguPraeService.comunicacao.dto.usuario;

import br.edu.ufape.sguPraeService.models.Usuario;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.modelmapper.ModelMapper;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class UsuarioResponse {
    private UUID id;
    private String nome;
    private String nomeSocial;
    private String cpf;
    private String email;
    private String telefone;

    public UsuarioResponse(Usuario usuario, ModelMapper modelMapper){
        if (usuario == null) throw new IllegalArgumentException("Usuario não pode ser nulo");
        else modelMapper.map(usuario, this);
    }
}