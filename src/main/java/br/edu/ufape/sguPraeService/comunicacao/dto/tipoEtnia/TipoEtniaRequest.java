package br.edu.ufape.sguPraeService.comunicacao.dto.tipoEtnia;

import br.edu.ufape.sguPraeService.models.TipoEtnia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.modelmapper.ModelMapper;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class TipoEtniaRequest {
    @NotBlank(message = "O tipo é obrigatório")
    @Size(min = 3, max = 50, message = "O tipo deve ter entre 3 e 50 caracteres")
    private String tipo;

    public TipoEtnia convertToEntity(TipoEtniaRequest tipoEtniaRequest, ModelMapper modelMapper) {
        return modelMapper.map(tipoEtniaRequest, TipoEtnia.class);
    }
}
