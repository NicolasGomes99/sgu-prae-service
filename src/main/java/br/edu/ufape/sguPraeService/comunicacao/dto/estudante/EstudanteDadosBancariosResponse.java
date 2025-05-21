package br.edu.ufape.sguPraeService.comunicacao.dto.estudante;

import br.edu.ufape.sguPraeService.comunicacao.dto.endereco.EnderecoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoEtnia.TipoEtniaResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.AlunoResponse;
import br.edu.ufape.sguPraeService.models.Estudante;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class EstudanteDadosBancariosResponse {

    private Long id;
    private AlunoResponse aluno;
    private BigDecimal rendaPercapta;
    private String contatoFamilia;
    private boolean deficiente;
    private String tipoDeficiencia;
    private TipoEtniaResponse tipoEtnia;
    private EnderecoResponse endereco;


    public EstudanteDadosBancariosResponse(Estudante estudante) {
        this.id = estudante.getId();
        this.rendaPercapta = estudante.getRendaPercapta();
        this.contatoFamilia = estudante.getContatoFamilia();
        this.deficiente = estudante.isDeficiente();
        this.tipoDeficiencia = estudante.getTipoDeficiencia();
        this.tipoEtnia = new TipoEtniaResponse(estudante.getTipoEtnia(), new ModelMapper());
        this.endereco = new EnderecoResponse(estudante.getEndereco(), new ModelMapper());
    }
}
