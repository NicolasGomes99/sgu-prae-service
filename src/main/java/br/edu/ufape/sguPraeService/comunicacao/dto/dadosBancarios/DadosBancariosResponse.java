package br.edu.ufape.sguPraeService.comunicacao.dto.dadosBancarios;

import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.EstudanteDadosBancariosResponse;
import br.edu.ufape.sguPraeService.models.DadosBancarios;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.modelmapper.ModelMapper;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DadosBancariosResponse {
    private Long id;
    private String nomeTitular;
    private String conta;
    private String agencia;
    private EstudanteDadosBancariosResponse estudante;

    public DadosBancariosResponse(DadosBancarios dadosBancarios, ModelMapper modelMapper) {
        if (dadosBancarios == null) throw new IllegalArgumentException("Dados bancários não podem ser nulos");
        else {
            modelMapper.map(dadosBancarios, this);
            if (dadosBancarios.getEstudante() != null) {
                this.estudante = new EstudanteDadosBancariosResponse(dadosBancarios.getEstudante());
            }
        }
    }
}
