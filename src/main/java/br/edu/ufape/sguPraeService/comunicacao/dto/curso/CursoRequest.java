package br.edu.ufape.sguPraeService.comunicacao.dto.curso;



import br.edu.ufape.sguPraeService.models.Curso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.modelmapper.ModelMapper;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class CursoRequest {

    @NotBlank(message = "O nome é obrigatório")
    @Size(min = 1, max = 100, message = "O curso deve ter entre 1 e 100 caracteres")
    private String nome;

    @NotNull(message = "O numero de periodos é obrigatório")
    @Positive(message = "O valor do número de períodos deve ser positivo")
    private int numeroPeriodos;

    public Curso convertToEntity(CursoRequest cursoRequest, ModelMapper modelMapper) {
        return modelMapper.map(cursoRequest, Curso.class);
    }
}