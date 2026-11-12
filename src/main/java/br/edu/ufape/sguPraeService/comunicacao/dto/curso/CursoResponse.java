package br.edu.ufape.sguPraeService.comunicacao.dto.curso;

import br.edu.ufape.sguPraeService.models.Curso;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.modelmapper.ModelMapper;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CursoResponse {
    Long id;
    String nome;
    int numeroPeriodos;

    public CursoResponse(Curso curso, ModelMapper modelMapper) {
        if (curso == null) throw new IllegalArgumentException("Curso não pode ser nulo");
        else modelMapper.map(curso, this);
    }
}
