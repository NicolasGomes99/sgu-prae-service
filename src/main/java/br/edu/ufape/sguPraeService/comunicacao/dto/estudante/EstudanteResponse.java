package br.edu.ufape.sguPraeService.comunicacao.dto.estudante;

import br.edu.ufape.sguPraeService.comunicacao.dto.curso.CursoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.dadosBancarios.DadosBancariosResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.endereco.EnderecoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoEtnia.TipoEtniaResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.UsuarioResponse;
import br.edu.ufape.sguPraeService.models.Estudante;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EstudanteResponse extends UsuarioResponse {

    // Herda ID (UUID), nome, cpf, email, etc. de UsuarioResponse

    private String matricula;
    private CursoResponse curso;
    private TipoEtniaResponse tipoEtnia;
    private BigDecimal rendaPercapta;
    private String contatoFamilia;
    private boolean deficiente;
    private String tipoDeficiencia;
    private EnderecoResponse endereco;
    private DadosBancariosResponse dadosBancarios;

    public EstudanteResponse(Estudante estudante, ModelMapper modelMapper) {
        super(estudante, modelMapper); // Mapeia os dados do Usuario
        this.matricula = estudante.getMatricula();
        this.rendaPercapta = estudante.getRendaPercapta();
        this.contatoFamilia = estudante.getContatoFamilia();
        this.deficiente = estudante.isDeficiente();
        this.tipoDeficiencia = estudante.getTipoDeficiencia();

        if (estudante.getCurso() != null) {
            this.curso = new CursoResponse(estudante.getCurso(), modelMapper);
        }
        if (estudante.getTipoEtnia() != null) {
            this.tipoEtnia = new TipoEtniaResponse(estudante.getTipoEtnia(), modelMapper);
        }
        if (estudante.getEndereco() != null) {
            this.endereco = new EnderecoResponse(estudante.getEndereco(), modelMapper);
        }
        if (estudante.getDadosBancarios() != null) {
            this.dadosBancarios = new DadosBancariosResponse(estudante.getDadosBancarios(), modelMapper);
        }
    }
}
